package com.tt23xrstudio.vanilla_refit_fabric.entity;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.ModEntityData;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceKnotOffset;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceRopeLogic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 位于一根栅栏上的绕绳结，可同时挂多条拴绳（每个伙伴栅栏一条）。
 *
 * <p>判定框与原版拴绳结一致（放在栅栏上）。点击（无待连接状态）会取消该栅栏上的
 * <b>所有</b>连接，并按“每条约消耗了一根拴绳”返还对应数量的拴绳（创造模式不返还）。
 * 当玩家处于待连接状态时，点击此绳结表示继续追加一条连接，而不是解开。</p>
 *
 * <p>伙伴列表与待连接玩家通过实体数据同步到客户端，用于渲染多条悬链线与“待连接到手部”的拴绳。</p>
 *
 * <p>26.2 适配：同步数据序列化器换自定义注册（COMPOUND_TAG/OPTIONAL_UUID 已删）；
 * {@code hurt} 拆成 {@link #hurtServer}/{@link #hurtClient}；存档改 ValueOutput/ValueInput；
 * {@code interact} 变三参；{@code getBoundingBoxForCulling} 已从 Entity 删除
 * （剔除框下沉到渲染器，任务侧在 FenceKnotRenderer 补回伙伴/pending 手部点）。</p>
 */
public class FenceKnotEntity extends LeashFenceKnotEntity {
    private static final EntityDataAccessor<CompoundTag> DATA_PARTNERS =
            SynchedEntityData.defineId(FenceKnotEntity.class, ModEntityData.COMPOUND_TAG);
    private static final EntityDataAccessor<Optional<UUID>> DATA_PENDING_PLAYER =
            SynchedEntityData.defineId(FenceKnotEntity.class, ModEntityData.OPTIONAL_UUID);

    /**
     * 伙伴列表的解析结果缓存：实体的同步数据里存的是 CompoundTag，
     * 而 getPartners() 每 tick 一次、每帧还被渲染与裁剪各调一次，
     * 每次都反序列化会持续产生垃圾。这里按“同步数据里的 tag 实例”做失效判断：
     * 同一个实例就直接返回缓存，set/网络同步都会换成新实例，于是会自动重新解析。
     */
    private CompoundTag cachedPartnersTag;
    private Map<BlockPos, Boolean> cachedPartners = Map.of();

    public FenceKnotEntity(EntityType<? extends FenceKnotEntity> entityType, Level level) {
        super(entityType, level);
    }

    public FenceKnotEntity(Level level, BlockPos pos) {
        this(ModEntities.FENCE_KNOT, level);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 先让原版结登记自己的同步数据（挂靠位置等），再登记本模组的两条。
        super.defineSynchedData(builder);
        builder.define(DATA_PARTNERS, serializePartners(Map.of()));
        builder.define(DATA_PENDING_PLAYER, Optional.empty());
    }

    // ====== 伙伴连接（每条记录伙伴栅栏 -> 是否消耗过拴绳） ======

    public Map<BlockPos, Boolean> getPartners() {
        CompoundTag tag = this.getEntityData().get(DATA_PARTNERS);
        if (tag != this.cachedPartnersTag) {
            this.cachedPartnersTag = tag;
            this.cachedPartners = deserializePartners(tag);
        }
        return this.cachedPartners;
    }

    public boolean hasPartners() {
        return !this.getPartners().isEmpty();
    }

    public void setPartner(BlockPos partner, boolean consumed) {
        // 复制一份再改，避免动到 getPartners() 返回的缓存
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>(this.getPartners());
        partners.put(partner, consumed);
        this.getEntityData().set(DATA_PARTNERS, serializePartners(partners));
    }

    public boolean removePartner(BlockPos partner) {
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>(this.getPartners());
        if (partners.remove(partner) == null) {
            return false;
        }
        this.getEntityData().set(DATA_PARTNERS, serializePartners(partners));
        return true;
    }

    private void clearPartners() {
        this.getEntityData().set(DATA_PARTNERS, serializePartners(Map.of()));
    }

    // ====== 待连接玩家（用于客户端渲染“挂在手上”的拴绳） ======

    public Optional<UUID> getPendingPlayerOpt() {
        return this.getEntityData().get(DATA_PENDING_PLAYER);
    }

    public UUID getPendingPlayer() {
        return this.getEntityData().get(DATA_PENDING_PLAYER).orElse(null);
    }

    public void setPendingPlayer(UUID uuid) {
        this.getEntityData().set(DATA_PENDING_PLAYER, Optional.ofNullable(uuid));
    }

    // 26.2：Entity.hurt 变 final，服务端伤害处理改由 hurtServer 覆写。
    // 任意伤害 = 全部解开（与源版语义一致）。
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return false;
        }
        this.cancelAll();
        return true;
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return false;
    }

    // ====== 逻辑 ======

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }
        BlockPos selfPos = this.getPos();
        if (!isFence(selfPos)) {
            // 自己所在的栅栏被拆：断开所有连接并移除本结。
            // 返还拴绳由仍存在的伙伴结负责（它们会检测到本结栅栏消失）。
            UUID pending = this.getPendingPlayer();
            if (pending != null) {
                for (Player player : this.level().players()) {
                    if (player.getUUID().equals(pending)) {
                        FenceRopeLogic.clearPending(player);
                        break;
                    }
                }
            }
            this.clearPartners();
            this.discard();
            return;
        }
        // 清理伙伴栅栏已被拆除的连接，并按每条消耗返还拴绳。
        for (Map.Entry<BlockPos, Boolean> entry : new ArrayList<>(this.getPartners().entrySet())) {
            BlockPos partner = entry.getKey();
            if (!isFence(partner)) {
                this.removePartner(partner);
                FenceKnotEntity partnerKnot = FenceRopeLogic.findKnot(this.level(), partner);
                if (partnerKnot != null) {
                    partnerKnot.removePartner(selfPos);
                }
                if (entry.getValue()) {
                    this.dropLead();
                }
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // 绳可能拉得很长，渲染距离放宽到 256 格。
        return distance < 65536.0D;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 interactionLocation) {
        if (this.level().isClientSide()) {
            // 客户端镜像"待连接"状态的转移（与服务端一致：有待连接就清空，
            // 再点同一根是取消、点另一根是完成，结果都是清空），用于右键本地预测。
            if (FenceRopeLogic.getPending(player) != null) {
                FenceRopeLogic.clearPending(player);
            }
            return InteractionResult.SUCCESS;
        }
        // 玩家正牵着动物时，交由原版逻辑（把动物拴到/解开本结）。
        if (FenceRopeLogic.hasPlayerLeashedMobs(player, this.level(), this.getPos())) {
            // 原版会把动物拴到本结上，这条待连接就用掉了，顺手清掉"拴到手上"的标记
            FenceRopeLogic.clearPendingWithKnot(player, this.level());
            return super.interact(player, hand, interactionLocation);
        }

        BlockPos pending = FenceRopeLogic.getPending(player);
        if (pending != null) {
            if (pending.equals(this.getPos())) {
                // 再次点击待连接结：取消建立状态。
                FenceRopeLogic.clearPending(player);
                this.setPendingPlayer(null);
                if (!this.hasPartners()) {
                    this.discard();
                }
                return InteractionResult.CONSUME;
            }
            // 处于待连接状态：追加一条到本栅栏的连接，不解开。
            boolean creative = player.getAbilities().instabuild;
            FenceRopeLogic.connect(this.level(), pending, this.getPos(), !creative);
            FenceRopeLogic.clearPending(player);
            if (!creative && player.getItemInHand(hand).is(Items.LEAD)) {
                player.getItemInHand(hand).shrink(1);
            }
            this.level().playSound(null, this.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        // 没有待连接状态：取消本结上的全部连接。
        this.cancelAll();
        return InteractionResult.CONSUME;
    }

    /** 取消本结上的全部连接，按每条消耗的拴绳返还，并移除本结。 */
    private void cancelAll() {
        if (this.level().isClientSide()) {
            return;
        }
        for (Map.Entry<BlockPos, Boolean> entry : new ArrayList<>(this.getPartners().entrySet())) {
            BlockPos partner = entry.getKey();
            boolean consumed = entry.getValue();
            FenceKnotEntity partnerKnot = FenceRopeLogic.findKnot(this.level(), partner);
            if (partnerKnot != null) {
                partnerKnot.removePartner(this.getPos());
                if (!partnerKnot.hasPartners() && partnerKnot.getPendingPlayer() == null) {
                    partnerKnot.discard();
                }
            }
            if (consumed) {
                this.dropLead();
            }
        }
        this.clearPartners();
        this.discard();
    }

    private void dropLead() {
        BlockPos p = this.getPos();
        ItemEntity lead = new ItemEntity(this.level(), p.getX() + 0.5, p.getY() + 0.45, p.getZ() + 0.25, new ItemStack(Items.LEAD));
        lead.setDefaultPickUpDelay();
        this.level().addFreshEntity(lead);
    }

    private boolean isFence(BlockPos pos) {
        return pos != null && this.level().getBlockState(pos).is(BlockTags.FENCES);
    }

    // ====== 持久化与同步 ======

    private static CompoundTag serializePartners(Map<BlockPos, Boolean> partners) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Boolean> entry : partners.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("P", entry.getKey().asLong());
            c.putBoolean("C", entry.getValue());
            list.add(c);
        }
        tag.put("L", list);
        return tag;
    }

    private static Map<BlockPos, Boolean> deserializePartners(CompoundTag tag) {
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>();
        ListTag list = tag.getListOrEmpty("L");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompoundOrEmpty(i);
            partners.put(BlockPos.of(c.getLongOr("P", 0L)), c.getBooleanOr("C", false));
        }
        return partners;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // LeashFenceKnotEntity 原版为空实现，须自行保存挂靠位置、伙伴与待连接玩家。
        output.putLong("AttachPos", this.getPos().asLong());
        output.putString("PendingPlayer", this.getPendingPlayerOpt().map(UUID::toString).orElse(""));
        var partnersList = output.childrenList("Partners");
        for (Map.Entry<BlockPos, Boolean> entry : this.getPartners().entrySet()) {
            var entryOut = partnersList.addChild();
            entryOut.putLong("P", entry.getKey().asLong());
            entryOut.putBoolean("C", entry.getValue());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        BlockPos p = BlockPos.of(input.getLongOr("AttachPos", 0L));
        this.setPos(p.getX(), p.getY(), p.getZ());
        String pending = input.getStringOr("PendingPlayer", "");
        this.getEntityData().set(DATA_PENDING_PLAYER, readPendingPlayer(pending));
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>();
        for (ValueInput entryIn : input.childrenListOrEmpty("Partners")) {
            partners.put(BlockPos.of(entryIn.getLongOr("P", 0L)), entryIn.getBooleanOr("C", false));
        }
        this.getEntityData().set(DATA_PARTNERS, serializePartners(partners));
    }

    /** 存档里的 UUID 字符串可能被写坏，解析失败时回退成“没有待连接玩家”，避免区块加载崩溃。 */
    private static Optional<UUID> readPendingPlayer(String pending) {
        if (pending == null || pending.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(pending));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}

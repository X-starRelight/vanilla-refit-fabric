package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import com.tt23xrstudio.vanilla_refit_fabric.network.CrawlStatePayload;
import com.tt23xrstudio.vanilla_refit_fabric.network.SitTogglePayload;
import com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic;
import com.tt23xrstudio.vanilla_refit_fabric.util.SitLogic;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * 坐/爬服务端逻辑与两个 C2S 载荷的测试。
 *
 * <p>网络接收器本身只能由真实客户端发包触发，这里测其背后的落点：
 * {@link SitLogic#toggleSit} 的上/下车状态机、{@link CrawlLogic} 的 UUID 集合，
 * 以及两个载荷的编解码往返。
 */
public class SitLogicGameTest {

    /** 坐一次：生成座位并骑上；再坐一次：下车且座位被立即销毁。 */
    @GameTest
    public void toggleSitSitsAndDismounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        // mock server player 出生在 (0,0,0)（测试世界原点，区块未加载）；
        // 把玩家挪进本测试结构内，座位才会跟着生成在已加载、会 tick 的区块里（与真实玩法一致）。
        BlockPos spot = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);

        SitLogic.toggleSit(player);

        if (!(player.getVehicle() instanceof SitEntity seat)) {
            // 失败路径探针：手工再生成一个座位并直接调 startRiding，
            // 区分「座位没进世界」与「startRiding 被拒绝」两种情况。
            SitEntity probe = new SitEntity(ModEntities.SIT, level);
            probe.setPos(player.getX(), player.getY(), player.getZ());
            boolean probeAdded = level.addFreshEntity(probe);
            boolean probeRode = player.startRiding(probe, true, true);
            AABB around = new AABB(player.blockPosition()).inflate(3.0);
            StringBuilder diag = new StringBuilder();
            diag.append("｜同level=").append(player.level() == level)
                    .append("，玩家区块已加载=").append(level.isLoaded(player.blockPosition()));

            // D7：force=true 但不发事件/触发器
            boolean d7 = probeRode || player.startRiding(probe, true, false);
            if (d7 && player.isPassenger()) player.stopRiding();
            diag.append("｜D7 force+不发事件=").append(d7);

            // D1：force=false（发送事件）
            boolean d1 = player.startRiding(probe, false, true);
            if (d1 && player.isPassenger()) player.stopRiding();
            diag.append("，D1 force=false=").append(d1);

            // D2：单参重载
            boolean d2 = player.startRiding(probe);
            if (d2 && player.isPassenger()) player.stopRiding();
            diag.append("，D2 单参=").append(d2);

            // D3：isPickable=true 的座位子类（检验「座位不可选中」是否是骑乘门槛）
            SitEntity pickable = new PickableSit(level);
            pickable.setPos(player.getX(), player.getY(), player.getZ());
            boolean pickAdded = level.addFreshEntity(pickable);
            boolean d3 = player.startRiding(pickable, true, true);
            if (d3 && player.isPassenger()) player.stopRiding();
            diag.append("，D3 可选中座位=").append(d3).append("(加入=").append(pickAdded).append(")");
            pickable.discard();

            // D3b：显式接受乘客的座位子类（检验 canAddPassenger 门槛）
            SitEntity eager = new EagerSit(level);
            eager.setPos(player.getX(), player.getY(), player.getZ());
            boolean eagerAdded = level.addFreshEntity(eager);
            boolean d3b = player.startRiding(eager, true, true);
            if (d3b && player.isPassenger()) player.stopRiding();
            diag.append("，D3b 显式接受乘客=").append(d3b).append("(加入=").append(eagerAdded).append(")");
            eager.discard();

            // D4：骑原版实体（全原版「玩家+载具」对照）——按 id 查注册表，避开动物类包名变化
            EntityType<?> vanillaType = BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.fromNamespaceAndPath("minecraft", "minecart"));
            if (vanillaType == null) {
                diag.append("，D4 minecart 类型=null");
            } else {
                // 26.2：create 需显式 SpawnReason（诊断分支只造实体对照骑乘，TRIGGERED 即可）
                Entity vanilla = vanillaType.create(level, EntitySpawnReason.TRIGGERED);
                if (vanilla == null) {
                    diag.append("，D4 minecart create=null");
                } else {
                    vanilla.setPos(player.getX(), player.getY(), player.getZ());
                    boolean vanillaAdded = level.addFreshEntity(vanilla);
                    boolean d4 = player.startRiding(vanilla, true, true);
                    if (d4 && player.isPassenger()) player.stopRiding();
                    diag.append("，D4 骑原版实体=").append(d4).append("(加入=").append(vanillaAdded).append(")");
                    vanilla.discard();
                }
            }

            // D5：第二个 mock 玩家骑同一座位（排除玩家个体状态）
            ServerPlayer other = MockPlayers.mockPlayer(helper);
            other.setPos(player.getX(), player.getY(), player.getZ());
            boolean d5 = other.startRiding(probe, true, true);
            diag.append("，D5 第二玩家骑座位=").append(d5);

            // D8-D10：原版三类实体对照（纯 Entity / LivingEntity / 裸 Entity 负对照），
            // 定位 26.2 startRiding 前置门槛落在「类层级 / noPhysics / 注册类型」哪一维。
            String[][] vanillaProbes = {
                    {"item", "D8 纯Entity(item)"},
                    {"armor_stand", "D9 Living(armor_stand)"},
                    {"marker", "D10 marker(负对照)"},
            };
            for (String[] vp : vanillaProbes) {
                EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", vp[0]));
                Entity e = t == null ? null : t.create(level, EntitySpawnReason.TRIGGERED);
                if (e == null) {
                    diag.append("，").append(vp[1]).append("=null");
                    continue;
                }
                e.setPos(player.getX(), player.getY(), player.getZ());
                boolean vAdded = level.addFreshEntity(e);
                boolean ok = player.startRiding(e, true, true);
                if (ok && player.isPassenger()) player.stopRiding();
                diag.append("，").append(vp[1]).append("=").append(ok)
                        .append("(加入=").append(vAdded).append(")");
                e.discard();
            }

            // D11：同类型座位但关闭 noPhysics（检验 noPhysics 门槛）。
            // 注意：SitEntity 派生类的空 defineSynchedData 若让挂载走到写同步数据会抛错，
            // 故捕获异常并记入 diag，保证整表可读。
            try {
                SitEntity physics = new PhysicsSit(level);
                physics.setPos(player.getX(), player.getY(), player.getZ());
                boolean phAdded = level.addFreshEntity(physics);
                boolean d11 = player.startRiding(physics, true, true);
                if (d11 && player.isPassenger()) player.stopRiding();
                diag.append("，D11 非noPhysics座位=").append(d11).append("(加入=").append(phAdded).append(")");
                physics.discard();
            } catch (Throwable ex) {
                diag.append("，D11 异常=").append(ex.getMessage());
            }

            // D12：VehicleEntity 子类座位（检验「载具类层级」门槛 = 候选修复路径）
            try {
                VehicleSeat vseat = new VehicleSeat(level);
                vseat.setPos(player.getX(), player.getY(), player.getZ());
                boolean vsAdded = level.addFreshEntity(vseat);
                boolean d12 = player.startRiding(vseat, true, true);
                if (d12 && player.isPassenger()) player.stopRiding();
                diag.append("，D12 VehicleEntity座位=").append(d12).append("(加入=").append(vsAdded).append(")");
                vseat.discard();
            } catch (Throwable ex) {
                diag.append("，D12 异常=").append(ex.getMessage());
            }

            // D13-D16：类 vs 类型正交判别 + 两个被遗漏的实例变量。
            // D13：我们的 SitEntity 类 + 原版矿车类型（不安全转型，仅测试仪器）
            try {
                @SuppressWarnings("unchecked")
                EntityType<SitEntity> cartType = (EntityType<SitEntity>) (EntityType<?>) BuiltInRegistries.ENTITY_TYPE
                        .getValue(Identifier.fromNamespaceAndPath("minecraft", "minecart"));
                SitEntity d13e = new SitEntity(cartType, level);
                d13e.setPos(player.getX(), player.getY(), player.getZ());
                boolean d13a = level.addFreshEntity(d13e);
                boolean d13 = player.startRiding(d13e, true, true);
                if (d13 && player.isPassenger()) player.stopRiding();
                diag.append("，D13 我的类+矿车类型=").append(d13).append("(加入=").append(d13a).append(")");
                d13e.discard();
            } catch (Throwable ex) {
                diag.append("，D13 异常=").append(ex.getMessage());
            }

            // D14：原版 ItemEntity 类 + 我们的 sit 类型（不安全转型，仅测试仪器）
            try {
                @SuppressWarnings("unchecked")
                EntityType<ItemEntity> sitRaw = (EntityType<ItemEntity>) (EntityType<?>) ModEntities.SIT;
                ItemEntity d14e = new ItemEntity(sitRaw, level);
                d14e.setPos(player.getX(), player.getY(), player.getZ());
                boolean d14a = level.addFreshEntity(d14e);
                boolean d14 = player.startRiding(d14e, true, true);
                if (d14 && player.isPassenger()) player.stopRiding();
                diag.append("，D14 原版类+我的类型=").append(d14).append("(加入=").append(d14a).append(")");
                d14e.discard();
            } catch (Throwable ex) {
                diag.append("，D14 异常=").append(ex.getMessage());
            }

            // D15：SitEntity + 关闭 noGravity（D11 只关了 noPhysics，noGravity 仍开着）
            try {
                GravitySit d15e = new GravitySit(level);
                d15e.setPos(player.getX(), player.getY(), player.getZ());
                boolean d15a = level.addFreshEntity(d15e);
                boolean d15 = player.startRiding(d15e, true, true);
                if (d15 && player.isPassenger()) player.stopRiding();
                diag.append("，D15 非noGravity座位=").append(d15).append("(加入=").append(d15a).append(")");
                d15e.discard();
            } catch (Throwable ex) {
                diag.append("，D15 异常=").append(ex.getMessage());
            }

            // D16：SitEntity + isPushable=true（座位覆写为 false）
            try {
                PushableSit d16e = new PushableSit(level);
                d16e.setPos(player.getX(), player.getY(), player.getZ());
                boolean d16a = level.addFreshEntity(d16e);
                boolean d16 = player.startRiding(d16e, true, true);
                if (d16 && player.isPassenger()) player.stopRiding();
                diag.append("，D16 可推动座位=").append(d16).append("(加入=").append(d16a).append(")");
                d16e.discard();
            } catch (Throwable ex) {
                diag.append("，D16 异常=").append(ex.getMessage());
            }

            helper.fail("toggle 一次后玩家应骑在 SitEntity 座位上；实际载具=" + player.getVehicle()
                    + "，玩家位置=" + player.position()
                    + "，附近座位实体数=" + level.getEntitiesOfClass(SitEntity.class, around).size()
                    + "，passenger=" + player.isPassenger()
                    + "，dead=" + player.isDeadOrDying()
                    + "，spectator=" + player.isSpectator()
                    + "｜探针：加入世界=" + probeAdded + "，startRiding 返回=" + probeRode
                    + "，探针乘客数=" + probe.getPassengers().size()
                    + "，探针存活=" + probe.isAlive()
                    + diag);
            return;
        }
        if (seat.isRemoved()) {
            helper.fail("刚生成的座位不应处于已移除状态");
            return;
        }
        if (!player.isPassenger()) {
            helper.fail("toggle 一次后玩家应处于乘坐状态");
            return;
        }

        SitLogic.toggleSit(player);

        if (player.isPassenger()) {
            helper.fail("再 toggle 一次应下车");
            return;
        }
        if (!seat.isRemoved()) {
            helper.fail("下车后座位实体应被立即销毁");
            return;
        }
        helper.succeed();
    }

    /**
     * 坐姿第一人称眼高（26.2 语义）：骑上座位后 {@code getEyeY()-getY()} 应为 1.62-0.35=1.27。
     *
     * <p>26.2 相机与弹道走 {@code getEyeY()}（研究 §5-①：读 eyeHeight 字段、不调 getEyeHeight()），
     * 两个注入各自只该下沉一次；若 {@code getEyeY} 内部实际经由 {@code getEyeHeight()} 计算，
     * 两条 -0.35 会叠成 0.92（视角过低），本用例钉住该语义。同时断言骑乘后脚底仍在原地
     * （座位挂接点没有把人塞低）。</p>
     */
    @GameTest
    public void sitEyeHeightDropsOnce(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.mockPlayer(helper);
        BlockPos spot = helper.absolutePos(new BlockPos(2, 1, 2));
        player.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        double feetBefore = player.getY();

        SitLogic.toggleSit(player);

        helper.runAfterDelay(2L, () -> {
            if (!(player.getVehicle() instanceof SitEntity seat)) {
                helper.fail("未坐上座位（车辆=" + player.getVehicle() + "）");
                return;
            }
            double feetDelta = player.getY() - feetBefore;
            if (Math.abs(feetDelta) > 0.05D) {
                StringBuilder diag = new StringBuilder();
                diag.append("骑乘后脚底位移=").append(feetDelta);
                diag.append("｜座位y=").append(seat.getY());
                diag.append("｜玩家bb=").append(player.getBbWidth()).append("x").append(player.getBbHeight());
                diag.append("｜vehicleAttach=").append(player.getVehicleAttachmentPoint(seat));
                diag.append("｜ridingPos=").append(seat.getPassengerRidingPosition(player));
                helper.fail(diag.toString());
                return;
            }
            double eyeDelta = player.getEyeY() - player.getY();
            if (Math.abs(eyeDelta - 1.27D) > 0.01D) {
                helper.fail("坐姿眼高差=" + eyeDelta + "，期望 1.27（1.62-0.35 单次下沉）");
                return;
            }
            // 下车后回到原站位（挂接点改动不得让站起时弹跳/瞬移）
            SitLogic.toggleSit(player);
            if (player.isPassenger()) {
                helper.fail("再 toggle 一次应下车");
                return;
            }
            double dismountDelta = player.getY() - feetBefore;
            if (Math.abs(dismountDelta) > 0.05D) {
                helper.fail("下车后脚底位移=" + dismountDelta + "，应回到原站位");
                return;
            }
            helper.succeed();
        });
    }

    /** 爬行标记：默认 false -> true -> false（服务端 UUID 集合读写）。 */
    @GameTest
    public void crawlFlagRoundTrip(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.mockPlayer(helper);

        if (CrawlLogic.isCrawling(player)) {
            helper.fail("新玩家默认不应处于爬行状态");
            return;
        }

        CrawlLogic.setCrawling(player, true);
        if (!CrawlLogic.isCrawling(player)) {
            helper.fail("写入 true 后应读回 true");
            return;
        }

        CrawlLogic.setCrawling(player, false);
        if (CrawlLogic.isCrawling(player)) {
            helper.fail("写入 false 后应读回 false");
            return;
        }
        helper.succeed();
    }

    /** 两个 C2S 载荷的编解码往返（boolean 一个字节；无字段载荷解出来是等价实例）。 */
    @GameTest
    public void payloadCodecRoundTrip(GameTestHelper helper) {
        RegistryFriendlyByteBuf trueBuf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        CrawlStatePayload.STREAM_CODEC.encode(trueBuf, new CrawlStatePayload(true));
        if (!CrawlStatePayload.STREAM_CODEC.decode(trueBuf).crawling()) {
            helper.fail("CrawlStatePayload(true) 往返后应仍为 true");
            return;
        }

        RegistryFriendlyByteBuf falseBuf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        CrawlStatePayload.STREAM_CODEC.encode(falseBuf, new CrawlStatePayload(false));
        if (CrawlStatePayload.STREAM_CODEC.decode(falseBuf).crawling()) {
            helper.fail("CrawlStatePayload(false) 往返后应仍为 false");
            return;
        }

        RegistryFriendlyByteBuf sitBuf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        SitTogglePayload.STREAM_CODEC.encode(sitBuf, new SitTogglePayload());
        if (!new SitTogglePayload().equals(SitTogglePayload.STREAM_CODEC.decode(sitBuf))) {
            helper.fail("SitTogglePayload 往返后应是等价实例");
            return;
        }
        helper.succeed();
    }

    /** 诊断用：可被选中的座位（检验 isPickable=false 是否是骑乘门槛）。 */
    private static final class PickableSit extends SitEntity {
        PickableSit(Level level) {
            super(ModEntities.SIT, level);
        }

        @Override
        public boolean isPickable() {
            return true;
        }
    }

    /** 诊断用：显式接受乘客的座位（检验 canAddPassenger 门槛）。 */
    private static final class EagerSit extends SitEntity {
        EagerSit(Level level) {
            super(ModEntities.SIT, level);
        }

        @Override
        protected boolean canAddPassenger(Entity passenger) {
            return true;
        }
    }

    /** 诊断用：关闭 noPhysics 的座位（检验 noPhysics 是否是骑乘门槛）。 */
    private static final class PhysicsSit extends SitEntity {
        PhysicsSit(Level level) {
            super(ModEntities.SIT, level);
            this.noPhysics = false;
        }
    }

    /** 诊断用：关闭 noGravity 的座位（D11 只关了 noPhysics，此处隔离 noGravity 变量）。 */
    private static final class GravitySit extends SitEntity {
        GravitySit(Level level) {
            super(ModEntities.SIT, level);
            this.setNoGravity(false);
        }
    }

    /** 诊断用：可推动的座位（隔离 isPushable 变量）。 */
    private static final class PushableSit extends SitEntity {
        PushableSit(Level level) {
            super(ModEntities.SIT, level);
        }

        @Override
        public boolean isPushable() {
            return true;
        }
    }

    /** 诊断用：VehicleEntity 子类座位（检验 26.2 骑乘是否要求载具类层级 = 候选修复路径）。 */
    private static final class VehicleSeat extends VehicleEntity {
        VehicleSeat(Level level) {
            // 诊断探针只关心对象类型层级：按 id 取原版矿车类型并收窄（26.2 已无 EntityType.MINECART 常量），
            // 工厂不参与本探针（直接 new）。
            super(castMinecartType(), level);
        }

        @SuppressWarnings("unchecked")
        private static EntityType<? extends VehicleEntity> castMinecartType() {
            return (EntityType<? extends VehicleEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.fromNamespaceAndPath("minecraft", "minecart"));
        }

        // 26.2 把「被破坏时掉落什么」上提为 VehicleEntity 的抽象方法；座位不掉落。
        @Override
        public Item getDropItem() {
            return Items.AIR;
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            return false;
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
        }
    }
}

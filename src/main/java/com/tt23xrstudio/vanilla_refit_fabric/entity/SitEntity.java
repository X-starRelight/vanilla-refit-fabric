package com.tt23xrstudio.vanilla_refit_fabric.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 隐形、静止的“座位”实体（原 NeoForge 版 {@code com.example.myfirstmod.entity.SitEntity}）。
 *
 * <p>玩家骑乘它即可显示原版坐姿动画（双腿弯曲）；由于座位本身不会移动，
 * 玩家的移动输入也不会把人带走。
 *
 * <p>26.2 适配：存档读写参数 {@code CompoundTag} → {@link ValueInput}/{@link ValueOutput}；
 * {@code canBeCollidedWith()} 带上了 {@code Entity} 参数；{@code getBoundingBoxForCulling()}
 * 已从 {@link Entity} 删除（culling 框下沉到渲染器），故不再覆写。
 */
public class SitEntity extends Entity {

    public SitEntity(EntityType<? extends SitEntity> type, Level level) {
        super(type, level);
        // 需要与方块碰撞才不会穿地（26.2 半空坐下会受重力下落，见 tick()）；
        // 重力不再关闭——半空按下 Z 后座位应继续掉落，落地被地面托住（用户要求）。
        this.noPhysics = false;
    }

    // 26.2 契约（与 1.21 源项目同款写法）：defineSynchedData 是抽象方法，子类只登记「自己的」追踪数据，
    // 基类键（位置/乘客等）由 Entity 构造器预登记。座位无自有数据，空体即可。
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    // 26.2 把服务端伤害处理拆成了 Entity 的抽象方法；座位不接受任何伤害，直接“未处理”。
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

    // 座位本身不参与任何碰撞、推动或交互。
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    // 不可见。
    @Override
    public boolean shouldRender(double x, double y, double z) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    // 玩家坐在座位上时的挂接点。
    // 26.2 实证（gametest + 用户客户端现象双向验证）：
    // - 骑乘落点 = 座位位置 + 本挂接点 - 乘客 VEHICLE 挂点（玩家 (0,0.6,0)）；
    // - 客户端渲染器把玩家模型髋部画在 origin + VEHICLE 挂点处。
    // 返回 ZERO：origin = 座位 - 0.6 → 模型髋部恰好落在座位上（屁股贴地，源项目观感）；
    // 代价是 origin 下沉 0.6，绝对眼高由 PlayerEyeHeightMixin 补回（见该类注释）。
    // 曾改为返回 VEHICLE 挂点“让脚底贴地”——相机对了但模型整体悬空 0.6，已回退。
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            // 26.2 实证（gametest 探测：40 tick 后 delta 恒为 (0,0,0)，applyGravity() 也无效——
            // 基类 getDefaultGravity() 返回 0）：Entity.tick() 不为普通实体集成重力与位移，
            // 必须自己施加重力并 move()，否则半空坐下会悬停（用户反馈的 bug）。
            if (!this.isNoGravity()) {
                Vec3 velocity = this.getDeltaMovement();
                this.setDeltaMovement(velocity.x, velocity.y - 0.08D, velocity.z);
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
            // 落地（onGround 或脚下紧贴实体方块）后清零竖直速度，防止重力逐 tick 无限累积；
            // 未落地时施加原版空气阻力（×0.98），避免高空落下速度无上限。
            Vec3 afterMove = this.getDeltaMovement();
            if (this.onGround() || blockedBelow()) {
                if (afterMove.y != 0.0D) {
                    this.setDeltaMovement(afterMove.x, 0.0D, afterMove.z);
                }
            } else {
                this.setDeltaMovement(afterMove.scale(0.98D));
            }
        }
        // 无人骑乘时自动清理（玩家死亡、离开或退出时）。
        if (!this.level().isClientSide() && this.getPassengers().isEmpty()) {
            this.discard();
        }
    }

    /** 座位正下方 0.01 格处是否有带碰撞形状的方块（兜底判定“被托住”，不依赖 onGround 语义）。 */
    private boolean blockedBelow() {
        Level level = this.level();
        BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.01D, this.getZ());
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}

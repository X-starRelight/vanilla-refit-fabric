package com.tt23xrstudio.vanilla_refit_fabric.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
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
        this.noPhysics = true;
        this.setNoGravity(true);
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

    // 玩家坐在座位上时，底边贴合座位位置（略微抬高以免穿地）。
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    public void tick() {
        super.tick();
        // 无人骑乘时自动清理（玩家死亡、离开或退出时）。
        if (!this.level().isClientSide() && this.getPassengers().isEmpty()) {
            this.discard();
        }
    }
}

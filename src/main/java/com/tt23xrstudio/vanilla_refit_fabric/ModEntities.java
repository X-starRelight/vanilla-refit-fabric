package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 实体类型注册（原 NeoForge 版 DeferredRegister → {@code Registry.register}）。
 *
 * <p>目前只有一个隐形座位实体 {@code vanilla_refit_fabric:sit}。
 */
public final class ModEntities {

    /** 隐形座位实体的 id。 */
    public static final Identifier SIT_ID = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "sit");

    /**
     * 隐形座位：随盘保存、尺寸 0.001×0.001、追踪范围 10 格、更新间隔
     * {@code Integer.MAX_VALUE}（几乎不发位置同步包——座位本身永远不动）。
     */
    public static final EntityType<SitEntity> SIT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SIT_ID,
            EntityType.Builder.<SitEntity>of(SitEntity::new, MobCategory.MISC)
                    .sized(0.001F, 0.001F)
                    // 26.2：startRiding 前置检查会拒绝 serialize=false（不可写盘）的载具——
                    // 26.1 世界存储重构后，骑上不入盘的实体会导致存档时玩家-载具关联断裂。
                    // 故座位必须随盘保存；无人骑乘时仍由 tick() 立即 discard，行为不变。
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, SIT_ID)));

    private ModEntities() {
    }

    /** 触发本类的静态注册；在 {@link ModBlockEntities#initialize()} 之后调用。 */
    public static void initialize() {
        // 静态字段在类加载时即完成注册
    }
}

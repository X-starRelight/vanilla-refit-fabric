package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.entity.FenceKnotEntity;
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
 * <p>目前有隐形座位 {@code vanilla_refit_fabric:sit} 与栅栏绕绳结
 * {@code vanilla_refit_fabric:fence_knot}（牵引绳悬链）。
 */
public final class ModEntities {

    /** 隐形座位实体的 id。 */
    public static final Identifier SIT_ID = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "sit");

    /** 绕绳结实体的 id（必须叫 fence_knot 才能命中语言项 entity.vanilla_refit_fabric.fence_knot）。 */
    public static final Identifier FENCE_KNOT_ID = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "fence_knot");

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

    /**
     * 栅栏上的绕绳结：一根栅栏可挂多条自绘悬链拴绳。updateInterval 为
     * {@code Integer.MAX_VALUE}（几乎不发位置同步包）——绳结与伙伴端点的高度由两端按
     * 本地方块状态现算“贴台阶”位移（见 {@code FenceKnotOffset}），不依赖位置包。
     *
     * <p>类加载即注册；使用自定义序列化器 {@code ModEntityData}，故本类的类初始化
     * 必须晚于 {@code ModEntityData.initialize()}。
     */
    public static final EntityType<FenceKnotEntity> FENCE_KNOT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            FENCE_KNOT_ID,
            EntityType.Builder.<FenceKnotEntity>of(FenceKnotEntity::new, MobCategory.MISC)
                    .sized(0.375F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, FENCE_KNOT_ID)));

    private ModEntities() {
    }

    /** 触发本类的静态注册；在 {@link ModBlockEntities#initialize()} 之后调用。 */
    public static void initialize() {
        // 静态字段在类加载时即完成注册
    }
}

package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.block.entity.MixedSlabBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体类型注册（原 NeoForge 版 DeferredRegister → {@code Registry.register}）。
 *
 * <p>26.2 没有 {@code BlockEntityType.Builder}，直接
 * {@code new BlockEntityType<>(工厂, 允许的方块集合)}。
 */
public final class ModBlockEntities {

    /** 混合半砖的方块实体类型（存两块半砖材质）。 */
    public static final BlockEntityType<MixedSlabBlockEntity> MIXED_SLAB = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "mixed_slab"),
            new BlockEntityType<>(MixedSlabBlockEntity::create, Set.of(ModBlocks.MERGED_SLAB)));

    private ModBlockEntities() {
    }

    /** 触发本类的静态注册；必须在 {@link ModBlocks#initialize()} 之后（引用其方块）。 */
    public static void initialize() {
        // 静态字段在类加载时即完成注册
    }
}

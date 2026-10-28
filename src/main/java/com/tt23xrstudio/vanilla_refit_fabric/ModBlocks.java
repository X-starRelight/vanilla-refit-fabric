package com.tt23xrstudio.vanilla_refit_fabric;

import com.tt23xrstudio.vanilla_refit_fabric.block.MixedSlabBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 方块与对应物品的注册（原 NeoForge 版用 DeferredRegister，这里改为
 * {@code Registry.register} 静态字段 + {@link #initialize()} 触发类加载）。
 *
 * <p>注册必须发生在 {@code Registry} 冻结之前（模组初始化阶段），由
 * {@link VanillaRefitFabric#onInitialize()} 调用 {@link #initialize()}。
 */
public final class ModBlocks {

    /**
     * 混合半砖：一格子里放两块“不同材质”的半砖（材质存方块实体）。
     *
     * <p>不复制石头的 {@code requiresCorrectToolForDrops}（保持默认 false），
     * 使混合半砖任何工具都能采集掉落两块半砖，避免整挖无掉落。
     */
    public static final Block MERGED_SLAB = Registry.register(
            BuiltInRegistries.BLOCK,
            id("merged_slab"),
            new MixedSlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .setId(blockKey("merged_slab"))));

    /** 混合半砖物品。 */
    public static final Item MERGED_SLAB_ITEM = registerBlockItem("merged_slab", MERGED_SLAB);

    private ModBlocks() {
    }

    /** 触发本类的静态注册（原 DeferredRegister 注册的等价物）。 */
    public static void initialize() {
        // 静态字段在类加载时即完成注册；本方法只是保证类被加载
    }

    private static Item registerBlockItem(String name, Block block) {
        Identifier itemId = id(name);
        return Registry.register(
                BuiltInRegistries.ITEM,
                itemId,
                new BlockItem(block, new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, itemId))
                        .useBlockDescriptionPrefix()));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, path);
    }

    /** 方块注册用的 ResourceKey（{@code Properties#setId} 需要）。 */
    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, id(path));
    }
}

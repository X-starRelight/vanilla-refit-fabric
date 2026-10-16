package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModBlocks;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.block.entity.MixedSlabBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 阶段 1 测试：注册层（方块 / 物品 / 方块实体）与基础掉落。
 *
 * <p>注意 {@code GameTestHelper.destroyBlock} 内部是 {@code drop=false}（不掉东西），
 * 要验证掉落必须自己调 {@code ServerLevel.destroyBlock(pos, true)}。
 */
public class Phase1RegistryGameTest {

    /** 注册表双向可查：id ↔ 实例（方块与物品都要能查到）。 */
    @GameTest
    public void registryRoundTrip(GameTestHelper helper) {
        Identifier stairsId = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "smooth_stone_stairs");
        Identifier slabId = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "merged_slab");

        if (BuiltInRegistries.BLOCK.getValue(stairsId) != ModBlocks.SMOOTH_STONE_STAIRS) {
            helper.fail("方块注册表查不到 smooth_stone_stairs（或指向了别的实例）");
        }
        if (BuiltInRegistries.BLOCK.getValue(slabId) != ModBlocks.MERGED_SLAB) {
            helper.fail("方块注册表查不到 merged_slab");
        }
        if (BuiltInRegistries.ITEM.getValue(stairsId) != ModBlocks.SMOOTH_STONE_STAIRS_ITEM) {
            helper.fail("smooth_stone_stairs 物品未注册");
        }
        if (BuiltInRegistries.ITEM.getValue(slabId) != ModBlocks.MERGED_SLAB_ITEM) {
            helper.fail("merged_slab 物品未注册");
        }
        helper.succeed();
    }

    /** 平滑石楼梯：可放置；破坏后经 data loot table 恰好掉 1 个自身物品。 */
    @GameTest
    public void stairsPlaceAndDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

        helper.setBlock(1, 0, 1, Blocks.BEDROCK); // 地板，防止掉落物坠出扫描范围
        helper.setBlock(1, 1, 1, ModBlocks.SMOOTH_STONE_STAIRS);
        helper.assertBlockPresent(ModBlocks.SMOOTH_STONE_STAIRS, 1, 1, 1);

        level.destroyBlock(pos, true);

        helper.runAfterDelay(2L, () -> {
            long count = totalItems(level, pos, stack -> stack.is(ModBlocks.SMOOTH_STONE_STAIRS_ITEM));
            if (count != 1L) {
                helper.fail("平滑石楼梯破坏后应恰好掉落 1 个自身物品，实际 " + count);
            }
            helper.succeed();
        });
    }

    /**
     * 混合半砖：放置即生成方块实体；掉落走“宽松工具规则”——
     * 空手挖 stone_slab（需要镐）= 0 掉落；带镐问掉落 = 2×stone_slab（走 getDrops 覆写）。
     */
    @GameTest
    public void mergedSlabEntityAndDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

        helper.setBlock(1, 0, 1, Blocks.BEDROCK);
        helper.setBlock(1, 1, 1, ModBlocks.MERGED_SLAB);
        helper.assertBlockPresent(ModBlocks.MERGED_SLAB, 1, 1, 1);

        if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity)) {
            helper.fail("merged_slab 放置后应生成 MixedSlabBlockEntity");
        }

        // 第一步：destroyBlock 不带工具（等价空手）→ stone_slab 需要镐 → 宽松规则不给掉落
        level.destroyBlock(pos, true);

        helper.runAfterDelay(2L, () -> {
            long bare = totalItems(level, pos, stack -> stack.is(Items.STONE_SLAB));
            if (bare != 0L) {
                helper.fail("无工具破坏双 stone_slab 混合半砖应掉 0，实际 " + bare);
            }

            // 第二步：带镐问掉落（测 getDrops 覆写 + 26.2 ItemInstance→ItemStack 转换）
            helper.setBlock(1, 1, 1, ModBlocks.MERGED_SLAB);
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MixedSlabBlockEntity)) {
                helper.fail("重新放置后应再次生成 MixedSlabBlockEntity");
            }
            BlockState state = level.getBlockState(pos);
            Block.dropResources(state, level, pos, be, null, new ItemStack(Items.IRON_PICKAXE));

            long withTool = totalItems(level, pos, stack -> stack.is(Items.STONE_SLAB));
            if (withTool != 2L) {
                helper.fail("带镐破坏双 stone_slab 混合半砖应掉 2×stone_slab，实际 " + withTool);
            }
            helper.succeed();
        });
    }

    /** 统计中心点附近、通过过滤器的物品总数（合并成一个实体也算总堆叠数）。 */
    private static long totalItems(ServerLevel level, BlockPos center, java.util.function.Predicate<ItemStack> filter) {
        AABB box = new AABB(center).inflate(2.5);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        return items.stream()
                .map(ItemEntity::getItem)
                .filter(filter)
                .mapToLong(ItemStack::getCount)
                .sum();
    }
}

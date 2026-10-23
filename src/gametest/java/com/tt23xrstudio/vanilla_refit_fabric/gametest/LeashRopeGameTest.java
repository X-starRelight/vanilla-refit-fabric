package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModEntities;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.entity.FenceKnotEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceKnotOffset;
import com.tt23xrstudio.vanilla_refit_fabric.util.FenceRopeLogic;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 牵引绳悬链测试：绳结实体注册、伙伴连接生命周期、消耗返还、贴台阶位移。
 *
 * <p>“待连接点栅栏 / 点动物”的交互用例在 mixin（任务2）与事件（任务4）落地后加入；
 * 本类先把纯服务端逻辑（注册 / connect / 拆栅栏结算 / 位移函数）钉住。</p>
 */
public class LeashRopeGameTest {

    /** 实体注册表可查：id 必须指向 ModEntities.FENCE_KNOT（DefaultedRegistry 查不到会回落到 air 类型）。 */
    @GameTest
    public void registryRoundTrip(GameTestHelper helper) {
        Identifier id = Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "fence_knot");
        if (BuiltInRegistries.ENTITY_TYPE.getValue(id) != ModEntities.FENCE_KNOT) {
            helper.fail("实体注册表查不到 fence_knot（或指向了别的实例）");
        }
        helper.succeed();
    }

    /** 两根栅栏 connect 后两结伙伴互指；拆掉 B 栅栏后 A 结伙伴清空、B 结移除，consumed=false 不掉落拴绳。 */
    @GameTest
    public void knotPartnerLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(3, 1, 1));

        helper.setBlock(1, 1, 1, Blocks.OAK_FENCE);
        helper.setBlock(3, 1, 1, Blocks.OAK_FENCE);
        FenceRopeLogic.connect(level, a, b, false);

        helper.runAfterDelay(1L, () -> {
            FenceKnotEntity knotA = FenceRopeLogic.findKnot(level, a);
            FenceKnotEntity knotB = FenceRopeLogic.findKnot(level, b);
            if (knotA == null || knotB == null) {
                helper.fail("connect 后两根栅栏上都应有绳结");
            }
            if (!knotA.getPartners().containsKey(b) || !knotB.getPartners().containsKey(a)) {
                helper.fail("两结的伙伴列表应互指");
            }

            helper.setBlock(3, 1, 1, Blocks.AIR); // 拆掉 B 栅栏

            helper.runAfterDelay(2L, () -> {
                if (!knotA.getPartners().isEmpty()) {
                    helper.fail("B 栅栏拆除后 A 结的伙伴应被清空");
                }
                if (!knotB.isRemoved()) {
                    helper.fail("B 栅栏拆除后 B 结应自行移除");
                }
                long leads = totalItems(level, a, stack -> stack.is(Items.LEAD));
                if (leads != 0L) {
                    helper.fail("consumed=false 的连接不应掉落拴绳，实际 " + leads);
                }
                helper.succeed();
            });
        });
    }

    /** consumed=true 的连接：拆掉伙伴栅栏后，仍存在的那一侧结恰好掉落 1 根拴绳。 */
    @GameTest
    public void breakFenceReturnsConsumedLead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(3, 1, 1));

        helper.setBlock(1, 1, 1, Blocks.OAK_FENCE);
        helper.setBlock(3, 1, 1, Blocks.OAK_FENCE);
        FenceRopeLogic.connect(level, a, b, true);

        helper.runAfterDelay(1L, () -> {
            helper.setBlock(3, 1, 1, Blocks.AIR); // 拆掉 B 栅栏

            helper.runAfterDelay(2L, () -> {
                long leads = totalItems(level, a, stack -> stack.is(Items.LEAD));
                if (leads != 1L) {
                    helper.fail("consumed=true 的连接被拆除后应掉落 1 根拴绳，实际 " + leads);
                }
                helper.succeed();
            });
        });
    }

    /**
     * 贴台阶位移的纯函数计算（绳结高度 = 方块 y + 0.375 + 位移量）。
     *
     * <p>实体侧的位移断言（recalculateBoundingBox mixin + 每 tick refresh）在任务 2 的
     * 两个 mixin 注册后补进本用例——任务 1 阶段它们还未注册。</p>
     */
    @GameTest
    public void knotShiftsWithSlabOffset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));

        helper.setBlock(1, 0, 1, Blocks.BEDROCK); // 地板
        helper.setBlock(1, 1, 1, Blocks.OAK_FENCE);
        BlockState fence = level.getBlockState(pos);
        if (!fence.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            helper.fail("栅栏应具有 SLAB_OFFSET 属性（FenceOnSlabMixin 已注入）");
        }

        // 贴下台阶：整体 -0.5（helper 的 BlockPos 版 setBlock 语义按绝对坐标不稳，
        // 直接走 level.setBlock——Phase1 既有用例同款惯例）
        level.setBlock(pos, fence.setValue(ModBlockStateProperties.SLAB_OFFSET, SlabOffset.LOWERED), 3);
        double baseY = pos.getY() + 0.375;
        if (Math.abs(FenceKnotOffset.knotY(level, pos) - (baseY - 0.5)) > 1.0E-9) {
            helper.fail("贴下台阶时 knotY 应为 y+0.375-0.5，pos.y=" + pos.getY()
                    + " 实际 " + FenceKnotOffset.knotY(level, pos));
        }
        if (Math.abs(FenceKnotOffset.ropeHoldY(level, pos) - (baseY - 0.5 + 0.2)) > 1.0E-9) {
            helper.fail("贴下台阶时握绳点应再高 0.2");
        }

        // 贴上台阶：整体 +0.5
        level.setBlock(pos, fence.setValue(ModBlockStateProperties.SLAB_OFFSET, SlabOffset.RAISED), 3);
        if (Math.abs(FenceKnotOffset.knotY(level, pos) - (baseY + 0.5)) > 1.0E-9) {
            helper.fail("贴上台阶时 knotY 应为 y+0.375+0.5");
        }

        // 不贴：原位
        level.setBlock(pos, fence.setValue(ModBlockStateProperties.SLAB_OFFSET, SlabOffset.NONE), 3);
        if (Math.abs(FenceKnotOffset.knotY(level, pos) - baseY) > 1.0E-9) {
            helper.fail("不贴台阶时 knotY 应为 y+0.375");
        }

        // getOrCreateKnot 应在贴位移的栅栏上建出结（位置=方块坐标；高度位移由任务 2 的 mixin 叠加）
        FenceKnotEntity knot = FenceRopeLogic.getOrCreateKnot(level, pos);
        if (knot == null || FenceRopeLogic.findKnot(level, pos) != knot) {
            helper.fail("getOrCreateKnot 应创建并可找回同一绳结");
        }
        helper.succeed();
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

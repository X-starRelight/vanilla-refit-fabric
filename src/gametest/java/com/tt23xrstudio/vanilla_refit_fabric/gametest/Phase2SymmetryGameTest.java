package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import com.tt23xrstudio.vanilla_refit_fabric.ModBlocks;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.StairConnection;
import com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 镜像/旋转变换（{@code BlockStateSymmetryMixin} + {@code SymmetrySupport}）：
 * 机械动力飞跃版对称之杖的对称模式、原版结构方块的镜像/旋转，最终都落到
 * {@code BlockState#mirror} / {@code BlockState#rotate} 这一层——这里断言本模组注入的
 * 方向性属性（竖半砖 mode+type、竖楼梯象限 facing+连接手性、玻璃板角面片）跟着正确变换。
 *
 * <p>全部是纯状态断言，不需要摆放方块；镜面轴语义按 26.2 字节码：
 * {@code FRONT_BACK=INVERT_X}（翻东西）、{@code LEFT_RIGHT=INVERT_Z}（翻南北），
 * 26.2 的 {@code Mirror} 没有对角镜面（LEFT_FRONT 已移除）。
 */
public class Phase2SymmetryGameTest {

    // ===== 竖半砖：朝向 = (mode 轴, type 侧) =====

    /**
     * 竖半砖按轴镜像/旋转：贴侧跟着翻（贴北 → 镜像 Z 轴 → 贴南……）；
     * 平放台阶与组合半砖的回归：平放上下半不被误翻、组合半砖同一套换算。
     */
    @GameTest
    public void verticalSlabMirrorsAndRotates(GameTestHelper helper) {
        BlockState ns = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);      // 贴北

        // FRONT_BACK = X 轴镜像：南北向竖半砖贴北不动
        assertSlab(helper, ns.mirror(Mirror.FRONT_BACK),
                VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM, "X 轴镜像后南北向竖半砖应仍贴北");
        // LEFT_RIGHT = Z 轴镜像：贴北 → 贴南
        assertSlab(helper, ns.mirror(Mirror.LEFT_RIGHT),
                VerticalSlabMode.VERTICAL_NS, SlabType.TOP, "Z 轴镜像后南北向竖半砖应贴南");
        // 顺时针 90°：贴北 → 贴东
        assertSlab(helper, ns.rotate(Rotation.CLOCKWISE_90),
                VerticalSlabMode.VERTICAL_EW, SlabType.TOP, "顺时针转 90° 后应贴东");

        BlockState ew = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_EW)
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);      // 贴西
        assertSlab(helper, ew.mirror(Mirror.FRONT_BACK),
                VerticalSlabMode.VERTICAL_EW, SlabType.TOP, "X 轴镜像后东西向竖半砖应贴东");
        assertSlab(helper, ew.mirror(Mirror.LEFT_RIGHT),
                VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM, "Z 轴镜像后东西向竖半砖应贴西");

        // 平放台阶回归：镜像/旋转都不能翻它的上下半（type 只在竖放时才是朝向）
        BlockState flat = Blocks.STONE_SLAB.defaultBlockState();
        assertSlab(helper, flat.mirror(Mirror.LEFT_RIGHT),
                VerticalSlabMode.SLAB, SlabType.BOTTOM, "平放台阶镜像后上下半不能被翻");
        assertSlab(helper, flat.rotate(Rotation.CLOCKWISE_180),
                VerticalSlabMode.SLAB, SlabType.BOTTOM, "平放台阶旋转后上下半不能被翻");

        // 组合半砖带同一套 MODE/TYPE，走同一套换算
        BlockState merged = ModBlocks.MERGED_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        assertSlab(helper, merged.mirror(Mirror.LEFT_RIGHT),
                VerticalSlabMode.VERTICAL_NS, SlabType.TOP, "组合半砖镜像后应贴南");

        helper.succeed();
    }

    // ===== 竖楼梯：facing = L 中心象限的对角 + 连接手性 =====

    /**
     * 竖楼梯镜像：facing 按象限换到相邻方位（北=西北象限 → 镜像后到相邻象限），
     * 左右连接手性互换、双面连接不动；旋转只转 facing 不改手性（原版转 facing 已经够）。
     */
    @GameTest
    public void verticalStairMirrorsQuadrantAndHandedness(GameTestHelper helper) {
        BlockState base = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(ModBlockStateProperties.VERTICAL, true)
                .setValue(StairBlock.FACING, Direction.NORTH)             // L 中心在西北象限
                .setValue(StairBlock.SHAPE, StairConnection.CONN_LEFT.toShape());

        // X 轴镜像：西北象限 ↔ 东北象限 → 北 → 东；连接左右手性互换
        assertStair(helper, base.mirror(Mirror.FRONT_BACK), Direction.EAST, StairConnection.CONN_RIGHT,
                "X 轴镜像后 L 中心应到东北象限、连接换手");
        // Z 轴镜像：西北 ↔ 西南 → 北 → 西
        assertStair(helper, base.mirror(Mirror.LEFT_RIGHT), Direction.WEST, StairConnection.CONN_RIGHT,
                "Z 轴镜像后 L 中心应到西南象限、连接换手");
        // 顺时针 90°：原版转 facing（北 → 东），手性不变
        assertStair(helper, base.rotate(Rotation.CLOCKWISE_90), Direction.EAST, StairConnection.CONN_LEFT,
                "旋转后 facing 应转到东、连接手性不变");

        // 双面连接镜像后仍是双面连接（左右对换对它无影响）
        BlockState dbl = base.setValue(StairBlock.SHAPE, StairConnection.CONN_DOUBLE.toShape());
        assertStair(helper, dbl.mirror(Mirror.FRONT_BACK), Direction.EAST, StairConnection.CONN_DOUBLE,
                "双面连接镜像后不应变成别的形态");

        // 拐角形态镜像后仍是拐角（原版 OUTER_LEFT↔OUTER_RIGHT 的交换不能被带进来）
        BlockState corner = base.setValue(StairBlock.SHAPE, StairConnection.CORNER.toShape());
        assertStair(helper, corner.mirror(Mirror.FRONT_BACK), Direction.EAST, StairConnection.CORNER,
                "拐角镜像后不应变成双面连接");

        helper.succeed();
    }

    // ===== 玻璃板：本模组加的角面片 / 上半片 =====

    /**
     * 玻璃板的 8 个附加属性（四角 + 四个上半片）跟着镜像/旋转搬到对应方向；
     * 原版那 4 个横片由 CrossCollisionBlock 自己处理，这里不重复断言。
     */
    @GameTest
    public void panePiecesMirrorAndRotate(GameTestHelper helper) {
        BlockState base = Blocks.GLASS_PANE.defaultBlockState()
                .setValue(ModBlockStateProperties.PANE_NORTH_EAST, true)   // 东北角
                .setValue(ModBlockStateProperties.PANE_NORTH_UP, true)     // 北上半
                .setValue(ModBlockStateProperties.PANE_EAST_UP, true);     // 东上半

        // X 轴镜像（FRONT_BACK）：东北角 → 西北角；北上半不动、东上半 → 西上半
        BlockState m = base.mirror(Mirror.FRONT_BACK);
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_WEST, true,
                "X 轴镜像后角应搬到西北");
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_EAST, false,
                "X 轴镜像后东北角应清空");
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_UP, true,
                "X 轴镜像后北上半不动");
        assertPane(helper, m, ModBlockStateProperties.PANE_WEST_UP, true,
                "X 轴镜像后东上半应搬到西上半");
        assertPane(helper, m, ModBlockStateProperties.PANE_EAST_UP, false,
                "X 轴镜像后东上半应清空");

        // Z 轴镜像（LEFT_RIGHT）：东北角 → 东南角；北上半 → 南上半、东上半不动
        m = base.mirror(Mirror.LEFT_RIGHT);
        assertPane(helper, m, ModBlockStateProperties.PANE_SOUTH_EAST, true,
                "Z 轴镜像后角应搬到东南");
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_EAST, false,
                "Z 轴镜像后东北角应清空");
        assertPane(helper, m, ModBlockStateProperties.PANE_SOUTH_UP, true,
                "Z 轴镜像后北上半应搬到南上半");
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_UP, false,
                "Z 轴镜像后北上半应清空");
        assertPane(helper, m, ModBlockStateProperties.PANE_EAST_UP, true,
                "Z 轴镜像后东上半不动");

        // 顺时针 90°：北 → 东、东 → 南：东北角 → 东南角，北上半 → 东上半、东上半 → 南上半
        m = base.rotate(Rotation.CLOCKWISE_90);
        assertPane(helper, m, ModBlockStateProperties.PANE_SOUTH_EAST, true,
                "旋转后角应搬到东南");
        assertPane(helper, m, ModBlockStateProperties.PANE_EAST_UP, true,
                "旋转后北上半应搬到东上半");
        assertPane(helper, m, ModBlockStateProperties.PANE_SOUTH_UP, true,
                "旋转后东上半应搬到南上半");
        assertPane(helper, m, ModBlockStateProperties.PANE_NORTH_UP, false,
                "旋转后北上半应清空");

        helper.succeed();
    }

    // ===== 断言工具 =====

    private static void assertSlab(GameTestHelper helper, BlockState state,
                                   VerticalSlabMode mode, SlabType type, String message) {
        if (!state.hasProperty(ModBlockStateProperties.MODE) || !state.hasProperty(SlabBlock.TYPE)) {
            helper.fail(message + "（状态缺少属性：" + state + "）");
            return;
        }
        if (state.getValue(ModBlockStateProperties.MODE) != mode
                || state.getValue(SlabBlock.TYPE) != type) {
            helper.fail(message + "，期望 " + mode + "+" + type + "，实际 "
                    + state.getValue(ModBlockStateProperties.MODE) + "+"
                    + state.getValue(SlabBlock.TYPE) + "（" + state + "）");
        }
    }

    private static void assertStair(GameTestHelper helper, BlockState state, Direction facing,
                                    StairConnection conn, String message) {
        if (!state.hasProperty(StairBlock.FACING) || !state.hasProperty(StairBlock.SHAPE)) {
            helper.fail(message + "（状态缺少属性：" + state + "）");
            return;
        }
        StairConnection actual = StairConnection.of(state);
        if (state.getValue(StairBlock.FACING) != facing || actual != conn) {
            helper.fail(message + "，期望 facing=" + facing + "+" + conn + "，实际 "
                    + state.getValue(StairBlock.FACING) + "+" + actual + "（" + state + "）");
        }
    }

    private static void assertPane(GameTestHelper helper, BlockState state,
                                   net.minecraft.world.level.block.state.properties.Property<Boolean> property,
                                   boolean expected, String message) {
        if (!state.hasProperty(property)) {
            helper.fail(message + "（状态缺少属性 " + property.getName() + "：" + state + "）");
            return;
        }
        if (state.getValue(property) != expected) {
            helper.fail(message + "，期望 " + property.getName() + "=" + expected
                    + "，实际 " + state.getValue(property) + "（" + state + "）");
        }
    }
}

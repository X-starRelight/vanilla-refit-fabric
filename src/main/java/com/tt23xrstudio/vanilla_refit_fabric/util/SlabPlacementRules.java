package com.tt23xrstudio.vanilla_refit_fabric.util;

import com.tt23xrstudio.vanilla_refit_fabric.config.BtsdhzConfig;
import com.tt23xrstudio.vanilla_refit_fabric.config.Blocklist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * 台阶“竖放还是平放”的判定，放置逻辑（BlockItemMixin）与状态生成
 * （SlabBlockMixin.getStateForPlacement）共用，保证两边结论一致。
 */
public final class SlabPlacementRules {

    private SlabPlacementRules() {
    }

    /**
     * 侧面竖带的分界（面内 u 坐标，与判定框画的两条竖线共用）：
     * 左带 u&lt;1/3、中间带 1/3≤u&lt;2/3（原版平放）、右带 u≥2/3。
     */
    public static final double SIDE_STRIP_EDGE = 1.0D / 3.0D;
    public static final double SIDE_STRIP_EDGE_2 = 2.0D / 3.0D;

    /** 侧面点击是否落在“中间竖带”（原版平放区）；顶/底面不走这里（那是中央正方形判定）。 */
    public static boolean isInSideMiddleStrip(UseOnContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis() == Direction.Axis.Y) {
            return false;
        }
        double u = faceLocalU(context);
        return u >= SIDE_STRIP_EDGE && u < SIDE_STRIP_EDGE_2;
    }

    /**
     * 点击位置在被点方块顶/底面的中央正方形内（原版平放区）。
     * 侧面不用它——侧面是三条竖带判定，见 {@link #isInSideMiddleStrip}。
     */
    public static boolean isInCenterSquare(UseOnContext context) {
        double ratio = centerSquareRatio();
        if (ratio <= 0.0D) {
            return false;
        }
        // 面内相对坐标。顶/底面点击时，放置格(BlockPlaceContext)与被点方块的 x/z 相同，
        // 所以这里直接用 getClickedPos() 即可。
        BlockPos facePos = context.getClickedPos();
        Vec3 loc = context.getClickLocation();
        double fx = loc.x - facePos.getX();
        double fz = loc.z - facePos.getZ();
        double half = ratio / 2.0D;
        return Math.abs(fx - 0.5D) <= half && Math.abs(fz - 0.5D) <= half;
    }

    /** 面内 u 坐标（与判定框共用同一套约定：南北面 u=x、东西面 u=z，见 PlacementGuideRenderer#mapFace）。 */
    private static double faceLocalU(UseOnContext context) {
        Direction face = context.getClickedFace();
        BlockPos origin = context.getClickedPos();
        Vec3 loc = context.getClickLocation();
        return face.getAxis() == Direction.Axis.X ? loc.z - origin.getZ() : loc.x - origin.getX();
    }

    /**
     * 这次点击是否应该放竖半砖。
     *
     * <ul>
     *     <li>原版档：恒不竖放；紧贴档（逻辑 3）：按面决定朝向，也不走这里的竖放分支；</li>
     *     <li>默认档：点顶/底面时，落在中央正方形内不竖放（按原版平放），正方形外竖放。</li>
     * </ul>
     */
    public static boolean wantsVertical(UseOnContext context, PlacementMode mode, Block block) {
        // 名单里被禁用的方块：完全退回原版平放（逃生口，见 config/Blocklist）
        if (block != null && !Blocklist.allows(block)) {
            return false;
        }
        // 原版交给原版；逻辑 3 是“按面决定朝向”，也不走默认档的竖放分支。
        if (mode == PlacementMode.VANILLA || mode == PlacementMode.MOD_3) {
            return false;
        }
        Direction face = context.getClickedFace();
        if (face != Direction.UP && face != Direction.DOWN) {
            return false;
        }
        // 默认档（README「默认」）：中央正方形内按原版平放，正方形外竖放
        return !isInCenterSquare(context);
    }

    /** 逻辑 3 里，某个点击面对应的“紧贴该面”的半砖朝向。 */
    public static Orientation flushAgainst(Direction face) {
        return switch (face) {
            // 点顶面 -> 放在上方那一格，占下半 => 下台阶
            case UP -> new Orientation(VerticalSlabMode.SLAB, SlabType.BOTTOM);
            // 点底面 -> 放在下方那一格，占上半 => 上台阶
            case DOWN -> new Orientation(VerticalSlabMode.SLAB, SlabType.TOP);
            // 点北面 -> 放在北边那一格，占南半 => 南竖台阶
            case NORTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
            // 点南面 -> 放在南边那一格，占北半 => 北竖台阶
            case SOUTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM);
            // 点西面 -> 放在西边那一格，占东半 => 东竖台阶
            case WEST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
            // 点东面 -> 放在东边那一格，占西半 => 西竖台阶
            case EAST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM);
        };
    }

    /**
     * 侧面点击的台阶竖放朝向（三条竖带，与判定框画的两条竖线一一对应）：
     * 左带（u&lt;1/3）=放置格靠面内横轴负端那半、右带（u≥2/3）=正端那半
     * （南北面→西/东半，东西面→北/南半）；中间带是原版平放区，不走这里。
     *
     * <p>上下位置不参与判定（竖带贯穿整个面高）。面内坐标与判定框共用同一套约定：
     * 南北面 u=x、东西面 u=z（见 PlacementGuideRenderer#mapFace）——
     * 这是默认档“侧面放置”增强（参考项目只支持顶/底面）。
     */
    public static Orientation verticalForSideFace(UseOnContext context) {
        Direction face = context.getClickedFace();
        double u = faceLocalU(context);
        boolean negative = u < SIDE_STRIP_EDGE;   // 左带；右带 u ≥ 2/3（中间带由调用方走原版）
        if (face.getAxis() == Direction.Axis.X) {
            // 东西面：u=z，负端=北半、正端=南半
            return negative
                    ? new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM)
                    : new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
        }
        // 南北面：u=x，负端=西半、正端=东半
        return negative
                ? new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM)
                : new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
    }

    /**
     * 侧面放竖楼梯时的朝向（三条竖带，与判定框画的两条竖线一一对应）：
     * L 形中心固定贴墙（被点面的反侧），左/右带决定中心的横向分量
     * （南北面 u=x→西/东，东西面 u=z→北/南），再按 StairBlockMixin#buildLShape
     * 的中心表映射到 FACING（NE→东、SE→南、SW→西、NW→北）。
     * 中间带是原版平放区，不走这里。
     *
     * <p>上下位置不参与判定（竖带贯穿整个面高）——
     * 这是默认档“侧面放置”增强（参考项目只支持顶/底面）。
     */
    public static Direction verticalStairFacingForSideFace(UseOnContext context) {
        Direction face = context.getClickedFace();
        double u = faceLocalU(context);

        // L 形中心的横向分量：左带=面内横轴负端、右带=正端（南北面→西/东，东西面→北/南）
        Direction lateralSide = face.getAxis() == Direction.Axis.X
                ? (u < SIDE_STRIP_EDGE ? Direction.NORTH : Direction.SOUTH)
                : (u < SIDE_STRIP_EDGE ? Direction.WEST : Direction.EAST);
        // 纵深分量固定贴墙（被点面的反侧）
        Direction depthSide = face.getOpposite();

        Direction centerX = lateralSide.getAxis() == Direction.Axis.X ? lateralSide : depthSide;
        Direction centerZ = lateralSide.getAxis() == Direction.Axis.X ? depthSide : lateralSide;
        if (centerZ == Direction.SOUTH) {
            return centerX == Direction.EAST ? Direction.SOUTH : Direction.WEST;
        }
        return centerX == Direction.EAST ? Direction.EAST : Direction.NORTH;
    }

    /** 半砖朝向：形态（平放 / 竖南北 / 竖东西）+ 占哪一半。 */
    public record Orientation(VerticalSlabMode mode, SlabType type) {
    }

    /**
     * 顶面 / 底面点击位置换算出的水平朝向（竖放时决定是“竖南北”还是“竖东西”、以及占哪一半）。
     *
     * <p>两个偏移量是在**世界坐标**（动辄上千万）上做减法得到的，浮点消去误差约 1e-9；
     * 恰好点在对角线上（|Δx| == |Δz|）时，这点误差的正负取决于本块在世界里的坐标，
     * 于是同一个“相对点击点”在不同坐标下会得出不同朝向 —— 表现就是测试跟着结构在世界里的位置漂移而翻转。
     *
     * <p>这里给一个 1e-6 的平局容差：它远小于面内的最小像素步长（1/16 = 0.0625），
     * 不会改变任何真实点击的判定；平局时按数学上的 {@code |x| <= |z|} 走南北分支，
     * 与原本 {@code |x| > |z| → 东西、否则 → 南北} 的语义一致，只是不再受浮点噪声左右。
     *
     * <p>{@code BlockItemMixin}（放置逻辑）与 {@code SlabBlockMixin}（状态生成）共用这一个实现，
     * 保证两边结论永远一致。
     */
    public static Direction horizontalDirectionFromClick(UseOnContext context) {
        double x = context.getClickLocation().x - context.getClickedPos().getX() - 0.5;
        double z = context.getClickLocation().z - context.getClickedPos().getZ() - 0.5;
        if (Math.abs(x) > Math.abs(z) + 1.0E-6D) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        }
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** 配置里的中央正方形边长比例；读取失败时按默认 0.5 处理。 */
    public static double centerSquareRatio() {
        try {
            return BtsdhzConfig.get().slabCenterSquareRatio;
        } catch (RuntimeException exception) {
            return 0.5D;
        }
    }
}

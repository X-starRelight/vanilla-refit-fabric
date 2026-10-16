package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementModeState;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabPlacementRules;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 手持台阶/楼梯时，在准星对着的方块面上额外画“放置区域”提示线
 * （移植自源项目的 {@code client/PlacementGuideRenderer}，NeoForge 版挂在
 * {@code RenderHighlightEvent.Block}，26.2 Fabric 版改挂
 * {@link LevelRenderEvents#BEFORE_BLOCK_OUTLINE} + {@link LevelExtractionEvents}）。
 *
 * <p>本模组的竖台阶/竖楼梯是“看着方块面点上去就判定成竖直版”，判定只看点击落在面内的位置，
 * 原版那圈形状外框看不出会被切成哪几块，所以这里把切分线画出来：
 * <ul>
 *     <li>默认档水平面（顶/底）：台阶画两条对角线（X，四个三角形空位决定竖放朝向）、
 *         楼梯画十字（+，四个象限决定 FACING）；</li>
 *     <li>面中央的正方形是“原版平放区”：分割线在正方形处截断，正方形边框本身也画出来
 *         （正方形占比见配置 {@code slabCenterSquareRatio}）；</li>
 *     <li>侧面：上下等分的那条中线；</li>
 * </ul>
 *
 * <p><b>判定看的是“这个面是不是完整面”，不是“方块是不是整方块”。</b>做法与源项目一致：
 * 取命中方向那一侧的形状极值平面，把该平面上的整个单位正方形与方块形状求差（ONLY_FIRST），
 * 差集为空才算完整面；平面坐标严格落在方块内部（0.5）的面是台阶“朝空余半砖空间”的内侧面，
 * 点它是合并成整块，不画线。
 *
 * <p>与源项目的实现差异（26.2 渲染管线适配）：
 * <ul>
 *     <li>26.2 没有 PoseStack+MultiBufferSource 的高亮事件，改为在轮廓提取阶段暂存本次命中的
 *         {@link BlockHitResult}（用原版自己算的那份，保证与轮廓同帧同目标），再在提交阶段画线；</li>
 *     <li>画线走 {@code SubmitNodeCollector.submitCustomGeometry}，顶点写法与 26.2 原版
 *         {@code ShapeOutlineFeatureRenderer} 完全一致（addVertex/setColor/setNormal/setLineWidth）；</li>
 *     <li>形状直接取自轮廓渲染状态 {@link BlockOutlineRenderState#shape()}（与原版外框同源，
 *         省掉再次取形状，也不可能再抛异常）；</li>
 *     <li>线宽取原版外框同款 {@code appropriateLineWidth}，顶点色与原版一致 (0,0,0,0.4)。</li>
 * </ul>
 *
 * <p>只额外画线，返回 true 让原版外框仍然照常绘制（返回 false 会取消原版外框）。
 */
public final class PlacementGuideRenderer {

    /**
     * 与原版高亮外框完全一致的顶点色（{@code LevelRenderer#submitBlockOutline} 里
     * 非高对比度时为 {@code ARGB.black(102)}，即 0,0,0,0.4）。
     */
    private static final float R = 0.0F;
    private static final float G = 0.0F;
    private static final float B = 0.0F;
    private static final float A = 0.4F;

    /** 用来把“整张面”做成一片极薄的形状再做差集；取 1/1000 格，远小于任何具体形状。 */
    private static final double FACE_EPS = 1.0E-3;

    /** 台阶：两条对角线（X 分割），单位 [0,1]。 */
    private static final float[][] SLAB_EDGES = {
            {0F, 0F, 1F, 1F},
            {1F, 0F, 0F, 1F},
    };

    /** 楼梯：十字分割（+），单位 [0,1]。 */
    private static final float[][] STAIR_EDGES = {
            {0.5F, 0F, 0.5F, 1F},
            {0F, 0.5F, 1F, 0.5F},
    };

    /** 侧面：上下等分的中线，单位 [0,1]（映射到该面的两个轴）。 */
    private static final float[][] SIDE_EDGES = {
            {0F, 0.5F, 1F, 0.5F},
    };

    /**
     * 默认档用的三组线，按配置比例缓存（配置值不变时不重复构造数组）：
     * 中央正方形的四条边，以及“在正方形处截断”的对角线（台阶）与十字（楼梯）。
     */
    private static double cachedSquareRatio = -1.0D;
    private static float[][] cachedSquare = new float[0][];
    private static float[][] cachedTruncatedDiagonals = SLAB_EDGES;
    private static float[][] cachedTruncatedCross = STAIR_EDGES;

    /**
     * 本次帧轮廓提取阶段记录的命中（原版自己算的那份，和轮廓渲染状态同源）。
     * 只在渲染线程读写，无需同步；离开世界后残留的旧值会被 pos 一致性检查挡掉。
     */
    private static BlockHitResult lastHit;

    private PlacementGuideRenderer() {
    }

    /** 客户端初始化时调用一次：注册轮廓提取与轮廓提交两个事件。 */
    public static void register() {
        // 提取阶段：记住原版这次画轮廓用的命中结果（MISS 时清空）
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hitResult) ->
                lastHit = hitResult instanceof BlockHitResult blockHit ? blockHit : null);

        // 提交阶段：原版轮廓提交之前插入我们的提示线；返回 true 表示原版外框照常画
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(PlacementGuideRenderer::onBeforeBlockOutline);
    }

    /** @return true 继续绘制原版外框（本渲染器只加线，永不取消原版外框）。 */
    private static boolean onBeforeBlockOutline(LevelRenderContext context, BlockOutlineRenderState outline) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || lastHit == null) {
            return true;
        }

        BlockHitResult hit = lastHit;
        // 与轮廓渲染状态对不上（跨世界残留等）就不画，避免错位闪线
        if (!hit.getBlockPos().equals(outline.pos())) {
            return true;
        }

        ItemStack held = mc.player.getMainHandItem();
        boolean slab = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
        boolean stair = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StairBlock;
        if (!slab && !stair) {
            return true;
        }
        // 原版放置逻辑不画提示线；台阶与楼梯各自成套，互不影响。
        PlacementMode mode = slab ? PlacementModeState.slab(mc.player) : PlacementModeState.stair(mc.player);
        // 原版逻辑与逻辑 3（紧贴面放置）都不需要辅助线。
        if (mode == PlacementMode.VANILLA || mode == PlacementMode.MOD_3) {
            return true;
        }

        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();

        // 只有准星足够近才画，避免远处方块也带线（与源项目相同的 +1 格余量）。
        double reach = mc.player.blockInteractionRange() + 1.0D;
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return true;
        }

        // 渲染形状与原版外框同源（就是轮廓状态里那份），玩家看到的面和它一致。
        VoxelShape shape = outline.shape();
        if (shape.isEmpty()) {
            return true;
        }

        // 命中方向那一侧的形状极值平面：顶面 maxY、底面 minY、北面 minZ、南面 maxZ……
        // 这一点很关键——下台阶的顶面在 y=0.5 而不是 1.0，直接按方块边界画就会浮空。
        Direction.Axis axis = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(axis)
                : shape.min(axis);

        if (!isFaceComplete(shape, face, plane)) {
            return true;
        }

        // 台阶靠内那面（朝空余半砖空间）点击是合并成整块半砖，不是紧邻放置，不画提示线。
        // 平面严格落在方块内部即为此类面；外侧的面都在 0 或 1。
        if (mc.level.getBlockState(pos).getBlock() instanceof SlabBlock
                && plane > 1.0E-6 && plane < 1.0 - 1.0E-6) {
            return true;
        }

        boolean horizontalFace = face == Direction.UP || face == Direction.DOWN;

        // 选好要画的线组：默认档的水平面画“被中央正方形截断”的分割线
        // （台阶为对角线、楼梯为十字）加上正方形边框；侧面画中线。
        float[][] first;
        float[][] second = null;
        if (horizontalFace) {
            updateCenterEdges();
            first = slab ? cachedTruncatedDiagonals : cachedTruncatedCross;
            second = cachedSquare;
        } else {
            first = SIDE_EDGES;
        }

        // 与原版外框相同的坐标基准：pose 是 submitFeatures 的恒等 pose，
        // 平移到“方块角 - 相机位置”，线段坐标用方块局部 0~1。
        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        // 线宽与原版外框同源（26.2 起线宽是逐顶点属性）。
        float lineWidth = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        RenderType lines = RenderTypes.lines();
        float[][] secondEdges = second;
        context.submitNodeCollector().submitCustomGeometry(poseStack, lines, (pose, buffer) -> {
            drawEdges(buffer, pose, face, plane, first, lineWidth);
            if (secondEdges != null) {
                drawEdges(buffer, pose, face, plane, secondEdges, lineWidth);
            }
        });

        poseStack.popPose();
        return true;
    }

    private static void drawEdges(VertexConsumer consumer, PoseStack.Pose pose, Direction face, double plane,
                                  float[][] edges, float lineWidth) {
        for (float[] edge : edges) {
            addEdge(consumer, pose, face, plane, edge[0], edge[1], edge[2], edge[3], lineWidth);
        }
    }

    /** 按当前配置比例（重新）构造默认档的三组线：中央正方形、截断对角线、截断十字。 */
    private static void updateCenterEdges() {
        double ratio = SlabPlacementRules.centerSquareRatio();
        if (ratio == cachedSquareRatio) {
            return;
        }
        cachedSquareRatio = ratio;

        if (ratio <= 0.0D) {
            // 没有正方形：退化成完整的对角线/十字。
            cachedSquare = new float[0][];
            cachedTruncatedDiagonals = SLAB_EDGES;
            cachedTruncatedCross = STAIR_EDGES;
            return;
        }
        if (ratio >= 1.0D) {
            // 正方形铺满整面：分割线全部落在正方形内，没有要画的线段；
            // 正方形边框和方块外框重合也不必画。
            cachedSquare = new float[0][];
            cachedTruncatedDiagonals = new float[0][];
            cachedTruncatedCross = new float[0][];
            return;
        }

        float min = (float) (0.5D - ratio / 2.0D);
        float max = (float) (0.5D + ratio / 2.0D);
        cachedSquare = new float[][]{
                {min, min, max, min},
                {max, min, max, max},
                {max, max, min, max},
                {min, max, min, min},
        };
        // 两条对角线各被正方形切成两段，只画正方形之外的那四段。
        cachedTruncatedDiagonals = new float[][]{
                {0F, 0F, min, min},   // 主对角线：左上角 -> 正方形左上角
                {max, max, 1F, 1F},   // 主对角线：正方形右下角 -> 右下角
                {1F, 0F, max, min},   // 副对角线：右上角 -> 正方形右上角
                {min, max, 0F, 1F},   // 副对角线：正方形左下角 -> 左下角
        };
        // 十字的竖线、横线也各被正方形切成两段，同样只画正方形之外的那四段。
        cachedTruncatedCross = new float[][]{
                {0.5F, 0F, 0.5F, min},  // 竖线上段：上边中点 -> 正方形上边
                {0.5F, max, 0.5F, 1F},  // 竖线下段：正方形下边 -> 下边中点
                {0F, 0.5F, min, 0.5F},  // 横线左段：左边中点 -> 正方形左边
                {max, 0.5F, 1F, 0.5F},  // 横线右段：正方形右边 -> 右边中点
        };
    }

    /**
     * 该面是不是完整面：把平面上的整个单位正方形做成一片极薄的形状，减去方块形状后为空，
     * 就说明整张面都被方块覆盖（也就是完整面）。
     *
     * <p>注意必须用 {@link BooleanOp#ONLY_FIRST}（“A 减 B”，A 是被减数）——这里 A 是那张整面。
     * 判断整方块时不能拿方块形状当被减数，否则几乎所有方块都会被当成整方块。
     */
    private static boolean isFaceComplete(VoxelShape shape, Direction face, double plane) {
        // 薄片必须朝方块“内部”长（正方向的面往负方向让、负方向的面往正方向让）。
        // 按面名硬编码时南北/东西不能写反：北面若写成 z 从 -0.001 到 0，就落在方块外面，
        // 于是四个侧面全被判成不完整，只有顶/底能画。
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double from = positive ? plane - FACE_EPS : plane;
        double to = positive ? plane : plane + FACE_EPS;
        VoxelShape fullFace = switch (face.getAxis()) {
            case X -> Shapes.box(from, 0.0, 0.0, to, 1.0, 1.0);
            case Y -> Shapes.box(0.0, from, 0.0, 1.0, to, 1.0);
            case Z -> Shapes.box(0.0, 0.0, from, 1.0, 1.0, to);
        };
        return !Shapes.joinIsNotEmpty(fullFace, shape, BooleanOp.ONLY_FIRST);
    }

    /**
     * 画一条边。输入是面内的两个 2D 坐标（0~1），按面朝向映射到方块局部 3D 坐标，
     * 并用命中面的实际平面坐标（不一定是 0/1，比如下台阶顶面是 0.5）。
     * 顶/底：u=x, v=z；南北侧：u=x, v=y；东西侧：u=z, v=y。
     */
    private static void addEdge(VertexConsumer consumer, PoseStack.Pose pose, Direction face, double plane,
                                float u1, float v1, float u2, float v2, float lineWidth) {
        float[] a = mapFace(face, plane, u1, v1);
        float[] b = mapFace(face, plane, u2, v2);
        // 法线取“沿线方向归一化”，和原版 renderShape 一致。
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-6F) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        addVertex(consumer, pose, a, dx, dy, dz, lineWidth);
        addVertex(consumer, pose, b, dx, dy, dz, lineWidth);
    }

    private static float[] mapFace(Direction face, double plane, float u, float v) {
        float p = (float) plane;
        return switch (face) {
            case UP, DOWN -> new float[]{u, p, v};
            case NORTH, SOUTH -> new float[]{u, v, p};
            case WEST, EAST -> new float[]{p, v, u};
        };
    }

    /**
     * 顶点写法与 26.2 原版 {@code ShapeOutlineFeatureRenderer} 一致：
     * addVertex(pose) → setColor → setNormal(pose) → setLineWidth。
     * 线宽由调用方通过 {@code lineWidth} 参数提供（与原版外框同源）。
     */
    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p,
                                  float nx, float ny, float nz, float lineWidth) {
        consumer.addVertex(pose, p[0], p[1], p[2])
                .setColor(R, G, B, A)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(lineWidth);
    }
}

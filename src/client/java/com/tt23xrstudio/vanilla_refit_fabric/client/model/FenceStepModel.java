package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import com.tt23xrstudio.vanilla_refit_fabric.util.SlabSupport;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

/**
 * 栅栏"台阶侧"横杆修正模型。（参考端 {@code FenceStepModel} 的 26.2/FRAPI 移植。）
 *
 * <p>背景：原版栅栏的两根横杆在 y=6~9 与 12~15（间距 6/16 格），而贴台阶的位移是 8/16 格，
 * 所以高低不同的两根栅栏，横杆必然错 2/16 格、怎么摆都对不齐。
 *
 * <p>修正规则分两类关节（只改"台阶侧"的横杆；位移量都是下横杆下移 2/16 格并改用上横杆那一段贴图）：
 * <ul>
 *     <li><b>下移关节</b>（两根里有一根是"下台阶上的栅栏"）：我更高 → 只保留下横杆并下移 2/16；
 *         我更低 → 下横杆下移 2/16、上横杆保留。</li>
 *     <li><b>上移关节</b>（两根里有一根是"上台阶下的栅栏"）：我更高 → 下横杆下移 2/16、上横杆保留；
 *         我更低 → 只保留上横杆（下横杆不画、也不位移）。</li>
 * </ul>
 * 效果都是：我下移后的下横杆与对方（未位移的）上横杆落在同一世界高度，跨台阶处接成一根横杆，
 * 多余的那根就不画，避免出现半截横杆。
 *
 * <p>"哪一侧是台阶侧、我是高的一侧还是低的一侧"要看邻居，属于按位置变化的信息；塞进方块状态会让
 * 状态量爆炸。参考端借用 NeoForge 的 {@code getModelData}，而 Fabric 26.2 的
 * {@code emitQuads} 直接带 world + pos，这里在渲染时现算。
 *
 * <p>嵌套约定：本模型作为<b>内层</b>包装（外层是 {@link SlabbedLoweringModel}/
 * {@link SlabbedRaisingModel}）。FRAPI transform 按 LIFO 应用——后压入的先执行，
 * 所以横杆分类看到的是未位移的原版坐标，与参考端的判定范围一致。
 *
 * <p>UV 行位移的换算：参考端按"sprite 图集跨度 × 6/16"换算（烘焙 quad 存的是图集坐标），
 * 但 26.2 的 {@code QuadView} 不暴露 sprite。改为从 quad 自身推导：下横杆侧面恰好占贴图
 * 3/16 行（v=7~10），要挪 6/16 行，正好是该 quad v 跨度的 -2 倍——与参考端数值完全等价，
 * 且不依赖图集分辨率。
 */
public final class FenceStepModel extends WrapperBlockStateModel {

    /** 与 {@link #computeStepPattern} 中的顺序一致：北、东、南、西。 */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final float EPS = 1.0F / 32.0F;
    /** 横杆下移量：2/16 格。 */
    private static final float BAR_SHIFT = -2.0F / 16.0F;
    /** 下横杆 y 6~9、上横杆 y 12~15。 */
    private static final float LOWER_MIN = 6.0F / 16.0F - EPS;
    private static final float LOWER_MAX = 9.0F / 16.0F + EPS;
    private static final float UPPER_MIN = 12.0F / 16.0F - EPS;
    private static final float UPPER_MAX = 15.0F / 16.0F + EPS;
    /** 横杆厚度（另一个水平轴）7~9。 */
    private static final float BAR_THICK_MIN = 7.0F / 16.0F - EPS;
    private static final float BAR_THICK_MAX = 9.0F / 16.0F + EPS;
    /** 半根横杆的远端：北/西到 z/x=9/16，南/东从 7/16 起。 */
    private static final float HALF_END = 9.0F / 16.0F + EPS;
    private static final float HALF_START = 7.0F / 16.0F - EPS;

    public FenceStepModel(BlockStateModel originalModel) {
        super(originalModel);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        if (level == null || level == BlockAndTintGetter.EMPTY) {
            // 无世界上下文（物品栏、破坏覆盖层等）：保持原版外观
            wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        int pattern = computeStepPattern(level, pos, state);
        if (pattern == 0) {
            wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        emitter.pushTransform(quad -> adjustBar(quad, pattern));
        wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
        emitter.popTransform();
    }

    /**
     * 算出这一格四个水平方向的横杆动作（0=不动，1=去上横杆+下横杆下移，2=下横杆下移，3=去下横杆）。
     *
     * <p>先判断关节类型：两根里有一根是"下台阶上的栅栏"算下移关节，否则（有一根是"上台阶下的栅栏"）
     * 算上移关节；两类关节的规则互为镜像。再比底面高度决定我是高的一侧还是低的一侧。
     * 比较用的是栅栏底面（横杆起点）的世界高度——跨台阶连接的两根栅栏并不在同一行。
     *
     * <p>先看同一行的那一侧；那里不是栅栏时再看斜上方/斜下方（跨台阶连接的两根栅栏就是这样斜着相邻的）。
     * 区块渲染区域只覆盖 3×3 区块，越界查询会抛异常，所以整体兜底：拿不到就当作没有台阶侧（保持原版外观）。
     */
    private static int computeStepPattern(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        try {
            double selfHeight = fenceBaseHeight(state, pos.getY());
            int selfDisplacement = displacement(state);
            int pattern = 0;
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction dir = HORIZONTAL[i];
                BlockPos sameRow = pos.relative(dir);
                BlockPos neighbourPos = findFence(level, sameRow, sameRow.above(), sameRow.below());
                if (neighbourPos == null) {
                    continue;
                }
                BlockState neighbour = level.getBlockState(neighbourPos);
                int neighbourDisplacement = displacement(neighbour);
                if (selfDisplacement == 0 && neighbourDisplacement == 0) {
                    // 两根都没贴台阶：这是原版本身就存在的高低差（比如栅栏沿楼梯逐格摆放），
                    // 原版并不会把它们接起来，这种情况也不去改横杆，保持原版外观。
                    continue;
                }
                if (selfDisplacement != 0 && neighbourDisplacement != 0) {
                    // 两根都有位移（差一整格）：这套 2/16 的规则不适用，保持原版
                    continue;
                }
                double neighbourHeight = fenceBaseHeight(neighbour, neighbourPos.getY());
                boolean selfHigher = neighbourHeight < selfHeight - 1.0E-4;
                boolean selfLower = neighbourHeight > selfHeight + 1.0E-4;
                if (!selfHigher && !selfLower) {
                    continue;
                }
                boolean loweredJoint = selfDisplacement < 0 || neighbourDisplacement < 0;
                int action = loweredJoint
                        ? (selfHigher ? 1 : 2)
                        : (selfHigher ? 2 : 3);
                pattern |= action << (i * 2);
            }
            return pattern;
        } catch (RuntimeException e) {
            // 越界（区块渲染区域边缘）等情况：退回原版外观
            return 0;
        }
    }

    /** 在同一行、斜上方、斜下方三个位置里找第一个栅栏。 */
    private static BlockPos findFence(BlockAndTintGetter level, BlockPos sameRow, BlockPos above, BlockPos below) {
        if (level.getBlockState(sameRow).is(BlockTags.FENCES)) {
            return sameRow;
        }
        if (level.getBlockState(above).is(BlockTags.FENCES)) {
            return above;
        }
        if (level.getBlockState(below).is(BlockTags.FENCES)) {
            return below;
        }
        return null;
    }

    /** 栅栏底面（横杆起点）的世界高度：上台阶下的栅栏高半格，下台阶上的栅栏低半格，其余按格子。 */
    private static double fenceBaseHeight(BlockState state, int y) {
        if (SlabSupport.isOnSlab(state)) {
            return y - 0.5;
        }
        return SlabSupport.isUnderTopSlab(state) ? y + 0.5 : y;
    }

    /** -1=下台阶上的栅栏（整体下移半格），0=普通，+1=上台阶下的栅栏（整体上移半格）。 */
    private static int displacement(BlockState state) {
        if (SlabSupport.isOnSlab(state)) {
            return -1;
        }
        return SlabSupport.isUnderTopSlab(state) ? 1 : 0;
    }

    /** 按动作改横杆：返回 false 丢弃该 quad；返回 true 保留（可能已修改顶点/UV）。 */
    private static boolean adjustBar(net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView quad,
                                     int pattern) {
        Bar bar = classifyBar(quad);
        if (bar == null) {
            return true;
        }
        int action = (pattern >> (bar.direction() * 2)) & 3;
        return switch (action) {
            case 1 -> {
                if (bar.upper()) {
                    yield false;   // 去上横杆
                }
                shiftDown(quad);
                yield true;
            }
            case 2 -> {
                if (bar.upper()) {
                    yield true;    // 上横杆保留
                }
                shiftDown(quad);
                yield true;
            }
            case 3 -> !bar.upper(); // 去下横杆（下横杆不画也不位移）
            default -> true;
        };
    }

    /** 判断这个 quad 属于哪一侧的横杆（上/下）。不属于任何横杆返回 null。 */
    private static Bar classifyBar(net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView quad) {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float x = quad.x(i);
            float y = quad.y(i);
            float z = quad.z(i);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        boolean lower = minY >= LOWER_MIN && maxY <= LOWER_MAX;
        boolean upper = minY >= UPPER_MIN && maxY <= UPPER_MAX;
        if (!lower && !upper) {
            return null;
        }
        boolean thickX = minX >= BAR_THICK_MIN && maxX <= BAR_THICK_MAX;
        boolean thickZ = minZ >= BAR_THICK_MIN && maxZ <= BAR_THICK_MAX;
        if (thickX) {
            if (maxZ <= HALF_END) {
                return new Bar(0, upper);           // 北
            }
            if (minZ >= HALF_START) {
                return new Bar(2, upper);           // 南
            }
        }
        if (thickZ) {
            if (maxX <= HALF_END) {
                return new Bar(3, upper);           // 西
            }
            if (minX >= HALF_START) {
                return new Bar(1, upper);           // 东
            }
        }
        return null;
    }

    /**
     * 顶点整体下移 2/16 格，并把这根下横杆的贴图换成上横杆那一段（只对侧面，含端头）。
     *
     * <p>只改位置不动贴图的话，位移后的横杆贴图会和它对接的上横杆对不上（会看到一条贴图接缝）；
     * 侧面把贴图行往上挪 6/16 正好落到上横杆那一段——从 quad 自身 v 跨度推导（占 3/16 行，
     * 挪 6/16 行 = -2 倍跨度，见类注释），上/下两个面两根横杆用的本来就是同一段 uv，所以不动。
     */
    private static void shiftDown(net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView quad) {
        boolean sideFace = quad.lightFace().getAxis().isHorizontal();
        float atlasVShift = 0.0F;
        if (sideFace) {
            float vMin = Float.MAX_VALUE;
            float vMax = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float v = quad.v(i);
                vMin = Math.min(vMin, v);
                vMax = Math.max(vMax, v);
            }
            atlasVShift = -2.0F * (vMax - vMin);
        }
        for (int i = 0; i < 4; i++) {
            quad.pos(i, quad.x(i), quad.y(i) + BAR_SHIFT, quad.z(i));
            if (sideFace) {
                quad.uv(i, quad.u(i), quad.v(i) + atlasVShift);
            }
        }
    }

    /** 横杆所属方向（{@link #HORIZONTAL} 下标）与是不是上横杆。 */
    private record Bar(int direction, boolean upper) {
    }
}

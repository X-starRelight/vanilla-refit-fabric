package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import com.tt23xrstudio.vanilla_refit_fabric.block.entity.MixedSlabBlockEntity;
import com.tt23xrstudio.vanilla_refit_fabric.util.FluidHolder;
import com.tt23xrstudio.vanilla_refit_fabric.util.FluidType;
import com.tt23xrstudio.vanilla_refit_fabric.util.ModBlockStateProperties;
import com.tt23xrstudio.vanilla_refit_fabric.util.VerticalSlabMode;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

/**
 * 混合半砖的客户端模型：从方块实体读取两块半砖材质，按其朝向动态合并渲染。
 * （参考端 {@code MixedSlabModel} 的 26.2/FRAPI 移植。）
 *
 * <p>之所以要运行时合并而不是烘焙成静态模型，是因为“哪两种材质组合”千变万化，无法预烘焙。
 * Fabric 26.2 的 {@code emitQuads} 直接拿到 level + pos，方块实体现场可取，
 * 不需要 NeoForge 的 ModelData 层；仍走原版区块烘焙管线，性能与参考版一致。
 *
 * <p>NeoForge 版要按 renderType 过滤“哪半块进哪一层”；Fabric 的 quad 自带
 * {@code chunkLayer}，渲染器逐 quad 落层，两半几何直接全部输出即可。
 */
public final class MixedSlabModel extends WrapperBlockStateModel {

    public MixedSlabModel(BlockStateModel originalModel) {
        super(originalModel);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        MixedSlabBlockEntity mixed = entityOf(level, pos);
        if (mixed == null || !state.hasProperty(ModBlockStateProperties.MODE)
                || !state.hasProperty(SlabBlock.TYPE)) {
            // 拿不到方块实体（物品栏/展示框等空 level 路径）：退回默认模型（石头占位）
            wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        BlockStateModelSet models = modelSet();
        if (models == null) {
            wrapped.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }

        BlockState stateA = buildHalfState(mixed.getSlabA(), state, true);
        BlockState stateB = buildHalfState(mixed.getSlabB(), state, false);
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        // 两块半砖相接处的“内部面”不应渲染，否则会看到砖内部的接缝面
        Direction internalA = internalFace(mode, true);
        Direction internalB = internalFace(mode, false);

        long seed = state.getSeed(pos);
        random.setSeed(seed);
        emitter.pushTransform(quad -> quad.lightFace() != internalA);
        models.get(stateA).emitQuads(emitter, level, pos, stateA, random, cullTest);
        emitter.popTransform();

        random.setSeed(seed);
        emitter.pushTransform(quad -> quad.lightFace() != internalB);
        models.get(stateB).emitQuads(emitter, level, pos, stateB, random, cullTest);
        emitter.popTransform();
    }

    /**
     * 混合半砖中，某块半砖“朝向另一块半砖”的内部面方向。该方向上的面在合并后应被剔除：
     * <ul>
     *   <li>MODE=SLAB：A=下半(内部朝上 UP)，B=上半(内部朝下 DOWN)</li>
     *   <li>MODE=VERTICAL_NS：A=北半(内部朝南 SOUTH)，B=南半(内部朝北 NORTH)</li>
     *   <li>MODE=VERTICAL_EW：A=西半(内部朝东 EAST)，B=东半(内部朝西 WEST)</li>
     * </ul>
     */
    private static Direction internalFace(VerticalSlabMode mode, boolean isFirst) {
        return switch (mode) {
            case SLAB -> isFirst ? Direction.UP : Direction.DOWN;
            case VERTICAL_NS -> isFirst ? Direction.SOUTH : Direction.NORTH;
            case VERTICAL_EW -> isFirst ? Direction.EAST : Direction.WEST;
        };
    }

    /**
     * 把一个材质 Block 转成“在混合半砖里应该呈现的那一半”的 BlockState。
     * 朝向由混合半砖状态里的 MODE / TYPE 决定（照抄参考版语义）：
     * <ul>
     *   <li>MODE=SLAB：A=下半(BOTTOM)，B=上半(TOP)</li>
     *   <li>竖直拼合：A=北/西半(TYPE=BOTTOM)，B=南/东半(TYPE=TOP)，流体按混合砖的流体属性带上</li>
     * </ul>
     */
    private static BlockState buildHalfState(Block slab, BlockState mixedState, boolean isFirst) {
        VerticalSlabMode mode = mixedState.getValue(ModBlockStateProperties.MODE);
        FluidType fluid = mixedState.getValue(ModBlockStateProperties.FLUID_TYPE);

        BlockState base = slab.defaultBlockState();
        if (mode == VerticalSlabMode.SLAB) {
            SlabType half = isFirst ? SlabType.BOTTOM : SlabType.TOP;
            if (base.hasProperty(SlabBlock.TYPE)) {
                return base.setValue(SlabBlock.TYPE, half);
            }
            return base;
        }

        if (base.hasProperty(ModBlockStateProperties.MODE)) {
            return FluidHolder.with(base.setValue(ModBlockStateProperties.MODE, mode)
                    .setValue(SlabBlock.TYPE, isFirst ? SlabType.BOTTOM : SlabType.TOP), fluid);
        }
        return base;
    }

    /**
     * 拆除碎屑的贴图：默认是组合半砖自己的默认贴图（石头），这里从两块半砖里随机挑一块的
     * 粒子材质，于是碎屑会两种材质混着出。
     *
     * <p>原版拆除粒子每颗都带位置重新问一次贴图（{@code TerrainParticle} →
     * {@code BlockStateModelSet#getParticleMaterial(state, level, pos)}），所以逐颗随机有效；
     * 拿不到位置的调用退回默认材质。
     */
    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        MixedSlabBlockEntity mixed = entityOf(level, pos);
        BlockStateModelSet models = mixed != null ? modelSet() : null;
        if (mixed != null && models != null) {
            Block pick = ThreadLocalRandom.current().nextBoolean() ? mixed.getSlabA() : mixed.getSlabB();
            return models.get(pick.defaultBlockState()).particleMaterial();
        }
        return wrapped.particleMaterial(level, pos, state);
    }

    /**
     * 无上下文版本必须对所有上下文“保守正确”（官方文档明确允许过度上报）：
     * 默认模型标记 | 半透明 | 动画——两块半砖材质要到渲染时才知道，宁可多报。
     */
    @Override
    public int materialFlags() {
        return wrapped.materialFlags() | BakedQuad.FLAG_TRANSLUCENT | BakedQuad.FLAG_ANIMATED;
    }

    /** 有方块实体时按两块半砖的模型精确求或。 */
    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        MixedSlabBlockEntity mixed = entityOf(level, pos);
        BlockStateModelSet models = mixed != null ? modelSet() : null;
        if (mixed != null && models != null && state.hasProperty(ModBlockStateProperties.MODE)
                && state.hasProperty(SlabBlock.TYPE)) {
            BlockState stateA = buildHalfState(mixed.getSlabA(), state, true);
            BlockState stateB = buildHalfState(mixed.getSlabB(), state, false);
            return models.get(stateA).materialFlags(level, pos, stateA, random)
                    | models.get(stateB).materialFlags(level, pos, stateB, random);
        }
        return wrapped.materialFlags(level, pos, state, random);
    }

    /** 几何键 = 状态朝向 + 两块材质（材质变化即几何变化，区块重建缓存按它区分）。 */
    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        MixedSlabBlockEntity mixed = entityOf(level, pos);
        if (mixed == null || !state.hasProperty(ModBlockStateProperties.MODE)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return null;
        }
        return new MixedGeometryKey(state.getValue(ModBlockStateProperties.MODE),
                state.getValue(SlabBlock.TYPE), mixed.getSlabA(), mixed.getSlabB());
    }

    private record MixedGeometryKey(VerticalSlabMode mode, SlabType type, Block slabA, Block slabB) {
    }

    private static MixedSlabBlockEntity entityOf(BlockAndTintGetter level, BlockPos pos) {
        if (level == null || level == BlockAndTintGetter.EMPTY) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MixedSlabBlockEntity mixed ? mixed : null;
    }

    private static BlockStateModelSet modelSet() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getModelManager() == null) {
            return null;
        }
        return minecraft.getModelManager().getBlockStateModelSet();
    }
}

package com.tt23xrstudio.vanilla_refit_fabric.client.model;

import java.util.function.Predicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

/**
 * 把被包装方块模型的四边顶点整体<b>上移半格</b>的模型包装器。
 * （参考端 {@code SlabbedRaisingModel} 的 26.2/FRAPI 移植。）
 *
 * <p>用于"贴在上台阶（上半台阶）下方的"灯笼/栅栏/墙（{@code btsdhz_slab_offset=raised}）：
 * 视觉上移，贴齐上台阶的底面。
 *
 * <p><b>剔除救援</b>（{@link SlabbedLoweringModel} 的镜像）：整体上移后，原本贴在方块
 * 下边界、带 {@code cullface: down} 的那组面升到半格高度，不再与下方方块紧贴，
 * 会被渲染器按"下方有方块"剔除而缺面，这里强制放出这一组面。上组面（顶到上台阶底面）
 * 保留剔除。
 */
public final class SlabbedRaisingModel extends WrapperBlockStateModel {

    private static final float SHIFT_Y = 0.5F;

    /** 位移后不再与下方方块贴面、需要强制渲染的那组面（原 {@code cullface: down}）。 */
    private static final Direction RESCUE = Direction.DOWN;

    public SlabbedRaisingModel(BlockStateModel originalModel) {
        super(originalModel);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        emitter.pushTransform(quad -> {
            for (int i = 0; i < 4; i++) {
                quad.pos(i, quad.x(i), quad.y(i) + SHIFT_Y, quad.z(i));
            }
            if (quad.cullFace() == RESCUE) {
                quad.cullFace(null);
            }
            return true;
        });
        wrapped.emitQuads(emitter, level, pos, state, random,
                dir -> dir != RESCUE && cullTest.test(dir));
        emitter.popTransform();
    }
}

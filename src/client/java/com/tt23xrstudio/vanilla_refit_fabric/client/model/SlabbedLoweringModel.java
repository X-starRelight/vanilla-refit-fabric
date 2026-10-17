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
 * 把被包装方块模型的四边顶点整体<b>下移半格</b>的模型包装器。
 * （参考端 {@code SlabbedLoweringModel} 的 26.2/FRAPI 移植。）
 *
 * <p>用于"贴在下台阶上的"火把/灯笼/栅栏/墙（{@code btsdhz_on_slab=true} 或
 * {@code btsdhz_slab_offset=lowered}）：视觉下移，贴齐下台阶的上表面。
 * 模型按方块状态烘焙一次后包装，运行期只是几条顶点平移，不涉及每帧额外计算。
 *
 * <p><b>剔除救援</b>：整体下移后，原本贴在方块上边界、带 {@code cullface: up} 的那组面
 * （栅栏/墙立柱的顶面等）已降到半格高度，不再与上方方块紧贴，却会被渲染器按"上方有方块"
 * 剔除，于是缺面。这里强制放出这一组面（跳过对该方向的 cullTest、并清掉 quad 自身的
 * cullFace，与参考端"并入不参与剔除的那组"等效）。下组面（立柱底面，落在下台阶上表面）
 * 保留剔除，避免与台阶上表面重合。
 *
 * <p>FRAPI transform 为 LIFO 应用（后压入的先执行）：本包装器在外层，内部再嵌
 * {@link FenceStepModel} 时，横杆分类看到的是未位移的原版坐标，之后本层才平移。
 */
public final class SlabbedLoweringModel extends WrapperBlockStateModel {

    private static final float SHIFT_Y = -0.5F;

    /** 位移后不再与上方方块贴面、需要强制渲染的那组面（原 {@code cullface: up}）。 */
    private static final Direction RESCUE = Direction.UP;

    public SlabbedLoweringModel(BlockStateModel originalModel) {
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

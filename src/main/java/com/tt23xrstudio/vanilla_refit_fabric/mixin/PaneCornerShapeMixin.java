package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabOffset;
import com.tt23xrstudio.vanilla_refit_fabric.util.SlabSupport;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玻璃板的碰撞箱与拾取形状：12 个部件属性全为 false 时保持原版，
 * 有一个为 true 时形状就换成由 12 个部件拼出来的形状（不再有中间那根立柱）。
 *
 * 玻璃板没有覆写 CrossCollisionBlock 的 getShape / getCollisionShape（形状来自构造期算好的
 * {@code shapes} / {@code collisionShapes} 两张表），所以和栅栏的台阶位移一样注入到基类；
 * 只有玻璃板会命中 {@link PaneCornerSupport#adjustShape(VoxelShape, VoxelShape, BlockState)} 的判定，
 * 栅栏与铁栏杆保持原版形状。
 *
 * <p><b>贴台阶位移（自研增强，参考项目没有）</b>：下方是下台阶 → 形状整体下移半格贴齐砖面，
 * 否则上方是上台阶 → 上移半格贴齐砖底。与栅栏/墙的属性路线（{@code btsdhz_slab_offset}）不同，
 * 板类<b>不落属性</b>、查询时按邻居现算（见 {@link #shiftForSlab}），
 * 客户端渲染走 {@code PaneBakedModel} 的同源判定，两端永远一致。
 *
 * <p>26.2：CrossCollisionBlock 不再有 {@code shapeByIndex} 数组与 {@code getAABBIndex}，
 * 改成 {@code Function<BlockState, VoxelShape>} 两张表，这里影子字段照抄新结构，
 * “原版形状”就是 {@code shapes.apply(state)}。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class PaneCornerShapeMixin {

    @Shadow
    @Final
    private Function<BlockState, VoxelShape> shapes;

    @Shadow
    @Final
    private Function<BlockState, VoxelShape> collisionShapes;

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void vanillaRefit$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape adjusted = PaneCornerSupport.adjustShape(
                cir.getReturnValue(), this.shapes.apply(state), state);
        adjusted = shiftForSlab(state, level, pos, adjusted);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void vanillaRefit$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape adjusted = PaneCornerSupport.adjustShape(
                cir.getReturnValue(), this.collisionShapes.apply(state), state);
        adjusted = shiftForSlab(state, level, pos, adjusted);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }

    /**
     * 板类贴台阶位移：下方是下台阶 → 整体下移半格；否则上方是上台阶 → 上移半格。
     *
     * <p>不落 {@code btsdhz_slab_offset} 属性：板类族已有 12 个布尔属性（4,096 态/块 × 20 多块），
     * 再乘三值枚举会把状态数炸 3 倍。位移是周围环境的纯函数，查询时现算即可；
     * 位移结果走 {@link SlabSupport} 的形状缓存，高频查询不重复建形状。
     * 覆盖玻璃板 / 染色玻璃板 / 铁栏杆 / 铜栏杆（{@code WeatheringCopperBarsBlock extends IronBarsBlock}）。
     */
    private static VoxelShape shiftForSlab(BlockState state, BlockGetter level, BlockPos pos, VoxelShape shape) {
        if (shape == null || !PaneCornerSupport.isSupportedPane(state)) {
            return shape;
        }
        SlabOffset offset = SlabSupport.offsetAt(level, pos);
        if (offset == SlabOffset.LOWERED) {
            return SlabSupport.shiftDownHalf(state, shape);
        }
        return offset == SlabOffset.RAISED ? SlabSupport.shiftUpHalf(state, shape) : shape;
    }
}

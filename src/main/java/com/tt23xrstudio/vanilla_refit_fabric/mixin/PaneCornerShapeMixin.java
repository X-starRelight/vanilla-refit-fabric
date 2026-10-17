package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
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
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void vanillaRefit$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape adjusted = PaneCornerSupport.adjustShape(
                cir.getReturnValue(), this.collisionShapes.apply(state), state);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }
}

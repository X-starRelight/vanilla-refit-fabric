package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CrossCollisionBlock（栅栏等）形状按台阶位移半格。
 *
 * <p>栅栏继承了 CrossCollisionBlock 的 getShape / getCollisionShape（不覆写），
 * 所以形状位移要注入到基类；下台阶上的栅栏（btsdhz_slab_offset=lowered）下移半格、
 * 上台阶下的栅栏（raised）上移半格。铁栏杆/玻璃板/铜栏杆不带该属性，它们的位移走
 * 另一条纯函数路径（{@link PaneCornerShapeMixin} 的服务端形状 + 客户端 PaneBakedModel
 * 渲染期平移），本类不重复处理。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class CrossCollisionOnSlabMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void vanillaRefit$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape base = cir.getReturnValue();
        VoxelShape shifted = shiftForSlab(state, base);
        VoxelShape self = shifted != null ? shifted : base;
        // 上方/下方还有同样位移了半格的方块时，它有一部分伸进本格，并入本格的拾取形状（舒适框）。
        // 碰撞形状（getCollisionShape）保持不变，只有准星/拾取用得到这个形状。
        SlabSupport.StackedNeighbour stacked = SlabSupport.stackedNeighbour(level, pos, context);
        if (stacked != null) {
            cir.setReturnValue(SlabSupport.mergedWithNeighbour(self, stacked.shape()));
            return;
        }
        if (shifted != null) {
            cir.setReturnValue(shifted);
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void vanillaRefit$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape shifted = shiftForSlab(state, cir.getReturnValue());
        if (shifted == null) {
            return;
        }
        // 上下还叠着同样位移了半格的方块时，把它伸进本格的那部分并进碰撞形状：
        // 投掷物走逐格射线，只查射线穿过的那一格的形状，不并进来就会从那一截穿过去。
        SlabSupport.StackedNeighbour stacked = SlabSupport.stackedNeighbour(level, pos, context);
        if (stacked != null) {
            cir.setReturnValue(SlabSupport.mergedWithNeighbour(shifted, stacked.collision()));
        } else {
            cir.setReturnValue(shifted);
        }
    }

    /** 贴下台阶则下移半格，贴上台阶下方则上移半格；不贴台阶返回 null（保持原版形状）。 */
    private static VoxelShape shiftForSlab(BlockState state, VoxelShape shape) {
        if (shape == null) {
            return null;
        }
        if (SlabSupport.isOnSlab(state)) {
            return SlabSupport.shiftDownHalf(state, shape);
        }
        return SlabSupport.isUnderTopSlab(state) ? SlabSupport.shiftUpHalf(state, shape) : null;
    }
}

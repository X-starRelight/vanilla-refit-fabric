package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.PaneConnection;
import com.tt23xrstudio.vanilla_refit_fabric.util.PaneCornerSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 补上“夹角斜角”这条原版邻居级联覆盖不到的依赖，修掉玻璃板按某些顺序放置时不更新、
 * 要在旁边手动放个方块才刷新的问题。
 *
 * 原因：本模组的规则 2 会读 12 个斜角位置有没有方块，而原版的邻居级联只会把方块变化
 * 告诉 6 个正交邻居（见 BlockBehaviour.BlockStateBase#updateNeighbourShapes），斜角位置收不到通知，
 * 于是那块玻璃板就一直停在旧状态上。
 *
 * 原版自己也有同样的问题（红石线的斜角连接），它的做法就是覆写
 * BlockBehaviour#updateIndirectNeighbourShapes 来额外通知斜角邻居（见 RedStoneWireBlock）。
 * 这里照搬同一个钩子：Level.setBlock 会对旧状态和新状态各调一次这个方法，
 * 所以放置、拆除以及由本模组改状态引起的连锁都能覆盖到。
 */
@Mixin(BlockBehaviour.class)
public abstract class PaneIndirectNeighbourMixin {

    @Inject(method = "updateIndirectNeighbourShapes", at = @At("HEAD"), remap = false)
    private void vanillaRefit$refreshDiagonalPanes(BlockState state, LevelAccessor level, BlockPos pos,
                                                   int flags, int recursionLeft, CallbackInfo ci) {
        // 最便宜的判断放在最前：普通方块在 instanceof 这一步就返回，不会有额外开销
        if (PaneCornerSupport.isSupportedPane(state)) {
            PaneConnection.refreshDiagonals(level, state, pos, flags, recursionLeft);
        }
    }
}

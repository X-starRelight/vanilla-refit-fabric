package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.FenceSlabConnection;
import com.tt23xrstudio.vanilla_refit_fabric.util.WallSlabConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 墙/栅栏/台阶被放置或移除后，刷新“跨台阶连接”波及的墙与栅栏。
 *
 * <p>这些连接是斜向关系（台阶格四周的墙/栅栏 ↔ 台阶上/下方的位移墙/栅栏），原版的方块更新
 * 只会通知六方向邻居，通知不到，所以这里在方块落位/移除后主动补一次刷新。
 *
 * <p>注入点在 BlockBehaviour 的默认实现上，只有墙、栅栏与台阶会继续往下走，
 * 其余方块只是几次廉价判断。
 *
 * <p>26.2：原版移除回调 {@code onRemove(state, level, pos, newState, movedByPiston)}
 * 改名为 {@code affectNeighborsAfterRemoval(state, ServerLevel, pos, movedByPiston)}
 * （不再传 newState，移除后该位置已是新方块）。
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourSlabWallMixin {

    @Inject(method = "onPlace", at = @At("RETURN"), remap = false)
    private void vanillaRefit$onPlace(BlockState state, Level level, BlockPos pos,
                                      BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
        WallSlabConnection.refreshAround(level, pos, state);
        FenceSlabConnection.refreshAround(level, pos, state);
    }

    @Inject(method = "affectNeighborsAfterRemoval", at = @At("RETURN"), remap = false)
    private void vanillaRefit$affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                                          boolean movedByPiston, CallbackInfo ci) {
        WallSlabConnection.refreshAround(level, pos, state);
        FenceSlabConnection.refreshAround(level, pos, state);
    }
}

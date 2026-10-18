package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.PreBreakCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 破坏入口的状态快照。
 *
 * <p>优先级 100（低于 Fabric 破坏事件的 1000 与本模组接管的 1100）：Mixin 高优先级
 * 先应用、低优先级后应用，HEAD 注入点上后应用的回调先执行——所以这个注入在
 * “事件触发”和“本模组的取消判断”之前跑，能拿到未被任何模组处理过的原方块状态。
 */
@Mixin(value = ServerPlayerGameMode.class, priority = 100)
public abstract class ServerPlayerGameModeBreakCaptureMixin {

    @Shadow(remap = false)
    @Final
    protected ServerLevel level;

    @Inject(method = "destroyBlock", at = @At("HEAD"), remap = false)
    private void vanillaRefit$capturePreBreakState(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = this.level.getBlockState(pos);
        PreBreakCapture.capture(pos, state);
    }
}

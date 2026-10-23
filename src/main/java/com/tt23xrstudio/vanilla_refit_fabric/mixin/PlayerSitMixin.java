package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 坐在本模组座位上时禁止用潜行键下车（源项目 §2.4）。
 *
 * <p>下车改由再次按坐键走 {@code SitLogic.toggleSit} 的正式通道；潜行下车会因座位
 * tick 立即回收而落点异常。默认注入器 {@code defaultRequire 1}：注入点失效即启动期硬错误。
 */
@Mixin(Player.class)
public abstract class PlayerSitMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true, remap = false)
    private void vanillaRefit$blockDismountWhileSitting(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof SitEntity) {
            cir.setReturnValue(false);   // 坐在本模组座位上时禁止用潜行键下车
        }
    }
}

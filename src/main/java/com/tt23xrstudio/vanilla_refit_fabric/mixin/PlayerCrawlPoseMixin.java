package com.tt23xrstudio.vanilla_refit_fabric.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 爬行标记 → 强制 SWIMMING 姿态（源项目 §2.6）。
 *
 * <p>{@code setPose} 写同步实体数据：双方模型与服务端眼高一致；退出标记后交回原版
 * {@code updatePlayerPose} 按潜行/游泳状态判定。{@code defaultRequire 1}：注入点失效即启动期硬错误。
 */
@Mixin(Player.class)
public abstract class PlayerCrawlPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void vanillaRefit$forceCrawlPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (CrawlLogic.isCrawling(self)) {
            self.setPose(Pose.SWIMMING);   // setPose 写同步实体数据：双方模型与服务端眼高一致
            ci.cancel();
        }
    }
}

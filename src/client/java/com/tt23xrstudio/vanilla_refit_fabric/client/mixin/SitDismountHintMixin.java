package com.tt23xrstudio.vanilla_refit_fabric.client.mixin;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坐在本模组座位上时隐藏原版「按 Shift 下车」提示（源项目 §2.5，客户端 mixin）。
 *
 * <p>本模组封了潜行下车（{@code PlayerSitMixin}），原版提示会误导。不抓局部变量：
 * 直接看本地玩家是否坐在本模组座位上。{@code require = 0}：装饰性修改，注入点
 * 找不到时退化为原版行为、不崩游戏。
 */
@Mixin(ClientPacketListener.class)
public abstract class SitDismountHintMixin {

    @Inject(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
            ),
            cancellable = true,
            remap = false,
            require = 0   // 装饰性修改：注入点找不到时退化为原版行为，不崩游戏
    )
    private void vanillaRefit$skipDismountHintForSeat(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof SitEntity) {
            ci.cancel();   // 不抓局部变量：直接看本地玩家是否坐在本模组座位上（源项目注释要点）
        }
    }
}

package com.tt23xrstudio.vanilla_refit_fabric.network;

import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementModeState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 网络载荷注册（原 NeoForge 版在类上注解自动注册，这里集中手动注册）。
 *
 * <p>只注册类型与服务端接收器；客户端接收器在客户端入口注册
 * （{@code VanillaRefitFabricClient}），专用服务器不加载客户端类。
 */
public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(PlacementModePayload.TYPE, PlacementModePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlacementModeSyncPayload.TYPE,
                PlacementModeSyncPayload.STREAM_CODEC);

        // 客户端切换放置逻辑后上报，服务端写进玩家持久数据（附件，随存档保存）
        ServerPlayNetworking.registerGlobalReceiver(PlacementModePayload.TYPE,
                (payload, context) -> PlacementModeState.setServer(
                        context.player(),
                        com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode.byOrdinal(payload.slabMode()),
                        com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode.byOrdinal(payload.stairMode())
                ));
    }
}

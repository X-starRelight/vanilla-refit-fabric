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
        // 坐/起切换与爬行状态：两个 C2S 载荷
        PayloadTypeRegistry.serverboundPlay().register(SitTogglePayload.TYPE, SitTogglePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CrawlStatePayload.TYPE, CrawlStatePayload.STREAM_CODEC);

        // 客户端切换放置逻辑后上报，服务端写进玩家持久数据（附件，随存档保存）
        ServerPlayNetworking.registerGlobalReceiver(PlacementModePayload.TYPE,
                (payload, context) -> PlacementModeState.setServer(
                        context.player(),
                        com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode.byOrdinal(payload.slabMode()),
                        com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode.byOrdinal(payload.stairMode())
                ));

        // 按一次坐/起键：服务端生成或销毁隐形座位
        ServerPlayNetworking.registerGlobalReceiver(SitTogglePayload.TYPE,
                (payload, context) -> com.tt23xrstudio.vanilla_refit_fabric.util.SitLogic.toggleSit(context.player()));
        // 爬行状态变化时上报：服务端记进 UUID 集合（断线时由 CrawlLogic 清理）
        ServerPlayNetworking.registerGlobalReceiver(CrawlStatePayload.TYPE,
                (payload, context) -> com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic
                        .setCrawling(context.player(), payload.crawling()));
    }
}

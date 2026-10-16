package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.tt23xrstudio.vanilla_refit_fabric.block.MixedSlabBlock;
import com.tt23xrstudio.vanilla_refit_fabric.network.PlacementModePayload;
import com.tt23xrstudio.vanilla_refit_fabric.network.PlacementModeSyncPayload;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementModeState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * 客户端：放置逻辑的切换按键与服务端同步。
 *
 * <p>按下切换键（默认 V）时轮转当前手持方块（台阶或楼梯）的放置逻辑，
 * 更新本地缓存（客户端预测与提示线用）并把新值上报服务端（服务端写进玩家持久数据）。
 * 登录时收到服务端下发的权威值则直接采用（不能反过来由客户端上报，
 * 那会用刚启动的默认值把存档记忆覆盖掉）。
 */
public final class PlacementModeClient {

    private PlacementModeClient() {
    }

    public static void init() {
        // 登录/切换维度时服务端下发该玩家存档里记住的放置逻辑
        ClientPlayNetworking.registerGlobalReceiver(PlacementModeSyncPayload.TYPE, (payload, context) ->
                Minecraft.getInstance().execute(() -> PlacementModeState.setClient(
                        context.player(),
                        PlacementMode.byOrdinal(payload.slabMode()),
                        PlacementMode.byOrdinal(payload.stairMode()))));

        // 离开世界时清掉本地缓存（按 UUID 存的，不清会带着旧值进下一个存档）
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                PlacementModeState.clearClient());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 混合半砖的客户端预测（playerDestroy）需要当前本地玩家，每帧同步一次
            MixedSlabBlock.cachedPlayer = client.player;
            tickPlacementMode(client);
        });
    }

    private static void tickPlacementMode(Minecraft client) {
        net.minecraft.client.KeyMapping key = ModKeyBindings.placementModeKey();
        if (client.player == null || client.gui.screen() != null) {
            return;
        }
        while (key.consumeClick()) {
            ItemStack stack = client.player.getMainHandItem();
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            boolean slab = blockItem.getBlock() instanceof SlabBlock;
            boolean stair = blockItem.getBlock() instanceof StairBlock;
            if (!slab && !stair) {
                continue;
            }
            PlacementMode slabMode = PlacementModeState.slab(client.player);
            PlacementMode stairMode = PlacementModeState.stair(client.player);
            if (slab) {
                slabMode = slabMode.next(false);
            } else {
                stairMode = stairMode.next(true);
            }
            PlacementModeState.setClient(client.player, slabMode, stairMode);
            ClientPlayNetworking.send(PlacementModePayload.of(slabMode, stairMode));
            showOverlay(client, slab ? "slab" : "stair", slab ? slabMode : stairMode);
        }
    }

    private static void showOverlay(Minecraft client, String kind, PlacementMode mode) {
        client.gui.hud.setOverlayMessage(Component.translatable(
                "message.vanilla_refit_fabric.placement_mode." + kind,
                Component.translatable("placement_mode.vanilla_refit_fabric." + mode.getSerializedName())), false);
    }
}

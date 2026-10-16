package com.tt23xrstudio.vanilla_refit_fabric.util;

import com.mojang.serialization.Codec;
import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.network.PlacementModeSyncPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 每个玩家、每种方块（台阶/楼梯）当前的放置逻辑。
 *
 * <p><b>服务端是权威且持久化的</b>：值写进玩家的 Fabric 数据附件（Data Attachment），
 * 随存档保存，所以重新打开存档后仍然记得上次选的模式。客户端只持有一份缓存，用于客户端预测
 * 与提示线渲染：登录时由服务端下发一次，之后每次按键切换时客户端本地更新并同步给服务端。
 *
 * <p>不能反过来让客户端在登录时上报——那会用客户端刚启动的默认值把存档里的记忆覆盖掉。
 */
public final class PlacementModeState {
    /** 客户端缓存（服务端不用它）。 */
    private static final Map<UUID, PlacementMode> CLIENT_SLAB = new ConcurrentHashMap<>();
    private static final Map<UUID, PlacementMode> CLIENT_STAIR = new ConcurrentHashMap<>();

    /** 台阶放置逻辑（服务端权威，持久化）。值为 {@link PlacementMode#ordinal()}。 */
    public static final AttachmentType<Integer> SLAB_MODE = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .initializer(() -> PlacementMode.MOD.ordinal())
            .buildAndRegister(Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "slab_placement_mode"));

    /** 楼梯放置逻辑（服务端权威，持久化）。值为 {@link PlacementMode#ordinal()}。 */
    public static final AttachmentType<Integer> STAIR_MODE = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .initializer(() -> PlacementMode.MOD.ordinal())
            .buildAndRegister(Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "stair_placement_mode"));

    private PlacementModeState() {
    }

    /** 注册登录下发钩子；主初始化时调用一次（附件在类加载时完成注册）。 */
    public static void init() {
        // 玩家进服时把存档里记的模式下发给他的客户端（客户端预测与提示线需要）。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(PlacementModeSyncPayload.of(slab(handler.player), stair(handler.player))));
    }

    public static PlacementMode slab(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_SLAB.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return PlacementMode.byOrdinal(player.getAttachedOrElse(SLAB_MODE, PlacementMode.MOD.ordinal()));
    }

    public static PlacementMode stair(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_STAIR.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return PlacementMode.byOrdinal(player.getAttachedOrElse(STAIR_MODE, PlacementMode.MOD.ordinal()));
    }

    /** 客户端本地更新：按键切换、或收到服务端下发的权威值时调用。 */
    public static void setClient(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        put(CLIENT_SLAB, player.getUUID(), slabMode);
        put(CLIENT_STAIR, player.getUUID(), stairMode);
    }

    /** 服务端写入玩家持久数据（随存档保存）。 */
    public static void setServer(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        write(player, SLAB_MODE, slabMode);
        write(player, STAIR_MODE, stairMode);
    }

    private static void write(Player player, AttachmentType<Integer> type, PlacementMode mode) {
        if (mode == null) {
            return;
        }
        // 只存非默认值，默认值就把附件去掉（存档更干净，缺附件即默认）。
        if (mode == PlacementMode.MOD) {
            player.removeAttached(type);
        } else {
            player.setAttached(type, mode.ordinal());
        }
    }

    private static void put(Map<UUID, PlacementMode> map, UUID uuid, PlacementMode mode) {
        if (mode == null || mode == PlacementMode.MOD) {
            map.remove(uuid);
        } else {
            map.put(uuid, mode);
        }
    }

    /** 离开世界时清空客户端缓存。 */
    public static void clearClient() {
        CLIENT_SLAB.clear();
        CLIENT_STAIR.clear();
    }
}

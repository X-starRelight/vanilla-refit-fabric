package com.tt23xrstudio.vanilla_refit_fabric.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.entity.player.Player;

/**
 * 服务端记录玩家是否处于手动爬行状态（原 NeoForge 版 {@code util.CrawlLogic}）。
 *
 * <p>爬行姿态在客户端按下 Ctrl 时由客户端强制；姿态是同步实体数据，只改客户端的话
 * 服务端仍按站立姿态算眼高，于是射箭/投掷物的出生点会停在站立高度。这里让服务端
 * 也记下同一批玩家，两边的眼高才一致。
 *
 * <p>26.2 适配：NeoForge 的 {@code PlayerLoggedOutEvent} → Fabric 的
 * {@link ServerPlayConnectionEvents#DISCONNECT}。
 */
public final class CrawlLogic {

    private static final Set<UUID> CRAWLING = ConcurrentHashMap.newKeySet();

    private CrawlLogic() {
    }

    /** 写入某玩家的爬行标记（C2S {@code crawl_state} 载荷的服务端落点）。 */
    public static void setCrawling(Player player, boolean crawling) {
        if (crawling) {
            CRAWLING.add(player.getUUID());
        } else {
            CRAWLING.remove(player.getUUID());
        }
    }

    public static boolean isCrawling(Player player) {
        return CRAWLING.contains(player.getUUID());
    }

    /** 按 UUID 摘除标记（玩家退出时调用）。 */
    public static void remove(UUID uuid) {
        CRAWLING.remove(uuid);
    }

    /** 注册断线清理钩子；主初始化时调用一次。 */
    public static void init() {
        // 玩家退出时清掉标记，避免离线后残留条目（重进也不会凭空处于爬行姿态）。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player.getUUID()));
    }
}

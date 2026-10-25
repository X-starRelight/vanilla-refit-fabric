package com.tt23xrstudio.vanilla_refit_fabric.client;

import com.tt23xrstudio.vanilla_refit_fabric.entity.SitEntity;
import com.tt23xrstudio.vanilla_refit_fabric.network.CrawlStatePayload;
import com.tt23xrstudio.vanilla_refit_fabric.network.SitTogglePayload;
import com.tt23xrstudio.vanilla_refit_fabric.util.CrawlLogic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.UUID;

/**
 * 客户端：坐键发包、爬行期望计算与姿态/状态同步（移植自源项目 ModClientEvents.onClientTick §2.13）。
 *
 * <p>26.2 无 {@code setForcedPose}（计划 §5-② 方案 A）：客户端直接写 {@link CrawlLogic}
 * 共享集合，{@code START_CLIENT_TICK} 在玩家实体 tick 之前算好爬行，缓解 1 tick 延迟；
 * {@code PlayerCrawlPoseMixin} 依据该集合在双方强制 SWIMMING，故姿态/眼高一致。
 */
public final class SitCrawlClient {

    /** 去抖：仅当期望状态与上次已发不同时才发包。 */
    private static boolean crawlStateSent;

    /** 坐 toggle 去抖：本次“坐在座位上的会话”里已发过一次 toggle 后抑制自动站起，下车后复位。 */
    private static boolean sitToggleSent;

    /** 断线时按 UUID 精确清理（单机下该集合与服务端共享）。 */
    private static UUID localPlayerUuid;

    private SitCrawlClient() {
    }

    public static void init() {
        // START_CLIENT_TICK：在玩家实体 tick 之前算好爬行，尽量同 tick 生效（源项目用 Pre 事件）
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                crawlStateSent = false;
                sitToggleSent = false;
                localPlayerUuid = null;
                return;
            }
            localPlayerUuid = client.player.getUUID();

            // 坐：无界面打开时消费一次点击 → 发空载荷（每 tick 只消费一个，与源项目一致）
            boolean seated = client.player.getVehicle() instanceof SitEntity;
            if (!seated) {
                sitToggleSent = false;
            }
            if (client.gui.screen() == null && ModKeyBindings.sitKey().consumeClick()) {
                ClientPlayNetworking.send(new SitTogglePayload());
                sitToggleSent = true;
            }

            // 坐着不冻结（用户要求）：按任意移动键/跳跃 → 自动站起来，WASD 与重力立即恢复
            // （半空坐下悬停时也靠它落地）。26.2 Options 字段名为 keyUp/keyDown/keyLeft/keyRight/keyJump。
            // sitToggleSent 去抖：本会话已发过一次 toggle（如刚按坐键站起、roundtrip 期间座位还在）
            // 就不再自动发，避免两次 toggle 相抵把人又坐回去。
            if (!sitToggleSent && seated && client.gui.screen() == null
                    && (client.options.keyUp.isDown()
                        || client.options.keyDown.isDown()
                        || client.options.keyLeft.isDown()
                        || client.options.keyRight.isDown()
                        || client.options.keyJump.isDown())) {
                ClientPlayNetworking.send(new SitTogglePayload());
                sitToggleSent = true;
            }

            // 爬：7 个条件（源项目 §2.13 原文）：按键按住 + 存活 + 非旁观 + 未飞行 +
            // 非鞘翅 + 非骑乘 + 未睡觉
            boolean wantCrawl = ModKeyBindings.crawlKey().isDown()
                    && client.player.isAlive()
                    && !client.player.isSpectator()
                    && !client.player.getAbilities().flying
                    && !client.player.isFallFlying()
                    && !client.player.isPassenger()
                    && !client.player.isSleeping();

            if (wantCrawl) {
                // 爬行时不能冲刺：否则叠加游泳冲刺速度、视角抖动（源项目注释）
                client.player.setSprinting(false);
            }
            // 方案 A：客户端直接写本地集合 → PlayerCrawlPoseMixin 在客户端也强制 SWIMMING（幂等，每 tick 调）
            CrawlLogic.setCrawling(client.player, wantCrawl);

            // 状态变化才发包（去抖）：服务端记 UUID → 双方姿态/眼高一致
            if (wantCrawl != crawlStateSent) {
                crawlStateSent = wantCrawl;
                ClientPlayNetworking.send(new CrawlStatePayload(wantCrawl));
            }
        });

        // 断线：清本机玩家的爬行标记与去抖状态（服务端侧另有 Disconnect 事件，互不干扰）
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (localPlayerUuid != null) {
                CrawlLogic.remove(localPlayerUuid);
                localPlayerUuid = null;
            }
            crawlStateSent = false;
            sitToggleSent = false;
        });
    }
}

package com.tt23xrstudio.vanilla_refit_fabric.gametest;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

/**
 * 26.2 mock 玩家统一入口。
 *
 * <p>旧 {@code makeMockServerPlayerInLevel()}（已标 forRemoval）内部带 dummy connection；
 * 新 {@code makeMockServerPlayer(GameType)} 不带——玩家一旦被 {@code tick}（典型：骑乘坐位实体被
 * {@code rideTick} → {@code ServerPlayer.tick} 读 {@code connection}）就会 NPE 崩服（gametest 实证）。
 * 这里按 Fabric 官方 {@code FakePlayerPacketListener} 同款形态挂 dummy listener：共享空连接、
 * {@code send} 静默（不向空 channel 发包）。不能直接用 Fabric {@code FakePlayer}：它硬性
 * {@code startRiding} 恒 false（禁止骑乘），坐/爬用例不可用。</p>
 */
final class MockPlayers {

    private MockPlayers() {
    }

    /** 生存模式 mock 玩家（带 dummy connection，可安全骑乘/tick）。 */
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = new DummyListener(player);
        return player;
    }

    /** 与 Fabric FakePlayerPacketListener 同构：真实 listener + 静默 send + 共享空连接。 */
    private static final class DummyListener extends ServerGamePacketListenerImpl {
        private static final Connection CONN = new FakeConnection();

        DummyListener(ServerPlayer player) {
            super(player.level().getServer(), CONN, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener callbacks) {
        }

        private static final class FakeConnection extends Connection {
            FakeConnection() {
                super(PacketFlow.CLIENTBOUND);
            }
        }
    }
}

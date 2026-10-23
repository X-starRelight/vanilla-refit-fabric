package com.tt23xrstudio.vanilla_refit_fabric.network;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：同步手动爬行状态（按住/松开 Ctrl），让服务端的站位/眼高与客户端一致。
 *
 * <p>线上只写 1 字节 boolean，且只在状态变化的那一 tick 发一次。
 */
public record CrawlStatePayload(boolean crawling) implements CustomPacketPayload {
    public static final Type<CrawlStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "crawl_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrawlStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBoolean(payload.crawling()),
                    buffer -> new CrawlStatePayload(buffer.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

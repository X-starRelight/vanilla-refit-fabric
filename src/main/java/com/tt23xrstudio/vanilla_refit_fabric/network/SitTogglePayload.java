package com.tt23xrstudio.vanilla_refit_fabric.network;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端 -> 服务端：坐/起切换键按下一次（无字段，玩家本身就是上下文）。 */
public record SitTogglePayload() implements CustomPacketPayload {
    public static final Type<SitTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "sit_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SitTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new SitTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

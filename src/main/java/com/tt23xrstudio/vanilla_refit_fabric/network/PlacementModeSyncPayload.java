package com.tt23xrstudio.vanilla_refit_fabric.network;

import com.tt23xrstudio.vanilla_refit_fabric.VanillaRefitFabric;
import com.tt23xrstudio.vanilla_refit_fabric.util.PlacementMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端 -> 客户端: 下发该玩家在存档里记住的放置逻辑（客户端预测与提示线用）。 */
public record PlacementModeSyncPayload(int slabMode, int stairMode) implements CustomPacketPayload {
    public static final Type<PlacementModeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VanillaRefitFabric.MOD_ID, "placement_mode_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementModeSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.slabMode());
                        buffer.writeVarInt(payload.stairMode());
                    },
                    buffer -> new PlacementModeSyncPayload(buffer.readVarInt(), buffer.readVarInt())
            );

    public static PlacementModeSyncPayload of(PlacementMode slabMode, PlacementMode stairMode) {
        return new PlacementModeSyncPayload(slabMode.ordinal(), stairMode.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

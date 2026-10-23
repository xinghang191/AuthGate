package com.example.authmod.network;

import com.example.authmod.AuthMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2COpenAuthPacket(int mode, String username, int maxAttempts) implements CustomPacketPayload {
    public static final Type<S2COpenAuthPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "open_auth")
    );

    public static final StreamCodec<FriendlyByteBuf, S2COpenAuthPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeVarInt(packet.mode());
            buffer.writeUtf(packet.username());
            buffer.writeVarInt(packet.maxAttempts());
        },
        buffer -> new S2COpenAuthPacket(buffer.readVarInt(), buffer.readUtf(), buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

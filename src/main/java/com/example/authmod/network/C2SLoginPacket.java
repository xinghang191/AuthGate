package com.example.authmod.network;

import com.example.authmod.AuthMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SLoginPacket(String password) implements CustomPacketPayload {
    public static final Type<C2SLoginPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "login")
    );

    public static final StreamCodec<FriendlyByteBuf, C2SLoginPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeUtf(packet.password()),
        buffer -> new C2SLoginPacket(buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

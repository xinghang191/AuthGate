package com.example.authmod.network;

import com.example.authmod.AuthMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRegisterPacket(String password, String confirm) implements CustomPacketPayload {
    public static final Type<C2SRegisterPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "register")
    );

    public static final StreamCodec<FriendlyByteBuf, C2SRegisterPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeUtf(packet.password());
            buffer.writeUtf(packet.confirm());
        },
        buffer -> new C2SRegisterPacket(buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

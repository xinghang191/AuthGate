package com.example.authmod.network;

import com.example.authmod.AuthMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SChangePasswordPacket(String oldPw, String newPw, String confirm) implements CustomPacketPayload {
    public static final Type<C2SChangePasswordPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "change_password")
    );

    public static final StreamCodec<FriendlyByteBuf, C2SChangePasswordPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeUtf(packet.oldPw());
            buffer.writeUtf(packet.newPw());
            buffer.writeUtf(packet.confirm());
        },
        buffer -> new C2SChangePasswordPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.example.authmod.network;

import com.example.authmod.AuthMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CAuthResultPacket(boolean success, int mode, String messageKey, List<String> args)
    implements CustomPacketPayload {
    public static final Type<S2CAuthResultPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "auth_result")
    );

    public static final StreamCodec<FriendlyByteBuf, S2CAuthResultPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBoolean(packet.success());
            buffer.writeVarInt(packet.mode());
            buffer.writeUtf(packet.messageKey());
            buffer.writeVarInt(packet.args().size());
            for (String arg : packet.args()) {
                buffer.writeUtf(arg);
            }
        },
        buffer -> {
            boolean success = buffer.readBoolean();
            int mode = buffer.readVarInt();
            String messageKey = buffer.readUtf();
            int argCount = buffer.readVarInt();
            if (argCount < 0 || argCount > 16) {
                throw new IllegalArgumentException("Invalid authentication response argument count");
            }
            List<String> args = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                args.add(buffer.readUtf());
            }
            return new S2CAuthResultPacket(success, mode, messageKey, args);
        }
    );

    public S2CAuthResultPacket {
        args = List.copyOf(args);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

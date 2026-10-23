package com.example.authmod.network;

import com.example.authmod.AuthMod;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Network registration for Forge 54.1.22.
 * Signatures were checked against ChannelBuilder, SimpleChannel, and
 * CustomPayloadEvent.Context in the 54.1.22 sources JAR.
 */
public final class ModNetwork {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MODE_REGISTER = 0;
    public static final int MODE_LOGIN = 1;
    public static final int MODE_CHANGE_PASSWORD = 2;

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(AuthMod.MODID, "main"))
        .networkProtocolVersion(PROTOCOL_VERSION)
        .acceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
        .simpleChannel();

    private static boolean registered;
    private static ServerHandler serverHandler;
    private static ClientHandler clientHandler;

    private ModNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        CHANNEL.messageBuilder(C2SRegisterPacket.class)
            .codec(C2SRegisterPacket.STREAM_CODEC)
            .consumerMainThread(ModNetwork::handleRegister)
            .direction(PacketFlow.SERVERBOUND)
            .add();
        CHANNEL.messageBuilder(C2SLoginPacket.class)
            .codec(C2SLoginPacket.STREAM_CODEC)
            .consumerMainThread(ModNetwork::handleLogin)
            .direction(PacketFlow.SERVERBOUND)
            .add();
        CHANNEL.messageBuilder(C2SChangePasswordPacket.class)
            .codec(C2SChangePasswordPacket.STREAM_CODEC)
            .consumerMainThread(ModNetwork::handleChangePassword)
            .direction(PacketFlow.SERVERBOUND)
            .add();
        CHANNEL.messageBuilder(S2COpenAuthPacket.class)
            .codec(S2COpenAuthPacket.STREAM_CODEC)
            .consumerMainThread(ModNetwork::handleOpenAuth)
            .direction(PacketFlow.CLIENTBOUND)
            .add();
        CHANNEL.messageBuilder(S2CAuthResultPacket.class)
            .codec(S2CAuthResultPacket.STREAM_CODEC)
            .consumerMainThread(ModNetwork::handleAuthResult)
            .direction(PacketFlow.CLIENTBOUND)
            .add();

        registered = true;
    }

    public static void setServerHandler(ServerHandler handler) {
        serverHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setClientHandler(ClientHandler handler) {
        clientHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void sendToServer(C2SRegisterPacket packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void sendToServer(C2SLoginPacket packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void sendToServer(C2SChangePasswordPacket packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void sendOpenAuth(ServerPlayer player, int mode, String username, int maxAttempts) {
        CHANNEL.send(new S2COpenAuthPacket(mode, username, maxAttempts), PacketDistributor.PLAYER.with(player));
    }

    public static void sendAuthResult(ServerPlayer player, boolean success, int mode, String messageKey, List<String> args) {
        CHANNEL.send(new S2CAuthResultPacket(success, mode, messageKey, args), PacketDistributor.PLAYER.with(player));
    }

    private static void handleRegister(C2SRegisterPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        ServerHandler handler = serverHandler;
        if (player == null) {
            return;
        }
        if (handler == null) {
            sendAuthResult(player, false, MODE_REGISTER, "authmod.msg.internal_error", List.of());
            return;
        }
        try {
            handler.register(player, packet);
        } catch (RuntimeException exception) {
            sendAuthResult(player, false, MODE_REGISTER, "authmod.msg.internal_error", List.of());
        }
    }

    private static void handleLogin(C2SLoginPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        ServerHandler handler = serverHandler;
        if (player == null) {
            return;
        }
        if (handler == null) {
            sendAuthResult(player, false, MODE_LOGIN, "authmod.msg.internal_error", List.of());
            return;
        }
        try {
            handler.login(player, packet);
        } catch (RuntimeException exception) {
            sendAuthResult(player, false, MODE_LOGIN, "authmod.msg.internal_error", List.of());
        }
    }

    private static void handleChangePassword(C2SChangePasswordPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        ServerHandler handler = serverHandler;
        if (player == null) {
            return;
        }
        if (handler == null) {
            sendAuthResult(player, false, MODE_CHANGE_PASSWORD, "authmod.msg.internal_error", List.of());
            return;
        }
        try {
            handler.changePassword(player, packet);
        } catch (RuntimeException exception) {
            sendAuthResult(player, false, MODE_CHANGE_PASSWORD, "authmod.msg.internal_error", List.of());
        }
    }

    private static void handleOpenAuth(S2COpenAuthPacket packet, CustomPayloadEvent.Context context) {
        ClientHandler handler = clientHandler;
        if (context.isClientSide() && handler != null) {
            handler.openAuth(packet);
        }
    }

    private static void handleAuthResult(S2CAuthResultPacket packet, CustomPayloadEvent.Context context) {
        ClientHandler handler = clientHandler;
        if (context.isClientSide() && handler != null) {
            handler.authResult(packet);
        }
    }

    public interface ServerHandler {
        void register(ServerPlayer player, C2SRegisterPacket packet);

        void login(ServerPlayer player, C2SLoginPacket packet);

        void changePassword(ServerPlayer player, C2SChangePasswordPacket packet);
    }

    public interface ClientHandler {
        void openAuth(S2COpenAuthPacket packet);

        void authResult(S2CAuthResultPacket packet);
    }
}

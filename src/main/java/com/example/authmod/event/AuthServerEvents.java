package com.example.authmod.event;

import com.example.authmod.AuthManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AuthServerEvents {
    private final Map<UUID, Long> openScreenAtTick = new HashMap<>();
    private long serverTick;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serverTick = 0L;
        openScreenAtTick.clear();
        AuthManager.getInstance().start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        openScreenAtTick.clear();
        AuthManager.getInstance().stop();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.getInstance().onLogin(player);
            scheduleAuthScreen(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            openScreenAtTick.remove(player.getUUID());
            AuthManager.getInstance().onLogout(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.getInstance().onRespawn(player);
            if (AuthManager.getInstance().isLocked(player)) {
                scheduleAuthScreen(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthManager.getInstance().onDimensionChange(player);
            if (AuthManager.getInstance().isLocked(player)) {
                scheduleAuthScreen(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent.Post event) {
        serverTick++;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Long>> iterator = openScreenAtTick.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= serverTick) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && AuthManager.getInstance().isLocked(player)) {
                    AuthManager.getInstance().openAuthScreen(player);
                }
                iterator.remove();
            }
        }

        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AuthManager.getInstance().hasTimedOut(player, now)) {
                player.connection.disconnect(Component.translatable("authmod.kick.timeout"));
            }
        }
    }

    private void scheduleAuthScreen(UUID playerId) {
        openScreenAtTick.put(playerId, serverTick + ThreadLocalRandom.current().nextLong(20L, 41L));
    }
}

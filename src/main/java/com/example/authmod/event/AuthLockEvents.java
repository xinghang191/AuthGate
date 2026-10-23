package com.example.authmod.event;

import com.example.authmod.AuthManager;
import com.example.authmod.PlayerAuthState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AuthLockEvents {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player instanceof ServerPlayer player) || !AuthManager.getInstance().isLocked(player)) {
            return;
        }
        PlayerAuthState state = AuthManager.getInstance().getState(player);
        if (state == null) {
            return;
        }
        Vec3 anchor = state.anchorPos();
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(anchor.x, anchor.y, anchor.z);
        player.setYRot(state.anchorYaw());
        player.setXRot(state.anchorPitch());
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)
            && (event instanceof PlayerInteractEvent.LeftClickBlock
                || event instanceof PlayerInteractEvent.RightClickBlock
                || event instanceof PlayerInteractEvent.RightClickItem
                || event instanceof PlayerInteractEvent.EntityInteract
                || event instanceof PlayerInteractEvent.EntityInteractSpecific)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (AuthManager.getInstance().isLocked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
            && AuthManager.getInstance().isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        cancelPlayerDamage(event.getEntity(), event::setCanceled);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        cancelPlayerDamage(event.getEntity(), event::setCanceled);
    }

    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        Entity mounting = event.getEntityMounting();
        Entity mounted = event.getEntityBeingMounted();
        if ((mounting instanceof ServerPlayer mountingPlayer && AuthManager.getInstance().isLocked(mountingPlayer))
            || (mounted instanceof ServerPlayer mountedPlayer && AuthManager.getInstance().isLocked(mountedPlayer))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)) {
            player.closeContainer();
        }
    }

    private static void cancelPlayerDamage(Entity entity, java.util.function.Consumer<Boolean> cancel) {
        if (entity instanceof ServerPlayer player && AuthManager.getInstance().isLocked(player)) {
            cancel.accept(true);
        }
    }
}

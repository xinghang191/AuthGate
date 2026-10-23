package com.example.authmod.client;

import com.example.authmod.AuthMod;
import com.example.authmod.network.ModNetwork;
import com.example.authmod.network.S2CAuthResultPacket;
import com.example.authmod.network.S2COpenAuthPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AuthMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientAuthHandler implements ModNetwork.ClientHandler {
    private static final ClientAuthHandler INSTANCE = new ClientAuthHandler();

    private ClientAuthHandler() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModNetwork.setClientHandler(INSTANCE));
    }

    @Override
    public void openAuth(S2COpenAuthPacket packet) {
        Minecraft.getInstance().setScreen(new AuthScreen(packet));
    }

    @Override
    public void authResult(S2CAuthResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof AuthScreen screen) {
            screen.applyResult(packet);
        }
    }
}

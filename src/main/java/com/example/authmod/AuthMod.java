package com.example.authmod;

import com.example.authmod.network.ModNetwork;
import com.example.authmod.event.AuthLockEvents;
import com.example.authmod.event.AuthServerEvents;
import com.example.authmod.command.AuthCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AuthMod.MODID)
public final class AuthMod {
    public static final String MODID = "authmod";

    public AuthMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, AuthConfig.SPEC);
        ModNetwork.register();
        ModNetwork.setServerHandler(AuthManager.getInstance());
        MinecraftForge.EVENT_BUS.register(new AuthServerEvents());
        MinecraftForge.EVENT_BUS.register(new AuthLockEvents());
        MinecraftForge.EVENT_BUS.register(new AuthCommands());
    }
}

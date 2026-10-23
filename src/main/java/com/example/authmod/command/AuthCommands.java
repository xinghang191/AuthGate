package com.example.authmod.command;

import com.example.authmod.AuthManager;
import com.example.authmod.AuthManager.AccountInfo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AuthCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("changepassword")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (!AuthManager.getInstance().openChangePassword(player)) {
                    context.getSource().sendFailure(Component.translatable("authmod.kick.relogin"));
                    return 0;
                }
                return 1;
            }));

        dispatcher.register(Commands.literal("authadmin")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reset")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "player");
                        if (AuthManager.getInstance().resetAccount(name)) {
                            context.getSource().sendSuccess(
                                () -> Component.translatable("authmod.cmd.reset_ok", name), true);
                            return 1;
                        }
                        context.getSource().sendFailure(Component.translatable("authmod.msg.no_account"));
                        return 0;
                    })))
            .then(Commands.literal("unlock")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "player");
                        if (AuthManager.getInstance().unlockAccount(name)) {
                            context.getSource().sendSuccess(
                                () -> Component.translatable("authmod.cmd.unlock_ok", name), true);
                            return 1;
                        }
                        context.getSource().sendFailure(Component.translatable("authmod.msg.no_account"));
                        return 0;
                    })))
            .then(Commands.literal("info")
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "player");
                        AccountInfo info = AuthManager.getInstance().getInfo(name);
                        if (info == null) {
                            context.getSource().sendFailure(Component.translatable("authmod.msg.no_account"));
                            return 0;
                        }
                        context.getSource().sendSuccess(
                            () -> Component.translatable("authmod.cmd.info", name,
                                info.failedAttempts(), Math.max(0L, info.lockedUntil() - System.currentTimeMillis() / 1_000L)),
                            false);
                        return 1;
                    }))));
    }
}

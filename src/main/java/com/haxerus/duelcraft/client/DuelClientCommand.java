package com.haxerus.duelcraft.client;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /duel show} reopens the duel screen from the live client state. Client-side only; every
 * other {@code /duel} subcommand fails to parse here and falls through to the server.
 */
public final class DuelClientCommand {

    private DuelClientCommand() { }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("show")
                                .executes(DuelClientCommand::show)));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        DuelScreen screen = LDLibDuelScreen.reopen();
        if (screen == null) {
            ctx.getSource().sendFailure(Component.literal("No duel in progress"));
            return 0;
        }
        Minecraft.getInstance().setScreen(screen);
        return 1;
    }
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DuelOptions;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

public class DuelCommand {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("challenge")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(DuelCommand::challenge)))
                        .then(Commands.literal("accept")
                                .executes(DuelCommand::accept))
                        .then(Commands.literal("forfeit")
                                .executes(DuelCommand::forfeit))
                        .then(Commands.literal("test")
                                .executes(DuelCommand::test))
        );
    }

    private static int challenge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("You can't challenge yourself."));
            return 0;
        }

        if (DuelManager.get().getPlayerActiveDuel(sender) != null
                || DuelManager.get().getPlayerActiveDuel(target) != null) {
            sender.sendSystemMessage(Component.literal("A player is already in a duel!"));
            return 0;
        }

        DuelManager.get().duelInvites.put(target.getUUID(), sender.getUUID());
        sender.sendSystemMessage(Component.literal( "Sent duel challenge."));
        target.sendSystemMessage(Component.literal( "You have been challenged to a duel."));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var challengerUUID = DuelManager.get().duelInvites.get(player.getUUID());
        if (challengerUUID == null) {
            player.sendSystemMessage(Component.literal( "No duel invites."));
            return 0;
        }

        var server = ctx.getSource().getServer();
        var challenger = server.getPlayerList().getPlayer(challengerUUID);
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("Challenger is no longer online."));
            DuelManager.get().duelInvites.remove(player.getUUID());
            return 0;
        }
        DuelManager.get().startDuel(player, challenger, DuelOptions.standard(), Deck.standard(), Deck.standard());
        DuelManager.get().duelInvites.remove(player.getUUID());
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (DuelManager.get().getPlayerActiveDuel(player) != null) {
            player.sendSystemMessage(Component.literal("You are already in a duel!"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Starting solo test duel vs AI..."));
        DuelManager.get().startSoloDuel(player, DuelOptions.standard(), Deck.standard(), Deck.standard());
        return 1;
    }

    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID duelID = DuelManager.get().getPlayerActiveDuel(player);
        if (duelID == null) {
            player.sendSystemMessage(Component.literal( "No active duel."));
            return 0;
        }

        DuelManager.get().endDuel(duelID);
        return 1;
    }
}

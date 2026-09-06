package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.core.DuelRule;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DuelCommand {

    private static final SuggestionProvider<CommandSourceStack> DECK_NAMES =
            (ctx, builder) -> {
                DeckRegistry reg = DuelManager.get().getDeckRegistry();
                if (reg == null) return builder.buildFuture();
                return SharedSuggestionProvider.suggest(reg.listDeckNames(), builder);
            };

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("challenge")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> challenge(ctx, ThreadLocalRandom.current().nextLong()))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> challenge(ctx, LongArgumentType.getLong(ctx, "seed"))))))
                        .then(Commands.literal("accept")
                                .executes(DuelCommand::accept))
                        .then(Commands.literal("forfeit")
                                .executes(DuelCommand::forfeit))
                        .then(Commands.literal("test")
                                .executes(ctx -> test(ctx, null, ThreadLocalRandom.current().nextLong()))
                                .then(Commands.argument("aiDeck", StringArgumentType.string())
                                        .suggests(DECK_NAMES)
                                        .executes(ctx -> test(ctx,
                                                StringArgumentType.getString(ctx, "aiDeck"),
                                                ThreadLocalRandom.current().nextLong()))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> test(ctx,
                                                        StringArgumentType.getString(ctx, "aiDeck"),
                                                        LongArgumentType.getLong(ctx, "seed"))))))
                        .then(Commands.literal("deck")
                                .then(Commands.literal("list")
                                        .executes(DuelCommand::deckList))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(DECK_NAMES)
                                                .executes(DuelCommand::deckSet)))
                                .then(Commands.literal("get")
                                        .executes(DuelCommand::deckGet))
                                .then(Commands.literal("clear")
                                        .executes(DuelCommand::deckClear)))
        );
    }

    // --- challenge / accept / forfeit ---

    private static int challenge(CommandContext<CommandSourceStack> ctx, long seed)
            throws CommandSyntaxException {
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

        DuelManager.get().duelInvites.put(target.getUUID(), new PendingChallenge(sender.getUUID(), seed));
        sender.sendSystemMessage(Component.literal("Sent duel challenge (seed=" + seed + ")."));
        target.sendSystemMessage(Component.literal("You have been challenged to a duel."));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var pending = DuelManager.get().duelInvites.get(player.getUUID());
        if (pending == null) {
            player.sendSystemMessage(Component.literal("No duel invites."));
            return 0;
        }

        var server = ctx.getSource().getServer();
        var challenger = server.getPlayerList().getPlayer(pending.challengerUUID());
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("Challenger is no longer online."));
            DuelManager.get().duelInvites.remove(player.getUUID());
            return 0;
        }

        Deck challengerDeck;
        Deck accepterDeck;
        try {
            challengerDeck = DuelManager.get().resolveDeck(challenger);
            accepterDeck = DuelManager.get().resolveDeck(player);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Failed to load a deck: " + e.getMessage()));
            return 0;
        }
        String challengerName = DuelManager.get().getPlayerCurrentDeck(challenger.getUUID()).orElse(null);
        String accepterName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        DuelManager.get().startDuel(challenger, player, pending.seed(), DuelRule.MR5,
                challengerDeck, accepterDeck, challengerName, accepterName);
        DuelManager.get().duelInvites.remove(player.getUUID());
        return 1;
    }

    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID duelID = DuelManager.get().getPlayerActiveDuel(player);
        if (duelID == null) {
            player.sendSystemMessage(Component.literal("No active duel."));
            return 0;
        }
        DuelManager.get().endDuel(duelID);
        return 1;
    }

    // --- test ---

    private static int test(CommandContext<CommandSourceStack> ctx, String aiDeckName, long seed)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (DuelManager.get().getPlayerActiveDuel(player) != null) {
            player.sendSystemMessage(Component.literal("You are already in a duel!"));
            return 0;
        }

        Deck playerDeck;
        Deck aiDeck;
        try {
            playerDeck = DuelManager.get().resolveDeck(player);
            aiDeck = (aiDeckName == null) ? playerDeck : DuelManager.get().getDeckRegistry().load(aiDeckName);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Failed to load a deck: " + e.getMessage()));
            return 0;
        }
        String playerDeckName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        player.sendSystemMessage(Component.literal("Starting solo test duel vs AI (seed=" + seed + ")..."));
        DuelManager.get().startSoloDuel(player, seed, DuelRule.MR5, playerDeck, aiDeck, playerDeckName, aiDeckName);
        return 1;
    }

    // --- deck subcommands ---

    private static int deckList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var names = DuelManager.get().getDeckRegistry().listDeckNames();
        if (names.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No decks. Drop .ydk files into <gameDir>/duelcraft/decks/."));
        } else {
            player.sendSystemMessage(Component.literal("Decks: " + String.join(", ", names)));
        }
        return 1;
    }

    private static int deckSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        try {
            DuelManager.get().getDeckRegistry().load(name); // validate at set-time
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Cannot set deck '" + name + "': " + e.getMessage()));
            return 0;
        }
        DuelManager.get().setPlayerCurrentDeck(player.getUUID(), name);
        player.sendSystemMessage(Component.literal("Current deck set to '" + name + "'."));
        return 1;
    }

    private static int deckGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var name = DuelManager.get().getPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal(
                name.map(n -> "Current deck: " + n).orElse("No current deck (using standard).")));
        return 1;
    }

    private static int deckClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DuelManager.get().clearPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal("Current deck cleared."));
        return 1;
    }

    /** Pending challenge: who challenged + the agreed seed. */
    public record PendingChallenge(UUID challengerUUID, long seed) {}
}

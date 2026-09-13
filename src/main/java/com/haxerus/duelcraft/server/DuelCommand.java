package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.core.DeckValidator;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.FirstTurnLobby;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
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

    /** How long a duel challenge stays acceptable. */
    public static final long INVITE_TIMEOUT_MS = 60_000L;

    private static final SuggestionProvider<CommandSourceStack> DECK_NAMES =
            (ctx, builder) -> {
                DeckRegistry reg = DuelManager.get().getDeckRegistry();
                if (reg == null) return builder.buildFuture();
                return SharedSuggestionProvider.suggest(reg.listDeckNames(), builder);
            };

    private static final SuggestionProvider<CommandSourceStack> RULE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(DuelRule.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> HANDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"rock", "paper", "scissors"}, builder);

    private static final SuggestionProvider<CommandSourceStack> YES_NO =
            (ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"yes", "no"}, builder);

    private static final DynamicCommandExceptionType UNKNOWN_RULE = new DynamicCommandExceptionType(
            id -> Component.literal("Unknown rule '" + id + "'. Valid rules: " + String.join(", ", DuelRule.ids())));

    private static final DynamicCommandExceptionType UNKNOWN_HAND = new DynamicCommandExceptionType(
            name -> Component.literal("Unknown hand '" + name + "'. Choose rock, paper or scissors."));

    /** Appends the optional {@code [rule] [seed] [lp] [hand] [draw]} tail, runnable at every depth. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withDuelOptions(
            T node, Command<CommandSourceStack> action) {
        return node.executes(action)
                .then(Commands.argument("rule", StringArgumentType.word()).suggests(RULE_IDS).executes(action)
                .then(Commands.argument("seed", LongArgumentType.longArg()).executes(action)
                .then(Commands.argument("lp", IntegerArgumentType.integer(1, 99999)).executes(action)
                .then(Commands.argument("hand", IntegerArgumentType.integer(0, 20)).executes(action)
                .then(Commands.argument("draw", IntegerArgumentType.integer(0, 10)).executes(action))))));
    }

    /** Whether the command line reached {@code name}; the option tail is optional at every depth. */
    private static boolean has(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(name));
    }

    private static DuelRule ruleOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!has(ctx, "rule")) return DuelRule.MR5;
        String id = StringArgumentType.getString(ctx, "rule");
        return DuelRule.parse(id).orElseThrow(() -> UNKNOWN_RULE.create(id));
    }

    private static long seedOf(CommandContext<CommandSourceStack> ctx) {
        return has(ctx, "seed")
                ? LongArgumentType.getLong(ctx, "seed")
                : ThreadLocalRandom.current().nextLong();
    }

    /** Starting LP, hand size and draw count; each falls back to the standard 8000/5/1. */
    private static PlayerOptions playerOptionsOf(CommandContext<CommandSourceStack> ctx) {
        PlayerOptions standard = PlayerOptions.standard();
        return new PlayerOptions(
                has(ctx, "lp") ? IntegerArgumentType.getInteger(ctx, "lp") : standard.lp(),
                has(ctx, "hand") ? IntegerArgumentType.getInteger(ctx, "hand") : standard.startHand(),
                has(ctx, "draw") ? IntegerArgumentType.getInteger(ctx, "draw") : standard.drawPerTurn());
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("challenge")
                                .then(withDuelOptions(Commands.argument("player", EntityArgument.player()),
                                        DuelCommand::challenge)))
                        .then(Commands.literal("accept")
                                .executes(DuelCommand::accept))
                        .then(Commands.literal("hand")
                                .then(Commands.argument("hand", StringArgumentType.word())
                                        .suggests(HANDS)
                                        .executes(DuelCommand::hand)))
                        .then(Commands.literal("first")
                                .then(Commands.argument("choice", StringArgumentType.word())
                                        .suggests(YES_NO)
                                        .executes(DuelCommand::first)))
                        .then(Commands.literal("forfeit")
                                .executes(DuelCommand::forfeit))
                        .then(Commands.literal("test")
                                .executes(ctx -> test(ctx, null))
                                .then(withDuelOptions(Commands.argument("aiDeck", StringArgumentType.string())
                                                .suggests(DECK_NAMES),
                                        ctx -> test(ctx, StringArgumentType.getString(ctx, "aiDeck")))))
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

    private static int challenge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        DuelRule rule = ruleOf(ctx);
        long seed = seedOf(ctx);
        PlayerOptions options = playerOptionsOf(ctx);

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("You can't challenge yourself."));
            return 0;
        }
        if (DuelManager.get().isBusy(sender) || DuelManager.get().isBusy(target)) {
            sender.sendSystemMessage(Component.literal("A player is already in a duel!"));
            return 0;
        }

        if (!deckIsLegal(sender, sender, rule)) return 0;
        if (!deckIsLegal(target, sender, rule)) return 0;

        DuelManager.get().duelInvites.put(target.getUUID(),
                new PendingChallenge(sender.getUUID(), seed, rule, options, System.currentTimeMillis()));
        sender.sendSystemMessage(Component.literal("Sent duel challenge (seed=" + seed + ", rule=" + rule.id()
                + ", lp=" + options.lp() + ", hand=" + options.startHand() + ", draw=" + options.drawPerTurn() + ")."));
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
        if (pending.isExpired(System.currentTimeMillis())) {
            DuelManager.get().duelInvites.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That duel challenge has expired."));
            return 0;
        }

        var server = ctx.getSource().getServer();
        var challenger = server.getPlayerList().getPlayer(pending.challengerUUID());
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("Challenger is no longer online."));
            DuelManager.get().duelInvites.remove(player.getUUID());
            return 0;
        }
        // Either of them may have started something else since the invite was sent.
        if (DuelManager.get().isBusy(player) || DuelManager.get().isBusy(challenger)) {
            player.sendSystemMessage(Component.literal("A player is already in a duel!"));
            return 0;
        }

        Deck challengerDeck;
        try {
            challengerDeck = DuelManager.get().resolveDeck(challenger);
        } catch (IOException | DeckLoader.DeckParseException e) {
            String who = challenger.getName().getString();
            player.sendSystemMessage(Component.literal(who + "'s deck could not be loaded: " + e.getMessage()));
            challenger.sendSystemMessage(Component.literal("Your deck could not be loaded, so "
                    + player.getName().getString() + " could not accept: " + e.getMessage()));
            return 0;
        }
        Deck accepterDeck;
        try {
            accepterDeck = DuelManager.get().resolveDeck(player);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Your deck could not be loaded: " + e.getMessage()));
            return 0;
        }
        // Both decks are re-read on every use, so they are re-checked here as edopro checks at ready time.
        var challengerProblems = DeckValidator.problems(challengerDeck, pending.rule());
        if (!challengerProblems.isEmpty()) {
            String joined = String.join("; ", challengerProblems);
            player.sendSystemMessage(Component.literal(
                    challenger.getName().getString() + "'s deck is not legal: " + joined));
            challenger.sendSystemMessage(Component.literal("Your deck is not legal, so "
                    + player.getName().getString() + " could not accept: " + joined));
            return 0;
        }
        if (!reportDeckProblems(accepterDeck, pending.rule(), player, player)) return 0;

        String challengerName = DuelManager.get().getPlayerCurrentDeck(challenger.getUUID()).orElse(null);
        String accepterName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        DuelManager.get().duelInvites.remove(player.getUUID());
        DuelManager.get().beginFirstTurnRoll(challenger, player, pending.seed(), pending.rule(),
                pending.options(), challengerDeck, accepterDeck, challengerName, accepterName);
        return 1;
    }

    // --- first-turn roll ---

    private static int hand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "hand");
        FirstTurnLobby.Hand hand = FirstTurnLobby.parse(name);
        if (hand == null) throw UNKNOWN_HAND.create(name);
        DuelManager.get().submitHand(player, hand);
        return 1;
    }

    private static int first(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String choice = StringArgumentType.getString(ctx, "choice");
        if (!choice.equalsIgnoreCase("yes") && !choice.equalsIgnoreCase("no")) {
            player.sendSystemMessage(Component.literal("Answer yes or no."));
            return 0;
        }
        DuelManager.get().submitFirstTurnChoice(player, choice.equalsIgnoreCase("yes"));
        return 1;
    }

    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID duelID = DuelManager.get().getPlayerActiveDuel(player);
        if (duelID == null) {
            player.sendSystemMessage(Component.literal("No active duel."));
            return 0;
        }
        DuelManager.get().forfeit(player);
        return 1;
    }

    /** Whether {@code who}'s current deck loads and is legal; failures are reported to {@code sender}. */
    private static boolean deckIsLegal(ServerPlayer who, ServerPlayer sender, DuelRule rule) {
        Deck deck;
        try {
            deck = DuelManager.get().resolveDeck(who);
        } catch (IOException | DeckLoader.DeckParseException e) {
            sender.sendSystemMessage(Component.literal(who == sender
                    ? "Your deck could not be loaded: " + e.getMessage()
                    : who.getName().getString() + "'s deck could not be loaded: " + e.getMessage()));
            return false;
        }
        return reportDeckProblems(deck, rule, who, sender);
    }

    /** Whether {@code deck} is legal; the joined problem list goes to {@code sender} when it is not. */
    private static boolean reportDeckProblems(Deck deck, DuelRule rule, ServerPlayer owner, ServerPlayer sender) {
        var problems = DeckValidator.problems(deck, rule);
        if (problems.isEmpty()) return true;
        sender.sendSystemMessage(Component.literal((owner == sender
                ? "Your deck is not legal: "
                : owner.getName().getString() + "'s deck is not legal: ") + String.join("; ", problems)));
        return false;
    }

    // --- test ---

    private static int test(CommandContext<CommandSourceStack> ctx, String aiDeckName)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DuelRule rule = ruleOf(ctx);
        long seed = seedOf(ctx);
        PlayerOptions options = playerOptionsOf(ctx);

        if (DuelManager.get().isBusy(player)) {
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
        if (!reportDeckProblems(playerDeck, rule, player, player)) return 0;
        if (aiDeck != playerDeck) {
            var problems = DeckValidator.problems(aiDeck, rule);
            if (!problems.isEmpty()) {
                player.sendSystemMessage(Component.literal("The AI deck '" + aiDeckName
                        + "' is not legal: " + String.join("; ", problems)));
                return 0;
            }
        }
        String playerDeckName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        player.sendSystemMessage(Component.literal("Starting solo test duel vs AI (seed=" + seed
                + ", rule=" + rule.id() + ", lp=" + options.lp() + ", hand=" + options.startHand()
                + ", draw=" + options.drawPerTurn() + ")..."));
        DuelManager.get().startSoloDuel(player, seed, rule, options, playerDeck, aiDeck, playerDeckName,
                aiDeckName != null ? aiDeckName : playerDeckName);
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
                name.map(n -> "Current deck: " + n).orElse("No deck set; run /duel deck set <name>.")));
        return 1;
    }

    private static int deckClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DuelManager.get().clearPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal("Current deck cleared."));
        return 1;
    }

    /** Pending challenge: who challenged, the agreed seed, rule set and per-player options, and when it was sent. */
    public record PendingChallenge(UUID challengerUUID, long seed, DuelRule rule, PlayerOptions options,
                                   long sentAtMillis) {
        public boolean isExpired(long nowMillis) {
            return nowMillis - sentAtMillis > INVITE_TIMEOUT_MS;
        }
    }
}

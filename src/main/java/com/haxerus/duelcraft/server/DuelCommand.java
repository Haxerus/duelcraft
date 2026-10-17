package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.*;
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
                        .then(Commands.literal("invite")
                                .then(Commands.literal("decline").executes(ctx -> cancel(ctx, true)))
                                .then(Commands.literal("cancel").executes(ctx -> cancel(ctx, false))))
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
                                .then(Commands.literal("get")
                                        .executes(DuelCommand::deckGet))
                                .then(Commands.literal("clear")
                                        .executes(DuelCommand::deckClear)))
        );
    }

    private static int action(ServerPlayer player, PreparationCommand command) {
        var reply = PreparationPayloadHandler.apply(DuelManager.get().preparation(), player.getUUID(),
                new PreparationRequestPayload(UUID.randomUUID(), command), System.currentTimeMillis());
        if (reply.result() != PreparationResult.OK) {
            player.sendSystemMessage(Component.literal(reply.result() == PreparationResult.INELIGIBLE
                    ? "A deck is not ready. Each owner receives their own deck details."
                    : "Preparation: " + reply.result().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
            return 0;
        }
        String message = switch (command) {
            case PreparationCommand.Invite ignored -> "Duel invitation sent.";
            case PreparationCommand.Decline ignored -> "Duel invitation declined.";
            case PreparationCommand.Cancel ignored -> "Duel preparation cancelled.";
            case PreparationCommand.Hand ignored -> "Hand submitted.";
            default -> null;
        };
        if (message != null) player.sendSystemMessage(Component.literal(message));
        return 1;
    }
    private static PreparationView view(ServerPlayer player) {
        return DuelManager.get().preparation().view(player.getUUID(), System.currentTimeMillis());
    }
    private static int challenge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return action(ctx.getSource().getPlayerOrException(), new PreparationCommand.Invite(
                EntityArgument.getPlayer(ctx, "player").getUUID(), ruleOf(ctx), seedOf(ctx), playerOptionsOf(ctx)));
    }
    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var view = view(player);
        if (view.flowId() == null) { player.sendSystemMessage(Component.literal("No duel invitation.")); return 0; }
        return action(player, new PreparationCommand.Accept(view.flowId()));
    }
    private static int cancel(CommandContext<CommandSourceStack> ctx, boolean decline) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var view = view(player);
        if (view.flowId() == null) { player.sendSystemMessage(Component.literal("No duel preparation.")); return 0; }
        return action(player, decline ? new PreparationCommand.Decline(view.flowId())
                : new PreparationCommand.Cancel(view.flowId()));
    }
    private static int hand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var view = view(player);
        String name = StringArgumentType.getString(ctx, "hand"); var hand = FirstTurnLobby.parse(name);
        if (hand == null) throw UNKNOWN_HAND.create(name);
        if (view.roundId() == null) { player.sendSystemMessage(Component.literal("No first-turn roll to answer.")); return 0; }
        return action(player, new PreparationCommand.Hand(view.flowId(), view.roundId(), hand));
    }
    private static int first(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var view = view(player);
        String choice = StringArgumentType.getString(ctx, "choice");
        if (!choice.equalsIgnoreCase("yes") && !choice.equalsIgnoreCase("no")) { player.sendSystemMessage(Component.literal("Answer yes or no.")); return 0; }
        if (view.flowId() == null) { player.sendSystemMessage(Component.literal("No first-turn choice.")); return 0; }
        return action(player, new PreparationCommand.First(view.flowId(), choice.equalsIgnoreCase("yes")));
    }
    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        if (DuelManager.get().getPlayerActiveDuel(player) == null) return cancel(ctx, false);
        DuelManager.get().forfeit(player); return 1;
    }
    private static int test(CommandContext<CommandSourceStack> ctx, String aiDeckName) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        try {
            Deck ai = aiDeckName == null ? null : DuelManager.get().getDeckRegistry().load(aiDeckName);
            return DuelManager.get().startSoloDuel(player,
                    new DuelSettings(ruleOf(ctx), seedOf(ctx), playerOptionsOf(ctx)), ai) ? 1 : 0;
        } catch (IOException | DeckLoader.DeckParseException exception) {
            player.sendSystemMessage(Component.literal("Failed to load AI deck: " + exception.getMessage())); return 0;
        }
    }
    private static int deckGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var name = DuelManager.get().getPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal(name.map(n -> "Current deck: " + n).orElse("No active deck; run /duel deck set <name>.")));
        return 1;
    }
    private static int deckClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return DuelManager.get().clearPlayerCurrentDeck(ctx.getSource().getPlayerOrException()) ? 1 : 0;
    }
}

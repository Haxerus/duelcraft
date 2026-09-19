package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.CollectionClient;
import com.haxerus.duelcraft.client.collection.DeckImportService;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.client.interaction.PreparationRouting;
import net.neoforged.fml.loading.FMLEnvironment;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Local screen access and deck files. Other duel commands fall through to the server.
 */
public final class DuelClientCommand {

    private DuelClientCommand() { }

    public static void register(RegisterClientCommandsEvent event) {
        if (!FMLEnvironment.production) {
            event.getDispatcher().register(Commands.literal("duel")
                    .then(Commands.literal("collection").executes(DuelClientCommand::collection)));
        }
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("show")
                                .executes(DuelClientCommand::show))
                        .then(Commands.literal("deck")
                                .then(Commands.literal("list").executes(DuelClientCommand::deckList))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    try {
                                                        String prefix = builder.getRemainingLowerCase();
                                                        if (prefix.startsWith("\"")) prefix = prefix.substring(1);
                                                        for (String name : decks().listDeckNames()) {
                                                            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                                                                builder.suggest(StringArgumentType.escapeIfRequired(name));
                                                            }
                                                        }
                                                        return builder.buildFuture();
                                                    } catch (UncheckedIOException e) {
                                                        return builder.buildFuture();
                                                    }
                                                }).executes(DuelClientCommand::deckSet)))));
    }

    private static DeckRegistry decks() {
        return DeckRegistry.open(Minecraft.getInstance().gameDirectory.toPath().resolve("duelcraft/decks"));
    }

    private static int collection(CommandContext<CommandSourceStack> ctx) {
        var minecraft = Minecraft.getInstance();
        if (PreparationRouting.managementBlocked()) {
            ctx.getSource().sendFailure(Component.literal("Collection editing is unavailable during duel preparation or a duel."));
            return 1;
        }
        if (PreparationRouting.restoreEditor()) return 1;
        long generation = PreparationRouting.editorGeneration();
        var player = minecraft.player;
        DuelcraftClient.getCollectionClient().refresh().whenCompleteAsync((view, error) -> {
            if (minecraft.player != player || player == null
                    || generation != PreparationRouting.editorGeneration() || PreparationRouting.managementBlocked()) return;
            if (error == null) {
                minecraft.setScreen(CollectionScreen.create(view));
            } else {
                var message = error instanceof CollectionClient.RefreshRejectedException rejected
                        ? Component.translatable("duelcraft.collection.error_" + rejected.error().name().toLowerCase(Locale.ROOT), "")
                        : Component.literal("Collection access failed; try again.");
                player.sendSystemMessage(message);
            }
        }, minecraft::execute);
        return 1;
    }

    private static int deckList(CommandContext<CommandSourceStack> ctx) {
        try {
            var registry = decks();
            var names = registry.listDeckNames();
            ctx.getSource().sendSuccess(() -> Component.literal(names.isEmpty()
                    ? "No local decks. Drop .ydk files into " + registry.dir()
                    : "Local decks: " + String.join(", ", names)), false);
            return 1;
        } catch (UncheckedIOException e) {
            ctx.getSource().sendFailure(Component.literal("Cannot list local decks: " + e.getCause().getMessage()));
            return 0;
        }
    }

    private static int deckSet(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        try {
            var imports = new DeckImportService(decks());
            var deck = imports.load(name);
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            var collection = DuelcraftClient.getCollectionClient();
            collection.refresh().thenCompose(view ->
                    imports.saveThenActivate(view.revision(), deck, collection::request))
                    .whenCompleteAsync((result, error) -> {
                        if (minecraft.player != player || player == null) return;
                        if (error != null) {
                            player.sendSystemMessage(Component.literal("Cannot import local deck '" + name
                                    + "': " + root(error).getMessage()));
                        } else if (result.activated()) {
                            player.sendSystemMessage(Component.literal("Imported and activated local deck '" + name + "'."));
                        } else if (result.saved() != null) {
                            player.sendSystemMessage(Component.literal("Imported local deck '" + name
                                    + "', but it remains inactive: " + explain(result.activation())));
                        } else {
                            player.sendSystemMessage(Component.literal("Cannot import local deck '" + name
                                    + "': " + explain(result.activation())));
                        }
                    }, minecraft::execute);
            ctx.getSource().sendSuccess(() -> Component.literal("Importing local deck '" + name + "'…"), false);
            return 1;
        } catch (IOException | UncheckedIOException | DeckLoader.DeckParseException | IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Cannot set local deck '" + name + "': " + e.getMessage()));
            return 0;
        }
    }

    private static String explain(CollectionReply reply) {
        if (!(reply instanceof CollectionReply.Rejected rejected)) return "unexpected server acknowledgement";
        var report = rejected.eligibility();
        if (report.restrictionReason() != null) return report.restrictionReason();
        if (report.ownershipRequired() && !report.missing().isEmpty()) {
            int missing = report.missing().values().stream().mapToInt(Integer::intValue).sum();
            return missing + " deposited card cop" + (missing == 1 ? "y is" : "ies are") + " missing";
        }
        if (!report.problems().isEmpty()) {
            return report.problems().size() + " supported legality problem"
                    + (report.problems().size() == 1 ? "" : "s");
        }
        return rejected.error().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static Throwable root(Throwable error) {
        while (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
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

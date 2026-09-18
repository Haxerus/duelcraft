package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionReply;
import net.neoforged.fml.loading.FMLEnvironment;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.server.DuelDeckPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
        var player = minecraft.player;
        DuelcraftClient.getCollectionClient().request(new CollectionCommand.Open()).whenCompleteAsync((reply, error) -> {
            if (minecraft.player != player || player == null) return;
            if (error == null && reply instanceof CollectionReply.Opened) {
                minecraft.setScreen(CollectionScreen.create());
            } else {
                var message = error == null && reply instanceof CollectionReply.Rejected rejected
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
            var deck = decks().load(name);
            PacketDistributor.sendToServer(new DuelDeckPayload(name, deck));
            return 1; // The server acknowledges only after validation.
        } catch (IOException | UncheckedIOException | DeckLoader.DeckParseException | IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Cannot set local deck '" + name + "': " + e.getMessage()));
            return 0;
        }
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

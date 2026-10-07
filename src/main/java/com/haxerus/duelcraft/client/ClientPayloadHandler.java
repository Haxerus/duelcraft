package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.server.DuelEndPayload;
import com.haxerus.duelcraft.server.DuelMessagePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.haxerus.duelcraft.server.collection.CollectionReplyPayload;
import java.util.Objects;
import java.util.function.Consumer;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public class ClientPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Consumer<CollectionReplyPayload> collectionReceiver = ignored -> {};
    private static Consumer<com.haxerus.duelcraft.duel.message.DuelMessage> duelObserver = ignored -> {};

    public static Consumer<com.haxerus.duelcraft.duel.message.DuelMessage> observeDuelMessages(
            Consumer<com.haxerus.duelcraft.duel.message.DuelMessage> observer) {
        if (net.neoforged.fml.loading.FMLEnvironment.production) throw new IllegalStateException("DEV-only duel observer");
        var previous = duelObserver;
        duelObserver = Objects.requireNonNull(observer);
        return previous;
    }

    public static void setCollectionReceiver(Consumer<CollectionReplyPayload> receiver) {
        collectionReceiver = Objects.requireNonNull(receiver);
    }

    public static void handleCollection(CollectionReplyPayload payload, IPayloadContext context) {
        collectionReceiver.accept(payload);
    }

    public static void handlePreparation(com.haxerus.duelcraft.server.PreparationStatePayload payload, IPayloadContext context) {
        var previous = com.haxerus.duelcraft.DuelcraftClient.preparation().view();
        if (com.haxerus.duelcraft.DuelcraftClient.preparation().receive(payload)) {
            com.haxerus.duelcraft.client.interaction.PreparationRouting.apply(payload);
            var view = payload.view();
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && view.mode() == com.haxerus.duelcraft.duel.PreparationView.Mode.INVITED
                    && !view.outgoing() && (previous == null || !Objects.equals(previous.flowId(), view.flowId()))) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Open invitation")
                        .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
                                .withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/duel lobby"))));
            }
        }
    }

    public static void handleInteraction(com.haxerus.duelcraft.server.InteractionStatePayload payload, IPayloadContext context) {
        com.haxerus.duelcraft.client.interaction.DuelcraftScreens.apply(payload);
    }

    public static void handlePreparationNotice(com.haxerus.duelcraft.server.PreparationNoticePayload payload, IPayloadContext context) {
        com.haxerus.duelcraft.DuelcraftClient.preparation().notice(payload.detail());
    }

    public static void handleStart(DuelStartPayload payload, IPayloadContext context) {
        LOGGER.info("Duel starting — player {}, opponent: {}, LP={}|{}, deck={}, extra={}",
                payload.localPlayer(), payload.opponentName(),
                payload.lp0(), payload.lp1(), payload.deckSize(), payload.extraSize());
        com.haxerus.duelcraft.client.interaction.PreparationRouting.suspendEditor();
        LDLibDuelScreen.open(payload);
    }

    public static void handleMessage(DuelMessagePayload payload, IPayloadContext context) {
        duelObserver.accept(payload.message());
        LDLibDuelScreen.applyMessage(payload.message());
    }

    public static void handleEnd(DuelEndPayload payload, IPayloadContext context) {
        LOGGER.info("Duel ended — winner: {}, reason: {}", payload.winner(), payload.reason());
        // The screen stays open on the result overlay; its Close button tears it down.
        LDLibDuelScreen.showResult(payload.winner(), payload.reason());
    }
}

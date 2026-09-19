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

    public static void setCollectionReceiver(Consumer<CollectionReplyPayload> receiver) {
        collectionReceiver = Objects.requireNonNull(receiver);
    }

    public static void handleCollection(CollectionReplyPayload payload, IPayloadContext context) {
        collectionReceiver.accept(payload);
    }

    public static void handlePreparation(com.haxerus.duelcraft.server.PreparationStatePayload payload, IPayloadContext context) {
        if (com.haxerus.duelcraft.DuelcraftClient.preparation().receive(payload)) {
            com.haxerus.duelcraft.client.interaction.PreparationRouting.apply(payload);
        }
    }

    public static void handleStart(DuelStartPayload payload, IPayloadContext context) {
        LOGGER.info("Duel starting — player {}, opponent: {}, LP={}|{}, deck={}, extra={}",
                payload.localPlayer(), payload.opponentName(),
                payload.lp0(), payload.lp1(), payload.deckSize(), payload.extraSize());
        com.haxerus.duelcraft.client.interaction.PreparationRouting.suspendEditor();
        LDLibDuelScreen.open(payload);
    }

    public static void handleMessage(DuelMessagePayload payload, IPayloadContext context) {
        LDLibDuelScreen.applyMessage(payload.message());
    }

    public static void handleEnd(DuelEndPayload payload, IPayloadContext context) {
        LOGGER.info("Duel ended — winner: {}, reason: {}", payload.winner(), payload.reason());
        // The screen stays open on the result overlay; its Close button tears it down.
        LDLibDuelScreen.showResult(payload.winner(), payload.reason());
    }
}

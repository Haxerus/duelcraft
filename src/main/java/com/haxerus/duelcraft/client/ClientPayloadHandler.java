package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.server.DuelEndPayload;
import com.haxerus.duelcraft.server.DuelMessagePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public class ClientPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleStart(DuelStartPayload payload, IPayloadContext context) {
        LOGGER.info("Duel starting — player {}, opponent: {}, LP={}|{}, deck={}, extra={}",
                payload.localPlayer(), payload.opponentName(),
                payload.lp0(), payload.lp1(), payload.deckSize(), payload.extraSize());
        LDLibDuelScreen.open(payload);
    }

    public static void handleMessage(DuelMessagePayload payload, IPayloadContext context) {
        LDLibDuelScreen.applyMessage(payload.message());
    }

    public static void handleEnd(DuelEndPayload payload, IPayloadContext context) {
        LOGGER.info("Duel ended — winner: {}, reason: {}", payload.winner(), payload.reason());
        LDLibDuelScreen.close();
        Minecraft.getInstance().setScreen(null);
    }
}

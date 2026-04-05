package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.Duelcraft;
import com.haxerus.duelcraft.server.DuelEndPayload;
import com.haxerus.duelcraft.server.DuelMessagePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleMessage(DuelMessagePayload payload, IPayloadContext context) {
        // TODO: update client duel state / UI
        Duelcraft.LOGGER.debug("Received duel message type {}", payload.msgType());
    }

    public static void handleEnd(DuelEndPayload payload, IPayloadContext context) {
        // TODO: close duel screen, show result
        Duelcraft.LOGGER.debug("Duel ended, winner: {}", payload.winner());
    }
}

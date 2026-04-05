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
        LOGGER.info("Duel starting — we are player {}, opponent: {}",
                payload.localPlayer(), payload.opponentName());
        Minecraft.getInstance().setScreen(new DuelScreen(payload.localPlayer(), payload.opponentName()));
    }

    public static void handleMessage(DuelMessagePayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof DuelScreen screen) {
            screen.applyMessage(payload);
        }
    }

    public static void handleEnd(DuelEndPayload payload, IPayloadContext context) {
        LOGGER.info("Duel ended — winner: {}, reason: {}", payload.winner(), payload.reason());
        Minecraft.getInstance().setScreen(null);
    }
}

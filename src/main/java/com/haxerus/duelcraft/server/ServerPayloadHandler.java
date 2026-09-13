package com.haxerus.duelcraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {
    public static void handleDeck(DuelDeckPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        try {
            DuelManager.get().setPlayerCurrentDeck(player.getUUID(), payload);
            player.sendSystemMessage(Component.literal("Current deck set to '" + payload.name() + "'."));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("Cannot set deck: " + e.getMessage()));
        }
    }

    public static void handleResponse(DuelResponsePayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        DuelManager.get().handleResponse(player, payload.response());
    }

    public static void handleConcede(DuelConcedePayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        DuelManager.get().forfeit(player);
    }
}

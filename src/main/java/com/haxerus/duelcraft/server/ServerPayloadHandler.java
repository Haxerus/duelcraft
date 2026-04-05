package com.haxerus.duelcraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {
    public static void handleResponse(DuelResponsePayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        DuelManager.get().handleResponse(player, payload.response());
    }
}

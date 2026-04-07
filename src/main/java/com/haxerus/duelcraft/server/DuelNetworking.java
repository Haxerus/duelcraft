package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.client.ClientPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class DuelNetworking {
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Server → Client (handlers run on client only)
        registrar.playToClient(DuelStartPayload.TYPE, DuelStartPayload.STREAM_CODEC,
                ClientPayloadHandler::handleStart);
        registrar.playToClient(DuelMessagePayload.TYPE, DuelMessagePayload.STREAM_CODEC,
                ClientPayloadHandler::handleMessage);
        registrar.playToClient(DuelEndPayload.TYPE, DuelEndPayload.STREAM_CODEC,
                ClientPayloadHandler::handleEnd);

        // Client → Server
        registrar.playToServer(DuelResponsePayload.TYPE, DuelResponsePayload.STREAM_CODEC,
                ServerPayloadHandler::handleResponse);
    }
}

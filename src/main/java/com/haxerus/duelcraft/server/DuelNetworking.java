package com.haxerus.duelcraft.server;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class DuelNetworking {
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(DuelStartPayload.TYPE, DuelStartPayload.STREAM_CODEC);
        registrar.playToClient(DuelMessagePayload.TYPE, DuelMessagePayload.STREAM_CODEC);
        registrar.playToClient(DuelEndPayload.TYPE, DuelEndPayload.STREAM_CODEC);
        registrar.playToServer(DuelResponsePayload.TYPE, DuelResponsePayload.STREAM_CODEC, ServerPayloadHandler::handleResponse);
    }
}

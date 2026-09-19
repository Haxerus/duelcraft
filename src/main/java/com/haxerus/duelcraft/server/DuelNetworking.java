package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.client.ClientPayloadHandler;
import com.haxerus.duelcraft.server.collection.CollectionPayloadHandler;
import com.haxerus.duelcraft.server.collection.CollectionRequestPayload;
import com.haxerus.duelcraft.server.collection.CollectionReplyPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class DuelNetworking {
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");

        // Server → Client (handlers run on client only)
        registrar.playToClient(CollectionReplyPayload.TYPE, CollectionReplyPayload.STREAM_CODEC,
                ClientPayloadHandler::handleCollection);
        registrar.playToClient(DuelStartPayload.TYPE, DuelStartPayload.STREAM_CODEC,
                ClientPayloadHandler::handleStart);
        registrar.playToClient(DuelMessagePayload.TYPE, DuelMessagePayload.STREAM_CODEC,
                ClientPayloadHandler::handleMessage);
        registrar.playToClient(DuelEndPayload.TYPE, DuelEndPayload.STREAM_CODEC,
                ClientPayloadHandler::handleEnd);

        // Client → Server
        registrar.playToServer(CollectionRequestPayload.TYPE, CollectionRequestPayload.STREAM_CODEC,
                CollectionPayloadHandler::handle);
        registrar.playToServer(DuelDeckPayload.TYPE, DuelDeckPayload.STREAM_CODEC,
                ServerPayloadHandler::handleDeck);
        registrar.playToServer(DuelResponsePayload.TYPE, DuelResponsePayload.STREAM_CODEC,
                ServerPayloadHandler::handleResponse);
        registrar.playToServer(DuelConcedePayload.TYPE, DuelConcedePayload.STREAM_CODEC,
                ServerPayloadHandler::handleConcede);
    }
}

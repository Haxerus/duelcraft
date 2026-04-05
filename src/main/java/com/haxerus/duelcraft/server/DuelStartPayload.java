package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → Client: sent when a duel begins, before any duel messages.
 * Tells the client which player they are, the opponent's name, and initial game state.
 */
public record DuelStartPayload(
        int localPlayer,
        String opponentName,
        int lp0,
        int lp1,
        int deckSize,
        int extraSize
) implements CustomPacketPayload {

    public static final Type<DuelStartPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("duelcraft", "duel_start"));

    public static final StreamCodec<ByteBuf, DuelStartPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelStartPayload::localPlayer,
                    ByteBufCodecs.STRING_UTF8, DuelStartPayload::opponentName,
                    ByteBufCodecs.VAR_INT, DuelStartPayload::lp0,
                    ByteBufCodecs.VAR_INT, DuelStartPayload::lp1,
                    ByteBufCodecs.VAR_INT, DuelStartPayload::deckSize,
                    ByteBufCodecs.VAR_INT, DuelStartPayload::extraSize,
                    DuelStartPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

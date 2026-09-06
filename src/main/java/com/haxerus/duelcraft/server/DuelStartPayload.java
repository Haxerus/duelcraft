package com.haxerus.duelcraft.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: sent when a duel begins, before any duel messages.
 * Tells the client which player they are, the opponent's name, initial game state,
 * and the engine flags the client derives the field layout from.
 */
public record DuelStartPayload(
        int localPlayer,
        String opponentName,
        int lp0,
        int lp1,
        int deckSize,
        int extraSize,
        long duelFlags
) implements CustomPacketPayload {

    public static final Type<DuelStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "duel_start"));

    // Hand-written because StreamCodec.composite stops at six fields.
    public static final StreamCodec<FriendlyByteBuf, DuelStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.localPlayer());
                buf.writeUtf(payload.opponentName());
                buf.writeVarInt(payload.lp0());
                buf.writeVarInt(payload.lp1());
                buf.writeVarInt(payload.deckSize());
                buf.writeVarInt(payload.extraSize());
                buf.writeLong(payload.duelFlags());
            },
            buf -> new DuelStartPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

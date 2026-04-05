package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DuelResponsePayload(byte[] response) implements CustomPacketPayload {

    public static final Type<DuelResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("duelcraft", "duel_response"));

    public static final StreamCodec<ByteBuf, DuelResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE_ARRAY, DuelResponsePayload::response,
                    DuelResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DuelMessagePayload(int msgType, byte[] data) implements CustomPacketPayload {

    public static final Type<DuelMessagePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("duelcraft", "duel_message"));

    public static final StreamCodec<ByteBuf, DuelMessagePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelMessagePayload::msgType,
                    ByteBufCodecs.BYTE_ARRAY, DuelMessagePayload::data,
                    DuelMessagePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

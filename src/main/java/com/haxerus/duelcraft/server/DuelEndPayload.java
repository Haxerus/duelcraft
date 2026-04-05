package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DuelEndPayload(int winner, int reason) implements CustomPacketPayload {

    public static final Type<DuelEndPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("duelcraft", "duel_end"));

    public static final StreamCodec<ByteBuf, DuelEndPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DuelEndPayload::winner,
                    ByteBufCodecs.VAR_INT, DuelEndPayload::reason,
                    DuelEndPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


}

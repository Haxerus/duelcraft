package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The sending player gives up the duel they are in; the server treats it as a forfeit. */
public record DuelConcedePayload() implements CustomPacketPayload {

    public static final Type<DuelConcedePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "duel_concede"));

    public static final StreamCodec<ByteBuf, DuelConcedePayload> STREAM_CODEC =
            StreamCodec.unit(new DuelConcedePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.DuelMessageCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DuelMessagePayload(DuelMessage message) implements CustomPacketPayload {

    public static final Type<DuelMessagePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("duelcraft", "duel_message"));

    public static final StreamCodec<FriendlyByteBuf, DuelMessagePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> DuelMessageCodec.encode(buf, payload.message()),
                    buf -> new DuelMessagePayload(DuelMessageCodec.decode(buf))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

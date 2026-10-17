package com.haxerus.duelcraft.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A private deck-policy explanation sent only to the affected owner. */
public record PreparationNoticePayload(String detail) implements CustomPacketPayload {
    public static final Type<PreparationNoticePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "preparation_notice"));
    public static final StreamCodec<ByteBuf, PreparationNoticePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> new FriendlyByteBuf(buffer).writeUtf(value.detail(), 2048),
            buffer -> {
                var value = new PreparationNoticePayload(new FriendlyByteBuf(buffer).readUtf(2048));
                if (buffer.isReadable()) throw new IllegalArgumentException("Trailing notice data");
                return value;
            });
    public PreparationNoticePayload {
        if (detail == null || detail.isBlank() || detail.length() > 2048) throw new IllegalArgumentException("Invalid preparation notice");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

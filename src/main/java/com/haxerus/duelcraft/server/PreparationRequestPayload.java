package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.PreparationCommand;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

public record PreparationRequestPayload(UUID requestId, PreparationCommand command) implements CustomPacketPayload {
    public static final Type<PreparationRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "preparation_request"));
    public static final StreamCodec<ByteBuf, PreparationRequestPayload> STREAM_CODEC = StreamCodec.of(PreparationWire::encodeRequest, PreparationWire::decodeRequest);
    public PreparationRequestPayload { Objects.requireNonNull(requestId); Objects.requireNonNull(command); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

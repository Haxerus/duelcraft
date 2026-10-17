package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionCommand;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

public record CollectionRequestPayload(UUID requestId, CollectionCommand command) implements CustomPacketPayload {
    public static final Type<CollectionRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "collection_request"));
    public static final StreamCodec<ByteBuf, CollectionRequestPayload> STREAM_CODEC = StreamCodec.of(CollectionWire::encodeRequest, CollectionWire::decodeRequest);
    public CollectionRequestPayload { Objects.requireNonNull(requestId); Objects.requireNonNull(command); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

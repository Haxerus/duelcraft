package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionReply;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

public record CollectionReplyPayload(UUID requestId, CollectionReply reply) implements CustomPacketPayload {
    public static final Type<CollectionReplyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "collection_reply"));
    public static final StreamCodec<ByteBuf, CollectionReplyPayload> STREAM_CODEC = StreamCodec.of(CollectionWire::encodeReply, CollectionWire::decodeReply);
    public CollectionReplyPayload { Objects.requireNonNull(requestId); Objects.requireNonNull(reply); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

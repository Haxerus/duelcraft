package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.interaction.ScreenRoute;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

public record InteractionRequestPayload(UUID requestId, ScreenRoute requested) implements CustomPacketPayload {
    public static final Type<InteractionRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "interaction_request"));
    public static final StreamCodec<ByteBuf, InteractionRequestPayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        var buf = new FriendlyByteBuf(buffer);
        buf.writeUUID(value.requestId()); buf.writeByte(value.requested().ordinal());
    }, buffer -> {
        var buf = new FriendlyByteBuf(buffer);
        var result = new InteractionRequestPayload(buf.readUUID(), route(buf));
        if (buf.isReadable()) throw new IllegalArgumentException("Trailing interaction data");
        return result;
    });
    public InteractionRequestPayload {
        Objects.requireNonNull(requestId); Objects.requireNonNull(requested);
        if (requested == ScreenRoute.DUEL) throw new IllegalArgumentException("Only the server can resolve a live duel");
    }
    static ScreenRoute route(FriendlyByteBuf buf) {
        int tag = buf.readUnsignedByte();
        if (tag >= ScreenRoute.values().length) throw new IllegalArgumentException("Unknown screen route");
        return ScreenRoute.values()[tag];
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

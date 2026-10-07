package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

public record InteractionStatePayload(UUID requestId, ScreenRoute resolved, PreparationView preparation,
                                      boolean unsolicited) implements CustomPacketPayload {
    public static final Type<InteractionStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "interaction_state"));
    public static final StreamCodec<ByteBuf, InteractionStatePayload> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
        var buf = new FriendlyByteBuf(buffer);
        buf.writeUUID(value.requestId()); buf.writeByte(value.resolved().ordinal()); buf.writeBoolean(value.unsolicited());
        PreparationWire.encodeState(buf, new PreparationStatePayload(null, PreparationResult.OK, value.preparation()));
    }, buffer -> {
        var buf = new FriendlyByteBuf(buffer);
        var id = buf.readUUID(); var route = InteractionRequestPayload.route(buf); boolean unsolicited = buf.readBoolean();
        return new InteractionStatePayload(id, route, PreparationWire.decodeState(buf).view(), unsolicited);
    });
    public InteractionStatePayload {
        Objects.requireNonNull(requestId); Objects.requireNonNull(resolved); Objects.requireNonNull(preparation);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

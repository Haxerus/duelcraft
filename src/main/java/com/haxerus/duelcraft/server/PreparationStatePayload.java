package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

public record PreparationStatePayload(@Nullable UUID requestId, PreparationResult result, PreparationView view) implements CustomPacketPayload {
    public static final Type<PreparationStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "preparation_state"));
    public static final StreamCodec<ByteBuf, PreparationStatePayload> STREAM_CODEC = StreamCodec.of(PreparationWire::encodeState, PreparationWire::decodeState);
    public PreparationStatePayload { Objects.requireNonNull(result); Objects.requireNonNull(view); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
import java.util.function.Consumer;

/** Explicit command tags, bounded enum/rule IDs, and an envelope budget. */
final class PreparationWire {
    private static final int MAX_BYTES = 24 * 1024;
    private PreparationWire() {}
    static void encodeRequest(ByteBuf buffer, PreparationRequestPayload payload) {
        encode(buffer, buf -> {
            buf.writeUUID(payload.requestId());
            switch (payload.command()) {
                case PreparationCommand.View ignored -> buf.writeByte(0);
                case PreparationCommand.Invite invite -> {
                    buf.writeByte(1); buf.writeUUID(invite.target()); buf.writeUtf(invite.rule().id(), 16);
                    buf.writeBoolean(invite.seed() != null); if (invite.seed() != null) buf.writeLong(invite.seed()); options(buf, invite.options());
                }
                case PreparationCommand.Accept accept -> { buf.writeByte(2); buf.writeUUID(accept.invitationId()); }
                case PreparationCommand.Decline decline -> { buf.writeByte(3); buf.writeUUID(decline.invitationId()); }
                case PreparationCommand.Cancel cancel -> { buf.writeByte(4); buf.writeUUID(cancel.flowId()); }
                case PreparationCommand.Hand hand -> { buf.writeByte(5); buf.writeUUID(hand.flowId()); buf.writeUUID(hand.roundId()); buf.writeByte(hand.hand().ordinal()); }
                case PreparationCommand.First first -> { buf.writeByte(6); buf.writeUUID(first.flowId()); buf.writeBoolean(first.goFirst()); }
            }
        });
    }
    static PreparationRequestPayload decodeRequest(ByteBuf buffer) {
        var buf = input(buffer); var id = buf.readUUID();
        PreparationCommand command = switch (buf.readUnsignedByte()) {
            case 0 -> new PreparationCommand.View();
            case 1 -> new PreparationCommand.Invite(buf.readUUID(), rule(buf), buf.readBoolean() ? buf.readLong() : null, options(buf));
            case 2 -> new PreparationCommand.Accept(buf.readUUID());
            case 3 -> new PreparationCommand.Decline(buf.readUUID());
            case 4 -> new PreparationCommand.Cancel(buf.readUUID());
            case 5 -> new PreparationCommand.Hand(buf.readUUID(), buf.readUUID(), tag(buf, FirstTurnLobby.Hand.values()));
            case 6 -> new PreparationCommand.First(buf.readUUID(), buf.readBoolean());
            default -> throw new IllegalArgumentException("Unknown preparation command");
        };
        complete(buf); return new PreparationRequestPayload(id, command);
    }
    static void encodeState(ByteBuf buffer, PreparationStatePayload payload) {
        encode(buffer, buf -> {
            optionalId(buf, payload.requestId()); buf.writeByte(payload.result().ordinal());
            var view = payload.view(); buf.writeVarLong(view.revision()); buf.writeByte(view.mode().ordinal());
            optionalId(buf, view.flowId()); optionalId(buf, view.roundId()); optionalId(buf, view.opponentId());
            buf.writeUtf(view.opponentName(), 128); buf.writeBoolean(view.outgoing()); buf.writeBoolean(view.ownHandSubmitted());
            buf.writeBoolean(view.canChooseFirst()); buf.writeVarLong(view.remainingMillis());
            buf.writeBoolean(view.settings() != null);
            if (view.settings() != null) { buf.writeUtf(view.settings().rule().id(), 16); buf.writeLong(view.settings().seed()); options(buf, view.settings().options()); }
        });
    }
    static PreparationStatePayload decodeState(ByteBuf buffer) {
        var buf = input(buffer); var request = optionalId(buf); var result = tag(buf, PreparationResult.values());
        long revision = buf.readVarLong(); var mode = tag(buf, PreparationView.Mode.values());
        var flow = optionalId(buf); var round = optionalId(buf); var opponent = optionalId(buf); var name = buf.readUtf(128);
        boolean outgoing = buf.readBoolean(), submitted = buf.readBoolean(), chooser = buf.readBoolean(); long remaining = buf.readVarLong();
        var settings = buf.readBoolean() ? new DuelSettings(rule(buf), buf.readLong(), options(buf)) : null;
        var view = new PreparationView(revision, mode, flow, round, opponent, name, outgoing, submitted, chooser, remaining, settings);
        complete(buf); return new PreparationStatePayload(request, result, view);
    }
    private static void options(FriendlyByteBuf buf, PlayerOptions options) {
        buf.writeVarInt(options.lp()); buf.writeVarInt(options.startHand()); buf.writeVarInt(options.drawPerTurn());
    }
    private static PlayerOptions options(FriendlyByteBuf buf) { return new PlayerOptions(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }
    private static DuelRule rule(FriendlyByteBuf buf) { return DuelRule.parse(buf.readUtf(16)).orElseThrow(() -> new IllegalArgumentException("Unknown duel rule")); }
    private static void optionalId(FriendlyByteBuf buf, UUID id) { buf.writeBoolean(id != null); if (id != null) buf.writeUUID(id); }
    private static UUID optionalId(FriendlyByteBuf buf) { return buf.readBoolean() ? buf.readUUID() : null; }
    private static <T> T tag(FriendlyByteBuf buf, T[] values) {
        int tag = buf.readUnsignedByte(); if (tag >= values.length) throw new IllegalArgumentException("Unknown preparation tag"); return values[tag];
    }
    private static FriendlyByteBuf input(ByteBuf buf) {
        if (buf.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Preparation packet too large");
        return new FriendlyByteBuf(buf);
    }
    private static void complete(FriendlyByteBuf buf) { if (buf.isReadable()) throw new IllegalArgumentException("Trailing preparation data"); }
    private static void encode(ByteBuf target, Consumer<FriendlyByteBuf> write) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { write.accept(buf); if (buf.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Preparation packet too large"); target.writeBytes(buf); }
        finally { buf.release(); }
    }
}

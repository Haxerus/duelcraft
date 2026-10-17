package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.client.interaction.ClientPreparationState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PreparationPayloadTest {
    static PreparationView idle(long revision) { return new PreparationView(revision, PreparationView.Mode.IDLE, null, null, null, "", false, false, false, 0, null); }
    @Test void allCommandsAndSettingsRoundTrip() {
        var id = UUID.randomUUID(); var round = UUID.randomUUID();
        for (var command : List.of(new PreparationCommand.View(), new PreparationCommand.Invite(id, DuelRule.RUSH, null, PlayerOptions.standard()),
                new PreparationCommand.Invite(id, DuelRule.MR1, Long.MIN_VALUE, new PlayerOptions(99999, 20, 10)), new PreparationCommand.Accept(id),
                new PreparationCommand.Decline(id), new PreparationCommand.Cancel(id), new PreparationCommand.Hand(id, round, FirstTurnLobby.Hand.PAPER), new PreparationCommand.First(id, false))) {
            var payload = new PreparationRequestPayload(UUID.randomUUID(), command); var buf = Unpooled.buffer();
            try { PreparationRequestPayload.STREAM_CODEC.encode(buf, payload); assertTrue(buf.readableBytes() <= 24576); assertEquals(payload, PreparationRequestPayload.STREAM_CODEC.decode(buf)); assertEquals(0, buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
    @Test void allRecipientViewsAndOptionalRequestRoundTrip() {
        for (var mode : PreparationView.Mode.values()) for (boolean requested : List.of(false, true)) {
            boolean active = mode != PreparationView.Mode.IDLE && mode != PreparationView.Mode.DUEL;
            var view = new PreparationView(15, mode, active ? UUID.randomUUID() : null, mode == PreparationView.Mode.RPS ? UUID.randomUUID() : null,
                    active ? UUID.randomUUID() : null, active ? "界".repeat(128) : "", active, mode == PreparationView.Mode.RPS,
                    mode == PreparationView.Mode.FIRST_CHOICE, active && mode != PreparationView.Mode.STARTING ? 60000 : 0,
                    active ? new DuelSettings(DuelRule.MR5, -42, PlayerOptions.standard()) : null);
            var payload = new PreparationStatePayload(requested ? UUID.randomUUID() : null, PreparationResult.OK, view);
            var buf = Unpooled.buffer();
            try { PreparationStatePayload.STREAM_CODEC.encode(buf, payload); assertTrue(buf.readableBytes() <= 24576); assertEquals(payload, PreparationStatePayload.STREAM_CODEC.decode(buf)); assertEquals(0, buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
    @Test void malformedTagRuleOptionsAndOversizedInputRejected() {
        for (int kind = 0; kind < 4; kind++) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeUUID(UUID.randomUUID()); buf.writeByte(kind == 0 ? 99 : 1);
                if (kind > 0) { buf.writeUUID(UUID.randomUUID()); buf.writeUtf(kind == 1 ? "invalid" : "mr5"); buf.writeBoolean(false); buf.writeVarInt(kind == 2 ? 0 : 8000); buf.writeVarInt(5); buf.writeVarInt(1); }
                if (kind == 3) buf.writeZero(24576);
                assertThrows(RuntimeException.class, () -> PreparationRequestPayload.STREAM_CODEC.decode(buf));
            } finally { buf.release(); }
        }
    }
    @Test void equalOrOlderReplyStillResolvesFutureWithoutRegressingStateAndDisconnectClearsPending() {
        var sent = new ArrayList<PreparationRequestPayload>();
        var client = new ClientPreparationState(sent::add); client.connect();
        var request = client.request(new PreparationCommand.View());
        client.receive(new PreparationStatePayload(null, PreparationResult.OK, idle(5)));
        client.receive(new PreparationStatePayload(sent.getLast().requestId(), PreparationResult.OK, idle(5)));
        assertTrue(request.isDone()); assertEquals(5, client.view().revision());
        var older = client.request(new PreparationCommand.View());
        client.receive(new PreparationStatePayload(sent.getLast().requestId(), PreparationResult.STALE, idle(2)));
        assertTrue(older.isDone()); assertEquals(5, client.view().revision());
        var pending = client.request(new PreparationCommand.View()); client.disconnect();
        assertTrue(pending.isCompletedExceptionally()); assertNull(client.view());
        client.receive(new PreparationStatePayload(null, PreparationResult.OK, idle(99))); assertNull(client.view());
    }
    @Test void malformedStateTagsRevisionNameAndTimeAreRejected() {
        for (int kind = 0; kind < 5; kind++) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeBoolean(false); buf.writeByte(kind == 0 ? 99 : 0);
                buf.writeVarLong(kind == 2 ? -1 : 0); buf.writeByte(kind == 1 ? 99 : 0);
                buf.writeBoolean(false); buf.writeBoolean(false); buf.writeBoolean(false);
                buf.writeUtf(kind == 3 ? "x".repeat(129) : "");
                buf.writeBoolean(false); buf.writeBoolean(false); buf.writeBoolean(false);
                buf.writeVarLong(kind == 4 ? 60001 : 0); buf.writeBoolean(false);
                assertThrows(RuntimeException.class, () -> PreparationStatePayload.STREAM_CODEC.decode(buf));
            } finally { buf.release(); }
        }
    }

}

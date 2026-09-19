package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionPayloadTest {
    static final UUID ID = UUID.randomUUID();
    static final SavedDeck DECK = new SavedDeck(ID, "Draft", new DeckList(List.of(1), List.of(2), List.of(3)));
    static final DeckEligibility.Report REPORT = new DeckEligibility.Report(List.of(new DeckEligibility.Issue("missing", 1, 2, 3)), Map.of(1, 2), true, true, "Era locked");
    @Test void allCommandsRoundTrip() {
        for (var command : List.of(new CollectionCommand.Open(), new CollectionCommand.Page(ID, CollectionCommand.PageKind.COUNTS, 0),
                new CollectionCommand.Page(ID, CollectionCommand.PageKind.DECKS, 1), new CollectionCommand.ReadDeck(ID),
                new CollectionCommand.Save(0, DECK), new CollectionCommand.Delete(1, ID), new CollectionCommand.Activate(2, ID), new CollectionCommand.ClearActive(3))) {
            var payload = new CollectionRequestPayload(ID, command); var buf = Unpooled.buffer();
            try { CollectionRequestPayload.STREAM_CODEC.encode(buf, payload); assertEquals(payload, CollectionRequestPayload.STREAM_CODEC.decode(buf)); assertEquals(0, buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
    @Test void allRepliesRoundTripIncludingOptionalFields() {
        for (var reply : List.of(new CollectionReply.Opened(ID, 0, 2, 1, ID), new CollectionReply.Opened(ID, 0, 0, 0, null),
                new CollectionReply.Counts(ID, 1, 0, Map.of(1, Long.MAX_VALUE)), new CollectionReply.Decks(ID, 1, 0, List.of(new CollectionReply.Summary(ID, "Draft", 1, 2, 3))),
                new CollectionReply.Deck(1, DECK), new CollectionReply.Changed(2, ID, DECK, 0, REPORT), new CollectionReply.Changed(2, null, null, 0, REPORT), new CollectionReply.Rejected(CollectionError.STALE, 2, REPORT))) {
            var payload = new CollectionReplyPayload(ID, reply); var buf = Unpooled.buffer();
            try { CollectionReplyPayload.STREAM_CODEC.encode(buf, payload); assertEquals(payload, CollectionReplyPayload.STREAM_CODEC.decode(buf)); assertEquals(0, buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
    @Test void rejectsUnknownTagsOversizeAndTrailingInput() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(ID); buf.writeByte(127);
            assertThrows(RuntimeException.class, () -> CollectionRequestPayload.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeZero(CollectionLimits.PACKET_BYTES + 1);
            assertThrows(RuntimeException.class, () -> CollectionReplyPayload.STREAM_CODEC.decode(buf));
            buf.clear(); CollectionRequestPayload.STREAM_CODEC.encode(buf, new CollectionRequestPayload(ID, new CollectionCommand.Open())); buf.writeByte(0);
            assertThrows(RuntimeException.class, () -> CollectionRequestPayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
    @Test void rejectsInvalidCommandFieldsAndAggregateDraftSizes() {
        badRequest(buf -> { buf.writeByte(1); buf.writeUUID(ID); buf.writeByte(2); buf.writeVarInt(0); });
        badRequest(buf -> { buf.writeByte(1); buf.writeUUID(ID); buf.writeByte(0); buf.writeVarInt(-1); });
        badRequest(buf -> { buf.writeByte(6); buf.writeVarLong(-1); });
        badRequest(buf -> { buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("x".repeat(129)); });
        badRequest(buf -> { buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("bad\nname"); });
        badRequest(buf -> { buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("Draft"); buf.writeVarInt(-1); });
        badRequest(buf -> { buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("Draft"); buf.writeVarInt(513); });
        badRequest(buf -> {
            buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("Draft");
            buf.writeVarInt(512); for (int i = 0; i < 512; i++) buf.writeVarInt(1); buf.writeVarInt(1);
        });
        badRequest(buf -> { buf.writeByte(3); buf.writeVarLong(0); buf.writeUUID(ID); buf.writeUtf("Draft"); buf.writeVarInt(1); buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(0); });
    }
    @Test void rejectsMalformedCountAndDeckPages() {
        for (int size : new int[] {-1, 257}) badReply(buf -> { countHeader(buf); buf.writeVarInt(size); });
        badReply(buf -> { countHeader(buf); buf.writeVarInt(1); buf.writeVarInt(0); buf.writeVarLong(1); });
        badReply(buf -> { countHeader(buf); buf.writeVarInt(1); buf.writeVarInt(1); buf.writeVarLong(0); });
        badReply(buf -> { countHeader(buf); buf.writeVarInt(1); buf.writeVarInt(1); buf.writeVarLong(-1); });
        badReply(buf -> { countHeader(buf); buf.writeVarInt(2); for (int i = 0; i < 2; i++) { buf.writeVarInt(1); buf.writeVarLong(1); } });
        for (int size : new int[] {-1, 33}) badReply(buf -> { deckHeader(buf); buf.writeVarInt(size); });
        badReply(buf -> { deckHeader(buf); buf.writeVarInt(2); for (int i = 0; i < 2; i++) { buf.writeUUID(ID); buf.writeUtf("Draft"); buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(0); } });
        badReply(buf -> { deckHeader(buf); buf.writeVarInt(1); buf.writeUUID(ID); buf.writeUtf("Draft"); buf.writeVarInt(512); buf.writeVarInt(1); buf.writeVarInt(0); });
        badReply(buf -> buf.writeByte(127));
        badReply(buf -> { buf.writeByte(5); buf.writeByte(127); });
        badReply(buf -> { buf.writeByte(5); buf.writeByte(CollectionError.NONE.ordinal()); });
    }
    @Test void summariesRejectNonPrintableUnicodeNames() {
        for (String name : List.of("format\u200B", "unassigned\uFFFF", "separator\u2028")) {
            badReply(buf -> { deckHeader(buf); buf.writeVarInt(1); buf.writeUUID(ID); buf.writeUtf(name); buf.writeVarInt(0); buf.writeVarInt(0); buf.writeVarInt(0); });
        }
    }
    @Test void rejectsMalformedReportsAndTruncatedEnvelopes() {
        for (int size : new int[] {-1, 65}) badReply(buf -> { rejectionHeader(buf); buf.writeVarInt(size); });
        for (String key : List.of("k".repeat(129), "nonascii\uFFFF", "control\n")) badReply(buf -> { rejectionHeader(buf); buf.writeVarInt(1); buf.writeUtf(key); });
        badReply(buf -> { rejectionHeader(buf); buf.writeVarInt(0); buf.writeVarInt(513); });
        badReply(buf -> { rejectionHeader(buf); buf.writeVarInt(0); buf.writeVarInt(2); for (int i = 0; i < 2; i++) { buf.writeVarInt(1); buf.writeVarInt(1); } buf.writeBoolean(false); });
        badReply(buf -> { rejectionHeader(buf); buf.writeVarInt(0); buf.writeVarInt(1); buf.writeVarInt(1); buf.writeVarInt(0); buf.writeBoolean(false); });
        badReply(buf -> { rejectionHeader(buf); emptyReportPrefix(buf); buf.writeBoolean(true); buf.writeUtf(" "); });
        badReply(buf -> { rejectionHeader(buf); emptyReportPrefix(buf); buf.writeBoolean(true); buf.writeUtf("x".repeat(257)); });
        var buf = Unpooled.buffer();
        try {
            CollectionRequestPayload.STREAM_CODEC.encode(buf, new CollectionRequestPayload(ID, new CollectionCommand.Save(0, DECK)));
            buf.writerIndex(buf.writerIndex() - 1);
            assertThrows(RuntimeException.class, () -> CollectionRequestPayload.STREAM_CODEC.decode(buf));
            buf.clear(); CollectionReplyPayload.STREAM_CODEC.encode(buf, new CollectionReplyPayload(ID, new CollectionReply.Deck(0, DECK))); buf.writeByte(0);
            assertThrows(RuntimeException.class, () -> CollectionReplyPayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
    @Test void maximumUtfAndAllMaximumRecordShapesFitWholeEnvelopeBudget() {
        String name = "\u6F22".repeat(CollectionLimits.NAME_LENGTH);
        var cards = Collections.nCopies(512, Integer.MAX_VALUE);
        var deck = new SavedDeck(ID, name, new DeckList(cards, List.of(), List.of()));
        var problems = Collections.nCopies(64, new DeckEligibility.Issue("k".repeat(128), Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
        var missing = new HashMap<Integer, Integer>(); var counts = new HashMap<Integer, Long>();
        for (int i = 0; i < 512; i++) missing.put(Integer.MAX_VALUE - i, Integer.MAX_VALUE);
        for (int i = 0; i < 256; i++) counts.put(Integer.MAX_VALUE - i, Long.MAX_VALUE);
        var report = new DeckEligibility.Report(problems, missing, true, true, "\u6F22".repeat(256));
        var summaries = new ArrayList<CollectionReply.Summary>();
        for (int i = 0; i < 32; i++) summaries.add(new CollectionReply.Summary(UUID.randomUUID(), name, 512, 0, 0));
        for (var reply : List.of(new CollectionReply.Changed(Long.MAX_VALUE, ID, deck, 0, report), new CollectionReply.Rejected(CollectionError.INELIGIBLE, Long.MAX_VALUE, report),
                new CollectionReply.Counts(ID, Long.MAX_VALUE, Integer.MAX_VALUE, counts), new CollectionReply.Decks(ID, Long.MAX_VALUE, Integer.MAX_VALUE, summaries), new CollectionReply.Deck(Long.MAX_VALUE, deck))) {
            var buf = Unpooled.buffer(); var payload = new CollectionReplyPayload(ID, reply);
            try { CollectionReplyPayload.STREAM_CODEC.encode(buf, payload); assertTrue(buf.readableBytes() <= 24576, "Whole envelope bytes: " + buf.readableBytes()); System.out.println(reply.getClass().getSimpleName() + " maximum envelope: " + buf.readableBytes()); assertEquals(payload, CollectionReplyPayload.STREAM_CODEC.decode(buf)); }
            finally { buf.release(); }
        }
        var buf = Unpooled.buffer(); var payload = new CollectionRequestPayload(ID, new CollectionCommand.Save(Long.MAX_VALUE, deck));
        try { CollectionRequestPayload.STREAM_CODEC.encode(buf, payload); assertTrue(buf.readableBytes() <= 24576); assertEquals(payload, CollectionRequestPayload.STREAM_CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void encodingRejectsUnboundedShapesBeforeWritingDestination() {
        var counts = new HashMap<Integer, Long>(); for (int i = 1; i <= 257; i++) counts.put(i, 1L);
        var buf = Unpooled.buffer();
        try {
            assertThrows(RuntimeException.class, () -> CollectionReplyPayload.STREAM_CODEC.encode(buf, new CollectionReplyPayload(ID, new CollectionReply.Counts(ID, 0, 0, counts))));
            assertEquals(0, buf.readableBytes());
            var report = new DeckEligibility.Report(List.of(new DeckEligibility.Issue("k".repeat(129), 0, 0, 0)), Map.of(), false, false, null);
            assertThrows(RuntimeException.class, () -> CollectionReplyPayload.STREAM_CODEC.encode(buf, new CollectionReplyPayload(ID, new CollectionReply.Rejected(CollectionError.INVALID, 0, report))));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    private static void badRequest(java.util.function.Consumer<FriendlyByteBuf> writer) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { buf.writeUUID(ID); writer.accept(buf); assertThrows(RuntimeException.class, () -> CollectionRequestPayload.STREAM_CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    private static void badReply(java.util.function.Consumer<FriendlyByteBuf> writer) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { buf.writeUUID(ID); writer.accept(buf); assertThrows(RuntimeException.class, () -> CollectionReplyPayload.STREAM_CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    private static void countHeader(FriendlyByteBuf buf) { buf.writeByte(1); buf.writeUUID(ID); buf.writeVarLong(0); buf.writeVarInt(0); }
    private static void deckHeader(FriendlyByteBuf buf) { buf.writeByte(2); buf.writeUUID(ID); buf.writeVarLong(0); buf.writeVarInt(0); }
    private static void rejectionHeader(FriendlyByteBuf buf) { buf.writeByte(5); buf.writeByte(CollectionError.INVALID.ordinal()); buf.writeVarLong(0); }
    private static void emptyReportPrefix(FriendlyByteBuf buf) {
        buf.writeVarInt(0); buf.writeVarInt(0); buf.writeBoolean(false); buf.writeBoolean(false);
    }}

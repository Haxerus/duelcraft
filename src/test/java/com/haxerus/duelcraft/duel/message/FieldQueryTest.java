package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/** Byte layouts follow ygopro-core card::get_infos: [u16 size][u32 flag][data], size counting flag and data. */
class FieldQueryTest {

    private static ByteBuffer buf(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void u32Block(ByteBuffer b, int flag, int value) {
        b.putShort((short) 8); b.putInt(flag); b.putInt(value);
    }

    private static void u8Block(ByteBuffer b, int flag, int value) {
        b.putShort((short) 5); b.putInt(flag); b.put((byte) value);
    }

    private static void endBlock(ByteBuffer b) {
        b.putShort((short) 4); b.putInt(QUERY_END);
    }

    private static void putLocInfo(ByteBuffer b, int con, int loc, int seq, int pos) {
        b.put((byte) con); b.put((byte) loc); b.putInt(seq); b.putInt(pos);
    }

    private static void locInfoBlock(ByteBuffer b, int flag, int con, int loc, int seq, int pos) {
        b.putShort((short) 14); b.putInt(flag);
        putLocInfo(b, con, loc, seq, pos);
    }

    /** {@code OCG_DuelQueryLocation} prefixes the stream with its own length (ocgapi.cpp:242-245). */
    private static byte[] withPayloadLength(byte[] body) {
        return buf(4 + body.length).putInt(body.length).put(body).array();
    }

    @Test
    void readsU32BlocksAndStopsAtQueryEnd() {
        ByteBuffer b = buf(10 + 10 + 6);
        u32Block(b, QUERY_CODE, 89631139);
        u32Block(b, QUERY_ATTACK, 3000);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(89631139, card.code);
        assertEquals(3000, card.attack);
        assertEquals(QUERY_CODE | QUERY_ATTACK, card.flags);
    }

    @Test
    void isPublicIsOneByteSoLinkStaysAligned() {
        ByteBuffer b = buf(7 + 14 + 6);
        u8Block(b, QUERY_IS_PUBLIC, 0);
        b.putShort((short) 12); b.putInt(QUERY_LINK); b.putInt(3); b.putInt(0b1010);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertFalse(card.isPublic);
        assertEquals(3, card.linkRating);
        assertEquals(0b1010, card.linkMarker);
    }

    @Test
    void isHiddenIsOneByteAndReadsTrue() {
        ByteBuffer b = buf(7 + 10 + 6);
        u8Block(b, QUERY_IS_HIDDEN, 1);
        u32Block(b, QUERY_ATTACK, 1800);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertTrue(card.isHidden);
        assertEquals(1800, card.attack);
    }

    @Test
    void publicFlagSetReadsTrue() {
        ByteBuffer b = buf(7 + 6);
        u8Block(b, QUERY_IS_PUBLIC, 1);
        endBlock(b);

        assertTrue(FieldQuery.parse(b.array()).isPublic);
    }

    @Test
    void unhandledFieldIsSkippedBySize() {
        // A flag readField has no case for at all, so parseCard falls through to the default
        // branch and must rely purely on the declared block size to find the next block.
        int unknownFlag = 1 << 30;
        ByteBuffer b = buf(10 + 10 + 6);
        u32Block(b, unknownFlag, 0xDEAD);
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(46986414, card.code);
        assertTrue((card.flags & unknownFlag) != 0);
    }

    @Test
    void ownerIsOneByte() {
        ByteBuffer b = buf(7 + 10 + 6);
        u8Block(b, QUERY_OWNER, 1);
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(1, card.owner);
        assertEquals(46986414, card.code);
    }

    @Test
    void overlayCardsAreACountThenOneCodeEach() {
        ByteBuffer b = buf(4 + 4 + 4 + 8 + 6);
        b.putShort((short) 16); b.putInt(QUERY_OVERLAY_CARD);
        b.putInt(2); b.putInt(89631139); b.putInt(46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(List.of(89631139, 46986414), card.overlayCards);
    }

    @Test
    void countersAreACountThenTypePackedWithTheirTotal() {
        ByteBuffer b = buf(4 + 4 + 4 + 4 + 6);
        b.putShort((short) 12); b.putInt(QUERY_COUNTERS);
        b.putInt(1); b.putInt(0x1 | (3 << 16));
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(1, card.counters.size());
        assertEquals(0x1, card.counters.getFirst() & 0xFFFF);
        assertEquals(3, card.counters.getFirst() >>> 16);
    }

    @Test
    void emptyListFieldsParseAsEmptyLists() {
        ByteBuffer b = buf(4 + 4 + 4 + 4 + 6);
        b.putShort((short) 8); b.putInt(QUERY_OVERLAY_CARD); b.putInt(0);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertTrue(card.overlayCards.isEmpty());
        assertTrue((card.flags & QUERY_OVERLAY_CARD) != 0);
    }

    @Test
    void reasonAndEquipCardsAreTenByteLocInfos() {
        ByteBuffer b = buf(2 + 14 + 2 + 14 + 6);
        locInfoBlock(b, QUERY_REASON_CARD, 1, LOCATION_GRAVE, 4, POS_FACEUP_ATTACK);
        locInfoBlock(b, QUERY_EQUIP_CARD, 0, LOCATION_MZONE, 2, POS_FACEUP_DEFENSE);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(new LocInfo(1, LOCATION_GRAVE, 4, POS_FACEUP_ATTACK), card.reasonCard);
        assertEquals(new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_DEFENSE), card.equipCard);
    }

    @Test
    void anAbsentReasonOrEquipCardIsAllZeroesAndStaysNull() {
        // card.cpp:133-161 always writes the block and fills it with zeroes when there is no card.
        ByteBuffer b = buf(2 + 14 + 2 + 14 + 6);
        locInfoBlock(b, QUERY_REASON_CARD, 0, 0, 0, 0);
        locInfoBlock(b, QUERY_EQUIP_CARD, 0, 0, 0, 0);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertNull(card.reasonCard);
        assertNull(card.equipCard);
        assertTrue((card.flags & QUERY_REASON_CARD) != 0);
    }

    @Test
    void targetCardsAreACountThenOneLocInfoEach() {
        ByteBuffer b = buf(2 + 4 + 4 + 10 + 10 + 6);
        b.putShort((short) 28); b.putInt(QUERY_TARGET_CARD); b.putInt(2);
        putLocInfo(b, 0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK);
        putLocInfo(b, 1, LOCATION_SZONE, 3, POS_FACEDOWN_DEFENSE);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(List.of(new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK),
                new LocInfo(1, LOCATION_SZONE, 3, POS_FACEDOWN_DEFENSE)), card.targetCards);
    }

    @Test
    void aBufferWithoutQueryEndIsRejected() {
        ByteBuffer b = buf(10);
        u32Block(b, QUERY_CODE, 46986414);

        assertThrows(IllegalArgumentException.class, () -> FieldQuery.parse(b.array()));
    }

    @Test
    void aTruncatedBlockIsRejected() {
        ByteBuffer b = buf(6);
        b.putShort((short) 8); b.putInt(QUERY_CODE);   // declares four data bytes that are not there

        assertThrows(IllegalArgumentException.class, () -> FieldQuery.parse(b.array()));
    }

    @Test
    void anEmptyBufferIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldQuery.parse(new byte[0]));
    }

    // ---- bulk location queries (OCG_DuelQueryLocation) ----

    @Test
    void locationBufferSplitsIntoOneCardPerBlockRun() {
        ByteBuffer body = buf(10 + 6 + 10 + 6);
        u32Block(body, QUERY_CODE, 89631139);
        endBlock(body);
        u32Block(body, QUERY_CODE, 46986414);
        endBlock(body);

        var cards = FieldQuery.parseLocation(withPayloadLength(body.array()));
        assertEquals(2, cards.size());
        assertEquals(89631139, cards.get(0).code);
        assertEquals(46986414, cards.get(1).code);
    }

    @Test
    void aZeroSizeMarkerIsAnEmptyFieldSlot() {
        // ocgapi.cpp:212 writes a bare u16 0 for every vacant monster or spell zone.
        ByteBuffer body = buf(2 + 10 + 6 + 2);
        body.putShort((short) 0);
        u32Block(body, QUERY_CODE, 89631139);
        endBlock(body);
        body.putShort((short) 0);

        var cards = FieldQuery.parseLocation(withPayloadLength(body.array()));
        assertEquals(3, cards.size());
        assertNull(cards.get(0));
        assertEquals(89631139, cards.get(1).code);
        assertNull(cards.get(2));
    }

    @Test
    void anEmptyLocationBufferHasNoCards() {
        assertTrue(FieldQuery.parseLocation(withPayloadLength(new byte[0])).isEmpty());
        assertTrue(FieldQuery.parseLocation(new byte[0]).isEmpty());
    }

    @Test
    void aLocationBufferWhosePayloadLengthOverrunsTheBufferIsRejected() {
        ByteBuffer b = buf(4 + 4);
        b.putInt(64);
        b.putInt(0);

        assertThrows(IllegalArgumentException.class, () -> FieldQuery.parseLocation(b.array()));
    }

    @Test
    void trailingBytesInABlockDoNotShiftTheNextBlock() {
        ByteBuffer b = buf(12 + 10 + 6);
        b.putShort((short) 10); b.putInt(QUERY_REASON); b.putInt(1); b.putShort((short) 0x5555); // 2 bytes past the u32
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(1, card.reason);
        assertEquals(46986414, card.code);
    }
}

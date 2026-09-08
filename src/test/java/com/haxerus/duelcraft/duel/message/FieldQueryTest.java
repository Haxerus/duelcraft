package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    void publicFlagSetReadsTrue() {
        ByteBuffer b = buf(7 + 6);
        u8Block(b, QUERY_IS_PUBLIC, 1);
        endBlock(b);

        assertTrue(FieldQuery.parse(b.array()).isPublic);
    }

    @Test
    void unhandledFieldIsSkippedBySize() {
        ByteBuffer b = buf(7 + 10 + 6);
        u8Block(b, QUERY_IS_HIDDEN, 1);   // no case in FieldQuery; one data byte
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(46986414, card.code);
        assertTrue((card.flags & QUERY_IS_HIDDEN) != 0);
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

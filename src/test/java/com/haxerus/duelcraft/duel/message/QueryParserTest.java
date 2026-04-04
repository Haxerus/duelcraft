package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class QueryParserTest {

    /**
     * Build a query field entry: [uint32 fieldSize][fieldData...].
     * fieldSize includes the 4-byte size header.
     */
    static byte[] queryField(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(4 + data.length); // field size includes the uint32 header
        buf.put(data);
        return buf.array();
    }

    /** Build a single int32 field. */
    static byte[] int32Field(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        return queryField(buf.array());
    }

    /** Build a single int64 field. */
    static byte[] int64Field(long value) {
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(value);
        return queryField(buf.array());
    }

    /** Wrap field entries in a card block: [uint32 totalSize][fields...]. */
    static byte[] cardBlock(byte[]... fields) {
        int dataLen = 0;
        for (byte[] f : fields) dataLen += f.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(dataLen); // total size of fields (not including this uint32)
        for (byte[] f : fields) buf.put(f);
        return buf.array();
    }

    /** Empty card slot: total_size = 0. */
    static byte[] emptySlot() {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0);
        return buf.array();
    }

    // ---- Single card query ----

    @Test
    void parseSingleCardCodeAndAttack() {
        // Query with QUERY_CODE | QUERY_ATTACK
        // Fields come in flag order: CODE first, then ATTACK
        byte[] buffer = cardBlock(
                int32Field(89631139),  // QUERY_CODE = 89631139 (Blue-Eyes)
                int32Field(3000)       // next flag in order after CODE is POSITION,
                                       // but we skip to ATTACK — see note below
        );

        // NOTE: The query parser uses sequential flag detection.
        // The first int32 field becomes QUERY_CODE, the second becomes QUERY_POSITION.
        // To test specific flag combos accurately, we must provide fields in exact flag order.
        // Let's build a proper sequence: CODE, POSITION
        buffer = cardBlock(
                int32Field(89631139),          // QUERY_CODE
                int32Field(POS_FACEUP_ATTACK)  // QUERY_POSITION
        );

        QueriedCard card = QueryParser.parseSingle(buffer);
        assertNotNull(card);
        assertEquals(89631139, card.code);
        assertEquals(POS_FACEUP_ATTACK, card.position);
        assertTrue((card.flags & QUERY_CODE) != 0);
        assertTrue((card.flags & QUERY_POSITION) != 0);
    }

    @Test
    void parseSingleFullMonster() {
        // Provide fields in flag order: CODE, POSITION, ALIAS, TYPE, LEVEL, RANK,
        // ATTRIBUTE, RACE, ATTACK, DEFENSE
        byte[] buffer = cardBlock(
                int32Field(89631139),          // CODE
                int32Field(POS_FACEUP_ATTACK), // POSITION
                int32Field(0),                 // ALIAS
                int32Field(TYPE_MONSTER | TYPE_NORMAL), // TYPE
                int32Field(8),                 // LEVEL
                int32Field(0),                 // RANK
                int32Field(ATTRIBUTE_LIGHT),   // ATTRIBUTE
                int64Field(RACE_DRAGON),       // RACE (64-bit)
                int32Field(3000),              // ATTACK
                int32Field(2500)               // DEFENSE
        );

        QueriedCard card = QueryParser.parseSingle(buffer);
        assertNotNull(card);
        assertEquals(89631139, card.code);
        assertEquals(TYPE_MONSTER | TYPE_NORMAL, card.type);
        assertEquals(8, card.level);
        assertEquals(ATTRIBUTE_LIGHT, card.attribute);
        assertEquals(RACE_DRAGON, card.race);
        assertEquals(3000, card.attack);
        assertEquals(2500, card.defense);
    }

    @Test
    void parseSingleWithOverlayCards() {
        // Fields: CODE, POSITION, ALIAS, TYPE, LEVEL, RANK, ATTRIBUTE, RACE,
        // ATTACK, DEFENSE, BASE_ATTACK, BASE_DEFENSE, REASON, REASON_CARD,
        // EQUIP_CARD, TARGET_CARD, OVERLAY_CARD
        // That's a lot of fields to get to OVERLAY_CARD.
        // Let's just test the overlay count parsing with a minimal approach:
        // Build the exact flags in order up through OVERLAY_CARD

        // For simplicity, build manually
        ByteBuffer data = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);

        // CODE
        data.putInt(8); data.putInt(12345);
        // POSITION
        data.putInt(8); data.putInt(POS_FACEUP_ATTACK);
        // ALIAS
        data.putInt(8); data.putInt(0);
        // TYPE
        data.putInt(8); data.putInt(TYPE_MONSTER | TYPE_XYZ);
        // LEVEL
        data.putInt(8); data.putInt(0);
        // RANK
        data.putInt(8); data.putInt(4);
        // ATTRIBUTE
        data.putInt(8); data.putInt(ATTRIBUTE_DARK);
        // RACE
        data.putInt(12); data.putLong(RACE_WARRIOR);
        // ATTACK
        data.putInt(8); data.putInt(2500);
        // DEFENSE
        data.putInt(8); data.putInt(2000);
        // BASE_ATTACK
        data.putInt(8); data.putInt(2500);
        // BASE_DEFENSE
        data.putInt(8); data.putInt(2000);
        // REASON
        data.putInt(8); data.putInt(0);
        // REASON_CARD: locinfo = con(1) + loc(1) + seq(4) + pos(4) = 10 bytes
        data.putInt(14); data.put((byte)0); data.put((byte)0); data.putInt(0); data.putInt(0);
        // EQUIP_CARD
        data.putInt(14); data.put((byte)0); data.put((byte)0); data.putInt(0); data.putInt(0);
        // TARGET_CARD: count + locinfos
        data.putInt(8); data.putInt(0); // count = 0, no targets
        // OVERLAY_CARD: count + codes
        data.putInt(12); data.putInt(2); data.putInt(11111); data.putInt(22222);

        data.flip();
        byte[] fieldData = new byte[data.remaining()];
        data.get(fieldData);

        // Wrap in card block
        ByteBuffer block = ByteBuffer.allocate(4 + fieldData.length).order(ByteOrder.LITTLE_ENDIAN);
        block.putInt(fieldData.length);
        block.put(fieldData);

        QueriedCard card = QueryParser.parseSingle(block.array());
        assertNotNull(card);
        assertEquals(12345, card.code);
        assertEquals(4, card.rank);
        assertEquals(List.of(11111, 22222), card.overlayCards);
    }

    @Test
    void parseSingleNullAndEmpty() {
        assertNull(QueryParser.parseSingle(null));
        assertNull(QueryParser.parseSingle(new byte[0]));
        assertNull(QueryParser.parseSingle(emptySlot()));
    }

    // ---- Location query ----

    @Test
    void parseLocationMultipleCards() {
        byte[] card1 = cardBlock(
                int32Field(89631139),          // CODE
                int32Field(POS_FACEUP_ATTACK)  // POSITION
        );
        byte[] card2 = cardBlock(
                int32Field(46986414),            // CODE
                int32Field(POS_FACEUP_DEFENSE)   // POSITION
        );

        ByteBuffer buf = ByteBuffer.allocate(card1.length + card2.length);
        buf.put(card1);
        buf.put(card2);

        List<QueriedCard> cards = QueryParser.parseLocation(buf.array());
        assertEquals(2, cards.size());
        assertEquals(89631139, cards.get(0).code);
        assertEquals(46986414, cards.get(1).code);
        assertEquals(POS_FACEUP_ATTACK, cards.get(0).position);
        assertEquals(POS_FACEUP_DEFENSE, cards.get(1).position);
    }

    @Test
    void parseLocationSkipsEmptySlots() {
        byte[] card1 = cardBlock(int32Field(89631139));
        byte[] empty = emptySlot();
        byte[] card2 = cardBlock(int32Field(46986414));

        ByteBuffer buf = ByteBuffer.allocate(card1.length + empty.length + card2.length);
        buf.put(card1);
        buf.put(empty);
        buf.put(card2);

        List<QueriedCard> cards = QueryParser.parseLocation(buf.array());
        assertEquals(2, cards.size()); // empty slot skipped
        assertEquals(89631139, cards.get(0).code);
        assertEquals(46986414, cards.get(1).code);
    }

    @Test
    void parseLocationEmpty() {
        assertEquals(List.of(), QueryParser.parseLocation(null));
        assertEquals(List.of(), QueryParser.parseLocation(new byte[0]));
    }

    // ---- Field query ----

    @Test
    void parseFieldIncludesNullForEmptySlots() {
        byte[] card1 = cardBlock(int32Field(89631139));
        byte[] empty = emptySlot();

        ByteBuffer buf = ByteBuffer.allocate(card1.length + empty.length);
        buf.put(card1);
        buf.put(empty);

        List<QueriedCard> cards = QueryParser.parseField(buf.array());
        assertEquals(2, cards.size());
        assertNotNull(cards.get(0));
        assertNull(cards.get(1)); // empty slot preserved as null
        assertEquals(89631139, cards.get(0).code);
    }
}

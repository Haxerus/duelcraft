package com.haxerus.duelcraft.duel.message;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Parses one card's answer to a native per-slot query ({@code OcgCore.nDuelQuery}): a run of
 * {@code [u16 size][u32 flag][data]} blocks, {@code size} counting the flag and the data, closed by a
 * {@code QUERY_END} block with no data (ygopro-core {@code card::get_infos}). Widths follow the engine:
 * {@code QUERY_RACE} is u64; {@code QUERY_OWNER}, {@code QUERY_IS_PUBLIC} and {@code QUERY_IS_HIDDEN}
 * are u8; {@code QUERY_REASON_CARD} and {@code QUERY_EQUIP_CARD} are a 10-byte loc_info, all zeroes
 * when there is no such card; {@code QUERY_TARGET_CARD}, {@code QUERY_OVERLAY_CARD} and
 * {@code QUERY_COUNTERS} are a u32 count followed by that many entries; every other scalar is u32.
 * Each block is skipped to its declared size after reading, so a field this class does not
 * understand, or reads too narrowly, cannot shift the blocks after it.
 *
 * <p>{@link #parseLocation} reads the bulk answer of {@code OcgCore.nDuelQueryLocation}: a
 * {@code u32} payload length, then one card per slot, a bare {@code u16 0} standing for a vacant
 * monster or spell zone ({@code ocgapi.cpp:206-247}).
 */
public final class FieldQuery {

    private FieldQuery() {}

    public static QueriedCard parse(byte[] data) {
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        QueriedCard card = parseCard(buf);
        if (card == null) throw new IllegalArgumentException("Query buffer holds an empty-slot marker, not a card");
        return card;
    }

    /** The cards of one location in slot order; null for a vacant monster or spell zone. */
    public static List<QueriedCard> parseLocation(byte[] data) {
        var cards = new ArrayList<QueriedCard>();
        if (data == null || data.length == 0) return cards;
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int payload = expect(buf, 4, "payload length").getInt();
        if (payload > buf.remaining()) {
            throw new IllegalArgumentException("Query payload length " + payload
                    + " exceeds the " + buf.remaining() + " bytes returned");
        }
        int end = buf.position() + payload;
        while (buf.position() < end) cards.add(parseCard(buf));
        return cards;
    }

    /** One card's blocks up to and including {@code QUERY_END}, or null for an empty-slot marker. */
    private static QueriedCard parseCard(ByteBuffer buf) {
        var card = new QueriedCard();
        while (true) {
            int size = Short.toUnsignedInt(expect(buf, 2, "block size").getShort());
            if (size == 0) return null;
            if (size < 4) throw new IllegalArgumentException("Query block size " + size + " is below the flag width");
            int end = buf.position() + size;
            if (end > buf.limit()) throw new IllegalArgumentException("Query block runs past the end of the buffer");
            int flag = buf.getInt();
            if (flag == QUERY_END) return card;
            card.flags |= flag;
            try {
                readField(buf, card, flag);
            } catch (BufferUnderflowException e) {
                throw new IllegalArgumentException("Query block 0x" + Integer.toHexString(flag) + " is truncated", e);
            }
            buf.position(end);
        }
    }

    private static ByteBuffer expect(ByteBuffer buf, int bytes, String what) {
        if (buf.remaining() < bytes) {
            throw new IllegalArgumentException("Query buffer ended before the " + what);
        }
        return buf;
    }

    private static void readField(ByteBuffer buf, QueriedCard card, int flag) {
        switch (flag) {
            case QUERY_CODE         -> card.code = buf.getInt();
            case QUERY_POSITION     -> card.position = buf.getInt();
            case QUERY_ALIAS        -> card.alias = buf.getInt();
            case QUERY_TYPE         -> card.type = buf.getInt();
            case QUERY_LEVEL        -> card.level = buf.getInt();
            case QUERY_RANK         -> card.rank = buf.getInt();
            case QUERY_ATTRIBUTE    -> card.attribute = buf.getInt();
            case QUERY_RACE         -> card.race = buf.getLong();
            case QUERY_ATTACK       -> card.attack = buf.getInt();
            case QUERY_DEFENSE      -> card.defense = buf.getInt();
            case QUERY_BASE_ATTACK  -> card.baseAttack = buf.getInt();
            case QUERY_BASE_DEFENSE -> card.baseDefense = buf.getInt();
            case QUERY_REASON       -> card.reason = buf.getInt();
            case QUERY_OWNER        -> card.owner = Byte.toUnsignedInt(buf.get());
            case QUERY_STATUS       -> card.status = buf.getInt();
            case QUERY_IS_PUBLIC    -> card.isPublic = buf.get() != 0;
            case QUERY_IS_HIDDEN    -> card.isHidden = buf.get() != 0;
            case QUERY_LSCALE       -> card.lscale = buf.getInt();
            case QUERY_RSCALE       -> card.rscale = buf.getInt();
            case QUERY_COVER        -> card.cover = buf.getInt();
            case QUERY_REASON_CARD  -> card.reasonCard = readLocInfo(buf);
            case QUERY_EQUIP_CARD   -> card.equipCard = readLocInfo(buf);
            case QUERY_TARGET_CARD  -> {
                var targets = new ArrayList<LocInfo>();
                for (int i = buf.getInt(); i > 0; i--) {
                    LocInfo info = readLocInfo(buf);
                    if (info != null) targets.add(info);
                }
                card.targetCards = List.copyOf(targets);
            }
            case QUERY_OVERLAY_CARD -> card.overlayCards = readInts(buf);
            case QUERY_COUNTERS     -> card.counters = readInts(buf);
            case QUERY_LINK -> {
                card.linkRating = buf.getInt();
                card.linkMarker = buf.getInt();
            }
            default -> { } // unknown to this build; parseCard skips the block by its declared size
        }
    }

    /** A 10-byte loc_info, or null for the all-zero block the engine writes when there is no card. */
    private static LocInfo readLocInfo(ByteBuffer buf) {
        int controller = Byte.toUnsignedInt(buf.get());
        int location = Byte.toUnsignedInt(buf.get());
        int sequence = buf.getInt();
        int position = buf.getInt();
        return location == 0 ? null : new LocInfo(controller, location, sequence, position);
    }

    private static List<Integer> readInts(ByteBuffer buf) {
        var values = new ArrayList<Integer>();
        for (int i = buf.getInt(); i > 0; i--) values.add(buf.getInt());
        return List.copyOf(values);
    }
}

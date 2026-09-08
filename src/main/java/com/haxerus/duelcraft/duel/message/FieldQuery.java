package com.haxerus.duelcraft.duel.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Parses one card's answer to a native per-slot query ({@code OcgCore.nDuelQuery}): a run of
 * {@code [u16 size][u32 flag][data]} blocks, {@code size} counting the flag and the data, closed by a
 * {@code QUERY_END} block with no data (ygopro-core {@code card::get_infos}). Widths follow the engine:
 * {@code QUERY_RACE} is u64; {@code QUERY_OWNER}, {@code QUERY_IS_PUBLIC} and {@code QUERY_IS_HIDDEN}
 * are u8; every other scalar is u32. Each block is skipped to its declared size after reading, so a
 * field this class does not understand, or reads too narrowly, cannot shift the blocks after it.
 */
public final class FieldQuery {

    private FieldQuery() {}

    public static QueriedCard parse(byte[] data) {
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        var card = new QueriedCard();
        while (buf.remaining() >= 6) {
            int size = Short.toUnsignedInt(buf.getShort());
            int end = buf.position() + size;
            int flag = buf.getInt();
            if (flag == QUERY_END) break;
            card.flags |= flag;
            readField(buf, card, flag);
            buf.position(end);
        }
        return card;
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
            case QUERY_STATUS       -> card.status = buf.getInt();
            case QUERY_IS_PUBLIC    -> card.isPublic = buf.get() != 0;
            case QUERY_LSCALE       -> card.lscale = buf.getInt();
            case QUERY_RSCALE       -> card.rscale = buf.getInt();
            case QUERY_COVER        -> card.cover = buf.getInt();
            case QUERY_LINK -> {
                card.linkRating = buf.getInt();
                card.linkMarker = buf.getInt();
            }
            default -> { } // not requested by DuelSession; parse() skips the block by its size
        }
    }
}

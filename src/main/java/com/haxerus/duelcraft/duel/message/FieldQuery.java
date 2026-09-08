package com.haxerus.duelcraft.duel.message;

import java.nio.ByteBuffer;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_ATTACK;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_ATTRIBUTE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_BASE_ATTACK;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_BASE_DEFENSE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_COVER;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_DEFENSE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_IS_PUBLIC;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_LEVEL;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_LINK;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_LSCALE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_RACE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_RANK;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_REASON;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_RSCALE;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_STATUS;
import static com.haxerus.duelcraft.core.OcgConstants.QUERY_TYPE;

public class FieldQuery {

    /** Read one field block: given u16 fieldSize already read, read [u32 flag][data]. */
    public static void readFieldBlock(ByteBuffer buf, QueriedCard card, int fieldSize) {
        int flag = buf.getInt();
        card.flags |= flag;
        int dataSize = fieldSize - 4; // fieldSize includes the flag

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
            case QUERY_IS_PUBLIC    -> card.isPublic = buf.getInt() != 0;
            case QUERY_LSCALE       -> card.lscale = buf.getInt();
            case QUERY_RSCALE       -> card.rscale = buf.getInt();
            case QUERY_COVER        -> card.cover = buf.getInt();
            case QUERY_LINK -> {
                card.linkRating = buf.getInt();
                card.linkMarker = buf.getInt();
            }
            default -> {
                // Skip unknown field data
                if (dataSize > 0 && buf.remaining() >= dataSize) {
                    buf.position(buf.position() + dataSize);
                }
            }
        }
    }
}

package com.haxerus.duelcraft.duel.message;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Parses binary responses from OCG_DuelQuery, OCG_DuelQueryLocation, and OCG_DuelQueryField.
 *
 * Query result format per card:
 * <pre>
 *   [uint32 total_size]  — size of remaining data for this card (0 = empty slot)
 *   For each QUERY_* flag present (ordered low bit to high bit):
 *     [uint32 field_size] — size including this uint32
 *     [field data...]     — field_size - 4 bytes
 * </pre>
 */
public class QueryParser {

    /**
     * Parse a single card query result (from {@code nDuelQuery}).
     * Returns null if the buffer is empty or the card slot is empty.
     */
    public static QueriedCard parseSingle(byte[] buffer) {
        if (buffer == null || buffer.length == 0) return null;
        BufferReader r = new BufferReader(buffer);
        return parseOneCard(r);
    }

    /**
     * Parse a location query result (from {@code nDuelQueryLocation}).
     * Returns one QueriedCard per card in that location. Empty slots are skipped.
     */
    public static List<QueriedCard> parseLocation(byte[] buffer) {
        if (buffer == null || buffer.length == 0) return List.of();
        BufferReader r = new BufferReader(buffer);
        List<QueriedCard> cards = new ArrayList<>();
        while (r.remaining() >= 4) {
            QueriedCard card = parseOneCard(r);
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    /**
     * Parse a field query result (from {@code nDuelQueryField}).
     * Returns one QueriedCard per slot across the entire field.
     * Empty slots are included as null entries.
     */
    public static List<QueriedCard> parseField(byte[] buffer) {
        if (buffer == null || buffer.length == 0) return List.of();
        BufferReader r = new BufferReader(buffer);
        List<QueriedCard> cards = new ArrayList<>();
        while (r.remaining() >= 4) {
            cards.add(parseOneCard(r)); // null for empty slots
        }
        return cards;
    }

    private static QueriedCard parseOneCard(BufferReader r) {
        if (r.remaining() < 4) return null;
        int totalSize = r.readInt32();
        if (totalSize == 0) return null;

        QueriedCard card = new QueriedCard();
        int endPos = r.remaining(); // track how much we've read
        int bytesToRead = totalSize;

        while (bytesToRead > 0 && r.remaining() >= 4) {
            int fieldSize = r.readInt32();
            bytesToRead -= fieldSize;

            if (fieldSize <= 4) continue; // empty field, just the size header

            int dataSize = fieldSize - 4;
            int beforeRead = r.remaining();

            // Read the flag by trying each in order. The core writes them
            // in flag bit order, so we track which flags we've seen.
            // However, it's simpler to identify by field size + position.
            // Instead, we use a sequential flag reader approach:
            readNextField(r, card, dataSize);

            // Safety: if we didn't consume exactly dataSize, skip the rest
            int actuallyRead = beforeRead - r.remaining();
            if (actuallyRead < dataSize) {
                r.skip(dataSize - actuallyRead);
            }
        }

        return card;
    }

    /**
     * Reads fields sequentially. The core writes query fields in flag bit order
     * (QUERY_CODE, QUERY_POSITION, QUERY_ALIAS, ... QUERY_END).
     * We use a stateful approach: each QueriedCard tracks which flags have been
     * read via its flags field, so we know which flag comes next.
     */
    private static void readNextField(BufferReader r, QueriedCard card, int dataSize) {
        // Find the next unread flag
        int flag = nextFlag(card.flags);
        if (flag == 0) {
            r.skip(dataSize);
            return;
        }

        card.flags |= flag;

        switch (flag) {
            case QUERY_CODE -> card.code = r.readInt32();
            case QUERY_POSITION -> card.position = r.readInt32();
            case QUERY_ALIAS -> card.alias = r.readInt32();
            case QUERY_TYPE -> card.type = r.readInt32();
            case QUERY_LEVEL -> card.level = r.readInt32();
            case QUERY_RANK -> card.rank = r.readInt32();
            case QUERY_ATTRIBUTE -> card.attribute = r.readInt32();
            case QUERY_RACE -> card.race = r.readInt64();
            case QUERY_ATTACK -> card.attack = r.readInt32();
            case QUERY_DEFENSE -> card.defense = r.readInt32();
            case QUERY_BASE_ATTACK -> card.baseAttack = r.readInt32();
            case QUERY_BASE_DEFENSE -> card.baseDefense = r.readInt32();
            case QUERY_REASON -> card.reason = r.readInt32();
            case QUERY_REASON_CARD -> card.reasonCard = LocInfo.read(r);
            case QUERY_EQUIP_CARD -> card.equipCard = LocInfo.read(r);
            case QUERY_TARGET_CARD -> {
                int count = r.readInt32();
                List<LocInfo> targets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    targets.add(LocInfo.read(r));
                }
                card.targetCards = targets;
            }
            case QUERY_OVERLAY_CARD -> {
                int count = r.readInt32();
                List<Integer> overlay = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    overlay.add(r.readInt32());
                }
                card.overlayCards = overlay;
            }
            case QUERY_COUNTERS -> {
                int count = r.readInt32();
                List<Integer> counters = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    counters.add(r.readInt32()); // packed: (type << 16) | count
                }
                card.counters = counters;
            }
            case QUERY_OWNER -> card.owner = r.readUint8();
            case QUERY_STATUS -> card.status = r.readInt32();
            case QUERY_IS_PUBLIC -> card.isPublic = r.readInt32() != 0;
            case QUERY_LSCALE -> card.lscale = r.readInt32();
            case QUERY_RSCALE -> card.rscale = r.readInt32();
            case QUERY_LINK -> {
                card.linkRating = r.readInt32();
                card.linkMarker = r.readInt32();
            }
            case QUERY_IS_HIDDEN -> card.isHidden = r.readInt32() != 0;
            case QUERY_COVER -> card.cover = r.readInt32();
            case QUERY_END -> { /* terminal marker, no data */ }
            default -> r.skip(dataSize);
        }
    }

    /**
     * Returns the next QUERY_* flag after all flags already set in {@code currentFlags}.
     * Flags are ordered by bit position (low to high).
     */
    private static int nextFlag(int currentFlags) {
        for (int flag : FLAG_ORDER) {
            if ((currentFlags & flag) == 0) {
                return flag;
            }
        }
        return 0;
    }

    /** QUERY_* flags in bit order, matching the order the core writes them. */
    private static final int[] FLAG_ORDER = {
        QUERY_CODE,
        QUERY_POSITION,
        QUERY_ALIAS,
        QUERY_TYPE,
        QUERY_LEVEL,
        QUERY_RANK,
        QUERY_ATTRIBUTE,
        QUERY_RACE,
        QUERY_ATTACK,
        QUERY_DEFENSE,
        QUERY_BASE_ATTACK,
        QUERY_BASE_DEFENSE,
        QUERY_REASON,
        QUERY_REASON_CARD,
        QUERY_EQUIP_CARD,
        QUERY_TARGET_CARD,
        QUERY_OVERLAY_CARD,
        QUERY_COUNTERS,
        QUERY_OWNER,
        QUERY_STATUS,
        QUERY_IS_PUBLIC,
        QUERY_LSCALE,
        QUERY_RSCALE,
        QUERY_LINK,
        QUERY_IS_HIDDEN,
        QUERY_COVER,
        QUERY_END,
    };
}

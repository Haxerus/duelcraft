package com.haxerus.duelcraft.duel.response;

import com.haxerus.duelcraft.duel.message.DuelMessage;

import java.util.Arrays;

/**
 * Validates player response parameters against the prompt message constraints
 * before encoding. Throws {@link IllegalArgumentException} on invalid input.
 * This prevents sending bad responses to ygopro-core (which would just
 * return MSG_RETRY with no explanation).
 */
public final class ResponseValidator {

    private ResponseValidator() {}

    /** Validate and build a response for MSG_SELECT_CARD. */
    public static byte[] selectCards(DuelMessage.SelectCard prompt, int... indices) {
        checkSelectionCount(indices.length, prompt.min(), prompt.max());
        for (int idx : indices) {
            checkIndex(idx, prompt.cards().size());
        }
        checkNoDuplicates(indices);
        return ResponseBuilder.selectCards(indices);
    }

    /** Validate and build a response for MSG_SELECT_TRIBUTE. */
    public static byte[] selectTribute(DuelMessage.SelectTribute prompt, int... indices) {
        checkSelectionCount(indices.length, prompt.min(), prompt.max());
        for (int idx : indices) {
            checkIndex(idx, prompt.cards().size());
        }
        checkNoDuplicates(indices);
        return ResponseBuilder.selectCards(indices);
    }

    /** Validate and build a response for MSG_SELECT_CHAIN. */
    public static byte[] selectChain(DuelMessage.SelectChain prompt, int index) {
        if (index == -1) {
            if (prompt.forced()) {
                throw new IllegalArgumentException(
                        "Cannot decline chain activation — forced is true");
            }
        } else {
            checkIndex(index, prompt.count());
        }
        return ResponseBuilder.selectChain(index);
    }

    /** Validate and build a response for MSG_SELECT_EFFECTYN. */
    public static byte[] selectEffectYn(DuelMessage.SelectEffectYn prompt, boolean yes) {
        // No constraints to validate — yes/no is always valid
        return ResponseBuilder.selectYesNo(yes);
    }

    /** Validate and build a response for MSG_SELECT_YESNO. */
    public static byte[] selectYesNo(DuelMessage.SelectYesNo prompt, boolean yes) {
        return ResponseBuilder.selectYesNo(yes);
    }

    /** Validate and build a response for MSG_SELECT_OPTION. */
    public static byte[] selectOption(DuelMessage.SelectOption prompt, int index) {
        checkIndex(index, prompt.options().size());
        return ResponseBuilder.selectOption(index);
    }

    /** Validate and build a response for MSG_SELECT_PLACE / MSG_SELECT_DISFIELD. */
    public static byte[] selectPlace(DuelMessage.SelectPlace prompt,
                                     int player, int location, int sequence) {
        // The field bitmask has 0 for selectable zones, 1 for unavailable.
        // Verify the chosen zone is selectable.
        int bit = zoneToBit(player, prompt.player(), location, sequence);
        if (bit >= 0 && (prompt.field() & (1 << bit)) != 0) {
            throw new IllegalArgumentException(
                    "Zone is not selectable: player=" + player
                            + " location=0x" + Integer.toHexString(location)
                            + " sequence=" + sequence);
        }
        return ResponseBuilder.selectPlace(player, location, sequence);
    }

    /** Validate and build a response for MSG_SELECT_POSITION. */
    public static byte[] selectPosition(DuelMessage.SelectPosition prompt, int position) {
        if ((prompt.positions() & position) == 0) {
            throw new IllegalArgumentException(
                    "Position 0x" + Integer.toHexString(position)
                            + " is not among available positions 0x"
                            + Integer.toHexString(prompt.positions()));
        }
        // Must be exactly one position bit
        if (Integer.bitCount(position) != 1) {
            throw new IllegalArgumentException(
                    "Must select exactly one position, got 0x"
                            + Integer.toHexString(position));
        }
        return ResponseBuilder.selectPosition(position);
    }

    /** Validate and build a response for MSG_SELECT_UNSELECT_CARD. */
    public static byte[] selectUnselectCard(DuelMessage.SelectUnselectCard prompt, int index) {
        if (index == -1) {
            if (!prompt.finishable()) {
                throw new IllegalArgumentException(
                        "Cannot finish selection — finishable is false");
            }
        } else {
            checkIndex(index, prompt.selectableCards().size());
        }
        return ResponseBuilder.selectUnselectCard(index);
    }

    /** Validate and build a response for MSG_SORT_CARD. */
    public static byte[] sortCard(DuelMessage.SortCard prompt, int... order) {
        checkPermutation(order, prompt.cards().size());
        return ResponseBuilder.sortCards(order);
    }

    /** Validate and build a response for MSG_SORT_CHAIN. */
    public static byte[] sortChain(DuelMessage.SortChain prompt, int... order) {
        checkPermutation(order, prompt.cards().size());
        return ResponseBuilder.sortCards(order);
    }

    /** Validate and build a response for MSG_ANNOUNCE_RACE. */
    public static byte[] announceRace(DuelMessage.AnnounceRace prompt, long raceFlags) {
        if ((raceFlags & ~prompt.available()) != 0) {
            throw new IllegalArgumentException(
                    "Selected races 0x" + Long.toHexString(raceFlags)
                            + " include unavailable races (available: 0x"
                            + Long.toHexString(prompt.available()) + ")");
        }
        if (Long.bitCount(raceFlags) != prompt.count()) {
            throw new IllegalArgumentException(
                    "Must select exactly " + prompt.count() + " race(s), selected "
                            + Long.bitCount(raceFlags));
        }
        return ResponseBuilder.announceRace(raceFlags);
    }

    /** Validate and build a response for MSG_ANNOUNCE_ATTRIB. */
    public static byte[] announceAttrib(DuelMessage.AnnounceAttrib prompt, int attribFlags) {
        if ((attribFlags & ~prompt.available()) != 0) {
            throw new IllegalArgumentException(
                    "Selected attributes 0x" + Integer.toHexString(attribFlags)
                            + " include unavailable attributes (available: 0x"
                            + Integer.toHexString(prompt.available()) + ")");
        }
        if (Integer.bitCount(attribFlags) != prompt.count()) {
            throw new IllegalArgumentException(
                    "Must select exactly " + prompt.count() + " attribute(s), selected "
                            + Integer.bitCount(attribFlags));
        }
        return ResponseBuilder.announceAttrib(attribFlags);
    }

    /** Validate and build a response for MSG_ANNOUNCE_NUMBER. */
    public static byte[] announceNumber(DuelMessage.AnnounceNumber prompt, int index) {
        checkIndex(index, prompt.options().size());
        return ResponseBuilder.announceNumber(index);
    }

    /** Validate and build a response for MSG_ROCK_PAPER_SCISSORS. */
    public static byte[] rockPaperScissors(int choice) {
        if (choice < 1 || choice > 3) {
            throw new IllegalArgumentException(
                    "RPS choice must be 1 (rock), 2 (paper), or 3 (scissors), got " + choice);
        }
        return ResponseBuilder.rockPaperScissors(choice);
    }

    /** Validate and build a response for MSG_SELECT_COUNTER. */
    public static byte[] selectCounter(DuelMessage.SelectCounter prompt, int... countsPerCard) {
        if (countsPerCard.length != prompt.cards().size()) {
            throw new IllegalArgumentException(
                    "Must provide a count for each card: expected "
                            + prompt.cards().size() + ", got " + countsPerCard.length);
        }
        int total = 0;
        for (int c : countsPerCard) {
            if (c < 0) {
                throw new IllegalArgumentException("Counter count cannot be negative: " + c);
            }
            total += c;
        }
        if (total != prompt.count()) {
            throw new IllegalArgumentException(
                    "Total counters must equal " + prompt.count() + ", got " + total);
        }
        return ResponseBuilder.selectCounter(countsPerCard);
    }

    // ---- Internal checks ----

    private static void checkIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                    "Index " + index + " out of range [0, " + size + ")");
        }
    }

    private static void checkSelectionCount(int count, int min, int max) {
        if (count < min || count > max) {
            throw new IllegalArgumentException(
                    "Selection count " + count + " not in range [" + min + ", " + max + "]");
        }
    }

    private static void checkNoDuplicates(int[] indices) {
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException("Duplicate index: " + sorted[i]);
            }
        }
    }

    private static void checkPermutation(int[] order, int size) {
        if (order.length != size) {
            throw new IllegalArgumentException(
                    "Sort order length " + order.length + " must equal card count " + size);
        }
        boolean[] seen = new boolean[size];
        for (int idx : order) {
            checkIndex(idx, size);
            if (seen[idx]) {
                throw new IllegalArgumentException("Duplicate index in sort order: " + idx);
            }
            seen[idx] = true;
        }
    }

    /**
     * Convert a zone choice to its bit position in the field bitmask.
     * The bitmask layout (32 bits):
     *   bits 0-4:   choosing player's Main Monster Zones
     *   bits 5-6:   Extra Monster Zones
     *   bits 8-12:  choosing player's Spell/Trap Zones
     *   bits 13:    choosing player's Field Zone
     *   bits 16-20: opponent's Main Monster Zones
     *   bits 21-22: opponent's Extra Monster Zones (from their perspective)
     *   bits 24-28: opponent's Spell/Trap Zones
     *   bits 29:    opponent's Field Zone
     * Returns -1 if the zone doesn't map to a known bit.
     */
    private static int zoneToBit(int player, int promptPlayer, int location, int sequence) {
        int offset = (player != promptPlayer) ? 16 : 0;
        if (location == 0x04) { // MZONE
            if (sequence >= 0 && sequence < 5) return offset + sequence;
            if (sequence >= 5 && sequence < 7) return offset + sequence; // EMZ
        } else if (location == 0x08) { // SZONE
            if (sequence >= 0 && sequence < 5) return offset + 8 + sequence;
            if (sequence == 5) return offset + 13; // Field Zone
        }
        return -1;
    }
}

package com.haxerus.duelcraft.duel.response;

import com.haxerus.duelcraft.core.OcgConstants.BattleAction;
import com.haxerus.duelcraft.core.OcgConstants.IdleAction;
import com.haxerus.duelcraft.duel.message.DuelMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.LOCATION_MZONE;
import static com.haxerus.duelcraft.core.OcgConstants.LOCATION_SZONE;

/**
 * Validates player response parameters against the prompt message constraints
 * before encoding. Throws {@link IllegalArgumentException} on invalid input.
 * This prevents sending bad responses to ygopro-core (which would just
 * return MSG_RETRY with no explanation).
 */
public final class ResponseValidator {

    private ResponseValidator() {}

    /**
     * Validate and build a response for MSG_SELECT_IDLECMD. The engine rejects a type above 8 and
     * an index past the list that type addresses ({@code playerop.cpp:140-152}).
     */
    public static byte[] selectCmd(DuelMessage.SelectIdleCmd prompt, int actionType, int index) {
        int size = switch (actionType) {
            case IdleAction.SUMMON -> prompt.summonable().size();
            case IdleAction.SPECIAL_SUMMON -> prompt.specialSummonable().size();
            case IdleAction.REPOSITION -> prompt.repositionable().size();
            case IdleAction.SET_MONSTER -> prompt.settableMonsters().size();
            case IdleAction.SET_SPELL_TRAP -> prompt.settableSpells().size();
            case IdleAction.ACTIVATE -> prompt.activatable().size();
            case IdleAction.TO_BATTLE -> prompt.canBattle() ? 1 : 0;
            case IdleAction.END_TURN -> prompt.canEnd() ? 1 : 0;
            case IdleAction.SHUFFLE_HAND -> prompt.canShuffle() ? 1 : 0;
            default -> throw new IllegalArgumentException(
                    "Idle action type must be 0-8, got " + actionType);
        };
        checkAction(actionType, index, size);
        return ResponseBuilder.selectCmd(actionType, index);
    }

    /**
     * Validate and build a response for MSG_SELECT_BATTLECMD ({@code playerop.cpp:55-66}):
     * type 0 activates, 1 attacks, 2 goes to Main 2, 3 ends the battle phase.
     */
    public static byte[] selectCmd(DuelMessage.SelectBattleCmd prompt, int actionType, int index) {
        int size = switch (actionType) {
            case BattleAction.ACTIVATE -> prompt.activatable().size();
            case BattleAction.ATTACK -> prompt.attackable().size();
            case BattleAction.TO_MAIN2 -> prompt.canMain2() ? 1 : 0;
            case BattleAction.END_BATTLE -> prompt.canEnd() ? 1 : 0;
            default -> throw new IllegalArgumentException(
                    "Battle action type must be 0-3, got " + actionType);
        };
        checkAction(actionType, index, size);
        return ResponseBuilder.selectCmd(actionType, index);
    }

    private static void checkAction(int actionType, int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Action type " + actionType + " has no entry "
                    + index + " (" + size + " available)");
        }
    }

    /** Validate and build a response for MSG_SELECT_CARD. */
    public static byte[] selectCards(DuelMessage.SelectCard prompt, int... indices) {
        checkSelectionCount(indices.length, prompt.min(), prompt.max());
        for (int idx : indices) {
            checkIndex(idx, prompt.cards().size());
        }
        checkNoDuplicates(indices);
        return ResponseBuilder.selectCards(indices);
    }

    /**
     * Validate and build a response for MSG_SELECT_TRIBUTE. The engine bounds the card count by
     * {@code max} and the summed {@code release_param} by {@code min} ({@code playerop.cpp:686-700}),
     * so over-tributing past {@code min} is legal.
     */
    public static byte[] selectTribute(DuelMessage.SelectTribute prompt, int... indices) {
        if (indices.length > prompt.max()) {
            throw new IllegalArgumentException(
                    "Tribute count " + indices.length + " exceeds max " + prompt.max());
        }
        for (int idx : indices) {
            checkIndex(idx, prompt.cards().size());
        }
        checkNoDuplicates(indices);
        int sum = 0;
        for (int idx : indices) sum += prompt.cards().get(idx).tributeCount();
        if (sum < prompt.min()) {
            throw new IllegalArgumentException(
                    "Tribute sum " + sum + " is below the required " + prompt.min());
        }
        return ResponseBuilder.selectCards(indices);
    }

    /**
     * Validate and build a response for MSG_SELECT_SUM. The engine's acceptance check is ported in
     * {@link SumSelection} ({@code playerop.cpp:820-867}); the response indexes the selectable list
     * alone, with must-select cards always counting towards the total.
     */
    public static byte[] selectSum(DuelMessage.SelectSum prompt, int... indices) {
        for (int idx : indices) {
            checkIndex(idx, prompt.selectable().size());
        }
        checkNoDuplicates(indices);
        var selection = new SumSelection(prompt);
        for (int idx : indices) selection.toggle(idx);
        if (!selection.isComplete()) {
            throw new IllegalArgumentException(
                    "Selection does not reach the required sum of " + prompt.targetSum());
        }
        return ResponseBuilder.selectSum(indices);
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

    /** Validate and build a single-zone response for MSG_SELECT_PLACE. */
    public static byte[] selectPlace(DuelMessage.SelectPlace prompt,
                                     int player, int location, int sequence) {
        return selectPlaces(prompt, List.of(new int[]{player, location, sequence}));
    }

    /** Validate and build a response for MSG_SELECT_PLACE: {@code count} player/location/sequence triples. */
    public static byte[] selectPlaces(DuelMessage.SelectPlace prompt, List<int[]> zones) {
        checkPlaces(prompt.player(), prompt.count(), prompt.field(), zones);
        return ResponseBuilder.selectPlaces(zones);
    }

    /** Validate and build a response for MSG_SELECT_DISFIELD, which reads the same triples. */
    public static byte[] selectDisfield(DuelMessage.SelectDisfield prompt, List<int[]> zones) {
        checkPlaces(prompt.player(), prompt.count(), prompt.field(), zones);
        return ResponseBuilder.selectPlaces(zones);
    }

    /**
     * The engine reads exactly {@code count} triples and retries on a location that is neither
     * monster nor spell zone, a monster sequence above 6, a spell sequence above 7, a zone whose
     * bit is already set in the field mask, or a zone named twice ({@code playerop.cpp:569-595}).
     */
    private static void checkPlaces(int promptPlayer, int count, int field, List<int[]> zones) {
        if (zones.size() != count) {
            throw new IllegalArgumentException(
                    "Must select exactly " + count + " zone(s), got " + zones.size());
        }
        var seen = new HashSet<Integer>();
        for (int[] zone : zones) {
            int bit = zoneToBit(zone[0], promptPlayer, zone[1], zone[2]);
            if (bit < 0) {
                throw new IllegalArgumentException("Not a placeable zone: player=" + zone[0]
                        + " location=0x" + Integer.toHexString(zone[1]) + " sequence=" + zone[2]);
            }
            if ((field & (1 << bit)) != 0 || !seen.add(bit)) {
                throw new IllegalArgumentException("Zone is not selectable: player=" + zone[0]
                        + " location=0x" + Integer.toHexString(zone[1]) + " sequence=" + zone[2]);
            }
        }
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

    /**
     * Validate and build a response for MSG_SELECT_UNSELECT_CARD ({@code playerop.cpp:429-451}):
     * {@code -1} is accepted whenever either flag is set, and an index at or past the selectable
     * list addresses the unselect list.
     */
    public static byte[] selectUnselectCard(DuelMessage.SelectUnselectCard prompt, int index) {
        if (index == -1) {
            if (!prompt.finishable() && !prompt.cancelable()) {
                throw new IllegalArgumentException(
                        "Cannot finish selection — neither finishable nor cancelable");
            }
        } else {
            checkIndex(index, prompt.selectableCards().size() + prompt.unselectableCards().size());
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

    /**
     * Validate and build a response for MSG_ANNOUNCE_CARD. The engine answers with one {@code int32}
     * passcode and keeps it only if {@code is_declarable} passes ({@code playerop.cpp:1084-1088}),
     * which needs the card database; this checks the structural part alone.
     */
    public static byte[] announceCard(DuelMessage.AnnounceCard prompt, int code) {
        if (code <= 0) {
            throw new IllegalArgumentException("Card code must be positive, got " + code);
        }
        return ResponseBuilder.announceCard(code);
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
     * Convert a zone choice to its bit position in the field bitmask, or -1 when the engine would
     * not read it as a zone at all. The engine builds the same bit as
     * {@code seq + (SZONE ? 8 : 0) + (opponent of the prompted player ? 16 : 0)}
     * ({@code playerop.cpp:583-591}):
     *   bits 0-4:   prompted player's Main Monster Zones
     *   bits 5-6:   prompted player's Extra Monster Zones
     *   bits 8-12:  prompted player's Spell/Trap Zones
     *   bit 13:     prompted player's Field Zone
     *   bits 14-15: prompted player's Pendulum Zones
     *   bits 16-31: the same block for the other player
     */
    private static int zoneToBit(int player, int promptPlayer, int location, int sequence) {
        if (player != 0 && player != 1) return -1;
        int offset = (player != promptPlayer) ? 16 : 0;
        // MZONE holds 7 zones (0-6); SZONE 8 (0-4 spell/trap, 5 field, 6-7 pendulum).
        if (location == LOCATION_MZONE && sequence >= 0 && sequence < 7) return offset + sequence;
        if (location == LOCATION_SZONE && sequence >= 0 && sequence < 8) return offset + 8 + sequence;
        return -1;
    }
}

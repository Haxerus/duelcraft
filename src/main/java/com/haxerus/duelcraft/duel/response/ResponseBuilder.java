package com.haxerus.duelcraft.duel.response;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds little-endian binary response buffers for OCG_DuelSetResponse.
 * Each static factory method corresponds to a MSG_SELECT_* prompt type.
 */
public class ResponseBuilder {
    private final ByteBuffer buf;

    private ResponseBuilder(int capacity) {
        this.buf = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private ResponseBuilder putInt8(int value) {
        buf.put((byte) (value & 0xFF));
        return this;
    }

    private ResponseBuilder putInt32(int value) {
        buf.putInt(value);
        return this;
    }

    private ResponseBuilder putInt64(long value) {
        buf.putLong(value);
        return this;
    }

    private byte[] build() {
        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    // ---- Factory methods for each selection message type ----

    /**
     * MSG_SELECT_IDLECMD / MSG_SELECT_BATTLECMD response.
     * Packed as a single int32: (index << 16) | (actionType & 0xFFFF).
     * @param actionType action category (0=summon, 1=spsummon, 2=reposition,
     *                   3=set monster, 4=set S/T, 5=activate, 6=to battle, 7=end turn
     *                   for idle; 1=attack, 2=activate, 3=main2, 6=end battle for battle)
     * @param index index within that action category
     */
    public static byte[] selectCmd(int actionType, int index) {
        return new ResponseBuilder(4)
                .putInt32((index << 16) | (actionType & 0xFFFF))
                .build();
    }

    /**
     * MSG_SELECT_CARD / MSG_SELECT_TRIBUTE response.
     * Format: [int32 formatCode=0][int32 count][int32 indices...]
     * @param indices selected card indices (0-based, from the card list in the prompt)
     */
    public static byte[] selectCards(int... indices) {
        var rb = new ResponseBuilder(4 + 4 + 4 * indices.length);
        rb.putInt32(0); // format code: 0 = uint32 indices
        rb.putInt32(indices.length);
        for (int idx : indices) {
            rb.putInt32(idx);
        }
        return rb.build();
    }

    /**
     * MSG_SELECT_CARD cancel response.
     * Format: [int32 formatCode=-1] — only valid when the prompt is cancelable.
     */
    public static byte[] selectCardsCancel() {
        return new ResponseBuilder(4).putInt32(-1).build();
    }

    /**
     * MSG_SELECT_CHAIN response.
     * @param index chain index to activate, or -1 to decline
     */
    public static byte[] selectChain(int index) {
        return new ResponseBuilder(4).putInt32(index).build();
    }

    /**
     * MSG_SELECT_EFFECTYN / MSG_SELECT_YESNO response.
     * @param yes true for yes, false for no
     */
    public static byte[] selectYesNo(boolean yes) {
        return new ResponseBuilder(4).putInt32(yes ? 1 : 0).build();
    }

    /**
     * MSG_SELECT_OPTION response.
     * @param index index of the selected option
     */
    public static byte[] selectOption(int index) {
        return new ResponseBuilder(4).putInt32(index).build();
    }

    /**
     * MSG_SELECT_PLACE / MSG_SELECT_DISFIELD response.
     * @param player controller (0 or 1)
     * @param location LOCATION_MZONE or LOCATION_SZONE
     * @param sequence zone index (0-4 for main, 5-6 for EMZ)
     */
    public static byte[] selectPlace(int player, int location, int sequence) {
        return new ResponseBuilder(3)
                .putInt8(player)
                .putInt8(location)
                .putInt8(sequence)
                .build();
    }

    /**
     * MSG_SELECT_POSITION response.
     * @param position one of POS_FACEUP_ATTACK, POS_FACEDOWN_DEFENSE, etc.
     */
    public static byte[] selectPosition(int position) {
        return new ResponseBuilder(4).putInt32(position).build();
    }

    /**
     * MSG_SELECT_COUNTER response.
     * @param countsPerCard number of counters to remove from each card (in card list order)
     */
    public static byte[] selectCounter(int... countsPerCard) {
        var rb = new ResponseBuilder(2 * countsPerCard.length);
        for (int count : countsPerCard) {
            rb.buf.putShort((short) count);
        }
        return rb.build();
    }

    /**
     * MSG_SELECT_SUM response.
     * @param indices selected card indices from the selectable list
     */
    public static byte[] selectSum(int... indices) {
        return selectCards(indices); // same format
    }

    /**
     * MSG_SORT_CARD / MSG_SORT_CHAIN response.
     * @param order permutation of indices (e.g., {2, 0, 1} means card 2 first)
     */
    public static byte[] sortCards(int... order) {
        var rb = new ResponseBuilder(order.length);
        for (int idx : order) {
            rb.putInt8(idx);
        }
        return rb.build();
    }

    /**
     * MSG_SELECT_UNSELECT_CARD response.
     * @param index card index to select, or -1 to finish (if finishable)
     */
    public static byte[] selectUnselectCard(int index) {
        return new ResponseBuilder(4).putInt32(index).build();
    }

    /**
     * MSG_ROCK_PAPER_SCISSORS response.
     * @param choice 1=rock, 2=paper, 3=scissors
     */
    public static byte[] rockPaperScissors(int choice) {
        return new ResponseBuilder(4).putInt32(choice).build();
    }

    /**
     * MSG_ANNOUNCE_RACE response.
     * @param raceFlags bitwise OR of selected RACE_* constants
     */
    public static byte[] announceRace(long raceFlags) {
        return new ResponseBuilder(8).putInt64(raceFlags).build();
    }

    /**
     * MSG_ANNOUNCE_ATTRIB response.
     * @param attribFlags bitwise OR of selected ATTRIBUTE_* constants
     */
    public static byte[] announceAttrib(int attribFlags) {
        return new ResponseBuilder(4).putInt32(attribFlags).build();
    }

    /**
     * MSG_ANNOUNCE_NUMBER response.
     * @param index index of the selected number option
     */
    public static byte[] announceNumber(int index) {
        return new ResponseBuilder(4).putInt32(index).build();
    }
}

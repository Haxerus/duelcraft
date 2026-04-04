package com.haxerus.duelcraft.duel.response;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseBuilderTest {

    /** Read a little-endian int32 from the response at a byte offset. */
    static int readInt32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /** Read a little-endian int64 from the response at a byte offset. */
    static long readInt64(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    /** Read a little-endian int16 from the response at a byte offset. */
    static short readInt16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    // ---- selectCmd ----

    @Test
    void selectCmdNormalSummon() {
        byte[] resp = ResponseBuilder.selectCmd(0, 2);
        assertEquals(8, resp.length);
        assertEquals(0, readInt32(resp, 0)); // action type
        assertEquals(2, readInt32(resp, 4)); // index
    }

    @Test
    void selectCmdEndTurn() {
        byte[] resp = ResponseBuilder.selectCmd(7, 0);
        assertEquals(7, readInt32(resp, 0));
    }

    @Test
    void selectCmdAttack() {
        byte[] resp = ResponseBuilder.selectCmd(1, 0);
        assertEquals(1, readInt32(resp, 0));
        assertEquals(0, readInt32(resp, 4));
    }

    // ---- selectCards ----

    @Test
    void selectCardsSingle() {
        byte[] resp = ResponseBuilder.selectCards(3);
        assertEquals(8, resp.length);
        assertEquals(1, readInt32(resp, 0)); // count
        assertEquals(3, readInt32(resp, 4)); // index
    }

    @Test
    void selectCardsMultiple() {
        byte[] resp = ResponseBuilder.selectCards(0, 2, 4);
        assertEquals(16, resp.length);
        assertEquals(3, readInt32(resp, 0));
        assertEquals(0, readInt32(resp, 4));
        assertEquals(2, readInt32(resp, 8));
        assertEquals(4, readInt32(resp, 12));
    }

    // ---- selectChain ----

    @Test
    void selectChainActivate() {
        byte[] resp = ResponseBuilder.selectChain(0);
        assertEquals(4, resp.length);
        assertEquals(0, readInt32(resp, 0));
    }

    @Test
    void selectChainDecline() {
        byte[] resp = ResponseBuilder.selectChain(-1);
        assertEquals(-1, readInt32(resp, 0));
    }

    // ---- selectYesNo ----

    @Test
    void selectYes() {
        byte[] resp = ResponseBuilder.selectYesNo(true);
        assertEquals(1, readInt32(resp, 0));
    }

    @Test
    void selectNo() {
        byte[] resp = ResponseBuilder.selectYesNo(false);
        assertEquals(0, readInt32(resp, 0));
    }

    // ---- selectOption ----

    @Test
    void selectOption() {
        byte[] resp = ResponseBuilder.selectOption(2);
        assertEquals(2, readInt32(resp, 0));
    }

    // ---- selectPlace ----

    @Test
    void selectPlaceMonsterZone() {
        byte[] resp = ResponseBuilder.selectPlace(0, LOCATION_MZONE, 2);
        assertEquals(3, resp.length);
        assertEquals(0, resp[0] & 0xFF);           // player
        assertEquals(LOCATION_MZONE, resp[1] & 0xFF); // location
        assertEquals(2, resp[2] & 0xFF);            // sequence
    }

    @Test
    void selectPlaceSpellZone() {
        byte[] resp = ResponseBuilder.selectPlace(1, LOCATION_SZONE, 0);
        assertEquals(1, resp[0] & 0xFF);
        assertEquals(LOCATION_SZONE, resp[1] & 0xFF);
        assertEquals(0, resp[2] & 0xFF);
    }

    // ---- selectPosition ----

    @Test
    void selectPositionFaceUpAttack() {
        byte[] resp = ResponseBuilder.selectPosition(POS_FACEUP_ATTACK);
        assertEquals(POS_FACEUP_ATTACK, readInt32(resp, 0));
    }

    @Test
    void selectPositionFaceDownDefense() {
        byte[] resp = ResponseBuilder.selectPosition(POS_FACEDOWN_DEFENSE);
        assertEquals(POS_FACEDOWN_DEFENSE, readInt32(resp, 0));
    }

    // ---- selectCounter ----

    @Test
    void selectCounter() {
        byte[] resp = ResponseBuilder.selectCounter(2, 0, 1);
        assertEquals(6, resp.length);
        assertEquals(2, readInt16(resp, 0));
        assertEquals(0, readInt16(resp, 2));
        assertEquals(1, readInt16(resp, 4));
    }

    // ---- sortCards ----

    @Test
    void sortCards() {
        byte[] resp = ResponseBuilder.sortCards(2, 0, 1);
        assertEquals(3, resp.length);
        assertEquals(2, resp[0] & 0xFF);
        assertEquals(0, resp[1] & 0xFF);
        assertEquals(1, resp[2] & 0xFF);
    }

    // ---- selectUnselectCard ----

    @Test
    void selectUnselectCardPick() {
        byte[] resp = ResponseBuilder.selectUnselectCard(1);
        assertEquals(1, readInt32(resp, 0));
    }

    @Test
    void selectUnselectCardFinish() {
        byte[] resp = ResponseBuilder.selectUnselectCard(-1);
        assertEquals(-1, readInt32(resp, 0));
    }

    // ---- rockPaperScissors ----

    @Test
    void rockPaperScissorsRock() {
        assertEquals(1, readInt32(ResponseBuilder.rockPaperScissors(1), 0));
    }

    @Test
    void rockPaperScissorsPaper() {
        assertEquals(2, readInt32(ResponseBuilder.rockPaperScissors(2), 0));
    }

    // ---- announceRace ----

    @Test
    void announceRace() {
        long flags = RACE_DRAGON | RACE_SPELLCASTER;
        byte[] resp = ResponseBuilder.announceRace(flags);
        assertEquals(8, resp.length);
        assertEquals(flags, readInt64(resp, 0));
    }

    // ---- announceAttrib ----

    @Test
    void announceAttrib() {
        byte[] resp = ResponseBuilder.announceAttrib(ATTRIBUTE_DARK | ATTRIBUTE_LIGHT);
        assertEquals(ATTRIBUTE_DARK | ATTRIBUTE_LIGHT, readInt32(resp, 0));
    }

    // ---- announceNumber ----

    @Test
    void announceNumber() {
        byte[] resp = ResponseBuilder.announceNumber(3);
        assertEquals(3, readInt32(resp, 0));
    }

    // ---- selectSum (same format as selectCards) ----

    @Test
    void selectSum() {
        byte[] resp = ResponseBuilder.selectSum(1, 3);
        assertEquals(12, resp.length);
        assertEquals(2, readInt32(resp, 0));
        assertEquals(1, readInt32(resp, 4));
        assertEquals(3, readInt32(resp, 8));
    }
}

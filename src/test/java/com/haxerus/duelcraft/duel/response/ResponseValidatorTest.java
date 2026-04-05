package com.haxerus.duelcraft.duel.response;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ResponseValidatorTest {

    // ---- Helpers ----

    static DuelMessage.CardInfo card(int code) {
        return new DuelMessage.CardInfo(code, 0, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE);
    }

    static LocInfo loc(int con, int location, int seq, int pos) {
        return new LocInfo(con, location, seq, pos);
    }

    // ---- SelectCard ----

    @Test
    void selectCardValid() {
        var prompt = new DuelMessage.SelectCard(0, false, 1, 2,
                List.of(card(1), card(2), card(3)));
        // Select 1 card (within min=1, max=2)
        assertDoesNotThrow(() -> ResponseValidator.selectCards(prompt, 0));
        // Select 2 cards
        assertDoesNotThrow(() -> ResponseValidator.selectCards(prompt, 0, 2));
    }

    @Test
    void selectCardTooFew() {
        var prompt = new DuelMessage.SelectCard(0, false, 2, 3,
                List.of(card(1), card(2), card(3)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCards(prompt, 0)); // need at least 2
    }

    @Test
    void selectCardTooMany() {
        var prompt = new DuelMessage.SelectCard(0, false, 1, 1,
                List.of(card(1), card(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCards(prompt, 0, 1)); // max is 1
    }

    @Test
    void selectCardIndexOutOfRange() {
        var prompt = new DuelMessage.SelectCard(0, false, 1, 1,
                List.of(card(1), card(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCards(prompt, 5)); // only 2 cards
    }

    @Test
    void selectCardDuplicateIndex() {
        var prompt = new DuelMessage.SelectCard(0, false, 2, 2,
                List.of(card(1), card(2), card(3)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCards(prompt, 1, 1)); // duplicate
    }

    // ---- SelectChain ----

    static DuelMessage.ActivatableCard activatable(int code) {
        return new DuelMessage.ActivatableCard(code, 0, LOCATION_SZONE, 0, 0L, 0);
    }

    static DuelMessage.TributeCard tribute(int code) {
        return new DuelMessage.TributeCard(code, 0, LOCATION_MZONE, 0, 1);
    }

    static DuelMessage.SortableCard sortable(int code) {
        return new DuelMessage.SortableCard(code, 0, LOCATION_HAND, 0);
    }

    static DuelMessage.CounterCard counterCard(int code) {
        return new DuelMessage.CounterCard(code, 0, LOCATION_MZONE, 0, 3);
    }

    @Test
    void selectChainActivateValid() {
        var prompt = new DuelMessage.SelectChain(0, 0, false, 0, 0,
                List.of(activatable(1), activatable(2)));
        assertDoesNotThrow(() -> ResponseValidator.selectChain(prompt, 0));
        assertDoesNotThrow(() -> ResponseValidator.selectChain(prompt, 1));
    }

    @Test
    void selectChainDeclineWhenOptional() {
        var prompt = new DuelMessage.SelectChain(0, 0, false, 0, 0,
                List.of(activatable(1), activatable(2)));
        assertDoesNotThrow(() -> ResponseValidator.selectChain(prompt, -1));
    }

    @Test
    void selectChainDeclineWhenForced() {
        var prompt = new DuelMessage.SelectChain(0, 0, true, 0, 0,
                List.of(activatable(1), activatable(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectChain(prompt, -1));
    }

    @Test
    void selectChainIndexOutOfRange() {
        var prompt = new DuelMessage.SelectChain(0, 0, false, 0, 0,
                List.of(activatable(1), activatable(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectChain(prompt, 3));
    }

    // ---- SelectOption ----

    @Test
    void selectOptionValid() {
        var prompt = new DuelMessage.SelectOption(0, List.of(100L, 200L, 300L));
        assertDoesNotThrow(() -> ResponseValidator.selectOption(prompt, 0));
        assertDoesNotThrow(() -> ResponseValidator.selectOption(prompt, 2));
    }

    @Test
    void selectOptionOutOfRange() {
        var prompt = new DuelMessage.SelectOption(0, List.of(100L, 200L));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectOption(prompt, 2));
    }

    // ---- SelectPosition ----

    @Test
    void selectPositionValid() {
        var prompt = new DuelMessage.SelectPosition(0, 89631139,
                POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE);
        assertDoesNotThrow(() -> ResponseValidator.selectPosition(prompt, POS_FACEUP_ATTACK));
        assertDoesNotThrow(() -> ResponseValidator.selectPosition(prompt, POS_FACEUP_DEFENSE));
    }

    @Test
    void selectPositionNotAvailable() {
        var prompt = new DuelMessage.SelectPosition(0, 89631139,
                POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectPosition(prompt, POS_FACEDOWN_DEFENSE));
    }

    @Test
    void selectPositionMultipleBitsRejects() {
        var prompt = new DuelMessage.SelectPosition(0, 89631139,
                POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectPosition(prompt, POS_FACEUP)); // two bits set
    }

    // ---- SelectPlace ----

    @Test
    void selectPlaceValidZone() {
        // field bitmask: 0 = selectable. Bit 0 = MMZ 0, bit 1 = MMZ 1, etc.
        // Field with all zones open (all 0s)
        var prompt = new DuelMessage.SelectPlace(0, 1, 0x00000000);
        assertDoesNotThrow(() -> ResponseValidator.selectPlace(prompt, 0, LOCATION_MZONE, 0));
    }

    @Test
    void selectPlaceBlockedZone() {
        // Bit 0 set = MMZ 0 is unavailable
        var prompt = new DuelMessage.SelectPlace(0, 1, 0x00000001);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectPlace(prompt, 0, LOCATION_MZONE, 0));
    }

    @Test
    void selectPlaceSpellZone() {
        // Bit 8 = S/T zone 0 for choosing player. Set it to block.
        var prompt = new DuelMessage.SelectPlace(0, 1, 0x00000100);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectPlace(prompt, 0, LOCATION_SZONE, 0));
        // Zone 1 should be fine (bit 9 is clear)
        assertDoesNotThrow(() -> ResponseValidator.selectPlace(prompt, 0, LOCATION_SZONE, 1));
    }

    // ---- SelectUnselectCard ----

    @Test
    void selectUnselectCardValid() {
        var prompt = new DuelMessage.SelectUnselectCard(0, true, false, 1, 3,
                List.of(card(1), card(2)), List.of());
        assertDoesNotThrow(() -> ResponseValidator.selectUnselectCard(prompt, 0));
        assertDoesNotThrow(() -> ResponseValidator.selectUnselectCard(prompt, -1)); // finish
    }

    @Test
    void selectUnselectCardFinishNotAllowed() {
        var prompt = new DuelMessage.SelectUnselectCard(0, false, false, 1, 3,
                List.of(card(1)), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectUnselectCard(prompt, -1));
    }

    @Test
    void selectUnselectCardIndexOutOfRange() {
        var prompt = new DuelMessage.SelectUnselectCard(0, true, false, 1, 3,
                List.of(card(1)), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectUnselectCard(prompt, 5));
    }

    // ---- SortCard ----

    @Test
    void sortCardValid() {
        var prompt = new DuelMessage.SortCard(0, List.of(sortable(1), sortable(2), sortable(3)));
        assertDoesNotThrow(() -> ResponseValidator.sortCard(prompt, 2, 0, 1));
    }

    @Test
    void sortCardWrongLength() {
        var prompt = new DuelMessage.SortCard(0, List.of(sortable(1), sortable(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.sortCard(prompt, 0)); // need 2 entries
    }

    @Test
    void sortCardDuplicate() {
        var prompt = new DuelMessage.SortCard(0, List.of(sortable(1), sortable(2), sortable(3)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.sortCard(prompt, 0, 0, 1)); // duplicate 0
    }

    @Test
    void sortCardIndexOutOfRange() {
        var prompt = new DuelMessage.SortCard(0, List.of(sortable(1), sortable(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.sortCard(prompt, 0, 5));
    }

    // ---- AnnounceRace ----

    @Test
    void announceRaceValid() {
        var prompt = new DuelMessage.AnnounceRace(0, 1,
                RACE_DRAGON | RACE_SPELLCASTER | RACE_WARRIOR);
        assertDoesNotThrow(() -> ResponseValidator.announceRace(prompt, RACE_DRAGON));
    }

    @Test
    void announceRaceWrongCount() {
        var prompt = new DuelMessage.AnnounceRace(0, 1,
                RACE_DRAGON | RACE_SPELLCASTER);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.announceRace(prompt, RACE_DRAGON | RACE_SPELLCASTER));
    }

    @Test
    void announceRaceUnavailable() {
        var prompt = new DuelMessage.AnnounceRace(0, 1, RACE_DRAGON);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.announceRace(prompt, RACE_ZOMBIE)); // not available
    }

    // ---- AnnounceAttrib ----

    @Test
    void announceAttribValid() {
        var prompt = new DuelMessage.AnnounceAttrib(0, 1, ATTRIBUTE_ALL);
        assertDoesNotThrow(() -> ResponseValidator.announceAttrib(prompt, ATTRIBUTE_DARK));
    }

    @Test
    void announceAttribUnavailable() {
        var prompt = new DuelMessage.AnnounceAttrib(0, 1, ATTRIBUTE_LIGHT | ATTRIBUTE_DARK);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.announceAttrib(prompt, ATTRIBUTE_FIRE));
    }

    @Test
    void announceAttribWrongCount() {
        var prompt = new DuelMessage.AnnounceAttrib(0, 1, ATTRIBUTE_ALL);
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.announceAttrib(prompt, ATTRIBUTE_DARK | ATTRIBUTE_LIGHT));
    }

    // ---- AnnounceNumber ----

    @Test
    void announceNumberValid() {
        var prompt = new DuelMessage.AnnounceNumber(0, List.of(1L, 2L, 3L, 4L));
        assertDoesNotThrow(() -> ResponseValidator.announceNumber(prompt, 3));
    }

    @Test
    void announceNumberOutOfRange() {
        var prompt = new DuelMessage.AnnounceNumber(0, List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.announceNumber(prompt, 5));
    }

    // ---- RockPaperScissors ----

    @Test
    void rpsValid() {
        assertDoesNotThrow(() -> ResponseValidator.rockPaperScissors(1));
        assertDoesNotThrow(() -> ResponseValidator.rockPaperScissors(2));
        assertDoesNotThrow(() -> ResponseValidator.rockPaperScissors(3));
    }

    @Test
    void rpsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.rockPaperScissors(0));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.rockPaperScissors(4));
    }

    // ---- SelectCounter ----

    @Test
    void selectCounterValid() {
        var prompt = new DuelMessage.SelectCounter(0, 0x1, 3,
                List.of(counterCard(1), counterCard(2)));
        // Remove 2 from first card, 1 from second = 3 total
        assertDoesNotThrow(() -> ResponseValidator.selectCounter(prompt, 2, 1));
    }

    @Test
    void selectCounterWrongTotal() {
        var prompt = new DuelMessage.SelectCounter(0, 0x1, 3,
                List.of(counterCard(1), counterCard(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCounter(prompt, 1, 1)); // total 2, need 3
    }

    @Test
    void selectCounterWrongCardCount() {
        var prompt = new DuelMessage.SelectCounter(0, 0x1, 3,
                List.of(counterCard(1), counterCard(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCounter(prompt, 3)); // 1 entry, need 2
    }

    @Test
    void selectCounterNegative() {
        var prompt = new DuelMessage.SelectCounter(0, 0x1, 3,
                List.of(counterCard(1), counterCard(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectCounter(prompt, 4, -1));
    }

    // ---- SelectTribute ----

    @Test
    void selectTributeValid() {
        var prompt = new DuelMessage.SelectTribute(0, false, 1, 2,
                List.of(tribute(1), tribute(2), tribute(3)));
        assertDoesNotThrow(() -> ResponseValidator.selectTribute(prompt, 0));
        assertDoesNotThrow(() -> ResponseValidator.selectTribute(prompt, 0, 2));
    }

    @Test
    void selectTributeDuplicate() {
        var prompt = new DuelMessage.SelectTribute(0, false, 2, 2,
                List.of(tribute(1), tribute(2), tribute(3)));
        assertThrows(IllegalArgumentException.class,
                () -> ResponseValidator.selectTribute(prompt, 1, 1));
    }

    // ---- YesNo (trivially valid, but test the pass-through) ----

    @Test
    void selectEffectYnReturnsResponse() {
        var prompt = new DuelMessage.SelectEffectYn(0, 44095762,
                loc(0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE), 0L);
        byte[] resp = ResponseValidator.selectEffectYn(prompt, true);
        assertNotNull(resp);
        assertEquals(4, resp.length);
    }

    @Test
    void selectYesNoReturnsResponse() {
        var prompt = new DuelMessage.SelectYesNo(0, 200L);
        byte[] resp = ResponseValidator.selectYesNo(prompt, false);
        assertNotNull(resp);
    }
}

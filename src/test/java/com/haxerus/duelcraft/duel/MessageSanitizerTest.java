package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Host policy ported from edopro's {@code generic_duel.cpp} / {@code core_utils.cpp}; every rule
 * is checked from both players' point of view.
 */
class MessageSanitizerTest {

    private static final int BLUE_EYES = 89631139;
    private static final int DARK_MAGICIAN = 46986414;

    private static LocInfo loc(int controller, int location, int sequence, int position) {
        return new LocInfo(controller, location, sequence, position);
    }

    // ---- MSG_MOVE (generic_duel.cpp:1032-1033) ----

    @Test
    void moveToHandHidesTheCodeForTheOpponentOnly() {
        // send_to forces POS_FACEUP for every destination except REMOVED, so the position
        // cannot be the test: a searched card is private because it lands in the hand.
        var move = new DuelMessage.Move(BLUE_EYES,
                loc(0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE),
                loc(0, LOCATION_HAND, 3, POS_FACEUP_ATTACK), 0);

        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 0)).code());
        assertEquals(0, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 1)).code());
    }

    @Test
    void moveToDeckHidesTheCodeForTheOpponentOnly() {
        var move = new DuelMessage.Move(BLUE_EYES,
                loc(1, LOCATION_HAND, 0, POS_FACEUP_ATTACK),
                loc(1, LOCATION_DECK, 0, POS_FACEUP_ATTACK), 0);

        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 1)).code());
        assertEquals(0, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 0)).code());
    }

    @Test
    void moveToGraveKeepsTheCodeForBothPlayers() {
        // GRAVE is excluded from the predicate even when the position bits say face-down.
        var move = new DuelMessage.Move(BLUE_EYES,
                loc(0, LOCATION_MZONE, 2, POS_FACEDOWN_DEFENSE),
                loc(0, LOCATION_GRAVE, 0, POS_FACEDOWN_DEFENSE), 0);

        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 0)).code());
        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 1)).code());
    }

    @Test
    void moveSetFaceDownOnTheFieldHidesTheCodeForTheOpponentOnly() {
        var move = new DuelMessage.Move(BLUE_EYES,
                loc(0, LOCATION_HAND, 1, POS_FACEUP_ATTACK),
                loc(0, LOCATION_SZONE, 2, POS_FACEDOWN_DEFENSE), 0);

        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 0)).code());
        assertEquals(0, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 1)).code());
    }

    @Test
    void moveToOverlayKeepsTheCodeForBothPlayers() {
        var move = new DuelMessage.Move(BLUE_EYES,
                loc(0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK),
                loc(1, LOCATION_MZONE | LOCATION_OVERLAY, 0, POS_FACEDOWN_DEFENSE), 0);

        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 0)).code());
        assertEquals(BLUE_EYES, ((DuelMessage.Move) MessageSanitizer.forRecipient(move, 1)).code());
    }

    // ---- MSG_DRAW (generic_duel.cpp:1080-1084) ----

    @Test
    void faceUpDrawStaysVisibleToTheOpponentAndFaceDownDoesNot() {
        var draw = new DuelMessage.Draw(0, List.of(
                new DuelMessage.DrawnCard(BLUE_EYES, POS_FACEUP_ATTACK),
                new DuelMessage.DrawnCard(DARK_MAGICIAN, POS_FACEDOWN_DEFENSE)));

        var drawer = (DuelMessage.Draw) MessageSanitizer.forRecipient(draw, 0);
        assertEquals(List.of(BLUE_EYES, DARK_MAGICIAN), drawer.cards().stream().map(DuelMessage.DrawnCard::code).toList());

        var opponent = (DuelMessage.Draw) MessageSanitizer.forRecipient(draw, 1);
        assertEquals(List.of(BLUE_EYES, 0), opponent.cards().stream().map(DuelMessage.DrawnCard::code).toList());
        assertEquals(List.of(POS_FACEUP_ATTACK, POS_FACEDOWN_DEFENSE),
                opponent.cards().stream().map(DuelMessage.DrawnCard::position).toList());
    }

    // ---- Prompt candidates (generic_duel.cpp:933-977) ----

    @Test
    void selectCardZeroesCandidatesThePromptedPlayerDoesNotControl() {
        var sel = new DuelMessage.SelectCard(0, false, 1, 1, List.of(
                new DuelMessage.CardInfo(BLUE_EYES, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                new DuelMessage.CardInfo(DARK_MAGICIAN, 1, LOCATION_MZONE, 0, POS_FACEDOWN_DEFENSE)));

        for (int recipient = 0; recipient < 2; recipient++) {
            var out = (DuelMessage.SelectCard) MessageSanitizer.forRecipient(sel, recipient);
            assertEquals(BLUE_EYES, out.cards().get(0).code());
            assertEquals(0, out.cards().get(1).code());
            assertEquals(LOCATION_MZONE, out.cards().get(1).location());
        }
    }

    @Test
    void selectTributeZeroesCandidatesThePromptedPlayerDoesNotControl() {
        var sel = new DuelMessage.SelectTribute(1, false, 1, 2, List.of(
                new DuelMessage.TributeCard(BLUE_EYES, 0, LOCATION_MZONE, 0, 1),
                new DuelMessage.TributeCard(DARK_MAGICIAN, 1, LOCATION_MZONE, 1, 2)));

        for (int recipient = 0; recipient < 2; recipient++) {
            var out = (DuelMessage.SelectTribute) MessageSanitizer.forRecipient(sel, recipient);
            assertEquals(0, out.cards().get(0).code());
            assertEquals(DARK_MAGICIAN, out.cards().get(1).code());
            assertEquals(2, out.cards().get(1).tributeCount());
        }
    }

    @Test
    void selectUnselectCardZeroesForeignCodesInBothLists() {
        var sel = new DuelMessage.SelectUnselectCard(0, false, false, 1, 1,
                List.of(new DuelMessage.CardInfo(BLUE_EYES, 1, LOCATION_MZONE, 0, POS_FACEDOWN_DEFENSE)),
                List.of(new DuelMessage.CardInfo(DARK_MAGICIAN, 0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK)));

        for (int recipient = 0; recipient < 2; recipient++) {
            var out = (DuelMessage.SelectUnselectCard) MessageSanitizer.forRecipient(sel, recipient);
            assertEquals(0, out.selectableCards().getFirst().code());
            assertEquals(DARK_MAGICIAN, out.unselectableCards().getFirst().code());
        }
    }

    // ---- MSG_CONFIRM_CARDS (generic_duel.cpp:985-1005) ----

    @Test
    void confirmCardsFromTheDeckReachesTheTargetPlayerOnly() {
        var confirm = new DuelMessage.ConfirmCards(1,
                List.of(new DuelMessage.ConfirmCard(BLUE_EYES, 1, LOCATION_DECK, 0)));

        var recipients = MessageSanitizer.recipientsOf(confirm);
        assertFalse(recipients.includes(0));
        assertTrue(recipients.includes(1));
    }

    @Test
    void confirmCardsFromTheExtraDeckReachesTheTargetPlayerOnly() {
        var confirm = new DuelMessage.ConfirmCards(0,
                List.of(new DuelMessage.ConfirmCard(BLUE_EYES, 0, LOCATION_EXTRA, 0)));

        var recipients = MessageSanitizer.recipientsOf(confirm);
        assertTrue(recipients.includes(0));
        assertFalse(recipients.includes(1));
    }

    @Test
    void confirmCardsOnTheFieldReachesBothPlayers() {
        var confirm = new DuelMessage.ConfirmCards(0,
                List.of(new DuelMessage.ConfirmCard(BLUE_EYES, 1, LOCATION_MZONE, 0)));

        var recipients = MessageSanitizer.recipientsOf(confirm);
        assertTrue(recipients.includes(0));
        assertTrue(recipients.includes(1));
    }

    // ---- MSG_HINT (generic_duel.cpp:843-880) ----

    @Test
    void selectMessageHintReachesTheTargetPlayerOnly() {
        var recipients = MessageSanitizer.recipientsOf(new DuelMessage.Hint(3, 1, 0));
        assertFalse(recipients.includes(0));
        assertTrue(recipients.includes(1));
    }

    @Test
    void raceHintReachesEveryoneExceptTheTargetPlayer() {
        var recipients = MessageSanitizer.recipientsOf(new DuelMessage.Hint(6, 1, 0));
        assertTrue(recipients.includes(0));
        assertFalse(recipients.includes(1));
    }

    @Test
    void cardHintReachesBothPlayers() {
        var recipients = MessageSanitizer.recipientsOf(new DuelMessage.Hint(10, 0, BLUE_EYES));
        assertTrue(recipients.includes(0));
        assertTrue(recipients.includes(1));
    }

    @Test
    void messagesWithoutARuleReachBothPlayers() {
        var recipients = MessageSanitizer.recipientsOf(new DuelMessage.NewPhase(PHASE_MAIN1));
        assertTrue(recipients.includes(0));
        assertTrue(recipients.includes(1));
    }

    // ---- MSG_SHUFFLE_HAND (generic_duel.cpp:1013-1014) ----

    @Test
    void shuffleHandKeepsCodesForTheOwnerAndZeroesThemForTheOpponent() {
        var shuffle = new DuelMessage.ShuffleHand(0, List.of(BLUE_EYES, DARK_MAGICIAN));

        assertEquals(List.of(BLUE_EYES, DARK_MAGICIAN),
                ((DuelMessage.ShuffleHand) MessageSanitizer.forRecipient(shuffle, 0)).codes());
        assertEquals(List.of(0, 0),
                ((DuelMessage.ShuffleHand) MessageSanitizer.forRecipient(shuffle, 1)).codes());
    }

    // ---- Query hiding (core_utils.cpp:153-160, 224-232) ----

    private static QueriedCard queried(int position, boolean isPublic, boolean isHidden) {
        var card = new QueriedCard();
        card.flags = QUERY_CODE | QUERY_POSITION | QUERY_ATTACK | QUERY_IS_PUBLIC | QUERY_IS_HIDDEN
                | QUERY_REASON | QUERY_COUNTERS;
        card.code = BLUE_EYES;
        card.position = position;
        card.attack = 3000;
        card.isPublic = isPublic;
        card.isHidden = isHidden;
        card.reason = REASON_EFFECT;
        card.counters = List.of(1);
        return card;
    }

    private static QueriedCard sanitizedForOpponent(QueriedCard card) {
        var update = new DuelMessage.UpdateData(0, LOCATION_MZONE, List.of(card));
        return ((DuelMessage.UpdateData) MessageSanitizer.forRecipient(update, 1)).cards().getFirst();
    }

    @Test
    void faceDownCardLosesItsPrivateFieldsAndTheirFlagBits() {
        var card = queried(POS_FACEDOWN_DEFENSE, false, false);

        var hidden = sanitizedForOpponent(card);
        assertEquals(0, hidden.flags & QUERY_CODE, "CODE flag must be cleared, not just zeroed");
        assertEquals(0, hidden.flags & QUERY_ATTACK);
        assertEquals(0, hidden.code);
        assertEquals(0, hidden.attack);
        // Public fields and their flags survive.
        assertEquals(QUERY_POSITION, hidden.flags & QUERY_POSITION);
        assertEquals(POS_FACEDOWN_DEFENSE, hidden.position);
        assertEquals(QUERY_REASON | QUERY_COUNTERS | QUERY_IS_PUBLIC,
                hidden.flags & (QUERY_REASON | QUERY_COUNTERS | QUERY_IS_PUBLIC));
        assertEquals(REASON_EFFECT, hidden.reason);
        assertEquals(List.of(1), hidden.counters);
    }

    @Test
    void ownerKeepsEveryFieldOfTheirFaceDownCard() {
        var update = new DuelMessage.UpdateData(0, LOCATION_MZONE,
                List.of(queried(POS_FACEDOWN_DEFENSE, false, false)));

        var mine = ((DuelMessage.UpdateData) MessageSanitizer.forRecipient(update, 0)).cards().getFirst();
        assertEquals(BLUE_EYES, mine.code);
        assertEquals(QUERY_CODE, mine.flags & QUERY_CODE);
    }

    @Test
    void faceUpCardIsUntouchedForTheOpponent() {
        var card = queried(POS_FACEUP_ATTACK, false, false);
        assertSame(card, sanitizedForOpponent(card));
    }

    @Test
    void faceDownButPublicCardIsUntouchedForTheOpponent() {
        var card = queried(POS_FACEDOWN_DEFENSE, true, false);
        assertSame(card, sanitizedForOpponent(card));
    }

    @Test
    void hiddenCardIsStrippedEvenWhileFaceUp() {
        var hidden = sanitizedForOpponent(queried(POS_FACEUP_ATTACK, false, true));
        assertEquals(0, hidden.flags & QUERY_CODE);
        assertEquals(0, hidden.code);
        assertEquals(POS_FACEUP_ATTACK, hidden.position);
    }
}

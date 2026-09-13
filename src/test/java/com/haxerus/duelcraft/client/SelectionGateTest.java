package com.haxerus.duelcraft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionGateTest {

    // ── SELECT_CARD: progress is the card count ──

    @Test
    void cardSelectionSubmitsOnceMaxCardsArePicked() {
        assertEquals(SelectionGate.SUBMIT, SelectionGate.of(2, 2, 1, 2, 5, false));
    }

    @Test
    void cardSelectionOffersFinishBetweenMinAndMax() {
        assertEquals(SelectionGate.FINISH, SelectionGate.of(1, 1, 1, 2, 5, false));
    }

    @Test
    void cardSelectionSubmitsWhenEverySelectableIsPickedAndMinIsMet() {
        assertEquals(SelectionGate.SUBMIT, SelectionGate.of(2, 2, 1, 3, 2, false));
    }

    @Test
    void cardSelectionOffersCancelBeforeMinWithNothingPicked() {
        assertEquals(SelectionGate.CANCEL, SelectionGate.of(0, 0, 2, 3, 5, true));
    }

    @Test
    void cardSelectionHidesTheButtonBeforeMinWithSomethingPicked() {
        assertEquals(SelectionGate.HIDDEN, SelectionGate.of(1, 1, 2, 3, 5, true));
    }

    @Test
    void cardSelectionHidesTheButtonBeforeMinWhenNotCancelable() {
        assertEquals(SelectionGate.HIDDEN, SelectionGate.of(0, 0, 2, 3, 5, false));
    }

    // ── SELECT_TRIBUTE: max bounds the card count, min bounds the summed tribute value ──

    @Test
    void tributeOffersFinishOnceTheSummedTributeMeetsMin() {
        // One double-tribute monster picked for a two-tribute summon: 1 card, sum 2.
        assertEquals(SelectionGate.FINISH, SelectionGate.of(1, 2, 2, 2, 3, false));
    }

    @Test
    void tributeDoesNotSubmitWhenOnlyTheSumIsMet() {
        // The old behaviour submitted here, which made over-tributing impossible.
        assertEquals(SelectionGate.FINISH, SelectionGate.of(1, 2, 2, 3, 4, false));
    }

    @Test
    void tributeSubmitsWhenTheCardCountReachesMax() {
        assertEquals(SelectionGate.SUBMIT, SelectionGate.of(2, 3, 2, 2, 4, false));
    }

    @Test
    void tributeHidesTheButtonWhileTheSumIsShortOfMin() {
        assertEquals(SelectionGate.HIDDEN, SelectionGate.of(1, 1, 2, 3, 4, false));
    }

    @Test
    void tributeSubmitsWhenEveryCandidateIsTributedAndTheSumIsMet() {
        assertEquals(SelectionGate.SUBMIT, SelectionGate.of(2, 2, 2, 3, 2, false));
    }
}

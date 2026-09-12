package com.haxerus.duelcraft.duel.response;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.LOCATION_MZONE;
import static com.haxerus.duelcraft.core.OcgConstants.POS_FACEUP_ATTACK;
import static org.junit.jupiter.api.Assertions.*;

class SumSelectionTest {

    /** Build a SelectSum whose cards carry the given sum_param values. */
    private static DuelMessage.SelectSum prompt(boolean atLeastMode, int target, int min, int max,
                                                int[] mustParams, int[] selectableParams) {
        return new DuelMessage.SelectSum(0, atLeastMode, target, min, max,
                cards(mustParams, 0), cards(selectableParams, mustParams.length));
    }

    private static List<DuelMessage.SumCard> cards(int[] params, int seqOffset) {
        var list = new ArrayList<DuelMessage.SumCard>(params.length);
        for (int i = 0; i < params.length; i++) {
            list.add(new DuelMessage.SumCard(1000 + i, 0, LOCATION_MZONE, seqOffset + i,
                    POS_FACEUP_ATTACK, params[i]));
        }
        return list;
    }

    // ---- Mode 0: exact sum within [min, max] picks ----

    @Test
    void mode0CompletesOnExactSum() {
        var sel = new SumSelection(prompt(false, 8, 1, 3, new int[0], new int[]{4, 4, 8}));
        assertFalse(sel.isComplete());

        sel.toggle(2);
        assertTrue(sel.isComplete());
        assertArrayEquals(new int[]{2}, sel.responseIndices());
        assertEquals(8, sel.currentSum());
    }

    @Test
    void mode0RejectsInexactSum() {
        var sel = new SumSelection(prompt(false, 8, 1, 3, new int[0], new int[]{4, 5}));
        sel.toggle(1);
        assertFalse(sel.isComplete());
        assertEquals(5, sel.currentSum());
    }

    @Test
    void mode0HonoursMinimumPickCount() {
        var sel = new SumSelection(prompt(false, 8, 2, 3, new int[0], new int[]{4, 4, 8}));
        sel.toggle(2);
        assertFalse(sel.isComplete(), "one pick is below min=2 even though the sum is exact");

        sel.toggle(2);
        sel.toggle(0);
        sel.toggle(1);
        assertTrue(sel.isComplete());
        assertArrayEquals(new int[]{0, 1}, sel.responseIndices());
    }

    @Test
    void mode0OffersOnlyPicksThatCanStillComplete() {
        var sel = new SumSelection(prompt(false, 8, 1, 3, new int[0], new int[]{5, 4, 4}));
        assertFalse(sel.canPick(0), "5 cannot reach 8 with the remaining 4s");
        assertTrue(sel.canPick(1));

        sel.toggle(1);
        assertFalse(sel.canPick(0), "4 + 5 overshoots 8");
        assertTrue(sel.canPick(2));
    }

    @Test
    void toggleDeselects() {
        var sel = new SumSelection(prompt(false, 8, 1, 3, new int[0], new int[]{4, 4}));
        sel.toggle(0);
        assertTrue(sel.isSelected(0));
        sel.toggle(0);
        assertFalse(sel.isSelected(0));
        assertEquals(0, sel.responseIndices().length);
    }

    // ---- value2: either packed value may complete the sum ----

    @Test
    void eitherPackedValueCanCompleteTheSum() {
        int both = (7 << 16) | 3;
        var byLow = new SumSelection(prompt(false, 3, 1, 1, new int[0], new int[]{both}));
        byLow.toggle(0);
        assertTrue(byLow.isComplete(), "value1 = 3 completes the sum");

        var byHigh = new SumSelection(prompt(false, 7, 1, 1, new int[0], new int[]{both}));
        byHigh.toggle(0);
        assertTrue(byHigh.isComplete(), "value2 = 7 completes the sum");
        assertEquals(7, byHigh.currentSum(), "the caption must report the accepted total, not value1");
    }

    @Test
    void aCompleteSelectionCanStillBeExtendable() {
        // Card A pays 4 or 8, card B pays 4: {A} is already exact, {A, B} is exact too, so the
        // UI cannot rely on "nothing left to pick" to submit.
        var sel = new SumSelection(prompt(false, 8, 1, 3, new int[0], new int[]{(8 << 16) | 4, 4}));
        sel.toggle(0);
        assertTrue(sel.isComplete());
        assertTrue(sel.hasPickable(), "a second card would also be legal");
    }

    // ---- Mode 1: sum at least the target ----

    @Test
    void mode1CompletesWhenSumReachesTarget() {
        var sel = new SumSelection(prompt(true, 5, 1, 1, new int[0], new int[]{3, 3, 3}));
        sel.toggle(0);
        assertFalse(sel.isComplete(), "3 is short of 5");

        sel.toggle(1);
        assertTrue(sel.isComplete(), "3 + 3 reaches 5");
        assertEquals(6, sel.currentSum(), "mode 1 reports the total reached, not the target");
    }

    @Test
    void mode1RejectsARedundantPick() {
        var sel = new SumSelection(prompt(true, 5, 1, 1, new int[0], new int[]{3, 3, 3}));
        sel.toggle(0);
        sel.toggle(1);
        assertFalse(sel.canPick(2), "a third card would be redundant");

        sel.toggle(2);
        assertFalse(sel.isComplete(), "the engine rejects a selection with a removable card");
    }

    // ---- Must-select cards ----

    @Test
    void mustSelectAloneCanCompleteTheSum() {
        var sel = new SumSelection(prompt(false, 8, 0, 2, new int[]{8}, new int[]{4}));
        assertTrue(sel.isComplete());
        assertEquals(0, sel.responseIndices().length);
        assertEquals(8, sel.currentSum());
    }

    @Test
    void responseIndicesNeverIncludeMustSelectCards() {
        var sel = new SumSelection(prompt(false, 8, 1, 2, new int[]{4}, new int[]{4, 3}));
        sel.toggle(0);
        assertTrue(sel.isComplete());
        assertArrayEquals(new int[]{0}, sel.responseIndices(),
                "indices address the selectable list only");
    }

    // ---- First viable combination (solo AI) ----

    @Test
    void firstViableCombinationAnswersTheSum() {
        var prompt = prompt(false, 8, 1, 3, new int[]{2}, new int[]{5, 6, 3});
        int[] picks = SumSelection.firstViableCombination(prompt);
        assertNotNull(picks);

        var sel = new SumSelection(prompt);
        for (int i : picks) sel.toggle(i);
        assertTrue(sel.isComplete());
    }

    @Test
    void firstViableCombinationIsNullWhenNothingWorks() {
        assertNull(SumSelection.firstViableCombination(
                prompt(false, 9, 1, 2, new int[0], new int[]{2, 4})));
    }
}

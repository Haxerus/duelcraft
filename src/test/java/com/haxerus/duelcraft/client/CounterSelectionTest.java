package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CounterSelectionTest {

    /** Two field cards with 2 and 1 counters, total to remove = 3 (all of them). */
    private static DuelMessage.SelectCounter prompt(int total, int... counterCounts) {
        var cards = new java.util.ArrayList<DuelMessage.CounterCard>();
        for (int i = 0; i < counterCounts.length; i++) {
            cards.add(new DuelMessage.CounterCard(1000 + i, 0, 0, i, counterCounts[i]));
        }
        return new DuelMessage.SelectCounter(0, 1, total, cards);
    }

    @Test
    void remainingStartsAtPromptCount() {
        var sel = new CounterSelection(prompt(3, 2, 1));
        assertEquals(3, sel.remaining());
        assertFalse(sel.isComplete());
    }

    @Test
    void pickRemovesOneCounterFromThatCardAndOverall() {
        var sel = new CounterSelection(prompt(3, 2, 1));
        sel.pick(0);
        assertEquals(2, sel.remaining());
        assertEquals(1, sel.remainingFor(0));
        assertEquals(1, sel.remainingFor(1));
    }

    @Test
    void canPickFalseOnceCardIsExhausted() {
        var sel = new CounterSelection(prompt(3, 2, 1));
        sel.pick(1); // card 1 had only 1 counter
        assertFalse(sel.canPick(1));
        assertTrue(sel.canPick(0));
    }

    @Test
    void canPickFalseOnceOverallTargetIsMet() {
        var sel = new CounterSelection(prompt(2, 2, 1));
        sel.pick(0);
        sel.pick(0);
        assertTrue(sel.isComplete());
        // Card 1 still has a counter, but the overall target is already met.
        assertFalse(sel.canPick(1));
    }

    @Test
    void pickIsNoOpWhenNotPickable() {
        var sel = new CounterSelection(prompt(1, 1));
        sel.pick(0);
        assertTrue(sel.isComplete());
        sel.pick(0); // already exhausted
        assertEquals(0, sel.remaining());
        assertArrayEquals(new int[]{1}, sel.response());
    }

    @Test
    void responseReflectsRemovedCountsInMessageOrder() {
        var sel = new CounterSelection(prompt(3, 2, 1));
        sel.pick(0);
        sel.pick(1);
        sel.pick(0);
        assertTrue(sel.isComplete());
        assertArrayEquals(new int[]{2, 1}, sel.response());
    }
}

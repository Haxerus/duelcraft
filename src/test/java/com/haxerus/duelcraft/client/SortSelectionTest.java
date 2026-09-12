package com.haxerus.duelcraft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SortSelectionTest {

    @Test
    void startsWithNoOrdinalsAssigned() {
        var sel = new SortSelection(3);
        assertEquals(0, sel.ordinalOf(0));
        assertEquals(0, sel.ordinalOf(1));
        assertEquals(0, sel.ordinalOf(2));
        assertFalse(sel.isComplete());
    }

    @Test
    void assignGivesTheNextOrdinal() {
        var sel = new SortSelection(3);
        sel.assign(2);
        sel.assign(0);
        assertEquals(1, sel.ordinalOf(2));
        assertEquals(2, sel.ordinalOf(0));
        assertEquals(0, sel.ordinalOf(1));
    }

    @Test
    void isCompleteOnceEveryCardIsNumbered() {
        var sel = new SortSelection(2);
        assertFalse(sel.isComplete());
        sel.assign(0);
        assertFalse(sel.isComplete());
        sel.assign(1);
        assertTrue(sel.isComplete());
    }

    @Test
    void reAssigningAnOrdinalRemovesItAndShiftsLaterOnesDown() {
        var sel = new SortSelection(3);
        sel.assign(0); // ordinal 1
        sel.assign(1); // ordinal 2
        sel.assign(2); // ordinal 3
        sel.assign(0); // un-assign ordinal 1 -> 1,2 shift down to 0,1
        assertEquals(0, sel.ordinalOf(0));
        assertEquals(1, sel.ordinalOf(1));
        assertEquals(2, sel.ordinalOf(2));
        assertFalse(sel.isComplete());
    }

    @Test
    void responseIsZeroBasedDestinationRankPerOriginalCard() {
        var sel = new SortSelection(3);
        sel.assign(2); // card 2 -> rank 0
        sel.assign(0); // card 0 -> rank 1
        sel.assign(1); // card 1 -> rank 2
        assertTrue(sel.isComplete());
        assertArrayEquals(new int[]{1, 2, 0}, sel.response());
    }
}

package com.haxerus.duelcraft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitSelectionTest {

    @Test
    void bitsListsOnlyAvailableBitsAscending() {
        var sel = new BitSelection(0b1011, 1); // bits 0, 1, 3
        assertEquals(java.util.List.of(0, 1, 3), sel.bits());
    }

    @Test
    void toggleIsIgnoredForABitNotInAvailable() {
        var sel = new BitSelection(0b0001, 1); // only bit 0 available
        sel.toggle(1);
        assertFalse(sel.isChecked(1));
        assertFalse(sel.isComplete());
    }

    @Test
    void toggleChecksThenUnchecksAnAvailableBit() {
        var sel = new BitSelection(0b0001, 1);
        sel.toggle(0);
        assertTrue(sel.isChecked(0));
        sel.toggle(0);
        assertFalse(sel.isChecked(0));
    }

    @Test
    void toggleRefusesANewPickOnceCountIsReached() {
        var sel = new BitSelection(0b0111, 1); // bits 0, 1, 2 available, only 1 pick allowed
        sel.toggle(0);
        assertTrue(sel.isChecked(0));
        sel.toggle(1); // count already reached, ignored
        assertFalse(sel.isChecked(1));
        assertTrue(sel.isChecked(0));
    }

    @Test
    void isCompleteOnlyWhenExactlyCountBitsAreChecked() {
        var sel = new BitSelection(0b0111, 2);
        assertFalse(sel.isComplete());
        sel.toggle(0);
        assertFalse(sel.isComplete());
        sel.toggle(1);
        assertTrue(sel.isComplete());
    }

    @Test
    void maskIsTheBitwiseOrOfCheckedBits() {
        var sel = new BitSelection(0b1011, 2);
        sel.toggle(0);
        sel.toggle(3);
        assertEquals(0b1001L, sel.mask());
    }

    @Test
    void uncheckingAfterCompleteAllowsAnotherPick() {
        var sel = new BitSelection(0b0111, 1);
        sel.toggle(0);
        assertTrue(sel.isComplete());
        sel.toggle(0); // uncheck
        assertFalse(sel.isComplete());
        sel.toggle(1);
        assertTrue(sel.isChecked(1));
        assertTrue(sel.isComplete());
    }
}

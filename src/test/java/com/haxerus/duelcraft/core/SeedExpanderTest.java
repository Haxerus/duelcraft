package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedExpanderTest {

    @Test
    void sameInputProducesSameOutput() {
        long[] a = SeedExpander.toFourLongs(42L);
        long[] b = SeedExpander.toFourLongs(42L);
        assertArrayEquals(a, b);
    }

    @Test
    void differentInputsProduceDifferentOutputs() {
        long[] a = SeedExpander.toFourLongs(42L);
        long[] b = SeedExpander.toFourLongs(43L);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void outputHasFourElements() {
        assertEquals(4, SeedExpander.toFourLongs(0L).length);
    }

    @Test
    void zeroSeedDoesNotProduceAllZeroOutput() {
        long[] out = SeedExpander.toFourLongs(0L);
        boolean anyNonZero = false;
        for (long v : out) if (v != 0L) { anyNonZero = true; break; }
        assertTrue(anyNonZero, "SplitMix64 must scramble even seed=0");
    }
}

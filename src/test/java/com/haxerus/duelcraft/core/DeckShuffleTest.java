package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckShuffleTest {

    private static Deck sampleDeck() {
        return new Deck(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
                        29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40),
                List.of(100, 101, 102, 103, 104));
    }

    @Test
    void sameSeedProducesSameMainOrder() {
        Deck a = sampleDeck().shuffled(42L);
        Deck b = sampleDeck().shuffled(42L);
        assertEquals(a.main(), b.main());
    }

    @Test
    void differentSeedsProduceDifferentMainOrder() {
        Deck a = sampleDeck().shuffled(42L);
        Deck b = sampleDeck().shuffled(43L);
        assertNotEquals(a.main(), b.main());
    }

    @Test
    void shuffledMainContainsSameElements() {
        Deck shuffled = sampleDeck().shuffled(42L);
        assertEquals(sampleDeck().main().size(), shuffled.main().size());
        assertTrue(shuffled.main().containsAll(sampleDeck().main()));
        assertTrue(sampleDeck().main().containsAll(shuffled.main()));
    }

    @Test
    void extraDeckIsNotShuffled() {
        Deck shuffled = sampleDeck().shuffled(42L);
        assertEquals(sampleDeck().extra(), shuffled.extra());
    }

    @Test
    void originalDeckIsNotMutated() {
        Deck original = sampleDeck();
        List<Integer> originalMainBefore = List.copyOf(original.main());
        original.shuffled(42L);
        assertEquals(originalMainBefore, original.main());
    }
}

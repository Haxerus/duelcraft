package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.Deck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeckListTest {
    @Test void sideCopiesCountButDoNotEnterEngineDeck() {
        var list = new DeckList(List.of(1), List.of(2), List.of(1));
        assertEquals(Map.of(1, 2, 2, 1), list.requiredCopies());
        assertEquals(new Deck(List.of(1), List.of(2)), list.toDuelDeck());
    }

    @Test void laterInputMutationCannotChangeTheList() {
        var main = new ArrayList<>(List.of(1));
        var extra = new ArrayList<>(List.of(2));
        var side = new ArrayList<>(List.of(3));
        var list = new DeckList(main, extra, side);
        main.clear();
        extra.clear();
        side.clear();
        assertEquals(List.of(1), list.main());
        assertEquals(List.of(2), list.extra());
        assertEquals(List.of(3), list.side());
    }

    @Test void exposedListsCountsAndEngineSnapshotAreImmutable() {
        var list = new DeckList(List.of(1), List.of(2), List.of(3));
        assertThrows(UnsupportedOperationException.class, () -> list.main().clear());
        assertThrows(UnsupportedOperationException.class, () -> list.extra().clear());
        assertThrows(UnsupportedOperationException.class, () -> list.side().clear());
        assertThrows(UnsupportedOperationException.class, () -> list.requiredCopies().clear());
        assertThrows(UnsupportedOperationException.class, () -> list.toDuelDeck().main().clear());
        assertThrows(UnsupportedOperationException.class, () -> list.toDuelDeck().extra().clear());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void requiresPositivePasscodesInEverySection(int code) {
        assertThrows(IllegalArgumentException.class, () -> new DeckList(List.of(code), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DeckList(List.of(), List.of(code), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DeckList(List.of(), List.of(), List.of(code)));
    }

    @Test void emptyListHasNoRequirements() {
        assertTrue(new DeckList(List.of(), List.of(), List.of()).requiredCopies().isEmpty());
    }
}

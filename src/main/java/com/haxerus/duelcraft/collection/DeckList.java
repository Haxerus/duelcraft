package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.Deck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record DeckList(List<Integer> main, List<Integer> extra, List<Integer> side) {
    public DeckList {
        main = copyCards(main);
        extra = copyCards(extra);
        side = copyCards(side);
    }

    private static List<Integer> copyCards(List<Integer> cards) {
        var copy = List.copyOf(cards);
        if (copy.stream().anyMatch(code -> code <= 0)) {
            throw new IllegalArgumentException("Card passcodes must be positive");
        }
        return copy;
    }

    public Map<Integer, Integer> requiredCopies() {
        var counts = new HashMap<Integer, Integer>();
        for (var section : List.of(main, extra, side)) {
            for (int code : section) counts.merge(code, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public Deck toDuelDeck() {
        return new Deck(List.copyOf(main), List.copyOf(extra));
    }
}

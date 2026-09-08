package com.haxerus.duelcraft.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public record Deck(
    List<Integer> main,
    List<Integer> extra
) {
    /**
     * Returns a copy of this deck with the main list deterministically shuffled.
     * The extra deck is not shuffled — its order does not affect draws.
     */
    public Deck shuffled(long seed) {
        var shuffledMain = new ArrayList<>(main);
        Collections.shuffle(shuffledMain, new Random(seed));
        return new Deck(List.copyOf(shuffledMain), extra);
    }
}

package com.haxerus.duelcraft.core;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static Deck standard() {
        Integer[] MAIN = {
            // Monster
            89631139, 33750025, 28406301, 55415564, 49238328, 39153655, 39153655, 70095154, 11747708, 55144522, 24094653,
            55144522, 25259669, 25259669, 13039848,
            55144522, 31786629, 31786629, 43096270,
            11091375, 11091375, 11091375, 11091375,
            69247929, 69247929, 69247929, 69247929, 28406301, 55415564,
            // Spells
            55144522, 55144522, 55144522,
            12580477, 12580477, 12580477,
            66788016, 66788016, 66788016,
            5318639,  5318639,  5318639,
            83764718, 83764718,
            // Traps
            44095762, 44095762, 44095762,
            62279055, 62279055, 8131171,
        };

        Integer[] EXTRA = {
                7391448,
                29981921,
                54752875,
                50321796,
                21044178,
                11398059,
                29301450,
                29301450,
                9024198
        };

        return new Deck(Arrays.asList(MAIN), Arrays.asList(EXTRA));
    }
}

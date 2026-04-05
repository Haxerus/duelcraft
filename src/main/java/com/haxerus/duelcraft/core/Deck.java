package com.haxerus.duelcraft.core;

import java.util.Arrays;
import java.util.List;

public record Deck(
    List<Integer> main,
    List<Integer> extra
) {
    public static Deck standard() {
        Integer[] MAIN = {
            // Monsters
            39552864, 39552864, 39552864, 39552864,
            13039848, 13039848, 13039848, 13039848,
            43096270, 43096270, 43096270, 43096270,
            11091375, 11091375, 11091375, 11091375,
            69247929, 69247929, 69247929, 69247929,
            // Spells
            55144522, 55144522, 55144522,
            12580477, 12580477, 12580477,
            66788016, 66788016, 66788016,
            5318639,  5318639,  5318639,
            83764718, 83764718,
            // Traps
            44095762, 44095762, 44095762,
            62279055, 62279055, 62279055,
        };
        return new Deck(Arrays.asList(MAIN), List.of());
    }
}

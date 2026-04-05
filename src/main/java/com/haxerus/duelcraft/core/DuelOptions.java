package com.haxerus.duelcraft.core;

import java.util.concurrent.ThreadLocalRandom;

public record DuelOptions(
    long[] seed,
    long flags,
    PlayerOptions team1,
    PlayerOptions team2
) {
    public static DuelOptions standard() {
        long[] seed = ThreadLocalRandom.current().longs(4).toArray();
        return new DuelOptions(seed, OcgConstants.DUEL_MODE_MR5,
                PlayerOptions.standard(), PlayerOptions.standard());
    }
}

package com.haxerus.duelcraft.core;

import java.util.concurrent.ThreadLocalRandom;

public record DuelOptions(
    long[] seed,
    long flags,
    PlayerOptions team1,
    PlayerOptions team2
) {
    /** Builds standard options with a fully expanded seed. */
    public static DuelOptions standard(long seed) {
        return new DuelOptions(
                SeedExpander.toFourLongs(seed),
                OcgConstants.DUEL_MODE_MR5,
                PlayerOptions.standard(),
                PlayerOptions.standard());
    }

    /** Builds standard options with a freshly randomized seed. */
    public static DuelOptions standard() {
        return standard(ThreadLocalRandom.current().nextLong());
    }
}

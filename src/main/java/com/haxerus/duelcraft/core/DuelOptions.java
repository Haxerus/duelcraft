package com.haxerus.duelcraft.core;

import java.util.concurrent.ThreadLocalRandom;

public record DuelOptions(
    long[] seed,
    long flags,
    PlayerOptions team1,
    PlayerOptions team2
) {
    /** Options for {@code rule} with a fully expanded seed and the standard 8000/5/1 per player. */
    public static DuelOptions of(long seed, DuelRule rule) {
        return of(seed, rule, PlayerOptions.standard());
    }

    /** Options for {@code rule} with a fully expanded seed, both players sharing {@code players}. */
    public static DuelOptions of(long seed, DuelRule rule, PlayerOptions players) {
        return new DuelOptions(
                SeedExpander.toFourLongs(seed),
                rule.flags(),
                players,
                players);
    }

    /** Master Rule 5 with the given seed. Kept for existing callers and tests. */
    public static DuelOptions standard(long seed) {
        return of(seed, DuelRule.MR5);
    }

    /** Master Rule 5 with a freshly randomized seed. */
    public static DuelOptions standard() {
        return standard(ThreadLocalRandom.current().nextLong());
    }
}

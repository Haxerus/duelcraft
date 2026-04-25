package com.haxerus.duelcraft.core;

/** Expands a single user seed into 4 longs of state for ygopro-core's xoshiro256** RNG. */
public final class SeedExpander {

    private SeedExpander() {}

    public static long[] toFourLongs(long seed) {
        long[] out = new long[4];
        long x = seed;
        for (int i = 0; i < 4; i++) {
            x += 0x9E3779B97F4A7C15L;
            long z = x;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z = z ^ (z >>> 31);
            out[i] = z;
        }
        return out;
    }
}

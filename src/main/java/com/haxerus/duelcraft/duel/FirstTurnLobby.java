package com.haxerus.duelcraft.duel;

import java.util.Locale;

/**
 * Rock-paper-scissors between the two duellists, edopro's pre-duel roll
 * ({@code generic_duel.cpp:474-513}). The winner picks who goes first.
 */
public final class FirstTurnLobby {

    /** {@link #resolve} result for two equal hands; edopro replays the roll. */
    public static final int TIE = -1;

    private FirstTurnLobby() {}

    /** The three hands, valued as the engine's rock-paper-scissors response codes. */
    public enum Hand {
        ROCK(1), PAPER(2), SCISSORS(3);

        private final int value;

        Hand(int value) {
            this.value = value;
        }

        public int value() { return value; }

        /** generic_duel.cpp:504-511: 1 loses to 2, 2 loses to 3, 3 loses to 1. */
        public boolean beats(Hand other) {
            return other.value == (value == 1 ? 3 : value - 1);
        }
    }

    /** The seat that won, or {@link #TIE}. */
    public static int resolve(Hand hand0, Hand hand1) {
        if (hand0 == hand1) return TIE;
        return hand0.beats(hand1) ? 0 : 1;
    }

    /** The hand named by a command argument, or null when it names none. */
    public static Hand parse(String name) {
        for (Hand hand : Hand.values()) {
            if (hand.name().equals(name.toUpperCase(Locale.ROOT))) return hand;
        }
        return null;
    }
}

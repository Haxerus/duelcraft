package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.FirstTurnLobby.Hand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirstTurnLobbyTest {

    /** generic_duel.cpp:504-511 — 1 loses to 2, 2 loses to 3, 3 loses to 1. */
    @Test
    void cycleIsRockPaperScissors() {
        assertTrue(Hand.PAPER.beats(Hand.ROCK));
        assertTrue(Hand.SCISSORS.beats(Hand.PAPER));
        assertTrue(Hand.ROCK.beats(Hand.SCISSORS));
        assertFalse(Hand.ROCK.beats(Hand.PAPER));
        assertFalse(Hand.PAPER.beats(Hand.SCISSORS));
        assertFalse(Hand.SCISSORS.beats(Hand.ROCK));
    }

    @Test
    void handValuesMatchTheEngineResponseCodes() {
        assertEquals(1, Hand.ROCK.value());
        assertEquals(2, Hand.PAPER.value());
        assertEquals(3, Hand.SCISSORS.value());
    }

    @Test
    void resolveReturnsTheWinningSeat() {
        assertEquals(0, FirstTurnLobby.resolve(Hand.PAPER, Hand.ROCK));
        assertEquals(1, FirstTurnLobby.resolve(Hand.ROCK, Hand.PAPER));
        assertEquals(0, FirstTurnLobby.resolve(Hand.ROCK, Hand.SCISSORS));
        assertEquals(1, FirstTurnLobby.resolve(Hand.PAPER, Hand.SCISSORS));
    }

    @Test
    void matchingHandsAreATie() {
        for (Hand hand : Hand.values()) {
            assertEquals(FirstTurnLobby.TIE, FirstTurnLobby.resolve(hand, hand), hand.toString());
        }
    }

    @Test
    void handsParseFromTheCommandArgument() {
        assertEquals(Hand.ROCK, FirstTurnLobby.parse("rock"));
        assertEquals(Hand.PAPER, FirstTurnLobby.parse("PAPER"));
        assertEquals(Hand.SCISSORS, FirstTurnLobby.parse("Scissors"));
        assertNull(FirstTurnLobby.parse("lizard"));
    }
}

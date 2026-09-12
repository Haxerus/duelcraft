package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The response ownership gate: only the player holding the pending prompt may answer it. */
class DuelEventListenerTest {

    /** Minimal listener that reports a fixed pending player. */
    private static DuelEventListener listenerPending(int pendingPlayer) {
        return new DuelEventListener() {
            @Override public int onMessage(DuelMessage msg) { return 0; }
            @Override public void onDuelEnd() { }
            @Override public int pendingPlayer() { return pendingPlayer; }
        };
    }

    @Test
    void acceptsThePromptedPlayer() {
        assertTrue(listenerPending(1).acceptsResponseFrom(1));
        assertTrue(listenerPending(0).acceptsResponseFrom(0));
    }

    @Test
    void rejectsTheOtherPlayer() {
        assertFalse(listenerPending(1).acceptsResponseFrom(0));
        assertFalse(listenerPending(0).acceptsResponseFrom(1));
    }

    @Test
    void rejectsWhenNoPromptIsPending() {
        assertFalse(listenerPending(-1).acceptsResponseFrom(0));
    }

    @Test
    void rejectsAPlayerWithNoSeatInTheDuel() {
        assertFalse(listenerPending(0).acceptsResponseFrom(-1));
    }
}

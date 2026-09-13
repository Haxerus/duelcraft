package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;

/**
 * Callback interface for duel events. Implemented by the Minecraft integration
 * layer (e.g., ServerDuelHandler) to route messages to players.
 */
public interface DuelEventListener {

    /** {@link #onMessage} result: hand the next message of the batch to the listener. */
    int CONTINUE = 0;
    /** {@link #onMessage} result: stop the batch, a player owes the engine a response. */
    int AWAIT_RESPONSE = 1;
    /** {@link #onMessage} result: stop the batch, the duel is over. */
    int DUEL_ENDED = 2;

    /**
     * Called for each message produced by the duel engine during processing.
     *
     * @param msg the parsed duel message
     * @return {@link #CONTINUE}, {@link #AWAIT_RESPONSE} or {@link #DUEL_ENDED}
     */
    int onMessage(DuelMessage msg);

    /**
     * Called when the duel has ended (either by MSG_WIN or engine status END).
     */
    void onDuelEnd();

    /**
     * Player index of the prompt currently awaiting a response, or -1 when none is pending.
     */
    int pendingPlayer();

    /**
     * True when {@code seat} owns the pending prompt, so that player's response may be applied.
     */
    default boolean acceptsResponseFrom(int seat) {
        return seat >= 0 && seat == pendingPlayer();
    }
}

package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;

/**
 * Callback interface for duel events. Implemented by the Minecraft integration
 * layer (e.g., DuelManager or a per-duel handler) to route messages to players.
 */
public interface DuelEventListener {

    /**
     * Called for each message produced by the duel engine during processing.
     *
     * @param msg the parsed duel message (for flow control and game logic)
     * @param rawData the raw bytes of this message from the engine buffer
     *                (for network forwarding to clients)
     * @return 0 to continue processing the next message,
     *         1 to stop (awaiting player response),
     *         2 to stop (duel ended)
     */
    int onMessage(DuelMessage msg, byte[] rawData);

    /**
     * Called when the duel has ended (either by MSG_WIN or engine status END).
     */
    void onDuelEnd();
}

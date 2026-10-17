package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.Deck;

/** Operations the server owns for a live session. */
public interface ManagedDuelSession extends AutoCloseable {
    void setupDuel(Deck first, Deck second);
    void process();
    void setResponse(byte[] response);
    boolean isEnded();
    DuelEventListener listener();
    @Override void close();
}

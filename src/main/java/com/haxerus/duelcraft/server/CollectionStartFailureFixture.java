package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.duel.*;

/** Opt-in one-shot wrapper around an actual JNI session; never substitutes manager policy. */
public final class CollectionStartFailureFixture {
    public int setups, closes;
    public Runnable afterSetup = () -> {};
    public void install() {
        DuelManager.get().decorateNextSession(factory -> (options, listener) -> {
            var real = factory.create(options, listener);
            return new ManagedDuelSession() {
                public void setupDuel(Deck first, Deck second) {
                    real.setupDuel(first, second);
                    setups++;
                    afterSetup.run();
                    throw new IllegalStateException("M4 injected failure after actual native setup");
                }
                public void process() { real.process(); }
                public void setResponse(byte[] response) { real.setResponse(response); }
                public boolean isEnded() { return real.isEnded(); }
                public DuelEventListener listener() { return real.listener(); }
                public void close() { closes++; real.close(); }
            };
        });
    }
}

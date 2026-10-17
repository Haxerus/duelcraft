package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.ManagedDuelSession;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.concurrent.atomic.AtomicInteger;

/** DEV-only probe for counting real setup calls without replacing the native session. */
public final class DevNativeStartCounter {
    private DevNativeStartCounter() {}

    public static AtomicInteger countNext(DuelManager manager) {
        if (FMLEnvironment.production) throw new IllegalStateException("Development fixture only");
        var setups = new AtomicInteger();
        manager.decorateNextSession(delegate -> (options, listener) -> {
            var session = delegate.create(options, listener);
            return new ManagedDuelSession() {
                @Override public void setupDuel(Deck first, Deck second) {
                    setups.incrementAndGet();
                    session.setupDuel(first, second);
                }
                @Override public void process() { session.process(); }
                @Override public void setResponse(byte[] response) { session.setResponse(response); }
                @Override public boolean isEnded() { return session.isEnded(); }
                @Override public DuelEventListener listener() { return session.listener(); }
                @Override public void close() { session.close(); }
            };
        });
        return setups;
    }
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.uitest.DuelVisibilityTrace;
import java.util.*;

/** DEV-only observer of a real manager session; its factory guard lives in DuelManager. */
public final class DuelVisibilitySessionFixture {
    public final DuelVisibilityTrace trace = new DuelVisibilityTrace();
    public record Answer(DuelMessage prompt, int player, byte[] bytes) {}
    public final List<Answer> answers = new ArrayList<>();
    public DuelSession session;
    public DuelMessage prompt;
    public int player;

    public void install() {
        DuelManager.get().decorateNextSession(factory -> (options, listener) -> {
            var observer = new DuelEventListener() {
                public int onMessage(DuelMessage message) {
                    trace.add(message);
                    if (message instanceof DuelMessage.Retry) throw new AssertionError("Native Retry during visibility scenario");
                    int result = listener.onMessage(message);
                    if (result == AWAIT_RESPONSE) { prompt = message; player = listener.pendingPlayer(); }
                    return result;
                }
                public void onDuelEnd() { listener.onDuelEnd(); }
                public int pendingPlayer() { return listener.pendingPlayer(); }
                public boolean acceptsResponseFrom(int seat) { return listener.acceptsResponseFrom(seat); }
            };
            var real = factory.create(options, observer);
            session = (DuelSession) real;
            return new ManagedDuelSession() {
                public void setupDuel(Deck first, Deck second) { real.setupDuel(first, second); }
                public void process() { real.process(); }
                public void setResponse(byte[] bytes) {
                    answers.add(new Answer(prompt, player, bytes.clone()));
                    prompt = null;
                    real.setResponse(bytes);
                }
                public boolean isEnded() { return real.isEnded(); }
                public DuelEventListener listener() { return real.listener(); }
                public void close() { real.close(); }
            };
        });
    }
    public void save(String name) {
        trace.save(name + "-native");
        try {
            java.nio.file.Files.write(java.nio.file.Path.of("build/evidence/visibility-runtime/" + name + "-answers.txt"),
                    answers.stream().map(a -> a.prompt().getClass().getSimpleName() + " player=" + a.player()
                            + " " + HexFormat.of().formatHex(a.bytes())).toList());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static void clearDecorator() { DuelManager.get().decorateNextSession(factory -> factory); }
}

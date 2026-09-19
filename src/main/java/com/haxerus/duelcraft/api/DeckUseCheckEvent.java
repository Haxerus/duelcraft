package com.haxerus.duelcraft.api;

import com.haxerus.duelcraft.server.collection.DeckUsePolicy;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Synchronous, deny-only companion hook for one immutable deck-use candidate. */
public final class DeckUseCheckEvent extends Event {
    public static final String INVALID_REASON_FALLBACK = DeckUsePolicy.INVALID_REASON_FALLBACK;

    private final DeckUsePolicy.Context context;
    private @Nullable String denialReason;

    public DeckUseCheckEvent(DeckUsePolicy.Context context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public DeckUsePolicy.Context context() {
        return context;
    }

    /** Retains the first denial; later listeners cannot replace or clear it. */
    public void deny(@Nullable String reason) {
        if (denialReason != null) return;
        denialReason = reason == null ? INVALID_REASON_FALLBACK
                : DeckUsePolicy.normalizeRestrictionReason(reason);
    }

    public @Nullable String denialReason() {
        return denialReason;
    }

    public static DeckUsePolicy.Restriction restriction(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        return context -> bus.post(new DeckUseCheckEvent(context)).denialReason();
    }
}

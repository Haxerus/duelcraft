package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.data.CardCatalog;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative legality, ownership, and companion restriction evaluation. */
public final class DeckUsePolicy {
    public static final int MAX_RESTRICTION_REASON_LENGTH = 256;
    public static final String INVALID_REASON_FALLBACK = "An additional deck restriction denied this list.";
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Context(UUID owner, DeckList list, Map<Integer, Long> counts, DuelRule rule) {
        public Context {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(list, "list");
            counts = Map.copyOf(counts);
            Objects.requireNonNull(rule, "rule");
        }
    }

    @FunctionalInterface
    public interface Restriction {
        /** Returns null to add no denial, otherwise a player-facing reason. */
        @Nullable String denial(Context context);
    }

    public static final class EvaluationException extends RuntimeException {
        private EvaluationException(Throwable cause) {
            super("Additional deck restriction evaluation failed", cause);
        }
    }

    private final boolean ownershipRequired;
    private final Restriction restriction;

    public DeckUsePolicy(boolean ownershipRequired, @Nullable Restriction restriction) {
        this.ownershipRequired = ownershipRequired;
        this.restriction = restriction == null ? ignored -> null : restriction;
    }

    public boolean ownershipRequired() {
        return ownershipRequired;
    }

    public DeckEligibility.Report emptyReport() {
        return new DeckEligibility.Report(java.util.List.of(), Map.of(), false, ownershipRequired, null);
    }

    public DeckEligibility.Report check(Context context, Map<Integer, CardCatalog.Facts> facts) {
        var core = DeckEligibility.check(context.list(), context.counts(), facts, context.rule());
        String reason;
        try {
            reason = normalizeRestrictionReason(restriction.denial(context));
        } catch (RuntimeException exception) {
            LOGGER.error("Additional deck restriction failed for player {}", context.owner(), exception);
            throw new EvaluationException(exception);
        }
        return new DeckEligibility.Report(core.problems(), core.missing(), core.moreProblems(),
                ownershipRequired, reason);
    }

    public static @Nullable String normalizeRestrictionReason(@Nullable String reason) {
        if (reason == null) return null;
        if (reason.isBlank()) return INVALID_REASON_FALLBACK;
        return reason.length() <= MAX_RESTRICTION_REASON_LENGTH
                ? reason : reason.substring(0, MAX_RESTRICTION_REASON_LENGTH);
    }
}

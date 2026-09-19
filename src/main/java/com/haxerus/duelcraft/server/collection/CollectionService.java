package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionError;
import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.PlayerCollectionData;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.data.CardCatalog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure operations on one authenticated owner's state; the adapter persists successful changes. */
public final class CollectionService {
    public record Change(PlayerCollectionData data, CollectionError error, DeckEligibility.Report eligibility) {
        public boolean success() {
            return error == CollectionError.NONE;
        }
    }

    private final Map<Integer, CardCatalog.Facts> facts;
    private final DeckUsePolicy policy;

    public CollectionService(Map<Integer, CardCatalog.Facts> facts, DeckUsePolicy policy) {
        this.facts = Map.copyOf(facts);
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    public DeckEligibility.Report emptyReport() {
        return policy.emptyReport();
    }

    public Change save(PlayerCollectionData before, long expectedRevision, boolean busy, UUID owner, SavedDeck deck) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (owner == null || deck == null) return reject(before, CollectionError.INVALID);
        var decks = new HashMap<>(before.decks());
        decks.put(deck.id(), deck);
        var report = policy.emptyReport();
        UUID active = before.activeDeckId();
        if (deck.id().equals(active)) {
            try {
                report = check(owner, deck.cards(), before.counts(), DuelRule.MR5);
            } catch (DeckUsePolicy.EvaluationException exception) {
                return reject(before, CollectionError.DATA_UNAVAILABLE);
            }
            if (!report.eligible()) active = null;
        }
        return changed(before, before.counts(), decks, active, report);
    }

    public Change delete(PlayerCollectionData before, long expectedRevision, boolean busy, UUID id) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (id == null) return reject(before, CollectionError.INVALID);
        if (!before.decks().containsKey(id)) return reject(before, CollectionError.NOT_FOUND);
        var decks = new HashMap<>(before.decks());
        decks.remove(id);
        UUID active = id.equals(before.activeDeckId()) ? null : before.activeDeckId();
        return changed(before, before.counts(), decks, active, policy.emptyReport());
    }

    public Change activate(PlayerCollectionData before, long expectedRevision, boolean busy, UUID owner, UUID id) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (owner == null || id == null) return reject(before, CollectionError.INVALID);
        var deck = before.decks().get(id);
        if (deck == null) return reject(before, CollectionError.NOT_FOUND);
        DeckEligibility.Report report;
        try {
            report = check(owner, deck.cards(), before.counts(), DuelRule.MR5);
        } catch (DeckUsePolicy.EvaluationException exception) {
            return reject(before, CollectionError.DATA_UNAVAILABLE);
        }
        if (!report.eligible()) return new Change(before, CollectionError.INELIGIBLE, report);
        return changed(before, before.counts(), before.decks(), id, report);
    }

    public Change clearActive(PlayerCollectionData before, long expectedRevision, boolean busy) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        return changed(before, before.counts(), before.decks(), null, policy.emptyReport());
    }

    /** Server-internal transfer input, never a client-provided replacement collection. */
    public Change replaceCounts(PlayerCollectionData before, long expectedRevision, boolean busy, UUID owner,
                                Map<Integer, Long> counts) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (owner == null) return reject(before, CollectionError.INVALID);
        final Map<Integer, Long> copy;
        try {
            copy = Map.copyOf(counts);
            if (copy.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
                return reject(before, CollectionError.INVALID);
            }
        } catch (NullPointerException exception) {
            return reject(before, CollectionError.INVALID);
        }

        var report = policy.emptyReport();
        UUID active = before.activeDeckId();
        if (active != null) {
            var deck = before.decks().get(active);
            if (deck == null) active = null;
            else {
                try {
                    report = check(owner, deck.cards(), copy, DuelRule.MR5);
                } catch (DeckUsePolicy.EvaluationException exception) {
                    return reject(before, CollectionError.DATA_UNAVAILABLE);
                }
                if (!report.eligible()) active = null;
            }
        }
        return changed(before, copy, before.decks(), active, report);
    }

    /** Rechecks a disk-loaded active selection under current policy before management or preparation use. */
    public Change revalidateActive(PlayerCollectionData before, UUID owner, DuelRule rule) {
        if (owner == null || rule == null) return reject(before, CollectionError.INVALID);
        UUID active = before.activeDeckId();
        if (active == null) return new Change(before, CollectionError.NONE, policy.emptyReport());
        var deck = before.decks().get(active);
        if (deck == null) return changed(before, before.counts(), before.decks(), null, policy.emptyReport());
        final DeckEligibility.Report report;
        try {
            report = check(owner, deck.cards(), before.counts(), rule);
        } catch (DeckUsePolicy.EvaluationException exception) {
            return reject(before, CollectionError.DATA_UNAVAILABLE);
        }
        if (report.eligible()) return new Change(before, CollectionError.NONE, report);
        return changed(before, before.counts(), before.decks(), null, report);
    }

    /** Shared selected-rule seam for preparation and final startup checks. */
    public DeckEligibility.Report check(UUID owner, DeckList list, Map<Integer, Long> counts, DuelRule rule) {
        return policy.check(new DeckUsePolicy.Context(owner, list, counts, rule), facts);
    }

    private Change changed(PlayerCollectionData before, Map<Integer, Long> counts, Map<UUID, SavedDeck> decks,
                           UUID active, DeckEligibility.Report report) {
        try {
            long revision = Math.incrementExact(before.revision());
            return new Change(new PlayerCollectionData(revision, counts, decks, active), CollectionError.NONE, report);
        } catch (ArithmeticException exception) {
            return reject(before, CollectionError.INVALID);
        }
    }

    private Change gate(PlayerCollectionData before, long expectedRevision, boolean busy) {
        if (busy) return reject(before, CollectionError.BUSY);
        if (expectedRevision != before.revision()) return reject(before, CollectionError.STALE);
        return null;
    }

    private Change reject(PlayerCollectionData before, CollectionError error) {
        return new Change(before, error, policy.emptyReport());
    }
}

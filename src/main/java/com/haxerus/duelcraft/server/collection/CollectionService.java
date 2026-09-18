package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionError;
import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.PlayerCollectionData;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.data.CardCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure operations on one authenticated owner's state; the adapter persists successful changes. */
public final class CollectionService {
    public record Change(PlayerCollectionData data, CollectionError error, DeckEligibility.Report eligibility) {
        public boolean success() {
            return error == CollectionError.NONE;
        }
    }

    private static final DeckEligibility.Report EMPTY = new DeckEligibility.Report(List.of(), Map.of(), false);
    private final Map<Integer, CardCatalog.Facts> facts;

    public CollectionService(Map<Integer, CardCatalog.Facts> facts) {
        this.facts = Map.copyOf(facts);
    }

    public Change save(PlayerCollectionData before, long expectedRevision, boolean busy, SavedDeck deck) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (deck == null) return reject(before, CollectionError.INVALID);
        var decks = new HashMap<>(before.decks());
        decks.put(deck.id(), deck);
        return changed(before, before.counts(), decks, before.activeDeckId());
    }

    public Change delete(PlayerCollectionData before, long expectedRevision, boolean busy, UUID id) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (id == null) return reject(before, CollectionError.INVALID);
        if (!before.decks().containsKey(id)) return reject(before, CollectionError.NOT_FOUND);
        var decks = new HashMap<>(before.decks());
        decks.remove(id);
        return changed(before, before.counts(), decks, before.activeDeckId());
    }

    public Change activate(PlayerCollectionData before, long expectedRevision, boolean busy, UUID id) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        if (id == null) return reject(before, CollectionError.INVALID);
        var deck = before.decks().get(id);
        if (deck == null) return reject(before, CollectionError.NOT_FOUND);
        var report = check(deck, before.counts());
        if (!report.eligible()) return new Change(before, CollectionError.INELIGIBLE, report);
        return changed(before, before.counts(), before.decks(), id);
    }

    public Change clearActive(PlayerCollectionData before, long expectedRevision, boolean busy) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        return changed(before, before.counts(), before.decks(), null);
    }

    /** Server-internal transfer input, never a client-provided replacement collection. */
    public Change replaceCounts(PlayerCollectionData before, long expectedRevision, boolean busy, Map<Integer, Long> counts) {
        var rejection = gate(before, expectedRevision, busy);
        if (rejection != null) return rejection;
        try {
            var copy = Map.copyOf(counts);
            if (copy.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
                return reject(before, CollectionError.INVALID);
            }
            return changed(before, copy, before.decks(), before.activeDeckId());
        } catch (NullPointerException exception) {
            return reject(before, CollectionError.INVALID);
        }
    }

    private Change changed(PlayerCollectionData before, Map<Integer, Long> counts, Map<UUID, SavedDeck> decks, UUID active) {
        try {
            long revision = Math.incrementExact(before.revision());
            var report = EMPTY;
            if (active != null) {
                var deck = decks.get(active);
                if (deck == null) active = null;
                else {
                    report = check(deck, counts);
                    if (!report.eligible()) active = null;
                }
            }
            return new Change(new PlayerCollectionData(revision, counts, decks, active), CollectionError.NONE, report);
        } catch (ArithmeticException exception) {
            return reject(before, CollectionError.INVALID);
        }
    }

    private DeckEligibility.Report check(SavedDeck deck, Map<Integer, Long> counts) {
        return DeckEligibility.check(deck.cards(), counts, facts, DuelRule.MR5);
    }

    private static Change gate(PlayerCollectionData before, long expectedRevision, boolean busy) {
        if (busy) return reject(before, CollectionError.BUSY);
        if (expectedRevision != before.revision()) return reject(before, CollectionError.STALE);
        return null;
    }

    private static Change reject(PlayerCollectionData before, CollectionError error) {
        return new Change(before, error, EMPTY);
    }
}

package com.haxerus.duelcraft.collection;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public sealed interface CollectionReply {
    record Summary(UUID id, String name, int main, int extra, int side) {}
    record Opened(UUID snapshotId, long revision, int countPages, int deckPages,
                  @Nullable UUID activeId, boolean ownershipRequired,
                  @Nullable DeckEligibility.Report clearedActivation) implements CollectionReply {
        public Opened(UUID snapshotId, long revision, int countPages, int deckPages, @Nullable UUID activeId) {
            this(snapshotId, revision, countPages, deckPages, activeId, false, null);
        }
    }
    record Counts(UUID snapshotId, long revision, int index, Map<Integer, Long> entries) implements CollectionReply {
        public Counts { entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); }
    }
    record Decks(UUID snapshotId, long revision, int index, List<Summary> entries) implements CollectionReply {
        public Decks { entries = List.copyOf(entries); }
    }
    record Deck(long revision, SavedDeck deck) implements CollectionReply {}
    record Changed(long revision, @Nullable UUID activeId, @Nullable SavedDeck saved, int transferred,
                   DeckEligibility.Report eligibility) implements CollectionReply {}
    record Rejected(CollectionError error, long revision, DeckEligibility.Report eligibility) implements CollectionReply {}
}

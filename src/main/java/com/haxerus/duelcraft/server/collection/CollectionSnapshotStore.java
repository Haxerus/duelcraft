package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import java.util.*;

/** One immutable private snapshot per owner. Accessed on the server main thread. */
public final class CollectionSnapshotStore {
    private static final DeckEligibility.Report EMPTY = new DeckEligibility.Report(List.of(), Map.of(), false);
    private record Snapshot(UUID id, PlayerCollectionData data, List<Map.Entry<Integer, Long>> counts,
                            List<CollectionReply.Summary> decks, long accessed) {}
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public CollectionReply.Opened open(UUID owner, PlayerCollectionData data, long now) {
        var counts = data.counts().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        var decks = data.decks().values().stream().sorted(Comparator.comparing(SavedDeck::name).thenComparing(SavedDeck::id))
                .map(deck -> new CollectionReply.Summary(deck.id(), deck.name(), deck.cards().main().size(),
                        deck.cards().extra().size(), deck.cards().side().size())).toList();
        var snapshot = new Snapshot(UUID.randomUUID(), data, counts, decks, now);
        snapshots.put(owner, snapshot);
        return new CollectionReply.Opened(snapshot.id(), data.revision(), pages(counts.size(), CollectionLimits.COUNT_PAGE),
                pages(decks.size(), CollectionLimits.SUMMARY_PAGE), data.activeDeckId());
    }

    public CollectionReply page(UUID owner, CollectionCommand.Page request, long now) {
        var snapshot = snapshots.get(owner);
        if (snapshot == null || !snapshot.id().equals(request.snapshotId())) return reject(CollectionError.STALE, snapshot);
        if (now - snapshot.accessed() >= CollectionLimits.SNAPSHOT_IDLE_MS) {
            snapshots.remove(owner);
            return reject(CollectionError.STALE, snapshot);
        }
        int size = request.kind() == CollectionCommand.PageKind.COUNTS ? snapshot.counts().size() : snapshot.decks().size();
        int limit = request.kind() == CollectionCommand.PageKind.COUNTS ? CollectionLimits.COUNT_PAGE : CollectionLimits.SUMMARY_PAGE;
        if (request.kind() == null || request.index() < 0 || request.index() >= pages(size, limit)) return reject(CollectionError.INVALID, snapshot);
        int start = request.index() * limit;
        int end = (int) Math.min((long) start + limit, size);
        snapshots.put(owner, new Snapshot(snapshot.id(), snapshot.data(), snapshot.counts(), snapshot.decks(), now));
        if (request.kind() == CollectionCommand.PageKind.COUNTS) {
            var entries = new LinkedHashMap<Integer, Long>();
            for (var entry : snapshot.counts().subList(start, end)) entries.put(entry.getKey(), entry.getValue());
            return new CollectionReply.Counts(snapshot.id(), snapshot.data().revision(), request.index(), entries);
        }
        return new CollectionReply.Decks(snapshot.id(), snapshot.data().revision(), request.index(), snapshot.decks().subList(start, end));
    }

    public void invalidate(UUID owner) { snapshots.remove(owner); }
    public void clear() { snapshots.clear(); }
    private static int pages(int size, int limit) { return size / limit + (size % limit == 0 ? 0 : 1); }
    private static CollectionReply.Rejected reject(CollectionError error, Snapshot snapshot) {
        return new CollectionReply.Rejected(error, snapshot == null ? 0 : snapshot.data().revision(), EMPTY);
    }
}

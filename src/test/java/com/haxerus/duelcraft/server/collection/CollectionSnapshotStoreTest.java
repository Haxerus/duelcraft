package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSnapshotStoreTest {
    @Test void openCarriesAuthoritativePolicyContextAndOptionalClearance() {
        var store = new CollectionSnapshotStore();
        var owner = UUID.randomUUID();
        var clearance = new DeckEligibility.Report(List.of(), Map.of(123, 2), false, true, null);
        var required = store.open(owner, PlayerCollectionData.empty(), 0, true, clearance);
        assertTrue(required.ownershipRequired());
        assertEquals(clearance, required.clearedActivation());

        var optional = store.open(owner, PlayerCollectionData.empty(), 1, false, null);
        assertFalse(optional.ownershipRequired());
        assertNull(optional.clearedActivation());
    }

    @Test void pagesArePrivateAndInvalidated() {
        var store = new CollectionSnapshotStore();
        var alice = UUID.randomUUID();
        var opened = store.open(alice, new PlayerCollectionData(0, Map.of(1, 999L), Map.of(), null), 0,
                false, null);
        var request = new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 0);
        assertInstanceOf(CollectionReply.Rejected.class, store.page(UUID.randomUUID(), request, 1));
        assertInstanceOf(CollectionReply.Counts.class, store.page(alice, request, 1));
        store.invalidate(alice);
        assertInstanceOf(CollectionReply.Rejected.class, store.page(alice, request, 2));
    }
    @Test void largeCollectionsHaveBoundedSortedPages() {
        var counts = new HashMap<Integer, Long>();
        var decks = new HashMap<UUID, SavedDeck>();
        for (int i = 1; i <= 700; i++) counts.put(i, Long.MAX_VALUE);
        for (int i = 0; i < 70; i++) {
            var deck = new SavedDeck(UUID.randomUUID(), "Deck " + i, new DeckList(List.of(), List.of(), List.of()));
            decks.put(deck.id(), deck);
        }
        var store = new CollectionSnapshotStore(); var owner = UUID.randomUUID();
        var opened = store.open(owner, new PlayerCollectionData(7, counts, decks, null), 0, false, null);
        assertEquals(3, opened.countPages()); assertEquals(3, opened.deckPages());
        for (int i = 0; i < 3; i++) {
            var page = (CollectionReply.Counts) store.page(owner, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, i), i);
            assertTrue(page.entries().size() <= 256); assertEquals(7, page.revision());
            assertEquals(i * 256 + 1, page.entries().keySet().iterator().next());
            var lists = (CollectionReply.Decks) store.page(owner, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.DECKS, i), i);
            assertTrue(lists.entries().size() <= 32);
            assertEquals(lists.entries().stream().map(CollectionReply.Summary::name).sorted().toList(), lists.entries().stream().map(CollectionReply.Summary::name).toList());
        }
    }
    @Test void emptyKindsInvalidIndexesReplacementAndIdleExpiry() {
        var store = new CollectionSnapshotStore(); var owner = UUID.randomUUID();
        var empty = store.open(owner, PlayerCollectionData.empty(), 0, false, null);
        assertEquals(0, empty.countPages()); assertEquals(0, empty.deckPages());
        for (var kind : CollectionCommand.PageKind.values()) assertInstanceOf(CollectionReply.Rejected.class,
                store.page(owner, new CollectionCommand.Page(empty.snapshotId(), kind, 0), 1));
        var opened = store.open(owner, new PlayerCollectionData(1, Map.of(1, 1L), Map.of(), null), 0,
                false, null);
        assertInstanceOf(CollectionReply.Rejected.class, store.page(owner, new CollectionCommand.Page(empty.snapshotId(), CollectionCommand.PageKind.COUNTS, 0), 1));
        assertInstanceOf(CollectionReply.Rejected.class, store.page(owner, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, -1), 1));
        assertInstanceOf(CollectionReply.Rejected.class, store.page(owner, new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 1), 1));
        var page = new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 0);
        assertInstanceOf(CollectionReply.Counts.class, store.page(owner, page, 29999));
        assertInstanceOf(CollectionReply.Counts.class, store.page(owner, page, 59998));
        assertInstanceOf(CollectionReply.Rejected.class, store.page(owner, page, 89998));
        store.open(owner, PlayerCollectionData.empty(), 0, false, null); store.clear();
        assertInstanceOf(CollectionReply.Rejected.class, store.page(owner, page, 1));
    }
}

package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientCollectionStateTest {
    @Test void completeViewCarriesPolicyAndClearanceAndClearDropsBoth() {
        var state = new ClientCollectionState();
        var clearance = new DeckEligibility.Report(List.of(), Map.of(123, 1), false, true, null);
        state.begin(new CollectionReply.Opened(UUID.randomUUID(), 7, 0, 0, null, true, clearance));
        assertTrue(state.view().ownershipRequired());
        assertEquals(clearance, state.view().clearedActivation());

        state.clear();
        assertNull(state.view());
    }

    @Test void olderSnapshotCannotReplaceAuthoritativePolicyContext() {
        var state = new ClientCollectionState();
        state.begin(new CollectionReply.Opened(UUID.randomUUID(), 7, 0, 0, null, true, null));
        assertThrows(IllegalArgumentException.class, () -> state.begin(
                new CollectionReply.Opened(UUID.randomUUID(), 6, 0, 0, null, false, null)));
        assertTrue(state.view().ownershipRequired());
    }

    @Test void emptySnapshotPublishesAnImmutableCompleteView() {
        var state = new ClientCollectionState();
        state.begin(new CollectionReply.Opened(UUID.randomUUID(), 7, 0, 0, null));
        assertEquals(7, state.view().revision());
        assertTrue(state.view().counts().isEmpty());
        assertTrue(state.view().summaries().isEmpty());
        assertNull(state.nextPage());
        assertThrows(UnsupportedOperationException.class, () -> state.view().counts().put(1, 1L));
    }

    @Test void partialRefreshNeverReplacesCompleteView() {
        var state = new ClientCollectionState();
        state.begin(new CollectionReply.Opened(UUID.randomUUID(), 1, 0, 0, null));
        var previous = state.view();
        var id = UUID.randomUUID();
        var deckId = UUID.randomUUID();
        state.begin(new CollectionReply.Opened(id, 2, 1, 1, deckId));
        assertSame(previous, state.view());
        assertEquals(new CollectionCommand.Page(id, CollectionCommand.PageKind.COUNTS, 0), state.nextPage());
        state.accept(new CollectionReply.Counts(id, 2, 0, Map.of(9, 4L)));
        assertSame(previous, state.view());
        state.accept(new CollectionReply.Decks(id, 2, 0, List.of(new CollectionReply.Summary(deckId, "Draft", 1, 0, 0))));
        assertEquals(Map.of(9, 4L), state.view().counts());
        assertEquals(deckId, state.view().activeId());
        assertEquals(2, state.view().revision());
        assertNull(state.nextPage());
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 2, 0, Map.of(10, 1L))));
    }

    @Test void rejectsWrongIdentityRevisionKindAndIndexWithoutPublishing() {
        var state = new ClientCollectionState();
        var id = UUID.randomUUID();
        state.begin(new CollectionReply.Opened(id, 3, 1, 1, null));
        for (var page : List.of(new CollectionReply.Counts(UUID.randomUUID(), 3, 0, Map.of(1, 1L)),
                new CollectionReply.Counts(id, 4, 0, Map.of(1, 1L)),
                new CollectionReply.Counts(id, 3, 1, Map.of(1, 1L)),
                new CollectionReply.Decks(id, 3, 0, List.of()))) {
            assertThrows(IllegalArgumentException.class, () -> state.accept(page));
            assertNull(state.view());
        }
    }

    @Test void rejectsDuplicateKeysAcrossPagesAndOversizedOrShortIntermediatePages() {
        var state = new ClientCollectionState();
        var id = UUID.randomUUID();
        state.begin(new CollectionReply.Opened(id, 1, 2, 0, null));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 1, 0, Map.of(1, 1L))));
        var counts = new LinkedHashMap<Integer, Long>();
        for (int code = 1; code <= 257; code++) counts.put(code, 1L);
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 1, 0, counts)));
        counts.remove(257);
        state.accept(new CollectionReply.Counts(id, 1, 0, counts));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 1, 1, Map.of(1, 1L))));
        assertNull(state.view());
    }

    @Test void rejectsDuplicateSummariesAndUnknownActiveId() {
        var state = new ClientCollectionState();
        var id = UUID.randomUUID();
        var summary = new CollectionReply.Summary(UUID.randomUUID(), "Draft", 0, 0, 0);
        state.begin(new CollectionReply.Opened(id, 1, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Decks(id, 1, 0, List.of(summary, summary))));
        state.begin(new CollectionReply.Opened(id, 1, 0, 1, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Decks(id, 1, 0, List.of(summary))));
        assertNull(state.view());
    }

    @Test void hugeAdvertisedTotalsDoNotAllocateOrPublishAndClearDropsPrivateData() {
        var state = new ClientCollectionState();
        var id = UUID.randomUUID();
        state.begin(new CollectionReply.Opened(id, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, null));
        assertEquals(new CollectionCommand.Page(id, CollectionCommand.PageKind.COUNTS, 0), state.nextPage());
        state.clear();
        assertNull(state.view());
        assertNull(state.nextPage());
        assertThrows(IllegalArgumentException.class, () -> state.begin(new CollectionReply.Opened(id, -1, 0, 0, null)));
    }

    @Test void duplicatesAcrossSummaryPagesAndNonpositiveCountsAreRejected() {
        var state = new ClientCollectionState();
        var id = UUID.randomUUID();
        state.begin(new CollectionReply.Opened(id, 1, 1, 0, null));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 1, 0, Map.of(1, 0L))));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Counts(id, 1, 0, Map.of(0, 1L))));
        state.begin(new CollectionReply.Opened(id, 1, 0, 2, null));
        var summaries = new ArrayList<CollectionReply.Summary>();
        for (int index = 0; index < 32; index++) summaries.add(new CollectionReply.Summary(UUID.randomUUID(), "Draft", 0, 0, 0));
        state.accept(new CollectionReply.Decks(id, 1, 0, summaries));
        assertThrows(IllegalArgumentException.class, () -> state.accept(new CollectionReply.Decks(id, 1, 1, List.of(summaries.getFirst()))));
        assertNull(state.view());
    }
}

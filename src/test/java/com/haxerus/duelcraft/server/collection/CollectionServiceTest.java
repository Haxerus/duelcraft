package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CollectionServiceTest {
    private final CollectionService service = new CollectionService(CollectionTestData.facts());
    private final UUID id = UUID.randomUUID();

    private PlayerCollectionData active() {
        return new PlayerCollectionData(3, CollectionTestData.owned(), Map.of(id, CollectionTestData.deck(id)), id);
    }

    private void rejected(PlayerCollectionData before, CollectionError error, CollectionService.Change change) {
        assertFalse(change.success());
        assertEquals(error, change.error());
        assertSame(before, change.data());
        assertNotNull(change.eligibility());
    }

    @Test void savesUnownedDraftThenActivatesAndInvalidatesAfterWithdrawal() {
        var saved = service.save(PlayerCollectionData.empty(), 0, false, CollectionTestData.deck(id));
        assertTrue(saved.success());
        rejected(saved.data(), CollectionError.INELIGIBLE, service.activate(saved.data(), 1, false, id));
        var stocked = service.replaceCounts(saved.data(), 1, false, CollectionTestData.owned());
        var activated = service.activate(stocked.data(), 2, false, id);
        assertTrue(activated.success());
        assertEquals(id, activated.data().activeDeckId());
        var reduced = new HashMap<>(activated.data().counts());
        reduced.remove(1);
        var withdrawn = service.replaceCounts(activated.data(), 3, false, reduced);
        assertTrue(withdrawn.success());
        assertEquals(4, withdrawn.data().revision());
        assertNull(withdrawn.data().activeDeckId());
        assertEquals(CollectionTestData.deck(id), withdrawn.data().decks().get(id));
        assertEquals(Map.of(1, 1), withdrawn.eligibility().missing());
        rejected(withdrawn.data(), CollectionError.STALE, service.delete(withdrawn.data(), 3, false, id));
        var restored = service.replaceCounts(withdrawn.data(), 4, false, CollectionTestData.owned());
        assertNull(restored.data().activeDeckId());
    }

    @Test void busyPrecedesRevisionAndMalformedInputForEveryOperation() {
        var before = active();
        rejected(before, CollectionError.BUSY, service.save(before, 0, true, null));
        rejected(before, CollectionError.BUSY, service.delete(before, 0, true, null));
        rejected(before, CollectionError.BUSY, service.activate(before, 0, true, null));
        rejected(before, CollectionError.BUSY, service.clearActive(before, 0, true));
        rejected(before, CollectionError.BUSY, service.replaceCounts(before, 0, true, null));
    }

    @Test void stalePrecedesMalformedInputForEveryOperation() {
        var before = active();
        rejected(before, CollectionError.STALE, service.save(before, 0, false, null));
        rejected(before, CollectionError.STALE, service.delete(before, 0, false, null));
        rejected(before, CollectionError.STALE, service.activate(before, 0, false, null));
        rejected(before, CollectionError.STALE, service.clearActive(before, 0, false));
        rejected(before, CollectionError.STALE, service.replaceCounts(before, 0, false, null));
    }

    @Test void rejectsMalformedInputsAndAbsentIdsWithoutMutation() {
        var before = active();
        rejected(before, CollectionError.INVALID, service.save(before, 3, false, null));
        rejected(before, CollectionError.INVALID, service.delete(before, 3, false, null));
        rejected(before, CollectionError.INVALID, service.activate(before, 3, false, null));
        rejected(before, CollectionError.INVALID, service.replaceCounts(before, 3, false, null));
        for (var counts : List.of(Map.of(1, 0L), Map.of(1, -1L), Map.of(0, 1L))) {
            rejected(before, CollectionError.INVALID, service.replaceCounts(before, 3, false, counts));
        }
        rejected(before, CollectionError.NOT_FOUND, service.delete(before, 3, false, UUID.randomUUID()));
        rejected(before, CollectionError.NOT_FOUND, service.activate(before, 3, false, UUID.randomUUID()));
    }

    @Test void renameAndDuplicatePreserveActivationAndDoNotReserveCopies() {
        var before = active();
        var renamed = service.save(before, 3, false, new SavedDeck(id, "Renamed", before.decks().get(id).cards()));
        assertEquals(id, renamed.data().activeDeckId());
        assertEquals("Renamed", renamed.data().decks().get(id).name());
        assertEquals(4, renamed.data().revision());
        var duplicateId = UUID.randomUUID();
        var duplicate = service.save(renamed.data(), 4, false, CollectionTestData.deck(duplicateId));
        assertEquals(2, duplicate.data().decks().size());
        assertEquals(before.counts(), duplicate.data().counts());
        var switched = service.activate(duplicate.data(), 5, false, duplicateId);
        assertTrue(switched.success());
        assertEquals(duplicateId, switched.data().activeDeckId());
        assertEquals(before.counts(), switched.data().counts());
    }

    @Test void saveCanInvalidateSelectionWithoutDiscardingTheDraft() {
        var before = active();
        var draft = new SavedDeck(id, "Draft", new DeckList(java.util.Collections.nCopies(70, 99), List.of(), List.of()));
        var saved = service.save(before, 3, false, draft);
        assertTrue(saved.success());
        assertEquals(4, saved.data().revision());
        assertNull(saved.data().activeDeckId());
        assertEquals(draft, saved.data().decks().get(id));
        assertFalse(saved.eligibility().eligible());
    }

    @Test void sideOnlyShortagePreventsActivation() {
        var deck = CollectionTestData.deck(id);
        var draft = new SavedDeck(id, deck.name(), new DeckList(deck.cards().main(), List.of(), List.of(1)));
        var before = new PlayerCollectionData(0, CollectionTestData.owned(), Map.of(id, draft), null);
        var result = service.activate(before, 0, false, id);
        rejected(before, CollectionError.INELIGIBLE, result);
        assertEquals(Map.of(1, 1), result.eligibility().missing());
    }

    @Test void deletionAndClearOnlyChangeTheirSelectionOrList() {
        var before = active();
        var otherId = UUID.randomUUID();
        var saved = service.save(before, 3, false, CollectionTestData.deck(otherId));
        var otherDeleted = service.delete(saved.data(), 4, false, otherId);
        assertEquals(id, otherDeleted.data().activeDeckId());
        var cleared = service.clearActive(otherDeleted.data(), 5, false);
        assertTrue(cleared.success());
        assertNull(cleared.data().activeDeckId());
        assertEquals(before.decks(), cleared.data().decks());
        var deleted = service.delete(before, 3, false, id);
        assertTrue(deleted.success());
        assertNull(deleted.data().activeDeckId());
        assertTrue(deleted.data().decks().isEmpty());
        assertEquals(4, deleted.data().revision());
    }

    @Test void unknownPositiveOwnedPasscodesRemainStorableAndCountsAreCopied() {
        var counts = new HashMap<>(Map.of(999, Long.MAX_VALUE));
        var result = service.replaceCounts(PlayerCollectionData.empty(), 0, false, counts);
        assertTrue(result.success());
        counts.clear();
        assertEquals(Map.of(999, Long.MAX_VALUE), result.data().counts());
        assertNull(result.data().activeDeckId());
    }

    @Test void revisionOverflowRejectsAllMutationsAtomically() {
        var before = new PlayerCollectionData(Long.MAX_VALUE, active().counts(), active().decks(), id);
        rejected(before, CollectionError.INVALID, service.save(before, Long.MAX_VALUE, false, CollectionTestData.deck(id)));
        rejected(before, CollectionError.INVALID, service.delete(before, Long.MAX_VALUE, false, id));
        rejected(before, CollectionError.INVALID, service.activate(before, Long.MAX_VALUE, false, id));
        rejected(before, CollectionError.INVALID, service.clearActive(before, Long.MAX_VALUE, false));
        rejected(before, CollectionError.INVALID, service.replaceCounts(before, Long.MAX_VALUE, false, Map.of()));
    }

    @Test void constructorCapturesFactsIndependentlyOfCallerChanges() {
        var facts = new HashMap<>(CollectionTestData.facts());
        var captured = new CollectionService(facts);
        facts.clear();
        assertTrue(captured.activate(active(), 3, false, id).success());
    }
}

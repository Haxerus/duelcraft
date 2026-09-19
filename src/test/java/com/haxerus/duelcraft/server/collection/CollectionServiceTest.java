package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CollectionServiceTest {
    private final UUID owner = UUID.randomUUID();
    private final CollectionService service = service(true, null);
    private final UUID id = UUID.randomUUID();

    private static CollectionService service(boolean ownershipRequired, DeckUsePolicy.Restriction restriction) {
        return new CollectionService(CollectionTestData.facts(), new DeckUsePolicy(ownershipRequired, restriction));
    }

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
        var saved = service.save(PlayerCollectionData.empty(), 0, false, owner, CollectionTestData.deck(id));
        assertTrue(saved.success());
        rejected(saved.data(), CollectionError.INELIGIBLE, service.activate(saved.data(), 1, false, owner, id));
        var stocked = service.replaceCounts(saved.data(), 1, false, owner, CollectionTestData.owned());
        var activated = service.activate(stocked.data(), 2, false, owner, id);
        assertTrue(activated.success());
        assertEquals(id, activated.data().activeDeckId());
        var reduced = new HashMap<>(activated.data().counts());
        reduced.remove(1);
        var withdrawn = service.replaceCounts(activated.data(), 3, false, owner, reduced);
        assertTrue(withdrawn.success());
        assertEquals(4, withdrawn.data().revision());
        assertNull(withdrawn.data().activeDeckId());
        assertEquals(CollectionTestData.deck(id), withdrawn.data().decks().get(id));
        assertEquals(Map.of(1, 1), withdrawn.eligibility().missing());
        rejected(withdrawn.data(), CollectionError.STALE, service.delete(withdrawn.data(), 3, false, id));
        var restored = service.replaceCounts(withdrawn.data(), 4, false, owner, CollectionTestData.owned());
        assertNull(restored.data().activeDeckId());
    }

    @Test void busyPrecedesRevisionAndMalformedInputForEveryOperation() {
        var before = active();
        rejected(before, CollectionError.BUSY, service.save(before, 0, true, owner, null));
        rejected(before, CollectionError.BUSY, service.delete(before, 0, true, null));
        rejected(before, CollectionError.BUSY, service.activate(before, 0, true, owner, null));
        rejected(before, CollectionError.BUSY, service.clearActive(before, 0, true));
        rejected(before, CollectionError.BUSY, service.replaceCounts(before, 0, true, owner, null));
    }

    @Test void stalePrecedesMalformedInputForEveryOperation() {
        var before = active();
        rejected(before, CollectionError.STALE, service.save(before, 0, false, owner, null));
        rejected(before, CollectionError.STALE, service.delete(before, 0, false, null));
        rejected(before, CollectionError.STALE, service.activate(before, 0, false, owner, null));
        rejected(before, CollectionError.STALE, service.clearActive(before, 0, false));
        rejected(before, CollectionError.STALE, service.replaceCounts(before, 0, false, owner, null));
    }

    @Test void rejectsMalformedInputsAndAbsentIdsWithoutMutation() {
        var before = active();
        rejected(before, CollectionError.INVALID, service.save(before, 3, false, owner, null));
        rejected(before, CollectionError.INVALID, service.delete(before, 3, false, null));
        rejected(before, CollectionError.INVALID, service.activate(before, 3, false, owner, null));
        rejected(before, CollectionError.INVALID, service.replaceCounts(before, 3, false, owner, null));
        for (var counts : List.of(Map.of(1, 0L), Map.of(1, -1L), Map.of(0, 1L))) {
            rejected(before, CollectionError.INVALID, service.replaceCounts(before, 3, false, owner, counts));
        }
        rejected(before, CollectionError.NOT_FOUND, service.delete(before, 3, false, UUID.randomUUID()));
        rejected(before, CollectionError.NOT_FOUND, service.activate(before, 3, false, owner, UUID.randomUUID()));
    }

    @Test void renameAndDuplicatePreserveActivationAndDoNotReserveCopies() {
        var before = active();
        var renamed = service.save(before, 3, false, owner, new SavedDeck(id, "Renamed", before.decks().get(id).cards()));
        assertEquals(id, renamed.data().activeDeckId());
        assertEquals("Renamed", renamed.data().decks().get(id).name());
        assertEquals(4, renamed.data().revision());
        var duplicateId = UUID.randomUUID();
        var duplicate = service.save(renamed.data(), 4, false, owner, CollectionTestData.deck(duplicateId));
        assertEquals(2, duplicate.data().decks().size());
        assertEquals(before.counts(), duplicate.data().counts());
        var switched = service.activate(duplicate.data(), 5, false, owner, duplicateId);
        assertTrue(switched.success());
        assertEquals(duplicateId, switched.data().activeDeckId());
        assertEquals(before.counts(), switched.data().counts());
    }

    @Test void saveCanInvalidateSelectionWithoutDiscardingTheDraft() {
        var before = active();
        var draft = new SavedDeck(id, "Draft", new DeckList(java.util.Collections.nCopies(70, 99), List.of(), List.of()));
        var saved = service.save(before, 3, false, owner, draft);
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
        var result = service.activate(before, 0, false, owner, id);
        rejected(before, CollectionError.INELIGIBLE, result);
        assertEquals(Map.of(1, 1), result.eligibility().missing());
    }

    @Test void deletionAndClearOnlyChangeTheirSelectionOrList() {
        var before = active();
        var otherId = UUID.randomUUID();
        var saved = service.save(before, 3, false, owner, CollectionTestData.deck(otherId));
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
        var result = service.replaceCounts(PlayerCollectionData.empty(), 0, false, owner, counts);
        assertTrue(result.success());
        counts.clear();
        assertEquals(Map.of(999, Long.MAX_VALUE), result.data().counts());
        assertNull(result.data().activeDeckId());
    }

    @Test void revisionOverflowRejectsAllMutationsAtomically() {
        var before = new PlayerCollectionData(Long.MAX_VALUE, active().counts(), active().decks(), id);
        rejected(before, CollectionError.INVALID, service.save(before, Long.MAX_VALUE, false, owner, CollectionTestData.deck(id)));
        rejected(before, CollectionError.INVALID, service.delete(before, Long.MAX_VALUE, false, id));
        rejected(before, CollectionError.INVALID, service.activate(before, Long.MAX_VALUE, false, owner, id));
        rejected(before, CollectionError.INVALID, service.clearActive(before, Long.MAX_VALUE, false));
        rejected(before, CollectionError.INVALID, service.replaceCounts(before, Long.MAX_VALUE, false, owner, Map.of()));
    }

    @Test void constructorCapturesFactsIndependentlyOfCallerChanges() {
        var facts = new HashMap<>(CollectionTestData.facts());
        var captured = new CollectionService(facts, new DeckUsePolicy(true, null));
        facts.clear();
        assertTrue(captured.activate(active(), 3, false, owner, id).success());
    }

    @Test void optionalOwnershipAllowsActivationAndCountReductionWithoutConsumingCopies() {
        var optional = service(false, null);
        var saved = optional.save(PlayerCollectionData.empty(), 0, false, owner, CollectionTestData.deck(id));
        var activated = optional.activate(saved.data(), 1, false, owner, id);
        assertTrue(activated.success());
        assertEquals(id, activated.data().activeDeckId());
        assertTrue(activated.data().counts().isEmpty());
        assertEquals(40, activated.eligibility().missing().size());
        assertFalse(activated.eligibility().ownershipRequired());

        var reduced = optional.replaceCounts(new PlayerCollectionData(2, CollectionTestData.owned(),
                activated.data().decks(), id), 2, false, owner, Map.of());
        assertTrue(reduced.success());
        assertEquals(id, reduced.data().activeDeckId());
        assertTrue(reduced.data().counts().isEmpty());
        assertTrue(reduced.eligibility().eligible());
        assertEquals(40, reduced.eligibility().missing().size());
    }

    @Test void activeRevalidationUsesExactProposedCountsAndAuthenticatedOwner() {
        var observed = new ArrayList<DeckUsePolicy.Context>();
        var restricted = service(false, context -> {
            observed.add(context);
            return context.counts().isEmpty() ? "Empty collections cannot stay active" : null;
        });
        var result = restricted.replaceCounts(active(), 3, false, owner, Map.of());
        assertTrue(result.success());
        assertNull(result.data().activeDeckId());
        assertTrue(result.data().counts().isEmpty());
        assertEquals("Empty collections cannot stay active", result.eligibility().restrictionReason());
        assertEquals(1, observed.size());
        assertEquals(owner, observed.getFirst().owner());
        assertEquals(CollectionTestData.deck(id).cards(), observed.getFirst().list());
        assertTrue(observed.getFirst().counts().isEmpty());
    }

    @Test void restrictionFailureRejectsMutationAndInactiveDraftSaveDoesNotCallRestriction() {
        int[] calls = {0};
        var failing = service(false, context -> {
            calls[0]++;
            throw new IllegalStateException("broken companion");
        });
        var inactive = failing.save(PlayerCollectionData.empty(), 0, false, owner, CollectionTestData.deck(id));
        assertTrue(inactive.success());
        assertEquals(0, calls[0]);

        var before = active();
        var result = failing.replaceCounts(before, 3, false, owner, Map.of());
        rejected(before, CollectionError.DATA_UNAVAILABLE, result);
        assertEquals(1, calls[0]);
        assertEquals(CollectionTestData.owned(), result.data().counts());
        assertEquals(id, result.data().activeDeckId());
    }

    @Test void gatesAndMalformedInputsRunBeforeRestrictionCallbacks() {
        int[] calls = {0};
        var restricted = service(false, context -> {
            calls[0]++;
            return null;
        });
        var before = active();
        assertEquals(CollectionError.BUSY,
                restricted.save(before, 0, true, owner, null).error());
        assertEquals(CollectionError.STALE,
                restricted.activate(before, 0, false, owner, null).error());
        assertEquals(CollectionError.INVALID,
                restricted.replaceCounts(before, 3, false, owner, null).error());
        assertEquals(0, calls[0]);
    }

    @Test void activeSaveEvaluatesTheProposedListBeforeCommitting() {
        var replacement = new SavedDeck(id, "Replacement",
                new DeckList(Collections.nCopies(40, 2), List.of(), List.of()));
        var restricted = service(false, context -> context.list().equals(replacement.cards()) ? "Era locked" : null);
        var result = restricted.save(active(), 3, false, owner, replacement);
        assertTrue(result.success());
        assertEquals(replacement, result.data().decks().get(id));
        assertNull(result.data().activeDeckId());
        assertEquals("Era locked", result.eligibility().restrictionReason());
    }

    @Test void persistedActiveRevalidationClearsOnlySelectionAndIncrementsOnce() {
        var unownedActive = new PlayerCollectionData(8, Map.of(), Map.of(id, CollectionTestData.deck(id)), id);
        var result = service(true, null).revalidateActive(unownedActive, owner, com.haxerus.duelcraft.core.DuelRule.MR5);
        assertTrue(result.success());
        assertEquals(9, result.data().revision());
        assertNull(result.data().activeDeckId());
        assertEquals(unownedActive.counts(), result.data().counts());
        assertEquals(unownedActive.decks(), result.data().decks());
        assertEquals(40, result.eligibility().missing().size());
    }
}

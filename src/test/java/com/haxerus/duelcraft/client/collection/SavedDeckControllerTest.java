package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SavedDeckControllerTest {
    @Test void ownedWrongPlacementActivationRetainsActionableReportWithoutShortages() {
        var c = productionController();
        var main = new ArrayList<>(java.util.stream.IntStream.rangeClosed(10001, 10040).boxed().toList());
        main.add(43227);
        var owned = new HashMap<Integer, Long>();
        main.forEach(code -> owned.put(code, 1L));
        model.load(new DeckList(main, List.of(), List.of()));
        c.applyView(new ClientCollectionState.View(4, owned, List.of(), null));
        c.activate();
        replies.getFirst().complete(new CollectionReply.Rejected(CollectionError.INELIGIBLE, 4,
                new DeckEligibility.Report(List.of(new DeckEligibility.Issue(
                        "duelcraft.collection.issue.main_placement", 43227, 65, 0)), Map.of(), false, true, null)));
        assertEquals("operation_failed", c.status());
        assertFalse(c.dirty());
        assertTrue(c.eligibility().missing().isEmpty());
        assertEquals(List.of(new DeckEligibility.Issue("duelcraft.collection.issue.main_placement", 43227, 65, 0)),
                c.eligibility().problems());
    }

    @Test void truncatedActivationReportRetainsOmissionFlagAndExactShortages() {
        var c = productionController();
        c.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), null));
        c.activate();
        replies.getFirst().complete(new CollectionReply.Rejected(CollectionError.INELIGIBLE, 4,
                new DeckEligibility.Report(List.of(new DeckEligibility.Issue(
                        "duelcraft.collection.issue.copies", 123, 4, 3)), Map.of(123, 2), true, true, null)));
        assertTrue(c.eligibility().moreProblems());
        assertEquals(Map.of(123, 2), c.eligibility().missing());
        assertEquals(4, c.eligibility().problems().getFirst().actual());
        assertEquals(3, c.eligibility().problems().getFirst().limit());
    }

    @Test void explicitClearAndDeleteClearPriorEligibilityFeedbackAsOrdinarySuccesses() {
        for (boolean delete : List.of(false, true)) {
            commands.clear();
            replies.clear();
            var c = productionController();
            c.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), id));
            c.activate();
            replies.getFirst().complete(new CollectionReply.Rejected(CollectionError.INELIGIBLE, 4,
                    new DeckEligibility.Report(List.of(new DeckEligibility.Issue(
                            "duelcraft.collection.issue.unknown", 123, 0, 0)), Map.of(), true, true, null)));
            if (delete) c.delete();
            else c.clearActive();
            replies.getLast().complete(new CollectionReply.Changed(5, null, null, 0,
                    new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
            assertEquals("updated", c.status());
            assertNull(c.activeId());
            assertTrue(c.eligibility().eligible());
        }
    }

    @Test void invalidatingSaveAcknowledgesSubmittedBaselineAndExplainsClearedActivation() {
        var c = productionController();
        c.applyView(new ClientCollectionState.View(4, Map.of(43227, 1L), List.of(), id));
        model.add(DeckEditorModel.Section.MAIN, 43227);
        c.save();
        var submitted = ((CollectionCommand.Save) commands.getFirst()).deck();
        replies.getFirst().complete(new CollectionReply.Changed(5, null, submitted, 0,
                new DeckEligibility.Report(List.of(new DeckEligibility.Issue(
                        "duelcraft.collection.issue.main_placement", 43227, 65, 0)), Map.of(), false, true, null)));
        assertFalse(c.pending());
        assertFalse(c.dirty());
        assertEquals(submitted.cards(), model.draft());
        assertNull(c.activeId());
        assertEquals("saved_active_cleared", c.status());
        assertEquals(43227, c.eligibility().problems().getFirst().code());
        model.add(DeckEditorModel.Section.SIDE, 123);
        model.discard();
        assertEquals(submitted.cards(), model.draft(), "invalidating Save still advances the acknowledged baseline");
    }

    private SavedDeckController productionController() {
        return new SavedDeckController(model, id, "List", null,
                command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
                CompletableFuture<ClientCollectionState.View>::new, Runnable::run, () -> {});
    }

    @Test void clearActiveAcknowledgementKeepsDirtyCloseBehindSaveDiscardCancel() {
        controller.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), id));
        model.add(DeckEditorModel.Section.MAIN, 123);
        controller.rename("Edited name");
        var dirtyDraft = model.draft();
        int[] closed = {0};
        controller.clearActive();
        assertEquals(new CollectionCommand.ClearActive(4), commands.getFirst());
        controller.navigate(() -> closed[0]++);
        assertTrue(controller.pending());
        assertFalse(controller.needsDecision());
        replies.getFirst().complete(new CollectionReply.Changed(5, null, null, 0,
                new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertEquals(0, closed[0], "Clear Active never acknowledges the dirty card/name edits");
        assertTrue(controller.needsDecision());
        assertTrue(controller.dirty());
        assertEquals(dirtyDraft, model.draft());
        assertEquals("Edited name", controller.name());
        controller.cancelNavigation();
        assertFalse(controller.needsDecision());
        assertEquals(0, closed[0]);
        controller.navigate(() -> closed[0]++);
        controller.discardNavigation();
        assertEquals(1, closed[0]);
    }
    private static final DeckList EMPTY = new DeckList(List.of(), List.of(), List.of());
    private final UUID id = UUID.randomUUID();
    private final DeckEditorModel model = new DeckEditorModel(EMPTY, Map.of());
    private final CompletableFuture<SavedDeck> save = new CompletableFuture<>();
    private final List<CollectionCommand> commands = new ArrayList<>();
    private final List<CompletableFuture<CollectionReply>> replies = new ArrayList<>();
    private final SavedDeckController controller = new SavedDeckController(model, id, "List", (name, cards) -> save,
            command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
            () -> CompletableFuture.completedFuture(new ClientCollectionState.View(0, Map.of(), List.of(), null)), Runnable::run, () -> {});

    @Test void nameOnlyEditsAreDirtyAndSaveAcknowledgementDefersNavigation() {
        controller.rename("Renamed");
        assertTrue(controller.dirty());
        int[] navigated = {0};
        controller.navigate(() -> navigated[0]++);
        assertTrue(controller.needsDecision());
        controller.save();
        assertTrue(controller.pending());
        assertThrows(IllegalStateException.class, () -> controller.rename("Late"));
        controller.save();
        assertEquals(0, navigated[0]);
        save.complete(new SavedDeck(id, "Renamed", EMPTY));
        assertEquals(1, navigated[0]);
        assertFalse(controller.dirty());
    }

    @Test void failureRetainsDraftAndNeverNavigates() {
        model.add(DeckEditorModel.Section.MAIN, 123);
        int[] navigated = {0};
        controller.navigate(() -> navigated[0]++);
        controller.save();
        save.completeExceptionally(new TimeoutException("timed out"));
        assertEquals(0, navigated[0]);
        assertTrue(controller.dirty());
        assertFalse(controller.pending());
        assertTrue(controller.detail().contains("timed out"));
    }

    @Test void disposalIgnoresLateSuccessWithoutClearingEditsOrNavigating() {
        controller.rename("Changed");
        controller.navigate(() -> fail("disposed navigation"));
        controller.save();
        controller.dispose();
        save.complete(new SavedDeck(id, "Changed", EMPTY));
        assertTrue(controller.dirty());
    }

    @Test void pendingSaveFreezesModelAndOwnershipReplacementPreservesDirtyDraft() {
        model.add(DeckEditorModel.Section.SIDE, 123);
        controller.save();
        assertThrows(IllegalStateException.class, () -> model.add(DeckEditorModel.Section.MAIN, 123));
        controller.applyView(new ClientCollectionState.View(2, Map.of(123, 9L), List.of(), null));
        assertEquals(List.of(123), model.draft().side());
        assertEquals(0, model.missing(123));
        assertTrue(controller.dirty());
        save.completeExceptionally(new CancellationException("disconnected"));
        assertTrue(model.dirty());
        model.add(DeckEditorModel.Section.MAIN, 123);
    }

    @Test void newerSelectionRejectsOldReadAndWrongRevisionPreservesDraft() {
        var other = UUID.randomUUID();
        controller.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), null));
        controller.select(id);
        controller.select(other);
        replies.get(0).complete(new CollectionReply.Deck(4, new SavedDeck(id, "Old", EMPTY)));
        assertEquals("List", controller.name());
        replies.get(1).complete(new CollectionReply.Deck(3, new SavedDeck(other, "Stale", EMPTY)));
        assertEquals("List", controller.name());
    }

    @Test void duplicateGetsOneNewIdentityAndActivationRequiresSaveFirst() {
        controller.duplicate();
        var duplicate = controller.id();
        assertNotEquals(id, duplicate);
        assertTrue(controller.dirty());
        controller.activate();
        assertTrue(commands.isEmpty());
        assertEquals(duplicate, controller.id());
        assertEquals("save_first", controller.status());
    }

    @Test void productionSaveUsesChangedRevisionAndRefreshFailureDoesNotUndoAcknowledgement() {
        var refresh = new CompletableFuture<ClientCollectionState.View>();
        var c = new SavedDeckController(model, id, "List", null,
                command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
                () -> refresh, Runnable::run, () -> {});
        c.applyView(new ClientCollectionState.View(5, Map.of(), List.of(), null));
        c.rename("Renamed");
        c.save();
        var submitted = ((CollectionCommand.Save) commands.getFirst()).deck();
        replies.getFirst().complete(new CollectionReply.Changed(6, null, submitted, 0, new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertFalse(c.dirty());
        assertEquals(6, c.revision());
        refresh.completeExceptionally(new IllegalStateException("refresh failed"));
        assertFalse(c.dirty());
        assertEquals("refresh_failed", c.status());
    }

    @Test void acknowledgementWaitsForClientDispatchAndRejectsDifferentIdentity() {
        var queue = new ArrayDeque<Runnable>();
        var c = new SavedDeckController(model, id, "List", (name, cards) -> save,
                controllerCommand -> CompletableFuture.failedFuture(new IllegalStateException()),
                () -> CompletableFuture.failedFuture(new IllegalStateException()), queue::add, () -> {});
        c.rename("Changed");
        c.save();
        save.complete(new SavedDeck(UUID.randomUUID(), "Changed", EMPTY));
        assertTrue(c.pending());
        assertTrue(c.dirty());
        queue.remove().run();
        assertFalse(c.pending());
        assertTrue(c.dirty());
        assertEquals(id, c.id());
    }

    @Test void failedDeferredSaveCanBeRetriedAndStillNavigateOnlyOnSuccess() {
        var attempts = new ArrayList<CompletableFuture<SavedDeck>>();
        var c = new SavedDeckController(model, id, "List", (name, cards) -> {
            var result = new CompletableFuture<SavedDeck>(); attempts.add(result); return result;
        }, command -> CompletableFuture.failedFuture(new IllegalStateException()),
                () -> CompletableFuture.failedFuture(new IllegalStateException()), Runnable::run, () -> {});
        c.rename("Changed");
        int[] navigated = {0};
        c.navigate(() -> navigated[0]++);
        c.save();
        attempts.getFirst().completeExceptionally(new IllegalStateException("failure"));
        assertEquals(0, navigated[0]);
        c.save();
        attempts.getLast().complete(new SavedDeck(id, "Changed", EMPTY));
        assertEquals(1, navigated[0]);
    }

    @Test void selectionAppliesOnlyMatchingCurrentRevisionAndOwnershipRefreshKeepsUuidAndNameEdits() {
        var target = UUID.randomUUID();
        controller.applyView(new ClientCollectionState.View(4, Map.of(123, 1L), List.of(), null));
        controller.select(target);
        var cards = new DeckList(List.of(123), List.of(), List.of());
        replies.getFirst().complete(new CollectionReply.Deck(4, new SavedDeck(target, "Selected", cards)));
        assertEquals(target, controller.id());
        assertEquals(cards, model.draft());
        controller.rename("Edited name");
        controller.applyView(new ClientCollectionState.View(5, Map.of(123, 2L), List.of(), target));
        assertEquals(target, controller.id());
        assertEquals("Edited name", controller.name());
        assertTrue(controller.dirty());
        assertEquals(target, controller.activeId());
    }

    @Test void supersededRefreshDoesNotReplaceOperationStatusOrDraft() {
        var refresh = new CompletableFuture<ClientCollectionState.View>();
        var c = new SavedDeckController(model, id, "List", null,
                command -> CompletableFuture.failedFuture(new IllegalStateException()),
                () -> refresh, Runnable::run, () -> {});
        c.rename("Changed");
        c.refresh();
        refresh.completeExceptionally(new CancellationException("Collection refresh superseded"));
        assertEquals("", c.status());
        assertEquals("Changed", c.name());
        assertTrue(c.dirty());
    }

    @Test void invalidNameNeverSendsSaveAndKeepsDeferredNavigation() {
        controller.rename(" ");
        controller.navigate(() -> fail("invalid navigation"));
        controller.save();
        assertFalse(controller.pending());
        assertTrue(controller.needsDecision());
        assertTrue(controller.dirty());
        assertTrue(commands.isEmpty());
    }

    @Test void editingWhileAReadIsPendingKeepsTheLocalDraftInsteadOfApplyingThatRead() {
        var target = UUID.randomUUID();
        controller.select(target);
        model.add(DeckEditorModel.Section.MAIN, 123);
        controller.rename("Local edits");
        replies.getFirst().complete(new CollectionReply.Deck(0, new SavedDeck(target, "Other", EMPTY)));
        assertEquals(id, controller.id());
        assertEquals("Local edits", controller.name());
        assertEquals(List.of(123), model.draft().main());
        assertTrue(controller.dirty());
    }

    @Test void saveThenSwitchWaitsForFreshCompleteViewBeforeReadDeck() {
        var refresh = new CompletableFuture<ClientCollectionState.View>();
        var target = UUID.randomUUID();
        var c = new SavedDeckController(model, id, "List", null,
                command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
                () -> refresh, Runnable::run, () -> {});
        c.applyView(new ClientCollectionState.View(5, Map.of(), List.of(), null));
        c.rename("Renamed");
        c.navigate(() -> c.select(target));
        c.save();
        var submitted = ((CollectionCommand.Save) commands.getFirst()).deck();
        replies.getFirst().complete(new CollectionReply.Changed(6, null, submitted, 0, new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertEquals(1, commands.size(), "ReadDeck must wait until CollectionClient has a revision-6 complete view");
        refresh.complete(new ClientCollectionState.View(6, Map.of(), List.of(), null));
        assertEquals(new CollectionCommand.ReadDeck(target), commands.getLast());
        replies.getLast().complete(new CollectionReply.Deck(6, new SavedDeck(target, "Target", EMPTY)));
        assertEquals(target, c.id());
        assertFalse(c.dirty());
    }

    @Test void productionLateSaveAfterDisposalDoesNotAdvanceLocalRevision() {
        var c = new SavedDeckController(model, id, "List", null,
                command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
                () -> CompletableFuture.failedFuture(new IllegalStateException()), Runnable::run, () -> {});
        c.applyView(new ClientCollectionState.View(5, Map.of(), List.of(), null));
        c.rename("Changed");
        c.save();
        var submitted = ((CollectionCommand.Save) commands.getFirst()).deck();
        c.dispose();
        replies.getFirst().complete(new CollectionReply.Changed(6, null, submitted, 0, new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertEquals(5, c.revision());
        assertTrue(c.dirty());
    }

    @Test void newerOwnershipSnapshotDuringSaveKeepsItsRevisionAndActiveSelection() {
        var active = UUID.randomUUID();
        var c = new SavedDeckController(model, id, "List", null,
                command -> { commands.add(command); var reply = new CompletableFuture<CollectionReply>(); replies.add(reply); return reply; },
                () -> CompletableFuture.completedFuture(new ClientCollectionState.View(7, Map.of(123, 9L), List.of(), active)),
                Runnable::run, () -> {});
        c.applyView(new ClientCollectionState.View(5, Map.of(), List.of(), null));
        model.add(DeckEditorModel.Section.MAIN, 123);
        c.save();
        var submitted = ((CollectionCommand.Save) commands.getFirst()).deck();
        c.applyView(new ClientCollectionState.View(7, Map.of(123, 9L), List.of(), active));
        int[] navigationRevision = {0};
        c.navigate(() -> navigationRevision[0] = (int) c.revision());
        replies.getFirst().complete(new CollectionReply.Changed(6, null, submitted, 0, new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertEquals(7, navigationRevision[0]);
        assertEquals(active, c.activeId());
        assertEquals(Map.of(123, 9L), model.owned());
        assertFalse(c.dirty());
        assertEquals(submitted.cards(), model.draft());
    }
}

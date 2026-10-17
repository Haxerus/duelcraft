package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CardTransferControllerTest {
    @Test void ownedUnknownPasscodesRemainDiscoverableWithoutASavedList() {
        var cards = CardSearch.search(List.of(), "2147483647", CardSearch.Filters.ALL,
                Map.of(Integer.MAX_VALUE, 5L), new DeckList(List.of(), List.of(), List.of()));
        assertEquals(1, cards.size()); assertEquals(Integer.MAX_VALUE, cards.getFirst().code());
        assertEquals(0, cards.getFirst().type());
        assertTrue(CardSearch.search(List.of(), "7", CardSearch.Filters.ALL, Map.of(),
                new DeckList(List.of(), List.of(), List.of())).isEmpty());
    }
    final DeckEditorModel model = new DeckEditorModel(new DeckList(List.of(7), List.of(), List.of()), Map.of(7, 20L));
    final CompletableFuture<CollectionReply> reply = new CompletableFuture<>();
    final CompletableFuture<ClientCollectionState.View> snapshot = new CompletableFuture<>();
    final List<CollectionCommand> requests = new ArrayList<>();
    final SavedDeckController lists = new SavedDeckController(model, UUID.randomUUID(), "Draft", null,
            command -> { requests.add(command); return reply; }, () -> snapshot, Runnable::run, () -> {});

    @Test void transferWaitsForAcknowledgedSnapshotAndPreservesDraft() {
        lists.applyView(new ClientCollectionState.View(4, Map.of(7, 20L), List.of(), null));
        model.add(DeckEditorModel.Section.SIDE, 7);
        var draft = model.draft();
        var form = new CardTransferController(lists);
        form.amount("5"); form.withdraw(7);
        assertEquals(List.of(new CollectionCommand.Withdraw(4, 7, 5)), requests);
        assertTrue(lists.pending()); assertEquals(20L, model.owned().get(7));
        form.withdraw(7); assertEquals(1, requests.size());
        reply.complete(new CollectionReply.Changed(5, null, null, 5, 0,
                new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        assertTrue(lists.pending()); assertEquals(20L, model.owned().get(7));
        snapshot.complete(new ClientCollectionState.View(5, Map.of(7, 15L), List.of(), null));
        assertFalse(lists.pending()); assertEquals(15L, model.owned().get(7));
        assertEquals(draft, model.draft()); assertTrue(model.dirty());
    }

    @Test void rejectionRetainsCountsAndDraftAndNeverRetries() {
        lists.applyView(new ClientCollectionState.View(4, Map.of(7, 20L), List.of(), null));
        var form = new CardTransferController(lists);
        form.deposit(7);
        reply.complete(new CollectionReply.Rejected(CollectionError.INSUFFICIENT_CARDS, 4,
                new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        snapshot.complete(new ClientCollectionState.View(4, Map.of(7, 20L), List.of(), null));
        assertEquals(Map.of(7, 20L), model.owned());
        assertEquals(List.of(7), model.draft().main()); assertEquals(1, requests.size());
        assertEquals("operation_failed", lists.status());
    }

    @Test void invalidAmountNeverSendsRequest() {
        lists.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), null));
        var form = new CardTransferController(lists);
        for (String value : List.of("", "0", "-1", "4097", "1.5", "abc")) {
            form.amount(value); assertFalse(form.valid()); form.withdraw(7);
        }
        assertTrue(requests.isEmpty());
        form.amount("4096"); assertTrue(form.valid());
    }

    @Test void busyReplyLocksFurtherRequestsUntilAnAuthoritativeOpenSucceeds() {
        lists.applyView(new ClientCollectionState.View(4, Map.of(7, 20L), List.of(), null));
        var form = new CardTransferController(lists);
        form.withdraw(7);
        reply.complete(new CollectionReply.Rejected(CollectionError.BUSY, 4,
                new DeckEligibility.Report(List.of(), Map.of(), false, false, null)));
        snapshot.completeExceptionally(new IllegalStateException("Still busy"));
        assertFalse(lists.ready()); assertFalse(lists.pending());
        form.withdraw(7); assertEquals(1, requests.size());
        lists.applyView(new ClientCollectionState.View(4, Map.of(7, 20L), List.of(), null));
        assertTrue(lists.ready());
    }
}

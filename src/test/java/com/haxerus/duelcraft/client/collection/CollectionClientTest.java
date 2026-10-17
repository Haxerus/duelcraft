package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.client.ClientPayloadHandler;
import com.haxerus.duelcraft.server.collection.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionClientTest {
    private static final DeckEligibility.Report EMPTY = new DeckEligibility.Report(List.of(), Map.of(), false, false, null);
    private static final SavedDeck DECK = new SavedDeck(UUID.randomUUID(), "Draft", new DeckList(List.of(), List.of(), List.of()));
    private static class Harness {
        final List<CollectionRequestPayload> sent = new ArrayList<>();
        final List<Runnable> timers = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final CollectionClient client = new CollectionClient(sent::add, (task, delay) -> {
            timers.add(task); delays.add(delay); return () -> {};
        }, Runnable::run);
        Harness() { client.connect(); }
        void reply(int index, CollectionReply reply) { client.receive(new CollectionReplyPayload(sent.get(index).requestId(), reply)); }
    }

    @Test void refreshPullsPagesSequentiallyAndCompletesOnlyAfterPublication() {
        var h = new Harness();
        var result = h.client.refresh().toCompletableFuture();
        var id = UUID.randomUUID();
        assertEquals(new CollectionCommand.Open(), h.sent.getFirst().command());
        h.reply(0, new CollectionReply.Opened(id, 3, 1, 1, null));
        assertEquals(2, h.sent.size());
        assertEquals(new CollectionCommand.Page(id, CollectionCommand.PageKind.COUNTS, 0), h.sent.get(1).command());
        assertFalse(result.isDone());
        h.reply(1, new CollectionReply.Counts(id, 3, 0, Map.of(7, 2L)));
        assertEquals(new CollectionCommand.Page(id, CollectionCommand.PageKind.DECKS, 0), h.sent.get(2).command());
        h.reply(2, new CollectionReply.Decks(id, 3, 0, List.of()));
        // Declaring a nonempty page then returning empty is a malformed snapshot.
        assertTrue(result.isCompletedExceptionally());
        assertNull(h.client.state().view());
    }

    @Test void completeRefreshAndEmptyRefreshPublishTheirViews() {
        var h = new Harness();
        var result = h.client.refresh().toCompletableFuture();
        var id = UUID.randomUUID();
        h.reply(0, new CollectionReply.Opened(id, 3, 1, 1, DECK.id()));
        h.reply(1, new CollectionReply.Counts(id, 3, 0, Map.of(7, 2L)));
        h.reply(2, new CollectionReply.Decks(id, 3, 0, List.of(new CollectionReply.Summary(DECK.id(), "Draft", 0, 0, 0))));
        assertEquals(Map.of(7, 2L), result.join().counts());
        assertEquals(DECK.id(), result.join().activeId());
        var empty = h.client.refresh().toCompletableFuture();
        h.reply(3, new CollectionReply.Opened(UUID.randomUUID(), 4, 0, 0, null));
        assertEquals(4, empty.join().revision());
    }

    @Test void wrongRequestAndPreviousConnectionRepliesCannotCompleteCurrentSave() {
        var h = new Harness();
        var old = h.client.request(new CollectionCommand.Save(0, DECK)).toCompletableFuture();
        h.client.disconnect(); h.client.connect();
        var current = h.client.request(new CollectionCommand.Save(0, DECK)).toCompletableFuture();
        var changed = new CollectionReply.Changed(1, null, DECK, 0, EMPTY);
        h.reply(0, changed);
        h.client.receive(new CollectionReplyPayload(UUID.randomUUID(), changed));
        assertTrue(old.isCompletedExceptionally());
        assertFalse(current.isDone());
        h.reply(1, changed);
        assertEquals(changed, current.join());
    }

    @Test void disconnectClearsPolicyAndPreviousConnectionOpenCannotReplaceCurrentMode() {
        var h = new Harness();
        var old = h.client.refresh().toCompletableFuture();
        h.client.disconnect();
        assertNull(h.client.state().view());
        h.client.connect();
        var current = h.client.refresh().toCompletableFuture();
        h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 1, 0, 0, null, true, null));
        assertTrue(old.isCompletedExceptionally());
        assertFalse(current.isDone());
        assertNull(h.client.state().view());
        h.reply(1, new CollectionReply.Opened(UUID.randomUUID(), 1, 0, 0, null, false, null));
        assertFalse(current.join().ownershipRequired());
    }

    @Test void rejectedSaveTimeoutAndDisconnectNeverReplayMutation() {
        var h = new Harness();
        var rejected = h.client.request(new CollectionCommand.Save(0, DECK)).toCompletableFuture();
        var rejection = new CollectionReply.Rejected(CollectionError.BUSY, 0, EMPTY);
        h.reply(0, rejection);
        assertEquals(rejection, rejected.join());
        var timedOut = h.client.request(new CollectionCommand.Save(0, DECK)).toCompletableFuture();
        assertEquals(10000L, h.delays.get(1));
        h.timers.get(1).run();
        assertInstanceOf(TimeoutException.class, assertThrows(CompletionException.class, timedOut::join).getCause());
        h.reply(1, new CollectionReply.Changed(1, null, DECK, 0, EMPTY));
        assertTrue(timedOut.isCompletedExceptionally());
        assertEquals(2, h.sent.size());
        var pending = h.client.request(new CollectionCommand.ReadDeck(DECK.id())).toCompletableFuture();
        h.client.disconnect();
        h.reply(2, new CollectionReply.Deck(0, DECK));
        assertTrue(pending.isCompletedExceptionally());
        assertNull(h.client.state().view());
    }

    @Test void readDeckRequiresMatchingCurrentCompleteRevisionAndIdThenRefreshes() {
        var h = new Harness();
        h.client.refresh(); h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        var stale = h.client.request(new CollectionCommand.ReadDeck(DECK.id())).toCompletableFuture();
        h.reply(1, new CollectionReply.Deck(3, DECK));
        assertTrue(stale.isCompletedExceptionally());
        assertEquals(new CollectionCommand.Open(), h.sent.get(2).command());
        h.reply(2, new CollectionReply.Opened(UUID.randomUUID(), 3, 0, 0, null));
        var valid = h.client.request(new CollectionCommand.ReadDeck(DECK.id())).toCompletableFuture();
        h.reply(3, new CollectionReply.Deck(3, DECK));
        assertEquals(DECK, ((CollectionReply.Deck) valid.join()).deck());
        var wrong = h.client.request(new CollectionCommand.ReadDeck(UUID.randomUUID())).toCompletableFuture();
        h.reply(4, new CollectionReply.Deck(3, DECK));
        assertTrue(wrong.isCompletedExceptionally());
    }

    @Test void dispatchesRequestsRepliesAndTimeoutCompletionOnClientExecutor() {
        var sent = new ArrayList<CollectionRequestPayload>();
        var queued = new ArrayDeque<Runnable>();
        var timers = new ArrayList<Runnable>();
        var client = new CollectionClient(sent::add, (task, delay) -> { timers.add(task); return () -> {}; }, queued::add);
        client.connect(); queued.remove().run();
        var result = client.request(new CollectionCommand.Open()).toCompletableFuture();
        assertTrue(sent.isEmpty()); queued.remove().run();
        client.receive(new CollectionReplyPayload(sent.getFirst().requestId(), new CollectionReply.Opened(UUID.randomUUID(), 0, 0, 0, null)));
        assertFalse(result.isDone()); queued.remove().run(); assertTrue(result.isDone());
        var timeout = client.request(new CollectionCommand.Open()).toCompletableFuture(); queued.remove().run();
        timers.get(1).run(); assertFalse(timeout.isDone()); queued.remove().run(); assertTrue(timeout.isCompletedExceptionally());
    }

    @Test void transportFailureCompletesExceptionallyWithoutRetryAndDisconnectedRequestIsNotSent() {
        var client = new CollectionClient(payload -> { throw new IllegalStateException("send failed"); },
                (task, delay) -> () -> {}, Runnable::run);
        assertTrue(client.request(new CollectionCommand.Open()).toCompletableFuture().isCompletedExceptionally());
        client.connect();
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class,
                () -> client.request(new CollectionCommand.Save(0, DECK)).toCompletableFuture().join()).getCause());
    }

    @Test void acknowledgedSaveStaysSuccessfulWhenRefreshFailsAndKeepsPreviousView() {
        var h = new Harness();
        h.client.refresh(); h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        var previous = h.client.state().view();
        var save = h.client.request(new CollectionCommand.Save(2, DECK)).toCompletableFuture();
        var changed = new CollectionReply.Changed(3, null, DECK, 0, EMPTY);
        h.reply(1, changed);
        var refreshed = h.client.refresh().toCompletableFuture();
        h.reply(2, new CollectionReply.Opened(UUID.randomUUID(), 3, 1, 0, null));
        h.reply(3, new CollectionReply.Rejected(CollectionError.STALE, 3, EMPTY));
        assertEquals(changed, save.join());
        assertTrue(refreshed.isCompletedExceptionally());
        assertSame(previous, h.client.state().view());
        assertEquals(4, h.sent.size());
    }

    @Test void actualReceiverHookCompletesMatchedRequestAndConcurrentRefreshSharesThePull() {
        var h = new Harness();
        ClientPayloadHandler.setCollectionReceiver(h.client::receive);
        try {
            var first = h.client.refresh().toCompletableFuture();
            var second = h.client.refresh().toCompletableFuture();
            assertEquals(1, h.sent.size());
            ClientPayloadHandler.handleCollection(new CollectionReplyPayload(h.sent.getFirst().requestId(),
                    new CollectionReply.Opened(UUID.randomUUID(), 0, 0, 0, null)), null);
            assertSame(first.join(), second.join());
        } finally { ClientPayloadHandler.setCollectionReceiver(ignored -> {}); }
    }

    @Test void queuedReplyFromOldConnectionAndCancelledTimeoutDoNotAffectNewRequest() {
        var sent = new ArrayList<CollectionRequestPayload>();
        var queued = new ArrayDeque<Runnable>();
        var timers = new ArrayList<Runnable>();
        var client = new CollectionClient(sent::add, (task, delay) -> { timers.add(task); return () -> {}; }, queued::add);
        client.connect(); queued.remove().run();
        var old = client.request(new CollectionCommand.Open()).toCompletableFuture(); queued.remove().run();
        client.disconnect();
        client.receive(new CollectionReplyPayload(sent.getFirst().requestId(), new CollectionReply.Opened(UUID.randomUUID(), 1, 0, 0, null)));
        client.connect();
        while (!queued.isEmpty()) queued.remove().run();
        assertTrue(old.isCompletedExceptionally());
        var current = client.request(new CollectionCommand.Open()).toCompletableFuture(); queued.remove().run();
        timers.getFirst().run(); queued.remove().run();
        assertFalse(current.isDone());
        client.receive(new CollectionReplyPayload(sent.get(1).requestId(), new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null)));
        queued.remove().run(); assertTrue(current.isDone());
    }

    @Test void matchingRequestIdStillRejectsWrongReplyTypeAndMutationRevision() {
        var h = new Harness();
        var read = h.client.request(new CollectionCommand.ReadDeck(DECK.id())).toCompletableFuture();
        h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        assertTrue(read.isCompletedExceptionally());
        var save = h.client.request(new CollectionCommand.Save(2, DECK)).toCompletableFuture();
        h.reply(1, new CollectionReply.Changed(2, null, DECK, 0, EMPTY));
        assertTrue(save.isCompletedExceptionally());
    }

    @Test void postSaveRefreshStartsFreshPullAndLateOldPageCannotDiscardNewAssembly() {
        var h = new Harness();
        var old = h.client.refresh().toCompletableFuture();
        var save = h.client.request(new CollectionCommand.Save(2, DECK)).toCompletableFuture();
        h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 1, 0, null));
        var changed = new CollectionReply.Changed(3, null, DECK, 0, EMPTY);
        h.reply(1, changed);
        var fresh = h.client.refresh().toCompletableFuture();
        var shared = h.client.refresh().toCompletableFuture();
        assertEquals(4, h.sent.size());
        assertEquals(new CollectionCommand.Open(), h.sent.get(3).command());
        var id = UUID.randomUUID();
        h.reply(3, new CollectionReply.Opened(id, 3, 1, 1, DECK.id()));
        h.reply(4, new CollectionReply.Counts(id, 3, 0, Map.of(7, 2L)));
        h.reply(2, new CollectionReply.Rejected(CollectionError.STALE, 3, EMPTY));
        assertFalse(fresh.isDone());
        h.reply(5, new CollectionReply.Decks(id, 3, 0, List.of(new CollectionReply.Summary(DECK.id(), "Draft", 0, 0, 0))));
        assertEquals(changed, save.join());
        assertTrue(old.isCompletedExceptionally());
        assertEquals(3, fresh.join().revision());
        assertEquals(Map.of(7, 2L), fresh.join().counts());
        assertSame(fresh.join(), shared.join());
        assertSame(fresh.join(), h.client.state().view());
        assertEquals(1, h.sent.stream().filter(packet -> packet.command() instanceof CollectionCommand.Save).count());
    }

    @Test void newerReadDeckResponseSupersedesOlderOpenAndItsLateHeaderCannotPublish() {
        var h = new Harness();
        h.client.refresh(); h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        var old = h.client.refresh().toCompletableFuture();
        var read = h.client.request(new CollectionCommand.ReadDeck(DECK.id())).toCompletableFuture();
        h.reply(2, new CollectionReply.Deck(3, DECK));
        assertTrue(read.isCompletedExceptionally());
        assertEquals(4, h.sent.size());
        assertEquals(new CollectionCommand.Open(), h.sent.get(3).command());
        var fresh = h.client.refresh().toCompletableFuture();
        var id = UUID.randomUUID();
        h.reply(3, new CollectionReply.Opened(id, 3, 1, 0, null));
        h.reply(1, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        assertEquals(2, h.client.state().view().revision());
        assertFalse(fresh.isDone());
        h.reply(4, new CollectionReply.Counts(id, 3, 0, Map.of(9, 1L)));
        assertTrue(old.isCompletedExceptionally());
        assertEquals(3, fresh.join().revision());
        assertEquals(Map.of(9, 1L), fresh.join().counts());
    }

    @Test void knownNewRevisionPreventsOlderPageOrFreshHeaderFromPublishing() {
        var h = new Harness();
        h.client.refresh(); h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        var previous = h.client.state().view();
        var old = h.client.refresh().toCompletableFuture();
        var id = UUID.randomUUID();
        h.reply(1, new CollectionReply.Opened(id, 2, 1, 0, null));
        var save = h.client.request(new CollectionCommand.Save(2, DECK)).toCompletableFuture();
        h.reply(3, new CollectionReply.Changed(3, null, DECK, 0, EMPTY));
        h.reply(2, new CollectionReply.Counts(id, 2, 0, Map.of(7, 1L)));
        assertTrue(old.isCompletedExceptionally());
        assertSame(previous, h.client.state().view());
        var fresh = h.client.refresh().toCompletableFuture();
        h.reply(4, new CollectionReply.Opened(UUID.randomUUID(), 2, 0, 0, null));
        assertTrue(fresh.isCompletedExceptionally());
        assertSame(previous, h.client.state().view());
        assertEquals(3, ((CollectionReply.Changed) save.join()).revision());
    }
    @Test void openingRefreshPreservesClearanceAndTypedAccessFailure() {
        var h = new Harness();
        var result = h.client.refresh().toCompletableFuture();
        var report = new DeckEligibility.Report(List.of(), Map.of(7, 1), false, true, null);
        h.reply(0, new CollectionReply.Opened(UUID.randomUUID(), 3, 0, 0, null, true, report));
        assertEquals(report, result.join().clearedActivation());
        assertEquals(1, h.sent.size());
        var denied = h.client.refresh().toCompletableFuture();
        h.reply(1, new CollectionReply.Rejected(CollectionError.BUSY, 3, EMPTY));
        var cause = assertThrows(CompletionException.class, denied::join).getCause();
        assertEquals(CollectionError.BUSY, assertInstanceOf(CollectionClient.RefreshRejectedException.class, cause).error());
        assertEquals(2, h.sent.size());
    }
}

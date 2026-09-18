package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSearchWorkerTest {
    private static final DeckList EMPTY = new DeckList(List.of(), List.of(), List.of());
    private static final CardInfo ALPHA = new CardInfo(1, "Alpha", "", 1, 0, 0, 4, 1, 1);
    private static final CardInfo BETA = new CardInfo(2, "Beta", "", 1, 0, 0, 4, 1, 1);
    private static class Harness {
        final List<Runnable> timers = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final List<Runnable> jobs = new ArrayList<>();
        final List<Runnable> deliveries = new ArrayList<>();
        final List<List<CardInfo>> results = new ArrayList<>();
        final CollectionSearchWorker worker = new CollectionSearchWorker((task, delay) -> {
            timers.add(task); delays.add(delay); return () -> {};
        }, jobs::add, deliveries::add);
        void submit(String text) { worker.submit(List.of(ALPHA, BETA), text, CardSearch.Filters.ALL, Map.of(), EMPTY, results::add); }
    }

    @Test void debounceDefersRealSearchToWorkerThenClientDelivery() {
        var h = new Harness(); h.submit("alpha");
        assertEquals(List.of(150L), h.delays); assertTrue(h.jobs.isEmpty());
        h.timers.getFirst().run(); assertTrue(h.results.isEmpty());
        h.jobs.getFirst().run(); assertTrue(h.results.isEmpty());
        h.deliveries.getFirst().run(); assertEquals(List.of(List.of(ALPHA)), h.results);
    }

    @Test void newestResultWinsEvenWhenOlderJobRunsAfterItsDelivery() {
        var h = new Harness(); h.submit("alpha"); h.timers.get(0).run();
        h.submit("beta"); h.timers.get(1).run();
        h.jobs.get(1).run(); h.deliveries.getFirst().run();
        h.jobs.get(0).run();
        for (int index = 1; index < h.deliveries.size(); index++) h.deliveries.get(index).run();
        assertEquals(List.of(List.of(BETA)), h.results);
    }

    @Test void submissionBetweenComputationAndClientDeliveryInvalidatesOldResult() {
        var h = new Harness(); h.submit("alpha"); h.timers.getFirst().run(); h.jobs.getFirst().run();
        h.submit("beta"); h.deliveries.getFirst().run();
        assertTrue(h.results.isEmpty());
        h.timers.get(1).run(); h.jobs.get(1).run(); h.deliveries.getLast().run();
        assertEquals(List.of(List.of(BETA)), h.results);
    }

    @Test void snapshotsMutableInputsAndUsesOwnedFilter() {
        var h = new Harness();
        var cards = new ArrayList<>(List.of(ALPHA, BETA));
        var counts = new HashMap<>(Map.of(1, 2L));
        var filters = new CardSearch.Filters(0, 0, 0, 0, 0, CardSearch.Measure.ANY,
                null, null, null, null, CardSearch.Ownership.OWNED, CardSearch.Sort.NAME);
        h.worker.submit(cards, "", filters, counts, EMPTY, h.results::add);
        cards.clear(); counts.clear(); counts.put(2, 1L);
        h.timers.getFirst().run(); h.jobs.getFirst().run(); h.deliveries.getFirst().run();
        assertEquals(List.of(List.of(ALPHA)), h.results);
    }

    @Test void closingInvalidatesTimersJobsAndQueuedClientDeliveries() {
        for (int phase = 0; phase < 3; phase++) {
            var h = new Harness(); h.submit("alpha");
            if (phase > 0) h.timers.getFirst().run();
            if (phase > 1) h.jobs.getFirst().run();
            h.worker.close(); h.timers.forEach(Runnable::run); h.jobs.forEach(Runnable::run); h.deliveries.forEach(Runnable::run);
            assertTrue(h.results.isEmpty());
            h.submit("beta"); assertEquals(1, h.timers.size());
        }
    }

    @Test void closingLeavesInjectedExecutorsAvailableToTheirOwner() {
        var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var search = new CollectionSearchWorker((task, delay) -> {
                var future = scheduler.schedule(task, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            }, worker, Runnable::run);
            search.close();
            assertFalse(scheduler.isShutdown());
            assertFalse(worker.isShutdown());
        } finally { scheduler.shutdownNow(); worker.shutdownNow(); }
    }
}

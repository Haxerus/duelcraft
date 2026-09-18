package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Screen-owned asynchronous search; invalidation also guards queued client deliveries. */
public final class CollectionSearchWorker implements AutoCloseable {
    private final BiFunction<Runnable, Long, Runnable> scheduler;
    private final Executor worker;
    private final Executor client;
    private ScheduledExecutorService ownedScheduler;
    private ExecutorService ownedWorker;
    private long generation;
    private boolean closed;
    private Runnable cancel = () -> {};

    public CollectionSearchWorker(Executor client) {
        ownedScheduler = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "DuelcraftCollectionDebounce"));
        ownedWorker = Executors.newSingleThreadExecutor(task -> daemon(task, "DuelcraftCollectionSearch"));
        scheduler = (task, delay) -> {
            var scheduled = ownedScheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
            return () -> scheduled.cancel(false);
        };
        worker = ownedWorker;
        this.client = Objects.requireNonNull(client);
    }

    public CollectionSearchWorker(BiFunction<Runnable, Long, Runnable> scheduler, Executor worker, Executor client) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.worker = Objects.requireNonNull(worker);
        this.client = Objects.requireNonNull(client);
    }

    private static Thread daemon(Runnable task, String name) {
        var thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    public synchronized void submit(List<CardInfo> cards, String text, CardSearch.Filters filters,
                                    Map<Integer, Long> counts, DeckList draft, Consumer<List<CardInfo>> apply) {
        if (closed) return;
        long submitted = ++generation;
        cancel.run();
        var catalog = List.copyOf(cards);
        var owned = Map.copyOf(counts);
        Objects.requireNonNull(text); Objects.requireNonNull(filters); Objects.requireNonNull(draft); Objects.requireNonNull(apply);
        cancel = scheduler.apply(() -> enqueue(submitted, () -> {
            if (!current(submitted)) return;
            var results = CardSearch.search(catalog, text, filters, owned, draft);
            client.execute(() -> deliver(submitted, results, apply));
        }), 150L);
    }

    private synchronized void enqueue(long submitted, Runnable job) {
        if (current(submitted)) worker.execute(job);
    }

    private synchronized boolean current(long submitted) { return !closed && generation == submitted; }

    private synchronized void deliver(long submitted, List<CardInfo> results, Consumer<List<CardInfo>> apply) {
        if (current(submitted)) apply.accept(results);
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        generation++;
        cancel.run();
        if (ownedScheduler != null) ownedScheduler.shutdownNow();
        if (ownedWorker != null) ownedWorker.shutdownNow();
    }
}

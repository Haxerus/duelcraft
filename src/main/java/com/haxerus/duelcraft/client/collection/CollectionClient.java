package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.server.collection.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Client-thread requests and private snapshots. Failed mutations are never replayed. */
public final class CollectionClient {
    public static final class RefreshRejectedException extends IllegalStateException {
        private final CollectionError error;
        private RefreshRejectedException(CollectionReply.Rejected reply) {
            super("Collection refresh rejected: " + reply.error());
            error = reply.error();
        }
        public CollectionError error() { return error; }
    }
    private static final long TIMEOUT_MS = 10000;
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "DuelcraftCollectionTimeout");
        thread.setDaemon(true);
        return thread;
    });
    private static final class Pending {
        final CollectionCommand command;
        final CompletableFuture<CollectionReply> result;
        Runnable cancel = () -> {};
        Pending(CollectionCommand command, CompletableFuture<CollectionReply> result) {
            this.command = command; this.result = result;
        }
    }

    private final Consumer<CollectionRequestPayload> transport;
    // The returned Runnable cancels the scheduled task. Injected schedulers remain caller-owned.
    private final BiFunction<Runnable, Long, Runnable> scheduler;
    private final Executor client;
    private final ClientCollectionState state = new ClientCollectionState();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private volatile long generation;
    private boolean connected;
    private CompletableFuture<ClientCollectionState.View> refreshing;
    private long requiredRevision;
    private long refreshingRevision;

    public CollectionClient(Executor client) {
        this(PacketDistributor::sendToServer, (task, delay) -> {
            var scheduled = TIMEOUTS.schedule(task, delay, TimeUnit.MILLISECONDS);
            return () -> scheduled.cancel(false);
        }, client);
    }

    public CollectionClient(Consumer<CollectionRequestPayload> transport,
                            BiFunction<Runnable, Long, Runnable> scheduler, Executor client) {
        this.transport = Objects.requireNonNull(transport);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.client = Objects.requireNonNull(client);
    }

    public ClientCollectionState state() { return state; }

    public void connect() {
        client.execute(() -> { reset(); connected = true; });
    }

    public void disconnect() { client.execute(this::reset); }

    private void reset() {
        connected = false;
        generation++;
        var requests = new ArrayList<>(pending.values());
        pending.clear();
        var refresh = refreshing;
        refreshing = null;
        requiredRevision = 0;
        refreshingRevision = 0;
        state.clear();
        for (var request : requests) {
            request.cancel.run();
            request.result.completeExceptionally(new CancellationException("Collection connection closed"));
        }
        if (refresh != null) refresh.completeExceptionally(new CancellationException("Collection connection closed"));
    }

    public CompletionStage<CollectionReply> request(CollectionCommand command) {
        Objects.requireNonNull(command);
        var result = new CompletableFuture<CollectionReply>();
        long connection = generation;
        client.execute(() -> send(command, result, connection));
        return result;
    }

    private void send(CollectionCommand command, CompletableFuture<CollectionReply> result, long connection) {
        if (!connected || connection != generation) {
            result.completeExceptionally(new CancellationException("Collection is disconnected"));
            return;
        }
        var id = UUID.randomUUID();
        var request = new Pending(command, result);
        pending.put(id, request);
        try {
            request.cancel = scheduler.apply(() -> client.execute(() -> {
                if (generation == connection && pending.remove(id, request)) {
                    request.cancel.run();
                    result.completeExceptionally(new TimeoutException("Collection request timed out"));
                }
            }), TIMEOUT_MS);
            transport.accept(new CollectionRequestPayload(id, command));
        } catch (RuntimeException exception) {
            pending.remove(id);
            request.cancel.run();
            result.completeExceptionally(exception);
        }
    }

    /** Installed through ClientPayloadHandler.setCollectionReceiver; queued replies retain their generation. */
    public void receive(CollectionReplyPayload payload) {
        long connection = generation;
        client.execute(() -> {
            if (!connected || connection != generation) return;
            var request = pending.remove(payload.requestId());
            if (request == null) return;
            request.cancel.run();
            var reply = payload.reply();
            if (!matches(request.command, reply)) {
                request.result.completeExceptionally(new IllegalArgumentException("Reply does not match collection request"));
                return;
            }
            switch (reply) {
                case CollectionReply.Changed changed -> requiredRevision = Math.max(requiredRevision, changed.revision());
                case CollectionReply.Deck deck -> requiredRevision = Math.max(requiredRevision, deck.revision());
                case CollectionReply.Rejected rejected -> requiredRevision = Math.max(requiredRevision, rejected.revision());
                default -> {}
            }
            if (request.command instanceof CollectionCommand.ReadDeck read && reply instanceof CollectionReply.Deck deck
                    && (state.view() == null || state.view().revision() != deck.revision()
                    || deck.revision() < requiredRevision || !read.id().equals(deck.deck().id()))) {
                request.result.completeExceptionally(new IllegalStateException("Deck does not match the current collection view; refreshing"));
                refresh();
                return;
            }
            request.result.complete(reply);
        });
    }

    private static boolean matches(CollectionCommand command, CollectionReply reply) {
        if (reply instanceof CollectionReply.Rejected) return true;
        return switch (command) {
            case CollectionCommand.Open ignored -> reply instanceof CollectionReply.Opened;
            case CollectionCommand.Page page -> switch (reply) {
                case CollectionReply.Counts counts -> page.kind() == CollectionCommand.PageKind.COUNTS
                        && page.snapshotId().equals(counts.snapshotId()) && page.index() == counts.index();
                case CollectionReply.Decks decks -> page.kind() == CollectionCommand.PageKind.DECKS
                        && page.snapshotId().equals(decks.snapshotId()) && page.index() == decks.index();
                default -> false;
            };
            case CollectionCommand.ReadDeck ignored -> reply instanceof CollectionReply.Deck;
            case CollectionCommand.Save save -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(save.expectedRevision(), changed.revision()) && save.deck().equals(changed.saved());
            case CollectionCommand.Delete delete -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(delete.expectedRevision(), changed.revision()) && changed.saved() == null;
            case CollectionCommand.Activate activate -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(activate.expectedRevision(), changed.revision()) && changed.saved() == null;
            case CollectionCommand.ClearActive clear -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(clear.expectedRevision(), changed.revision()) && changed.saved() == null;
            case CollectionCommand.Deposit deposit -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(deposit.expectedRevision(), changed.revision()) && changed.saved() == null
                    && changed.transferred() == deposit.amount() && changed.skipped() == 0;
            case CollectionCommand.Withdraw withdraw -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(withdraw.expectedRevision(), changed.revision()) && changed.saved() == null
                    && changed.transferred() == withdraw.amount() && changed.skipped() == 0;
            case CollectionCommand.DepositAll all -> reply instanceof CollectionReply.Changed changed
                    && nextRevision(all.expectedRevision(), changed.revision()) && changed.saved() == null && changed.transferred() > 0;
        };
    }

    private static boolean nextRevision(long expected, long received) {
        return expected >= 0 && expected < Long.MAX_VALUE && received == expected + 1;
    }

    /** Pulls one page at a time. Callers coalesce only while the pull meets the required revision. */
    public CompletionStage<ClientCollectionState.View> refresh() {
        var result = new CompletableFuture<ClientCollectionState.View>();
        long connection = generation;
        client.execute(() -> {
            if (!connected || connection != generation) {
                result.completeExceptionally(new CancellationException("Collection is disconnected"));
                return;
            }
            if (refreshing != null && refreshingRevision >= requiredRevision) {
                refreshing.whenComplete((view, error) -> complete(result, view, error));
                return;
            }
            var previous = refreshing;
            refreshing = result;
            refreshingRevision = requiredRevision;
            state.discardPending();
            if (previous != null) previous.completeExceptionally(new CancellationException("Collection refresh superseded"));
            request(new CollectionCommand.Open()).whenComplete((reply, error) -> {
                if (refreshing != result) return;
                if (error != null) { finishRefresh(result, error); return; }
                try {
                    if (reply instanceof CollectionReply.Rejected rejected) throw new RefreshRejectedException(rejected);
                    if (!(reply instanceof CollectionReply.Opened opened)) throw new IllegalStateException("Collection refresh rejected: " + reply);
                    if (opened.revision() < requiredRevision) throw new IllegalStateException("Collection snapshot is outdated");
                    refreshingRevision = opened.revision();
                    state.begin(opened);
                    pullPage(result);
                } catch (RuntimeException exception) { finishRefresh(result, exception); }
            });
        });
        return result;
    }

    private void pullPage(CompletableFuture<ClientCollectionState.View> result) {
        var page = state.nextPage();
        if (page == null) { finishRefresh(result, null); return; }
        request(page).whenComplete((reply, error) -> {
            if (refreshing != result) return;
            if (error != null) { finishRefresh(result, error); return; }
            try {
                if (refreshingRevision < requiredRevision) throw new IllegalStateException("Collection changed during refresh");
                state.accept(reply);
                pullPage(result);
            } catch (RuntimeException exception) { finishRefresh(result, exception); }
        });
    }

    private void finishRefresh(CompletableFuture<ClientCollectionState.View> result, Throwable error) {
        if (refreshing != result) return;
        refreshing = null;
        if (error != null) state.discardPending();
        complete(result, state.view(), error);
    }

    private static <T> void complete(CompletableFuture<T> result, T value, Throwable error) {
        if (error == null) result.complete(value);
        else result.completeExceptionally(error);
    }
}

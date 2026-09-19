package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.duel.PreparationCommand;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.server.PreparationRequestPayload;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Client-thread state. Request acknowledgements and monotonic view application are independent. */
public final class ClientPreparationState {
    private final Consumer<PreparationRequestPayload> send;
    private final Map<UUID, CompletableFuture<PreparationStatePayload>> pending = new HashMap<>();
    private PreparationView view;
    private boolean connected;
    public ClientPreparationState(Consumer<PreparationRequestPayload> send) { this.send = send; }
    public PreparationView view() { return view; }
    public void connect() { disconnect(); connected = true; }
    public void disconnect() {
        connected = false; view = null;
        var requests = new ArrayList<>(pending.values()); pending.clear();
        for (var request : requests) request.completeExceptionally(new IllegalStateException("Disconnected"));
    }
    public CompletableFuture<PreparationStatePayload> request(PreparationCommand command) {
        if (!connected) return CompletableFuture.failedFuture(new IllegalStateException("Disconnected"));
        var id = UUID.randomUUID(); var future = new CompletableFuture<PreparationStatePayload>(); pending.put(id, future);
        try { send.accept(new PreparationRequestPayload(id, command)); }
        catch (RuntimeException exception) { pending.remove(id); future.completeExceptionally(exception); }
        return future;
    }
    /** True only when this reply applies to current UI state. */
    public boolean receive(PreparationStatePayload reply) {
        if (!connected) return false;
        boolean apply = view == null || reply.view().revision() >= view.revision();
        if (apply) view = reply.view();
        var future = pending.remove(reply.requestId());
        if (future != null) future.complete(reply);
        return apply;
    }
}

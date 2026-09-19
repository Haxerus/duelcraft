package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Client-thread owner of saved-list identity, acknowledgement and deferred navigation. */
public final class SavedDeckController {
    private static final DeckEligibility.Report EMPTY = new DeckEligibility.Report(List.of(), Map.of(), false, false, null);
    private final DeckEditorModel model;
    private final DeckSaveHandler saveHandler;
    private final Function<CollectionCommand, CompletionStage<CollectionReply>> request;
    private final Supplier<CompletionStage<ClientCollectionState.View>> refresh;
    private final Executor client;
    private final Runnable changed;
    private UUID id;
    private String name;
    private String savedName;
    private boolean stored = true;
    private long revision;
    private long viewRevision;
    private UUID activeId;
    private boolean ownershipRequired;
    private List<CollectionReply.Summary> summaries = List.of();
    private boolean ready;
    private boolean pending;
    private boolean disposed;
    private long readToken;
    private Runnable navigation;
    private boolean decision;
    private String status = "";
    private String detail = "";
    private int transferred, skipped;
    private DeckEligibility.Report eligibility = EMPTY;

    public SavedDeckController(DeckEditorModel model, UUID id, String name, DeckSaveHandler saveHandler,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh, Executor client, Runnable changed) {
        this.model = model;
        this.id = id;
        this.name = name;
        savedName = name;
        this.saveHandler = saveHandler;
        this.request = request;
        this.refresh = refresh;
        this.client = client;
        this.changed = changed;
        ready = saveHandler != null;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public long revision() { return revision; }
    public UUID activeId() { return activeId; }
    public boolean ownershipRequired() { return ownershipRequired; }
    public List<CollectionReply.Summary> summaries() { return summaries; }
    public boolean pending() { return pending; }
    public boolean ready() { return ready; }
    public boolean stored() { return stored; }
    public boolean needsDecision() { return decision; }
    public boolean dirty() { return !stored || model.dirty() || !name.equals(savedName); }
    public String status() { return status; }
    public String detail() { return detail; }
    public DeckEligibility.Report eligibility() { return eligibility; }
    public int transferred() { return transferred; }
    public int skipped() { return skipped; }

    public void rename(String name) {
        requireEditable();
        this.name = name;
        changed.run();
    }

    public void newDraft() {
        requireEditable();
        eligibility = EMPTY;
        readToken++;
        id = UUID.randomUUID();
        name = "New list";
        savedName = name;
        stored = false;
        model.load(new DeckList(List.of(), List.of(), List.of()));
        changed.run();
    }

    public void duplicate() {
        requireEditable();
        readToken++;
        id = UUID.randomUUID();
        // Keep the name valid even when its original was already 128 characters.
        name = name.substring(0, Math.min(name.length(), 123)) + " copy";
        savedName = name;
        stored = false;
        changed.run();
    }

    public void importDraft(SavedDeck imported) {
        requireEditable();
        eligibility = EMPTY;
        readToken++;
        id = imported.id();
        name = imported.name();
        savedName = name;
        stored = false;
        model.load(imported.cards());
        changed.run();
    }

    public void importFailed(Throwable error) {
        if (!disposed) fail(error);
    }

    public void applyView(ClientCollectionState.View view) {
        if (disposed || view.revision() < revision) return;
        revision = view.revision();
        viewRevision = view.revision();
        activeId = view.activeId();
        ownershipRequired = view.ownershipRequired();
        summaries = view.summaries();
        model.replaceOwnership(view.counts());
        if (view.clearedActivation() != null) {
            eligibility = view.clearedActivation();
            status = "active_cleared";
        }
        ready = true;
        changed.run();
    }

    public void refresh() {
        if (disposed) return;
        refresh.get().whenCompleteAsync((view, error) -> {
            if (disposed) return;
            if (error == null) applyView(view);
            else refreshFailed(error);
        }, client);
    }

    public void select(UUID target) {
        requireEditable();
        long token = ++readToken;
        UUID origin = id;
        String originName = name;
        DeckList originDraft = model.draft();
        if (saveHandler == null && viewRevision < revision) {
            refresh.get().whenCompleteAsync((view, error) -> {
                if (!readCurrent(token, origin, originName, originDraft)) return;
                if (error != null) { refreshFailed(error); return; }
                applyView(view);
                if (viewRevision < revision) { refreshFailed(new IllegalStateException("Collection snapshot is outdated")); return; }
                readDeck(target, token, origin, originName, originDraft);
            }, client);
        } else readDeck(target, token, origin, originName, originDraft);
    }

    private boolean readCurrent(long token, UUID origin, String originName, DeckList originDraft) {
        return !disposed && token == readToken && origin.equals(id)
                && originName.equals(name) && originDraft.equals(model.draft());
    }

    private void readDeck(UUID target, long token, UUID origin, String originName, DeckList originDraft) {
        long expected = revision;
        request.apply(new CollectionCommand.ReadDeck(target)).whenCompleteAsync((reply, error) -> {
            if (!readCurrent(token, origin, originName, originDraft)) return;
            if (error != null) { fail(error); refresh(); return; }
            if (!(reply instanceof CollectionReply.Deck deck) || deck.revision() != revision
                    || expected != revision || !target.equals(deck.deck().id())) {
                reject(reply); refresh(); return;
            }
            id = target;
            name = deck.deck().name();
            savedName = name;
            stored = true;
            model.load(deck.deck().cards());
            status = "";
            eligibility = EMPTY;
            changed.run();
        }, client);
    }

    public void navigate(Runnable action) {
        if (disposed) return;
        navigation = action;
        if (pending) return;
        if (dirty()) { decision = true; changed.run(); }
        else finishNavigation();
    }

    public void cancelNavigation() { navigation = null; decision = false; changed.run(); }

    public void discardNavigation() {
        if (pending || disposed) return;
        model.discard();
        name = savedName;
        finishNavigation();
    }

    public void save() {
        if (pending || disposed || !ready) return;
        SavedDeck submitted;
        try { submitted = new SavedDeck(id, name, model.draft()); }
        catch (RuntimeException error) { fail(error); return; }
        readToken++;
        eligibility = EMPTY;
        detail = "";
        setPending(true);
        status = "saving";
        changed.run();
        try {
            CompletionStage<SavedDeck> stage;
            if (saveHandler != null) stage = saveHandler.save(submitted.name(), submitted.cards());
            else stage = request.apply(new CollectionCommand.Save(revision, submitted)).thenApplyAsync(reply -> {
                if (disposed) throw new CancellationException("Editor disposed");
                if (!(reply instanceof CollectionReply.Changed ack) || !submitted.equals(ack.saved()))
                    throw rejection(reply);
                applyAcknowledgement(ack);
                return ack.saved();
            }, client);
            stage.whenCompleteAsync((saved, error) -> {
                if (disposed) return;
                setPending(false);
                if (error != null || !submitted.equals(saved)) {
                    decision = navigation != null;
                    fail(error != null ? error : new IllegalArgumentException("Save acknowledgement differs from submitted list"));
                    if (saveHandler == null) refresh();
                    return;
                }
                savedName = submitted.name();
                stored = true;
                model.acknowledge(submitted.cards());
                status = eligibility.eligible() ? "saved" : "saved_active_cleared";
                changed.run();
                finishNavigation();
                if (saveHandler == null) refresh();
            }, client);
        } catch (RuntimeException error) {
            setPending(false); decision = navigation != null; fail(error);
        }
    }

    public void activate() {
        if (dirty()) { status = "save_first"; changed.run(); return; }
        mutate(new CollectionCommand.Activate(revision, id), false);
    }

    public void clearActive() { mutate(new CollectionCommand.ClearActive(revision), false); }
    public void delete() { mutate(new CollectionCommand.Delete(revision, id), true); }

    public void transfer(CollectionCommand command) {
        if (pending || disposed || !ready) return;
        UUID previouslyActive = activeId;
        eligibility = EMPTY;
        detail = "";
        status = "transferring";
        readToken++;
        setPending(true);
        changed.run();
        try {
            request.apply(command).whenCompleteAsync((reply, error) -> {
                if (disposed) return;
                if (error != null) fail(error);
                else if (reply instanceof CollectionReply.Changed ack) {
                    applyAcknowledgement(ack);
                    transferred = ack.transferred(); skipped = ack.skipped();
                    status = previouslyActive != null && activeId == null
                            ? command instanceof CollectionCommand.Withdraw && eligibility.ownershipRequired()
                                    && !eligibility.missing().isEmpty() && eligibility.restrictionReason() == null
                                    ? "withdraw_active_cleared" : "active_cleared"
                            : "transfer_done";
                } else reject(reply);
                refresh.get().whenCompleteAsync((view, refreshError) -> {
                    if (disposed) return;
                    if (refreshError == null) applyView(view); else refreshFailed(refreshError);
                    setPending(false);
                    changed.run();
                    if (navigation != null) navigate(navigation);
                }, client);
            }, client);
        } catch (RuntimeException error) { setPending(false); fail(error); }
    }

    private void mutate(CollectionCommand command, boolean delete) {
        if (pending || disposed || !ready) return;
        eligibility = EMPTY;
        detail = "";
        setPending(true);
        changed.run();
        request.apply(command).whenCompleteAsync((reply, error) -> {
            if (disposed) return;
            setPending(false);
            if (error != null) { fail(error); return; }
            if (!(reply instanceof CollectionReply.Changed ack)) { reject(reply); refresh(); return; }
            applyAcknowledgement(ack);
            if (delete) newDraft();
            status = "updated";
            changed.run();
            if (navigation != null) navigate(navigation);
            refresh();
        }, client);
    }

    public void dispose() {
        disposed = true;
        readToken++;
        navigation = null;
        decision = false;
        setPending(false);
    }

    private void applyAcknowledgement(CollectionReply.Changed ack) {
        eligibility = ack.eligibility();
        if (ack.revision() >= revision) {
            revision = ack.revision();
            activeId = ack.activeId();
        }
    }

    private void requireEditable() {
        if (pending || disposed) throw new IllegalStateException("Wait for the pending collection operation");
    }

    private void setPending(boolean value) { pending = value; model.setFrozen(value); }
    private void finishNavigation() {
        decision = false;
        var action = navigation;
        navigation = null;
        changed.run();
        if (action != null) action.run();
    }

    private void reject(CollectionReply reply) { fail(rejection(reply)); }
    private RuntimeException rejection(CollectionReply reply) {
        if (reply instanceof CollectionReply.Rejected rejected) {
            if (rejected.error() == CollectionError.BUSY) ready = false;
            eligibility = rejected.eligibility();
            String reason = rejected.eligibility().restrictionReason();
            if (reason == null && rejected.eligibility().ownershipRequired()
                    && !rejected.eligibility().missing().isEmpty()) {
                reason = "missing copies (" + rejected.eligibility().missing().values().stream()
                        .mapToInt(Integer::intValue).sum() + ")";
            }
            return new IllegalStateException(rejected.error().name() + (reason == null ? "" : ": " + reason));
        }
        return new IllegalStateException("Collection changed; refresh and retry");
    }

    private void fail(Throwable error) {
        status = "operation_failed";
        var cause = root(error);
        detail = cause.getMessage() == null ? "Try again" : cause.getMessage();
        changed.run();
    }

    private void refreshFailed(Throwable error) {
        if ("Collection refresh superseded".equals(root(error).getMessage())) return;
        if (root(error) instanceof CollectionClient.RefreshRejectedException rejected
                && rejected.error() == CollectionError.BUSY) ready = false;
        status = "refresh_failed";
        detail = root(error).getMessage() == null ? "Try again" : root(error).getMessage();
        changed.run();
    }

    private static Throwable root(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error;
    }
}

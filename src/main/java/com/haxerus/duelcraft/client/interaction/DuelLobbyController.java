package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.duel.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Typed actions always take their IDs from the view currently on screen. */
public final class DuelLobbyController {
    private final Supplier<PreparationView> view;
    private final Consumer<PreparationCommand> send;
    private boolean pending;
    public DuelLobbyController(Supplier<PreparationView> view, Consumer<PreparationCommand> send) {
        this.view = view; this.send = send;
    }
    public boolean pending() { return pending; }
    public void acknowledge() { pending = false; }
    public void onRock() { onHand(FirstTurnLobby.Hand.ROCK); }
    public void onHand(FirstTurnLobby.Hand hand) {
        var state = view.get();
        if (state != null && state.mode() == PreparationView.Mode.RPS && !state.ownHandSubmitted())
            submit(new PreparationCommand.Hand(state.flowId(), state.roundId(), hand));
    }
    public void onAccept() { invitation(true); }
    public void onDecline() { invitation(false); }
    private void invitation(boolean accept) {
        var state = view.get();
        if (state != null && state.mode() == PreparationView.Mode.INVITED && !state.outgoing())
            submit(accept ? new PreparationCommand.Accept(state.flowId()) : new PreparationCommand.Decline(state.flowId()));
    }
    public void onCancel() {
        var state = view.get();
        if (state != null && state.flowId() != null && state.mode() != PreparationView.Mode.STARTING)
            submit(new PreparationCommand.Cancel(state.flowId()));
    }
    public void onFirst(boolean first) {
        var state = view.get();
        if (state != null && state.mode() == PreparationView.Mode.FIRST_CHOICE && state.canChooseFirst())
            submit(new PreparationCommand.First(state.flowId(), first));
    }
    public void submit(PreparationCommand command) {
        if (pending) return;
        pending = true;
        try { send.accept(command); }
        catch (RuntimeException error) { pending = false; throw error; }
    }
}

package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelLobbyControllerTest {
    final UUID flow = UUID.randomUUID(), round = UUID.randomUUID();
    PreparationView view(PreparationView.Mode mode, boolean outgoing, boolean submitted, boolean chooser) {
        return new PreparationView(3, mode, flow, mode == PreparationView.Mode.RPS ? round : null,
                UUID.randomUUID(), "Other player", outgoing, submitted, chooser, 45000,
                new DuelSettings(DuelRule.MR5, 20, PlayerOptions.standard()));
    }
    @Test void handPreservesIdsAndRepeatedClickSendsOnce() {
        var sent = new ArrayList<PreparationCommand>();
        var c = new DuelLobbyController(() -> view(PreparationView.Mode.RPS, false, false, false), sent::add);
        c.onRock(); c.onRock();
        assertEquals(List.of(new PreparationCommand.Hand(flow, round, FirstTurnLobby.Hand.ROCK)), sent);
        assertTrue(c.pending()); c.acknowledge(); assertFalse(c.pending());
    }
    @Test void incomingActionsUseDisplayedInvitationOnly() {
        var sent = new ArrayList<PreparationCommand>();
        var c = new DuelLobbyController(() -> view(PreparationView.Mode.INVITED, false, false, false), sent::add);
        c.onAccept(); c.acknowledge(); c.onDecline(); c.acknowledge(); c.onCancel();
        assertEquals(List.of(new PreparationCommand.Accept(flow), new PreparationCommand.Decline(flow), new PreparationCommand.Cancel(flow)), sent);
    }
    @Test void onlyWinnerCanChooseAndSubmittedHandCannotSendAgain() {
        var sent = new ArrayList<PreparationCommand>();
        new DuelLobbyController(() -> view(PreparationView.Mode.FIRST_CHOICE, false, false, false), sent::add).onFirst(true);
        new DuelLobbyController(() -> view(PreparationView.Mode.RPS, false, true, false), sent::add).onRock();
        assertTrue(sent.isEmpty());
        new DuelLobbyController(() -> view(PreparationView.Mode.FIRST_CHOICE, false, false, true), sent::add).onFirst(false);
        assertEquals(List.of(new PreparationCommand.First(flow, false)), sent);
    }
    @Test void outgoingCannotAcceptOrDecline() {
        var sent = new ArrayList<PreparationCommand>();
        var c = new DuelLobbyController(() -> view(PreparationView.Mode.INVITED, true, false, false), sent::add);
        c.onAccept(); c.onDecline();
        assertTrue(sent.isEmpty());
    }
}

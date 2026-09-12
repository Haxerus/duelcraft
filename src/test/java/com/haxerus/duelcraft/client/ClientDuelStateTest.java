package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientDuelStateTest {

    private static ClientDuelState newState() {
        return new ClientDuelState(0, "Opponent", 8000, 8000, 40, 15, 0L);
    }

    /** Read a little-endian int32 from the response at a byte offset. */
    private static int readInt32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    // ---- Battle-phase Activate must send the engine's action type 0 ----

    @Test
    void battleActivateSendsActionType0() {
        var state = newState();
        var activatable = new DuelMessage.ActivatableCard(12345, 0, LOCATION_MZONE, 0, 100L, 0);
        var battleCmd = new DuelMessage.SelectBattleCmd(0, List.of(activatable), List.of(), true, true);

        state.applyMessage(battleCmd);

        var loc = new ClientDuelState.CardLocation(0, LOCATION_MZONE, 0);
        var actions = state.cardActions.get(loc);
        assertNotNull(actions);
        var activate = actions.stream()
                .filter(a -> a.label().equals("Activate"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, activate.actionType());

        byte[] response = ResponseBuilder.selectCmd(activate.actionType(), activate.listIndex());
        assertEquals(0, readInt32(response, 0) & 0xFFFF);
    }

    // ---- SelectDisfield is treated as a prompt like SelectPlace ----

    @Test
    void selectDisfieldSetsPendingPrompt() {
        var state = newState();
        var disfield = new DuelMessage.SelectDisfield(0, 2, 0x0000001F);

        state.applyMessage(disfield);

        assertEquals(disfield, state.pendingPrompt);
    }

    // ---- Answered guard: at most one response per prompt ----

    @Test
    void secondResponseForSamePromptIsRejected() {
        var state = newState();
        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));

        assertTrue(state.markResponseSent());
        assertFalse(state.markResponseSent());
    }

    @Test
    void nextPromptResetsTheGuard() {
        var state = newState();
        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        assertTrue(state.markResponseSent());

        state.applyMessage(new DuelMessage.SelectYesNo(0, 43L));
        assertTrue(state.markResponseSent());
    }

    @Test
    void retryResetsTheGuard() {
        var state = newState();
        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        assertTrue(state.markResponseSent());

        state.applyMessage(new DuelMessage.Retry());
        assertTrue(state.markResponseSent());
    }

    // ---- Retry restores the prompt (Task 2) ----

    @Test
    void retryRestoresThePendingPromptAfterItWasSent() {
        var state = newState();
        var prompt = new DuelMessage.SelectYesNo(0, 42L);
        state.applyMessage(prompt);

        assertTrue(state.markResponseSent());
        state.onResponseSent();
        assertNull(state.pendingPrompt);

        state.applyMessage(new DuelMessage.Retry());

        assertEquals(prompt, state.pendingPrompt);
        assertTrue(state.markResponseSent());
    }

    @Test
    void retrySetsAStatusMessage() {
        var state = newState();
        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        state.markResponseSent();
        state.onResponseSent();

        state.applyMessage(new DuelMessage.Retry());

        assertEquals("Invalid response, try again", state.retryMessage);
    }

    @Test
    void retryRebuildsCardActionsForIdleCmd() {
        var state = newState();
        var summonable = new DuelMessage.IdleCmdCard(12345, 0, LOCATION_HAND, 0);
        var idleCmd = new DuelMessage.SelectIdleCmd(0, List.of(summonable), List.of(), List.of(),
                List.of(), List.of(), List.of(), true, true, false);
        state.applyMessage(idleCmd);

        state.markResponseSent();
        state.onResponseSent();
        assertTrue(state.cardActions.isEmpty());

        state.applyMessage(new DuelMessage.Retry());

        assertEquals(idleCmd, state.pendingPrompt);
        var loc = new ClientDuelState.CardLocation(0, LOCATION_HAND, 0);
        assertFalse(state.cardActions.getOrDefault(loc, List.of()).isEmpty());
    }
}

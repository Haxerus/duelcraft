package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
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

    private static void draw(ClientDuelState state, int player, int... codes) {
        state.applyMessage(new DuelMessage.Draw(player, java.util.Arrays.stream(codes)
                .mapToObj(code -> new DuelMessage.DrawnCard(code, POS_FACEDOWN_DEFENSE))
                .toList()));
    }

    private static void move(ClientDuelState state, int code, LocInfo from, LocInfo to) {
        state.applyMessage(new DuelMessage.Move(code, from, to, 0));
    }

    private static List<Integer> codesOf(List<ClientCard> cards) {
        return cards.stream().map(card -> card.code).toList();
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

    // ---- Duel result (MSG_WIN or a host-synthesised DuelEndPayload) ----

    @Test
    void applyResultStoresTheWinnerAndRaisesTheWinnerFlag() {
        var state = newState();

        state.applyResult(1, 4); // opponent wins by disconnect

        assertEquals(1, state.winner);
        assertEquals(4, state.winReason);
        assertTrue(state.consumeDirtyFlags().contains(ClientDuelState.DirtyFlag.WINNER));
    }

    @Test
    void winMessageGoesThroughApplyResult() {
        var state = newState();

        state.applyMessage(new DuelMessage.Win(0, 1));

        assertEquals(0, state.winner);
        assertEquals(1, state.winReason);
        assertTrue(state.consumeDirtyFlags().contains(ClientDuelState.DirtyFlag.WINNER));
    }

    // ---- Card object model (Task 12) ----

    /** The engine reports a material's destination as the host zone | LOCATION_OVERLAY, e.g. 0x84. */
    @Test
    void overlayAttachPutsTheMaterialOnTheHostCard() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        move(state, 22222, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 0));
        move(state, 33333, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 1));

        var host = state.mzone[0][0];
        assertNotNull(host);
        assertEquals(11111, host.code);
        assertEquals(List.of(22222, 33333), codesOf(host.materials));
        assertTrue(state.hand[0].isEmpty());
    }

    @Test
    void overlayDetachRenumbersTheSurvivingMaterials() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333, 44444);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        for (int code : new int[]{22222, 33333, 44444}) {
            move(state, code, new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 0));
        }

        // Detach the middle material: from.position is the material's index on the host.
        move(state, 33333, new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 1),
                new LocInfo(0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK));

        var host = state.mzone[0][0];
        assertEquals(List.of(22222, 44444), codesOf(host.materials));
        assertEquals(0, host.materials.get(0).sequence);
        assertEquals(1, host.materials.get(1).sequence);
        assertEquals(List.of(33333), codesOf(state.grave[0]));
    }

    @Test
    void countersClearWhenTheCardLeavesTheField() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        state.applyMessage(new DuelMessage.AddCounter(0x1, 0, LOCATION_MZONE, 0, 3));
        var card = state.mzone[0][0];
        assertEquals(3, card.counters.get(0x1));

        move(state, 11111, new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                new LocInfo(0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK));

        assertTrue(card.counters.isEmpty());
        assertSame(card, state.grave[0].getFirst());
    }

    @Test
    void swapExchangesSpellTrapZonesAcrossControllers() {
        var state = newState();
        draw(state, 0, 11111);
        draw(state, 1, 22222);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_SZONE, 0, POS_FACEUP_ATTACK));
        move(state, 22222, new LocInfo(1, LOCATION_HAND, 0, 0),
                new LocInfo(1, LOCATION_SZONE, 2, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.Swap(
                11111, new LocInfo(0, LOCATION_SZONE, 0, POS_FACEUP_ATTACK),
                22222, new LocInfo(1, LOCATION_SZONE, 2, POS_FACEUP_ATTACK)));

        assertEquals(22222, state.szone[0][0].code);
        assertEquals(0, state.szone[0][0].controller);
        assertEquals(0, state.szone[0][0].sequence);
        assertEquals(11111, state.szone[1][2].code);
        assertEquals(1, state.szone[1][2].controller);
        assertEquals(2, state.szone[1][2].sequence);
    }

    @Test
    void fieldDisabledSplitsTheMaskIntoPerPlayerHalves() {
        var state = newState();

        state.applyMessage(new DuelMessage.FieldDisabled(0x01080004));

        assertEquals(0x0004, state.disabledZones[0]);
        assertEquals(0x0108, state.disabledZones[1]);
    }

    @Test
    void moveToHandAppendsACardCarryingTheMessageCode() {
        var state = newState();

        move(state, 55555, new LocInfo(0, LOCATION_DECK, 39, 0),
                new LocInfo(0, LOCATION_HAND, 0, 0));

        assertEquals(List.of(55555), codesOf(state.hand[0]));
        assertEquals(39, state.deckCount(0));
    }
}

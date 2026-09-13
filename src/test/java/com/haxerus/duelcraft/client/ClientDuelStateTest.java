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
        var activatable = new DuelMessage.ActivatableCard(12345, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK, 100L, 0);
        var battleCmd = new DuelMessage.SelectBattleCmd(0, List.of(activatable), List.of(), true, true);

        state.applyMessage(battleCmd);

        var loc = new ClientDuelState.CardLocation(0, LOCATION_MZONE, 0);
        var actions = state.cardActions.get(loc);
        assertNotNull(actions);
        var activate = actions.stream()
                .filter(a -> a.actionType() == BattleAction.ACTIVATE)
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

    /** A material this client never tracked still has to land on the host, not vanish. */
    @Test
    void overlayAttachFromAnUnknownSourceStillReachesTheHost() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        // Source names an empty monster zone, so the card cannot be resolved.
        move(state, 22222, new LocInfo(1, LOCATION_MZONE, 4, POS_FACEUP_ATTACK),
                new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 0));

        var host = state.mzone[0][0];
        assertEquals(List.of(22222), codesOf(host.materials));
        assertEquals(0, host.materials.getFirst().sequence);
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

    // ---- Deck reveals and orientation (Task 14) ----

    /** duelclient.cpp:2862: the named card is at {@code size - 1 - offset}, counting from the top. */
    @Test
    void deckTopWritesTheCodeAtTheOffsetFromTheTop() {
        var state = newState();

        state.applyMessage(new DuelMessage.DeckTop(0, 2, 89631139, POS_FACEUP_DEFENSE));

        assertEquals(89631139, state.deck[0].get(37).code);
        assertEquals(POS_FACEUP_DEFENSE, state.deck[0].get(37).position);
        assertEquals(0, state.deck[0].get(39).code);
        assertEquals(40, state.deckCount(0));
    }

    @Test
    void deckTopOutsideTheDeckIsIgnored() {
        var state = newState();

        state.applyMessage(new DuelMessage.DeckTop(0, 40, 89631139, POS_FACEUP_DEFENSE));

        assertTrue(state.deck[0].stream().allMatch(card -> card.code == 0));
    }

    /** duelclient.cpp:2852: one global flag, not one per player. */
    @Test
    void reverseDeckTogglesTheGlobalFlag() {
        var state = newState();
        assertFalse(state.deckReversed);

        state.applyMessage(new DuelMessage.ReverseDeck());
        assertTrue(state.deckReversed);

        state.applyMessage(new DuelMessage.ReverseDeck());
        assertFalse(state.deckReversed);
    }

    /** duelclient.cpp:2691: a shuffle takes back every code and per-card reveal. */
    @Test
    void shuffleDeckZeroesEveryDeckCode() {
        var state = newState();
        state.applyMessage(new DuelMessage.DeckTop(0, 0, 89631139, POS_FACEUP_DEFENSE));

        state.applyMessage(new DuelMessage.ShuffleDeck(0));

        assertTrue(state.deck[0].stream().allMatch(card -> card.code == 0));
        assertEquals(POS_FACEDOWN_DEFENSE, state.deck[0].getLast().position);
        assertEquals(40, state.deckCount(0));
    }

    @Test
    void shuffleDeckLeavesTheOtherPlayersDeckAlone() {
        var state = newState();
        state.applyMessage(new DuelMessage.DeckTop(1, 0, 89631139, POS_FACEUP_DEFENSE));

        state.applyMessage(new DuelMessage.ShuffleDeck(0));

        assertEquals(89631139, state.deck[1].getLast().code);
    }

    /** duelclient.cpp:2513: the revealed codes are written onto the deck objects from the top down. */
    @Test
    void confirmDeckTopNamesThePileAndWritesTheCodes() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmDeckTop(0, List.of(
                new DuelMessage.ConfirmCard(89631139, 0, LOCATION_DECK, 39),
                new DuelMessage.ConfirmCard(46986414, 0, LOCATION_DECK, 38))));

        assertEquals("Your Deck (top)", state.confirmTitle);
        assertEquals(2, state.confirmCards.size());
        assertEquals(89631139, state.deck[0].get(39).code);
        assertEquals(46986414, state.deck[0].get(38).code);
    }

    /** duelclient.cpp:2548: same body, over the extra deck, counted from its back. */
    @Test
    void confirmExtraTopNamesTheExtraDeckAndWritesTheCodes() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmExtraTop(1, List.of(
                new DuelMessage.ConfirmCard(89631139, 1, LOCATION_EXTRA, 14))));

        assertEquals("Opponent's Extra Deck (top)", state.confirmTitle);
        assertEquals(1, state.confirmCards.size());
        assertEquals(89631139, state.extra[1].get(14).code);
    }

    @Test
    void confirmCardsNamesTheOwnerOfTheRevealedCards() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmCards(0, List.of(
                new DuelMessage.ConfirmCard(89631139, 1, LOCATION_HAND, 0))));

        assertEquals("Opponent's Revealed Cards", state.confirmTitle);
    }

    // ---- Pile restructuring (Task 14) ----

    /** duelclient.cpp:2811: the two piles trade places, then flagged cards leave for the extra deck. */
    @Test
    void swapGraveDeckExchangesThePilesAndRoutesFlaggedCardsToTheExtraDeck() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333);
        for (int code : new int[]{11111, 22222, 33333}) {
            move(state, code, new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_GRAVE, state.graveCount(0), POS_FACEUP_ATTACK));
        }

        // Bit 1: the second card of the new deck is an extra-deck monster.
        state.applyMessage(new DuelMessage.SwapGraveDeck(0, 15, new byte[]{0b0000_0010}));

        assertEquals(37, state.graveCount(0), "the old deck became the graveyard");
        assertEquals(List.of(11111, 33333), codesOf(state.deck[0]));
        assertEquals(16, state.extraCount(0));
        assertEquals(22222, state.extra[0].getLast().code);
        assertTrue(state.extra[0].getLast().isFaceDown());
        assertEquals(LOCATION_EXTRA, state.extra[0].getLast().location);
        assertEquals(LOCATION_GRAVE, state.grave[0].getFirst().location);
        assertEquals(LOCATION_DECK, state.deck[0].getFirst().location);
        assertEquals(List.of(0, 1), state.deck[0].stream().map(card -> card.sequence).toList());
    }

    /** Cards that came from the graveyard face-up have to turn over on their way into the deck. */
    @Test
    void swapGraveDeckTurnsTheNewDeckFaceDown() {
        var state = newState();
        draw(state, 0, 11111, 22222);
        for (int code : new int[]{11111, 22222}) {
            move(state, code, new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_GRAVE, state.graveCount(0), POS_FACEUP_ATTACK));
        }

        state.applyMessage(new DuelMessage.SwapGraveDeck(0, 15, new byte[]{0}));

        assertEquals(List.of(11111, 22222), codesOf(state.deck[0]));
        assertTrue(state.deck[0].stream().allMatch(ClientCard::isFaceDown),
                "every card of the new deck is face-down");
    }

    /** libduel.cpp:832 writes each revealed card's own sequence; that is what addresses the pile. */
    @Test
    void confirmDeckTopWritesCodesAtTheRevealedSequences() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmDeckTop(0, List.of(
                new DuelMessage.ConfirmCard(11111, 0, LOCATION_DECK, 39),
                new DuelMessage.ConfirmCard(22222, 0, LOCATION_DECK, 38))));

        assertEquals(11111, state.deck[0].get(39).code);
        assertEquals(22222, state.deck[0].get(38).code);
        assertEquals(0, state.deck[0].get(37).code);
    }

    /** ConfirmExtratop skips the pendulum cards at the end of the list, so it names other sequences. */
    @Test
    void confirmExtraTopWritesCodesAtTheRevealedSequences() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmExtraTop(0, List.of(
                new DuelMessage.ConfirmCard(33333, 0, LOCATION_EXTRA, 12))));

        assertEquals(33333, state.extra[0].get(12).code);
        assertEquals(0, state.extra[0].get(14).code);
    }

    @Test
    void revealOutsideThePileIsIgnored() {
        var state = newState();

        state.applyMessage(new DuelMessage.ConfirmDeckTop(0, List.of(
                new DuelMessage.ConfirmCard(11111, 0, LOCATION_DECK, 99))));

        assertEquals(40, state.deckCount(0));
        assertTrue(state.deck[0].stream().allMatch(card -> card.code == 0));
    }

    /** duelclient.cpp:4017 resolves every named card before deleting any of them. */
    @Test
    void removeCardsResolvesEverySequenceBeforeDeleting() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333);

        state.applyMessage(new DuelMessage.RemoveCards(List.of(
                new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_HAND, 1, 0))));

        assertEquals(List.of(33333), codesOf(state.hand[0]));
        assertEquals(0, state.hand[0].getFirst().sequence);
    }

    @Test
    void removeCardsRenumbersTheSurvivingMaterials() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333, 44444);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        for (int code : new int[]{22222, 33333, 44444}) {
            move(state, code, new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 0));
        }

        state.applyMessage(new DuelMessage.RemoveCards(List.of(
                new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 1))));

        var host = state.mzone[0][0];
        assertEquals(List.of(22222, 44444), codesOf(host.materials));
        assertEquals(List.of(0, 1), host.materials.stream().map(card -> card.sequence).toList());
    }

    /** duelclient.cpp:2881: every named card loses its code; only the second block re-places any. */
    @Test
    void shuffleSetCardZeroesTheCodesAndRePlacesTheNamedCards() {
        var state = newState();
        draw(state, 0, 11111, 22222, 33333);
        for (int seq = 0; seq < 3; seq++) {
            move(state, 11111 * (seq + 1), new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_SZONE, seq, POS_FACEDOWN_DEFENSE));
        }
        var first = state.szone[0][0];
        var second = state.szone[0][1];
        var third = state.szone[0][2];

        state.applyMessage(new DuelMessage.ShuffleSetCard(LOCATION_SZONE,
                List.of(new LocInfo(0, LOCATION_SZONE, 0, POS_FACEDOWN_DEFENSE),
                        new LocInfo(0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE)),
                // Only the first card carries materials, so only it names a new zone.
                List.of(new LocInfo(0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE),
                        new LocInfo(0, 0, 0, 0))));

        assertEquals(0, first.code);
        assertEquals(0, second.code);
        assertEquals(33333, third.code, "a card the message did not name keeps its code");
        assertSame(first, state.szone[0][1]);
        assertEquals(1, first.sequence);
        assertSame(second, state.szone[0][0]);
        assertEquals(0, second.sequence);
        assertSame(third, state.szone[0][2]);
    }

    // ---- Highlights and hints (Task 14) ----

    @Test
    void randomSelectedHighlightsTheNamedCardsUntilTheNextPrompt() {
        var state = newState();
        draw(state, 0, 11111, 22222);

        state.applyMessage(new DuelMessage.RandomSelected(0,
                List.of(new LocInfo(0, LOCATION_HAND, 1, POS_FACEDOWN_DEFENSE))));

        assertEquals(List.of(22222), codesOf(List.copyOf(state.highlighted)));

        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        assertTrue(state.highlighted.isEmpty());
    }

    @Test
    void becomeTargetHighlightsTheTargetedCardsUntilTheNextPrompt() {
        var state = newState();
        draw(state, 0, 11111, 22222);

        state.applyMessage(new DuelMessage.BecomeTarget(
                List.of(new LocInfo(0, LOCATION_HAND, 1, POS_FACEDOWN_DEFENSE))));

        assertEquals(List.of(22222), codesOf(List.copyOf(state.highlighted)));

        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        assertTrue(state.highlighted.isEmpty());
    }

    @Test
    void missedEffectHighlightsTheCardThatMissedItsTiming() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.MissedEffect(
                new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), 11111));

        assertEquals(List.of(state.mzone[0][2]), List.copyOf(state.highlighted));
    }

    /** duelclient.cpp:3999 refcounts the hints per player, dropping one at zero. */
    @Test
    void playerHintsAreRefcountedPerPlayer() {
        var state = newState();

        state.applyMessage(new DuelMessage.PlayerHint(1, PHINT_DESC_ADD, 1160L));
        state.applyMessage(new DuelMessage.PlayerHint(1, PHINT_DESC_ADD, 1160L));
        assertEquals(2, state.playerHints[1].get(1160L));
        assertTrue(state.playerHints[0].isEmpty());

        state.applyMessage(new DuelMessage.PlayerHint(1, PHINT_DESC_REMOVE, 1160L));
        assertEquals(1, state.playerHints[1].get(1160L));

        state.applyMessage(new DuelMessage.PlayerHint(1, PHINT_DESC_REMOVE, 1160L));
        assertTrue(state.playerHints[1].isEmpty());
    }

    @Test
    void matchKillStoresTheCardThatEndedTheMatch() {
        var state = newState();

        state.applyMessage(new DuelMessage.MatchKill(89631139));

        assertEquals(89631139, state.matchKillCode);
    }

    // ---- Hint surfaces (Task 16) ----

    /** duelclient.cpp:1412: HINT_SELECTMSG is held until the next prompt captions itself with it. */
    @Test
    void selectMsgHintCaptionsTheNextPromptAndIsThenSpent() {
        var state = newState();

        state.applyMessage(new DuelMessage.Hint(HINT_SELECTMSG, 0, 501L));
        assertEquals(0L, state.promptCaptionDesc, "the hint waits for a prompt to caption");

        state.applyMessage(new DuelMessage.SelectCard(0, false, 1, 1, List.of(
                new DuelMessage.CardInfo(11111, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK))));
        assertEquals(501L, state.promptCaptionDesc);

        state.applyMessage(new DuelMessage.SelectYesNo(0, 42L));
        assertEquals(0L, state.promptCaptionDesc, "one hint captions one prompt");
    }

    /** duelclient.cpp:3971: CHINT_DESC_ADD/REMOVE refcount per card, dropping the desc at zero. */
    @Test
    void cardDescHintsAreRefcountedPerCard() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK));
        var loc = new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK);

        state.applyMessage(new DuelMessage.CardHint(loc, CHINT_DESC_ADD, 1160L));
        state.applyMessage(new DuelMessage.CardHint(loc, CHINT_DESC_ADD, 1160L));
        assertEquals(2, state.mzone[0][1].descHints.get(1160L));

        state.applyMessage(new DuelMessage.CardHint(loc, CHINT_DESC_REMOVE, 1160L));
        assertEquals(1, state.mzone[0][1].descHints.get(1160L));

        state.applyMessage(new DuelMessage.CardHint(loc, CHINT_DESC_REMOVE, 1160L));
        assertTrue(state.mzone[0][1].descHints.isEmpty());
    }

    /** duelclient.cpp:3977: every other CHINT_* type lands in the single cHint/chValue slot. */
    @Test
    void cardTurnHintFillsTheSingleHintSlot() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.CardHint(
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), CHINT_TURN, 2L));

        assertEquals(CHINT_TURN, state.mzone[0][0].hintType);
        assertEquals(2L, state.mzone[0][0].hintValue);
    }

    /** duelclient.cpp:3105: the single hint slot is dropped by every non-overlay move. */
    @Test
    void cardHintSlotDoesNotFollowTheCardOutOfItsZone() {
        var state = newState();
        draw(state, 0, 11111);
        move(state, 11111, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        state.applyMessage(new DuelMessage.CardHint(
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), CHINT_TURN, 2L));
        var card = state.mzone[0][0];

        move(state, 11111, new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                new LocInfo(0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK));

        assertEquals(0, card.hintType);
        assertEquals(0L, card.hintValue);
    }

    @Test
    void opponentSelectionHintQueuesAToast() {
        var state = newState();

        state.applyMessage(new DuelMessage.Hint(HINT_OPSELECTED, 1, 42L));

        assertEquals(1, state.toasts.size());
        assertTrue(state.toasts.peek().contains("#42"), state.toasts.peek());
    }

    @Test
    void hintMessageWaitsAsAModal() {
        var state = newState();

        state.applyMessage(new DuelMessage.Hint(HINT_MESSAGE, 0, 7L));

        assertEquals("#7", state.pendingModal);
    }

    /** duelclient.cpp:1481 swaps the two halves when the hint is about the other side of the field. */
    @Test
    void zoneHintKeepsTheMaskViewerRelative() {
        var state = newState();

        state.applyMessage(new DuelMessage.Hint(HINT_ZONE, 0, 0x1L));
        assertEquals(0x1, state.zoneFlashMask);

        state.applyMessage(new DuelMessage.Hint(HINT_ZONE, 1, 0x1L));
        assertEquals(0x00010000, state.zoneFlashMask);
    }

    @Test
    void moveToHandAppendsACardCarryingTheMessageCode() {
        var state = newState();

        move(state, 55555, new LocInfo(0, LOCATION_DECK, 39, 0),
                new LocInfo(0, LOCATION_HAND, 0, 0));

        assertEquals(List.of(55555), codesOf(state.hand[0]));
        assertEquals(39, state.deckCount(0));
    }

    // ---- Chain links carry their trigger location and resolve state ----

    @Test
    void chainLinkKeepsTheTriggerLocationAndIsPoppedWhenSolved() {
        var state = newState();

        state.applyMessage(new DuelMessage.Chaining(12345,
                new LocInfo(0, LOCATION_HAND, 2, POS_FACEUP_ATTACK),
                1, LOCATION_MZONE, 3, 501L, 1));

        var link = state.chain.getFirst();
        assertEquals(1, link.trigController);
        assertEquals(LOCATION_MZONE, link.trigLocation);
        assertEquals(3, link.trigSequence);
        assertFalse(link.negated);
        assertFalse(link.solving);

        state.applyMessage(new DuelMessage.ChainSolving(1));
        assertTrue(state.chain.getFirst().solving);

        state.applyMessage(new DuelMessage.ChainSolved(1));
        assertTrue(state.chain.isEmpty());
    }

    @Test
    void chainNegationStampsTheLinkAndChainEndClearsThem() {
        var state = newState();
        state.applyMessage(new DuelMessage.Chaining(12345,
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                0, LOCATION_MZONE, 0, 0L, 1));

        state.applyMessage(new DuelMessage.ChainNegated(1));
        assertTrue(state.chain.getFirst().negated);

        state.applyMessage(new DuelMessage.ChainEnd());
        assertTrue(state.chain.isEmpty());
    }

    // ---- MSG_BATTLE writes combat stats that last until the damage step ends ----

    @Test
    void battleStatsOverrideBothCardsUntilTheDamageStepEnds() {
        var state = newState();
        move(state, 100, new LocInfo(0, LOCATION_DECK, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));
        move(state, 200, new LocInfo(1, LOCATION_DECK, 0, 0),
                new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.Battle(
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), 2500, 2100, 0,
                new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), 1800, 1200, 1));

        assertEquals(2500, state.cardAt(0, LOCATION_MZONE, 0).combatAttack);
        assertEquals(2100, state.cardAt(0, LOCATION_MZONE, 0).combatDefense);
        assertEquals(1800, state.cardAt(1, LOCATION_MZONE, 0).combatAttack);

        state.applyMessage(new DuelMessage.DamageStepEnd());

        assertNull(state.cardAt(0, LOCATION_MZONE, 0).combatAttack);
        assertNull(state.cardAt(1, LOCATION_MZONE, 0).combatAttack);
    }

    /**
     * A monster destroyed in battle is already in the graveyard when MSG_DAMAGE_STEP_END arrives
     * (processor.cpp:2720), and the same card object is what comes back on a revival, so the
     * override has to go on the way out of the zone rather than in the field sweep.
     */
    @Test
    void combatStatsLeaveWithACardDestroyedInBattle() {
        var state = newState();
        move(state, 200, new LocInfo(1, LOCATION_DECK, 0, 0),
                new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.Battle(
                new LocInfo(0, 0, 0, 0), 0, 0, 0,
                new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), 1800, 1200, 1));
        move(state, 200, new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                new LocInfo(1, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK));
        state.applyMessage(new DuelMessage.DamageStepEnd());
        move(state, 200, new LocInfo(1, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK),
                new LocInfo(1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        assertNull(state.cardAt(1, LOCATION_MZONE, 0).combatAttack);
        assertNull(state.cardAt(1, LOCATION_MZONE, 0).combatDefense);
    }

    /** A direct attack carries a zeroed target, and only the attacker gets combat stats. */
    @Test
    void attackRecordsTheArrowEndsForTheUI() {
        var state = newState();
        move(state, 100, new LocInfo(0, LOCATION_DECK, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK));

        state.applyMessage(new DuelMessage.Attack(
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), new LocInfo(0, 0, 0, 0)));

        assertNotNull(state.attack);
        assertEquals(0, state.attack.attacker().controller());
        assertNull(state.attack.target());
    }

    // ---- LP feedback: a signed floating number with a colour class per reason ----

    @Test
    void lpChangesPushAFloatingNumber() {
        var state = newState();

        state.applyMessage(new DuelMessage.Damage(0, 500));
        assertEquals("-500", state.lpDelta[0].text());
        assertEquals("lp-damage", state.lpDelta[0].styleClass());

        state.applyMessage(new DuelMessage.Recover(1, 300));
        assertEquals("+300", state.lpDelta[1].text());
        assertEquals("lp-recover", state.lpDelta[1].styleClass());

        // A number still on screen absorbs the next one, so a batch of LP messages reads as a total.
        state.applyMessage(new DuelMessage.PayLpCost(0, 800));
        assertEquals("-1300", state.lpDelta[0].text());
        assertEquals("lp-cost", state.lpDelta[0].styleClass());

        // LPUPDATE is silent (duelclient.cpp:3708 shows no number).
        state.applyMessage(new DuelMessage.LpUpdate(0, 1000));
        assertEquals("-1300", state.lpDelta[0].text());

        // The screen clears the slot when the number times out; the next change starts afresh.
        state.lpDelta[0] = null;
        state.applyMessage(new DuelMessage.Damage(0, 200));
        assertEquals("-200", state.lpDelta[0].text());
    }

    // ---- Turn and phase banners ----

    /** A turn change and the phases that follow it arrive together, so the banners queue up. */
    @Test
    void turnAndPhaseChangesQueueBanners() {
        var state = newState();

        state.applyMessage(new DuelMessage.NewTurn(1));
        state.applyMessage(new DuelMessage.NewPhase(PHASE_DRAW));
        state.applyMessage(new DuelMessage.NewPhase(PHASE_BATTLE_START));

        assertEquals(List.of("Turn 1 - Opponent's turn", "Draw", "Battle"),
                List.copyOf(state.banners));
        assertEquals("Battle", state.phaseName());
    }
}

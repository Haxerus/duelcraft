package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.RefreshSchedule.Refresh;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static com.haxerus.duelcraft.duel.RefreshSchedule.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The schedule is edopro's, from {@code generic_duel.cpp} {@code BeforeParsing} (:795-828) and
 * {@code AfterParsing} (:1143-1266); the masks are the defaults in {@code generic_duel.h:42-48}.
 */
class RefreshScheduleTest {

    private static Refresh loc(int player, int location, int flags) {
        return new Refresh(player, location, -1, flags);
    }

    private static Refresh single(int player, int location, int sequence) {
        return new Refresh(player, location, sequence, SINGLE_FLAGS);
    }

    private static List<Refresh> fieldOfBoth() {
        return List.of(loc(0, LOCATION_MZONE, MZONE_FLAGS), loc(1, LOCATION_MZONE, MZONE_FLAGS),
                loc(0, LOCATION_SZONE, SZONE_FLAGS), loc(1, LOCATION_SZONE, SZONE_FLAGS));
    }

    private static List<Refresh> handOfBoth() {
        return List.of(loc(0, LOCATION_HAND, HAND_FLAGS), loc(1, LOCATION_HAND, HAND_FLAGS));
    }

    private static List<Refresh> fieldAndHandOfBoth() {
        return Stream.concat(fieldOfBoth().stream(), handOfBoth().stream()).toList();
    }

    private static DuelMessage.SelectChain selectChain() {
        return new DuelMessage.SelectChain(0, 0, false, 0, 0, List.of());
    }

    // ---- masks ----

    @Test
    void masksMatchEdoprosDefaults() {
        assertEquals(0x3881fff, MZONE_FLAGS);
        assertEquals(0x3e81fff, SZONE_FLAGS);
        assertEquals(0x3781fff, HAND_FLAGS);
        assertEquals(0x381fff, PILE_FLAGS);
        assertEquals(0x3f81fff, SINGLE_FLAGS);
        assertEquals(0x3181fff, SET_CARD_FLAGS);
    }

    @Test
    void everyFieldMaskRequestsIsHiddenAndNoMaskRequestsAListField() {
        int listFields = QUERY_REASON_CARD | QUERY_EQUIP_CARD | QUERY_TARGET_CARD
                | QUERY_OVERLAY_CARD | QUERY_COUNTERS | QUERY_OWNER;
        for (int mask : new int[]{ MZONE_FLAGS, SZONE_FLAGS, HAND_FLAGS, SINGLE_FLAGS }) {
            assertNotEquals(0, mask & QUERY_IS_HIDDEN,
                    () -> "IS_HIDDEN missing from 0x" + Integer.toHexString(mask));
        }
        for (int mask : new int[]{ MZONE_FLAGS, SZONE_FLAGS, HAND_FLAGS, PILE_FLAGS, SINGLE_FLAGS }) {
            assertEquals(0, mask & listFields,
                    () -> "0x" + Integer.toHexString(mask) + " requests a list field");
        }
    }

    @Test
    void spellZonesAskForPendulumScalesAndMonsterZonesDoNot() {
        assertEquals(QUERY_LSCALE | QUERY_RSCALE, SZONE_FLAGS & (QUERY_LSCALE | QUERY_RSCALE));
        assertEquals(0, MZONE_FLAGS & (QUERY_LSCALE | QUERY_RSCALE));
    }

    // ---- duel start ----

    @Test
    void duelStartRefreshesBothExtraDecksAndNothingElse() {
        assertEquals(List.of(loc(0, LOCATION_EXTRA, PILE_FLAGS), loc(1, LOCATION_EXTRA, PILE_FLAGS)),
                atDuelStart());
    }

    // ---- before ----

    @Test
    void idleAndBattleCommandsRefreshFieldAndHandOfBothPlayers() {
        assertEquals(fieldAndHandOfBoth(), before(new DuelMessage.SelectIdleCmd(0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true, true, false)));
        assertEquals(fieldAndHandOfBoth(), before(new DuelMessage.SelectBattleCmd(1,
                List.of(), List.of(), true, true)));
    }

    @Test
    void chainPromptAndNewTurnRefreshTheFieldButNotTheHand() {
        assertEquals(fieldOfBoth(), before(selectChain()));
        assertEquals(fieldOfBoth(), before(new DuelMessage.NewTurn(1)));
    }

    @Test
    void flipSummoningRefreshesTheFlippedSlot() {
        assertEquals(List.of(single(1, LOCATION_MZONE, 3)),
                before(new DuelMessage.FlipSummoning(1234,
                        new LocInfo(1, LOCATION_MZONE, 3, POS_FACEUP_ATTACK))));
    }

    @Test
    void noOtherPromptIsPrecededByARefresh() {
        assertEquals(List.of(), before(new DuelMessage.SelectYesNo(0, 30)));
        assertEquals(List.of(), before(new DuelMessage.SelectPosition(0, 1234, POS_FACEUP_ATTACK)));
        assertEquals(List.of(), before(new DuelMessage.Draw(0, List.of())));
    }

    // ---- after ----

    @Test
    void drawAndShuffleHandRefreshThatPlayersHand() {
        assertEquals(List.of(loc(1, LOCATION_HAND, HAND_FLAGS)),
                after(new DuelMessage.Draw(1, List.of())));
        assertEquals(List.of(loc(0, LOCATION_HAND, HAND_FLAGS)),
                after(new DuelMessage.ShuffleHand(0, List.of())));
    }

    @Test
    void shuffleExtraRefreshesThatPlayersExtraDeck() {
        assertEquals(List.of(loc(1, LOCATION_EXTRA, PILE_FLAGS)), after(new DuelMessage.ShuffleExtra(1)));
    }

    @Test
    void swapGraveDeckRefreshesThatPlayersGraveyard() {
        assertEquals(List.of(loc(1, LOCATION_GRAVE, PILE_FLAGS)),
                after(new DuelMessage.SwapGraveDeck(1, 15, new byte[]{1})));
    }

    @Test
    void shuffleSetCardRefreshesTheNamedLocationForBothPlayers() {
        assertEquals(List.of(loc(0, LOCATION_SZONE, SET_CARD_FLAGS), loc(1, LOCATION_SZONE, SET_CARD_FLAGS)),
                after(new DuelMessage.ShuffleSetCard(LOCATION_SZONE, List.of(), List.of())));
        assertEquals(List.of(loc(0, LOCATION_MZONE, SET_CARD_FLAGS), loc(1, LOCATION_MZONE, SET_CARD_FLAGS)),
                after(new DuelMessage.ShuffleSetCard(LOCATION_MZONE, List.of(), List.of())));
    }

    @Test
    void newPhaseChainedAndChainEndRefreshFieldAndHand() {
        assertEquals(fieldAndHandOfBoth(), after(new DuelMessage.NewPhase(PHASE_MAIN1)));
        assertEquals(fieldAndHandOfBoth(), after(new DuelMessage.Chained(1)));
        assertEquals(fieldAndHandOfBoth(), after(new DuelMessage.ChainEnd()));
    }

    @Test
    void summonsAndChainSolvedRefreshTheFieldOnly() {
        assertEquals(fieldOfBoth(), after(new DuelMessage.Summoned()));
        assertEquals(fieldOfBoth(), after(new DuelMessage.SpSummoned()));
        assertEquals(fieldOfBoth(), after(new DuelMessage.FlipSummoned()));
        assertEquals(fieldOfBoth(), after(new DuelMessage.ChainSolved(1)));
    }

    @Test
    void damageStepsRefreshMonsterZonesOnly() {
        List<Refresh> expected = List.of(loc(0, LOCATION_MZONE, MZONE_FLAGS),
                loc(1, LOCATION_MZONE, MZONE_FLAGS));
        assertEquals(expected, after(new DuelMessage.DamageStepStart()));
        assertEquals(expected, after(new DuelMessage.DamageStepEnd()));
    }

    @Test
    void moveRefreshesTheDestinationWhenTheLocationOrControllerChanged() {
        assertEquals(List.of(single(0, LOCATION_MZONE, 2)),
                after(new DuelMessage.Move(1234, new LocInfo(0, LOCATION_HAND, 0, 0),
                        new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), 0)));
        assertEquals(List.of(single(1, LOCATION_MZONE, 2)),
                after(new DuelMessage.Move(1234, new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK),
                        new LocInfo(1, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), 0)));
    }

    @Test
    void moveWithinOneLocationOffTheFieldOrOntoAnOverlayRefreshesNothing() {
        // Same location and controller: a slot shuffle, no refresh.
        assertEquals(List.of(), after(new DuelMessage.Move(1234,
                new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK),
                new LocInfo(0, LOCATION_MZONE, 4, POS_FACEUP_ATTACK), 0)));
        // Destination location 0: the card left the field entirely.
        assertEquals(List.of(), after(new DuelMessage.Move(1234,
                new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), new LocInfo(0, 0, 0, 0), 0)));
        // Becoming an XYZ material is an overlay destination.
        assertEquals(List.of(), after(new DuelMessage.Move(1234,
                new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, 1), 0)));
    }

    @Test
    void posChangeRefreshesOnlyWhenTurningFaceUp() {
        assertEquals(List.of(single(0, LOCATION_MZONE, 1)),
                after(new DuelMessage.PosChange(1234, 0, LOCATION_MZONE, 1,
                        POS_FACEDOWN_DEFENSE, POS_FACEUP_ATTACK)));
        assertEquals(List.of(), after(new DuelMessage.PosChange(1234, 0, LOCATION_MZONE, 1,
                POS_FACEUP_ATTACK, POS_FACEDOWN_DEFENSE)));
        assertEquals(List.of(), after(new DuelMessage.PosChange(1234, 0, LOCATION_MZONE, 1,
                POS_FACEUP_ATTACK, POS_FACEUP_DEFENSE)));
    }

    @Test
    void swapRefreshesBothSlots() {
        assertEquals(List.of(single(0, LOCATION_MZONE, 1), single(1, LOCATION_MZONE, 3)),
                after(new DuelMessage.Swap(1, new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK),
                        2, new LocInfo(1, LOCATION_MZONE, 3, POS_FACEUP_ATTACK))));
    }

    @Test
    void theDeckIsNeverRefreshedAndUnscheduledMessagesRefreshNothing() {
        assertEquals(List.of(), after(new DuelMessage.ShuffleDeck(0)));
        assertEquals(List.of(), after(new DuelMessage.NewTurn(0)));
        assertEquals(List.of(), after(new DuelMessage.Hint(1, 0, 0)));
        assertEquals(List.of(), after(selectChain()));
        assertEquals(List.of(), before(new DuelMessage.Move(1, new LocInfo(0, LOCATION_HAND, 0, 0),
                new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), 0)));
    }
}

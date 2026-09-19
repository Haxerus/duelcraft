package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.DuelSettings;
import com.haxerus.duelcraft.duel.FirstTurnLobby.Hand;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.duel.PreparationView.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.haxerus.duelcraft.duel.PreparationResult.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelPreparationServiceTest {
    PreparationTestHost host;
    DuelPreparationService service;

    @BeforeEach
    void setup() { install(new PreparationTestHost()); }

    void install(PreparationTestHost testHost) {
        host = testHost;
        service = new DuelPreparationService(host, () -> 42L);
        host.service = service;
    }

    UUID invite() {
        assertEquals(OK, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 0).code());
        return service.view(host.bob, 0).flowId();
    }

    UUID accept() {
        UUID id = invite();
        assertEquals(OK, service.accept(host.bob, id, 1).code());
        return id;
    }

    UUID chooseFirst() {
        UUID id = accept();
        UUID round = service.view(host.alice, 1).roundId();
        assertEquals(OK, service.hand(host.alice, id, round, Hand.ROCK, 2).code());
        assertEquals(OK, service.hand(host.bob, id, round, Hand.SCISSORS, 3).code());
        return id;
    }

    void assertIdle() {
        for (var id : List.of(host.alice, host.bob)) {
            assertFalse(service.isPreparing(id));
            assertEquals(Mode.IDLE, service.view(id, 0).mode());
        }
    }

    @Test
    void invitationRemainsEditableAndAcceptanceLocksBothBeforeNotifications() {
        UUID id = invite();
        assertFalse(service.isPreparing(host.alice));
        assertFalse(service.isPreparing(host.bob));
        assertTrue(service.view(host.alice, 0).outgoing());
        assertFalse(service.view(host.bob, 0).outgoing());
        assertEquals("Alice", service.view(host.bob, 0).opponentName());
        assertEquals(host.alice, service.view(host.bob, 0).opponentId());
        assertEquals(42, service.view(host.bob, 0).settings().seed());
        assertEquals(OK, service.accept(host.bob, id, 1).code());
        assertTrue(service.isPreparing(host.alice));
        assertTrue(service.isPreparing(host.bob));
        assertEquals(STALE, service.accept(host.bob, id, 2).code());
    }

    @Test
    void targetCanSelectDeckAfterInvitationButSenderMustBeReady() {
        host.selections.put(host.bob, new DuelPreparationService.Selection(null, host.collectionService.emptyReport()));
        UUID id = invite();
        assertEquals(INELIGIBLE, service.accept(host.bob, id, 1).code());
        assertFalse(service.isPreparing(host.alice));
        host.selections.remove(host.bob);
        assertEquals(OK, service.accept(host.bob, id, 2).code());
        service.cancel(host.alice, id, 3);
        host.selections.put(host.alice, new DuelPreparationService.Selection(null, host.collectionService.emptyReport()));
        assertEquals(INELIGIBLE, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 4).code());
        assertIdle();
    }

    @Test
    void rejectsSelfOfflineBusyAndExistingInvitationWithoutReplacingIt() {
        assertEquals(INVALID, service.invite(host.alice, host.alice, DuelRule.MR5, null,
                PlayerOptions.standard(), 0).code());
        host.online.remove(host.bob);
        assertEquals(OFFLINE, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 0).code());
        host.online.add(host.bob);
        host.dueling.add(host.bob);
        assertEquals(BUSY, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 0).code());
        host.dueling.clear();
        UUID id = invite();
        assertEquals(BUSY, service.invite(host.bob, host.outsider, DuelRule.MR5, null,
                PlayerOptions.standard(), 1).code());
        assertEquals(BUSY, service.invite(host.outsider, host.alice, DuelRule.MR5, null,
                PlayerOptions.standard(), 1).code());
        assertEquals(id, service.view(host.alice, 1).flowId());
    }

    @ParameterizedTest
    @CsvSource({"0,5,1", "100000,5,1", "8000,-1,1", "8000,21,1", "8000,5,-1", "8000,5,11"})
    void rejectsOutOfRangeSettings(int lp, int hand, int draw) {
        var options = new PlayerOptions(lp, hand, draw);
        assertThrows(IllegalArgumentException.class, () -> new DuelSettings(DuelRule.MR5, 0, options));
        assertEquals(INVALID, service.invite(host.alice, host.bob, DuelRule.MR5, null, options, 0).code());
        assertIdle();
    }

    @ParameterizedTest
    @CsvSource({"1,0,0", "99999,20,10"})
    void acceptsInclusiveSettingsBoundsAndExplicitSeed(int lp, int hand, int draw) {
        var options = new PlayerOptions(lp, hand, draw);
        assertEquals(OK, service.invite(host.alice, host.bob, DuelRule.MR5, -7L, options, 0).code());
        assertEquals(new DuelSettings(DuelRule.MR5, -7, options), service.view(host.bob, 0).settings());
    }

    @Test
    void rejectsNullSettingsAndHandsWithoutChangingState() {
        assertEquals(INVALID, service.invite(host.alice, host.bob, null, null, PlayerOptions.standard(), 0).code());
        assertEquals(INVALID, service.invite(host.alice, host.bob, DuelRule.MR5, null, null, 0).code());
        UUID id = accept();
        var before = service.view(host.alice, 1);
        assertEquals(INVALID, service.hand(host.alice, id, before.roundId(), null, 2).code());
        assertEquals(before.revision(), service.view(host.alice, 2).revision());
    }

    @ParameterizedTest
    @CsvSource({"false,false,OK", "true,false,INELIGIBLE", "false,true,INELIGIBLE"})
    void withdrawalBeforeAcceptanceUsesActualCollectionPolicy(boolean required, boolean companion,
                                                           PreparationResult expected) {
        install(new PreparationTestHost(required, context -> companion && context.counts().isEmpty() ? "Needs cards" : null));
        UUID id = invite();
        var before = host.collections.get(host.alice);
        var change = host.collectionService.replaceCounts(before, before.revision(), service.isPreparing(host.alice),
                host.alice, Map.of());
        assertTrue(change.success());
        host.collections.put(host.alice, change.data());
        assertEquals(expected, service.accept(host.bob, id, 1).code());
        assertEquals(expected == OK, service.isPreparing(host.alice));
    }

    @Test
    void acceptanceRechecksCompanionPolicyInsteadOfCachingInviteApproval() {
        var denied = new AtomicBoolean();
        install(new PreparationTestHost(false, context -> denied.get() ? "Restricted" : null));
        UUID id = invite();
        denied.set(true);
        assertEquals(INELIGIBLE, service.accept(host.bob, id, 1).code());
        assertFalse(service.isPreparing(host.alice));
    }

    @Test
    void acceptanceCapturesEditedDeckAndImmutableSnapshot() {
        UUID id = invite();
        var original = host.deck(host.alice);
        var main = new ArrayList<>(original.cards().main());
        main.set(0, 2);
        var edited = new SavedDeck(original.id(), "Edited", new DeckList(main, List.of(), List.of()));
        var data = host.collections.get(host.alice);
        var saved = host.collectionService.save(data, data.revision(), false, host.alice, edited);
        assertTrue(saved.success());
        host.collections.put(host.alice, saved.data());
        assertEquals(OK, service.accept(host.bob, id, 1).code());
        main.clear();
        host.collections.put(host.alice, data);
        UUID round = service.view(host.alice, 1).roundId();
        service.hand(host.alice, id, round, Hand.ROCK, 2);
        service.hand(host.bob, id, round, Hand.SCISSORS, 3);
        assertEquals(OK, service.first(host.alice, id, false, 4).code());
        var snapshot = host.starts.getFirst().challenger().deck();
        assertEquals("Edited", snapshot.name());
        assertEquals(2, snapshot.cards().main().getFirst());
        assertEquals(40, snapshot.cards().main().size());
        assertNotSame(edited, snapshot);
        assertNotSame(edited.cards(), snapshot.cards());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.cards().main().clear());
    }

    @Test
    void privateHandsTieReplayAndChooserOnlyFirstChoice() {
        UUID id = accept();
        UUID round = service.view(host.alice, 1).roundId();
        assertEquals(OK, service.hand(host.alice, id, round, Hand.ROCK, 2).code());
        assertTrue(service.view(host.alice, 2).ownHandSubmitted());
        assertFalse(service.view(host.bob, 2).ownHandSubmitted());
        assertEquals(STALE, service.hand(host.alice, id, round, Hand.PAPER, 3).code());
        assertEquals(OK, service.hand(host.bob, id, round, Hand.ROCK, 4).code());
        UUID replay = service.view(host.alice, 4).roundId();
        assertNotEquals(round, replay);
        assertFalse(service.view(host.alice, 4).ownHandSubmitted());
        assertFalse(service.view(host.bob, 4).ownHandSubmitted());
        assertEquals(STALE, service.hand(host.bob, id, round, Hand.SCISSORS, 5).code());
        service.hand(host.alice, id, replay, Hand.PAPER, 6);
        service.hand(host.bob, id, replay, Hand.SCISSORS, 7);
        assertFalse(service.view(host.alice, 7).canChooseFirst());
        assertTrue(service.view(host.bob, 7).canChooseFirst());
        assertEquals(STALE, service.first(host.alice, id, true, 8).code());
        assertEquals(OK, service.first(host.bob, id, true, 9).code());
        assertEquals(1, host.firstSeat);
    }

    @ParameterizedTest
    @CsvSource({"true,0", "false,1"})
    void startupRetainsShuffleIdentityAndLocksDuringCallback(boolean goFirst, int expectedSeat) {
        UUID id = chooseFirst();
        host.duringStart = () -> {
            assertEquals(BUSY, service.cancel(host.alice, id, 4).code());
            service.expire(1000000);
            assertTrue(service.isPreparing(host.bob));
        };
        assertEquals(OK, service.first(host.alice, id, goFirst, 4).code());
        assertEquals(expectedSeat, host.firstSeat);
        var prepared = host.starts.getFirst();
        assertEquals(id, prepared.id());
        assertEquals(host.alice, prepared.challenger().id());
        assertEquals(host.bob, prepared.accepter().id());
        assertEquals(42, prepared.challenger().shuffleSeed());
        assertEquals(43, prepared.accepter().shuffleSeed());
        assertFalse(service.isPreparing(host.alice));
        assertEquals(Mode.DUEL, service.view(host.alice, 4).mode());
    }

    @Test
    void immediateCompletionIsSuccessfulAndNeverReleasesStartingInsideCallback() {
        UUID id = chooseFirst();
        host.completeImmediately = true;
        assertEquals(OK, service.first(host.alice, id, true, 4).code());
        assertIdle();
        assertFalse(host.notifications.get(host.alice).contains(START_FAILED));
    }

    @Test
    void completionNotificationAdvancesRevisionAfterOwnershipIsRemoved() {
        UUID id = chooseFirst();
        service.first(host.alice, id, true, 4);
        long revision = service.view(host.alice, 4).revision();
        host.dueling.clear();
        service.duelEnded(host.alice);
        assertEquals(revision + 1, service.view(host.alice, 5).revision());
        assertEquals(Mode.IDLE, service.view(host.alice, 5).mode());
        assertEquals(OK, host.notifications.get(host.alice).getLast());
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void startupFailureReleasesBothAndNotifiesTerminalError(boolean fail, boolean throwing) {
        UUID id = chooseFirst();
        host.failStart = fail;
        host.throwStart = throwing;
        assertEquals(START_FAILED, service.first(host.alice, id, true, 4).code());
        assertIdle();
        assertEquals(START_FAILED, host.notifications.get(host.alice).getLast());
        assertEquals(START_FAILED, host.notifications.get(host.bob).getLast());
    }

    @Test
    void offlineBeforeStartDoesNotCallHostAndReleasesBoth() {
        UUID id = chooseFirst();
        host.online.remove(host.bob);
        assertEquals(OFFLINE, service.first(host.alice, id, true, 4).code());
        assertTrue(host.starts.isEmpty());
        assertIdle();
    }

    @Test
    void acceptRechecksOnlineAndDuelBusyState() {
        UUID id = invite();
        host.online.remove(host.alice);
        assertEquals(OFFLINE, service.accept(host.bob, id, 1).code());
        assertIdle();
        host.online.add(host.alice);
        id = invite();
        host.dueling.add(host.alice);
        assertEquals(BUSY, service.accept(host.bob, id, 1).code());
        assertFalse(service.isPreparing(host.bob));
        assertEquals(Mode.IDLE, service.view(host.bob, 1).mode());
    }

    @Test
    void outsidersWrongTargetAndStaleIdsCannotMutateCurrentFlow() {
        UUID id = invite();
        assertEquals(STALE, service.accept(host.alice, id, 1).code());
        assertEquals(STALE, service.decline(host.alice, id, 1).code());
        assertEquals(STALE, service.cancel(host.outsider, id, 1).code());
        assertEquals(STALE, service.accept(host.outsider, id, 1).code());
        assertEquals(OK, service.decline(host.bob, id, 1).code());
        UUID fresh = accept();
        assertEquals(STALE, service.cancel(host.bob, id, 2).code());
        UUID round = service.view(host.bob, 2).roundId();
        assertEquals(STALE, service.hand(host.outsider, fresh, round, Hand.ROCK, 2).code());
        assertEquals(STALE, service.first(host.outsider, fresh, true, 2).code());
        assertEquals(fresh, service.view(host.bob, 2).flowId());
    }

    @Test
    void invitationExpiresExactlyAtSixtySecondsWithoutReadRefresh() {
        UUID id = invite();
        assertEquals(1, service.view(host.alice, 59999).remainingMillis());
        service.expire(59999);
        assertEquals(Mode.INVITED, service.view(host.bob, 59999).mode());
        assertEquals(STALE, service.accept(host.bob, id, 60000).code());
        assertIdle();
    }

    @Test
    void firstSubmittedHandDoesNotExtendRoundButTieAndWinnerStartFreshSteps() {
        UUID id = accept();
        UUID round = service.view(host.alice, 1).roundId();
        service.hand(host.alice, id, round, Hand.ROCK, 59999);
        assertEquals(2, service.view(host.bob, 59999).remainingMillis());
        service.hand(host.bob, id, round, Hand.ROCK, 60000);
        assertEquals(60000, service.view(host.bob, 60000).remainingMillis());
        round = service.view(host.bob, 60000).roundId();
        service.hand(host.alice, id, round, Hand.ROCK, 119998);
        service.hand(host.bob, id, round, Hand.SCISSORS, 119999);
        assertEquals(60000, service.view(host.alice, 119999).remainingMillis());
        assertEquals(STALE, service.first(host.bob, id, true, 179998).code());
        assertEquals(STALE, service.first(host.alice, id, true, 179999).code());
        assertIdle();
    }

    @Test
    void rpsExpiresExactlyAtSixtySeconds() {
        UUID id = accept();
        UUID round = service.view(host.alice, 1).roundId();
        service.expire(60000);
        assertTrue(service.isPreparing(host.alice));
        assertEquals(STALE, service.hand(host.alice, id, round, Hand.ROCK, 60001).code());
        assertIdle();
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"INVITED", "RPS", "FIRST_CHOICE"})
    void logoutAndCancelReleaseBothInEveryCancellableState(Mode mode) {
        UUID id = switch (mode) { case INVITED -> invite(); case RPS -> accept(); default -> chooseFirst(); };
        service.logout(host.bob);
        assertIdle();
        assertEquals(OFFLINE, host.notifications.get(host.alice).getLast());
        assertEquals(STALE, service.cancel(host.alice, id, 4).code());
        id = switch (mode) { case INVITED -> invite(); case RPS -> accept(); default -> chooseFirst(); };
        assertEquals(OK, service.cancel(host.bob, id, 4).code());
        assertIdle();
    }

    @Test
    void revisionsAdvanceOnTransitionsAndRemainStableOnReadsAndRejectedActions() {
        assertEquals(0, service.view(host.alice, 0).revision());
        UUID id = invite();
        assertEquals(1, service.view(host.alice, 0).revision());
        assertEquals(1, service.view(host.bob, 100).revision());
        assertEquals(STALE, service.accept(host.alice, id, 100).code());
        assertEquals(1, service.view(host.alice, 100).revision());
        service.accept(host.bob, id, 101);
        assertEquals(2, service.view(host.alice, 101).revision());
        UUID round = service.view(host.alice, 101).roundId();
        service.hand(host.alice, id, round, Hand.ROCK, 102);
        assertEquals(3, service.view(host.alice, 102).revision());
        service.cancel(host.bob, id, 103);
        assertEquals(4, service.view(host.alice, 103).revision());
        assertEquals(4, service.view(host.bob, 103).revision());
        var idle = service.view(host.bob, 103);
        assertNull(idle.flowId());
        assertNull(idle.roundId());
        assertNull(idle.opponentId());
        assertNull(idle.settings());
        assertFalse(idle.ownHandSubmitted());
        assertFalse(idle.canChooseFirst());
        assertEquals(0, idle.remainingMillis());
    }
    @Test
    void logoutDuringStartupKeepsLocksUntilCallbackReturnsThenReleasesBoth() {
        UUID id = chooseFirst();
        host.failStart = true;
        host.duringStart = () -> {
            host.online.remove(host.bob);
            service.logout(host.bob);
            assertTrue(service.isPreparing(host.alice));
            assertTrue(service.isPreparing(host.bob));
        };
        assertEquals(OFFLINE, service.first(host.alice, id, true, 4).code());
        assertIdle();
        assertEquals(OFFLINE, host.notifications.get(host.alice).getLast());
    }

    @Test
    void throwingCompanionCannotAuthorizeInvitationOrAcceptance() {
        var throwing = new AtomicBoolean();
        install(new PreparationTestHost(false, context -> {
            if (throwing.get()) throw new IllegalStateException("Injected policy failure");
            return null;
        }));
        UUID id = invite();
        throwing.set(true);
        assertEquals(INELIGIBLE, service.accept(host.bob, id, 1).code());
        assertFalse(service.isPreparing(host.alice));
        service.cancel(host.bob, id, 2);
        assertEquals(INELIGIBLE, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 3).code());
        assertIdle();
    }

    @Test
    void expiredInvitationIdCannotAcceptDeclineOrCancelReplacement() {
        UUID old = invite();
        service.expire(60000);
        assertEquals(OK, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 60001).code());
        var fresh = service.view(host.bob, 60001);
        assertEquals(STALE, service.accept(host.bob, old, 60002).code());
        assertEquals(STALE, service.decline(host.bob, old, 60002).code());
        assertEquals(STALE, service.cancel(host.alice, old, 60002).code());
        assertEquals(fresh.revision(), service.view(host.bob, 60002).revision());
        assertEquals(fresh.flowId(), service.view(host.bob, 60002).flowId());
    }

    @Test
    void viewRejectsUnboundedValuesAndInconsistentModeFields() {
        var settings = new DuelSettings(DuelRule.MR5, 42, PlayerOptions.standard());
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                -1, Mode.IDLE, null, null, null, "", false, false, false, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.INVITED, UUID.randomUUID(), null, host.bob, "B".repeat(129), true, false, false, 1, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.INVITED, UUID.randomUUID(), null, host.bob, "Bob", true, false, false, 60001, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.INVITED, UUID.randomUUID(), null, host.bob, "Bob", true, false, false, -1, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.RPS, UUID.randomUUID(), null, host.bob, "Bob", true, false, false, 1, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.IDLE, UUID.randomUUID(), null, null, "", false, false, false, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.INVITED, UUID.randomUUID(), null, host.bob, "Bob", true, false, true, 1, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.FIRST_CHOICE, UUID.randomUUID(), null, null, "Bob", true, false, true, 1, settings));
        assertThrows(IllegalArgumentException.class, () -> new PreparationView(
                0, Mode.STARTING, UUID.randomUUID(), null, host.bob, "Bob", true, true, false, 1, settings));
    }

    @Test
    void remainingTimeStaysBoundedIfClockMovesBackwards() {
        assertEquals(OK, service.invite(host.alice, host.bob, DuelRule.MR5, null,
                PlayerOptions.standard(), 100).code());
        assertEquals(60000, service.view(host.bob, 0).remainingMillis());
    }
    @Test
    void notificationViewReadsDoNotExpireAnotherFlowTwice() {
        invite();
        UUID fourth = UUID.randomUUID();
        host.online.add(fourth);
        host.collections.put(fourth, host.collections.get(host.bob));
        assertEquals(OK, service.invite(host.outsider, fourth, DuelRule.MR5, null,
                PlayerOptions.standard(), 0).code());
        host.duringChanged = () -> service.view(host.outsider, 60000);
        service.expire(60000);
        assertEquals(2, service.view(host.alice, 60000).revision());
        assertEquals(2, service.view(host.outsider, 60000).revision());
        assertEquals(List.of(OK, STALE), host.notifications.get(host.alice));
        assertEquals(List.of(OK, STALE), host.notifications.get(host.outsider));
    }

    @Test
    void acceptedSaveAndWithdrawalPreserveExactStateUntilCancel() {
        UUID flow = accept();
        var before = host.collections.get(host.alice);
        var edited = new SavedDeck(host.deck(host.alice).id(), "Changed", host.deck(host.alice).cards());
        var save = host.collectionService.save(before, before.revision(), service.isPreparing(host.alice), host.alice, edited);
        var withdraw = host.collectionService.replaceCounts(before, before.revision(), service.isPreparing(host.alice), host.alice, Map.of());
        assertEquals(com.haxerus.duelcraft.collection.CollectionError.BUSY, save.error());
        assertEquals(com.haxerus.duelcraft.collection.CollectionError.BUSY, withdraw.error());
        assertEquals(before, save.data());
        assertEquals(before, withdraw.data());
        service.cancel(host.alice, flow, 2);
        assertTrue(host.collectionService.save(before, before.revision(), service.isPreparing(host.alice), host.alice, edited).success());
        assertTrue(host.collectionService.replaceCounts(before, before.revision(), service.isPreparing(host.alice), host.alice, Map.of()).success());
    }

    @Test
    void duplicateAcceptAndFirstRequestsProduceExactlyOneStart() {
        UUID flow = accept();
        assertEquals(STALE, service.accept(host.bob, flow, 2).code());
        var round = service.view(host.alice, 2).roundId();
        service.hand(host.alice, flow, round, Hand.ROCK, 3);
        service.hand(host.bob, flow, round, Hand.SCISSORS, 4);
        assertEquals(OK, service.first(host.alice, flow, true, 5).code());
        assertEquals(STALE, service.first(host.alice, flow, true, 6).code());
        assertEquals(1, host.starts.size());
    }

    @Test
    void delayedHandAndOldDeadlineCannotMutateReplacementFlow() {
        UUID old = accept();
        UUID oldRound = service.view(host.alice, 1).roundId();
        service.cancel(host.alice, old, 2);
        assertEquals(OK, service.invite(host.alice, host.bob, DuelRule.MR5, 8L, PlayerOptions.standard(), 50000).code());
        var fresh = service.view(host.alice, 50000);
        assertEquals(STALE, service.hand(host.alice, old, oldRound, Hand.ROCK, 50001).code());
        service.expire(60001);
        assertEquals(fresh.flowId(), service.view(host.alice, 60001).flowId());
        assertEquals(fresh.revision(), service.view(host.alice, 60001).revision());
        assertTrue(host.starts.isEmpty());
    }
}

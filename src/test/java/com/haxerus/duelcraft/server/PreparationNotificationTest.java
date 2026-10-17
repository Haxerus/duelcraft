package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.FirstTurnLobby;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreparationNotificationTest {
    private static final long NOW = System.currentTimeMillis();

    @Test
    void opponentSubmittingDoesNotRepeatRpsPromptAndStillPublishesPrivateViews() {
        for (boolean aFirst : List.of(true, false)) {
            var fixture = new DuelStartPolicyTest.Fixture(false);
            var preparation = fixture.manager.preparation();
            assertEquals(PreparationResult.OK, preparation.invite(fixture.a, fixture.b, DuelRule.MR5, 42L,
                    PlayerOptions.standard(), NOW).code());
            UUID flow = preparation.view(fixture.a, NOW).flowId();
            assertEquals(PreparationResult.OK, preparation.accept(fixture.b, flow, NOW).code());
            UUID round = preparation.view(fixture.a, NOW).roundId();

            UUID first = aFirst ? fixture.a : fixture.b;
            UUID second = aFirst ? fixture.b : fixture.a;
            assertEquals(PreparationResult.OK,
                    preparation.hand(first, flow, round, FirstTurnLobby.Hand.ROCK, NOW).code());

            for (UUID owner : List.of(fixture.a, fixture.b)) {
                var states = fixture.statesByOwner.get(owner);
                assertEquals(3, states.size(), "invitation, RPS and first-hand views for " + owner);
                assertEquals(owner.equals(first), states.get(2).view().ownHandSubmitted());
                assertEquals(PreparationView.Mode.RPS, states.get(2).view().mode());
            }

            assertEquals(PreparationResult.OK,
                    preparation.hand(second, flow, round, FirstTurnLobby.Hand.SCISSORS, NOW).code());
            UUID winner = aFirst ? fixture.a : fixture.b;
            for (UUID owner : List.of(fixture.a, fixture.b)) {
                var states = fixture.statesByOwner.get(owner);
                assertEquals(4, states.size(), "every accepted transition publishes to " + owner);
                assertEquals(PreparationView.Mode.FIRST_CHOICE, states.get(3).view().mode());
                assertEquals(owner.equals(winner), states.get(3).view().canChooseFirst());
                assertEquals(1, count(fixture, owner, "Rock-paper-scissors:"));
                assertEquals(owner.equals(winner) ? 1 : 0, count(fixture, owner, "You won the roll."));
            }
        }
    }

    @Test
    void tieAnnouncesEachRoundOnceAndKeepsTheSameFlow() {
        var fixture = new DuelStartPolicyTest.Fixture(false);
        var preparation = fixture.manager.preparation();
        preparation.invite(fixture.a, fixture.b, DuelRule.MR5, 42L, PlayerOptions.standard(), NOW);
        UUID flow = preparation.view(fixture.a, NOW).flowId();
        preparation.accept(fixture.b, flow, NOW);
        UUID firstRound = preparation.view(fixture.a, NOW).roundId();

        preparation.hand(fixture.a, flow, firstRound, FirstTurnLobby.Hand.ROCK, NOW);
        preparation.hand(fixture.b, flow, firstRound, FirstTurnLobby.Hand.ROCK, NOW);
        UUID secondRound = preparation.view(fixture.a, NOW).roundId();

        assertNotEquals(firstRound, secondRound);
        for (UUID owner : List.of(fixture.a, fixture.b)) {
            var view = preparation.view(owner, NOW);
            assertEquals(flow, view.flowId());
            assertEquals(secondRound, view.roundId());
            assertFalse(view.ownHandSubmitted());
            assertEquals(2, count(fixture, owner, "Rock-paper-scissors:"));
        }
    }

    @Test
    void invitationAndWinnerAnnouncementsAreOncePerActionableStage() {
        var fixture = new DuelStartPolicyTest.Fixture(false);
        fixture.ready();

        assertEquals(1, count(fixture, fixture.b, "Alice challenged you."));
        assertEquals(0, count(fixture, fixture.a, "Alice challenged you."));
        assertEquals(1, count(fixture, fixture.a, "You won the roll."));
        assertEquals(0, count(fixture, fixture.b, "You won the roll."));
    }

    private static long count(DuelStartPolicyTest.Fixture fixture, UUID owner, String prefix) {
        return fixture.messages.getOrDefault(owner, List.of()).stream().filter(text -> text.startsWith(prefix)).count();
    }
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.DuelSettings;
import com.haxerus.duelcraft.duel.FirstTurnLobby;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.duel.PreparationView.Mode;
import com.haxerus.duelcraft.server.collection.DeckUsePolicy;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import static com.haxerus.duelcraft.duel.PreparationResult.*;

/** Authoritative preparation on the server thread; invitations permit editing until acceptance. */
public final class DuelPreparationService {
    private static final long STEP_MILLIS = 60_000;

    public record Selection(@Nullable SavedDeck deck, DeckEligibility.Report eligibility) {}
    public record PreparedPlayer(UUID id, SavedDeck deck, long shuffleSeed) {
        public PreparedPlayer {
            var cards = deck.cards();
            deck = new SavedDeck(deck.id(), deck.name(), new DeckList(cards.main(), cards.extra(), cards.side()));
        }
    }
    public record PreparedDuel(UUID id, PreparedPlayer challenger, PreparedPlayer accepter, DuelSettings settings) {}
    public record Result(PreparationResult code) {}

    public interface Host {
        boolean online(UUID id);
        String name(UUID id);
        boolean isDueling(UUID id);
        /** Evaluates the current active list through the shared collection policy. */
        Selection selection(UUID id, DuelRule rule);
        /** Returns true after installing live ownership, or after a successful immediately completed duel. */
        boolean start(PreparedDuel prepared, int firstSeat);
        void changed(UUID id, PreparationResult result);
    }

    private static final class Flow {
        final UUID id = UUID.randomUUID();
        final UUID challenger;
        final UUID accepter;
        final DuelSettings settings;
        Mode mode = Mode.INVITED;
        long deadline;
        UUID roundId;
        FirstTurnLobby.Hand challengerHand;
        FirstTurnLobby.Hand accepterHand;
        int winner;
        PreparedDuel prepared;
        boolean loggedOutDuringStart;

        Flow(UUID challenger, UUID accepter, DuelSettings settings, long now) {
            this.challenger = challenger;
            this.accepter = accepter;
            this.settings = settings;
            deadline = now + STEP_MILLIS;
        }

        void newRound(long now) {
            mode = Mode.RPS;
            roundId = UUID.randomUUID();
            challengerHand = null;
            accepterHand = null;
            deadline = now + STEP_MILLIS;
        }
    }

    private final Host host;
    private final LongSupplier nextSeed;
    private final Map<UUID, Flow> flows = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();

    public DuelPreparationService(Host host, LongSupplier nextSeed) {
        this.host = host;
        this.nextSeed = nextSeed;
    }

    public Result invite(UUID sender, UUID target, DuelRule rule, @Nullable Long seed, PlayerOptions options, long now) {
        expire(now);
        if (sender == null || target == null || sender.equals(target)) return new Result(INVALID);
        final DuelSettings settings;
        try {
            settings = new DuelSettings(rule, seed == null ? nextSeed.getAsLong() : seed, options);
        } catch (IllegalArgumentException exception) {
            return new Result(INVALID);
        }
        if (!host.online(sender) || !host.online(target)) return new Result(OFFLINE);
        if (flows.containsKey(sender) || flows.containsKey(target) || host.isDueling(sender) || host.isDueling(target)) {
            return new Result(BUSY);
        }
        if (!eligible(selection(sender, rule))) return new Result(INELIGIBLE);
        var flow = new Flow(sender, target, settings, now);
        flows.put(sender, flow);
        flows.put(target, flow);
        changed(flow, OK);
        return new Result(OK);
    }

    public Result accept(UUID actor, UUID invitationId, long now) {
        var flow = current(actor, invitationId, now);
        if (flow == null || flow.mode != Mode.INVITED || !flow.accepter.equals(actor)) return new Result(STALE);
        var availability = availability(flow);
        if (availability != OK) return finish(flow, availability);
        var challenger = selection(flow.challenger, flow.settings.rule());
        var accepter = selection(flow.accepter, flow.settings.rule());
        if (!eligible(challenger) || !eligible(accepter)) return new Result(INELIGIBLE);
        flow.prepared = new PreparedDuel(flow.id,
                new PreparedPlayer(flow.challenger, challenger.deck(), flow.settings.seed()),
                new PreparedPlayer(flow.accepter, accepter.deck(), flow.settings.seed() + 1), flow.settings);
        flow.newRound(now);
        changed(flow, OK);
        return new Result(OK);
    }

    public Result decline(UUID actor, UUID invitationId, long now) {
        var flow = current(actor, invitationId, now);
        if (flow == null || flow.mode != Mode.INVITED || !flow.accepter.equals(actor)) return new Result(STALE);
        return finish(flow, OK);
    }

    public Result cancel(UUID actor, UUID flowId, long now) {
        var flow = current(actor, flowId, now);
        if (flow == null) return new Result(STALE);
        if (flow.mode == Mode.STARTING) return new Result(BUSY);
        return finish(flow, OK);
    }

    public Result hand(UUID actor, UUID flowId, UUID roundId, FirstTurnLobby.Hand hand, long now) {
        var flow = current(actor, flowId, now);
        if (flow == null || flow.mode != Mode.RPS || !flow.roundId.equals(roundId)) return new Result(STALE);
        if (hand == null) return new Result(INVALID);
        if (actor.equals(flow.challenger)) {
            if (flow.challengerHand != null) return new Result(STALE);
            flow.challengerHand = hand;
        } else {
            if (flow.accepterHand != null) return new Result(STALE);
            flow.accepterHand = hand;
        }
        if (flow.challengerHand != null && flow.accepterHand != null) {
            flow.winner = FirstTurnLobby.resolve(flow.challengerHand, flow.accepterHand);
            if (flow.winner == FirstTurnLobby.TIE) flow.newRound(now);
            else {
                flow.mode = Mode.FIRST_CHOICE;
                flow.roundId = null;
                flow.deadline = now + STEP_MILLIS;
            }
        }
        changed(flow, OK);
        return new Result(OK);
    }

    public Result first(UUID actor, UUID flowId, boolean goFirst, long now) {
        var flow = current(actor, flowId, now);
        if (flow == null || flow.mode != Mode.FIRST_CHOICE
                || !(flow.winner == 0 ? flow.challenger : flow.accepter).equals(actor)) return new Result(STALE);
        var availability = availability(flow);
        if (availability != OK) return finish(flow, availability);
        flow.mode = Mode.STARTING;
        changed(flow, OK);
        boolean started;
        try {
            started = host.start(flow.prepared, goFirst ? flow.winner : 1 - flow.winner);
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Duel preparation {} failed to start", flow.id, exception);
            started = false;
        }
        return finish(flow, flow.loggedOutDuringStart ? OFFLINE : started ? OK : START_FAILED);
    }

    public void expire(long now) {
        for (var flow : new HashSet<>(flows.values())) {
            if (flows.get(flow.challenger) == flow && flow.mode != Mode.STARTING && now >= flow.deadline) {
                finish(flow, STALE);
            }
        }
    }

    public void logout(UUID actor) {
        var flow = flows.get(actor);
        if (flow == null) return;
        // A synchronous startup owns the locks until its callback returns, even on disconnect.
        if (flow.mode == Mode.STARTING) flow.loggedOutDuringStart = true;
        else finish(flow, OFFLINE);
    }

    /** Call for each human after live ownership is removed; also safe during synchronous startup completion. */
    public void duelEnded(UUID actor) {
        advance(actor);
        host.changed(actor, OK);
    }

    public boolean isPreparing(UUID actor) {
        var flow = flows.get(actor);
        return flow != null && flow.mode != Mode.INVITED;
    }

    public PreparationView view(UUID viewer, long now) {
        expire(now);
        long revision = revisions.getOrDefault(viewer, 0L);
        var flow = flows.get(viewer);
        if (flow == null) {
            return new PreparationView(revision, host.isDueling(viewer) ? Mode.DUEL : Mode.IDLE,
                    null, null, null, "", false, false, false, 0, null);
        }
        boolean challenger = flow.challenger.equals(viewer);
        UUID opponent = challenger ? flow.accepter : flow.challenger;
        return new PreparationView(revision, flow.mode, flow.id, flow.roundId, opponent, host.name(opponent),
                challenger, (challenger ? flow.challengerHand : flow.accepterHand) != null,
                flow.mode == Mode.FIRST_CHOICE && flow.winner == (challenger ? 0 : 1),
                flow.mode == Mode.STARTING ? 0 : Math.clamp(flow.deadline - now, 0, STEP_MILLIS), flow.settings);
    }

    private @Nullable Flow current(UUID actor, UUID id, long now) {
        expire(now);
        var flow = flows.get(actor);
        return flow != null && flow.id.equals(id) ? flow : null;
    }

    private PreparationResult availability(Flow flow) {
        if (!host.online(flow.challenger) || !host.online(flow.accepter)) return OFFLINE;
        return host.isDueling(flow.challenger) || host.isDueling(flow.accepter) ? BUSY : OK;
    }

    private @Nullable Selection selection(UUID actor, DuelRule rule) {
        try {
            return host.selection(actor, rule);
        } catch (DeckUsePolicy.EvaluationException exception) {
            return null; // The policy logs the failure; it cannot authorize preparation.
        }
    }

    private static boolean eligible(@Nullable Selection selection) {
        return selection != null && selection.deck() != null && selection.eligibility().eligible();
    }

    private Result finish(Flow flow, PreparationResult result) {
        flows.remove(flow.challenger, flow);
        flows.remove(flow.accepter, flow);
        changed(flow, result);
        return new Result(result);
    }

    private void changed(Flow flow, PreparationResult result) {
        advance(flow.challenger);
        advance(flow.accepter);
        host.changed(flow.challenger, result);
        host.changed(flow.accepter, result);
    }

    private void advance(UUID actor) {
        revisions.compute(actor, (id, revision) -> revision == null ? 1 : Math.incrementExact(revision));
    }
}

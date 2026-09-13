package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * When to re-query the engine and with which fields, transcribed from edopro's host:
 * {@code generic_duel.cpp} {@code BeforeParsing} (:795-828) and {@code AfterParsing} (:1143-1266),
 * with the per-location masks its {@code Refresh*} helpers default to ({@code generic_duel.h:42-48}).
 *
 * <p>edopro refreshes a location when something could have changed it, not after every engine pause.
 * The deck is never sent over the network and {@code LOCATION_REMOVED} is never refreshed at all:
 * both piles are tracked from {@code MSG_MOVE} alone. Overlays, counters, equips and targets are
 * likewise message-tracked, which is why no mask here asks for them.
 *
 * <p>Pure and Minecraft-free: {@link DuelSession} turns each {@link Refresh} into a native query.
 */
public final class RefreshSchedule {

    /** The 13 base stats every mask carries: {@code QUERY_CODE} through {@code QUERY_REASON}. */
    private static final int BASE_STATS = 0x1fff;

    public static final int MZONE_FLAGS  = BASE_STATS | QUERY_STATUS | QUERY_LINK | QUERY_IS_HIDDEN | QUERY_COVER;
    public static final int SZONE_FLAGS  = MZONE_FLAGS | QUERY_LSCALE | QUERY_RSCALE;
    public static final int HAND_FLAGS   = BASE_STATS | QUERY_STATUS | QUERY_IS_PUBLIC | QUERY_LSCALE
            | QUERY_RSCALE | QUERY_IS_HIDDEN | QUERY_COVER;
    /** Graveyard and extra deck ({@code RefreshGrave}, {@code RefreshExtra}). */
    public static final int PILE_FLAGS   = BASE_STATS | QUERY_STATUS | QUERY_IS_PUBLIC | QUERY_LSCALE;
    public static final int SINGLE_FLAGS = BASE_STATS | QUERY_STATUS | QUERY_IS_PUBLIC | QUERY_LSCALE
            | QUERY_RSCALE | QUERY_LINK | QUERY_IS_HIDDEN | QUERY_COVER;

    /** One query to run. {@code sequence} is -1 for a whole location, otherwise a single slot. */
    public record Refresh(int player, int location, int sequence, int flags) {
        public boolean isWholeLocation() { return sequence < 0; }
    }

    private RefreshSchedule() {}

    /** Extra decks only; the opening hands arrive as {@code MSG_DRAW} ({@code generic_duel.cpp:719-720}). */
    public static List<Refresh> atDuelStart() {
        return List.of(location(0, LOCATION_EXTRA, PILE_FLAGS), location(1, LOCATION_EXTRA, PILE_FLAGS));
    }

    /** Refreshes to emit before {@code msg} reaches the players. */
    public static List<Refresh> before(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.SelectIdleCmd ignored -> fieldAndHand();
            case DuelMessage.SelectBattleCmd ignored -> fieldAndHand();
            case DuelMessage.SelectChain ignored -> field();
            case DuelMessage.NewTurn ignored -> field();
            case DuelMessage.FlipSummoning flip -> List.of(single(flip.location()));
            default -> List.of();
        };
    }

    /** Refreshes to emit after {@code msg} has reached the players. */
    public static List<Refresh> after(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.Draw draw -> List.of(location(draw.player(), LOCATION_HAND, HAND_FLAGS));
            case DuelMessage.ShuffleHand sh -> List.of(location(sh.player(), LOCATION_HAND, HAND_FLAGS));
            case DuelMessage.ShuffleExtra se -> List.of(location(se.player(), LOCATION_EXTRA, PILE_FLAGS));

            case DuelMessage.NewPhase ignored -> fieldAndHand();
            case DuelMessage.Chained ignored -> fieldAndHand();
            case DuelMessage.ChainEnd ignored -> fieldAndHand();
            case DuelMessage.Summoned ignored -> field();
            case DuelMessage.SpSummoned ignored -> field();
            case DuelMessage.FlipSummoned ignored -> field();
            case DuelMessage.ChainSolved ignored -> field();
            case DuelMessage.DamageStepStart ignored -> monsterZones();
            case DuelMessage.DamageStepEnd ignored -> monsterZones();

            case DuelMessage.Move move -> {
                LocInfo to = move.to();
                boolean moved = to.location() != move.from().location()
                        || to.controller() != move.from().controller();
                yield to.location() != 0 && (to.location() & LOCATION_OVERLAY) == 0 && moved
                        ? List.of(single(to)) : List.of();
            }
            case DuelMessage.PosChange change -> (change.prevPosition() & POS_FACEDOWN) != 0
                    && (change.newPosition() & POS_FACEUP) != 0
                    ? List.of(new Refresh(change.controller(), change.location(), change.sequence(), SINGLE_FLAGS))
                    : List.of();
            case DuelMessage.Swap swap -> List.of(single(swap.loc1()), single(swap.loc2()));

            default -> List.of();
        };
    }

    private static List<Refresh> monsterZones() {
        return List.of(location(0, LOCATION_MZONE, MZONE_FLAGS), location(1, LOCATION_MZONE, MZONE_FLAGS));
    }

    private static List<Refresh> field() {
        return List.of(location(0, LOCATION_MZONE, MZONE_FLAGS), location(1, LOCATION_MZONE, MZONE_FLAGS),
                location(0, LOCATION_SZONE, SZONE_FLAGS), location(1, LOCATION_SZONE, SZONE_FLAGS));
    }

    private static List<Refresh> fieldAndHand() {
        return List.of(location(0, LOCATION_MZONE, MZONE_FLAGS), location(1, LOCATION_MZONE, MZONE_FLAGS),
                location(0, LOCATION_SZONE, SZONE_FLAGS), location(1, LOCATION_SZONE, SZONE_FLAGS),
                location(0, LOCATION_HAND, HAND_FLAGS), location(1, LOCATION_HAND, HAND_FLAGS));
    }

    private static Refresh location(int player, int location, int flags) {
        return new Refresh(player, location, -1, flags);
    }

    private static Refresh single(LocInfo info) {
        return new Refresh(info.controller(), info.location(), info.sequence(), SINGLE_FLAGS);
    }
}

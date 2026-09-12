package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.QueriedCard;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Host-side hidden-information policy, ported from edopro's host: who may see a message
 * ({@link #recipientsOf}) and what a given recipient may see of it ({@link #forRecipient}).
 * Line references are edopro's {@code gframe/generic_duel.cpp} and {@code gframe/core_utils.cpp}.
 *
 * <p>The 21 prompt records are routed to their own {@code player()} by the duel handlers, so
 * {@link #recipientsOf} has no case for them; their payloads are still sanitised here.
 */
public final class MessageSanitizer {

    /** Private query fields: omitted for a recipient who may not know the card ({@code core_utils.cpp:224-232}). */
    private static final int PRIVATE_QUERY_FLAGS =
            QUERY_CODE | QUERY_ALIAS | QUERY_TYPE | QUERY_LEVEL | QUERY_RANK | QUERY_ATTRIBUTE
            | QUERY_RACE | QUERY_ATTACK | QUERY_DEFENSE | QUERY_BASE_ATTACK | QUERY_BASE_DEFENSE
            | QUERY_STATUS | QUERY_LSCALE | QUERY_RSCALE | QUERY_LINK;

    private MessageSanitizer() {}

    /** Which of the two players a message may reach. */
    public record Recipients(Kind kind, int player) {
        public enum Kind { BOTH, ONLY, ALL_EXCEPT }

        public static final Recipients BOTH = new Recipients(Kind.BOTH, -1);

        public static Recipients only(int player) { return new Recipients(Kind.ONLY, player); }

        public static Recipients allExcept(int player) { return new Recipients(Kind.ALL_EXCEPT, player); }

        public boolean includes(int recipient) {
            return switch (kind) {
                case BOTH -> true;
                case ONLY -> recipient == player;
                case ALL_EXCEPT -> recipient != player;
            };
        }
    }

    /** Routing for a broadcast message; anything without a rule reaches both players. */
    public static Recipients recipientsOf(DuelMessage msg) {
        return switch (msg) {
            // generic_duel.cpp:843-880
            case DuelMessage.Hint hint -> switch (hint.hintType()) {
                case 1, 2, 3, 5 -> Recipients.only(hint.player());
                case 4, 6, 7, 8, 9, 11 -> Recipients.allExcept(hint.player());
                default -> Recipients.BOTH;
            };
            // generic_duel.cpp:985-1005: a peek at the deck or extra deck is private.
            case DuelMessage.ConfirmCards confirm -> {
                int location = confirm.cards().isEmpty() ? 0 : confirm.cards().getFirst().location();
                yield location == LOCATION_DECK || location == LOCATION_EXTRA
                        ? Recipients.only(confirm.player())
                        : Recipients.BOTH;
            }
            default -> Recipients.BOTH;
        };
    }

    /** The copy of {@code msg} that {@code recipient} may see; the same instance when nothing is hidden. */
    public static DuelMessage forRecipient(DuelMessage msg, int recipient) {
        return switch (msg) {
            // generic_duel.cpp:1080-1084: a face-up draw (reversed deck) stays visible.
            case DuelMessage.Draw draw -> {
                if (draw.player() == recipient) yield draw;
                yield new DuelMessage.Draw(draw.player(), draw.cards().stream()
                        .map(card -> (card.position() & POS_FACEUP) != 0
                                ? card
                                : new DuelMessage.DrawnCard(0, card.position()))
                        .toList());
            }
            // generic_duel.cpp:1032-1033. send_to forces POS_FACEUP for every destination except
            // REMOVED (operations.cpp:327-328), so the destination location carries the secrecy.
            case DuelMessage.Move move -> {
                int location = move.to().location();
                int position = move.to().position();
                boolean secret = (location & (LOCATION_GRAVE | LOCATION_OVERLAY)) == 0
                        && ((location & (LOCATION_DECK | LOCATION_HAND)) != 0
                            || (position & POS_FACEDOWN) != 0);
                if (move.to().controller() != recipient && secret)
                    yield new DuelMessage.Move(0, move.from(), move.to(), move.reason());
                yield move;
            }
            // generic_duel.cpp:1013-1014
            case DuelMessage.ShuffleHand shuffle -> {
                if (shuffle.player() == recipient) yield shuffle;
                yield new DuelMessage.ShuffleHand(shuffle.player(),
                        shuffle.codes().stream().map(code -> 0).toList());
            }
            // Set cards are always face-down; edopro zeroes the code for the setter too
            // (generic_duel.cpp:1043) and relies on the refresh that follows.
            case DuelMessage.Set set -> {
                if (set.location().controller() == recipient) yield set;
                yield new DuelMessage.Set(0, set.location());
            }
            case DuelMessage.PosChange change -> {
                if (change.controller() == recipient || (change.newPosition() & POS_FACEDOWN) == 0)
                    yield change;
                yield new DuelMessage.PosChange(0, change.controller(), change.location(),
                        change.sequence(), change.prevPosition(), change.newPosition());
            }
            case DuelMessage.UpdateData update -> {
                if (update.player() == recipient) yield update;
                yield new DuelMessage.UpdateData(update.player(), update.location(),
                        update.cards().stream().map(MessageSanitizer::hideCard).toList());
            }
            case DuelMessage.UpdateCard update -> {
                if (update.player() == recipient) yield update;
                yield new DuelMessage.UpdateCard(update.player(), update.location(),
                        update.sequence(), hideCard(update.card()));
            }
            // generic_duel.cpp:933-977: candidates the prompted player does not control lose
            // their code, whoever the prompt is delivered to.
            case DuelMessage.SelectCard select -> new DuelMessage.SelectCard(select.player(),
                    select.cancelable(), select.min(), select.max(),
                    hideForeignCards(select.cards(), select.player()));
            case DuelMessage.SelectTribute select -> new DuelMessage.SelectTribute(select.player(),
                    select.cancelable(), select.min(), select.max(),
                    select.cards().stream()
                            .map(card -> card.controller() == select.player() ? card
                                    : new DuelMessage.TributeCard(0, card.controller(), card.location(),
                                            card.sequence(), card.tributeCount()))
                            .toList());
            case DuelMessage.SelectUnselectCard select -> new DuelMessage.SelectUnselectCard(
                    select.player(), select.finishable(), select.cancelable(),
                    select.min(), select.max(),
                    hideForeignCards(select.selectableCards(), select.player()),
                    hideForeignCards(select.unselectableCards(), select.player()));
            default -> msg;
        };
    }

    private static List<DuelMessage.CardInfo> hideForeignCards(List<DuelMessage.CardInfo> cards, int player) {
        return cards.stream()
                .map(card -> card.controller() == player ? card
                        : new DuelMessage.CardInfo(0, card.controller(), card.location(),
                                card.sequence(), card.position()))
                .toList();
    }

    /**
     * Drops the private fields of a card the recipient may not know, clearing their flag bits as
     * well so the recipient never sees a field advertised as present but zeroed
     * ({@code core_utils.cpp:144-160}). Position, reason, counters, overlays, equip and target
     * links survive: where a card is and what it carries is public.
     */
    private static QueriedCard hideCard(QueriedCard card) {
        if (card == null) return null;
        boolean known = ((card.flags & QUERY_IS_PUBLIC) != 0 && card.isPublic)
                || ((card.flags & QUERY_POSITION) != 0 && (card.position & POS_FACEUP) != 0);
        boolean hidden = (card.flags & QUERY_IS_HIDDEN) != 0 && card.isHidden;
        if (known && !hidden) return card;

        var stripped = new QueriedCard();
        stripped.flags = card.flags & ~PRIVATE_QUERY_FLAGS;
        stripped.position = card.position;
        stripped.reason = card.reason;
        stripped.owner = card.owner;
        stripped.isPublic = card.isPublic;
        stripped.isHidden = card.isHidden;
        stripped.cover = card.cover;
        stripped.overlayCards = card.overlayCards;
        stripped.counters = card.counters;
        stripped.reasonCard = card.reasonCard;
        stripped.equipCard = card.equipCard;
        stripped.targetCards = card.targetCards;
        return stripped;
    }
}

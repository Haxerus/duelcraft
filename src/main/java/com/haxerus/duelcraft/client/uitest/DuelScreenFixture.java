package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.FieldLayout;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.haxerus.duelcraft.server.DuelStartPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Synthetic duel state for UI scenarios: no server and no engine, only the messages a client
 * would receive. Card codes are a fixed list of passcodes; nothing here depends on the card
 * database or images being present.
 */
@OnlyIn(Dist.CLIENT)
public final class DuelScreenFixture {

    private static final List<Integer> CODES = List.of(
            89631139, 33750025, 28406301, 55415564,
            49238328, 39153655, 39153655, 70095154,
            11747708, 55144522, 24094653, 55144522,
            25259669, 25259669, 13039848, 55144522,
            31786629, 31786629, 43096270, 11091375,
            11091375, 11091375, 11091375, 69247929,
            69247929, 69247929, 69247929, 28406301);

    private DuelScreenFixture() {}

    /** Player 0 versus "Fixture", 8000 LP each, 40-card decks, 15-card extra decks, the rule's flags. */
    public static DuelStartPayload startPayload(DuelRule rule) {
        return new DuelStartPayload(0, "Fixture", 8000, 8000, 40, 15, rule.flags());
    }

    /**
     * Fills every zone type the rule has: a face-up monster in each main monster zone on both
     * sides, one in the viewer's first EMZ, a spell in S/T 1, a card in the left separate
     * pendulum zone, and one card in the graveyard. Ten cards are drawn per player first so the
     * hand never runs dry. Call after {@link LDLibDuelScreen#create} has run.
     */
    public static void populate(DuelRule rule) {
        FieldLayout layout = FieldLayout.fromFlags(rule.flags());

        for (int p = 0; p < 2; p++) {
            LDLibDuelScreen.applyMessage(new DuelMessage.Draw(p, CODES.subList(0, 10).stream()
                    .map(code -> new DuelMessage.DrawnCard(code, POS_FACEDOWN_DEFENSE))
                    .toList()));
        }

        int first = layout.columns() == 3 ? 1 : 0;
        int last = layout.columns() == 3 ? 3 : 4;
        for (int p = 0; p < 2; p++) {
            for (int seq = first; seq <= last; seq++) {
                moveFromHand(p, CODES.get(seq - first), LOCATION_MZONE, seq, POS_FACEUP_ATTACK);
            }
        }
        if (layout.emz()) {
            moveFromHand(0, CODES.get(5), LOCATION_MZONE, 5, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, CODES.get(20), LOCATION_SZONE, 1, POS_FACEUP_ATTACK);
        if (layout.pendulum() == PendulumMode.SEPARATE) {
            moveFromHand(0, CODES.get(21), LOCATION_SZONE, 6, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, CODES.get(27), LOCATION_GRAVE, 0, POS_FACEUP_ATTACK);
    }

    /**
     * Layers the card-object model onto a populated MR5 field: two XYZ materials under the monster
     * in zone 0, three counters on the monster in zone 1, a targeting highlight on zone 2 and a
     * disabled zone 3 — one of each thing {@code ClientCard} now tracks.
     */
    public static void populateCardModel() {
        // Attach two hand cards as materials of the monster in zone 0. The engine addresses a
        // material by its host zone | LOCATION_OVERLAY, so the destination location is 0x84.
        for (int i = 0; i < 2; i++) {
            LDLibDuelScreen.applyMessage(new DuelMessage.Move(CODES.get(22 + i),
                    new LocInfo(0, LOCATION_HAND, 0, 0),
                    new LocInfo(0, LOCATION_MZONE | LOCATION_OVERLAY, 0, i),
                    0));
        }
        LDLibDuelScreen.applyMessage(new DuelMessage.AddCounter(0x1, 0, LOCATION_MZONE, 1, 3));
        // A pendulum card in MR5's left pendulum zone (the shared S/T zone 0). Its scales reach the
        // client only as a query result, the way the spell-zone refresh mask delivers them
        // (RefreshSchedule.SZONE_FLAGS).
        moveFromHand(0, CODES.get(21), LOCATION_SZONE, 0, POS_FACEUP_ATTACK);
        LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(0, LOCATION_SZONE, 0, scales(1, 8)));
        LDLibDuelScreen.applyMessage(new DuelMessage.BecomeTarget(
                List.of(new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK))));
        // Low 16 bits are player 0's zones; bit 3 is their fourth monster zone.
        LDLibDuelScreen.applyMessage(new DuelMessage.FieldDisabled(1 << 3));
    }

    /**
     * Sends an idle command whose only entry makes the monster at {@code (player, MZONE, sequence)}
     * repositionable, so clicking that zone opens the context menu. Card codes follow
     * {@link #populate}'s five-column mapping, where monster zone N holds {@code CODES.get(N)}.
     */
    public static void promptRepositionOf(int player, int sequence) {
        int code = CODES.get(sequence);
        LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(player,
                List.of(), List.of(),
                List.of(new DuelMessage.ReposCard(code, player, LOCATION_MZONE, sequence)),
                List.of(), List.of(), List.of(),
                true, true, false));
    }

    /** A query result carrying nothing but a pair of pendulum scales. */
    private static QueriedCard scales(int lscale, int rscale) {
        var card = new QueriedCard();
        card.flags = QUERY_LSCALE | QUERY_RSCALE;
        card.lscale = lscale;
        card.rscale = rscale;
        return card;
    }

    /** Moves the first card of the player's hand to the given zone. */
    private static void moveFromHand(int player, int code, int location, int sequence, int position) {
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code,
                new LocInfo(player, LOCATION_HAND, 0, 0),
                new LocInfo(player, location, sequence, position),
                0));
    }
}

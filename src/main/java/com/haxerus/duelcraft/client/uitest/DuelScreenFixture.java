package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.FieldLayout;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.server.DuelStartPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Synthetic duel state for UI scenarios: no server and no engine, only the messages a client
 * would receive. Card codes come from the standard deck; nothing here depends on the card
 * database or images being present.
 */
@OnlyIn(Dist.CLIENT)
public final class DuelScreenFixture {

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
        List<Integer> codes = Deck.standard().main();

        for (int p = 0; p < 2; p++) {
            LDLibDuelScreen.applyMessage(new DuelMessage.Draw(p, codes.subList(0, 10)));
        }

        int first = layout.columns() == 3 ? 1 : 0;
        int last = layout.columns() == 3 ? 3 : 4;
        for (int p = 0; p < 2; p++) {
            for (int seq = first; seq <= last; seq++) {
                moveFromHand(p, codes.get(seq - first), LOCATION_MZONE, seq, POS_FACEUP_ATTACK);
            }
        }
        if (layout.emz()) {
            moveFromHand(0, codes.get(5), LOCATION_MZONE, 5, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, codes.get(30), LOCATION_SZONE, 1, POS_FACEUP_ATTACK);
        if (layout.pendulum() == PendulumMode.SEPARATE) {
            moveFromHand(0, codes.get(31), LOCATION_SZONE, 6, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, codes.get(44), LOCATION_GRAVE, 0, POS_FACEUP_ATTACK);
    }

    /**
     * Sends an idle command whose only entry makes the monster at {@code (player, MZONE, sequence)}
     * repositionable, so clicking that zone opens the context menu. Card codes follow
     * {@link #populate}'s five-column mapping, where monster zone N holds {@code main().get(N)}.
     */
    public static void promptRepositionOf(int player, int sequence) {
        int code = Deck.standard().main().get(sequence);
        LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(player,
                List.of(), List.of(),
                List.of(new DuelMessage.ReposCard(code, player, LOCATION_MZONE, sequence)),
                List.of(), List.of(), List.of(),
                true, true, false));
    }

    /** Moves the first card of the player's hand to the given zone. */
    private static void moveFromHand(int player, int code, int location, int sequence, int position) {
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code,
                new LocInfo(player, LOCATION_HAND, 0, 0),
                new LocInfo(player, location, sequence, position),
                0));
    }
}

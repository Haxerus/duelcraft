package com.haxerus.duelcraft.client;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * The floating panel that shows pile contents (graveyard, banished, extra deck)
 * or a list of confirmed/revealed cards. Holds its own lifecycle — open/close/refresh —
 * and reacts to PILE_COUNTS and CONFIRM dirty flags so that additions/removals
 * update live without the user reopening the panel.
 */
public class ZoneInspectorController {

    /** Hooks back into the host UI for rendering + click dispatch. */
    public interface Callbacks {
        void setCardImageBackground(UIElement elem, int code);
        void onCardClicked(int player, int location, int sequence, UIEvent event);
        void showCardInfo(int code);
        void hideCardInfo();
    }

    private final UI ui;
    private final ClientDuelState state;
    private final Callbacks callbacks;
    private final UIElement inspector;

    // Current inspection target — drives refresh() after pile mutations.
    private int inspectedPlayer = -1;
    private int inspectedLocation = -1;
    private String inspectedTitle;

    public ZoneInspectorController(UI ui, ClientDuelState state, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.callbacks = callbacks;
        this.inspector = byId("zone-inspector");
    }

    /** Bind pile-slot click handlers and the close button. Call once after construction. */
    public void wirePileClicks() {
        int plr = state.localPlayer;
        int opp = state.opponent();
        bindPileClick("plr-graveyard", "Your Graveyard", plr, LOCATION_GRAVE);
        bindPileClick("opp-graveyard", "Opponent's Graveyard", opp, LOCATION_GRAVE);
        bindPileClick("plr-banished", "Your Banished", plr, LOCATION_REMOVED);
        bindPileClick("opp-banished", "Opponent's Banished", opp, LOCATION_REMOVED);
        bindPileClick("plr-extra-deck", "Your Extra Deck", plr, LOCATION_EXTRA);

        var closeBtn = byId("zone-inspector-close");
        if (closeBtn instanceof Button btn) {
            btn.setOnClick(e -> close());
        }
    }

    /** Rebuild the inspector list from current state. Safe no-op when hidden. */
    public void refresh() {
        if (inspector == null || inspector.hasClass("hidden")) return;
        if (inspectedPlayer < 0 || inspectedLocation < 0) return;

        var titleLabel = byId("zone-inspector-title");
        var listElem = byId("zone-inspector-list", ScrollerView.class);
        if (titleLabel instanceof Label lbl) lbl.setText(Component.literal(inspectedTitle));
        if (listElem == null) return;

        int player = inspectedPlayer;
        int location = inspectedLocation;
        List<Integer> cards = getPileCards(player, location);

        listElem.clearAllScrollViewChildren();
        for (int i = 0; i < cards.size(); i++) {
            int code = cards.get(i);
            int seq = i;

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                callbacks.onCardClicked(player, location, seq, ev);
            });
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());

            listElem.addScrollViewChild(card);
        }
    }

    /** Display the reveal/confirm list (consumes state.confirmCards). */
    public void showConfirmCards() {
        if (state.confirmCards == null || state.confirmCards.isEmpty()) return;
        if (inspector == null) return;

        inspector.removeClass("hidden");
        var titleLabel = byId("zone-inspector-title");
        var listElem = byId("zone-inspector-list", ScrollerView.class);
        if (titleLabel instanceof Label lbl) lbl.setText(Component.literal(state.confirmTitle));

        if (listElem != null) {
            listElem.clearAllScrollViewChildren();
            for (var confirmCard : state.confirmCards) {
                int code = confirmCard.code();

                var card = new UIElement();
                card.addClass("card");
                callbacks.setCardImageBackground(card, code);
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());

                listElem.addScrollViewChild(card);
            }
        }

        state.confirmCards = null;
    }

    private void open(int player, int location, String title) {
        inspectedPlayer = player;
        inspectedLocation = location;
        inspectedTitle = title;
        if (inspector != null) inspector.removeClass("hidden");
        refresh();
    }

    private void close() {
        if (inspector != null) inspector.addClass("hidden");
        inspectedPlayer = -1;
        inspectedLocation = -1;
        inspectedTitle = null;
    }

    private void bindPileClick(String elementId, String title, int player, int location) {
        var elem = byId(elementId);
        if (elem != null) {
            elem.addEventListener(UIEvents.CLICK, e -> open(player, location, title));
        }
    }

    private List<Integer> getPileCards(int player, int location) {
        return switch (location) {
            case LOCATION_GRAVE -> state.grave[player];
            case LOCATION_REMOVED -> state.banished[player];
            case LOCATION_EXTRA -> state.extra[player];
            default -> List.of();
        };
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }

    private <T> T byId(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElse(null);
    }
}

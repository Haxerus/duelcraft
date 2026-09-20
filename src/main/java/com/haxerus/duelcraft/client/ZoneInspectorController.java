package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
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
 * The floating panel for pile contents (graveyard, banished, extra deck) and Xyz materials.
 * Owns open/close/refresh and reacts to pile or inspected-host mutations so additions/removals
 * update live without the user reopening the panel.
 */
public class ZoneInspectorController {

    /** Hooks back into the host UI for rendering + click dispatch. */
    public interface Callbacks {
        void setCardImageBackground(UIElement elem, int code);
        void onCardClicked(int player, int location, int sequence, UIEvent event);
        void showCardInfo(int code);
        void hideCardInfo();
        void onVisibilityChanged();
        void restoreScrollAfterContentLayout(float offset);
    }

    private final UI ui;
    private final ClientDuelState state;
    private final Callbacks callbacks;
    private final UIElement inspector;

    // Current inspection target — drives refresh() after pile mutations.
    private int inspectedPlayer = -1;
    private int inspectedLocation = -1;
    private String inspectedTitle;
    private ClientCard inspectedHost;

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
        boolean materials = inspectedHost != null;
        if (!materials && (inspectedPlayer < 0 || inspectedLocation < 0)) return;
        if (materials && !isHostOnField(inspectedHost)) {
            close();
            return;
        }

        var titleLabel = byId("zone-inspector-title");
        var listElem = byId("zone-inspector-list", ScrollerView.class);
        if (titleLabel instanceof Label lbl) {
            lbl.setText(materials
                    ? Component.translatable("duelcraft.duel.materials", inspectedHost.materials.size())
                    : Component.literal(inspectedTitle));
        }
        if (listElem == null) return;

        int player = inspectedPlayer;
        int location = inspectedLocation;
        List<ClientCard> cards = materials ? inspectedHost.materials : getPileCards(player, location);
        float scrollOffset = Math.max(0, -listElem.viewContainer.getTaffyLayout().location().y);

        listElem.clearAllScrollViewChildren();
        for (int i = 0; i < cards.size(); i++) {
            ClientCard inspectedCard = cards.get(i);
            int code = inspectedCard.code;
            int seq = i;

            var card = new UIElement();
            card.addClass("card");
            if (materials) card.addClass("material-card");
            else if (isActionable(player, location, seq)) card.addClass("selectable");
            if (code == 0) card.addClass("card-back");
            callbacks.setCardImageBackground(card, code);
            if (!materials) {
                card.addEventListener(UIEvents.CLICK, ev -> {
                    ev.stopPropagation();
                    callbacks.onCardClicked(player, location, seq, ev);
                });
            }
            if (code != 0) {
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            } else {
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.hideCardInfo());
            }
            if (!materials && location == LOCATION_REMOVED && inspectedCard.isFaceDown()) {
                card.addChild(new Label()
                        .setText(Component.translatable("duelcraft.duel.face_down"))
                        .addClass("face-down-marker"));
            }

            listElem.addScrollViewChild(card);
        }
        callbacks.restoreScrollAfterContentLayout(scrollOffset);
    }

    /** Open the read-only material list for the exact field card the user inspected. */
    public void openMaterials(ClientCard host) {
        inspectedPlayer = -1;
        inspectedLocation = -1;
        inspectedTitle = null;
        inspectedHost = host;
        if (inspector != null) inspector.removeClass("hidden");
        callbacks.onVisibilityChanged();
        refresh();
    }

    private void open(int player, int location, String title) {
        inspectedPlayer = player;
        inspectedLocation = location;
        inspectedTitle = title;
        inspectedHost = null;
        if (inspector != null) inspector.removeClass("hidden");
        callbacks.onVisibilityChanged();
        refresh();
    }

    private void close() {
        if (inspector != null) inspector.addClass("hidden");
        inspectedPlayer = -1;
        inspectedLocation = -1;
        inspectedTitle = null;
        inspectedHost = null;
        callbacks.onVisibilityChanged();
    }

    private void bindPileClick(String elementId, String title, int player, int location) {
        var elem = byId(elementId);
        if (elem != null) {
            elem.addEventListener(UIEvents.CLICK, e -> open(player, location, title));
        }
    }

    private List<ClientCard> getPileCards(int player, int location) {
        return switch (location) {
            case LOCATION_GRAVE -> state.grave[player];
            case LOCATION_REMOVED -> state.banished[player];
            case LOCATION_EXTRA -> state.extra[player];
            default -> List.of();
        };
    }

    private boolean isActionable(int player, int location, int sequence) {
        if (!(state.pendingPrompt instanceof DuelMessage.SelectIdleCmd
                || state.pendingPrompt instanceof DuelMessage.SelectBattleCmd)) return false;
        var actions = state.cardActions.get(new ClientDuelState.CardLocation(player, location, sequence));
        return actions != null && !actions.isEmpty();
    }

    private boolean isHostOnField(ClientCard host) {
        return (host.location == LOCATION_MZONE || host.location == LOCATION_SZONE)
                && state.cardAt(host.controller, host.location, host.sequence) == host;
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }

    private <T> T byId(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElse(null);
    }
}

package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.FieldLayout.Side;
import com.haxerus.duelcraft.client.FieldLayout.Zone;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Owns the field UI: monster zones, spell/trap zones, EMZ, pendulum zones, field spell,
 * piles, and the stat overlays drawn on monster cards. Reacts to dirty flags from
 * ClientDuelState via explicit refresh methods called from the tick loop.
 *
 * Every zone lookup goes through {@link FieldLayout}, which knows the rule set's geometry
 * and the slot each engine zone lives in. This class is the only place that converts
 * between absolute player indices (state, engine) and viewer-relative {@link Side}s (layout, XML).
 *
 * Decoupled from click routing via the {@link Callbacks} interface: slot click handlers are
 * wired here but forward the click decision back to the host, which also owns card image
 * async retry and the card info hover banner.
 */
public class FieldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hooks back into host UI for things not owned by FieldRenderer. */
    public interface Callbacks {
        void setCardImageBackground(UIElement elem, int code);
        void onCardClicked(int player, int location, int sequence, UIEvent event);
        void showCardInfo(int code);
        void hideCardInfo();
        /** Clears async-retry tracking for a slot that's no longer displaying a card. */
        void clearPendingImage(UIElement elem);
    }

    public static final String CARD_BACK_SPRITE = "sprite(duelcraft:textures/card_back.png)";

    private final UI ui;
    private final ClientDuelState state;
    private final FieldLayout layout;
    private final Callbacks callbacks;
    /** Superset slot id → element. Ids the XML lacks are absent. */
    private final Map<String, UIElement> slots = new HashMap<>();

    public FieldRenderer(UI ui, ClientDuelState state, FieldLayout layout, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.layout = layout;
        this.callbacks = callbacks;
        bindSlots();
    }

    private void bindSlots() {
        for (String id : FieldLayout.allSlotIds()) {
            UIElement el = ui.selectId(id).findFirst().orElse(null);
            if (el == null) {
                LOGGER.warn("duel_screen.xml has no slot #{}; treating it as hidden", id);
                continue;
            }
            slots.put(id, el);
        }
        for (String id : layout.hiddenSlotIds()) {
            UIElement el = slots.get(id);
            if (el != null) el.addClass("rule-hidden");
        }
        if (layout.pendulum() == PendulumMode.SHARED) {
            for (int seq : layout.pendulumSequences()) {
                for (Side side : Side.values()) {
                    slot(new Zone(side, LOCATION_SZONE, seq)).ifPresent(el -> el.addClass("pendulum"));
                }
            }
        }
    }

    // ── Player ↔ side conversion (the only place it happens) ────────────────

    private Side side(int player) {
        return player == state.localPlayer ? Side.PLR : Side.OPP;
    }

    private int player(Side side) {
        return side == Side.PLR ? state.localPlayer : state.opponent();
    }

    private Optional<UIElement> slot(Zone zone) {
        return layout.slotId(zone).map(slots::get);
    }

    private boolean occupied(Zone z) {
        return state.cardAt(player(z.side()), z.location(), z.sequence()) != null;
    }

    /** The zone a slot shows: the occupied candidate, else the viewer's own zone (for placement). */
    private Zone shownZone(List<Zone> candidates) {
        return candidates.stream().filter(this::occupied).findFirst().orElse(candidates.get(0));
    }

    // ── Click wiring ────────────────────────────────────────────────────────

    /** Bind click handlers on every visible monster and spell slot. Call once after construction. */
    public void wireFieldClicks() {
        for (String id : FieldLayout.allSlotIds()) {
            UIElement el = slots.get(id);
            List<Zone> zones = layout.zonesOf(id);
            if (el == null || zones.isEmpty()) continue;
            int location = zones.get(0).location();
            if (location != LOCATION_MZONE && location != LOCATION_SZONE) continue; // piles: ZoneInspectorController
            el.addEventListener(UIEvents.CLICK, e -> {
                Zone target = shownZone(zones);
                callbacks.onCardClicked(player(target.side()), target.location(), target.sequence(), e);
            });
        }
    }

    // ── Zone refresh ────────────────────────────────────────────────────────

    public void refreshMonsterZones(int player) {
        Side side = side(player);
        for (int seq = 0; seq <= 6; seq++) {
            refreshZone(new Zone(side, LOCATION_MZONE, seq));
        }
    }

    public void refreshSpellZones(int player) {
        Side side = side(player);
        for (int seq = 0; seq <= 7; seq++) {
            refreshZone(new Zone(side, LOCATION_SZONE, seq));
        }
    }

    /** Re-render the slot holding {@code zone}. Shared EMZ slots re-evaluate both owners. */
    private void refreshZone(Zone zone) {
        layout.slotId(zone).ifPresent(id -> {
            UIElement el = slots.get(id);
            if (el == null) return;
            Zone shown = shownZone(layout.zonesOf(id));
            int p = player(shown.side());
            refreshZoneSlot(el, state.cardAt(p, shown.location(), shown.sequence()),
                    p, shown.location(), shown.sequence());
        });
    }

    private void refreshZoneSlot(UIElement slot, ClientCard card, int player, int locationType, int sequence) {
        if (slot == null) return;
        slot.getChildren().stream()
                .filter(c -> c.hasClass("card") || c.hasClass("card-back")
                        || c.hasClass("stat-atk-def") || c.hasClass("stat-level")
                        || c.hasClass("card-materials") || c.hasClass("card-counters"))
                .toList()
                .forEach(slot::removeChild);

        slot.removeClass("targeted");
        slot.removeClass("disabled");
        if (isZoneDisabled(player, locationType, sequence)) slot.addClass("disabled");

        if (card != null) {
            int code = card.code;
            int position = card.position;
            boolean faceDown = code == 0 || card.isFaceDown();
            boolean defense = (position & (POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE)) != 0;

            var cardVisual = new UIElement();
            if (faceDown) {
                cardVisual.addClass("card-back");

                if (player == state.localPlayer && code != 0) {
                    int hoverCode = code;
                    cardVisual.addEventListener(UIEvents.MOUSE_ENTER, e -> callbacks.showCardInfo(hoverCode));
                    cardVisual.addEventListener(UIEvents.MOUSE_LEAVE, e -> callbacks.hideCardInfo());
                }
            } else {
                cardVisual.addClass("card");
                cardVisual.lss("height", "100%");
                callbacks.setCardImageBackground(cardVisual, code);

                int hoverCode = code;
                cardVisual.addEventListener(UIEvents.MOUSE_ENTER, e -> callbacks.showCardInfo(hoverCode));
                cardVisual.addEventListener(UIEvents.MOUSE_LEAVE, e -> callbacks.hideCardInfo());
            }

            if (defense && locationType == LOCATION_MZONE) {
                cardVisual.addClass("defense");
            }

            // EMZ slots live outside #opponent-side, so opponent's cards need manual 180° flip
            if (locationType == LOCATION_MZONE && (sequence == 5 || sequence == 6)
                    && player != state.localPlayer) {
                cardVisual.addClass("emz-opp");
            }

            slot.addChild(cardVisual);
            addBadges(slot, card);
            if (state.highlighted.contains(card) || !card.targetedBy.isEmpty()) slot.addClass("targeted");
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
        } else {
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
        }
    }

    /** Overlay material count (bottom-left) and total counters (top-left), when either is non-zero. */
    private void addBadges(UIElement slot, ClientCard card) {
        if (!card.materials.isEmpty()) {
            var badge = new Label();
            badge.addClass("card-materials");
            badge.setText(Component.literal("x" + card.materials.size()));
            slot.addChild(badge);
        }
        int counters = card.counterTotal();
        if (counters > 0) {
            var badge = new Label();
            badge.addClass("card-counters");
            badge.setText(Component.literal(String.valueOf(counters)));
            slot.addChild(badge);
        }
    }

    /** MSG_FIELD_DISABLED masks use the SelectPlace bit layout: monsters 0-6, spell/traps 8-15. */
    private boolean isZoneDisabled(int player, int location, int sequence) {
        int bit = sequence + (location == LOCATION_SZONE ? 8 : 0);
        return (state.disabledZones[player] & (1 << bit)) != 0;
    }

    // ── Stat overlays ──────────────────────────────────────────────────────

    public void refreshFieldStats() {
        for (Side side : Side.values()) {
            for (int seq = 0; seq <= 6; seq++) {
                layout.slotId(new Zone(side, LOCATION_MZONE, seq)).ifPresent(id -> {
                    UIElement el = slots.get(id);
                    if (el == null) return;
                    Zone shown = shownZone(layout.zonesOf(id));
                    int p = player(shown.side());
                    updateMonsterStats(el, state.cardAt(p, LOCATION_MZONE, shown.sequence()), p);
                });
            }
        }
    }

    private void updateMonsterStats(UIElement slot, ClientCard card, int player) {
        if (slot == null) return;

        // Remove old stat labels
        slot.getChildren().stream()
                .filter(c -> c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
                .toList()
                .forEach(slot::removeChild);

        if (card == null || card.code == 0 || card.stats == null) return;
        int code = card.code;
        QueriedCard stats = card.stats;

        boolean faceDown = (stats.position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
        if (faceDown) return;

        // Only show stats for monsters (check if QUERY_ATTACK was present in the flags)
        if ((stats.flags & QUERY_ATTACK) == 0) return;

        boolean isOpp = player != state.localPlayer;

        CardDatabase db = DuelcraftClient.getCardDatabase();
        CardInfo cardInfo = db != null ? db.getCard(code) : null;
        boolean isLink = cardInfo != null && cardInfo.isLink();

        // ATK/DEF label — bottom for own cards, top for opponent's (card visual is flipped 180°)
        // Link monsters have no DEF, so show ATK only.
        var atkDefLabel = new Label();
        atkDefLabel.addClass("stat-atk-def");
        if (isOpp) atkDefLabel.addClass("opp");
        String atkText = stats.attack == -2 ? "?" : String.valueOf(stats.attack);
        if (isLink) {
            atkDefLabel.setText(Component.literal(atkText));
        } else {
            String defText = stats.defense == -2 ? "?" : String.valueOf(stats.defense);
            atkDefLabel.setText(Component.literal(atkText + "/" + defText));
        }

        // Color based on buff/debuff (ATK takes priority)
        if (stats.baseAttack > 0 && stats.attack != stats.baseAttack) {
            if (stats.attack > stats.baseAttack) atkDefLabel.addClass("stat-buffed");
            else atkDefLabel.addClass("stat-debuffed");
        }
        slot.addChild(atkDefLabel);

        // Level/Rank label — top-right for own cards, bottom-right for opponent's
        if (cardInfo != null && cardInfo.isMonster() && !cardInfo.isLink()) {
            int originalLevel = cardInfo.levelOrRank();
            int currentLevel = stats.rank > 0 ? stats.rank : stats.level;
            if (currentLevel != originalLevel && currentLevel > 0) {
                var levelLabel = new Label();
                levelLabel.addClass("stat-level");
                if (isOpp) levelLabel.addClass("opp");
                boolean isXyz = cardInfo.isXyz();
                levelLabel.setText(Component.literal((isXyz ? "R" : "★") + currentLevel));
                if (currentLevel > originalLevel) levelLabel.addClass("stat-buffed");
                else if (currentLevel < originalLevel) levelLabel.addClass("stat-debuffed");
                slot.addChild(levelLabel);
            }
        }
    }

    // ── Piles ───────────────────────────────────────────────────────────────

    public void refreshPiles() {
        for (int p = 0; p < 2; p++) {
            final int player = p;
            Side side = side(player);
            // Deck: always face-down card back when non-empty
            slot(new Zone(side, LOCATION_DECK, 0)).ifPresent(el ->
                    setPileBackground(el, state.deckCount(player) > 0 ? CARD_BACK_SPRITE : null));
            // Extra deck: top face-up card if any, otherwise card back when non-empty
            slot(new Zone(side, LOCATION_EXTRA, 0)).ifPresent(el -> refreshExtraDeckPile(el, player));
            // Graveyard & Banished: top card image when non-empty
            slot(new Zone(side, LOCATION_GRAVE, 0)).ifPresent(el -> setPileTopCard(el, state.grave[player]));
            slot(new Zone(side, LOCATION_REMOVED, 0)).ifPresent(el -> setPileTopCard(el, state.banished[player]));
        }
    }

    private void refreshExtraDeckPile(UIElement slot, int player) {
        if (state.extra[player].isEmpty()) {
            setPileBackground(slot, null);
            return;
        }

        // Find the top-most face-up card (iterate from end)
        int topFaceUpCode = 0;
        var cards = state.extra[player];
        for (int i = cards.size() - 1; i >= 0; i--) {
            if ((cards.get(i).position & (POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE)) != 0) {
                topFaceUpCode = cards.get(i).code;
                break;
            }
        }

        if (topFaceUpCode != 0) {
            // Face-up card — use setCardImageBackground for async retry support
            callbacks.setCardImageBackground(slot, topFaceUpCode);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            // All face-down: static card back, clear any pending image retry
            slot.lss("background", CARD_BACK_SPRITE);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
            callbacks.clearPendingImage(slot);
        }
    }

    private void setPileBackground(UIElement slot, String background) {
        if (slot == null) return;
        if (background != null) {
            slot.lss("background", background);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            slot.removeClass("has-card");
        }
    }

    private void setPileTopCard(UIElement slot, List<ClientCard> cards) {
        if (slot == null) return;
        if (!cards.isEmpty()) {
            // Use setCardImageBackground so the pile is registered for async image retry
            callbacks.setCardImageBackground(slot, cards.getLast().code);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            slot.removeClass("has-card");
            callbacks.clearPendingImage(slot);
        }
    }

    // ── Highlighting ───────────────────────────────────────────────────────

    /**
     * Highlight valid placement zones for a SelectPlace/SelectDisfield prompt.
     * Bitmask is relative to the asking player (set bit = blocked zone); the asking player is the viewer.
     * Monster bits 0-6 and spell bits 8-15 of the viewer's block; the opponent's block starts at bit 16.
     * The two physical EMZ slots are each addressable from either block (bit 5/6 of the viewer's own
     * block, or the cross-mapped bit 21/22 of the opponent's block — see {@link FieldLayout#slotId}),
     * so both blocks check sequences 5 and 6, not just the viewer's.
     */
    public void highlightValidPlaces(int field) {
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
        for (Side side : Side.values()) {
            int base = side == Side.PLR ? 0 : 16;
            for (int seq = 0; seq <= 6; seq++) {
                if ((field & (1 << (base + seq))) == 0) {
                    slot(new Zone(side, LOCATION_MZONE, seq)).ifPresent(el -> el.addClass("target"));
                }
            }
            for (int seq = 0; seq <= 7; seq++) {
                if ((field & (1 << (base + 8 + seq))) == 0) {
                    slot(new Zone(side, LOCATION_SZONE, seq)).ifPresent(el -> el.addClass("target"));
                }
            }
        }
    }

    /** Refresh the .target highlight on field slots based on state.cardActions. */
    public void refreshTargetHighlights() {
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));

        for (var entry : state.cardActions.entrySet()) {
            var loc = entry.getKey();
            UIElement slot = findSlotForLocation(loc);
            if (slot != null) slot.addClass("target");
        }
    }

    /** Map an absolute (player, location, sequence) to the corresponding UI slot, or null. */
    public UIElement findSlotForLocation(ClientDuelState.CardLocation loc) {
        return slot(new Zone(side(loc.controller()), loc.location(), loc.sequence())).orElse(null);
    }

    /** Compute the bit position in the SelectPlace/SelectDisfield bitmask for a given zone. */
    public int getFieldBit(int player, int location, int sequence) {
        // Bitmask is relative: self=0, opponent=1. Map absolute player to bitmask position.
        int bitmaskPlayer = (player == state.localPlayer) ? 0 : 1;
        int offset = bitmaskPlayer * 16;
        if (location == LOCATION_SZONE) offset += 8;
        return 1 << (offset + sequence);
    }

    /**
     * Resolve a clicked field zone to the absolute (player, location, sequence) that should actually
     * be submitted for a SelectPlace/SelectDisfield response, honouring the shared EMZ slots: if the
     * clicked zone's own bit is blocked but it's an EMZ slot (MZONE 5/6), the same physical slot is
     * also addressable via the other player's cross-mapped sequence (see {@link FieldLayout#slotId}).
     * Returns null if the zone (and its EMZ alias, if any) is blocked.
     */
    public int[] resolvePlaceZone(int player, int location, int sequence, int field) {
        if ((field & getFieldBit(player, location, sequence)) == 0) {
            return new int[]{player, location, sequence};
        }
        if (location == LOCATION_MZONE && (sequence == 5 || sequence == 6)) {
            int otherPlayer = (player == state.localPlayer) ? state.opponent() : state.localPlayer;
            int otherSeq = sequence == 5 ? 6 : 5;
            if ((field & getFieldBit(otherPlayer, location, otherSeq)) == 0) {
                return new int[]{otherPlayer, location, otherSeq};
            }
        }
        return null;
    }
}

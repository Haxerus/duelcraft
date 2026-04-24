package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Owns the field UI: monster zones, spell/trap zones, EMZ, field spell, piles,
 * and the stat overlays drawn on top of monster cards. Reacts to dirty flags
 * from ClientDuelState via explicit refresh methods called from the tick loop.
 *
 * Decoupled from click routing via the {@link Callbacks} interface — slot
 * click handlers are wired here but forward the click decision back to the
 * host. Similarly delegates card image async retry (setCardImageBackground)
 * and the card info hover banner to the host.
 */
public class FieldRenderer {

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

    // Slot arrays — owned by FieldRenderer, bound in constructor.
    public final UIElement[][] monsterSlots = new UIElement[2][5];
    public final UIElement[][] spellSlots = new UIElement[2][5];
    public final UIElement[] emzSlots = new UIElement[2];
    public final UIElement[] fieldSpellSlots = new UIElement[2];
    public final UIElement[] deckSlots = new UIElement[2];
    public final UIElement[] graveSlots = new UIElement[2];
    public final UIElement[] extraSlots = new UIElement[2];
    public final UIElement[] banishedSlots = new UIElement[2];

    private final UI ui;
    private final ClientDuelState state;
    private final Callbacks callbacks;

    public FieldRenderer(UI ui, ClientDuelState state, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.callbacks = callbacks;
        bindSlotElements();
    }

    private void bindSlotElements() {
        int plr = state.localPlayer;
        int opp = state.opponent();
        for (int i = 0; i < 5; i++) {
            monsterSlots[opp][i] = byId("opp-mon-" + i);
            monsterSlots[plr][i] = byId("plr-mon-" + i);
            spellSlots[opp][i]   = byId("opp-st-" + i);
            spellSlots[plr][i]   = byId("plr-st-" + i);
        }
        emzSlots[0] = byId("emz-left");
        emzSlots[1] = byId("emz-right");
        fieldSpellSlots[opp] = byId("opp-field-spell");
        fieldSpellSlots[plr] = byId("plr-field-spell");
        deckSlots[opp]       = byId("opp-deck");
        deckSlots[plr]       = byId("plr-deck");
        graveSlots[opp]      = byId("opp-graveyard");
        graveSlots[plr]      = byId("plr-graveyard");
        extraSlots[opp]      = byId("opp-extra-deck");
        extraSlots[plr]      = byId("plr-extra-deck");
        banishedSlots[opp]   = byId("opp-banished");
        banishedSlots[plr]   = byId("plr-banished");
    }

    // ── Click wiring ────────────────────────────────────────────────────────

    /** Bind click handlers on all field slots. Call once after construction. */
    public void wireFieldClicks() {
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 5; i++) {
                int player = p;
                int seq = i;
                if (monsterSlots[p][i] != null) {
                    monsterSlots[p][i].addEventListener(UIEvents.CLICK,
                            e -> callbacks.onCardClicked(player, LOCATION_MZONE, seq, e));
                }
                if (spellSlots[p][i] != null) {
                    // S/T 0 and 4 share physical space with pendulum zones (seq 6/7).
                    // Send the pendulum seq only when a pendulum card is actually there;
                    // otherwise use the base S/T seq (needed for SelectPlace).
                    if (seq == 0) {
                        spellSlots[p][i].addEventListener(UIEvents.CLICK, e -> {
                            if (state.szone[player][0] != 0 || state.szonePos[player][0] != 0)
                                callbacks.onCardClicked(player, LOCATION_SZONE, 0, e);
                            else if (state.szone[player][6] != 0 || state.szonePos[player][6] != 0)
                                callbacks.onCardClicked(player, LOCATION_SZONE, 6, e);
                            else
                                callbacks.onCardClicked(player, LOCATION_SZONE, 0, e);
                        });
                    } else if (seq == 4) {
                        spellSlots[p][i].addEventListener(UIEvents.CLICK, e -> {
                            if (state.szone[player][4] != 0 || state.szonePos[player][4] != 0)
                                callbacks.onCardClicked(player, LOCATION_SZONE, 4, e);
                            else if (state.szone[player][7] != 0 || state.szonePos[player][7] != 0)
                                callbacks.onCardClicked(player, LOCATION_SZONE, 7, e);
                            else
                                callbacks.onCardClicked(player, LOCATION_SZONE, 4, e);
                        });
                    } else {
                        spellSlots[p][i].addEventListener(UIEvents.CLICK,
                                e -> callbacks.onCardClicked(player, LOCATION_SZONE, seq, e));
                    }
                }
            }

            // Extra Monster Zone — dynamically resolve occupant at click time.
            // Physical mapping is relative to the local player:
            //   emz-left (slot 0):  local seq 5 shares physical space with opp seq 6
            //   emz-right (slot 1): local seq 6 shares physical space with opp seq 5
            int slotIdx = p;
            if (emzSlots[p] != null) {
                emzSlots[p].addEventListener(UIEvents.CLICK, e -> {
                    int lp = state.localPlayer;
                    int opp = state.opponent();
                    int mySeq = slotIdx == 0 ? 5 : 6;
                    int oppSeq = slotIdx == 0 ? 6 : 5;
                    if (state.mzone[lp][mySeq] != 0 || state.mzonePos[lp][mySeq] != 0) {
                        callbacks.onCardClicked(lp, LOCATION_MZONE, mySeq, e);
                    } else if (state.mzone[opp][oppSeq] != 0 || state.mzonePos[opp][oppSeq] != 0) {
                        callbacks.onCardClicked(opp, LOCATION_MZONE, oppSeq, e);
                    } else {
                        // Empty — send local player's mapping for placement
                        callbacks.onCardClicked(lp, LOCATION_MZONE, mySeq, e);
                    }
                });
            }
        }
    }

    // ── Zone refresh ────────────────────────────────────────────────────────

    public void refreshMonsterZones(int player) {
        for (int i = 0; i < 5; i++) {
            var slot = monsterSlots[player][i];
            if (slot == null) continue;
            refreshZoneSlot(slot, state.mzone[player][i], state.mzonePos[player][i], player, LOCATION_MZONE, i);
        }

        // EMZ: physical slots are relative to local player's view.
        int lp = state.localPlayer;
        int opp = state.opponent();
        refreshEmzSlot(0, lp, 5, opp, 6);
        refreshEmzSlot(1, lp, 6, opp, 5);
    }

    /** Refresh a physical EMZ slot. It can be occupied by (p1,s1) or (p2,s2). */
    private void refreshEmzSlot(int slotIdx, int p1, int s1, int p2, int s2) {
        if (emzSlots[slotIdx] == null) return;
        if (state.mzone[p1][s1] != 0 || state.mzonePos[p1][s1] != 0) {
            refreshZoneSlot(emzSlots[slotIdx], state.mzone[p1][s1], state.mzonePos[p1][s1], p1, LOCATION_MZONE, s1);
        } else {
            refreshZoneSlot(emzSlots[slotIdx], state.mzone[p2][s2], state.mzonePos[p2][s2], p2, LOCATION_MZONE, s2);
        }
    }

    public void refreshSpellZones(int player) {
        for (int i = 0; i < 5; i++) {
            var slot = spellSlots[player][i];
            if (slot == null) continue;

            int code = state.szone[player][i];
            int pos = state.szonePos[player][i];

            // Pendulum zones (seq 6/7) share physical space with S/T 0 and 4
            if (i == 0 && code == 0 && pos == 0) {
                code = state.szone[player][6];
                pos = state.szonePos[player][6];
                refreshZoneSlot(slot, code, pos, player, LOCATION_SZONE, code != 0 || pos != 0 ? 6 : 0);
            } else if (i == 4 && code == 0 && pos == 0) {
                code = state.szone[player][7];
                pos = state.szonePos[player][7];
                refreshZoneSlot(slot, code, pos, player, LOCATION_SZONE, code != 0 || pos != 0 ? 7 : 4);
            } else {
                refreshZoneSlot(slot, code, pos, player, LOCATION_SZONE, i);
            }
        }

        if (fieldSpellSlots[player] != null) {
            refreshZoneSlot(fieldSpellSlots[player], state.szone[player][5], state.szonePos[player][5], player, LOCATION_SZONE, 5);
        }
    }

    private void refreshZoneSlot(UIElement slot, int code, int position, int player, int locationType, int sequence) {
        if (slot == null) return;
        slot.getChildren().stream()
                .filter(c -> c.hasClass("card") || c.hasClass("card-back")
                        || c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
                .toList()
                .forEach(slot::removeChild);

        // A card is present if we know the code OR if a non-zero position is set
        // (opponent's face-down cards have code=0 but position is still set)
        if (code != 0 || position != 0) {
            boolean faceDown = code == 0
                    || (position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
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
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
        } else {
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
        }
    }

    // ── Stat overlays ──────────────────────────────────────────────────────

    public void refreshFieldStats() {
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 5; i++) {
                updateMonsterStats(monsterSlots[p][i], state.mzoneStats[p][i], state.mzone[p][i], p);
            }
        }
        // EMZ slots — check both cross-mapped sources, relative to local player
        int lp = state.localPlayer;
        int opp = state.opponent();
        updateEmzStats(0, lp, 5, opp, 6);
        updateEmzStats(1, lp, 6, opp, 5);
    }

    private void updateEmzStats(int slotIdx, int p1, int s1, int p2, int s2) {
        if (emzSlots[slotIdx] == null) return;
        if (state.mzone[p1][s1] != 0 || state.mzonePos[p1][s1] != 0) {
            updateMonsterStats(emzSlots[slotIdx], state.mzoneStats[p1][s1], state.mzone[p1][s1], p1);
        } else {
            updateMonsterStats(emzSlots[slotIdx], state.mzoneStats[p2][s2], state.mzone[p2][s2], p2);
        }
    }

    private void updateMonsterStats(UIElement slot, QueriedCard stats, int code, int player) {
        if (slot == null) return;

        // Remove old stat labels
        slot.getChildren().stream()
                .filter(c -> c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
                .toList()
                .forEach(slot::removeChild);

        if (code == 0 || stats == null) return;

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
            // Deck: always face-down card back when non-empty
            setPileBackground(deckSlots[p], state.deckCount[p] > 0 ? CARD_BACK_SPRITE : null);

            // Extra deck: show top face-up card if any (pendulum cards returning face-up),
            //             otherwise card back when non-empty
            refreshExtraDeckPile(p);

            // Graveyard & Banished: top card image when non-empty
            setPileTopCard(graveSlots[p], state.grave[p]);
            setPileTopCard(banishedSlots[p], state.banished[p]);
        }
    }

    private void refreshExtraDeckPile(int player) {
        var slot = extraSlots[player];
        if (slot == null) return;

        if (state.extra[player].isEmpty()) {
            setPileBackground(slot, null);
            return;
        }

        // Find the top-most face-up card (iterate from end)
        int topFaceUpCode = 0;
        var codes = state.extra[player];
        var positions = state.extraPos[player];
        for (int i = codes.size() - 1; i >= 0; i--) {
            int pos = i < positions.size() ? positions.get(i) : 0;
            if ((pos & (POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE)) != 0) {
                topFaceUpCode = codes.get(i);
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

    private void setPileTopCard(UIElement slot, List<Integer> cards) {
        if (slot == null) return;
        if (!cards.isEmpty()) {
            // Use setCardImageBackground so the pile is registered for async image retry
            callbacks.setCardImageBackground(slot, cards.getLast());
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
     * Highlight valid placement zones for a SelectPlace prompt.
     * Bitmask is relative to the asking player (set bit = blocked zone).
     */
    public void highlightValidPlaces(int field) {
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
        // Bitmask is relative: p=0 = asking player (self), p=1 = opponent
        for (int p = 0; p < 2; p++) {
            int absPlayer = (p == 0) ? state.localPlayer : state.opponent();
            for (int i = 0; i < 5; i++) {
                int mBit = 1 << (p * 16 + i);
                if ((field & mBit) == 0 && monsterSlots[absPlayer][i] != null)
                    monsterSlots[absPlayer][i].addClass("target");

                int sBit = 1 << (p * 16 + 8 + i);
                if ((field & sBit) == 0 && spellSlots[absPlayer][i] != null)
                    spellSlots[absPlayer][i].addClass("target");
            }
        }

        // Extra Monster Zones: bit 5 = asking player's seq 5 (their left EMZ → physical emz-left);
        //                      bit 6 = asking player's seq 6 (their right EMZ → physical emz-right)
        if ((field & (1 << 5)) == 0 && emzSlots[0] != null)
            emzSlots[0].addClass("target");
        if ((field & (1 << 6)) == 0 && emzSlots[1] != null)
            emzSlots[1].addClass("target");
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

    /** Map an absolute (player, location, sequence) to the corresponding UI slot. */
    public UIElement findSlotForLocation(ClientDuelState.CardLocation loc) {
        int p = loc.controller();
        return switch (loc.location()) {
            case LOCATION_MZONE -> {
                if (loc.sequence() < 5) yield monsterSlots[p][loc.sequence()];
                // EMZ: slot 0 = local player's seq 5 (or opp's seq 6);
                //      slot 1 = local player's seq 6 (or opp's seq 5)
                else if (loc.sequence() == 5)
                    yield p == state.localPlayer ? emzSlots[0] : emzSlots[1];
                else if (loc.sequence() == 6)
                    yield p == state.localPlayer ? emzSlots[1] : emzSlots[0];
                else yield null;
            }
            case LOCATION_SZONE -> {
                if (loc.sequence() < 5) yield spellSlots[p][loc.sequence()];
                else if (loc.sequence() == 5) yield fieldSpellSlots[p];
                else if (loc.sequence() == 6) yield spellSlots[p][0]; // pendulum left shares S/T 0
                else if (loc.sequence() == 7) yield spellSlots[p][4]; // pendulum right shares S/T 4
                else yield null;
            }
            case LOCATION_EXTRA -> extraSlots[p];
            case LOCATION_GRAVE -> graveSlots[p];
            case LOCATION_REMOVED -> banishedSlots[p];
            case LOCATION_DECK -> deckSlots[p];
            default -> null;
        };
    }

    /** Compute the bit position in the SelectPlace bitmask for a given zone. */
    public int getFieldBit(int player, int location, int sequence) {
        // Bitmask is relative: self=0, opponent=1. Map absolute player to bitmask position.
        int bitmaskPlayer = (player == state.localPlayer) ? 0 : 1;
        int offset = bitmaskPlayer * 16;
        if (location == LOCATION_SZONE) offset += 8;
        return 1 << (offset + sequence);
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }
}

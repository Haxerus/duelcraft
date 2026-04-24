package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Routes card-slot and pile clicks to the right handler based on the current
 * pending prompt. Also owns the floating context menu (the row of action icons
 * that appears at the cursor when you click a card with available actions).
 *
 * Flow:
 * <ol>
 *   <li>Click arrives via {@link #onCardClicked} (called from FieldRenderer's
 *       slot click handlers or ZoneInspectorController's pile click handlers).
 *   <li>If the current prompt is SelectIdleCmd/SelectBattleCmd and the clicked
 *       card has registered actions, show the context menu.
 *   <li>Otherwise if SelectPlace is active, validate the zone and send the
 *       placement response directly.
 *   <li>Otherwise delegate to {@link PromptController#handleFieldClick}.
 * </ol>
 */
public class ClickDispatcher {

    public interface Callbacks {
        void sendResponse(byte[] response);
    }

    private final UI ui;
    private final ClientDuelState state;
    private final FieldRenderer field;
    private final PromptController prompt;
    private final Callbacks callbacks;

    private final UIElement contextMenu;

    public ClickDispatcher(UI ui, ClientDuelState state, FieldRenderer field,
                           PromptController prompt, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.field = field;
        this.prompt = prompt;
        this.callbacks = callbacks;
        this.contextMenu = byId("context-menu");
    }

    /** Register the root click listener that dismisses the context menu on outside clicks. */
    public void wireOutsideDismiss() {
        ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
            if (contextMenu != null && !contextMenu.hasClass("hidden")) {
                if (!contextMenu.isAncestorOf(e.target)) {
                    hideContextMenu();
                }
            }
        });
    }

    // ── Primary entry point ────────────────────────────────────────────────

    public void onCardClicked(int player, int location, int sequence, UIEvent event) {
        var loc = new ClientDuelState.CardLocation(player, location, sequence);
        var actions = state.cardActions.get(loc);

        // Only show context menu during idle/battle command (not sub-prompts like SelectPlace)
        if (actions != null && !actions.isEmpty()
                && (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd
                    || state.pendingPrompt instanceof DuelMessage.SelectBattleCmd)) {
            event.stopPropagation();
            showContextMenu(actions, event.x, event.y);
            return;
        }

        // No actions — dismiss any open context menu
        hideContextMenu();

        // SelectPlace is a pure field-bitmask check, doesn't go through PromptController
        if (state.pendingPrompt instanceof DuelMessage.SelectPlace sel) {
            handlePlaceSelection(sel, player, location, sequence);
            return;
        }

        prompt.handleFieldClick(player, location, sequence);
    }

    public void hideContextMenu() {
        if (contextMenu != null) contextMenu.addClass("hidden");
    }

    // ── SelectPlace routing ────────────────────────────────────────────────

    private void handlePlaceSelection(DuelMessage.SelectPlace sel, int player, int location, int sequence) {
        if (location != LOCATION_MZONE && location != LOCATION_SZONE) return;

        int bit = field.getFieldBit(player, location, sequence);
        if ((sel.field() & bit) != 0) return; // zone is blocked

        callbacks.sendResponse(ResponseBuilder.selectPlace(player, location, sequence));
    }

    // ── Context menu ───────────────────────────────────────────────────────

    private void showContextMenu(List<ClientDuelState.CardAction> actions, float mouseX, float mouseY) {
        if (contextMenu == null) return;

        contextMenu.clearAllChildren();

        for (var action : actions) {
            var icon = new UIElement();
            icon.addClass("ctx-action");

            var info = getActionIconInfo(action.actionType(), prompt.isBattleCmd());
            icon.lss("background", "sdf(" + info.color() + ", 3, 2)");
            icon.lss("tooltips", info.tooltip());

            icon.addEventListener(UIEvents.CLICK, e -> {
                e.stopPropagation();
                callbacks.sendResponse(ResponseBuilder.selectCmd(action.actionType(), action.listIndex()));
            });

            contextMenu.addChild(icon);
        }

        // Flip/nudge positioning so the menu never runs off the edge
        var root = ui.rootElement;
        float rootW = root.getSizeWidth();
        float rootH = root.getSizeHeight();
        // Estimated menu size — must stay in sync with #context-menu and .ctx-action CSS:
        //   width per icon = 14 (.ctx-action width) + 1 (gap-all) = 15
        //   total padding = 1 (padding-all) * 2 sides = 2
        //   height = 14 (.ctx-action height) + 2 (padding-all * 2) = 16
        float menuW = actions.size() * 15f + 2f;
        float menuH = 16f;

        float x = mouseX;
        float y = mouseY;
        if (x + menuW > rootW) x = mouseX - menuW;
        if (y + menuH > rootH) y = mouseY - menuH;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        contextMenu.lss("left", String.valueOf((int) x));
        contextMenu.lss("top", String.valueOf((int) y));
        contextMenu.removeClass("hidden");
    }

    private record ActionIconInfo(String color, String tooltip) {}

    private ActionIconInfo getActionIconInfo(int actionType, boolean isBattleCmd) {
        if (isBattleCmd) {
            return switch (actionType) {
                case 1 -> new ActionIconInfo("#FF4444", "Attack");
                case 2 -> new ActionIconInfo("#FF6644", "Activate");
                default -> new ActionIconInfo("#AAAAAA", "Action");
            };
        }
        return switch (actionType) {
            case 0 -> new ActionIconInfo("#FFCC00", "Summon");
            case 1 -> new ActionIconInfo("#44CC44", "Special Summon");
            case 2 -> new ActionIconInfo("#44AAFF", "Reposition");
            case 3 -> new ActionIconInfo("#6688FF", "Set");
            case 4 -> new ActionIconInfo("#8866FF", "Set S/T");
            case 5 -> new ActionIconInfo("#FF6644", "Activate");
            default -> new ActionIconInfo("#AAAAAA", "Action");
        };
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }
}

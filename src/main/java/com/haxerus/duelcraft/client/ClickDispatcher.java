package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.joml.Vector3f;

import java.util.List;

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
 *   <li>Otherwise delegate to {@link PromptController#handleFieldClick}.
 * </ol>
 */
public class ClickDispatcher {

    public interface Callbacks {
        void sendResponse(byte[] response);
    }

    private final UI ui;
    private final ClientDuelState state;
    private final PromptController prompt;
    private final Callbacks callbacks;

    private final UIElement contextMenu;
    private final UIElement canvas;

    public ClickDispatcher(UI ui, ClientDuelState state,
                           PromptController prompt, UIElement canvas, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.prompt = prompt;
        this.canvas = canvas;
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
            // UIEvent carries screen coordinates. The inverse pose undoes the canvas scale but
            // lands in root-layout space, which still carries the root's centering offset, so the
            // canvas position comes off: "left" and "top" of an absolute child are measured from
            // the canvas's own origin.
            var local = canvas.getWorldToLocalPose().transformPosition(new Vector3f(event.x, event.y, 0f));
            showContextMenu(actions, local.x - canvas.getPositionX(), local.y - canvas.getPositionY());
            return;
        }

        // No actions — dismiss any open context menu
        hideContextMenu();

        prompt.handleFieldClick(player, location, sequence);
    }

    public void hideContextMenu() {
        if (contextMenu != null) contextMenu.addClass("hidden");
    }

    // ── Context menu ───────────────────────────────────────────────────────

    private void showContextMenu(List<ClientDuelState.CardAction> actions, float mouseX, float mouseY) {
        if (contextMenu == null) return;

        contextMenu.clearAllChildren();

        boolean battleCmd = prompt.isBattleCmd();
        int activateType = battleCmd
                ? ClientDuelState.BattleAction.ACTIVATE
                : ClientDuelState.IdleAction.ACTIVATE;
        // A card with several activatable effects gets one Activate icon; picking which effect
        // happens in the option dialog, as in edopro.
        var activations = actions.stream().filter(a -> a.actionType() == activateType).toList();
        boolean activateShown = false;
        int iconCount = 0;

        for (var action : actions) {
            boolean isActivate = action.actionType() == activateType;
            if (isActivate && activateShown) continue;
            if (isActivate) activateShown = true;

            var icon = new UIElement();
            icon.addClass("ctx-action");

            var info = getActionIconInfo(action.actionType(), battleCmd);
            icon.lss("background", "sdf(" + info.color() + ", 3, 2)");
            icon.lss("tooltips", info.tooltip());

            icon.addEventListener(UIEvents.CLICK, e -> {
                e.stopPropagation();
                if (isActivate && activations.size() > 1) {
                    hideContextMenu();
                    prompt.showActivateOptions(activations);
                } else {
                    callbacks.sendResponse(ResponseBuilder.selectCmd(action.actionType(), action.listIndex()));
                }
            });

            contextMenu.addChild(icon);
            iconCount++;
        }

        // Flip/nudge positioning so the menu never runs off the canvas
        float rootW = canvas.getSizeWidth();
        float rootH = canvas.getSizeHeight();
        // Estimated menu size — must stay in sync with #context-menu and .ctx-action CSS:
        //   width per icon = 14 (.ctx-action width) + 1 (gap-all) = 15
        //   total padding = 1 (padding-all) * 2 sides = 2
        //   height = 14 (.ctx-action height) + 2 (padding-all * 2) = 16
        float menuW = iconCount * 15f + 2f;
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
                case ClientDuelState.BattleAction.ATTACK -> new ActionIconInfo("#FF4444", "Attack");
                case ClientDuelState.BattleAction.ACTIVATE -> new ActionIconInfo("#FF6644", "Activate");
                default -> new ActionIconInfo("#AAAAAA", "Action");
            };
        }
        return switch (actionType) {
            case ClientDuelState.IdleAction.SUMMON -> new ActionIconInfo("#FFCC00", "Summon");
            case ClientDuelState.IdleAction.SPECIAL_SUMMON -> new ActionIconInfo("#44CC44", "Special Summon");
            case ClientDuelState.IdleAction.REPOSITION -> new ActionIconInfo("#44AAFF", "Reposition");
            case ClientDuelState.IdleAction.SET_MONSTER -> new ActionIconInfo("#6688FF", "Set");
            case ClientDuelState.IdleAction.SET_SPELL_TRAP -> new ActionIconInfo("#8866FF", "Set S/T");
            case ClientDuelState.IdleAction.ACTIVATE -> new ActionIconInfo("#FF6644", "Activate");
            default -> new ActionIconInfo("#AAAAAA", "Action");
        };
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }
}

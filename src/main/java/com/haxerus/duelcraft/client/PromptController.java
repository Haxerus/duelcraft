package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Owns all prompt UIs — the floating dialog overlay, per-prompt-type builders,
 * and the field-click dispatch logic that routes clicks into prompt state.
 *
 * The prompt subsystem has a single active prompt at a time, and a grab-bag
 * of shared selection state ({@code selectedIndices}, {@code sumSelectableCards},
 * {@code inFieldSelectionMode}) that gets cleared/rebuilt at each prompt start.
 * Keeping them in one place here means future per-prompt-class splits can
 * happen incrementally without touching callers.
 *
 * Entry points for the host:
 * <ul>
 *   <li>{@link #rebuild()} — called on PROMPT dirty flag
 *   <li>{@link #handleFieldClick} — dispatch field slot click to active prompt
 *   <li>{@link #handleRightClick} — cancel field selection / finish unselect-card
 *   <li>{@link #onResponseSent()} — clear prompt UI after a response is sent
 *   <li>{@link #isBattleCmd()} — context menu icon lookup needs to know if
 *       we're in the battle phase command menu
 * </ul>
 */
public class PromptController {

    private static final Logger LOGGER = LogUtils.getLogger();

    public interface Callbacks {
        void setCardImageBackground(UIElement elem, int code);
        void showCardInfo(int code);
        void hideCardInfo();
        /** Send a response to the server (consumes current prompt, triggers onResponseSent). */
        void sendResponse(byte[] response);
        /** Resolve a card code to its display name (from the client's card DB). */
        String cardDisplayName(int code);
        /** Resolve a ygopro-core description code to human-readable text. */
        String resolveDesc(long desc);
    }

    private final UI ui;
    private final ClientDuelState state;
    private final FieldRenderer field;
    private final Callbacks callbacks;

    // DOM references (resolved in constructor)
    private final UIElement promptOverlay;
    private final UIElement promptTitle;
    private final UIElement promptBody;
    private final UIElement promptButtons;
    private final UIElement statusLabel;

    // Shared prompt state — only one prompt is active at a time, so sharing is safe
    // as long as each prompt clears at start. The existing shared-state bugs were
    // from calling rebuild() mid-interaction (now guarded by dedicated field-click
    // handlers that don't rebuild).
    private final List<Integer> selectedIndices = new ArrayList<>();
    private final List<UIElement> sumSelectableCards = new ArrayList<>();
    private boolean inFieldSelectionMode;
    private boolean isBattleCmd;

    public PromptController(UI ui, ClientDuelState state, FieldRenderer field,
                            UIElement statusLabel, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.field = field;
        this.callbacks = callbacks;
        this.promptOverlay = byId("prompt-overlay");
        this.promptTitle = byId("prompt-title");
        this.promptBody = byId("prompt-body");
        this.promptButtons = byId("prompt-buttons");
        this.statusLabel = statusLabel;
    }

    public boolean isBattleCmd() { return isBattleCmd; }

    // ── Rebuild dispatch (PROMPT dirty) ────────────────────────────────────

    public void rebuild() {
        LOGGER.debug("Rebuilding prompt: {}", state.pendingPrompt != null ? state.pendingPrompt.getClass().getSimpleName() : "null");
        if (state.pendingPrompt == null) {
            if (promptOverlay != null) promptOverlay.addClass("hidden");
            if (statusLabel != null) statusLabel.addClass("hidden");
            return;
        }

        switch (state.pendingPrompt) {
            case DuelMessage.SelectIdleCmd ignored -> {
                isBattleCmd = false;
                promptOverlay.addClass("hidden");
                field.refreshTargetHighlights();
            }
            case DuelMessage.SelectBattleCmd ignored -> {
                isBattleCmd = true;
                promptOverlay.addClass("hidden");
                field.refreshTargetHighlights();
            }

            case DuelMessage.SelectYesNo sel ->
                buildYesNoPrompt(callbacks.resolveDesc(sel.desc()));
            case DuelMessage.SelectEffectYn sel ->
                buildYesNoPrompt(callbacks.resolveDesc(sel.desc())
                        + "\n(" + callbacks.cardDisplayName(sel.code()) + ")");

            case DuelMessage.SelectOption sel -> buildOptionPrompt("Choose Option",
                    sel.options().stream().map(callbacks::resolveDesc).toList(),
                    i -> callbacks.sendResponse(ResponseBuilder.selectOption(i)));
            case DuelMessage.RockPaperScissors ignored -> buildOptionPrompt("Rock Paper Scissors",
                    List.of("Rock", "Paper", "Scissors"),
                    i -> callbacks.sendResponse(ResponseBuilder.rockPaperScissors(i + 1)));

            case DuelMessage.SelectChain sel -> buildChainPrompt(sel);

            case DuelMessage.SelectCard sel -> {
                if (isFieldOnlySelection(sel)) enterFieldSelectionMode(sel);
                else buildCardSelectionPrompt(sel);
            }
            case DuelMessage.SelectTribute sel -> buildTributePrompt(sel);

            case DuelMessage.SelectUnselectCard sel -> {
                if (isFieldOnlyUnselectCard(sel)) buildFieldUnselectCardPrompt(sel);
                else buildUnselectCardPrompt(sel);
            }

            case DuelMessage.SelectSum sel -> {
                if (isFieldOnlySum(sel)) buildFieldSumPrompt(sel);
                else buildSelectSumPrompt(sel);
            }

            case DuelMessage.SelectPosition sel -> buildPositionPrompt(sel);

            case DuelMessage.SelectPlace sel -> {
                promptOverlay.addClass("hidden");
                field.highlightValidPlaces(sel.field());
            }

            default -> {
                promptOverlay.removeClass("hidden");
                if (promptTitle instanceof Label title) {
                    title.setText(Component.literal(state.pendingPrompt.getClass().getSimpleName()));
                }
                clearPromptContent();
                LOGGER.warn("Unhandled prompt type: {}", state.pendingPrompt.getClass().getSimpleName());
            }
        }
    }

    // ── Prompt builders ────────────────────────────────────────────────────

    private void buildYesNoPrompt(String title) {
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t) t.setText(Component.literal(title));
        clearPromptContent();

        var yesBtn = new Button();
        yesBtn.setText(Component.literal("Yes"));
        yesBtn.addClass("prompt-btn");
        yesBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectYesNo(true)));

        var noBtn = new Button();
        noBtn.setText(Component.literal("No"));
        noBtn.addClass("prompt-btn");
        noBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectYesNo(false)));

        promptButtons.addChild(yesBtn);
        promptButtons.addChild(noBtn);
    }

    private void buildOptionPrompt(String title, List<String> options, IntConsumer onSelect) {
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t) t.setText(Component.literal(title));
        clearPromptContent();

        for (int i = 0; i < options.size(); i++) {
            int idx = i;
            var btn = new Button();
            btn.setText(Component.literal(options.get(i)));
            btn.addClass("prompt-btn");
            btn.setOnClick(e -> onSelect.accept(idx));
            promptButtons.addChild(btn);
        }
    }

    private void buildChainPrompt(DuelMessage.SelectChain sel) {
        // Auto-pass if not forced and no chain options
        if (!sel.forced() && sel.chains().isEmpty()) {
            callbacks.sendResponse(ResponseBuilder.selectChain(-1));
            return;
        }

        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t) t.setText(Component.literal("Activate Chain?"));
        clearPromptContent();

        var scroller = createPromptCardScroller();
        for (int i = 0; i < sel.chains().size(); i++) {
            int idx = i;
            var chain = sel.chains().get(i);
            int code = chain.code();

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                callbacks.sendResponse(ResponseBuilder.selectChain(idx));
            });
            scroller.addScrollViewChild(card);
        }

        if (!sel.forced()) {
            var passBtn = new Button();
            passBtn.setText(Component.literal("Pass"));
            passBtn.addClasses("prompt-btn");
            passBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectChain(-1)));
            promptButtons.addChild(passBtn);
        }
    }

    private void buildCardSelectionPrompt(DuelMessage.SelectCard sel) {
        selectedIndices.clear();
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t)
            t.setText(Component.literal("Select " + sel.min() + "-" + sel.max() + " card(s)"));
        clearPromptContent();

        var scroller = createPromptCardScroller();
        for (int i = 0; i < sel.cards().size(); i++) {
            int idx = i;
            var cardInfo = sel.cards().get(i);
            int code = cardInfo.code();

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                if (selectedIndices.contains(idx)) {
                    selectedIndices.remove(Integer.valueOf(idx));
                    card.removeClass("target");
                } else if (selectedIndices.size() < sel.max()) {
                    selectedIndices.add(idx);
                    card.addClass("target");
                }
            });
            scroller.addScrollViewChild(card);
        }

        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("Confirm"));
        confirmBtn.addClasses("prompt-btn");
        confirmBtn.setOnClick(e -> {
            if (selectedIndices.size() >= sel.min()) {
                callbacks.sendResponse(ResponseBuilder.selectCards(
                        selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
            }
        });
        promptButtons.addChild(confirmBtn);

        if (sel.cancelable()) {
            var cancelBtn = new Button();
            cancelBtn.setText(Component.literal("Cancel"));
            cancelBtn.addClasses("prompt-btn");
            cancelBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectCardsCancel()));
            promptButtons.addChild(cancelBtn);
        }
    }

    private void buildPositionPrompt(DuelMessage.SelectPosition sel) {
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t) t.setText(Component.literal("Choose Position"));
        clearPromptContent();
        // TODO: Replace with card images (face up attack and face up defense)
        if ((sel.positions() & POS_FACEUP_ATTACK) != 0)   addPositionButton("Face-up ATK",   POS_FACEUP_ATTACK);
        if ((sel.positions() & POS_FACEDOWN_ATTACK) != 0) addPositionButton("Face-down ATK", POS_FACEDOWN_ATTACK);
        if ((sel.positions() & POS_FACEUP_DEFENSE) != 0)  addPositionButton("Face-up DEF",   POS_FACEUP_DEFENSE);
        if ((sel.positions() & POS_FACEDOWN_DEFENSE) != 0) addPositionButton("Face-down DEF", POS_FACEDOWN_DEFENSE);
    }

    private void addPositionButton(String label, int position) {
        var btn = new Button();
        btn.setText(Component.literal(label));
        btn.addClasses("prompt-btn");
        btn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectPosition(position)));
        promptButtons.addChild(btn);
    }

    private void buildTributePrompt(DuelMessage.SelectTribute sel) {
        selectedIndices.clear();
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t)
            t.setText(Component.literal("Tribute " + sel.min() + "-" + sel.max() + " card(s)"));
        clearPromptContent();

        var scroller = createPromptCardScroller();
        for (int i = 0; i < sel.cards().size(); i++) {
            int idx = i;
            var tributeCard = sel.cards().get(i);
            int code = tributeCard.code();

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                if (selectedIndices.contains(idx)) {
                    selectedIndices.remove(Integer.valueOf(idx));
                    card.removeClass("target");
                } else if (selectedIndices.size() < sel.max()) {
                    selectedIndices.add(idx);
                    card.addClass("target");
                }
            });
            scroller.addScrollViewChild(card);
        }

        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("Confirm"));
        confirmBtn.addClasses("prompt-btn");
        confirmBtn.setOnClick(e -> {
            if (selectedIndices.size() >= sel.min()) {
                callbacks.sendResponse(ResponseBuilder.selectCards(
                        selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
            }
        });
        promptButtons.addChild(confirmBtn);

        if (sel.cancelable()) {
            var cancelBtn = new Button();
            cancelBtn.setText(Component.literal("Cancel"));
            cancelBtn.addClasses("prompt-btn");
            cancelBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectCardsCancel()));
            promptButtons.addChild(cancelBtn);
        }
    }

    private void buildFieldUnselectCardPrompt(DuelMessage.SelectUnselectCard sel) {
        promptOverlay.addClass("hidden");

        for (var cardInfo : sel.selectableCards()) {
            var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selectable");
        }

        for (var cardInfo : sel.unselectableCards()) {
            var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selected");
        }

        if (statusLabel instanceof Label lbl) {
            int totalSelected = sel.unselectableCards().size();
            String text = "Select Materials (" + totalSelected + " selected)";
            if (sel.finishable() && !sel.unselectableCards().isEmpty())
                text += "  (Right-click to finish)";
            lbl.setText(Component.literal(text));
            statusLabel.removeClass("hidden");
        }
    }

    private void buildUnselectCardPrompt(DuelMessage.SelectUnselectCard sel) {
        promptOverlay.removeClass("hidden");
        int totalSelected = sel.unselectableCards().size();
        if (promptTitle instanceof Label t)
            t.setText(Component.literal("Select Materials (" + totalSelected + " selected)"));
        clearPromptContent();

        var scroller = createPromptCardScroller();

        for (int i = 0; i < sel.selectableCards().size(); i++) {
            var cardInfo = sel.selectableCards().get(i);
            int code = cardInfo.code();
            int index = i;

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                callbacks.sendResponse(ResponseBuilder.selectUnselectCard(index));
            });
            scroller.addScrollViewChild(card);
        }

        for (int i = 0; i < sel.unselectableCards().size(); i++) {
            var cardInfo = sel.unselectableCards().get(i);
            int code = cardInfo.code();
            int index = i;

            var card = new UIElement();
            card.addClasses("card", "selected");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                callbacks.sendResponse(ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + index));
            });
            scroller.addScrollViewChild(card);
        }

        if ((sel.finishable() || sel.cancelable()) && !sel.unselectableCards().isEmpty()) {
            var finishBtn = new Button();
            finishBtn.setText(Component.literal("Finish"));
            finishBtn.addClasses("prompt-btn");
            finishBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectUnselectCardFinish()));
            promptButtons.addChild(finishBtn);
        }
        if (sel.cancelable() && sel.unselectableCards().isEmpty()) {
            var cancelBtn = new Button();
            cancelBtn.setText(Component.literal("Cancel"));
            cancelBtn.addClasses("prompt-btn");
            cancelBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.selectUnselectCardFinish()));
            promptButtons.addChild(cancelBtn);
        }

        for (var cardInfo : sel.selectableCards()) {
            var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("target");
        }
        for (var cardInfo : sel.unselectableCards()) {
            var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selected");
        }
    }

    private void buildFieldSumPrompt(DuelMessage.SelectSum sel) {
        selectedIndices.clear();
        sumSelectableCards.clear();
        promptOverlay.addClass("hidden");

        for (var mustCard : sel.mustSelect()) {
            var loc = new ClientDuelState.CardLocation(mustCard.controller(), mustCard.location(), mustCard.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selected");
        }

        for (var sumCard : sel.selectable()) {
            var loc = new ClientDuelState.CardLocation(sumCard.controller(), sumCard.location(), sumCard.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selectable");
        }

        updateFieldSumStatus(sel);
    }

    private void updateFieldSumStatus(DuelMessage.SelectSum sel) {
        if (!(statusLabel instanceof Label lbl)) return;
        int sum = 0;
        for (var c : sel.mustSelect()) sum += c.value1();
        for (int idx : selectedIndices) sum += sel.selectable().get(idx).value1();
        lbl.setText(Component.literal("Select Materials (Sum: " + sum + " / " + sel.targetSum() + ")"));
        statusLabel.removeClass("hidden");
    }

    private void buildSelectSumPrompt(DuelMessage.SelectSum sel) {
        selectedIndices.clear();
        promptOverlay.removeClass("hidden");
        clearPromptContent();

        int mustSum = 0;
        for (var c : sel.mustSelect()) mustSum += c.value1();
        final int[] runningSum = { mustSum };

        Runnable updateTitle = () -> {
            if (promptTitle instanceof Label t)
                t.setText(Component.literal("Select Materials (Sum: " + runningSum[0] + " / " + sel.targetSum() + ")"));
        };
        updateTitle.run();

        var scroller = createPromptCardScroller();
        sumSelectableCards.clear();

        for (var mustCard : sel.mustSelect()) {
            var card = new UIElement();
            card.addClasses("card", "selected");
            callbacks.setCardImageBackground(card, mustCard.code());
            int code = mustCard.code();
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            scroller.addScrollViewChild(card);

            var loc = new ClientDuelState.CardLocation(mustCard.controller(), mustCard.location(), mustCard.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selected");
        }

        for (int i = 0; i < sel.selectable().size(); i++) {
            var sumCard = sel.selectable().get(i);
            int code = sumCard.code();
            int value = sumCard.value1();
            int index = i;

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);

            var loc = new ClientDuelState.CardLocation(sumCard.controller(), sumCard.location(), sumCard.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("target");

            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                if (selectedIndices.contains(index)) {
                    selectedIndices.remove(Integer.valueOf(index));
                    card.removeClass("selected");
                    if (slot != null) slot.removeClass("selected");
                    runningSum[0] -= value;
                } else {
                    selectedIndices.add(index);
                    card.addClass("selected");
                    if (slot != null) slot.addClass("selected");
                    runningSum[0] += value;
                }
                updateTitle.run();
            });

            sumSelectableCards.add(card);
            scroller.addScrollViewChild(card);
        }

        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("Confirm"));
        confirmBtn.addClasses("prompt-btn");
        confirmBtn.setOnClick(e -> {
            int totalSelected = sel.mustSelect().size() + selectedIndices.size();
            if (runningSum[0] == sel.targetSum()
                    && totalSelected >= sel.min() && totalSelected <= sel.max()) {
                int mustCount = sel.mustSelect().size();
                int[] allIndices = new int[totalSelected];
                for (int i = 0; i < mustCount; i++) allIndices[i] = i;
                int j = mustCount;
                for (int idx : selectedIndices) allIndices[j++] = mustCount + idx;
                callbacks.sendResponse(ResponseBuilder.selectSum(allIndices));
            }
        });
        promptButtons.addChild(confirmBtn);
    }

    // ── Field selection mode (SelectCard with all-field candidates) ────────

    private void enterFieldSelectionMode(DuelMessage.SelectCard sel) {
        inFieldSelectionMode = true;
        selectedIndices.clear();
        promptOverlay.addClass("hidden");

        for (var card : sel.cards()) {
            var loc = new ClientDuelState.CardLocation(
                    card.controller(), card.location(), card.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selectable");
        }

        if (statusLabel instanceof Label lbl) {
            String text = sel.min() == sel.max()
                    ? "Select " + sel.min() + " card(s)"
                    : "Select " + sel.min() + "-" + sel.max() + " card(s)";
            if (sel.cancelable()) text += "  (Right-click to cancel)";
            lbl.setText(Component.literal(text));
            statusLabel.removeClass("hidden");
        }
    }

    private void exitFieldSelectionMode() {
        if (!inFieldSelectionMode) return;
        inFieldSelectionMode = false;
        selectedIndices.clear();
        ui.rootElement.select(".selectable").forEach(e -> e.removeClass("selectable"));
        if (statusLabel != null) statusLabel.addClass("hidden");
    }

    // ── Field click dispatch ───────────────────────────────────────────────

    /**
     * Route a field slot click to the active prompt's handler.
     * Returns true if the click was consumed by a prompt, false otherwise.
     */
    public boolean handleFieldClick(int player, int location, int sequence) {
        if (state.pendingPrompt instanceof DuelMessage.SelectCard) {
            handleSelectCardClick(player, location, sequence);
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectUnselectCard sel) {
            handleSelectUnselectCardClick(player, location, sequence, sel);
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectSum sel) {
            handleSelectSumClick(player, location, sequence, sel);
            return true;
        }
        return false;
    }

    private void handleSelectCardClick(int player, int location, int sequence) {
        if (!(state.pendingPrompt instanceof DuelMessage.SelectCard sel)) return;
        for (int i = 0; i < sel.cards().size(); i++) {
            var card = sel.cards().get(i);
            if (card.controller() == player && card.location() == location && card.sequence() == sequence) {
                if (selectedIndices.contains(i)) {
                    selectedIndices.remove(Integer.valueOf(i));
                } else if (selectedIndices.size() < sel.max()) {
                    selectedIndices.add(i);
                }
                if (selectedIndices.size() == sel.max()) {
                    callbacks.sendResponse(ResponseBuilder.selectCards(
                            selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
                }
                return;
            }
        }
    }

    private void handleSelectUnselectCardClick(int player, int location, int sequence,
                                               DuelMessage.SelectUnselectCard sel) {
        for (int i = 0; i < sel.selectableCards().size(); i++) {
            var c = sel.selectableCards().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                callbacks.sendResponse(ResponseBuilder.selectUnselectCard(i));
                return;
            }
        }
        for (int i = 0; i < sel.unselectableCards().size(); i++) {
            var c = sel.unselectableCards().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                callbacks.sendResponse(ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + i));
                return;
            }
        }
    }

    private void handleSelectSumClick(int player, int location, int sequence, DuelMessage.SelectSum sel) {
        for (int i = 0; i < sel.selectable().size(); i++) {
            var c = sel.selectable().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                var loc = new ClientDuelState.CardLocation(player, location, sequence);
                UIElement slot = field.findSlotForLocation(loc);
                UIElement scrollerCard = i < sumSelectableCards.size() ? sumSelectableCards.get(i) : null;

                if (selectedIndices.contains(i)) {
                    selectedIndices.remove(Integer.valueOf(i));
                    if (slot != null) slot.removeClass("selected");
                    if (scrollerCard != null) scrollerCard.removeClass("selected");
                } else {
                    selectedIndices.add(i);
                    if (slot != null) slot.addClass("selected");
                    if (scrollerCard != null) scrollerCard.addClass("selected");
                }

                int sum = 0;
                for (var m : sel.mustSelect()) sum += m.value1();
                for (int idx : selectedIndices) sum += sel.selectable().get(idx).value1();

                if (isFieldOnlySum(sel)) {
                    updateFieldSumStatus(sel);
                    int totalSelected = sel.mustSelect().size() + selectedIndices.size();
                    if (sum == sel.targetSum()
                            && totalSelected >= sel.min() && totalSelected <= sel.max()) {
                        int mustCount = sel.mustSelect().size();
                        int[] allIndices = new int[totalSelected];
                        for (int j = 0; j < mustCount; j++) allIndices[j] = j;
                        int k = mustCount;
                        for (int idx : selectedIndices) allIndices[k++] = mustCount + idx;
                        callbacks.sendResponse(ResponseBuilder.selectSum(allIndices));
                    }
                } else {
                    if (promptTitle instanceof Label t)
                        t.setText(Component.literal("Select Materials (Sum: " + sum + " / " + sel.targetSum() + ")"));
                }
                return;
            }
        }
    }

    // ── Right-click cancel/finish ──────────────────────────────────────────

    /**
     * Handle a right-click at the root. Returns true if consumed (caller should
     * stop further processing).
     */
    public boolean handleRightClick(UIEvent e) {
        if (inFieldSelectionMode
                && state.pendingPrompt instanceof DuelMessage.SelectCard sel
                && sel.cancelable()) {
            e.stopPropagation();
            exitFieldSelectionMode();
            callbacks.sendResponse(ResponseBuilder.selectCardsCancel());
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectUnselectCard sel
                && promptOverlay.hasClass("hidden")
                && (sel.finishable() || sel.cancelable())
                && !sel.unselectableCards().isEmpty()) {
            e.stopPropagation();
            callbacks.sendResponse(ResponseBuilder.selectUnselectCardFinish());
            return true;
        }
        return false;
    }

    // ── Win overlay (reuses the prompt overlay DOM) ────────────────────────

    /** Show the game-over overlay with a "You Win!"/"You Lose!" message and a Close button. */
    public void showWinOverlay(boolean localWon, Runnable onClose) {
        if (promptOverlay == null) return;
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label title) {
            title.setText(Component.literal(localWon ? "You Win!" : "You Lose!"));
        }
        if (promptBody != null) promptBody.clearAllChildren();
        if (promptButtons != null) {
            promptButtons.clearAllChildren();
            var closeBtn = new Button();
            closeBtn.setText(Component.literal("Close"));
            closeBtn.addClass("prompt-btn");
            closeBtn.setOnClick(e -> onClose.run());
            promptButtons.addChild(closeBtn);
        }
    }

    // ── Cleanup after a response is sent ───────────────────────────────────

    public void onResponseSent() {
        if (promptOverlay != null) promptOverlay.addClass("hidden");
        exitFieldSelectionMode();
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
        ui.rootElement.select(".selected").forEach(e -> e.removeClass("selected"));
        ui.rootElement.select(".selectable").forEach(e -> e.removeClass("selectable"));
        if (statusLabel != null) statusLabel.addClass("hidden");
    }

    // ── "Is this selection all on-field?" helpers ──────────────────────────

    private boolean isFieldOnlySelection(DuelMessage.SelectCard sel) {
        return !sel.cards().isEmpty()
                && sel.cards().stream()
                        .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0
                                && (c.location() & LOCATION_OVERLAY) == 0);
    }

    private boolean isFieldOnlyUnselectCard(DuelMessage.SelectUnselectCard sel) {
        return sel.selectableCards().stream()
                        .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0)
                && sel.unselectableCards().stream()
                        .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0);
    }

    private boolean isFieldOnlySum(DuelMessage.SelectSum sel) {
        return sel.selectable().stream()
                        .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0)
                && sel.mustSelect().stream()
                        .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0);
    }

    // ── DOM helpers ────────────────────────────────────────────────────────

    private void clearPromptContent() {
        if (promptBody != null) promptBody.clearAllChildren();
        if (promptButtons != null) promptButtons.clearAllChildren();
    }

    private ScrollerView createPromptCardScroller() {
        var scroller = new ScrollerView();
        scroller.addClass("prompt-card-scroller");
        promptBody.addChild(scroller);
        return scroller;
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }
}

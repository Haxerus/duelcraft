package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.carddata.CardStringHelper;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.duel.response.SumSelection;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

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
 *   <li>{@link #handleRightClick} — fire the shared Cancel/Finish action
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
        /** Resolve a system string by code (strings.conf {@code !system}), or null if unknown. */
        String systemString(int code);
        /** Resolve a counter type's display name (strings.conf {@code !counter}), or null if unknown. */
        String counterName(int counterType);
        /** True when the client card database is loaded, so the ANNOUNCE_CARD search can run. */
        boolean cardSearchAvailable();
        /** Cards an ANNOUNCE_CARD prompt accepts, matching {@code query} by passcode or name. */
        List<CardInfo> searchDeclarable(String query, List<Long> opcodes);
    }

    private final UI ui;
    private final ClientDuelState state;
    private final FieldRenderer field;
    private final Callbacks callbacks;

    /** The three states of edopro's shared Cancel/Finish button (`event_handler.cpp:2727-2745`). */
    public enum ActionButton { HIDDEN, CANCEL, FINISH }

    // DOM references (resolved in constructor)
    private final UIElement promptOverlay;
    private final UIElement promptTitle;
    private final UIElement promptBody;
    private final UIElement promptButtons;
    private final UIElement statusLabel;
    private final Button promptActionBtn;

    // The shared Cancel/Finish action: what the button — and a right-click — does right now.
    // Null while the button is hidden.
    private Runnable actionButtonAction;
    // Dialog-mode twin of promptActionBtn, rebuilt with the dialog's buttons.
    private Button dialogActionBtn;

    // Shared prompt state — only one prompt is active at a time, so sharing is safe
    // as long as each prompt clears at start. The existing shared-state bugs were
    // from calling rebuild() mid-interaction (now guarded by dedicated field-click
    // handlers that don't rebuild).
    private final List<Integer> selectedIndices = new ArrayList<>();
    private final List<UIElement> sumSelectableCards = new ArrayList<>();
    private SumSelection sumSelection;
    private boolean inFieldSelectionMode;
    private boolean isBattleCmd;

    // SelectCounter: per-card removal tally for the active prompt.
    private CounterSelection counterSelection;
    // SortCard/SortChain: ordinal assignment for the active prompt, plus the labels drawn on each card.
    private SortSelection sortSelection;
    private final List<Label> sortOrdinalLabels = new ArrayList<>();

    // AnnounceRace/AnnounceAttrib: checked bits for the active prompt, plus the "Select N" caption.
    private BitSelection bitSelection;
    private Label bitSelectionCaption;

    // SelectPlace/SelectDisfield: {player, location, sequence} triples chosen so far, in click order.
    private final List<int[]> chosenPlaces = new ArrayList<>();

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
        this.promptActionBtn = ui.selectId("prompt-action-btn", Button.class).findFirst().orElse(null);
        if (promptActionBtn != null) promptActionBtn.setOnClick(e -> {
            e.stopPropagation();
            runActionButton();
        });
    }

    // ── Shared Cancel/Finish button ────────────────────────────────────────

    /** Puts the shared button (and its dialog twin) in one of its three states. */
    private void setActionButton(ActionButton state, Runnable action) {
        actionButtonAction = state == ActionButton.HIDDEN ? null : action;
        applyActionButton(promptActionBtn, state);
        applyActionButton(dialogActionBtn, state);
    }

    private static void applyActionButton(Button button, ActionButton state) {
        if (button == null) return;
        if (state == ActionButton.HIDDEN) {
            button.addClass("hidden");
            return;
        }
        button.setText(Component.literal(state == ActionButton.CANCEL ? "Cancel" : "Finish"));
        button.removeClass("hidden");
    }

    private void runActionButton() {
        if (actionButtonAction != null) actionButtonAction.run();
    }

    /** A dialog-local Finish/Cancel button driven by the same state as the shared one. */
    private Button addDialogActionButton() {
        var button = new Button();
        button.setId("prompt-dialog-action-btn");
        button.addClasses("prompt-btn", "hidden");
        button.setOnClick(e -> runActionButton());
        promptButtons.addChild(button);
        return button;
    }

    public boolean isBattleCmd() { return isBattleCmd; }

    // ── Rebuild dispatch (PROMPT dirty) ────────────────────────────────────

    public void rebuild() {
        LOGGER.debug("Rebuilding prompt: {}", state.pendingPrompt != null ? state.pendingPrompt.getClass().getSimpleName() : "null");
        setActionButton(ActionButton.HIDDEN, null);
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
            case DuelMessage.SelectEffectYn sel -> {
                state.highlightPromptCard(sel.location());
                buildYesNoPrompt(callbacks.resolveDesc(sel.desc())
                        + "\n(" + callbacks.cardDisplayName(sel.code()) + ")");
            }

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

            case DuelMessage.SelectPlace sel -> buildPlacePrompt(sel.count(), sel.field(), false);
            case DuelMessage.SelectDisfield sel -> buildPlacePrompt(sel.count(), sel.field(), true);

            case DuelMessage.SelectCounter sel -> buildCounterPrompt(sel);

            case DuelMessage.SortCard sel -> buildSortPrompt(sel.cards(), 205, "Sort Cards");
            case DuelMessage.SortChain sel -> buildSortPrompt(sel.cards(), 206, "Sort Chain");

            case DuelMessage.AnnounceNumber sel -> {
                String title = callbacks.systemString(565);
                buildOptionPrompt(title != null ? title : "Declare a number",
                        sel.options().stream().map(String::valueOf).toList(),
                        i -> callbacks.sendResponse(ResponseBuilder.announceNumber(i)));
            }
            case DuelMessage.AnnounceRace sel -> buildAnnounceRacePrompt(sel);
            case DuelMessage.AnnounceAttrib sel -> buildAnnounceAttribPrompt(sel);

            case DuelMessage.AnnounceCard sel -> buildAnnounceCardPrompt(sel);

            default -> {
                promptOverlay.removeClass("hidden");
                if (promptTitle instanceof Label title) {
                    title.setText(Component.literal(state.pendingPrompt.getClass().getSimpleName()));
                }
                clearPromptContent();
                LOGGER.warn("Unhandled prompt type: {}", state.pendingPrompt.getClass().getSimpleName());
            }
        }

        if (state.retryMessage != null) {
            if (statusLabel != null) {
                statusLabel.removeClass("hidden");
                if (statusLabel instanceof Label lbl) lbl.setText(Component.literal(state.retryMessage));
            }
            state.retryMessage = null;
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

    /**
     * edopro's effect disambiguation (`event_handler.cpp:351-382`): a card offering several
     * activations opens the option dialog, one entry per effect description.
     */
    public void showActivateOptions(List<ClientDuelState.CardAction> activations) {
        buildOptionPrompt("Choose Effect",
                activations.stream().map(a -> callbacks.resolveDesc(a.desc())).toList(),
                choice -> {
                    var action = activations.get(choice);
                    callbacks.sendResponse(ResponseBuilder.selectCmd(action.actionType(), action.listIndex()));
                });
    }

    /**
     * edopro's declare-a-card dialog: a search box over the client card database, filtered by the
     * prompt's {@code is_declarable} opcodes, and one button per hit. Clicking a hit answers with
     * its passcode. Without a card database there is nothing to search, so this degrades to raw
     * passcode entry.
     */
    private void buildAnnounceCardPrompt(DuelMessage.AnnounceCard sel) {
        promptOverlay.removeClass("hidden");
        String title = callbacks.systemString(564);
        if (promptTitle instanceof Label t) t.setText(Component.literal(title != null ? title : "Declare a card name"));
        clearPromptContent();

        if (!callbacks.cardSearchAvailable()) {
            buildPasscodeEntry();
            return;
        }

        var results = new ScrollerView();
        results.addClass("prompt-name-scroller");

        var search = new TextField();
        search.setId("announce-card-search");
        search.getLayout().widthPercent(100);
        search.textFieldStyle(style -> style.placeholder(Component.literal("Name or passcode")));
        search.setTextResponder(text -> refreshDeclarableResults(results, text, sel.opcodes()));

        promptBody.addChild(search);
        promptBody.addChild(results);
        refreshDeclarableResults(results, "", sel.opcodes());
    }

    private void refreshDeclarableResults(ScrollerView results, String query, List<Long> opcodes) {
        results.clearAllScrollViewChildren();
        for (CardInfo card : callbacks.searchDeclarable(query, opcodes)) {
            int code = card.code();
            var btn = new Button();
            btn.setText(Component.literal(card.name()));
            btn.addClass("prompt-name-btn");
            btn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.announceCard(code)));
            results.addScrollViewChild(btn);
        }
    }

    /** ANNOUNCE_CARD fallback when no card database is available: type the passcode yourself. */
    private void buildPasscodeEntry() {
        var input = new TextField();
        input.setId("announce-card-search");
        input.getLayout().widthPercent(100);
        promptBody.addChild(input);

        var okBtn = new Button();
        okBtn.setText(Component.literal("OK"));
        okBtn.addClass("prompt-btn");
        okBtn.setOnClick(e -> {
            try {
                int code = Integer.parseInt(input.getValue().trim());
                callbacks.sendResponse(ResponseBuilder.announceCard(code));
            } catch (NumberFormatException ignored) {
                // Invalid/empty input: keep the dialog open.
            }
        });
        promptButtons.addChild(okBtn);
    }

    // ── AnnounceRace / AnnounceAttrib (checkbox grid, no OK button) ─────────

    private void buildAnnounceRacePrompt(DuelMessage.AnnounceRace sel) {
        bitSelection = new BitSelection(sel.available(), sel.count());
        // edopro shows these names via strings.conf system strings 1020-1044ish; CardStringHelper
        // already has the same names hardcoded from OcgConstants RACE_* bits, so it's reused here.
        buildBitSelectionPrompt(563, "Declare a race", sel.count(),
                bit -> CardStringHelper.raceName(1L << bit),
                () -> callbacks.sendResponse(ResponseBuilder.announceRace(bitSelection.mask())));
    }

    private void buildAnnounceAttribPrompt(DuelMessage.AnnounceAttrib sel) {
        bitSelection = new BitSelection(sel.available(), sel.count());
        // edopro shows these names via strings.conf system strings 1010-1016; CardStringHelper
        // already has the same names hardcoded from OcgConstants ATTRIBUTE_* bits, so it's reused here.
        buildBitSelectionPrompt(562, "Declare an attribute", sel.count(),
                bit -> CardStringHelper.attributeName(1 << bit),
                () -> callbacks.sendResponse(ResponseBuilder.announceAttrib((int) bitSelection.mask())));
    }

    /**
     * One toggle button per bit set in {@code bitSelection}'s available mask; submits as soon as
     * exactly {@code count} are checked (edopro: no OK button for these two prompts).
     */
    private void buildBitSelectionPrompt(int titleStringCode, String fallbackTitle, int count,
                                         IntFunction<String> labelFor, Runnable onComplete) {
        promptOverlay.removeClass("hidden");
        String title = callbacks.systemString(titleStringCode);
        if (promptTitle instanceof Label t) t.setText(Component.literal(title != null ? title : fallbackTitle));
        clearPromptContent();

        bitSelectionCaption = new Label();
        promptBody.addChild(bitSelectionCaption);

        for (int bit : bitSelection.bits()) {
            var btn = new Button();
            btn.setId("announce-bit-" + bit);
            btn.setText(Component.literal(labelFor.apply(bit)));
            btn.addClass("prompt-btn");
            btn.setOnClick(e -> {
                bitSelection.toggle(bit);
                toggleClass(btn, "selected", bitSelection.isChecked(bit));
                updateBitSelectionCaption(count);
                if (bitSelection.isComplete()) onComplete.run();
            });
            promptButtons.addChild(btn);
        }
        updateBitSelectionCaption(count);
    }

    private void updateBitSelectionCaption(int count) {
        if (bitSelectionCaption == null || bitSelection == null) return;
        int remaining = count - Long.bitCount(bitSelection.mask());
        bitSelectionCaption.setText(Component.literal("Select " + remaining));
    }

    private void buildChainPrompt(DuelMessage.SelectChain sel) {
        if (sel.chains().isEmpty()) {
            // A forced prompt always carries candidates (`processor.cpp:943-958`, `:663-668`), so an
            // empty forced list can only be a desync; answer 0 the way edopro's auto-chain-order
            // does rather than leave the duel waiting for a pick that cannot be made.
            callbacks.sendResponse(ResponseBuilder.selectChain(sel.forced() ? 0 : -1));
            return;
        }

        promptOverlay.removeClass("hidden");
        // edopro shows hint 556 once any candidate is a resolve-mode (continuous) effect, else 550.
        boolean resolveMode = sel.chains().stream()
                .anyMatch(c -> c.flag() == EFFECT_CLIENT_MODE_RESOLVE);
        String title = callbacks.systemString(resolveMode ? 556 : 550);
        if (promptTitle instanceof Label t)
            t.setText(Component.literal(title != null ? title : "Activate Chain?"));
        clearPromptContent();

        var scroller = createPromptCardScroller();
        for (var entry : chainEntriesByCard(sel).values()) {
            int code = sel.chains().get(entry.get(0)).code();

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                if (entry.size() == 1) callbacks.sendResponse(ResponseBuilder.selectChain(entry.get(0)));
                else buildChainEffectOptions(sel, entry);
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

    /** Chain entry indices grouped by the card that owns them, in message order. */
    private static Map<ClientDuelState.CardLocation, List<Integer>> chainEntriesByCard(
            DuelMessage.SelectChain sel) {
        var byCard = new LinkedHashMap<ClientDuelState.CardLocation, List<Integer>>();
        for (int i = 0; i < sel.chains().size(); i++) {
            var chain = sel.chains().get(i);
            var key = new ClientDuelState.CardLocation(
                    chain.controller(), chain.location(), chain.sequence());
            byCard.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        return byCard;
    }

    /** One card with several activatable effects: edopro's option dialog over each entry's desc. */
    private void buildChainEffectOptions(DuelMessage.SelectChain sel, List<Integer> indices) {
        buildOptionPrompt("Choose Effect",
                indices.stream().map(i -> callbacks.resolveDesc(sel.chains().get(i).desc())).toList(),
                choice -> callbacks.sendResponse(ResponseBuilder.selectChain(indices.get(choice))));
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
                applyCardSelectionGate(sel);
            });
            scroller.addScrollViewChild(card);
        }

        dialogActionBtn = addDialogActionButton();
        setInitialSelectionButton(sel.cancelable(), sel.min() == 0, this::sendSelectedCards);
    }

    /**
     * edopro's state for the shared button when a card or tribute prompt opens
     * (`duelclient.cpp:2022-2029`, `:2385-2387`): Cancel wins over Finish while nothing is picked.
     */
    private void setInitialSelectionButton(boolean cancelable, boolean ready, Runnable finish) {
        if (cancelable) setActionButton(ActionButton.CANCEL, this::sendSelectCardsCancel);
        else if (ready) setActionButton(ActionButton.FINISH, finish);
        else setActionButton(ActionButton.HIDDEN, null);
    }

    /** Re-gate a {@code SELECT_CARD} selection after a pick: submit, or pick a button state. */
    private void applyCardSelectionGate(DuelMessage.SelectCard sel) {
        int picked = selectedIndices.size();
        applyGate(SelectionGate.of(picked, picked, sel.min(), sel.max(),
                sel.cards().size(), sel.cancelable()), this::sendSelectedCards);
    }

    private void applyGate(SelectionGate gate, Runnable finish) {
        switch (gate) {
            case SUBMIT -> finish.run();
            case FINISH -> setActionButton(ActionButton.FINISH, finish);
            case CANCEL -> setActionButton(ActionButton.CANCEL, this::sendSelectCardsCancel);
            case HIDDEN -> setActionButton(ActionButton.HIDDEN, null);
        }
    }

    private void sendSelectedCards() {
        callbacks.sendResponse(ResponseBuilder.selectCards(
                selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
    }

    private void sendSelectCardsCancel() {
        callbacks.sendResponse(ResponseBuilder.selectCardsCancel());
    }

    /** edopro's battle-position window (title 561): one card-image button per offered position. */
    private void buildPositionPrompt(DuelMessage.SelectPosition sel) {
        promptOverlay.removeClass("hidden");
        String title = callbacks.systemString(561);
        if (promptTitle instanceof Label t)
            t.setText(Component.literal(title != null ? title : "Choose Position"));
        clearPromptContent();

        var row = new UIElement();
        row.addClass("position-row");
        promptBody.addChild(row);

        addPositionChoice(row, sel, POS_FACEUP_ATTACK, "ATK");
        addPositionChoice(row, sel, POS_FACEDOWN_ATTACK, "Set ATK");
        addPositionChoice(row, sel, POS_FACEUP_DEFENSE, "DEF");
        addPositionChoice(row, sel, POS_FACEDOWN_DEFENSE, "Set DEF");
    }

    /** The card as it would look in that position: face-up art or the card back, turned for defense. */
    private void addPositionChoice(UIElement row, DuelMessage.SelectPosition sel, int position, String label) {
        if ((sel.positions() & position) == 0) return;

        var choice = new UIElement();
        choice.addClass("position-choice");

        var card = new UIElement();
        card.addClass("card");
        if ((position & POS_FACEUP) != 0) callbacks.setCardImageBackground(card, sel.code());
        else card.lss("background", FieldRenderer.CARD_BACK_SPRITE);
        if ((position & POS_DEFENSE) != 0) card.addClass("defense");
        card.addEventListener(UIEvents.CLICK, ev -> {
            ev.stopPropagation();
            callbacks.sendResponse(ResponseBuilder.selectPosition(position));
        });

        var caption = new Label();
        caption.addClass("position-label");
        caption.setText(Component.literal(label));

        choice.addChild(card);
        choice.addChild(caption);
        row.addChild(choice);
    }

    // ── SelectPlace / SelectDisfield (field-only, count zones, no cancel) ──

    private void buildPlacePrompt(int count, int placeField, boolean disfield) {
        chosenPlaces.clear();
        promptOverlay.addClass("hidden");
        field.highlightValidPlaces(placeField);
        updatePlaceStatus(count, disfield);
    }

    private void updatePlaceStatus(int count, boolean disfield) {
        if (!(statusLabel instanceof Label lbl)) return;
        int remaining = count - chosenPlaces.size();
        String text = disfield
                ? "Select " + remaining + " zone(s) to become unusable"
                : (remaining <= 1 ? "Select a zone" : "Select " + remaining + " more zone(s)");
        lbl.setText(Component.literal(text));
        statusLabel.removeClass("hidden");
    }

    /** Toggle a clicked field zone for the active SelectPlace/SelectDisfield prompt; submit at count. */
    private void handlePlaceClick(int player, int location, int sequence, int count, int placeField, boolean disfield) {
        UIElement slot = field.findSlotForLocation(new ClientDuelState.CardLocation(player, location, sequence));
        if (slot == null) return;

        for (int i = 0; i < chosenPlaces.size(); i++) {
            int[] chosen = chosenPlaces.get(i);
            if (field.findSlotForLocation(new ClientDuelState.CardLocation(chosen[0], chosen[1], chosen[2])) == slot) {
                chosenPlaces.remove(i);
                slot.removeClass("selected");
                updatePlaceStatus(count, disfield);
                return;
            }
        }

        int[] resolved = field.resolvePlaceZone(player, location, sequence, placeField);
        if (resolved == null) return; // blocked

        chosenPlaces.add(resolved);
        slot.addClass("selected");
        if (chosenPlaces.size() == count) {
            callbacks.sendResponse(ResponseBuilder.selectPlaces(new ArrayList<>(chosenPlaces)));
        } else {
            updatePlaceStatus(count, disfield);
        }
    }

    // ── SelectCounter (field-only, click removes one counter at a time) ────

    private void buildCounterPrompt(DuelMessage.SelectCounter sel) {
        counterSelection = new CounterSelection(sel);
        promptOverlay.addClass("hidden");
        refreshCounterHighlights(sel);
        updateCounterStatus(sel);
    }

    private void refreshCounterHighlights(DuelMessage.SelectCounter sel) {
        for (int i = 0; i < sel.cards().size(); i++) {
            var c = sel.cards().get(i);
            UIElement slot = field.findSlotForLocation(
                    new ClientDuelState.CardLocation(c.controller(), c.location(), c.sequence()));
            if (slot != null) toggleClass(slot, "target", counterSelection.canPick(i));
        }
    }

    private void updateCounterStatus(DuelMessage.SelectCounter sel) {
        if (!(statusLabel instanceof Label lbl)) return;
        String name = callbacks.counterName(sel.counterType());
        if (name == null) name = "counter type " + sel.counterType();
        lbl.setText(Component.literal("Remove " + counterSelection.remaining() + " \"" + name + "\""));
        statusLabel.removeClass("hidden");
    }

    private void handleSelectCounterClick(int player, int location, int sequence, DuelMessage.SelectCounter sel) {
        if (counterSelection == null) return;
        for (int i = 0; i < sel.cards().size(); i++) {
            var c = sel.cards().get(i);
            if (c.controller() != player || c.location() != location || c.sequence() != sequence) continue;
            if (!counterSelection.canPick(i)) return;

            counterSelection.pick(i);
            refreshCounterHighlights(sel);
            updateCounterStatus(sel);
            if (counterSelection.isComplete()) {
                callbacks.sendResponse(ResponseBuilder.selectCounter(counterSelection.response()));
            }
            return;
        }
    }

    // ── SortCard / SortChain (overlay card list, click assigns the next ordinal) ──

    private void buildSortPrompt(List<DuelMessage.SortableCard> cards, int titleStringCode, String fallbackTitle) {
        sortSelection = new SortSelection(cards.size());
        sortOrdinalLabels.clear();
        promptOverlay.removeClass("hidden");
        String title = callbacks.systemString(titleStringCode);
        if (promptTitle instanceof Label t) t.setText(Component.literal(title != null ? title : fallbackTitle));
        clearPromptContent();

        var scroller = createPromptCardScroller();
        for (int i = 0; i < cards.size(); i++) {
            int idx = i;
            int code = cards.get(i).code();

            var card = new UIElement();
            card.setId("sort-card-" + i);
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                sortSelection.assign(idx);
                refreshSortOrdinals();
                if (sortSelection.isComplete()) {
                    callbacks.sendResponse(ResponseBuilder.sortCards(sortSelection.response()));
                }
            });

            var ordinalLabel = new Label();
            ordinalLabel.addClass("card-ordinal");
            card.addChild(ordinalLabel);
            sortOrdinalLabels.add(ordinalLabel);

            scroller.addScrollViewChild(card);
        }
        refreshSortOrdinals();

        var keepOrderBtn = new Button();
        keepOrderBtn.setText(Component.literal("Keep Order"));
        keepOrderBtn.addClasses("prompt-btn");
        keepOrderBtn.setOnClick(e -> callbacks.sendResponse(ResponseBuilder.sortCardsDefault()));
        promptButtons.addChild(keepOrderBtn);
    }

    private void refreshSortOrdinals() {
        for (int i = 0; i < sortOrdinalLabels.size(); i++) {
            int ordinal = sortSelection.ordinalOf(i);
            sortOrdinalLabels.get(i).setText(Component.literal(ordinal == 0 ? "" : String.valueOf(ordinal)));
        }
    }

    private void buildTributePrompt(DuelMessage.SelectTribute sel) {
        // Tributes are definitionally field-only (you can't tribute from a pile),
        // so this always renders as field highlights — no scroller dialog.
        selectedIndices.clear();
        promptOverlay.addClass("hidden");

        for (var tributeCard : sel.cards()) {
            var loc = new ClientDuelState.CardLocation(
                    tributeCard.controller(), tributeCard.location(), tributeCard.sequence());
            UIElement slot = field.findSlotForLocation(loc);
            if (slot != null) slot.addClass("selectable");
        }

        updateTributeStatus(sel);
        setInitialSelectionButton(sel.cancelable(), false, this::sendSelectedCards);
    }

    private void updateTributeStatus(DuelMessage.SelectTribute sel) {
        if (!(statusLabel instanceof Label lbl)) return;
        int sum = currentTributeSum(sel);
        String text;
        if (sel.min() == sel.max()) {
            String plural = sel.max() == 1 ? "tribute" : "tributes";
            text = "Tribute " + sel.max() + " " + plural + " (" + sum + "/" + sel.max() + ")";
        } else {
            text = "Tribute " + sel.min() + "-" + sel.max() + " tributes (" + sum + ")";
        }
        lbl.setText(Component.literal(text));
        statusLabel.removeClass("hidden");
    }

    private int currentTributeSum(DuelMessage.SelectTribute sel) {
        int sum = 0;
        for (int idx : selectedIndices) {
            if (idx < sel.cards().size()) sum += sel.cards().get(idx).tributeCount();
        }
        return sum;
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
            lbl.setText(Component.literal(unselectCardCaption(sel)));
            statusLabel.removeClass("hidden");
        }
        setUnselectCardActionButton(sel);
    }

    /** edopro's caption for a running per-click selection: how many are in, and the bounds. */
    private static String unselectCardCaption(DuelMessage.SelectUnselectCard sel) {
        return "Selected " + sel.unselectableCards().size()
                + " (" + sel.min() + "-" + sel.max() + ")";
    }

    /** The engine takes {@code -1} whenever either flag is set; the caption is all that differs. */
    private void setUnselectCardActionButton(DuelMessage.SelectUnselectCard sel) {
        if (sel.finishable())
            setActionButton(ActionButton.FINISH, this::sendUnselectCardFinish);
        else if (sel.cancelable())
            setActionButton(ActionButton.CANCEL, this::sendUnselectCardFinish);
        else
            setActionButton(ActionButton.HIDDEN, null);
    }

    private void sendUnselectCardFinish() {
        callbacks.sendResponse(ResponseBuilder.selectUnselectCardFinish());
    }

    private void buildUnselectCardPrompt(DuelMessage.SelectUnselectCard sel) {
        promptOverlay.removeClass("hidden");
        if (promptTitle instanceof Label t)
            t.setText(Component.literal(unselectCardCaption(sel)));
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

        dialogActionBtn = addDialogActionButton();
        setUnselectCardActionButton(sel);

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
        sumSelection = new SumSelection(sel);
        sumSelectableCards.clear();
        promptOverlay.addClass("hidden");

        for (var mustCard : sel.mustSelect()) {
            UIElement slot = slotOf(mustCard);
            if (slot != null) slot.addClass("selected");
        }

        refreshSumHighlights(sel, "selectable");
        updateSumCaption(sel, true);
        maybeAutoSubmitSum();
    }

    private void buildSelectSumPrompt(DuelMessage.SelectSum sel) {
        sumSelection = new SumSelection(sel);
        promptOverlay.removeClass("hidden");
        clearPromptContent();
        updateSumCaption(sel, false);

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

            UIElement slot = slotOf(mustCard);
            if (slot != null) slot.addClass("selected");
        }

        for (int i = 0; i < sel.selectable().size(); i++) {
            int code = sel.selectable().get(i).code();
            int index = i;

            var card = new UIElement();
            card.addClass("card");
            callbacks.setCardImageBackground(card, code);

            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> callbacks.showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> callbacks.hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                toggleSumPick(sel, index, false);
            });

            sumSelectableCards.add(card);
            scroller.addScrollViewChild(card);
        }

        refreshSumHighlights(sel, "target");

        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("Confirm"));
        confirmBtn.addClasses("prompt-btn");
        confirmBtn.setOnClick(e -> {
            if (sumSelection.isComplete()) sendSumResponse();
        });
        promptButtons.addChild(confirmBtn);

        maybeAutoSubmitSum();
    }

    /** Toggle one selectable card, then repaint, recaption and submit if nothing is left to pick. */
    private void toggleSumPick(DuelMessage.SelectSum sel, int index, boolean fieldMode) {
        if (!sumSelection.isSelected(index) && !sumSelection.canPick(index)) return;
        sumSelection.toggle(index);
        refreshSumHighlights(sel, fieldMode ? "selectable" : "target");
        updateSumCaption(sel, fieldMode);
        maybeAutoSubmitSum();
    }

    /** Mark picked cards and offer only those that can still complete a legal total. */
    private void refreshSumHighlights(DuelMessage.SelectSum sel, String candidateClass) {
        for (int i = 0; i < sel.selectable().size(); i++) {
            boolean picked = sumSelection.isSelected(i);
            boolean offered = picked || sumSelection.canPick(i);

            UIElement slot = slotOf(sel.selectable().get(i));
            if (slot != null) {
                toggleClass(slot, "selected", picked);
                toggleClass(slot, candidateClass, offered && !picked);
            }
            if (i < sumSelectableCards.size()) {
                toggleClass(sumSelectableCards.get(i), "selected", picked);
            }
        }
    }

    private void updateSumCaption(DuelMessage.SelectSum sel, boolean fieldMode) {
        String target = (sel.selectMode() ? ">=" : "") + sel.targetSum();
        String text = "Select Materials (Sum: " + sumSelection.currentSum() + " / " + target + ")";
        if (fieldMode) {
            // A complete selection that can still be extended has no other way out — SELECT_SUM
            // has no cancel encoding, so the field prompt must offer the Finish gesture.
            setActionButton(sumSelection.isComplete() ? ActionButton.FINISH : ActionButton.HIDDEN,
                    this::sendSumResponse);
            if (statusLabel instanceof Label lbl) {
                lbl.setText(Component.literal(text));
                statusLabel.removeClass("hidden");
            }
        } else if (promptTitle instanceof Label t) {
            t.setText(Component.literal(text));
        }
    }

    /** Submit as soon as the selection is legal and no further pick could be legal. */
    private void maybeAutoSubmitSum() {
        if (sumSelection.isComplete() && !sumSelection.hasPickable()) sendSumResponse();
    }

    private void sendSumResponse() {
        callbacks.sendResponse(ResponseBuilder.selectSum(sumSelection.responseIndices()));
    }

    private UIElement slotOf(DuelMessage.SumCard card) {
        return field.findSlotForLocation(new ClientDuelState.CardLocation(
                card.controller(), card.location(), card.sequence()));
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
            lbl.setText(Component.literal(sel.min() == sel.max()
                    ? "Select " + sel.min() + " card(s)"
                    : "Select " + sel.min() + "-" + sel.max() + " card(s)"));
            statusLabel.removeClass("hidden");
        }
        setInitialSelectionButton(sel.cancelable(), sel.min() == 0, this::sendSelectedCards);
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
        // A selection being made in the overlay dialog owns the clicks: the same card would
        // otherwise be toggled twice, once in the dialog and once on the field behind it.
        if (isDialogSelectionOpen()) return true;
        if (state.pendingPrompt instanceof DuelMessage.SelectPlace sel) {
            handlePlaceClick(player, location, sequence, sel.count(), sel.field(), false);
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectDisfield sel) {
            handlePlaceClick(player, location, sequence, sel.count(), sel.field(), true);
            return true;
        }
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
        if (state.pendingPrompt instanceof DuelMessage.SelectTribute sel) {
            handleSelectTributeClick(player, location, sequence, sel);
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectCounter sel) {
            handleSelectCounterClick(player, location, sequence, sel);
            return true;
        }
        return false;
    }

    /** True while a card-list prompt is being answered in the overlay dialog rather than on the field. */
    private boolean isDialogSelectionOpen() {
        if (promptOverlay == null || promptOverlay.hasClass("hidden")) return false;
        return state.pendingPrompt instanceof DuelMessage.SelectCard
                || state.pendingPrompt instanceof DuelMessage.SelectUnselectCard
                || state.pendingPrompt instanceof DuelMessage.SelectSum;
    }

    private void handleSelectCardClick(int player, int location, int sequence) {
        if (!(state.pendingPrompt instanceof DuelMessage.SelectCard sel)) return;
        for (int i = 0; i < sel.cards().size(); i++) {
            var card = sel.cards().get(i);
            if (card.controller() != player || card.location() != location || card.sequence() != sequence)
                continue;

            UIElement slot = field.findSlotForLocation(
                    new ClientDuelState.CardLocation(player, location, sequence));
            if (selectedIndices.contains(i)) {
                selectedIndices.remove(Integer.valueOf(i));
                if (slot != null) slot.removeClass("selected");
            } else if (selectedIndices.size() < sel.max()) {
                selectedIndices.add(i);
                if (slot != null) slot.addClass("selected");
            }
            applyCardSelectionGate(sel);
            return;
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
        if (sumSelection == null) return;
        for (int i = 0; i < sel.selectable().size(); i++) {
            var c = sel.selectable().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                toggleSumPick(sel, i, isFieldOnlySum(sel));
                return;
            }
        }
    }

    private void handleSelectTributeClick(int player, int location, int sequence,
                                          DuelMessage.SelectTribute sel) {
        for (int i = 0; i < sel.cards().size(); i++) {
            var c = sel.cards().get(i);
            if (c.controller() != player || c.location() != location || c.sequence() != sequence) continue;

            var loc = new ClientDuelState.CardLocation(player, location, sequence);
            UIElement slot = field.findSlotForLocation(loc);

            if (selectedIndices.contains(i)) {
                selectedIndices.remove(Integer.valueOf(i));
                if (slot != null) slot.removeClass("selected");
            } else {
                // No overshoot guard: ygopro-core allows over-tribute (e.g., a regular monster
                // plus a double-cost monster for a 2-tribute summon — total 3, accepted).
                selectedIndices.add(i);
                if (slot != null) slot.addClass("selected");
            }

            updateTributeStatus(sel);

            // The engine measures `min` against the summed tribute value and `max` against the card
            // count, so only reaching `max` submits by itself; meeting `min` offers Finish and
            // leaves room for one more over-tribute.
            applyGate(SelectionGate.of(selectedIndices.size(), currentTributeSum(sel),
                    sel.min(), sel.max(), sel.cards().size(), sel.cancelable()),
                    this::sendSelectedCards);
            return;
        }
    }

    // ── Right-click cancel/finish ──────────────────────────────────────────

    /**
     * Handle a right-click at the root. Returns true if consumed (caller should
     * stop further processing).
     */
    public boolean handleRightClick(UIEvent e) {
        // edopro binds right-click to the shared Cancel/Finish button (`event_handler.cpp:1498-1511`).
        if (actionButtonAction != null) {
            e.stopPropagation();
            runActionButton();
            return true;
        }
        // The effect-choice dialog is the only way out of a card with several activations, so
        // right-click has to be able to back out of it (edopro's CancelOrFinish, `:2906-2926`).
        if ((state.pendingPrompt instanceof DuelMessage.SelectIdleCmd
                || state.pendingPrompt instanceof DuelMessage.SelectBattleCmd)
                && promptOverlay != null && !promptOverlay.hasClass("hidden")) {
            e.stopPropagation();
            promptOverlay.addClass("hidden");
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectChain sel && !sel.forced()) {
            e.stopPropagation();
            callbacks.sendResponse(ResponseBuilder.selectChain(-1));
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SortCard || state.pendingPrompt instanceof DuelMessage.SortChain) {
            e.stopPropagation();
            callbacks.sendResponse(ResponseBuilder.sortCardsDefault());
            return true;
        }
        return false;
    }

    // ── Cleanup after a response is sent ───────────────────────────────────

    public void onResponseSent() {
        if (promptOverlay != null) promptOverlay.addClass("hidden");
        setActionButton(ActionButton.HIDDEN, null);
        exitFieldSelectionMode();
        chosenPlaces.clear();
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
        dialogActionBtn = null;
    }

    private ScrollerView createPromptCardScroller() {
        var scroller = new ScrollerView();
        scroller.addClass("prompt-card-scroller");
        promptBody.addChild(scroller);
        return scroller;
    }

    private static void toggleClass(UIElement element, String name, boolean on) {
        if (on) element.addClass(name);
        else element.removeClass(name);
    }

    private UIElement byId(String id) {
        return ui.selectId(id).findFirst().orElse(null);
    }
}

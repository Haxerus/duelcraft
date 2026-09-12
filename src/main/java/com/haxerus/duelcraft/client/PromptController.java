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
import java.util.List;
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
 *   <li>{@link #handleRightClick} — cancel field selection / finish unselect-card / confirm a sum
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
        if (sel.cancelable()) text += "  (Right-click to cancel)";
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
            // has no cancel encoding, so the field prompt must offer the confirm gesture.
            if (sumSelection.isComplete()) text += "  (Right-click to confirm)";
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

            // Auto-submit once we've reached the minimum required tribute count.
            // If the selection went OVER max via over-tribute, the engine accepts it.
            int sum = currentTributeSum(sel);
            if (sum >= sel.min()) {
                callbacks.sendResponse(ResponseBuilder.selectCards(
                        selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
            }
            return;
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
        if (state.pendingPrompt instanceof DuelMessage.SelectSum
                && promptOverlay.hasClass("hidden")
                && sumSelection != null && sumSelection.isComplete()) {
            e.stopPropagation();
            sendSumResponse();
            return true;
        }
        if (state.pendingPrompt instanceof DuelMessage.SelectTribute sel && sel.cancelable()) {
            e.stopPropagation();
            callbacks.sendResponse(ResponseBuilder.selectCardsCancel());
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

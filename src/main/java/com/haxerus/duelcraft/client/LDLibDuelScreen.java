package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.carddata.CardStringHelper;
import org.slf4j.Logger;

import static com.haxerus.duelcraft.core.OcgConstants.*;

import com.haxerus.duelcraft.client.ClientDuelState.DirtyFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * XML-based duel screen using LDLib2.
 * <p>
 * Loads the UI layout from duel_screen.xml and wires it to ClientDuelState.
 * The UIRefresher inner class handles all data binding and event wiring.
 */
public class LDLibDuelScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation DUEL_UI =
            ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/duel_screen.xml");

    private static ClientDuelState activeState;
    private static ModularUI activeUI;
    private static UIRefresher refresher;

    /**
     * Load the XML UI and open the duel screen.
     */
    public static void open(DuelStartPayload startInfo) {
        activeState = new ClientDuelState(startInfo);
        activeUI = loadFromXml();
        refresher = new UIRefresher(activeUI, activeState);
        var screen = new ModularUIScreen(activeUI,
                Component.literal("Duel vs. " + startInfo.opponentName()));
        Minecraft.getInstance().setScreen(screen);
        LOGGER.info("Duel screen opened (XML-based) vs. {}", startInfo.opponentName());
    }

    /**
     * Route an incoming duel message to the active state.
     */
    public static void applyMessage(DuelMessage msg) {
        if (activeState != null) {
            activeState.applyMessage(msg);
        }
    }

    /**
     * Clean up when the duel ends.
     */
    public static void close() {
        activeState = null;
        activeUI = null;
        refresher = null;
    }

    private static ModularUI loadFromXml() {
        var doc = XmlUtils.loadXml(DUEL_UI);
        if (doc == null) {
            throw new IllegalStateException("Failed to load " + DUEL_UI);
        }
        // Parse XML to get the root element and stylesheets
        var parsed = UI.of(doc);
        // Rebuild with a DynamicSizeProvider that uses the full screen size,
        // so the root element can use width/height: 100% reliably
        var ui = UI.of(parsed.rootElement, parsed.stylesheets, screenSize -> screenSize);
        return ModularUI.of(ui);
    }

    // ─── Response Helper ─────────────────────────────────────

    static void sendResponse(ClientDuelState state, byte[] response) {
        PacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.pendingPrompt = null;
        state.clearCardActions();
        if (refresher != null) {
            refresher.onResponseSent();
        }
    }

    // ─── UIRefresher: binds XML elements to game state ───────

    static class UIRefresher {
        private final UI ui;
        private final ClientDuelState state;

        // HUD
        private final ProgressBar oppLpBar;
        private final ProgressBar plrLpBar;
        private final UIElement titleLabel;
        private final UIElement turnPhaseLabel;
        private final UIElement statusLabel;

        // Zone slots: [player 0/1][sequence]
        private final UIElement[][] monsterSlots = new UIElement[2][5];
        private final UIElement[][] spellSlots = new UIElement[2][5];
        private final UIElement[] emzSlots = new UIElement[2];
        private final UIElement[] fieldSpellSlots = new UIElement[2];

        // Pile count labels + slot elements
        private final Label[] deckCountLabels = new Label[2];
        private final Label[] graveCountLabels = new Label[2];
        private final Label[] extraCountLabels = new Label[2];
        private final Label[] banishedCountLabels = new Label[2];
        private final UIElement[] deckSlots = new UIElement[2];
        private final UIElement[] graveSlots = new UIElement[2];
        private final UIElement[] extraSlots = new UIElement[2];
        private final UIElement[] banishedSlots = new UIElement[2];

        // Hands
        private final ScrollerView oppHandContainer;
        private final ScrollerView plrHandContainer;

        // Phase buttons
        private final Button phaseBtnLeft;
        private final Button phaseBtnCenter;
        private final Button phaseBtnRight;

        // Overlays
        private final UIElement cardInfoBanner;
        private final UIElement zoneInspector;
        private final UIElement promptOverlay;
        private final UIElement promptTitle;
        private final UIElement promptBody;
        private final UIElement promptButtons;
        // Context menu (cursor-anchored action icons)
        private final UIElement contextMenu;

        // Card Selection
        private final List<Integer> selectedIndices = new ArrayList<>();
        private boolean isBattleCmd;
        private boolean inFieldSelectionMode;

        UIRefresher(ModularUI modularUI, ClientDuelState state) {
            this.ui = modularUI.ui;
            this.state = state;

            int plr = state.localPlayer;
            int opp = state.opponent();

            // ── HUD ──
            oppLpBar = byId("opp-lp-bar", ProgressBar.class);
            plrLpBar = byId("plr-lp-bar", ProgressBar.class);
            titleLabel = byId("duel-title-label");
            turnPhaseLabel = byId("hud-turn-phase");
            statusLabel = byId("status-label");

            // ── Zone slots ──
            for (int i = 0; i < 5; i++) {
                monsterSlots[opp][i] = byId("opp-mon-" + i);
                monsterSlots[plr][i] = byId("plr-mon-" + i);
                spellSlots[opp][i] = byId("opp-st-" + i);
                spellSlots[plr][i] = byId("plr-st-" + i);
            }
            emzSlots[0] = byId("emz-left");
            emzSlots[1] = byId("emz-right");
            fieldSpellSlots[opp] = byId("opp-field-spell");
            fieldSpellSlots[plr] = byId("plr-field-spell");

            // ── Pile count labels ──
            deckCountLabels[opp] = findCardCountLabel("opp-deck");
            deckCountLabels[plr] = findCardCountLabel("plr-deck");
            graveCountLabels[opp] = findCardCountLabel("opp-graveyard");
            graveCountLabels[plr] = findCardCountLabel("plr-graveyard");
            extraCountLabels[opp] = findCardCountLabel("opp-extra-deck");
            extraCountLabels[plr] = findCardCountLabel("plr-extra-deck");
            banishedCountLabels[opp] = findCardCountLabel("opp-banished");
            banishedCountLabels[plr] = findCardCountLabel("plr-banished");
            deckSlots[opp] = byId("opp-deck");
            deckSlots[plr] = byId("plr-deck");
            graveSlots[opp] = byId("opp-graveyard");
            graveSlots[plr] = byId("plr-graveyard");
            extraSlots[opp] = byId("opp-extra-deck");
            extraSlots[plr] = byId("plr-extra-deck");
            banishedSlots[opp] = byId("opp-banished");
            banishedSlots[plr] = byId("plr-banished");

            // ── Hands ──
            oppHandContainer = byId("opponent-hand", ScrollerView.class);
            plrHandContainer = byId("player-hand", ScrollerView.class);

            // ── Phase buttons ──
            phaseBtnLeft = byId("phase-btn-left", Button.class);
            phaseBtnCenter = byId("phase-btn-center", Button.class);
            phaseBtnRight = byId("phase-btn-right", Button.class);

            // ── Overlays ──
            cardInfoBanner = byId("card-info-banner");
            zoneInspector = byId("zone-inspector");
            promptOverlay = byId("prompt-overlay");
            promptTitle = byId("prompt-title");
            promptBody = byId("prompt-body");
            promptButtons = byId("prompt-buttons");
            contextMenu = byId("context-menu");

            // ── Bind reactive data ──
            bindReactiveData();

            // ── Register tick handler for dirty-flag-driven updates ──
            ui.rootElement.addEventListener(UIEvents.TICK, this::onTick);

            wirePhaseButtons();
            handleZoneClicks();
            handlePileClicks();

            // Refresh hand/field when card images finish downloading
            CardImageManager images = DuelcraftClient.getCardImageManager();
            if (images != null) {
                images.setOnTextureLoaded(state::markAllVisualsDirty);
            }

            // Dismiss context menu when clicking outside it
            ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
                if (contextMenu != null && !contextMenu.hasClass("hidden")) {
                    // Check if the click target is inside the context menu
                    if (!contextMenu.isAncestorOf(e.target)) {
                        hideContextMenu();
                    }
                }
            });

            // Right-click to cancel field selection
            ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
                if (e.button == 1 && inFieldSelectionMode
                        && state.pendingPrompt instanceof DuelMessage.SelectCard sel
                        && sel.cancelable()) {
                    e.stopPropagation();
                    exitFieldSelectionMode();
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCardsCancel());
                }
            });

            LOGGER.info("UIRefresher initialized — all elements bound");
        }

        // ── Element lookup helpers ──

        private UIElement byId(String id) {
            return ui.selectId(id).findFirst().orElse(null);
        }

        private <T> T byId(String id, Class<T> type) {
            return ui.selectId(id, type).findFirst().orElse(null);
        }

        // ── Reactive bindings (auto-update every tick) ──

        private void bindReactiveData() {
            int plr = state.localPlayer;
            int opp = state.opponent();

            // LP bars — updated via refreshLP() on DirtyFlag.LP, not reactive binding

            // Title and turn/phase labels
            bindLabel(titleLabel, () -> "You vs. " + state.opponentName);
            bindLabel(turnPhaseLabel, () ->
                    "Turn " + state.turnCount + " - " + state.phaseName()
                            + (state.isLocalTurn() ? " (Your turn)" : ""));

            // Pile counts
            bindPileCount(deckCountLabels[opp], () -> state.deckCount[opp]);
            bindPileCount(deckCountLabels[plr], () -> state.deckCount[plr]);
            bindPileCount(graveCountLabels[opp], () -> state.graveCount(opp));
            bindPileCount(graveCountLabels[plr], () -> state.graveCount(plr));
            bindPileCount(extraCountLabels[opp], () -> state.extraCount(opp));
            bindPileCount(extraCountLabels[plr], () -> state.extraCount(plr));
            bindPileCount(banishedCountLabels[opp], () -> state.banishedCount(opp));
            bindPileCount(banishedCountLabels[plr], () -> state.banishedCount(plr));
        }

        private void bindLabel(UIElement element, java.util.function.Supplier<String> textSupplier) {
            if (element instanceof Label label) {
                label.bindDataSource(SupplierDataSource.of(() ->
                        Component.literal(textSupplier.get())));
            }
        }

        private void bindPileCount(Label label, java.util.function.IntSupplier countSupplier) {
            if (label != null) {
                label.bindDataSource(SupplierDataSource.of(() ->
                        Component.literal(String.valueOf(countSupplier.getAsInt()))));
            }
        }

        // ── Tick handler: process dirty flags ──

        private void onTick(UIEvent event) {
            if (!state.isDirty()) return;
            var flags = state.consumeDirtyFlags();

            int plr = state.localPlayer;
            int opp = state.opponent();

            if (flags.contains(DirtyFlag.HAND_0) || flags.contains(DirtyFlag.HAND_1)) {
                if (flags.contains(plr == 0 ? DirtyFlag.HAND_0 : DirtyFlag.HAND_1))
                    rebuildHand(plrHandContainer, state.hand[plr], plr, true);
                if (flags.contains(opp == 0 ? DirtyFlag.HAND_0 : DirtyFlag.HAND_1))
                    rebuildHand(oppHandContainer, state.hand[opp], opp, false);
            }

            if (flags.contains(plr == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1))
                refreshMonsterZones(plr);
            if (flags.contains(opp == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1))
                refreshMonsterZones(opp);

            if (flags.contains(plr == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
                refreshSpellZones(plr);
            if (flags.contains(opp == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
                refreshSpellZones(opp);

            if (flags.contains(DirtyFlag.LP))
                refreshLP();
            if (flags.contains(DirtyFlag.PILE_COUNTS))
                refreshPiles();

            if (flags.contains(ClientDuelState.DirtyFlag.PROMPT)) {
                rebuildPrompt();
                updatePhaseButtons();
            }

            if (flags.contains(DirtyFlag.CHAIN))
                updateStatusLabel();
            if (flags.contains(DirtyFlag.WINNER))
                showWinOverlay();
        }

        // ── Rebuilders ──
        private void rebuildHand(UIElement _container, List<Integer> codes, int player, boolean isLocal) {
            if (_container == null) {
                LOGGER.info("container is null");
                return;
            }
            var container = (ScrollerView) _container;
            LOGGER.debug("Rebuilding hand: player={}, cards={}", player, codes.size());

            container.clearAllScrollViewChildren();

            for (int i = 0; i < codes.size(); i++) {
                int code = codes.get(i);
                int seq = i;
                var card = new UIElement();
                card.addClasses("card-slot", "hand-card");

                if (isLocal && code != 0) {
                    setCardImageBackground(card, code);
                } else {
                    card.lss("background", CARD_BACK_SPRITE);
                }

                if (isLocal) {
                    card.addEventListener(UIEvents.CLICK, e -> onCardClicked(player, LOCATION_HAND, seq, e));
                    card.addEventListener(UIEvents.MOUSE_ENTER, e -> showCardInfo(code));
                    card.addEventListener(UIEvents.MOUSE_LEAVE, e -> hideCardInfo());
                }

                container.addScrollViewChild(card);
            }
        }

        private void rebuildPrompt() {
            LOGGER.debug("Rebuilding prompt: {}", state.pendingPrompt != null ? state.pendingPrompt.getClass().getSimpleName() : "null");
            if (state.pendingPrompt == null) {
                if (promptOverlay != null) promptOverlay.addClass("hidden");
                if (statusLabel != null) statusLabel.addClass("hidden");
                return;
            }

            switch (state.pendingPrompt) {
                // Idle/Battle Cmds
                case DuelMessage.SelectIdleCmd ignored -> {
                    isBattleCmd = false;
                    promptOverlay.addClass("hidden");
                    refreshTargetHighlights();
                }
                case DuelMessage.SelectBattleCmd ignored -> {
                    isBattleCmd = true;
                    promptOverlay.addClass("hidden");
                    refreshTargetHighlights();
                }

                // Yes/No
                case DuelMessage.SelectYesNo sel ->
                    buildYesNoPrompt("Yes or No? (desc=" + sel.desc() + ")");
                case DuelMessage.SelectEffectYn sel ->
                    buildYesNoPrompt("Activate effect? (Card " + sel.code() + ")");

                // Option
                case DuelMessage.SelectOption sel -> buildOptionPrompt("Choose Option",
                        sel.options().stream().map(String::valueOf).toList(),
                        i -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectOption(i)));
                case DuelMessage.RockPaperScissors ignored -> buildOptionPrompt("Rock Paper Scissors",
                        List.of("Rock", "Paper", "Scissors"),
                        i -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.rockPaperScissors(i + 1)));

                case DuelMessage.SelectChain sel -> buildChainPrompt(sel);

                case DuelMessage.SelectCard sel -> {
                    if (isFieldOnlySelection(sel)) enterFieldSelectionMode(sel);
                    else buildCardSelectionPrompt(sel);
                }
                case DuelMessage.SelectTribute sel -> buildTributePrompt(sel);

                case DuelMessage.SelectPosition sel -> buildPositionPrompt(sel);

                case DuelMessage.SelectPlace sel -> {
                    promptOverlay.addClass("hidden");
                    highlightValidPlaces(sel.field());
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

        private void buildYesNoPrompt(String title) {
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t) {
                t.setText(Component.literal(title));
            }
            clearPromptContent();

            var yesBtn = new Button();
            yesBtn.setText(Component.literal("Yes"));
            yesBtn.addClass("prompt-btn");
            yesBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectYesNo(true)));

            var noBtn = new Button();
            noBtn.setText(Component.literal("No"));
            noBtn.addClass("prompt-btn");
            noBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectYesNo(false)));

            promptButtons.addChild(yesBtn);
            promptButtons.addChild(noBtn);
        }

        private void buildOptionPrompt(String title, List<String> options, IntConsumer onSelect) {
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t) {
                t.setText(Component.literal(title));
            }
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
            // Auto-pass if not forced and TODO: account for holding left-click
            if (!sel.forced() && sel.chains().isEmpty()) {
                LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectChain(-1));
                return;
            }

            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t) t.setText(Component.literal("Activate Chain?"));
            clearPromptContent();

            for (int i = 0; i < sel.chains().size(); i++) {
                int idx = i;
                var chain = sel.chains().get(i);
                var btn = new Button();
                // TODO: Replace with card images
                btn.setText(Component.literal("Card" + chain.code()));
                btn.addClasses("prompt-btn");
                btn.setOnClick(e -> LDLibDuelScreen.sendResponse(state,
                        ResponseBuilder.selectChain(idx)));
                promptButtons.addChild(btn);
            }

            if (!sel.forced()) {
                var passBtn = new Button();
                passBtn.setText(Component.literal("Pass"));
                passBtn.addClasses("prompt-btn");
                passBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state,
                        ResponseBuilder.selectChain(-1)));
                promptButtons.addChild(passBtn);
            }
        }

        private void buildCardSelectionPrompt(DuelMessage.SelectCard sel) {
            selectedIndices.clear();
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t)
                t.setText(Component.literal("Select " + sel.min() + "-" + sel.max() + " card(s)"));
            clearPromptContent();

            for (int i = 0; i < sel.cards().size(); i++) {
                var entry = getButton(sel, i);
                promptBody.addChild(entry);
            }

            var confirmBtn = new Button();
            confirmBtn.setText(Component.literal("Confirm"));
            confirmBtn.addClasses("prompt-btn");
            confirmBtn.setOnClick(e -> {
                if (selectedIndices.size() >= sel.min()) {
                    LDLibDuelScreen.sendResponse(state,
                            ResponseBuilder.selectCards(selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
                }
            });
            promptButtons.addChild(confirmBtn);

            // Cancel button (if cancelable)
            if (sel.cancelable()) {
                var cancelBtn = new Button();
                cancelBtn.setText(Component.literal("Cancel"));
                cancelBtn.addClasses("prompt-btn");
                cancelBtn.setOnClick(e ->
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCardsCancel()));
                promptButtons.addChild(cancelBtn);
            }
        }

        private @NotNull Button getButton(DuelMessage.SelectCard sel, int i) {
            var card = sel.cards().get(i);
            var entry = new Button();
            entry.setText(Component.literal("Card" + card.code()));
            entry.setOnClick(e -> {
                if (selectedIndices.contains(i)) {
                    selectedIndices.remove(Integer.valueOf(i));
                    entry.removeClass("target");
                } else if (selectedIndices.size() < sel.max()) {
                    selectedIndices.add(i);
                    entry.addClass("target");
                }
            });
            return entry;
        }

        private void buildPositionPrompt(DuelMessage.SelectPosition sel) {
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t) {
                t.setText(Component.literal("Choose Position"));
            }
            clearPromptContent();
            // TODO: Replace with card images
            if (((sel.positions()) & POS_FACEUP_ATTACK) != 0) {
                addPositionButton("Face-up ATK", POS_FACEUP_ATTACK);
            }

            if (((sel.positions()) & POS_FACEDOWN_ATTACK) != 0) {
                addPositionButton("Face-down ATK", POS_FACEDOWN_ATTACK);
            }

            if (((sel.positions()) & POS_FACEUP_DEFENSE) != 0) {
                addPositionButton("Face-up DEF", POS_FACEUP_DEFENSE);
            }

            if (((sel.positions()) & POS_FACEDOWN_DEFENSE) != 0) {
                addPositionButton("Face-down DEF", POS_FACEDOWN_DEFENSE);
            }
        }

        private void addPositionButton(String label, int position) {
            var btn = new Button();
            btn.setText(Component.literal(label));
            btn.addClasses("prompt-btn");
            btn.setOnClick(e -> LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectPosition(position)));
            promptButtons.addChild(btn);
        }

        private void buildTributePrompt(DuelMessage.SelectTribute sel) {
            selectedIndices.clear();
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t)
                t.setText(Component.literal("Tribute " + sel.min() + "-" + sel.max() + " card(s)"));
            clearPromptContent();

            for (int i = 0; i < sel.cards().size(); i++) {
                int idx = i;
                var card = sel.cards().get(i);
                var entry = new Button();
                entry.setText(Component.literal("Card " + card.code()));
                entry.setOnClick(e -> {
                    if (selectedIndices.contains(idx)) {
                        selectedIndices.remove(Integer.valueOf(idx));
                        entry.removeClass("target");
                    } else if (selectedIndices.size() < sel.max()) {
                        selectedIndices.add(idx);
                        entry.addClass("target");
                    }
                });
                promptBody.addChild(entry);
            }

            var confirmBtn = new Button();
            confirmBtn.setText(Component.literal("Confirm"));
            confirmBtn.addClasses("prompt-btn");
            confirmBtn.setOnClick(e -> {
                if (selectedIndices.size() >= sel.min()) {
                    LDLibDuelScreen.sendResponse(state,
                            ResponseBuilder.selectCards(
                                    selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
                }
            });
            promptButtons.addChild(confirmBtn);

            if (sel.cancelable()) {
                var cancelBtn = new Button();
                cancelBtn.setText(Component.literal("Cancel"));
                cancelBtn.addClasses("prompt-btn");
                cancelBtn.setOnClick(e ->
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCardsCancel()));
                promptButtons.addChild(cancelBtn);
            }
        }

        private void highlightValidPlaces(int field) {
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
        }

        private void enterFieldSelectionMode(DuelMessage.SelectCard sel) {
            inFieldSelectionMode = true;
            selectedIndices.clear();
            promptOverlay.addClass("hidden");

            // Highlight valid targets on the field
            for (var card : sel.cards()) {
                var loc = new ClientDuelState.CardLocation(
                        card.controller(), card.location(), card.sequence());
                UIElement slot = findSlotForLocation(loc);
                if (slot != null) slot.addClass("selectable");
            }

            // Show minimal indicator via status label
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

        private void wirePhaseButtons() {
            if (phaseBtnLeft != null) {
                phaseBtnLeft.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle && idle.canBattle())
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(6, 0));
                });
            }
            if (phaseBtnCenter != null) {
                phaseBtnCenter.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle && battle.canMain2())
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(2, 0));
                });
            }
            if (phaseBtnRight != null) {
                phaseBtnRight.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle && idle.canEnd())
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(7, 0));
                    else if (state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle && battle.canEnd())
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(3, 0));
                });
            }
        }

        private void refreshMonsterZones(int player) {
            for (int i = 0; i < 5; i++) {
                var slot = monsterSlots[player][i];
                if (slot == null) continue;
                refreshZoneSlot(slot, state.mzone[player][i], state.mzonePos[player][i], player, LOCATION_MZONE, i);
            }

            // EMZ slots (indices 5 and 6)
            refreshZoneSlot(emzSlots[0], state.mzone[0][5], state.mzonePos[0][5], 0, LOCATION_MZONE, 5);
            refreshZoneSlot(emzSlots[1], state.mzone[1][5], state.mzonePos[1][5], 1, LOCATION_MZONE, 5);
        }

        private void refreshSpellZones(int player) {
            for (int i = 0; i < 5; i++) {
                var slot = spellSlots[player][i];
                if (slot == null) continue;
                refreshZoneSlot(slot, state.szone[player][i], state.szonePos[player][i], player, LOCATION_SZONE, i);
            }

            if (fieldSpellSlots[player] != null) {
                refreshZoneSlot(fieldSpellSlots[player], state.szone[player][5], state.szonePos[player][5], player, LOCATION_SZONE, 5);
            }
        }

        private void refreshZoneSlot(UIElement slot, int code, int position, int player, int locationType, int sequence) {
            if (slot == null) return;
            slot.getChildren().stream()
                    .filter(c -> c.hasClass("card-image") || c.hasClass("card-back"))
                    .toList()
                    .forEach(slot::removeChild);

            if (code != 0) {
                boolean faceDown = (position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
                boolean defense = (position & (POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE)) != 0;

                var cardVisual = new UIElement();
                if (faceDown) {
                    cardVisual.addClass("card-back");
                } else {
                    cardVisual.addClass("card-image");
                    cardVisual.lss("aspect-rate", "0.75");
                    cardVisual.lss("height", "100%");
                    setCardImageBackground(cardVisual, code);
                }

                if (defense) {
                    cardVisual.addClass("defense");
                }

                slot.addChild(cardVisual);

                slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            } else {
                slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            }
        }

        private void refreshLP() {
            int plr = state.localPlayer;
            int opp = state.opponent();
            updateLpBar(oppLpBar, state.lp[opp]);
            updateLpBar(plrLpBar, state.lp[plr]);
        }

        private void updateLpBar(ProgressBar bar, int lp) {
            if (bar == null) return;
            // If LP exceeds starting LP (e.g. LP gain), raise the max so the bar doesn't overflow
            float max = Math.max(state.startingLP, lp);
            bar.setMaxValue(max);
            bar.setProgress(lp);
            bar.label.setText(Component.literal(String.valueOf(lp)));
        }

        private void refreshPiles() {
            for (int p = 0; p < 2; p++) {
                // Deck & Extra: card back when non-empty
                setPileBackground(deckSlots[p], state.deckCount[p] > 0 ? CARD_BACK_SPRITE : null);
                setPileBackground(extraSlots[p], !state.extra[p].isEmpty() ? CARD_BACK_SPRITE : null);

                // Graveyard & Banished: top card image when non-empty
                setPileTopCard(graveSlots[p], state.grave[p]);
                setPileTopCard(banishedSlots[p], state.banished[p]);
            }
        }

        private void setPileBackground(UIElement slot, String background) {
            if (slot == null) return;
            if (background != null) {
                slot.lss("background", background);
                slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            } else {
                slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
                slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            }
        }

        private void setPileTopCard(UIElement slot, List<Integer> cards) {
            if (slot == null) return;
            if (!cards.isEmpty()) {
                int topCode = cards.getLast();
                CardImageManager images = DuelcraftClient.getCardImageManager();
                ResourceLocation loc = images != null ? images.getCardTexture(topCode) : null;
                if (loc != null) {
                    slot.lss("background", "sprite(" + loc + ")");
                } else {
                    slot.lss("background", CARD_BACK_SPRITE);
                }
                slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            } else {
                slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
                slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            }
        }

        private void updateStatusLabel() {
            if (statusLabel == null) return;
            if (!state.chain.isEmpty()) {
                statusLabel.removeClass("hidden");
                if (statusLabel instanceof Label lbl)
                    lbl.setText(Component.literal("Chain: " + state.chain.size()));
            } else if (state.pendingPrompt == null && !state.isLocalTurn()) {
                statusLabel.removeClass("hidden");
                if (statusLabel instanceof Label lbl)
                    lbl.setText(Component.literal("Waiting..."));
            } else {
                statusLabel.addClass("hidden");
            }
        }

        private void showWinOverlay() {
            if (promptOverlay == null) return;
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label title) {
                title.setText(Component.literal(
                        state.winner == state.localPlayer ? "You Win!" : "You Lose!"
                ));
            }
            if (promptBody != null) promptBody.clearAllChildren();
            if (promptButtons != null) {
                promptButtons.clearAllChildren();
                var closeBtn = new Button();
                closeBtn.setText(Component.literal("Close"));
                closeBtn.addClass("prompt-btn");
                closeBtn.setOnClick(e -> Minecraft.getInstance().setScreen(null));
                promptButtons.addChild(closeBtn);
            }
        }

        private void updatePhaseButtons() {
            if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle) {
                setButtonActive(phaseBtnLeft, idle.canBattle(), "BP");
                setButtonActive(phaseBtnCenter, false, "");
                setButtonActive(phaseBtnRight, idle.canEnd(), "EP");
            } else if (state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle) {
                setButtonActive(phaseBtnLeft, false, "");
                setButtonActive(phaseBtnCenter, battle.canMain2(), "M2");
                setButtonActive(phaseBtnRight, battle.canEnd(), "EP");
            } else {
                setButtonActive(phaseBtnLeft, false, "SP");
                setButtonActive(phaseBtnCenter, false, "BP");
                setButtonActive(phaseBtnRight, false, "EP");
            }
        }

        private void setButtonActive(Button btn, boolean active, String text) {
            if (btn == null) return;
            btn.setActive(active);
            if (!active) {
                btn.addClass("hidden");
            } else {
                btn.removeClass("hidden");
            }
            if (!text.isEmpty()) btn.setText(Component.literal(text));
        }

        // ── Called after a response is sent ──

        void onResponseSent() {
            if (promptOverlay != null) promptOverlay.addClass("hidden");
            hideContextMenu();
            exitFieldSelectionMode();
            ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
            updatePhaseButtons();
        }

        // ── Handlers ──
        private void onCardClicked(int player, int location, int sequence, UIEvent event) {
            var loc = new ClientDuelState.CardLocation(player, location, sequence);
            var actions = state.cardActions.get(loc);

            if (actions != null && !actions.isEmpty()) {
                event.stopPropagation();
                showContextMenu(actions, event.x, event.y);
                return;
            }

            // No actions — dismiss context menu if open
            hideContextMenu();

            if (state.pendingPrompt instanceof DuelMessage.SelectCard) {
                handleCardSelection(player, location, sequence);
            } else if (state.pendingPrompt instanceof DuelMessage.SelectPlace) {
                handlePlaceSelection(player, location, sequence);
            }
        }

        private void handleCardSelection(int player, int location, int sequence) {
            if (!(state.pendingPrompt instanceof DuelMessage.SelectCard sel)) return;

            for (int i = 0; i < sel.cards().size(); i++) {
                var card = sel.cards().get(i);
                if (card.controller() == player && card.location() == location
                        && card.sequence() == sequence) {
                    if (selectedIndices.contains(i)) {
                        selectedIndices.remove(Integer.valueOf(i));
                    } else if (selectedIndices.size() < sel.max()) {
                        selectedIndices.add(i);
                    }
                    if (selectedIndices.size() == sel.max()) {
                        LDLibDuelScreen.sendResponse(state,
                                ResponseBuilder.selectCards(
                                        selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
                    }
                    return;
                }
            }
        }

        private void handlePlaceSelection(int player, int location, int sequence) {
            if (!(state.pendingPrompt instanceof DuelMessage.SelectPlace sel)) return;

            int bit = getFieldBit(player, location, sequence);
            if ((sel.field() & bit) != 0) return; // zone is blocked

            LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectPlace(player, location, sequence));
        }

        private int getFieldBit(int player, int location, int sequence) {
            // Bitmask is relative: self=0, opponent=1. Map absolute player to bitmask position.
            int bitmaskPlayer = (player == state.localPlayer) ? 0 : 1;
            int offset = bitmaskPlayer * 16;
            if (location == LOCATION_SZONE) offset += 8;
            return 1 << (offset + sequence);
        }

        private void handleZoneClicks() {
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < 5; i++) {
                    int player = p;
                    int seq = i;
                    if (monsterSlots[p][i] != null) {
                        monsterSlots[p][i].addEventListener(UIEvents.CLICK,
                                e -> onCardClicked(player, LOCATION_MZONE, seq, e));
                        addZoneHover(monsterSlots[p][i], () -> faceUpCode(state.mzone[player][seq], state.mzonePos[player][seq]));
                    }
                    if (spellSlots[p][i] != null) {
                        spellSlots[p][i].addEventListener(UIEvents.CLICK,
                                e -> onCardClicked(player, LOCATION_SZONE, seq, e));
                        addZoneHover(spellSlots[p][i], () -> faceUpCode(state.szone[player][seq], state.szonePos[player][seq]));
                    }
                }
            }
            // EMZ slots
            for (int e = 0; e < 2; e++) {
                int idx = e;
                if (emzSlots[e] != null) {
                    addZoneHover(emzSlots[e], () -> faceUpCode(state.mzone[idx][5], state.mzonePos[idx][5]));
                }
            }
            // Field spell slots
            for (int p = 0; p < 2; p++) {
                int player = p;
                if (fieldSpellSlots[p] != null) {
                    addZoneHover(fieldSpellSlots[p], () -> faceUpCode(state.szone[player][5], state.szonePos[player][5]));
                }
            }
        }

        /** Add hover-to-inspect on a zone slot. codeSupplier reads the current card code dynamically. */
        private void addZoneHover(UIElement slot, java.util.function.IntSupplier codeSupplier) {
            slot.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                int code = codeSupplier.getAsInt();
                if (code != 0) showCardInfo(code);
            });
            slot.addEventListener(UIEvents.MOUSE_LEAVE, e -> hideCardInfo());
        }

        /** Return the card code only if it's face-up, 0 otherwise. */
        private static int faceUpCode(int code, int position) {
            if (code == 0) return 0;
            if ((position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0) return 0;
            return code;
        }

        private void handlePileClicks() {
            handlePileClick("plr-graveyard", "Your Graveyard", state.localPlayer, LOCATION_GRAVE);
            handlePileClick("opp-graveyard", "Opponent's Graveyard", state.opponent(), LOCATION_GRAVE);
            handlePileClick("plr-banished", "Your Banished", state.localPlayer, LOCATION_REMOVED);
            handlePileClick("opp-banished", "Opponent's Banished", state.opponent(), LOCATION_REMOVED);
            handlePileClick("plr-extra-deck", "Your Extra Deck", state.localPlayer, LOCATION_EXTRA);

            var closeBtn = byId("zone-inspector-close");
            if (closeBtn instanceof Button btn) {
                btn.setOnClick(e -> zoneInspector.addClass("hidden"));
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

        private void handlePileClick(String elementId, String title, int player, int location) {
            var elem = byId(elementId);
            if (elem != null) {
                elem.addEventListener(UIEvents.CLICK, e -> {
                   zoneInspector.removeClass("hidden");
                   var titleLabel = byId("zone-inspector-title");
                   var zoneInspectorList = byId("zone-inspector-list", ScrollerView.class);
                   if (titleLabel instanceof Label lbl) lbl.setText(Component.literal(title));

                   if (zoneInspectorList != null) {
                       List<Integer> cards = getPileCards(player, location);

                       zoneInspectorList.clearAllScrollViewChildren();
                       for (int i = 0; i < cards.size(); i++) {
                           int code = cards.get(i);
                           int seq = i;

                           var card = new UIElement();
                           card.addClasses("card-slot", "hand-card");
                           setCardImageBackground(card, code);

                           card.addEventListener(UIEvents.CLICK, ev -> {
                               ev.stopPropagation();
                               onCardClicked(player, location, seq, ev);
                           });
                           card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                           card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());

                           zoneInspectorList.addScrollViewChild(card);
                       }
                   }
                });
            }
        }

        // ── Helpers ──

        private String cardDisplayName(int code) {
            CardDatabase db = DuelcraftClient.getCardDatabase();
            CardInfo info = db != null ? db.getCard(code) : null;
            return info != null ? info.name() : String.valueOf(code);
        }

        private void showCardInfo(int code) {
            if (code == 0 || cardInfoBanner == null) return;
            cardInfoBanner.removeClass("hidden");

            CardDatabase db = DuelcraftClient.getCardDatabase();
            CardInfo card = db != null ? db.getCard(code) : null;

            var imageArea = byId("card-image-area");

            if (card != null) {
                setTextElement("card-name-label", card.name());
                setTextElement("card-stats-label", CardStringHelper.typeLine(card));
                setTextElement("card-text", card.desc());
                setTextElement("card-atk-def-label", CardStringHelper.atkDefLine(card));

                // Card art (cropped artwork)
                CardImageManager images = DuelcraftClient.getCardImageManager();
                if (images != null && imageArea != null) {
                    var loc = images.getCardArt(code);
                    if (loc != null) {
                        imageArea.lss("background", "sprite(" + loc + ")");
                    } else {
                        imageArea.lss("background", "sdf(#3c3c50, 3, 2)");
                    }
                }
            } else {
                setTextElement("card-name-label", "Card #" + code);
                setTextElement("card-stats-label", "");
                setTextElement("card-text", "");
                setTextElement("card-atk-def-label", "");
                if (imageArea != null)
                    imageArea.lss("background", "sdf(#3c3c50, 3, 2)");
            }
        }

        private static final String CARD_BACK_SPRITE = "sprite(duelcraft:textures/card_back.png)";

        /** Set a card's full image as the element background, card back as fallback. */
        private void setCardImageBackground(UIElement elem, int code) {
            CardImageManager images = DuelcraftClient.getCardImageManager();
            ResourceLocation loc = images != null ? images.getCardTexture(code) : null;
            elem.lss("background", loc != null ? "sprite(" + loc + ")" : CARD_BACK_SPRITE);
        }

        /** Set text on an element that could be either a Label or TextElement (XML <text> tag). */
        private void setTextElement(String id, String text) {
            var elem = byId(id);
            if (elem instanceof TextElement te) te.setText(Component.literal(text));
            else if (elem instanceof Label lbl) lbl.setText(Component.literal(text));
        }

        private void hideCardInfo() {
            if (cardInfoBanner != null) cardInfoBanner.addClass("hidden");
        }

        private void refreshTargetHighlights() {
            ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));

            for (var entry : state.cardActions.entrySet()) {
                var loc = entry.getKey();
                UIElement slot = findSlotForLocation(loc);
                if (slot != null) slot.addClass("target");
            }
        }

        private UIElement findSlotForLocation(ClientDuelState.CardLocation loc) {
            return switch (loc.location()) {
                case LOCATION_MZONE -> {
                    if (loc.sequence() < 5) yield monsterSlots[loc.controller()][loc.sequence()];
                    else if (loc.sequence() == 5) yield emzSlots[loc.controller()];
                    else yield null;
                }
                case LOCATION_SZONE -> {
                    if (loc.sequence() < 5) yield spellSlots[loc.controller()][loc.sequence()];
                    else yield fieldSpellSlots[loc.controller()];
                }
                default -> null;
            };
        }

        private boolean isFieldOnlySelection(DuelMessage.SelectCard sel) {
            return !sel.cards().isEmpty()
                    && sel.cards().stream()
                            .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0);
        }

        private Label findCardCountLabel(String parentId) {
            var parent = byId(parentId);
            if (parent == null) return null;
            // Search children for an element with the card-count class
            for (var child : parent.getChildren()) {
                if (child instanceof Label label && child.hasClass("card-count")) {
                    return label;
                }
            }
            return null;
        }

        private void clearPromptContent() {
            if (promptBody != null) {
                promptBody.clearAllChildren();
            }
            if (promptButtons != null) {
                promptButtons.clearAllChildren();
            }
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

        private void hideContextMenu() {
            if (contextMenu != null) contextMenu.addClass("hidden");
        }

        private void showContextMenu(List<ClientDuelState.CardAction> actions, float mouseX, float mouseY) {
            if (contextMenu == null) return;

            contextMenu.clearAllChildren();

            for (var action : actions) {
                var icon = new UIElement();
                icon.addClass("ctx-action");

                var info = getActionIconInfo(action.actionType(), isBattleCmd);
                icon.lss("background", "sdf(" + info.color() + ", 3, 2)");
                icon.lss("tooltips", info.tooltip());

                icon.addEventListener(UIEvents.CLICK, e -> {
                    e.stopPropagation();
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(action.actionType(),
                            action.listIndex()));
                });

                contextMenu.addChild(icon);
            }

            // Flip/nudge positioning
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
            // Clamp to not go negative
            if (x < 0) x = 0;
            if (y < 0) y = 0;

            contextMenu.lss("left", String.valueOf((int) x));
            contextMenu.lss("top", String.valueOf((int) y));
            contextMenu.removeClass("hidden");
        }
    }
}

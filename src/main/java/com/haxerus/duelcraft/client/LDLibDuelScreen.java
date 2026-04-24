package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
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
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.carddata.CardStringHelper;
import org.slf4j.Logger;

import static com.haxerus.duelcraft.core.OcgConstants.*;

import com.haxerus.duelcraft.client.ClientDuelState.DirtyFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        private final FieldRenderer field;

        // Pile count labels + slot elements
        private final Label[] deckCountLabels = new Label[2];
        private final Label[] graveCountLabels = new Label[2];
        private final Label[] extraCountLabels = new Label[2];
        private final Label[] banishedCountLabels = new Label[2];

        // Hands
        private final ScrollerView oppHandContainer;
        private final ScrollerView plrHandContainer;

        // Phase buttons
        private final Button phaseBtnLeft;
        private final Button phaseBtnCenter;
        private final Button phaseBtnRight;

        // Overlays
        private final UIElement cardInfoBanner;
        private final ZoneInspectorController zoneInspector;
        private final UIElement promptOverlay;
        private final UIElement promptTitle;
        private final UIElement promptBody;
        private final UIElement promptButtons;
        // Context menu (cursor-anchored action icons)
        private final UIElement contextMenu;

        // Card Selection
        private final Map<UIElement, Integer> pendingCardImages = new LinkedHashMap<>();
        private UIElement pendingArtElement;
        private int pendingArtCode;
        private final List<Integer> selectedIndices = new ArrayList<>();
        private final List<UIElement> sumSelectableCards = new ArrayList<>();
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

            // ── Field slots (owned by FieldRenderer) ──
            field = new FieldRenderer(ui, state, new FieldRenderer.Callbacks() {
                @Override public void setCardImageBackground(UIElement elem, int code) {
                    UIRefresher.this.setCardImageBackground(elem, code);
                }
                @Override public void onCardClicked(int player, int location, int sequence, UIEvent event) {
                    UIRefresher.this.onCardClicked(player, location, sequence, event);
                }
                @Override public void showCardInfo(int code) {
                    UIRefresher.this.showCardInfo(code);
                }
                @Override public void hideCardInfo() {
                    UIRefresher.this.hideCardInfo();
                }
                @Override public void clearPendingImage(UIElement elem) {
                    pendingCardImages.remove(elem);
                }
            });

            // ── Pile count labels ──
            deckCountLabels[opp] = findCardCountLabel("opp-deck");
            deckCountLabels[plr] = findCardCountLabel("plr-deck");
            graveCountLabels[opp] = findCardCountLabel("opp-graveyard");
            graveCountLabels[plr] = findCardCountLabel("plr-graveyard");
            extraCountLabels[opp] = findCardCountLabel("opp-extra-deck");
            extraCountLabels[plr] = findCardCountLabel("plr-extra-deck");
            banishedCountLabels[opp] = findCardCountLabel("opp-banished");
            banishedCountLabels[plr] = findCardCountLabel("plr-banished");

            // ── Hands ──
            oppHandContainer = byId("opponent-hand", ScrollerView.class);
            plrHandContainer = byId("player-hand", ScrollerView.class);

            // ── Phase buttons ──
            phaseBtnLeft = byId("phase-btn-left", Button.class);
            phaseBtnCenter = byId("phase-btn-center", Button.class);
            phaseBtnRight = byId("phase-btn-right", Button.class);

            // ── Overlays ──
            cardInfoBanner = byId("card-info-banner");
            zoneInspector = new ZoneInspectorController(ui, state, new ZoneInspectorController.Callbacks() {
                @Override public void setCardImageBackground(UIElement elem, int code) {
                    UIRefresher.this.setCardImageBackground(elem, code);
                }
                @Override public void onCardClicked(int player, int location, int sequence, UIEvent event) {
                    UIRefresher.this.onCardClicked(player, location, sequence, event);
                }
                @Override public void showCardInfo(int code) {
                    UIRefresher.this.showCardInfo(code);
                }
                @Override public void hideCardInfo() {
                    UIRefresher.this.hideCardInfo();
                }
            });
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
            field.wireFieldClicks();
            zoneInspector.wirePileClicks();

            // Refresh hand/field when card images finish downloading
            CardImageManager images = DuelcraftClient.getCardImageManager();
            if (images != null) {
                images.setOnTextureLoaded(() -> {
                    state.markAllVisualsDirty();
                    retryPendingCardImages();
                });
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

            // Right-click to cancel/finish field selection
            ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
                if (e.button != 1) return;
                if (inFieldSelectionMode
                        && state.pendingPrompt instanceof DuelMessage.SelectCard sel
                        && sel.cancelable()) {
                    e.stopPropagation();
                    exitFieldSelectionMode();
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCardsCancel());
                } else if (state.pendingPrompt instanceof DuelMessage.SelectUnselectCard sel
                        && promptOverlay.hasClass("hidden")
                        && (sel.finishable() || sel.cancelable())
                        && !sel.unselectableCards().isEmpty()) {
                    e.stopPropagation();
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCardFinish());
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

            String localName = Minecraft.getInstance().getUser().getName();

            plrLpBar.bindDataSource(SupplierDataSource.of(
                    () -> (float) state.lp[plr]
            )).label(label -> label.bindDataSource(SupplierDataSource.of(
                    () -> Component.literal(localName + ": ").append(String.valueOf(state.lp[plr]))
            )));

            oppLpBar.bindDataSource(SupplierDataSource.of(
                    () -> (float) state.lp[opp]
            )).label(label -> label.bindDataSource(SupplierDataSource.of(
                    () -> Component.literal(state.opponentName + ": ").append(String.valueOf(state.lp[opp]))
            )));

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
                field.refreshMonsterZones(plr);
            if (flags.contains(opp == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1))
                field.refreshMonsterZones(opp);

            if (flags.contains(plr == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
                field.refreshSpellZones(plr);
            if (flags.contains(opp == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
                field.refreshSpellZones(opp);

            if (flags.contains(DirtyFlag.FIELD_STATS)
                    || flags.contains(DirtyFlag.MZONE_0) || flags.contains(DirtyFlag.MZONE_1))
                field.refreshFieldStats();

            if (flags.contains(DirtyFlag.PILE_COUNTS)) {
                field.refreshPiles();
                zoneInspector.refresh();
            }

            if (flags.contains(ClientDuelState.DirtyFlag.PROMPT)) {
                rebuildPrompt();
                updatePhaseButtons();
            }

            // Turn/phase change also affects button visibility (hide on opponent's turn)
            if (flags.contains(DirtyFlag.TURN_PHASE))
                updatePhaseButtons();

            if (flags.contains(DirtyFlag.CHAIN))
                updateStatusLabel();
            if (flags.contains(DirtyFlag.WINNER))
                showWinOverlay();
            if (flags.contains(DirtyFlag.CONFIRM))
                zoneInspector.showConfirmCards();
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
                card.addClass("card");

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
                    field.refreshTargetHighlights();
                }
                case DuelMessage.SelectBattleCmd ignored -> {
                    isBattleCmd = true;
                    promptOverlay.addClass("hidden");
                    field.refreshTargetHighlights();
                }

                // Yes/No
                case DuelMessage.SelectYesNo sel ->
                    buildYesNoPrompt("Yes or No? (desc=" + sel.desc() + ")");
                case DuelMessage.SelectEffectYn sel ->
                    buildYesNoPrompt("Activate effect of " + cardDisplayName(sel.code()) + "?");

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

            var scroller = createPromptCardScroller();
            for (int i = 0; i < sel.chains().size(); i++) {
                int idx = i;
                var chain = sel.chains().get(i);
                int code = chain.code();

                var card = new UIElement();
                card.addClass("card");
                setCardImageBackground(card, code);
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
                card.addEventListener(UIEvents.CLICK, ev -> {
                    ev.stopPropagation();
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectChain(idx));
                });
                scroller.addScrollViewChild(card);
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

            var scroller = createPromptCardScroller();
            for (int i = 0; i < sel.cards().size(); i++) {
                int idx = i;
                var cardInfo = sel.cards().get(i);
                int code = cardInfo.code();

                var card = new UIElement();
                card.addClass("card");
                setCardImageBackground(card, code);
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
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

        private void buildPositionPrompt(DuelMessage.SelectPosition sel) {
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label t) {
                t.setText(Component.literal("Choose Position"));
            }
            clearPromptContent();
            // TODO: Replace with card images (face up attack and face up defense)
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

            var scroller = createPromptCardScroller();
            for (int i = 0; i < sel.cards().size(); i++) {
                int idx = i;
                var tributeCard = sel.cards().get(i);
                int code = tributeCard.code();

                var card = new UIElement();
                card.addClass("card");
                setCardImageBackground(card, code);
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
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

        private void buildFieldUnselectCardPrompt(DuelMessage.SelectUnselectCard sel) {
            promptOverlay.addClass("hidden");

            // Highlight selectable cards on field
            for (var cardInfo : sel.selectableCards()) {
                var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("selectable");
            }

            // Highlight already-selected cards on field
            for (var cardInfo : sel.unselectableCards()) {
                var loc = new ClientDuelState.CardLocation(cardInfo.controller(), cardInfo.location(), cardInfo.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("selected");
            }

            // Status label
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

            // Show selectable cards (can toggle ON)
            for (int i = 0; i < sel.selectableCards().size(); i++) {
                var cardInfo = sel.selectableCards().get(i);
                int code = cardInfo.code();
                int index = i;

                var card = new UIElement();
                card.addClass("card");
                setCardImageBackground(card, code);

                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
                card.addEventListener(UIEvents.CLICK, ev -> {
                    ev.stopPropagation();
                    LDLibDuelScreen.sendResponse(state,
                            ResponseBuilder.selectUnselectCard(index));
                });

                scroller.addScrollViewChild(card);
            }

            // Show already-selected cards (can toggle OFF) with .selected indicator
            for (int i = 0; i < sel.unselectableCards().size(); i++) {
                var cardInfo = sel.unselectableCards().get(i);
                int code = cardInfo.code();
                int index = i;

                var card = new UIElement();
                card.addClasses("card", "selected");
                setCardImageBackground(card, code);

                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
                card.addEventListener(UIEvents.CLICK, ev -> {
                    ev.stopPropagation();
                    LDLibDuelScreen.sendResponse(state,
                            ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + index));
                });

                scroller.addScrollViewChild(card);
            }

            // Finish/Cancel buttons
            if ((sel.finishable() || sel.cancelable()) && !sel.unselectableCards().isEmpty()) {
                var finishBtn = new Button();
                finishBtn.setText(Component.literal("Finish"));
                finishBtn.addClasses("prompt-btn");
                finishBtn.setOnClick(e ->
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCardFinish()));
                promptButtons.addChild(finishBtn);
            }
            if (sel.cancelable() && sel.unselectableCards().isEmpty()) {
                var cancelBtn = new Button();
                cancelBtn.setText(Component.literal("Cancel"));
                cancelBtn.addClasses("prompt-btn");
                cancelBtn.setOnClick(e ->
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCardFinish()));
                promptButtons.addChild(cancelBtn);
            }

            // Highlight on-field targets
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

            // Highlight must-select cards on field
            for (var mustCard : sel.mustSelect()) {
                var loc = new ClientDuelState.CardLocation(mustCard.controller(), mustCard.location(), mustCard.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("selected");
            }

            // Highlight selectable cards on field
            for (var sumCard : sel.selectable()) {
                var loc = new ClientDuelState.CardLocation(sumCard.controller(), sumCard.location(), sumCard.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("selectable");
            }

            // Show status label with running sum
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

            // Calculate must-select sum
            int mustSum = 0;
            for (var c : sel.mustSelect()) {
                mustSum += c.value1();
            }
            final int[] runningSum = { mustSum };

            // Title with running sum
            Runnable updateTitle = () -> {
                if (promptTitle instanceof Label t)
                    t.setText(Component.literal("Select Materials (Sum: " + runningSum[0] + " / " + sel.targetSum() + ")"));
            };
            updateTitle.run();

            var scroller = createPromptCardScroller();
            sumSelectableCards.clear();

            // Must-select cards (locked, shown with .selected, no click)
            for (var mustCard : sel.mustSelect()) {
                var card = new UIElement();
                card.addClasses("card", "selected");
                setCardImageBackground(card, mustCard.code());
                int code = mustCard.code();
                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
                scroller.addScrollViewChild(card);

                var loc = new ClientDuelState.CardLocation(mustCard.controller(), mustCard.location(), mustCard.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("selected");
            }

            // Selectable cards (toggleable via scroller or field click)
            for (int i = 0; i < sel.selectable().size(); i++) {
                var sumCard = sel.selectable().get(i);
                int code = sumCard.code();
                int value = sumCard.value1();
                int index = i;

                var card = new UIElement();
                card.addClass("card");
                setCardImageBackground(card, code);

                var loc = new ClientDuelState.CardLocation(sumCard.controller(), sumCard.location(), sumCard.sequence());
                UIElement slot = field.findSlotForLocation(loc);
                if (slot != null) slot.addClass("target");

                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
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

            // Confirm button
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
                    for (int idx : selectedIndices) {
                        allIndices[j++] = mustCount + idx;
                    }
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectSum(allIndices));
                }
            });
            promptButtons.addChild(confirmBtn);
        }

        private void enterFieldSelectionMode(DuelMessage.SelectCard sel) {
            inFieldSelectionMode = true;
            selectedIndices.clear();
            promptOverlay.addClass("hidden");

            // Highlight valid targets on the field
            for (var card : sel.cards()) {
                var loc = new ClientDuelState.CardLocation(
                        card.controller(), card.location(), card.sequence());
                UIElement slot = field.findSlotForLocation(loc);
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

        private void updateStatusLabel() {
            if (statusLabel == null) return;
            if (state.rpsHand0 > 0 && state.rpsHand1 > 0) {
                statusLabel.removeClass("hidden");
                int local = state.localPlayer == 0 ? state.rpsHand0 : state.rpsHand1;
                int remote = state.localPlayer == 0 ? state.rpsHand1 : state.rpsHand0;
                if (statusLabel instanceof Label lbl)
                    lbl.setText(Component.literal("You: " + rpsName(local) + " vs " + rpsName(remote)));
                state.rpsHand0 = 0;
                state.rpsHand1 = 0;
                return;
            }
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

        private static String rpsName(int hand) {
            return switch (hand) {
                case 1 -> "Rock";
                case 2 -> "Paper";
                case 3 -> "Scissors";
                default -> "???";
            };
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
            boolean myTurn = state.isLocalTurn();
            if (myTurn && state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle) {
                setButtonActive(phaseBtnLeft, idle.canBattle(), "BP");
                setButtonActive(phaseBtnCenter, false, "");
                setButtonActive(phaseBtnRight, idle.canEnd(), "EP");
            } else if (myTurn && state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle) {
                setButtonActive(phaseBtnLeft, false, "");
                setButtonActive(phaseBtnCenter, battle.canMain2(), "M2");
                setButtonActive(phaseBtnRight, battle.canEnd(), "EP");
            } else {
                setButtonActive(phaseBtnLeft, false, "");
                setButtonActive(phaseBtnCenter, false, "");
                setButtonActive(phaseBtnRight, false, "");
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
            ui.rootElement.select(".selected").forEach(e -> e.removeClass("selected"));
            ui.rootElement.select(".selectable").forEach(e -> e.removeClass("selectable"));
            if (statusLabel != null) statusLabel.addClass("hidden");
            updatePhaseButtons();
        }

        // ── Handlers ──
        private void onCardClicked(int player, int location, int sequence, UIEvent event) {
            var loc = new ClientDuelState.CardLocation(player, location, sequence);
            var actions = state.cardActions.get(loc);

            // Only show context menu during idle/battle command (not during sub-prompts like SelectPlace)
            if (actions != null && !actions.isEmpty()
                    && (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd
                        || state.pendingPrompt instanceof DuelMessage.SelectBattleCmd)) {
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
            } else if (state.pendingPrompt instanceof DuelMessage.SelectUnselectCard sel) {
                for (int i = 0; i < sel.selectableCards().size(); i++) {
                    var c = sel.selectableCards().get(i);
                    if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCard(i));
                        return;
                    }
                }
                for (int i = 0; i < sel.unselectableCards().size(); i++) {
                    var c = sel.unselectableCards().get(i);
                    if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                        LDLibDuelScreen.sendResponse(state,
                                ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + i));
                        return;
                    }
                }
            } else if (state.pendingPrompt instanceof DuelMessage.SelectSum sel) {
                for (int i = 0; i < sel.selectable().size(); i++) {
                    var c = sel.selectable().get(i);
                    if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
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

                        // Compute running sum
                        int sum = 0;
                        for (var m : sel.mustSelect()) sum += m.value1();
                        for (int idx : selectedIndices) sum += sel.selectable().get(idx).value1();

                        // Field mode: update status label and auto-submit when sum matches
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
                                LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectSum(allIndices));
                            }
                        } else {
                            // Dialog mode: update prompt title
                            if (promptTitle instanceof Label t)
                                t.setText(Component.literal("Select Materials (Sum: " + sum + " / " + sel.targetSum() + ")"));
                        }
                        return;
                    }
                }
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
            if (location != LOCATION_MZONE && location != LOCATION_SZONE) return;

            int bit = field.getFieldBit(player, location, sequence);
            if ((sel.field() & bit) != 0) return; // zone is blocked

            LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectPlace(player, location, sequence));
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
                        pendingArtElement = null;
                    } else {
                        imageArea.lss("background", "sdf(#3c3c50, 3, 2)");
                        pendingArtElement = imageArea;
                        pendingArtCode = code;
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

        /** Set a card's full image as the element background, card back as fallback.
         *  If the image isn't cached yet, the element is tracked for lazy update. */
        private void setCardImageBackground(UIElement elem, int code) {
            CardImageManager images = DuelcraftClient.getCardImageManager();
            ResourceLocation loc = images != null ? images.getCardTexture(code) : null;
            if (loc != null) {
                elem.lss("background", "sprite(" + loc + ")");
                pendingCardImages.remove(elem);
            } else {
                elem.lss("background", CARD_BACK_SPRITE);
                if (code != 0) pendingCardImages.put(elem, code);
            }
        }

        /** Retry setting backgrounds for elements whose images weren't cached at build time. */
        private void retryPendingCardImages() {
            CardImageManager images = DuelcraftClient.getCardImageManager();
            if (images == null) return;

            var it = pendingCardImages.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                ResourceLocation loc = images.getCardTexture(entry.getValue());
                if (loc != null) {
                    entry.getKey().lss("background", "sprite(" + loc + ")");
                    it.remove();
                }
            }

            // Retry card art in info banner
            if (pendingArtElement != null) {
                ResourceLocation loc = images.getCardArt(pendingArtCode);
                if (loc != null) {
                    pendingArtElement.lss("background", "sprite(" + loc + ")");
                    pendingArtElement = null;
                }
            }
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

        private ScrollerView createPromptCardScroller() {
            var scroller = new ScrollerView();
            scroller.addClass("prompt-card-scroller");
            promptBody.addChild(scroller);
            return scroller;
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

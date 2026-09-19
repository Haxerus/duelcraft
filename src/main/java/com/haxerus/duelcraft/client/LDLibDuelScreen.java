package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseValidator;
import com.haxerus.duelcraft.server.DuelConcedePayload;
import com.haxerus.duelcraft.server.DuelEndPayload;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
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
import com.lowdragmc.lowdraglib2.math.Size;
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
import com.haxerus.duelcraft.client.carddata.OptionTextResolver;
import com.haxerus.duelcraft.client.carddata.SystemStringTable;
import org.slf4j.Logger;

import static com.haxerus.duelcraft.core.OcgConstants.*;

import com.haxerus.duelcraft.client.ClientDuelState.DirtyFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    /** How many hits the ANNOUNCE_CARD search dialog lists at once. */
    private static final int DECLARABLE_SEARCH_LIMIT = 50;
    /** How long one hint toast stays up (edopro waits 40 frames, `duelclient.cpp:1427`). */
    private static final long TOAST_MS = 2500L;
    /** How long a HINT_ZONE flash and a HINT_CARD reveal stay up (edopro: 40 and 30 frames). */
    private static final long ZONE_FLASH_MS = 1000L;
    private static final long CARD_REVEAL_MS = 1500L;
    /** How long the attack pair, an LP number and a turn/phase banner stay up (edopro: 40, 30 and 40 frames). */
    private static final long ATTACK_MS = 1500L;
    private static final long LP_DELTA_MS = 1000L;
    private static final long BANNER_MS = 1200L;

    private static ClientDuelState activeState;
    private static ModularUI activeUI;
    private static UIRefresher refresher;
    private static DuelScreen activeScreen;

    /**
     * Load the XML UI and open the duel screen.
     */
    public static void open(DuelStartPayload startInfo) {
        Minecraft.getInstance().setScreen(create(startInfo));
        LOGGER.info("Duel screen opened (XML-based) vs. {}", startInfo.opponentName());
    }

    /** Builds the screen without showing it. The UI test harness opens the result itself. */
    public static DuelScreen create(DuelStartPayload startInfo) {
        return build(new ClientDuelState(startInfo.localPlayer(), startInfo.opponentName(),
                startInfo.lp0(), startInfo.lp1(), startInfo.deckSize(), startInfo.extraSize(), startInfo.duelFlags()));
    }

    /** Rebuilds the screen around the live duel state ({@code /duel show}); null when no duel is in progress. */
    public static DuelScreen reopen() {
        if (activeState == null) return null;
        DuelScreen screen = build(activeState);
        activeState.markAllDirty();
        return screen;
    }

    private static DuelScreen build(ClientDuelState state) {
        activeState = state;
        activeUI = loadFromXml();
        refresher = new UIRefresher(activeUI, state);
        UIElement canvas = activeUI.ui.selectId("duel-canvas").findFirst()
                .orElseThrow(() -> new IllegalStateException(DUEL_UI + " has no #duel-canvas"));
        activeScreen = new DuelScreen(activeUI, canvas, Component.literal("Duel vs. " + state.opponentName));
        return activeScreen;
    }

    /** True while a duel is in progress: a state exists and no result has arrived yet. */
    public static boolean isDuelLive() {
        return activeState != null && activeState.winner < 0;
    }

    /** ESC while the duel is live: opens the leave-duel dialog, or closes it again. */
    public static void togglePauseMenu() {
        if (refresher != null) {
            refresher.togglePauseOverlay();
        }
    }

    static void setChainSkipHeld(boolean held) {
        if (refresher != null) refresher.chainSkipHeld = held;
    }

    /**
     * A duel screen went away. Drops the statics unless the screen was replaced by a rebuild
     * ({@code /duel show}) or the duel is still live, so ESC after a result cannot leave them dangling.
     */
    static void onScreenRemoved(DuelScreen screen) {
        if (screen != activeScreen || isDuelLive()) return;
        close();
    }

    /** Records the duel result; the result overlay appears on the next tick. */
    public static void showResult(int winner, int reason) {
        if (activeState != null) {
            activeState.applyResult(winner, reason);
        }
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
        activeScreen = null;
    }

    private static ModularUI loadFromXml() {
        var doc = XmlUtils.loadXml(DUEL_UI);
        if (doc == null) {
            throw new IllegalStateException("Failed to load " + DUEL_UI);
        }
        // Parse XML to get the root element and stylesheets
        var parsed = UI.of(doc);
        // Fixed design size: LDLib2 centers the root, DuelScreen scales #duel-canvas to fit.
        var ui = UI.of(parsed.rootElement, parsed.stylesheets,
                screenSize -> Size.of(DuelScreen.DESIGN_WIDTH, DuelScreen.DESIGN_HEIGHT));
        return ModularUI.of(ui);
    }

    // ─── Response Helper ─────────────────────────────────────

    /**
     * Build a response through {@link ResponseValidator} and send it. edopro treats {@code MSG_RETRY}
     * as a bug signal, so a response the engine would reject is reported on the status label and
     * never reaches the server; the prompt stays open for another try.
     */
    static void sendResponse(ClientDuelState state, Supplier<byte[]> builder) {
        // Inspection must not submit a phase change, shuffle, or another field action.
        if (refresher != null && refresher.state == state && refresher.prompt.isInspectingField()) return;
        byte[] response;
        try {
            response = builder.get();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Duel] Rejected response for {}: {}",
                    state.pendingPrompt == null ? "no prompt" : state.pendingPrompt.getClass().getSimpleName(),
                    e.getMessage());
            if (refresher != null) refresher.showStatus(e.getMessage());
            return;
        }
        sendResponse(state, response);
    }

    private static void sendResponse(ClientDuelState state, byte[] response) {
        if (!state.markResponseSent()) {
            LOGGER.warn("Ignoring duplicate response for the current prompt");
            return;
        }
        PacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.onResponseSent();
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
        private final UIElement chainCountLabel;

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
        // Shuffle hand: shown only while the idle command offers it (engine action type 8).
        private final Button shuffleBtn;
        private final Button chainToggleBtn;
        private final Button promptToggleBtn;
        private boolean chainSkipHeld;

        // Concede opens the same confirmation dialog as ESC.
        private final Button concedeBtn;

        // Hint surfaces (MSG_HINT / MSG_CARD_HINT / MSG_PLAYER_HINT)
        private final UIElement toastLabel;
        private final UIElement hintModal;
        private final UIElement hintModalText;
        private final Button hintModalOk;
        private long toastUntil;
        /** {@code ClientDuelState.zoneFlashAt} of the flash on screen, 0 when none is. */
        private long zoneFlashStartedAt;
        private long cardRevealUntil;

        // Feedback surfaces: the attack pair, the two floating LP numbers and the turn/phase banner.
        // Each remembers the state timestamp it is showing, so a repeat restarts instead of being
        // swallowed — the same trick the HINT_ZONE flash uses.
        private final UIElement bannerLabel;
        private final UIElement[] lpDeltaLabels = new UIElement[2];
        private long attackShownAt;
        private final long[] lpDeltaShownAt = new long[2];
        private long bannerUntil;

        // Duel log panel
        private final UIElement logPanel;
        private final ScrollerView logList;
        private final Button logToggle;
        private final Button logClose;

        // Overlays
        private final UIElement cardInfoBanner;
        private long cardInfoHideAt;
        private final UIElement resultOverlay;
        private final UIElement resultTitle;
        private final UIElement resultReason;
        private final Button resultClose;
        private final UIElement pauseOverlay;
        private final Button pauseConcede;
        private final Button pauseStay;
        private final ZoneInspectorController zoneInspector;
        private final PromptController prompt;
        private final ClickDispatcher clicks;

        // Card image async retry — shared between field, prompts, and the info banner
        private final Map<UIElement, Integer> pendingCardImages = new LinkedHashMap<>();
        private UIElement pendingArtElement;
        private int pendingArtCode;

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
            chainCountLabel = byId("chain-count");
            bannerLabel = byId("banner");
            // The local player's LP bar is always the left one, so its number sits on the left.
            lpDeltaLabels[0] = byId("lp-delta-0");
            lpDeltaLabels[1] = byId("lp-delta-1");
            addClassTo(lpDeltaLabels[plr], "lp-left");
            addClassTo(lpDeltaLabels[opp], "lp-right");
            logPanel = byId("duel-log");
            logList = byId("duel-log-list", ScrollerView.class);
            logToggle = byId("log-toggle", Button.class);
            logClose = byId("duel-log-close", Button.class);

            // ── Field slots (owned by FieldRenderer) ──
            field = new FieldRenderer(ui, state, FieldLayout.fromFlags(state.duelFlags), new FieldRenderer.Callbacks() {
                @Override public void setCardImageBackground(UIElement elem, int code) {
                    UIRefresher.this.setCardImageBackground(elem, code);
                }
                @Override public void onCardClicked(int player, int location, int sequence, UIEvent event) {
                    UIRefresher.this.onCardClicked(player, location, sequence, event);
                }
                @Override public void showCardInfo(int code, ClientCard card) {
                    UIRefresher.this.showCardInfo(code, card);
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
            shuffleBtn = byId("shuffle-btn", Button.class);
            chainToggleBtn = byId("chain-toggle-btn", Button.class);
            promptToggleBtn = byId("prompt-toggle-btn", Button.class);
            // A sibling of the modal is needed for both drawing and hit testing above its dimmer.
            var canvas = byId("duel-canvas");
            var fieldArea = byId("field-area");
            var controls = byId("duel-controls");
            fieldArea.addEventListener(UIEvents.LAYOUT_CHANGED, e -> controls.layout(l -> l
                    .left(fieldArea.getPositionX() - canvas.getPositionX() + fieldArea.getSizeWidth() + 4)
                    .bottom(canvas.getPositionY() + canvas.getSizeHeight()
                            - fieldArea.getPositionY() - fieldArea.getSizeHeight() + 8)));

            concedeBtn = byId("concede-btn", Button.class);

            // ── Overlays ──
            toastLabel = byId("toast");
            toastLabel.setAllowHitTest(false);
            hintModal = byId("hint-modal");
            hintModalText = byId("hint-modal-text");
            hintModalOk = byId("hint-modal-ok", Button.class);
            cardInfoBanner = byId("card-info-banner");
            resultOverlay = byId("result-overlay");
            resultTitle = byId("result-title");
            resultReason = byId("result-reason");
            resultClose = byId("result-close", Button.class);
            pauseOverlay = byId("pause-overlay");
            pauseConcede = byId("pause-concede", Button.class);
            pauseStay = byId("pause-stay", Button.class);
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
            var descResolver = new OptionTextResolver(
                    DuelcraftClient.getCardDatabase(),
                    DuelcraftClient.getSystemStringTable());
            state.hintText = new ClientDuelState.HintText() {
                @Override public String desc(long desc) { return descResolver.resolve(desc); }
                @Override public String cardName(int code) { return UIRefresher.this.cardDisplayName(code); }
                @Override public String systemString(int code) {
                    SystemStringTable table = DuelcraftClient.getSystemStringTable();
                    return table != null ? table.getSystem(code) : null;
                }
            };
            prompt = new PromptController(ui, state, field, statusLabel, new PromptController.Callbacks() {
                @Override public void setCardImageBackground(UIElement elem, int code) {
                    UIRefresher.this.setCardImageBackground(elem, code);
                }
                @Override public void showCardInfo(int code) {
                    UIRefresher.this.showCardInfo(code);
                }
                @Override public void hideCardInfo() {
                    UIRefresher.this.hideCardInfo();
                }
                @Override public void sendResponse(Supplier<byte[]> response) {
                    LDLibDuelScreen.sendResponse(state, response);
                }
                @Override public String cardDisplayName(int code) {
                    return UIRefresher.this.cardDisplayName(code);
                }
                @Override public String resolveDesc(long desc) {
                    return descResolver.resolve(desc);
                }
                @Override public String systemString(int code) {
                    SystemStringTable table = DuelcraftClient.getSystemStringTable();
                    return table != null ? table.getSystem(code) : null;
                }
                @Override public String counterName(int counterType) {
                    SystemStringTable table = DuelcraftClient.getSystemStringTable();
                    return table != null ? table.getCounter(counterType) : null;
                }
                @Override public boolean cardSearchAvailable() {
                    return DuelcraftClient.getCardDatabase() != null;
                }
                @Override public List<CardInfo> searchDeclarable(String query, List<Long> opcodes) {
                    CardDatabase cards = DuelcraftClient.getCardDatabase();
                    return cards != null ? cards.searchDeclarable(query, opcodes, DECLARABLE_SEARCH_LIMIT) : List.of();
                }
            });
            clicks = new ClickDispatcher(ui, state, prompt, canvas,
                    response -> LDLibDuelScreen.sendResponse(state, response));

            // ── Bind reactive data ──
            bindReactiveData();

            // ── Register tick handler for dirty-flag-driven updates ──
            ui.rootElement.addEventListener(UIEvents.TICK, this::onTick);
            chainToggleBtn.getStyle().tooltips(Component.literal("Toggle optional chain prompts."),
                    Component.literal("Hold C to skip temporarily."),
                    Component.literal("Forced choices always appear."));
            chainToggleBtn.setOnClick(e -> {
                e.stopPropagation();
                if (e.button == 0 && !isBlockingOverlayUp()) {
                    state.chainPromptsEnabled = !state.chainPromptsEnabled;
                    updatePromptControls();
                }
            });
            promptToggleBtn.setOnClick(e -> {
                e.stopPropagation();
                if (e.button == 0 && !isBlockingOverlayUp()) {
                    prompt.toggleInspection();
                    if (prompt.isInspectingField()) modularUI.requestFocus(null);
                    clicks.hideContextMenu();
                    updatePromptControls();
                }
            });

            wirePhaseButtons();
            wireLifecycleButtons();
            field.wireFieldClicks();
            zoneInspector.wirePileClicks();
            clicks.wireOutsideDismiss();

            // Refresh hand/field when card images finish downloading
            CardImageManager images = DuelcraftClient.getCardImageManager();
            if (images != null) {
                images.setOnTextureLoaded(() -> {
                    state.markAllVisualsDirty();
                    retryPendingCardImages();
                });
            }

            // Right-click to cancel/finish field selection
            ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
                if (e.button != 1 || isBlockingOverlayUp()) return;
                prompt.handleRightClick(e);
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

            // The bars fill against the LP this duel started with, not the XML's 8000 default.
            plrLpBar.setMaxValue(state.startingLp[plr]);
            oppLpBar.setMaxValue(state.startingLp[opp]);

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
            bindPileCount(deckCountLabels[opp], () -> state.deckCount(opp));
            bindPileCount(deckCountLabels[plr], () -> state.deckCount(plr));
            bindPileCount(graveCountLabels[opp], () -> state.graveCount(opp));
            bindPileCount(graveCountLabels[plr], () -> state.graveCount(plr));
            bindPileCount(extraCountLabels[opp], () -> state.extraCount(opp));
            bindPileCount(extraCountLabels[plr], () -> state.extraCount(plr));
            bindPileCount(banishedCountLabels[opp], () -> state.banishedCount(opp));
            bindPileCount(banishedCountLabels[plr], () -> state.banishedCount(plr));

            // Refcounted MSG_PLAYER_HINT descs, under the name each LP bar carries.
            bindPlayerHints(byId("plr-hints", Label.class), plr, cardInfoBanner, logPanel);
            bindPlayerHints(byId("opp-hints", Label.class), opp, byId("zone-inspector"));
        }

        /** Each standing effect gets its own wrapped paragraph. */
        private String playerHintsText(int player) {
            return state.playerHints[player].keySet().stream()
                    .map(state.hintText::desc)
                    .collect(Collectors.joining("\n"));
        }

        private void bindPlayerHints(Label label, int player, UIElement... panels) {
            var canvas = byId("duel-canvas");
            Runnable positionPanels = () -> {
                float top = label.hasClass("hidden") ? 44
                        : label.getPositionY() - canvas.getPositionY() + label.getSizeHeight() + 4;
                for (var panel : panels) panel.layout(layout -> layout.top(top));
            };
            label.addEventListener(UIEvents.LAYOUT_CHANGED, event -> positionPanels.run());
            // Hidden labels do not tick; observe from the canvas so a new effect can reveal them.
            canvas.addEventListener(UIEvents.TICK, event -> {
                String text = playerHintsText(player);
                if (label.getText().getString().equals(text)) return;
                label.setText(Component.literal(text));
                if (text.isEmpty()) label.addClass("hidden");
                else label.removeClass("hidden");
                positionPanels.run();
            });
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
            updateHintSurfaces();
            updateFeedbackSurfaces();
            if (state.winner < 0 && !isBlockingOverlayUp() && (!state.chainPromptsEnabled || chainSkipHeld))
                prompt.skipOptionalChain();
            updatePromptControls();
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
                prompt.rebuild();
                updatePhaseButtons();
                zoneInspector.refresh();
            }

            // Turn/phase change also affects button visibility (hide on opponent's turn)
            if (flags.contains(DirtyFlag.TURN_PHASE))
                updatePhaseButtons();

            if (flags.contains(DirtyFlag.CHAIN)) {
                updateChainCount();
                updateStatusLabel();
            }
            // Markers live on the zone slots, so a zone rebuild drops them: redraw whenever either
            // the chain or a slot the chain could be sitting on has changed.
            if (flags.contains(DirtyFlag.CHAIN) || (!state.chain.isEmpty() && flags.stream()
                    .anyMatch(flag -> flag.name().startsWith("MZONE") || flag.name().startsWith("SZONE"))))
                field.refreshChainMarkers();
            if (flags.contains(DirtyFlag.LOG) && logPanel != null && !logPanel.hasClass("hidden"))
                rebuildLog();
            if (flags.contains(DirtyFlag.WINNER))
                showResultOverlay();
            if (flags.contains(DirtyFlag.CONFIRM))
                zoneInspector.showConfirmCards();
            updatePromptControls();
        }

        private void updatePromptControls() {
            chainToggleBtn.setText(Component.literal(chainSkipHeld ? "OFF (hold C)"
                    : state.chainPromptsEnabled ? "Chain: ON" : "Chain: OFF"));
            if (prompt.hasDialog()) promptToggleBtn.removeClass("hidden");
            else promptToggleBtn.addClass("hidden");
            promptToggleBtn.setText(Component.literal(prompt.isInspectingField() ? "Show Prompt" : "Hide Prompt"));
        }

        // ── Rebuilders ──
        private void rebuildHand(UIElement _container, List<ClientCard> cards, int player, boolean isLocal) {
            if (_container == null) {
                LOGGER.info("container is null");
                return;
            }
            var container = (ScrollerView) _container;
            LOGGER.debug("Rebuilding hand: player={}, cards={}", player, cards.size());

            container.clearAllScrollViewChildren();

            for (int i = 0; i < cards.size(); i++) {
                int code = cards.get(i).code;
                int seq = i;
                var card = new UIElement();
                card.addClass("card");

                // Center short hands without hiding overflow; LDLib2 needs the full margin shorthand for auto.
                if (i == 0 || i == cards.size() - 1) {
                    String left = i == 0 ? "auto" : "0";
                    String right = i == cards.size() - 1 ? "auto" : "0";
                    card.lss("margin", "0 " + right + " 0 " + left);
                }

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


        private void wirePhaseButtons() {
            if (phaseBtnLeft != null) {
                phaseBtnLeft.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle && idle.canBattle())
                        LDLibDuelScreen.sendResponse(state,
                                () -> ResponseValidator.selectCmd(idle, IdleAction.TO_BATTLE, 0));
                });
            }
            if (phaseBtnCenter != null) {
                phaseBtnCenter.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle && battle.canMain2())
                        LDLibDuelScreen.sendResponse(state,
                                () -> ResponseValidator.selectCmd(battle, BattleAction.TO_MAIN2, 0));
                });
            }
            if (phaseBtnRight != null) {
                phaseBtnRight.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle && idle.canEnd())
                        LDLibDuelScreen.sendResponse(state,
                                () -> ResponseValidator.selectCmd(idle, IdleAction.END_TURN, 0));
                    else if (state.pendingPrompt instanceof DuelMessage.SelectBattleCmd battle && battle.canEnd())
                        LDLibDuelScreen.sendResponse(state,
                                () -> ResponseValidator.selectCmd(battle, BattleAction.END_BATTLE, 0));
                });
            }
            if (shuffleBtn != null) {
                shuffleBtn.setOnClick(e -> {
                    if (state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle && idle.canShuffle())
                        LDLibDuelScreen.sendResponse(state,
                                () -> ResponseValidator.selectCmd(idle, IdleAction.SHUFFLE_HAND, 0));
                });
            }
        }

        /** Shows a one-off message on the status label (a rejected response); the next state change replaces it. */
        private void showStatus(String message) {
            if (statusLabel == null) return;
            statusLabel.removeClass("hidden");
            if (statusLabel instanceof Label lbl) lbl.setText(Component.literal(message));
        }

        /** Chain depth on its own element, so prompt captions and chain text stop overwriting each other. */
        private void updateChainCount() {
            if (chainCountLabel == null) return;
            if (state.chain.isEmpty()) {
                chainCountLabel.addClass("hidden");
                return;
            }
            chainCountLabel.removeClass("hidden");
            if (chainCountLabel instanceof Label lbl)
                lbl.setText(Component.literal("Chain: " + state.chain.size()));
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
            // A live prompt owns the label; PromptController wrote its caption there.
            if (state.pendingPrompt != null) return;
            if (state.waitingForOpponent || !state.isLocalTurn()) {
                statusLabel.removeClass("hidden");
                if (statusLabel instanceof Label lbl)
                    lbl.setText(Component.literal(state.waitingForOpponent
                            ? ClientDuelState.WAITING_TEXT : "Waiting..."));
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

        /** Lifecycle actions share the leave-duel confirmation dialog. */
        private void wireLifecycleButtons() {
            if (concedeBtn != null) {
                concedeBtn.setOnClick(e -> togglePauseOverlay());
            }
            if (resultClose != null) {
                resultClose.setOnClick(e -> {
                    LDLibDuelScreen.close();
                    if (!com.haxerus.duelcraft.client.interaction.PreparationRouting.restoreEditor()) Minecraft.getInstance().setScreen(null);
                });
            }
            // The pause dialog is itself the confirmation, so its Concede sends straight away.
            if (pauseConcede != null) {
                pauseConcede.setOnClick(e -> {
                    hidePauseOverlay();
                    sendConcede();
                });
            }
            if (pauseStay != null) {
                pauseStay.setOnClick(e -> hidePauseOverlay());
            }
            if (hintModalOk != null) {
                hintModalOk.setOnClick(e -> {
                    state.pendingModal = null;
                    if (hintModal != null) hintModal.addClass("hidden");
                });
            }
            if (logToggle != null) {
                logToggle.setOnClick(e -> {
                    if (logPanel == null) return;
                    if (logPanel.hasClass("hidden")) {
                        if (cardInfoBanner != null) cardInfoBanner.addClass("hidden");
                        logPanel.removeClass("hidden");
                        rebuildLog();
                    } else {
                        logPanel.addClass("hidden");
                    }
                    e.stopPropagation();
                });
            }
            if (logClose != null) {
                logClose.setOnClick(e -> {
                    if (logPanel != null) logPanel.addClass("hidden");
                });
            }
        }

        // ── Duel log panel ──

        /** Rewrites the panel from {@link DuelLog}; only called while it is open. */
        private void rebuildLog() {
            if (logList == null) return;
            logList.clearAllScrollViewChildren();
            for (var entry : state.duelLog.entries()) {
                var line = new Label();
                line.addClass("log-line");
                var text = Component.empty();
                for (var segment : entry.segments()) {
                    var part = Component.literal(segment.text());
                    switch (segment.role()) {
                        case CARD_NAME -> part.withStyle(style -> style.withColor(0xE6C878));
                        case CONTEXT -> part.withStyle(style -> style.withColor(0xA9C7EF).withBold(true));
                        case PLAIN -> { }
                    }
                    text.append(part);
                }
                line.setText(text);
                if (entry.code() != 0) {
                    int code = entry.code();
                    line.addEventListener(UIEvents.CLICK, e -> {
                        // The panel and the info banner share the left column, so the panel has to
                        // step aside for the card it just sent there.
                        if (logPanel != null) logPanel.addClass("hidden");
                        showCardInfo(code);
                        e.stopPropagation();
                    });
                }
                logList.addScrollViewChild(line);
            }
        }

        private static void addClassTo(UIElement element, String styleClass) {
            if (element != null) element.addClass(styleClass);
        }

        // ── Hint surfaces (MSG_HINT), polled each tick ──

        /**
         * Drives the four timed hint surfaces from {@link ClientDuelState}: the toast queue, the
         * HINT_ZONE flash, the HINT_CARD reveal and the HINT_MESSAGE modal. edopro blocks its
         * message loop for each of these; we let the duel run on and time them out here instead.
         */
        private void updateHintSurfaces() {
            long now = System.currentTimeMillis();

            if (toastLabel != null) {
                if (toastUntil != 0 && now >= toastUntil) {
                    toastLabel.addClass("hidden");
                    toastUntil = 0;
                }
                if (toastUntil == 0 && !state.toasts.isEmpty()) {
                    setLabelText(toastLabel, state.toasts.poll());
                    toastLabel.removeClass("hidden");
                    toastUntil = now + TOAST_MS;
                }
                if (!toastLabel.hasClass("hidden")) positionToast();
            }

            // Keyed on the hint's own timestamp, so a second HINT_ZONE restarts the flash instead
            // of being swallowed by the one already up — even when it names the same zones.
            if (state.zoneFlashMask != 0 && state.zoneFlashAt != zoneFlashStartedAt) {
                field.clearZoneFlash();
                field.flashZones(state.zoneFlashMask);
                zoneFlashStartedAt = state.zoneFlashAt;
            } else if (zoneFlashStartedAt != 0 && now - zoneFlashStartedAt > ZONE_FLASH_MS) {
                field.clearZoneFlash();
                state.zoneFlashMask = 0;
                zoneFlashStartedAt = 0;
            }

            if (state.revealCardCode != 0) {
                showCardInfo(state.revealCardCode);
                state.revealCardCode = 0;
                cardRevealUntil = now + CARD_REVEAL_MS;
            } else if (cardRevealUntil != 0 && now >= cardRevealUntil) {
                if (cardInfoBanner != null) cardInfoBanner.addClass("hidden");
                cardRevealUntil = 0;
            }

            if (cardInfoHideAt != 0 && now >= cardInfoHideAt
                    && cardInfoBanner != null && !cardInfoBanner.isSelfOrChildHover()) {
                cardInfoBanner.addClass("hidden");
                cardInfoHideAt = 0;
            }

            if (state.pendingModal != null && hintModal != null && hintModal.hasClass("hidden")) {
                setLabelText(hintModalText, state.pendingModal);
                hintModal.removeClass("hidden");
            }
        }

        /**
         * The three timed battle surfaces: the attack pair, the floating LP numbers and the
         * turn/phase banner. Each is keyed on the timestamp the state recorded, so a repeated
         * event restarts it rather than being swallowed by the one already up.
         */
        private void updateFeedbackSurfaces() {
            long now = System.currentTimeMillis();

            if (state.attack != null && state.attack.at() != attackShownAt) {
                field.clearAttack();
                field.showAttack(state.attack.attacker(), state.attack.target());
                if (state.attack.target() == null) {
                    // A direct attack is aimed at the player, so their LP bar takes the mark.
                    int defender = 1 - state.attack.attacker().controller();
                    (defender == state.localPlayer ? plrLpBar : oppLpBar).addClass("attacked");
                }
                attackShownAt = state.attack.at();
            } else if (attackShownAt != 0 && now - attackShownAt > ATTACK_MS) {
                field.clearAttack();
                state.attack = null;
                attackShownAt = 0;
            }

            for (int player = 0; player < 2; player++) {
                UIElement label = lpDeltaLabels[player];
                if (label == null) continue;
                var delta = state.lpDelta[player];
                if (delta != null && delta.at() != lpDeltaShownAt[player]) {
                    label.removeClass("lp-damage");
                    label.removeClass("lp-recover");
                    label.removeClass("lp-cost");
                    label.addClass(delta.styleClass());
                    setLabelText(label, delta.text());
                    label.removeClass("hidden");
                    lpDeltaShownAt[player] = delta.at();
                } else if (lpDeltaShownAt[player] != 0 && now - lpDeltaShownAt[player] > LP_DELTA_MS) {
                    label.addClass("hidden");
                    state.lpDelta[player] = null;
                    lpDeltaShownAt[player] = 0;
                }
            }

            // Banners queue like the toasts: a turn change and its draw phase arrive in one batch,
            // and each deserves its moment rather than the last one winning.
            if (bannerLabel != null) {
                if (bannerUntil != 0 && now >= bannerUntil) {
                    bannerLabel.addClass("hidden");
                    bannerUntil = 0;
                }
                if (bannerUntil == 0 && !state.banners.isEmpty()) {
                    setLabelText(bannerLabel, state.banners.poll());
                    bannerLabel.removeClass("hidden");
                    bannerUntil = now + BANNER_MS;
                }
            }
        }

        private static void sendConcede() {
            PacketDistributor.sendToServer(new DuelConcedePayload());
        }

        /** True while a modal, the pause dialog or the result banner owns the screen and clicks must stop there. */
        /** Keep transient results near the board, clear of any open dialog. */
        private void positionToast() {
            var canvas = byId("duel-canvas");
            var fieldArea = byId("field-area");
            float left = fieldArea.getPositionX() - canvas.getPositionX()
                    + (fieldArea.getSizeWidth() - toastLabel.getSizeWidth()) / 2;
            float top = fieldArea.getPositionY() - canvas.getPositionY()
                    + (fieldArea.getSizeHeight() - toastLabel.getSizeHeight()) / 2;
            for (String id : new String[]{"prompt-dialog", "hint-modal-dialog", "pause-dialog", "result-dialog"}) {
                var dialog = byId(id);
                var overlay = dialog.getParent();
                if (!overlay.hasClass("hidden") && !overlay.hasClass("inspection-hidden")) {
                    top = Math.min(top, dialog.getPositionY() - canvas.getPositionY()
                            - toastLabel.getSizeHeight() - 4);
                }
            }
            float hudBottom = byId("hud-bar").getPositionY() + byId("hud-bar").getSizeHeight();
            float positionedTop = Math.max(hudBottom - canvas.getPositionY() + 4, top);
            toastLabel.layout(layout -> layout.left(left).top(positionedTop));
        }

        private boolean isBlockingOverlayUp() {
            return (hintModal != null && !hintModal.hasClass("hidden"))
                    || (pauseOverlay != null && !pauseOverlay.hasClass("hidden"))
                    || (resultOverlay != null && !resultOverlay.hasClass("hidden"));
        }

        void togglePauseOverlay() {
            if (pauseOverlay == null) return;
            if (pauseOverlay.hasClass("hidden")) {
                pauseOverlay.removeClass("hidden");
            } else {
                pauseOverlay.addClass("hidden");
            }
        }

        private void hidePauseOverlay() {
            if (pauseOverlay != null) pauseOverlay.addClass("hidden");
        }

        private void showResultOverlay() {
            if (resultOverlay == null || state.winner < 0) return;
            resultOverlay.removeClass("hidden");
            setLabelText(resultTitle, state.winner == DuelEndPayload.WINNER_DRAW ? "Draw"
                    : state.winner == state.localPlayer ? "You win" : "You lose");
            setLabelText(resultReason, state.winner == DuelEndPayload.WINNER_DRAW
                    ? "No winner" : winReasonText(state.winReason));
        }

        private static void setLabelText(UIElement element, String text) {
            if (element instanceof Label label) label.setText(Component.literal(text));
        }

        /** Engine MSG_WIN reasons plus the host-synthesised ones carried by {@link DuelEndPayload}. */
        private static String winReasonText(int reason) {
            return switch (reason) {
                case DuelEndPayload.REASON_SURRENDER -> "Surrender";
                case 1 -> "Life points";
                case 2 -> "Deck out";
                case DuelEndPayload.REASON_TIMEOUT -> "Timeout";
                case DuelEndPayload.REASON_DISCONNECT -> "Disconnect";
                default -> "Card effect";
            };
        }

        private void updatePhaseButtons() {
            boolean myTurn = state.isLocalTurn();
            setShuffleVisible(myTurn && state.pendingPrompt instanceof DuelMessage.SelectIdleCmd idle
                    && idle.canShuffle());
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

        private void setShuffleVisible(boolean visible) {
            if (shuffleBtn == null) return;
            shuffleBtn.setActive(visible);
            if (visible) shuffleBtn.removeClass("hidden");
            else shuffleBtn.addClass("hidden");
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
            clicks.hideContextMenu();
            prompt.onResponseSent();
            updatePhaseButtons();
            zoneInspector.refresh();
        }

        // Exposed so FieldRenderer/ZoneInspectorController callbacks can forward here.
        private void onCardClicked(int player, int location, int sequence, UIEvent event) {
            clicks.onCardClicked(player, location, sequence, event);
        }

        // ── Helpers ──

        private String cardDisplayName(int code) {
            CardDatabase db = DuelcraftClient.getCardDatabase();
            CardInfo info = db != null ? db.getCard(code) : null;
            return info != null ? info.name() : String.valueOf(code);
        }

        private void showCardInfo(int code) {
            showCardInfo(code, null);
        }

        /** {@code onField} is the card object whose hints to list, null when there is none. */
        private void showCardInfo(int code, ClientCard onField) {
            if (code == 0 || cardInfoBanner == null) return;
            if (logPanel != null && !logPanel.hasClass("hidden")) return;
            cardInfoHideAt = 0;
            cardRevealUntil = 0;   // whatever asks for the banner takes it over from a HINT_CARD reveal
            setCardHints(onField);
            cardInfoBanner.removeClass("hidden");

            CardDatabase db = DuelcraftClient.getCardDatabase();
            CardInfo card = db != null ? db.getCard(code) : null;

            Integer markers = CardStringHelper.linkMarkers(card, onField != null ? onField.stats : null);
            if (markers == null) {
                byId("card-link-markers").addClass("hidden");
            } else {
                byId("card-link-markers").removeClass("hidden");
                int[] directions = {LINK_MARKER_TOP_LEFT, LINK_MARKER_TOP, LINK_MARKER_TOP_RIGHT,
                        LINK_MARKER_LEFT, LINK_MARKER_RIGHT,
                        LINK_MARKER_BOTTOM_LEFT, LINK_MARKER_BOTTOM, LINK_MARKER_BOTTOM_RIGHT};
                String[] names = {"tl", "t", "tr", "l", "r", "bl", "b", "br"};
                for (int i = 0; i < directions.length; i++) {
                    var arrow = byId("card-link-" + names[i]);
                    if ((markers & directions[i]) != 0) arrow.addClass("active");
                    else arrow.removeClass("active");
                }
            }

            var imageArea = byId("card-image-area");

            if (card != null) {
                setTextElement("card-name-label", card.name());
                setTextElement("card-stats-label", CardStringHelper.typeLine(card));
                setTextElement("card-text", card.desc().replace("\r\n", "\n").replace('\r', '\n'));
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

        /**
         * The card's own hints under its stats, as edopro's tooltip lines
         * ({@code event_handler.cpp:1618-1632}): the single cHint slot, then every refcounted desc.
         */
        private void setCardHints(ClientCard card) {
            var element = byId("card-hints");
            if (element == null) return;
            var lines = new ArrayList<String>();
            if (card != null) {
                if (card.hintType != 0 && card.hintValue != 0) lines.add(cardHintLine(card));
                for (long desc : card.descHints.keySet()) lines.add("* " + state.hintText.desc(desc));
            }
            if (lines.isEmpty()) {
                element.addClass("hidden");
            } else {
                setTextElement("card-hints", String.join("\n", lines));
                element.removeClass("hidden");
            }
        }

        private String cardHintLine(ClientCard card) {
            return switch (card.hintType) {
                case CHINT_TURN -> "Turns passed: " + card.hintValue;
                case CHINT_CARD -> "Declared card: " + cardDisplayName((int) card.hintValue);
                case CHINT_RACE -> "Declared Type: " + CardStringHelper.raceName(card.hintValue);
                case CHINT_ATTRIBUTE -> "Declared Attribute: " + CardStringHelper.attributeName((int) card.hintValue);
                default -> "Declared number: " + card.hintValue;   // CHINT_NUMBER
            };
        }

        private void hideCardInfo() {
            // Allow the pointer to cross the gap to the scrollable description panel.
            cardInfoHideAt = System.currentTimeMillis() + 500;
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

    }
}

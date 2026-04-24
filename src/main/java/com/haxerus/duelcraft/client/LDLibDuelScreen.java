package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
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
import com.haxerus.duelcraft.client.carddata.OptionTextResolver;
import org.slf4j.Logger;

import static com.haxerus.duelcraft.core.OcgConstants.*;

import com.haxerus.duelcraft.client.ClientDuelState.DirtyFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            var descResolver = new OptionTextResolver(
                    DuelcraftClient.getCardDatabase(),
                    DuelcraftClient.getSystemStringTable());
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
                @Override public void sendResponse(byte[] response) {
                    LDLibDuelScreen.sendResponse(state, response);
                }
                @Override public String cardDisplayName(int code) {
                    return UIRefresher.this.cardDisplayName(code);
                }
                @Override public String resolveDesc(long desc) {
                    return descResolver.resolve(desc);
                }
            });
            clicks = new ClickDispatcher(ui, state, field, prompt,
                    response -> LDLibDuelScreen.sendResponse(state, response));

            // ── Bind reactive data ──
            bindReactiveData();

            // ── Register tick handler for dirty-flag-driven updates ──
            ui.rootElement.addEventListener(UIEvents.TICK, this::onTick);

            wirePhaseButtons();
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
                if (e.button != 1) return;
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
                prompt.rebuild();
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
            prompt.showWinOverlay(state.winner == state.localPlayer,
                    () -> Minecraft.getInstance().setScreen(null));
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
            clicks.hideContextMenu();
            prompt.onResponseSent();
            updatePhaseButtons();
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

package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DuelLobbyScreen extends ManagementScreen {
    public record Target(UUID id, String name) {}
    private static final Form form = new Form();
    private static final class Form {
        UUID target;
        DuelRule rule = DuelRule.MR5;
        String lp = "8000", hand = "5", draw = "1", seed = "";
    }
    private final ClientPreparationState preparation;
    private final DuelLobbyController controller;
    private final boolean connected;
    private final BiFunction<Target, Boolean, Button> targetRenderer;
    private final Runnable unsubscribe;
    private ClientCollectionState.View collection;
    private List<Target> targets;
    private long receivedAt;
    private PreparationView timedView;
    private boolean expiryRequested;
    private String feedback = "";

    private DuelLobbyScreen(String layout, ClientPreparationState preparation, ClientCollectionState.View collection, List<Target> targets,
                            Function<PreparationCommand, CompletionStage<PreparationStatePayload>> send,
                            Consumer<ScreenRoute> navigate, boolean connected, BiFunction<Target, Boolean, Button> targetRenderer) {
        super(layout, "Duel");
        this.targetRenderer = targetRenderer;
        this.preparation = preparation; this.collection = collection; this.targets = targets; this.connected = connected;
        controller = new DuelLobbyController(preparation::view, command -> {
            feedback = "";
            send.apply(command).whenCompleteAsync((reply, error) -> {
                acknowledge();
                if (removed) return;
                feedback = error != null ? "Could not send that action. Try again."
                        : switch (reply.result()) {
                            case OK -> "";
                            case OFFLINE -> "That player is no longer online.";
                            case BUSY -> "A player already has an invitation or duel.";
                            case INELIGIBLE -> "A deck is not ready. Check your active deck and server feedback.";
                            case STALE -> "That invitation or round has ended.";
                            case INVALID -> "Check the duel settings and try again.";
                            case START_FAILED -> "The duel could not start. Try again.";
                        };
                if (reply != null && reply.result() == PreparationResult.INELIGIBLE) refreshCollection();
                update();
            }, Minecraft.getInstance()::execute);
            update();
        });
        button("lobby-home", () -> navigate.accept(ScreenRoute.HOME));
        button("lobby-close", this::onClose);
        button("lobby-select-deck", () -> navigate.accept(ScreenRoute.COLLECTION));
        button("lobby-invite", this::invite);
        button("lobby-accept", controller::onAccept);
        button("lobby-decline", controller::onDecline);
        button("lobby-cancel", controller::onCancel);
        button("lobby-rock", controller::onRock);
        button("lobby-paper", () -> controller.onHand(FirstTurnLobby.Hand.PAPER));
        button("lobby-scissors", () -> controller.onHand(FirstTurnLobby.Hand.SCISSORS));
        button("lobby-first", () -> controller.onFirst(true));
        button("lobby-second", () -> controller.onFirst(false));
        button("lobby-retry", () -> { preparation.request(new PreparationCommand.View()); refreshCollection(); });
        button("lobby-help", () -> element("management-help").setDisplay(true));
        button("help-close", () -> hide("management-help"));
        button("lobby-settings", () -> element("lobby-settings-dialog").setDisplay(true));
        button("settings-close", () -> hide("lobby-settings-dialog"));
        configureSettings();
        element("management-help").setDisplay(false);
        element("lobby-settings-dialog").setDisplay(false);
        unsubscribe = preparation.subscribe(this::received);
        received();
    }
    public static DuelLobbyScreen create() {
        var screen = new DuelLobbyScreen("duel_lobby", DuelcraftClient.preparation(), DuelcraftClient.getCollectionClient().state().view(),
                online(), DuelcraftClient.preparation()::request, DuelcraftScreens::request, true, DuelLobbyScreen::targetRow);
        if (!DuelcraftScreens.managementBlocked()) screen.refreshCollection();
        return screen;
    }
    public static DuelLobbyScreen create(ClientPreparationState preparation, ClientCollectionState.View collection, List<Target> targets,
                                         Function<PreparationCommand, CompletionStage<PreparationStatePayload>> send,
                                         Consumer<ScreenRoute> navigate) {
        return new DuelLobbyScreen("duel_lobby", preparation, collection, targets, send, navigate, false, DuelLobbyScreen::targetRow);
    }
    static DuelLobbyScreen createFixture(String layout, ClientPreparationState preparation, ClientCollectionState.View collection, List<Target> targets,
                                          Function<PreparationCommand, CompletionStage<PreparationStatePayload>> send,
                                          Consumer<ScreenRoute> navigate) {
        return new DuelLobbyScreen(layout, preparation, collection, targets, send, navigate, false, DuelLobbyScreen::targetRow);
    }
    static DuelLobbyScreen createFixture(String layout, ClientPreparationState preparation, ClientCollectionState.View collection, List<Target> targets,
                                          Function<PreparationCommand, CompletionStage<PreparationStatePayload>> send,
                                          Consumer<ScreenRoute> navigate, BiFunction<Target, Boolean, Button> targetRenderer) {
        return new DuelLobbyScreen(layout, preparation, collection, targets, send, navigate, false, targetRenderer);
    }
    private static Button targetRow(Target target, boolean selected) {
        var row = new Button();
        row.setText(Component.literal(target.name() + (selected ? "  ✓" : "")));
        return row;
    }
    public static void clearForm() { form.target = null; form.rule = DuelRule.MR5; form.lp = "8000"; form.hand = "5"; form.draw = "1"; form.seed = ""; }
    private void acknowledge() { controller.acknowledge(); }
    private static List<Target> online() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return List.of();
        return mc.getConnection().getOnlinePlayers().stream().filter(p -> !p.getProfile().getId().equals(mc.player.getUUID()))
                .map(p -> new Target(p.getProfile().getId(), p.getProfile().getName())).sorted(Comparator.comparing(Target::name)).toList();
    }
    private void refreshCollection() {
        if (!connected || DuelcraftScreens.managementBlocked()) return;
        DuelcraftClient.getCollectionClient().refresh().whenCompleteAsync((view, error) -> {
            if (removed) return;
            if (error == null) collection = view; else feedback = "Could not load your deck. Try again.";
            update();
        }, Minecraft.getInstance()::execute);
    }
    @SuppressWarnings("unchecked")
    private void configureSettings() {
        var selector = (Selector<DuelRule>) element("settings-rule");
        selector.setCandidates(List.of(DuelRule.values())).setSelected(form.rule, false);
        selector.selectorStyle(style -> style.maxItemCount(6).scrollerViewHeight(180));
        selector.setOnValueChanged(rule -> { form.rule = rule; update(); });
        selector.setFocusable(true);
        selector.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.KEY_DOWN, event -> {
            int step = switch (event.keyCode) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> -1;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> 1;
                default -> 0;
            };
            if (step != 0) {
                selector.setSelected(DuelRule.values()[Math.floorMod(form.rule.ordinal() + step, DuelRule.values().length)]);
                event.stopPropagation();
            }
        });
        var values = Map.of("settings-lp", form.lp, "settings-hand", form.hand, "settings-draw", form.draw, "settings-seed", form.seed);
        values.forEach((id, value) -> ((TextField) element(id)).setText(value, false).setTextResponder(text -> {
            switch (id) {
                case "settings-lp" -> form.lp = text;
                case "settings-hand" -> form.hand = text;
                case "settings-draw" -> form.draw = text;
                case "settings-seed" -> form.seed = text;
            }
            update();
        }));
    }
    private PlayerOptions options() { return new PlayerOptions(Integer.parseInt(form.lp), Integer.parseInt(form.hand), Integer.parseInt(form.draw)); }
    private Long seed() { return form.seed.isBlank() ? null : Long.parseLong(form.seed); }
    private String invalidSettings() {
        try {
            int lp = Integer.parseInt(form.lp); if (lp < 1 || lp > 99999) return "Life points must be 1–99999.";
        } catch (NumberFormatException error) { return "Enter a whole number for life points."; }
        try {
            int hand = Integer.parseInt(form.hand); if (hand < 0 || hand > 20) return "Starting hand must be 0–20.";
        } catch (NumberFormatException error) { return "Enter a whole number for starting hand."; }
        try {
            int draw = Integer.parseInt(form.draw); if (draw < 0 || draw > 10) return "Draw per turn must be 0–10.";
        } catch (NumberFormatException error) { return "Enter a whole number for draw per turn."; }
        try { seed(); } catch (NumberFormatException error) { return "Seed must be a signed 64-bit whole number, or empty."; }
        return "";
    }
    private void invite() {
        if (!invalidSettings().isEmpty() || form.target == null || targets.stream().noneMatch(t -> t.id().equals(form.target))) return;
        controller.submit(new PreparationCommand.Invite(form.target, form.rule, seed(), options()));
    }
    private void received() {
        if (timedView != preparation.view()) {
            timedView = preparation.view();
            receivedAt = System.currentTimeMillis();
            expiryRequested = false;
        }
        update();
    }
    private void update() {
        var view = preparation.view();
        var mode = view == null ? null : view.mode();
        boolean idle = mode == PreparationView.Mode.IDLE, invited = mode == PreparationView.Mode.INVITED;
        boolean ready = collection != null && collection.activeId() != null;
        text("lobby-deck", HomeScreen.deckSummary(collection));
        text("lobby-feedback", preparation.detail().isEmpty() ? feedback : preparation.detail());
        text("settings-error", invalidSettings());
        text("lobby-help-copy", "Close hides this screen; Cancel ends preparation.\n\n" +
                (collection != null && collection.ownershipRequired() ? "This server requires deposited card copies." : "Card ownership is optional on this server.") +
                " Legal decks and server restrictions still apply.");
        element("lobby-home").setDisplay(idle || invited || mode == null);
        element("lobby-select-deck").setDisplay(idle || invited);
        element("lobby-settings").setDisplay(idle);
        if (!idle) hide("lobby-settings-dialog");
        element("lobby-players").setDisplay(idle);
        element("lobby-invite").setDisplay(idle);
        element("lobby-invite").setActive(ready && !controller.pending() && form.target != null && invalidSettings().isEmpty()
                && targets.stream().anyMatch(t -> t.id().equals(form.target)));
        element("lobby-accept").setDisplay(invited && !view.outgoing());
        element("lobby-decline").setDisplay(invited && !view.outgoing());
        element("lobby-accept").setActive(ready && !controller.pending());
        element("lobby-decline").setActive(!controller.pending());
        element("lobby-cancel").setDisplay(invited && view.outgoing() || mode == PreparationView.Mode.RPS || mode == PreparationView.Mode.FIRST_CHOICE);
        element("lobby-cancel").setActive(!controller.pending());
        element("lobby-hands").setDisplay(mode == PreparationView.Mode.RPS);
        element("lobby-hands").setActive(mode == PreparationView.Mode.RPS && !view.ownHandSubmitted() && !controller.pending());
        element("lobby-first-choice").setDisplay(mode == PreparationView.Mode.FIRST_CHOICE && view.canChooseFirst());
        element("lobby-first-choice").setActive(!controller.pending());
        element("lobby-retry").setDisplay(view == null || !feedback.isEmpty());
        String prompt = mode == null ? "Loading…" : switch (mode) {
            case IDLE -> "Invite a player";
            case INVITED -> view.outgoing() ? "Waiting for " + view.opponentName() : view.opponentName() + " invited you to duel";
            case RPS -> view.ownHandSubmitted() ? "Waiting for " + view.opponentName() : "Rock, paper, scissors";
            case FIRST_CHOICE -> view.canChooseFirst() ? "You won the roll. Choose who goes first." : "Waiting for " + view.opponentName() + " to choose";
            case STARTING -> "Starting duel…";
            case DUEL -> "Your duel is active.";
        };
        text("lobby-prompt", prompt);
        if (idle) {
            var list = (ScrollerView) element("lobby-players"); list.clearAllScrollViewChildren();
            for (var target : targets) {
                var row = targetRenderer.apply(target, target.id().equals(form.target));
                row.setId("lobby-player-" + target.id()); row.addClass("player-row");
                bind(row, () -> { form.target = target.id(); update(); });
                list.addScrollViewChild(row);
            }
            if (targets.isEmpty()) text("lobby-prompt", "No other players are online.");
            text("lobby-options", form.rule + "\nLP " + form.lp + " / Hand " + form.hand + " / Draw " + form.draw);
        } else if (view != null && view.settings() != null) {
            var settings = view.settings();
            text("lobby-options", settings.rule() + "\nLP " + settings.options().lp() + " / Hand " + settings.options().startHand()
                    + " / Draw " + settings.options().drawPerTurn() + "\nSeed " + settings.seed());
        }
    }
    @Override public void tick() {
        super.tick();
        if (connected) {
            var current = online();
            if (!current.equals(targets)) { targets = current; update(); }
        }
        var view = preparation.view();
        boolean timed = view != null && view.remainingMillis() > 0;
        long seconds = timed ? Math.max(0, (view.remainingMillis() - (System.currentTimeMillis()-receivedAt) + 999) / 1000) : 0;
        text("lobby-countdown", timed ? seconds + "s remaining" : "");
        if (connected && timed && seconds == 0 && !expiryRequested) {
            expiryRequested = true;
            preparation.request(new PreparationCommand.View());
        }
    }
    @Override protected boolean dismiss() {
        var rule = (Selector<?>) element("settings-rule");
        if (rule.isOpen()) { rule.hide(); return true; }
        if (element("lobby-settings-dialog").isDisplayed()) { hide("lobby-settings-dialog"); return true; }
        return super.dismiss();
    }
    @Override protected com.lowdragmc.lowdraglib2.gui.ui.UIElement keyboardSurface() {
        if (element("management-help").isDisplayed()) return super.keyboardSurface();
        return element("lobby-settings-dialog").isDisplayed() ? element("lobby-settings-dialog") : super.keyboardSurface();
    }
    @Override protected void hide(String id) {
        if (id.equals("lobby-settings-dialog")) ((Selector<?>) element("settings-rule")).hide();
        super.hide(id);
    }
    @Override public void removed() { unsubscribe.run(); super.removed(); }
}

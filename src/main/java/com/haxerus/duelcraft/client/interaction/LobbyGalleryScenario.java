package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.uitest.*;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@LDLRegisterClient(name = "gallery_lobby", group = "duelcraft_gallery", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class LobbyGalleryScenario implements UIScenario {
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000005");
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        var backdrop = new com.haxerus.duelcraft.client.uitest.GalleryBackdrop();
        s.step("install Gallery backdrop", ctx -> backdrop.install());
        s.teardown("remove Gallery backdrop", ctx -> backdrop.close());
        s.step("restore iconified fixture window", ctx -> {
            var window = ctx.mc().getWindow();
            int iconified = GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_ICONIFIED);
            ctx.attach("native window before restore", "iconified=" + iconified + " framebuffer=" + window.getWidth() + "x" + window.getHeight());
            if (iconified == GLFW.GLFW_TRUE || window.getWidth() <= 1 || window.getHeight() <= 1) {
                GLFW.glfwRestoreWindow(window.getWindow());
                String[] requested = System.getProperty("ldlib2.uitest.window", "1280x720").split("x");
                GLFW.glfwSetWindowSize(window.getWindow(), Integer.parseInt(requested[0]), Integer.parseInt(requested[1]));
                GLFW.glfwPollEvents();
            }
        }).waitUntil("native framebuffer restored", ctx -> ctx.mc().getWindow().getWidth() > 100 && ctx.mc().getWindow().getHeight() > 100)
         .openScreen("baseline focus observation", ctx -> fixture(ctx, false)).awaitModularUI().ticks(3);
        focusObservation(s, "baseline");
        s.step("Gallery preflight", ctx -> {
            var original = XmlUtils.loadXml(resource("duel_lobby"));
            var result = GalleryPreflight.check(resource("uitest/gallery_lobby"), GalleryPreflight.ids(original));
            ctx.attach("preflight", result.toString());
            ctx.require("all production IDs and supported Gallery styles", result.errors().isEmpty());
        }).openScreen("Gallery real lobby", ctx -> backdrop.include(fixture(ctx, true))).awaitModularUI().ticks(3)
         .step("viewport and visible text", LobbyGalleryScenario::audit)
         .step("approved lobby composition", LobbyGalleryScenario::composition)
         .screenshot("gallery-lobby-normal")
         .step("select opponent", ctx -> ManagementTestInput.click(ctx, "#lobby-player-" + OTHER)).ticks(2)
         .check("selected card has explicit marker", ctx -> ctx.el("#lobby-player-" + OTHER).element().getClasses().contains("gallery-selected"))
         .screenshot("gallery-lobby-selected");
        focusObservation(s, "gallery");
        s.step("open Settings", ctx -> ManagementTestInput.click(ctx, "#lobby-settings")).ticks(2)
         .screenshot("gallery-lobby-settings")
         .step("Tab stays within visible Settings", ctx -> {
             for (int i = 0; i < 18; i++) {
                 ctx.screen().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                 var focused = ctx.requireUI().getFocusedElement();
                 ctx.check("Tab modal surface " + i, focused != null && ctx.el("#lobby-settings-dialog").element().isAncestorOf(focused));
             }
         }).step("open actual rule popup", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(3)
         .step("rule geometry", ctx -> {
             ctx.check("popup layout stable", !ctx.requireUI().getStyleEngine().requireCalculate());
             GalleryVisualAssertions.control(ctx, "#settings-close");
             var nativeScroll = selector(ctx).scrollerView.verticalScroller;
             // Selector's constructor deliberately hides popup arrows; retain its native component identity.
             ctx.check("selector retains hidden native arrows", !nativeScroll.headButton.isDisplayed() && !nativeScroll.tailButton.isDisplayed());
             ctx.check("selector retains thin track and thumb", nativeScroll.scrollContainer.getSizeWidth() <= 4.1f && nativeScroll.scrollBar.getSizeWidth() <= 4.1f);
             ctx.check("selector retains native thumb and role", nativeScroll.scrollBar.getButtonStyle().baseTexture() == Sprites.SCROLL_BAR_V
                     && !nativeScroll.scrollBar.getClasses().contains("gallery-action") && !nativeScroll.headButton.getClasses().contains("gallery-action")
                     && !nativeScroll.tailButton.getClasses().contains("gallery-action"));
             var selector = selector(ctx);
             var anchor = ctx.el("#settings-rule").bounds();
             var popup = ElementBounds.of(selector.dialog);
             ctx.check("popup aligned to selector", Math.abs(popup.x() - anchor.x()) < 1 && Math.abs(popup.y() - anchor.bottom()) < 1);
             ctx.check("six or more actual rules", selector.getCandidates().size() >= 6);
             ctx.check("popup hosted at root", selector.dialog.getParent() == ctx.requireUI().ui.rootElement);
             var dialogBounds = ElementBounds.of(selector.dialog);
             for (var child : selector.dialog.selfAndAllChildren().toList()) {
                 var b = ElementBounds.of(child);
                 if (child.isDisplayed() && child.isVisible() && b.width() > 0)
                     ctx.check("popup child horizontal containment " + child.getClasses(), b.x() >= dialogBounds.x() - 1 && b.right() <= dialogBounds.right() + 1);
             }
             var viewport = ElementBounds.of(selector.scrollerView.viewPort);
             List<Button> buttons = new ArrayList<>(buttons(ctx).values());
             buttons.sort(Comparator.comparingDouble(Button::getPositionY));
             int visible = 0;
             float bottom = -Float.MAX_VALUE;
             for (var button : buttons) {
                 var b = ElementBounds.of(button);
                 ctx.check("rule hit rows do not overlap", b.y() >= bottom - 1); bottom = b.bottom();
                 DuelUiAssertions.textFits(ctx, (TextElement) button.getParent());
                 DuelUiAssertions.contains(ctx, b, ElementBounds.of(button.getParent()), "rule click target contains text");
                 if (b.y() >= viewport.y() - 1 && b.bottom() <= viewport.bottom() + 1) visible++;
             }
             ctx.attach("rule bounds", "viewport=" + viewport + " rows=" + buttons.stream().map(ElementBounds::of).toList());
             ctx.check("at least five complete rows", visible >= 5);
         }).screenshot("gallery-lobby-rule-popup")
         .step("select first rule by mouse", ctx -> choose(ctx, DuelRule.MR1)).ticks(2)
         .step("reopen rule popup", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(2)
         .step("select middle rule by mouse", ctx -> choose(ctx, DuelRule.MR3)).ticks(2)
         .step("reopen to scroll", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(2)
         .step("wheel to last rule", ctx -> {
             var point = ElementBounds.of(selector(ctx).scrollerView.viewPort).center();
             for (int i = 0; i < 12; i++) ctx.input().scroll(point.x, point.y, -1);
         }).ticks(3).screenshot("gallery-lobby-rule-scrolled")
         .step("select scrolled last rule by mouse", ctx -> choose(ctx, DuelRule.RUSH)).ticks(2)
         .step("reopen selector for Escape", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(2)
         .key(GLFW.GLFW_KEY_ESCAPE)
         .check("Escape closes only selector", ctx -> !selector(ctx).isOpen())
         .checkVisible("#lobby-settings-dialog")
         .typeInto("#settings-lp", "0").ticks(2)
         .checkTextContains("#settings-error", "Life points")
         .check("invalid settings disable invite", ctx -> !ctx.el("#lobby-invite").isActive())
         .step("invalid Settings text audit", LobbyGalleryScenario::audit).screenshot("gallery-lobby-invalid-settings")
         .typeInto("#settings-lp", "8000").ticks(2)
         .check("actual field has visible focus", ctx -> ctx.el("#settings-lp").element().isFocused())
         .screenshot("gallery-lobby-field-focus")
         .key(GLFW.GLFW_KEY_ESCAPE).checkHidden("#lobby-settings-dialog")
         .check("hidden field focus cleared", ctx -> !ctx.el("#settings-lp").element().isFocused())
         .step("type after dismissal", ctx -> ctx.screen().charTyped('j', 0))
         .checkText("#settings-lp", "8000")
         .step("send local invite", ctx -> ManagementTestInput.click(ctx, "#lobby-invite")).ticks(2)
         .check("real invite uses selected target and rule", ctx -> ctx.get("command") instanceof PreparationCommand.Invite invite
                 && invite.target().equals(OTHER) && invite.rule() == DuelRule.RUSH)
         .check("pending invite disables action", ctx -> !ctx.el("#lobby-invite").isActive())
         .step("native pending presentation", ctx -> GalleryVisualAssertions.disabled(ctx, "#lobby-invite"))
         .screenshot("gallery-lobby-pending")
         .openScreen("zero-player lobby", ctx -> backdrop.include(fixture(ctx, true, 0))).awaitModularUI().ticks(3)
         .checkHidden("#gallery-roster-slot").checkCount(".player-row", 0).checkTextContains("#lobby-prompt", "No other players")
         .check("zero roster cannot invite", ctx -> !ctx.el("#lobby-invite").isActive())
         .screenshot("gallery-lobby-empty")
         .openScreen("one-player lobby", ctx -> backdrop.include(fixture(ctx, true, 1))).awaitModularUI().ticks(3)
         .checkCount(".player-avatar", 1)
         .step("one card stays centered", ctx -> ctx.check("one card centered", Math.abs(ctx.all(".player-row").getFirst().bounds().centerX() - ctx.el("#lobby-players").bounds().centerX()) < 8))
         .screenshot("gallery-lobby-one")
         .openScreen("long-roster lobby", ctx -> backdrop.include(fixture(ctx, true, 18))).awaitModularUI().ticks(3)
         .checkCount(".player-avatar", 18).checkCount(".player-name", 18).checkCount(".player-availability", 18)
         .step("long roster retains card structure", ctx -> {
             GalleryVisualAssertions.scroller(ctx, "#lobby-players");
             ctx.put("roster-before", ctx.all(".player-row").getLast().bounds().y());
             var point = ctx.el("#lobby-players").bounds().center();
             for (int i = 0; i < 300; i++) ctx.input().scroll(point.x, point.y, -1);
         }).ticks(3)
         .step("long roster scrolls to last identity", ctx -> {
             var last = ctx.all(".player-row").getLast().bounds();
             var viewport = ElementBounds.of(ctx.el("#lobby-players").as(ScrollerView.class).viewPort);
             ctx.check("roster moved", last.y() < (Float) ctx.get("roster-before") - 100);
             DuelUiAssertions.contains(ctx, viewport, last, "last complete player card visible");
             ManagementTestInput.click(ctx, "#lobby-player-" + targetId(17));
         }).ticks(3)
         .checkCount(".player-avatar", 18)
         .check("last selected identity survives controller update", ctx -> ctx.el("#lobby-player-" + targetId(17)).element().getClasses().contains("gallery-selected"))
         .step("show selected last card", ctx -> ctx.el("#lobby-players").as(ScrollerView.class).scrollToChild(ctx.el("#lobby-player-" + targetId(17)).element())).ticks(3)
         .screenshot("gallery-lobby-long-roster")
         .teardown("close fixture", ctx -> { ctx.mc().setScreen(null); DuelLobbyScreen.clearForm(); });
    }
    private static void composition(TestContext ctx) {
        var cards = ctx.all(".player-row");
        ctx.check("three normal player cards", cards.size() == 3);
        var stage = ctx.el("#lobby-players").bounds();
        float previous = -1;
        for (var card : cards) {
            var b = card.bounds();
            for (var child : card.element().getChildren()) if (child.isDisplayed())
                DuelUiAssertions.contains(ctx, b, ElementBounds.of(child), "card contains its avatar/name/availability");
            ctx.check("horizontal card shape", b.width() < stage.width()/2 && b.height() > b.width()*.8f);
            ctx.check("cards run left to right", b.x() > previous); previous = b.x();
        }
        ctx.check("avatar name availability per card", ctx.all(".player-avatar").size() == 3
                && ctx.all(".player-name").size() == 3 && ctx.all(".player-availability").size() == 3);
        var invite = ctx.el("#lobby-invite").bounds();
        ctx.check("Invite centered below roster", Math.abs(invite.centerX() - stage.centerX()) < 1 && invite.y() >= stage.bottom());
        ctx.check("active deck artwork", ctx.all("#gallery-lobby-art").size() == 1);
        DuelUiAssertions.textFits(ctx, ctx.el("#gallery-lobby-title").as(TextElement.class));
        var deck = ctx.el("#lobby-footer").bounds();
        var change = ctx.el("#lobby-select-deck").bounds();
        var settings = ctx.el("#lobby-settings").bounds();
        ctx.check("deck strip below decision", deck.y() > invite.bottom());
        ctx.check("top-right deck/settings grouping", Math.abs(change.y() - settings.y()) < 1
                && settings.x() > change.right() && settings.centerX() > stage.right());
        for (String id : List.of("lobby-home", "lobby-select-deck", "lobby-settings", "lobby-help", "lobby-close", "lobby-invite"))
            GalleryVisualAssertions.control(ctx, "#" + id);
    }
    private static ResourceLocation resource(String name) { return ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/" + name + ".xml"); }
    private static DuelLobbyScreen fixture(TestContext ctx, boolean gallery) { return fixture(ctx, gallery, 3); }
    private static DuelLobbyScreen fixture(TestContext ctx, boolean gallery, int count) {
        DuelLobbyScreen.clearForm();
        var state = new ClientPreparationState(request -> {}); state.connect();
        state.receive(new PreparationStatePayload(null, PreparationResult.OK,
                new PreparationView(0, PreparationView.Mode.IDLE, null, null, null, "", false, false, false, 0, null)));
        var id = UUID.randomUUID();
        var collection = new ClientCollectionState.View(0, Map.of(), List.of(new CollectionReply.Summary(id, "Dragon study", 40, 0, 2)), id);
        var targets = new ArrayList<DuelLobbyScreen.Target>();
        String[] names = {"Alex", "Rowan", "Mira"};
        for (int i = 0; i < count; i++) targets.add(new DuelLobbyScreen.Target(targetId(i), i < 3 ? names[i] : "Player " + (i + 1)));
        java.util.function.Function<PreparationCommand, java.util.concurrent.CompletionStage<PreparationStatePayload>> send = command -> {
            ctx.put("command", command); return new CompletableFuture<>();
        };
        if (!gallery) return DuelLobbyScreen.create(state, collection, targets, send, route -> ctx.put("route", route));
        var screen = DuelLobbyScreen.createFixture("uitest/gallery_lobby", state, collection, targets, send, route -> ctx.put("route", route), LobbyGalleryScenario::playerCard);
        ((ScrollerView) screen.element("lobby-right")).viewContainer.addClass("gallery-stage");
        ((ScrollerView) screen.element("lobby-players")).viewContainer.addClass("gallery-roster");
        ((ScrollerView) screen.element("lobby-right")).viewPort.addClass("gallery-stage-viewport");
        ((ScrollerView) screen.element("lobby-players")).viewPort.addClass("gallery-roster-viewport");
        alignRulePopup(screen);
        screen.text("gallery-roster-count", count + (count == 1 ? " player available" : " players available"));
        if (count == 0) screen.element("lobby-root").addClass("empty-roster");
        return screen;
    }
    private static void alignRulePopup(DuelLobbyScreen screen) {
        var selector = (Selector<?>) screen.element("settings-rule");
        selector.scrollerView.setId("gallery-rule-scroller");
        // Pinned LDLib's root-hosted popup clamp uses unscaled screen coordinates.
        selector.dialog.getBubbleListeners(UIEvents.LAYOUT_CHANGED).forEach(listener ->
                selector.dialog.removeEventListener(UIEvents.LAYOUT_CHANGED, listener));
        selector.dialog.addEventListener(UIEvents.LAYOUT_CHANGED, event -> {
            var dialog = selector.dialog;
            var root = screen.element("lobby-root");
            float width = Math.max(selector.getSizeWidth(), 50);
            float left = Math.clamp(selector.getPositionX() - root.getPositionX(), 0, Math.max(0, root.getSizeWidth() - width));
            float top = Math.clamp(selector.getPositionY() + selector.getSizeHeight() - root.getPositionY(), 0,
                    Math.max(0, root.getSizeHeight() - dialog.getSizeHeight()));
            if (Math.abs(dialog.getPositionX() - root.getPositionX() - left) > .1f
                    || Math.abs(dialog.getPositionY() - root.getPositionY() - top) > .1f
                    || Math.abs(dialog.getSizeWidth() - width) > .1f)
                dialog.layout(layout -> layout.left(left).top(top).width(width));
        });
    }
    private static UUID targetId(int index) { return index == 0 ? OTHER : new UUID(0, 100 + index); }
    private static Button playerCard(DuelLobbyScreen.Target target, boolean selected) {
        var card = new Button();
        card.setText(target.name() + (selected ? "  ✓" : ""));
        if (selected) card.addClass("gallery-selected");
        var avatar = new Label(); avatar.setText(target.name().substring(0, 1)); avatar.addClass("player-avatar");
        var name = new Label(); name.setText(target.name()); name.addClass("player-name");
        var availability = new Label(); availability.setText(selected ? "✓ Available" : "Available"); availability.addClass("player-availability");
        card.addChildren(avatar, name, availability);
        return card;
    }
    private static void focusObservation(ScenarioBuilder s, String label) {
        s.key(GLFW.GLFW_KEY_TAB).ticks(2)
         .step(label + " focus Settings by routed Tab", ctx -> {
             for (int i = 0; i < 24 && !ctx.el("#lobby-settings").element().isFocused(); i++) ctx.screen().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
             ctx.require("initiator focused", ctx.el("#lobby-settings").element().isFocused());
             ctx.attach(label + " before opening", focused(ctx));
         }).screenshot(label + "-lobby-settings-trigger-focus")
         .key(GLFW.GLFW_KEY_ENTER).ticks(2).checkVisible("#lobby-settings-dialog")
         .typeInto("#settings-lp", "8000").ticks(2).key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .step(label + " observe restoration", ctx -> {
             ctx.attach(label + " after dismissal", focused(ctx));
             ctx.attach("restoration requirement", ctx.el("#lobby-settings").element().isFocused() ? "RESTORED" : "UNMET: initiating Settings control not restored; observation only, no production fix");
             ctx.check("hidden field loses focus", !ctx.el("#settings-lp").element().isFocused());
         });
    }
    private static String focused(TestContext ctx) { var e = ctx.requireUI().getFocusedElement(); return e == null ? "none" : e.getId(); }
    private static Selector<?> selector(TestContext ctx) { return ctx.el("#settings-rule").as(Selector.class); }
    @SuppressWarnings("unchecked")
    private static Map<DuelRule, Button> buttons(TestContext ctx) { return ctx.getField(selector(ctx), "candidateButtons"); }
    private static void choose(TestContext ctx, DuelRule rule) {
        var b = ElementBounds.of(buttons(ctx).get(rule));
        var viewport = ElementBounds.of(selector(ctx).scrollerView.viewPort);
        DuelUiAssertions.contains(ctx, viewport, b, "requested rule row visible");
        ctx.input().moveTo(b.centerX(), b.centerY());
        ctx.input().mouseDown(b.centerX(), b.centerY(), 0); ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        ctx.check("mouse chose " + rule, selector(ctx).getValue() == rule && !selector(ctx).isOpen());
    }
    private static void audit(TestContext ctx) {
        var w = ctx.mc().getWindow();
        ctx.require("native framebuffer is reviewable", w.getWidth() > 100 && w.getHeight() > 100);
        ctx.attach("viewport", "requestedWindow=%s requestedScale=%s framebuffer=%dx%d GUI=%dx%d effectiveScale=%.1f design=1280x720 panel=%s".formatted(
                System.getProperty("ldlib2.uitest.window"), System.getProperty("ldlib2.uitest.guiScale"), w.getWidth(), w.getHeight(),
                w.getGuiScaledWidth(), w.getGuiScaledHeight(), w.getGuiScale(), ctx.el("#lobby-panel").bounds()));
        for (var ref : ctx.all("*")) {
            var e = ref.element(); boolean visible = true;
            for (var p = e; p != null; p = p.getParent()) if (!p.isDisplayed() || !p.isVisible()) { visible = false; break; }
            if (visible && e instanceof TextElement text && e.getSizeWidth() > 0 && e.getSizeHeight() > 0) DuelUiAssertions.textFits(ctx, text);
        }
    }
}

package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.uitest.*;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
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
        s.openScreen("baseline focus observation", ctx -> fixture(ctx, false)).awaitModularUI().ticks(3);
        focusObservation(s, "baseline");
        s.step("Gallery preflight", ctx -> {
            var original = XmlUtils.loadXml(resource("duel_lobby"));
            var result = GalleryPreflight.check(resource("uitest/gallery_lobby"), GalleryPreflight.ids(original));
            ctx.attach("preflight", result.toString());
            ctx.require("all production IDs and supported Gallery styles", result.errors().isEmpty());
        }).openScreen("Gallery real lobby", ctx -> fixture(ctx, true)).awaitModularUI().ticks(3)
         .step("viewport and visible text", LobbyGalleryScenario::audit)
         .checkCount(".player-row", 7)
         .step("select opponent", ctx -> ManagementTestInput.click(ctx, "#lobby-player-" + OTHER)).ticks(2)
         .checkTextContains("#lobby-player-" + OTHER, "✓")
         .screenshot("gallery-lobby-selected");
        focusObservation(s, "gallery");
        s.step("open Settings", ctx -> ManagementTestInput.click(ctx, "#lobby-settings")).ticks(2)
         .step("Tab stays within visible Settings", ctx -> {
             for (int i = 0; i < 18; i++) {
                 ctx.screen().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                 var focused = ctx.requireUI().getFocusedElement();
                 ctx.check("Tab modal surface " + i, focused != null && ctx.el("#lobby-settings-dialog").element().isAncestorOf(focused));
             }
         }).step("open actual rule popup", ctx -> ManagementTestInput.click(ctx, "#settings-rule")).ticks(3)
         .step("rule geometry", ctx -> {
             var selector = selector(ctx);
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
         .screenshot("gallery-lobby-pending")
         .teardown("close fixture", ctx -> { ctx.mc().setScreen(null); DuelLobbyScreen.clearForm(); });
    }
    private static ResourceLocation resource(String name) { return ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/" + name + ".xml"); }
    private static DuelLobbyScreen fixture(TestContext ctx, boolean gallery) {
        DuelLobbyScreen.clearForm();
        var state = new ClientPreparationState(request -> {}); state.connect();
        state.receive(new PreparationStatePayload(null, PreparationResult.OK,
                new PreparationView(0, PreparationView.Mode.IDLE, null, null, null, "", false, false, false, 0, null)));
        var id = UUID.randomUUID();
        var collection = new ClientCollectionState.View(0, Map.of(), List.of(new CollectionReply.Summary(id, "Spell toolbox", 40, 5, 5)), id);
        var targets = new ArrayList<DuelLobbyScreen.Target>(); targets.add(new DuelLobbyScreen.Target(OTHER, "Other player"));
        for (int i = 0; i < 6; i++) targets.add(new DuelLobbyScreen.Target(UUID.randomUUID(), "Player " + i));
        java.util.function.Function<PreparationCommand, java.util.concurrent.CompletionStage<PreparationStatePayload>> send = command -> {
            ctx.put("command", command); return new CompletableFuture<>();
        };
        if (!gallery) return DuelLobbyScreen.create(state, collection, targets, send, route -> ctx.put("route", route));
        return DuelLobbyScreen.createFixture("uitest/gallery_lobby", state, collection, targets, send, route -> ctx.put("route", route));
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

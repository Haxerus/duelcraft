package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "collection_layout", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionLayoutScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("collection").requiresWorld(false);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("sample collection", ctx -> {
            var model = ctx.put("model", CollectionFixture.model(false));
            return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                    CollectionFixture.LIST_ID, (name, draft) -> {
                        ctx.put("saved", draft);
                        return CollectionFixture.saved(name, draft);
                    }, CollectionFixture.query());
        }).awaitModularUI().ticks(3).click("#main-card-0").ticks(2);
        for (var id : new String[]{"collection-root", "collection-canvas", "editor-header", "save-deck",
                "editor-body", "card-inspector", "card-details-scroll", "card-edit-controls", "add-card",
                "remove-card", "section-deck", "section-side", "main-header", "main-grid", "extra-header", "extra-grid", "side-header",
                "side-grid", "collection-search", "collection-results", "filter-panel", "filter-apply",
                "editor-footer", "card-density", "editor-status"}) s.checkExists("#" + id);
        bounds(s);
        s.check("sample counts", ctx -> {
            DeckEditorModel model = ctx.get("model");
            return model.draft().main().size() == 40 && model.draft().extra().size() == 5
                    && model.draft().side().isEmpty() && model.missing(CollectionFixture.FIRST) == 1;
        }).click("#collection-card-10003").ticks(2)
         .checkTextContains("#inspector-name", "Cinder Dragon")
         .click("#main-card-0").ticks(2)
         .checkTextContains("#inspector-name", "Amber Dragon")
         .checkTextContains("#inspector-missing", "1")
         .step("effective viewport", CollectionLayoutScenario::recordViewport)
         .hoverAt(-100, -100).screenshot("collection-standard")
         .click("#toggle-filters").ticks(2)
         .checkVisible("#filter-panel").checkBounds("#filter-apply", DuelScreenScenario::insideViewport)
         .screenshot("collection-filters")
         .step("press Apply", ctx -> press(ctx, "#filter-apply"))
         .step("release Apply", CollectionLayoutScenario::release).ticks(2)
         .teardown("retain sample for playtest when requested", ctx -> {
             if (!Boolean.getBoolean("ldlib2.uitest.keepOpen")) ctx.mc().setScreen(null);
         });
    }

    static void bounds(ScenarioBuilder s) {
        for (var id : new String[]{"collection-canvas", "editor-header", "save-deck", "editor-body",
                "card-inspector", "card-details-scroll", "card-edit-controls", "add-card", "remove-card",
                "main-header", "main-grid", "extra-header", "extra-grid", "side-header",
                "collection-search", "collection-results", "editor-footer", "card-density", "editor-status"}) {
            s.checkExists("#" + id).checkVisible("#" + id)
             .checkBounds("#" + id, b -> b.width() > 0 && b.height() > 0 && DuelScreenScenario.insideViewport(b));
        }
        s.check("columns do not overlap", ctx -> {
            var left = ctx.el("#card-inspector").bounds();
            var center = ctx.el("#main-grid").bounds();
            var right = ctx.el("#collection-results").bounds();
            return left.x() + left.width() <= center.x() + 1 && center.x() + center.width() <= right.x() + 1;
        });
    }

    static void recordViewport(TestContext ctx) {
        var w = ctx.mc().getWindow();
        ctx.attach("viewport", "framebuffer=%dx%d GUI=%dx%d effectiveScale=%.1f design=1280x720".formatted(
                w.getWidth(), w.getHeight(), w.getGuiScaledWidth(), w.getGuiScaledHeight(), w.getGuiScale()));
    }

    /** LDLib buttons act on press; retain the pointer when the action removes its own target. */
    static void press(TestContext ctx, String selector) {
        var target = ctx.el(selector);
        var bounds = target.bounds();
        ctx.require("click target is visible: " + selector, target.isVisible() && bounds.width() > 0
                && bounds.height() > 0 && DuelScreenScenario.insideViewport(bounds));
        ctx.put("pressedX", bounds.center().x);
        ctx.put("pressedY", bounds.center().y);
        ctx.input().moveTo(bounds.center().x, bounds.center().y);
        ctx.input().mouseDown(bounds.center().x, bounds.center().y, 0);
    }

    static void release(TestContext ctx) {
        ctx.input().mouseUp(ctx.<Float>get("pressedX"), ctx.<Float>get("pressedY"), 0);
    }

    static boolean contained(ElementBounds inner, ElementBounds outer) {
        return inner.x() >= outer.x() - 1 && inner.y() >= outer.y() - 1
                && inner.x() + inner.width() <= outer.x() + outer.width() + 1
                && inner.y() + inner.height() <= outer.y() + outer.height() + 1;
    }
}

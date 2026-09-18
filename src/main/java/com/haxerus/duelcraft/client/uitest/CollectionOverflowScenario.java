package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CollectionCardGrid;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.collection.DeckList;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@LDLRegisterClient(name = "collection_overflow", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionOverflowScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("collection").requiresWorld(false);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("full sample deck", ctx -> {
            var model = ctx.put("model", CollectionFixture.model(true));
            return CollectionScreen.create(model, CollectionFixture.cards(), code -> null, CollectionFixture.LIST_ID,
                    (name, draft) -> { ctx.put("saved", draft); return CollectionFixture.saved(name, draft); }, CollectionFixture.query());
        }).awaitModularUI().ticks(3)
         .click("#card-density").ticks(2)
         .click("#section-side").ticks(2);
        CollectionLayoutScenario.bounds(s);
        s.checkVisible("#side-grid")
         .check("Large density layout settles", ctx -> !ctx.requireUI().getStyleEngine().requireCalculate())
         .check("all deck rows have nonoverlapping bounds", CollectionOverflowScenario::rowsDoNotOverlap)
         .step("effective viewport", CollectionLayoutScenario::recordViewport)
         .screenshot("collection-large-expanded")
         .step("scroll Side before collapsing", ctx -> ctx.el("#side-grid").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(1, true)).ticks(2)
         .step("remember Side pixels", ctx -> ctx.put("sideBeforeCollapse", pixels(ctx, "side-grid")))
         .click("#toggle-side").ticks(2)
         .click("#extra-card-0").click("#section-main").click("#add-card").ticks(2)
         .click("#remove-card").ticks(2)
         .click("#toggle-side").ticks(2)
         .check("editing Main preserves collapsed Side scroll", ctx -> ctx.<Float>get("sideBeforeCollapse") > 0
                 && Math.abs(pixels(ctx, "side-grid") - ctx.<Float>get("sideBeforeCollapse")) < 1)
         .step("reset Side scroll", ctx -> ctx.el("#side-grid").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(0, true)).ticks(2)
         .click("#main-card-0").ticks(2)
         .step("remember independent offsets", CollectionOverflowScenario::rememberOffsets)
         .scroll("#card-details-scroll", -12).ticks(2)
         .check("description scroll is independent", ctx -> offset(ctx, "card-details-scroll") > 0
                 && offset(ctx, "main-grid") == ctx.<Float>get("main-grid")
                 && offset(ctx, "collection-results") == ctx.<Float>get("collection-results"))
         .scroll("#main-grid", -80).ticks(2)
         .check("main scroll is independent", ctx -> offset(ctx, "main-grid") > 0
                 && offset(ctx, "extra-grid") == ctx.<Float>get("extra-grid")
                 && offset(ctx, "side-grid") == ctx.<Float>get("side-grid")
                 && offset(ctx, "collection-results") == ctx.<Float>get("collection-results"))
         .step("move Main scrollbar to end", ctx -> ctx.el("#main-grid").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(1, true)).ticks(2)
         .click("#main-card-59").ticks(2)
         .checkTextContains("#inspector-name", "Sample 60")
         .step("record scrolled main", ctx -> ctx.put("mainBeforeEdit", offset(ctx, "main-grid")))
         .click("#add-card").ticks(2)
         .check("editing retains main offset", ctx -> Math.abs(offset(ctx, "main-grid") - ctx.<Float>get("mainBeforeEdit")) < .001f)
         .step("remember pixels after same-row edit", ctx -> ctx.put("sameRowPixels", pixels(ctx, "main-grid")))
         .scroll("#main-grid", 1).ticks(2)
         .check("next wheel scroll is not undone by edit restoration", ctx -> pixels(ctx, "main-grid") < ctx.<Float>get("sameRowPixels"))
         .step("remember pixels before row growth", ctx -> ctx.put("beforeGrowth", pixels(ctx, "main-grid")))
         .repeat(4, step -> step.click("#add-card")).ticks(3)
         .check("adding a row preserves pixel offset", ctx -> Math.abs(pixels(ctx, "main-grid") - ctx.<Float>get("beforeGrowth")) < 1)
         .step("reach new last row", ctx -> ctx.el("#main-grid").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(1, true)).ticks(2)
         .click("#main-card-64").click("#remove-card").ticks(3)
         .check("removing the last row clamps pixel offset", ctx -> {
             var main = ctx.el("#main-grid").as(ScrollerView.class);
             float max = Math.max(0, main.getContainerHeight() - main.viewPort.getContentHeight());
             return Math.abs(pixels(ctx, "main-grid") - max) <= 1;
         })
         .step("remember clamped Main offset", ctx -> ctx.put("mainBeforeEdit", offset(ctx, "main-grid")))
         .click("#toggle-filters").ticks(2)
         .click("#filter-category-monster").click("#filter-category-spell").click("#filter-category-trap")
         .click("#filter-attribute-earth").click("#filter-attribute-water").click("#filter-attribute-fire")
         .click("#filter-attribute-wind").click("#filter-attribute-light").click("#filter-attribute-dark")
         .click("#filter-attribute-divine")
         .scroll("#filter-scroll", -100).ticks(2)
         .checkBounds("#filter-apply", DuelScreenScenario::insideViewport)
         .check("filters scroll independently", ctx -> offset(ctx, "filter-scroll") > 0
                 && Math.abs(offset(ctx, "main-grid") - ctx.<Float>get("mainBeforeEdit")) < .001f)
         .screenshot("collection-filter-overflow")
         .step("press Apply", ctx -> CollectionLayoutScenario.press(ctx, "#filter-apply"))
         .step("release Apply", CollectionLayoutScenario::release).ticks(2)
         .checkCount(".filter-chip", 10)
         .checkBounds("#active-filters", DuelScreenScenario::insideViewport)
         .scroll("#active-filters", -20).ticks(2)
         .check("applied chips scroll independently", ctx -> offset(ctx, "active-filters") > 0
                 && Math.abs(offset(ctx, "main-grid") - ctx.<Float>get("mainBeforeEdit")) < .001f)
         .step("resize dirty draft to non-widescreen window", ctx -> {
             var w = ctx.mc().getWindow();
             ctx.put("originalWidth", w.getWidth());
             ctx.put("originalHeight", w.getHeight());
             ctx.put("beforeResize", ctx.<DeckEditorModel>get("model").draft());
             ctx.put("mainPixelsBeforeResize", pixels(ctx, "main-grid"));
             GLFW.glfwSetWindowSize(w.getWindow(), 1100, 800);
         }).ticks(6)
         .check("dirty draft survives resize", ctx -> ctx.<DeckEditorModel>get("model").dirty()
                 && ctx.<DeckEditorModel>get("model").draft().equals(ctx.get("beforeResize")))
         .check("resize preserves Main pixel offset", ctx -> Math.abs(pixels(ctx, "main-grid")
                 - ctx.<Float>get("mainPixelsBeforeResize")) < 1)
         .checkTextContains("#inspector-name", "Sample 60")
         .checkBounds("#collection-canvas", DuelScreenScenario::insideViewport)
         .step("non-widescreen viewport", CollectionLayoutScenario::recordViewport)
         .screenshot("collection-dirty-resized")
         .step("restore requested window", ctx -> GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(),
                 ctx.get("originalWidth"), ctx.get("originalHeight"))).ticks(6)
         .openScreen("16000-card catalog", ctx -> {
             var requested = ctx.put("textures", new HashSet<Integer>());
             return CollectionScreen.create(new DeckEditorModel(new DeckList(List.of(), List.of(), List.of()), Map.of()),
                     CollectionFixture.largeCatalog(), code -> { requested.add(code); return null; }, CollectionFixture.LIST_ID,
                     CollectionFixture::saved, CollectionFixture.query());
         }).awaitModularUI().ticks(4)
         .check("16000 cards mount bounded rows", CollectionOverflowScenario::boundedRows)
         .check("texture requests limited to mounted cards and inspector", ctx ->
                 ctx.<HashSet<Integer>>get("textures").size() <= grid(ctx).getMountedItemCount() * 4 + 1)
         .step("scroll catalog near end", ctx -> grid(ctx).verticalScroller.setNormalizedValue(1, true))
         .ticks(4)
         .check("last catalog rows stay bounded", CollectionOverflowScenario::boundedRows)
         .checkExists("#collection-card-36000")
         .click("#collection-card-36000").ticks(2)
         .checkTextContains("#inspector-name", "Catalog 16000")
         .check("selected last card lies in collection viewport", ctx -> CollectionLayoutScenario.contained(
                 ctx.el("#collection-card-36000").bounds(), ctx.el("#collection-results").bounds()))
         .step("remember catalog position", ctx -> ctx.put("catalogBeforeEdit", offset(ctx, "collection-results")))
         .click("#add-card").ticks(2)
         .check("adding retains independent catalog scroll", ctx -> Math.abs(offset(ctx, "collection-results")
                 - ctx.<Float>get("catalogBeforeEdit")) < .001f)
         .checkTextContains("#inspector-missing", "1")
         .screenshot("collection-catalog-end")
         .teardown("close", ctx -> ctx.mc().setScreen(null));
    }

    private static CollectionCardGrid grid(TestContext ctx) {
        return ctx.el("#collection-results").as(CollectionCardGrid.class);
    }

    private static boolean boundedRows(TestContext ctx) {
        var grid = grid(ctx);
        float rowHeight = grid.getVirtualScrollerViewStyle().estimatedItemHeight();
        int limit = (int) Math.ceil(grid.viewPort.getContentHeight() / rowHeight) + 3;
        ctx.attach("virtual rows", "items=%d mounted=%d limit=%d viewport=%.1f row=%.1f".formatted(
                grid.getItemCount(), grid.getMountedItemCount(), limit, grid.viewPort.getContentHeight(), rowHeight));
        return grid.getItemCount() == 4000 && grid.getMountedItemCount() > 0 && grid.getMountedItemCount() <= limit;
    }

    private static float offset(TestContext ctx, String id) {
        return ctx.el("#" + id).as(ScrollerView.class).verticalScroller.getNormalizedValue();
    }

    private static float pixels(TestContext ctx, String id) {
        return -ctx.el("#" + id).as(ScrollerView.class).viewContainer.getLayoutY();
    }

    private static void rememberOffsets(TestContext ctx) {
        for (var id : List.of("main-grid", "extra-grid", "side-grid", "collection-results")) ctx.put(id, offset(ctx, id));
    }

    private static boolean rowsDoNotOverlap(TestContext ctx) {
        for (var section : List.of("main", "extra", "side")) {
            int count = section.equals("main") ? 60 : 15;
            for (int i = 0; i < count; i++) {
                var a = ctx.el("#" + section + "-card-" + i).bounds();
                for (int j = i + 1; j < count; j++) {
                    var b = ctx.el("#" + section + "-card-" + j).bounds();
                    boolean overlap = a.x() < b.x() + b.width() - .1f && a.x() + a.width() > b.x() + .1f
                            && a.y() < b.y() + b.height() - .1f && a.y() + a.height() > b.y() + .1f;
                    if (overlap) { ctx.attach("overlap", section + " " + i + " / " + j); return false; }
                }
            }
        }
        return true;
    }
}

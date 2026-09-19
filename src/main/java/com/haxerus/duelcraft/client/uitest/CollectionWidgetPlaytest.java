package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CardSearch;
import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.SavedDeckController;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Real popup hit testing and visual checks for the playtest-specific collection regressions. */
final class CollectionWidgetPlaytest {
    private CollectionWidgetPlaytest() {}

    static void define(ScenarioBuilder s, int width, int height, int guiScale) {
        s.step("set requested viewport", ctx -> {
            var window = ctx.mc().getWindow();
            ctx.put("originalWidth", window.getWidth());
            ctx.put("originalHeight", window.getHeight());
            GLFW.glfwSetWindowSize(window.getWindow(), width, height);
        }).waitUntil("requested viewport applied", ctx -> ctx.mc().getWindow().getWidth() == width
                && ctx.mc().getWindow().getHeight() == height)
          .openScreen("scaled collection widgets", ctx -> {
              var fullCalls = ctx.put("fullCalls", new HashSet<Integer>());
              var artCalls = ctx.put("artCalls", new HashSet<Integer>());
              var full = ResourceLocation.fromNamespaceAndPath("duelcraft", "textures/card_back.png");
              var art = ResourceLocation.withDefaultNamespace("textures/block/diamond_block.png");
              var model = ctx.put("model", CollectionFixture.model(false));
              var cards = new java.util.ArrayList<>(CollectionFixture.cards());
              cards.add(new com.haxerus.duelcraft.client.carddata.CardInfo(90000, "Anime sample", "",
                      1, 0, 0, 1, 1, 1, 4));
              var screen = CollectionScreen.create(model, cards,
                      code -> { fullCalls.add(code); return code == 10003 ? null : full; },
                      code -> { artCalls.add(code); return code == 10003 ? null : art; },
                      CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
              Object controller = ctx.getField(screen, "controller");
              SavedDeckController lists = ctx.getField(controller, "lists");
              lists.applyView(new ClientCollectionState.View(1, model.owned(), List.of(
                      new CollectionReply.Summary(CollectionFixture.LIST_ID, "New list", 40, 5, 0)), null));
              return screen;
          }).awaitModularUI().ticks(3)
          .check("requested GUI scale is active", ctx -> ctx.mc().getWindow().getGuiScale() == guiScale)
          .check("only Deck and Side edit modes are exposed", ctx -> ctx.exists("#section-deck")
                  && ctx.exists("#section-side") && !ctx.exists("#section-main") && !ctx.exists("#section-extra"))
          .check("Saved lists and Save list align", ctx -> {
              var lists = ctx.el("#saved-lists").bounds();
              var save = ctx.el("#save-deck").bounds();
              return Math.abs(lists.width() - save.width()) < 1 && Math.abs(lists.height() - save.height()) < 1
                      && Math.abs(lists.y() - save.y()) < 1;
          })
          .checkVisible("#inspector-empty")
          .checkText("#inspector-empty-prompt", "Select a card to inspect.")
          .checkHidden("#card-details-scroll")
          .checkHidden("#card-edit-controls")
          .check("no arbitrary card inspected on open", ctx -> ctx.<HashSet<Integer>>get("artCalls").isEmpty()
                  && ctx.count(".card-tile.selected") == 0)
          .step("remember inspector bounds", ctx -> ctx.put("inspectorBounds", ctx.el("#card-inspector").bounds()))
          .hoverAt(-100, -100).screenshot("unselected-inspector-scale" + guiScale)
          .click("#main-card-0").ticks(2)
          .checkHidden("#inspector-empty")
          .checkVisible("#card-details-scroll")
          .checkVisible("#card-edit-controls")
          .check("selecting a card preserves the panel dimensions", ctx -> {
              ElementBounds before = ctx.get("inspectorBounds");
              var after = ctx.el("#card-inspector").bounds();
              return before.x() == after.x() && before.y() == after.y()
                      && before.width() == after.width() && before.height() == after.height();
          })
          .check("inspector uses its distinct cropped-art supplier", ctx ->
                  ctx.<HashSet<Integer>>get("artCalls").contains(CollectionFixture.FIRST)
                          && ctx.<HashSet<Integer>>get("fullCalls").contains(CollectionFixture.FIRST))
          .check("collection scroller arrows have usable square dimensions", CollectionWidgetPlaytest::compactScrollers)
          .checkTextContains("#result-count", "80 cards")
          .click("#alternate-formats").ticks(2)
          .checkTextContains("#result-count", "81 cards")
          .checkClass("#alternate-formats", "selected")
          .click("#alternate-formats").ticks(2)
          .checkTextContains("#result-count", "80 cards")
          .click("#collection-card-10003").ticks(2)
          .checkVisible("#collection-card-10003 .card-placeholder")
          .checkVisible("#inspector-art .card-placeholder")
          .hoverAt(-100, -100).screenshot("card-back-fallback-scale" + guiScale)
          .checkClass("#collection-card-10003", "selected")
          .checkClass("#section-deck", "selected")
          .click("#collection-ownership")
          .screenshot("collection-ownership-open-scale" + guiScale)
          .step("choose Ownership through its real popup row", ctx -> choose(ctx, "collection-ownership", CardSearch.Ownership.OWNED))
          .click("#card-sort")
          .step("choose Sort through its real popup row", ctx -> choose(ctx, "card-sort", CardSearch.Sort.PASSCODE))
          .click("#toggle-filters").ticks(2)
          .step("bring Measure into view", ctx -> ctx.el("#filter-scroll").as(ScrollerView.class)
                  .verticalScroller.setNormalizedValue(1, true)).ticks(2)
          .click("#filter-measure")
          .step("choose Measure through its real popup row", ctx -> choose(ctx, "filter-measure", CardSearch.Measure.RANK))
          .click("#toggle-filters").ticks(2)
          .step("open Saved lists with real mouse input", ctx -> click(ctx, "saved-lists")).ticks(2)
          .click("#list-picker")
          .step("choose Saved list through its real popup row", ctx -> choose(ctx, "list-picker", CollectionFixture.LIST_ID))
          .hoverAt(-100, -100).screenshot("collection-playtest-scale" + guiScale)
          .step("close Saved lists with real mouse input", ctx -> click(ctx, "lists-close")).ticks(2)
          .click("#sort-deck").ticks(2)
          .check("Sort deck groups cards without moving sections", ctx -> {
              var model = ctx.<com.haxerus.duelcraft.client.collection.DeckEditorModel>get("model");
              return model.dirty() && model.draft().main().subList(0, 2).equals(List.of(10001, 10003))
                      && model.draft().main().size() == 40 && model.draft().extra().size() == 5
                      && model.draft().side().isEmpty();
          })
          .checkText("#deck-summary", "Unsaved changes")
          .hoverAt(-100, -100).screenshot("sorted-deck-scale" + guiScale)
          .teardown("restore viewport", ctx -> {
              ctx.mc().setScreen(null);
              GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(), ctx.get("originalWidth"), ctx.get("originalHeight"));
          });
    }

    @SuppressWarnings("unchecked")
    private static <T> void choose(TestContext ctx, String selectorId, T value) {
        var selector = ctx.el("#" + selectorId).as(Selector.class);
        Map<T, Button> candidates = ctx.getField(selector, "candidateButtons");
        var button = candidates.get(value);
        ctx.require("popup is open for #" + selectorId, selector.isOpen() && button != null);
        var anchor = ctx.el("#" + selectorId).bounds();
        var dialog = ElementBounds.of(selector.dialog);
        var label = ElementBounds.of(button.getParent());
        var hit = ElementBounds.of(button);
        ctx.attach("popup bounds", "anchor=%s dialog=%s label=%s hit=%s".formatted(anchor, dialog, label, hit));
        float expectedX = Math.clamp(anchor.x(), 0, Math.max(0,
                ctx.mc().getWindow().getGuiScaledWidth() - dialog.width()));
        float expectedY = Math.clamp(anchor.y() + anchor.height(), 0, Math.max(0,
                ctx.mc().getWindow().getGuiScaledHeight() - dialog.height()));
        ctx.check("popup stays aligned and clamped with its scaled selector", dialog.width() >= anchor.width() - 1
                && Math.abs(dialog.x() - expectedX) <= 2 && Math.abs(dialog.y() - expectedY) <= 2);
        ctx.check("popup label is readable and its overlay covers the row", label.height() >= 8
                && CollectionLayoutScenario.contained(hit, label) && CollectionLayoutScenario.contained(label, dialog));
        ctx.input().mouseDown(hit.center().x, hit.center().y, 0);
        ctx.input().mouseUp(hit.center().x, hit.center().y, 0);
        ctx.check("popup click selects " + value, value.equals(selector.getValue()) && !selector.isOpen());
    }

    private static void click(TestContext ctx, String id) {
        var hit = ctx.el("#" + id).bounds();
        ctx.input().mouseDown(hit.center().x, hit.center().y, 0);
        ctx.input().mouseUp(hit.center().x, hit.center().y, 0);
    }

    private static boolean compactScrollers(TestContext ctx) {
        for (String id : List.of("card-details-scroll", "main-grid", "extra-grid", "side-grid",
                "active-filters", "collection-results", "filter-scroll")) {
            var scroll = ctx.el("#" + id).as(ScrollerView.class);
            if (!compact(scroll.verticalScroller, false) || !compact(scroll.horizontalScroller, true)) return false;
        }
        for (String id : List.of("collection-ownership", "card-sort", "filter-measure", "list-picker")) {
            var selector = ctx.el("#" + id).as(Selector.class);
            if (!compact(selector.scrollerView.verticalScroller, false)
                    || !compact(selector.scrollerView.horizontalScroller, true)) return false;
        }
        return true;
    }

    private static boolean compact(Scroller scroller, boolean horizontal) {
        // Hidden/unopened scrollers have no laid-out buttons yet.
        if (scroller.getSizeWidth() == 0 || scroller.getSizeHeight() == 0) return true;
        float head = horizontal ? scroller.headButton.getSizeWidth() : scroller.headButton.getSizeHeight();
        float tail = horizontal ? scroller.tailButton.getSizeWidth() : scroller.tailButton.getSizeHeight();
        return Math.abs(head - 12) < .1f && Math.abs(tail - 12) < .1f;
    }
}

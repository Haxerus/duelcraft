package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CollectionCardGrid;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Real pointer coverage for collection double-click and exact deck-tile removal. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "collection_editor_qa", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionEditorQAScenario implements UIScenario {
    private static final int MAIN = 10003;
    private static final int OTHER = 10002;

    @Override public void configure(ScenarioOptions options) {
        options.tags("collection", "pointer").requiresWorld(false).guiScale(2);
    }

    @Override public void define(ScenarioBuilder s) {
        s.step("set 1280x720 pointer viewport", ctx -> {
            var window = ctx.mc().getWindow();
            ctx.put("originalWidth", window.getWidth());
            ctx.put("originalHeight", window.getHeight());
            GLFW.glfwSetWindowSize(window.getWindow(), 1280, 720);
        }).waitUntil("1280x720 pointer viewport applied", ctx -> ctx.mc().getWindow().getWidth() == 1280
                && ctx.mc().getWindow().getHeight() == 720)
          .openScreen("collection pointer editing", ctx -> {
              var model = ctx.put("model", new DeckEditorModel(empty(), CollectionFixture.owned()));
              ctx.put("attempts", new ArrayList<CompletableFuture<SavedDeck>>());
              return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                      CollectionFixture.LIST_ID, (name, draft) -> {
                          var submitted = new SavedDeck(CollectionFixture.LIST_ID, name, draft);
                          ctx.put("submitted", submitted);
                          var pending = new CompletableFuture<SavedDeck>();
                          ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").add(pending);
                          return pending;
                      }, CollectionFixture.query());
          }).awaitModularUI().ticks(3)
          .step("record actual pointer viewport", ctx -> ctx.attach("pointer viewport",
                  "%dx%d physical, GUI scale %.1f, %dx%d scaled".formatted(
                          ctx.mc().getWindow().getWidth(), ctx.mc().getWindow().getHeight(),
                          ctx.mc().getWindow().getGuiScale(), ctx.mc().getWindow().getGuiScaledWidth(),
                          ctx.mc().getWindow().getGuiScaledHeight())))
          .check("actual viewport and GUI scale are active", ctx -> ctx.mc().getWindow().getWidth() == 1280
                  && ctx.mc().getWindow().getHeight() == 720 && ctx.mc().getWindow().getGuiScale() == 2)
          .check("collection rows are virtualized", ctx -> {
              var grid = grid(ctx);
              return grid.getItemCount() == 20 && grid.getMountedItemCount() > 0
                      && grid.getMountedItemCount() < grid.getItemCount();
          });

        leftPress(s, "single inspect", "#collection-card-" + MAIN);
        s.check("single left click only inspects", ctx -> model(ctx).draft().equals(empty())
                && ctx.el("#inspector-name").text().equals("Cinder Dragon"))
         .ticks(7);

        doubleLeft(s, "first Main double click", "#collection-card-" + MAIN);
        s.check("two left clicks add exactly one Main copy", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN), List.of(), List.of())));

        s.step("filter to the interleaving card", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(OTHER), true)).ticks(2)
         .checkExists("#collection-card-" + OTHER);
        doubleLeft(s, "filtered Main double click", "#collection-card-" + OTHER);
        s.check("filter refresh double click adds once", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER), List.of(), List.of())));

        s.step("filter back to the repeated card", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(MAIN), true)).ticks(2)
         .checkExists("#collection-card-" + MAIN);
        doubleLeft(s, "second Main double click", "#collection-card-" + MAIN);
        s.check("second double click adds one more copy", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of())))
         .click("#add-card").ticks(2)
         .check("button route adds the third aggregate copy", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN, MAIN), List.of(), List.of())))
         .check("shared cap disables the Add button", ctx -> !ctx.el("#add-card").isActive());
        doubleLeft(s, "rejected fourth Main copy", "#collection-card-" + MAIN);
        s.check("fourth copy is rejected without a false success", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN, MAIN), List.of(), List.of()))
                         && ctx.el("#editor-status").text().contains("Three copies"))
         .step("filter to a different inspector identity", ctx -> ctx.el("#collection-search").as(TextField.class)
                 .setText(String.valueOf(OTHER), true)).ticks(2);
        leftPress(s, "preselect different identity", "#collection-card-" + OTHER);
        s.check("different identity is selected before deck removal", ctx ->
                ctx.el("#inspector-name").text().equals("Beacon Spell"));

        rightPress(s, "exact repeated tile", "#main-card-2");
        s.check("right press removes the rendered index only", ctx ->
                model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of())))
         .step("release exact repeated tile", CollectionEditorQAScenario::rightRelease)
         .check("right release does not remove again or fire left selection", ctx ->
                 model(ctx).draft().equals(new DeckList(List.of(MAIN, OTHER, MAIN), List.of(), List.of()))
                         && ctx.el("#inspector-name").text().equals("Beacon Spell"));

        s.step("restore all virtual rows", ctx -> ctx.el("#collection-search").as(TextField.class).setText("", true))
         .ticks(2)
         .step("scroll virtual rows to the Extra card", ctx -> grid(ctx).verticalScroller.setNormalizedValue(1, true))
         .ticks(2)
         .checkExists("#collection-card-10080");
        doubleLeft(s, "virtualized Extra double click", "#collection-card-10080");
        s.check("Deck mode routes Extra type to Extra", ctx -> model(ctx).draft().extra().equals(List.of(10080)));

        s.step("filter to Side sample", ctx -> ctx.el("#collection-search").as(TextField.class).setText("10004", true))
         .ticks(2).click("#section-side").ticks(2);
        doubleLeft(s, "Side double click", "#collection-card-10004");
        s.check("Side mode routes pointer addition to Side", ctx -> model(ctx).draft().side().equals(List.of(10004)));

        s.step("make mounted deck tiles stale", ctx -> {
            ctx.put("staleMain", ctx.el("#main-card-0").element());
            ctx.put("beforeStaleClick", model(ctx).draft());
            ctx.check("stale setup mutation succeeds", model(ctx).add(DeckEditorModel.Section.SIDE, OTHER));
        }).check("stale tile remains mounted after the draft changes", ctx ->
                ctx.el("#main-card-0").element() == ctx.get("staleMain"));
        rightPress(s, "stale rendered tile", "#main-card-0");
        s.step("release stale rendered tile", CollectionEditorQAScenario::rightRelease)
         .check("stale rendered callback cannot mutate the newer draft", ctx -> {
             DeckList before = ctx.get("beforeStaleClick");
             var expected = new DeckList(before.main(), before.extra(), List.of(10004, OTHER));
             return model(ctx).draft().equals(expected);
         })
         .click("#sort-deck").ticks(2)
         .step("begin delayed Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
         .step("release delayed Save", CollectionLayoutScenario::release).ticks(2)
         .check("pending Save freezes all edit controls", ctx -> !ctx.el("#add-card").isActive()
                 && !ctx.el("#remove-card").isActive() && !ctx.el("#sort-deck").isActive());

        s.step("filter while Save is pending", ctx -> ctx.el("#collection-search").as(TextField.class)
                    .setText(String.valueOf(OTHER), true)).ticks(2)
         .step("remember frozen draft", ctx -> ctx.put("frozenDraft", model(ctx).draft()));
        doubleLeft(s, "pending collection double click", "#collection-card-" + OTHER);
        s.check("pending double click leaves the frozen draft unchanged", ctx ->
                model(ctx).draft().equals(ctx.get("frozenDraft")));
        rightPress(s, "pending deck tile", "#main-card-0");
        s.step("release pending deck tile", CollectionEditorQAScenario::rightRelease)
         .check("pending pointer gestures leave the frozen draft unchanged", ctx ->
                 model(ctx).draft().equals(ctx.get("frozenDraft")))
         .step("fail delayed Save", ctx -> ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts")
                 .getLast().completeExceptionally(new IllegalStateException("delayed save failed"))).ticks(2)
         .check("failed delayed Save retains the dirty editor", ctx -> ctx.screen() instanceof CollectionScreen
                 && model(ctx).dirty() && ctx.el("#sort-deck").isActive()
                 && ctx.el("#editor-status").text().contains("delayed save failed"))
         .step("press visible Close with dirty draft", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release visible Close with dirty draft", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#close-dialog")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("Escape keeps editing and cancels queued dirty navigation", ctx ->
                 ctx.screen() instanceof CollectionScreen && !ctx.el("#close-dialog").isVisible()
                         && model(ctx).dirty())
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .step("retry delayed Save", ctx -> CollectionLayoutScenario.press(ctx, "#save-deck"))
         .step("release delayed Save retry", CollectionLayoutScenario::release).ticks(2)
         .check("retry remains pending until acknowledgement", ctx ->
                 ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").size() == 2
                         && !ctx.el("#sort-deck").isActive())
         .step("acknowledge delayed Save", ctx -> ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts")
                 .getLast().complete(ctx.get("submitted"))).ticks(2)
         .check("Save acknowledgement unfreezes and cleans the draft", ctx ->
                 ctx.el("#sort-deck").isActive() && !model(ctx).dirty())
         .step("open My decks", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
         .step("release My decks", CollectionLayoutScenario::release).ticks(2)
         .step("open delete confirmation", ctx -> CollectionLayoutScenario.press(ctx, "#list-delete"))
         .step("release delete confirmation", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#delete-dialog")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("Escape dismisses only delete confirmation", ctx -> !ctx.el("#delete-dialog").isVisible()
                 && ctx.el("#lists-dialog").isVisible())
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .step("reopen delete confirmation", ctx -> CollectionLayoutScenario.press(ctx, "#list-delete"))
         .step("release reopened delete confirmation", CollectionLayoutScenario::release).ticks(2)
         .step("cancel delete visibly", ctx -> CollectionLayoutScenario.press(ctx, "#delete-cancel"))
         .step("release delete cancel", CollectionLayoutScenario::release).ticks(2)
         .check("visible delete cancel keeps My decks", ctx -> !ctx.el("#delete-dialog").isVisible()
                 && ctx.el("#lists-dialog").isVisible())
         .step("open saved-list dropdown", ctx -> CollectionLayoutScenario.press(ctx, "#list-picker"))
         .step("release saved-list dropdown", CollectionLayoutScenario::release).ticks(2)
         .check("saved-list dropdown is attached to the root", ctx ->
                 ctx.el("#list-picker").as(Selector.class).isOpen())
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("first nested Escape dismisses only the dropdown", ctx ->
                 !ctx.el("#list-picker").as(Selector.class).isOpen() && ctx.el("#lists-dialog").isVisible()
                         && ctx.screen() instanceof CollectionScreen)
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("second nested Escape dismisses only My decks", ctx -> !ctx.el("#lists-dialog").isVisible()
                 && ctx.screen() instanceof CollectionScreen)
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .hoverAt(-100, -100).screenshot("collection-editor-pointer-qa")
         .keyDown(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("third nested Escape closes the clean editor", ctx -> !(ctx.screen() instanceof CollectionScreen))
         .keyUp(GLFW.GLFW_KEY_ESCAPE)
         .openScreen("empty unsaved draft closes without discard", ctx -> {
             var model = ctx.put("emptyModel", new DeckEditorModel(empty(), CollectionFixture.owned()));
             return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                     CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
         }).awaitModularUI().ticks(2)
         .step("open My decks for New", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
         .step("release My decks for New", CollectionLayoutScenario::release).ticks(2)
         .step("create empty unsaved draft", ctx -> CollectionLayoutScenario.press(ctx, "#list-new"))
         .step("release New", CollectionLayoutScenario::release).ticks(2)
         .step("close My decks after New", ctx -> CollectionLayoutScenario.press(ctx, "#lists-close"))
         .step("release My decks after New", CollectionLayoutScenario::release).ticks(2)
         .check("empty unsaved draft remains saveable", ctx -> ctx.el("#save-deck").isActive()
                 && ctx.el("#deck-summary").text().contains("Unsaved"))
         .step("close empty unsaved draft", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release empty unsaved Close", CollectionLayoutScenario::release).ticks(2)
         .check("empty unsaved draft closes without a discard decision", ctx ->
                 !(ctx.screen() instanceof CollectionScreen))
         .openScreen("renamed saved empty list still prompts", ctx -> CollectionScreen.create(
                 new DeckEditorModel(empty(), CollectionFixture.owned()), CollectionFixture.cards(), code -> null,
                 CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query()))
         .awaitModularUI().ticks(2)
         .step("open My decks for saved-empty rename", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
         .step("release My decks for saved-empty rename", CollectionLayoutScenario::release).ticks(2)
         .typeInto("#list-name", "Renamed empty saved list").ticks(2)
         .step("close My decks after saved-empty rename", ctx -> CollectionLayoutScenario.press(ctx, "#lists-close"))
         .step("release My decks after saved-empty rename", CollectionLayoutScenario::release).ticks(2)
         .step("close renamed saved empty list", ctx -> CollectionLayoutScenario.press(ctx, "#close-editor"))
         .step("release renamed saved empty Close", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#close-dialog")
         .check("saved empty rename still requires a discard decision", ctx ->
                 ctx.screen() instanceof CollectionScreen)
         .teardown("restore pointer viewport", ctx -> {
             ctx.mc().setScreen(null);
             GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(),
                     ctx.get("originalWidth"), ctx.get("originalHeight"));
         });
    }

    private static void leftPress(ScenarioBuilder s, String name, String selector) {
        s.step(name + " press", ctx -> press(ctx, selector, 0, name))
         .step(name + " release", ctx -> releaseSame(ctx, selector, 0, name));
    }

    private static void doubleLeft(ScenarioBuilder s, String name, String selector) {
        s.step(name + " first press", ctx -> press(ctx, selector, 0, name))
         .step(name + " first release", ctx -> releaseSame(ctx, selector, 0, name))
         .step(name + " second press", ctx -> pressSame(ctx, selector, 0, name))
         .step(name + " second release", ctx -> releaseSame(ctx, selector, 0, name));
    }

    private static void rightPress(ScenarioBuilder s, String name, String selector) {
        s.step(name + " right press", ctx -> press(ctx, selector, 1, name));
    }

    private static void press(TestContext ctx, String selector, int button, String key) {
        var target = ctx.el(selector);
        var bounds = target.bounds();
        ctx.require("pointer target is visible: " + selector, target.isVisible() && bounds.width() > 0
                && bounds.height() > 0 && DuelScreenScenario.insideViewport(bounds));
        ctx.put("pointerElement", target.element());
        ctx.put("pointerX", bounds.center().x);
        ctx.put("pointerY", bounds.center().y);
        ctx.put("pointerKey", key);
        ctx.input().moveTo(bounds.center().x, bounds.center().y);
        ctx.input().mouseDown(bounds.center().x, bounds.center().y, button);
    }

    private static void pressSame(TestContext ctx, String selector, int button, String key) {
        UIElement mounted = ctx.get("pointerElement");
        ctx.check(key + " keeps the mounted hit target between clicks", ctx.el(selector).element() == mounted);
        ctx.input().mouseDown(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), button);
    }

    private static void releaseSame(TestContext ctx, String selector, int button, String key) {
        UIElement mounted = ctx.get("pointerElement");
        ctx.check(key + " keeps the mounted hit target through release", ctx.el(selector).element() == mounted);
        ctx.input().mouseUp(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), button);
    }

    private static void rightRelease(TestContext ctx) {
        ctx.input().mouseUp(ctx.<Float>get("pointerX"), ctx.<Float>get("pointerY"), 1);
    }

    private static CollectionCardGrid grid(TestContext ctx) {
        return ctx.el("#collection-results").as(CollectionCardGrid.class);
    }

    private static DeckEditorModel model(TestContext ctx) { return ctx.get("model"); }

    private static DeckList empty() { return new DeckList(List.of(), List.of(), List.of()); }
}

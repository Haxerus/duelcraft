package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.CardSearch;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.collection.DeckList;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
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

/** Real transformed input, with only sample metadata and the save destination injected. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "collection_search", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionSearchScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("collection").requiresWorld(false);
    }

    @Override
    public void define(ScenarioBuilder s) {
        open(s, "collection editing and search");
        s.checkExists("#close-dialog")
         .checkExists("#filter-panel")
         .checkHidden("#close-dialog")
         .checkHidden("#filter-panel")
         .step("remember collapsed Side viewport", ctx -> ctx.put("collapsedSideHeight", ctx.el("#side-grid").bounds().height()))
         .click("#collection-card-10001").ticks(2)
         .checkText("#inspector-name", "Amber Dragon")
         .checkTextContains("#inspector-owned", "0")
         .checkTextContains("#inspector-used", "1")
         .checkTextContains("#inspector-missing", "1")
         .checkTextContains("#inspector-description", "Draw two cards.")
         .click("#section-main")
         .click("#add-card").ticks(2)
         .checkCount("#main-grid .card-tile", 41)
         .checkTextContains("#main-count", "41")
         .checkTextContains("#inspector-used", "2")
         .checkTextContains("#inspector-missing", "2")
         .click("#main-card-0").ticks(2)
         .checkText("#inspector-name", "Amber Dragon")
         .click("#remove-card").ticks(2)
         .checkCount("#main-grid .card-tile", 40)
         .checkTextContains("#main-count", "40")
         .click("#section-side")
         .click("#add-card").ticks(2)
         .checkVisible("#side-grid")
         .checkCount("#side-grid .card-tile", 1)
         .checkTextContains("#side-count", "1")
         .checkTextContains("#inspector-owned", "0")
         .checkTextContains("#inspector-used", "2")
         .checkTextContains("#inspector-missing", "2")
         .step("expected edited list", ctx -> {
             DeckList initial = ctx.get("initial");
             var main = new ArrayList<>(initial.main().subList(1, initial.main().size()));
             main.add(CollectionFixture.FIRST);
             var expected = new DeckList(main, initial.extra(), List.of(CollectionFixture.FIRST));
             ctx.put("expected", expected);
             ctx.check("Add and Remove affect the selected sections", expected.equals(model(ctx).draft()),
                     expected, model(ctx).draft());
             ctx.check("editing preserves owned copies", CollectionFixture.owned().equals(model(ctx).owned()));
             ctx.check("Side expands after adding a copy", ctx.el("#side-grid").bounds().height()
                     > ctx.<Float>get("collapsedSideHeight"));
         })
         .typeInto("#collection-search", "Cinder").ticks(2)
         .checkCount("#collection-results .collection-card", 1)
         .checkExists("#collection-card-10003")
         .checkText("#inspector-name", "Amber Dragon")
         .typeInto("#collection-search", "draw two").ticks(2)
         .checkCount("#collection-results .collection-card", 3)
         .checkExists("#collection-card-10001")
         .checkExists("#collection-card-10002")
         .checkExists("#collection-card-10003")
         .click("#toggle-filters").ticks(2)
         .checkVisible("#filter-panel")
         .click("#filter-category-monster")
         .click("#filter-attribute-dark")
         .step("press filter-apply", ctx -> CollectionLayoutScenario.press(ctx, "#filter-apply"))
         .step("release filter-apply", CollectionLayoutScenario::release).ticks(2)
         .checkHidden("#filter-panel")
         .checkCount("#collection-results .collection-card", 1)
         .checkExists("#collection-card-10001")
         .checkText("#inspector-name", "Amber Dragon")
         .screenshot("collection-effect-text-and-filters")
         .checkExists("#chip-category-monster")
         .checkExists("#chip-attribute-dark")
         .checkExists("#chip-query")
         .step("press chip-attribute-dark", ctx -> CollectionLayoutScenario.press(ctx, "#chip-attribute-dark"))
         .step("release chip-attribute-dark", CollectionLayoutScenario::release).ticks(2)
         .checkCount("#chip-attribute-dark", 0)
         .checkCount("#collection-results .collection-card", 2)
         .checkExists("#collection-card-10001")
         .checkExists("#collection-card-10003")
         .checkText("#inspector-name", "Amber Dragon")
         .click("#toggle-filters")
         .step("press filter-clear", ctx -> CollectionLayoutScenario.press(ctx, "#filter-clear"))
         .step("release filter-clear", CollectionLayoutScenario::release).ticks(2)
         .checkHidden("#filter-panel")
         .checkValue("#collection-search", "")
         .checkExists("#collection-card-10001")
         .checkExists("#collection-card-10002")
         .checkExists("#collection-card-10003")
         .checkExists("#collection-card-10004")
         .check("clear restores collection results", ctx -> ctx.count("#collection-results .collection-card") > 3)
         .typeInto("#collection-search", "draw two").ticks(2)
         .click("#toggle-filters").click("#filter-category-monster").click("#toggle-filters").ticks(2)
         .step("change sort with unapplied category", ctx -> ctx.el("#card-sort").as(Selector.class)
                 .setValue(CardSearch.Sort.PASSCODE, true)).ticks(2)
         .checkCount("#collection-results .collection-card", 3)
         .checkExists("#collection-card-10002")
         .click("#toggle-filters")
         .step("enter an invalid pending number range", ctx -> {
             ctx.el("#filter-measure-min").as(TextField.class).setText("10");
             ctx.el("#filter-measure-max").as(TextField.class).setText("1");
         }).click("#toggle-filters")
         .step("change ownership with invalid unapplied range", ctx -> ctx.el("#collection-ownership").as(Selector.class)
                 .setValue(CardSearch.Ownership.MISSING, true)).ticks(2)
         .checkCount("#collection-results .collection-card", 1)
         .checkExists("#collection-card-10001")
         .click("#toggle-filters")
         .step("press clear pending filters", ctx -> CollectionLayoutScenario.press(ctx, "#filter-clear"))
         .step("release clear pending filters", CollectionLayoutScenario::release).ticks(2)
         .click("#save-deck").ticks(2)
         .step("save missing-copy sample draft", ctx -> {
             DeckList expected = ctx.get("expected");
             ctx.check("save receives the exact Main Extra Side snapshot", expected.equals(ctx.get("saved")),
                     expected, ctx.get("saved"));
             ctx.check("successful save clears dirty state", !model(ctx).dirty());
             ctx.check("saving missing copies does not grant ownership", model(ctx).missing(CollectionFixture.FIRST) == 2);
             ctx.check("save callback ran once", ctx.<Integer>get("saveCalls") == 1);
         })
         .screenshot("collection-missing-copy-saved")
         .click("#add-card").ticks(2)
         .step("fail the next save", ctx -> {
             ctx.put("failSave", true);
             ctx.put("failedDraft", model(ctx).draft());
         })
         .click("#save-deck").ticks(2)
         .checkScreen(CollectionScreen.class)
         .step("failed save retains the draft", ctx -> {
             ctx.check("failed callback receives the edited snapshot", ctx.get("failedDraft").equals(ctx.get("attempted")));
             ctx.check("failed save keeps every edit", ctx.get("failedDraft").equals(model(ctx).draft()));
             ctx.check("failed save remains dirty", model(ctx).dirty());
             ctx.check("failure does not replace the saved snapshot", ctx.get("expected").equals(ctx.get("saved")));
         })
         .checkTextContains("#editor-status", "fail")
         .screenshot("collection-save-failure")
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .step("press close-save", ctx -> CollectionLayoutScenario.press(ctx, "#close-save"))
         .step("release close-save", CollectionLayoutScenario::release).ticks(2)
         .checkScreen(CollectionScreen.class)
         .checkVisible("#close-dialog")
         .check("failed close-save stays dirty", ctx -> model(ctx).dirty())
         .step("press close-cancel", ctx -> CollectionLayoutScenario.press(ctx, "#close-cancel"))
         .step("release close-cancel", CollectionLayoutScenario::release).ticks(2)
         .checkHidden("#close-dialog")
         .checkScreen(CollectionScreen.class)
         .check("Cancel keeps the dirty draft", ctx -> model(ctx).dirty()
                 && ctx.get("failedDraft").equals(model(ctx).draft()))
         .step("remember save attempts before Discard", ctx -> ctx.put("callsBeforeDiscard", ctx.get("saveCalls")))
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .screenshot("collection-dirty-close")
         .step("press close-discard", ctx -> CollectionLayoutScenario.press(ctx, "#close-discard"))
         .step("release close-discard", CollectionLayoutScenario::release).ticks(2)
         .check("Discard closes without saving", ctx -> !(ctx.screen() instanceof CollectionScreen)
                 && ctx.get("callsBeforeDiscard").equals(ctx.get("saveCalls")));

        open(s, "dirty-close Save");
        s.click("#collection-card-10001")
         .click("#section-main")
         .click("#add-card").ticks(2)
         .step("remember close-save snapshot", ctx -> ctx.put("expected", model(ctx).draft()))
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .step("press close-save", ctx -> CollectionLayoutScenario.press(ctx, "#close-save"))
         .step("release close-save", CollectionLayoutScenario::release).ticks(2)
         .check("Save closes after saving the exact draft", ctx -> !(ctx.screen() instanceof CollectionScreen)
                 && ctx.get("expected").equals(ctx.get("saved")) && !model(ctx).dirty()
                 && ctx.<Integer>get("saveCalls") == 1)
         .teardown("close sample collection", ctx -> ctx.mc().setScreen(null));
    }

    private static void open(ScenarioBuilder s, String name) {
        s.openScreen(name, ctx -> {
            var model = CollectionFixture.model(false);
            ctx.put("model", model);
            ctx.put("initial", model.draft());
            ctx.put("failSave", false);
            ctx.put("saveCalls", 0);
            return CollectionScreen.create(model, CollectionFixture.cards(), code -> null, snapshot -> {
                ctx.put("attempted", snapshot);
                ctx.put("saveCalls", ctx.<Integer>get("saveCalls") + 1);
                if (ctx.<Boolean>get("failSave")) throw new IllegalStateException("Sample save failure");
                ctx.put("saved", snapshot);
            });
        }).awaitModularUI().ticks(2);
    }

    private static DeckEditorModel model(TestContext ctx) {
        return ctx.get("model");
    }
}

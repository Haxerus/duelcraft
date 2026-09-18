package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Controlled replies exercise the real dark editor without server/database fixtures. */
@LDLRegisterClient(name = "collection_eligibility", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionEligibilityScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection").requiresWorld(false); }

    @Override public void define(ScenarioBuilder s) {
        s.openScreen("owned wrong-placement list", ctx -> {
            var main = new ArrayList<>(java.util.stream.IntStream.rangeClosed(10001, 10040).boxed().toList());
            main.add(10066);
            var owned = new HashMap<>(CollectionFixture.owned());
            owned.put(CollectionFixture.FIRST, 1L);
            var model = ctx.put("model", new DeckEditorModel(new DeckList(main,
                    List.of(10067, 10068, 10069, 10070), List.of()), owned));
            var replies = ctx.put("replies", new ArrayList<CompletableFuture<CollectionReply>>());
            Function<CollectionCommand, CompletionStage<CollectionReply>> request = command -> {
                ctx.put("command", command);
                var reply = new CompletableFuture<CollectionReply>();
                replies.add(reply);
                return reply;
            };
            // Use the existing injection factory; no production test instrumentation.
            try {
                var factory = CollectionScreen.class.getDeclaredMethod("create", DeckEditorModel.class, List.class,
                        IntFunction.class, UUID.class, DeckSaveHandler.class, CollectionQuery.class,
                        Function.class, Supplier.class, Executor.class);
                factory.setAccessible(true);
                var screen = (CollectionScreen) factory.invoke(null, model, CollectionFixture.cards(),
                        (IntFunction<net.minecraft.resources.ResourceLocation>) code -> null, CollectionFixture.LIST_ID,
                        null, CollectionFixture.query(), request,
                        (Supplier<CompletionStage<ClientCollectionState.View>>) CompletableFuture<ClientCollectionState.View>::new,
                        (Executor) Runnable::run);
                Object controller = ctx.getField(screen, "controller");
                SavedDeckController lists = ctx.getField(controller, "lists");
                ctx.put("lists", lists);
                lists.applyView(new ClientCollectionState.View(4, owned, List.of(), CollectionFixture.LIST_ID));
                return screen;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not create controlled editor", error);
            }
        }).awaitModularUI().ticks(3)
         .click("#activate-deck")
         .step("reject owned Fusion in Main without shortages", ctx -> reply(ctx, new CollectionReply.Rejected(
                 CollectionError.INELIGIBLE, 4, placement()))).ticks(3)
         .checkVisible("#eligibility-dialog")
         .checkBounds("#eligibility-panel", DuelScreenScenario::insideViewport)
         .checkTextContains("#eligibility-issue-0", "10066")
         .checkTextContains("#eligibility-issue-0", "Extra")
         .check("reason is translated", ctx -> !ctx.el("#eligibility-issue-0").text().contains("duelcraft.collection.issue"))
         .check("owned wrong placement stays clean", ctx -> !ctx.<SavedDeckController>get("lists").dirty())
         .hoverAt(-100, -100).screenshot("owned-placement-rejection");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        s.checkHidden("#eligibility-dialog")
         .click("#add-card").click("#save-deck")
         .step("acknowledge invalidating Save", ctx -> {
             var submitted = ((CollectionCommand.Save) ctx.get("command")).deck();
             ctx.put("submitted", submitted);
             reply(ctx, new CollectionReply.Changed(5, null, submitted, 0, placement()));
         }).ticks(3)
         .checkTextContains("#editor-status", "List saved")
         .checkTextContains("#editor-status", "active selection cleared")
         .checkTextContains("#eligibility-summary", "List saved")
         .checkTextContains("#eligibility-summary", "active selection cleared")
         .checkVisible("#eligibility-dialog")
         .check("acknowledged invalidating Save is clean and exact", ctx -> {
             SavedDeck submitted = ctx.get("submitted");
             return !ctx.<SavedDeckController>get("lists").dirty()
                     && ctx.<DeckEditorModel>get("model").draft().equals(submitted.cards())
                     && ctx.<SavedDeckController>get("lists").activeId() == null;
         }).hoverAt(-100, -100).screenshot("saved-active-cleared");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        s.click("#activate-deck")
         .step("full bounded report has additional omitted issues and shortage", ctx -> {
             var issues = new ArrayList<>(placement().problems());
             issues.add(new DeckEligibility.Issue("duelcraft.collection.issue.copies", 10066, 4, 3));
             for (int index = 0; index < 62; index++) issues.add(new DeckEligibility.Issue(
                     "duelcraft.collection.issue.unknown", 90000 + index, 0, 0));
             reply(ctx, new CollectionReply.Rejected(CollectionError.INELIGIBLE, 5,
                     new DeckEligibility.Report(issues, Map.of(10066, 2), true)));
         })
         .ticks(3)
         .checkCount("#eligibility-scroll .wrap", 66)
         .checkTextContains("#eligibility-issue-1", "4 copies")
         .checkTextContains("#eligibility-issue-1", "maximum is 3")
         .check("bounded details overflow inside the scroll surface", ctx -> {
             var scroll = (com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView) ctx.el("#eligibility-scroll").element();
             return scroll.getContainerHeight() > scroll.viewPort.getContentHeight();
         })
         .step("scroll to shortages and omitted-problems notice", ctx ->
                 ((com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView) ctx.el("#eligibility-scroll").element())
                         .verticalScroller.setNormalizedValue(1)).ticks(3)
         .check("omitted-problems notice stays inside the scroll surface", ctx -> CollectionLayoutScenario.contained(
                 ctx.el("#eligibility-more").bounds(), ctx.el("#eligibility-scroll").bounds()))
         .checkTextContains("#eligibility-more", "Additional problems")
         .checkTextContains("#eligibility-missing-10066", "10066")
         .checkTextContains("#eligibility-missing-10066", "2")
         .hoverAt(-100, -100).screenshot("shortage-and-omitted-problems");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        CollectionRuntimeFixture.press(s, "#eligibility-details");
        s.checkVisible("#eligibility-dialog")
         .checkTextContains("#eligibility-more", "Additional problems")
         .teardown("close controlled editor", ctx -> ctx.mc().setScreen(null));
    }

    private static DeckEligibility.Report placement() {
        return new DeckEligibility.Report(List.of(new DeckEligibility.Issue(
                "duelcraft.collection.issue.main_placement", 10066, 65, 0)), Map.of(), false);
    }

    private static void reply(TestContext ctx, CollectionReply reply) {
        ctx.<ArrayList<CompletableFuture<CollectionReply>>>get("replies").getLast().complete(reply);
    }
}

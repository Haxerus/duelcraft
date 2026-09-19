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
        s.openScreen("unowned legal list", ctx -> {
            var main = new ArrayList<>(java.util.stream.IntStream.rangeClosed(10001, 10040).boxed().toList());
            var owned = new HashMap<>(CollectionFixture.owned());
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
                lists.applyView(new ClientCollectionState.View(4, owned, List.of(), null, false, null));
                return screen;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not create controlled editor", error);
            }
        }).awaitModularUI().ticks(3)
         .checkTextContains("#deck-shortages", "Ownership optional")
         .checkClass("#main-card-0 .missing-copy", "neutral")
         .click("#main-card-0")
         .checkClass("#inspector-missing", "neutral")
         .click("#activate-deck")
         .step("server approves optional-ownership activation", ctx -> reply(ctx, new CollectionReply.Changed(
                 5, CollectionFixture.LIST_ID, null, 0,
                 new DeckEligibility.Report(List.of(), Map.of(CollectionFixture.FIRST, 1), false, false, null))))
         .ticks(3)
         .checkHidden("#eligibility-dialog")
         .checkTextContains("#list-active-status", "active")
         .hoverAt(-100, -100).screenshot("optional-ownership-activation")
         .step("switch controlled snapshot to required ownership", ctx ->
                 ctx.<SavedDeckController>get("lists").applyView(new ClientCollectionState.View(
                         5, ctx.<DeckEditorModel>get("model").owned(), List.of(), null, true, null)))
         .ticks(3)
         .checkTextContains("#deck-shortages", "Ownership required")
         .check("required shortage uses warning treatment", ctx ->
                 !ctx.el("#main-card-0 .missing-copy").element().hasClass("neutral"))
         .click("#activate-deck")
         .step("server rejects required-ownership shortage", ctx -> reply(ctx, new CollectionReply.Rejected(
                 CollectionError.INELIGIBLE, 5,
                 new DeckEligibility.Report(List.of(), Map.of(CollectionFixture.FIRST, 1), false, true, null))))
         .ticks(3)
         .checkVisible("#eligibility-dialog")
         .checkBounds("#eligibility-panel", DuelScreenScenario::insideViewport)
         .checkTextContains("#eligibility-missing-10001", "10001")
         .check("required shortage stays clean", ctx -> !ctx.<SavedDeckController>get("lists").dirty())
         .hoverAt(-100, -100).screenshot("required-ownership-rejection");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        s.checkHidden("#eligibility-dialog")
         .step("switch controlled snapshot to optional ownership", ctx ->
                 ctx.<SavedDeckController>get("lists").applyView(new ClientCollectionState.View(
                         5, ctx.<DeckEditorModel>get("model").owned(), List.of(), null, false, null)))
         .click("#activate-deck")
         .step("server applies companion denial while ownership is optional", ctx -> reply(ctx,
                 new CollectionReply.Rejected(CollectionError.INELIGIBLE, 5,
                         new DeckEligibility.Report(List.of(), Map.of(CollectionFixture.FIRST, 1), false,
                                 false, "Era locked"))))
         .ticks(3)
         .checkVisible("#eligibility-dialog")
         .checkTextContains("#eligibility-restriction", "Era locked")
         .check("optional shortage is not presented as a denial reason", ctx ->
                 ctx.elOpt("#eligibility-missing-10001").isEmpty())
         .checkTextContains("#editor-status", "Era locked")
         .hoverAt(-100, -100).screenshot("optional-companion-denial");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        s.step("surface first-open clearance without evaluating displayed draft", ctx ->
                 ctx.<SavedDeckController>get("lists").applyView(new ClientCollectionState.View(
                         6, ctx.<DeckEditorModel>get("model").owned(), List.of(), null, false,
                         new DeckEligibility.Report(List.of(), Map.of(), false, false, "Previously restricted"))))
         .ticks(3)
         .checkVisible("#eligibility-dialog")
         .checkTextContains("#editor-status", "previous active list")
         .checkTextContains("#eligibility-restriction", "Previously restricted")
         .hoverAt(-100, -100).screenshot("first-open-active-cleared");
        CollectionRuntimeFixture.press(s, "#eligibility-close");
        s.click("#activate-deck")
         .step("full bounded report has additional omitted issues and shortage", ctx -> {
             var issues = new ArrayList<DeckEligibility.Issue>();
             issues.add(new DeckEligibility.Issue("duelcraft.collection.issue.copies", 10066, 4, 3));
             for (int index = 0; index < 62; index++) issues.add(new DeckEligibility.Issue(
                     "duelcraft.collection.issue.unknown", 90000 + index, 0, 0));
             reply(ctx, new CollectionReply.Rejected(CollectionError.INELIGIBLE, 6,
                     new DeckEligibility.Report(issues, Map.of(10066, 2), true, true, null)));
         })
         .ticks(3)
         .checkCount("#eligibility-scroll .wrap", 65)
         .checkTextContains("#eligibility-issue-0", "4 copies")
         .checkTextContains("#eligibility-issue-0", "maximum is 3")
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

    private static void reply(TestContext ctx, CollectionReply reply) {
        ctx.<ArrayList<CompletableFuture<CollectionReply>>>get("replies").getLast().complete(reply);
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Collection menu behavior with duplicate names and more rows than the visible list. */
@LDLRegisterClient(name = "collection_controls", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionControlsScenario implements UIScenario {
    private static final UUID ACTIVE = new UUID(0, 1);
    private static final UUID SELECTED = new UUID(0, 6);
    private static final String LONG_NAME = "A long saved deck name with enough words to exercise the compact header without hiding its actions";

    @Override public void configure(ScenarioOptions options) {
        options.tags("collection").requiresWorld(false);
    }

    @Override public void define(ScenarioBuilder s) {
        s.openScreen("seven saved lists with duplicate names", CollectionControlsScenario::screen)
         .awaitModularUI().ticks(4)
         .checkText("#editor-title", "My Collection")
         .check("seven UUID rows with duplicate names", ctx -> {
             Object controller = ctx.getField(ctx.screen(), "controller");
             Map<?, ?> rows = ctx.getField(controller, "savedRows");
             return rows.size() == 7 && rows.containsKey(SELECTED);
         });
        CollectionRuntimeFixture.press(s, "#saved-lists");
        s.checkVisible("#lists-dialog").checkBounds("#lists-panel", DuelScreenScenario::insideViewport)
         .check("saved list viewport shows five full rows", ctx ->
                 CollectionLayoutScenario.contained(ctx.el("#saved-row-4").bounds(),
                         ctx.el("#saved-list-scroll").bounds()))
         .hoverAt(-100, -100).screenshot("collection-saved-rows-top")
         .step("scroll to sixth saved row", ctx -> ctx.el("#saved-list-scroll").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(1, true)).ticks(3)
         .checkBounds("#saved-row-5", DuelScreenScenario::insideViewport)
         .screenshot("collection-saved-rows");
        CollectionRuntimeFixture.press(s, "#saved-row-5");
        s.check("row selection keeps the current edited deck", ctx -> lists(ctx).id().equals(ACTIVE)
                && !ctx.<List<UUID>>get("reads").contains(SELECTED));
        CollectionRuntimeFixture.press(s, "#list-delete");
        s.checkVisible("#delete-dialog").checkTextContains("#delete-title", "Same name")
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("Escape dismisses delete above My decks", ctx ->
                 !ctx.el("#delete-dialog").isVisible() && ctx.el("#lists-dialog").isVisible());
        CollectionRuntimeFixture.press(s, "#list-open");
        s.check("Open reads the selected UUID even with duplicate names", ctx ->
                lists(ctx).id().equals(SELECTED) && ctx.<List<UUID>>get("reads").contains(SELECTED))
         .checkHidden("#lists-dialog")
         .checkText("#list-name", "Same name")
         .screenshot("collection-open-selected");

        CollectionRuntimeFixture.press(s, "#import-export");
        s.checkVisible("#import-dialog")
         .checkBounds("#import-panel", DuelScreenScenario::insideViewport)
         .check("empty local folder disables Import YDK", ctx -> {
             var picker = ctx.el("#list-import-picker").as(Selector.class);
             return !ctx.el("#list-import").element().isActive() && picker.getValue() == null;
         })
         .screenshot("collection-import-export");
        CollectionRuntimeFixture.press(s, "#list-import-picker");
        s.key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("first Escape closes only the dropdown", ctx ->
                 !ctx.el("#list-import-picker").as(Selector.class).isOpen()
                         && ctx.el("#import-dialog").isVisible());
        s.key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkHidden("#import-dialog").checkScreen(CollectionScreen.class);
        CollectionRuntimeFixture.press(s, "#help-button");
        s.checkTextContains("#help-controls", "Browse cards")
         .checkTextContains("#help-navigation", "Esc closes")
         .checkTextContains("#help-supported-checks", "Activation")
         .checkTextContains("#help-ownership", "deposited copies")
         .checkBounds("#help-panel", DuelScreenScenario::insideViewport)
         .screenshot("collection-help");
        s.key(GLFW.GLFW_KEY_ESCAPE).ticks(2).checkHidden("#help-dialog");

        s.typeInto("#list-name", LONG_NAME).ticks(2)
         .check("long name stays in the draft", ctx -> LONG_NAME.equals(lists(ctx).name()));
        CollectionRuntimeFixture.press(s, "#new-deck");
        s.checkVisible("#close-dialog")
         .checkText("#close-title", "Save changes?")
         .checkBounds("#close-panel", DuelScreenScenario::insideViewport)
         .screenshot("collection-save-changes");
        CollectionRuntimeFixture.press(s, "#close-cancel");
        s.check("Keep editing retains selected dirty draft", ctx ->
                lists(ctx).id().equals(SELECTED) && LONG_NAME.equals(lists(ctx).name()));
        CollectionRuntimeFixture.press(s, "#new-deck");
        CollectionRuntimeFixture.press(s, "#close-save");
        s.check("pending save guards navigation", ctx ->
                lists(ctx).pending() && lists(ctx).id().equals(SELECTED)
                        && !ctx.el("#save-deck").element().isActive())
         .screenshot("collection-pending-save")
         .step("reject save", ctx -> ctx.<CompletableFuture<CollectionReply>>get("saveReply")
                 .complete(new CollectionReply.Rejected(CollectionError.INVALID, 4,
                         new DeckEligibility.Report(List.of(), Map.of(), false, false, null))))
         .ticks(3)
         .check("failed save keeps draft and decision", ctx ->
                 lists(ctx).id().equals(SELECTED) && LONG_NAME.equals(lists(ctx).name())
                         && ctx.el("#editor-status").text().contains("retained"))
         .screenshot("collection-save-failed")
         .teardown("close collection and empty import fixture", ctx -> {
             ctx.mc().setScreen(null);
             try { Files.deleteIfExists(ctx.get("emptyImportDir")); }
             catch (IOException error) { throw new UncheckedIOException(error); }
         });
    }

    @SuppressWarnings("unchecked")
    private static CollectionScreen screen(TestContext ctx) {
        var model = new DeckEditorModel(new DeckList(List.of(), List.of(), List.of()), CollectionFixture.owned());
        var decks = new HashMap<UUID, SavedDeck>();
        var summaries = new ArrayList<CollectionReply.Summary>();
        for (int i = 1; i <= 7; i++) {
            UUID id = new UUID(0, i);
            String name = i == 7 ? LONG_NAME : "Same name";
            var deck = new SavedDeck(id, name, new DeckList(List.of(10000 + i), List.of(), List.of()));
            decks.put(id, deck);
            summaries.add(new CollectionReply.Summary(id, name, 1, 0, 0));
        }
        var view = new ClientCollectionState.View(4, CollectionFixture.owned(), summaries, ACTIVE);
        ctx.put("reads", new ArrayList<UUID>());
        Function<CollectionCommand, CompletionStage<CollectionReply>> request = command -> {
            if (command instanceof CollectionCommand.ReadDeck read) {
                ctx.<List<UUID>>get("reads").add(read.id());
                return CompletableFuture.completedFuture(new CollectionReply.Deck(4, decks.get(read.id())));
            }
            var reply = new CompletableFuture<CollectionReply>();
            if (command instanceof CollectionCommand.Save) ctx.put("saveReply", reply);
            return reply;
        };
        Supplier<CompletionStage<ClientCollectionState.View>> refresh = () -> CompletableFuture.completedFuture(view);
        try {
            var method = CollectionScreen.class.getDeclaredMethod("create", DeckEditorModel.class, List.class,
                    IntFunction.class, UUID.class, DeckSaveHandler.class, CollectionQuery.class,
                    Function.class, Supplier.class, Executor.class);
            method.setAccessible(true);
            var screen = (CollectionScreen) method.invoke(null, model, CollectionFixture.cards(),
                    (IntFunction<net.minecraft.resources.ResourceLocation>) code -> null, UUID.randomUUID(),
                    null, CollectionFixture.query(), request, refresh, (Executor) ctx.mc()::execute);
            Object controller = ctx.getField(screen, "controller");
            var emptyDir = Files.createDirectory(ctx.mc().gameDirectory.toPath()
                    .resolve("duelcraft/uitest-empty-" + UUID.randomUUID()));
            ctx.put("emptyImportDir", emptyDir);
            var importField = controller.getClass().getDeclaredField("imports");
            importField.setAccessible(true);
            importField.set(controller, new DeckImportService(new DeckRegistry(emptyDir)));
            ctx.<SavedDeckController>getField(controller, "lists").openInitial(view);
            return screen;
        } catch (ReflectiveOperationException | IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static SavedDeckController lists(TestContext ctx) {
        return ctx.getField(ctx.getField(ctx.screen(), "controller"), "lists");
    }
}

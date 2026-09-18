package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real inspector/name/save controls with deliberately delayed acknowledgements. */
@LDLRegisterClient(name = "collection_saved_list", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionSavedListScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.tags("collection").requiresWorld(false);
    }

    @Override public void define(ScenarioBuilder s) {
        s.openScreen("saved passcode placeholders", ctx -> {
            var model = ctx.put("model", new DeckEditorModel(new DeckList(List.of(99999999), List.of(), List.of()), Map.of()));
            ctx.put("attempts", new ArrayList<CompletableFuture<SavedDeck>>());
            return CollectionScreen.create(model, CollectionFixture.cards(), code -> null, CollectionFixture.LIST_ID,
                    (name, cards) -> {
                        ctx.put("submitted", new SavedDeck(CollectionFixture.LIST_ID, name, cards));
                        var result = new CompletableFuture<SavedDeck>();
                        ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").add(result);
                        return result;
                    }, CollectionFixture.query());
        }).awaitModularUI().ticks(3)
         .click("#main-card-0").ticks(2)
         .checkTextContains("#inspector-name", "99999999")
         .check("unknown passcode can be removed but not added", ctx ->
                 ctx.el("#remove-card").element().isActive() && !ctx.el("#add-card").element().isActive())
         .screenshot("collection-unknown-passcode")
         .click("#remove-card").ticks(2)
         .checkCount("#main-grid .card-tile", 0)
         .click("#save-deck").ticks(2)
         .check("save keeps dirty state until acknowledgement", ctx -> ctx.<DeckEditorModel>get("model").dirty())
         .check("pending save freezes mutations and keeps search", ctx ->
                 !ctx.el("#save-deck").element().isActive() && !ctx.el("#remove-card").element().isActive()
                 && ctx.el("#collection-search").element().isActive())
         .step("acknowledge removed unknown passcode", CollectionSavedListScenario::acknowledge).ticks(2)
         .check("acknowledged list is empty and clean", ctx -> ctx.<DeckEditorModel>get("model").draft().main().isEmpty()
                 && !ctx.<DeckEditorModel>get("model").dirty())
         .step("open saved lists", ctx -> CollectionLayoutScenario.press(ctx, "#saved-lists"))
         .step("release picker button", CollectionLayoutScenario::release).ticks(2)
         .checkVisible("#lists-dialog")
         .checkBounds("#lists-panel", DuelScreenScenario::insideViewport)
         .checkText("#lists-title", "Saved lists")
         .checkTextContains("#list-name-label", "128 printable")
         .checkTextContains("#list-supported-checks", "deposited copies")
         .checkTextContains("#list-save-first", "acknowledged saved list")
         .typeInto("#list-name", "Renamed list").ticks(2)
         .check("name-only changes disable activation", ctx -> !ctx.el("#list-activate").element().isActive())
         .screenshot("collection-saved-list-picker")
         .step("close saved lists", ctx -> CollectionLayoutScenario.press(ctx, "#lists-close"))
         .step("release Done", CollectionLayoutScenario::release).ticks(2)
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .step("press Save before leaving", ctx -> CollectionLayoutScenario.press(ctx, "#close-save"))
         .step("release Save", CollectionLayoutScenario::release).ticks(2)
         .checkScreen(CollectionScreen.class)
         .check("one save per acknowledgement", ctx -> ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").size() == 2)
         .screenshot("collection-save-awaiting-acknowledgement")
         .step("acknowledge renamed list", CollectionSavedListScenario::acknowledge).ticks(2)
         .check("only acknowledgement completes close", ctx -> !(ctx.screen() instanceof CollectionScreen))
         .openScreen("initial collection synchronization failure", ctx -> {
             var model = ctx.put("unavailableModel", new DeckEditorModel(new DeckList(List.of(), List.of(), List.of()), Map.of()));
             var refresh = ctx.put("unavailableRefresh", new CompletableFuture<ClientCollectionState.View>());
             return CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                     CollectionFixture.LIST_ID, null, CollectionFixture.query(), () -> refresh);
         }).awaitModularUI().ticks(3)
         .step("fail initial snapshot", ctx -> ctx.<CompletableFuture<ClientCollectionState.View>>get("unavailableRefresh")
                 .completeExceptionally(new IllegalStateException("Initial synchronization unavailable")))
         .ticks(2)
         .checkTextContains("#editor-status", "refresh failed")
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .check("failed initial snapshot keeps local close decisions usable", ctx ->
                 !ctx.el("#close-save").isActive() && ctx.el("#close-cancel").isActive() && ctx.el("#close-discard").isActive())
         .screenshot("collection-initial-refresh-failure-close")
         .step("Cancel after failed synchronization", ctx -> CollectionLayoutScenario.press(ctx, "#close-cancel"))
         .step("release Cancel", CollectionLayoutScenario::release).ticks(2)
         .checkScreen(CollectionScreen.class)
         .checkHidden("#close-dialog")
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .checkVisible("#close-dialog")
         .step("Discard after failed synchronization", ctx -> CollectionLayoutScenario.press(ctx, "#close-discard"))
         .step("release Discard", CollectionLayoutScenario::release).ticks(2)
         .check("Discard closes without a ready snapshot", ctx -> !(ctx.screen() instanceof CollectionScreen))
         .teardown("close collection", ctx -> ctx.mc().setScreen(null));
    }

    private static void acknowledge(TestContext ctx) {
        ctx.<ArrayList<CompletableFuture<SavedDeck>>>get("attempts").getLast().complete(ctx.get("submitted"));
    }
}

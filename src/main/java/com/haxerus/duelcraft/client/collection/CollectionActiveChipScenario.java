package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.uitest.CollectionFixture;
import com.haxerus.duelcraft.client.uitest.GalleryVisualAssertions;
import com.haxerus.duelcraft.client.uitest.ManagementTestInput;
import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

@LDLRegisterClient(name = "collection_active_chip", group = "duelcraft_gallery", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionActiveChipScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        s.openScreen("active chip", ctx -> {
            var model = CollectionFixture.model(false);
            var screen = CollectionScreen.create(model, CollectionFixture.cards(), code -> null,
                    CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
            Object controller = ctx.getField(screen, "controller");
            SavedDeckController lists = ctx.getField(controller, "lists");
            ctx.put("lists", lists);
            ctx.put("controller", controller);
            ctx.setField(lists, "request", (Function<CollectionCommand, CompletionStage<CollectionReply>>) command -> {
                ctx.check("clear sends current authoritative revision", command instanceof CollectionCommand.ClearActive clear
                        && clear.expectedRevision() == lists.revision());
                ctx.put("requests", ctx.<Integer>get("requests") + 1);
                return ctx.get("reply");
            });
            ctx.setField(lists, "refresh", (Supplier<CompletionStage<ClientCollectionState.View>>) () ->
                    CompletableFuture.completedFuture(new ClientCollectionState.View(lists.revision(), model.owned(),
                            lists.summaries(), lists.activeId())));
            lists.applyView(new ClientCollectionState.View(1, model.owned(), List.of(
                    new CollectionReply.Summary(CollectionFixture.LIST_ID, "Active deck", 40, 0, 0),
                    new CollectionReply.Summary(UUID.randomUUID(), "Another saved deck", 40, 0, 0)), CollectionFixture.LIST_ID));
            return screen;
        }).awaitModularUI().ticks(3)
          .step("open My decks", ctx -> ManagementTestInput.click(ctx, "#saved-lists")).ticks(2).click("#saved-row-1")
          .hoverAt(-100, -100).ticks(2).checkText("#list-clear-active", "Active")
          .step("remember chip bounds", ctx -> ctx.put("chipBounds", ctx.el("#list-clear-active").bounds()))
          .screenshot("active-chip-rest")
          .hover("#list-clear-active").ticks(2).checkText("#list-clear-active", "Clear")
          .check("hover keeps chip bounds", ctx -> {
              var before = ctx.<ElementBounds>get("chipBounds");
              var after = ctx.el("#list-clear-active").bounds();
              return Math.abs(before.x() - after.x()) < .1f && Math.abs(before.y() - after.y()) < .1f
                      && Math.abs(before.width() - after.width()) < .1f && Math.abs(before.height() - after.height()) < .1f;
          })
          .screenshot("active-chip-hover")
          .hoverAt(-100, -100).ticks(2).checkText("#list-clear-active", "Active");
        for (int key : new int[]{0, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE}) {
            s.step("prepare clear " + key, ctx -> {
                SavedDeckController lists = ctx.get("lists");
                lists.applyView(new ClientCollectionState.View(lists.revision(), java.util.Map.of(), lists.summaries(), CollectionFixture.LIST_ID));
                ctx.put("reply", new CompletableFuture<CollectionReply>());
                ctx.put("requests", 0);
                ctx.put("selected", ctx.getField(ctx.get("controller"), "selectedSavedId"));
            }).ticks(2);
            if (key == 0) s.step("click chip", ctx -> ManagementTestInput.click(ctx, "#list-clear-active"));
            else s.step("Tab to chip", ctx -> GalleryVisualAssertions.tabTo(ctx, "#list-clear-active")).ticks(2)
                    .checkText("#list-clear-active", "Clear").screenshot("active-chip-focus-" + key).key(key);
            s.ticks(2).check("one request, no row selection, activation awaits server", ctx -> {
                SavedDeckController lists = ctx.get("lists");
                return ctx.<Integer>get("requests") == 1 && lists.activeId() != null
                        && ctx.get("selected").equals(ctx.getField(ctx.get("controller"), "selectedSavedId"))
                        && !ctx.el("#list-clear-active").as(Button.class).isActive();
            }).step("server acknowledges clear", ctx -> {
                SavedDeckController lists = ctx.get("lists");
                ctx.<CompletableFuture<CollectionReply>>get("reply").complete(
                        new CollectionReply.Changed(lists.revision() + 1, null, null, 0, lists.eligibility()));
            }).ticks(2).checkCount("#list-clear-active", 0).checkCount(".saved-row", 2);
        }
    }
}

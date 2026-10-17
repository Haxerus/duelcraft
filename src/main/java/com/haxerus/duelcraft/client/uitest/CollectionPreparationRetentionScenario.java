package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.ClientPayloadHandler;
import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.client.interaction.PreparationRouting;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import com.haxerus.duelcraft.server.collection.CollectionReplyPayload;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Real command/editor with synthetic preparation pushes and delayed real collection replies. */
@LDLRegisterClient(name = "collection_preparation_retention", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionPreparationRetentionScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection", "preparation"); }

    @Override public void define(ScenarioBuilder s) {
        s.closeScreen().timeoutMs(60000).server("capture collection before delayed Save", CollectionRuntimeFixture::capture)
         .step("capture collection receiver", ctx -> {
            try {
                var field = ClientPayloadHandler.class.getDeclaredField("collectionReceiver");
                field.setAccessible(true);
                ctx.put("receiver", field.get(null));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot capture collection receiver", error);
            }
        });
        CollectionRuntimeFixture.open(s);
        s.step("dirty original editor", ctx -> {
            Object controller = ctx.getField(ctx.screen(), "controller");
            DeckEditorModel model = ctx.getField(controller, "model");
            SavedDeckController lists = ctx.getField(controller, "lists");
            model.add(DeckEditorModel.Section.MAIN, 89631139);
            lists.rename("Retained unsaved draft");
            ctx.put("originalScreen", ctx.screen());
            ctx.put("model", model); ctx.put("lists", lists);
            ctx.put("draft", model.draft()); ctx.put("id", lists.id());
        }).step("accepted preparation suspends editor", ctx -> push(PreparationView.Mode.RPS))
          .step("attempt collection reopen while preparing", ctx ->
                  ctx.require("registered collection command handled", ClientCommandHandler.runCommand("duel collection")))
          .ticks(30)
          .check("preparation blocks a replacement editor", ctx -> !(ctx.screen() instanceof CollectionScreen))
          .step("next preparation update", ctx -> push(PreparationView.Mode.RPS))
          .step("cancel preparation", ctx -> push(PreparationView.Mode.IDLE))
          .ticks(10)
          .check("cancel does not force the suspended editor open", ctx -> !(ctx.screen() instanceof CollectionScreen))
          .step("explicitly resume cancelled draft", ctx -> ctx.require("draft can resume", PreparationRouting.restoreEditor()))
          .check("cancel restores original dirty identity name and cards", CollectionPreparationRetentionScenario::retained);
        delayedOpen(s, false);
        delayedOpen(s, true);
        s.screenshot("retained-draft-after-reopen-and-delayed-refresh")
         .step("hold Save acknowledgement with navigation queued", ctx -> {
             var held = new ArrayList<CollectionReplyPayload>(); ctx.put("held", held);
             ClientPayloadHandler.setCollectionReceiver(held::add);
             SavedDeckController lists = ctx.get("lists");
             lists.navigate(() -> ctx.put("unexpectedNavigation", true));
             lists.save();
         }).waitUntil("real Save reply held", ctx -> !ctx.<List<CollectionReplyPayload>>get("held").isEmpty())
         .step("preparation interrupts pending Save", ctx -> push(PreparationView.Mode.RPS))
         .step("release Save after forced route", ctx -> {
             ClientPayloadHandler.setCollectionReceiver(ctx.<Consumer<CollectionReplyPayload>>get("receiver"));
             release(ctx);
         }).ticks(5)
         .check("acknowledged Save cannot execute superseded navigation", ctx -> ctx.get("unexpectedNavigation") == null
                 && ctx.screen() instanceof com.haxerus.duelcraft.client.interaction.DuelLobbyScreen)
         .step("cancel and resume acknowledged draft", ctx -> {
             push(PreparationView.Mode.IDLE);
             ctx.require("saved session can resume", PreparationRouting.restoreEditor());
         }).check("same editor retains acknowledged cards and name", ctx -> ctx.screen() == ctx.get("originalScreen")
                 && ctx.<DeckEditorModel>get("model").draft().equals(ctx.get("draft"))
                 && !ctx.<SavedDeckController>get("lists").dirty())
         .teardown("restore receiver and clear connection routing", ctx -> {
             ClientPayloadHandler.setCollectionReceiver(ctx.<Consumer<CollectionReplyPayload>>get("receiver"));
             release(ctx);
             PreparationRouting.disconnect();
             DuelcraftClient.preparation().disconnect();
             if (ctx.mc().player != null && ctx.mc().getConnection() != null) DuelcraftClient.preparation().connect();
             ctx.mc().setScreen(null);
         });
        CollectionRuntimeFixture.teardown(s);
    }

    private static void delayedOpen(ScenarioBuilder s, boolean cancelBeforeReply) {
        String timing = cancelBeforeReply ? "after cancellation" : "during preparation";
        s.step("hold collection refresh " + timing, ctx -> {
            var held = new ArrayList<CollectionReplyPayload>();
            ctx.put("held", held);
            ClientPayloadHandler.setCollectionReceiver(held::add);
            ctx.require("registered command begins refresh", ClientCommandHandler.runCommand("duel collection"));
            ctx.put("refresh", DuelcraftClient.getCollectionClient().refresh().toCompletableFuture());
        }).waitUntil("real collection reply held", ctx -> !ctx.<List<CollectionReplyPayload>>get("held").isEmpty())
          .step("preparation starts with open pending", ctx -> push(PreparationView.Mode.RPS));
        if (cancelBeforeReply) s.step("cancel before old open completes", ctx -> push(PreparationView.Mode.IDLE));
        s.step("release real reply " + timing, ctx -> {
            ClientPayloadHandler.setCollectionReceiver(ctx.<Consumer<CollectionReplyPayload>>get("receiver"));
            release(ctx);
        }).waitUntil("real pending refresh completes " + timing,
                ctx -> ctx.<CompletableFuture<?>>get("refresh").isDone()).ticks(2)
          .check("real refresh succeeded " + timing,
                ctx -> !ctx.<CompletableFuture<?>>get("refresh").isCompletedExceptionally());
        if (!cancelBeforeReply) {
            s.check("late refresh cannot open during preparation", ctx -> !(ctx.screen() instanceof CollectionScreen))
             .step("another preparation update after reply", ctx -> push(PreparationView.Mode.RPS))
             .step("cancel after old open completes", ctx -> push(PreparationView.Mode.IDLE)).ticks(10);
        }
        s.check("late refresh does not restore the editor " + timing, ctx -> !(ctx.screen() instanceof CollectionScreen))
         .step("explicitly resume draft " + timing, ctx -> ctx.require("draft can resume", PreparationRouting.restoreEditor()))
         .check("original dirty editor retained " + timing, CollectionPreparationRetentionScenario::retained);
    }

    private static void release(TestContext ctx) {
        List<CollectionReplyPayload> held = ctx.get("held");
        if (held != null) {
            for (var reply : List.copyOf(held)) ctx.<Consumer<CollectionReplyPayload>>get("receiver").accept(reply);
            held.clear();
        }
    }

    private static boolean retained(TestContext ctx) {
        DeckEditorModel model = ctx.get("model"); SavedDeckController lists = ctx.get("lists");
        return ctx.screen() == ctx.get("originalScreen") && !ctx.<Boolean>getField(lists, "disposed")
                && model.dirty() && lists.dirty()
                && model.draft().equals(ctx.get("draft")) && lists.id().equals(ctx.get("id"))
                && lists.name().equals("Retained unsaved draft");
    }

    private static void push(PreparationView.Mode mode) {
        var current = DuelcraftClient.preparation().view();
        long revision = current == null ? 1 : current.revision() + 1;
        boolean active = mode == PreparationView.Mode.RPS;
        var view = new PreparationView(revision, mode, active ? UUID.randomUUID() : null,
                active ? UUID.randomUUID() : null, active ? UUID.randomUUID() : null,
                active ? "Fixture opponent" : "", false, false, false, active ? 60000 : 0,
                active ? new DuelSettings(DuelRule.MR5, 42, new PlayerOptions(8000, 5, 1)) : null);
        ClientPayloadHandler.handlePreparation(new PreparationStatePayload(null, PreparationResult.OK, view), null);
    }
}

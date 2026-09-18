package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** Real widgets, authenticated save/read/activation packets and server-confirmed snapshots. */
@LDLRegisterClient(name = "collection_saved_decks", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionSavedDeckScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection", "network"); }
    @Override public void define(ScenarioBuilder s) {
        s.server("capture and seed unowned collection", sc -> {
            CollectionRuntimeFixture.capture(sc);
            sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(20, Map.of(), Map.of(), null)));
        }).waitUntil("real catalog ready", ctx -> DuelcraftClient.getCollectionCatalog().toCompletableFuture().isDone())
          .step("choose real catalog card", ctx -> ctx.put("code", DuelcraftClient.getCollectionCatalog().toCompletableFuture().join().getFirst().code()));
        CollectionRuntimeFixture.open(s);
        s.focus("#collection-search").step("type real passcode", ctx -> {
            for (char digit : String.valueOf(ctx.<Integer>get("code")).toCharArray()) ctx.input().charTyped(digit, 0);
        })
         .waitUntil("real search result mounted", ctx -> ctx.elOpt("#collection-card-" + ctx.<Integer>get("code")).isPresent())
         .step("select real search card", ctx -> CollectionLayoutScenario.press(ctx, "#collection-card-" + ctx.<Integer>get("code")))
         .step("release selected card", CollectionLayoutScenario::release).ticks(2)
         .click("#add-card").click("#section-extra").click("#add-card")
         .click("#section-side").click("#add-card")
         .checkTextContains("#inspector-missing", "3");
        CollectionRuntimeFixture.press(s, "#saved-lists");
        s.typeInto("#list-name", "Runtime unowned draft");
        CollectionRuntimeFixture.press(s, "#lists-close");
        // Press and inspect in the same client step: no fabricated acknowledgement delay.
        s.step("Save enters pending before real packet acknowledgement", ctx -> {
            CollectionLayoutScenario.press(ctx, "#save-deck");
            ctx.check("real Save freezes mutations while pending", !ctx.el("#save-deck").isActive() && !ctx.el("#add-card").isActive());
            ctx.check("draft remains unsaved before acknowledgement", ctx.el("#deck-summary").text().contains("Unsaved"));
            CollectionLayoutScenario.release(ctx);
        }).waitUntilServer("submitted draft stored", sc -> CollectionRuntimeFixture.data(sc).revision() == 21)
         .waitUntil("Save acknowledgement applied", ctx -> ctx.el("#save-deck").isActive() && ctx.el("#editor-status").text().contains("List saved"))
         .checkTextContains("#deck-summary", "Saved list")
         .serverGet("capture authoritative saved state", "expected", CollectionRuntimeFixture::data)
         .step("record exact server-confirmed saved fixture", ctx -> ctx.attach("saved-state", ctx.<PlayerCollectionData>get("expected").toString()))
         .checkServer("unowned draft name and all sections saved exactly", sc -> {
             var data = CollectionRuntimeFixture.data(sc);
             int code = sc.get("code");
             return data.counts().isEmpty() && data.activeDeckId() == null && data.decks().size() == 1
                     && data.decks().values().iterator().next().name().equals("Runtime unowned draft")
                     && data.decks().values().iterator().next().cards().equals(new DeckList(List.of(code), List.of(code), List.of(code)));
         }).hoverAt(-100, -100).screenshot("collection-real-save-acknowledged")
         .key(GLFW.GLFW_KEY_ESCAPE).ticks(2)
         .check("acknowledged Save permits close", ctx -> !(ctx.screen() instanceof com.haxerus.duelcraft.client.collection.CollectionScreen));
        CollectionRuntimeFixture.open(s);
        s.waitUntil("reopened private snapshot equals server state", CollectionRuntimeFixture::snapshotMatches)
         .check("reopen preserves complete private snapshot", CollectionRuntimeFixture::snapshotMatches);
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> ctx.<PlayerCollectionData>get("expected").decks().keySet().iterator().next());
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#sample-label", "Runtime unowned draft")
         .check("ReadDeck loads every saved section exactly", ctx -> CollectionRuntimeFixture.draftMatches(ctx, ctx.<PlayerCollectionData>get("expected").decks().values().iterator().next().cards()));
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.checkCount("#main-grid .card-tile", 1).checkCount("#extra-grid .card-tile", 1)
         .click("#toggle-side").checkCount("#side-grid .card-tile", 1)
         .click("#main-card-0").checkTextContains("#inspector-missing", "3")
         .click("#activate-deck")
         .waitUntil("activation shortage visible", ctx -> ctx.el("#editor-status").text().contains("missing copies"))
         .checkTextContains("#deck-warnings", "3 missing")
         .checkServer("rejected activation leaves saved state unchanged", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
         .hoverAt(-100, -100).screenshot("collection-real-activation-shortage")
         .closeScreen()
         .server("seed unreadable disposable attachment", sc -> {
             var raw = new net.minecraft.nbt.CompoundTag();
             raw.putInt("schema", 999);
             raw.putString("recoverySentinel", "must remain untouched");
             sc.put("unreadableRaw", raw);
             sc.put("unreadableAttachment", CollectionAttachment.unreadable(raw));
             sc.player().setData(CollectionAttachments.COLLECTION, sc.<CollectionAttachment>get("unreadableAttachment"));
         }).step("request unreadable collection through real packets and dev route", ctx -> {
             ctx.put("unreadableReply", DuelcraftClient.getCollectionClient().request(new CollectionCommand.Open()).toCompletableFuture());
             ctx.require("unreadable access command handled", net.neoforged.neoforge.client.ClientCommandHandler.runCommand("duel collection"));
         }).waitUntil("DATA_UNAVAILABLE reply received", ctx -> ctx.<java.util.concurrent.CompletableFuture<CollectionReply>>get("unreadableReply").isDone())
         .check("unreadable data rejected with DATA_UNAVAILABLE", ctx ->
                 ctx.<java.util.concurrent.CompletableFuture<CollectionReply>>get("unreadableReply").join()
                         instanceof CollectionReply.Rejected rejected && rejected.error() == CollectionError.DATA_UNAVAILABLE)
         .waitUntil("route displays recovery guidance", ctx -> {
             List<net.minecraft.client.GuiMessage> messages = ctx.getField(ctx.mc().gui.getChat(), "allMessages");
             return messages.stream().anyMatch(message -> message.content().getString().contains("Ask the server administrator"));
         }).check("unreadable access leaves editor closed", ctx -> !(ctx.screen() instanceof com.haxerus.duelcraft.client.collection.CollectionScreen))
         .checkServer("unreadable original raw attachment remains untouched", sc -> {
             var current = sc.player().getData(CollectionAttachments.COLLECTION);
             return current == sc.<CollectionAttachment>get("unreadableAttachment") && current.data().isEmpty()
                     && CollectionAttachment.SERIALIZER.write(current, sc.player().registryAccess()).equals(sc.get("unreadableRaw"));
         });
        CollectionRuntimeFixture.teardown(s);
    }
}

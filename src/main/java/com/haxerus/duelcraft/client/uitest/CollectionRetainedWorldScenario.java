package com.haxerus.duelcraft.client.uitest;

import com.google.gson.JsonObject;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Leaves only this newly created disposable save seeded across normal rejoin and JVM restart. */
@LDLRegisterClient(name = "collection_retained_world", group = "duelcraft_lifecycle", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionRetainedWorldScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("lifecycle"); }
    @Override public void define(ScenarioBuilder s) {
        String stage = CollectionLifecycleLauncher.stage;
        if (stage == null) throw new IllegalStateException("Requires opt-in retained-world launcher");
        if (stage.equals("seed")) seed(s);
        else s.server("read disk-loaded authority before any client request", sc -> {
            var manifest = CollectionLifecycleLauncher.readManifest();
            var expected = PlayerCollectionData.CODEC.parse(JsonOps.INSTANCE, manifest.get("collection")).getOrThrow();
            sc.put("expected", expected);
            sc.check("complete loaded attachment equals server-confirmed manifest", CollectionRuntimeFixture.data(sc).equals(expected));
            sc.check("same authenticated UUID loaded", sc.player().getUUID().toString().equals(manifest.get("playerUuid").getAsString()));
            sc.check("seed PID matches rejoin and differs after restart", stage.equals("rejoin")
                    ? ProcessHandle.current().pid() == manifest.get("seedPid").getAsLong()
                    : ProcessHandle.current().pid() != manifest.get("seedPid").getAsLong());
            sc.check("phase precondition exact", manifest.get("phase").getAsString().equals(stage.equals("rejoin") ? "seeded" : "rejoined"));
        });
        CollectionRuntimeFixture.open(s);
        s.waitUntil("complete retained private snapshot exact", CollectionRuntimeFixture::snapshotMatches)
         .check("counts, summaries, revision and active ID exact", CollectionRuntimeFixture::snapshotMatches);
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> ctx.<PlayerCollectionData>get("expected").activeDeckId());
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#sample-label", "Retained M2 all sections")
         .checkTextContains("#list-active-status", "is active")
         .check("real ReadDeck loads exact UUID name Main Extra Side", ctx -> CollectionRuntimeFixture.draftMatches(ctx,
                 ctx.<PlayerCollectionData>get("expected").decks().values().iterator().next().cards()));
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.checkCount("#main-grid .card-tile", 40).checkCount("#extra-grid .card-tile", 1)
         .click("#toggle-side").checkCount("#side-grid .card-tile", 2)
         .click("#main-card-0").checkTextContains("#inspector-owned", "1000")
         .hoverAt(-100, -100).screenshot("retained-" + stage + "-exact-dark-editor")
         .checkServer("reads leave disk-loaded attachment unchanged", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
         .server("record completed phase and actual JVM PID", sc -> {
             var manifest = CollectionLifecycleLauncher.readManifest();
             manifest.addProperty("phase", stage.equals("seed") ? "seeded" : stage.equals("rejoin") ? "rejoined" : "verified");
             manifest.addProperty(stage + "Pid", ProcessHandle.current().pid());
             manifest.addProperty(stage + "VerifiedAt", System.currentTimeMillis());
             CollectionLifecycleLauncher.writeManifest(manifest);
         }).step("attach exact lifecycle manifest", ctx -> ctx.attach("server-confirmed-phase-manifest", CollectionLifecycleLauncher.readManifest().toString()))
         .teardown("close real collection screen", ctx -> ctx.mc().setScreen(null));
    }

    private static void seed(ScenarioBuilder s) {
        s.waitUntil("real catalog ready", ctx -> DuelcraftClient.getCollectionCatalog().toCompletableFuture().isDone())
         .step("build owned valid fixture from real catalog", ctx -> {
             var catalog = DuelcraftClient.getCollectionCatalog().toCompletableFuture().join();
             int extraTypes = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK;
             var main = catalog.stream().filter(card -> (card.type() & (TYPE_MONSTER | TYPE_SPELL | TYPE_TRAP)) != 0
                     && (card.type() & (extraTypes | TYPE_TOKEN)) == 0).limit(42).map(card -> card.code()).toList();
             int extra = catalog.stream().filter(card -> (card.type() & TYPE_MONSTER) != 0
                     && (card.type() & extraTypes) != 0 && (card.type() & TYPE_TOKEN) == 0).findFirst().orElseThrow().code();
             ctx.put("saved", new SavedDeck(UUID.randomUUID(), "Retained M2 all sections",
                     new DeckList(main.subList(0, 40), List.of(extra), main.subList(40, 42))));
         }).server("seed only artificial collection counts in fresh disposable world", sc -> {
             sc.check("fresh collection empty", CollectionRuntimeFixture.data(sc).equals(PlayerCollectionData.empty()));
             SavedDeck saved = sc.get("saved");
             var counts = new HashMap<Integer, Long>();
             saved.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
             counts.put(saved.cards().main().getFirst(), 1000L);
             sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(), null)));
         }).step("send actual authenticated Save packet", ctx -> ctx.put("saveReply", DuelcraftClient.getCollectionClient()
                 .request(new CollectionCommand.Save(10, ctx.get("saved"))).toCompletableFuture()))
         .waitUntil("actual Save acknowledgement", ctx -> ctx.<CompletableFuture<CollectionReply>>get("saveReply").isDone())
         .check("Save accepted at revision 11 with exact saved UUID/list", ctx -> ctx.<CompletableFuture<CollectionReply>>get("saveReply").join()
                 instanceof CollectionReply.Changed reply && reply.revision() == 11 && reply.saved().equals(ctx.get("saved")));
        CollectionRuntimeFixture.open(s);
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> ctx.<SavedDeck>get("saved").id());
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#sample-label", "Retained M2 all sections");
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.click("#activate-deck")
         .waitUntilServer("real editor Activate packet accepted", sc -> CollectionRuntimeFixture.data(sc).revision() == 12
                 && sc.<SavedDeck>get("saved").id().equals(CollectionRuntimeFixture.data(sc).activeDeckId()))
         .waitUntil("activation private acknowledgement revision 12", ctx -> {
             var view = DuelcraftClient.getCollectionClient().state().view();
             return view != null && view.revision() == 12 && ctx.<SavedDeck>get("saved").id().equals(view.activeId());
         }).serverGet("capture exact server-confirmed state", "expected", CollectionRuntimeFixture::data)
         .server("write authoritative retained fixture manifest", sc -> {
             var manifest = new JsonObject();
             manifest.addProperty("world", CollectionLifecycleLauncher.WORLD);
             manifest.addProperty("phase", "seeded");
             manifest.addProperty("seedPid", ProcessHandle.current().pid());
             manifest.addProperty("playerUuid", sc.player().getUUID().toString());
             manifest.add("collection", PlayerCollectionData.CODEC.encodeStart(JsonOps.INSTANCE, sc.<PlayerCollectionData>get("expected")).getOrThrow());
             CollectionLifecycleLauncher.writeManifest(manifest);
         }).closeScreen();
    }
}

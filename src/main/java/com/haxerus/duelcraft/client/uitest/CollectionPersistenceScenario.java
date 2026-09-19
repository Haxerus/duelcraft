package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Serialization plus actual kill/respawn, followed by private packet/read verification. */
@LDLRegisterClient(name = "collection_persistence", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionPersistenceScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection", "network"); }
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("real catalog ready", ctx -> DuelcraftClient.getCollectionCatalog().toCompletableFuture().isDone())
         .step("build known valid list from real metadata", ctx -> {
             var cards = DuelcraftClient.getCollectionCatalog().toCompletableFuture().join();
             int extraTypes = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK;
             var main = cards.stream().filter(card -> (card.type() & (TYPE_MONSTER | TYPE_SPELL | TYPE_TRAP)) != 0
                     && (card.type() & (extraTypes | TYPE_TOKEN)) == 0).limit(42).map(card -> card.code()).toList();
             var extra = cards.stream().filter(card -> (card.type() & TYPE_MONSTER) != 0
                     && (card.type() & extraTypes) != 0 && (card.type() & TYPE_TOKEN) == 0).findFirst().orElseThrow();
             ctx.require("catalog supplies valid main and side cards", main.size() == 42);
             ctx.put("ownedCards", new DeckList(main.subList(0, 40), List.of(extra.code()), main.subList(40, 42)));
         }).server("capture and seed disposable owned attachment", sc -> {
             CollectionRuntimeFixture.capture(sc);
             DeckList cards = sc.get("ownedCards");
             var counts = new HashMap<Integer, Long>();
             cards.requiredCopies().forEach((code, amount) -> counts.put(code, amount.longValue()));
             counts.put(cards.main().getFirst(), 1000L);
             var owned = new SavedDeck(CollectionRuntimeFixture.OWNED_ID, "Runtime owned all sections", cards);
             var other = new SavedDeck(CollectionRuntimeFixture.OTHER_ID, "Runtime second saved UUID",
                     new DeckList(List.of(99999999), List.of(99999998), List.of(99999997)));
             sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(
                     new PlayerCollectionData(10, counts, Map.of(owned.id(), owned, other.id(), other), null)));
         });
        CollectionRuntimeFixture.open(s);
        s.check("default snapshot ownership optional and no cleared activation", ctx -> {
            var view = DuelcraftClient.getCollectionClient().state().view();
            return !view.ownershipRequired() && view.clearedActivation() == null;
        });
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> CollectionRuntimeFixture.OWNED_ID);
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#editor-title", "Runtime owned all sections");
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.click("#activate-deck")
         .waitUntilServer("known owned activation accepted", sc -> CollectionRuntimeFixture.OWNED_ID.equals(CollectionRuntimeFixture.data(sc).activeDeckId()))
         .waitUntil("activation acknowledgement snapshot", ctx -> {
             var view = DuelcraftClient.getCollectionClient().state().view();
             return view != null && view.revision() == 11 && CollectionRuntimeFixture.OWNED_ID.equals(view.activeId());
         }).serverGet("capture complete pre-death state", "expected", CollectionRuntimeFixture::data)
         .step("record exact pre-death authoritative fixture", ctx -> ctx.attach("pre-death-state", ctx.<PlayerCollectionData>get("expected").toString()))
         .server("attachment serialization round trip", sc -> {
             var attachment = sc.player().getData(CollectionAttachments.COLLECTION);
             var tag = CollectionAttachment.SERIALIZER.write(attachment, sc.player().registryAccess());
             var decoded = CollectionAttachment.SERIALIZER.read(sc.player(), tag, sc.player().registryAccess());
             sc.check("serialized count 1000, UUIDs, names, sections, revision and active ID round trip", decoded.data().orElseThrow().equals(sc.<PlayerCollectionData>get("expected")));
         }).hoverAt(-100, -100).screenshot("collection-owned-active-before-death")
         .closeScreen().runCommand("kill @a")
         .waitUntil("actual client death", ctx -> ctx.mc().player != null && ctx.mc().player.isDeadOrDying())
         .step("send actual LocalPlayer respawn packet", ctx -> ctx.mc().player.respawn())
         .waitUntilServer("replacement ServerPlayer alive with same UUID", sc -> sc.player() != sc.<ServerPlayer>get("originalPlayer")
                 && sc.player().isAlive() && sc.player().getUUID().equals(sc.get("playerUuid")))
         .checkServer("actual clone preserves entire attachment including count 1000", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
         .waitUntil("respawned client alive", ctx -> ctx.mc().player != null && ctx.mc().player.isAlive())
         .closeScreen();
        CollectionRuntimeFixture.open(s);
        s.waitUntil("respawn private snapshot matches complete authority", CollectionRuntimeFixture::snapshotMatches)
         .check("respawn private counts, summaries, revisions and active selection match", CollectionRuntimeFixture::snapshotMatches);
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> CollectionRuntimeFixture.OWNED_ID);
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#editor-title", "Runtime owned all sections")
         .checkTextContains("#list-active-status", "is active")
         .check("ReadDeck after respawn loads complete owned Main Extra Side", ctx -> CollectionRuntimeFixture.draftMatches(ctx, ctx.<PlayerCollectionData>get("expected").decks().get(CollectionRuntimeFixture.OWNED_ID).cards()));
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.checkCount("#main-grid .card-tile", 40).checkCount("#extra-grid .card-tile", 1)
         .click("#toggle-side").checkCount("#side-grid .card-tile", 2)
         .click("#main-card-0").checkTextContains("#inspector-owned", "1000")
         .hoverAt(-100, -100).screenshot("collection-owned-active-after-respawn");
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> CollectionRuntimeFixture.OTHER_ID);
        s
         .waitForText("#editor-title", "Runtime second saved UUID")
         .check("second UUID ReadDeck preserves complete Main Extra Side", ctx -> CollectionRuntimeFixture.draftMatches(ctx, ctx.<PlayerCollectionData>get("expected").decks().get(CollectionRuntimeFixture.OTHER_ID).cards()));
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.checkTextContains("#main-count", "1").checkTextContains("#extra-count", "1").checkTextContains("#side-count", "1")
         .click("#main-card-0").checkTextContains("#inspector-name", "99999999")
         .checkServer("reads leave complete cloned authority unchanged", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")));
        CollectionRuntimeFixture.teardown(s);
    }
}

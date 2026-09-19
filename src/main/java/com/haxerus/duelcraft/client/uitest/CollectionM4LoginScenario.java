package com.haxerus.duelcraft.client.uitest;

import com.google.gson.JsonObject;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.DuelSettings;
import com.haxerus.duelcraft.server.DuelManager;
import com.haxerus.duelcraft.uitest.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@LDLRegisterClient(name = "collection_m4_login", group = "duelcraft_lifecycle", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionM4LoginScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        String phase = CollectionM4LifecycleLauncher.phase();
        if (phase == null) throw new IllegalStateException("Requires M4 retained launcher");
        s.server("verify login before any Open request", sc -> {
            boolean ownership = !phase.equals("default");
            sc.check("effective ownership", ServerConfig.requireCardOwnership() == ownership);
            sc.check("listener installed before login when requested", CollectionM4LoginHook.installed() == (phase.equals("restricted") || phase.equals("throwing")));
            var actual = CollectionRuntimeFixture.data(sc);
            if (phase.equals("default")) {
                sc.check("new retained world attachment empty", actual.equals(PlayerCollectionData.empty()));
                var deck = new SavedDeck(UUID.randomUUID(), "M4 all sections", CollectionPrivacyScenario.cards());
                var unknown = new SavedDeck(UUID.randomUUID(), "Retained unknown passcode", new DeckList(List.of(99999999), List.of(), List.of()));
                sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, Map.of(), Map.of(deck.id(), deck, unknown.id(), unknown), deck.id())));
            } else {
                var manifest = CollectionM4LifecycleLauncher.manifest();
                var before = PlayerCollectionData.CODEC.parse(JsonOps.INSTANCE, manifest.get("collection")).getOrThrow();
                sc.check("same authenticated UUID", sc.player().getUUID().toString().equals(manifest.get("playerUuid").getAsString()));
                sc.check("distinct actual JVM PID", ProcessHandle.current().pid() != manifest.get("pid").getAsLong());
                var expected = phase.equals("ownership") || phase.equals("restricted")
                        ? new PlayerCollectionData(before.revision() + 1, before.counts(), before.decks(), null) : before;
                sc.check("login exact revision counts lists active before Open", actual.equals(expected));
                if (phase.equals("removed")) sc.check("removed companion never reactivates", actual.activeDeckId() == null);
                if (phase.equals("throwing")) {
                    sc.check("throwing login retains recoverable attachment", actual.equals(before) && actual.activeDeckId() != null);
                    sc.check("throwing companion blocks actual solo use", !DuelManager.get().startSoloDuel(sc.player(), new DuelSettings(DuelRule.MR5, 42, PlayerOptions.standard()), null));
                    sc.check("throwing use preserves attachment and releases guard", CollectionRuntimeFixture.data(sc).equals(before) && !DuelManager.get().isBusy(sc.player()));
                }
                if (phase.equals("ownership")) {
                    // Seed owned activation only after the login clearance assertion, for the next JVM's hook checks.
                    var deck = actual.decks().values().stream().filter(d -> d.cards().main().size() == 40).findFirst().orElseThrow();
                    var counts = new HashMap<Integer, Long>(); deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
                    sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(actual.revision() + 1, counts, actual.decks(), deck.id())));
                }
            }
            sc.put("expected", CollectionRuntimeFixture.data(sc));
        }).step("first real Open after login assertions", ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(new CollectionCommand.Open()).toCompletableFuture()))
          .waitUntil("private Open reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone())
          .check("first Open does not supply login clearance", ctx -> {
              var reply = ctx.<CompletableFuture<CollectionReply>>get("reply").join();
              return phase.equals("throwing") ? reply instanceof CollectionReply.Rejected r && r.error() == CollectionError.DATA_UNAVAILABLE
                      : reply instanceof CollectionReply.Opened opened && opened.clearedActivation() == null;
          }).checkServer("Open makes no additional write", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
          .server("write exact retained manifest", sc -> {
              var manifest = new JsonObject(); manifest.addProperty("world", CollectionM4LifecycleLauncher.WORLD);
              manifest.addProperty("phase", phase); manifest.addProperty("pid", ProcessHandle.current().pid());
              manifest.addProperty("playerUuid", sc.player().getUUID().toString()); manifest.addProperty("ownershipRequired", ServerConfig.requireCardOwnership());
              manifest.addProperty("loginHook", CollectionM4LoginHook.installed());
              manifest.add("collection", PlayerCollectionData.CODEC.encodeStart(JsonOps.INSTANCE, CollectionRuntimeFixture.data(sc)).getOrThrow());
              CollectionM4LifecycleLauncher.manifest(manifest);
          }).step("record exact phase manifest", ctx -> ctx.attach("phase-manifest", CollectionM4LifecycleLauncher.manifest().toString()));
    }
}

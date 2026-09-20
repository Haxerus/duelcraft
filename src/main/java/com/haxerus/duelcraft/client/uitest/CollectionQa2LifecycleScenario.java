package com.haxerus.duelcraft.client.uitest;

import com.google.gson.JsonObject;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.collection.SavedDeckController;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.haxerus.duelcraft.uitest.CollectionTransferIsolationScenario;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.serialization.JsonOps;
import java.util.*;

/** Fresh QA.2 save only; verifies initial editor selection across real logout and JVM restart. */
@LDLRegisterClient(name = "collection_qa2_lifecycle", group = "duelcraft_lifecycle", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionQa2LifecycleScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        String phase = CollectionQa2LifecycleLauncher.stage;
        if (phase == null) throw new IllegalStateException("Requires isolated QA.2 launcher");
        s.timeoutMs(60000).server("verify exact login state before editor Open", sc -> {
            boolean required = phase.startsWith("required");
            sc.check("effective ownership policy", ServerConfig.requireCardOwnership() == required);
            var actual = CollectionRuntimeFixture.data(sc);
            if (phase.equals("optional-seed")) {
                sc.check("fresh world has empty collection", actual.equals(PlayerCollectionData.empty()));
                var saved = new SavedDeck(UUID.randomUUID(), "QA2 retained all sections", CollectionPrivacyScenario.cards());
                sc.check("save through production request boundary", CollectionTransferIsolationScenario.exchange(sc.player(),
                        new CollectionCommand.Save(actual.revision(), saved)) instanceof CollectionReply.Changed);
                sc.check("activate legal unowned list through production boundary", CollectionTransferIsolationScenario.exchange(sc.player(),
                        new CollectionCommand.Activate(CollectionRuntimeFixture.data(sc).revision(), saved.id())) instanceof CollectionReply.Changed);
            } else {
                var manifest = CollectionQa2LifecycleLauncher.manifest();
                var before = PlayerCollectionData.CODEC.parse(JsonOps.INSTANCE, manifest.get("collection")).getOrThrow();
                sc.check("same authenticated UUID", sc.player().getUUID().toString().equals(manifest.get("playerUuid").getAsString()));
                sc.check("rejoin keeps PID; restart changes PID", (ProcessHandle.current().pid() == manifest.get("pid").getAsLong()) == phase.equals("optional-rejoin"));
                var expected = phase.equals("required-missing")
                        ? new PlayerCollectionData(before.revision() + 1, before.counts(), before.decks(), null) : before;
                sc.check("login changes only missing-ownership activation once", actual.equals(expected));
                if (phase.equals("required-owned-seed")) {
                    var saved = actual.decks().values().iterator().next();
                    for (var entry : saved.cards().requiredCopies().entrySet()) {
                        sc.player().getInventory().setItem(0, CardItem.stack(entry.getKey(), entry.getValue()));
                        sc.check("deposit exact passcode " + entry.getKey(), CollectionTransferIsolationScenario.exchange(sc.player(),
                                new CollectionCommand.Deposit(CollectionRuntimeFixture.data(sc).revision(), entry.getKey(), entry.getValue())) instanceof CollectionReply.Changed);
                    }
                    sc.check("deposits never activate", CollectionRuntimeFixture.data(sc).activeDeckId() == null);
                    sc.check("explicit activation with deposited copies", CollectionTransferIsolationScenario.exchange(sc.player(),
                            new CollectionCommand.Activate(CollectionRuntimeFixture.data(sc).revision(), saved.id())) instanceof CollectionReply.Changed);
                }
            }
            sc.put("expected", CollectionRuntimeFixture.data(sc));
            sc.put("saved", CollectionRuntimeFixture.data(sc).decks().values().iterator().next());
        });
        CollectionRuntimeFixture.open(s);
        s.waitUntil("first editor selection loaded", ctx -> {
            Object controller = ctx.getField(ctx.screen(), "controller");
            SavedDeckController lists = ctx.getField(controller, "lists");
            return !lists.pending() && (phase.equals("required-missing") || lists.id().equals(ctx.<SavedDeck>get("saved").id()));
        }).check("initial editor exact active list or blank draft", ctx -> {
            Object controller = ctx.getField(ctx.screen(), "controller");
            SavedDeckController lists = ctx.getField(controller, "lists");
            var saved = ctx.<SavedDeck>get("saved");
            return phase.equals("required-missing")
                    ? CollectionRuntimeFixture.draftMatches(ctx, new DeckList(List.of(), List.of(), List.of())) && !lists.id().equals(saved.id())
                    : CollectionRuntimeFixture.draftMatches(ctx, saved.cards()) && lists.id().equals(saved.id()) && lists.name().equals(saved.name());
        }).check("private snapshot exact", CollectionRuntimeFixture::snapshotMatches)
          .checkServer("Open does not alter saved state", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
          .screenshot("qa2-" + phase + "-initial-editor")
          .server("record authoritative phase", sc -> {
              var manifest = new JsonObject();
              manifest.addProperty("world", CollectionQa2LifecycleLauncher.WORLD);
              manifest.addProperty("phase", phase); manifest.addProperty("pid", ProcessHandle.current().pid());
              manifest.addProperty("serverPid", ProcessHandle.current().pid());
              manifest.addProperty("serverKind", "integrated, same JVM as client");
              manifest.addProperty("playerUuid", sc.player().getUUID().toString());
              manifest.addProperty("ownershipRequired", ServerConfig.requireCardOwnership());
              manifest.addProperty("config", CollectionQa2LifecycleLauncher.directory().resolve("config/duelcraft-server.toml").toAbsolutePath().toString());
              manifest.add("collection", PlayerCollectionData.CODEC.encodeStart(JsonOps.INSTANCE, CollectionRuntimeFixture.data(sc)).getOrThrow());
              CollectionQa2LifecycleLauncher.manifest(manifest);
          }).teardown("close editor", ctx -> ctx.mc().setScreen(null));
    }
}

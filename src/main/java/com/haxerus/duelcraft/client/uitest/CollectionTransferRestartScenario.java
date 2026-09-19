package com.haxerus.duelcraft.client.uitest;

import com.google.gson.JsonObject;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.serialization.JsonOps;
import java.util.Map;

/** Real packets followed by normal save/rejoin and a distinct JVM loading the same disposable world. */
@LDLRegisterClient(name = "collection_transfer_restart", group = "duelcraft_lifecycle", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionTransferRestartScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        String stage = CollectionLifecycleLauncher.stage;
        if (stage == null || System.getProperty("duelcraft.uitest.collectionTransferLifecycle") == null)
            throw new IllegalStateException("Requires transfer lifecycle launcher");
        if (stage.equals("seed")) {
            s.server("seed physical cards only in new transfer world", sc -> {
                sc.check("fresh empty attachment", CollectionRuntimeFixture.data(sc).equals(PlayerCollectionData.empty()));
                sc.check("fresh empty inventory", sc.player().getInventory().isEmpty());
                sc.player().getInventory().setItem(0, CardItem.stack(CollectionTransferScenario.CODE, 20));
                CardTransferService.owner(sc.player()).inventoryChanged();
            });
            CollectionTransferScenario.request(s, "deposit twenty by packet", ctx -> new CollectionCommand.Deposit(0, CollectionTransferScenario.CODE, 20));
            CollectionTransferScenario.request(s, "withdraw five by packet", ctx -> new CollectionCommand.Withdraw(1, CollectionTransferScenario.CODE, 5));
        } else {
            s.server("verify exact disk-loaded collection and physical stacks", sc -> {
                var manifest = CollectionLifecycleLauncher.readManifest();
                var expected = PlayerCollectionData.CODEC.parse(JsonOps.INSTANCE, manifest.get("collection")).getOrThrow();
                sc.check("complete saved collection exact", CollectionRuntimeFixture.data(sc).equals(expected));
                sc.check("same authenticated player", sc.player().getUUID().toString().equals(manifest.get("playerUuid").getAsString()));
                sc.check("same PID for rejoin, different PID after restart", stage.equals("rejoin")
                        ? ProcessHandle.current().pid() == manifest.get("seedPid").getAsLong()
                        : ProcessHandle.current().pid() != manifest.get("seedPid").getAsLong());
            });
        }
        s.server("assert conserved twenty copies after transfer or load", sc -> {
            var data = CollectionRuntimeFixture.data(sc);
            sc.check("exact collection revision two and fifteen deposited", data.revision() == 2 && data.counts().equals(Map.of(CollectionTransferScenario.CODE, 15L)));
            sc.check("five physical copies retain canonical identity", CardItem.isCanonical(sc.player().getInventory().getItem(0))
                    && sc.player().getInventory().getItem(0).getCount() == 5
                    && CardItem.code(sc.player().getInventory().getItem(0)).orElseThrow() == CollectionTransferScenario.CODE);
            sc.check("no extra physical copies", CollectionTransferScenario.carried(sc) == 5);
            sc.check("no autoactivation or invented list", data.activeDeckId() == null && data.decks().isEmpty());
            var manifest = stage.equals("seed") ? new JsonObject() : CollectionLifecycleLauncher.readManifest();
            manifest.addProperty("world", CollectionLifecycleLauncher.TRANSFER_WORLD);
            manifest.addProperty("playerUuid", sc.player().getUUID().toString());
            manifest.addProperty("phase", stage.equals("seed") ? "seeded" : stage.equals("rejoin") ? "rejoined" : "verified");
            manifest.addProperty(stage + "Pid", ProcessHandle.current().pid());
            manifest.add("collection", PlayerCollectionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow());
            manifest.addProperty("physicalCode", CollectionTransferScenario.CODE);
            manifest.addProperty("physicalCount", 5);
            CollectionLifecycleLauncher.writeManifest(manifest);
        });
        CollectionRuntimeFixture.open(s);
        s.checkTextContains("#editor-title", "New list")
         .hoverAt(-100, -100).screenshot("transfer-" + stage)
         .teardown("close transfer editor", ctx -> ctx.mc().setScreen(null));
    }
}

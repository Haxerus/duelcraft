package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.uitest.CollectionDuelPolicyScenario;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.*;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import com.haxerus.duelcraft.client.uitest.CollectionDuelPolicyClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@LDLRegister(name = "collection_duel_policy_mp", group = "duelcraft_disconnect", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionDuelPolicyMPScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("collection", "preparation", "native"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("capture and seed current ownership fixtures", sc -> {
            sc.check("requested ownership setting", ServerConfig.requireCardOwnership() == System.getProperty("duelcraft.uitest.mpOwnership", "default").equals("required"));
            for (String role : List.of("A", "B")) {
                var p = sc.player(role); sc.put("player" + role, p); sc.put("original" + role, p.getData(CollectionAttachments.COLLECTION));
                var inventory = new ArrayList<ItemStack>();
                for (int i = 0; i < 41; i++) inventory.add(p.getInventory().getItem(i).copy());
                sc.put("inventory" + role, inventory); p.getInventory().clearContent();
                p.getInventory().setItem(0, CardItem.stack(89631139, 8)); CardTransferService.owner(p).inventoryChanged();
                seed(p);
            }
            sc.put("hook", new CollectionProgressionFixture());
        });
        // No hook, then a registered approving hook: both use real JNI sessions and current manager policy.
        for (boolean hook : List.of(false, true)) {
            s.server("native solo and multiplayer approving=" + hook, sc -> {
                CollectionProgressionFixture listener = sc.get("hook");
                if (hook) NeoForge.EVENT_BUS.register(listener);
                var manager = DuelManager.get(); var a = sc.player("A");
                sc.check("real solo starts", manager.startSoloDuel(a, settings(), null)); manager.forfeit(a);
                var flow = ready(sc);
                sc.check("real multiplayer starts", manager.preparation().first(a.getUUID(), flow, true, now()).code() == PreparationResult.OK);
                sc.check("both live in same native session", manager.getPlayerActiveDuel(a) != null && manager.getPlayerActiveDuel(a).equals(manager.getPlayerActiveDuel(sc.player("B"))));
                manager.forfeit(a);
                if (hook) NeoForge.EVENT_BUS.unregister(listener);
            });
        }
        for (String mode : List.of("deny", "throw")) {
            s.server("progression " + mode + " solo acceptance and final start", sc -> {
                var manager = DuelManager.get(); var a = sc.player("A"); var b = sc.player("B");
                CollectionProgressionFixture hook = sc.get("hook"); hook.owner = a.getUUID(); NeoForge.EVENT_BUS.register(hook);
                try {
                    seed(a); hook.mode = mode;
                    sc.check("hook blocks solo", !manager.startSoloDuel(a, settings(), null));
                    sc.check("solo releases guard", !manager.isBusy(a));
                    hook.mode = "allow"; seed(a);
                    var prep = manager.preparation();
                    sc.check("invite before progression change", prep.invite(a.getUUID(), b.getUUID(), DuelRule.MR5, 42L, PlayerOptions.standard(), now()).code() == PreparationResult.OK);
                    var flow = prep.view(a.getUUID(), now()).flowId(); hook.mode = mode;
                    sc.check("accept rechecks current progression", prep.accept(b.getUUID(), flow, now()).code() == PreparationResult.INELIGIBLE);
                    sc.check("ineligible invitation stays editable", !manager.isBusy(a) && !manager.isBusy(b));
                    prep.cancel(a.getUUID(), flow, now());
                    hook.mode = "allow"; seed(a); flow = ready(sc); hook.mode = mode;
                    sc.check("final start rechecks changed progression", prep.first(a.getUUID(), flow, true, now()).code() == PreparationResult.START_FAILED);
                    sc.check("failed final start completely releases both", !manager.isBusy(a) && !manager.isBusy(b) && prep.view(a.getUUID(), now()).flowId() == null);
                } finally { hook.mode = "allow"; NeoForge.EVENT_BUS.unregister(hook); seed(a); }
            });
        }
        s.server("deposited exact passcodes including Side remain authoritative", sc -> {
            var manager = DuelManager.get(); var a = sc.player("A"); var b = sc.player("B");
            CollectionProgressionFixture hook = sc.get("hook"); NeoForge.EVENT_BUS.register(hook);
            try {
                seed(a); var owned = data(a);
                // Physical cards are present while deposited counts are empty.
                int slot = 0;
                for (var entry : CollectionPrivacyScenario.cards().requiredCopies().entrySet()) {
                    if (slot < 36) a.getInventory().setItem(slot++, CardItem.stack(entry.getKey(), entry.getValue()));
                }
                a.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(11, Map.of(), owned.decks(), owned.activeDeckId())));
                boolean allowed = !ServerConfig.requireCardOwnership();
                sc.check("approving hook cannot replace deposited ownership for solo", manager.startSoloDuel(a, settings(), null) == allowed);
                if (allowed) manager.forfeit(a);
                a.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(11, Map.of(), owned.decks(), owned.activeDeckId())));
                var prep = manager.preparation();
                sc.check("empty deposited multiplayer invite follows core setting", prep.invite(a.getUUID(), b.getUUID(), DuelRule.MR5, 42L, PlayerOptions.standard(), now()).code()
                        == (allowed ? PreparationResult.OK : PreparationResult.INELIGIBLE));
                var view = prep.view(a.getUUID(), now()); if (view.flowId() != null) prep.cancel(a.getUUID(), view.flowId(), now());
                seed(a); var flow = ready(sc); var before = data(a);
                var shortage = new HashMap<>(before.counts());
                shortage.remove(CollectionPrivacyScenario.cards().side().getFirst());
                shortage.put(89631139, 100L); // Other passcodes never satisfy the missing Side copy.
                a.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(11, shortage, before.decks(), before.activeDeckId())));
                sc.check("final start checks exact Side passcode", prep.first(a.getUUID(), flow, true, now()).code()
                        == (allowed ? PreparationResult.OK : PreparationResult.START_FAILED));
                if (allowed) manager.forfeit(a);
                sc.check("shortage attempt leaves no busy state", !manager.isBusy(a) && !manager.isBusy(b));
            } finally {
                NeoForge.EVENT_BUS.unregister(hook); seed(a);
                a.getInventory().clearContent(); a.getInventory().setItem(0, CardItem.stack(89631139, 8)); CardTransferService.owner(a).inventoryChanged();
            }
        });
        s.allClients("settle prior terminal states", b -> b.ticks(10).closeScreen())
         .allClients("open and dirty actual editor", b -> CollectionDuelPolicyClient.openDirty(b))
         .server("accept actual preparation", sc -> {
             var prep = DuelManager.get().preparation(); var a = sc.player("A").getUUID(); var b = sc.player("B").getUUID();
             sc.check("invite", prep.invite(a, b, DuelRule.MR5, 42L, PlayerOptions.standard(), now()).code() == PreparationResult.OK);
             var flow = prep.view(a, now()).flowId(); sc.put("flow", flow);
             sc.check("accept", prep.accept(b, flow, now()).code() == PreparationResult.OK);
             CollectionPrivacyScenario.verifyWireNegotiation(sc);
             sc.put("before", data(sc.player("A")));
         }).client("A", "editor suspended in real RPS", b -> b.waitUntil("editor suspended", ctx -> !(ctx.screen() instanceof CollectionScreen)));
        mutations(s, true);
        s.server("cancel real preparation", sc -> DuelManager.get().preparation().cancel(sc.player("A").getUUID(), sc.get("flow"), now()))
         .client("A", "original dirty draft on real cancel", b -> b.waitUntil("editor restored", CollectionDuelPolicyScenario::retained).check("exact dirty draft retained", CollectionDuelPolicyScenario::retained));
        mutations(s, false);
        s.server("hold real first-turn choice", sc -> {
            sc.put("flow", ready(sc)); sc.put("before", data(sc.player("A")));
            sc.check("first-choice state", DuelManager.get().preparation().view(sc.player("A").getUUID(), now()).mode() == PreparationView.Mode.FIRST_CHOICE);
        });
        mutations(s, true);
        s.server("injected failure after real native setup", sc -> {
            var fault = new CollectionStartFailureFixture(); sc.put("fault", fault); fault.install();
            fault.afterSetup = () -> {
                var player = sc.player("A"); var current = data(player);
                var save = CollectionTransferIsolationScenario.exchange(player, new CollectionCommand.Save(current.revision(), current.decks().get(current.activeDeckId())));
                var deposit = CollectionTransferIsolationScenario.exchange(player, new CollectionCommand.Deposit(current.revision(), 89631139, 1));
                sc.check("STARTING Save and transfer boundary remains BUSY", save instanceof CollectionReply.Rejected x && x.error() == CollectionError.BUSY
                        && deposit instanceof CollectionReply.Rejected y && y.error() == CollectionError.BUSY);
                sc.check("STARTING mutation attempts leave exact attachment", current.equals(data(player)));
            };
            var before = data(sc.player("A")); UUID flow = sc.get("flow");
            sc.check("post-setup failure returned", DuelManager.get().preparation().first(sc.player("A").getUUID(), flow, true, now()).code() == PreparationResult.START_FAILED);
            sc.check("native setup and delegated close once", fault.setups == 1 && fault.closes == 1);
            sc.check("saved selection survives failure", before.equals(data(sc.player("A"))));
        }).client("A", "failed native startup restores dirty editor", b -> b.waitUntil("restored after failed setup", CollectionDuelPolicyScenario::retained).check("no fake victory", ctx -> !(ctx.screen() instanceof com.haxerus.duelcraft.client.DuelScreen)));
        mutations(s, false);
        s.server("normal native retry", sc -> {
            var flow = ready(sc);
            sc.check("retry starts normally", DuelManager.get().preparation().first(sc.player("A").getUUID(), flow, true, now()).code() == PreparationResult.OK);
            sc.put("before", data(sc.player("A")));
        }).allClients("live native retry", b -> b.waitUntil("native screen", ctx -> com.haxerus.duelcraft.client.LDLibDuelScreen.isDuelLive()).screenshot("native-retry"));
        mutations(s, true);
        s.server("restore departing player's fixture before actual disconnect", sc -> restore(sc, "B"))
         .client("B", "disconnect game connection with control hub alive", b -> CollectionDuelPolicyClient.disconnect(b))
         .serverWaitUntil("disconnect releases survivor live lock", sc -> !DuelManager.get().isBusy(sc.player("A")))
         .server("survivor persistence after disconnect", sc -> sc.check("exact survivor attachment", data(sc.player("A")).equals(sc.<PlayerCollectionData>get("before"))));
        mutations(s, false);
        s.teardownAllClients("close runtime fixture screens", b -> b.closeScreen())
         .teardownServer("restore attached fixtures even when disconnected", sc -> {
             CollectionProgressionFixture hook = sc.get("hook"); if (hook != null) NeoForge.EVENT_BUS.unregister(hook);
             for (String role : List.of("A", "B")) {
                 ServerPlayer p = sc.get("player" + role);
                 if (p != null) { DuelManager.get().forfeit(p); restore(sc, role); }
             }
         });
    }
    private static void mutations(MPScenarioBuilder s, boolean busy) {
        if (busy) s.server("capture exact inventory before BUSY requests", sc -> {
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < 41; i++) inventory.add(sc.player("A").getInventory().getItem(i).copy());
            sc.put("busyInventory", inventory);
        });
        s.fetchOn("A", "revision", "current authority revision", Long.class, sc -> data(sc.player("A")).revision())
         .client("A", "valid Save " + busy, b -> b.step("send real Save", ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(new CollectionCommand.Save(ctx.get("revision"), new SavedDeck(UUID.fromString("6c004000-0000-4000-8000-000000000001"), "M4 fixture", CollectionPrivacyScenario.cards()))).toCompletableFuture()))
                 .waitUntil("Save reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone()).check("Save result", ctx -> expected(ctx, busy)))
         .fetchOn("A", "revision", "current authority after Save", Long.class, sc -> data(sc.player("A")).revision())
         .client("A", "valid deposit " + busy, b -> b.step("send real deposit", ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(new CollectionCommand.Deposit(ctx.get("revision"), 89631139, 1)).toCompletableFuture()))
                 .waitUntil("deposit reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone()).check("deposit result", ctx -> expected(ctx, busy)));
        if (busy) s.server("exact unchanged state for rejected operations", sc -> {
            sc.check("BUSY revision counts lists active exact", data(sc.player("A")).equals(sc.<PlayerCollectionData>get("before")));
            List<ItemStack> inventory = sc.get("busyInventory");
            sc.check("BUSY all inventory slots exact", java.util.stream.IntStream.range(0, 41).allMatch(i -> ItemStack.matches(inventory.get(i), sc.player("A").getInventory().getItem(i))));
        });
    }
    private static boolean expected(TestContext ctx, boolean busy) {
        var reply = ctx.<CompletableFuture<CollectionReply>>get("reply").join();
        return busy ? reply instanceof CollectionReply.Rejected r && r.error() == CollectionError.BUSY : reply instanceof CollectionReply.Changed;
    }
    private static UUID ready(ServerContext sc) {
        var prep = DuelManager.get().preparation(); var a = sc.player("A").getUUID(); var b = sc.player("B").getUUID();
        sc.check("ready invite", prep.invite(a, b, DuelRule.MR5, 42L, PlayerOptions.standard(), now()).code() == PreparationResult.OK);
        var flow = prep.view(a, now()).flowId();
        sc.check("ready accept", prep.accept(b, flow, now()).code() == PreparationResult.OK);
        var round = prep.view(a, now()).roundId(); prep.hand(a, flow, round, FirstTurnLobby.Hand.ROCK, now()); prep.hand(b, flow, round, FirstTurnLobby.Hand.SCISSORS, now()); return flow;
    }
    private static void seed(ServerPlayer p) {
        var deck = new SavedDeck(UUID.fromString("6c004000-0000-4000-8000-000000000001"), "M4 fixture", CollectionPrivacyScenario.cards());
        var counts = new HashMap<Integer, Long>();
        if (ServerConfig.requireCardOwnership()) deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
        p.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(deck.id(), deck), deck.id())));
    }
    private static void restore(ServerContext sc, String role) {
        ServerPlayer p = sc.get("player" + role); CollectionAttachment original = sc.get("original" + role);
        if (p == null || original == null) return;
        p.setData(CollectionAttachments.COLLECTION, original);
        List<ItemStack> inventory = sc.get("inventory" + role);
        for (int i = 0; i < 41; i++) p.getInventory().setItem(i, inventory.get(i));
        CardTransferService.owner(p).inventoryChanged();
    }
    private static PlayerCollectionData data(ServerPlayer p) { return p.getData(CollectionAttachments.COLLECTION).data().orElseThrow(); }
    private static DuelSettings settings() { return new DuelSettings(DuelRule.MR5, 42, PlayerOptions.standard()); }
    private static long now() { return System.currentTimeMillis(); }
}

package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.uitest.CollectionPrivacyClient;
import com.haxerus.duelcraft.client.uitest.CollectionTransferClient;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.DuelManager;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

@LDLRegister(name = "collection_transfer_isolation", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionTransferIsolationScenario implements MPScenario {
    private static final int CODE = 89631139;
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("collection", "inventory"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("capture and seed physical cards for distinct owners", sc -> {
            sc.check("two distinct authenticated players", !sc.player("A").getUUID().equals(sc.player("B").getUUID()));
            sc.check("effective ownership setting", ServerConfig.requireCardOwnership()
                    == System.getProperty("duelcraft.uitest.mpOwnership", "default").equals("required"));
            for (String role : List.of("A", "B")) {
                var player = sc.player(role);
                sc.put("original" + role, player.getData(CollectionAttachments.COLLECTION));
                var inventory = new ArrayList<ItemStack>();
                for (int i = 0; i < 41; i++) inventory.add(player.getInventory().getItem(i).copy());
                sc.put("inventory" + role, inventory);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, CardItem.stack(CODE, 20));
                var deck = new SavedDeck(UUID.randomUUID(), "Transfer " + role, CollectionPrivacyScenario.cards());
                var counts = new HashMap<Integer, Long>();
                deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
                player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(deck.id(), deck), deck.id())));
                CardTransferService.owner(player).inventoryChanged();
            }
        }).allClients("observe private receive boundary", b -> b.step("install observer", CollectionPrivacyClient::install));
        for (String role : List.of("A", "B")) {
            String other = role.equals("A") ? "B" : "A";
            s.server("capture idle owner exact state", sc -> sc.put("idleBefore", data(sc, other)))
             .client(other, "mark idle owner", CollectionTransferClient::mark)
             .client(role, "deposit twenty", b -> CollectionTransferClient.exchange(b, new CollectionCommand.Deposit(10, CODE, 20), CollectionError.NONE, 0))
             .client(role, "withdraw five", b -> CollectionTransferClient.exchange(b, new CollectionCommand.Withdraw(11, CODE, 5), CollectionError.NONE, 5))
             .client(role, "reject original revision replay", b -> CollectionTransferClient.exchange(b, new CollectionCommand.Withdraw(11, CODE, 5), CollectionError.STALE, 5))
             .server("server confirms conservation and isolation", sc -> {
                 sc.check(role + " stores exactly fifteen", data(sc, role).counts().get(CODE) == 15L);
                 sc.check(role + " carries exactly five", sc.player(role).getInventory().getItem(0).getCount() == 5);
                 sc.check(other + " exact private state unchanged", data(sc, other).equals(sc.<PlayerCollectionData>get("idleBefore")));
                 sc.check("replay never increments revision", data(sc, role).revision() == 12);
             }).serverSettle(5)
             .client(other, "no foreign replies", CollectionTransferClient::unchanged)
             .client(role, "withdraw active-list required copy", b -> CollectionTransferClient.exchange(b,
                     new CollectionCommand.Withdraw(12, CollectionPrivacyScenario.cards().main().getFirst(), 1), CollectionError.NONE, 5))
             .server("configured shortage behavior keeps saved list", sc -> {
                 sc.check("saved list remains", data(sc, role).decks().size() == 1);
                 sc.check("only required ownership clears activation", (data(sc, role).activeDeckId() == null) == ServerConfig.requireCardOwnership());
             });
        }
        s.server("begin actual first-turn roll", sc -> {
            var cards = CollectionPrivacyScenario.cards();
            var deck = new Deck(cards.main(), cards.extra());
            DuelManager.get().beginFirstTurnRoll(sc.player("A"), sc.player("B"), 3, DuelRule.MR5, PlayerOptions.standard(), deck, deck, "A", "B");
            sc.put("busyBefore", data(sc, "A"));
            sc.check("both participants locked by real roll", DuelManager.get().isBusy(sc.player("A")) && DuelManager.get().isBusy(sc.player("B")));
        }).client("A", "busy transfer rejected", b -> CollectionTransferClient.exchange(b,
                new CollectionCommand.Withdraw(13, CODE, 1), CollectionError.BUSY, 5))
         .server("busy request preserves exact state", sc -> {
             sc.check("collection unchanged", data(sc, "A").equals(sc.<PlayerCollectionData>get("busyBefore")));
             sc.check("physical five unchanged", sc.player("A").getInventory().getItem(0).getCount() == 5);
             DuelManager.get().cancelFirstTurnRoll(sc.player("A").getUUID(), "M3 fixture complete");
         }).teardownAllClients("restore observers", b -> b.step("restore observer", CollectionPrivacyClient::restore).closeScreen())
         .teardownServer("restore inventories and attachments", sc -> {
             DuelManager.get().cancelFirstTurnRoll(sc.player("A").getUUID(), "M3 fixture teardown");
             for (String role : List.of("A", "B")) {
                 CollectionAttachment original = sc.get("original" + role);
                 if (original == null) continue;
                 sc.player(role).setData(CollectionAttachments.COLLECTION, original);
                 List<ItemStack> inventory = sc.get("inventory" + role);
                 for (int i = 0; i < 41; i++) sc.player(role).getInventory().setItem(i, inventory.get(i));
                 CardTransferService.owner(sc.player(role)).inventoryChanged();
             }
         });
    }
    private static PlayerCollectionData data(ServerContext sc, String role) {
        return sc.player(role).getData(CollectionAttachments.COLLECTION).data().orElseThrow();
    }
}

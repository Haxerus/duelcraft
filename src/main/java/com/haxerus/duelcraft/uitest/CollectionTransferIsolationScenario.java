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
            for (String role : List.of("A", "B")) {
                var player = sc.player(role);
                var before = data(sc, role);
                var restored = exchange(player, new CollectionCommand.Deposit(before.revision(), CollectionPrivacyScenario.cards().main().getFirst(), 1));
                sc.check("restore withdrawn copy through collection policy", restored instanceof CollectionReply.Changed);
                var current = data(sc, role);
                var activated = exchange(player, new CollectionCommand.Activate(current.revision(), current.decks().keySet().iterator().next()));
                sc.check("activate eligible fixture through shared policy", activated instanceof CollectionReply.Changed);
            }
            var preparation = DuelManager.get().preparation();
            long now = System.currentTimeMillis();
            var a = sc.player("A").getUUID(); var b = sc.player("B").getUUID();
            sc.check("invite accepted", preparation.invite(a, b, DuelRule.MR5, 3L, PlayerOptions.standard(), now).code() == com.haxerus.duelcraft.duel.PreparationResult.OK);
            sc.check("real preparation locks", preparation.accept(b, preparation.view(b, now).flowId(), now).code() == com.haxerus.duelcraft.duel.PreparationResult.OK);
            sc.put("busyBefore", data(sc, "A"));
            sc.check("both participants locked by real roll", DuelManager.get().isBusy(sc.player("A")) && DuelManager.get().isBusy(sc.player("B")));
        }).client("A", "busy transfer rejected", b -> CollectionTransferClient.exchange(b,
                new CollectionCommand.Withdraw(15, CODE, 1), CollectionError.BUSY, 5))
         .server("busy request preserves exact state", sc -> {
             sc.check("collection unchanged", data(sc, "A").equals(sc.<PlayerCollectionData>get("busyBefore")));
             sc.check("physical five unchanged", sc.player("A").getInventory().getItem(0).getCount() == 5);
             cancel(sc);
         }).teardownAllClients("restore observers", b -> b.step("restore observer", CollectionPrivacyClient::restore).closeScreen())
         .teardownServer("restore inventories and attachments", sc -> {
             cancel(sc);
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
    private static void cancel(ServerContext sc) {
        var preparation = DuelManager.get().preparation(); var id = sc.player("A").getUUID();
        var view = preparation.view(id, System.currentTimeMillis());
        if (view.flowId() != null) preparation.cancel(id, view.flowId(), System.currentTimeMillis());
    }
    private static CollectionReply exchange(net.minecraft.server.level.ServerPlayer player, CollectionCommand command) {
        var owner = CardTransferService.owner(player);
        var reply = new CollectionReply[1];
        DuelManager.get().collectionHandler().handle(new com.haxerus.duelcraft.server.collection.CollectionRequestPayload(UUID.randomUUID(), command),
                new com.haxerus.duelcraft.server.collection.CollectionPayloadHandler.Sender() {
                    public UUID id() { return player.getUUID(); }
                    public Optional<PlayerCollectionData> data() { return owner.data(); }
                    public boolean busy() { return DuelManager.get().isBusy(player); }
                    public void persist(PlayerCollectionData data) { owner.persist(data); }
                    public void reply(com.haxerus.duelcraft.server.collection.CollectionReplyPayload payload) { reply[0] = payload.reply(); }
                    public ItemStack slot(int index) { return owner.slot(index); }
                    public void slot(int index, ItemStack stack) { owner.slot(index, stack); }
                    public void inventoryChanged() { owner.inventoryChanged(); }
                }, System.currentTimeMillis());
        return reply[0];
    }
    private static PlayerCollectionData data(ServerContext sc, String role) {
        return sc.player(role).getData(CollectionAttachments.COLLECTION).data().orElseThrow();
    }
}

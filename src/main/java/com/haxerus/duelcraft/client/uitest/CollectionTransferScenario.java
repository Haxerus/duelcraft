package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.data.CardCatalog;
import com.haxerus.duelcraft.core.data.CardData;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.collection.*;
import com.haxerus.duelcraft.uitest.CollectionPolicyTestRestriction;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

/** Real inventory, editor controls and packet acknowledgements in a disposable world. */
@LDLRegisterClient(name = "collection_transfers", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionTransferScenario implements UIScenario {
    static final int CODE = 89631139;
    static final UUID ID = UUID.fromString("6c003000-0000-4000-8000-000000000001");

    @Override public void configure(ScenarioOptions options) { options.tags("collection", "inventory", "network"); }

    @Override public void define(ScenarioBuilder s) {
        s.server("capture state and seed twenty physical cards", sc -> {
            CollectionRuntimeFixture.capture(sc);
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < 41; i++) inventory.add(sc.player().getInventory().getItem(i).copy());
            sc.put("inventory", inventory);
            sc.player().getInventory().clearContent();
            long revision = CollectionRuntimeFixture.data(sc).revision() + 1000;
            sc.put("baseRevision", revision);
            sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(revision, Map.of(), Map.of(), null)));
            sc.player().getInventory().setItem(0, CardItem.stack(CODE, 20));
            CardTransferService.owner(sc.player()).inventoryChanged();
            sc.check("twenty physical and zero stored", carried(sc) == 20 && CollectionRuntimeFixture.data(sc).counts().isEmpty());
        }).waitUntil("real catalog ready", ctx -> DuelcraftClient.getCollectionCatalog().toCompletableFuture().isDone());
        request(s, "save sixteen-copy draft without owning cards", ctx ->
                new CollectionCommand.Save(ctx.<Long>get("baseRevision"), new SavedDeck(ID, "M3 transfer draft",
                        new DeckList(Collections.nCopies(16, CODE), List.of(), List.of()))));
        CollectionRuntimeFixture.open(s);
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> ID);
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#editor-title", "M3 transfer draft");
        CollectionRuntimeFixture.press(s, "#lists-close");
        s.click("#main-card-0").typeInto("#transfer-amount", "20");
        CollectionRuntimeFixture.press(s, "#deposit-card");
        s.waitUntilServer("deposit twenty authoritative", sc -> carried(sc) == 0 && stored(sc) == 20)
         .waitForText("#inspector-owned", "In collection: 20").waitForText("#inspector-carried", "Carried: 0")
         .checkServer("deposit never activates", sc -> CollectionRuntimeFixture.data(sc).activeDeckId() == null)
         .typeInto("#transfer-amount", "5");
        CollectionRuntimeFixture.press(s, "#withdraw-card");
        s.waitUntilServer("withdraw five authoritative", sc -> carried(sc) == 5 && stored(sc) == 15)
         .waitForText("#inspector-owned", "In collection: 15").waitForText("#inspector-carried", "Carried: 5")
         .checkTextContains("#inspector-missing", "1")
         .checkCount("#main-grid .card-tile", 16)
         .hoverAt(-100, -100).screenshot("physical-five-stored-fifteen")
         .server("fill main inventory without touching stored cards", sc -> {
             sc.player().getInventory().setItem(0, CardItem.stack(CODE, 64));
             for (int i = 1; i < 36; i++) sc.player().getInventory().setItem(i, new ItemStack(Items.STONE, 64));
             CardTransferService.owner(sc.player()).inventoryChanged();
             sc.put("fullBefore", CollectionRuntimeFixture.data(sc));
         }).step("scroll inspector before rejected transfer", ctx ->
                 ((com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView) ctx.el("#card-details-scroll").element()).verticalScroller.setNormalizedValue(0.5f))
         .ticks(2).step("capture inspector offset", ctx -> ctx.put("inspectorOffset",
                 ((com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView) ctx.el("#card-details-scroll").element()).viewContainer.getLayoutY()));
        CollectionRuntimeFixture.press(s, "#withdraw-card");
        s.waitUntil("Not enough inventory space feedback", ctx -> ctx.el("#editor-status").text().contains("Not enough inventory space"))
         .checkServer("capacity failure changes neither side", sc -> carried(sc) == 64
                 && CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("fullBefore")))
         .checkCount("#main-grid .card-tile", 16).checkTextContains("#inspector-owned", "15")
         .check("rejection preserves selected card and inspector scroll", ctx -> {
             Object controller = ctx.getField(ctx.screen(), "controller");
             int selected = ctx.getField(controller, "selectedCode");
             float offset = ((com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView) ctx.el("#card-details-scroll").element()).viewContainer.getLayoutY();
             return selected == CODE && Math.abs(offset - ctx.<Float>get("inspectorOffset")) < 1;
         })
         .server("free exactly one main slot", sc -> {
             sc.player().getInventory().setItem(1, ItemStack.EMPTY);
             CardTransferService.owner(sc.player()).inventoryChanged();
         });
        CollectionRuntimeFixture.press(s, "#withdraw-card");
        s.waitUntilServer("retry moves exact requested five", sc -> sc.player().getInventory().getItem(1).getCount() == 5 && stored(sc) == 10)
         .waitForText("#inspector-owned", "In collection: 10")
         .server("mixed offhand and unsupported stack fixture", sc -> {
             sc.player().getInventory().clearContent();
             sc.player().getInventory().setItem(0, CardItem.stack(CODE, 3));
             sc.player().getInventory().setItem(40, CardItem.stack(CODE, 2));
             var named = CardItem.stack(CODE, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
             sc.player().getInventory().setItem(1, named);
             sc.put("named", named);
             CardTransferService.owner(sc.player()).inventoryChanged();
         });
        CollectionRuntimeFixture.press(s, "#deposit-cards");
        s.waitUntilServer("main and offhand deposited with customized stack intact", sc -> stored(sc) == 15
                && sc.player().getInventory().getItem(0).isEmpty() && sc.player().getInventory().getItem(40).isEmpty())
         .waitUntil("skipped 4 feedback", ctx -> ctx.el("#editor-status").text().contains("skipped 4"))
         .checkServer("custom stack object and name preserved", sc -> sc.player().getInventory().getItem(1) == sc.<ItemStack>get("named"))
         .hoverAt(-100, -100).screenshot("bulk-deposit-skips-customized-cards");
        CollectionRuntimeFixture.press(s, "#deposit-cards");
        s.waitUntil("Not enough eligible cards feedback", ctx -> ctx.el("#editor-status").text().contains("Not enough eligible cards"))
         .checkServer("empty eligible bulk preserves fifteen stored", sc -> stored(sc) == 15)
         .server("seed an owned passcode absent from catalog and saved lists", sc -> {
             var before = CollectionRuntimeFixture.data(sc);
             var counts = new HashMap<>(before.counts()); counts.put(Integer.MAX_VALUE, 2L);
             sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(
                     before.revision() + 1, counts, before.decks(), before.activeDeckId())));
         });
        CollectionRuntimeFixture.open(s);
        s.typeInto("#collection-search", Integer.toString(Integer.MAX_VALUE)).waitUntil("unknown owned result selectable", ctx ->
                ctx.exists("#collection-card-2147483647"));
        CollectionRuntimeFixture.press(s, "#collection-card-2147483647");
        s.checkTextContains("#inspector-name", "2147483647").typeInto("#transfer-amount", "2");
        CollectionRuntimeFixture.press(s, "#withdraw-card");
        s.waitUntilServer("unknown owned cards recovered without saved list", sc ->
                !CollectionRuntimeFixture.data(sc).counts().containsKey(Integer.MAX_VALUE)
                && CardItem.code(sc.player().getInventory().getItem(0)).orElse(0) == Integer.MAX_VALUE
                && sc.player().getInventory().getItem(0).getCount() == 2)
         .server("both ownership settings and companion outcomes use real inventory", CollectionTransferScenario::policyMatrix);
        s.teardownServer("restore exact physical inventory", sc -> {
            List<ItemStack> original = sc.get("inventory");
            if (original != null) for (int i = 0; i < original.size(); i++) sc.player().getInventory().setItem(i, original.get(i));
            CardTransferService.owner(sc.player()).inventoryChanged();
        });
        CollectionRuntimeFixture.teardown(s);
    }

    static long stored(ServerContext sc) { return CollectionRuntimeFixture.data(sc).counts().getOrDefault(CODE, 0L); }
    static void request(ScenarioBuilder s, String name, java.util.function.Function<TestContext, CollectionCommand> command) {
        s.step(name, ctx -> ctx.put("transferReply", DuelcraftClient.getCollectionClient().request(command.apply(ctx)).toCompletableFuture()))
         .waitUntil(name + " acknowledged", ctx -> ctx.<java.util.concurrent.CompletableFuture<CollectionReply>>get("transferReply").isDone());
    }
    static int carried(ServerContext sc) {
        int total = 0;
        for (int i = 0; i <= 40; i++) {
            if (i >= 36 && i != 40) continue;
            var stack = sc.player().getInventory().getItem(i);
            if (CardItem.isCanonical(stack) && CardItem.code(stack).orElse(0) == CODE) total += stack.getCount();
        }
        return total;
    }

    private static void policyMatrix(ServerContext sc) {
        Map<Integer, CardCatalog.Facts> facts;
        try { facts = CardCatalog.load(CardData.load().join().database()); }
        catch (java.sql.SQLException exception) { throw new IllegalStateException(exception); }
        var deck = new SavedDeck(ID, "Legal transfer fixture", CollectionPrivacyScenario.cards());
        int code = deck.cards().main().getFirst();
        var counts = new HashMap<Integer, Long>();
        deck.cards().requiredCopies().forEach((key, value) -> counts.put(key, value.longValue()));
        for (boolean required : new boolean[]{false, true}) {
            for (String mode : List.of("none", "deny", "throw")) {
                sc.player().getInventory().clearContent();
                var before = new PlayerCollectionData(30, counts, Map.of(ID, deck), ID);
                sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(before));
                var listener = new CollectionPolicyTestRestriction(mode.equals("throw"));
                if (!mode.equals("none")) NeoForge.EVENT_BUS.register(listener);
                try {
                    var service = new CollectionService(facts, new DeckUsePolicy(required, DeckUseCheckEvent.restriction(NeoForge.EVENT_BUS)));
                    var reply = new CardTransferService(service, service.depositableCodes()).apply(sc.player(), 30,
                            InventoryTransferPlan.Kind.WITHDRAW, code, 1);
                    if (mode.equals("throw")) {
                        sc.check(required + " failed policy leaves attachment exact", reply instanceof CollectionReply.Rejected r
                                && r.error() == CollectionError.DATA_UNAVAILABLE && CollectionRuntimeFixture.data(sc).equals(before));
                        sc.check(required + " failed policy leaves inventory empty", sc.player().getInventory().isEmpty());
                    } else {
                        var after = CollectionRuntimeFixture.data(sc);
                        sc.check(required + " " + mode + " policy controls activation", Objects.equals(after.activeDeckId(), required || mode.equals("deny") ? null : ID));
                        sc.check(required + " " + mode + " keeps saved list", after.decks().equals(before.decks()));
                        sc.check(required + " " + mode + " moves exact real copy", sc.player().getInventory().getItem(0).getCount() == 1 && !after.counts().containsKey(code));
                        if (mode.equals("deny")) sc.check("actual companion reason", reply instanceof CollectionReply.Changed r
                                && CollectionPolicyTestRestriction.REASON.equals(r.eligibility().restrictionReason()));
                    }
                } finally { if (!mode.equals("none")) NeoForge.EVENT_BUS.unregister(listener); }
            }
        }
    }
}

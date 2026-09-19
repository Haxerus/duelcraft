package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.*;
import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.*;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@LDLRegisterClient(name = "collection_duel_policy", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionDuelPolicyScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection", "preparation", "native"); }
    @Override public void define(ScenarioBuilder s) {
        s.closeScreen().timeoutMs(60000).server("capture and seed empty default collection", sc -> {
            CollectionRuntimeFixture.capture(sc);
            sc.check("standalone default ownership optional", !ServerConfig.requireCardOwnership());
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < 41; i++) inventory.add(sc.player().getInventory().getItem(i).copy());
            sc.put("inventory", inventory);
            sc.player().getInventory().clearContent();
            sc.player().getInventory().setItem(0, CardItem.stack(89631139, 2));
            CardTransferService.owner(sc.player()).inventoryChanged();
            var saved = new SavedDeck(UUID.randomUUID(), "Native solo fixture", CollectionPrivacyScenario.cards());
            sc.put("saved", saved);
            sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, Map.of(), Map.of(saved.id(), saved), null)));
        });
        request(s, "activate real legal unowned list", ctx -> new CollectionCommand.Activate(10, ctx.<SavedDeck>get("saved").id()), CollectionError.NONE);
        CollectionRuntimeFixture.open(s);
        s.step("retain a dirty editor", CollectionDuelPolicyScenario::dirty)
         .step("registered solo command", ctx -> ctx.mc().player.connection.sendCommand("duel test"))
         .waitUntil("actual native solo screen", ctx -> ctx.screen() instanceof DuelScreen && LDLibDuelScreen.isDuelLive())
         .checkServer("empty counts start native solo", sc -> CollectionRuntimeFixture.data(sc).counts().isEmpty() && DuelManager.get().isBusy(sc.player()))
         .serverGet("capture exact live attachment", "before", CollectionRuntimeFixture::data);
        busy(s);
        s.screenshot("empty-default-native-solo").step("registered forfeit", ctx -> ctx.mc().player.connection.sendCommand("duel forfeit"))
         .waitUntil("result received", ctx -> !LDLibDuelScreen.isDuelLive())
         .waitUntil("terminal preparation", ctx -> DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
         .step("close real duel result", ctx -> ctx.screen().onClose())
         .waitUntil("original editor restored", CollectionDuelPolicyScenario::retained)
         .check("dirty draft restored after real end", CollectionDuelPolicyScenario::retained);
        released(s);
        s.server("install real setup failure", sc -> {
            var fault = new CollectionStartFailureFixture(); fault.install(); sc.put("fault", fault);
            sc.put("before", CollectionRuntimeFixture.data(sc));
        }).step("registered solo triggers native setup failure", ctx -> {
            ctx.put("revision", DuelcraftClient.preparation().view().revision());
            ctx.mc().player.connection.sendCommand("duel test");
        }).waitUntil("failure returns a newer idle state", ctx -> DuelcraftClient.preparation().view().revision() > ctx.<Long>get("revision")
                && DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
          .waitUntil("failed start restores editor", CollectionDuelPolicyScenario::retained)
          .check("failure has no live duel or fake result screen", ctx -> !LDLibDuelScreen.isDuelLive() && !(ctx.screen() instanceof DuelScreen))
          .server("actual native close and persistent selection", sc -> {
              CollectionStartFailureFixture fault = sc.get("fault");
              sc.check("real setup and JNI close exactly once", fault.setups == 1 && fault.closes == 1);
              sc.check("failure releases busy", !DuelManager.get().isBusy(sc.player()));
              sc.check("exact attachment survives startup failure", CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("before")));
          }).screenshot("native-failure-restores-dirty-draft");
        released(s);
        s.step("normal retry after native cleanup", ctx -> ctx.mc().player.connection.sendCommand("duel test"))
         .waitUntil("normal retry reaches real native duel", ctx -> LDLibDuelScreen.isDuelLive())
         .step("end retry", ctx -> ctx.mc().player.connection.sendCommand("duel forfeit"))
         .waitUntil("retry ended", ctx -> !LDLibDuelScreen.isDuelLive())
         .waitUntil("retry terminal state", ctx -> DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
         .step("close retry result", ctx -> ctx.screen().onClose())
         .check("retry restores exact dirty editor", CollectionDuelPolicyScenario::retained)
         .teardownServer("restore inventory and release native session", sc -> {
             DuelManager.get().forfeit(sc.player());
             List<ItemStack> inventory = sc.get("inventory");
             if (inventory != null) for (int i = 0; i < 41; i++) sc.player().getInventory().setItem(i, inventory.get(i));
             CardTransferService.owner(sc.player()).inventoryChanged();
         });
        CollectionRuntimeFixture.teardown(s);
    }
    private static void busy(ScenarioBuilder s) {
        request(s, "live Save", ctx -> new CollectionCommand.Save(ctx.<PlayerCollectionData>get("before").revision(), ctx.get("saved")), CollectionError.BUSY);
        request(s, "live valid deposit", ctx -> new CollectionCommand.Deposit(ctx.<PlayerCollectionData>get("before").revision(), 89631139, 1), CollectionError.BUSY);
        s.checkServer("BUSY preserves exact attachment and inventory", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("before"))
                && sc.player().getInventory().getItem(0).getCount() == 2);
    }
    private static void released(ScenarioBuilder s) {
        s.serverGet("current idle attachment", "before", CollectionRuntimeFixture::data);
        request(s, "same Save after release", ctx -> new CollectionCommand.Save(ctx.<PlayerCollectionData>get("before").revision(), ctx.get("saved")), CollectionError.NONE);
        s.serverGet("revision after Save", "before", CollectionRuntimeFixture::data);
        request(s, "same deposit after release", ctx -> new CollectionCommand.Deposit(ctx.<PlayerCollectionData>get("before").revision(), 89631139, 1), CollectionError.NONE);
    }
    private static void request(ScenarioBuilder s, String name, Function<TestContext, CollectionCommand> command, CollectionError error) {
        s.step(name, ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(command.apply(ctx)).toCompletableFuture()))
         .waitUntil(name + " reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone())
         .check(name + " expected result", ctx -> {
             var reply = ctx.<CompletableFuture<CollectionReply>>get("reply").join();
             return error == CollectionError.NONE ? reply instanceof CollectionReply.Changed : reply instanceof CollectionReply.Rejected r && r.error() == error;
         });
    }
    public static void dirty(TestContext ctx) {
        Object controller = ctx.getField(ctx.screen(), "controller");
        DeckEditorModel model = ctx.getField(controller, "model");
        SavedDeckController lists = ctx.getField(controller, "lists");
        model.add(DeckEditorModel.Section.MAIN, 89631139); lists.rename("M4 retained native draft");
        ctx.put("editor", ctx.screen()); ctx.put("model", model); ctx.put("lists", lists); ctx.put("draft", model.draft()); ctx.put("draftId", lists.id());
    }
    public static boolean retained(TestContext ctx) {
        SavedDeckController lists = ctx.get("lists"); DeckEditorModel model = ctx.get("model");
        return ctx.screen() == ctx.get("editor") && !ctx.<Boolean>getField(lists, "disposed") && model.dirty() && lists.dirty()
                && model.draft().equals(ctx.get("draft")) && lists.id().equals(ctx.get("draftId")) && lists.name().equals("M4 retained native draft");
    }
}

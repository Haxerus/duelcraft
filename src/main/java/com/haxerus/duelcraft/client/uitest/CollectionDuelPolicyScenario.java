package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import com.haxerus.duelcraft.client.*;
import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.*;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.haxerus.duelcraft.uitest.CollectionTransferIsolationScenario;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@LDLRegisterClient(name = "collection_duel_policy", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionDuelPolicyScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("collection", "preparation", "native"); }
    @Override public void define(ScenarioBuilder s) {
        s.closeScreen().timeoutMs(60000).server("capture and seed effective ownership fixture", sc -> {
            CollectionRuntimeFixture.capture(sc);
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < 41; i++) inventory.add(sc.player().getInventory().getItem(i).copy());
            sc.put("inventory", inventory);
            sc.player().getInventory().clearContent();
            sc.player().getInventory().setItem(0, CardItem.stack(89631139, 2));
            CardTransferService.owner(sc.player()).inventoryChanged();
            var saved = new SavedDeck(UUID.randomUUID(), "Native solo fixture", CollectionPrivacyScenario.cards());
            sc.put("saved", saved);
            var counts = new HashMap<Integer, Long>();
            if (ServerConfig.requireCardOwnership()) saved.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
            sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(saved.id(), saved), null)));
        });
        s.serverGet("read effective server ownership", "effectiveOwnership", sc -> ServerConfig.requireCardOwnership())
         .step("record effective server ownership", ctx -> ctx.attach("requireCardOwnership", String.valueOf(ctx.<Boolean>get("effectiveOwnership"))));
        request(s, "activate real legal list under current policy", ctx -> new CollectionCommand.Activate(10, ctx.<SavedDeck>get("saved").id()), CollectionError.NONE);
        CollectionRuntimeFixture.open(s);
        s.step("retain a dirty editor", CollectionDuelPolicyScenario::dirty)
         .step("registered solo command", ctx -> ctx.mc().player.connection.sendCommand("duel test"))
         .waitUntil("actual native solo screen", ctx -> ctx.screen() instanceof DuelScreen && LDLibDuelScreen.isDuelLive())
         .checkServer("native solo follows effective ownership", sc -> DuelManager.get().isBusy(sc.player())
                 && (ServerConfig.requireCardOwnership() ? CollectionPrivacyScenario.cards().requiredCopies().entrySet().stream()
                 .allMatch(entry -> CollectionRuntimeFixture.data(sc).counts().getOrDefault(entry.getKey(), 0L) >= entry.getValue())
                 : CollectionRuntimeFixture.data(sc).counts().isEmpty()))
         .serverGet("capture exact live attachment", "before", CollectionRuntimeFixture::data);
        busy(s);
        s.screenshot("effective-policy-native-solo").step("registered forfeit", ctx -> ctx.mc().player.connection.sendCommand("duel forfeit"))
         .waitUntil("result received", ctx -> !LDLibDuelScreen.isDuelLive())
         .waitUntil("terminal preparation", ctx -> DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
         .step("close real duel result", ctx -> ctx.screen().onClose())
         .waitUntil("original editor restored", CollectionDuelPolicyScenario::retained)
         .check("dirty draft restored after real end", CollectionDuelPolicyScenario::retained);
        released(s);
        s.server("install real setup failure", sc -> {
            var fault = new CollectionStartFailureFixture(); fault.install(); sc.put("fault", fault);
            sc.put("before", CollectionRuntimeFixture.data(sc));
            fault.afterSetup = () -> {
                var before = CollectionRuntimeFixture.data(sc);
                var inventory = new ArrayList<ItemStack>();
                for (int i = 0; i < 41; i++) inventory.add(sc.player().getInventory().getItem(i).copy());
                for (long revision : List.of(before.revision(), before.revision() - 1)) {
                    for (var command : mutations(revision, sc.get("saved"))) {
                        var reply = CollectionTransferIsolationScenario.exchange(sc.player(), command);
                        sc.check("STARTING rejects " + command.getClass().getSimpleName() + " revision=" + revision,
                                reply instanceof CollectionReply.Rejected r && r.error() == CollectionError.BUSY);
                    }
                }
                sc.check("STARTING rejected mutations preserve exact attachment", before.equals(CollectionRuntimeFixture.data(sc)));
                sc.check("STARTING rejected mutations preserve every inventory slot", java.util.stream.IntStream.range(0, 41)
                        .allMatch(i -> ItemStack.matches(inventory.get(i), sc.player().getInventory().getItem(i))));
            };
        }).step("registered solo triggers native setup failure", ctx -> {
            ctx.put("revision", DuelcraftClient.preparation().view().revision());
            ctx.mc().player.connection.sendCommand("duel test");
        }).waitUntil("failure returns a newer idle state", ctx -> DuelcraftClient.preparation().view().revision() > ctx.<Long>get("revision")
                && DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
          .step("explicitly resume failed-start draft", ctx -> ctx.require("draft available", com.haxerus.duelcraft.client.interaction.PreparationRouting.restoreEditor()))
          .waitUntil("failed start retains editor", CollectionDuelPolicyScenario::retained)
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
         .check("retry restores exact dirty editor", CollectionDuelPolicyScenario::retained);
        finalRestriction(s, false);
        finalRestriction(s, true);
        s.teardownServer("restore inventory and release native session", sc -> {
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
        request(s, "live Clear", ctx -> new CollectionCommand.ClearActive(ctx.<PlayerCollectionData>get("before").revision()), CollectionError.BUSY);
        request(s, "live Activate", ctx -> new CollectionCommand.Activate(ctx.<PlayerCollectionData>get("before").revision(), ctx.<SavedDeck>get("saved").id()), CollectionError.BUSY);
        request(s, "live Delete", ctx -> new CollectionCommand.Delete(ctx.<PlayerCollectionData>get("before").revision(), ctx.<SavedDeck>get("saved").id()), CollectionError.BUSY);
        request(s, "live Withdraw", ctx -> new CollectionCommand.Withdraw(ctx.<PlayerCollectionData>get("before").revision(), 89631139, 1), CollectionError.BUSY);
        request(s, "live stale Save", ctx -> new CollectionCommand.Save(ctx.<PlayerCollectionData>get("before").revision() - 1, ctx.get("saved")), CollectionError.BUSY);
        s.checkServer("BUSY preserves exact attachment and inventory", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("before"))
                && java.util.stream.IntStream.range(0, 41).allMatch(i -> ItemStack.matches(
                i == 0 ? CardItem.stack(89631139, 2) : ItemStack.EMPTY, sc.player().getInventory().getItem(i))));
    }
    private static void released(ScenarioBuilder s) {
        s.serverGet("current idle attachment", "before", CollectionRuntimeFixture::data);
        request(s, "same Save after release", ctx -> new CollectionCommand.Save(ctx.<PlayerCollectionData>get("before").revision(), ctx.get("saved")), CollectionError.NONE);
        request(s, "replayed Save after release", ctx -> new CollectionCommand.Save(ctx.<PlayerCollectionData>get("before").revision(), ctx.get("saved")), CollectionError.STALE);
        s.checkServer("Save replay makes no second change", sc -> {
            var before = sc.<PlayerCollectionData>get("before"); var after = CollectionRuntimeFixture.data(sc);
            return after.revision() == before.revision() + 1 && after.counts().equals(before.counts())
                    && after.decks().equals(before.decks()) && Objects.equals(after.activeDeckId(), before.activeDeckId());
        });
        s.serverGet("revision after Save", "before", CollectionRuntimeFixture::data);
        request(s, "same deposit after release", ctx -> new CollectionCommand.Deposit(ctx.<PlayerCollectionData>get("before").revision(), 89631139, 1), CollectionError.NONE);
    }
    private static List<CollectionCommand> mutations(long revision, SavedDeck saved) {
        return List.of(new CollectionCommand.Save(revision, saved), new CollectionCommand.ClearActive(revision),
                new CollectionCommand.Activate(revision, saved.id()), new CollectionCommand.Delete(revision, saved.id()),
                new CollectionCommand.Deposit(revision, 89631139, 1), new CollectionCommand.Withdraw(revision, 89631139, 1));
    }
    private static void finalRestriction(ScenarioBuilder s, boolean throwing) {
        String name = throwing ? "throw" : "deny";
        s.server("install final solo recheck " + name, sc -> {
            sc.put("before", CollectionRuntimeFixture.data(sc));
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < 41; i++) inventory.add(sc.player().getInventory().getItem(i).copy());
            sc.put("policyInventory", inventory);
            var hook = new FinalRestriction(sc.player().getUUID(), throwing);
            sc.put("finalHook", hook); NeoForge.EVENT_BUS.register(hook);
        }).step("registered solo with final recheck " + name, ctx -> {
            ctx.put("revision", DuelcraftClient.preparation().view().revision());
            ctx.mc().player.connection.sendCommand("duel test");
        }).waitUntil("final " + name + " returns newer idle state", ctx -> DuelcraftClient.preparation().view().revision() > ctx.<Long>get("revision")
                && DuelcraftClient.preparation().view().mode() == PreparationView.Mode.IDLE)
          .check("final " + name + " cannot open native duel", ctx -> !LDLibDuelScreen.isDuelLive() && !(ctx.screen() instanceof DuelScreen))
          .waitUntil("owner receives final " + name + " explanation", ctx -> DuelcraftClient.preparation().detail().equals(throwing
                  ? "Deck permission check failed; try again or contact the server administrator."
                  : "Active deck cleared: M6 final solo policy requirement"))
          .server("final " + name + " releases guard and preserves content", sc -> {
              FinalRestriction hook = sc.get("finalHook"); NeoForge.EVENT_BUS.unregister(hook);
              var before = sc.<PlayerCollectionData>get("before"); var after = CollectionRuntimeFixture.data(sc);
              sc.check("failure reached second policy check", hook.checks == 2);
              sc.check("final restriction releases busy and session", !DuelManager.get().isBusy(sc.player()) && DuelManager.get().getPlayerActiveDuel(sc.player()) == null);
              sc.check("counts and saved lists unchanged", before.counts().equals(after.counts()) && before.decks().equals(after.decks()));
              List<ItemStack> inventory = sc.get("policyInventory");
              sc.check("final restriction preserves every inventory slot", java.util.stream.IntStream.range(0, 41)
                      .allMatch(i -> ItemStack.matches(inventory.get(i), sc.player().getInventory().getItem(i))));
              sc.check("only ordinary denial clears selection", throwing ? before.equals(after)
                      : after.activeDeckId() == null && after.revision() == before.revision() + 1);
              sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(before));
          }).step("resume after final " + name, ctx -> ctx.require("dirty editor resumes", retained(ctx)
                  || com.haxerus.duelcraft.client.interaction.PreparationRouting.restoreEditor()))
          .check("final " + name + " retains exact draft", CollectionDuelPolicyScenario::retained)
          .teardownServer("remove final " + name + " fixture hook", sc -> {
              FinalRestriction hook = sc.get("finalHook"); if (hook != null) NeoForge.EVENT_BUS.unregister(hook);
          });
    }
    public static final class FinalRestriction {
        private final UUID owner;
        private final boolean throwing;
        int checks;
        FinalRestriction(UUID owner, boolean throwing) { this.owner = owner; this.throwing = throwing; }
        @SubscribeEvent public void check(DeckUseCheckEvent event) {
            if (!owner.equals(event.context().owner()) || ++checks < 2) return;
            if (throwing) throw new IllegalStateException("M6 final solo policy fixture failure");
            event.deny("M6 final solo policy requirement");
        }
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

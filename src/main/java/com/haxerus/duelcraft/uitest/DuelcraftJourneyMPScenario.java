package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.*;
import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.client.interaction.*;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.DuelManager;
import com.haxerus.duelcraft.server.collection.CardTransferService;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Dedicated players operate the actual M5 screens; server steps supply disposable fixtures only. */
@LDLRegister(name = "duelcraft_journey", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelcraftJourneyMPScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("start with empty independent collections", sc -> {
            sc.check("requested ownership mode", ServerConfig.requireCardOwnership() == required());
            for (var role : List.of("A", "B")) {
                var p = sc.player(role); sc.put("original" + role, p.getData(CollectionAttachments.COLLECTION));
                p.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(PlayerCollectionData.empty()));
            }
        }).allClients("Home to saved legal draft without items", b -> {
            b.closeScreen().step("clear cached snapshot", ctx -> DuelcraftClient.getCollectionClient().connect())
             .step("queue mapped J key", ctx -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J)))
             .waitUntil("Home opened", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI().step("press #home-collection", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#home-collection"))
             .waitUntil("editor opened", ctx -> ctx.screen() instanceof CollectionScreen).awaitModularUI()
             .waitUntil("catalog ready", ctx -> ctx.el("#save-deck").isActive())
             .step("fill deterministic legal draft", ctx -> {
                 Object controller = ctx.getField(ctx.screen(), "controller");
                 DeckEditorModel model = ctx.getField(controller, "model");
                 SavedDeckController lists = ctx.getField(controller, "lists");
                 var cards = CollectionPrivacyScenario.cards();
                 cards.main().forEach(code -> model.add(DeckEditorModel.Section.MAIN, code));
                 cards.extra().forEach(code -> model.add(DeckEditorModel.Section.EXTRA, code));
                 cards.side().forEach(code -> model.add(DeckEditorModel.Section.SIDE, code));
                 lists.rename("M5 " + ctx.mc().player.getName().getString()); ctx.put("lists", lists);
             }).step("press #save-deck", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#save-deck")).waitUntil("save acknowledged", ctx -> !ctx.<SavedDeckController>get("lists").dirty())
             .step("press #activate-deck", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#activate-deck"));
            if (required()) b.waitUntil("required copies shown", ctx -> ctx.el("#eligibility-dialog").isVisible()).step("press #eligibility-close", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#eligibility-close"));
            else b.waitUntil("unowned activation allowed", ctx -> ctx.<SavedDeckController>get("lists").activeId() != null);
        }).server("verify saves and supply required physical copies", sc -> {
            for (var role : List.of("A", "B")) {
                var p = sc.player(role); var data = p.getData(CollectionAttachments.COLLECTION).data().orElseThrow();
                sc.check("saved with zero deposits", data.counts().isEmpty() && data.decks().size() == 1);
                sc.check("activation follows ownership", (data.activeDeckId() == null) == required());
                if (!required()) continue;
                var original = p.getInventory().getItem(0).copy();
                for (var entry : CollectionPrivacyScenario.cards().requiredCopies().entrySet()) {
                    p.getInventory().setItem(0, CardItem.stack(entry.getKey(), entry.getValue()));
                    CardTransferService.owner(p).inventoryChanged();
                    var current = p.getData(CollectionAttachments.COLLECTION).data().orElseThrow();
                    sc.check("physical fixture deposited", CollectionTransferIsolationScenario.exchange(p,
                            new CollectionCommand.Deposit(current.revision(), entry.getKey(), entry.getValue())) instanceof CollectionReply.Changed);
                }
                p.getInventory().setItem(0, original); CardTransferService.owner(p).inventoryChanged();
            }
        }).allClients("activate and return Home", b -> {
            if (required()) b.step("refresh after physical deposits", ctx -> ctx.<SavedDeckController>get("lists").refresh())
                    .waitUntil("deposited counts received", ctx -> DuelcraftClient.getCollectionClient().state().view().counts().size() == CollectionPrivacyScenario.cards().requiredCopies().size()).awaitModularUI().step("press #activate-deck", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#activate-deck"))
                    .waitUntil("deposited deck activates", ctx -> ctx.el("#list-active-status").text().contains("Active"));
            b.step("press #collection-back", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#collection-back")).waitUntil("Home returns", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI();
        }).allClients("close Home for shared mat", b -> b.closeScreen())
          .server("same mat serves both owners", sc -> {
              var pos = sc.player("A").blockPosition().offset(1, 0, 0);
              sc.put("matPos", pos); sc.put("matBefore", sc.player("A").level().getBlockState(pos));
              sc.player("A").level().setBlockAndUpdate(pos, Duelcraft.DUEL_MAT.get().defaultBlockState());
              useMat(sc, "A"); useMat(sc, "B");
          }).allClients("mat shows own active deck", b -> b.waitUntil("mat lobby", ctx -> ctx.screen() instanceof DuelLobbyScreen)
                  .awaitModularUI().waitUntil("own mat summary", ctx -> ctx.el("#lobby-deck").text().contains("M5 " + ctx.mc().player.getName().getString()))
                  .step("Home from mat", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-home"))
                  .waitUntil("Home from shared mat", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI())
          .client("A", "invite through Home lobby", b -> b.step("press #home-duels", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#home-duels"))
                .waitUntil("lobby opened", ctx -> ctx.screen() instanceof DuelLobbyScreen).awaitModularUI()
                .step("select other player row", ctx -> {
                    var other = ctx.mc().getConnection().getOnlinePlayers().stream().filter(p -> p.getProfile().getName().equals("LDTestB")).findFirst().orElseThrow();
                    ctx.el("#lobby-player-" + other.getProfile().getId()).as(com.lowdragmc.lowdraglib2.gui.ui.elements.Button.class).setId("journey-target");
                }).step("press #journey-target", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#journey-target")).step("press #lobby-invite", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-invite")))
          .client("B", "open incoming invitation and accept", b -> b.waitUntil("incoming notification", ctx -> ctx.el("#home-invitation-row").isVisible())
                  .step("press #home-invitation", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#home-invitation")).waitUntil("incoming lobby", ctx -> ctx.screen() instanceof DuelLobbyScreen).awaitModularUI()
                  .waitUntil("Accept ready", ctx -> ctx.el("#lobby-accept").isActive()).step("press #lobby-accept", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-accept")))
          .allClients("accepted flow forces lobby", b -> b.waitUntil("RPS visible", ctx -> ctx.screen() instanceof DuelLobbyScreen
                  && ctx.el("#lobby-hands").isVisible()).screenshot("m5-dedicated-rps"))
          .client("A", "Rock", b -> b.step("press #lobby-rock", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-rock")))
          .client("B", "Scissors", b -> b.step("press #lobby-scissors", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-scissors")))
          .client("A", "winner chooses first", b -> b.waitUntil("first choice", ctx -> ctx.el("#lobby-first-choice").isVisible()).step("press #lobby-first", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#lobby-first")))
          .allClients("native duel opens", b -> b.waitUntil("live duel", ctx -> ctx.screen() instanceof DuelScreen && LDLibDuelScreen.isDuelLive()).awaitModularUI().screenshot("m5-dedicated-live"))
          .client("A", "concede through duel UI", b -> b.step("press #concede-btn", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#concede-btn")).ticks(2).step("confirm concede", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#pause-concede")))
          .allClients("result and Home", b -> b.waitUntil("duel ended", ctx -> !LDLibDuelScreen.isDuelLive()).step("press #result-close", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#result-close"))
                  .step("queue mapped J key", ctx -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J))).waitUntil("Home after result", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI().closeScreen())
          .server("both players use a binder", sc -> {
              for (var role : List.of("A", "B")) {
                  var p = sc.player(role); var hand = p.getMainHandItem();
                  p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Duelcraft.CARD_BINDER.get()));
                  Duelcraft.CARD_BINDER.get().use(p.level(), p, InteractionHand.MAIN_HAND);
                  p.setItemInHand(InteractionHand.MAIN_HAND, hand);
              }
          }).allClients("binder retains own saved deck", b -> b.waitUntil("binder editor", ctx -> ctx.screen() instanceof CollectionScreen).awaitModularUI()
                  .waitUntil("own active name", ctx -> ctx.el("#list-name").text().equals("M5 " + ctx.mc().player.getName().getString())))
          .server("companion denies only A", sc -> {
              var hook = new CollectionProgressionFixture(); hook.owner = sc.player("A").getUUID(); hook.mode = "deny";
              sc.put("hook", hook); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(hook);
          }).client("A", "Home reports own companion denial", b -> b.step("Back to Home", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#collection-back"))
                  .waitUntil("owner Home", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI()
                  .waitUntil("actual companion reason", ctx -> ctx.el("#home-active-deck").text().contains("M4 progression requirement"))
                  .screenshot("m5-private-companion-denial"))
          .client("B", "opponent remains eligible without private reason", b -> b.step("Back to Home", ctx -> com.haxerus.duelcraft.client.uitest.ManagementTestInput.click(ctx, "#collection-back"))
                  .waitUntil("peer Home", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI()
                  .waitUntil("peer own deck", ctx -> ctx.el("#home-active-deck").text().contains("M5 LDTestB"))
                  .check("no private companion reason on peer", ctx -> !ctx.el("#home-active-deck").text().contains("progression requirement")))
          .server("denial retains saved list and other player activation", sc -> {
              var a = sc.player("A").getData(CollectionAttachments.COLLECTION).data().orElseThrow();
              var b = sc.player("B").getData(CollectionAttachments.COLLECTION).data().orElseThrow();
              sc.check("only denied activation clears", a.activeDeckId() == null && a.decks().size() == 1 && b.activeDeckId() != null);
          })
          .teardownAllClients("close M5 fixtures", b -> b.closeScreen())
          .teardownServer("restore collections and release duel", sc -> {
              CollectionProgressionFixture hook = sc.get("hook"); if (hook != null) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(hook);
              if (sc.get("matPos") != null) sc.player("A").level().setBlockAndUpdate(sc.get("matPos"), sc.get("matBefore"));
              for (var role : List.of("A", "B")) {
                  var p = sc.player(role); DuelManager.get().forfeit(p);
                  p.setData(CollectionAttachments.COLLECTION, sc.get("original" + role));
              }
          });
    }
    private static void useMat(ServerContext sc, String role) {
        var player = sc.player(role);
        net.minecraft.core.BlockPos pos = sc.get("matPos");
        player.level().getBlockState(pos).useWithoutItem(player.level(), player,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }
    private static boolean required() { return System.getProperty("duelcraft.uitest.mpOwnership", "default").equals("required"); }
}

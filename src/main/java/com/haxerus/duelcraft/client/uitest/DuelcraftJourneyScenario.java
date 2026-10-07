package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.*;
import com.haxerus.duelcraft.client.interaction.*;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import org.lwjgl.glfw.GLFW;
import java.util.Map;

/** No-command navigation and UI persistence against the integrated server. */
@LDLRegisterClient(name = "duelcraft_journey", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelcraftJourneyScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.closeScreen().timeoutMs(60000).server("empty player collection", sc -> {
            CollectionRuntimeFixture.capture(sc);
            sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(
                    new PlayerCollectionData(100, Map.of(), Map.of(), null)));
        }).step("reset fixture client snapshot", ctx -> DuelcraftClient.getCollectionClient().connect())
          .step("queue mapped J key", ctx -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J)))
          .waitUntil("hotkey opens Home", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI()
          .waitForText("#home-active-deck", "No active deck");
        CollectionRuntimeFixture.press(s, "#home-collection");
        s.waitUntil("Home opens editor", ctx -> ctx.screen() instanceof CollectionScreen).awaitModularUI()
         .waitUntil("editor catalog ready", ctx -> ctx.el("#save-deck").isActive())
         .step("populate legal draft in editor model", ctx -> {
             Object controller = ctx.getField(ctx.screen(), "controller");
             DeckEditorModel model = ctx.getField(controller, "model");
             SavedDeckController lists = ctx.getField(controller, "lists");
             var cards = CollectionPrivacyScenario.cards();
             cards.main().forEach(code -> model.add(DeckEditorModel.Section.MAIN, code));
             cards.extra().forEach(code -> model.add(DeckEditorModel.Section.EXTRA, code));
             cards.side().forEach(code -> model.add(DeckEditorModel.Section.SIDE, code));
             lists.rename("M5 journey deck"); ctx.put("lists", lists);
         });
        CollectionRuntimeFixture.press(s, "#save-deck");
        s.waitUntil("saved without ownership", ctx -> !ctx.<SavedDeckController>get("lists").dirty())
         .checkServer("server saved exact unowned deck", sc -> CollectionRuntimeFixture.data(sc).counts().isEmpty()
                 && CollectionRuntimeFixture.data(sc).decks().values().stream().anyMatch(d -> d.cards().equals(CollectionPrivacyScenario.cards())));
        CollectionRuntimeFixture.press(s, "#activate-deck");
        s.waitUntilServer("default ownership permits activation", sc -> CollectionRuntimeFixture.data(sc).activeDeckId() != null);
        s.step("edit while Back reply is in flight", ctx -> {
            DuelcraftScreens.request(com.haxerus.duelcraft.interaction.ScreenRoute.HOME);
            ctx.<SavedDeckController>get("lists").rename("Late unsaved change");
        }).waitUntil("late edits still require a decision", ctx -> ctx.screen() instanceof CollectionScreen
                && ctx.el("#close-dialog").isVisible());
        CollectionRuntimeFixture.press(s, "#close-discard");
        s.waitUntil("editor Back reaches Home", ctx -> ctx.screen() instanceof HomeScreen).awaitModularUI()
         .waitUntil("Home shows active deck", ctx -> ctx.el("#home-active-deck").text().contains("M5 journey deck"));
        CollectionRuntimeFixture.press(s, "#home-duels");
        s.waitUntil("Home opens lobby", ctx -> ctx.screen() instanceof DuelLobbyScreen).awaitModularUI();
        CollectionRuntimeFixture.press(s, "#lobby-settings");
        s.step("change unsent seed", ctx -> ctx.el("#settings-seed").as(TextField.class).setText("12345", true));
        CollectionRuntimeFixture.press(s, "#settings-close");
        CollectionRuntimeFixture.press(s, "#lobby-select-deck");
        s.waitUntil("lobby opens own editor", ctx -> ctx.screen() instanceof CollectionScreen).awaitModularUI();
        CollectionRuntimeFixture.press(s, "#collection-back");
        s.waitUntil("Back returns to lobby", ctx -> ctx.screen() instanceof DuelLobbyScreen).awaitModularUI();
        CollectionRuntimeFixture.press(s, "#lobby-settings");
        s.checkText("#settings-seed", "12345").screenshot("journey-preserved-settings");
        CollectionRuntimeFixture.teardown(s);
    }
}

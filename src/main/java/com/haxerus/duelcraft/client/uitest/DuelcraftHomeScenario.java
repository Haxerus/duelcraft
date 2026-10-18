package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.client.interaction.HomeScreen;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.*;

@LDLRegisterClient(name = "duelcraft_home", group = "duelcraft", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelcraftHomeScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        s.openScreen("Home long active deck", ctx -> {
            var id = UUID.randomUUID();
            var view = new ClientCollectionState.View(1, Map.of(), List.of(new CollectionReply.Summary(id,
                    "Spell toolbox " + "with a long name for the Home screen ".repeat(4).substring(0, 114), 40, 5, 5)), id);
            return HomeScreen.create(view, null, route -> ctx.put("route", route), artwork());
        }).awaitModularUI().ticks(3)
          .step("effective viewport", CollectionLayoutScenario::recordViewport)
          .checkTextContains("#home-active-deck", "Spell toolbox")
          .step("long active deck wraps without covering actions", ctx -> {
              var text = ctx.el("#home-active-deck").as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class);
              DuelUiAssertions.textFits(ctx, text);
              ctx.check("multiple lines reserved", text.getContentHeight() >= text.getTextStyle().fontSize() * 3);
              ctx.check("Duel below complete deck summary", ctx.el("#home-duels").bounds().y() >= ctx.el("#home-active-deck").bounds().bottom());
          })
          .checkBounds("#home-panel", DuelScreenScenario::insideViewport)
          .checkBounds("#home-active-deck", DuelScreenScenario::insideViewport)
          .checkBounds("#home-collection", DuelScreenScenario::insideViewport)
          .checkBounds("#home-duels", DuelScreenScenario::insideViewport)
          .checkBounds("#home-help", DuelScreenScenario::insideViewport)
          .checkBounds("#home-close", DuelScreenScenario::insideViewport)
          .click("#home-collection").check("collection route", ctx -> ctx.get("route") == ScreenRoute.COLLECTION)
          .click("#home-duels").check("duel route", ctx -> ctx.get("route") == ScreenRoute.LOBBY)
          .step("clear mouse focus", ctx -> ctx.ui().clearFocus())
          .key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB).key(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER).ticks(2)
          .checkVisible("#management-help")
          .step("focus Help close", ctx -> ctx.el("#help-close").element().focus())
          .key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
          .checkHidden("#management-help").checkScreen(HomeScreen.class)
          .check("Help restores its initiating control", ctx -> ctx.el("#home-help").element().isFocused())
          .screenshot("m5-home-long-artwork")
          .openScreen("Home without active deck", ctx -> HomeScreen.create(new ClientCollectionState.View(0, Map.of(), List.of(), null), null, route -> {}))
          .awaitModularUI().ticks(2).checkTextContains("#home-active-deck", "No active deck")
          .checkVisible("#home-collection").checkVisible("#home-duels").screenshot("m5-home-missing-artwork")
          .openScreen("Home normal featured artwork", ctx -> {
              var id = UUID.randomUUID();
              var view = new ClientCollectionState.View(1, Map.of(), List.of(new CollectionReply.Summary(id, "Dragon study", 40, 0, 2)), id);
              return HomeScreen.create(view, null, route -> {}, artwork());
          }).awaitModularUI().ticks(3).screenshot("m5-home-featured-artwork")
          .step("Tab to Home Duel with pointer away", ctx -> GalleryVisualAssertions.tabTo(ctx, "#home-duels")).ticks(2)
          .step("Home primary keyboard presentation", ctx -> GalleryVisualAssertions.keyboardFocus(ctx, "#home-duels"))
          .screenshot("home-duel-keyboard-focus")
          .step("Tab to ordinary Home action", ctx -> GalleryVisualAssertions.tabTo(ctx, "#home-collection")).ticks(2)
          .step("Home ordinary keyboard presentation", ctx -> GalleryVisualAssertions.keyboardFocus(ctx, "#home-collection"))
          .screenshot("home-collection-keyboard-focus");
    }
    private static net.minecraft.resources.ResourceLocation artwork() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("duelcraft", "textures/uitest/gallery/home-featured.png");
    }
}

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
                    "Spell toolbox with a long name for the Home screen", 40, 5, 5)), id);
            return HomeScreen.create(view, null, route -> ctx.put("route", route));
        }).awaitModularUI().ticks(3)
          .step("effective viewport", CollectionLayoutScenario::recordViewport)
          .checkTextContains("#home-active-deck", "Spell toolbox")
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
          .checkVisible("#management-help").key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
          .checkHidden("#management-help").checkScreen(HomeScreen.class)
          .screenshot("m5-home")
          .openScreen("Home without active deck", ctx -> HomeScreen.create(new ClientCollectionState.View(0, Map.of(), List.of(), null), null, route -> {}))
          .awaitModularUI().ticks(2).checkTextContains("#home-active-deck", "No active deck")
          .checkVisible("#home-collection").checkVisible("#home-duels");
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * ESC on a live duel opens the leave-duel dialog instead of closing the screen, and a second ESC
 * dismisses it. The harness drives the real key event, which reaches {@code DuelScreen.keyPressed}.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_pause_menu", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPauseMenuScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel pause menu",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .checkHidden("#pause-overlay")
         .key(GLFW.GLFW_KEY_ESCAPE)
         .ticks(2)
         .checkScreen(DuelScreen.class)
         .checkVisible("#pause-overlay")
         .checkVisible("#pause-concede")
         .checkVisible("#pause-stay")
         .screenshot("duel_pause_menu")
         .key(GLFW.GLFW_KEY_ESCAPE)
         .ticks(2)
         .checkHidden("#pause-overlay")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

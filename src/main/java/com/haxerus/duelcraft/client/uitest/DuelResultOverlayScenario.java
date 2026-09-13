package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The end-of-duel result overlay, driven straight from a duel result instead of a server packet:
 * local player 0 wins by life points.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_result_overlay", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelResultOverlayScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel result overlay",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .checkHidden("#result-overlay")
         .step("local player wins by life points", ctx -> LDLibDuelScreen.showResult(0, 1))
         .ticks(2)
         .checkVisible("#result-overlay")
         .checkTextContains("#result-title", "win")
         .checkText("#result-reason", "Life points")
         .checkVisible("#result-close")
         .screenshot("duel_result_overlay")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

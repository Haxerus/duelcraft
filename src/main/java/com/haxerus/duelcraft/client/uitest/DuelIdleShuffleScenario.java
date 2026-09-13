package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * MSG_SELECT_IDLECMD with {@code canShuffle}: the shuffle-hand button (engine action type 8)
 * joins the phase buttons, and stays out of the layout entirely when the engine does not offer it.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_idle_shuffle", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelIdleShuffleScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel idle shuffle",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .ticks(2)
         .checkHidden("#shuffle-btn")
         .step("idle command offering a hand shuffle",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(0,
                         List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                         true, true, true)))
         .ticks(2)
         .checkVisible("#shuffle-btn")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_idle_shuffle")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

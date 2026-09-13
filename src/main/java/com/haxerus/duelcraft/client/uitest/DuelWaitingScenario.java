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

/**
 * The host-synthesised MSG_WAITING: the status label says the opponent is answering, and our own
 * next prompt takes the label back.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_waiting", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelWaitingScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel waiting", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .ticks(2)
         .checkHidden("#status-label")
         .step("the opponent is prompted", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Waiting()))
         .ticks(2)
         .checkVisible("#status-label")
         .checkTextContains("#status-label", "Waiting")
         .screenshot("duel_waiting")
         .step("our own prompt arrives",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 30L)))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkHidden("#status-label")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

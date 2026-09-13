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

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * MSG_SELECT_COUNTER: no dialog. Candidate field cards get the target highlight, and the status
 * label shows the overall remaining count; each click removes one counter from that card. No card
 * database or images are needed since the caption falls back to "counter type N" without one.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_select_counter", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSelectCounterPromptScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel select counter prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .step("select counter, two field cards holding 2 and 1 counters",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectCounter(0, 1, 3, List.of(
                         new DuelMessage.CounterCard(89631139, 0, LOCATION_MZONE, 0, 2),
                         new DuelMessage.CounterCard(33750025, 0, LOCATION_MZONE, 1, 1)))))
         .ticks(2)
         .checkClass("#plr-mon-0", "target")
         .checkClass("#plr-mon-1", "target")
         .checkVisible("#status-label")
         .checkTextContains("#status-label", "Remove 3")
         .click("#plr-mon-0")
         .frames(2)
         .checkTextContains("#status-label", "Remove 2")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_select_counter")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

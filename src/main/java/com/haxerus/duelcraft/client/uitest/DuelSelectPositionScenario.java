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

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * MSG_SELECT_POSITION offering all four battle positions: each one is a card-image button —
 * the card's art face-up, the card back face-down, turned sideways for defense.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_select_position", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSelectPositionScenario implements UIScenario {

    private static final int ALL_POSITIONS =
            POS_FACEUP_ATTACK | POS_FACEDOWN_ATTACK | POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE;

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel select position",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .step("select position, all four offered",
                 ctx -> LDLibDuelScreen.applyMessage(
                         new DuelMessage.SelectPosition(0, 89631139, ALL_POSITIONS)))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkCount("#prompt-body .position-choice", 4)
         .checkCount("#prompt-body .card", 4)
         .checkCount("#prompt-body .card.defense", 2)
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_select_position")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

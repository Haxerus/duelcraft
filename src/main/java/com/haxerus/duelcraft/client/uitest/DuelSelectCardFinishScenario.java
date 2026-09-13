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
 * MSG_SELECT_CARD in field mode with {@code min < max}: one pick meets the minimum without
 * reaching the maximum, so the selection must stay open and offer the shared Finish button
 * instead of submitting itself.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_select_card_finish", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSelectCardFinishScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel select card finish",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         // A dialog-mode selection first: its in-dialog Finish/Cancel twin must not outlive the
         // prompt, or the next field selection shows no button at all.
         .step("select one graveyard card in the dialog",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectCard(0, false, 1, 1, List.of(
                         new DuelMessage.CardInfo(28406301, 0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK)))))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkExists("#prompt-dialog-action-btn")
         .step("select 1-2 of two on-field monsters",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                         new DuelMessage.CardInfo(89631139, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                         new DuelMessage.CardInfo(33750025, 0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK)))))
         .ticks(2)
         .checkHidden("#prompt-overlay")
         .checkClass("#plr-mon-0", "selectable")
         .checkHidden("#prompt-action-btn")
         .click("#plr-mon-0")
         .frames(2)
         .checkClass("#plr-mon-0", "selected")
         .checkVisible("#prompt-action-btn")
         .checkText("#prompt-action-btn", "Finish")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_select_card_finish")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

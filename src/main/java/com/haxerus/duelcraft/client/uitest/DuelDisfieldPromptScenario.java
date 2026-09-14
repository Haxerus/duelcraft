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
 * MSG_SELECT_DISFIELD with count=2: clicking one of the two highlighted zones must only count
 * down ("1 zone(s)") rather than submit, since the response needs both zone triples together.
 * No cards are placed on the field, so this does not touch the card database or card images.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_disfield_prompt", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelDisfieldPromptScenario implements UIScenario {

    // Every zone bit set (blocked) except the local player's monster zones 3 and 4.
    private static final int FIELD = ~((1 << 3) | (1 << 4));

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel disfield prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("select disfield, count=2",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectDisfield(0, 2, FIELD)))
         .ticks(2)
         .checkClass("#plr-mon-3", "target")
         .checkClass("#plr-mon-4", "target")
         .checkVisible("#status-label")
         .checkTextContains("#status-label", "Select 2 zone(s) to become unusable")
         .click("#plr-mon-3")
         .frames(2)
         .checkTextContains("#status-label", "Select 1 zone(s) to become unusable")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_disfield_prompt")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

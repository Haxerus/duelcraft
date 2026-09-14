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
 * The card object model on the field: an XYZ host carrying two materials, a monster with three
 * counters, a targeted monster, a disabled zone and a pendulum card showing its scales. Each one
 * is a badge or class the renderer can only draw because materials, counters, target links, the
 * disabled mask and the latest query live on {@link com.haxerus.duelcraft.client.ClientCard}
 * rather than in parallel arrays.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_card_model", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelCardModelScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel card model",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .step("materials, counters, target and a disabled zone",
                 ctx -> DuelScreenFixture.populateCardModel())
         .ticks(2)
         .checkText("#plr-mon-0 .card-materials", "x2")
         .checkText("#plr-mon-1 .card-counters", "3")
         .checkClass("#plr-mon-2", "targeted")
         .checkClass("#plr-mon-3", "disabled")
         .checkText("#plr-st-0 .card-scales", "1/8")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_card_model")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

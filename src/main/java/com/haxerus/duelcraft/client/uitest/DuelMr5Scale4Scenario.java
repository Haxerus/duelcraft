package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale4", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale4Scenario extends DuelScreenScenario {

    public DuelMr5Scale4Scenario() {
        super(DuelRule.MR5, 4);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkVisible("#plr-mon-0")
         .checkVisible("#plr-mon-4")
         .checkHidden("#plr-pz-left")
         .checkHidden("#opp-pz-right");
    }
}

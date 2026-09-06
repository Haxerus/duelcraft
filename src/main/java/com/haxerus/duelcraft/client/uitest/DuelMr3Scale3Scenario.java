package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr3_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr3Scale3Scenario extends DuelScreenScenario {

    public DuelMr3Scale3Scenario() {
        super(DuelRule.MR3, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#plr-pz-left")
         .checkVisible("#plr-pz-right")
         .checkVisible("#opp-pz-left")
         .checkVisible("#opp-pz-right")
         .checkExists("#plr-pz-left .card")
         // checkHidden also passes when the selector matches nothing, so pair each one that is
         // never checked visible here with a checkExists that would catch a renamed id or class.
         .checkExists("#emz-left")
         .checkHidden("#emz-left")
         .checkExists("#emz-right")
         .checkHidden("#emz-right")
         .checkExists("#plr-st-0 .pendulum-marker")
         .checkHidden("#plr-st-0 .pendulum-marker")
         .checkExists("#plr-st-4 .pendulum-marker")
         .checkHidden("#plr-st-4 .pendulum-marker");
    }
}

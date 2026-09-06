package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale2", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale2Scenario extends DuelScreenScenario {

    public DuelMr5Scale2Scenario() {
        super(DuelRule.MR5, 2);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkVisible("#plr-mon-0")
         .checkVisible("#plr-mon-4")
         .checkVisible("#plr-st-0 .pendulum-marker")
         // checkHidden also passes when the selector matches nothing, so pair each one that is
         // never checked visible here with a checkExists that would catch a renamed id or class.
         .checkExists("#plr-st-1 .pendulum-marker")
         .checkHidden("#plr-st-1 .pendulum-marker")
         .checkExists("#plr-pz-left")
         .checkHidden("#plr-pz-left")
         .checkExists("#opp-pz-right")
         .checkHidden("#opp-pz-right");
    }
}

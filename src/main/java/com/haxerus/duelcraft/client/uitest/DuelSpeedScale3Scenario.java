package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_speed_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSpeedScale3Scenario extends DuelScreenScenario {

    public DuelSpeedScale3Scenario() {
        super(DuelRule.SPEED, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        // checkHidden also passes when the selector matches nothing, so pair each one that is
        // never checked visible here with a checkExists that would catch a renamed id or class.
        s.checkExists("#plr-mon-0")
         .checkHidden("#plr-mon-0")
         .checkExists("#plr-mon-4")
         .checkHidden("#plr-mon-4")
         .checkExists("#opp-st-0")
         .checkHidden("#opp-st-0")
         .checkExists("#opp-st-4")
         .checkHidden("#opp-st-4")
         .checkVisible("#plr-mon-1")
         .checkVisible("#plr-mon-3")
         .checkVisible("#opp-st-2")
         .checkExists("#emz-left")
         .checkHidden("#emz-left")
         .checkExists("#emz-right")
         .checkHidden("#emz-right")
         .checkExists("#plr-pz-left")
         .checkHidden("#plr-pz-left")
         .checkExists("#plr-st-1 .pendulum-marker")
         .checkHidden("#plr-st-1 .pendulum-marker")
         .checkExists("#plr-st-3 .pendulum-marker")
         .checkHidden("#plr-st-3 .pendulum-marker");
    }
}

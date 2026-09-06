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
        s.checkHidden("#plr-mon-0")
         .checkHidden("#plr-mon-4")
         .checkHidden("#opp-st-0")
         .checkHidden("#opp-st-4")
         .checkVisible("#plr-mon-1")
         .checkVisible("#plr-mon-3")
         .checkVisible("#opp-st-2")
         .checkHidden("#emz-left")
         .checkHidden("#emz-right")
         .checkHidden("#plr-pz-left")
         .checkHidden("#plr-st-1 .pendulum-marker")
         .checkHidden("#plr-st-3 .pendulum-marker");
    }
}

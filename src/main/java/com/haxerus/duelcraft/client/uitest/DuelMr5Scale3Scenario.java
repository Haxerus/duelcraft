package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The case from dev/gui_3.png. Hovering a card also proves hit-testing survives the canvas transform. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale3Scenario extends DuelScreenScenario {

    public DuelMr5Scale3Scenario() {
        super(DuelRule.MR5, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkHidden("#plr-pz-left")
         .checkHidden("#opp-pz-right")
         .checkHidden("#card-info-banner")
         .hover("#plr-mon-2 .card")
         .frames(2)
         .checkVisible("#card-info-banner");
    }
}

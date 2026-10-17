package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "collection_playtest_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionPlaytestScale3Scenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.tags("collection", "visual-audit").requiresWorld(false).guiScale(3);
    }

    @Override public void define(ScenarioBuilder s) {
        CollectionWidgetPlaytest.define(s, 1920, 1080, 3);
    }
}

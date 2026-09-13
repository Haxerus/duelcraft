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

/**
 * MSG_ANNOUNCE_NUMBER: reuses {@code buildOptionPrompt} over the option values; clicking sends the
 * option's index, not the value. This does not need the card database or images.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_announce_number", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelAnnounceNumberPromptScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel announce number prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("announce number, four values",
                 ctx -> LDLibDuelScreen.applyMessage(
                         new DuelMessage.AnnounceNumber(0, List.of(1L, 2L, 3L, 4L))))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkCount("#prompt-buttons .prompt-btn", 4)
         .screenshot("duel_announce_number")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

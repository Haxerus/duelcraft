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
 * MSG_ANNOUNCE_RACE: one toggle button per bit set in {@code available}, no OK button — checking
 * exactly {@code count} bits submits immediately. This does not need the card database or images.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_announce_race", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelAnnounceRacePromptScenario implements UIScenario {

    // RACE_WARRIOR | RACE_SPELLCASTER | RACE_FAIRY
    private static final long THREE_RACES = 0x1L | 0x2L | 0x4L;

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel announce race prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("announce race, count=1 of 3 available",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceRace(0, 1, THREE_RACES)))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkCount("#prompt-buttons .prompt-btn", 3)
         .checkVisible("#announce-bit-0")
         .checkVisible("#announce-bit-1")
         .checkVisible("#announce-bit-2")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_announce_race")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

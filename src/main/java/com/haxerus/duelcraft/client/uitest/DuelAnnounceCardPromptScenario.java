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

import static com.haxerus.duelcraft.core.OcgConstants.OPCODE_ISTYPE;
import static com.haxerus.duelcraft.core.OcgConstants.TYPE_MONSTER;

/**
 * MSG_ANNOUNCE_CARD: a search box over the card database filtered by the prompt's opcodes, or raw
 * passcode entry when no database is present. The result list depends on the downloaded cards.cdb,
 * so this only checks that the dialog and its search field come up either way.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_announce_card", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelAnnounceCardPromptScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel announce card prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("announce card, no opcode filter",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceCard(0, List.of())))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkVisible("#announce-card-search")
         .screenshot("duel_announce_card")
         .step("announce card, ISTYPE monster",
                 ctx -> LDLibDuelScreen.applyMessage(
                         new DuelMessage.AnnounceCard(0, List.of((long) TYPE_MONSTER, OPCODE_ISTYPE))))
         .ticks(2)
         .checkVisible("#announce-card-search")
         .screenshot("duel_announce_card_monsters")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

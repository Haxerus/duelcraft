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

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * MSG_SORT_CARD: overlay card list, clicking a card assigns the next ordinal (drawn on the card).
 * The prompt cards never touch the field, so this does not need the card database or images.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_sort_card", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSortCardPromptScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel sort card prompt",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("sort card, three cards",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SortCard(0, List.of(
                         new DuelMessage.SortableCard(89631139, 0, LOCATION_HAND, 0),
                         new DuelMessage.SortableCard(33750025, 0, LOCATION_HAND, 1),
                         new DuelMessage.SortableCard(28406301, 0, LOCATION_HAND, 2)))))
         .ticks(2)
         .checkVisible("#prompt-overlay")
         .checkText("#sort-card-0 .card-ordinal", "")
         .click("#sort-card-0")
         .frames(2)
         .click("#sort-card-2")
         .frames(2)
         .check("two ordinal labels visible", ctx ->
                 !ctx.el("#sort-card-0 .card-ordinal").text().isBlank()
                         && !ctx.el("#sort-card-2 .card-ordinal").text().isBlank())
         .checkText("#sort-card-1 .card-ordinal", "")
         .screenshot("duel_sort_card")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * The card object model on the field: an XYZ host carrying two materials, a monster with three
 * counters, a targeted monster, a disabled zone and a pendulum card showing its scales. Each one
 * is a badge or class the renderer can only draw because materials, counters, target links, the
 * disabled mask and the latest query live on {@link com.haxerus.duelcraft.client.ClientCard}
 * rather than in parallel arrays.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_card_model", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelCardModelScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel card model",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .step("materials, counters, target and a disabled zone",
                 ctx -> DuelScreenFixture.populateCardModel())
         .ticks(2)
         .checkCount("#plr-mon-0 .xyz-material", 2)
         .step("material cards fit beneath their host", ctx -> DuelXyzMaterialsScenario.assertStack(ctx, "#plr-mon-0"))
         .checkText("#plr-mon-1 .card-counters", "3")
         .checkClass("#plr-mon-2", "targeted")
         .checkClass("#plr-mon-3", "disabled")
         .checkText("#plr-st-0 .card-scales", "1/8")
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_card_model")
         .step("single and multiple digit annotations", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.AddCounter(1, 0, LOCATION_MZONE, 2, 123));
             for (int seq = 0; seq < 2; seq++) {
                 var loc = new LocInfo(0, LOCATION_MZONE, seq, POS_FACEUP_ATTACK);
                 LDLibDuelScreen.applyMessage(new DuelMessage.CardHint(loc, CHINT_TURN, seq == 0 ? 1 : 12));
                 LDLibDuelScreen.applyMessage(new DuelMessage.Chaining(89631139, loc,
                         0, LOCATION_MZONE, seq, 501, seq == 0 ? 1 : 12));
             }
             var scales = new QueriedCard();
             scales.flags = QUERY_LSCALE | QUERY_RSCALE;
             scales.lscale = 12;
             scales.rscale = 13;
             LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(0, LOCATION_SZONE, 0, scales));
             var stats = new QueriedCard();
             stats.flags = QUERY_ATTACK | QUERY_DEFENSE;
             stats.attack = 3000;
             stats.defense = 2500;
             LDLibDuelScreen.applyMessage(new DuelMessage.UpdateCard(0, LOCATION_MZONE, 2, stats));
         }).ticks(2)
         .checkText("#plr-mon-0 .chain-marker", "1")
         .checkText("#plr-mon-1 .chain-marker", "12")
         .checkText("#plr-mon-0 .card-turns", "T1")
         .checkText("#plr-mon-1 .card-turns", "T12")
         .checkText("#plr-mon-2 .card-counters", "123")
         .checkText("#plr-st-0 .card-scales", "12/13")
         .checkText("#plr-mon-2 .stat-atk-def", "3000/2500")
         .step("annotation backgrounds contain their text", DuelUiAssertions::audit)
         .screenshot("wide-card-annotations")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

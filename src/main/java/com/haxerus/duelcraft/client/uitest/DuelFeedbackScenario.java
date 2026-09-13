package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * The feedback layer: two chain links marked on the slots they triggered from, the duel log open
 * from the control panel, and a floating LP number next to the damaged player's bar. The damage comes last
 * so the number is still up when the screenshot is taken.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_feedback", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelFeedbackScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel feedback",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .ticks(2)
         .checkHidden("#duel-log")
         .checkHidden("#lp-delta-0")
         .step("a two-link chain, each link triggered from a monster zone", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.Chaining(89631139,
                     new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                     0, LOCATION_MZONE, 0, 501L, 1));
             LDLibDuelScreen.applyMessage(new DuelMessage.Chaining(33750025,
                     new LocInfo(1, LOCATION_MZONE, 1, POS_FACEUP_ATTACK),
                     1, LOCATION_MZONE, 1, 502L, 2));
         })
         .ticks(2)
         .checkCount(".chain-marker", 2)
         .checkText("#chain-count", "Chain: 2")
         .click("#log-toggle")
         .ticks(1)
         .checkVisible("#duel-log")
         .step("player hint beside transient feedback", ctx ->
                 LDLibDuelScreen.applyMessage(new DuelMessage.PlayerHint(0, PHINT_DESC_ADD, 501L)))
         .step("the player takes 500 damage",
                 ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Damage(0, 500)))
         .ticks(1)
         .checkVisible("#lp-delta-0")
         .checkTextContains("#lp-delta-0", "-500")
         .check("player hint and LP delta have separate rows", ctx ->
                 ctx.el("#plr-hints").bounds().bottom() <= ctx.el("#lp-delta-0").bounds().y() + 1)
         .check("LP delta stays above log header", ctx ->
                 ctx.el("#lp-delta-0").bounds().bottom() <= ctx.el("#duel-log-title").bounds().y() + 1)
         .check("the log lists the chain links and the damage",
                 ctx -> ctx.count(".log-line") >= 3)
         .step("visual layout", DuelUiAssertions::audit)
         .screenshot("duel_feedback")
         .step("turn banner and a long toast", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.NewTurn(0));
             LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_OPSELECTED, 0, 501L));
         }).ticks(2)
         .step("long toast text", ctx -> ctx.el("#toast").as(Label.class).setText(Component.literal(
                 "Your opponent selected a card effect that adds one monster from the Deck to the hand, then Special Summons a monster.")))
         .check("toast stays below player hints", ctx ->
                 ctx.el("#toast").bounds().y() >= ctx.el("#plr-hints").bounds().bottom() - 1)
         .step("feedback layout", DuelUiAssertions::audit)
         .screenshot("feedback-banner-toast")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }
}

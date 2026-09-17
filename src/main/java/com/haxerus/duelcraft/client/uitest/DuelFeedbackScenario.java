package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.ClientDuelState;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
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
         .step("stable log names and phase context", ctx -> {
             var state = activeState();
             state.hintText = new ClientDuelState.HintText() {
                 public String cardName(int code) { return code == 89631139 ? "Azure Dragon" : "Silver Guardian"; }
                 public String desc(long desc) {
                     return desc == 502 ? "Target one monster in the Graveyard; Special Summon it, then draw one card. "
                             + "That monster cannot attack for the rest of this turn." : "Draw one card";
                 }
                 public String systemString(int code) { return null; }
             };
             LDLibDuelScreen.applyMessage(new DuelMessage.NewTurn(0));
             LDLibDuelScreen.applyMessage(new DuelMessage.NewPhase(PHASE_DRAW));
             LDLibDuelScreen.applyMessage(new DuelMessage.NewPhase(PHASE_MAIN1));
         })
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
         .check("player hint stays below the LP delta", ctx ->
                 ctx.el("#plr-hints").bounds().y() >= ctx.el("#lp-delta-0").bounds().bottom() - 1)
         .check("log stays below the wrapped player hint", ctx ->
                 ctx.el("#duel-log").bounds().y() >= ctx.el("#plr-hints").bounds().bottom() + 1)
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
         .ticks(2)
         .check("toast stays clear of the open log", ctx ->
                 ctx.el("#toast").bounds().x() >= ctx.el("#duel-log").bounds().right() + 1)
         .step("feedback layout", DuelUiAssertions::audit)
         .screenshot("feedback-banner-toast")
         .step("resolve chain and enter a real damage step", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainSolving(2));
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainNegated(2));
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainSolved(2));
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainSolving(1));
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainDisabled(1));
             LDLibDuelScreen.applyMessage(new DuelMessage.ChainEnd());
             LDLibDuelScreen.applyMessage(new DuelMessage.NewPhase(PHASE_BATTLE_START));
             LDLibDuelScreen.applyMessage(new DuelMessage.Attack(
                     new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                     new LocInfo(1, LOCATION_MZONE, 1, POS_FACEUP_ATTACK)));
             LDLibDuelScreen.applyMessage(new DuelMessage.DamageStepStart());
             LDLibDuelScreen.applyMessage(new DuelMessage.DamageStepEnd());
         }).ticks(2)
         .check("log keeps its 210-unit footprint", ctx -> Math.abs(ctx.el("#duel-log").bounds().width()
                 - ctx.el("#duel-canvas").bounds().width() * 210 / 960) <= 1)
         .step("long effect wraps and card names retain semantic styling", ctx -> {
             var line = ctx.all(".log-line").stream()
                     .map(ref -> ref.as(TextElement.class))
                     .filter(text -> text.getText().getString().contains("Target one monster")).findFirst().orElseThrow();
             ctx.check("long log entry wraps", line.getContentHeight() > line.getTextStyle().fontSize() * 2);
             var name = line.getText().getSiblings().stream()
                     .filter(part -> part.getString().equals("Silver Guardian")).findFirst().orElseThrow();
             ctx.check("card name has its own color", name.getStyle().getColor() != null
                     && name.getStyle().getColor().getValue() == 0xE6C878);
         })
         .screenshot("log-mixed-context")
         .step("target a sanitized set card beside a known card", ctx -> {
             var hidden = new LocInfo(1, LOCATION_SZONE, 4, POS_FACEDOWN_DEFENSE);
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(0, new LocInfo(1, 0, 0, 0), hidden, 0));
             LDLibDuelScreen.applyMessage(new DuelMessage.BecomeTarget(java.util.List.of(hidden,
                     new LocInfo(1, LOCATION_MZONE, 1, POS_FACEUP_ATTACK))));
         }).ticks(2)
         .check("sanitized target has a readable label", ctx -> ctx.all(".log-line").stream()
                 .anyMatch(ref -> ref.as(TextElement.class).getText().getString().equals("Face-down card targeted")))
         .check("known target keeps its name", ctx -> ctx.all(".log-line").stream()
                 .anyMatch(ref -> ref.as(TextElement.class).getText().getString().equals("\"Silver Guardian\" targeted")))
         .screenshot("sanitized-target-log")
         .step("enough real events to require scrolling", ctx -> {
             for (int i = 0; i < 60; i++) LDLibDuelScreen.applyMessage(new DuelMessage.Recover(0, i + 1));
         }).ticks(2)
         .step("remember log offset", ctx -> ctx.put("logY", ctx.all(".log-line").getFirst().bounds().y()))
         .scroll("#duel-log-list", -100).ticks(2)
         .check("log scrolls", ctx -> ctx.all(".log-line").getFirst().bounds().y() < (float) ctx.get("logY"))
         .screenshot("log-long-scrolled")
         .scroll("#duel-log-list", 100).ticks(2)
         .step("click a whole chain line", ctx -> {
             var bounds = ctx.all(".log-line").stream()
                     .filter(ref -> ref.as(TextElement.class).getText().getString().startsWith("Chain Link 1:"))
                     .findFirst().orElseThrow().bounds();
             ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
             ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
         }).ticks(2)
         .checkHidden("#duel-log")
         .checkVisible("#card-info-banner")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    private static ClientDuelState activeState() {
        try {
            var field = LDLibDuelScreen.class.getDeclaredField("activeState");
            field.setAccessible(true);
            return (ClientDuelState) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot install log text fixture", e);
        }
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Actionable cards stay visible when idle/battle commands point into an inspected pile. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_zone_inspector_actions", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelZoneInspectorActionsScenario implements UIScenario {
    private static final int GRAVE_CARD_0 = 28406301;
    private static final int GRAVE_CARD_1 = 55415564;

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel", "visual-audit").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("zone inspector actions",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate sparse graveyard", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(GRAVE_CARD_0,
                     new LocInfo(0, 0, 0, 0),
                     new LocInfo(0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK), 0));
             LDLibDuelScreen.applyMessage(new DuelMessage.Move(GRAVE_CARD_1,
                     new LocInfo(0, 0, 0, 0),
                     new LocInfo(0, LOCATION_GRAVE, 1, POS_FACEUP_ATTACK), 0));
         }).ticks(2)
         .click("#plr-graveyard").ticks(2)
         .checkCount("#zone-inspector-list .card", 2)
         .checkCount("#zone-inspector-list .card.selectable", 0)
         .step("remember sparse geometry", ctx -> rememberGeometry(ctx, "sparse"))
         .step("idle activation in graveyard", ctx -> LDLibDuelScreen.applyMessage(idleActivation()))
         .ticks(2)
         .checkCount("#zone-inspector-list .card.selectable", 1)
         .step("only the first graveyard card is actionable", ctx -> {
             var cards = ctx.all("#zone-inspector-list .card");
             ctx.check("first graveyard card is actionable", cards.get(0).hasClass("selectable"));
             ctx.check("second graveyard card is inactive", !cards.get(1).hasClass("selectable"));
         })
         .step("idle highlight preserves sparse geometry", ctx -> checkGeometry(ctx, "sparse"))
         .screenshot("idle-graveyard-actions")
         .step("replace command with a non-action prompt", ctx ->
                 LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 1L)))
         .ticks(2)
         .checkCount("#zone-inspector-list .card.selectable", 0)
         .step("clearing highlights preserves sparse geometry", ctx -> checkGeometry(ctx, "sparse"))
         .screenshot("graveyard-actions-cleared")
         .step("battle activation in graveyard", ctx -> LDLibDuelScreen.applyMessage(battleActivation()))
         .ticks(2)
         .checkCount("#zone-inspector-list .card.selectable", 1)
         .step("only the second graveyard card is actionable", ctx -> {
             var cards = ctx.all("#zone-inspector-list .card");
             ctx.check("first graveyard card is inactive", !cards.get(0).hasClass("selectable"));
             ctx.check("second graveyard card is actionable", cards.get(1).hasClass("selectable"));
         })
         .screenshot("battle-graveyard-actions");

        DuelPanelLayoutScenario.press(s, "#zone-inspector-close");
        s.click("#plr-extra-deck").ticks(2)
         .checkCount("#zone-inspector-list .card", 15)
         .step("remember full geometry", ctx -> rememberGeometry(ctx, "full"))
         .step("extra deck summons", ctx -> LDLibDuelScreen.applyMessage(extraDeckSummons()))
         .ticks(2)
         .checkCount("#zone-inspector-list .card.selectable", 2)
         .step("only offered extra deck cards are actionable", ctx -> {
             var cards = ctx.all("#zone-inspector-list .card");
             ctx.check("first extra deck card is inactive", !cards.get(0).hasClass("selectable"));
             ctx.check("third extra deck card is actionable", cards.get(2).hasClass("selectable"));
             ctx.check("last extra deck card is actionable", cards.get(14).hasClass("selectable"));
         })
         .step("summon highlights preserve full geometry", ctx -> checkGeometry(ctx, "full"))
         .screenshot("extra-deck-summon-actions")
         .step("open offered summon action", ctx -> clickCard(ctx, 2))
         .ticks(2)
         .checkCount(".ctx-action", 1);
        DuelPanelLayoutScenario.press(s, ".ctx-action");
        s.step("response clears full-grid actions", ctx -> { })
         .ticks(2)
         .checkCount("#zone-inspector-list .card.selectable", 0)
         .step("clearing highlights preserves full geometry", ctx -> checkGeometry(ctx, "full"))
         .screenshot("extra-deck-actions-cleared")
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    private static DuelMessage.SelectIdleCmd idleActivation() {
        var activation = new DuelMessage.ActivatableCard(
                GRAVE_CARD_0, 0, LOCATION_GRAVE, 0, 0, 1L, 0);
        return new DuelMessage.SelectIdleCmd(0, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(activation), true, true, false);
    }

    private static DuelMessage.SelectBattleCmd battleActivation() {
        var activation = new DuelMessage.ActivatableCard(
                GRAVE_CARD_1, 0, LOCATION_GRAVE, 1, 0, 2L, 0);
        return new DuelMessage.SelectBattleCmd(0, List.of(activation), List.of(), true, true);
    }

    private static DuelMessage.SelectIdleCmd extraDeckSummons() {
        return new DuelMessage.SelectIdleCmd(0, List.of(), List.of(
                new DuelMessage.IdleCmdCard(0, 0, LOCATION_EXTRA, 2),
                new DuelMessage.IdleCmdCard(0, 0, LOCATION_EXTRA, 14)),
                List.of(), List.of(), List.of(), List.of(), true, true, false);
    }

    private static void rememberGeometry(TestContext ctx, String key) {
        ctx.put(key, ctx.all("#zone-inspector-list .card").stream()
                .map(card -> card.bounds()).toArray(ElementBounds[]::new));
    }

    private static void checkGeometry(TestContext ctx, String key) {
        var before = (ElementBounds[]) ctx.get(key);
        var cards = ctx.all("#zone-inspector-list .card");
        ctx.check(key + " card count unchanged", cards.size() == before.length, before.length, cards.size());
        for (int i = 0; i < before.length; i++) {
            var after = cards.get(i).bounds();
            ctx.check(key + " card " + i + " keeps its bounds", sameBounds(before[i], after), before[i], after);
        }
    }

    private static boolean sameBounds(ElementBounds a, ElementBounds b) {
        return Math.abs(a.x() - b.x()) <= 1 && Math.abs(a.y() - b.y()) <= 1
                && Math.abs(a.width() - b.width()) <= 1 && Math.abs(a.height() - b.height()) <= 1;
    }

    private static void clickCard(TestContext ctx, int index) {
        var bounds = ctx.all("#zone-inspector-list .card").get(index).bounds();
        ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }
}

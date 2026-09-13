package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * MSG_SELECT_IDLECMD with {@code canShuffle}: the shuffle-hand button (engine action type 8)
 * appears in the hand-side controls without shifting the board or the permanent buttons.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_idle_shuffle", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelIdleShuffleScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        for (var rule : new DuelRule[]{DuelRule.MR3, DuelRule.MR5, DuelRule.SPEED}) {
            s.openScreen("duel idle shuffle",
                            ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(rule)))
             .awaitModularUI()
             .step("populate field", ctx -> DuelScreenFixture.populate(rule))
             .step("idle without shuffle", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(0,
                     List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true, true, false)))
             .ticks(2)
             .checkHidden("#shuffle-btn")
             .checkCount("#hud-bar button", 0)
             .step("controls fit beside the hand", ctx -> DuelUiAssertions.surface(ctx, "#duel-controls"))
             .screenshot("controls-" + rule.id())
             .step("remember board bounds", ctx -> {
                 for (String id : new String[]{"field-area", "center-controls", "emz-left", "emz-right", "player-hand",
                         "plr-banished", "opp-banished", "log-toggle", "concede-btn"}) ctx.put(id, ctx.el("#" + id).bounds());
             })
             .step("idle command offering a hand shuffle",
                     ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(0,
                             List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                             true, true, true)))
             .ticks(2)
             .checkVisible("#shuffle-btn")
             .step("shuffle preserves board geometry", ctx -> {
                 for (String id : new String[]{"field-area", "center-controls", "emz-left", "emz-right", "player-hand",
                         "plr-banished", "opp-banished", "log-toggle", "concede-btn"}) {
                     var before = (ElementBounds) ctx.get(id);
                     var after = ctx.el("#" + id).bounds();
                     ctx.check(id + " unchanged with shuffle", Math.abs(before.x() - after.x()) <= 1
                             && Math.abs(before.y() - after.y()) <= 1 && Math.abs(before.width() - after.width()) <= 1
                             && Math.abs(before.height() - after.height()) <= 1, before, after);
                 }
                 var panel = ctx.el("#duel-controls").bounds();
                 var hand = ctx.el("#player-hand").bounds();
                 ctx.check("controls align with the hand's bottom edge", panel.x() >= ctx.el("#field-area").bounds().right()
                         && Math.abs(panel.bottom() - hand.bottom()) <= 1);
                 DuelUiAssertions.surface(ctx, "#duel-controls");
             })
             .step("visual layout", DuelUiAssertions::audit)
             .screenshot("shuffle-" + rule.id());
            DuelPanelLayoutScenario.press(s, "#shuffle-btn");
            s.checkHidden("#shuffle-btn")
             .click("#log-toggle").ticks(2).checkVisible("#duel-log")
             .screenshot("controls-log-" + rule.id())
             .click("#log-toggle").ticks(2).checkHidden("#duel-log")
             .click("#plr-graveyard").ticks(2).checkVisible("#zone-inspector")
             .step("controls stay clear of the inspector", ctx -> {
                 ctx.check("controls and inspector do not overlap",
                         ctx.el("#duel-controls").bounds().right() + 1 <= ctx.el("#zone-inspector").bounds().x());
                 DuelUiAssertions.audit(ctx);
             }).screenshot("controls-inspector-" + rule.id());
            DuelPanelLayoutScenario.press(s, "#concede-btn");
            s.checkVisible("#pause-overlay");
            DuelPanelLayoutScenario.press(s, "#pause-stay");
            s.checkHidden("#pause-overlay")
             .step("close", ctx -> {
                 LDLibDuelScreen.close();
                 ctx.mc().setScreen(null);
             });
        }
    }
}

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
 * sits beside the hand without shifting the board when it appears.
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
             .step("remember board bounds", ctx -> {
                 for (String id : new String[]{"field-area", "center-controls", "emz-left", "emz-right", "player-hand",
                         "plr-banished", "opp-banished"}) ctx.put(id, ctx.el("#" + id).bounds());
             })
             .step("idle command offering a hand shuffle",
                     ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(0,
                             List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                             true, true, true)))
             .ticks(2)
             .checkVisible("#shuffle-btn")
             .step("shuffle preserves board geometry", ctx -> {
                 for (String id : new String[]{"field-area", "center-controls", "emz-left", "emz-right", "player-hand",
                         "plr-banished", "opp-banished"}) {
                     var before = (ElementBounds) ctx.get(id);
                     var after = ctx.el("#" + id).bounds();
                     ctx.check(id + " unchanged with shuffle", Math.abs(before.x() - after.x()) <= 1
                             && Math.abs(before.y() - after.y()) <= 1 && Math.abs(before.width() - after.width()) <= 1
                             && Math.abs(before.height() - after.height()) <= 1, before, after);
                 }
                 var button = ctx.el("#shuffle-btn").bounds();
                 var hand = ctx.el("#player-hand").bounds();
                 ctx.check("shuffle is beside the hand", button.x() >= ctx.el("#field-area").bounds().right()
                         && button.y() >= hand.y() && button.bottom() <= hand.bottom() + 1);
                 DuelUiAssertions.surface(ctx, "#shuffle-btn");
             })
             .step("visual layout", DuelUiAssertions::audit)
             .screenshot("shuffle-" + rule.id());
            DuelPanelLayoutScenario.press(s, "#shuffle-btn");
            s.checkHidden("#shuffle-btn")
             .step("close", ctx -> {
                 LDLibDuelScreen.close();
                 ctx.mc().setScreen(null);
             });
        }
    }
}

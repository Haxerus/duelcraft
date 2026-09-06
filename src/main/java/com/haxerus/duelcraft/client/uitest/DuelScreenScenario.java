package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Opens the real duel screen with a synthetic start payload, populates it, and checks that the
 * canvas and both hands fit the viewport. Subclasses pick the rule and GUI scale and add
 * rule-specific visibility checks in {@link #ruleChecks}.
 *
 * Run one with {@code gradlew runClient -PldTest=<name>} or all with {@code -PldTest=group:duelcraft}.
 */
@OnlyIn(Dist.CLIENT)
public abstract class DuelScreenScenario implements UIScenario {

    protected final DuelRule rule;
    protected final int guiScale;

    protected DuelScreenScenario(DuelRule rule, int guiScale) {
        this.rule = rule;
        this.guiScale = guiScale;
    }

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(guiScale);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel " + rule.id(), ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(rule)))
         .awaitModularUI()
         .step("populate " + rule.id(), ctx -> DuelScreenFixture.populate(rule))
         .ticks(2)
         .checkBounds("#duel-canvas", DuelScreenScenario::insideViewport)
         .checkBounds("#opponent-hand", DuelScreenScenario::insideViewport)
         .checkBounds("#player-hand", DuelScreenScenario::insideViewport);
        ruleChecks(s);
        s.screenshot(rule.id() + "-scale" + guiScale)
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    /** Rule-specific checks, appended after the shared bounds checks and before the screenshot. */
    protected abstract void ruleChecks(ScenarioBuilder s);

    /** Bounds are in GUI space, the same space as the window's scaled size. One pixel of slack for rounding. */
    protected static boolean insideViewport(ElementBounds b) {
        var window = Minecraft.getInstance().getWindow();
        float slack = 1f;
        return b.x() >= -slack
                && b.y() >= -slack
                && b.x() + b.width() <= window.getGuiScaledWidth() + slack
                && b.y() + b.height() <= window.getGuiScaledHeight() + slack;
    }
}

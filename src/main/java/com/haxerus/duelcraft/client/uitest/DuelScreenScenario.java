package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
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
         .checkBounds("#player-hand", DuelScreenScenario::insideViewport)
         .check("hand rows span the field", DuelScreenScenario::handsSpanField)
         .checkNotExists("#center-row .pile-slot")
         .check("banished piles sit outside the zone grids", DuelScreenScenario::banishedOutsideGrids)
         .check("zone grids are vertically aligned", DuelScreenScenario::gridsAligned);
        ruleChecks(s);
        s.screenshot(rule.id() + "-scale" + guiScale)
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    /** Rule-specific checks, appended after the shared bounds checks and before the screenshot. */
    protected abstract void ruleChecks(ScenarioBuilder s);

    /** Both hands stretch to the field's content width, so each must be as wide as its side row. */
    private static boolean handsSpanField(TestContext ctx) {
        boolean opponent = spansSide(ctx, "#opponent-hand", "#opponent-side");
        boolean player = spansSide(ctx, "#player-hand", "#player-side");
        return opponent && player;
    }

    private static boolean spansSide(TestContext ctx, String hand, String side) {
        var handBounds = ctx.el(hand).bounds();
        var sideBounds = ctx.el(side).bounds();
        ctx.attach(hand, "hand=%.1f %s=%.1f".formatted(handBounds.width(), side, sideBounds.width()));
        return Math.abs(handBounds.width() - sideBounds.width()) <= 2f;
    }

    /** Each banished pile sits in its own column past the outer edge of its zone grid. */
    private static boolean banishedOutsideGrids(TestContext ctx) {
        var plrBanished = ctx.el("#plr-banished").bounds();
        var plrZones = ctx.el("#plr-zones").bounds();
        var oppBanished = ctx.el("#opp-banished").bounds();
        var oppZones = ctx.el("#opp-zones").bounds();
        ctx.attach("banished vs zones", "plr banished.x=%.1f zones.right=%.1f, opp banished.right=%.1f zones.x=%.1f"
                .formatted(plrBanished.x(), plrZones.x() + plrZones.width(),
                        oppBanished.x() + oppBanished.width(), oppZones.x()));
        return plrBanished.x() >= plrZones.x() + plrZones.width() - 1f
                && oppBanished.x() + oppBanished.width() <= oppZones.x() + 1f;
    }

    /** The spacer column opposite the banished column keeps both grids at the same left edge. */
    private static boolean gridsAligned(TestContext ctx) {
        var plrZones = ctx.el("#plr-zones").bounds();
        var oppZones = ctx.el("#opp-zones").bounds();
        ctx.attach("zone grid x", "plr=%.1f opp=%.1f".formatted(plrZones.x(), oppZones.x()));
        return Math.abs(plrZones.x() - oppZones.x()) <= 1f;
    }

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

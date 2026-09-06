package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Clicking a card that has an action opens the context menu at the cursor. The other scenarios
 * only look at layout, so this is the one that covers ClickDispatcher's screen-to-canvas
 * conversion — the step that goes wrong whenever the root is not the size of the viewport.
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale3_click", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale3ClickScenario extends DuelScreenScenario {

    /** showContextMenu truncates left/top to whole design pixels, which is under a screen pixel here. */
    private static final float TOLERANCE = 2f;

    public DuelMr5Scale3ClickScenario() {
        super(DuelRule.MR5, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        // plr-mon-2 sits mid-field, far enough from every canvas edge that the flip-and-nudge
        // in showContextMenu leaves the position alone.
        s.step("reposition action on plr-mon-2", ctx -> DuelScreenFixture.promptRepositionOf(0, 2))
         .ticks(2)
         .checkHidden("#context-menu")
         .click("#plr-mon-2 .card")
         .frames(2)
         .checkVisible("#context-menu")
         .check("context menu opens at the cursor", ctx -> {
             var card = ctx.el("#plr-mon-2 .card").bounds();
             var menu = ctx.el("#context-menu").bounds();
             ctx.attach("menu vs cursor", "menu=(%.1f, %.1f) cursor=(%.1f, %.1f)"
                     .formatted(menu.x(), menu.y(), card.center().x, card.center().y));
             return Math.abs(menu.x() - card.center().x) <= TOLERANCE
                     && Math.abs(menu.y() - card.center().y) <= TOLERANCE;
         });
    }
}

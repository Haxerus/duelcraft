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
import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.*;

/** The remaining field and chain surfaces, captured before and during selections. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_selection_surfaces", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSelectionSurfacesScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("selection surfaces", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("populate", ctx -> DuelScreenFixture.populate(DuelRule.MR5)).ticks(2);
        var cards = List.of(new DuelMessage.CardInfo(0, 0, LOCATION_MZONE, 0, 1),
                new DuelMessage.CardInfo(0, 0, LOCATION_MZONE, 1, 1), new DuelMessage.CardInfo(0, 0, LOCATION_MZONE, 2, 1));
        prompt(s, "tribute", new DuelMessage.SelectTribute(0, true, 1, 3, List.of(
                new DuelMessage.TributeCard(0, 0, LOCATION_MZONE, 0, 1), new DuelMessage.TributeCard(0, 0, LOCATION_MZONE, 1, 1))));
        s.click("#plr-mon-0").ticks(2).checkText("#prompt-action-btn", "Finish");
        capture(s, "tribute-selected");
        prompt(s, "unselect-field", new DuelMessage.SelectUnselectCard(0, true, true, 1, 3, cards, List.of()));
        prompt(s, "sum-field", new DuelMessage.SelectSum(0, false, 6, 1, 3, List.of(), List.of(
                new DuelMessage.SumCard(0, 0, LOCATION_MZONE, 0, 1, 2),
                new DuelMessage.SumCard(0, 0, LOCATION_MZONE, 1, 1, 2),
                new DuelMessage.SumCard(0, 0, LOCATION_MZONE, 2, 1, 2))));
        s.click("#plr-mon-0");
        capture(s, "sum-field-selected");
        prompt(s, "place", new DuelMessage.SelectPlace(0, 2, ~0x1f));
        s.click("#plr-mon-0");
        capture(s, "place-selected");
        var chains = List.of(new DuelMessage.ActivatableCard(0, 0, LOCATION_MZONE, 0, 1, 1, 0),
                new DuelMessage.ActivatableCard(0, 0, LOCATION_MZONE, 0, 1, 2, 0),
                new DuelMessage.ActivatableCard(0, 0, LOCATION_MZONE, 1, 1, 3, 0));
        prompt(s, "idle-actions", new DuelMessage.SelectIdleCmd(0, List.of(), List.of(),
                List.of(new DuelMessage.ReposCard(89631139, 0, LOCATION_MZONE, 0)),
                List.of(), List.of(), chains, true, true, true));
        s.click("#plr-mon-0 .card").ticks(2).checkCount(".ctx-action", 2);
        capture(s, "context-actions");
        s.step("choose Activate", ctx -> {
            var b = ctx.all(".ctx-action").getLast().bounds();
            ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        });
        capture(s, "idle-effect-options");
        s.checkCount("#prompt-dialog .prompt-btn", 2);
        prompt(s, "chain-optional", new DuelMessage.SelectChain(0, 0, false, 0, 0, chains));
        s.step("choose a card with two effects", ctx -> {
            var b = ctx.all("#prompt-body .card").getFirst().bounds();
            ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        });
        capture(s, "chain-effect-options");
        s.checkCount("#prompt-dialog .prompt-btn", 2);
        prompt(s, "chain-forced", new DuelMessage.SelectChain(0, 0, true, 0, 0, chains));
        s.checkCount("#prompt-buttons .prompt-btn", 0);
        prompt(s, "battle", new DuelMessage.SelectBattleCmd(0, chains, List.of(), true, true));
        s.checkVisible("#phase-btn-center");
        s.teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }
}

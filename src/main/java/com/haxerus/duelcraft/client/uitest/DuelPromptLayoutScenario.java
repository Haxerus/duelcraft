package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ErrorPolicy;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.stream.IntStream;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Dense and long-text prompts, independent of downloaded card names and images. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_prompt_layout", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPromptLayoutScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel", "visual-audit").onError(ErrorPolicy.CONTINUE);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("prompt layout", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI();
        prompt(s, "yes-no", new DuelMessage.SelectYesNo(0, 1));
        s.step("long question", ctx -> ctx.el("#prompt-title").as(Label.class).setText(Component.literal(
                "Would you like to activate this effect and Special Summon one monster from your Graveyard?")));
        capture(s, "long-question");
        prompt(s, "effect-yes-no", new DuelMessage.SelectEffectYn(0, 0, new LocInfo(0, LOCATION_MZONE, 0, 1), 1));
        prompt(s, "all-races", new DuelMessage.AnnounceRace(0, 2, (1L << 26) - 1));
        s.click("#announce-bit-0").ticks(2).checkTextContains("#prompt-body", "Select 1");
        capture(s, "race-selected");
        prompt(s, "all-attributes", new DuelMessage.AnnounceAttrib(0, 2, 0x7f));
        prompt(s, "many-numbers", new DuelMessage.AnnounceNumber(0, IntStream.rangeClosed(1, 40).mapToObj(i -> (long) i * 1000).toList()));
        prompt(s, "rock-paper-scissors", new DuelMessage.RockPaperScissors(0));
        prompt(s, "options", new DuelMessage.SelectOption(0, IntStream.rangeClosed(1, 20).mapToObj(i -> (long) i).toList()));
        s.step("long effect descriptions", ctx -> {
            int i = 1;
            for (var ref : ctx.all("#prompt-dialog .prompt-btn")) {
                ref.as(Button.class).setText(Component.literal("Effect " + i++
                        + ": Special Summon one monster from your Graveyard, then add one Spell or Trap Card from your Deck to your hand."));
            }
        });
        capture(s, "long-options");
        s.step("mark last effect", ctx -> ctx.all("#prompt-dialog .prompt-btn").getLast().element().setId("audit-last-option"))
         .hover(".prompt-option-scroller")
         .step("scroll to last effect", ctx -> {
             var b = ctx.el(".prompt-option-scroller").bounds();
             // LDLib2 consumes the sign, not the magnitude, of each wheel event.
             for (int i = 0; i < 200; i++) ctx.input().scroll(b.centerX(), b.centerY(), -1);
         }).ticks(2)
         .step("last effect is reachable", ctx -> DuelUiAssertions.contains(ctx,
                 ctx.el(".prompt-option-scroller .__scroller_view_view-port__").bounds(),
                 ctx.el("#audit-last-option").bounds(), "last option inside viewport"));
        capture(s, "last-option");
        DuelPanelLayoutScenario.press(s, "#audit-last-option");
        s.checkHidden("#prompt-overlay");
        prompt(s, "retry", new DuelMessage.Retry());
        s.checkVisible("#prompt-overlay").checkVisible("#status-label");
        prompt(s, "positions", new DuelMessage.SelectPosition(0, 0, 0xf));
        var cards = IntStream.range(0, 20).mapToObj(i -> new DuelMessage.CardInfo(89631139, 0, LOCATION_DECK, i, 1)).toList();
        prompt(s, "card-dialog", new DuelMessage.SelectCard(0, true, 1, 3, cards));
        s.step("mark first candidate", ctx -> ctx.all("#prompt-body .card").getFirst().element().setId("audit-first-card"))
         .hover("#audit-first-card").ticks(2)
         .step("long candidate description", ctx -> ctx.el("#card-text").as(TextElement.class).setText(Component.literal(
                 "Once per turn: You can target one monster; return it to its owner's hand.\n".repeat(30))))
         .ticks(2).step("remember candidate text offset", ctx -> ctx.put("candidateY", ctx.el("#card-text").bounds().y()))
         .scroll("#card-text-scroll", -1).ticks(15)
         .checkVisible("#card-info-banner")
         .check("candidate description scrolls through prompt overlay", ctx ->
                 ctx.el("#card-text").bounds().y() < (float) ctx.get("candidateY"));
        capture(s, "candidate-description");
        prompt(s, "unselect-dialog", new DuelMessage.SelectUnselectCard(0, true, true, 1, 3, cards, List.of()));
        var sums = IntStream.range(0, 12).mapToObj(i -> new DuelMessage.SumCard(0, 0, LOCATION_DECK, i, 1, 2)).toList();
        prompt(s, "sum-dialog", new DuelMessage.SelectSum(0, false, 6, 1, 3, List.of(sums.getFirst()), sums.subList(1, sums.size())));
        var sortable = IntStream.range(0, 20).mapToObj(i -> new DuelMessage.SortableCard(0, 0, LOCATION_DECK, i)).toList();
        prompt(s, "sort-chain", new DuelMessage.SortChain(0, sortable));
        prompt(s, "sort-card", new DuelMessage.SortCard(0, sortable));
        s.teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }

    static void prompt(ScenarioBuilder s, String name, DuelMessage message) {
        s.step(name, ctx -> LDLibDuelScreen.applyMessage(message));
        capture(s, name);
    }

    static void capture(ScenarioBuilder s, String name) {
        s.ticks(3).step("layout: " + name, DuelUiAssertions::audit).screenshot(name);
    }
}

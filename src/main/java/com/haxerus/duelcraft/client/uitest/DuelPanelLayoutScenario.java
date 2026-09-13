package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.stream.IntStream;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.capture;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_panel_layout", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPanelLayoutScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("panel layout", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("populate", ctx -> DuelScreenFixture.populate(DuelRule.MR5)).ticks(2)
         .hover("#plr-mon-0 .card").ticks(2)
         .step("long card metadata and description", ctx -> {
             ctx.el("#card-name-label").as(TextElement.class).setText(Component.literal(
                     "A very long monster name that must wrap inside the card information panel"));
             ctx.el("#card-stats-label").as(TextElement.class).setText(Component.literal(
                     "DARK / Level 12 / Dragon / Fusion / Pendulum / Effect"));
             ctx.el("#card-text").as(TextElement.class).setText(Component.literal(
                     "Once per turn: You can target one monster in your Graveyard; Special Summon it, then draw one card.\n".repeat(30)));
         });
        capture(s, "long-card-info");
        s.step("remember description offset", ctx -> ctx.put("textY", ctx.el("#card-text").bounds().y()))
         .scroll("#card-text-scroll", -10).ticks(2)
         .check("card description scrolls", ctx -> ctx.el("#card-text").bounds().y() < (float) ctx.get("textY"));
        capture(s, "card-info-scrolled");
        s.step("reveal sixty cards", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.ConfirmCards(0,
                IntStream.range(0, 60).mapToObj(i -> new DuelMessage.ConfirmCard(0, 0, LOCATION_DECK, i)).toList())));
        capture(s, "large-reveal");
        s.checkCount("#zone-inspector-list .card", 60)
         .step("remember inspector offset", ctx -> ctx.put("pileY", ctx.all("#zone-inspector-list .card").getFirst().bounds().y()))
         .scroll("#zone-inspector-list", -20).ticks(2)
         .check("large inspector scrolls", ctx -> ctx.all("#zone-inspector-list .card").getFirst().bounds().y() < (float) ctx.get("pileY"));
        capture(s, "reveal-scrolled");
        press(s, "#zone-inspector-close");
        s.checkHidden("#zone-inspector").click("#opp-graveyard").ticks(2);
        capture(s, "empty-graveyard");
        press(s, "#zone-inspector-close");
        s.click("#log-toggle").ticks(2).step("long log", ctx -> {
            var list = ctx.el("#duel-log-list").as(ScrollerView.class);
            list.clearAllScrollViewChildren();
            for (int i = 0; i < 60; i++) {
                var line = new Label();
                line.addClass("log-line");
                line.setText(Component.literal("Turn " + i + ": A monster activates its effect, targeting another monster on the field."));
                list.addScrollViewChild(line);
            }
        });
        capture(s, "long-log");
        s.scroll("#duel-log-list", -20);
        capture(s, "log-scrolled");
        press(s, "#duel-log-close");
        s.step("long message", ctx -> {
            LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_MESSAGE, 0, 1));
        }).ticks(2).step("message text", ctx -> ctx.el("#hint-modal-text").as(Label.class).setText(Component.literal(
                "Your opponent has activated a card effect. Select the cards you want to use, then confirm your selection to continue the duel.")));
        capture(s, "long-message");
        press(s, "#hint-modal-ok");
        s.step("draw result", ctx -> LDLibDuelScreen.showResult(2, 0));
        capture(s, "draw-result");
        s.step("loss result", ctx -> LDLibDuelScreen.showResult(1, 4));
        capture(s, "loss-result");
        s.teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }

    static void press(ScenarioBuilder s, String selector) {
        s.step("press " + selector, ctx -> {
            var b = ctx.el(selector).bounds();
            ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        }).ticks(2);
    }
}

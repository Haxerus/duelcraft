package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.capture;
import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.prompt;
import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Candidate source labels and iterative selection progress on field and dialog surfaces. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_selection_captions", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSelectionCaptionsScenario implements UIScenario {

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel", "visual-audit").guiScale(3);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel selection captions",
                        ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI()
         .step("populate field", ctx -> DuelScreenFixture.populate(DuelRule.MR5))
         .ticks(2);

        prompt(s, "field-single-zone", new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                card(LOCATION_MZONE, 0), card(LOCATION_MZONE, 1))));
        s.checkText("#status-source-title", "Monster Zone")
         .checkHidden("#prompt-overlay")
         .checkText("#status-label", "Select 1-2 card(s)");
        heading(s, "#status-source-title", "#status-label");
        DuelPanelLayoutScenario.press(s, "#plr-mon-0");
        s.checkClass("#plr-mon-0", "selected");

        prompt(s, "field-mixed-zones", new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                card(LOCATION_MZONE, 0), card(LOCATION_SZONE, 1))));
        s.checkTextContains("#status-source-title", "Monster Zone / Spell & Trap Zone");
        DuelPanelLayoutScenario.press(s, "#plr-mon-0");
        s.checkVisible("#prompt-action-btn").checkText("#prompt-action-btn", "Finish");

        s.step("active chain and phase feedback during selection", ctx -> {
            ctx.put("field-before-feedback", ctx.el("#field-area").bounds());
            ctx.put("actions-before-feedback", ctx.el("#prompt-action-bar").bounds());
            LDLibDuelScreen.applyMessage(new DuelMessage.NewPhase(PHASE_MAIN1));
            LDLibDuelScreen.applyMessage(new DuelMessage.Chaining(89631139,
                    new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK),
                    0, LOCATION_MZONE, 0, 1, 1));
        });
        capture(s, "field-source-with-chain-and-banner");
        s.checkVisible("#chain-count").checkVisible("#banner")
         .checkVisible("#prompt-action-btn").checkText("#prompt-action-btn", "Finish")
         .check("source heading stays below the chain count", ctx ->
                 ctx.el("#status-source-title").bounds().y() >= ctx.el("#chain-count").bounds().bottom())
         .check("chain count stays below the banner", ctx ->
                 ctx.el("#chain-count").bounds().y() >= ctx.el("#banner").bounds().bottom())
         .check("feedback stays below the field and hands", ctx ->
                 ctx.el("#banner").bounds().y() >= ctx.el("#field-area").bounds().bottom())
         .check("feedback preserves field bounds", ctx ->
                 sameBounds(ctx.el("#field-area").bounds(), ctx.get("field-before-feedback")))
         .check("feedback preserves action button placement", ctx ->
                 sameBounds(ctx.el("#prompt-action-bar").bounds(), ctx.get("actions-before-feedback")))
         .step("chain finished", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.ChainEnd()));

        prompt(s, "dialog-single-zone", new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                card(LOCATION_DECK, 0), card(LOCATION_DECK, 1))));
        s.checkText("#prompt-source-title", "Deck")
         .checkText("#prompt-title", "Select 1-2 card(s)");
        heading(s, "#prompt-source-title", "#prompt-title");

        prompt(s, "chain-single-zone", new DuelMessage.SelectChain(0, 0, false, 0, 0, List.of(
                new DuelMessage.ActivatableCard(89631139, 1, LOCATION_GRAVE, 0, 1, 1, 0))));
        s.checkTextContains("#prompt-source-title", "Opponent's GY")
         .checkCount("#prompt-body .card-source", 0)
         .checkCount("#prompt-body .card", 1);

        var sortable = List.of(
                new DuelMessage.SortableCard(89631139, 0, LOCATION_DECK, 0),
                new DuelMessage.SortableCard(33750025, 0, LOCATION_DECK, 1));
        prompt(s, "sort-card-single-zone", new DuelMessage.SortCard(0, sortable));
        s.checkTextContains("#prompt-source-title", "Deck")
         .checkCount("#prompt-body .card-source", 0)
         .checkCount("#prompt-body .card", 2);
        prompt(s, "sort-chain-single-zone", new DuelMessage.SortChain(0, sortable));
        s.checkTextContains("#prompt-source-title", "Deck")
         .checkCount("#prompt-body .card-source", 0)
         .checkCount("#prompt-body .card", 2);

        prompt(s, "dialog-mixed-zones", new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                card(LOCATION_HAND, 0), card(LOCATION_GRAVE, 0))));
        s.checkTextContains("#prompt-source-title", "Hand / GY")
         .checkText("#selection-source-0", "Hand")
         .checkText("#selection-source-1", "GY");

        prompt(s, "dialog-mixed-ownership", new DuelMessage.SelectCard(0, false, 1, 2, List.of(
                card(0, LOCATION_HAND, 0), card(1, LOCATION_SZONE, 0))));
        s.checkTextContains("#prompt-source-title", "Your Hand / Opponent's Spell & Trap Zone")
         .checkText("#selection-source-0", "Your Hand")
         .checkText("#selection-source-1", "Opponent's Spell & Trap Zone")
         .check("source labels use prompt body text size", ctx ->
                 ctx.el("#selection-source-1").as(TextElement.class).getTextStyle().fontSize() == 8)
         .step("source captions sit below intact card art", ctx -> {
             var choice = ctx.el("#selection-source-1").element().getParent();
             var card = choice.select(".card").findFirst().orElseThrow();
             var cardBounds = com.lowdragmc.lowdraglib2.uitest.ElementBounds.of(card);
             var labelBounds = ctx.el("#selection-source-1").bounds();
             float aspect = cardBounds.width() / cardBounds.height();
             ctx.check("card keeps its portrait aspect", Math.abs(aspect - .75f) < .02f,
                     .75f, aspect);
             ctx.check("source caption starts below the card", labelBounds.y() >= cardBounds.bottom() - 1,
                     cardBounds.toString(), labelBounds.toString());
             DuelUiAssertions.contains(ctx,
                     com.lowdragmc.lowdraglib2.uitest.ElementBounds.of(choice), labelBounds,
                     "long opponent source inside choice column");
         });

        var selectedMaterials = List.of(card(LOCATION_MZONE, 0), card(LOCATION_MZONE, 1));
        prompt(s, "partial-materials-field", new DuelMessage.SelectUnselectCard(
                0, true, false, 1, 1, List.of(card(LOCATION_MZONE, 2)), selectedMaterials));
        s.checkTextContains("#status-label", "2 selected")
         .check("field caption does not present toggle bounds as a total",
                 ctx -> !ctx.el("#status-label").text().contains("1-1"))
         .checkText("#prompt-action-btn", "Finish");
        capture(s, "partial-materials-field");

        prompt(s, "partial-materials-dialog", new DuelMessage.SelectUnselectCard(
                0, true, false, 1, 1,
                List.of(card(LOCATION_DECK, 0)), List.of(card(LOCATION_GRAVE, 0))));
        s.checkTextContains("#prompt-title", "1 selected")
         .checkTextContains("#prompt-source-title", "Deck / GY")
         .checkText("#unselect-source-0", "Deck")
         .checkText("#unselect-source-1", "GY")
         .check("dialog caption does not present toggle bounds as a total",
                 ctx -> !ctx.el("#prompt-title").text().contains("1-1"))
         .checkText("#prompt-dialog-action-btn", "Finish");
        capture(s, "partial-materials-dialog");

        prompt(s, "unrelated-prompt-clears-source", new DuelMessage.SelectYesNo(0, 1));
        s.checkHidden("#prompt-source-title").checkHidden("#status-source-title");

        s.teardown("close", ctx -> {
            LDLibDuelScreen.close();
            ctx.mc().setScreen(null);
        });
    }

    private static boolean sameBounds(ElementBounds actual, ElementBounds before) {
        return actual.x() == before.x() && actual.y() == before.y()
                && actual.width() == before.width() && actual.height() == before.height();
    }

    private static void heading(ScenarioBuilder s, String source, String detail) {
        s.check(source + " appears above the detailed prompt", ctx ->
                ctx.el(source).bounds().bottom() <= ctx.el(detail).bounds().y() + 1)
         .check(source + " is larger than the subtitle", ctx ->
                ctx.el(source).as(TextElement.class).getTextStyle().fontSize()
                        > ctx.el(detail).as(TextElement.class).getTextStyle().fontSize())
         .check(source + " uses a distinct gold color", ctx ->
                ctx.el(source).as(TextElement.class).getTextStyle().textColor() == 0xFFFFAA00
                        && ctx.el(detail).as(TextElement.class).getTextStyle().textColor() == 0xFFFFFFFF);
    }

    private static DuelMessage.CardInfo card(int location, int sequence) {
        return card(0, location, sequence);
    }

    private static DuelMessage.CardInfo card(int controller, int location, int sequence) {
        return new DuelMessage.CardInfo(89631139, controller, location, sequence, POS_FACEUP_ATTACK);
    }
}

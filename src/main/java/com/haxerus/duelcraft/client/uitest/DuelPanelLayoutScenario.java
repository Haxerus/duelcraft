package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.ClientDuelState;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


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
        s.step("populate sixty-card inspection pile", ctx -> inspectionPile(60)).click("#plr-graveyard");
        capture(s, "large-reveal");
        s.checkCount("#zone-inspector-list .card", 60)
         .step("reveal grid uses its full width evenly", ctx -> {
             var cards = ctx.all("#zone-inspector-list .card");
             var first = cards.getFirst().bounds();
             var row = cards.stream().map(ref -> ref.bounds())
                     .filter(b -> Math.abs(b.y() - first.y()) <= 1).toList();
             ctx.put("inspectorColumns", row.toArray(ElementBounds[]::new));
             var content = ElementBounds.of(
                     ctx.el("#zone-inspector-list").as(ScrollerView.class).viewContainer);
             float left = first.x() - content.x();
             float right = content.right() - row.getLast().right();
             ctx.check("reveal row has balanced edge space", Math.abs(left - right) <= 1, left, right);
             float gap = row.get(1).x() - first.right();
             for (int i = 2; i < row.size(); i++) {
                 float nextGap = row.get(i).x() - row.get(i - 1).right();
                 ctx.check("reveal card gaps are equal", Math.abs(gap - nextGap) <= 1, gap, nextGap);
             }
         })
         .step("remember inspector offset", ctx -> ctx.put("pileY", ctx.all("#zone-inspector-list .card").getFirst().bounds().y()))
         .scroll("#zone-inspector-list", -20).ticks(2)
         .check("large inspector scrolls", ctx -> ctx.all("#zone-inspector-list .card").getFirst().bounds().y() < (float) ctx.get("pileY"));
        capture(s, "reveal-scrolled");
        for (int count : new int[]{1, 2, 3, 5, 6}) {
            s.step("inspect " + count + " cards", ctx -> inspectionPile(count))
             .ticks(2).checkCount("#zone-inspector-list .card", count)
             .step("partial rows keep their columns", DuelPanelLayoutScenario::checkInspectorColumns);
            capture(s, "reveal-" + count + "-cards");
        }
        press(s, "#zone-inspector-close");
        s.checkHidden("#zone-inspector").click("#opp-graveyard").ticks(2);
        capture(s, "empty-graveyard");
        s.step("two cards enter the open graveyard", ctx -> {
            for (int i = 0; i < 2; i++) {
                LDLibDuelScreen.applyMessage(new DuelMessage.Move(89631139,
                        new LocInfo(1, LOCATION_HAND, 0, 0),
                        new LocInfo(1, LOCATION_GRAVE, i, POS_FACEUP_ATTACK), 0));
            }
        }).ticks(2).checkCount("#zone-inspector-list .card", 2)
         .step("sparse graveyard keeps its columns", DuelPanelLayoutScenario::checkInspectorColumns);
        capture(s, "two-card-graveyard");
        press(s, "#zone-inspector-close");
        s.step("active prompt, long player hints and real log contents", ctx -> {
            var state = DuelVisibilityFixture.state();
            state.hintText = new ClientDuelState.HintText() {
                @Override public String desc(long desc) {
                    return desc == 101
                            ? "Your cards are affected by a long standing effect that wraps beneath the local player name."
                            : "Opponent cards are affected by another long standing effect that wraps above the right panel column.";
                }
                @Override public String cardName(int code) { return "Card #" + code; }
                @Override public String systemString(int code) { return null; }
            };
            LDLibDuelScreen.applyMessage(new DuelMessage.PlayerHint(0, PHINT_DESC_ADD, 101));
            LDLibDuelScreen.applyMessage(new DuelMessage.PlayerHint(1, PHINT_DESC_ADD, 102));
            for (int i = 0; i < 60; i++) LDLibDuelScreen.applyMessage(new DuelMessage.Recover(0, i + 1));
            LDLibDuelScreen.applyMessage(new DuelMessage.Summoning(89631139,
                    new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK)));
            LDLibDuelScreen.applyMessage(new DuelMessage.SelectCard(0, true, 1, 3,
                    java.util.stream.IntStream.range(0, 20)
                            .mapToObj(i -> new DuelMessage.CardInfo(89631139, 0, LOCATION_DECK, i, POS_FACEDOWN_DEFENSE))
                            .toList()));
        }).ticks(3)
         .checkVisible("#prompt-overlay").checkVisible("#plr-hints").checkVisible("#opp-hints")
         .step("neither right panel", ctx -> {
             checkRightPanels(ctx, false, false);
             ctx.put("fieldBounds", ctx.el("#field-area").bounds());
             ctx.put("promptBounds", ctx.el("#prompt-dialog").bounds());
         });
        capture(s, "right-panels-neither-active-prompt");
        press(s, "#prompt-toggle-btn");
        s.checkHidden("#prompt-overlay")
         .check("prompt remains active while inspecting panels",
                 ctx -> DuelVisibilityFixture.state().pendingPrompt instanceof DuelMessage.SelectCard);

        s.step("show card details before opening log", ctx -> LDLibDuelScreen.applyMessage(
                new DuelMessage.Hint(HINT_CARD, 0, 89631139))).ticks(1)
         .checkVisible("#card-info-banner");
        press(s, "#log-toggle");
        s.checkVisible("#card-info-banner")
         .step("log-only fills right column", ctx -> checkRightPanels(ctx, false, true))
         .check("newly opened long log starts at bottom", ctx -> atBottom(ctx, "#duel-log-list"));
        capture(s, "right-panels-log-only");
        press(s, "#duel-log-close");
        s.step("return to neither", ctx -> checkRightPanels(ctx, false, false));

        s.step("restore sixty-card inspection pile", ctx -> inspectionPile(60)).click("#plr-graveyard").ticks(3)
         .step("zone-only fills right column", ctx -> checkRightPanels(ctx, true, false))
         .scroll("#zone-inspector-list", -20).ticks(2)
         .step("remember zone pixel offset", ctx -> {
             float offset = scrollOffset(ctx, "#zone-inspector-list");
             ctx.check("zone has a real content offset", offset > 0, "> 0", offset);
             ctx.put("zoneOffset", offset);
         });
        capture(s, "right-panels-zone-only-scrolled");

        press(s, "#log-toggle");
        s.step("both panels split the column", ctx -> {
             checkRightPanels(ctx, true, true);
             sameOffset(ctx, "#zone-inspector-list", "zoneOffset", "opening log preserves zone offset");
             ctx.check("toggling log retains inspected pile", ctx.all("#zone-inspector-list .card").size() == 60);
         });
        s.step("show details beside both panels", ctx -> LDLibDuelScreen.applyMessage(
                new DuelMessage.Hint(HINT_CARD, 0, 89631139))).ticks(1);
        press(s, "#prompt-toggle-btn");
        s.checkVisible("#prompt-overlay").checkVisible("#card-info-banner")
         .step("panels preserve board and prompt geometry", ctx -> {
             sameBounds(ctx, "#field-area", "fieldBounds", "field");
             sameBounds(ctx, "#prompt-dialog", "promptBounds", "prompt");
             checkRightPanels(ctx, true, true);
         });
        capture(s, "right-panels-both-active-prompt");
        press(s, "#prompt-toggle-btn");

        s.scroll("#duel-log-list", 20).ticks(2)
         .step("remember independent offsets", ctx -> {
             float offset = scrollOffset(ctx, "#duel-log-list");
             ctx.check("log is no longer at bottom", !atBottom(ctx, "#duel-log-list"));
             ctx.put("logOffset", offset);
             sameOffset(ctx, "#zone-inspector-list", "zoneOffset", "scrolling log does not move zone");
         });
        press(s, "#log-toggle");
        s.step("zone expands without losing its scroll", ctx -> {
             checkRightPanels(ctx, true, false);
             sameOffset(ctx, "#zone-inspector-list", "zoneOffset", "closing log preserves zone offset");
             ctx.check("closing log retains inspected pile", ctx.all("#zone-inspector-list .card").size() == 60);
         });
        press(s, "#log-toggle");
        s.step("log reopens at its prior offset", ctx -> {
             checkRightPanels(ctx, true, true);
             sameOffset(ctx, "#zone-inspector-list", "zoneOffset", "reopening log preserves zone offset");
             sameOffset(ctx, "#duel-log-list", "logOffset", "reopening log preserves log offset");
         })
         .step("append while log is away from bottom", ctx -> LDLibDuelScreen.applyMessage(
                 new DuelMessage.Summoning(33750025,
                         new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK))))
         .ticks(3)
         .step("non-bottom append preserves exact log offset", ctx -> {
             sameOffset(ctx, "#duel-log-list", "logOffset", "non-bottom append preserves log offset");
             ctx.check("non-bottom append stays away from bottom", !atBottom(ctx, "#duel-log-list"));
         })
         .step("scroll log to bottom", ctx -> ctx.el("#duel-log-list").as(ScrollerView.class)
                 .verticalScroller.setNormalizedValue(1)).ticks(2)
         .step("remember bottom before append", ctx -> {
             ctx.check("large downward scroll reaches bottom", atBottom(ctx, "#duel-log-list"));
             ctx.put("bottomOffset", scrollOffset(ctx, "#duel-log-list"));
         })
         .step("append while log is at bottom", ctx -> LDLibDuelScreen.applyMessage(
                 new DuelMessage.Summoning(28406301,
                         new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK))))
         .ticks(3)
         .check("bottom append follows the new last line", ctx -> atBottom(ctx, "#duel-log-list")
                 && scrollOffset(ctx, "#duel-log-list") > (float) ctx.get("bottomOffset"));
        s.step("click newest card log entry", ctx -> {
            var bounds = ctx.all("#duel-log-list .log-line").getLast().bounds();
            ctx.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
            ctx.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
        }).ticks(2).checkVisible("#duel-log").checkVisible("#zone-inspector")
         .checkVisible("#card-info-banner")
         .check("log entry replaces prior details", ctx -> !ctx.el("#card-name-label").text().equals(
                 "A very long monster name that must wrap inside the card information panel"));
        capture(s, "right-panels-log-click-keeps-all-details");

        press(s, "#zone-inspector-close");
        s.step("log-only after closing zone", ctx -> checkRightPanels(ctx, false, true));
        press(s, "#duel-log-close");
        s.step("neither panel after both close", ctx -> checkRightPanels(ctx, false, false));
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

    private static void checkInspectorColumns(TestContext ctx) {
        var columns = (ElementBounds[]) ctx.get("inspectorColumns");
        var cards = ctx.all("#zone-inspector-list .card");
        float firstY = cards.getFirst().bounds().y();
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i).bounds();
            var column = columns[i % columns.length];
            ctx.check("card " + i + " stays in its grid column", Math.abs(card.x() - column.x()) <= 1,
                    column.x(), card.x());
            ctx.check("card " + i + " preserves its size", Math.abs(card.width() - column.width()) <= 1
                    && Math.abs(card.height() - column.height()) <= 1);
            ctx.check("card " + i + " wraps to the expected row", i < columns.length
                    ? Math.abs(card.y() - firstY) <= 1 : card.y() >= firstY + column.height());
        }
    }

    private static void checkRightPanels(TestContext ctx, boolean zoneVisible, boolean logVisible) {
        var columns = ctx.all("#duel-right-column");
        ctx.check("duel has one right panel column", columns.size() == 1, 1, columns.size());
        if (columns.isEmpty()) return;
        var columnRef = columns.getFirst();
        ctx.check("right column visibility matches its children",
                columnRef.isVisible() == (zoneVisible || logVisible));
        ctx.check("zone visibility matches panel state", ctx.el("#zone-inspector").isVisible() == zoneVisible);
        ctx.check("log visibility matches panel state", ctx.el("#duel-log").isVisible() == logVisible);
        if (!zoneVisible && !logVisible) return;

        var column = columnRef.bounds();
        ctx.check("right column starts below opponent hints",
                column.y() >= ctx.el("#opp-hints").bounds().bottom() - 1);
        if (zoneVisible) DuelUiAssertions.contains(ctx, column, ctx.el("#zone-inspector").bounds(),
                "zone inspector inside right column");
        if (logVisible) DuelUiAssertions.contains(ctx, column, ctx.el("#duel-log").bounds(),
                "duel log inside right column");

        if (zoneVisible && logVisible) {
            var zone = ctx.el("#zone-inspector").bounds();
            var log = ctx.el("#duel-log").bounds();
            ctx.check("zone stays above log without sharing area", zone.bottom() <= log.y() + 1,
                    "zone bottom <= log top", zone.bottom() + " / " + log.y());
            ctx.check("right panels use an equal split", Math.abs(zone.height() - log.height()) <= 1,
                    zone.height(), log.height());
        } else {
            var panel = ctx.el(zoneVisible ? "#zone-inspector" : "#duel-log").bounds();
            ctx.check("single right panel uses full column height", Math.abs(panel.y() - column.y()) <= 1
                    && Math.abs(panel.height() - column.height()) <= 1, column.toString(), panel.toString());
        }
    }

    private static float scrollOffset(TestContext ctx, String selector) {
        var list = ctx.el(selector).as(ScrollerView.class);
        return -list.viewContainer.getTaffyLayout().location().y;
    }

    private static boolean atBottom(TestContext ctx, String selector) {
        var list = ctx.el(selector).as(ScrollerView.class);
        float range = Math.max(0, list.getContainerHeight() - list.viewPort.getContentHeight());
        return range - scrollOffset(ctx, selector) <= 1;
    }

    private static void sameOffset(TestContext ctx, String selector, String key, String description) {
        float expected = ctx.get(key);
        float actual = scrollOffset(ctx, selector);
        ctx.check(description, Math.abs(expected - actual) <= 1, expected, actual);
    }

    private static void sameBounds(TestContext ctx, String selector, String key, String description) {
        var expected = (ElementBounds) ctx.get(key);
        var actual = ctx.el(selector).bounds();
        ctx.check(description + " bounds unchanged", Math.abs(expected.x() - actual.x()) <= 1
                && Math.abs(expected.y() - actual.y()) <= 1
                && Math.abs(expected.width() - actual.width()) <= 1
                && Math.abs(expected.height() - actual.height()) <= 1, expected.toString(), actual.toString());
    }

    private static void inspectionPile(int count) {
        DuelVisibilityFixture.state().grave[0].clear();
        for (int i = 0; i < count; i++) LDLibDuelScreen.applyMessage(new DuelMessage.Move(0,
                new LocInfo(0, 0, 0, 0), new LocInfo(0, LOCATION_GRAVE, i, POS_FACEUP_ATTACK), 0));
    }

    static void press(ScenarioBuilder s, String selector) {
        s.step("press " + selector, ctx -> {
            var b = ctx.el(selector).bounds();
            ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        }).ticks(2);
    }
}

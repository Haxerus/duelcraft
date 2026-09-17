package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.ClientDuelState;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.RectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Standing effects share their owner's side column without covering LP changes or open panels. */
@LDLRegisterClient(name = "duel_player_hints", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPlayerHintsScenario implements UIScenario {
    private static final String FUSION = "Affected by Dracotail Lukias: You cannot Special Summon from the Extra Deck for the rest of this turn, except Fusion Monsters.";
    private static final String ATTACK = "Monsters you control gain 500 ATK. Monsters Special Summoned by this effect cannot attack directly, and their effects are negated until the End Phase.";
    private static final String DRAW = "During your next Draw Phase, draw 2 cards instead of 1. You cannot activate cards with the same name as a card drawn by this effect this turn.";

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit").guiScale(3); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("standing player hints", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("populate and install long effect descriptions", ctx -> {
             DuelScreenFixture.populate(DuelRule.MR5);
             activeState().hintText = new ClientDuelState.HintText() {
                 public String cardName(int code) { return "Fixture card"; }
                 public String systemString(int code) { return null; }
                 public String desc(long desc) { return desc == 101 ? FUSION : desc == 102 ? ATTACK : DRAW; }
             };
         }).ticks(2).checkHidden("#plr-hints").checkHidden("#opp-hints")
         .click("#plr-graveyard").ticks(2)
         .hover("#plr-mon-0 .card").ticks(2)
         .checkVisible("#zone-inspector").checkVisible("#card-info-banner")
         .step("save unoccupied panel and field bounds", ctx -> {
             ctx.put("field-before-hints", ctx.el("#field-area").bounds());
             ctx.put("controls-before-hints", ctx.el("#duel-controls").bounds());
             ctx.put("details-before-hints", ctx.el("#card-info-banner").bounds());
             ctx.put("inspector-before-hints", ctx.el("#zone-inspector").bounds());
             add(0, 101); add(0, 102); add(1, 102); add(1, 103);
             LDLibDuelScreen.applyMessage(new DuelMessage.Damage(0, 500));
             LDLibDuelScreen.applyMessage(new DuelMessage.Recover(1, 250));
         }).ticks(3).checkVisible("#plr-hints").checkVisible("#opp-hints")
         .checkText("#plr-hints", FUSION + "\n" + ATTACK)
         .checkText("#opp-hints", ATTACK + "\n" + DRAW)
         .checkVisible("#lp-delta-0").checkVisible("#lp-delta-1");
        readableHint(s, "#plr-hints", "#lp-delta-0", "#card-info-banner");
        readableHint(s, "#opp-hints", "#lp-delta-1", "#zone-inspector");
        s.check("standing effects preserve field geometry", ctx -> sameBounds(
                    ctx.el("#field-area").bounds(), ctx.get("field-before-hints")))
         .check("standing effects preserve control geometry", ctx -> sameBounds(
                    ctx.el("#duel-controls").bounds(), ctx.get("controls-before-hints")));
        DuelPromptLayoutScenario.capture(s, "long-standing-hints-and-details");
        DuelPanelLayoutScenario.press(s, "#log-toggle");
        s.checkVisible("#duel-log")
         .check("log starts below local standing effects", ctx ->
                 ctx.el("#duel-log").bounds().y() >= ctx.el("#plr-hints").bounds().bottom());
        DuelPromptLayoutScenario.capture(s, "long-standing-hints-and-log");
        s.step("clear local effects independently", ctx -> { remove(0, 101); remove(0, 102); })
         .ticks(3).checkHidden("#plr-hints").checkVisible("#opp-hints")
         .check("cleared local backing restores log height", ctx ->
                 ctx.el("#duel-log").bounds().y() == ((ElementBounds) ctx.get("details-before-hints")).y())
         .check("opponent effects still reserve inspector space", ctx ->
                 ctx.el("#zone-inspector").bounds().y() >= ctx.el("#opp-hints").bounds().bottom());
        DuelPromptLayoutScenario.capture(s, "local-standing-hints-cleared");
        s.step("clear remaining effects", ctx -> { remove(1, 102); remove(1, 103); })
         .ticks(3).checkHidden("#plr-hints").checkHidden("#opp-hints")
         .check("cleared opponent backing restores inspector height", ctx -> sameBounds(
                 ctx.el("#zone-inspector").bounds(), ctx.get("inspector-before-hints")));
        DuelPromptLayoutScenario.capture(s, "all-standing-hints-cleared");
        s.teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }

    private static void readableHint(ScenarioBuilder s, String hint, String lp, String panel) {
        s.check(hint + " uses readable wrapped adaptive text", ctx -> {
            var style = ctx.el(hint).as(TextElement.class).getTextStyle();
            return style.fontSize() == 8 && style.textWrap() == TextWrap.WRAP && style.adaptiveHeight();
        }).check(hint + " pads all text edges", ctx -> {
            var padding = ctx.el(hint).element().getTaffyLayout().padding();
            return padding.left >= 4 && padding.right >= 4 && padding.top >= 4 && padding.bottom >= 4;
        }).check(hint + " has a translucent dark backing", ctx -> {
            var background = ctx.el(hint).element().getStyle().backgroundTexture().getRawTexture();
            Integer color = switch (background) {
                case RectTexture rect -> rect.getColor();
                case ColorRectTexture rect -> rect.color;
                default -> null;
            };
            return color != null && (color >>> 24) >= 128 && (color >>> 24) < 255
                    && ((color >> 16) & 0xFF) <= 48 && ((color >> 8) & 0xFF) <= 48 && (color & 0xFF) <= 48;
        })
         .check(hint + " stays below its LP delta", ctx ->
                ctx.el(hint).bounds().y() >= ctx.el(lp).bounds().bottom())
         .check(panel + " starts below standing effects", ctx ->
                ctx.el(panel).bounds().y() >= ctx.el(hint).bounds().bottom());
    }

    private static boolean sameBounds(ElementBounds a, ElementBounds b) {
        return a.x() == b.x() && a.y() == b.y() && a.width() == b.width() && a.height() == b.height();
    }

    private static void add(int player, long desc) {
        LDLibDuelScreen.applyMessage(new DuelMessage.PlayerHint(player, PHINT_DESC_ADD, desc));
    }

    private static void remove(int player, long desc) {
        LDLibDuelScreen.applyMessage(new DuelMessage.PlayerHint(player, PHINT_DESC_REMOVE, desc));
    }

    private static ClientDuelState activeState() {
        try {
            var field = LDLibDuelScreen.class.getDeclaredField("activeState");
            field.setAccessible(true);
            return (ClientDuelState) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot install player-hint text fixture", e);
        }
    }
}

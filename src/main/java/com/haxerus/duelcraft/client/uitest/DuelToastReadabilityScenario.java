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
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.HINT_MESSAGE;
import static com.haxerus.duelcraft.client.uitest.DuelPanelLayoutScenario.press;

/** Real result events and timed queue, with readable placement around the duel's panels. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_toast_readability", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelToastReadabilityScenario implements UIScenario {
    private static final String COIN = "Coin landed on: [Heads][Tails][Heads]";
    private static final String DICE = "Die landed on: [1][6][3]";
    private static final String LONG = "Your opponent selected a card effect that adds one monster from the Deck "
            + "to the hand, then Special Summons a monster. Choose the cards you want to use before continuing the duel.";
    private static final String[] BOARD = {"#field-area", "#player-hand", "#opponent-hand", "#plr-mon-0", "#opp-mon-4"};

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("readable result toasts", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("synthetic field and deterministic result names", ctx -> {
             DuelScreenFixture.populate(DuelRule.MR5);
             state().hintText = new ClientDuelState.HintText() {
                 public String cardName(int code) { return "Fixture monster"; }
                 public String desc(long desc) { return "Continue the duel?"; }
                 public String systemString(int code) { return null; }
             };
         }).ticks(2).checkHidden("#toast")
         .step("remember underlying board geometry", ctx -> {
             for (String selector : BOARD) ctx.put(selector, ctx.el(selector).bounds());
         })
         .step("coin and dice results arrive together", ctx -> {
             LDLibDuelScreen.applyMessage(new DuelMessage.TossCoin(0, List.of(1, 0, 1)));
             LDLibDuelScreen.applyMessage(new DuelMessage.TossDice(1, List.of(1, 6, 3)));
         }).waitForText("#toast", COIN).checkVisible("#toast")
         .step("coin remains first while dice waits", ctx -> {
             ctx.put("coin-seen", System.currentTimeMillis());
             ctx.check("dice waits in the queue", List.copyOf(state().toasts).equals(List.of(DICE)));
         });
        capture(s, "coin-result", null, false);
        s.waitMs(500).checkText("#toast", COIN)
         .timeoutMs(4000).waitForText("#toast", DICE)
         .check("first toast kept its normal display duration", ctx ->
                 System.currentTimeMillis() - (long) ctx.get("coin-seen") >= 2000)
         .check("dice was consumed once", ctx -> state().toasts.isEmpty());
        capture(s, "dice-result", null, false);
        expire(s);

        s.hover("#plr-mon-0 .card").ticks(2).checkVisible("#card-info-banner")
         .click("#plr-graveyard").ticks(2).checkVisible("#zone-inspector")
         .hover("#card-info-banner");
        enqueueLong(s);
        s.checkVisible("#card-info-banner");
        capture(s, "long-toast-card-info-inspector", null, true);
        expire(s);
        s.click("#log-toggle").ticks(2).checkVisible("#duel-log").checkVisible("#zone-inspector");
        enqueueLong(s);
        capture(s, "long-toast-log-inspector", null, true);
        expire(s);

        s.step("choice dialog remains usable beside a toast", ctx ->
                LDLibDuelScreen.applyMessage(new DuelMessage.SelectYesNo(0, 1))).ticks(2)
         .checkVisible("#prompt-dialog");
        enqueueLong(s);
        capture(s, "toast-above-choice", "#prompt-dialog", true);
        expire(s);
        s.step("all races make a tall choice dialog", ctx ->
                LDLibDuelScreen.applyMessage(new DuelMessage.AnnounceRace(0, 2, (1L << 26) - 1))).ticks(2)
         .checkVisible("#prompt-dialog").checkVisible("#announce-bit-25");
        enqueueLong(s);
        capture(s, "toast-above-all-races", "#prompt-dialog", true);
        expire(s);
        press(s, "#prompt-toggle-btn");
        s.step("script message remains readable beside a toast", ctx ->
                LDLibDuelScreen.applyMessage(new DuelMessage.Hint(HINT_MESSAGE, 0, 1))).ticks(2)
         .checkVisible("#hint-modal-dialog");
        enqueueLong(s);
        capture(s, "toast-above-message", "#hint-modal-dialog", true);
        press(s, "#hint-modal-ok");
        s.checkHidden("#hint-modal").checkVisible("#toast");
        expire(s);
        s.teardown("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
    }

    private static void enqueueLong(ScenarioBuilder s) {
        s.step("queue long declaration text", ctx -> state().toasts.add(LONG))
         .waitForText("#toast", LONG).checkVisible("#toast").ticks(2);
    }

    private static void expire(ScenarioBuilder s) {
        s.timeoutMs(4000).waitUntil("toast expires without another event", ctx -> !ctx.el("#toast").isVisible())
         .check("expired queue is empty", ctx -> state().toasts.isEmpty());
    }

    private static void capture(ScenarioBuilder s, String name, String dialog, boolean wrapped) {
        s.step(name + " geometry and readability", ctx -> {
            var toast = ctx.el("#toast").as(TextElement.class);
            var bounds = ctx.el("#toast").bounds();
            var hitWithToast = ctx.requireUI().hitTestAtScreen(bounds.centerX(), bounds.centerY());
            boolean visible = toast.isVisible();
            try {
                toast.setVisible(false);
                var underlyingHit = ctx.requireUI().hitTestAtScreen(bounds.centerX(), bounds.centerY());
                ctx.check("toast lets pointer reach the same underlying element", underlyingHit != null
                        && hitWithToast == underlyingHit, underlyingHit, hitWithToast);
            } finally {
                toast.setVisible(visible);
            }
            var field = ctx.el("#field-area").bounds();
            float scale = ctx.el("#duel-canvas").bounds().width() / 960f;
            ctx.check("toast centered horizontally over the board", Math.abs(bounds.centerX() - field.centerX()) <= 1);
            if (dialog == null) {
                ctx.check("toast centered vertically over the board", Math.abs(bounds.centerY() - field.centerY()) <= 2);
            } else {
                var modal = ctx.el(dialog).bounds();
                ctx.check("toast sits just above the dialog", bounds.bottom() <= modal.y() - scale
                        && modal.y() - bounds.bottom() <= 16 * scale);
                noOverlap(ctx, bounds, modal, "toast leaves modal text and buttons unobscured");
            }
            ctx.check("toast text larger than old small footer", toast.getTextStyle().fontSize() >= 10);
            ctx.check("toast wraps long text", toast.getTextStyle().textWrap() == TextWrap.WRAP);
            var padding = toast.getTaffyLayout().padding();
            ctx.check("toast panel pads all text edges", padding.left >= 6 && padding.right >= 6
                    && padding.top >= 6 && padding.bottom >= 6);
            var background = toast.getStyle().backgroundTexture().getRawTexture();
            Integer color = switch (background) {
                case RectTexture rect -> rect.getColor();
                case ColorRectTexture rect -> rect.color;
                default -> null;
            };
            ctx.check("toast has translucent dark panel", color != null
                    && (color >>> 24) >= 128 && (color >>> 24) < 255
                    && ((color >> 16) & 0xFF) <= 48 && ((color >> 8) & 0xFF) <= 48 && (color & 0xFF) <= 48,
                    "alpha 128..254; each RGB channel <= 48", background.getClass().getSimpleName()
                            + " color=" + (color == null ? "unavailable" : String.format("#%08X", color)));
            if (wrapped) ctx.check("long message occupies multiple readable lines",
                    toast.getContentHeight() >= toast.getTextStyle().fontSize() * 2);
            for (String side : new String[]{"#duel-log", "#card-info-banner", "#zone-inspector", "#hud-bar"}) {
                if (ctx.el(side).isVisible()) noOverlap(ctx, bounds, ctx.el(side).bounds(), "toast clear of " + side);
            }
            for (String selector : BOARD) {
                var before = (ElementBounds) ctx.get(selector);
                var after = ctx.el(selector).bounds();
                ctx.check(selector + " unchanged by toast", Math.abs(before.x() - after.x()) <= 1
                        && Math.abs(before.y() - after.y()) <= 1 && Math.abs(before.width() - after.width()) <= 1
                        && Math.abs(before.height() - after.height()) <= 1);
            }
            DuelUiAssertions.audit(ctx);
        }).screenshot(name);
    }

    private static void noOverlap(TestContext ctx, ElementBounds a, ElementBounds b, String description) {
        ctx.check(description, a.right() <= b.x() + 1 || b.right() <= a.x() + 1
                || a.bottom() <= b.y() + 1 || b.bottom() <= a.y() + 1);
    }

    private static ClientDuelState state() {
        try {
            var field = LDLibDuelScreen.class.getDeclaredField("activeState");
            field.setAccessible(true);
            return (ClientDuelState) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access toast fixture state", e);
        }
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Both hands center when they fit and retain their entire scroll range when they overflow. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_hand_layout", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelHandLayoutScenario implements UIScenario {
    private static final String[] HANDS = {"#player-hand", "#opponent-hand"};

    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        for (var rule : new DuelRule[]{DuelRule.MR3, DuelRule.MR5, DuelRule.SPEED}) {
            s.openScreen("hands " + rule.id(), ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(rule)))
             .awaitModularUI().ticks(2);
            draw(s, 1);
            s.step("single cards centered", DuelHandLayoutScenario::centered)
             .step("remember card and field size", ctx -> {
                 ctx.put("card", ctx.el("#player-hand .card").bounds());
                 ctx.put("field", ctx.el("#field-area").bounds());
             })
             .screenshot(rule.id() + "-single-card");
            draw(s, 4);
            s.step("short hands centered", DuelHandLayoutScenario::centered)
             .screenshot(rule.id() + "-short-hands");
            draw(s, 15);
            s.step("overflow starts at the first card", ctx -> {
                for (String hand : HANDS) {
                    var cards = ctx.all(hand + " .card");
                    DuelUiAssertions.contains(ctx, viewport(ctx, hand), cards.getFirst().bounds(), hand + " first card visible");
                    ctx.check(hand + " overflows", cards.getLast().bounds().right() > viewport(ctx, hand).right());
                    var size = (ElementBounds) ctx.get("card");
                    ctx.check(hand + " cards retain size", Math.abs(cards.getFirst().bounds().width() - size.width()) <= 1);
                }
                var before = (ElementBounds) ctx.get("field");
                var after = ctx.el("#field-area").bounds();
                ctx.check("long hands do not grow the field", Math.abs(before.width() - after.width()) <= 1
                        && Math.abs(before.height() - after.height()) <= 1);
            }).screenshot(rule.id() + "-long-hands-start");
            scroll(s, -1);
            s.step("last cards reachable", ctx -> {
                for (String hand : HANDS) {
                    DuelUiAssertions.contains(ctx, viewport(ctx, hand), ctx.all(hand + " .card").getLast().bounds(), hand + " last card visible");
                    ctx.check(hand + " scroll moved", ctx.all(hand + " .card").getFirst().bounds().right() < viewport(ctx, hand).x());
                }
            }).screenshot(rule.id() + "-long-hands-end");
            scroll(s, 1);
            s.step("first cards reachable again", ctx -> {
                for (String hand : HANDS) DuelUiAssertions.contains(ctx, viewport(ctx, hand),
                        ctx.all(hand + " .card").getFirst().bounds(), hand + " first card after scrolling back");
            });
            scroll(s, -1);
            s.step("shrink scrolled hands", ctx -> {
                for (int player = 0; player < 2; player++) {
                    for (int i = 0; i < 19; i++) LDLibDuelScreen.applyMessage(new DuelMessage.Move(89631139,
                            new LocInfo(player, LOCATION_HAND, 0, 0),
                            new LocInfo(player, LOCATION_GRAVE, i, POS_FACEUP_ATTACK), 0));
                }
            }).ticks(3).step("remaining cards recentered", DuelHandLayoutScenario::centered)
             .screenshot(rule.id() + "-hand-shrunk")
             .step("close", ctx -> { LDLibDuelScreen.close(); ctx.mc().setScreen(null); });
        }
    }

    private static void draw(ScenarioBuilder s, int count) {
        s.step("draw " + count, ctx -> {
            for (int player = 0; player < 2; player++) LDLibDuelScreen.applyMessage(new DuelMessage.Draw(player,
                    Collections.nCopies(count, new DuelMessage.DrawnCard(89631139, POS_FACEDOWN_DEFENSE))));
        }).ticks(3);
    }

    private static void scroll(ScenarioBuilder s, int direction) {
        for (String hand : HANDS) {
            s.hover(hand).step("scroll " + hand, ctx -> {
                var port = viewport(ctx, hand);
                for (int i = 0; i < 200; i++) ctx.input().scroll(port.centerX(), port.centerY(), direction);
            }).ticks(2);
        }
    }

    private static void centered(TestContext ctx) {
        for (String hand : HANDS) {
            var cards = ctx.all(hand + " .card");
            var port = viewport(ctx, hand);
            float center = (cards.getFirst().bounds().x() + cards.getLast().bounds().right()) / 2;
            ctx.check(hand + " centered", Math.abs(center - port.centerX()) <= 1, port.centerX(), center);
            DuelUiAssertions.contains(ctx, port, cards.getFirst().bounds(), hand + " first card fits");
            DuelUiAssertions.contains(ctx, port, cards.getLast().bounds(), hand + " last card fits");
        }
    }

    private static ElementBounds viewport(TestContext ctx, String hand) {
        return ctx.el(hand + " .__scroller_view_view-port__").bounds();
    }
}

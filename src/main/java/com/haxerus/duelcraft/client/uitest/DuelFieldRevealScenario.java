package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;

import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Prompt-only identities must not disappear into the ordinary face-down field renderer. */
@LDLRegisterClient(name = "duel_field_reveal", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelFieldRevealScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.guiScale(2).tags("duel", "visibility"); }

    @Override public void define(ScenarioBuilder s) {
        s.step("deterministic artwork", ctx -> ctx.put("art", new DuelVisibilityFixture()))
         .openScreen("field reveal choices", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("capture validated responses", ctx -> ctx.<DuelVisibilityFixture>get("art").captureResponses());
        for (String mode : List.of("card", "unselect", "sum", "tribute")) {
            for (boolean batch : new boolean[]{true, false}) {
                s.step("unknown face-down opponents: " + mode + "/" + batch, ctx -> {
                    DuelVisibilityFixture.state().onResponseSent();
                    LDLibDuelScreen.applyMessage(new DuelMessage.Waiting());
                    for (int i = 0; i < 3; i++) {
                        LDLibDuelScreen.applyMessage(new DuelMessage.Move(0,
                                new LocInfo(1, LOCATION_MZONE, i, POS_FACEDOWN_DEFENSE),
                                new LocInfo(1, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK), 0));
                        LDLibDuelScreen.applyMessage(new DuelMessage.Move(0, new LocInfo(1, LOCATION_DECK, 0, 0),
                                new LocInfo(1, LOCATION_MZONE, i, POS_FACEDOWN_DEFENSE), 0));
                    }
                    ctx.<DuelVisibilityFixture>get("art").responses.clear();
                    LDLibDuelScreen.applyMessage(choice(mode));
                }).ticks(2).checkHidden("#prompt-overlay")
                 .step("reveal superseding a fresh choice", ctx -> {
                     DuelVisibilityFixture.state().onResponseSent();
                     LDLibDuelScreen.applyMessage(new DuelMessage.Waiting());
                     reveal();
                     if (batch) LDLibDuelScreen.applyMessage(choice(mode));
                 });
                if (!batch) s.ticks(2).checkVisible("#reveal-close")
                        .step("later choice", ctx -> LDLibDuelScreen.applyMessage(choice(mode)));
                s.ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector")
                 .checkCount("#prompt-body .card", 3)
                 .check("matched reveal consumed without response", ctx -> DuelVisibilityFixture.state().pendingReveals.isEmpty()
                         && ctx.<DuelVisibilityFixture>get("art").responses.isEmpty())
                 .step("artwork preserves original coordinates", ctx -> {
                     // Continue through every mode on RED without turning absent presentation into a fixture error.
                     if (!dialogVisible(ctx)) return;
                     for (int i = 0; i < 3; i++) DuelVisibilityFixture.artwork(ctx, i, i == 2 ? 0 : 99000001 + i);
                     DuelVisibilityFixture.pointer(ctx, 0, false);
                 }).ticks(2)
                 .check("first revealed hover", ctx -> !dialogVisible(ctx) || ctx.el("#card-name-label").text().equals("Card #99000001"))
                 .step("second revealed hover", ctx -> { if (dialogVisible(ctx)) DuelVisibilityFixture.pointer(ctx, 1, false); })
                 .ticks(2)
                 .check("second revealed hover name", ctx -> !dialogVisible(ctx) || ctx.el("#card-name-label").text().equals("Card #99000002"))
                 .step("unknown hover", ctx -> { if (dialogVisible(ctx)) DuelVisibilityFixture.pointer(ctx, 2, false); })
                 .waitMs(700).checkHidden("#card-info-banner").screenshot("field-" + mode + "-" + batch)
                 .step("choose original index one", ctx -> { if (dialogVisible(ctx)) DuelVisibilityFixture.pointer(ctx, 1, true); })
                 .ticks(2).step("finish weighted tribute", ctx -> {
                     if (mode.equals("tribute") && dialogVisible(ctx)) {
                         var b = ctx.el("#prompt-dialog-action-btn").bounds();
                         ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
                         ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
                     }
                 }).ticks(2)
                 .check("one validated response retains index one and weights", ctx -> {
                     var responses = ctx.<DuelVisibilityFixture>get("art").responses;
                     return responses.size() == 1 && HexFormat.of().formatHex(responses.getFirst())
                             .equals(mode.equals("unselect") ? "0100000001000000" : "000000000100000001000000");
                 }).step("ordinary field privacy after prompt", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Waiting()))
                 .ticks(2).checkCount("#opp-mon-0 .card-back", 1).checkCount("#opp-mon-1 .card-back", 1)
                 .step("ordinary opponent hover stays private", ctx -> {
                     var b = ctx.el("#opp-mon-1 .card-back").bounds();
                     ctx.input().moveTo(b.centerX(), b.centerY());
                 }).waitMs(700).checkHidden("#card-info-banner");
            }
        }
        s.teardown("restore fixture", ctx -> {
            LDLibDuelScreen.close(); ctx.mc().setScreen(null);
            DuelVisibilityFixture fixture = ctx.get("art"); if (fixture != null) fixture.close();
        });
    }

    private static boolean dialogVisible(TestContext ctx) {
        return !ctx.el("#prompt-overlay").element().hasClass("hidden");
    }

    private static void reveal() {
        LDLibDuelScreen.applyMessage(new DuelMessage.ConfirmCards(0, List.of(
                new DuelMessage.ConfirmCard(99000002, 1, LOCATION_MZONE, 1),
                new DuelMessage.ConfirmCard(99000001, 1, LOCATION_MZONE, 0))));
    }

    private static DuelMessage choice(String mode) {
        var cards = IntStream.range(0, 3).mapToObj(i ->
                new DuelMessage.CardInfo(0, 1, LOCATION_MZONE, i, POS_FACEDOWN_DEFENSE)).toList();
        return switch (mode) {
            case "card" -> new DuelMessage.SelectCard(0, false, 1, 1, cards);
            case "unselect" -> new DuelMessage.SelectUnselectCard(0, false, false, 1, 1,
                    cards.subList(0, 1), cards.subList(1, 3));
            case "sum" -> new DuelMessage.SelectSum(0, false, 2, 1, 1, List.of(), cards.stream().map(c ->
                    new DuelMessage.SumCard(0, 1, LOCATION_MZONE, c.sequence(), c.position(), c.sequence() == 1 ? 2 : 1)).toList());
            case "tribute" -> new DuelMessage.SelectTribute(0, false, 2, 2, cards.stream().map(c ->
                    new DuelMessage.TributeCard(0, 1, LOCATION_MZONE, c.sequence(), c.sequence() == 1 ? 2 : 1)).toList());
            default -> throw new IllegalArgumentException(mode);
        };
    }
}

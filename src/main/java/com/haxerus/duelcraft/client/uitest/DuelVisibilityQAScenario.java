package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;

import java.util.HexFormat;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static com.haxerus.duelcraft.client.uitest.DuelPanelLayoutScenario.press;

@LDLRegisterClient(name = "duel_visibility_qa", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelVisibilityQAScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.guiScale(2).tags("duel", "visibility"); }
    @Override public void define(ScenarioBuilder s) {
        s.step("deterministic local artwork", ctx -> ctx.put("art", new DuelVisibilityFixture()))
         .openScreen("reveal prompts", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("initialize cards and capture validated synthetic responses", ctx -> {
             DuelScreenFixture.populate(DuelRule.MR5);
             LDLibDuelScreen.applyMessage(new DuelMessage.ShuffleHand(1, java.util.Collections.nCopies(5, 0)));
             ctx.<DuelVisibilityFixture>get("art").captureResponses();
         }).ticks(3);
        for (int location : new int[]{LOCATION_HAND, LOCATION_DECK, LOCATION_EXTRA}) {
            for (boolean sameTick : new boolean[]{true, false}) {
                if (sameTick) s.step("one batch: reversed reveal and original-order choice " + location, ctx -> {
                    reveal(location);
                    LDLibDuelScreen.applyMessage(selection(location, 1));
                });
                else s.step("reversed reveal before choice " + location, ctx -> reveal(location))
                        .ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector")
                        .step("original-order zeroed choices", ctx -> LDLibDuelScreen.applyMessage(selection(location, 1)));
                s.ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector")
                 .checkCount("#prompt-body .card", 4)
                 .step("resolved artwork and unknown back", ctx -> {
                     for (int i = 0; i < 4; i++) DuelVisibilityFixture.artwork(ctx, i, i == 3 ? 0 : i == 2 ? 99000002 : 99000001);
                     DuelVisibilityFixture.pointer(ctx, 1, false);
                 }).ticks(3).checkText("#card-name-label", "Card #99000001")
                 .step("unknown candidate hover stays anonymous", ctx -> DuelVisibilityFixture.pointer(ctx, 3, false))
                 .waitMs(700).checkHidden("#card-info-banner")
                 .screenshot("choice-" + location + "-" + sameTick)
                 .step("choose non-first original index", ctx -> DuelVisibilityFixture.pointer(ctx, 1, true))
                 .ticks(2).check("validated response preserves index 1", ctx -> HexFormat.of().formatHex(
                         ctx.<DuelVisibilityFixture>get("art").responses.getLast()).equals("000000000100000001000000"));
            }
        }
        s.step("read-only events in one batch", ctx -> {
            LDLibDuelScreen.applyMessage(new DuelMessage.Waiting());
            reveal(LOCATION_DECK);
            reveal(LOCATION_EXTRA);
        }).ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector")
         .checkText("#prompt-title", "Opponent's Revealed Cards");
        press(s, "#reveal-close");
        s.checkVisible("#prompt-overlay");
        press(s, "#reveal-close");
        s.checkHidden("#prompt-overlay").click("#plr-graveyard").ticks(2)
         .checkCount("#zone-inspector-list .card", 1)
         .step("mandatory selection", ctx -> LDLibDuelScreen.applyMessage(selection(LOCATION_DECK, 2)))
         .ticks(2).step("retain first selected index", ctx -> DuelVisibilityFixture.pointer(ctx, 0, true))
         .step("unrelated reveal cannot replace pending choice", ctx -> reveal(LOCATION_EXTRA))
         .ticks(2).checkCount("#prompt-body .target", 1).checkCount("#zone-inspector-list .card", 1)
         .step("retry rebuilds the same mandatory choice", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Retry()))
         .ticks(2).checkCount("#prompt-body .card", 4)
         .step("retry selection completes normally", ctx -> DuelVisibilityFixture.pointer(ctx, 0, true))
         .step("retry second choice", ctx -> DuelVisibilityFixture.pointer(ctx, 1, true))
         .step("waiting releases queued read-only reveal", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.Waiting()))
         .ticks(2).checkVisible("#reveal-close").checkCount("#zone-inspector-list .card", 1);
        press(s, "#reveal-close");
        s.checkHidden("#prompt-overlay").checkCount("#zone-inspector-list .card", 1)
         .check("local acknowledgements send no engine response", ctx -> ctx.<DuelVisibilityFixture>get("art").responses.size() == 7)
         .step("mixed field and revealed Deck tribute", ctx -> LDLibDuelScreen.applyMessage(new DuelMessage.SelectTribute(0, false, 2, 2,
                 List.of(new DuelMessage.TributeCard(89631139, 0, LOCATION_MZONE, 0, 1),
                         new DuelMessage.TributeCard(0, 1, LOCATION_DECK, 2, 2)))))
         .ticks(3).checkVisible("#prompt-overlay").checkCount("#prompt-body .card", 2)
         .step("tribute uses recipient identity", ctx -> {
             DuelVisibilityFixture.artwork(ctx, 1, 99000002);
             DuelVisibilityFixture.pointer(ctx, 1, false);
         }).ticks(2).checkText("#card-name-label", "Card #99000002")
         .step("double-valued pile tribute", ctx -> DuelVisibilityFixture.pointer(ctx, 1, true))
         .ticks(2).checkText("#prompt-dialog-action-btn", "Finish");
        press(s, "#prompt-dialog-action-btn");
        s.check("tribute preserves weight and original response index", ctx -> HexFormat.of().formatHex(
                ctx.<DuelVisibilityFixture>get("art").responses.getLast()).equals("000000000100000001000000"))
         .teardown("restore fixture", ctx -> {
             LDLibDuelScreen.close(); ctx.mc().setScreen(null);
             DuelVisibilityFixture fixture = ctx.get("art"); if (fixture != null) fixture.close();
         });
    }
    private static void reveal(int location) {
        LDLibDuelScreen.applyMessage(new DuelMessage.ConfirmCards(0, List.of(
                new DuelMessage.ConfirmCard(99000002, 1, location, 2),
                new DuelMessage.ConfirmCard(99000001, 1, location, 1),
                new DuelMessage.ConfirmCard(99000001, 1, location, 0))));
    }
    private static DuelMessage.SelectCard selection(int location, int max) {
        return new DuelMessage.SelectCard(0, false, max, max, java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> new DuelMessage.CardInfo(0, 1, location, i, POS_FACEDOWN_DEFENSE)).toList());
    }
}

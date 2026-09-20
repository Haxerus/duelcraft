package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.MessageSanitizer;
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
import static com.haxerus.duelcraft.client.uitest.DuelPromptLayoutScenario.capture;

/** Revealed opponent hand candidates must provide artwork and hover details after code sanitization. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_revealed_hand", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelRevealedHandScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) { options.tags("duel", "visual-audit"); }

    @Override
    public void define(ScenarioBuilder s) {
        var choices = List.of(new DuelMessage.CardInfo(99000001, 1, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE),
                new DuelMessage.CardInfo(99000002, 1, LOCATION_HAND, 1, POS_FACEDOWN_DEFENSE));
        s.step("deterministic art", ctx -> ctx.put("art", new DuelVisibilityFixture()))
         .openScreen("revealed hand", ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(DuelRule.MR5)))
         .awaitModularUI().step("hidden opponent hand", ctx -> {
             DuelScreenFixture.populate(DuelRule.MR5);
             ctx.<DuelVisibilityFixture>get("art").captureResponses();
             LDLibDuelScreen.applyMessage(new DuelMessage.ShuffleHand(1, List.of(0,0,0)));
         }).ticks(2).step("reveal two cards, leaving one unknown", ctx ->
                LDLibDuelScreen.applyMessage(new DuelMessage.ConfirmCards(0, List.of(
                        new DuelMessage.ConfirmCard(99000002, 1, LOCATION_HAND, 1),
                        new DuelMessage.ConfirmCard(99000001, 1, LOCATION_HAND, 0)))))
         .ticks(3).step("subsequent sanitized choice", ctx -> LDLibDuelScreen.applyMessage(
                 MessageSanitizer.forRecipient(new DuelMessage.SelectCard(0, false, 1, 1, choices), 0)))
         .ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector").checkCount("#prompt-body .card", 2)
         .step("hover first candidate", ctx -> {
             DuelVisibilityFixture.artwork(ctx, 0, 99000001);
             DuelVisibilityFixture.artwork(ctx, 1, 99000002);
             var b = ctx.all("#prompt-body .card").getFirst().bounds();
             ctx.input().moveTo(b.centerX(), b.centerY());
         }).ticks(3).checkVisible("#card-info-banner").checkText("#card-name-label", "Card #99000001");
        capture(s, "revealed-hand-selection");
        s.step("hover second candidate", ctx -> {
            var b = ctx.all("#prompt-body .card").getLast().bounds();
            ctx.input().moveTo(b.centerX(), b.centerY());
        }).ticks(3).checkVisible("#card-info-banner").checkText("#card-name-label", "Card #99000002");
        capture(s, "revealed-hand-second-card");
        s.step("select original second index", ctx -> DuelVisibilityFixture.pointer(ctx, 1, true))
         .ticks(2).check("original index response", ctx -> java.util.HexFormat.of().formatHex(
                 ctx.<DuelVisibilityFixture>get("art").responses.getLast()).equals("000000000100000001000000"))
         .teardown("close", ctx -> {
             LDLibDuelScreen.close(); ctx.mc().setScreen(null);
             DuelVisibilityFixture art = ctx.get("art"); if (art != null) art.close();
         });
    }
}

package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.*;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.uitest.DuelVisibilityTrace;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.*;
import java.util.function.Consumer;
import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Dedicated scenario client: only received sanitized records are available here. */
public final class DuelVisibilityMPClient {
    private static DuelVisibilityTrace trace;
    private static Consumer<DuelMessage> previous;
    private static DuelVisibilityFixture art;
    private static String name;

    public static void begin(ScenarioBuilder s, String effect) {
        s.step("observe actual decoded packets", ctx -> {
            name = effect; trace = new DuelVisibilityTrace();
            art = new DuelVisibilityFixture();
            previous = ClientPayloadHandler.observeDuelMessages(trace::add);
        });
    }
    public static void finish(ScenarioBuilder s) {
        s.step("save sanitized receipt evidence and restore", ctx -> {
            try {
                var state = DuelVisibilityFixture.state();
                if (trace != null) trace.save(name + "-recipient-" + (state == null ? "unstarted" : state.localPlayer));
            } finally {
                if (previous != null) ClientPayloadHandler.observeDuelMessages(previous);
                previous = null; trace = null;
                if (art != null) art.close(); art = null;
                LDLibDuelScreen.close(); ctx.mc().setScreen(null);
            }
        });
    }
    public static void choose(ScenarioBuilder s, int location, int controller, int count, int... indices) {
        s.waitUntil("actual native choice received", ctx -> LDLibDuelScreen.isDuelLive()
                && DuelVisibilityFixture.state().pendingPrompt instanceof DuelMessage.SelectCard c
                && c.min() == count && c.cards().getFirst().location() == location
                && c.cards().getFirst().controller() == controller)
         .ticks(4).checkVisible("#prompt-overlay").checkHidden("#zone-inspector")
         .step("actual candidate identity and artwork", ctx -> {
             var state = DuelVisibilityFixture.state();
             var prompt = (DuelMessage.SelectCard) state.pendingPrompt;
             List<DuelMessage.ConfirmCard> revealed = trace.messages().stream().filter(DuelMessage.ConfirmCards.class::isInstance)
                     .map(DuelMessage.ConfirmCards.class::cast).flatMap(c -> c.cards().stream()).toList();
             for (int i = 0; i < prompt.cards().size(); i++) {
                 var c = prompt.cards().get(i);
                 int code = state.candidateCode(c);
                 ctx.check("candidate " + i + " known", code != 0);
                 if (controller != state.localPlayer) {
                     ctx.check("foreign candidate remains redacted", c.code() == 0);
                     ctx.check("recipient reveal authorizes exact coordinate", revealed.stream().anyMatch(r ->
                             r.controller() == c.controller() && r.location() == c.location()
                                     && r.sequence() == c.sequence() && r.code() == code));
                 }
                 DuelVisibilityFixture.artwork(ctx, i, code);
             }
             ctx.put("hoverCode", state.candidateCode(prompt.cards().get(indices[0])));
             DuelVisibilityFixture.pointer(ctx, indices[0], false);
         }).ticks(3).check("hover shares resolved artwork identity", ctx -> ctx.el("#card-name-label").text()
                 .equals(cardName(ctx.get("hoverCode"))))
         .screenshot("native-" + location + "-choices");
        for (int index : indices) s.step("pointer choose original index " + index,
                ctx -> DuelVisibilityFixture.pointer(ctx, index, true)).ticks(2);
    }
    public static void receipts(ScenarioBuilder s) {
        s.ticks(5).step("recipient-specific wire privacy", ctx -> {
            int local = DuelVisibilityFixture.state().localPlayer;
            for (var message : trace.messages()) {
                ctx.check("no Retry", !(message instanceof DuelMessage.Retry));
                if (message instanceof DuelMessage.SelectCard c) ctx.check("prompt only reaches responder", c.player() == local);
                if (message instanceof DuelMessage.ConfirmCards c && c.cards().stream().anyMatch(r ->
                        r.location() == LOCATION_DECK || r.location() == LOCATION_EXTRA))
                    ctx.check("private reveal reaches addressed player only", c.player() == local);
                if (message instanceof DuelMessage.Draw draw && draw.player() != local)
                    ctx.check("private opening draw is redacted", draw.cards().stream().allMatch(c -> c.code() == 0));
            }
            ctx.check("host Waiting actually received", trace.messages().stream().anyMatch(DuelMessage.Waiting.class::isInstance));
            if (name.equals("painful") && local == 1) ctx.check("unrelated opponent Deck remains anonymous",
                    DuelVisibilityFixture.state().deck[0].stream().anyMatch(c -> c.code == 0));
            if (name.equals("trident")) {
                var extra = DuelVisibilityFixture.state().extra[1];
                ctx.check("received Extra shuffle removes only opponent identities", extra.size() == 2
                        && extra.stream().allMatch(c -> c.code == (local == 0 ? 0 : 67598234)));
                ctx.check("actual Extra shuffle received", trace.messages().stream().anyMatch(DuelMessage.ShuffleExtra.class::isInstance));
            }
        });
    }
    public static void bellPublic(ScenarioBuilder s) {
        s.waitUntil("both public hands received", ctx -> LDLibDuelScreen.isDuelLive() &&
                Arrays.stream(DuelVisibilityFixture.state().hand).allMatch(hand -> !hand.isEmpty()
                        && hand.stream().allMatch(c -> c.isPublic && !c.isHidden && c.code != 0)))
         .ticks(4).step("both recipients receive public identity queries", ctx -> {
             for (int owner = 0; owner < 2; owner++) {
                 int p = owner;
                 ctx.check("public hand query owner " + p, trace.messages().stream().anyMatch(m ->
                         m instanceof DuelMessage.UpdateData q && q.player() == p && q.location() == LOCATION_HAND
                                 && q.cards().stream().filter(Objects::nonNull).anyMatch(c ->
                                 (c.flags & QUERY_IS_PUBLIC) != 0 && c.isPublic && (c.flags & QUERY_CODE) != 0 && c.code != 0)));
             }
             // On B, choose a stable A filler, not Raigeki. On A, show B's Blue-Eyes.
             var state = DuelVisibilityFixture.state();
             var hand = state.hand[state.opponent()];
             for (int i = 0; i < hand.size(); i++) {
                 var texture = ctx.all("#opponent-hand .card").get(i).element().getStyle().backgroundTexture().getRawTexture();
                 ctx.check("public opponent tile " + i + " uses authorized artwork", texture instanceof
                         com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture sprite
                         && sprite.getImageLocation().equals(DuelVisibilityFixture.texture(hand.get(i).code)));
             }
             int index = java.util.stream.IntStream.range(0, hand.size()).filter(i -> state.localPlayer == 1
                     ? hand.get(i).code != 12580477 : hand.get(i).code == 89631139).findFirst().orElseThrow();
             ctx.put("publicHoverCode", hand.get(index).code);
             var b = ctx.all("#opponent-hand .card").get(index).bounds();
             ctx.input().moveTo(b.centerX(), b.centerY());
         }).ticks(3).checkVisible("#card-info-banner")
         .check("public hover identity", ctx -> ctx.el("#card-name-label").text().equals(cardName(ctx.get("publicHoverCode"))))
         .screenshot("bell-public-hand");
    }
    public static void activateRaigeki(ScenarioBuilder s) {
        s.waitUntil("Raigeki idle", ctx -> DuelVisibilityFixture.state().pendingPrompt instanceof DuelMessage.SelectIdleCmd)
         .step("pointer opens Raigeki action", ctx -> {
             var state = DuelVisibilityFixture.state();
             int index = java.util.stream.IntStream.range(0, state.hand[0].size()).filter(i -> state.hand[0].get(i).code == 12580477).findFirst().orElseThrow();
             var actions = state.cardActions.get(new ClientDuelState.CardLocation(0, LOCATION_HAND, index));
             ctx.put("activateIndex", java.util.stream.IntStream.range(0, actions.size())
                     .filter(i -> actions.get(i).actionType() == IdleAction.ACTIVATE).findFirst().orElseThrow());
             var b = ctx.all("#player-hand .card").get(index).bounds();
             ctx.input().mouseDown(b.centerX(), b.centerY(), 0); ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
         }).ticks(2).step("pointer activates real spell", ctx -> {
             var b = ctx.all("#context-menu .ctx-action").get(ctx.<Integer>get("activateIndex")).bounds();
             ctx.input().mouseDown(b.centerX(), b.centerY(), 0); ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
         }).ticks(2);
    }
    public static void bellRevoked(ScenarioBuilder s) {
        s.waitUntil("public permission revoked on both hands", ctx -> Arrays.stream(DuelVisibilityFixture.state().hand)
                .allMatch(hand -> hand.stream().noneMatch(c -> c.isPublic))).ticks(4)
         .checkHidden("#card-info-banner").step("received revocation and negative shuffle", ctx -> {
             var state = DuelVisibilityFixture.state();
             ctx.check("opponent artwork permission gone", state.hand[state.opponent()].stream().allMatch(c -> state.handDisplayCode(c) == 0));
             ctx.check("owner retains codes", state.hand[state.localPlayer].stream().allMatch(c -> c.code != 0));
             ctx.check("real ShuffleHand received", trace.messages().stream().anyMatch(DuelMessage.ShuffleHand.class::isInstance));
             var messages = trace.messages();
             int removal = java.util.stream.IntStream.range(0, messages.size()).filter(i -> messages.get(i)
                     instanceof DuelMessage.Move m && m.code() == 20228463 && m.to().location() == LOCATION_GRAVE)
                     .findFirst().orElseThrow();
             for (int owner = 0; owner < 2; owner++) {
                 int p = owner;
                 ctx.check("actual post-removal public-off query owner " + p, messages.subList(removal + 1, messages.size()).stream().anyMatch(m ->
                         m instanceof DuelMessage.UpdateData q && q.player() == p && q.location() == LOCATION_HAND
                                 && q.cards().stream().filter(Objects::nonNull).anyMatch(c ->
                                 (c.flags & QUERY_IS_PUBLIC) != 0 && !c.isPublic)));
             }
         }).screenshot("bell-revoked-hand").hover("#hud-bar");
        for (int i = 0; i < 4; i++) {
            int index = i;
            s.step("new hover of hidden opponent card " + i, ctx -> {
                var b = ctx.all("#opponent-hand .card").get(index).bounds();
                ctx.input().moveTo(b.centerX(), b.centerY());
            }).ticks(3).checkHidden("#card-info-banner");
        }
    }

    private static String cardName(int code) {
        var db = com.haxerus.duelcraft.DuelcraftClient.getCardDatabase();
        var card = db == null ? null : db.getCard(code);
        return card == null ? "Card #" + code : card.name();
    }
}

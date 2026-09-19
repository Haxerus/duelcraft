package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.ClientPayloadHandler;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.server.collection.CollectionReplyPayload;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.neoforged.neoforge.client.ClientCommandHandler;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Client-only bodies are loaded only by MP client segments, never by dedicated scenario registration. */
public final class CollectionPrivacyClient {
    private CollectionPrivacyClient() {}
    @SuppressWarnings("unchecked")
    public static void install(TestContext ctx) {
        Consumer<CollectionReplyPayload> previous;
        try {
            var field = ClientPayloadHandler.class.getDeclaredField("collectionReceiver");
            field.setAccessible(true);
            previous = (Consumer<CollectionReplyPayload>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot capture collection receiver", exception);
        }
        ctx.put("previousReceiver", previous);
        var receipts = new ArrayList<CollectionReplyPayload>();
        ctx.put("receipts", receipts);
        ClientPayloadHandler.setCollectionReceiver(payload -> {
            receipts.add(payload); // Before CollectionClient discards unrecognized request IDs.
            previous.accept(payload);
        });
    }
    public static void restore(TestContext ctx) {
        Consumer<CollectionReplyPayload> previous = ctx.get("previousReceiver");
        if (previous != null) ClientPayloadHandler.setCollectionReceiver(previous);
    }
    private static List<CollectionReplyPayload> receipts(TestContext ctx) { return ctx.get("receipts"); }
    private static boolean snapshot(String role, boolean changed) {
        var expected = CollectionPrivacyScenario.expected(role, changed);
        var view = DuelcraftClient.getCollectionClient().state().view();
        return view != null && view.revision() == expected.revision() && view.counts().equals(expected.counts())
                && Objects.equals(view.activeId(), expected.activeDeckId())
                && view.summaries().size() == expected.decks().size()
                && view.summaries().stream().allMatch(summary -> {
                    var deck = expected.decks().get(summary.id());
                    return deck != null && summary.name().equals(deck.name()) && summary.main() == 40
                            && summary.extra() == 1 && summary.side() == 2;
                });
    }
    public static void open(ScenarioBuilder b, String role, boolean changed) {
        b.closeScreen().timeoutMs(60000).waitUntil("real catalog ready", ctx -> DuelcraftClient.getCollectionCatalog().toCompletableFuture().isDone())
         .step("open through registered authenticated command", ctx -> ctx.require("development command handled", ClientCommandHandler.runCommand("duel collection")))
         .waitUntil("real server-approved editor", ctx -> ctx.screen() instanceof CollectionScreen)
         .awaitModularUI().waitUntil("own complete private snapshot assembled", ctx -> snapshot(role, changed))
         .check("only own complete counts summaries revision and active UUID", ctx -> snapshot(role, changed))
         .waitUntil("editor applies complete snapshot", ctx -> ctx.el("#save-deck").isActive());
        CollectionRuntimeFixture.press(b, "#saved-lists");
        CollectionRuntimeFixture.select(b, ctx -> CollectionPrivacyScenario.id(role, changed));
        CollectionRuntimeFixture.discardNewDraft(b);
        b.waitForText("#editor-title", "Private same name")
         .check("own UUID ReadDeck loads exact ordered Main Extra Side", ctx -> CollectionRuntimeFixture.draftMatches(ctx, CollectionPrivacyScenario.cards()));
        if (changed) b.checkTextContains("#list-active-status", "is active");
        CollectionRuntimeFixture.press(b, "#lists-close");
        b.checkCount("#main-grid .card-tile", 40).checkCount("#extra-grid .card-tile", 1)
         .checkTextContains("#side-count", "2").ticks(3)
         .screenshot(role + (changed ? "-own-saved-active" : "-own-initial"));
    }
    private static void request(ScenarioBuilder b, String name, java.util.function.Function<TestContext, CollectionCommand> command) {
        b.step(name, ctx -> ctx.put("replyFuture", DuelcraftClient.getCollectionClient().request(command.apply(ctx)).toCompletableFuture()))
         .waitUntil(name + " actual reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("replyFuture").isDone());
    }
    private static CollectionReply reply(TestContext ctx) { return ctx.<CompletableFuture<CollectionReply>>get("replyFuture").join(); }
    public static void mutate(ScenarioBuilder b, String role) {
        long revision = CollectionPrivacyScenario.expected(role, false).revision();
        var deck = new SavedDeck(CollectionPrivacyScenario.id(role, true), "Private same name", CollectionPrivacyScenario.cards());
        request(b, "send own Save packet", ctx -> new CollectionCommand.Save(revision, deck));
        b.check("own Save acknowledgement exact UUID list and revision", ctx -> reply(ctx) instanceof CollectionReply.Changed changed
                && changed.revision() == revision + 1 && deck.equals(changed.saved()) && changed.activeId() == null);
        request(b, "send own Activate packet", ctx -> new CollectionCommand.Activate(revision + 1, deck.id()));
        b.check("own Activate acknowledgement UUID revision and eligibility", ctx -> reply(ctx) instanceof CollectionReply.Changed changed
                && changed.revision() == revision + 2 && deck.id().equals(changed.activeId()) && changed.eligibility().eligible());
    }
    public static void mark(ScenarioBuilder b) {
        b.step("record boundary receipt count", ctx -> ctx.put("receiptMark", receipts(ctx).size()));
    }
    public static void unchanged(ScenarioBuilder b, String role, boolean changed) {
        b.check("idle client received zero collection packets including foreign acknowledgements", ctx -> receipts(ctx).size() == ctx.<Integer>get("receiptMark"))
         .check("idle client complete private state unchanged", ctx -> snapshot(role, changed));
    }
    public static void rejectForeign(ScenarioBuilder b, String role) {
        String other = role.equals("A") ? "B" : "A";
        for (var kind : CollectionCommand.PageKind.values()) {
            request(b, "send foreign " + kind + " snapshot token", ctx -> new CollectionCommand.Page(UUID.fromString(ctx.get("foreignSnapshot")), kind, 0));
            b.check("foreign " + kind + " page rejected privately as STALE", ctx -> reply(ctx) instanceof CollectionReply.Rejected rejected && rejected.error() == CollectionError.STALE);
        }
        request(b, "send foreign saved UUID ReadDeck", ctx -> new CollectionCommand.ReadDeck(CollectionPrivacyScenario.id(other, true)));
        b.check("foreign saved UUID rejected privately as NOT_FOUND", ctx -> reply(ctx) instanceof CollectionReply.Rejected rejected && rejected.error() == CollectionError.NOT_FOUND)
         .check("foreign requests leave own full snapshot unchanged", ctx -> snapshot(role, true));
    }
    public static void evidence(ScenarioBuilder b) {
        b.step("record all real receive-boundary replies", ctx -> {
            String role = System.getProperty("ldlib2.mptest.role");
            var foreign = CollectionPrivacyScenario.expected(role.equals("A") ? "B" : "A", true);
            int foreignCode = role.equals("A") ? 99999991 : 99999990;
            ctx.check("no foreign sentinels UUIDs active IDs or revisions in any received packet", receipts(ctx).stream().allMatch(payload -> switch (payload.reply()) {
                case CollectionReply.Counts counts -> !counts.entries().containsKey(foreignCode) && counts.revision() != foreign.revision();
                case CollectionReply.Decks decks -> decks.entries().stream().noneMatch(summary -> foreign.decks().containsKey(summary.id()));
                case CollectionReply.Deck deck -> !foreign.decks().containsKey(deck.deck().id());
                case CollectionReply.Opened opened -> (opened.activeId() == null || !foreign.decks().containsKey(opened.activeId())) && opened.revision() != foreign.revision();
                case CollectionReply.Changed changed -> (changed.activeId() == null || !foreign.decks().containsKey(changed.activeId())) && (changed.saved() == null || !foreign.decks().containsKey(changed.saved().id())) && changed.revision() != foreign.revision();
                case CollectionReply.Rejected rejected -> rejected.revision() != foreign.revision();
            }));
            ctx.attach("receive-boundary", receipts(ctx).toString());
        });
    }
}

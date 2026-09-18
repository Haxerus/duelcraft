package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.collection.*;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.neoforged.neoforge.client.ClientCommandHandler;
import java.util.*;

/** Disposable integrated-server fixtures; all attachment access stays on the server thread. */
final class CollectionRuntimeFixture {
    static final UUID OWNED_ID = UUID.fromString("6c001000-0000-4000-8000-000000000001");
    static final UUID OTHER_ID = UUID.fromString("6c001000-0000-4000-8000-000000000002");
    private CollectionRuntimeFixture() {}

    static void capture(ServerContext sc) {
        sc.put("hadAttachment", sc.player().hasData(CollectionAttachments.COLLECTION));
        sc.put("original", sc.player().getData(CollectionAttachments.COLLECTION));
        sc.put("originalPlayer", sc.player());
        sc.put("playerUuid", sc.player().getUUID());
    }

    static void teardown(ScenarioBuilder s) {
        s.teardown("close real collection", ctx -> ctx.mc().setScreen(null))
         .teardownServer("restore attachment on current player", sc -> {
             CollectionAttachment original = sc.get("original");
             if (original != null) {
                 if (Boolean.TRUE.equals(sc.get("hadAttachment"))) {
                     sc.player().setData(CollectionAttachments.COLLECTION, original);
                     sc.check("original attachment restored on current player", sc.player().getData(CollectionAttachments.COLLECTION) == original);
                 } else {
                     sc.player().removeData(CollectionAttachments.COLLECTION);
                     sc.check("original attachment absence restored on current player", !sc.player().hasData(CollectionAttachments.COLLECTION));
                 }
             }
         });
    }

    static void open(ScenarioBuilder s) {
        s.step("registered development collection command", ctx ->
                ctx.require("collection command handled", ClientCommandHandler.runCommand("duel collection")))
         .waitUntil("server-approved collection screen", ctx -> ctx.screen() instanceof CollectionScreen)
         .awaitModularUI()
         .waitUntil("complete private snapshot applied", ctx -> ctx.el("#save-deck").isActive());
    }

    static void press(ScenarioBuilder s, String id) {
        s.step("press " + id, ctx -> CollectionLayoutScenario.press(ctx, id))
         .step("release " + id, CollectionLayoutScenario::release).ticks(2);
    }

    static void discardNewDraft(ScenarioBuilder s) {
        s.checkVisible("#close-dialog");
        press(s, "#close-discard");
    }

    static boolean draftMatches(TestContext ctx, DeckList expected) {
        Object controller = ctx.getField(ctx.screen(), "controller");
        com.haxerus.duelcraft.client.collection.DeckEditorModel model = ctx.getField(controller, "model");
        return expected.equals(model.draft()) && !model.dirty();
    }

    static void select(ScenarioBuilder s, java.util.function.Function<TestContext, UUID> target) {
        press(s, "#list-picker");
        s.step("identify saved UUID dropdown row", ctx -> {
            Map<UUID, com.lowdragmc.lowdraglib2.gui.ui.elements.Button> buttons =
                    ctx.getField(ctx.el("#list-picker").element(), "candidateButtons");
            var button = buttons.get(target.apply(ctx));
            ctx.require("saved UUID has a real dropdown row", button != null);
            button.setId("runtime-list-choice");
        });
        press(s, "#runtime-list-choice");
    }

    static PlayerCollectionData data(ServerContext sc) {
        return sc.player().getData(CollectionAttachments.COLLECTION).data().orElseThrow();
    }

    static boolean snapshotMatches(TestContext ctx) {
        PlayerCollectionData expected = ctx.get("expected");
        var view = DuelcraftClient.getCollectionClient().state().view();
        return view != null && expected.revision() == view.revision() && expected.counts().equals(view.counts())
                && Objects.equals(expected.activeDeckId(), view.activeId())
                && expected.decks().size() == view.summaries().size()
                && view.summaries().stream().allMatch(summary -> {
                    var deck = expected.decks().get(summary.id());
                    return deck != null && deck.name().equals(summary.name()) && deck.cards().main().size() == summary.main()
                            && deck.cards().extra().size() == summary.extra() && deck.cards().side().size() == summary.side();
                });
    }
}

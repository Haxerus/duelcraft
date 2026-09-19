package com.haxerus.duelcraft.client.uitest;

import com.google.gson.JsonObject;
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.uitest.CollectionPolicyTestRestriction;
import com.haxerus.duelcraft.uitest.CollectionPrivacyScenario;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Four real JVMs share only the isolated policy save and its server-confirmed manifest. */
@LDLRegisterClient(name = "collection_policy_lifecycle", group = "duelcraft_lifecycle", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionPolicyLifecycleScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.tags("lifecycle", "policy"); }

    @Override public void define(ScenarioBuilder s) {
        String stage = CollectionLifecycleLauncher.stage;
        if (stage == null || System.getProperty("duelcraft.uitest.collectionPolicyLifecycle") == null) {
            throw new IllegalStateException("Requires isolated policy launcher");
        }
        boolean ownership = !stage.equals("default");
        s.server("verify exact disk state and effective startup policy", sc -> {
            sc.check("effective ownership setting", ServerConfig.requireCardOwnership() == ownership);
            if (stage.equals("default")) {
                sc.check("new policy world is empty", CollectionRuntimeFixture.data(sc).equals(PlayerCollectionData.empty()));
                sc.put("saved", new SavedDeck(UUID.randomUUID(), "Policy retained all sections", CollectionPrivacyScenario.cards()));
            } else {
                var manifest = CollectionLifecycleLauncher.readManifest();
                var before = PlayerCollectionData.CODEC.parse(JsonOps.INSTANCE, manifest.get("collection")).getOrThrow();
                sc.check("entire disk-loaded attachment equals previous manifest", CollectionRuntimeFixture.data(sc).equals(before));
                sc.check("same authenticated owner", sc.player().getUUID().toString().equals(manifest.get("playerUuid").getAsString()));
                sc.check("new JVM PID", ProcessHandle.current().pid() != manifest.get("pid").getAsLong());
                sc.put("saved", before.decks().values().iterator().next());
            }
            sc.put("before", CollectionRuntimeFixture.data(sc));
        });
        if (stage.equals("default")) {
            request(s, "Save legal unowned list", ctx -> new CollectionCommand.Save(0, ctx.get("saved")));
            s.check("private Save exact list and revision", ctx -> reply(ctx) instanceof CollectionReply.Changed r
                    && r.revision() == 1 && r.saved().equals(ctx.get("saved")));
        }
        if (stage.equals("ownership")) {
            // A failed first Open must leave the prior active state recoverable and unchanged.
            install(s, true);
            request(s, "first Open with throwing companion", ctx -> new CollectionCommand.Open());
            s.check("failed Open private DATA_UNAVAILABLE", ctx -> reply(ctx) instanceof CollectionReply.Rejected r
                    && r.error() == CollectionError.DATA_UNAVAILABLE)
             .checkServer("failed Open leaves exact disk-loaded attachment", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("before")));
            remove(s);
        }
        if (stage.equals("restricted")) install(s, false);
        CollectionRuntimeFixture.open(s);
        s.server("verify first Open persistence and preserve counts and lists", sc -> {
            PlayerCollectionData before = sc.get("before");
            var actual = CollectionRuntimeFixture.data(sc);
            if (stage.equals("ownership") || stage.equals("restricted")) {
                sc.check("first Open clears only activation and increments once", actual.equals(
                        new PlayerCollectionData(before.revision() + 1, before.counts(), before.decks(), null)));
            } else if (stage.equals("removed")) {
                sc.check("removed restriction never reactivates", actual.equals(before) && actual.activeDeckId() == null);
            }
            sc.put("expected", actual);
        }).waitUntil("full private snapshot equals authority", CollectionRuntimeFixture::snapshotMatches)
         .check("snapshot policy and clearance report", ctx -> {
             var view = DuelcraftClient.getCollectionClient().state().view();
             var cleared = view.clearedActivation();
             return view.ownershipRequired() == ownership && switch (stage) {
                 case "ownership" -> cleared != null && cleared.ownershipRequired() && !cleared.missing().isEmpty();
                 case "restricted" -> cleared != null && CollectionPolicyTestRestriction.REASON.equals(cleared.restrictionReason());
                 default -> cleared == null;
             };
         }).step("attach first private Open snapshot", ctx -> ctx.attach("first-open-private-snapshot", DuelcraftClient.getCollectionClient().state().view().toString()));
        if (stage.equals("ownership") || stage.equals("restricted")) {
            s.checkTextContains("#editor-status", "previous active list")
             .hoverAt(-100, -100).screenshot("policy-" + stage + "-clearance");
            CollectionRuntimeFixture.press(s, "#eligibility-close");
        }
        load(s);
        s.hoverAt(-100, -100).screenshot("policy-" + stage + "-first-open");
        if (stage.equals("restricted")) remove(s);
        if (stage.equals("default") || stage.equals("ownership")) {
            if (ownership) {
                request(s, "Activate unowned under required ownership", ctx -> new CollectionCommand.Activate(
                        ctx.<PlayerCollectionData>get("expected").revision(), ctx.<SavedDeck>get("saved").id()));
                s.check("required ownership blocks unowned activation", ctx -> reply(ctx) instanceof CollectionReply.Rejected r
                        && r.error() == CollectionError.INELIGIBLE && r.eligibility().ownershipRequired() && !r.eligibility().missing().isEmpty())
                 .checkServer("denied unowned activation unchanged", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
                 .server("deposit sufficient test counts without implementing M3", sc -> {
                     var before = CollectionRuntimeFixture.data(sc);
                     var counts = new HashMap<Integer, Long>();
                     sc.<SavedDeck>get("saved").cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
                     var owned = new PlayerCollectionData(before.revision() + 1, counts, before.decks(), null);
                     sc.player().setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(owned));
                     sc.put("expected", owned);
                 }).closeScreen();
                CollectionRuntimeFixture.open(s);
                load(s);
            }
            s.click("#activate-deck")
             .waitUntilServer("real editor Activate accepted", sc -> sc.<SavedDeck>get("saved").id().equals(CollectionRuntimeFixture.data(sc).activeDeckId()))
             .server("activation increments once without consuming counts", sc -> {
                 PlayerCollectionData before = sc.get("expected");
                 sc.check("complete active state exact", CollectionRuntimeFixture.data(sc).equals(new PlayerCollectionData(
                         before.revision() + 1, before.counts(), before.decks(), sc.<SavedDeck>get("saved").id())));
                 sc.put("expected", CollectionRuntimeFixture.data(sc));
             }).waitUntil("private active snapshot exact", CollectionRuntimeFixture::snapshotMatches)
             .hoverAt(-100, -100).screenshot("policy-" + stage + "-active");
            request(s, "Clear active before companion attempts", ctx -> new CollectionCommand.ClearActive(ctx.<PlayerCollectionData>get("expected").revision()));
            s.check("clear accepted", ctx -> reply(ctx) instanceof CollectionReply.Changed r && r.activeId() == null)
             .serverGet("capture inactive authority", "expected", CollectionRuntimeFixture::data);
            for (boolean throwing : List.of(false, true)) {
                install(s, throwing);
                request(s, "Activate with " + (throwing ? "throwing" : "denying") + " companion", ctx -> new CollectionCommand.Activate(
                        ctx.<PlayerCollectionData>get("expected").revision(), ctx.<SavedDeck>get("saved").id()));
                s.check("companion private failure exact", ctx -> reply(ctx) instanceof CollectionReply.Rejected r
                        && r.error() == (throwing ? CollectionError.DATA_UNAVAILABLE : CollectionError.INELIGIBLE)
                        && r.eligibility().ownershipRequired() == ownership
                        && (throwing || CollectionPolicyTestRestriction.REASON.equals(r.eligibility().restrictionReason())))
                 .step("attach actual companion reply", ctx -> ctx.attach("companion-" + throwing, reply(ctx).toString()))
                 .checkServer("companion failure preserves entire attachment", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")));
                remove(s);
            }
            request(s, "Activate after exact listener removal", ctx -> new CollectionCommand.Activate(
                    ctx.<PlayerCollectionData>get("expected").revision(), ctx.<SavedDeck>get("saved").id()));
            s.check("removed listener permits activation", ctx -> reply(ctx) instanceof CollectionReply.Changed r && r.eligibility().eligible())
             .serverGet("capture retained active authority", "expected", CollectionRuntimeFixture::data);
        }
        // Another real Open proves revalidation does not produce a second write or auto-reactivation.
        s.closeScreen();
        CollectionRuntimeFixture.open(s);
        s.waitUntil("final full private snapshot exact", CollectionRuntimeFixture::snapshotMatches)
         .check("ordinary Open has no clearance report", ctx -> DuelcraftClient.getCollectionClient().state().view().clearedActivation() == null)
         .checkServer("second Open performs no mutation", sc -> CollectionRuntimeFixture.data(sc).equals(sc.<PlayerCollectionData>get("expected")))
         .server("write exact server-confirmed phase manifest", sc -> {
             var manifest = new JsonObject();
             manifest.addProperty("world", CollectionLifecycleLauncher.POLICY_WORLD);
             manifest.addProperty("phase", stage);
             manifest.addProperty("pid", ProcessHandle.current().pid());
             manifest.addProperty("ownershipRequired", ownership);
             manifest.addProperty("hookAtExit", "none");
             manifest.addProperty("playerUuid", sc.player().getUUID().toString());
             manifest.add("collection", PlayerCollectionData.CODEC.encodeStart(JsonOps.INSTANCE, CollectionRuntimeFixture.data(sc)).getOrThrow());
             CollectionLifecycleLauncher.writeManifest(manifest);
         }).step("attach exact phase state", ctx -> ctx.attach("phase-manifest", CollectionLifecycleLauncher.readManifest().toString()))
         .teardownServer("remove test companion even on scenario failure", CollectionPolicyLifecycleScenario::unregister)
         .teardown("close real editor", ctx -> ctx.mc().setScreen(null));
    }

    private static void load(ScenarioBuilder s) {
        CollectionRuntimeFixture.press(s, "#saved-lists");
        CollectionRuntimeFixture.select(s, ctx -> ctx.<SavedDeck>get("saved").id());
        CollectionRuntimeFixture.discardNewDraft(s);
        s.waitForText("#editor-title", "Policy retained all sections")
         .check("ReadDeck retains every ordered section", ctx -> CollectionRuntimeFixture.draftMatches(ctx, ctx.<SavedDeck>get("saved").cards()));
        CollectionRuntimeFixture.press(s, "#lists-close");
    }
    private static void install(ScenarioBuilder s, boolean throwing) {
        s.server("register explicit test companion " + throwing, sc -> {
            var listener = new CollectionPolicyTestRestriction(throwing);
            sc.put("listener", listener);
            NeoForge.EVENT_BUS.register(listener);
        });
    }
    private static void remove(ScenarioBuilder s) { s.server("unregister exact test companion", CollectionPolicyLifecycleScenario::unregister); }
    private static void unregister(ServerContext sc) {
        CollectionPolicyTestRestriction listener = sc.get("listener");
        if (listener != null) NeoForge.EVENT_BUS.unregister(listener);
    }
    private static void request(ScenarioBuilder s, String name, Function<TestContext, CollectionCommand> command) {
        s.step(name, ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(command.apply(ctx)).toCompletableFuture()))
         .waitUntil(name + " private reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone());
    }
    private static CollectionReply reply(TestContext ctx) { return ctx.<CompletableFuture<CollectionReply>>get("reply").join(); }
}

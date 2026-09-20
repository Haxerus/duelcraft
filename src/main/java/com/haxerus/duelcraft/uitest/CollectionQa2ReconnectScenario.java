package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.uitest.CollectionDuelPolicyClient;
import com.haxerus.duelcraft.client.uitest.CollectionDuelPolicyScenario;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.server.DuelManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import java.util.*;

/** Runs alone: the final assertion deliberately disconnects a suspended editor. */
@LDLRegister(name = "collection_qa2_reconnect", group = "duelcraft_disconnect", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionQa2ReconnectScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("collection", "preparation"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("seed disposable eligible lists and record server PID", sc -> {
            sc.check("requested effective ownership", ServerConfig.requireCardOwnership() == System.getProperty("duelcraft.uitest.mpOwnership", "default").equals("required"));
            sc.put("serverPid", ProcessHandle.current().pid());
            for (String role : List.of("A", "B")) {
                var saved = new SavedDeck(UUID.randomUUID(), "QA2 reconnect", CollectionPrivacyScenario.cards());
                var counts = new HashMap<Integer, Long>();
                if (ServerConfig.requireCardOwnership()) saved.cards().requiredCopies().forEach((code, n) -> counts.put(code, n.longValue()));
                sc.player(role).setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(saved.id(), saved), saved.id())));
            }
        }).fetchOn("A", "serverPid", "actual server PID", Long.class, sc -> sc.get("serverPid"))
          .client("A", "archive server PID", b -> b.step("server PID", ctx -> ctx.attach("server-pid", ctx.get("serverPid").toString())))
          .allClients("open dirty editor and record client PID", b -> {
            CollectionDuelPolicyClient.openDirty(b);
            b.step("record client PID", ctx -> ctx.attach("client-pid", Long.toString(ProcessHandle.current().pid())));
        });
        accept(s);
        s.allClients("accepted invite suspends exact dirty editor", b -> b.waitUntil("editor suspended", ctx -> !(ctx.screen() instanceof com.haxerus.duelcraft.client.collection.CollectionScreen)))
         .server("cancel accepted flow", sc -> {
             var prep = DuelManager.get().preparation(); var a = sc.player("A").getUUID();
             sc.check("cancel succeeds", prep.cancel(a, prep.view(a, System.currentTimeMillis()).flowId(), System.currentTimeMillis()).code() == PreparationResult.OK);
         }).allClients("cancel restores dirty draft", b -> b.waitUntil("same editor draft restored", CollectionDuelPolicyScenario::retained)
                 .check("same unsaved ID name cards and controller", CollectionDuelPolicyScenario::retained).screenshot("qa2-dirty-restored"));
        accept(s);
        s.allClients("second acceptance suspends again", b -> b.waitUntil("editor suspended again", ctx -> !(ctx.screen() instanceof com.haxerus.duelcraft.client.collection.CollectionScreen)))
         .client("B", "real disconnect disposes suspended controller", b -> {
             CollectionDuelPolicyClient.disconnect(b);
             b.check("suspended saved-deck controller disposed", ctx -> ctx.<Boolean>getField(ctx.get("lists"), "disposed"));
         }).client("A", "opponent disconnect restores survivor draft", b -> b.waitUntil("survivor original editor", CollectionDuelPolicyScenario::retained))
         .server("disconnect releases survivor lock", sc -> sc.check("survivor editable", !DuelManager.get().isBusy(sc.player("A"))))
         .teardownAllClients("close surviving fixture screen", b -> b.closeScreen());
    }
    private static void accept(MPScenarioBuilder s) {
        s.server("invite and accept current flow", sc -> {
            var prep = DuelManager.get().preparation(); var a = sc.player("A").getUUID(); var b = sc.player("B").getUUID(); long now = System.currentTimeMillis();
            sc.check("invite", prep.invite(a, b, DuelRule.MR5, 42L, PlayerOptions.standard(), now).code() == PreparationResult.OK);
            sc.check("accept", prep.accept(b, prep.view(b, now).flowId(), now).code() == PreparationResult.OK);
        });
    }
}

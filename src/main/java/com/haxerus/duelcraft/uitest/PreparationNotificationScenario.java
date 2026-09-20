package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.uitest.PreparationNotificationClient;
import com.haxerus.duelcraft.collection.CollectionAttachment;
import com.haxerus.duelcraft.collection.CollectionAttachments;
import com.haxerus.duelcraft.collection.PlayerCollectionData;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.server.DevNativeStartCounter;
import com.haxerus.duelcraft.server.DuelManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.mp.MPScenario;
import com.lowdragmc.lowdraglib2.uitest.mp.MPScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.mp.MPScenarioOptions;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Real two-client coverage for semantic preparation announcements and flow cardinality. */
@LDLRegister(name = "duel_preparation_notifications", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class PreparationNotificationScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) {
        options.clients("A", "B").tags("duel", "preparation", "notifications", "native");
    }

    @Override public void define(MPScenarioBuilder s) {
        s.server("seed legal saved lists for current ownership mode", sc -> {
            CollectionPrivacyScenario.verifyWireNegotiation(sc);
            for (String role : List.of("A", "B")) {
                var player = sc.player(role);
                sc.put("original" + role, player.getData(CollectionAttachments.COLLECTION));
                var deck = new SavedDeck(UUID.randomUUID(), "B1 " + role, CollectionPrivacyScenario.cards());
                var counts = new HashMap<Integer, Long>();
                if (ServerConfig.requireCardOwnership()) {
                    deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
                }
                player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(
                        new PlayerCollectionData(20, counts, Map.of(deck.id(), deck), deck.id())));
                sc.check("deposited exact copies " + role,
                        !counts.isEmpty() == ServerConfig.requireCardOwnership());
            }
        }).allClients("start fresh chat capture", b -> PreparationNotificationClient.clearChat(b))
          .client("A", "challenge A to B", b -> b.step("send challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 4242")))
          .serverWaitUntil("initial invitation delivered", sc -> view(sc.player("B")).mode() == PreparationView.Mode.INVITED)
          .client("A", "repeat A to B challenge", b -> b.step("repeat challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 4242")))
          .client("B", "crossed B to A challenge", b -> b.step("send crossed challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestA mr5 4242")))
          .serverWaitUntil("one invitation remains authoritative", sc -> view(sc.player("B")).mode() == PreparationView.Mode.INVITED)
          .server("capture sole invitation", sc -> {
              var a = view(sc.player("A"));
              var b = view(sc.player("B"));
              sc.put("flow1", a.flowId());
              sc.check("one shared flow after repeated and crossed commands",
                      a.flowId() != null && a.flowId().equals(b.flowId()));
          })
          .sync("A invitation view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B invitation view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("A", "outgoing invitation prompts", b -> PreparationNotificationClient.checkPrompts(b, "invited-A", 0, 0, 0))
          .client("B", "incoming invitation prompts", b -> PreparationNotificationClient.checkPrompts(b, "invited-B", 1, 0, 0))
          .client("B", "accept invitation", b -> b.step("accept", ctx -> ctx.mc().player.connection.sendCommand("duel accept")))
          .serverWaitUntil("first RPS round exists", sc -> view(sc.player("A")).mode() == PreparationView.Mode.RPS)
          .server("capture first round", sc -> {
              var a = view(sc.player("A"));
              var b = view(sc.player("B"));
              sc.put("round1", a.roundId());
              sc.check("accepted flow unchanged", a.flowId().equals(sc.get("flow1")) && a.flowId().equals(b.flowId()));
              sc.check("first round shared", a.roundId() != null && a.roundId().equals(b.roundId()));
          })
          .sync("A accepted view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B accepted view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("A", "first-round prompt A", b -> PreparationNotificationClient.checkPrompts(b, "round1-A", 0, 1, 0))
          .client("B", "first-round prompt B", b -> PreparationNotificationClient.checkPrompts(b, "round1-B", 1, 1, 0))
          .client("A", "submit first hand", b -> b.step("rock", ctx -> ctx.mc().player.connection.sendCommand("duel hand rock")))
          .serverWaitUntil("only A hand is recorded", sc -> view(sc.player("A")).ownHandSubmitted()
                  && !view(sc.player("B")).ownHandSubmitted())
          .sync("A private submitted view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B private waiting view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("B", "waiting player is not reprompted", b -> PreparationNotificationClient.checkPrompts(b, "one-hand-B", 1, 1, 0))
          .client("B", "tie first round", b -> b.step("rock", ctx -> ctx.mc().player.connection.sendCommand("duel hand rock")))
          .serverWaitUntil("tie creates a fresh round", sc -> view(sc.player("A")).mode() == PreparationView.Mode.RPS
                  && view(sc.player("A")).roundId() != null && !view(sc.player("A")).roundId().equals(sc.get("round1")))
          .server("capture tie replay", sc -> {
              var a = view(sc.player("A"));
              var b = view(sc.player("B"));
              sc.put("round2", a.roundId());
              sc.check("tie retains one flow", a.flowId().equals(sc.get("flow1")) && a.flowId().equals(b.flowId()));
              sc.check("tie clears both private hands", !a.ownHandSubmitted() && !b.ownHandSubmitted());
          })
          .sync("A tie replay view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B tie replay view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("A", "second-round prompt A", b -> PreparationNotificationClient.checkPrompts(b, "round2-A", 0, 2, 0))
          .client("B", "second-round prompt B", b -> PreparationNotificationClient.checkPrompts(b, "round2-B", 1, 2, 0))
          .client("A", "submit second-round hand", b -> b.step("rock", ctx -> ctx.mc().player.connection.sendCommand("duel hand rock")))
          .serverWaitUntil("second-round A hand recorded", sc -> view(sc.player("A")).ownHandSubmitted()
                  && !view(sc.player("B")).ownHandSubmitted())
          .sync("A second-round submitted view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B second-round waiting view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("B", "second-round waiting player is not reprompted", b -> PreparationNotificationClient.checkPrompts(b, "one-hand-round2-B", 1, 2, 0))
          .client("B", "lose second round", b -> b.step("scissors", ctx -> ctx.mc().player.connection.sendCommand("duel hand scissors")))
          .serverWaitUntil("A alone may choose first", sc -> view(sc.player("A")).canChooseFirst()
                  && !view(sc.player("B")).canChooseFirst())
          .sync("A first-choice view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B first-choice view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("A", "winner prompt A", b -> PreparationNotificationClient.checkPrompts(b, "winner-A", 0, 2, 1))
          .client("B", "winner prompt B", b -> PreparationNotificationClient.checkPrompts(b, "winner-B", 1, 2, 0))
          .server("arm exact native setup counter", sc -> sc.put("nativeStarts", DevNativeStartCounter.countNext(DuelManager.get())))
          .client("A", "choose first", b -> b.step("first yes", ctx -> ctx.mc().player.connection.sendCommand("duel first yes")))
          .serverWaitUntil("both players share one native session", sc -> DuelManager.get().getPlayerActiveDuel(sc.player("A")) != null
                  && DuelManager.get().getPlayerActiveDuel(sc.player("A")).equals(DuelManager.get().getPlayerActiveDuel(sc.player("B"))))
          .server("assert one exact native setup", sc -> {
              int starts = sc.<AtomicInteger>get("nativeStarts").get();
              sc.check("one native setupDuel call", starts == 1, 1, starts);
              sc.log("native setupDuel calls=" + starts + ", shared session=" + DuelManager.get().getPlayerActiveDuel(sc.player("A")));
          })
          .client("A", "finish completed flow", b -> b.step("forfeit", ctx -> ctx.mc().player.connection.sendCommand("duel forfeit")))
          .serverWaitUntil("completed flow releases both players", sc -> !DuelManager.get().isBusy(sc.player("A"))
                  && !DuelManager.get().isBusy(sc.player("B")))
          .client("A", "create cancellable flow", b -> b.step("challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 5252")))
          .serverWaitUntil("cancellable invitation delivered", sc -> view(sc.player("B")).mode() == PreparationView.Mode.INVITED)
          .client("A", "cancel invitation", b -> b.step("cancel", ctx -> ctx.mc().player.connection.sendCommand("duel invite cancel")))
          .serverWaitUntil("cancellation releases both players", sc -> view(sc.player("A")).mode() == PreparationView.Mode.IDLE
                  && view(sc.player("B")).mode() == PreparationView.Mode.IDLE)
          .sync("A cancellation state", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B cancellation state", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .server("announcement keys clear on cancellation", sc -> sc.check("no retained announcement keys",
                  sc.<Map<?, ?>>getField(DuelManager.get(), "announcements").isEmpty()))
          .client("A", "retry after cancellation", b -> b.step("challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 6262")))
          .serverWaitUntil("retry invitation delivered", sc -> view(sc.player("B")).mode() == PreparationView.Mode.INVITED)
          .client("B", "retry invitation prompt", b -> PreparationNotificationClient.checkPrompts(b, "retry-after-cancel-B", 3, 2, 0))
          .server("expire retry without sleeping", sc -> {
              sc.put("expiredFlow", view(sc.player("A")).flowId());
              DuelManager.get().preparation().expire(System.currentTimeMillis() + 60_000);
          })
          .serverWaitUntil("expiry releases both players", sc -> view(sc.player("A")).mode() == PreparationView.Mode.IDLE
                  && view(sc.player("B")).mode() == PreparationView.Mode.IDLE)
          .sync("A expiry state", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B expiry state", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .server("announcement keys clear on expiry", sc -> sc.check("no retained announcement keys",
                  sc.<Map<?, ?>>getField(DuelManager.get(), "announcements").isEmpty()))
          .client("A", "retry after expiry", b -> b.step("challenge", ctx ->
                  ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 7272")))
          .serverWaitUntil("post-expiry invitation delivered", sc -> view(sc.player("B")).mode() == PreparationView.Mode.INVITED
                  && !view(sc.player("B")).flowId().equals(sc.get("expiredFlow")))
          .client("B", "post-expiry invitation prompt", b -> PreparationNotificationClient.checkPrompts(b, "retry-after-expiry-B", 4, 2, 0))
          .client("B", "accept disconnect flow", b -> b.step("accept", ctx -> ctx.mc().player.connection.sendCommand("duel accept")))
          .serverWaitUntil("disconnect flow reaches RPS", sc -> view(sc.player("A")).mode() == PreparationView.Mode.RPS)
          .sync("A disconnect-flow view", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .sync("B disconnect-flow view", "B", sc -> signature(view(sc.player("B"))), ctx -> PreparationNotificationClient.viewSignature())
          .client("A", "third RPS prompt A", b -> PreparationNotificationClient.checkPrompts(b, "disconnect-flow-A", 0, 3, 1))
          .client("B", "third RPS prompt B", b -> PreparationNotificationClient.checkPrompts(b, "disconnect-flow-B", 4, 3, 0))
          .server("restore departing player fixture", sc -> restore(sc, "B"))
          .client("B", "disconnect during RPS", b -> PreparationNotificationClient.disconnect(b))
          .serverWaitUntil("disconnect releases survivor", sc -> !DuelManager.get().isBusy(sc.player("A")))
          .sync("A disconnect state", "A", sc -> signature(view(sc.player("A"))), ctx -> PreparationNotificationClient.viewSignature())
          .server("disconnect clears all announcement keys", sc -> sc.check("no retained announcement keys",
                  sc.<Map<?, ?>>getField(DuelManager.get(), "announcements").isEmpty()))
          .teardownAllClients("close fixture screens", b -> b.closeScreen())
          .teardownServer("restore attached fixtures", sc -> {
              for (String role : List.of("A", "B")) {
                  var player = sc.get("player" + role);
                  if (player instanceof ServerPlayer serverPlayer) {
                      DuelManager.get().forfeit(serverPlayer);
                      restore(sc, role);
                  }
              }
          });
    }

    private static PreparationView view(ServerPlayer player) {
        return DuelManager.get().preparation().view(player.getUUID(), System.currentTimeMillis());
    }

    private static String signature(PreparationView view) {
        return view.mode() + "|" + view.flowId() + "|" + view.roundId() + "|"
                + view.ownHandSubmitted() + "|" + view.canChooseFirst();
    }

    private static void restore(com.lowdragmc.lowdraglib2.uitest.ServerContext sc, String role) {
        ServerPlayer player = sc.get("player" + role);
        CollectionAttachment original = sc.get("original" + role);
        if (player != null && original != null) player.setData(CollectionAttachments.COLLECTION, original);
    }
}

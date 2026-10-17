package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.server.DuelManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import java.util.*;

/** Registered text commands, real dedicated connections, and native setup with an empty default collection. */
@LDLRegister(name = "duel_preparation_commands", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelPreparationCommandScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("duel", "preparation", "network"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("seed legal saved lists for current ownership mode", sc -> {
            CollectionPrivacyScenario.verifyWireNegotiation(sc);
            for (String role : List.of("A", "B")) {
                var player = sc.player(role); sc.put("original" + role, player.getData(CollectionAttachments.COLLECTION));
                var deck = new SavedDeck(UUID.randomUUID(), "M4 " + role, CollectionPrivacyScenario.cards());
                var counts = new HashMap<Integer, Long>();
                if (ServerConfig.requireCardOwnership()) deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
                player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(deck.id(), deck), deck.id())));
                sc.check("configured deposited counts", !counts.isEmpty() == ServerConfig.requireCardOwnership());
            }
        }).client("A", "registered challenge command", b -> b.step("challenge B", ctx -> ctx.mc().player.connection.sendCommand("duel challenge LDTestB mr5 4242")))
          .serverWaitUntil("invitation delivered", sc -> DuelManager.get().preparation().view(sc.player("B").getUUID(), System.currentTimeMillis()).mode() == PreparationView.Mode.INVITED)
          .client("B", "registered accept command", b -> b.step("accept A", ctx -> ctx.mc().player.connection.sendCommand("duel accept")))
          .serverWaitUntil("both players locked in RPS", sc -> DuelManager.get().isBusy(sc.player("A")) && DuelManager.get().isBusy(sc.player("B")))
          .client("A", "registered hand command A", b -> b.step("rock", ctx -> ctx.mc().player.connection.sendCommand("duel hand rock")))
          .client("B", "registered hand command B", b -> b.step("scissors", ctx -> ctx.mc().player.connection.sendCommand("duel hand scissors")))
          .serverWaitUntil("A chooses first", sc -> DuelManager.get().preparation().view(sc.player("A").getUUID(), System.currentTimeMillis()).canChooseFirst())
          .client("A", "registered first command", b -> b.step("give B first turn", ctx -> ctx.mc().player.connection.sendCommand("duel first no")))
          .serverWaitUntil("both share a native session", sc -> DuelManager.get().getPlayerActiveDuel(sc.player("A")) != null
                  && DuelManager.get().getPlayerActiveDuel(sc.player("A")).equals(DuelManager.get().getPlayerActiveDuel(sc.player("B"))))
          .allClients("real start screen and current protocol", b -> b.waitUntil("duel screen open", ctx -> ctx.mc().screen instanceof DuelScreen)
                  .waitUntil("DUEL state received", ctx -> DuelcraftClient.preparation().view() != null && DuelcraftClient.preparation().view().mode() == PreparationView.Mode.DUEL)
                  .screenshot("command-duel-start"))
          .client("A", "registered forfeit command", b -> b.step("forfeit", ctx -> ctx.mc().player.connection.sendCommand("duel forfeit")))
          .serverWaitUntil("live and preparation locks released", sc -> !DuelManager.get().isBusy(sc.player("A")) && !DuelManager.get().isBusy(sc.player("B")))
          .server("persistent selection survives normal finish", sc -> {
              for (String role : List.of("A", "B")) sc.check("active list preserved " + role,
                      sc.player(role).getData(CollectionAttachments.COLLECTION).data().orElseThrow().activeDeckId() != null);
          }).teardownAllClients("close fixture screen", b -> b.closeScreen())
          .teardownServer("restore attachments", sc -> {
              for (String role : List.of("A", "B")) {
                  DuelManager.get().forfeit(sc.player(role));
                  CollectionAttachment original = sc.get("original" + role);
                  if (original != null) sc.player(role).setData(CollectionAttachments.COLLECTION, original);
              }
          });
    }
}

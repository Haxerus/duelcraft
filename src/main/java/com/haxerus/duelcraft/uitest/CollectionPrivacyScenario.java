package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.client.uitest.CollectionPrivacyClient;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.server.DuelManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import java.util.*;

/** Real dedicated-server management packets, with observation before client reply filtering. */
@LDLRegister(name = "collection_privacy", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionPrivacyScenario implements MPScenario {
    public static UUID id(String role, boolean saved) {
        return UUID.fromString("6c00" + (role.equals("A") ? "1000" : "2000") + "-0000-4000-8000-00000000000" + (saved ? "2" : "1"));
    }
    public static DeckList cards() {
        return new DeckList(List.of(483,2511,10000,27551,32864,35699,39015,41546,41777,44818,
                50755,56889,59080,62121,64865,64866,96540,98905,102380,111280,114932,122520,
                123709,126218,128454,131182,132308,135598,164710,168917,176392,191749,197042,
                209710,212652,213326,218704,220414,242146,255998), List.of(43227), List.of(259314,263926));
    }
    public static PlayerCollectionData expected(String role, boolean changed) {
        var counts = new HashMap<Integer, Long>();
        cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
        counts.put(role.equals("A") ? 99999990 : 99999991, role.equals("A") ? 1000L : 2000L);
        var initial = new SavedDeck(id(role, false), "Private same name", cards());
        var saved = new SavedDeck(id(role, true), "Private same name", cards());
        return new PlayerCollectionData((role.equals("A") ? 10 : 20) + (changed ? 2 : 0), counts,
                changed ? Map.of(initial.id(), initial, saved.id(), saved) : Map.of(initial.id(), initial),
                changed ? saved.id() : null);
    }
    private static PlayerCollectionData data(ServerContext sc, String role) {
        return sc.player(role).getData(CollectionAttachments.COLLECTION).data().orElseThrow();
    }
    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("collection", "privacy", "network"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("capture and seed distinct private attachments", sc -> {
            sc.check("two distinct dedicated players", sc.players().size() == 2 && sc.player("A") != sc.player("B"));
            for (var role : List.of("A", "B")) {
                var player = sc.player(role);
                sc.put("had" + role, player.hasData(CollectionAttachments.COLLECTION));
                sc.put("original" + role, player.getData(CollectionAttachments.COLLECTION));
                player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(expected(role, false)));
            }
        }).allClients("install receive-boundary observers", b -> b.step("wrap existing collection receiver", CollectionPrivacyClient::install));
        // B opens first, then observes A's complete journey. Reverse direction follows.
        for (var role : List.of("B", "A")) {
            s.client(role, "open own complete private editor", b -> CollectionPrivacyClient.open(b, role, false));
        }
        for (var role : List.of("A", "B")) {
            String other = role.equals("A") ? "B" : "A";
            s.client(other, "mark idle receive boundary", CollectionPrivacyClient::mark)
             .client(role, "save and activate authenticated own list", b -> CollectionPrivacyClient.mutate(b, role))
             .serverWaitUntil("sender save and activation persisted", sc -> data(sc, role).equals(expected(role, true)))
             .server("only sender attachment changes", sc -> {
                 sc.check("sender complete counts lists active ID and revision", data(sc, role).equals(expected(role, true)));
                 sc.check("other complete attachment unchanged", data(sc, other).equals(expected(other, role.equals("B"))));
             }).serverSettle(10)
             .client(other, "no foreign packets or state reached idle client", b -> CollectionPrivacyClient.unchanged(b, other, role.equals("B")))
             .client(role, "refresh own acknowledged collection", b -> CollectionPrivacyClient.open(b, role, true));
        }
        for (var role : List.of("A", "B")) {
            String other = role.equals("A") ? "B" : "A";
            // Transfer only an adversarial snapshot capability, never counts/list contents/reply expectations.
            s.fetchOn(role, "foreignSnapshot", "supply foreign snapshot token for unauthorized request", String.class, sc -> {
                Object store = sc.getField(DuelManager.get().collectionHandler(), "snapshots");
                Map<UUID, ?> snapshots = sc.getField(store, "snapshots");
                UUID token = sc.getField(snapshots.get(sc.player(other).getUUID()), "id");
                return token.toString();
            }).client(other, "mark boundary before foreign requests", CollectionPrivacyClient::mark)
             .client(role, "reject foreign snapshot and saved UUID", b -> CollectionPrivacyClient.rejectForeign(b, role))
             .serverSettle(10)
             .client(other, "no foreign rejection acknowledgements arrive", b -> CollectionPrivacyClient.unchanged(b, other, true));
        }
        s.server("reads and rejected requests preserve both complete collections", sc -> {
            for (var role : List.of("A", "B")) sc.check(role + " complete final state", data(sc, role).equals(expected(role, true)));
        }).allClients("record own private boundary evidence", CollectionPrivacyClient::evidence)
         .teardownAllClients("restore exact receiver and close editor", b -> b.step("restore receiver", CollectionPrivacyClient::restore).closeScreen())
         .teardownServer("restore both original attachments", sc -> {
             for (var role : List.of("A", "B")) {
                 CollectionAttachment original = sc.get("original" + role);
                 if (original == null) continue;
                 var player = sc.player(role);
                 if (Boolean.TRUE.equals(sc.get("had" + role))) player.setData(CollectionAttachments.COLLECTION, original);
                 else player.removeData(CollectionAttachments.COLLECTION);
                 sc.check(role + " original attachment restored", Boolean.TRUE.equals(sc.get("had" + role))
                         ? player.getData(CollectionAttachments.COLLECTION) == original : !player.hasData(CollectionAttachments.COLLECTION));
             }
         });
    }
}

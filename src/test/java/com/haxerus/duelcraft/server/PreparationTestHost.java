package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.PlayerCollectionData;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.server.collection.CollectionService;
import com.haxerus.duelcraft.server.collection.CollectionTestData;
import com.haxerus.duelcraft.server.collection.DeckUsePolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PreparationTestHost implements DuelPreparationService.Host {
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();
    final UUID outsider = UUID.randomUUID();
    final Set<UUID> online = new HashSet<>(Set.of(alice, bob, outsider));
    final Set<UUID> dueling = new HashSet<>();
    final Map<UUID, PlayerCollectionData> collections = new HashMap<>();
    final Map<UUID, DuelPreparationService.Selection> selections = new HashMap<>();
    final List<DuelPreparationService.PreparedDuel> starts = new ArrayList<>();
    final Map<UUID, List<PreparationResult>> notifications = new HashMap<>();
    CollectionService collectionService;
    DuelPreparationService service;
    boolean failStart;
    boolean throwStart;
    boolean completeImmediately;
    Runnable duringStart = () -> {};
    Runnable duringChanged = () -> {};
    int firstSeat = -1;

    PreparationTestHost() {
        this(false, null);
    }

    PreparationTestHost(boolean ownershipRequired, DeckUsePolicy.Restriction restriction) {
        collectionService = new CollectionService(CollectionTestData.facts(),
                new DeckUsePolicy(ownershipRequired, restriction));
        for (var id : online) {
            var deck = CollectionTestData.deck(UUID.randomUUID());
            collections.put(id, new PlayerCollectionData(0, CollectionTestData.owned(),
                    Map.of(deck.id(), deck), deck.id()));
        }
    }

    SavedDeck deck(UUID owner) {
        var data = collections.get(owner);
        return data.decks().get(data.activeDeckId());
    }

    @Override
    public boolean online(UUID id) { return online.contains(id); }

    @Override
    public String name(UUID id) { return id.equals(alice) ? "Alice" : id.equals(bob) ? "Bob" : "Other"; }

    @Override
    public boolean isDueling(UUID id) { return dueling.contains(id); }

    @Override
    public DuelPreparationService.Selection selection(UUID id, DuelRule rule) {
        if (selections.containsKey(id)) return selections.get(id);
        var data = collections.get(id);
        var deck = data.activeDeckId() == null ? null : data.decks().get(data.activeDeckId());
        return new DuelPreparationService.Selection(deck, deck == null ? collectionService.emptyReport()
                : collectionService.check(id, deck.cards(), data.counts(), rule));
    }

    @Override
    public boolean start(DuelPreparationService.PreparedDuel prepared, int firstSeat) {
        starts.add(prepared);
        this.firstSeat = firstSeat;
        assertStarting(prepared);
        duringStart.run();
        assertStarting(prepared);
        if (throwStart) throw new IllegalStateException("Injected startup failure");
        if (failStart) return false;
        if (completeImmediately) {
            service.duelEnded(prepared.challenger().id());
            service.duelEnded(prepared.accepter().id());
            assertStarting(prepared);
        } else {
            dueling.add(prepared.challenger().id());
            dueling.add(prepared.accepter().id());
        }
        return true;
    }

    private void assertStarting(DuelPreparationService.PreparedDuel prepared) {
        for (var id : List.of(prepared.challenger().id(), prepared.accepter().id())) {
            assertTrue(service.isPreparing(id), "Startup must hold both collection locks");
            assertEquals(PreparationView.Mode.STARTING, service.view(id, 4).mode());
        }
    }

    @Override
    public void changed(UUID id, PreparationResult result) {
        notifications.computeIfAbsent(id, ignored -> new ArrayList<>()).add(result);
        duringChanged.run();
        if (service.view(id, 0).mode() == PreparationView.Mode.RPS) {
            assertTrue(service.isPreparing(alice));
            assertTrue(service.isPreparing(bob));
        }
    }
}

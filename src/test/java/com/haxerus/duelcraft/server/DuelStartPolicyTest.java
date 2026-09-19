package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.collection.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelStartPolicyTest {
    private static final long NOW = System.currentTimeMillis();
    static final class Fixture implements DuelManager.Players {
        final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        final Map<UUID, PlayerCollectionData> data = new HashMap<>();
        final List<String> events = new ArrayList<>();
        final List<PreparationStatePayload> states = new ArrayList<>();
        final Map<UUID, List<String>> messages = new HashMap<>();
        final DuelManager manager;
        int allocations, closes;
        String failure = "";
        boolean deny, throwing, immediate;
        Deck first, second;
        Fixture(boolean ownership) { this(ownership, true); }
        Fixture(boolean ownership, boolean hook) {
            var service = new CollectionService(CollectionTestData.facts(), new DeckUsePolicy(ownership, hook ? context -> {
                if (throwing) throw new IllegalStateException("private hook internals");
                return deny ? "Private progression requirement" : null;
            } : null));
            for (var id : List.of(a, b)) {
                var deck = CollectionTestData.deck(UUID.randomUUID());
                data.put(id, new PlayerCollectionData(1, ownership ? CollectionTestData.owned() : Map.of(), Map.of(deck.id(), deck), deck.id()));
            }
            manager = new DuelManager(service, this, (options, listener) -> {
                allocations++;
                assertTrue(managerBusy());
                if (failure.equals("create")) throw new IllegalStateException("create failure");
                return new ManagedDuelSession() {
                    public void setupDuel(Deck x, Deck y) {
                        first = x; second = y;
                        assertTrue(managerBusy());
                        listener.onMessage(new com.haxerus.duelcraft.duel.message.DuelMessage.UpdateData(0, OcgConstants.LOCATION_EXTRA, List.of()));
                        if (failure.equals("setup")) throw new IllegalStateException("setup failure");
                    }
                    public void process() {
                        events.add("process");
                        if (immediate) listener.onDuelEnd();
                        if (failure.equals("process")) throw new IllegalStateException("process failure");
                    }
                    public void setResponse(byte[] response) {}
                    public boolean isEnded() { return immediate; }
                    public DuelEventListener listener() { return listener; }
                    public void close() { closes++; if (failure.equals("close")) throw new IllegalStateException("close failure"); }
                };
            });
        }
        boolean managerBusy() { return manager.isBusy(a); }
        void assertNoOwnership() {
            for (String name : List.of("activeDuels", "playerToDuel", "duelSeats", "soloHandlers", "startingPlayers")) {
                try {
                    var field = DuelManager.class.getDeclaredField(name); field.setAccessible(true); var value = field.get(manager);
                    assertTrue(value instanceof Map<?, ?> map ? map.isEmpty() : ((Set<?>) value).isEmpty(), name);
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            }
        }
        public boolean online(UUID id) { return data.containsKey(id); }
        public String name(UUID id) { return id.equals(a) ? "Alice" : "Bob"; }
        public Optional<PlayerCollectionData> data(UUID id) { return Optional.ofNullable(data.get(id)); }
        public void persist(UUID id, PlayerCollectionData value) { data.put(id, value); }
        public void send(UUID id, CustomPacketPayload payload) {
            if (payload instanceof DuelStartPayload) events.add("start");
            if (payload instanceof DuelMessagePayload) events.add("refresh");
            if (payload instanceof PreparationStatePayload state) { states.add(state); if (state.result() == PreparationResult.START_FAILED) events.add("failed"); }
        }
        public void message(UUID id, Component message) { messages.computeIfAbsent(id, x -> new ArrayList<>()).add(message.getString()); }
        UUID ready() {
            var prep = manager.preparation();
            assertEquals(PreparationResult.OK, prep.invite(a, b, DuelRule.MR5, 42L, PlayerOptions.standard(), NOW).code());
            var id = prep.view(a, NOW).flowId();
            assertEquals(PreparationResult.OK, prep.accept(b, id, NOW).code());
            var round = prep.view(a, NOW).roundId();
            prep.hand(a, id, round, FirstTurnLobby.Hand.ROCK, NOW);
            prep.hand(b, id, round, FirstTurnLobby.Hand.SCISSORS, NOW);
            return id;
        }
    }

    @Test void verifiedCurrentListsReachSetupWithRoleSeedsAndSeatMapping() {
        var f = new Fixture(false); var id = f.ready();
        var a = f.data.get(f.a).decks().values().iterator().next();
        var b = f.data.get(f.b).decks().values().iterator().next();
        assertEquals(PreparationResult.OK, f.manager.preparation().first(f.a, id, false, NOW).code());
        assertEquals(b.cards().toDuelDeck().shuffled(43), f.first);
        assertEquals(a.cards().toDuelDeck().shuffled(42), f.second);
        assertEquals(List.of("start", "start", "refresh", "refresh", "process"), f.events);
        f.manager.forfeit(f.a);
        assertFalse(f.manager.isBusy(f.a)); assertFalse(f.manager.isBusy(f.b)); assertEquals(1, f.closes);
        assertNotNull(f.data.get(f.a).activeDeckId());
    }
    @Test void changedSameIdAndClearedSelectionCannotReachNativeSetup() {
        for (boolean clear : List.of(false, true)) {
            var f = new Fixture(false); var flow = f.ready(); var before = f.data.get(f.b);
            var original = before.decks().get(before.activeDeckId());
            var changed = new SavedDeck(original.id(), original.name(), new DeckList(original.cards().main().reversed(), original.cards().extra(), original.cards().side()));
            f.data.put(f.b, new PlayerCollectionData(2, before.counts(), Map.of(changed.id(), changed), clear ? null : changed.id()));
            assertEquals(PreparationResult.START_FAILED, f.manager.preparation().first(f.a, flow, true, NOW).code());
            assertEquals(0, f.allocations); assertFalse(f.manager.isBusy(f.a)); assertFalse(f.manager.isBusy(f.b));
        }
    }
    @Test void finalCompanionDenialOrFailureRechecksBothOwnershipModes() {
        for (boolean ownership : List.of(false, true)) for (boolean throwing : List.of(false, true)) {
            var f = new Fixture(ownership); var id = f.ready(); f.deny = !throwing; f.throwing = throwing;
            assertEquals(PreparationResult.START_FAILED, f.manager.preparation().first(f.a, id, true, NOW).code());
            assertEquals(0, f.allocations); assertFalse(f.manager.isBusy(f.a)); assertFalse(f.manager.isBusy(f.b));
        }
    }
    @Test void failedAllocationSetupAndProcessReleaseEveryMappingAndCloseOnce() {
        for (String failure : List.of("create", "setup", "process")) {
            var f = new Fixture(false); var id = f.ready(); f.failure = failure;
            assertEquals(PreparationResult.START_FAILED, f.manager.preparation().first(f.a, id, true, NOW).code());
            assertEquals(failure.equals("create") ? 0 : 1, f.closes);
            assertFalse(f.manager.isBusy(f.a)); assertFalse(f.manager.isBusy(f.b));
            f.assertNoOwnership();
            if (!failure.equals("create")) assertTrue(f.events.indexOf("start") < f.events.indexOf("refresh") && f.events.indexOf("refresh") < f.events.indexOf("failed"));
        }
    }
    @Test void synchronousEndIsSuccessfulAndDoesNotCloseTwice() {
        var f = new Fixture(false); var id = f.ready(); f.immediate = true;
        assertEquals(PreparationResult.OK, f.manager.preparation().first(f.a, id, true, NOW).code());
        assertEquals(1, f.closes); assertFalse(f.manager.isBusy(f.a));
        assertTrue(f.states.stream().noneMatch(x -> x.result() == PreparationResult.START_FAILED));
    }
    @Test void soloUsesCurrentPolicyAndGuardDuringAllocationThenReleasesOnFailure() {
        for (boolean ownership : List.of(false, true)) for (String failure : List.of("", "create", "setup", "process")) {
            var f = new Fixture(ownership); f.failure = failure;
            boolean started = f.manager.startSoloDuel(f.a, new DuelSettings(DuelRule.MR5, 42, PlayerOptions.standard()), null);
            assertEquals(failure.isEmpty(), started);
            assertEquals(failure.isEmpty(), f.manager.isBusy(f.a));
            var terminal = f.states.getLast();
            assertEquals(failure.isEmpty() ? PreparationView.Mode.DUEL : PreparationView.Mode.IDLE, terminal.view().mode());
            assertEquals(failure.isEmpty() ? PreparationResult.OK : PreparationResult.START_FAILED, terminal.result());
            assertTrue(terminal.view().revision() > 0);
            if (started) f.manager.forfeit(f.a);
            assertFalse(f.manager.isBusy(f.a)); assertEquals(failure.equals("create") ? 0 : 1, f.closes);
            f.assertNoOwnership();
        }
    }
    @Test void acceptanceReportsPrivateOwnerReasonAndGenericPeerStatus() {
        var f = new Fixture(false);
        var service = f.manager.preparation();
        service.invite(f.a, f.b, DuelRule.MR5, 1L, PlayerOptions.standard(), NOW);
        f.deny = true;
        assertEquals(PreparationResult.INELIGIBLE, service.accept(f.b, service.view(f.b, NOW).flowId(), NOW).code());
        assertTrue(f.messages.get(f.a).contains("Opponent's deck is not ready"));
        assertEquals(0, f.allocations);
    }
    @Test void loginRevalidationClearsInvalidPersistedActivationOnceAndNeverDeletesUnknownSavedIds() {
        var f = new Fixture(true); var before = f.data.get(f.a);
        var unknown = new SavedDeck(UUID.randomUUID(), "Unknown", new DeckList(List.of(999999), List.of(), List.of()));
        var decks = new HashMap<>(before.decks()); decks.put(unknown.id(), unknown);
        f.data.put(f.a, new PlayerCollectionData(5, Map.of(), decks, before.activeDeckId()));
        f.manager.revalidate(f.a, DuelRule.MR5);
        assertNull(f.data.get(f.a).activeDeckId()); assertEquals(6, f.data.get(f.a).revision());
        assertEquals(decks, f.data.get(f.a).decks());
        f.manager.revalidate(f.a, DuelRule.MR5); assertEquals(6, f.data.get(f.a).revision());
    }
    @Test void addingOrRemovingCompanionDoesNotReactivateClearedSelection() {
        var f = new Fixture(false); f.deny = true; f.manager.revalidate(f.a, DuelRule.MR5);
        var cleared = f.data.get(f.a); assertNull(cleared.activeDeckId());
        f.deny = false; f.manager.revalidate(f.a, DuelRule.MR5); assertEquals(cleared, f.data.get(f.a));
    }
    @Test void soloPolicyDenialThrowAndMissingCopiesNeverAllocateAndReleaseGuard() {
        for (boolean ownership : List.of(false, true)) for (String denial : List.of("deny", "throw", "missing")) {
            var f = new Fixture(ownership); f.deny = denial.equals("deny"); f.throwing = denial.equals("throw");
            var before = f.data.get(f.a); f.data.put(f.a, new PlayerCollectionData(before.revision(), Map.of(), before.decks(), before.activeDeckId()));
            boolean allowed = !ownership && denial.equals("missing");
            assertEquals(allowed, f.manager.startSoloDuel(f.a, new DuelSettings(DuelRule.MR5, 1, PlayerOptions.standard()), null));
            assertEquals(allowed ? 1 : 0, f.allocations);
            if (allowed) f.manager.forfeit(f.a);
            assertFalse(f.manager.isBusy(f.a));
        }
    }
    @Test void soloImmediateEndAndCloseFailureCannotStrandOwnership() {
        var f = new Fixture(false); f.immediate = true; f.failure = "close";
        assertTrue(f.manager.startSoloDuel(f.a, new DuelSettings(DuelRule.MR5, 1, PlayerOptions.standard()), null));
        assertFalse(f.manager.isBusy(f.a)); assertEquals(1, f.closes);
        assertEquals(PreparationView.Mode.IDLE, f.states.getLast().view().mode());
    }
    @Test void noRawMultiplayerStartOrUploadSetterRemains() {
        assertTrue(Arrays.stream(DuelManager.class.getMethods()).noneMatch(method -> method.getName().equals("startDuel") || method.getName().equals("setPlayerCurrentDeck")));
    }

    @Test void noCompanionAndApprovingCompanionBothPermitCurrentEligibleSelections() {
        for (boolean ownership : List.of(false, true)) for (boolean hook : List.of(false, true)) {
            var f = new Fixture(ownership, hook); var id = f.ready();
            assertEquals(PreparationResult.OK, f.manager.preparation().first(f.a, id, true, NOW).code());
            f.manager.forfeit(f.a);
            assertTrue(f.manager.startSoloDuel(f.a, new DuelSettings(DuelRule.MR5, 1, PlayerOptions.standard()), null));
            f.manager.forfeit(f.a);
        }
    }
    @Test void shutdownReleasesLiveAndPreparationOwnershipAndResetsViews() {
        var f = new Fixture(false); f.ready(); f.manager.shutdown();
        assertFalse(f.manager.isBusy(f.a)); assertFalse(f.manager.isBusy(f.b));
        assertEquals(0, f.manager.preparation().view(f.a, NOW).revision());
        assertEquals(PreparationView.Mode.IDLE, f.manager.preparation().view(f.b, NOW).mode());
        assertNotNull(f.data.get(f.a).activeDeckId());
    }

    @Test void finalCurrentDepositedCountsIncludeSideAndCannotBeReplacedByApproval() {
        for (boolean ownership : List.of(false, true)) {
            var f = new Fixture(ownership); var before = f.data.get(f.a); var original = before.decks().get(before.activeDeckId());
            var withSide = new SavedDeck(original.id(), original.name(), new DeckList(original.cards().main(), List.of(), List.of(1)));
            var counts = new HashMap<>(CollectionTestData.owned()); counts.put(1, 2L);
            f.data.put(f.a, new PlayerCollectionData(2, counts, Map.of(withSide.id(), withSide), withSide.id()));
            var flow = f.ready(); counts.put(1, 1L);
            f.data.put(f.a, new PlayerCollectionData(3, counts, Map.of(withSide.id(), withSide), withSide.id()));
            assertEquals(ownership ? PreparationResult.START_FAILED : PreparationResult.OK,
                    f.manager.preparation().first(f.a, flow, true, NOW).code());
            assertEquals(ownership ? 0 : 1, f.allocations);
            if (!ownership) f.manager.forfeit(f.a);
            f.assertNoOwnership();
        }
    }

}

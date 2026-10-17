package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.item.CardItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.haxerus.duelcraft.server.collection.InventoryTransferPlan.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class CardTransferServiceTest {
    @Test void selectedCustomizedCardExplainsRejectionWithoutStrippingComponents() {
        var owner = new Owner(); var named = CardItem.stack(7, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me")); owner.slots[0] = named;
        var reply = (CollectionReply.Rejected) service(false, null).apply(owner, 0, DEPOSIT, 7, 1);
        assertEquals(CollectionError.UNSUPPORTED_CARDS, reply.error());
        assertSame(named, owner.slots[0]); assertEquals(PlayerCollectionData.empty(), owner.data);
    }
    static class Owner implements CardTransferService.Owner {
        final UUID id = UUID.randomUUID();
        PlayerCollectionData data = PlayerCollectionData.empty();
        final ItemStack[] slots = new ItemStack[41];
        boolean busy; int writes, syncs;
        Owner() { Arrays.fill(slots, ItemStack.EMPTY); }
        public UUID id() { return id; }
        public Optional<PlayerCollectionData> data() { return Optional.ofNullable(data); }
        public boolean busy() { return busy; }
        public void persist(PlayerCollectionData value) { data = value; writes++; }
        public ItemStack slot(int index) { return slots[index]; }
        public void slot(int index, ItemStack value) { slots[index] = value; }
        public void inventoryChanged() { syncs++; }
    }
    static CardTransferService service(boolean required, DeckUsePolicy.Restriction restriction) {
        return new CardTransferService(new CollectionService(CollectionTestData.facts(), new DeckUsePolicy(required, restriction)), Set.of(7));
    }

    @Test void depositsAndWithdrawalsConserveRealStacksAndRejectReplay() {
        var owner = new Owner(); owner.slots[0] = CardItem.stack(7, 20);
        var service = service(false, null);
        assertInstanceOf(CollectionReply.Changed.class, service.apply(owner, 0, DEPOSIT, 7, 20));
        assertTrue(owner.slots[0].isEmpty()); assertEquals(Map.of(7, 20L), owner.data.counts());
        assertInstanceOf(CollectionReply.Changed.class, service.apply(owner, 1, WITHDRAW, 7, 5));
        assertEquals(5, owner.slots[0].getCount()); assertEquals(Map.of(7, 15L), owner.data.counts());
        assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) service.apply(owner, 1, WITHDRAW, 7, 5)).error());
        assertEquals(2, owner.writes); assertEquals(2, owner.syncs); assertEquals(5, owner.slots[0].getCount());
        assertNull(owner.data.activeDeckId());
    }

    @Test void unsupportedStacksArmorAndOffhandSurviveExceptEligibleDeposits() {
        var owner = new Owner(); var named = CardItem.stack(7, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        owner.slots[0] = named; owner.slots[1] = CardItem.stack(8, 4);
        owner.slots[36] = CardItem.stack(7, 1); owner.slots[40] = CardItem.stack(7, 2);
        var reply = (CollectionReply.Changed) service(false, null).apply(owner, 0, DEPOSIT_ALL, 0, 0);
        assertEquals(2, reply.transferred()); assertEquals(7, reply.skipped());
        assertSame(named, owner.slots[0]); assertEquals(4, owner.slots[1].getCount());
        assertEquals(1, owner.slots[36].getCount()); assertTrue(owner.slots[40].isEmpty());
        assertEquals(Map.of(7, 2L), owner.data.counts());
    }

    @Test void shortageOnlyClearsRequiredOwnershipAndRetainsSavedList() {
        for (boolean required : new boolean[]{false, true}) {
            var owner = activeOwner(); var saved = owner.data.decks();
            var reply = (CollectionReply.Changed) service(required, null).apply(owner, 0, WITHDRAW, 7, 1);
            assertEquals(required ? null : saved.keySet().iterator().next(), owner.data.activeDeckId());
            assertEquals(saved, owner.data.decks()); assertEquals(Map.of(7, 1), reply.eligibility().missing());
            assertEquals(1, owner.slots[0].getCount());
        }
    }

    @Test void companionSeesProposedCountsAndDenialClearsEitherMode() {
        for (boolean required : new boolean[]{false, true}) {
            var owner = activeOwner();
            var reply = (CollectionReply.Changed) service(required, context -> {
                assertEquals(owner.id, context.owner());
                assertFalse(context.counts().containsKey(7));
                return "Companion denial";
            }).apply(owner, 0, WITHDRAW, 7, 1);
            assertNull(owner.data.activeDeckId()); assertEquals(1, owner.data.decks().size());
            assertEquals("Companion denial", reply.eligibility().restrictionReason());
        }
    }

    @Test void failedEvaluationAndBusyUnreadableStaleRequestsNeverMutate() {
        var owner = activeOwner(); var before = owner.data;
        var service = service(false, context -> { throw new IllegalStateException("fixture"); });
        assertEquals(CollectionError.DATA_UNAVAILABLE, ((CollectionReply.Rejected) service.apply(owner, 0, WITHDRAW, 7, 1)).error());
        assertSame(before, owner.data); assertTrue(owner.slots[0].isEmpty());
        owner.busy = true;
        assertEquals(CollectionError.BUSY, ((CollectionReply.Rejected) service.apply(owner, 0, WITHDRAW, 7, 1)).error());
        owner.busy = false;
        assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) service.apply(owner, 9, WITHDRAW, 7, 1)).error());
        owner.data = null;
        assertEquals(CollectionError.DATA_UNAVAILABLE, ((CollectionReply.Rejected) service.apply(owner, 0, WITHDRAW, 7, 1)).error());
        assertEquals(0, owner.writes); assertEquals(0, owner.syncs);
    }

    private static Owner activeOwner() {
        var owner = new Owner(); var deck = CollectionTestData.deck(UUID.randomUUID());
        owner.data = new PlayerCollectionData(0, CollectionTestData.owned(), Map.of(deck.id(), deck), deck.id());
        return owner;
    }
}

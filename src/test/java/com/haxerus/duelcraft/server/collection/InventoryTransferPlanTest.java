package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionError;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static com.haxerus.duelcraft.server.collection.InventoryTransferPlan.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTransferPlanTest {
    @Test void selectedDepositConservesCopiesAndUsesMainBeforeOffhand() {
        var result = plan(List.of(new Slot(40, 7, 20), new Slot(2, 7, 3), new Slot(0, 7, 2)),
                Map.of(7, 1000L), Set.of(7), Kind.DEPOSIT, 7, 7);
        assertEquals(CollectionError.NONE, result.error());
        assertEquals(7, result.moved());
        assertEquals(1007L, result.counts().get(7));
        assertEquals(List.of(new Slot(40, 7, 18), new Slot(2, 0, 0), new Slot(0, 0, 0)), result.slots());
        assertEquals(1025L, result.counts().get(7) + result.slots().stream().mapToInt(Slot::count).sum());
    }

    @Test void withdrawalUsesPartialStacksThenMainEmptiesNeverOffhand() {
        var result = plan(List.of(new Slot(0, 0, 0), new Slot(1, 7, 60), new Slot(40, 7, 1)),
                Map.of(7, 10L), Set.of(), Kind.WITHDRAW, 7, 10);
        assertEquals(List.of(new Slot(0, 7, 6), new Slot(1, 7, 64), new Slot(40, 7, 1)), result.slots());
        assertEquals(Map.of(), result.counts());
        assertEquals(10, result.moved());
    }

    @Test void insufficientCapacityAndQuantityLeaveEverythingUnchanged() {
        var full = IntStream.range(0, 36).mapToObj(i -> new Slot(i, -1, 64)).toList();
        unchanged(full, Map.of(7, 10L), Kind.WITHDRAW, 7, 1, CollectionError.INVENTORY_FULL);
        unchanged(List.of(new Slot(40, 0, 0)), Map.of(7, 10L), Kind.WITHDRAW, 7, 1, CollectionError.INVENTORY_FULL);
        unchanged(List.of(new Slot(0, 7, 63)), Map.of(7, 10L), Kind.WITHDRAW, 7, 2, CollectionError.INVENTORY_FULL);
        unchanged(List.of(new Slot(0, 0, 0)), Map.of(7, 1L), Kind.WITHDRAW, 7, 2, CollectionError.INSUFFICIENT_CARDS);
        unchanged(List.of(new Slot(0, 7, 1)), Map.of(), Kind.DEPOSIT, 7, 2, CollectionError.INSUFFICIENT_CARDS);
    }

    @Test void exactCapacityAndUnknownOwnedWithdrawalWork() {
        var result = plan(List.of(new Slot(0, 7, 63)), Map.of(7, 1L), Set.of(), Kind.WITHDRAW, 7, 1);
        assertEquals(CollectionError.NONE, result.error());
        assertEquals(new Slot(0, 7, 64), result.slots().getFirst());
        assertTrue(result.counts().isEmpty());
    }

    @Test void bulkSkipsUnknownCopiesAndLeavesUnusableSlotsIntact() {
        var result = plan(List.of(new Slot(0, 7, 20), new Slot(1, 8, 4), new Slot(2, -1, 3), new Slot(40, 7, 2)),
                Map.of(), Set.of(7), Kind.DEPOSIT_ALL, 0, 0);
        assertEquals(22, result.moved()); assertEquals(4, result.skipped());
        assertEquals(Map.of(7, 22L), result.counts());
        assertEquals(List.of(new Slot(0, 0, 0), new Slot(1, 8, 4), new Slot(2, -1, 3), new Slot(40, 0, 0)), result.slots());
        unchanged(List.of(new Slot(0, 8, 4)), Map.of(), Kind.DEPOSIT_ALL, 0, 0, CollectionError.INSUFFICIENT_CARDS);
        unchanged(List.of(), Map.of(), Kind.DEPOSIT_ALL, 0, 0, CollectionError.INSUFFICIENT_CARDS);
    }

    @Test void invalidAmountsAndOverflowAreAtomic() {
        for (var kind : List.of(Kind.DEPOSIT, Kind.WITHDRAW)) {
            for (int amount : new int[]{0, -1, 4097})
                unchanged(List.of(new Slot(0, 7, 20)), Map.of(7, 20L), kind, 7, amount, CollectionError.INVALID);
            unchanged(List.of(), Map.of(), kind, 0, 1, CollectionError.INVALID);
        }
        for (var kind : List.of(Kind.DEPOSIT, Kind.DEPOSIT_ALL))
            unchanged(List.of(new Slot(0, 7, 20)), Map.of(7, Long.MAX_VALUE), kind, 7, 1, CollectionError.INVALID);
    }

    @Test void resultDoesNotRetainMutableInputs() {
        var slots = new ArrayList<>(List.of(new Slot(0, 7, 1)));
        var counts = new HashMap<>(Map.of(7, 1L));
        var result = plan(slots, counts, Set.of(7), Kind.DEPOSIT, 7, 2);
        slots.clear(); counts.clear();
        assertEquals(List.of(new Slot(0, 7, 1)), result.slots());
        assertEquals(Map.of(7, 1L), result.counts());
        assertThrows(UnsupportedOperationException.class, () -> result.counts().clear());
    }

    private static void unchanged(List<Slot> slots, Map<Integer, Long> counts, Kind kind, int code, int amount, CollectionError error) {
        var result = plan(slots, counts, Set.of(7), kind, code, amount);
        assertEquals(error, result.error()); assertEquals(0, result.moved());
        assertEquals(slots, result.slots()); assertEquals(counts, result.counts());
    }
}

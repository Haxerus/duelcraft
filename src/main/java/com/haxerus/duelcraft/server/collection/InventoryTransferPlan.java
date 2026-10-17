package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.CollectionError;
import java.util.*;

/** Pure all-or-nothing plan over main inventory and offhand snapshots. */
public final class InventoryTransferPlan {
    public enum Kind { DEPOSIT, WITHDRAW, DEPOSIT_ALL }
    public record Slot(int index, int code, int count) {}
    public record Result(List<Slot> slots, Map<Integer, Long> counts, int moved, int skipped, CollectionError error) {
        public Result { slots = List.copyOf(slots); counts = Map.copyOf(counts); }
    }

    public static Result plan(List<Slot> slots, Map<Integer, Long> counts, Set<Integer> depositableCodes,
                              Kind kind, int code, int amount) {
        if (kind == null || (kind != Kind.DEPOSIT_ALL && (code <= 0 || amount < 1 || amount > 4096)))
            return failed(slots, counts, CollectionError.INVALID);
        var after = new ArrayList<>(slots);
        var stored = new HashMap<>(counts);
        var order = new ArrayList<Integer>();
        for (int i = 0; i < slots.size(); i++) {
            int index = slots.get(i).index();
            if (index >= 0 && index < 36 || index == 40) order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> slots.get(i).index()));
        int moved = 0, skipped = 0;
        try {
            if (kind == Kind.WITHDRAW) {
                long owned = counts.getOrDefault(code, 0L);
                if (owned < amount) return failed(slots, counts, CollectionError.INSUFFICIENT_CARDS);
                for (boolean empty : new boolean[]{false, true}) {
                    for (int i : order) {
                        var slot = after.get(i);
                        if (slot.index() == 40 || (empty ? slot.code() != 0 : slot.code() != code)) continue;
                        int take = Math.min(amount - moved, 64 - slot.count());
                        if (take <= 0) continue;
                        after.set(i, new Slot(slot.index(), code, slot.count() + take));
                        moved += take;
                    }
                }
                if (moved != amount) return failed(slots, counts, CollectionError.INVENTORY_FULL);
                if (owned == amount) stored.remove(code); else stored.put(code, owned - amount);
            } else {
                for (int i : order) {
                    var slot = slots.get(i);
                    if (slot.code() <= 0) continue;
                    if (!depositableCodes.contains(slot.code())) {
                        if (kind == Kind.DEPOSIT_ALL) skipped += slot.count();
                        continue;
                    }
                    if (kind == Kind.DEPOSIT && slot.code() != code) continue;
                    int take = kind == Kind.DEPOSIT_ALL ? slot.count() : Math.min(slot.count(), amount - moved);
                    if (take <= 0) continue;
                    stored.put(slot.code(), Math.addExact(stored.getOrDefault(slot.code(), 0L), take));
                    after.set(i, new Slot(slot.index(), take == slot.count() ? 0 : slot.code(), slot.count() - take));
                    moved += take;
                }
                if (moved == 0 || kind == Kind.DEPOSIT && moved != amount)
                    return failed(slots, counts, CollectionError.INSUFFICIENT_CARDS);
            }
        } catch (ArithmeticException exception) {
            return failed(slots, counts, CollectionError.INVALID);
        }
        return new Result(after, stored, moved, skipped, CollectionError.NONE);
    }

    private static Result failed(List<Slot> slots, Map<Integer, Long> counts, CollectionError error) {
        return new Result(slots, counts, 0, 0, error);
    }
}

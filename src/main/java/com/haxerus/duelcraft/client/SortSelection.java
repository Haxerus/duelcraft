package com.haxerus.duelcraft.client;

/**
 * Tracks a {@code MSG_SORT_CARD} / {@code MSG_SORT_CHAIN} click-order selection.
 * Clicking a card assigns it the next ordinal; re-clicking an already-numbered
 * card removes its ordinal and shifts every later ordinal down by one, mirroring
 * edopro's {@code sort_list} bookkeeping ({@code event_handler.cpp}, MSG_SORT_CARD
 * click handling).
 */
public final class SortSelection {

    /** 1-based ordinal per original card index; 0 = unassigned. */
    private final int[] ordinal;
    private int assignedCount;

    public SortSelection(int cardCount) {
        this.ordinal = new int[cardCount];
    }

    /** Assign the next ordinal to this card, or remove its ordinal (shifting later ones down) on re-click. */
    public void assign(int index) {
        if (ordinal[index] != 0) {
            int removedOrdinal = ordinal[index];
            ordinal[index] = 0;
            for (int i = 0; i < ordinal.length; i++) {
                if (ordinal[i] > removedOrdinal) ordinal[i]--;
            }
            assignedCount--;
        } else {
            assignedCount++;
            ordinal[index] = assignedCount;
        }
    }

    /** 1-based ordinal shown on the card, or 0 if unassigned. */
    public int ordinalOf(int index) {
        return ordinal[index];
    }

    public boolean isComplete() {
        return assignedCount == ordinal.length;
    }

    /** response[i] = zero-based destination rank of original card i (the MSG_SORT_CARD/CHAIN response). */
    public int[] response() {
        int[] result = new int[ordinal.length];
        for (int i = 0; i < ordinal.length; i++) {
            result[i] = ordinal[i] - 1;
        }
        return result;
    }
}

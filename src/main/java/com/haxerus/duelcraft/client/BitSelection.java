package com.haxerus.duelcraft.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks a checkbox-grid selection over a bitmask: {@code MSG_ANNOUNCE_RACE} (u64) and
 * {@code MSG_ANNOUNCE_ATTRIB} (u32, widened to long here) share the same shape — pick exactly
 * {@code count} of the bits set in {@code available}, in any order, un-picking is allowed.
 */
public final class BitSelection {

    private final long available;
    private final int count;
    private long checked;

    public BitSelection(long available, int count) {
        this.available = available;
        this.count = count;
    }

    /** Available bit positions, ascending. */
    public List<Integer> bits() {
        List<Integer> result = new ArrayList<>();
        for (int bit = 0; bit < 64; bit++) {
            if ((available & (1L << bit)) != 0) result.add(bit);
        }
        return result;
    }

    /** Check/uncheck a bit. No-op if the bit isn't available, or checking it would exceed {@code count}. */
    public void toggle(int bit) {
        long flag = 1L << bit;
        if ((available & flag) == 0) return;
        if ((checked & flag) != 0) {
            checked &= ~flag;
        } else if (Long.bitCount(checked) < count) {
            checked |= flag;
        }
    }

    public boolean isChecked(int bit) {
        return (checked & (1L << bit)) != 0;
    }

    public boolean isComplete() {
        return Long.bitCount(checked) == count;
    }

    /** Bitwise OR of the checked bits — the response payload. */
    public long mask() {
        return checked;
    }
}

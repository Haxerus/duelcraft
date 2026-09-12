package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;

/**
 * Tracks a {@code MSG_SELECT_COUNTER} selection: each click removes one counter
 * from a card, up to that card's own counter count, until the overall target
 * count is met.
 *
 * <p>Mirrors the engine's own acceptance check ({@code playerop.cpp} SelectCounter
 * step 1): per card, counters removed must not exceed that card's counter count,
 * and the total removed across all cards must equal the prompt's {@code count}
 * exactly.
 */
public final class CounterSelection {

    private final int[] initialCounts;
    private final int[] removed;
    private int remaining;

    public CounterSelection(DuelMessage.SelectCounter prompt) {
        this.initialCounts = prompt.cards().stream()
                .mapToInt(DuelMessage.CounterCard::counterCount)
                .toArray();
        this.removed = new int[initialCounts.length];
        this.remaining = prompt.count();
    }

    /** Overall counters still to be removed across all cards. */
    public int remaining() {
        return remaining;
    }

    /** Counters still remaining on the given card. */
    public int remainingFor(int index) {
        return initialCounts[index] - removed[index];
    }

    /** True if this card still has a counter to remove and the overall target isn't met yet. */
    public boolean canPick(int index) {
        return remaining > 0 && remainingFor(index) > 0;
    }

    /** Remove one counter from the given card. No-op if it can't be picked. */
    public void pick(int index) {
        if (!canPick(index)) return;
        removed[index]++;
        remaining--;
    }

    public boolean isComplete() {
        return remaining == 0;
    }

    /** Counters removed per card, in message order — the MSG_SELECT_COUNTER response. */
    public int[] response() {
        return removed.clone();
    }
}

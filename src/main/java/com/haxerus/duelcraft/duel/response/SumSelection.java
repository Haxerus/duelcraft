package com.haxerus.duelcraft.duel.response;

import com.haxerus.duelcraft.duel.message.DuelMessage;

import java.util.List;

/**
 * Tracks a {@code MSG_SELECT_SUM} selection and which picks can still reach a legal total.
 *
 * <p>Mirrors the engine's own acceptance checks (`playerop.cpp:771-867`):
 * <ul>
 *   <li>Every card contributes {@code value1} or, when {@code value2 != 0}, {@code value2}.
 *   <li>Mode 0 ({@code selectMode == false}): the total must equal the target exactly and the
 *       number of <em>selectable</em> picks must lie in {@code [min, max]}.
 *   <li>Mode 1 ({@code selectMode == true}): the maximum reachable total must be at least the
 *       target and no card may be removable — the engine rejects a selection whose minimum
 *       total, less its smallest contribution, still reaches the target. Counts are not checked.
 *   <li>Must-select cards always contribute but are never part of the response, which indexes
 *       the selectable list alone (`parse_response_cards`).
 * </ul>
 */
public final class SumSelection {

    private final int target;
    private final int min;
    private final int max;
    /** Engine mode byte 1: the total only has to reach the target. */
    private final boolean atLeastMode;
    private final int[] mustParams;
    private final int[] selectableParams;
    private final boolean[] selected;

    public SumSelection(DuelMessage.SelectSum prompt) {
        this.target = prompt.targetSum();
        this.min = prompt.min();
        this.max = prompt.max();
        this.atLeastMode = prompt.selectMode();
        this.mustParams = params(prompt.mustSelect());
        this.selectableParams = params(prompt.selectable());
        this.selected = new boolean[selectableParams.length];
    }

    private static int[] params(List<DuelMessage.SumCard> cards) {
        return cards.stream().mapToInt(DuelMessage.SumCard::sumParam).toArray();
    }

    /** The first selection the engine would accept, or null if no combination works. */
    public static int[] firstViableCombination(DuelMessage.SelectSum prompt) {
        var selection = new SumSelection(prompt);
        return selection.completable(selection.selected, 0) ? selection.responseIndices() : null;
    }

    public boolean isSelected(int index) {
        return selected[index];
    }

    /** True if picking this card can still lead to a selection the engine accepts. */
    public boolean canPick(int index) {
        if (selected[index]) return false;
        boolean[] trial = selected.clone();
        trial[index] = true;
        return completable(trial, 0);
    }

    /** True if any unselected card is still worth offering. */
    public boolean hasPickable() {
        for (int i = 0; i < selected.length; i++) {
            if (canPick(i)) return true;
        }
        return false;
    }

    public void toggle(int index) {
        selected[index] = !selected[index];
    }

    /** True if the current selection (must-select cards included) is a legal answer. */
    public boolean isComplete() {
        return isValid(selected);
    }

    /**
     * The accepted total once the selection is legal, otherwise the total the selection is
     * guaranteed to reach. A mode 0 selection is exact by definition, so it reports the target —
     * a card that completes the sum through {@code value2} would otherwise read as short.
     */
    public int currentSum() {
        if (!atLeastMode && isComplete()) return target;
        return minTotal(selected);
    }

    /** Indices into the selectable list; must-select cards are never indexed. */
    public int[] responseIndices() {
        int[] indices = new int[count(selected)];
        int n = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) indices[n++] = i;
        }
        return indices;
    }

    // ── Engine checks ──────────────────────────────────────────────────────

    private boolean isValid(boolean[] picks) {
        if (atLeastMode) return checkAtLeast(picks);
        int count = count(picks);
        return count >= min && count <= max && checkExact(chosenParams(picks), 0, target);
    }

    /** Port of {@code select_sum_check1}: each card spends value1 or value2, hitting acc exactly. */
    private static boolean checkExact(int[] params, int index, int acc) {
        if (acc == 0 || index == params.length) return false;
        int o1 = params[index] & 0xFFFF;
        int o2 = params[index] >>> 16;
        if (index == params.length - 1) return acc == o1 || acc == o2;
        return (acc > o1 && checkExact(params, index + 1, acc - o1))
                || (o2 > 0 && acc > o2 && checkExact(params, index + 1, acc - o2));
    }

    /** Port of the {@code max == 0} branch: reachable total >= target and no removable card. */
    private boolean checkAtLeast(boolean[] picks) {
        int[] params = chosenParams(picks);
        if (params.length == 0) return false;
        int sum = 0, maxTotal = 0, smallest = Integer.MAX_VALUE;
        for (int param : params) {
            int o1 = param & 0xFFFF;
            int o2 = param >>> 16;
            int low = minValue(param);
            sum += low;
            maxTotal += Math.max(o1, o2);
            smallest = Math.min(smallest, low);
        }
        return maxTotal >= target && sum - smallest < target;
    }

    /** Depth-first search for a superset of {@code picks} the engine would accept. */
    private boolean completable(boolean[] picks, int from) {
        if (isValid(picks)) return true;
        if (isDeadEnd(picks)) return false;
        for (int i = from; i < picks.length; i++) {
            if (picks[i]) continue;
            picks[i] = true;
            if (completable(picks, i + 1)) return true;
            picks[i] = false;
        }
        return false;
    }

    /** True when adding more cards can no longer help — both bounds only grow with each pick. */
    private boolean isDeadEnd(boolean[] picks) {
        int[] params = chosenParams(picks);
        if (atLeastMode) {
            int smallest = Integer.MAX_VALUE;
            for (int param : params) smallest = Math.min(smallest, minValue(param));
            return params.length > 0 && minTotal(picks) - smallest >= target;
        }
        return count(picks) >= max || minTotal(picks) > target;
    }

    /** Total of every card's smallest contribution, must-select cards included. */
    private int minTotal(boolean[] picks) {
        int sum = 0;
        for (int param : mustParams) sum += minValue(param);
        for (int i = 0; i < picks.length; i++) {
            if (picks[i]) sum += minValue(selectableParams[i]);
        }
        return sum;
    }

    private int[] chosenParams(boolean[] picks) {
        int[] params = new int[mustParams.length + count(picks)];
        System.arraycopy(mustParams, 0, params, 0, mustParams.length);
        int n = mustParams.length;
        for (int i = 0; i < picks.length; i++) {
            if (picks[i]) params[n++] = selectableParams[i];
        }
        return params;
    }

    private static int minValue(int param) {
        int o1 = param & 0xFFFF;
        int o2 = param >>> 16;
        return (o2 != 0 && o2 < o1) ? o2 : o1;
    }

    private static int count(boolean[] picks) {
        int n = 0;
        for (boolean p : picks) {
            if (p) n++;
        }
        return n;
    }
}

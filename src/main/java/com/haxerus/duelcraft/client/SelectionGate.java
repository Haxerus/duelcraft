package com.haxerus.duelcraft.client;

/**
 * edopro's per-click gating for {@code MSG_SELECT_CARD} and {@code MSG_SELECT_TRIBUTE}
 * (<code>event_handler.cpp:1407-1446</code>): after every pick, either the selection submits itself
 * or the shared Cancel/Finish button takes one of its three states.
 *
 * <p>The two prompts differ only in what the engine compares against {@code min}: the card count for
 * {@code SELECT_CARD}, the summed {@code tributeCount} for {@code SELECT_TRIBUTE}. {@code max}
 * always bounds the card count, so over-tributing stays legal.
 */
public enum SelectionGate {
    /** Send the selection now. */
    SUBMIT,
    /** Show "Finish": the selection is legal but can still be extended. */
    FINISH,
    /** Show "Cancel": nothing is picked yet and the prompt accepts {@code -1}. */
    CANCEL,
    /** No button: the selection is not legal yet and cannot be cancelled. */
    HIDDEN;

    /**
     * @param picked      cards selected so far
     * @param progress    what the engine measures against {@code min} (card count, or tribute sum)
     * @param candidates  size of the prompt's selectable list
     */
    public static SelectionGate of(int picked, int progress, int min, int max,
                                   int candidates, boolean cancelable) {
        if (picked >= max) return SUBMIT;
        if (progress >= min) return picked == candidates ? SUBMIT : FINISH;
        return cancelable && picked == 0 ? CANCEL : HIDDEN;
    }
}

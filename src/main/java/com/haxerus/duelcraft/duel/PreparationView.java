package com.haxerus.duelcraft.duel;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Recipient-specific preparation state; contains no deck contents or opponent choices. */
public record PreparationView(long revision, Mode mode, @Nullable UUID flowId, @Nullable UUID roundId,
                              @Nullable UUID opponentId, String opponentName, boolean outgoing,
                              boolean ownHandSubmitted, boolean canChooseFirst, long remainingMillis,
                              @Nullable DuelSettings settings) {
    public enum Mode { IDLE, INVITED, RPS, FIRST_CHOICE, STARTING, DUEL }

    public PreparationView {
        if (revision < 0 || mode == null || opponentName == null || opponentName.length() > 128
                || remainingMillis < 0 || remainingMillis > 60_000) {
            throw new IllegalArgumentException("Invalid preparation view bounds");
        }
        boolean active = mode != Mode.IDLE && mode != Mode.DUEL;
        if (active ? flowId == null || opponentId == null || settings == null
                : flowId != null || opponentId != null || settings != null || !opponentName.isEmpty()
                        || outgoing || ownHandSubmitted || canChooseFirst || remainingMillis != 0) {
            throw new IllegalArgumentException("Preparation fields do not match mode");
        }
        if ((mode == Mode.RPS) != (roundId != null) || (canChooseFirst && mode != Mode.FIRST_CHOICE)
                || (mode == Mode.INVITED && ownHandSubmitted) || (mode == Mode.STARTING && remainingMillis != 0)) {
            throw new IllegalArgumentException("Preparation step fields do not match mode");
        }
    }
}

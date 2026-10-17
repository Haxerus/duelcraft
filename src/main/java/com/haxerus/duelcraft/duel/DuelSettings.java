package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;

/** Fixed invitation settings, using the same supported bounds as duel commands. */
public record DuelSettings(DuelRule rule, long seed, PlayerOptions options) {
    public DuelSettings {
        if (rule == null || options == null || options.lp() < 1 || options.lp() > 99999
                || options.startHand() < 0 || options.startHand() > 20
                || options.drawPerTurn() < 0 || options.drawPerTurn() > 10) {
            throw new IllegalArgumentException("Invalid duel settings");
        }
    }
}

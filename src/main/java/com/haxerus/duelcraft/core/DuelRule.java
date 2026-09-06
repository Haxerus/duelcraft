package com.haxerus.duelcraft.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** The eight ygopro-core rule presets, addressable from chat commands by a lowercase id. */
public enum DuelRule {
    MR1(DUEL_MODE_MR1),
    GOAT(DUEL_MODE_GOAT),
    MR2(DUEL_MODE_MR2),
    MR3(DUEL_MODE_MR3),
    MR4(DUEL_MODE_MR4),
    MR5(DUEL_MODE_MR5),
    SPEED(DUEL_MODE_SPEED),
    RUSH(DUEL_MODE_RUSH);

    private final long flags;

    DuelRule(long flags) {
        this.flags = flags;
    }

    /** Engine flags for {@code OCG_CreateDuel}. */
    public long flags() {
        return flags;
    }

    /** Lowercase id used on the command line, e.g. {@code mr3}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive lookup by id; empty for anything unknown. */
    public static Optional<DuelRule> parse(String id) {
        for (DuelRule rule : values()) {
            if (rule.id().equalsIgnoreCase(id)) return Optional.of(rule);
        }
        return Optional.empty();
    }

    /** Every id, in declaration order, for tab completion. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(DuelRule::id).toList();
    }
}

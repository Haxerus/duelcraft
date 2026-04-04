package com.haxerus.duelcraft.core;

public record DuelOptions(
    long[] seed,
    long flags,
    PlayerOptions team1,
    PlayerOptions team2
) {}

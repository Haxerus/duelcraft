package com.haxerus.duelcraft.core;

public record PlayerOptions(
    int lp,
    int startHand,
    int drawPerTurn
) {
    public static PlayerOptions standard() {
        return new PlayerOptions(8000, 5, 1);
    }
}

package com.haxerus.duelcraft.core;

import java.util.List;

public record Deck(
    List<Integer> main,
    List<Integer> extra
) {}

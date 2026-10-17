package com.haxerus.duelcraft.collection;

/** Deterministic, database-independent YDK serialization. */
public final class DeckListWriter {
    private DeckListWriter() {}

    public static String toYdk(DeckList cards) {
        var text = new StringBuilder("#created by Duelcraft\n#main\n");
        cards.main().forEach(code -> text.append(code).append('\n'));
        text.append("#extra\n");
        cards.extra().forEach(code -> text.append(code).append('\n'));
        text.append("!side\n");
        cards.side().forEach(code -> text.append(code).append('\n'));
        return text.toString();
    }
}

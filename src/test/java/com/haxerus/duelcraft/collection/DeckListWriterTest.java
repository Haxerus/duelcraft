package com.haxerus.duelcraft.collection;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeckListWriterTest {
    @Test void roundTripsAllSectionsAndPreservesOrder() {
        var cards = new DeckList(List.of(7, 8, 7, 7, 7), List.of(9), List.of(10, 7));
        String text = DeckListWriter.toYdk(cards);
        assertEquals("#created by Duelcraft\n#main\n7\n8\n7\n7\n7\n#extra\n9\n!side\n10\n7\n", text);
        assertEquals(cards, DeckListLoader.parseYdk(text));
    }

    @Test void emptySectionsStillHaveHeadersAndFinalNewline() {
        var cards = new DeckList(List.of(), List.of(), List.of());
        assertEquals("#created by Duelcraft\n#main\n#extra\n!side\n", DeckListWriter.toYdk(cards));
        assertEquals(cards, DeckListLoader.parseYdk(DeckListWriter.toYdk(cards)));
    }

    @Test void positiveUnknownPasscodesNeedNoDatabase() {
        var cards = new DeckList(List.of(Integer.MAX_VALUE), List.of(), List.of(1));
        assertEquals("#created by Duelcraft\n#main\n2147483647\n#extra\n!side\n1\n", DeckListWriter.toYdk(cards));
        assertEquals(cards, DeckListLoader.parseYdk(DeckListWriter.toYdk(cards)));
    }
}

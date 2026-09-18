package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.OcgConstants;
import com.haxerus.duelcraft.core.data.CardCatalog;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class CollectionTestData {
    private CollectionTestData() {}

    public static Map<Integer, CardCatalog.Facts> facts() {
        return IntStream.rangeClosed(1, 40).boxed().collect(Collectors.toUnmodifiableMap(
                code -> code, code -> new CardCatalog.Facts(code, OcgConstants.TYPE_MONSTER)));
    }

    public static Map<Integer, Long> owned() {
        return IntStream.rangeClosed(1, 40).boxed().collect(Collectors.toUnmodifiableMap(code -> code, code -> 1L));
    }

    public static SavedDeck deck(UUID id) {
        return new SavedDeck(id, "Fixture", new DeckList(IntStream.rangeClosed(1, 40).boxed().toList(),
                List.of(), List.of()));
    }
}

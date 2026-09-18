package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Production submits to its owned worker; UI fixtures inject immediate deterministic queries. */
@FunctionalInterface
public interface CollectionQuery extends AutoCloseable {
    void submit(List<CardInfo> cards, String text, CardSearch.Filters filters,
            Map<Integer, Long> counts, DeckList draft, Consumer<List<CardInfo>> apply);
    @Override default void close() {}
}

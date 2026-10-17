package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.SavedDeck;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface DeckSaveHandler {
    CompletionStage<SavedDeck> save(String name, DeckList submitted);
}

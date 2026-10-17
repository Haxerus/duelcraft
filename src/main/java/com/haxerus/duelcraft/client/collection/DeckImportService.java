package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.collection.SavedDeck;
import com.haxerus.duelcraft.core.DeckRegistry;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Reads player-selected local YDK files and coordinates the command import handshake. */
public final class DeckImportService {
    public record Result(SavedDeck saved, CollectionReply activation) {
        public boolean activated() {
            return saved != null && activation instanceof CollectionReply.Changed changed
                    && saved.id().equals(changed.activeId());
        }
    }

    private final DeckRegistry decks;

    public DeckImportService(DeckRegistry decks) {
        this.decks = decks;
    }

    public List<String> names() {
        return decks.listDeckNames();
    }

    public SavedDeck load(String name) throws IOException {
        return new SavedDeck(UUID.randomUUID(), name, decks.loadList(name));
    }

    public CompletionStage<Result> saveThenActivate(long revision, SavedDeck deck,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request) {
        var save = new CollectionCommand.Save(revision, deck);
        return request.apply(save).thenCompose(reply -> {
            if (reply instanceof CollectionReply.Rejected) {
                return CompletableFuture.completedFuture(new Result(null, reply));
            }
            if (!(reply instanceof CollectionReply.Changed changed)
                    || changed.revision() != revision + 1 || !deck.equals(changed.saved())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Save acknowledgement does not match the imported list"));
            }
            return request.apply(new CollectionCommand.Activate(changed.revision(), deck.id()))
                    .thenApply(activation -> {
                        if (activation instanceof CollectionReply.Rejected) return new Result(deck, activation);
                        if (activation instanceof CollectionReply.Changed activated
                                && activated.revision() == changed.revision() + 1
                                && deck.id().equals(activated.activeId()) && activated.saved() == null) {
                            return new Result(deck, activation);
                        }
                        throw new IllegalStateException("Activation acknowledgement does not match the imported list");
                    });
        });
    }
}

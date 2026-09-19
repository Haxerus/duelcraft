package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.client.collection.DeckImportService;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.server.collection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class DuelDeckImportPolicyTest {
    @TempDir Path temp;
    @Test void localFileImportsPersistWithoutGrantingCopiesOrBypassingEitherPolicy() throws Exception {
        var registry = DeckRegistry.open(temp);
        Files.writeString(temp.resolve("My Deck.ydk"), "#main\n" + java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining("\n")) + "\n#extra\n!side\n");
        var imports = new DeckImportService(registry);
        for (boolean ownership : List.of(false, true)) for (boolean deny : List.of(false, true)) {
            var owner = UUID.randomUUID();
            var service = new CollectionService(CollectionTestData.facts(), new DeckUsePolicy(ownership, context -> deny ? "Progression denied" : null));
            var state = new PlayerCollectionData[]{PlayerCollectionData.empty()};
            var deck = imports.load("My Deck");
            var result = imports.saveThenActivate(0, deck, command -> {
                var change = switch (command) {
                    case CollectionCommand.Save save -> service.save(state[0], save.expectedRevision(), false, owner, save.deck());
                    case CollectionCommand.Activate activate -> service.activate(state[0], activate.expectedRevision(), false, owner, activate.id());
                    default -> throw new AssertionError();
                };
                if (!change.success()) return CompletableFuture.completedFuture(new CollectionReply.Rejected(change.error(), state[0].revision(), change.eligibility()));
                state[0] = change.data();
                return CompletableFuture.completedFuture(new CollectionReply.Changed(state[0].revision(), state[0].activeDeckId(), command instanceof CollectionCommand.Save ? deck : null, 0, change.eligibility()));
            }).toCompletableFuture().join();
            assertEquals(!ownership && !deny, result.activated());
            assertEquals(deck, state[0].decks().get(deck.id())); assertTrue(state[0].counts().isEmpty());
        }
    }
}

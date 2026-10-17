package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionError;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.collection.DeckEligibility;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.core.DeckRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckImportServiceTest {
    private static final DeckEligibility.Report ELIGIBLE =
            new DeckEligibility.Report(List.of(), Map.of(), false, false, null);

    @Test
    void sameNamedImportsHaveIndependentIds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("same.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));

        var first = imports.load("same");
        var second = imports.load("same");

        assertEquals("same", first.name());
        assertEquals(first.cards(), second.cards());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void savedListIsIndependentOfItsDeletedSourceFile(@TempDir Path dir) throws IOException {
        var file = dir.resolve("snapshot.ydk");
        Files.writeString(file, "#main\n1\n#extra\n2\n!side\n3\n");
        var imports = new DeckImportService(new DeckRegistry(dir));
        var imported = imports.load("snapshot");
        var result = imports.saveThenActivate(0, imported, command -> switch (command) {
            case CollectionCommand.Save ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Changed(1, null, imported, 0, ELIGIBLE));
            case CollectionCommand.Activate ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Rejected(CollectionError.INELIGIBLE, 1, ELIGIBLE));
            default -> throw new AssertionError(command);
        }).toCompletableFuture().join();

        Files.delete(file);

        assertEquals(new DeckList(List.of(1), List.of(2), List.of(3)), result.saved().cards());
    }

    @Test
    void activationWaitsForAcknowledgedSaveRevisionAndKeepsRejectedListSaved(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("policy.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));
        var deck = imports.load("policy");
        var commands = new ArrayList<CollectionCommand>();
        var replies = new ArrayList<CompletableFuture<CollectionReply>>();

        var result = imports.saveThenActivate(7, deck, command -> {
            commands.add(command);
            var reply = new CompletableFuture<CollectionReply>();
            replies.add(reply);
            return reply;
        }).toCompletableFuture();

        assertEquals(List.of(new CollectionCommand.Save(7, deck)), commands);
        replies.getFirst().complete(new CollectionReply.Changed(8, null, deck, 0, ELIGIBLE));
        assertEquals(List.of(new CollectionCommand.Save(7, deck), new CollectionCommand.Activate(8, deck.id())), commands);
        var denial = new CollectionReply.Rejected(CollectionError.INELIGIBLE, 8,
                new DeckEligibility.Report(List.of(), Map.of(), false, false, "Era locked"));
        replies.getLast().complete(denial);

        assertEquals(deck, result.join().saved());
        assertEquals(denial, result.join().activation());
        assertFalse(result.join().activated());
    }

    @Test
    void matchingActivationAcknowledgementMarksTheImportedListActive(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("open.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));
        var deck = imports.load("open");

        var result = imports.saveThenActivate(2, deck, command -> switch (command) {
            case CollectionCommand.Save ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Changed(3, null, deck, 0, ELIGIBLE));
            case CollectionCommand.Activate ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Changed(4, deck.id(), null, 0, ELIGIBLE));
            default -> throw new AssertionError(command);
        }).toCompletableFuture().join();

        assertEquals(deck, result.saved());
        assertEquals(deck.id(), ((CollectionReply.Changed) result.activation()).activeId());
        assertTrue(result.activated());
    }

    @Test
    void mismatchedActivationAcknowledgementFailsInsteadOfClaimingSuccess(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad-ack.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));
        var deck = imports.load("bad-ack");

        var result = imports.saveThenActivate(2, deck, command -> switch (command) {
            case CollectionCommand.Save ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Changed(3, null, deck, 0, ELIGIBLE));
            case CollectionCommand.Activate ignored -> CompletableFuture.completedFuture(
                    new CollectionReply.Changed(5, deck.id(), null, 0, ELIGIBLE));
            default -> throw new AssertionError(command);
        }).toCompletableFuture();

        assertThrows(java.util.concurrent.CompletionException.class, result::join);
    }

    @Test
    void rejectedSaveNeverAttemptsActivation(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("stale.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));
        var deck = imports.load("stale");
        var commands = new ArrayList<CollectionCommand>();
        var result = imports.saveThenActivate(7, deck, command -> {
            commands.add(command);
            return CompletableFuture.completedFuture(new CollectionReply.Rejected(
                    CollectionError.STALE, 8, ELIGIBLE));
        }).toCompletableFuture().join();

        assertEquals(List.of(new CollectionCommand.Save(7, deck)), commands);
        assertNull(result.saved());
        assertEquals(CollectionError.STALE, ((CollectionReply.Rejected) result.activation()).error());
    }

    @Test
    void traversalCannotSelectAnOutsideDeck(@TempDir Path parent) throws IOException {
        var dir = Files.createDirectory(parent.resolve("decks"));
        Files.writeString(parent.resolve("outside.ydk"), "#main\n1\n");
        var imports = new DeckImportService(new DeckRegistry(dir));

        assertThrows(IOException.class, () -> imports.load("../outside"));
    }
}

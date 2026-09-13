package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DuelDeckUploadTest {
    @TempDir Path temp;

    private static Deck deck(int first) {
        return new Deck(IntStream.range(first, first + 40).boxed().toList(), List.of(first + 100));
    }

    private static DuelDeckPayload roundTrip(DuelDeckPayload payload) {
        var buffer = Unpooled.buffer();
        try {
            DuelDeckPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = DuelDeckPayload.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            assertEquals(payload, decoded);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    @Test
    void differentClientsCanUploadTheSameFilenameWithoutAnyServerFile() throws IOException {
        var aliceFiles = DeckRegistry.open(temp.resolve("alice"));
        var bobFiles = DeckRegistry.open(temp.resolve("bob"));
        Files.writeString(aliceFiles.dir().resolve("My Deck.ydk"), ydk(deck(1000)));
        Files.writeString(bobFiles.dir().resolve("My Deck.ydk"), ydk(deck(2000)));
        var alice = UUID.randomUUID();
        var bob = UUID.randomUUID();
        var manager = new DuelManager(); // No server registry or native engine needed to select/resolve decks.
        manager.setPlayerCurrentDeck(alice, roundTrip(new DuelDeckPayload("My Deck", aliceFiles.load("My Deck"))));
        manager.setPlayerCurrentDeck(bob, roundTrip(new DuelDeckPayload("My Deck", bobFiles.load("My Deck"))));
        Files.delete(aliceFiles.dir().resolve("My Deck.ydk"));
        assertEquals(deck(1000), manager.resolveDeck(alice));
        assertEquals(deck(2000), manager.resolveDeck(bob));
        assertEquals("My Deck", manager.getPlayerCurrentDeck(alice).orElseThrow());
        manager.clearPlayerCurrentDeck(alice);
        assertThrows(IOException.class, () -> manager.resolveDeck(alice));
        assertTrue(manager.getPlayerCurrentDeck(alice).isEmpty());
        assertEquals(deck(2000), manager.resolveDeck(bob));
    }

    @Test
    void invalidUploadPreservesPreviousSelectionAndValidUploadReplacesIt() throws IOException {
        var manager = new DuelManager();
        var player = UUID.randomUUID();
        manager.setPlayerCurrentDeck(player, new DuelDeckPayload("original", deck(1000)));
        for (var invalid : List.of(new Deck(List.of(1), List.of()),
                new Deck(java.util.Collections.nCopies(40, 1), List.of()),
                new Deck(deck(1000).main(), List.of(0, -1)))) {
            var payload = roundTrip(new DuelDeckPayload("invalid", invalid));
            assertThrows(IllegalArgumentException.class, () -> manager.setPlayerCurrentDeck(player, payload));
            assertEquals(deck(1000), manager.resolveDeck(player));
            assertEquals("original", manager.getPlayerCurrentDeck(player).orElseThrow());
        }
        manager.setPlayerCurrentDeck(player, new DuelDeckPayload("replacement", deck(2000)));
        assertEquals(deck(2000), manager.resolveDeck(player));
    }

    @Test
    void uploadSnapshotCannotBeMutatedThroughCallerLists() {
        var main = new ArrayList<>(deck(1000).main());
        var extra = new ArrayList<>(deck(1000).extra());
        var payload = new DuelDeckPayload("snapshot", new Deck(main, extra));
        main.clear();
        extra.clear();
        assertEquals(deck(1000), payload.deck());
        assertThrows(UnsupportedOperationException.class, () -> payload.deck().main().clear());
        assertThrows(UnsupportedOperationException.class, () -> payload.deck().extra().clear());
    }

    @Test
    void maximumSizedDeckAndUnicodeNameRoundTrip() {
        roundTrip(new DuelDeckPayload("Dragon デッキ", new Deck(IntStream.range(1, 61).boxed().toList(),
                IntStream.range(61, 76).boxed().toList())));
    }

    @Test
    void malformedCountsAreRejectedBeforeReadingCardData() {
        for (int count : new int[]{-1, 61, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeUtf("bad").writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> DuelDeckPayload.STREAM_CODEC.decode(buf));
            } finally { buf.release(); }
        }
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUtf("bad").writeVarInt(0).writeVarInt(16);
            assertThrows(IllegalArgumentException.class, () -> DuelDeckPayload.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    @Test
    void invalidNamesAndOversizedSectionsCannotBeEncoded() {
        for (String name : List.of("", " ", "bad\nname", "x".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> new DuelDeckPayload(name, deck(1000)));
        }
        assertThrows(IllegalArgumentException.class, () -> new DuelDeckPayload("big",
                new Deck(IntStream.range(1, 62).boxed().toList(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> new DuelDeckPayload("big",
                new Deck(deck(1000).main(), IntStream.range(1, 17).boxed().toList())));
    }

    private static String ydk(Deck deck) {
        return "#main\n" + deck.main().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("\n"))
                + "\n#extra\n" + deck.extra().getFirst() + "\n!side\n";
    }
}

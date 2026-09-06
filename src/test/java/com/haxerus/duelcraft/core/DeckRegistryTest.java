package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckRegistryTest {

    private static final String SIMPLE_YDK = """
            #main
            89631139
            #extra
            7391448
            """;

    @Test
    void listsYdkFilesWithoutExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("alpha.ydk"), SIMPLE_YDK);
        Files.writeString(dir.resolve("beta.ydk"), SIMPLE_YDK);
        Files.writeString(dir.resolve("not_a_deck.txt"), "ignored");

        DeckRegistry registry = new DeckRegistry(dir);
        List<String> names = registry.listDeckNames();
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
        assertFalse(names.contains("not_a_deck"));
        assertEquals(2, names.size());
    }

    @Test
    void loadByNameReturnsParsedDeck(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("test.ydk"), SIMPLE_YDK);
        DeckRegistry registry = new DeckRegistry(dir);

        Deck loaded = registry.load("test");
        assertEquals(List.of(89631139), loaded.main());
        assertEquals(List.of(7391448), loaded.extra());
    }

    @Test
    void loadMissingDeckThrowsIOException(@TempDir Path dir) {
        DeckRegistry registry = new DeckRegistry(dir);
        assertThrows(IOException.class, () -> registry.load("nope"));
    }

    @Test
    void rescansOnEachListCall(@TempDir Path dir) throws IOException {
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(), registry.listDeckNames());

        Files.writeString(dir.resolve("late.ydk"), SIMPLE_YDK);
        assertEquals(List.of("late"), registry.listDeckNames());
    }

    @Test
    void rereadsFileOnEachLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hot.ydk");
        Files.writeString(file, "#main\n111\n");
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(111), registry.load("hot").main());

        Files.writeString(file, "#main\n222\n");
        assertEquals(List.of(222), registry.load("hot").main());
    }

    @Test
    void openCreatesDirectoryIfMissing(@TempDir Path parent) {
        Path target = parent.resolve("decks_subdir");
        assertFalse(Files.exists(target));
        DeckRegistry registry = DeckRegistry.open(target);
        assertTrue(Files.isDirectory(target));
        assertEquals(target, registry.dir());
    }

    @Test
    void listOnEmptyDirReturnsEmptyList(@TempDir Path dir) {
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(), registry.listDeckNames());
    }
}

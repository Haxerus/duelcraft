package com.haxerus.duelcraft.core;

import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.collection.DeckListLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Lists and loads {@link Deck}s from a directory of {@code .ydk} files. No caching. */
public record DeckRegistry(Path dir) {

    private static final String EXT = ".ydk";

    /** Opens the registry at {@code dir}, creating the directory if it is missing. */
    public static DeckRegistry open(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create decks directory: " + dir, e);
        }
        return new DeckRegistry(dir);
    }

    /** Returns alphabetized names (no extension) of every {@code .ydk} file in the directory. */
    public List<String> listDeckNames() {
        try (Stream<Path> stream = Files.list(dir)) {
            List<String> names = new ArrayList<>();
            stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase().endsWith(EXT))
                .map(n -> n.substring(0, n.length() - EXT.length()))
                .forEach(names::add);
            Collections.sort(names);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list decks in " + dir, e);
        }
    }

    /** Loads {@code <name>.ydk} from the registry directory, re-reading on every call. */
    public Deck load(String name) throws IOException {
        return loadList(name).toDuelDeck();
    }

    /** Loads a complete list selected from {@link #listDeckNames()}, without following it outside this directory. */
    public DeckList loadList(String name) throws IOException {
        if (name == null || !listDeckNames().contains(name)) {
            throw new IOException("Deck file is not listed in " + dir + ": " + name);
        }
        Path root = dir.toRealPath();
        Path file = dir.resolve(name + EXT).toRealPath();
        if (!root.equals(file.getParent())) {
            throw new IOException("Deck file resolves outside " + dir + ": " + name);
        }
        return DeckListLoader.loadFromFile(file);
    }
}

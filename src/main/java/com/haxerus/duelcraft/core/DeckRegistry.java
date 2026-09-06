package com.haxerus.duelcraft.core;

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
        Path file = dir.resolve(name + EXT);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Deck file not found: " + file);
        }
        return DeckLoader.loadFromFile(file);
    }
}

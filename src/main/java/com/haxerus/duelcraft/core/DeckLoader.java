package com.haxerus.duelcraft.core;

import com.haxerus.duelcraft.collection.DeckListLoader;

import java.io.IOException;
import java.nio.file.Path;

/** Parser for EDOPro {@code .ydk} files. Pure: no validation against the card database. */
public final class DeckLoader {

    private DeckLoader() {}

    /** Reads the given file as UTF-8 and parses it. */
    public static Deck loadFromFile(Path path) throws IOException, DeckParseException {
        return DeckListLoader.loadFromFile(path).toDuelDeck();
    }

    /**
     * Parses a {@code .ydk} string. Recognized headers: {@code #main}, {@code #extra}, {@code !side}.
     * Other {@code #}-lines are comments. Integer lines are passcodes for the current section.
     * Whitespace and blank lines are tolerated.
     */
    public static Deck parseYdk(String content) {
        return DeckListLoader.parseYdk(content).toDuelDeck();
    }

    /** Thrown when a {@code .ydk} file cannot be parsed. */
    public static final class DeckParseException extends RuntimeException {
        private final int line;

        public DeckParseException(String message, int line) {
            super(message + " (line " + line + ")");
            this.line = line;
        }

        public int getLine() { return line; }
    }
}

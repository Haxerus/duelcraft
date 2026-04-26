package com.haxerus.duelcraft.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser for EDOPro {@code .ydk} files. Pure: no validation against the card database. */
public final class DeckLoader {

    private DeckLoader() {}

    /** Reads the given file as UTF-8 and parses it. */
    public static Deck loadFromFile(Path path) throws IOException, DeckParseException {
        return parseYdk(Files.readString(path));
    }

    /**
     * Parses a {@code .ydk} string. Recognized headers: {@code #main}, {@code #extra}, {@code !side}.
     * Other {@code #}-lines are comments. Integer lines are passcodes for the current section.
     * Whitespace and blank lines are tolerated.
     */
    public static Deck parseYdk(String content) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> sideSink = new ArrayList<>();

        // Strip leading UTF-8 BOM if present
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        Section section = Section.NONE;
        boolean sawMain = false;
        int lineNum = 0;

        for (String raw : content.split("\\R", -1)) {
            lineNum++;
            String line = raw.strip();
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                String header = line.toLowerCase();
                if (header.equals("#main")) { section = Section.MAIN; sawMain = true; }
                else if (header.equals("#extra")) section = Section.EXTRA;
                // any other #-line is a comment
                continue;
            }
            if (line.equals("!side")) {
                section = Section.SIDE;
                continue;
            }

            int code;
            try {
                code = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                throw new DeckParseException("Expected card passcode, got: " + line, lineNum);
            }
            if (code <= 0) {
                throw new DeckParseException("Passcode must be positive, got: " + code, lineNum);
            }
            switch (section) {
                case MAIN -> main.add(code);
                case EXTRA -> extra.add(code);
                case SIDE -> sideSink.add(code);
                case NONE -> throw new DeckParseException(
                        "Card before any section header", lineNum);
            }
        }

        if (!sawMain) {
            throw new DeckParseException("Missing #main section", lineNum);
        }
        return new Deck(List.copyOf(main), List.copyOf(extra));
    }

    private enum Section { NONE, MAIN, EXTRA, SIDE }

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

package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.DeckLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser for complete EDOPro {@code .ydk} lists. Pure: no card database validation. */
public final class DeckListLoader {
    private DeckListLoader() { }

    public static DeckList loadFromFile(Path path) throws IOException, DeckLoader.DeckParseException {
        return parseYdk(Files.readString(path));
    }

    public static DeckList parseYdk(String content) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> side = new ArrayList<>();

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
                throw new DeckLoader.DeckParseException("Expected card passcode, got: " + line, lineNum);
            }
            if (code <= 0) {
                throw new DeckLoader.DeckParseException("Passcode must be positive, got: " + code, lineNum);
            }
            switch (section) {
                case MAIN -> main.add(code);
                case EXTRA -> extra.add(code);
                case SIDE -> side.add(code);
                case NONE -> throw new DeckLoader.DeckParseException("Card before any section header", lineNum);
            }
            if ((long) main.size() + extra.size() + side.size() > CollectionLimits.DRAFT_CARDS) {
                throw new DeckLoader.DeckParseException("Draft exceeds the 512-card editor limit", lineNum);
            }
        }

        if (!sawMain) {
            throw new DeckLoader.DeckParseException("Missing #main section", lineNum);
        }
        return new DeckList(List.copyOf(main), List.copyOf(extra), List.copyOf(side));
    }

    private enum Section { NONE, MAIN, EXTRA, SIDE }
}

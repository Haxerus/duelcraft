package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckLoaderTest {

    @Test
    void parsesWellFormedYdk() {
        String ydk = """
                #created by ...
                #main
                89631139
                55144522
                #extra
                7391448
                29981921
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(89631139, 55144522), deck.main());
        assertEquals(List.of(7391448, 29981921), deck.extra());
    }

    @Test
    void ignoresSideDeck() {
        String ydk = """
                #main
                111
                #extra
                222
                !side
                999
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
        assertEquals(List.of(222), deck.extra());
    }

    @Test
    void toleratesBlankLinesAndWhitespace() {
        String ydk = """
                #main

                   89631139\s
                \t55144522
                #extra
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(89631139, 55144522), deck.main());
        assertEquals(List.of(), deck.extra());
    }

    @Test
    void allowsEmptyExtraDeck() {
        String ydk = """
                #main
                111
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
        assertEquals(List.of(), deck.extra());
    }

    @Test
    void rejectsFileWithoutMainSection() {
        String ydk = """
                #extra
                111
                """;
        DeckLoader.DeckParseException ex = assertThrows(
                DeckLoader.DeckParseException.class,
                () -> DeckLoader.parseYdk(ydk));
        assertTrue(ex.getMessage().toLowerCase().contains("main"));
    }

    @Test
    void rejectsNonIntegerLineInMain() {
        String ydk = """
                #main
                89631139
                not_a_number
                """;
        DeckLoader.DeckParseException ex = assertThrows(
                DeckLoader.DeckParseException.class,
                () -> DeckLoader.parseYdk(ydk));
        assertEquals(3, ex.getLine());
    }

    @Test
    void treatsUnknownHashLineAsComment() {
        String ydk = """
                #created by Player1
                #note: this is a goat deck
                #main
                111
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
    }

    @Test
    void stripsLeadingByteOrderMark() {
        String ydk = "﻿#main\n89631139\n";
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(89631139), deck.main());
    }
}

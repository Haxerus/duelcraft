package com.haxerus.duelcraft.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deck legality at challenge time, the subset of edopro's {@code CheckDeckSize}/{@code CheckDeckContent}
 * ({@code deck_manager.cpp:204-258}) a host with no card database can decide: main and extra sizes and
 * the copy limit.
 *
 * <p>Not checked, and why: forbidden types per rule set ({@code DUEL_MODE_MR*_FORB}), extra-deck
 * membership, legend and skill limits and banlists all need each card's type, which lives in the card
 * database the server never opens (the thick C++ bridge owns it). Copies are counted by passcode, so
 * alternate artworks that share an alias are not collapsed the way edopro collapses them.
 * {@code .ydk} side decks are parsed but dropped by {@link DeckLoader}, so the side size is not checked.
 */
public final class DeckValidator {

    public static final int MAIN_MIN = 40;
    public static final int MAIN_MAX = 60;
    public static final int EXTRA_MAX = 15;
    public static final int MAX_COPIES = 3;

    private DeckValidator() {}

    /**
     * Everything wrong with {@code deck}, one sentence each; empty when it may be used.
     * {@code rule} selects no checks yet — the rule-set forbidden types need the card database.
     */
    public static List<String> problems(Deck deck, DuelRule rule) {
        List<String> problems = new ArrayList<>();

        int main = deck.main().size();
        if (main < MAIN_MIN || main > MAIN_MAX) {
            problems.add("main deck has " + main + " cards, needs " + MAIN_MIN + "-" + MAIN_MAX);
        }
        int extra = deck.extra().size();
        if (extra > EXTRA_MAX) {
            problems.add("extra deck has " + extra + " cards, at most " + EXTRA_MAX);
        }

        Map<Integer, Integer> copies = new LinkedHashMap<>();
        for (int code : deck.main()) copies.merge(code, 1, Integer::sum);
        for (int code : deck.extra()) copies.merge(code, 1, Integer::sum);
        copies.forEach((code, count) -> {
            if (code <= 0) problems.add("card passcode must be positive: " + code);
            if (count > MAX_COPIES) {
                problems.add(count + " copies of card " + code + ", at most " + MAX_COPIES);
            }
        });

        return problems;
    }
}

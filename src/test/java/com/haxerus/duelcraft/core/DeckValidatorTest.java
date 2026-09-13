package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckValidatorTest {

    /** {@code count} distinct passcodes starting at {@code first}. */
    private static List<Integer> distinct(int first, int count) {
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) codes.add(first + i);
        return codes;
    }

    private static Deck deck(int mainSize, int extraSize) {
        return new Deck(distinct(1000, mainSize), distinct(9000, extraSize));
    }

    @Test
    void legalDeckHasNoProblems() {
        assertEquals(List.of(), DeckValidator.problems(deck(40, 15), DuelRule.MR5));
    }

    @Test
    void mainDeckBelowMinimumIsReported() {
        var problems = DeckValidator.problems(deck(39, 0), DuelRule.MR5);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("39"), problems.getFirst());
        assertTrue(problems.getFirst().toLowerCase().contains("main"), problems.getFirst());
    }

    @Test
    void mainDeckAboveMaximumIsReported() {
        var problems = DeckValidator.problems(deck(61, 0), DuelRule.MR5);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("61"), problems.getFirst());
    }

    @Test
    void mainDeckBoundsAreInclusive() {
        assertEquals(List.of(), DeckValidator.problems(deck(40, 0), DuelRule.MR5));
        assertEquals(List.of(), DeckValidator.problems(deck(60, 0), DuelRule.MR5));
    }

    @Test
    void extraDeckAboveFifteenIsReported() {
        var problems = DeckValidator.problems(deck(40, 16), DuelRule.MR5);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().toLowerCase().contains("extra"), problems.getFirst());
    }

    @Test
    void fourCopiesOfOneCodeIsReported() {
        List<Integer> main = new ArrayList<>(distinct(1000, 36));
        main.addAll(List.of(555, 555, 555, 555));
        var problems = DeckValidator.problems(new Deck(main, List.of()), DuelRule.MR5);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("555"), problems.getFirst());
        assertTrue(problems.getFirst().contains("4"), problems.getFirst());
    }

    @Test
    void threeCopiesOfOneCodeIsLegal() {
        List<Integer> main = new ArrayList<>(distinct(1000, 37));
        main.addAll(List.of(555, 555, 555));
        assertEquals(List.of(), DeckValidator.problems(new Deck(main, List.of()), DuelRule.MR5));
    }

    @Test
    void copiesAreCountedAcrossMainAndExtra() {
        List<Integer> main = new ArrayList<>(distinct(1000, 38));
        main.addAll(List.of(555, 555));
        var problems = DeckValidator.problems(new Deck(main, List.of(555, 555)), DuelRule.MR5);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("555"), problems.getFirst());
    }

    @Test
    void everyBrokenRuleIsReportedAtOnce() {
        var problems = DeckValidator.problems(new Deck(List.of(555, 555, 555, 555), distinct(9000, 16)),
                DuelRule.MR5);
        assertEquals(3, problems.size(), problems.toString());
    }
}

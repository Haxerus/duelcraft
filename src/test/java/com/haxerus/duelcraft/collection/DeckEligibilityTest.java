package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.data.CardCatalog;
import com.haxerus.duelcraft.server.collection.CollectionTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeckEligibilityTest {
    private DeckEligibility.Report check(DeckList list, Map<Integer, Long> counts, Map<Integer, CardCatalog.Facts> facts) {
        return DeckEligibility.check(list, counts, facts, DuelRule.MR5);
    }

    @Test void fullOwnedListIsEligible() {
        assertTrue(check(CollectionTestData.deck(UUID.randomUUID()).cards(), CollectionTestData.owned(),
                CollectionTestData.facts()).eligible());
    }

    @Test void countsExactPasscodesAcrossAllSectionsOnce() {
        var list = new DeckList(CollectionTestData.deck(UUID.randomUUID()).cards().main(), List.of(), List.of(1, 1, 1, 99));
        var report = check(list, CollectionTestData.owned(), CollectionTestData.facts());
        assertFalse(report.eligible());
        assertEquals(Map.of(1, 3, 99, 1), report.missing());
        assertEquals(1, report.problems().stream().filter(issue -> issue.key().equals("duelcraft.collection.issue.copies")).count());
        assertTrue(report.problems().contains(new DeckEligibility.Issue("duelcraft.collection.issue.unknown", 99, 0, 0)));
    }

    @ParameterizedTest
    @CsvSource({"1,true,false", "2,true,false", "4,true,false", "65,false,true", "8193,false,true",
            "8388609,false,true", "67108865,false,true", "16385,false,false", "0,false,false", "64,false,false"})
    void enforcesPlayableCategoryAndPlacement(int type, boolean mainAllowed, boolean extraAllowed) {
        var facts = new HashMap<>(CollectionTestData.facts());
        facts.put(1, new CardCatalog.Facts(1, type));
        facts.put(41, new CardCatalog.Facts(41, type));
        var owned = new HashMap<>(CollectionTestData.owned());
        owned.put(41, 1L);
        var main = CollectionTestData.deck(UUID.randomUUID()).cards().main();
        assertEquals(mainAllowed, check(new DeckList(main, List.of(), List.of()), owned, facts).eligible());
        facts.put(1, new CardCatalog.Facts(1, 1));
        assertEquals(extraAllowed, check(new DeckList(main, List.of(41), List.of()), owned, facts).eligible());
        assertEquals(mainAllowed || extraAllowed, check(new DeckList(main, List.of(), List.of(41)), owned, facts).eligible());
    }

    @Test void reportsStructuralProblemsIndependentlyOfMissingCopies() {
        var report = check(new DeckList(List.of(1), java.util.Collections.nCopies(16, 2), java.util.Collections.nCopies(16, 3)),
                Map.of(), CollectionTestData.facts());
        assertEquals(Map.of(1, 1, 2, 16, 3, 16), report.missing());
        assertTrue(report.problems().contains(new DeckEligibility.Issue("duelcraft.collection.issue.main_size", 0, 1, 40)));
        assertTrue(report.problems().contains(new DeckEligibility.Issue("duelcraft.collection.issue.extra_size", 0, 16, 15)));
        assertTrue(report.problems().contains(new DeckEligibility.Issue("duelcraft.collection.issue.side_size", 0, 16, 15)));
    }

    @Test void truncatesIssuesWithoutLosingMissingCopiesOrEligibility() {
        var cards = java.util.stream.IntStream.rangeClosed(100, 200).boxed().toList();
        var report = check(new DeckList(cards, List.of(), List.of()), Map.of(), Map.of());
        assertEquals(64, report.problems().size());
        assertEquals(101, report.missing().size());
        assertTrue(report.moreProblems());
        assertFalse(report.eligible());
        assertFalse(new DeckEligibility.Report(List.of(), Map.of(), true).eligible());
    }

    @Test void reportDefensivelyCopiesResults() {
        var issues = new ArrayList<DeckEligibility.Issue>();
        var missing = new HashMap<Integer, Integer>();
        missing.put(1, 1);
        var report = new DeckEligibility.Report(issues, missing, false);
        issues.add(new DeckEligibility.Issue("problem", 1, 1, 0));
        missing.clear();
        assertTrue(report.problems().isEmpty());
        assertEquals(Map.of(1, 1), report.missing());
        assertThrows(UnsupportedOperationException.class, () -> report.problems().clear());
    }
}

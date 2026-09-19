package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.collection.DeckList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.haxerus.duelcraft.client.collection.CardSearch.*;
import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class CardSearchTest {
    private static final DeckList EMPTY = new DeckList(List.of(), List.of(), List.of());

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4, 8, 16, 32, 64, 512, 1024, 3 | 64})
    void alternateFormatsRequireOptInEvenForOwnedExactPasscodes(int scope) {
        var card = new CardInfo(99, "Unusual", "", TYPE_MONSTER, 0, 0, 1, 1, 1, scope);
        var normal = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.OWNED, Sort.NAME, false);
        var alternate = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.OWNED, Sort.NAME, true);
        assertTrue(CardSearch.search(List.of(card), "99", normal, Map.of(99, 1L), EMPTY).isEmpty());
        assertEquals(List.of(card), CardSearch.search(List.of(card), "99", alternate, Map.of(99, 1L), EMPTY));
        assertTrue(CardSearch.search(List.of(card), "99", alternate, Map.of(), EMPTY).isEmpty());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 256, 259})
    void standardAndPrereleaseScopesRemainVisible(int scope) {
        var card = new CardInfo(99, "Standard", "", TYPE_MONSTER, 0, 0, 1, 1, 1, scope);
        assertEquals(List.of(card), search(List.of(card), "", Filters.ALL));
    }

    @Test void hiddenCardsAndTokensStayExcludedWhenAlternateFormatsAreEnabled() {
        var hidden = new CardInfo(99, "Hidden", "", TYPE_MONSTER, 0, 0, 1, 1, 1, 4096 | 3);
        var token = new CardInfo(100, "Token", "", TYPE_MONSTER | TYPE_TOKEN, 0, 0, 1, 1, 1, 4);
        var alternate = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.ALL, Sort.NAME, true);
        assertTrue(search(List.of(hidden, token), "", alternate).isEmpty());
    }

    private static CardInfo card(int code, String name, int type, int atk, int def, int level) {
        return new CardInfo(code, name, "Draw two cards.", type, atk, def, level, RACE_DRAGON, ATTRIBUTE_LIGHT);
    }

    private static Filters masks(int category, int subtype, int properties, long race, int attribute) {
        return new Filters(category, subtype, properties, race, attribute, Measure.ANY,
                null, null, null, null, Ownership.ALL, Sort.NAME);
    }

    private static List<CardInfo> search(List<CardInfo> cards, String query, Filters filters) {
        return CardSearch.search(cards, query, filters, Map.of(), EMPTY);
    }

    @Test void searchesEffectTextWithoutRequiringNameMatch() {
        var card = card(10, "Lantern", TYPE_SPELL, 0, 0, 0);
        assertEquals(List.of(card), search(List.of(card), "dRaW tWo", Filters.ALL));
    }

    @Test void searchesNamesCaseInsensitivelyAndTrimsQuery() {
        var card = card(10, "Lantern", TYPE_SPELL, 0, 0, 0);
        assertEquals(List.of(card), search(List.of(card), "  LANT  ", Filters.ALL));
        assertTrue(search(List.of(card), "unrelated", Filters.ALL).isEmpty());
    }

    @Test void numericPasscodesMatchExactly() {
        var card = card(1234, "Lantern", TYPE_SPELL, 0, 0, 0);
        assertEquals(List.of(card), search(List.of(card), "1234", Filters.ALL));
        assertTrue(search(List.of(card), "123", Filters.ALL).isEmpty());
        assertTrue(search(List.of(card), "999999999999999999999", Filters.ALL).isEmpty());
    }

    @Test void tokensNeverAppearEvenForExactPasscodeOrOwnedQueries() {
        var token = card(1234, "Token", TYPE_MONSTER | TYPE_TOKEN, 0, 0, 1);
        var regular = card(5678, "Regular", TYPE_MONSTER, 0, 0, 1);
        assertTrue(CardSearch.search(List.of(token, regular), "1234", Filters.ALL,
                Map.of(1234, 1L), EMPTY).isEmpty());
        var owned = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.OWNED, Sort.NAME);
        assertEquals(List.of(regular), CardSearch.search(List.of(token, regular), "", owned,
                Map.of(1234, 1L, 5678, 1L), EMPTY));
    }

    @Test void percentAndUnderscoreAreLiteralText() {
        var literal = card(1, "100%_Magic", TYPE_SPELL, 0, 0, 0);
        var other = card(2, "100XXMagic", TYPE_SPELL, 0, 0, 0);
        assertEquals(List.of(literal), search(List.of(other, literal), "%_", Filters.ALL));
    }

    @Test void matchingDoesNotDependOnDefaultLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var card = card(1, "INSIGHT", TYPE_SPELL, 0, 0, 0);
            assertEquals(List.of(card), search(List.of(card), "insight", Filters.ALL));
        } finally {
            Locale.setDefault(previous);
        }
    }

    static Stream<Arguments> maskCases() {
        int type = TYPE_MONSTER | TYPE_FUSION | TYPE_EFFECT | TYPE_TUNER;
        return Stream.of(
                Arguments.of(type, masks(0, 0, 0, 0, 0), true),
                Arguments.of(type, masks(TYPE_MONSTER | TYPE_SPELL, 0, 0, 0, 0), true),
                Arguments.of(type, masks(TYPE_TRAP, 0, 0, 0, 0), false),
                Arguments.of(TYPE_SPELL, masks(TYPE_SPELL | TYPE_TRAP, 0, 0, 0, 0), true),
                Arguments.of(TYPE_TRAP, masks(TYPE_SPELL | TYPE_TRAP, 0, 0, 0, 0), true),
                Arguments.of(type, masks(0, TYPE_FUSION | TYPE_SYNCHRO, 0, 0, 0), true),
                Arguments.of(TYPE_MONSTER | TYPE_SYNCHRO, masks(0, TYPE_FUSION | TYPE_SYNCHRO, 0, 0, 0), true),
                Arguments.of(type, masks(0, TYPE_XYZ | TYPE_LINK, 0, 0, 0), false),
                Arguments.of(type, masks(0, 0, TYPE_EFFECT | TYPE_TUNER, 0, 0), true),
                Arguments.of(TYPE_MONSTER | TYPE_EFFECT, masks(0, 0, TYPE_EFFECT | TYPE_TUNER, 0, 0), false),
                Arguments.of(type, masks(0, 0, 0, RACE_DRAGON | RACE_WARRIOR, 0), true),
                Arguments.of(type, masks(0, 0, 0, RACE_YOKAI | RACE_DRAGON, 0), true),
                Arguments.of(type, masks(0, 0, 0, RACE_YOKAI, 0), false),
                Arguments.of(type, masks(0, 0, 0, 0, ATTRIBUTE_LIGHT | ATTRIBUTE_DARK), true),
                Arguments.of(type, masks(0, 0, 0, 0, ATTRIBUTE_DARK), false),
                Arguments.of(type, masks(TYPE_MONSTER, TYPE_FUSION, TYPE_EFFECT | TYPE_TUNER,
                        RACE_DRAGON, ATTRIBUTE_LIGHT), true),
                Arguments.of(type, masks(TYPE_MONSTER, TYPE_FUSION, TYPE_EFFECT | TYPE_TUNER,
                        RACE_WARRIOR, ATTRIBUTE_LIGHT), false));
    }

    @ParameterizedTest
    @MethodSource("maskCases")
    void alternativeMasksUseOrAndRequiredPropertiesUseAnd(int type, Filters filters, boolean expected) {
        var card = card(1, "Card", type, 1000, 1000, 4);
        assertEquals(expected, !search(List.of(card), "", filters).isEmpty());
    }

    @Test void raceMasksRetainBitsAboveTheIntRange() {
        var card = new CardInfo(1, "Yokai", "", TYPE_MONSTER, 0, 0, 4, RACE_YOKAI, ATTRIBUTE_DARK);
        assertEquals(List.of(card), search(List.of(card), "", masks(0, 0, 0, RACE_YOKAI, 0)));
        assertTrue(search(List.of(card), "", masks(0, 0, 0, RACE_DRAGON, 0)).isEmpty());
    }

    static Stream<Arguments> measureCases() {
        return Stream.of(
                Arguments.of(TYPE_MONSTER, 4, Measure.LEVEL, true),
                Arguments.of(TYPE_MONSTER | TYPE_XYZ, 4, Measure.LEVEL, false),
                Arguments.of(TYPE_MONSTER | TYPE_LINK, 4, Measure.LEVEL, false),
                Arguments.of(TYPE_SPELL, 4, Measure.LEVEL, false),
                Arguments.of(TYPE_MONSTER, 3, Measure.LEVEL, false),
                Arguments.of(TYPE_MONSTER, 6, Measure.LEVEL, false),
                Arguments.of(TYPE_MONSTER, (8 << 24) | (1 << 16) | 5, Measure.LEVEL, true),
                Arguments.of(TYPE_MONSTER | TYPE_XYZ, 4, Measure.RANK, true),
                Arguments.of(TYPE_MONSTER, 4, Measure.RANK, false),
                Arguments.of(TYPE_MONSTER | TYPE_LINK, 4, Measure.RANK, false),
                Arguments.of(TYPE_MONSTER | TYPE_LINK, 5, Measure.LINK, true),
                Arguments.of(TYPE_MONSTER, 4, Measure.LINK, false),
                Arguments.of(TYPE_MONSTER | TYPE_XYZ, 4, Measure.LINK, false),
                Arguments.of(TYPE_MONSTER, 4, Measure.ANY, true),
                Arguments.of(TYPE_MONSTER | TYPE_XYZ, 4, Measure.ANY, true),
                Arguments.of(TYPE_MONSTER | TYPE_LINK, 5, Measure.ANY, true),
                Arguments.of(TYPE_MONSTER, 3, Measure.ANY, false),
                Arguments.of(TYPE_MONSTER | TYPE_XYZ, 6, Measure.ANY, false),
                Arguments.of(TYPE_MONSTER | TYPE_LINK, 3, Measure.ANY, false),
                Arguments.of(TYPE_SPELL, 4, Measure.ANY, false),
                Arguments.of(TYPE_TRAP, 4, Measure.ANY, false));
    }

    @ParameterizedTest
    @MethodSource("measureCases")
    void measuresRequireTheirMonsterKindAndUseLowByte(int type, int level, Measure measure, boolean expected) {
        var filters = new Filters(0, 0, 0, 0, 0, measure, new Range(4, 5),
                null, null, null, Ownership.ALL, Sort.NAME);
        assertEquals(expected, !search(List.of(card(1, "Card", type, 0, 0, level)), "", filters).isEmpty());
    }

    @Test void measureSelectionStillRestrictsKindWithoutARange() {
        var filters = new Filters(0, 0, 0, 0, 0, Measure.RANK,
                null, null, null, null, Ownership.ALL, Sort.NAME);
        var xyz = card(1, "Xyz", TYPE_MONSTER | TYPE_XYZ, 0, 0, 4);
        assertEquals(List.of(xyz), search(List.of(card(2, "Level", TYPE_MONSTER, 0, 0, 4), xyz), "", filters));
    }

    static Stream<Arguments> statCases() {
        return Stream.of(
                Arguments.of("atk", TYPE_MONSTER, 1000, 0, 0, true),
                Arguments.of("atk", TYPE_MONSTER, 2000, 0, 0, true),
                Arguments.of("atk", TYPE_MONSTER, 999, 0, 0, false),
                Arguments.of("atk", TYPE_MONSTER, 2001, 0, 0, false),
                Arguments.of("atk", TYPE_MONSTER, -1, 0, 0, false),
                Arguments.of("atk", TYPE_SPELL, 1500, 0, 0, false),
                Arguments.of("atk", TYPE_MONSTER | TYPE_LINK, 1500, 0, 0, true),
                Arguments.of("def", TYPE_MONSTER, 0, 1500, 0, true),
                Arguments.of("def", TYPE_MONSTER, 0, -1, 0, false),
                Arguments.of("def", TYPE_TRAP, 0, 1500, 0, false),
                Arguments.of("def", TYPE_MONSTER | TYPE_LINK, 0, 1500, 0, false),
                Arguments.of("scale", TYPE_MONSTER | TYPE_PENDULUM, 0, 0, (3 << 24) | (8 << 16), true),
                Arguments.of("scale", TYPE_MONSTER | TYPE_PENDULUM, 0, 0, (8 << 24) | (5 << 16), true),
                Arguments.of("scale", TYPE_MONSTER | TYPE_PENDULUM, 0, 0, (2 << 24) | (6 << 16), false),
                Arguments.of("scale", TYPE_MONSTER, 0, 0, (3 << 24) | (4 << 16), false));
    }

    @ParameterizedTest
    @MethodSource("statCases")
    void numericStatsExcludeInapplicableAndUnknownValues(String stat, int type, int atk, int def,
                                                       int level, boolean expected) {
        var stats = new Range(1000, 2000);
        var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY, null,
                stat.equals("atk") ? stats : null, stat.equals("def") ? stats : null,
                stat.equals("scale") ? new Range(3, 5) : null, Ownership.ALL, Sort.NAME);
        assertEquals(expected, !search(List.of(card(1, "Card", type, atk, def, level)), "", filters).isEmpty());
    }

    @Test void unknownStatsCannotMatchEvenRangesContainingNegativeValues() {
        var card = card(1, "Unknown", TYPE_MONSTER, -1, -1, 4);
        var range = new Range(-10, 10);
        var atk = new Filters(0, 0, 0, 0, 0, Measure.ANY, null, range, null, null, Ownership.ALL, Sort.NAME);
        var def = new Filters(0, 0, 0, 0, 0, Measure.ANY, null, null, range, null, Ownership.ALL, Sort.NAME);
        assertTrue(search(List.of(card), "", atk).isEmpty());
        assertTrue(search(List.of(card), "", def).isEmpty());
    }

    static Stream<Arguments> ownershipCases() {
        return Stream.of(
                Arguments.of(Ownership.ALL, 0L, true),
                Arguments.of(Ownership.OWNED, 0L, false),
                Arguments.of(Ownership.OWNED, 1L, true),
                Arguments.of(Ownership.MISSING, 0L, true),
                Arguments.of(Ownership.MISSING, 1L, true),
                Arguments.of(Ownership.MISSING, 2L, false),
                Arguments.of(Ownership.MISSING, 3L, false),
                Arguments.of(Ownership.EXTRAS, 1L, false),
                Arguments.of(Ownership.EXTRAS, 2L, false),
                Arguments.of(Ownership.EXTRAS, 3L, true));
    }

    @ParameterizedTest
    @MethodSource("ownershipCases")
    void ownershipComparesCountsAcrossMainAndSide(Ownership ownership, long owned, boolean expected) {
        var card = card(10, "Lantern", TYPE_SPELL, 0, 0, 0);
        var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, ownership, Sort.NAME);
        var draft = new DeckList(List.of(10), List.of(), List.of(10));
        assertEquals(expected, !CardSearch.search(List.of(card), "", filters, Map.of(10, owned), draft).isEmpty());
    }

    @Test void absentOwnershipAndAbsentRequirementsMeanNeitherMissingNorExtras() {
        var card = card(10, "Lantern", TYPE_SPELL, 0, 0, 0);
        for (var ownership : List.of(Ownership.MISSING, Ownership.EXTRAS, Ownership.OWNED)) {
            var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                    null, null, null, null, ownership, Sort.NAME);
            assertTrue(search(List.of(card), "", filters).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(Sort.class)
    void exactPasscodeThenExactNamePrecedePartialMatchesForEverySort(Sort sort) {
        var passcode = card(10, "ZZZ", TYPE_MONSTER, 9999, 9999, 4);
        var exactName = card(20, "10", TYPE_MONSTER, 5000, 5000, 4);
        var partial = card(1, "10 A", TYPE_MONSTER, 0, 0, 4);
        var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.ALL, sort);
        assertEquals(List.of(passcode, exactName, partial), search(List.of(partial, exactName, passcode), "10", filters));
    }

    static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of(Sort.NAME, List.of(2, 1, 3)),
                Arguments.of(Sort.PASSCODE, List.of(1, 2, 3)),
                Arguments.of(Sort.ATK, List.of(3, 2, 1)),
                Arguments.of(Sort.DEF, List.of(1, 3, 2)));
    }

    @ParameterizedTest
    @MethodSource("sortCases")
    void chosenSortOrdersEachTier(Sort sort, List<Integer> expectedCodes) {
        var cards = List.of(card(3, "C", TYPE_MONSTER, 0, 1000, 4),
                card(1, "b", TYPE_MONSTER, 2000, 0, 4), card(2, "A", TYPE_MONSTER, 1000, 2000, 4));
        var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.ALL, sort);
        assertEquals(expectedCodes, search(cards, "", filters).stream().map(CardInfo::code).toList());
    }

    @ParameterizedTest
    @EnumSource(Sort.class)
    void passcodesBreakSortTiesIndependentlyOfInputOrder(Sort sort) {
        var low = card(1, "Same", TYPE_MONSTER, 1000, 1000, 4);
        var high = card(2, "Same", TYPE_MONSTER, 1000, 1000, 4);
        var filters = new Filters(0, 0, 0, 0, 0, Measure.ANY,
                null, null, null, null, Ownership.ALL, sort);
        assertEquals(List.of(low, high), search(List.of(high, low), "Same", filters));
    }
}

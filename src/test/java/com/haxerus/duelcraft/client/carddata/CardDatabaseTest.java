package com.haxerus.duelcraft.client.carddata;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CardDatabase using the local EDOPro cards.cdb.
 * Requires: -Dduelcraft.test.dbPath=path/to/cards.cdb
 */
class CardDatabaseTest {

    private static CardDatabase db;

    @BeforeAll
    static void openDb() throws Exception {
        String dbPath = System.getProperty("duelcraft.test.dbPath");
        assertNotNull(dbPath, "Set -Dduelcraft.test.dbPath");
        db = new CardDatabase(Path.of(dbPath));
    }

    @AfterAll
    static void closeDb() throws Exception {
        if (db != null) db.close();
    }

    @Test
    void getCard_blueEyesWhiteDragon() {
        CardInfo card = db.getCard(89631139);
        assertNotNull(card);
        assertEquals("Blue-Eyes White Dragon", card.name());
        assertEquals(89631139, card.code());
        assertEquals(3000, card.atk());
        assertEquals(2500, card.def());
        assertEquals(8, card.levelOrRank());
        assertTrue(card.isMonster());
        assertFalse(card.isSpell());
        assertEquals(ATTRIBUTE_LIGHT, card.attribute());
        assertEquals(RACE_DRAGON, card.race());
    }

    @Test
    void getCard_raigeki_spellCard() {
        CardInfo card = db.getCard(12580477);
        assertNotNull(card);
        assertTrue(card.isSpell());
        assertFalse(card.isMonster());
    }

    @Test
    void getCard_unknownCode_returnsNull() {
        assertNull(db.getCard(999999999));
    }

    @Test
    void getCard_cachedOnSecondCall() {
        CardInfo first = db.getCard(89631139);
        CardInfo second = db.getCard(89631139);
        assertSame(first, second, "Second call should return cached instance");
    }

    @Test
    void getCard_darkMagician_hasDescription() {
        CardInfo card = db.getCard(46986414);
        assertNotNull(card);
        assertEquals("Dark Magician", card.name());
        assertNotNull(card.desc());
        assertFalse(card.desc().isEmpty());
    }

    /** A lone truthy operand: every non-alias, non-token card is declarable. */
    private static final List<Long> ANY_CARD = List.of(1L);

    @Test
    void searchDeclarable_passcodeQueryFindsThatCard() {
        List<CardInfo> results = db.searchDeclarable("89631139", ANY_CARD, 50);
        assertFalse(results.isEmpty());
        assertEquals(89631139, results.getFirst().code());
        assertEquals("Blue-Eyes White Dragon", results.getFirst().name());
    }

    @Test
    void searchDeclarable_nameSubstringPutsExactMatchFirst() {
        List<CardInfo> results = db.searchDeclarable("dark magician", ANY_CARD, 50);
        assertEquals("Dark Magician", results.getFirst().name(), "exact name match must sort first");
        assertTrue(results.size() > 1, "'Dark Magician' is a substring of several other card names");
        assertTrue(results.stream().allMatch(c -> c.name().toLowerCase().contains("dark magician")));
    }

    @Test
    void searchDeclarable_opcodesFilterOutNonMatchingCards() {
        // "Dark Magic" matches both spells ("Dark Magic Attack") and monsters ("Dark Magician").
        assertTrue(db.searchDeclarable("dark magic", ANY_CARD, 50).stream().anyMatch(CardInfo::isMonster));
        List<CardInfo> spells = db.searchDeclarable("dark magic", List.of((long) TYPE_SPELL, OPCODE_ISTYPE), 50);
        assertFalse(spells.isEmpty());
        assertTrue(spells.stream().allMatch(CardInfo::isSpell));
        assertTrue(spells.stream().noneMatch(c -> c.code() == 46986414), "Dark Magician must be filtered out");
    }

    @Test
    void searchDeclarable_respectsLimit() {
        assertEquals(5, db.searchDeclarable("dragon", ANY_CARD, 5).size());
    }

    @Test
    void searchDeclarable_blankQueryStopsAtLimit() {
        // No exact match is possible for a blank query, so the scan must stop at the limit instead
        // of filtering the whole table.
        assertEquals(7, db.searchDeclarable("", ANY_CARD, 7).size());
    }

    @Test
    void searchDeclarable_resultsShareTheGetCardCache() {
        CardInfo fromSearch = db.searchDeclarable("89631139", ANY_CARD, 50).getFirst();
        assertSame(fromSearch, db.getCard(89631139), "search results should populate the code cache");
    }

    @Test
    void searchDeclarable_emptyOpcodeListMatchesNothing() {
        assertTrue(db.searchDeclarable("dark magician", List.of(), 50).isEmpty());
    }
}

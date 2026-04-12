package com.haxerus.duelcraft.client.carddata;

import org.junit.jupiter.api.*;

import java.nio.file.Path;

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
}

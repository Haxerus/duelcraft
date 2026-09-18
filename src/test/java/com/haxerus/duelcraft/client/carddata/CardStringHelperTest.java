package com.haxerus.duelcraft.client.carddata;

import org.junit.jupiter.api.Test;
import com.haxerus.duelcraft.duel.message.QueriedCard;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class CardStringHelperTest {

    @Test
    void linkMarkers_preservePrintedDirectionMasksIncludingZero() {
        for (int mask : new int[]{0, 0x40, 0x80, 0x100, 0x8, 0x20, 0x1, 0x2, 0x4, 0x1ef}) {
            assertEquals(Integer.valueOf(mask), CardStringHelper.linkMarkers(linkCard(mask), null));
        }
    }

    @Test
    void linkMarkers_liveQueryOverridesPrintedMaskIncludingZero() {
        var query = new QueriedCard();
        query.flags = QUERY_LINK | QUERY_TYPE;
        query.type = TYPE_MONSTER | TYPE_LINK;
        query.linkMarker = LINK_MARKER_BOTTOM;
        assertEquals(Integer.valueOf(LINK_MARKER_BOTTOM), CardStringHelper.linkMarkers(linkCard(LINK_MARKER_TOP), query));
        query.linkMarker = 0;
        assertEquals(Integer.valueOf(0), CardStringHelper.linkMarkers(linkCard(LINK_MARKER_TOP), query));
        query.flags = QUERY_TYPE;
        assertEquals(Integer.valueOf(LINK_MARKER_TOP), CardStringHelper.linkMarkers(linkCard(LINK_MARKER_TOP), query));
        query.flags = QUERY_LINK;
        query.type = TYPE_MONSTER;
        query.linkMarker = LINK_MARKER_RIGHT;
        assertEquals(Integer.valueOf(LINK_MARKER_RIGHT), CardStringHelper.linkMarkers(linkCard(LINK_MARKER_TOP), query));
    }

    @Test
    void linkMarkers_nonLinkAndUnknownCardsHaveNoMarkers() {
        var monster = new CardInfo(1, "Monster", "", TYPE_MONSTER, 1000, 2000, 4, RACE_WARRIOR, ATTRIBUTE_LIGHT);
        assertNull(CardStringHelper.linkMarkers(monster, null));
        assertNull(CardStringHelper.linkMarkers(null, null));
        var query = new QueriedCard();
        query.flags = QUERY_TYPE | QUERY_LINK;
        query.type = TYPE_MONSTER;
        query.linkMarker = LINK_MARKER_TOP;
        assertNull(CardStringHelper.linkMarkers(linkCard(LINK_MARKER_TOP), query));
        query.type = TYPE_MONSTER | TYPE_LINK;
        assertNull(CardStringHelper.linkMarkers(null, query));
        assertEquals(Integer.valueOf(LINK_MARKER_TOP), CardStringHelper.linkMarkers(monster, query));
    }

    private static CardInfo linkCard(int markers) {
        return new CardInfo(1, "Link monster", "", TYPE_MONSTER | TYPE_LINK,
                2300, markers, 3, RACE_CYBERSE, ATTRIBUTE_DARK);
    }

    @Test
    void attributeName_returnsCorrectNames() {
        assertEquals("DARK", CardStringHelper.attributeName(ATTRIBUTE_DARK));
        assertEquals("LIGHT", CardStringHelper.attributeName(ATTRIBUTE_LIGHT));
        assertEquals("FIRE", CardStringHelper.attributeName(ATTRIBUTE_FIRE));
        assertEquals("WATER", CardStringHelper.attributeName(ATTRIBUTE_WATER));
        assertEquals("EARTH", CardStringHelper.attributeName(ATTRIBUTE_EARTH));
        assertEquals("WIND", CardStringHelper.attributeName(ATTRIBUTE_WIND));
        assertEquals("DIVINE", CardStringHelper.attributeName(ATTRIBUTE_DIVINE));
    }

    @Test
    void attributeName_unknownReturnsQuestionMarks() {
        assertEquals("???", CardStringHelper.attributeName(0));
    }

    @Test
    void raceName_returnsCorrectNames() {
        assertEquals("Warrior", CardStringHelper.raceName(RACE_WARRIOR));
        assertEquals("Spellcaster", CardStringHelper.raceName(RACE_SPELLCASTER));
        assertEquals("Dragon", CardStringHelper.raceName(RACE_DRAGON));
        assertEquals("Machine", CardStringHelper.raceName(RACE_MACHINE));
        assertEquals("Cyberse", CardStringHelper.raceName(RACE_CYBERSE));
    }

    @Test
    void typeLine_normalMonster() {
        var card = new CardInfo(89631139, "Blue-Eyes White Dragon", "",
                TYPE_MONSTER | TYPE_NORMAL, 3000, 2500,
                8, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertEquals("LIGHT / Level 8 / Dragon / Normal", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_effectMonster() {
        var card = new CardInfo(40737112, "Dark Magician of Chaos", "",
                TYPE_MONSTER | TYPE_EFFECT, 2800, 2600,
                8, RACE_SPELLCASTER, ATTRIBUTE_DARK);
        assertEquals("DARK / Level 8 / Spellcaster / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_xyzMonster() {
        var card = new CardInfo(84013237, "Number 39: Utopia", "",
                TYPE_MONSTER | TYPE_XYZ, 2500, 2000,
                4, RACE_WARRIOR, ATTRIBUTE_LIGHT);
        assertEquals("LIGHT / Rank 4 / Warrior / Xyz", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_linkMonster() {
        var card = new CardInfo(1861629, "Decode Talker", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_LINK, 2300, 0,
                3, RACE_CYBERSE, ATTRIBUTE_DARK);
        assertEquals("DARK / Link 3 / Cyberse / Link / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_pendulumMonster() {
        var card = new CardInfo(16178681, "Odd-Eyes Pendulum Dragon", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_PENDULUM, 2500, 2000,
                7 | (4 << 24) | (4 << 16), RACE_DRAGON, ATTRIBUTE_DARK);
        assertEquals("DARK / Level 7 / Dragon / Pendulum / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_spellCard() {
        var card = new CardInfo(70368879, "Raigeki", "", TYPE_SPELL,
                0, 0, 0, 0, 0);
        assertEquals("Spell Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_continuousSpell() {
        var card = new CardInfo(12580477, "Messenger of Peace", "",
                TYPE_SPELL | TYPE_CONTINUOUS, 0, 0, 0, 0, 0);
        assertEquals("Continuous Spell Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_trapCard() {
        var card = new CardInfo(44095762, "Mirror Force", "", TYPE_TRAP,
                0, 0, 0, 0, 0);
        assertEquals("Trap Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_counterTrap() {
        var card = new CardInfo(40605147, "Solemn Judgment", "",
                TYPE_TRAP | TYPE_COUNTER, 0, 0, 0, 0, 0);
        assertEquals("Counter Trap Card", CardStringHelper.typeLine(card));
    }

    @Test
    void atkDefLine_normalMonster() {
        var card = new CardInfo(89631139, "Blue-Eyes White Dragon", "",
                TYPE_MONSTER | TYPE_NORMAL, 3000, 2500, 8, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertEquals("ATK 3000 / DEF 2500", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_linkMonster_showsLinkRating() {
        var card = new CardInfo(1861629, "Decode Talker", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_LINK, 2300, 0,
                3, RACE_CYBERSE, ATTRIBUTE_DARK);
        assertEquals("ATK 2300 / Link 3", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_unknownAtk() {
        var card = new CardInfo(0, "Test", "", TYPE_MONSTER | TYPE_EFFECT,
                -2, 0, 1, RACE_WARRIOR, ATTRIBUTE_DARK);
        assertEquals("ATK ? / DEF 0", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_spellCard_returnsEmpty() {
        var card = new CardInfo(70368879, "Raigeki", "", TYPE_SPELL,
                0, 0, 0, 0, 0);
        assertEquals("", CardStringHelper.atkDefLine(card));
    }
}

package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelRuleTest {

    @Test
    void everyIdParsesBackToItsRule() {
        for (DuelRule rule : DuelRule.values()) {
            assertEquals(Optional.of(rule), DuelRule.parse(rule.id()), rule.name());
        }
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(Optional.of(DuelRule.MR3), DuelRule.parse("MR3"));
        assertEquals(Optional.of(DuelRule.SPEED), DuelRule.parse("Speed"));
    }

    @Test
    void parseRejectsUnknownIds() {
        assertTrue(DuelRule.parse("mr6").isEmpty());
        assertTrue(DuelRule.parse("").isEmpty());
    }

    @Test
    void flagsMatchTheEnginePresets() {
        assertEquals(DUEL_MODE_MR1, DuelRule.MR1.flags());
        assertEquals(DUEL_MODE_GOAT, DuelRule.GOAT.flags());
        assertEquals(DUEL_MODE_MR2, DuelRule.MR2.flags());
        assertEquals(DUEL_MODE_MR3, DuelRule.MR3.flags());
        assertEquals(DUEL_MODE_MR4, DuelRule.MR4.flags());
        assertEquals(DUEL_MODE_MR5, DuelRule.MR5.flags());
        assertEquals(DUEL_MODE_SPEED, DuelRule.SPEED.flags());
        assertEquals(DUEL_MODE_RUSH, DuelRule.RUSH.flags());
    }

    @Test
    void idsAreLowercaseAndUnique() {
        List<String> ids = DuelRule.ids();
        assertEquals(8, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size());
        for (String id : ids) {
            assertEquals(id.toLowerCase(Locale.ROOT), id);
        }
    }

    @Test
    void standardOptionsUseMr5AndOfUsesTheGivenRule() {
        assertEquals(DUEL_MODE_MR5, DuelOptions.standard(1L).flags());
        assertEquals(DUEL_MODE_MR3, DuelOptions.of(1L, DuelRule.MR3).flags());
        assertArrayEquals(SeedExpander.toFourLongs(1L), DuelOptions.of(1L, DuelRule.MR3).seed());
    }
}

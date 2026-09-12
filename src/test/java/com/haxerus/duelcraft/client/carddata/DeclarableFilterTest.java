package com.haxerus.duelcraft.client.carddata;

import com.haxerus.duelcraft.client.carddata.DeclarableFilter.CardFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected values come from ygopro-core's {@code is_declarable}
 * ({@code native/ygopro-core/playerop.cpp:1004-1069}), not from this port's code.
 */
class DeclarableFilterTest {

    /** A plain effect monster with the "Blue-Eyes" archetype (0x5b) in the first setcode slot. */
    private static CardFacts monster(long setcodes, int type) {
        return new CardFacts(1234, 0, setcodes, type, RACE_DRAGON, ATTRIBUTE_LIGHT);
    }

    @Test
    @DisplayName("no opcodes leaves the stack empty, which the engine rejects")
    void emptyOpcodesMatchNothing() {
        assertFalse(DeclarableFilter.matches(List.of(), monster(0x5b, TYPE_MONSTER | TYPE_EFFECT)));
    }

    @Test
    @DisplayName("a bare truthy operand declares any non-alias non-token card")
    void bareOperandMatches() {
        assertTrue(DeclarableFilter.matches(List.of(1L), monster(0x5b, TYPE_MONSTER | TYPE_EFFECT)));
        assertFalse(DeclarableFilter.matches(List.of(0L), monster(0x5b, TYPE_MONSTER | TYPE_EFFECT)));
    }

    @Test
    @DisplayName("ISSETCARD matches a setcode in any of the four packed slots")
    void isSetCardMatchesPackedSlots() {
        List<Long> blueEyes = List.of(0x5bL, OPCODE_ISSETCARD);
        assertTrue(DeclarableFilter.matches(blueEyes, monster(0x5b, TYPE_MONSTER)));
        assertTrue(DeclarableFilter.matches(blueEyes, monster(0x0099_005b_0000L, TYPE_MONSTER)));
        assertFalse(DeclarableFilter.matches(blueEyes, monster(0x5c, TYPE_MONSTER)));
        assertFalse(DeclarableFilter.matches(blueEyes, monster(0, TYPE_MONSTER)));
    }

    @Test
    @DisplayName("ISSETCARD sub-archetype nibble is a subset test, not equality")
    void isSetCardSubArchetype() {
        // Asking for the plain archetype 0x107 accepts the sub-archetype 0x1107...
        assertTrue(DeclarableFilter.matches(List.of(0x107L, OPCODE_ISSETCARD), monster(0x1107, TYPE_MONSTER)));
        // ...but asking for the sub-archetype 0x1107 rejects the plain 0x107.
        assertFalse(DeclarableFilter.matches(List.of(0x1107L, OPCODE_ISSETCARD), monster(0x107, TYPE_MONSTER)));
        assertTrue(DeclarableFilter.matches(List.of(0x1107L, OPCODE_ISSETCARD), monster(0x1107, TYPE_MONSTER)));
    }

    @Test
    @DisplayName("ISCODE compares the passcode")
    void isCode() {
        assertTrue(DeclarableFilter.matches(List.of(1234L, OPCODE_ISCODE), monster(0, TYPE_MONSTER)));
        assertFalse(DeclarableFilter.matches(List.of(4321L, OPCODE_ISCODE), monster(0, TYPE_MONSTER)));
    }

    @Test
    @DisplayName("ISTYPE pushes the masked bits, so a missing type is falsy")
    void isType() {
        assertTrue(DeclarableFilter.matches(List.of((long) TYPE_SPELL, OPCODE_ISTYPE), monster(0, TYPE_SPELL | TYPE_QUICKPLAY)));
        assertFalse(DeclarableFilter.matches(List.of((long) TYPE_SPELL, OPCODE_ISTYPE), monster(0, TYPE_MONSTER | TYPE_EFFECT)));
    }

    @Test
    @DisplayName("ISRACE and ISATTRIBUTE mask the card's race/attribute")
    void isRaceAndAttribute() {
        CardFacts dragon = monster(0, TYPE_MONSTER);
        assertTrue(DeclarableFilter.matches(List.of(RACE_DRAGON | RACE_WARRIOR, OPCODE_ISRACE), dragon));
        assertFalse(DeclarableFilter.matches(List.of(RACE_WARRIOR, OPCODE_ISRACE), dragon));
        assertTrue(DeclarableFilter.matches(List.of((long) ATTRIBUTE_LIGHT, OPCODE_ISATTRIBUTE), dragon));
        assertFalse(DeclarableFilter.matches(List.of((long) ATTRIBUTE_DARK, OPCODE_ISATTRIBUTE), dragon));
    }

    @Test
    @DisplayName("ISTYPE combined with AND")
    void isTypeWithAnd() {
        // "an Effect Monster": ISTYPE MONSTER AND ISTYPE EFFECT
        List<Long> effectMonster = List.of(
                (long) TYPE_MONSTER, OPCODE_ISTYPE,
                (long) TYPE_EFFECT, OPCODE_ISTYPE,
                OPCODE_AND);
        assertTrue(DeclarableFilter.matches(effectMonster, monster(0, TYPE_MONSTER | TYPE_EFFECT)));
        assertFalse(DeclarableFilter.matches(effectMonster, monster(0, TYPE_MONSTER | TYPE_NORMAL)));
        assertFalse(DeclarableFilter.matches(effectMonster, monster(0, TYPE_SPELL)));
    }

    @Test
    @DisplayName("NOT inverts truthiness")
    void not() {
        List<Long> notSpell = List.of((long) TYPE_SPELL, OPCODE_ISTYPE, OPCODE_NOT);
        assertTrue(DeclarableFilter.matches(notSpell, monster(0, TYPE_MONSTER)));
        assertFalse(DeclarableFilter.matches(notSpell, monster(0, TYPE_SPELL)));
    }

    @Test
    @DisplayName("ISSETCARD X AND NOT ISTYPE Y")
    void setCardAndNotType() {
        List<Long> blueEyesNonMonster = List.of(
                0x5bL, OPCODE_ISSETCARD,
                (long) TYPE_MONSTER, OPCODE_ISTYPE, OPCODE_NOT,
                OPCODE_AND);
        assertTrue(DeclarableFilter.matches(blueEyesNonMonster, monster(0x5b, TYPE_SPELL)));
        assertFalse(DeclarableFilter.matches(blueEyesNonMonster, monster(0x5b, TYPE_MONSTER)));
        assertFalse(DeclarableFilter.matches(blueEyesNonMonster, monster(0x5c, TYPE_SPELL)));
    }

    @Test
    @DisplayName("arithmetic and bitwise operands evaluate before the result is tested")
    void arithmeticAndBitwise() {
        // (2 + 3) - 5 == 0 -> falsy
        assertFalse(DeclarableFilter.matches(List.of(2L, 3L, OPCODE_ADD, 5L, OPCODE_SUB), monster(0, TYPE_MONSTER)));
        // (2 * 3) - 5 == 1 -> truthy
        assertTrue(DeclarableFilter.matches(List.of(2L, 3L, OPCODE_MUL, 5L, OPCODE_SUB), monster(0, TYPE_MONSTER)));
        // ISTYPE MONSTER masked down to the SPELL bit -> 0
        assertFalse(DeclarableFilter.matches(
                List.of((long) TYPE_MONSTER, OPCODE_ISTYPE, (long) TYPE_SPELL, OPCODE_BAND),
                monster(0, TYPE_MONSTER)));
    }

    @Test
    @DisplayName("aliases are rejected unless ALLOW_ALIASES appears")
    void aliasRules() {
        CardFacts alt = new CardFacts(1235, 1234, 0x5b, TYPE_MONSTER, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertFalse(DeclarableFilter.matches(List.of(0x5bL, OPCODE_ISSETCARD), alt));
        assertTrue(DeclarableFilter.matches(List.of(OPCODE_ALLOW_ALIASES, 0x5bL, OPCODE_ISSETCARD), alt));
    }

    @Test
    @DisplayName("monster tokens are rejected unless ALLOW_TOKENS appears")
    void tokenRules() {
        CardFacts token = monster(0x5b, TYPE_MONSTER | TYPE_TOKEN | TYPE_NORMAL);
        assertFalse(DeclarableFilter.matches(List.of(0x5bL, OPCODE_ISSETCARD), token));
        assertTrue(DeclarableFilter.matches(List.of(OPCODE_ALLOW_TOKENS, 0x5bL, OPCODE_ISSETCARD), token));
        // TYPE_TOKEN without TYPE_MONSTER is not a token for this rule.
        assertTrue(DeclarableFilter.matches(List.of(0x5bL, OPCODE_ISSETCARD), monster(0x5b, TYPE_TOKEN)));
    }

    @Test
    @DisplayName("Marine Dolphin and Twinkle Moss bypass the alias and token rules")
    void hardCodedExceptions() {
        CardFacts dolphin = new CardFacts(78734254, 1, 0x5b, TYPE_MONSTER | TYPE_TOKEN, RACE_DRAGON, ATTRIBUTE_LIGHT);
        CardFacts moss = new CardFacts(13857930, 1, 0x5b, TYPE_MONSTER | TYPE_TOKEN, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertTrue(DeclarableFilter.matches(List.of(0x5bL, OPCODE_ISSETCARD), dolphin));
        assertTrue(DeclarableFilter.matches(List.of(0x5bL, OPCODE_ISSETCARD), moss));
        // The exception does not rescue a card the opcodes rejected.
        assertFalse(DeclarableFilter.matches(List.of(0x5cL, OPCODE_ISSETCARD), dolphin));
    }

    @Test
    @DisplayName("a leftover operand leaves more than one value on the stack, which the engine rejects")
    void unbalancedStackIsRejected() {
        assertFalse(DeclarableFilter.matches(List.of(1L, 1L), monster(0, TYPE_MONSTER)));
    }
}

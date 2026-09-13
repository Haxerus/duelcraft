package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Byte-level parser tests for the cases docs/engine-gap-analysis.md §10 listed as parsed but
 * untested. Bodies are encoded from the engine writers (playerop.cpp, operations.cpp,
 * processor.cpp, card.cpp, field.cpp — see docs/engine-wiki/message-index.md), not from the
 * records under test. Split from MessageParserTest to keep that file under ~1200 lines.
 */
class MessageParserBackfillTest {

    // ---- List-entry helpers not already in MessageParserTest ----

    /** IdleCmdCard: code(i32) + con(u8) + loc(u8) + seq(i32). No position. */
    static void putIdleCmdCard(ByteBuffer b, int code, int con, int loc, int seq) {
        b.putInt(code); b.put((byte) con); b.put((byte) loc); b.putInt(seq);
    }

    /** ReposCard: code(i32) + con(u8) + loc(u8) + seq(u8). */
    static void putReposCard(ByteBuffer b, int code, int con, int loc, int seq) {
        b.putInt(code); b.put((byte) con); b.put((byte) loc); b.put((byte) seq);
    }

    /** ActivatableCard: code(i32) + con(u8) + loc(u8) + seq(i32) + desc(i64) + flag(u8). */
    static void putActivatableCard(ByteBuffer b, int code, int con, int loc, int seq, long desc, int flag) {
        b.putInt(code); b.put((byte) con); b.put((byte) loc); b.putInt(seq); b.putLong(desc); b.put((byte) flag);
    }

    /** AttackCard: code(i32) + con(u8) + loc(u8) + seq(u8) + diratt(u8). */
    static void putAttackCard(ByteBuffer b, int code, int con, int loc, int seq, int diratt) {
        b.putInt(code); b.put((byte) con); b.put((byte) loc); b.put((byte) seq); b.put((byte) diratt);
    }

    /** SortableCard: code(i32) + con(u8) + loc(i32, full width) + seq(i32). */
    static void putSortableCard(ByteBuffer b, int code, int con, int loc, int seq) {
        b.putInt(code); b.put((byte) con); b.putInt(loc); b.putInt(seq);
    }

    /** CounterCard: code(i32) + con(u8) + loc(u8) + seq(u8) + counterCount(u16). */
    static void putCounterCard(ByteBuffer b, int code, int con, int loc, int seq, int counterCount) {
        b.putInt(code); b.put((byte) con); b.put((byte) loc); b.put((byte) seq); b.putShort((short) counterCount);
    }

    @Test
    void parseRetry() {
        List<DuelMessage> msgs = MessageParser.parse(MessageParserTest.msg(MSG_RETRY, new byte[0]));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.Retry.class, msgs.getFirst());
    }

    /** playerop.cpp select_battle_command: activatable (19B entries) then attackable (8B entries). */
    @Test
    void parseSelectBattleCmd() {
        ByteBuffer b = MessageParserTest.body(1 + 4 + 19 + 4 + 8 + 1 + 1);
        b.put((byte) 1); // player
        b.putInt(1);     // activatable count
        putActivatableCard(b, 89631139, 0, LOCATION_SZONE, 3, 0x1234L, 1);
        b.putInt(1);     // attackable count
        putAttackCard(b, 46986414, 1, LOCATION_MZONE, 2, 1);
        b.put((byte) 1); // canMain2
        b.put((byte) 0); // canEnd

        var bc = (DuelMessage.SelectBattleCmd) MessageParser.parse(MessageParserTest.msg(MSG_SELECT_BATTLECMD, b.array())).getFirst();
        assertEquals(1, bc.player());
        assertEquals(1, bc.activatable().size());
        var act = bc.activatable().getFirst();
        assertEquals(89631139, act.code());
        assertEquals(0, act.controller());
        assertEquals(LOCATION_SZONE, act.location());
        assertEquals(3, act.sequence());
        assertEquals(0, act.position());
        assertEquals(0x1234L, act.desc());
        assertEquals(1, act.flag());
        assertEquals(1, bc.attackable().size());
        var atk = bc.attackable().getFirst();
        assertEquals(46986414, atk.code());
        assertEquals(1, atk.controller());
        assertEquals(LOCATION_MZONE, atk.location());
        assertEquals(2, atk.sequence());
        assertEquals(1, atk.directAttack());
        assertTrue(bc.canMain2());
        assertFalse(bc.canEnd());
    }

    /** playerop.cpp select_idle_command: six count-prefixed lists across three entry widths, then three flags. */
    @Test
    void parseSelectIdleCmd() {
        ByteBuffer b = MessageParserTest.body(1 + (4 + 10) + (4 + 10) + (4 + 7) + (4 + 10) + (4 + 10) + (4 + 19) + 1 + 1 + 1);
        b.put((byte) 1); // player

        b.putInt(1); putIdleCmdCard(b, 11111111, 0, LOCATION_HAND, 0);   // summonable
        b.putInt(1); putIdleCmdCard(b, 22222222, 0, LOCATION_HAND, 1);   // specialSummonable
        b.putInt(1); putReposCard(b, 33333333, 0, LOCATION_MZONE, 2);    // repositionable
        b.putInt(1); putIdleCmdCard(b, 44444444, 0, LOCATION_HAND, 3);   // settableMonsters
        b.putInt(1); putIdleCmdCard(b, 55555555, 0, LOCATION_HAND, 4);   // settableSpells
        b.putInt(1); putActivatableCard(b, 66666666, 0, LOCATION_MZONE, 5, 0x9999L, 1); // activatable

        b.put((byte) 1); // canBattle
        b.put((byte) 1); // canEnd
        b.put((byte) 0); // canShuffle

        var idle = (DuelMessage.SelectIdleCmd) MessageParser.parse(MessageParserTest.msg(MSG_SELECT_IDLECMD, b.array())).getFirst();
        assertEquals(1, idle.player());

        assertEquals(1, idle.summonable().size());
        assertEquals(11111111, idle.summonable().getFirst().code());
        assertEquals(0, idle.summonable().getFirst().sequence());

        assertEquals(1, idle.specialSummonable().size());
        assertEquals(22222222, idle.specialSummonable().getFirst().code());
        assertEquals(1, idle.specialSummonable().getFirst().sequence());

        assertEquals(1, idle.repositionable().size());
        assertEquals(33333333, idle.repositionable().getFirst().code());
        assertEquals(LOCATION_MZONE, idle.repositionable().getFirst().location());
        assertEquals(2, idle.repositionable().getFirst().sequence());

        assertEquals(1, idle.settableMonsters().size());
        assertEquals(44444444, idle.settableMonsters().getFirst().code());

        assertEquals(1, idle.settableSpells().size());
        assertEquals(55555555, idle.settableSpells().getFirst().code());

        assertEquals(1, idle.activatable().size());
        var act = idle.activatable().getFirst();
        assertEquals(66666666, act.code());
        assertEquals(LOCATION_MZONE, act.location());
        assertEquals(5, act.sequence());
        assertEquals(0, act.position());
        assertEquals(0x9999L, act.desc());
        assertEquals(1, act.flag());

        assertTrue(idle.canBattle());
        assertTrue(idle.canEnd());
        assertFalse(idle.canShuffle());
    }

    /** playerop.cpp sort_chain: player + count + n×SortableCard(13B). */
    @Test
    void parseSortChain() {
        ByteBuffer b = MessageParserTest.body(1 + 4 + 13 * 2);
        b.put((byte) 0);
        b.putInt(2);
        putSortableCard(b, 11111111, 0, LOCATION_SZONE, 1);
        putSortableCard(b, 22222222, 1, LOCATION_MZONE, 2);

        var sc = (DuelMessage.SortChain) MessageParser.parse(MessageParserTest.msg(MSG_SORT_CHAIN, b.array())).getFirst();
        assertEquals(0, sc.player());
        assertEquals(2, sc.cards().size());
        assertEquals(11111111, sc.cards().get(0).code());
        assertEquals(LOCATION_SZONE, sc.cards().get(0).location());
        assertEquals(1, sc.cards().get(0).sequence());
        assertEquals(22222222, sc.cards().get(1).code());
        assertEquals(1, sc.cards().get(1).controller());
        assertEquals(LOCATION_MZONE, sc.cards().get(1).location());
        assertEquals(2, sc.cards().get(1).sequence());
    }

    /** playerop.cpp select_counter: player + counterType(u16) + count(u16) + cardCount(i32) + n×CounterCard(9B). */
    @Test
    void parseSelectCounter() {
        ByteBuffer b = MessageParserTest.body(1 + 2 + 2 + 4 + 9 * 2);
        b.put((byte) 1);
        b.putShort((short) 2000); // counterType
        b.putShort((short) 5);    // total count required
        b.putInt(2);              // card count
        putCounterCard(b, 11111111, 0, LOCATION_MZONE, 0, 3);
        putCounterCard(b, 22222222, 0, LOCATION_MZONE, 1, 2);

        var sc = (DuelMessage.SelectCounter) MessageParser.parse(MessageParserTest.msg(MSG_SELECT_COUNTER, b.array())).getFirst();
        assertEquals(1, sc.player());
        assertEquals(2000, sc.counterType());
        assertEquals(5, sc.count());
        assertEquals(2, sc.cards().size());
        assertEquals(11111111, sc.cards().get(0).code());
        assertEquals(3, sc.cards().get(0).counterCount());
        assertEquals(22222222, sc.cards().get(1).code());
        assertEquals(1, sc.cards().get(1).sequence());
        assertEquals(2, sc.cards().get(1).counterCount());
    }

    /** playerop.cpp sort_card: same shape as sort_chain. */
    @Test
    void parseSortCard() {
        ByteBuffer b = MessageParserTest.body(1 + 4 + 13 * 2);
        b.put((byte) 1);
        b.putInt(2);
        putSortableCard(b, 33333333, 0, LOCATION_HAND, 0);
        putSortableCard(b, 44444444, 0, LOCATION_HAND, 1);

        var sc = (DuelMessage.SortCard) MessageParser.parse(MessageParserTest.msg(MSG_SORT_CARD, b.array())).getFirst();
        assertEquals(1, sc.player());
        assertEquals(2, sc.cards().size());
        assertEquals(33333333, sc.cards().get(0).code());
        assertEquals(LOCATION_HAND, sc.cards().get(0).location());
        assertEquals(44444444, sc.cards().get(1).code());
        assertEquals(1, sc.cards().get(1).sequence());
    }

    /** field.cpp:461 MSG_SWAP: code1 + loc_info + code2 + loc_info. */
    @Test
    void parseSwap() {
        ByteBuffer b = MessageParserTest.body(4 + 10 + 4 + 10);
        b.putInt(11111111);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);
        b.putInt(22222222);
        MessageParserTest.putLocInfo(b, 1, LOCATION_MZONE, 1, POS_FACEDOWN_DEFENSE);

        var sw = (DuelMessage.Swap) MessageParser.parse(MessageParserTest.msg(MSG_SWAP, b.array())).getFirst();
        assertEquals(11111111, sw.code1());
        assertEquals(new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), sw.loc1());
        assertEquals(22222222, sw.code2());
        assertEquals(new LocInfo(1, LOCATION_MZONE, 1, POS_FACEDOWN_DEFENSE), sw.loc2());
    }

    /** processor.cpp:4476,4641 MSG_FIELD_DISABLED: one u32, low 16 bits player 0, high 16 bits player 1. */
    @Test
    void parseFieldDisabled() {
        ByteBuffer b = MessageParserTest.body(4);
        b.putInt(0x00020001);

        var fd = (DuelMessage.FieldDisabled) MessageParser.parse(MessageParserTest.msg(MSG_FIELD_DISABLED, b.array())).getFirst();
        assertEquals(0x00020001, fd.field());
    }

    @Test
    void parseSpSummoning() {
        ByteBuffer b = MessageParserTest.body(4 + 10);
        b.putInt(33333333);
        MessageParserTest.putLocInfo(b, 1, LOCATION_MZONE, 3, POS_FACEDOWN_ATTACK);

        var s = (DuelMessage.SpSummoning) MessageParser.parse(MessageParserTest.msg(MSG_SPSUMMONING, b.array())).getFirst();
        assertEquals(33333333, s.code());
        assertEquals(new LocInfo(1, LOCATION_MZONE, 3, POS_FACEDOWN_ATTACK), s.location());
    }

    @Test
    void parseSpSummoned() {
        List<DuelMessage> msgs = MessageParser.parse(MessageParserTest.msg(MSG_SPSUMMONED, new byte[0]));
        assertInstanceOf(DuelMessage.SpSummoned.class, msgs.getFirst());
    }

    @Test
    void parseFlipSummoning() {
        ByteBuffer b = MessageParserTest.body(4 + 10);
        b.putInt(44444444);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK);

        var s = (DuelMessage.FlipSummoning) MessageParser.parse(MessageParserTest.msg(MSG_FLIPSUMMONING, b.array())).getFirst();
        assertEquals(44444444, s.code());
        assertEquals(new LocInfo(0, LOCATION_MZONE, 1, POS_FACEUP_ATTACK), s.location());
    }

    @Test
    void parseFlipSummoned() {
        List<DuelMessage> msgs = MessageParser.parse(MessageParserTest.msg(MSG_FLIPSUMMONED, new byte[0]));
        assertInstanceOf(DuelMessage.FlipSummoned.class, msgs.getFirst());
    }

    @Test
    void parseChainSolved() {
        var cs = (DuelMessage.ChainSolved) MessageParser.parse(MessageParserTest.msg(MSG_CHAIN_SOLVED, new byte[]{2})).getFirst();
        assertEquals(2, cs.chainIndex());
    }

    @Test
    void parseChainDisabled() {
        var cd = (DuelMessage.ChainDisabled) MessageParser.parse(MessageParserTest.msg(MSG_CHAIN_DISABLED, new byte[]{1})).getFirst();
        assertEquals(1, cd.chainIndex());
    }

    /** processor.cpp:2031 MSG_CARD_SELECTED: count + n×loc_info, no player byte. */
    @Test
    void parseCardSelected() {
        ByteBuffer b = MessageParserTest.body(4 + 10 * 2);
        b.putInt(2);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);
        MessageParserTest.putLocInfo(b, 1, LOCATION_MZONE, 1, POS_FACEUP_ATTACK);

        var cs = (DuelMessage.CardSelected) MessageParser.parse(MessageParserTest.msg(MSG_CARD_SELECTED, b.array())).getFirst();
        assertEquals(2, cs.cards().size());
        assertEquals(new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), cs.cards().get(0));
        assertEquals(new LocInfo(1, LOCATION_MZONE, 1, POS_FACEUP_ATTACK), cs.cards().get(1));
    }

    /** card.cpp (3 sites) MSG_BECOME_TARGET: count + n×loc_info. */
    @Test
    void parseBecomeTarget() {
        ByteBuffer b = MessageParserTest.body(4 + 10 * 2);
        b.putInt(2);
        MessageParserTest.putLocInfo(b, 0, LOCATION_SZONE, 2, POS_FACEDOWN_ATTACK);
        MessageParserTest.putLocInfo(b, 1, LOCATION_MZONE, 3, POS_FACEUP_DEFENSE);

        var bt = (DuelMessage.BecomeTarget) MessageParser.parse(MessageParserTest.msg(MSG_BECOME_TARGET, b.array())).getFirst();
        assertEquals(2, bt.targets().size());
        assertEquals(new LocInfo(0, LOCATION_SZONE, 2, POS_FACEDOWN_ATTACK), bt.targets().get(0));
        assertEquals(new LocInfo(1, LOCATION_MZONE, 3, POS_FACEUP_DEFENSE), bt.targets().get(1));
    }

    /** card.cpp:2345 MSG_CARD_TARGET: loc_info card + loc_info target. */
    @Test
    void parseCardTarget() {
        ByteBuffer b = MessageParserTest.body(10 + 10);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);
        MessageParserTest.putLocInfo(b, 1, LOCATION_MZONE, 1, POS_FACEUP_DEFENSE);

        var ct = (DuelMessage.CardTarget) MessageParser.parse(MessageParserTest.msg(MSG_CARD_TARGET, b.array())).getFirst();
        assertEquals(new LocInfo(0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK), ct.card());
        assertEquals(new LocInfo(1, LOCATION_MZONE, 1, POS_FACEUP_DEFENSE), ct.target());
    }

    /** card.cpp:2358 MSG_CANCEL_TARGET: loc_info card + loc_info target. */
    @Test
    void parseCancelTarget() {
        ByteBuffer b = MessageParserTest.body(10 + 10);
        MessageParserTest.putLocInfo(b, 1, LOCATION_SZONE, 0, POS_FACEDOWN_DEFENSE);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK);

        var ct = (DuelMessage.CancelTarget) MessageParser.parse(MessageParserTest.msg(MSG_CANCEL_TARGET, b.array())).getFirst();
        assertEquals(new LocInfo(1, LOCATION_SZONE, 0, POS_FACEDOWN_DEFENSE), ct.card());
        assertEquals(new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), ct.target());
    }

    /** operations.cpp:749 MSG_PAY_LPCOST: player + amount. */
    @Test
    void parsePayLpCost() {
        ByteBuffer b = MessageParserTest.body(1 + 4);
        b.put((byte) 1);
        b.putInt(800);

        var plc = (DuelMessage.PayLpCost) MessageParser.parse(MessageParserTest.msg(MSG_PAY_LPCOST, b.array())).getFirst();
        assertEquals(1, plc.player());
        assertEquals(800, plc.amount());
    }

    /** card.cpp:2241 MSG_ADD_COUNTER: counterType(u16) + con(u8) + loc(u8) + seq(u8) + count(u16). */
    @Test
    void parseAddCounter() {
        ByteBuffer b = MessageParserTest.body(2 + 1 + 1 + 1 + 2);
        b.putShort((short) 2000);
        b.put((byte) 0);
        b.put((byte) LOCATION_MZONE);
        b.put((byte) 3);
        b.putShort((short) 2);

        var ac = (DuelMessage.AddCounter) MessageParser.parse(MessageParserTest.msg(MSG_ADD_COUNTER, b.array())).getFirst();
        assertEquals(2000, ac.counterType());
        assertEquals(0, ac.controller());
        assertEquals(LOCATION_MZONE, ac.location());
        assertEquals(3, ac.sequence());
        assertEquals(2, ac.count());
    }

    /** MSG_REMOVE_COUNTER (4 sites): same layout as MSG_ADD_COUNTER. */
    @Test
    void parseRemoveCounter() {
        ByteBuffer b = MessageParserTest.body(2 + 1 + 1 + 1 + 2);
        b.putShort((short) 2001);
        b.put((byte) 1);
        b.put((byte) LOCATION_SZONE);
        b.put((byte) 4);
        b.putShort((short) 1);

        var rc = (DuelMessage.RemoveCounter) MessageParser.parse(MessageParserTest.msg(MSG_REMOVE_COUNTER, b.array())).getFirst();
        assertEquals(2001, rc.counterType());
        assertEquals(1, rc.controller());
        assertEquals(LOCATION_SZONE, rc.location());
        assertEquals(4, rc.sequence());
        assertEquals(1, rc.count());
    }

    /** MSG_TOSS_DICE (4 sites): player + count + n×u8 results. */
    @Test
    void parseTossDice() {
        var td = (DuelMessage.TossDice) MessageParser.parse(MessageParserTest.msg(MSG_TOSS_DICE, new byte[]{1, 2, 3, 5})).getFirst();
        assertEquals(1, td.player());
        assertEquals(List.of(3, 5), td.results());
    }

    /** playerop.cpp MSG_ANNOUNCE_ATTRIB: player + count + available(i32 mask). */
    @Test
    void parseAnnounceAttrib() {
        ByteBuffer b = MessageParserTest.body(1 + 1 + 4);
        b.put((byte) 0);
        b.put((byte) 2);
        b.putInt(ATTRIBUTE_DARK | ATTRIBUTE_LIGHT);

        var aa = (DuelMessage.AnnounceAttrib) MessageParser.parse(MessageParserTest.msg(MSG_ANNOUNCE_ATTRIB, b.array())).getFirst();
        assertEquals(0, aa.player());
        assertEquals(2, aa.count());
        assertEquals(ATTRIBUTE_DARK | ATTRIBUTE_LIGHT, aa.available());
    }

    /** playerop.cpp MSG_ANNOUNCE_NUMBER: player + count + n×i64 options. */
    @Test
    void parseAnnounceNumber() {
        ByteBuffer b = MessageParserTest.body(1 + 1 + 8 * 2);
        b.put((byte) 1);
        b.put((byte) 2);
        b.putLong(3L);
        b.putLong(5L);

        var an = (DuelMessage.AnnounceNumber) MessageParser.parse(MessageParserTest.msg(MSG_ANNOUNCE_NUMBER, b.array())).getFirst();
        assertEquals(1, an.player());
        assertEquals(List.of(3L, 5L), an.options());
    }

    /** MSG_CARD_HINT (5 sites): loc_info + chintType(u8) + value(i64). */
    @Test
    void parseCardHint() {
        ByteBuffer b = MessageParserTest.body(10 + 1 + 8);
        MessageParserTest.putLocInfo(b, 0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK);
        b.put((byte) CHINT_DESC_ADD);
        b.putLong(89631139L << 4);

        var ch = (DuelMessage.CardHint) MessageParser.parse(MessageParserTest.msg(MSG_CARD_HINT, b.array())).getFirst();
        assertEquals(new LocInfo(0, LOCATION_MZONE, 2, POS_FACEUP_ATTACK), ch.location());
        assertEquals(CHINT_DESC_ADD, ch.chintType());
        assertEquals(89631139L << 4, ch.value());
    }
}

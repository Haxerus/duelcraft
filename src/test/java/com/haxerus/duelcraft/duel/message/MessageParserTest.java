package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageParserTest {

    /** Extract just the DuelMessage list from parse results. */
    static List<DuelMessage> parseMessages(byte[] buffer) {
        return MessageParser.parse(buffer).stream()
                .map(ParsedEntry::message).toList();
    }

    // ---- Helpers to build little-endian message buffers ----

    /**
     * Build a complete message buffer: [uint32 length][uint8 type][body...].
     * length = 1 (type byte) + body.length.
     */
    static byte[] msg(int type, byte[] body) {
        int length = 1 + body.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.put((byte) type);
        buf.put(body);
        return buf.array();
    }

    /** Concatenate multiple byte arrays. */
    static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        ByteBuffer buf = ByteBuffer.allocate(total);
        for (byte[] a : arrays) buf.put(a);
        return buf.array();
    }

    /** Build a little-endian body from a sequence of typed writes. */
    static ByteBuffer body(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Encode a LocInfo: [uint8 con][uint8 loc][int32 seq][int32 pos]. */
    static void putLocInfo(ByteBuffer buf, int con, int loc, int seq, int pos) {
        buf.put((byte) con);
        buf.put((byte) loc);
        buf.putInt(seq);
        buf.putInt(pos);
    }

    /** Encode a CardInfo: [int32 code][uint8 con][uint8 loc][int32 seq][int32 pos]. */
    static void putCardInfo(ByteBuffer buf, int code, int con, int loc, int seq, int pos) {
        buf.putInt(code);
        buf.put((byte) con);
        buf.put((byte) loc);
        buf.putInt(seq);
        buf.putInt(pos);
    }

    // ---- Lifecycle Messages ----

    @Test
    void parseStart() {
        // [uint8 playerType][int32 lp0][int32 lp1][uint16 deck0][uint16 extra0][uint16 deck1][uint16 extra1]
        ByteBuffer b = body(17);
        b.put((byte) 0);       // playerType
        b.putInt(8000);         // lp0
        b.putInt(8000);         // lp1
        b.putShort((short) 40); // deck0
        b.putShort((short) 15); // extra0
        b.putShort((short) 40); // deck1
        b.putShort((short) 15); // extra1

        List<DuelMessage> msgs = parseMessages(msg(MSG_START, b.array()));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.Start.class, msgs.getFirst());
        var start = (DuelMessage.Start) msgs.getFirst();
        assertEquals(0, start.playerType());
        assertEquals(8000, start.lp0());
        assertEquals(8000, start.lp1());
        assertEquals(40, start.deckCount0());
        assertEquals(15, start.extraCount0());
        assertEquals(40, start.deckCount1());
        assertEquals(15, start.extraCount1());
    }

    @Test
    void parseWin() {
        // [uint8 winner][uint8 reason]
        ByteBuffer b = body(2);
        b.put((byte) 1); // winner = player 1
        b.put((byte) 3); // reason

        List<DuelMessage> msgs = parseMessages(msg(MSG_WIN, b.array()));
        assertEquals(1, msgs.size());
        var win = (DuelMessage.Win) msgs.getFirst();
        assertEquals(1, win.winner());
        assertEquals(3, win.reason());
    }

    @Test
    void parseNewTurn() {
        List<DuelMessage> msgs = parseMessages(msg(MSG_NEW_TURN, new byte[]{0}));
        assertEquals(1, msgs.size());
        var turn = (DuelMessage.NewTurn) msgs.getFirst();
        assertEquals(0, turn.player());
    }

    @Test
    void parseNewPhase() {
        ByteBuffer b = body(2);
        b.putShort((short) PHASE_MAIN1);
        List<DuelMessage> msgs = parseMessages(msg(MSG_NEW_PHASE, b.array()));
        var phase = (DuelMessage.NewPhase) msgs.getFirst();
        assertEquals(PHASE_MAIN1, phase.phase());
    }

    // ---- Card Movement ----

    @Test
    void parseDraw() {
        // [uint8 player][int32 count][int32[] codes]
        ByteBuffer b = body(1 + 4 + 4 * 3);
        b.put((byte) 0);     // player
        b.putInt(3);          // count
        b.putInt(89631139);   // Blue-Eyes White Dragon
        b.putInt(46986414);   // Dark Magician
        b.putInt(55144522);   // Pot of Greed

        List<DuelMessage> msgs = parseMessages(msg(MSG_DRAW, b.array()));
        var draw = (DuelMessage.Draw) msgs.getFirst();
        assertEquals(0, draw.player());
        assertEquals(List.of(89631139, 46986414, 55144522), draw.codes());
    }

    @Test
    void parseMove() {
        // [int32 code][locinfo from][locinfo to][int32 reason]
        ByteBuffer b = body(4 + 10 + 10 + 4);
        b.putInt(89631139);                                   // code
        putLocInfo(b, 0, LOCATION_HAND, 2, POS_FACEDOWN_DEFENSE); // from
        putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);  // to
        b.putInt(REASON_SUMMON);                              // reason

        List<DuelMessage> msgs = parseMessages(msg(MSG_MOVE, b.array()));
        var move = (DuelMessage.Move) msgs.getFirst();
        assertEquals(89631139, move.code());
        assertEquals(LOCATION_HAND, move.from().location());
        assertEquals(LOCATION_MZONE, move.to().location());
        assertEquals(POS_FACEUP_ATTACK, move.to().position());
        assertEquals(REASON_SUMMON, move.reason());
    }

    @Test
    void parsePosChange() {
        // [int32 code][uint8 con][uint8 loc][uint8 seq][uint8 prevPos][uint8 newPos]
        ByteBuffer b = body(4 + 5);
        b.putInt(89631139);
        b.put((byte) 0);                   // controller
        b.put((byte) LOCATION_MZONE);      // location
        b.put((byte) 2);                   // sequence
        b.put((byte) POS_FACEUP_ATTACK);   // prev
        b.put((byte) POS_FACEUP_DEFENSE);  // new

        List<DuelMessage> msgs = parseMessages(msg(MSG_POS_CHANGE, b.array()));
        var pc = (DuelMessage.PosChange) msgs.getFirst();
        assertEquals(89631139, pc.code());
        assertEquals(POS_FACEUP_ATTACK, pc.prevPosition());
        assertEquals(POS_FACEUP_DEFENSE, pc.newPosition());
    }

    @Test
    void parseSet() {
        ByteBuffer b = body(4 + 10);
        b.putInt(44095762);  // Mirror Force
        putLocInfo(b, 0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SET, b.array()));
        var set = (DuelMessage.Set) msgs.getFirst();
        assertEquals(44095762, set.code());
        assertEquals(LOCATION_SZONE, set.location().location());
        assertEquals(POS_FACEDOWN_DEFENSE, set.location().position());
    }

    // ---- Summons ----

    @Test
    void parseSummoning() {
        ByteBuffer b = body(4 + 10);
        b.putInt(89631139);
        putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SUMMONING, b.array()));
        var summon = (DuelMessage.Summoning) msgs.getFirst();
        assertEquals(89631139, summon.code());
        assertEquals(LOCATION_MZONE, summon.location().location());
    }

    @Test
    void parseSummonedNoBody() {
        // MSG_SUMMONED has no body
        List<DuelMessage> msgs = parseMessages(msg(MSG_SUMMONED, new byte[0]));
        assertInstanceOf(DuelMessage.Summoned.class, msgs.getFirst());
    }

    // ---- Chain ----

    @Test
    void parseChaining() {
        // [int32 code][locinfo][uint8 chainIdx][int64 desc][uint8 chainCount]
        ByteBuffer b = body(4 + 10 + 1 + 8 + 1);
        b.putInt(44095762);  // Mirror Force
        putLocInfo(b, 0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE);
        b.put((byte) 1);    // chain index
        b.putLong(0L);      // desc
        b.put((byte) 1);    // chain count

        List<DuelMessage> msgs = parseMessages(msg(MSG_CHAINING, b.array()));
        var chain = (DuelMessage.Chaining) msgs.getFirst();
        assertEquals(44095762, chain.code());
        assertEquals(1, chain.chainIndex());
    }

    @Test
    void parseChainEnd() {
        List<DuelMessage> msgs = parseMessages(msg(MSG_CHAIN_END, new byte[0]));
        assertInstanceOf(DuelMessage.ChainEnd.class, msgs.getFirst());
    }

    @Test
    void parseChainedSolvedNegated() {
        // These all have a single uint8 chain index
        List<DuelMessage> msgs;
        msgs = parseMessages(msg(MSG_CHAINED, new byte[]{2}));
        assertEquals(2, ((DuelMessage.Chained) msgs.getFirst()).chainIndex());

        msgs = parseMessages(msg(MSG_CHAIN_SOLVING, new byte[]{3}));
        assertEquals(3, ((DuelMessage.ChainSolving) msgs.getFirst()).chainIndex());

        msgs = parseMessages(msg(MSG_CHAIN_NEGATED, new byte[]{1}));
        assertEquals(1, ((DuelMessage.ChainNegated) msgs.getFirst()).chainIndex());
    }

    // ---- LP ----

    @Test
    void parseDamage() {
        ByteBuffer b = body(5);
        b.put((byte) 1);  // player
        b.putInt(2400);    // amount

        List<DuelMessage> msgs = parseMessages(msg(MSG_DAMAGE, b.array()));
        var dmg = (DuelMessage.Damage) msgs.getFirst();
        assertEquals(1, dmg.player());
        assertEquals(2400, dmg.amount());
    }

    @Test
    void parseRecover() {
        ByteBuffer b = body(5);
        b.put((byte) 0);
        b.putInt(1000);

        List<DuelMessage> msgs = parseMessages(msg(MSG_RECOVER, b.array()));
        var rec = (DuelMessage.Recover) msgs.getFirst();
        assertEquals(0, rec.player());
        assertEquals(1000, rec.amount());
    }

    @Test
    void parseLpUpdate() {
        ByteBuffer b = body(5);
        b.put((byte) 0);
        b.putInt(5600);

        List<DuelMessage> msgs = parseMessages(msg(MSG_LPUPDATE, b.array()));
        var lp = (DuelMessage.LpUpdate) msgs.getFirst();
        assertEquals(5600, lp.lp());
    }

    // ---- Battle ----

    @Test
    void parseAttack() {
        ByteBuffer b = body(20);
        putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);  // attacker
        putLocInfo(b, 1, LOCATION_MZONE, 2, POS_FACEUP_ATTACK);  // target

        List<DuelMessage> msgs = parseMessages(msg(MSG_ATTACK, b.array()));
        var atk = (DuelMessage.Attack) msgs.getFirst();
        assertEquals(0, atk.attacker().controller());
        assertEquals(1, atk.target().controller());
    }

    @Test
    void parseBattle() {
        ByteBuffer b = body(10 + 8 + 10 + 8);
        putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);
        b.putInt(3000);  // attacker ATK
        b.putInt(2500);  // attacker DEF
        putLocInfo(b, 1, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);
        b.putInt(2500);  // defender ATK
        b.putInt(2100);  // defender DEF

        List<DuelMessage> msgs = parseMessages(msg(MSG_BATTLE, b.array()));
        var battle = (DuelMessage.Battle) msgs.getFirst();
        assertEquals(3000, battle.atkAtk());
        assertEquals(2500, battle.defAtk());
    }

    @Test
    void parseBattleStepMarkers() {
        // No-body messages
        assertInstanceOf(DuelMessage.AttackDisabled.class,
                parseMessages(msg(MSG_ATTACK_DISABLED, new byte[0])).getFirst());
        assertInstanceOf(DuelMessage.DamageStepStart.class,
                parseMessages(msg(MSG_DAMAGE_STEP_START, new byte[0])).getFirst());
        assertInstanceOf(DuelMessage.DamageStepEnd.class,
                parseMessages(msg(MSG_DAMAGE_STEP_END, new byte[0])).getFirst());
    }

    // ---- Hint ----

    @Test
    void parseHint() {
        ByteBuffer b = body(10);
        b.put((byte) HINT_SELECTMSG);
        b.put((byte) 0);
        b.putLong(560L);  // string code

        List<DuelMessage> msgs = parseMessages(msg(MSG_HINT, b.array()));
        var hint = (DuelMessage.Hint) msgs.getFirst();
        assertEquals(HINT_SELECTMSG, hint.hintType());
        assertEquals(0, hint.player());
        assertEquals(560L, hint.data());
    }

    // ---- Deck/Hand ----

    @Test
    void parseShuffleDeck() {
        List<DuelMessage> msgs = parseMessages(msg(MSG_SHUFFLE_DECK, new byte[]{1}));
        var sd = (DuelMessage.ShuffleDeck) msgs.getFirst();
        assertEquals(1, sd.player());
    }

    @Test
    void parseShuffleHand() {
        ByteBuffer b = body(1 + 4 + 4 * 2);
        b.put((byte) 0);
        b.putInt(2);
        b.putInt(89631139);
        b.putInt(46986414);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SHUFFLE_HAND, b.array()));
        var sh = (DuelMessage.ShuffleHand) msgs.getFirst();
        assertEquals(0, sh.player());
        assertEquals(List.of(89631139, 46986414), sh.codes());
    }

    // ---- Selection Messages ----

    @Test
    void parseSelectEffectYn() {
        ByteBuffer b = body(1 + 4 + 10 + 8);
        b.put((byte) 0);      // player
        b.putInt(44095762);    // Mirror Force code
        putLocInfo(b, 0, LOCATION_SZONE, 1, POS_FACEDOWN_DEFENSE);
        b.putLong(44095762L);  // desc

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_EFFECTYN, b.array()));
        var yn = (DuelMessage.SelectEffectYn) msgs.getFirst();
        assertEquals(0, yn.player());
        assertEquals(44095762, yn.code());
        assertEquals(LOCATION_SZONE, yn.location().location());
    }

    @Test
    void parseSelectYesNo() {
        ByteBuffer b = body(9);
        b.put((byte) 1);
        b.putLong(200L);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_YESNO, b.array()));
        var yn = (DuelMessage.SelectYesNo) msgs.getFirst();
        assertEquals(1, yn.player());
        assertEquals(200L, yn.desc());
    }

    @Test
    void parseSelectOption() {
        ByteBuffer b = body(1 + 1 + 8 * 2);
        b.put((byte) 0);  // player
        b.put((byte) 2);  // count
        b.putLong(100L);   // option 1
        b.putLong(200L);   // option 2

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_OPTION, b.array()));
        var opt = (DuelMessage.SelectOption) msgs.getFirst();
        assertEquals(0, opt.player());
        assertEquals(List.of(100L, 200L), opt.options());
    }

    @Test
    void parseSelectCard() {
        // [uint8 player][uint8 cancelable][int32 min][int32 max][int32 count][CardInfo...]
        ByteBuffer b = body(1 + 1 + 4 + 4 + 4 + 14 * 2);
        b.put((byte) 0);    // player
        b.put((byte) 0);    // not cancelable
        b.putInt(1);         // min
        b.putInt(2);         // max
        b.putInt(2);         // count
        putCardInfo(b, 89631139, 0, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE);
        putCardInfo(b, 46986414, 0, LOCATION_HAND, 1, POS_FACEDOWN_DEFENSE);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_CARD, b.array()));
        var sc = (DuelMessage.SelectCard) msgs.getFirst();
        assertEquals(0, sc.player());
        assertFalse(sc.cancelable());
        assertEquals(1, sc.min());
        assertEquals(2, sc.max());
        assertEquals(2, sc.cards().size());
        assertEquals(89631139, sc.cards().get(0).code());
        assertEquals(46986414, sc.cards().get(1).code());
    }

    @Test
    void parseSelectPlace() {
        ByteBuffer b = body(6);
        b.put((byte) 0);      // player
        b.put((byte) 1);      // count
        b.putInt(0x0000001F);  // field bitmask

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_PLACE, b.array()));
        var sp = (DuelMessage.SelectPlace) msgs.getFirst();
        assertEquals(0, sp.player());
        assertEquals(1, sp.count());
        assertEquals(0x1F, sp.field());
    }

    @Test
    void parseSelectPosition() {
        ByteBuffer b = body(6);
        b.put((byte) 0);           // player
        b.putInt(89631139);         // code
        b.put((byte) (POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE)); // available positions

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_POSITION, b.array()));
        var sp = (DuelMessage.SelectPosition) msgs.getFirst();
        assertEquals(89631139, sp.code());
        assertEquals(POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE, sp.positions());
    }

    @Test
    void parseSelectTribute() {
        ByteBuffer b = body(1 + 1 + 4 + 4 + 4 + 14);
        b.put((byte) 0);  // player
        b.put((byte) 0);  // not cancelable
        b.putInt(1);       // min
        b.putInt(1);       // max
        b.putInt(1);       // count
        putCardInfo(b, 69247929, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_TRIBUTE, b.array()));
        var st = (DuelMessage.SelectTribute) msgs.getFirst();
        assertEquals(1, st.min());
        assertEquals(1, st.cards().size());
        assertEquals(69247929, st.cards().getFirst().code());
    }

    @Test
    void parseSelectChain() {
        // [uint8 player][uint8 count][uint8 forced][...chain response...]
        ByteBuffer b = body(3 + 6); // 3 header bytes + some raw chain response
        b.put((byte) 0);   // player
        b.put((byte) 2);   // count
        b.put((byte) 1);   // forced
        // Remaining bytes are raw chain response
        b.put(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06});

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_CHAIN, b.array()));
        var sc = (DuelMessage.SelectChain) msgs.getFirst();
        assertEquals(0, sc.player());
        assertEquals(2, sc.count());
        assertTrue(sc.forced());
        assertEquals(6, sc.rawBody().length);
    }

    @Test
    void parseSelectUnselectCard() {
        ByteBuffer b = body(1 + 1 + 1 + 4 + 4 + 4 + 14 + 4 + 14);
        b.put((byte) 0);    // player
        b.put((byte) 1);    // finishable
        b.put((byte) 0);    // not cancelable
        b.putInt(1);         // min
        b.putInt(3);         // max
        b.putInt(1);         // selectable count
        putCardInfo(b, 89631139, 0, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE);
        b.putInt(1);         // unselectable count
        putCardInfo(b, 46986414, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);

        List<DuelMessage> msgs = parseMessages(msg(MSG_SELECT_UNSELECT_CARD, b.array()));
        var su = (DuelMessage.SelectUnselectCard) msgs.getFirst();
        assertTrue(su.finishable());
        assertFalse(su.cancelable());
        assertEquals(1, su.selectableCards().size());
        assertEquals(1, su.unselectableCards().size());
        assertEquals(89631139, su.selectableCards().getFirst().code());
        assertEquals(46986414, su.unselectableCards().getFirst().code());
    }

    @Test
    void parseAnnounceRace() {
        ByteBuffer b = body(10);
        b.put((byte) 0);  // player
        b.put((byte) 1);  // count
        b.putLong(RACE_DRAGON | RACE_SPELLCASTER);

        List<DuelMessage> msgs = parseMessages(msg(MSG_ANNOUNCE_RACE, b.array()));
        var ar = (DuelMessage.AnnounceRace) msgs.getFirst();
        assertEquals(1, ar.count());
        assertTrue((ar.available() & RACE_DRAGON) != 0);
        assertTrue((ar.available() & RACE_SPELLCASTER) != 0);
    }

    @Test
    void parseRockPaperScissors() {
        List<DuelMessage> msgs = parseMessages(msg(MSG_ROCK_PAPER_SCISSORS, new byte[]{0}));
        var rps = (DuelMessage.RockPaperScissors) msgs.getFirst();
        assertEquals(0, rps.player());
    }

    // ---- Misc ----

    @Test
    void parseEquip() {
        ByteBuffer b = body(20);
        putLocInfo(b, 0, LOCATION_SZONE, 1, POS_FACEUP_ATTACK);
        putLocInfo(b, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK);

        List<DuelMessage> msgs = parseMessages(msg(MSG_EQUIP, b.array()));
        var eq = (DuelMessage.Equip) msgs.getFirst();
        assertEquals(LOCATION_SZONE, eq.card().location());
        assertEquals(LOCATION_MZONE, eq.target().location());
    }

    @Test
    void parseTossCoin() {
        ByteBuffer b = body(1 + 1 + 3);
        b.put((byte) 0);  // player
        b.put((byte) 3);  // count
        b.put((byte) 1);  // heads
        b.put((byte) 0);  // tails
        b.put((byte) 1);  // heads

        List<DuelMessage> msgs = parseMessages(msg(MSG_TOSS_COIN, b.array()));
        var tc = (DuelMessage.TossCoin) msgs.getFirst();
        assertEquals(0, tc.player());
        assertEquals(List.of(1, 0, 1), tc.results());
    }

    // ---- Buffer-level tests ----

    @Test
    void multipleMessagesInOneBuffer() {
        byte[] buf = concat(
                msg(MSG_NEW_TURN, new byte[]{0}),
                msg(MSG_NEW_PHASE, body(2).putShort((short) PHASE_DRAW).array()),
                msg(MSG_SHUFFLE_DECK, new byte[]{0})
        );

        List<DuelMessage> msgs = parseMessages(buf);
        assertEquals(3, msgs.size());
        assertInstanceOf(DuelMessage.NewTurn.class, msgs.get(0));
        assertInstanceOf(DuelMessage.NewPhase.class, msgs.get(1));
        assertInstanceOf(DuelMessage.ShuffleDeck.class, msgs.get(2));
    }

    @Test
    void unknownMessageBecomesRaw() {
        byte[] unknownBody = {0x01, 0x02, 0x03};
        List<DuelMessage> msgs = parseMessages(msg(255, unknownBody));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.Raw.class, msgs.getFirst());
        var raw = (DuelMessage.Raw) msgs.getFirst();
        assertEquals(255, raw.type());
        assertEquals(3, raw.body().length);
    }

    @Test
    void rawBytesPreservedPerMessage() {
        byte[] msgBytes = msg(MSG_NEW_TURN, new byte[]{1});
        List<ParsedEntry> entries = MessageParser.parse(msgBytes);
        assertEquals(1, entries.size());
        // Raw bytes should be the complete message: length(4) + type(1) + body(1)
        assertArrayEquals(msgBytes, entries.getFirst().raw());
    }

    @Test
    void rawBytesPreservedForMultipleMessages() {
        byte[] msg1 = msg(MSG_NEW_TURN, new byte[]{0});
        byte[] msg2 = msg(MSG_SHUFFLE_DECK, new byte[]{1});
        byte[] buf = concat(msg1, msg2);

        List<ParsedEntry> entries = MessageParser.parse(buf);
        assertEquals(2, entries.size());
        assertArrayEquals(msg1, entries.get(0).raw());
        assertArrayEquals(msg2, entries.get(1).raw());
    }

    @Test
    void emptyBufferReturnsEmptyList() {
        assertEquals(List.of(), MessageParser.parse(new byte[0]).stream().map(ParsedEntry::message).toList());
        assertEquals(List.of(), MessageParser.parse(new byte[3]).stream().map(ParsedEntry::message).toList());
    }

    @Test
    void unknownMessageDoesNotCorruptSubsequentMessages() {
        // An unknown message followed by a known message should both parse correctly
        byte[] unknownBody = {0x0A, 0x0B, 0x0C, 0x0D};
        byte[] buf = concat(
                msg(253, unknownBody),
                msg(MSG_NEW_TURN, new byte[]{1})
        );

        List<DuelMessage> msgs = parseMessages(buf);
        assertEquals(2, msgs.size());
        assertInstanceOf(DuelMessage.Raw.class, msgs.get(0));
        assertInstanceOf(DuelMessage.NewTurn.class, msgs.get(1));
        assertEquals(1, ((DuelMessage.NewTurn) msgs.get(1)).player());
    }
}

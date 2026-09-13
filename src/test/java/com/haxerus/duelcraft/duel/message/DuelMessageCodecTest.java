package com.haxerus.duelcraft.duel.message;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full encode/decode round trip for every DuelMessage record.
 * LOC_OVERLAY_MZONE/LOC_OVERLAY_SZONE use the real overlay-material location bytes
 * (0x84 = LOCATION_OVERLAY|LOCATION_MZONE, 0x88 = LOCATION_OVERLAY|LOCATION_SZONE) that
 * decode negative under a signed readByte (see docs/engine-gap-analysis.md §3.4).
 */
class DuelMessageCodecTest {

    private static final LocInfo LOC_OVERLAY_MZONE = new LocInfo(0, 0x84, 3, 0x1);
    private static final LocInfo LOC_OVERLAY_SZONE = new LocInfo(1, 0x88, 5, 0x8);
    private static final LocInfo LOC_PLAIN = new LocInfo(1, 0x04, 2, 0x1);

    /** Above 2^32 so a naive int/long-narrowing bug would truncate it. */
    private static final long U64_ABOVE_32BIT = 0x1_0000_0007L;

    /** Top bit set: negative if ever misread as a narrower signed type. */
    private static final int INT_TOP_BIT = 0x80000000;

    private static QueriedCard queriedCard(int code, boolean withRefs) {
        QueriedCard c = new QueriedCard();
        c.flags = 0xFFFF;
        c.code = code;
        c.position = 0x1;
        c.alias = 0;
        c.type = 0x1;
        c.level = 4;
        c.rank = 0;
        c.attribute = 0x10;
        c.race = U64_ABOVE_32BIT;
        c.attack = 1000;
        c.defense = 800;
        c.baseAttack = 1000;
        c.baseDefense = 800;
        c.reason = 0;
        c.owner = 1;
        c.status = 0;
        c.isPublic = true;
        c.isHidden = false;
        c.lscale = 1;
        c.rscale = 2;
        c.linkRating = 0;
        c.linkMarker = 0;
        c.cover = 0;
        if (withRefs) {
            c.overlayCards = List.of(11111111, 22222222);
            c.counters = List.of(1);
            c.reasonCard = LOC_OVERLAY_MZONE;
            c.equipCard = LOC_OVERLAY_SZONE;
            c.targetCards = List.of(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE);
        } else {
            c.overlayCards = List.of();
            c.counters = List.of();
            c.reasonCard = null;
            c.equipCard = null;
            c.targetCards = List.of();
        }
        return c;
    }

    private static final Map<Class<?>, DuelMessage> SAMPLES = new LinkedHashMap<>();

    static {
        SAMPLES.put(DuelMessage.Raw.class, new DuelMessage.Raw(99, new byte[]{9, 8, 7, 6, 5}));
        SAMPLES.put(DuelMessage.Retry.class, new DuelMessage.Retry());
        SAMPLES.put(DuelMessage.Waiting.class, new DuelMessage.Waiting());
        SAMPLES.put(DuelMessage.Win.class, new DuelMessage.Win(1, 2));
        SAMPLES.put(DuelMessage.UpdateData.class,
                new DuelMessage.UpdateData(1, 0x84, List.of(queriedCard(1001, true), queriedCard(1002, false))));
        SAMPLES.put(DuelMessage.UpdateCard.class,
                new DuelMessage.UpdateCard(0, 0x88, 3, queriedCard(1003, true)));
        SAMPLES.put(DuelMessage.NewTurn.class, new DuelMessage.NewTurn(1));
        SAMPLES.put(DuelMessage.NewPhase.class, new DuelMessage.NewPhase(64));
        SAMPLES.put(DuelMessage.Draw.class, new DuelMessage.Draw(0,
                List.of(new DuelMessage.DrawnCard(12345678, 1), new DuelMessage.DrawnCard(87654321, 1))));
        SAMPLES.put(DuelMessage.Move.class, new DuelMessage.Move(55555555, LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE, 0x1000));
        SAMPLES.put(DuelMessage.PosChange.class, new DuelMessage.PosChange(11112222, 1, 0x84, 3, 0x1, 0x8));
        SAMPLES.put(DuelMessage.Set.class, new DuelMessage.Set(22223333, LOC_PLAIN));
        SAMPLES.put(DuelMessage.Swap.class, new DuelMessage.Swap(1, LOC_OVERLAY_MZONE, 2, LOC_OVERLAY_SZONE));
        SAMPLES.put(DuelMessage.Summoning.class, new DuelMessage.Summoning(3, LOC_PLAIN));
        SAMPLES.put(DuelMessage.Summoned.class, new DuelMessage.Summoned());
        SAMPLES.put(DuelMessage.SpSummoning.class, new DuelMessage.SpSummoning(4, LOC_PLAIN));
        SAMPLES.put(DuelMessage.SpSummoned.class, new DuelMessage.SpSummoned());
        SAMPLES.put(DuelMessage.FlipSummoning.class, new DuelMessage.FlipSummoning(5, LOC_PLAIN));
        SAMPLES.put(DuelMessage.FlipSummoned.class, new DuelMessage.FlipSummoned());
        SAMPLES.put(DuelMessage.Chaining.class,
                new DuelMessage.Chaining(6, LOC_PLAIN, 1, 0x84, 2, U64_ABOVE_32BIT, 3));
        SAMPLES.put(DuelMessage.Chained.class, new DuelMessage.Chained(0));
        SAMPLES.put(DuelMessage.ChainSolving.class, new DuelMessage.ChainSolving(1));
        SAMPLES.put(DuelMessage.ChainSolved.class, new DuelMessage.ChainSolved(2));
        SAMPLES.put(DuelMessage.ChainEnd.class, new DuelMessage.ChainEnd());
        SAMPLES.put(DuelMessage.ChainNegated.class, new DuelMessage.ChainNegated(1));
        SAMPLES.put(DuelMessage.ChainDisabled.class, new DuelMessage.ChainDisabled(2));
        SAMPLES.put(DuelMessage.Damage.class, new DuelMessage.Damage(0, 500));
        SAMPLES.put(DuelMessage.Recover.class, new DuelMessage.Recover(1, 300));
        SAMPLES.put(DuelMessage.LpUpdate.class, new DuelMessage.LpUpdate(0, 7000));
        SAMPLES.put(DuelMessage.PayLpCost.class, new DuelMessage.PayLpCost(1, 800));
        SAMPLES.put(DuelMessage.Attack.class, new DuelMessage.Attack(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE));
        SAMPLES.put(DuelMessage.Battle.class,
                new DuelMessage.Battle(LOC_OVERLAY_MZONE, 2500, 2000, 1, LOC_OVERLAY_SZONE, 1800, 1200, 0));
        SAMPLES.put(DuelMessage.AttackDisabled.class, new DuelMessage.AttackDisabled());
        SAMPLES.put(DuelMessage.DamageStepStart.class, new DuelMessage.DamageStepStart());
        SAMPLES.put(DuelMessage.DamageStepEnd.class, new DuelMessage.DamageStepEnd());
        SAMPLES.put(DuelMessage.ShuffleDeck.class, new DuelMessage.ShuffleDeck(0));
        SAMPLES.put(DuelMessage.ShuffleHand.class, new DuelMessage.ShuffleHand(1, List.of()));
        SAMPLES.put(DuelMessage.ShuffleExtra.class, new DuelMessage.ShuffleExtra(1));
        SAMPLES.put(DuelMessage.ConfirmDeckTop.class, new DuelMessage.ConfirmDeckTop(0,
                List.of(new DuelMessage.ConfirmCard(111, 0, 0x01, 0), new DuelMessage.ConfirmCard(222, 1, 0x04, 1))));
        SAMPLES.put(DuelMessage.ConfirmCards.class, new DuelMessage.ConfirmCards(1,
                List.of(new DuelMessage.ConfirmCard(333, 1, 0x84, 2))));
        SAMPLES.put(DuelMessage.ConfirmExtraTop.class, new DuelMessage.ConfirmExtraTop(1,
                List.of(new DuelMessage.ConfirmCard(444, 1, 0x40, 14))));
        SAMPLES.put(DuelMessage.ReverseDeck.class, new DuelMessage.ReverseDeck());
        SAMPLES.put(DuelMessage.DeckTop.class, new DuelMessage.DeckTop(1, 2, 89631139, 0x4));
        SAMPLES.put(DuelMessage.SwapGraveDeck.class,
                new DuelMessage.SwapGraveDeck(1, 15, new byte[]{0b0000_1001, 0b0000_0010}));
        SAMPLES.put(DuelMessage.ShuffleSetCard.class, new DuelMessage.ShuffleSetCard(0x08,
                List.of(LOC_PLAIN, LOC_OVERLAY_SZONE), List.of(LOC_PLAIN, new LocInfo(0, 0, 0, 0))));
        SAMPLES.put(DuelMessage.RemoveCards.class,
                new DuelMessage.RemoveCards(List.of(LOC_OVERLAY_MZONE, LOC_PLAIN)));
        SAMPLES.put(DuelMessage.CardSelected.class, new DuelMessage.CardSelected(List.of(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE)));
        SAMPLES.put(DuelMessage.RandomSelected.class,
                new DuelMessage.RandomSelected(1, List.of(LOC_PLAIN, LOC_OVERLAY_MZONE)));
        SAMPLES.put(DuelMessage.MissedEffect.class,
                new DuelMessage.MissedEffect(LOC_OVERLAY_SZONE, 89631139));
        SAMPLES.put(DuelMessage.MatchKill.class, new DuelMessage.MatchKill(89631139));
        SAMPLES.put(DuelMessage.Hint.class, new DuelMessage.Hint(3, 0, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.PlayerHint.class, new DuelMessage.PlayerHint(1, 6, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.CardHint.class, new DuelMessage.CardHint(LOC_PLAIN, 2, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.FieldDisabled.class, new DuelMessage.FieldDisabled(INT_TOP_BIT));
        SAMPLES.put(DuelMessage.BecomeTarget.class, new DuelMessage.BecomeTarget(List.of(LOC_OVERLAY_MZONE)));
        SAMPLES.put(DuelMessage.SelectIdleCmd.class, new DuelMessage.SelectIdleCmd(0,
                List.of(new DuelMessage.IdleCmdCard(1, 0, 0x04, 0)),
                List.of(),
                List.of(new DuelMessage.ReposCard(2, 0, 0x04, 1)),
                List.of(new DuelMessage.IdleCmdCard(3, 0, 0x04, 2)),
                List.of(),
                List.of(new DuelMessage.ActivatableCard(4, 0, 0x08, 0, 0, U64_ABOVE_32BIT, 1)),
                true, true, false));
        SAMPLES.put(DuelMessage.SelectBattleCmd.class, new DuelMessage.SelectBattleCmd(1,
                List.of(new DuelMessage.ActivatableCard(5, 1, 0x08, 1, 0, U64_ABOVE_32BIT, 0)),
                List.of(new DuelMessage.AttackCard(6, 1, 0x04, 0, 1)),
                false, true));
        SAMPLES.put(DuelMessage.SelectCard.class, new DuelMessage.SelectCard(0, true, 1, 2,
                List.of(new DuelMessage.CardInfo(7, 0, 0x04, 0, 1))));
        SAMPLES.put(DuelMessage.SelectChain.class, new DuelMessage.SelectChain(1, 2, true, 3, 4,
                List.of(new DuelMessage.ActivatableCard(8, 1, 0x08, 2, 0x8, U64_ABOVE_32BIT, 1))));
        SAMPLES.put(DuelMessage.SelectEffectYn.class, new DuelMessage.SelectEffectYn(0, 9, LOC_OVERLAY_MZONE, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.SelectYesNo.class, new DuelMessage.SelectYesNo(1, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.SelectOption.class, new DuelMessage.SelectOption(0, List.of(1L, 2L, 3L)));
        SAMPLES.put(DuelMessage.SelectPlace.class, new DuelMessage.SelectPlace(1, 1, INT_TOP_BIT));
        SAMPLES.put(DuelMessage.SelectDisfield.class, new DuelMessage.SelectDisfield(0, 2, INT_TOP_BIT));
        SAMPLES.put(DuelMessage.SelectPosition.class, new DuelMessage.SelectPosition(1, 10, 0xB));
        SAMPLES.put(DuelMessage.SelectTribute.class, new DuelMessage.SelectTribute(0, true, 1, 2,
                List.of(new DuelMessage.TributeCard(11, 0, 0x04, 0, 1))));
        SAMPLES.put(DuelMessage.SelectCounter.class, new DuelMessage.SelectCounter(1, 2000, 3,
                List.of(new DuelMessage.CounterCard(12, 1, 0x04, 1, 5))));
        SAMPLES.put(DuelMessage.SelectSum.class, new DuelMessage.SelectSum(0, true, 10, 1, 2,
                List.of(new DuelMessage.SumCard(13, 0, 0x04, 0, 1, 65537)), List.of()));
        SAMPLES.put(DuelMessage.SelectUnselectCard.class, new DuelMessage.SelectUnselectCard(1, true, false, 1, 2,
                List.of(new DuelMessage.CardInfo(14, 1, 0x04, 0, 1)), List.of()));
        SAMPLES.put(DuelMessage.SortCard.class, new DuelMessage.SortCard(0,
                List.of(new DuelMessage.SortableCard(15, 0, 0x04, 0))));
        SAMPLES.put(DuelMessage.SortChain.class, new DuelMessage.SortChain(1,
                List.of(new DuelMessage.SortableCard(16, 1, 0x08, 1))));
        SAMPLES.put(DuelMessage.AnnounceRace.class, new DuelMessage.AnnounceRace(0, 1, U64_ABOVE_32BIT));
        SAMPLES.put(DuelMessage.AnnounceAttrib.class, new DuelMessage.AnnounceAttrib(1, 2, INT_TOP_BIT));
        SAMPLES.put(DuelMessage.AnnounceNumber.class, new DuelMessage.AnnounceNumber(0, List.of(1L, 2L)));
        SAMPLES.put(DuelMessage.AnnounceCard.class, new DuelMessage.AnnounceCard(1, List.of()));
        SAMPLES.put(DuelMessage.RockPaperScissors.class, new DuelMessage.RockPaperScissors(0));
        SAMPLES.put(DuelMessage.HandResult.class, new DuelMessage.HandResult(1, 2));
        SAMPLES.put(DuelMessage.Equip.class, new DuelMessage.Equip(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE));
        SAMPLES.put(DuelMessage.CardTarget.class, new DuelMessage.CardTarget(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE));
        SAMPLES.put(DuelMessage.CancelTarget.class, new DuelMessage.CancelTarget(LOC_OVERLAY_MZONE, LOC_OVERLAY_SZONE));
        SAMPLES.put(DuelMessage.AddCounter.class, new DuelMessage.AddCounter(2000, 1, 0x84, 3, 5));
        SAMPLES.put(DuelMessage.RemoveCounter.class, new DuelMessage.RemoveCounter(2001, 0, 0x88, 4, 2));
        SAMPLES.put(DuelMessage.TossCoin.class, new DuelMessage.TossCoin(0, List.of(1, 2)));
        SAMPLES.put(DuelMessage.TossDice.class, new DuelMessage.TossDice(1, List.of(3, 4, 5, 6)));
    }

    private static DuelMessage sample(Class<? extends DuelMessage> cls) {
        DuelMessage s = SAMPLES.get(cls);
        if (s == null) throw new IllegalArgumentException("No sample registered for " + cls);
        return s;
    }

    @Test
    void samplesCoverEveryPermittedSubclassExactly() {
        assertEquals(Set.of(DuelMessage.class.getPermittedSubclasses()), SAMPLES.keySet(),
                "a new DuelMessage record needs a sample here or the round trip below silently skips it");
    }

    @TestFactory
    List<DynamicTest> roundTripsEveryRecord() {
        List<DynamicTest> tests = new ArrayList<>();
        for (var entry : SAMPLES.entrySet()) {
            DuelMessage msg = entry.getValue();
            tests.add(DynamicTest.dynamicTest(entry.getKey().getSimpleName(), () -> assertRoundTrips(msg)));
        }
        return tests;
    }

    private static void assertRoundTrips(DuelMessage sample) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DuelMessageCodec.encode(buf, sample);
        DuelMessage decoded = DuelMessageCodec.decode(buf);
        assertEquals(0, buf.readableBytes(), "encode wrote bytes decode did not read");

        // QueriedCard is a mutable class with no equals(), and record equals() on a byte[]
        // field compares references, not contents; compare both by hand instead of relying
        // on the containing record's structural equality.
        if (sample instanceof DuelMessage.Raw expected) {
            DuelMessage.Raw actual = (DuelMessage.Raw) decoded;
            assertEquals(expected.type(), actual.type());
            assertArrayEquals(expected.body(), actual.body());
        } else if (sample instanceof DuelMessage.SwapGraveDeck expected) {
            DuelMessage.SwapGraveDeck actual = (DuelMessage.SwapGraveDeck) decoded;
            assertEquals(expected.player(), actual.player());
            assertEquals(expected.extraCount(), actual.extraCount());
            assertArrayEquals(expected.extraMask(), actual.extraMask());
        } else if (sample instanceof DuelMessage.UpdateData expected) {
            DuelMessage.UpdateData actual = (DuelMessage.UpdateData) decoded;
            assertEquals(expected.player(), actual.player());
            assertEquals(expected.location(), actual.location());
            assertEquals(expected.cards().size(), actual.cards().size());
            for (int i = 0; i < expected.cards().size(); i++) {
                assertQueriedCardEquals(expected.cards().get(i), actual.cards().get(i));
            }
        } else if (sample instanceof DuelMessage.UpdateCard expected) {
            DuelMessage.UpdateCard actual = (DuelMessage.UpdateCard) decoded;
            assertEquals(expected.player(), actual.player());
            assertEquals(expected.location(), actual.location());
            assertEquals(expected.sequence(), actual.sequence());
            assertQueriedCardEquals(expected.card(), actual.card());
        } else {
            assertEquals(sample, decoded);
        }
    }

    private static void assertQueriedCardEquals(QueriedCard expected, QueriedCard actual) {
        assertEquals(expected.flags, actual.flags);
        assertEquals(expected.code, actual.code);
        assertEquals(expected.position, actual.position);
        assertEquals(expected.alias, actual.alias);
        assertEquals(expected.type, actual.type);
        assertEquals(expected.level, actual.level);
        assertEquals(expected.rank, actual.rank);
        assertEquals(expected.attribute, actual.attribute);
        assertEquals(expected.race, actual.race);
        assertEquals(expected.attack, actual.attack);
        assertEquals(expected.defense, actual.defense);
        assertEquals(expected.baseAttack, actual.baseAttack);
        assertEquals(expected.baseDefense, actual.baseDefense);
        assertEquals(expected.reason, actual.reason);
        assertEquals(expected.owner, actual.owner);
        assertEquals(expected.status, actual.status);
        assertEquals(expected.isPublic, actual.isPublic);
        assertEquals(expected.isHidden, actual.isHidden);
        assertEquals(expected.lscale, actual.lscale);
        assertEquals(expected.rscale, actual.rscale);
        assertEquals(expected.linkRating, actual.linkRating);
        assertEquals(expected.linkMarker, actual.linkMarker);
        assertEquals(expected.cover, actual.cover);
        assertEquals(expected.overlayCards, actual.overlayCards);
        assertEquals(expected.counters, actual.counters);
        assertEquals(expected.reasonCard, actual.reasonCard);
        assertEquals(expected.equipCard, actual.equipCard);
        assertEquals(expected.targetCards, actual.targetCards);
    }

    @Test
    void decodeThrowsOnUnknownType() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        buf.writeByte(250); // not a recognised MSG_* constant
        assertThrows(DecoderException.class, () -> DuelMessageCodec.decode(buf));
    }

    @Test
    void readByteArrayRejectsOversizedRawBody() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeByte(1);
        buf.writeInt(2 * 1024 * 1024); // > 1 MiB bound
        assertThrows(DecoderException.class, () -> DuelMessageCodec.decode(buf));
    }

    private static final int ONE_MIB = 1024 * 1024;

    @Test
    void readByteArrayAcceptsExactlyOneMebibyte() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeByte(1);
        buf.writeInt(ONE_MIB);
        buf.writeBytes(new byte[ONE_MIB]);

        DuelMessage.Raw decoded = (DuelMessage.Raw) DuelMessageCodec.decode(buf);
        assertEquals(ONE_MIB, decoded.body().length);
    }

    @Test
    void readByteArrayRejectsOneMebibytePlusOne() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeByte(1);
        buf.writeInt(ONE_MIB + 1);
        assertThrows(DecoderException.class, () -> DuelMessageCodec.decode(buf));
    }

    @Test
    void readByteArrayRejectsNegativeLength() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeByte(1);
        buf.writeInt(-1);
        assertThrows(DecoderException.class, () -> DuelMessageCodec.decode(buf));
    }
}

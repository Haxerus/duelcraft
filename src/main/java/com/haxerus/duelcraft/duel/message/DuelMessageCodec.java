package com.haxerus.duelcraft.duel.message;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Encodes/decodes DuelMessage records for network transport.
 * This is our own serialization format — independent of the ygopro-core binary format.
 * The server parses engine bytes via MessageParser, then encodes typed messages here.
 * The client decodes them directly — no re-parsing.
 */
public class DuelMessageCodec {

    // ---- Encode ----

    public static void encode(FriendlyByteBuf buf, DuelMessage msg) {
        // Raw messages get a special prefix so the decoder doesn't try to parse them as typed
        if (msg instanceof DuelMessage.Raw raw) {
            buf.writeBoolean(true);
            buf.writeByte(raw.type());
            writeByteArray(buf, raw.body());
            return;
        }
        buf.writeBoolean(false);
        buf.writeByte(msg.type());

        switch (msg) {
            // System
            case DuelMessage.Retry ignored -> {}

            // Lifecycle
            case DuelMessage.Start m -> {
                buf.writeInt(m.playerType());
                buf.writeInt(m.lp0());
                buf.writeInt(m.lp1());
                buf.writeShort(m.deckCount0());
                buf.writeShort(m.extraCount0());
                buf.writeShort(m.deckCount1());
                buf.writeShort(m.extraCount1());
            }
            case DuelMessage.Win m -> { buf.writeByte(m.winner()); buf.writeByte(m.reason()); }
            case DuelMessage.UpdateData m -> {
                buf.writeByte(m.player());
                buf.writeByte(m.location());
                writeQueriedCardList(buf, m.cards());
            }
            case DuelMessage.UpdateCard m -> {
                buf.writeByte(m.player());
                buf.writeByte(m.location());
                buf.writeInt(m.sequence());
                writeQueriedCard(buf, m.card());
            }
            case DuelMessage.NewTurn m -> buf.writeByte(m.player());
            case DuelMessage.NewPhase m -> buf.writeShort(m.phase());

            // Card movement
            case DuelMessage.Draw m -> {
                buf.writeByte(m.player());
                writeIntList(buf, m.codes());
            }
            case DuelMessage.Move m -> {
                buf.writeInt(m.code());
                writeLocInfo(buf, m.from());
                writeLocInfo(buf, m.to());
                buf.writeInt(m.reason());
            }
            case DuelMessage.PosChange m -> {
                buf.writeInt(m.code());
                buf.writeByte(m.controller());
                buf.writeByte(m.location());
                buf.writeByte(m.sequence());
                buf.writeByte(m.prevPosition());
                buf.writeByte(m.newPosition());
            }
            case DuelMessage.Set m -> { buf.writeInt(m.code()); writeLocInfo(buf, m.location()); }
            case DuelMessage.Swap m -> {
                buf.writeInt(m.code1()); writeLocInfo(buf, m.loc1());
                buf.writeInt(m.code2()); writeLocInfo(buf, m.loc2());
            }

            // Summons
            case DuelMessage.Summoning m -> { buf.writeInt(m.code()); writeLocInfo(buf, m.location()); }
            case DuelMessage.Summoned ignored -> {}
            case DuelMessage.SpSummoning m -> { buf.writeInt(m.code()); writeLocInfo(buf, m.location()); }
            case DuelMessage.SpSummoned ignored -> {}
            case DuelMessage.FlipSummoning m -> { buf.writeInt(m.code()); writeLocInfo(buf, m.location()); }
            case DuelMessage.FlipSummoned ignored -> {}

            // Chain
            case DuelMessage.Chaining m -> {
                buf.writeInt(m.code());
                writeLocInfo(buf, m.location());
                buf.writeByte(m.trigController());
                buf.writeByte(m.trigLocation());
                buf.writeInt(m.trigSequence());
                buf.writeLong(m.desc());
                buf.writeInt(m.chainCount());
            }
            case DuelMessage.Chained m -> buf.writeByte(m.chainIndex());
            case DuelMessage.ChainSolving m -> buf.writeByte(m.chainIndex());
            case DuelMessage.ChainSolved m -> buf.writeByte(m.chainIndex());
            case DuelMessage.ChainEnd ignored -> {}
            case DuelMessage.ChainNegated m -> buf.writeByte(m.chainIndex());
            case DuelMessage.ChainDisabled m -> buf.writeByte(m.chainIndex());

            // LP
            case DuelMessage.Damage m -> { buf.writeByte(m.player()); buf.writeInt(m.amount()); }
            case DuelMessage.Recover m -> { buf.writeByte(m.player()); buf.writeInt(m.amount()); }
            case DuelMessage.LpUpdate m -> { buf.writeByte(m.player()); buf.writeInt(m.lp()); }
            case DuelMessage.PayLpCost m -> { buf.writeByte(m.player()); buf.writeInt(m.amount()); }

            // Battle
            case DuelMessage.Attack m -> { writeLocInfo(buf, m.attacker()); writeLocInfo(buf, m.target()); }
            case DuelMessage.Battle m -> {
                writeLocInfo(buf, m.attacker());
                buf.writeInt(m.atkAtk()); buf.writeInt(m.atkDef()); buf.writeByte(m.atkDamage());
                writeLocInfo(buf, m.defender());
                buf.writeInt(m.defAtk()); buf.writeInt(m.defDef()); buf.writeByte(m.defDamage());
            }
            case DuelMessage.AttackDisabled ignored -> {}
            case DuelMessage.DamageStepStart ignored -> {}
            case DuelMessage.DamageStepEnd ignored -> {}

            // Deck/Hand
            case DuelMessage.ShuffleDeck m -> buf.writeByte(m.player());
            case DuelMessage.ShuffleHand m -> { buf.writeByte(m.player()); writeIntList(buf, m.codes()); }
            case DuelMessage.ShuffleExtra m -> buf.writeByte(m.player());
            case DuelMessage.ConfirmDeckTop m -> { buf.writeByte(m.player()); writeConfirmCardList(buf, m.cards()); }
            case DuelMessage.ConfirmCards m -> { buf.writeByte(m.player()); writeConfirmCardList(buf, m.cards()); }

            // UI/Info
            case DuelMessage.Hint m -> { buf.writeByte(m.hintType()); buf.writeByte(m.player()); buf.writeLong(m.data()); }
            case DuelMessage.CardHint m -> {
                writeLocInfo(buf, m.location());
                buf.writeByte(m.chintType()); buf.writeLong(m.value());
            }
            case DuelMessage.FieldDisabled m -> buf.writeInt(m.field());
            case DuelMessage.BecomeTarget m -> writeLocInfoList(buf, m.targets());

            // Selection prompts
            case DuelMessage.SelectIdleCmd m -> {
                buf.writeByte(m.player());
                writeIdleCmdCardList(buf, m.summonable());
                writeIdleCmdCardList(buf, m.specialSummonable());
                writeReposCardList(buf, m.repositionable());
                writeIdleCmdCardList(buf, m.settableMonsters());
                writeIdleCmdCardList(buf, m.settableSpells());
                writeActivatableList(buf, m.activatable());
                buf.writeBoolean(m.canBattle());
                buf.writeBoolean(m.canEnd());
                buf.writeBoolean(m.canShuffle());
            }
            case DuelMessage.SelectBattleCmd m -> {
                buf.writeByte(m.player());
                writeActivatableList(buf, m.activatable());
                writeAttackCardList(buf, m.attackable());
                buf.writeBoolean(m.canMain2());
                buf.writeBoolean(m.canEnd());
            }
            case DuelMessage.SelectCard m -> {
                buf.writeByte(m.player());
                buf.writeBoolean(m.cancelable());
                buf.writeInt(m.min()); buf.writeInt(m.max());
                writeCardInfoList(buf, m.cards());
            }
            case DuelMessage.SelectChain m -> {
                buf.writeByte(m.player());
                buf.writeByte(m.speCount());
                buf.writeBoolean(m.forced());
                buf.writeInt(m.hint0());
                buf.writeInt(m.hint1());
                writeActivatableList(buf, m.chains());
            }
            case DuelMessage.SelectEffectYn m -> {
                buf.writeByte(m.player()); buf.writeInt(m.code());
                writeLocInfo(buf, m.location()); buf.writeLong(m.desc());
            }
            case DuelMessage.SelectYesNo m -> { buf.writeByte(m.player()); buf.writeLong(m.desc()); }
            case DuelMessage.SelectOption m -> {
                buf.writeByte(m.player());
                writeLongList(buf, m.options());
            }
            case DuelMessage.SelectPlace m -> { buf.writeByte(m.player()); buf.writeByte(m.count()); buf.writeInt(m.field()); }
            case DuelMessage.SelectPosition m -> { buf.writeByte(m.player()); buf.writeInt(m.code()); buf.writeByte(m.positions()); }
            case DuelMessage.SelectTribute m -> {
                buf.writeByte(m.player());
                buf.writeBoolean(m.cancelable());
                buf.writeInt(m.min()); buf.writeInt(m.max());
                writeTributeCardList(buf, m.cards());
            }
            case DuelMessage.SelectCounter m -> {
                buf.writeByte(m.player());
                buf.writeShort(m.counterType());
                buf.writeShort(m.count());
                writeCounterCardList(buf, m.cards());
            }
            case DuelMessage.SelectSum m -> {
                buf.writeByte(m.player());
                buf.writeBoolean(m.selectMode());
                buf.writeInt(m.targetSum());
                buf.writeInt(m.min()); buf.writeInt(m.max());
                writeSumCardList(buf, m.mustSelect());
                writeSumCardList(buf, m.selectable());
            }
            case DuelMessage.SelectUnselectCard m -> {
                buf.writeByte(m.player());
                buf.writeBoolean(m.finishable());
                buf.writeBoolean(m.cancelable());
                buf.writeInt(m.min()); buf.writeInt(m.max());
                writeCardInfoList(buf, m.selectableCards());
                writeCardInfoList(buf, m.unselectableCards());
            }
            case DuelMessage.SortCard m -> { buf.writeByte(m.player()); writeSortableCardList(buf, m.cards()); }
            case DuelMessage.SortChain m -> { buf.writeByte(m.player()); writeSortableCardList(buf, m.cards()); }
            case DuelMessage.AnnounceRace m -> { buf.writeByte(m.player()); buf.writeByte(m.count()); buf.writeLong(m.available()); }
            case DuelMessage.AnnounceAttrib m -> { buf.writeByte(m.player()); buf.writeByte(m.count()); buf.writeInt(m.available()); }
            case DuelMessage.AnnounceNumber m -> { buf.writeByte(m.player()); writeLongList(buf, m.options()); }
            case DuelMessage.AnnounceCard m -> { buf.writeByte(m.player()); writeByteArray(buf, m.rawBody()); }
            case DuelMessage.RockPaperScissors m -> buf.writeByte(m.player());
            case DuelMessage.HandResult m -> { buf.writeByte(m.hand0()); buf.writeByte(m.hand1()); }

            // Misc
            case DuelMessage.Equip m -> { writeLocInfo(buf, m.card()); writeLocInfo(buf, m.target()); }
            case DuelMessage.Unequip m -> writeLocInfo(buf, m.card());
            case DuelMessage.CardTarget m -> { writeLocInfo(buf, m.card()); writeLocInfo(buf, m.target()); }
            case DuelMessage.CancelTarget m -> { writeLocInfo(buf, m.card()); writeLocInfo(buf, m.target()); }
            case DuelMessage.AddCounter m -> { buf.writeShort(m.counterType()); buf.writeByte(m.controller()); buf.writeByte(m.location()); buf.writeByte(m.sequence()); buf.writeShort(m.count()); }
            case DuelMessage.RemoveCounter m -> { buf.writeShort(m.counterType()); buf.writeByte(m.controller()); buf.writeByte(m.location()); buf.writeByte(m.sequence()); buf.writeShort(m.count()); }
            case DuelMessage.TossCoin m -> { buf.writeByte(m.player()); writeSmallIntList(buf, m.results()); }
            case DuelMessage.TossDice m -> { buf.writeByte(m.player()); writeSmallIntList(buf, m.results()); }

            // Raw is handled before the switch, this should never be reached
            case DuelMessage.Raw m -> throw new IllegalStateException("Raw should be handled before switch");
        }
    }

    // ---- Decode ----

    public static DuelMessage decode(FriendlyByteBuf buf) {
        boolean isRaw = buf.readBoolean();
        if (isRaw) {
            int rawType = buf.readUnsignedByte();
            return new DuelMessage.Raw(rawType, readByteArray(buf));
        }

        int type = buf.readUnsignedByte();

        return switch (type) {
            // Lifecycle
            case MSG_RETRY -> new DuelMessage.Retry();
            case MSG_START -> new DuelMessage.Start(buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort());
            case MSG_WIN -> new DuelMessage.Win(buf.readByte(), buf.readByte());
            case MSG_UPDATE_DATA -> new DuelMessage.UpdateData(buf.readByte(), buf.readByte(), readQueriedCardList(buf));
            case MSG_UPDATE_CARD -> new DuelMessage.UpdateCard(buf.readByte(), buf.readByte(), buf.readInt(), readQueriedCard(buf));
            case MSG_NEW_TURN -> new DuelMessage.NewTurn(buf.readByte());
            case MSG_NEW_PHASE -> new DuelMessage.NewPhase(buf.readUnsignedShort());

            // Card movement
            case MSG_DRAW -> new DuelMessage.Draw(buf.readByte(), readIntList(buf));
            case MSG_MOVE -> new DuelMessage.Move(buf.readInt(), readLocInfo(buf), readLocInfo(buf), buf.readInt());
            case MSG_POS_CHANGE -> new DuelMessage.PosChange(buf.readInt(),
                    buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
            case MSG_SET -> new DuelMessage.Set(buf.readInt(), readLocInfo(buf));
            case MSG_SWAP -> new DuelMessage.Swap(buf.readInt(), readLocInfo(buf), buf.readInt(), readLocInfo(buf));

            // Summons
            case MSG_SUMMONING -> new DuelMessage.Summoning(buf.readInt(), readLocInfo(buf));
            case MSG_SUMMONED -> new DuelMessage.Summoned();
            case MSG_SPSUMMONING -> new DuelMessage.SpSummoning(buf.readInt(), readLocInfo(buf));
            case MSG_SPSUMMONED -> new DuelMessage.SpSummoned();
            case MSG_FLIPSUMMONING -> new DuelMessage.FlipSummoning(buf.readInt(), readLocInfo(buf));
            case MSG_FLIPSUMMONED -> new DuelMessage.FlipSummoned();

            // Chain
            case MSG_CHAINING -> new DuelMessage.Chaining(buf.readInt(), readLocInfo(buf),
                    buf.readByte(), buf.readByte(), buf.readInt(), buf.readLong(), buf.readInt());
            case MSG_CHAINED -> new DuelMessage.Chained(buf.readByte());
            case MSG_CHAIN_SOLVING -> new DuelMessage.ChainSolving(buf.readByte());
            case MSG_CHAIN_SOLVED -> new DuelMessage.ChainSolved(buf.readByte());
            case MSG_CHAIN_END -> new DuelMessage.ChainEnd();
            case MSG_CHAIN_NEGATED -> new DuelMessage.ChainNegated(buf.readByte());
            case MSG_CHAIN_DISABLED -> new DuelMessage.ChainDisabled(buf.readByte());

            // LP
            case MSG_DAMAGE -> new DuelMessage.Damage(buf.readByte(), buf.readInt());
            case MSG_RECOVER -> new DuelMessage.Recover(buf.readByte(), buf.readInt());
            case MSG_LPUPDATE -> new DuelMessage.LpUpdate(buf.readByte(), buf.readInt());
            case MSG_PAY_LPCOST -> new DuelMessage.PayLpCost(buf.readByte(), buf.readInt());

            // Battle
            case MSG_ATTACK -> new DuelMessage.Attack(readLocInfo(buf), readLocInfo(buf));
            case MSG_BATTLE -> new DuelMessage.Battle(readLocInfo(buf), buf.readInt(), buf.readInt(), buf.readByte(),
                    readLocInfo(buf), buf.readInt(), buf.readInt(), buf.readByte());
            case MSG_ATTACK_DISABLED -> new DuelMessage.AttackDisabled();
            case MSG_DAMAGE_STEP_START -> new DuelMessage.DamageStepStart();
            case MSG_DAMAGE_STEP_END -> new DuelMessage.DamageStepEnd();

            // Deck/Hand
            case MSG_SHUFFLE_DECK -> new DuelMessage.ShuffleDeck(buf.readByte());
            case MSG_SHUFFLE_HAND -> new DuelMessage.ShuffleHand(buf.readByte(), readIntList(buf));
            case MSG_SHUFFLE_EXTRA -> new DuelMessage.ShuffleExtra(buf.readByte());
            case MSG_CONFIRM_DECKTOP -> new DuelMessage.ConfirmDeckTop(buf.readByte(), readConfirmCardList(buf));
            case MSG_CONFIRM_CARDS -> new DuelMessage.ConfirmCards(buf.readByte(), readConfirmCardList(buf));

            // UI/Info
            case MSG_HINT -> new DuelMessage.Hint(buf.readByte(), buf.readByte(), buf.readLong());
            case MSG_CARD_HINT -> new DuelMessage.CardHint(readLocInfo(buf), buf.readByte(), buf.readLong());
            case MSG_FIELD_DISABLED -> new DuelMessage.FieldDisabled(buf.readInt());
            case MSG_BECOME_TARGET -> new DuelMessage.BecomeTarget(readLocInfoList(buf));

            // Selection prompts
            case MSG_SELECT_IDLECMD -> new DuelMessage.SelectIdleCmd(buf.readByte(),
                    readIdleCmdCardList(buf), readIdleCmdCardList(buf), readReposCardList(buf),
                    readIdleCmdCardList(buf), readIdleCmdCardList(buf), readActivatableList(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
            case MSG_SELECT_BATTLECMD -> new DuelMessage.SelectBattleCmd(buf.readByte(),
                    readActivatableList(buf), readAttackCardList(buf),
                    buf.readBoolean(), buf.readBoolean());
            case MSG_SELECT_CARD -> new DuelMessage.SelectCard(buf.readByte(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), readCardInfoList(buf));
            case MSG_SELECT_CHAIN -> new DuelMessage.SelectChain(buf.readByte(), buf.readByte(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), readActivatableList(buf));
            case MSG_SELECT_EFFECTYN -> new DuelMessage.SelectEffectYn(buf.readByte(), buf.readInt(),
                    readLocInfo(buf), buf.readLong());
            case MSG_SELECT_YESNO -> new DuelMessage.SelectYesNo(buf.readByte(), buf.readLong());
            case MSG_SELECT_OPTION -> new DuelMessage.SelectOption(buf.readByte(), readLongList(buf));
            case MSG_SELECT_PLACE -> new DuelMessage.SelectPlace(buf.readByte(), buf.readByte(), buf.readInt());
            case MSG_SELECT_POSITION -> new DuelMessage.SelectPosition(buf.readByte(), buf.readInt(), buf.readByte());
            case MSG_SELECT_TRIBUTE -> new DuelMessage.SelectTribute(buf.readByte(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), readTributeCardList(buf));
            case MSG_SELECT_COUNTER -> new DuelMessage.SelectCounter(buf.readByte(), buf.readUnsignedShort(),
                    buf.readUnsignedShort(), readCounterCardList(buf));
            case MSG_SELECT_SUM -> new DuelMessage.SelectSum(buf.readByte(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    readSumCardList(buf), readSumCardList(buf));
            case MSG_SELECT_UNSELECT_CARD -> new DuelMessage.SelectUnselectCard(buf.readByte(),
                    buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(),
                    readCardInfoList(buf), readCardInfoList(buf));
            case MSG_SORT_CARD -> new DuelMessage.SortCard(buf.readByte(), readSortableCardList(buf));
            case MSG_SORT_CHAIN -> new DuelMessage.SortChain(buf.readByte(), readSortableCardList(buf));
            case MSG_ANNOUNCE_RACE -> new DuelMessage.AnnounceRace(buf.readByte(), buf.readByte(), buf.readLong());
            case MSG_ANNOUNCE_ATTRIB -> new DuelMessage.AnnounceAttrib(buf.readByte(), buf.readByte(), buf.readInt());
            case MSG_ANNOUNCE_NUMBER -> new DuelMessage.AnnounceNumber(buf.readByte(), readLongList(buf));
            case MSG_ANNOUNCE_CARD -> new DuelMessage.AnnounceCard(buf.readByte(), readByteArray(buf));
            case MSG_ROCK_PAPER_SCISSORS -> new DuelMessage.RockPaperScissors(buf.readByte());
            case MSG_HAND_RES -> new DuelMessage.HandResult(buf.readByte(), buf.readByte());

            // Misc
            case MSG_EQUIP -> new DuelMessage.Equip(readLocInfo(buf), readLocInfo(buf));
            case MSG_UNEQUIP -> new DuelMessage.Unequip(readLocInfo(buf));
            case MSG_CARD_TARGET -> new DuelMessage.CardTarget(readLocInfo(buf), readLocInfo(buf));
            case MSG_CANCEL_TARGET -> new DuelMessage.CancelTarget(readLocInfo(buf), readLocInfo(buf));
            case MSG_ADD_COUNTER -> new DuelMessage.AddCounter(buf.readUnsignedShort(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedShort());
            case MSG_REMOVE_COUNTER -> new DuelMessage.RemoveCounter(buf.readUnsignedShort(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedShort());
            case MSG_TOSS_COIN -> new DuelMessage.TossCoin(buf.readByte(), readSmallIntList(buf));
            case MSG_TOSS_DICE -> new DuelMessage.TossDice(buf.readByte(), readSmallIntList(buf));

            // Raw fallback
            default -> new DuelMessage.Raw(type, readByteArray(buf));
        };
    }

    // ---- Helpers ----

    private static void writeLocInfo(FriendlyByteBuf buf, LocInfo loc) {
        buf.writeByte(loc.controller());
        buf.writeByte(loc.location());
        buf.writeInt(loc.sequence());
        buf.writeInt(loc.position());
    }

    private static LocInfo readLocInfo(FriendlyByteBuf buf) {
        return new LocInfo(buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt());
    }

    private static void writeCardInfo(FriendlyByteBuf buf, DuelMessage.CardInfo ci) {
        buf.writeInt(ci.code());
        buf.writeByte(ci.controller());
        buf.writeByte(ci.location());
        buf.writeInt(ci.sequence());
        buf.writeInt(ci.position());
    }

    private static DuelMessage.CardInfo readCardInfo(FriendlyByteBuf buf) {
        return new DuelMessage.CardInfo(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readInt());
    }

    private static void writeByteArray(FriendlyByteBuf buf, byte[] data) {
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static byte[] readByteArray(FriendlyByteBuf buf) {
        int len = buf.readInt();
        byte[] data = new byte[len];
        buf.readBytes(data);
        return data;
    }

    private static void writeIntList(FriendlyByteBuf buf, List<Integer> list) {
        buf.writeInt(list.size());
        for (int v : list) buf.writeInt(v);
    }

    private static List<Integer> readIntList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readInt());
        return list;
    }

    private static void writeLongList(FriendlyByteBuf buf, List<Long> list) {
        buf.writeInt(list.size());
        for (long v : list) buf.writeLong(v);
    }

    private static List<Long> readLongList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<Long> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readLong());
        return list;
    }

    /** For small int lists where values fit in a byte (coin/dice results). */
    private static void writeSmallIntList(FriendlyByteBuf buf, List<Integer> list) {
        buf.writeByte(list.size());
        for (int v : list) buf.writeByte(v);
    }

    private static List<Integer> readSmallIntList(FriendlyByteBuf buf) {
        int count = buf.readUnsignedByte();
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add((int) buf.readByte());
        return list;
    }

    private static void writeCardInfoList(FriendlyByteBuf buf, List<DuelMessage.CardInfo> list) {
        buf.writeInt(list.size());
        for (var ci : list) writeCardInfo(buf, ci);
    }

    private static List<DuelMessage.CardInfo> readCardInfoList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.CardInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(readCardInfo(buf));
        return list;
    }

    private static void writeConfirmCard(FriendlyByteBuf buf, DuelMessage.ConfirmCard card) {
        buf.writeInt(card.code());
        buf.writeByte(card.controller());
        buf.writeByte(card.location());
        buf.writeInt(card.sequence());
    }

    private static DuelMessage.ConfirmCard readConfirmCard(FriendlyByteBuf buf) {
        return new DuelMessage.ConfirmCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt());
    }

    private static void writeConfirmCardList(FriendlyByteBuf buf, List<DuelMessage.ConfirmCard> list) {
        buf.writeInt(list.size());
        for (var c : list) writeConfirmCard(buf, c);
    }

    private static List<DuelMessage.ConfirmCard> readConfirmCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        var list = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) list.add(readConfirmCard(buf));
        return list;
    }

    private static void writeLocInfoList(FriendlyByteBuf buf, List<LocInfo> list) {
        buf.writeInt(list.size());
        for (var loc : list) writeLocInfo(buf, loc);
    }

    private static List<LocInfo> readLocInfoList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<LocInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(readLocInfo(buf));
        return list;
    }

    // IdleCmdCard: code(4) + con(1) + loc(1) + seq(4) = 10 bytes
    private static void writeIdleCmdCardList(FriendlyByteBuf buf, List<DuelMessage.IdleCmdCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeByte(c.location()); buf.writeInt(c.sequence()); }
    }

    private static List<DuelMessage.IdleCmdCard> readIdleCmdCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.IdleCmdCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.IdleCmdCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt()));
        return list;
    }

    // ReposCard: code(4) + con(1) + loc(1) + seq(1) = 7 bytes
    private static void writeReposCardList(FriendlyByteBuf buf, List<DuelMessage.ReposCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeByte(c.location()); buf.writeByte(c.sequence()); }
    }

    private static List<DuelMessage.ReposCard> readReposCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.ReposCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.ReposCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readByte()));
        return list;
    }

    // AttackCard: code(4) + con(1) + loc(1) + seq(1) + diratt(1) = 8 bytes
    private static void writeAttackCardList(FriendlyByteBuf buf, List<DuelMessage.AttackCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeByte(c.location()); buf.writeByte(c.sequence()); buf.writeByte(c.directAttack()); }
    }

    private static List<DuelMessage.AttackCard> readAttackCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.AttackCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.AttackCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte()));
        return list;
    }

    // ActivatableCard: code(4) + con(1) + loc(1) + seq(4) + desc(8) + flag(1) = 19 bytes
    private static void writeActivatableList(FriendlyByteBuf buf, List<DuelMessage.ActivatableCard> list) {
        buf.writeInt(list.size());
        for (var ac : list) { buf.writeInt(ac.code()); buf.writeByte(ac.controller()); buf.writeByte(ac.location()); buf.writeInt(ac.sequence()); buf.writeLong(ac.desc()); buf.writeByte(ac.flag()); }
    }

    private static List<DuelMessage.ActivatableCard> readActivatableList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.ActivatableCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.ActivatableCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readLong(), buf.readByte()));
        return list;
    }

    // TributeCard: code(4) + con(1) + loc(1) + seq(4) + tributeCount(1) = 11 bytes
    private static void writeTributeCardList(FriendlyByteBuf buf, List<DuelMessage.TributeCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeByte(c.location()); buf.writeInt(c.sequence()); buf.writeByte(c.tributeCount()); }
    }

    private static List<DuelMessage.TributeCard> readTributeCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.TributeCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.TributeCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt(), buf.readByte()));
        return list;
    }

    // CounterCard: code(4) + con(1) + loc(1) + seq(1) + counterCount(2) = 9 bytes
    private static void writeCounterCardList(FriendlyByteBuf buf, List<DuelMessage.CounterCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeByte(c.location()); buf.writeByte(c.sequence()); buf.writeShort(c.counterCount()); }
    }

    private static List<DuelMessage.CounterCard> readCounterCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.CounterCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.CounterCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readUnsignedShort()));
        return list;
    }

    // SumCard: code(4) + con(1) + loc(1) + seq(4) + opParam(8) = 18 bytes
    private static void writeSumCard(FriendlyByteBuf buf, DuelMessage.SumCard card) {
        buf.writeInt(card.code());
        buf.writeByte(card.controller());
        buf.writeByte(card.location());
        buf.writeInt(card.sequence());
        buf.writeLong(card.opParam());
    }

    private static DuelMessage.SumCard readSumCard(FriendlyByteBuf buf) {
        return new DuelMessage.SumCard(buf.readInt(), buf.readByte(), buf.readByte(),
                buf.readInt(), buf.readLong());
    }

    private static void writeSumCardList(FriendlyByteBuf buf, List<DuelMessage.SumCard> list) {
        buf.writeInt(list.size());
        for (var c : list) writeSumCard(buf, c);
    }

    private static List<DuelMessage.SumCard> readSumCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        var list = new ArrayList<DuelMessage.SumCard>(count);
        for (int i = 0; i < count; i++) list.add(readSumCard(buf));
        return list;
    }

    // SortableCard: code(4) + con(1) + loc(4) + seq(4) = 13 bytes
    private static void writeSortableCardList(FriendlyByteBuf buf, List<DuelMessage.SortableCard> list) {
        buf.writeInt(list.size());
        for (var c : list) { buf.writeInt(c.code()); buf.writeByte(c.controller()); buf.writeInt(c.location()); buf.writeInt(c.sequence()); }
    }

    private static List<DuelMessage.SortableCard> readSortableCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DuelMessage.SortableCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(new DuelMessage.SortableCard(buf.readInt(), buf.readByte(), buf.readInt(), buf.readInt()));
        return list;
    }

    // QueriedCard serialization
    private static void writeQueriedCard(FriendlyByteBuf buf, QueriedCard c) {
        buf.writeInt(c.flags);
        buf.writeInt(c.code);
        buf.writeInt(c.position);
        buf.writeInt(c.alias);
        buf.writeInt(c.type);
        buf.writeInt(c.level);
        buf.writeInt(c.rank);
        buf.writeInt(c.attribute);
        buf.writeLong(c.race);
        buf.writeInt(c.attack);
        buf.writeInt(c.defense);
        buf.writeInt(c.baseAttack);
        buf.writeInt(c.baseDefense);
        buf.writeInt(c.reason);
        buf.writeInt(c.owner);
        buf.writeInt(c.status);
        buf.writeBoolean(c.isPublic);
        buf.writeBoolean(c.isHidden);
        buf.writeInt(c.lscale);
        buf.writeInt(c.rscale);
        buf.writeInt(c.linkRating);
        buf.writeInt(c.linkMarker);
        buf.writeInt(c.cover);
        writeIntList(buf, c.overlayCards);
        writeIntList(buf, c.counters);
        boolean hasReasonCard = c.reasonCard != null;
        buf.writeBoolean(hasReasonCard);
        if (hasReasonCard) writeLocInfo(buf, c.reasonCard);
        boolean hasEquipCard = c.equipCard != null;
        buf.writeBoolean(hasEquipCard);
        if (hasEquipCard) writeLocInfo(buf, c.equipCard);
        writeLocInfoList(buf, c.targetCards);
    }

    private static QueriedCard readQueriedCard(FriendlyByteBuf buf) {
        QueriedCard c = new QueriedCard();
        c.flags = buf.readInt();
        c.code = buf.readInt();
        c.position = buf.readInt();
        c.alias = buf.readInt();
        c.type = buf.readInt();
        c.level = buf.readInt();
        c.rank = buf.readInt();
        c.attribute = buf.readInt();
        c.race = buf.readLong();
        c.attack = buf.readInt();
        c.defense = buf.readInt();
        c.baseAttack = buf.readInt();
        c.baseDefense = buf.readInt();
        c.reason = buf.readInt();
        c.owner = buf.readInt();
        c.status = buf.readInt();
        c.isPublic = buf.readBoolean();
        c.isHidden = buf.readBoolean();
        c.lscale = buf.readInt();
        c.rscale = buf.readInt();
        c.linkRating = buf.readInt();
        c.linkMarker = buf.readInt();
        c.cover = buf.readInt();
        c.overlayCards = readIntList(buf);
        c.counters = readIntList(buf);
        if (buf.readBoolean()) c.reasonCard = readLocInfo(buf);
        if (buf.readBoolean()) c.equipCard = readLocInfo(buf);
        c.targetCards = readLocInfoList(buf);
        return c;
    }

    private static void writeQueriedCardList(FriendlyByteBuf buf, List<QueriedCard> list) {
        buf.writeInt(list.size());
        for (var c : list) {
            buf.writeBoolean(c != null);
            if (c != null) writeQueriedCard(buf, c);
        }
    }

    private static List<QueriedCard> readQueriedCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<QueriedCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readBoolean() ? readQueriedCard(buf) : null);
        }
        return list;
    }
}

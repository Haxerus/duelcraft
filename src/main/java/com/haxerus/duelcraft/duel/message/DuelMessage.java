package com.haxerus.duelcraft.duel.message;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Sealed interface representing a parsed duel message from ygopro-core.
 * Each message type is a record with the relevant fields already extracted.
 * Use {@code instanceof} pattern matching to handle specific types.
 */
public sealed interface DuelMessage {

    /** The MSG_* constant identifying this message type. */
    int type();

    /** Fallback for messages we don't parse yet. Carries the raw body bytes. */
    record Raw(int type, byte[] body) implements DuelMessage {}

    /** Engine rejected the last response — re-prompt the player. */
    record Retry() implements DuelMessage {
        public int type() { return MSG_RETRY; }
    }

    // ---- Lifecycle ----

    record Start(int playerType, int lp0, int lp1,
                 int deckCount0, int extraCount0,
                 int deckCount1, int extraCount1) implements DuelMessage {
        public int type() { return MSG_START; }
    }

    record Win(int winner, int reason) implements DuelMessage {
        public int type() { return MSG_WIN; }
    }

    record UpdateData(int player, int location, List<QueriedCard> cards) implements DuelMessage {
        public int type() { return MSG_UPDATE_DATA; }
    }

    record UpdateCard(int player, int location, int sequence, QueriedCard card) implements DuelMessage {
        public int type() { return MSG_UPDATE_CARD; }
    }

    record NewTurn(int player) implements DuelMessage {
        public int type() { return MSG_NEW_TURN; }
    }

    record NewPhase(int phase) implements DuelMessage {
        public int type() { return MSG_NEW_PHASE; }
    }

    // ---- Card Movement ----

    record Draw(int player, List<Integer> codes) implements DuelMessage {
        public int type() { return MSG_DRAW; }
    }

    record Move(int code, LocInfo from, LocInfo to, int reason) implements DuelMessage {
        public int type() { return MSG_MOVE; }
    }

    record PosChange(int code, int controller, int location, int sequence,
                     int prevPosition, int newPosition) implements DuelMessage {
        public int type() { return MSG_POS_CHANGE; }
    }

    record Set(int code, LocInfo location) implements DuelMessage {
        public int type() { return MSG_SET; }
    }

    record Swap(int code1, LocInfo loc1, int code2, LocInfo loc2) implements DuelMessage {
        public int type() { return MSG_SWAP; }
    }

    // ---- Summons ----

    record Summoning(int code, LocInfo location) implements DuelMessage {
        public int type() { return MSG_SUMMONING; }
    }

    record Summoned() implements DuelMessage {
        public int type() { return MSG_SUMMONED; }
    }

    record SpSummoning(int code, LocInfo location) implements DuelMessage {
        public int type() { return MSG_SPSUMMONING; }
    }

    record SpSummoned() implements DuelMessage {
        public int type() { return MSG_SPSUMMONED; }
    }

    record FlipSummoning(int code, LocInfo location) implements DuelMessage {
        public int type() { return MSG_FLIPSUMMONING; }
    }

    record FlipSummoned() implements DuelMessage {
        public int type() { return MSG_FLIPSUMMONED; }
    }

    // ---- Chain ----

    record Chaining(int code, LocInfo location, int trigController, int trigLocation,
                    int trigSequence, long desc, int chainCount) implements DuelMessage {
        public int type() { return MSG_CHAINING; }
    }

    record Chained(int chainIndex) implements DuelMessage {
        public int type() { return MSG_CHAINED; }
    }

    record ChainSolving(int chainIndex) implements DuelMessage {
        public int type() { return MSG_CHAIN_SOLVING; }
    }

    record ChainSolved(int chainIndex) implements DuelMessage {
        public int type() { return MSG_CHAIN_SOLVED; }
    }

    record ChainEnd() implements DuelMessage {
        public int type() { return MSG_CHAIN_END; }
    }

    record ChainNegated(int chainIndex) implements DuelMessage {
        public int type() { return MSG_CHAIN_NEGATED; }
    }

    record ChainDisabled(int chainIndex) implements DuelMessage {
        public int type() { return MSG_CHAIN_DISABLED; }
    }

    // ---- LP ----

    record Damage(int player, int amount) implements DuelMessage {
        public int type() { return MSG_DAMAGE; }
    }

    record Recover(int player, int amount) implements DuelMessage {
        public int type() { return MSG_RECOVER; }
    }

    record LpUpdate(int player, int lp) implements DuelMessage {
        public int type() { return MSG_LPUPDATE; }
    }

    record PayLpCost(int player, int amount) implements DuelMessage {
        public int type() { return MSG_PAY_LPCOST; }
    }

    // ---- Battle ----

    record Attack(LocInfo attacker, LocInfo target) implements DuelMessage {
        public int type() { return MSG_ATTACK; }
    }

    record Battle(LocInfo attacker, int atkAtk, int atkDef, int atkDamage,
                  LocInfo defender, int defAtk, int defDef, int defDamage) implements DuelMessage {
        public int type() { return MSG_BATTLE; }
    }

    record AttackDisabled() implements DuelMessage {
        public int type() { return MSG_ATTACK_DISABLED; }
    }

    record DamageStepStart() implements DuelMessage {
        public int type() { return MSG_DAMAGE_STEP_START; }
    }

    record DamageStepEnd() implements DuelMessage {
        public int type() { return MSG_DAMAGE_STEP_END; }
    }

    // ---- Deck/Hand ----

    record ShuffleDeck(int player) implements DuelMessage {
        public int type() { return MSG_SHUFFLE_DECK; }
    }

    record ShuffleHand(int player, List<Integer> codes) implements DuelMessage {
        public int type() { return MSG_SHUFFLE_HAND; }
    }

    record ShuffleExtra(int player) implements DuelMessage {
        public int type() { return MSG_SHUFFLE_EXTRA; }
    }

    record ConfirmDeckTop(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_DECKTOP; }
    }

    record ConfirmCards(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_CARDS; }
    }

    // ---- UI / Info ----

    record Hint(int hintType, int player, long data) implements DuelMessage {
        public int type() { return MSG_HINT; }
    }

    record CardHint(LocInfo location, int chintType, long value) implements DuelMessage {
        public int type() { return MSG_CARD_HINT; }
    }

    record FieldDisabled(int field) implements DuelMessage {
        public int type() { return MSG_FIELD_DISABLED; }
    }

    record BecomeTarget(List<LocInfo> targets) implements DuelMessage {
        public int type() { return MSG_BECOME_TARGET; }
    }

    // ---- Selection (prompts that require a player response) ----

    /** Main phase action menu. */
    record SelectIdleCmd(int player,
                         List<IdleCmdCard> summonable,
                         List<IdleCmdCard> specialSummonable,
                         List<ReposCard> repositionable,
                         List<IdleCmdCard> settableMonsters,
                         List<IdleCmdCard> settableSpells,
                         List<ActivatableCard> activatable,
                         boolean canBattle,
                         boolean canEnd,
                         boolean canShuffle) implements DuelMessage {
        public int type() { return MSG_SELECT_IDLECMD; }
    }

    /** Battle phase action menu. */
    record SelectBattleCmd(int player,
                           List<ActivatableCard> activatable,
                           List<AttackCard> attackable,
                           boolean canMain2,
                           boolean canEnd) implements DuelMessage {
        public int type() { return MSG_SELECT_BATTLECMD; }
    }

    record SelectCard(int player, boolean cancelable, int min, int max,
                      List<CardInfo> cards) implements DuelMessage {
        public int type() { return MSG_SELECT_CARD; }
    }

    record SelectChain(int player, int speCount, boolean forced,
                       int hint0, int hint1, List<ActivatableCard> chains) implements DuelMessage {
        public int type() { return MSG_SELECT_CHAIN; }
        public int count() { return chains.size(); }
    }

    record SelectEffectYn(int player, int code, LocInfo location,
                          long desc) implements DuelMessage {
        public int type() { return MSG_SELECT_EFFECTYN; }
    }

    record SelectYesNo(int player, long desc) implements DuelMessage {
        public int type() { return MSG_SELECT_YESNO; }
    }

    record SelectOption(int player, List<Long> options) implements DuelMessage {
        public int type() { return MSG_SELECT_OPTION; }
    }

    record SelectPlace(int player, int count, int field) implements DuelMessage {
        public int type() { return MSG_SELECT_PLACE; }
    }

    record SelectPosition(int player, int code, int positions) implements DuelMessage {
        public int type() { return MSG_SELECT_POSITION; }
    }

    record SelectTribute(int player, boolean cancelable, int min, int max,
                         List<TributeCard> cards) implements DuelMessage {
        public int type() { return MSG_SELECT_TRIBUTE; }
    }

    record SelectCounter(int player, int counterType, int count,
                         List<CounterCard> cards) implements DuelMessage {
        public int type() { return MSG_SELECT_COUNTER; }
    }

    record SelectSum(int player, boolean selectMode, int targetSum,
                     int min, int max, List<SumCard> mustSelect,
                     List<SumCard> selectable) implements DuelMessage {
        public int type() { return MSG_SELECT_SUM; }
    }

    record SelectUnselectCard(int player, boolean finishable, boolean cancelable,
                              int min, int max, List<CardInfo> selectableCards,
                              List<CardInfo> unselectableCards) implements DuelMessage {
        public int type() { return MSG_SELECT_UNSELECT_CARD; }
    }

    record SortCard(int player, List<SortableCard> cards) implements DuelMessage {
        public int type() { return MSG_SORT_CARD; }
    }

    record SortChain(int player, List<SortableCard> cards) implements DuelMessage {
        public int type() { return MSG_SORT_CHAIN; }
    }

    record AnnounceRace(int player, int count, long available) implements DuelMessage {
        public int type() { return MSG_ANNOUNCE_RACE; }
    }

    record AnnounceAttrib(int player, int count, int available) implements DuelMessage {
        public int type() { return MSG_ANNOUNCE_ATTRIB; }
    }

    record AnnounceNumber(int player, List<Long> options) implements DuelMessage {
        public int type() { return MSG_ANNOUNCE_NUMBER; }
    }

    record AnnounceCard(int player, byte[] rawBody) implements DuelMessage {
        public int type() { return MSG_ANNOUNCE_CARD; }
    }

    record RockPaperScissors(int player) implements DuelMessage {
        public int type() { return MSG_ROCK_PAPER_SCISSORS; }
    }

    record HandResult(int hand0, int hand1) implements DuelMessage {
        public int type() { return MSG_HAND_RES; }
    }

    // ---- Misc Action ----

    record Equip(LocInfo card, LocInfo target) implements DuelMessage {
        public int type() { return MSG_EQUIP; }
    }

    record Unequip(LocInfo card) implements DuelMessage {
        public int type() { return MSG_UNEQUIP; }
    }

    record CardTarget(LocInfo card, LocInfo target) implements DuelMessage {
        public int type() { return MSG_CARD_TARGET; }
    }

    record CancelTarget(LocInfo card, LocInfo target) implements DuelMessage {
        public int type() { return MSG_CANCEL_TARGET; }
    }

    record AddCounter(int counterType, int controller, int location,
                      int sequence, int count) implements DuelMessage {
        public int type() { return MSG_ADD_COUNTER; }
    }

    record RemoveCounter(int counterType, int controller, int location,
                         int sequence, int count) implements DuelMessage {
        public int type() { return MSG_REMOVE_COUNTER; }
    }

    record TossCoin(int player, List<Integer> results) implements DuelMessage {
        public int type() { return MSG_TOSS_COIN; }
    }

    record TossDice(int player, List<Integer> results) implements DuelMessage {
        public int type() { return MSG_TOSS_DICE; }
    }

    // ---- Shared sub-records for cards within selection messages ----

    record ConfirmCard(int code, int controller, int location, int sequence) {
        public static ConfirmCard read(BufferReader reader) {
            return new ConfirmCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32()
            );
        }
    }

    record CardInfo(int code, int controller, int location, int sequence, int position) {
        public static CardInfo read(BufferReader reader) {
            return new CardInfo(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32(),
                reader.readInt32()
            );
        }
    }

    record SumCard(int code, int controller, int location, int sequence, long opParam) {
        public static SumCard read(BufferReader reader) {
            return new SumCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32(),
                reader.readInt64()
            );
        }
        public int value1() { return (int)(opParam & 0xFFFF); }
        public int value2() { return (int)((opParam >> 16) & 0xFFFF); }
    }

    /** Card entry in tribute selection: code + con + loc + seq(uint32) + tributeCount(uint8). */
    record TributeCard(int code, int controller, int location, int sequence, int tributeCount) {
        public static TributeCard read(BufferReader reader) {
            return new TributeCard(
                reader.readInt32(), reader.readUint8(), reader.readUint8(),
                reader.readInt32(), reader.readUint8());
        }
    }

    /** Card entry in counter selection: code + con + loc + seq(uint8) + counterCount(uint16). */
    record CounterCard(int code, int controller, int location, int sequence, int counterCount) {
        public static CounterCard read(BufferReader reader) {
            return new CounterCard(
                reader.readInt32(), reader.readUint8(), reader.readUint8(),
                reader.readUint8(), reader.readUint16());
        }
    }

    /** Card entry in sort messages: code + con + loc(uint32) + seq(uint32). */
    record SortableCard(int code, int controller, int location, int sequence) {
        public static SortableCard read(BufferReader reader) {
            return new SortableCard(
                reader.readInt32(), reader.readUint8(),
                reader.readInt32(), reader.readInt32());
        }
    }

    /** Card entry in idle cmd summon/set lists: code + con + loc + seq(uint32). No position. */
    record IdleCmdCard(int code, int controller, int location, int sequence) {
        public static IdleCmdCard read(BufferReader reader) {
            return new IdleCmdCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32()
            );
        }
    }

    /** Card entry in idle cmd reposition list: code + con + loc + seq(uint8). */
    record ReposCard(int code, int controller, int location, int sequence) {
        public static ReposCard read(BufferReader reader) {
            return new ReposCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readUint8()
            );
        }
    }

    /** Card entry in battle cmd attack list: code + con + loc + seq(uint8) + diratt(uint8). */
    record AttackCard(int code, int controller, int location, int sequence, int directAttack) {
        public static AttackCard read(BufferReader reader) {
            return new AttackCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readUint8()
            );
        }
    }

    /** A card that can be activated: code + con + loc + seq(uint32) + desc(uint64) + flag(uint8). */
    record ActivatableCard(int code, int controller, int location, int sequence, long desc, int flag) {
        public static ActivatableCard read(BufferReader reader) {
            return new ActivatableCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32(),
                reader.readInt64(),
                reader.readUint8()
            );
        }
    }
}

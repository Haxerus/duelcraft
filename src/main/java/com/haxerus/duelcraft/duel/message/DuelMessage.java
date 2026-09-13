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

    /**
     * The other duellist is being prompted. Host-synthesised, never parsed from the engine
     * ({@code generic_duel.cpp:1326-1343}).
     */
    record Waiting() implements DuelMessage {
        public int type() { return MSG_WAITING; }
    }

    // ---- Lifecycle ----

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

    record Draw(int player, List<DrawnCard> cards) implements DuelMessage {
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

    record Battle(LocInfo attacker, int atkAtk, int atkDef, int atkDestroyed,
                  LocInfo defender, int defAtk, int defDef, int defDestroyed) implements DuelMessage {
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

    /**
     * A player's graveyard and deck traded places ({@code field.cpp:1048}). Bit {@code i} of
     * {@code extraMask} (byte {@code i/8}, bit {@code i%8}) flags the {@code i}-th card of the new
     * deck as an extra-deck monster, which goes to the extra deck face-down instead.
     * {@code extraCount} is the extra deck's size before those cards were inserted; edopro
     * discards it and so do we.
     */
    record SwapGraveDeck(int player, int extraCount, byte[] extraMask) implements DuelMessage {
        public int type() { return MSG_SWAP_GRAVE_DECK; }
    }

    /**
     * Face-down cards of one location were shuffled among their zones ({@code libduel.cpp:1404},
     * {@code operations.cpp:2958}). {@code from} names each card's old zone; {@code follow} names
     * the new zone, but only for cards carrying XYZ materials — the rest are zeroed {@link LocInfo}s.
     */
    record ShuffleSetCard(int location, List<LocInfo> from, List<LocInfo> follow) implements DuelMessage {
        public int type() { return MSG_SHUFFLE_SET_CARD; }
    }

    /** Cards deleted from the duel outright ({@code libduel.cpp:536}); batched at 255 per message. */
    record RemoveCards(List<LocInfo> cards) implements DuelMessage {
        public int type() { return MSG_REMOVE_CARDS; }
    }

    /** Same body as {@link ConfirmDeckTop}, over the extra deck ({@code libduel.cpp:854}). */
    record ConfirmExtraTop(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_EXTRATOP; }
    }

    record ConfirmCards(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_CARDS; }
    }

    /** Both decks turned over or back ({@code processor.cpp:4926}); one global state, not per player. */
    record ReverseDeck() implements DuelMessage {
        public int type() { return MSG_REVERSE_DECK; }
    }

    /** The card {@code offsetFromTop} down from the top of a deck is now known to both players. */
    record DeckTop(int player, int offsetFromTop, int code, int position) implements DuelMessage {
        public int type() { return MSG_DECK_TOP; }
    }

    /** Informational: "the engine just selected these cards" — no action required. */
    record CardSelected(List<LocInfo> cards) implements DuelMessage {
        public int type() { return MSG_CARD_SELECTED; }
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

    /** The cards a random pick landed on ({@code libgroup.cpp:326}). */
    record RandomSelected(int player, List<LocInfo> cards) implements DuelMessage {
        public int type() { return MSG_RANDOM_SELECTED; }
    }

    /** A card whose effect missed its timing ({@code processor.cpp:4374}); its controller only. */
    record MissedEffect(LocInfo location, int code) implements DuelMessage {
        public int type() { return MSG_MISSED_EFFECT; }
    }

    /**
     * A refcounted hint on a player rather than a card ({@code field.cpp:1332-1402}):
     * {@code hintType} is {@code PHINT_DESC_ADD} or {@code PHINT_DESC_REMOVE}.
     */
    record PlayerHint(int player, int hintType, long desc) implements DuelMessage {
        public int type() { return MSG_PLAYER_HINT; }
    }

    /** The card that ended a match outright ({@code operations.cpp:609}); match play only. */
    record MatchKill(int code) implements DuelMessage {
        public int type() { return MSG_MATCH_KILL; }
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

    /** Same wire shape as SelectPlace, but the chosen zones become unusable rather than hosting a placement. */
    record SelectDisfield(int player, int count, int field) implements DuelMessage {
        public int type() { return MSG_SELECT_DISFIELD; }
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

    record AnnounceCard(int player, List<Long> opcodes) implements DuelMessage {
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

    /** Drawn card: code + position(uint32). The position is the only signal that a draw is public. */
    record DrawnCard(int code, int position) {
        public static DrawnCard read(BufferReader reader) {
            return new DrawnCard(reader.readInt32(), reader.readInt32());
        }
    }

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

    /** Card entry in sum selection: code + con + loc + seq(uint32) + position(uint32) + sumParam(uint32). */
    record SumCard(int code, int controller, int location, int sequence, int position, int sumParam) {
        public static SumCard read(BufferReader reader) {
            return new SumCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32(),
                reader.readInt32(),
                reader.readInt32()
            );
        }
        public int value1() { return sumParam & 0xFFFF; }
        public int value2() { return (sumParam >>> 16) & 0xFFFF; }
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

    /**
     * A card that can be activated: code + con + loc + seq(uint32) + desc(uint64) + flag(uint8).
     * {@code position} only reaches the record from {@code MSG_SELECT_CHAIN}, whose entries carry a
     * full {@code loc_info}; the idle and battle command lists write no position and leave it 0.
     */
    record ActivatableCard(int code, int controller, int location, int sequence, int position,
                           long desc, int flag) {
        public static ActivatableCard read(BufferReader reader) {
            return new ActivatableCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32(),
                0,
                reader.readInt64(),
                reader.readUint8()
            );
        }
    }
}

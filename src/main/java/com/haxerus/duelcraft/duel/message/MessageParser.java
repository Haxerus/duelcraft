package com.haxerus.duelcraft.duel.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

public class MessageParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageParser.class);

    public static List<DuelMessage> parse(byte[] buffer) {
        List<DuelMessage> messages = new ArrayList<>();
        BufferReader reader = new BufferReader(buffer);

        while (reader.remaining() >= 5) { // at least length(4) + type(1)
            int length = reader.readInt32();
            if (length < 1 || reader.remaining() < length) break;

            int messageEnd = reader.position() + length; // expected position after this message
            int type = reader.readUint8();
            int bodyLength = length - 1;

            DuelMessage msg;
            try {
                msg = switch (type) {
                // System
                case MSG_RETRY         -> new DuelMessage.Retry();

                // Lifecycle
                case MSG_START         -> parseStart(reader);
                case MSG_WIN           -> parseWin(reader, bodyLength);
                case MSG_NEW_TURN      -> parseNewTurn(reader);
                case MSG_NEW_PHASE     -> parseNewPhase(reader);

                // Card movement
                case MSG_DRAW          -> parseDraw(reader);
                case MSG_MOVE          -> parseMove(reader);
                case MSG_POS_CHANGE    -> parsePosChange(reader);
                case MSG_SET           -> parseSet(reader);
                case MSG_SWAP          -> parseSwap(reader);

                // Summons
                case MSG_SUMMONING     -> parseSummoning(reader);
                case MSG_SUMMONED      -> new DuelMessage.Summoned();
                case MSG_SPSUMMONING   -> parseSpSummoning(reader);
                case MSG_SPSUMMONED    -> new DuelMessage.SpSummoned();
                case MSG_FLIPSUMMONING -> parseFlipSummoning(reader);
                case MSG_FLIPSUMMONED  -> new DuelMessage.FlipSummoned();

                // Chain
                case MSG_CHAINING      -> parseChaining(reader);
                case MSG_CHAINED       -> new DuelMessage.Chained(reader.readUint8());
                case MSG_CHAIN_SOLVING -> new DuelMessage.ChainSolving(reader.readUint8());
                case MSG_CHAIN_SOLVED  -> new DuelMessage.ChainSolved(reader.readUint8());
                case MSG_CHAIN_END     -> new DuelMessage.ChainEnd();
                case MSG_CHAIN_NEGATED -> new DuelMessage.ChainNegated(reader.readUint8());
                case MSG_CHAIN_DISABLED -> new DuelMessage.ChainDisabled(reader.readUint8());

                // LP
                case MSG_DAMAGE        -> parsePlayerAmount(reader, MSG_DAMAGE);
                case MSG_RECOVER       -> parsePlayerAmount(reader, MSG_RECOVER);
                case MSG_LPUPDATE      -> parsePlayerAmount(reader, MSG_LPUPDATE);
                case MSG_PAY_LPCOST    -> parsePlayerAmount(reader, MSG_PAY_LPCOST);

                // Battle
                case MSG_ATTACK           -> parseAttack(reader);
                case MSG_BATTLE           -> parseBattle(reader);
                case MSG_ATTACK_DISABLED  -> new DuelMessage.AttackDisabled();
                case MSG_DAMAGE_STEP_START -> new DuelMessage.DamageStepStart();
                case MSG_DAMAGE_STEP_END  -> new DuelMessage.DamageStepEnd();

                // Deck/Hand
                case MSG_SHUFFLE_DECK  -> new DuelMessage.ShuffleDeck(reader.readUint8());
                case MSG_SHUFFLE_HAND  -> parseShuffleHand(reader);
                case MSG_SHUFFLE_EXTRA -> new DuelMessage.ShuffleExtra(reader.readUint8());

                // UI/Info
                case MSG_HINT          -> parseHint(reader);
                case MSG_CARD_HINT     -> parseCardHint(reader);
                case MSG_FIELD_DISABLED -> new DuelMessage.FieldDisabled(reader.readInt32());
                case MSG_BECOME_TARGET -> parseBecomeTarget(reader);

                // Selection messages
                case MSG_SELECT_IDLECMD   -> parseSelectIdleCmd(reader);
                case MSG_SELECT_BATTLECMD -> parseSelectBattleCmd(reader);
                case MSG_SELECT_CARD      -> parseSelectCard(reader);
                case MSG_SELECT_CHAIN     -> parseSelectChain(reader);
                case MSG_SELECT_EFFECTYN  -> parseSelectEffectYn(reader);
                case MSG_SELECT_YESNO     -> parseSelectYesNo(reader);
                case MSG_SELECT_OPTION    -> parseSelectOption(reader);
                case MSG_SELECT_PLACE     -> parseSelectPlace(reader);
                case MSG_SELECT_DISFIELD  -> parseSelectPlace(reader); // same format
                case MSG_SELECT_POSITION  -> parseSelectPosition(reader);
                case MSG_SELECT_TRIBUTE   -> parseSelectTribute(reader);
                case MSG_SELECT_COUNTER   -> parseSelectCounter(reader);
                case MSG_SELECT_SUM       -> parseSelectSum(reader, bodyLength);
                case MSG_SELECT_UNSELECT_CARD -> parseSelectUnselectCard(reader);
                case MSG_SORT_CARD        -> parseSortCard(reader);
                case MSG_SORT_CHAIN       -> parseSortChain(reader);
                case MSG_ANNOUNCE_RACE    -> parseAnnounceRace(reader);
                case MSG_ANNOUNCE_ATTRIB  -> parseAnnounceAttrib(reader);
                case MSG_ANNOUNCE_NUMBER  -> parseAnnounceNumber(reader);
                case MSG_ANNOUNCE_CARD    -> parseRawSelection(reader, bodyLength, MSG_ANNOUNCE_CARD);
                case MSG_ROCK_PAPER_SCISSORS -> new DuelMessage.RockPaperScissors(reader.readUint8());
                case MSG_CONFIRM_DECKTOP -> parseConfirmDeckTop(reader);
                case MSG_CONFIRM_CARDS   -> parseConfirmCards(reader);
                case MSG_HAND_RES        -> parseHandResult(reader);

                // Misc action
                case MSG_EQUIP         -> new DuelMessage.Equip(LocInfo.read(reader), LocInfo.read(reader));
                case MSG_UNEQUIP       -> new DuelMessage.Unequip(LocInfo.read(reader));
                case MSG_CARD_TARGET   -> new DuelMessage.CardTarget(LocInfo.read(reader), LocInfo.read(reader));
                case MSG_CANCEL_TARGET -> new DuelMessage.CancelTarget(LocInfo.read(reader), LocInfo.read(reader));
                case MSG_ADD_COUNTER   -> parseAddCounter(reader);
                case MSG_REMOVE_COUNTER -> parseRemoveCounter(reader);
                case MSG_TOSS_COIN     -> parseTossCoin(reader);
                case MSG_TOSS_DICE     -> parseTossDice(reader);

                // State updates
                case MSG_UPDATE_DATA -> {
                    int player = reader.readUint8();
                    int location = reader.readUint8();
                    int queryLen = bodyLength - 2;
                    byte[] queryData = new byte[queryLen];
                    for (int i = 0; i < queryLen; i++) queryData[i] = (byte) reader.readUint8();
                    yield new DuelMessage.UpdateData(player, location, QueryParser.parseLocation(queryData));
                }
                case MSG_UPDATE_CARD -> {
                    int player = reader.readUint8();
                    int location = reader.readUint8();
                    int sequence = reader.readUint8();
                    int queryLen = bodyLength - 3;
                    byte[] queryData = new byte[queryLen];
                    for (int i = 0; i < queryLen; i++) queryData[i] = (byte) reader.readUint8();
                    var card = QueryParser.parseSingle(queryData);
                    yield new DuelMessage.UpdateCard(player, location, sequence,
                            card != null ? card : new QueriedCard());
                }

                // Fallback
                default -> {
                    byte[] body = new byte[bodyLength];
                    for (int i = 0; i < bodyLength; i++) {
                        body[i] = (byte) reader.readUint8();
                    }
                    yield new DuelMessage.Raw(type, body);
                }
            };

            } catch (Exception e) {
                LOGGER.warn("[Parse] FAILED msg type {} (body {} bytes): {}",
                        type, bodyLength, e.getMessage());
                msg = new DuelMessage.Raw(type, new byte[0]);
            }

            // Ensure we're at the expected position regardless of how much the parser read.
            int drift = messageEnd - reader.position();
            if (drift != 0) {
                LOGGER.debug("[Parse] msg type {} drifted {} bytes (body={}, consumed={})",
                        type, drift, bodyLength, bodyLength - drift);
                reader.skip(drift);
            }

            LOGGER.debug("[Parse] {} (type={}, body={} bytes)",
                    msg.getClass().getSimpleName(), type, bodyLength);
            messages.add(msg);
        }

        return messages;
    }

    // ---- Lifecycle ----

    private static DuelMessage.Start parseStart(BufferReader r) {
        int playerType = r.readUint8();
        int lp0 = r.readInt32();
        int lp1 = r.readInt32();
        int deck0 = r.readUint16();
        int extra0 = r.readUint16();
        int deck1 = r.readUint16();
        int extra1 = r.readUint16();
        return new DuelMessage.Start(playerType, lp0, lp1, deck0, extra0, deck1, extra1);
    }

    private static DuelMessage.Win parseWin(BufferReader r, int bodyLength) {
        int winner = r.readUint8();
        int reason = r.readUint8();
        return new DuelMessage.Win(winner, reason);
    }

    private static DuelMessage.NewTurn parseNewTurn(BufferReader r) {
        return new DuelMessage.NewTurn(r.readUint8());
    }

    private static DuelMessage.NewPhase parseNewPhase(BufferReader r) {
        return new DuelMessage.NewPhase(r.readUint16());
    }

    // ---- Card Movement ----

    private static DuelMessage.Draw parseDraw(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        List<Integer> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(r.readInt32());
            r.readInt32(); // position info (not needed for draw)
        }
        return new DuelMessage.Draw(player, codes);
    }

    private static DuelMessage.Move parseMove(BufferReader r) {
        int code = r.readInt32();
        LocInfo from = LocInfo.read(r);
        LocInfo to = LocInfo.read(r);
        int reason = r.readInt32();
        return new DuelMessage.Move(code, from, to, reason);
    }

    private static DuelMessage.PosChange parsePosChange(BufferReader r) {
        int code = r.readInt32();
        int controller = r.readUint8();
        int location = r.readUint8();
        int sequence = r.readUint8();
        int prevPos = r.readUint8();
        int newPos = r.readUint8();
        return new DuelMessage.PosChange(code, controller, location, sequence, prevPos, newPos);
    }

    private static DuelMessage.Set parseSet(BufferReader r) {
        int code = r.readInt32();
        LocInfo loc = LocInfo.read(r);
        return new DuelMessage.Set(code, loc);
    }

    private static DuelMessage.Swap parseSwap(BufferReader r) {
        int code1 = r.readInt32();
        LocInfo loc1 = LocInfo.read(r);
        int code2 = r.readInt32();
        LocInfo loc2 = LocInfo.read(r);
        return new DuelMessage.Swap(code1, loc1, code2, loc2);
    }

    // ---- Summons ----

    private static DuelMessage.Summoning parseSummoning(BufferReader r) {
        int code = r.readInt32();
        return new DuelMessage.Summoning(code, LocInfo.read(r));
    }

    private static DuelMessage.SpSummoning parseSpSummoning(BufferReader r) {
        int code = r.readInt32();
        return new DuelMessage.SpSummoning(code, LocInfo.read(r));
    }

    private static DuelMessage.FlipSummoning parseFlipSummoning(BufferReader r) {
        int code = r.readInt32();
        return new DuelMessage.FlipSummoning(code, LocInfo.read(r));
    }

    // ---- Chain ----

    private static DuelMessage.Chaining parseChaining(BufferReader r) {
        int code = r.readInt32();
        LocInfo loc = LocInfo.read(r);
        int trigController = r.readUint8();
        int trigLocation = r.readUint8();
        int trigSequence = r.readInt32();
        long desc = r.readInt64();
        int chainCount = r.readInt32();
        return new DuelMessage.Chaining(code, loc, trigController, trigLocation, trigSequence, desc, chainCount);
    }

    // ---- LP (shared structure) ----

    private static DuelMessage parsePlayerAmount(BufferReader r, int msgType) {
        int player = r.readUint8();
        int amount = r.readInt32();
        return switch (msgType) {
            case MSG_DAMAGE     -> new DuelMessage.Damage(player, amount);
            case MSG_RECOVER    -> new DuelMessage.Recover(player, amount);
            case MSG_LPUPDATE   -> new DuelMessage.LpUpdate(player, amount);
            case MSG_PAY_LPCOST -> new DuelMessage.PayLpCost(player, amount);
            default -> throw new IllegalArgumentException("Not a player+amount message: " + msgType);
        };
    }

    // ---- Battle ----

    private static DuelMessage.Attack parseAttack(BufferReader r) {
        LocInfo attacker = LocInfo.read(r);
        LocInfo target = LocInfo.read(r);
        return new DuelMessage.Attack(attacker, target);
    }

    private static DuelMessage.Battle parseBattle(BufferReader r) {
        LocInfo attacker = LocInfo.read(r);
        int atkAtk = r.readInt32();
        int atkDef = r.readInt32();
        int da = r.readUint8();
        LocInfo defender = LocInfo.read(r);
        int defAtk = r.readInt32();
        int defDef = r.readInt32();
        int dd = r.readUint8();
        return new DuelMessage.Battle(attacker, atkAtk, atkDef, da, defender, defAtk, defDef, dd);
    }

    // ---- Deck/Hand ----

    private static DuelMessage.ShuffleHand parseShuffleHand(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        List<Integer> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(r.readInt32());
        }
        return new DuelMessage.ShuffleHand(player, codes);
    }

    // ---- UI/Info ----

    private static DuelMessage.Hint parseHint(BufferReader r) {
        int hintType = r.readUint8();
        int player = r.readUint8();
        long data = r.readInt64();
        return new DuelMessage.Hint(hintType, player, data);
    }

    private static DuelMessage.CardHint parseCardHint(BufferReader r) {
        LocInfo loc = LocInfo.read(r);
        int chintType = r.readUint8();
        long value = r.readInt64();
        return new DuelMessage.CardHint(loc, chintType, value);
    }

    private static DuelMessage.BecomeTarget parseBecomeTarget(BufferReader r) {
        int count = r.readInt32();
        List<LocInfo> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(LocInfo.read(r));
        }
        return new DuelMessage.BecomeTarget(targets);
    }

    // ---- Selection messages ----

    private static DuelMessage.SelectIdleCmd parseSelectIdleCmd(BufferReader r) {
        int player = r.readUint8();

        // Summonable: [uint32 count][ (uint32 code, uint8 con, uint8 loc, uint32 seq) * N ]
        int summonCount = r.readInt32();
        List<DuelMessage.IdleCmdCard> summonable = new ArrayList<>(summonCount);
        for (int i = 0; i < summonCount; i++) summonable.add(DuelMessage.IdleCmdCard.read(r));

        int spSummonCount = r.readInt32();
        List<DuelMessage.IdleCmdCard> spSummonable = new ArrayList<>(spSummonCount);
        for (int i = 0; i < spSummonCount; i++) spSummonable.add(DuelMessage.IdleCmdCard.read(r));

        // Repositionable: [uint32 count][ (uint32 code, uint8 con, uint8 loc, uint8 seq) * N ]
        int reposCount = r.readInt32();
        List<DuelMessage.ReposCard> repositionable = new ArrayList<>(reposCount);
        for (int i = 0; i < reposCount; i++) repositionable.add(DuelMessage.ReposCard.read(r));

        int setMonsterCount = r.readInt32();
        List<DuelMessage.IdleCmdCard> settableMonsters = new ArrayList<>(setMonsterCount);
        for (int i = 0; i < setMonsterCount; i++) settableMonsters.add(DuelMessage.IdleCmdCard.read(r));

        int setSpellCount = r.readInt32();
        List<DuelMessage.IdleCmdCard> settableSpells = new ArrayList<>(setSpellCount);
        for (int i = 0; i < setSpellCount; i++) settableSpells.add(DuelMessage.IdleCmdCard.read(r));

        // Activatable: [uint32 count][ (uint32 code, uint8 con, uint8 loc, uint32 seq, uint64 desc, uint8 flag) * N ]
        int activateCount = r.readInt32();
        List<DuelMessage.ActivatableCard> activatable = new ArrayList<>(activateCount);
        for (int i = 0; i < activateCount; i++) activatable.add(DuelMessage.ActivatableCard.read(r));

        boolean canBattle = r.readUint8() != 0;
        boolean canEnd = r.readUint8() != 0;
        boolean canShuffle = r.readUint8() != 0;

        return new DuelMessage.SelectIdleCmd(player, summonable, spSummonable,
                repositionable, settableMonsters, settableSpells, activatable,
                canBattle, canEnd, canShuffle);
    }

    private static DuelMessage.SelectBattleCmd parseSelectBattleCmd(BufferReader r) {
        int player = r.readUint8();

        // Activatable: same format as idle cmd
        int activateCount = r.readInt32();
        List<DuelMessage.ActivatableCard> activatable = new ArrayList<>(activateCount);
        for (int i = 0; i < activateCount; i++) activatable.add(DuelMessage.ActivatableCard.read(r));

        // Attackable: [uint32 count][ (uint32 code, uint8 con, uint8 loc, uint8 seq, uint8 diratt) * N ]
        int attackCount = r.readInt32();
        List<DuelMessage.AttackCard> attackable = new ArrayList<>(attackCount);
        for (int i = 0; i < attackCount; i++) attackable.add(DuelMessage.AttackCard.read(r));

        boolean canMain2 = r.readUint8() != 0;
        boolean canEnd = r.readUint8() != 0;
        return new DuelMessage.SelectBattleCmd(player, activatable, attackable,
                canMain2, canEnd);
    }

    /** Read a count-prefixed list of CardInfo entries. */
    private static List<DuelMessage.CardInfo> readCardInfoList(BufferReader r) {
        int count = r.readInt32();
        List<DuelMessage.CardInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(DuelMessage.CardInfo.read(r));
        }
        return list;
    }

    /** Read a count-prefixed list of ActivatableCard entries (CardInfo + int64 desc). */
    private static List<DuelMessage.ActivatableCard> readActivatableList(BufferReader r) {
        int count = r.readInt32();
        List<DuelMessage.ActivatableCard> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(DuelMessage.ActivatableCard.read(r));
        }
        return list;
    }

    private static DuelMessage.SelectCard parseSelectCard(BufferReader r) {
        int player = r.readUint8();
        boolean cancelable = r.readUint8() != 0;
        int min = r.readInt32();
        int max = r.readInt32();
        int count = r.readInt32();
        List<DuelMessage.CardInfo> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.CardInfo.read(r));
        }
        return new DuelMessage.SelectCard(player, cancelable, min, max, cards);
    }

    private static DuelMessage.SelectChain parseSelectChain(BufferReader r) {
        int player = r.readUint8();
        int speCount = r.readUint8();
        boolean forced = r.readUint8() != 0;
        int hint0 = r.readInt32();
        int hint1 = r.readInt32();
        int count = r.readInt32();
        List<DuelMessage.ActivatableCard> chains = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chains.add(DuelMessage.ActivatableCard.read(r));
        }
        return new DuelMessage.SelectChain(player, speCount, forced, hint0, hint1, chains);
    }

    private static DuelMessage.SelectEffectYn parseSelectEffectYn(BufferReader r) {
        int player = r.readUint8();
        int code = r.readInt32();
        LocInfo loc = LocInfo.read(r);
        long desc = r.readInt64();
        return new DuelMessage.SelectEffectYn(player, code, loc, desc);
    }

    private static DuelMessage.SelectYesNo parseSelectYesNo(BufferReader r) {
        int player = r.readUint8();
        long desc = r.readInt64();
        return new DuelMessage.SelectYesNo(player, desc);
    }

    private static DuelMessage.SelectOption parseSelectOption(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        List<Long> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(r.readInt64());
        }
        return new DuelMessage.SelectOption(player, options);
    }

    private static DuelMessage.SelectPlace parseSelectPlace(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        int field = r.readInt32();
        return new DuelMessage.SelectPlace(player, count, field);
    }

    private static DuelMessage.SelectPosition parseSelectPosition(BufferReader r) {
        int player = r.readUint8();
        int code = r.readInt32();
        int positions = r.readUint8();
        return new DuelMessage.SelectPosition(player, code, positions);
    }

    private static DuelMessage.SelectTribute parseSelectTribute(BufferReader r) {
        int player = r.readUint8();
        boolean cancelable = r.readUint8() != 0;
        int min = r.readInt32();
        int max = r.readInt32();
        int count = r.readInt32();
        List<DuelMessage.TributeCard> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.TributeCard.read(r));
        }
        return new DuelMessage.SelectTribute(player, cancelable, min, max, cards);
    }

    private static DuelMessage.SelectCounter parseSelectCounter(BufferReader r) {
        int player = r.readUint8();
        int counterType = r.readUint16();
        int count = r.readUint16();
        int cardCount = r.readInt32();
        List<DuelMessage.CounterCard> cards = new ArrayList<>(cardCount);
        for (int i = 0; i < cardCount; i++) {
            cards.add(DuelMessage.CounterCard.read(r));
        }
        return new DuelMessage.SelectCounter(player, counterType, count, cards);
    }

    private static DuelMessage.SelectSum parseSelectSum(BufferReader r, int bodyLength) {
        int player = r.readUint8();
        boolean selectMode = r.readUint8() != 0;
        int targetSum = r.readInt32();
        int min = r.readInt32();
        int max = r.readInt32();

        int mustCount = r.readInt32();
        var mustSelect = new ArrayList<DuelMessage.SumCard>(mustCount);
        for (int i = 0; i < mustCount; i++) {
            mustSelect.add(DuelMessage.SumCard.read(r));
        }

        int selectCount = r.readInt32();
        var selectable = new ArrayList<DuelMessage.SumCard>(selectCount);
        for (int i = 0; i < selectCount; i++) {
            selectable.add(DuelMessage.SumCard.read(r));
        }

        return new DuelMessage.SelectSum(player, selectMode, targetSum,
                min, max, mustSelect, selectable);
    }

    private static DuelMessage.SelectUnselectCard parseSelectUnselectCard(BufferReader r) {
        int player = r.readUint8();
        boolean finishable = r.readUint8() != 0;
        boolean cancelable = r.readUint8() != 0;
        int min = r.readInt32();
        int max = r.readInt32();
        int selectCount = r.readInt32();
        List<DuelMessage.CardInfo> selectableCards = new ArrayList<>(selectCount);
        for (int i = 0; i < selectCount; i++) {
            selectableCards.add(DuelMessage.CardInfo.read(r));
        }
        int unselectCount = r.readInt32();
        List<DuelMessage.CardInfo> unselectableCards = new ArrayList<>(unselectCount);
        for (int i = 0; i < unselectCount; i++) {
            unselectableCards.add(DuelMessage.CardInfo.read(r));
        }
        return new DuelMessage.SelectUnselectCard(player, finishable, cancelable,
                min, max, selectableCards, unselectableCards);
    }

    private static DuelMessage.SortCard parseSortCard(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        List<DuelMessage.SortableCard> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.SortableCard.read(r));
        }
        return new DuelMessage.SortCard(player, cards);
    }

    private static DuelMessage.SortChain parseSortChain(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        List<DuelMessage.SortableCard> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.SortableCard.read(r));
        }
        return new DuelMessage.SortChain(player, cards);
    }

    private static DuelMessage.AnnounceRace parseAnnounceRace(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        long available = r.readInt64();
        return new DuelMessage.AnnounceRace(player, count, available);
    }

    private static DuelMessage.AnnounceAttrib parseAnnounceAttrib(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        int available = r.readInt32();
        return new DuelMessage.AnnounceAttrib(player, count, available);
    }

    private static DuelMessage.AnnounceNumber parseAnnounceNumber(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        List<Long> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(r.readInt64());
        }
        return new DuelMessage.AnnounceNumber(player, options);
    }

    private static DuelMessage.AnnounceCard parseRawSelection(BufferReader r, int bodyLength,
                                                               int msgType) {
        int startPos = r.remaining();
        int player = r.readUint8();
        int consumed = startPos - r.remaining();
        int remaining = bodyLength - consumed;
        byte[] rawBody = new byte[remaining];
        for (int i = 0; i < remaining; i++) {
            rawBody[i] = (byte) r.readUint8();
        }
        return new DuelMessage.AnnounceCard(player, rawBody);
    }

    // ---- Confirm / Hand Result ----

    private static DuelMessage.ConfirmDeckTop parseConfirmDeckTop(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        var cards = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.ConfirmCard.read(r));
        }
        return new DuelMessage.ConfirmDeckTop(player, cards);
    }

    private static DuelMessage.ConfirmCards parseConfirmCards(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        var cards = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.ConfirmCard.read(r));
        }
        return new DuelMessage.ConfirmCards(player, cards);
    }

    private static DuelMessage.HandResult parseHandResult(BufferReader r) {
        int result = r.readUint8();
        int hand0 = result & 0x3;
        int hand1 = (result >> 2) & 0x3;
        return new DuelMessage.HandResult(hand0, hand1);
    }

    // ---- Misc ----

    private static DuelMessage.AddCounter parseAddCounter(BufferReader r) {
        int counterType = r.readUint16();
        int controller = r.readUint8();
        int location = r.readUint8();
        int sequence = r.readUint8();
        int count = r.readUint16();
        return new DuelMessage.AddCounter(counterType, controller, location, sequence, count);
    }

    private static DuelMessage.RemoveCounter parseRemoveCounter(BufferReader r) {
        int counterType = r.readUint16();
        int controller = r.readUint8();
        int location = r.readUint8();
        int sequence = r.readUint8();
        int count = r.readUint16();
        return new DuelMessage.RemoveCounter(counterType, controller, location, sequence, count);
    }

    private static DuelMessage.TossCoin parseTossCoin(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        List<Integer> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(r.readUint8());
        }
        return new DuelMessage.TossCoin(player, results);
    }

    private static DuelMessage.TossDice parseTossDice(BufferReader r) {
        int player = r.readUint8();
        int count = r.readUint8();
        List<Integer> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(r.readUint8());
        }
        return new DuelMessage.TossDice(player, results);
    }
}

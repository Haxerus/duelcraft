package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Client-side duel state, built up from incoming duel messages.
 * Owned by DuelScreen — lives only while the duel screen is open.
 */
public class ClientDuelState {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Which player index we are (0 or 1)
    public final int localPlayer;
    public final String opponentName;

    // Life points per player
    public final int[] lp = new int[2];

    // Turn / phase
    public int currentTurn;
    public int currentPhase;
    public int turnCount;

    // Hands — card codes per player (0 = unknown/face-down for opponent)
    @SuppressWarnings("unchecked")
    public final List<Integer>[] hand = new List[]{ new ArrayList<>(), new ArrayList<>() };

    // Monster zones: [player][0-4 = main, 5-6 = EMZ] — card code, 0 = empty
    public final int[][] mzone = new int[2][7];
    public final int[][] mzonePos = new int[2][7];

    // Spell/Trap zones: [player][0-4 = S/T, 5 = field zone] — card code, 0 = empty
    public final int[][] szone = new int[2][6];
    public final int[][] szonePos = new int[2][6];

    // Pile counts
    public final int[] deckCount = new int[2];
    public final int[] extraCount = new int[2];
    public final int[] graveCount = new int[2];
    public final int[] banishedCount = new int[2];

    // Current chain links
    public final List<ChainLink> chain = new ArrayList<>();

    // Pending selection prompt (null = no prompt)
    public DuelMessage pendingPrompt;

    // Last game action message (for UI display)
    public DuelMessage lastAction;

    // Winner (-1 = ongoing)
    public int winner = -1;
    public int winReason;

    public ClientDuelState(DuelStartPayload startInfo) {
        this.localPlayer = startInfo.localPlayer();
        this.opponentName = startInfo.opponentName();
        this.lp[0] = startInfo.lp0();
        this.lp[1] = startInfo.lp1();
        this.deckCount[0] = startInfo.deckSize();
        this.deckCount[1] = startInfo.deckSize();
        this.extraCount[0] = startInfo.extraSize();
        this.extraCount[1] = startInfo.extraSize();
        LOGGER.debug("[State] Init: localPlayer={}, LP={}|{}, deck={}|{}, extra={}|{}",
                localPlayer, lp[0], lp[1], deckCount[0], deckCount[1], extraCount[0], extraCount[1]);
    }

    /**
     * Apply a duel message to update state.
     */
    public void applyMessage(DuelMessage msg) {
        lastAction = msg;

        switch (msg) {
            // ---- Lifecycle ----
            case DuelMessage.Start start -> {
                lp[0] = start.lp0();
                lp[1] = start.lp1();
                deckCount[0] = start.deckCount0();
                deckCount[1] = start.deckCount1();
                extraCount[0] = start.extraCount0();
                extraCount[1] = start.extraCount1();
                LOGGER.debug("[State] Start: LP={}|{}, Deck={}|{}, Extra={}|{}",
                        lp[0], lp[1], deckCount[0], deckCount[1], extraCount[0], extraCount[1]);
            }
            case DuelMessage.Win win -> {
                winner = win.winner();
                winReason = win.reason();
                LOGGER.info("[State] Win: player={}, reason={}", winner, winReason);
            }
            case DuelMessage.NewTurn nt -> {
                currentTurn = nt.player();
                turnCount++;
                LOGGER.debug("[State] NewTurn: player={}, turnCount={}", currentTurn, turnCount);
            }
            case DuelMessage.NewPhase np -> {
                currentPhase = np.phase();
                LOGGER.debug("[State] NewPhase: {}", phaseName());
            }

            // ---- Card Movement ----
            case DuelMessage.Draw draw -> {
                hand[draw.player()].addAll(draw.codes());
                deckCount[draw.player()] -= draw.codes().size();
                LOGGER.debug("[State] Draw: player={}, codes={}", draw.player(), draw.codes());
            }
            case DuelMessage.Move move -> {
                LOGGER.debug("[State] Move: code={}, from=[p{} loc=0x{} seq={}] to=[p{} loc=0x{} seq={} pos=0x{}], reason=0x{}",
                        move.code(),
                        move.from().controller(), Integer.toHexString(move.from().location()), move.from().sequence(),
                        move.to().controller(), Integer.toHexString(move.to().location()), move.to().sequence(),
                        Integer.toHexString(move.to().position()), Integer.toHexString(move.reason()));
                applyMove(move);
            }
            case DuelMessage.PosChange pc -> {
                if (pc.location() == LOCATION_MZONE) {
                    mzonePos[pc.controller()][pc.sequence()] = pc.newPosition();
                } else if (pc.location() == LOCATION_SZONE) {
                    szonePos[pc.controller()][pc.sequence()] = pc.newPosition();
                }
            }
            case DuelMessage.Set set -> {
                LOGGER.debug("[State] Set: code={}, loc=[p{} loc=0x{} seq={}]",
                        set.code(), set.location().controller(),
                        Integer.toHexString(set.location().location()), set.location().sequence());
                placeCard(set.location(), set.code());
            }
            case DuelMessage.Swap swap -> swapCards(swap.loc1(), swap.loc2());

            // ---- Summons ----
            case DuelMessage.Summoning ignored -> { } // card appears via MSG_MOVE
            case DuelMessage.SpSummoning ignored -> { }
            case DuelMessage.FlipSummoning fs -> {
                // Flip the card face-up in place
                if (fs.location().location() == LOCATION_MZONE) {
                    mzone[fs.location().controller()][fs.location().sequence()] = fs.code();
                    mzonePos[fs.location().controller()][fs.location().sequence()] = fs.location().position();
                }
            }
            case DuelMessage.Summoned ignored -> { }
            case DuelMessage.SpSummoned ignored -> { }
            case DuelMessage.FlipSummoned ignored -> { }

            // ---- Chain ----
            case DuelMessage.Chaining c ->
                    chain.add(new ChainLink(c.code(), c.location(), c.chainIndex()));
            case DuelMessage.ChainEnd ignored -> chain.clear();
            case DuelMessage.Chained ignored -> { }
            case DuelMessage.ChainSolving ignored -> { }
            case DuelMessage.ChainSolved ignored -> { }
            case DuelMessage.ChainNegated ignored -> { }
            case DuelMessage.ChainDisabled ignored -> { }

            // ---- LP ----
            case DuelMessage.Damage dmg -> {
                lp[dmg.player()] = Math.max(0, lp[dmg.player()] - dmg.amount());
                LOGGER.debug("[State] Damage: player={}, amount={}, lp now={}", dmg.player(), dmg.amount(), lp[dmg.player()]);
            }
            case DuelMessage.Recover rec -> {
                lp[rec.player()] += rec.amount();
                LOGGER.debug("[State] Recover: player={}, amount={}, lp now={}", rec.player(), rec.amount(), lp[rec.player()]);
            }
            case DuelMessage.LpUpdate upd -> {
                lp[upd.player()] = upd.lp();
                LOGGER.debug("[State] LpUpdate: player={}, lp={}", upd.player(), upd.lp());
            }
            case DuelMessage.PayLpCost pay -> {
                lp[pay.player()] = Math.max(0, lp[pay.player()] - pay.amount());
                LOGGER.debug("[State] PayLpCost: player={}, amount={}, lp now={}", pay.player(), pay.amount(), lp[pay.player()]);
            }

            // ---- Deck/Hand ----
            case DuelMessage.ShuffleDeck ignored -> { }
            case DuelMessage.ShuffleHand sh -> {
                hand[sh.player()].clear();
                hand[sh.player()].addAll(sh.codes());
            }
            case DuelMessage.ShuffleExtra ignored -> { }

            // ---- Battle (no state change, UI can animate from lastAction) ----
            case DuelMessage.Attack ignored -> { }
            case DuelMessage.Battle ignored -> { }
            case DuelMessage.AttackDisabled ignored -> { }
            case DuelMessage.DamageStepStart ignored -> { }
            case DuelMessage.DamageStepEnd ignored -> { }

            // ---- Selection prompts — set pendingPrompt for the UI ----
            case DuelMessage.SelectIdleCmd sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectIdleCmd player={}", sel.player()); }
            case DuelMessage.SelectBattleCmd sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectBattleCmd player={}", sel.player()); }
            case DuelMessage.SelectCard sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectCard player={}, min={}, max={}, cards={}", sel.player(), sel.min(), sel.max(), sel.cards().stream().map(c -> String.valueOf(c.code())).toList()); }
            case DuelMessage.SelectChain sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectChain player={}, count={}, forced={}", sel.player(), sel.count(), sel.forced()); }
            case DuelMessage.SelectEffectYn sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectEffectYn player={}, code={}", sel.player(), sel.code()); }
            case DuelMessage.SelectYesNo sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectYesNo player={}, desc={}", sel.player(), sel.desc()); }
            case DuelMessage.SelectOption sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectOption player={}, options={}", sel.player(), sel.options()); }
            case DuelMessage.SelectPlace sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectPlace player={}, count={}, field=0x{}", sel.player(), sel.count(), Integer.toHexString(sel.field())); }
            case DuelMessage.SelectPosition sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectPosition player={}, code={}, pos=0x{}", sel.player(), sel.code(), Integer.toHexString(sel.positions())); }
            case DuelMessage.SelectTribute sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectTribute player={}, min={}, max={}", sel.player(), sel.min(), sel.max()); }
            case DuelMessage.SelectCounter sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectCounter player={}", sel.player()); }
            case DuelMessage.SelectSum sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectSum player={}", sel.player()); }
            case DuelMessage.SelectUnselectCard sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SelectUnselectCard player={}", sel.player()); }
            case DuelMessage.SortCard sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SortCard player={}", sel.player()); }
            case DuelMessage.SortChain sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: SortChain player={}", sel.player()); }
            case DuelMessage.AnnounceRace sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: AnnounceRace player={}", sel.player()); }
            case DuelMessage.AnnounceAttrib sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: AnnounceAttrib player={}", sel.player()); }
            case DuelMessage.AnnounceNumber sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: AnnounceNumber player={}", sel.player()); }
            case DuelMessage.AnnounceCard sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: AnnounceCard player={}", sel.player()); }
            case DuelMessage.RockPaperScissors sel -> { pendingPrompt = msg; LOGGER.debug("[State] Prompt: RockPaperScissors player={}", sel.player()); }

            // ---- Everything else ----
            default -> LOGGER.debug("[State] Unhandled: {} (type={})", msg.getClass().getSimpleName(), msg.type());
        }
    }

    // ---- Move handling ----

    private void applyMove(DuelMessage.Move move) {
        removeCard(move.from());
        if (move.to().location() != 0) {
            placeCard(move.to(), move.code());
        }
    }

    private void removeCard(LocInfo loc) {
        switch (loc.location()) {
            case LOCATION_HAND -> {
                if (loc.sequence() < hand[loc.controller()].size()) {
                    hand[loc.controller()].remove(loc.sequence());
                } else if (!hand[loc.controller()].isEmpty()) {
                    hand[loc.controller()].removeLast();
                }
            }
            case LOCATION_MZONE -> {
                mzone[loc.controller()][loc.sequence()] = 0;
                mzonePos[loc.controller()][loc.sequence()] = 0;
            }
            case LOCATION_SZONE -> {
                szone[loc.controller()][loc.sequence()] = 0;
                szonePos[loc.controller()][loc.sequence()] = 0;
            }
            case LOCATION_DECK -> deckCount[loc.controller()]--;
            case LOCATION_EXTRA -> extraCount[loc.controller()]--;
            case LOCATION_GRAVE -> graveCount[loc.controller()]--;
            case LOCATION_REMOVED -> banishedCount[loc.controller()]--;
            default -> { }
        }
    }

    private void placeCard(LocInfo loc, int code) {
        switch (loc.location()) {
            case LOCATION_HAND -> hand[loc.controller()].add(code);
            case LOCATION_MZONE -> {
                mzone[loc.controller()][loc.sequence()] = code;
                mzonePos[loc.controller()][loc.sequence()] = loc.position();
            }
            case LOCATION_SZONE -> {
                szone[loc.controller()][loc.sequence()] = code;
                szonePos[loc.controller()][loc.sequence()] = loc.position();
            }
            case LOCATION_DECK -> deckCount[loc.controller()]++;
            case LOCATION_EXTRA -> extraCount[loc.controller()]++;
            case LOCATION_GRAVE -> graveCount[loc.controller()]++;
            case LOCATION_REMOVED -> banishedCount[loc.controller()]++;
            default -> { }
        }
    }

    private void swapCards(LocInfo loc1, LocInfo loc2) {
        if (loc1.location() == LOCATION_MZONE && loc2.location() == LOCATION_MZONE) {
            int tempCode = mzone[loc1.controller()][loc1.sequence()];
            int tempPos = mzonePos[loc1.controller()][loc1.sequence()];
            mzone[loc1.controller()][loc1.sequence()] = mzone[loc2.controller()][loc2.sequence()];
            mzonePos[loc1.controller()][loc1.sequence()] = mzonePos[loc2.controller()][loc2.sequence()];
            mzone[loc2.controller()][loc2.sequence()] = tempCode;
            mzonePos[loc2.controller()][loc2.sequence()] = tempPos;
        }
    }

    // ---- Helpers for the UI ----

    public boolean isLocalTurn() {
        return currentTurn == localPlayer;
    }

    public int opponent() {
        return 1 - localPlayer;
    }

    public String phaseName() {
        return switch (currentPhase) {
            case PHASE_DRAW -> "Draw";
            case PHASE_STANDBY -> "Standby";
            case PHASE_MAIN1 -> "Main 1";
            case PHASE_BATTLE_START, PHASE_BATTLE_STEP, PHASE_DAMAGE,
                 PHASE_DAMAGE_CAL, PHASE_BATTLE -> "Battle";
            case PHASE_MAIN2 -> "Main 2";
            case PHASE_END -> "End";
            default -> "---";
        };
    }

    public record ChainLink(int code, LocInfo location, int chainIndex) {}
}

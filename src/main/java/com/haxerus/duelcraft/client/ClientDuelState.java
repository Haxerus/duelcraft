package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.client.carddata.CardStringHelper;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Client-side duel state, built up from incoming duel messages.
 * Owned by DuelScreen — lives only while the duel screen is open.
 */
public class ClientDuelState {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientDuelState.class);

    // ── Dirty flags for efficient UI refresh ──
    public enum DirtyFlag {
        LP, TURN_PHASE,
        HAND_0, HAND_1,
        MZONE_0, MZONE_1,
        SZONE_0, SZONE_1,
        PILE_COUNTS, CHAIN, PROMPT, WINNER,
        FIELD_STATS, CONFIRM
    }

    private final EnumSet<DirtyFlag> dirtyFlags = EnumSet.noneOf(DirtyFlag.class);

    public EnumSet<DirtyFlag> consumeDirtyFlags() {
        var copy = EnumSet.copyOf(dirtyFlags);
        dirtyFlags.clear();
        return copy;
    }

    public boolean isDirty() {
        return !dirtyFlags.isEmpty();
    }

    /** Mark everything dirty so a rebuilt UI renders the current state (used when reopening the screen). */
    public void markAllDirty() {
        dirtyFlags.addAll(EnumSet.allOf(DirtyFlag.class));
    }

    /** Mark all visual zones dirty (used when card images finish loading). */
    public void markAllVisualsDirty() {
        dirtyFlags.addAll(EnumSet.of(
                DirtyFlag.HAND_0, DirtyFlag.HAND_1,
                DirtyFlag.MZONE_0, DirtyFlag.MZONE_1,
                DirtyFlag.SZONE_0, DirtyFlag.SZONE_1));
    }

    private DirtyFlag handFlag(int player) {
        return player == 0 ? DirtyFlag.HAND_0 : DirtyFlag.HAND_1;
    }

    private DirtyFlag mzoneFlag(int player) {
        return player == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1;
    }

    private DirtyFlag szoneFlag(int player) {
        return player == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1;
    }

    // Which player index we are (0 or 1)
    public final int localPlayer;
    public final String opponentName;
    // Engine flags this duel was created with; the field layout is derived from them.
    public final long duelFlags;

    // Life points per player
    public final int[] lp = new int[2];
    public int startingLP;

    // Turn / phase
    public int currentTurn;
    public int currentPhase;
    public int turnCount;

    // Hands — one card object per held card (code 0 = hidden from this client)
    @SuppressWarnings("unchecked")
    public final List<ClientCard>[] hand = new List[]{ new ArrayList<>(), new ArrayList<>() };

    // Monster zones: [player][0-4 = main, 5-6 = EMZ] — null = empty
    public final ClientCard[][] mzone = new ClientCard[2][7];

    // Spell/Trap zones: [player][0-4 = S/T, 5 = field spell, 6-7 = pendulum] — null = empty
    public final ClientCard[][] szone = new ClientCard[2][8];

    // Deck, extra deck, graveyard, banished — card lists; the deck holds blank face-down cards
    // from the start payload so deck-top reveals have objects to write codes into.
    @SuppressWarnings("unchecked")
    public final List<ClientCard>[] deck = new List[]{ new ArrayList<>(), new ArrayList<>() };
    @SuppressWarnings("unchecked")
    public final List<ClientCard>[] extra = new List[]{ new ArrayList<>(), new ArrayList<>() };
    @SuppressWarnings("unchecked")
    public final List<ClientCard>[] grave = new List[]{ new ArrayList<>(), new ArrayList<>() };
    @SuppressWarnings("unchecked")
    public final List<ClientCard>[] banished = new List[]{ new ArrayList<>(), new ArrayList<>() };

    // Zones the engine has disabled, as a 16-bit mask per absolute player (MSG_FIELD_DISABLED).
    // Bit layout matches SelectPlace: monster zones 0-6, spell/trap zones 8-15.
    public final int[] disabledZones = new int[2];

    // Cards the engine just targeted or selected; cleared when the next prompt arrives.
    public final Set<ClientCard> highlighted = new LinkedHashSet<>();

    // Refcounted per-player hints (MSG_PLAYER_HINT), desc -> count. No UI yet.
    @SuppressWarnings("unchecked")
    public final Map<Long, Integer>[] playerHints = new Map[]{ new LinkedHashMap<>(), new LinkedHashMap<>() };

    // The card that ended a match outright (MSG_MATCH_KILL); match play only, no UI yet.
    public int matchKillCode;

    // Both decks turned over (MSG_REVERSE_DECK). One global flag, as edopro's deck_reversed.
    public boolean deckReversed;

    // Convenience accessors for pile counts (derived from list size)
    public int deckCount(int player) { return deck[player].size(); }
    public int extraCount(int player) { return extra[player].size(); }
    public int graveCount(int player) { return grave[player].size(); }
    public int banishedCount(int player) { return banished[player].size(); }

    /** The card occupying a field zone or sitting at {@code sequence} of a pile, or null. */
    public ClientCard cardAt(int player, int location, int sequence) {
        if (location == LOCATION_MZONE) {
            return sequence >= 0 && sequence < mzone[player].length ? mzone[player][sequence] : null;
        }
        if (location == LOCATION_SZONE) {
            return sequence >= 0 && sequence < szone[player].length ? szone[player][sequence] : null;
        }
        var list = pile(player, location);
        return list != null && sequence >= 0 && sequence < list.size() ? list.get(sequence) : null;
    }

    /** The list backing a non-field location, or null for the field and unknown locations. */
    public List<ClientCard> pile(int player, int location) {
        return switch (location) {
            case LOCATION_HAND -> hand[player];
            case LOCATION_DECK -> deck[player];
            case LOCATION_EXTRA -> extra[player];
            case LOCATION_GRAVE -> grave[player];
            case LOCATION_REMOVED -> banished[player];
            default -> null;
        };
    }

    // Current chain links
    public final List<ChainLink> chain = new ArrayList<>();

    // Pending selection prompt (null = no prompt)
    public DuelMessage pendingPrompt;

    // Last prompt record received, kept after pendingPrompt is cleared so MSG_RETRY can restore it.
    private DuelMessage lastPrompt;

    // One-shot status text for the UI after MSG_RETRY; PromptController shows it and clears it.
    public String retryMessage;

    // Answered guard: at most one response per prompt (edopro's "answered" flag).
    // Reset whenever a new prompt (or Retry) arrives.
    private boolean responseSent;

    /** Marks a response as sent for the current prompt. Returns false if one was already sent. */
    public boolean markResponseSent() {
        if (responseSent) return false;
        responseSent = true;
        return true;
    }

    /** Called by the screen right after a response is sent: clears the active prompt UI, keeping lastPrompt. */
    public void onResponseSent() {
        pendingPrompt = null;
        clearCardActions();
    }

    /** Sets a new pending prompt, remembering it for Retry and resetting the answered guard. */
    private void setPrompt(DuelMessage msg) {
        pendingPrompt = msg;
        lastPrompt = msg;
        responseSent = false;
        // edopro spends select_hint on the prompt it captions (duelclient.cpp:2016); a prompt that
        // arrives without one falls back to its own text. Retry keeps the caption it was given.
        promptCaptionDesc = selectHint;
        selectHint = 0;
        clearHighlights();
    }

    // MSG_SELECT_BATTLECMD action types (playerop.cpp select_battle_command).
    public static final class BattleAction {
        public static final int ACTIVATE = 0;
        public static final int ATTACK = 1;
        public static final int TO_MAIN2 = 2;
        public static final int END_BATTLE = 3;
        private BattleAction() { }
    }

    // MSG_SELECT_IDLECMD action types (playerop.cpp select_idle_command).
    public static final class IdleAction {
        public static final int SUMMON = 0;
        public static final int SPECIAL_SUMMON = 1;
        public static final int REPOSITION = 2;
        public static final int SET_MONSTER = 3;
        public static final int SET_SPELL_TRAP = 4;
        public static final int ACTIVATE = 5;
        public static final int TO_BATTLE = 6;
        public static final int END_TURN = 7;
        public static final int SHUFFLE_HAND = 8;
        private IdleAction() { }
    }

    // Card actions: maps card location → available actions (for click-on-card UI).
    // `desc` is the activating effect's description (0 for everything but Activate), so two
    // effects on one card can be told apart in the option dialog.
    public record CardAction(int actionType, int listIndex, String label, long desc) {}
    public record CardLocation(int controller, int location, int sequence) {}
    public final Map<CardLocation, List<CardAction>> cardActions = new HashMap<>();

    // Last game action message (for UI display)
    public DuelMessage lastAction;

    // Last hint (provides context for next selection, e.g., "Select a monster")
    public int lastHintType;
    public long lastHintData;

    // ── Hint surfaces (MSG_HINT, edopro duelclient.cpp:1390-1522) ──

    /** A HINT_SELECTMSG waiting for the prompt it captions ({@code select_hint}). */
    private long selectHint;
    /** The caption HINT_SELECTMSG gave the active prompt, 0 when it arrived without one. */
    public long promptCaptionDesc;
    /** HINT_MESSAGE text waiting on its blocking modal; the modal's OK clears it. */
    public String pendingModal;
    /** Toast lines from HINT_OPSELECTED/RACE/ATTRIB/CODE/NUMBER, shown one at a time. */
    public final Deque<String> toasts = new ArrayDeque<>();
    /** HINT_CARD: the card to drop into the info banner, 0 once the screen has shown it. */
    public int revealCardCode;
    /**
     * HINT_ZONE: the zones to flash and when they were named. The bit layout is SELECT_PLACE's,
     * but a <b>set</b> bit marks a zone to flash — the opposite of SELECT_PLACE's blocked bits.
     */
    public int zoneFlashMask;
    public long zoneFlashAt;

    /** Card-data lookups hint text needs; the screen swaps in the card-database backed one. */
    public HintText hintText = HintText.CODES_ONLY;

    /** The three lookups {@link #applyHint} needs, so this class stays free of the client's card data. */
    public interface HintText {
        /** An engine description code as text ({@code OptionTextResolver}). */
        String desc(long desc);
        /** A card code as its printed name. */
        String cardName(int code);
        /** A {@code strings.conf} {@code !system} entry, or null when it is unknown. */
        String systemString(int code);

        /** The fallback before the screen injects its own: codes, unresolved. */
        HintText CODES_ONLY = new HintText() {
            @Override public String desc(long desc) { return "#" + desc; }
            @Override public String cardName(int code) { return String.valueOf(code); }
            @Override public String systemString(int code) { return null; }
        };
    }

    // Confirm/reveal card display
    public String confirmTitle;
    public List<DuelMessage.ConfirmCard> confirmCards;

    // RPS result display
    public int rpsHand0;
    public int rpsHand1;

    // Winner (-1 = ongoing, 2 = draw)
    public int winner = -1;
    public int winReason;

    /** Records the duel result, from MSG_WIN or a host-synthesised DuelEndPayload. */
    public void applyResult(int winner, int reason) {
        this.winner = winner;
        this.winReason = reason;
        dirtyFlags.add(DirtyFlag.WINNER);
        LOGGER.info("[State] Result: winner={}, reason={}", winner, reason);
    }

    public ClientDuelState(int localPlayer, String opponentName, int lp0, int lp1,
                           int deckSize, int extraSize, long duelFlags) {
        this.localPlayer = localPlayer;
        this.opponentName = opponentName;
        this.duelFlags = duelFlags;
        this.lp[0] = lp0;
        this.lp[1] = lp1;
        // Decks and extra decks start as blank face-down cards: codes arrive later (reveals, queries).
        for (int p = 0; p < 2; p++) {
            fillBlanks(deck[p], p, LOCATION_DECK, deckSize);
            fillBlanks(extra[p], p, LOCATION_EXTRA, extraSize);
        }
        LOGGER.debug("[State] Init: localPlayer={}, LP={}|{}, deck={}|{}, extra={}|{}",
                localPlayer, lp[0], lp[1], deckCount(0), deckCount(1), extraCount(0), extraCount(1));
    }

    /**
     * Apply a duel message to update state.
     */
    public void applyMessage(DuelMessage msg) {
        lastAction = msg;

        switch (msg) {
            // ---- System ----
            case DuelMessage.Retry ignored -> {
                responseSent = false;
                pendingPrompt = lastPrompt;
                if (pendingPrompt instanceof DuelMessage.SelectIdleCmd sel) buildIdleCmdActions(sel);
                else if (pendingPrompt instanceof DuelMessage.SelectBattleCmd sel) buildBattleCmdActions(sel);
                retryMessage = "Invalid response, try again";
                dirtyFlags.add(DirtyFlag.PROMPT);
                LOGGER.warn("[State] RETRY — last response was invalid, re-prompting");
            }

            // ---- UI/Info ----
            case DuelMessage.Hint hint -> applyHint(hint);
            case DuelMessage.CardHint hint -> applyCardHint(hint);

            // ---- Lifecycle ----
            case DuelMessage.Start start -> {
                lp[0] = start.lp0();
                lp[1] = start.lp1();
                startingLP = Math.max(lp[0], lp[1]);
                // Reset the deck and extra deck lists to the announced sizes (codes still unknown).
                deck[0].clear(); deck[1].clear();
                extra[0].clear(); extra[1].clear();
                fillBlanks(deck[0], 0, LOCATION_DECK, start.deckCount0());
                fillBlanks(deck[1], 1, LOCATION_DECK, start.deckCount1());
                fillBlanks(extra[0], 0, LOCATION_EXTRA, start.extraCount0());
                fillBlanks(extra[1], 1, LOCATION_EXTRA, start.extraCount1());
                dirtyFlags.add(DirtyFlag.PILE_COUNTS);
                LOGGER.debug("[State] Start: LP={}|{}, Deck={}|{}, Extra={}|{}",
                        lp[0], lp[1], deckCount(0), deckCount(1), extraCount(0), extraCount(1));
            }
            case DuelMessage.Win win -> applyResult(win.winner(), win.reason());
            case DuelMessage.NewTurn nt -> {
                currentTurn = nt.player();
                turnCount++;
                dirtyFlags.add(DirtyFlag.TURN_PHASE);
                LOGGER.debug("[State] NewTurn: player={}, turnCount={}", currentTurn, turnCount);
            }
            case DuelMessage.NewPhase np -> {
                currentPhase = np.phase();
                dirtyFlags.add(DirtyFlag.TURN_PHASE);
                LOGGER.debug("[State] NewPhase: {}", phaseName());
            }

            // ---- Card Movement ----
            case DuelMessage.Draw draw -> {
                int p = draw.player();
                for (var drawn : draw.cards()) {
                    // edopro writes the code onto the deck-top object and then moves it to the hand.
                    ClientCard card = deck[p].isEmpty()
                            ? new ClientCard(0, p, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE)
                            : deck[p].removeLast();
                    card.code = drawn.code();
                    putAt(card, p, LOCATION_HAND, hand[p].size());
                }
                renumber(deck[p]);
                dirtyFlags.add(handFlag(p));
                dirtyFlags.add(DirtyFlag.PILE_COUNTS);
                LOGGER.debug("[State] Draw: player={}, cards={}", p, draw.cards());
            }
            case DuelMessage.Move move -> {
                LOGGER.debug("[State] Move: code={}, from=[p{} loc=0x{} seq={}] to=[p{} loc=0x{} seq={} pos=0x{}], reason=0x{}",
                        move.code(),
                        move.from().controller(), Integer.toHexString(move.from().location()), move.from().sequence(),
                        move.to().controller(), Integer.toHexString(move.to().location()), move.to().sequence(),
                        Integer.toHexString(move.to().position()), Integer.toHexString(move.reason()));
                applyMove(move);
                markZoneDirty(move.from().controller(), move.from().location());
                markZoneDirty(move.to().controller(), move.to().location());
            }
            case DuelMessage.PosChange pc -> {
                ClientCard card = cardAt(pc.controller(), pc.location(), pc.sequence());
                if (card != null) {
                    if ((pc.prevPosition() & POS_FACEUP) != 0 && (pc.newPosition() & POS_FACEDOWN) != 0) {
                        card.counters.clear();
                        clearTargets(card);
                    }
                    if (pc.code() != 0) card.code = pc.code();
                    card.position = pc.newPosition();
                }
                markZoneDirty(pc.controller(), pc.location());
            }
            case DuelMessage.Set set -> {
                // MSG_MOVE already placed the card (operations.cpp emits the move first);
                // MSG_SET only confirms the code and the face-down position.
                LOGGER.debug("[State] Set: code={}, loc=[p{} loc=0x{} seq={}]",
                        set.code(), set.location().controller(),
                        Integer.toHexString(set.location().location()), set.location().sequence());
                var loc = set.location();
                ClientCard card = cardAt(loc.controller(), loc.location(), loc.sequence());
                if (card == null) {
                    place(new ClientCard(set.code(), loc.controller(), loc.location(),
                            loc.sequence(), loc.position()), loc);
                } else {
                    if (set.code() != 0) card.code = set.code();
                    card.position = loc.position();
                }
                markZoneDirty(loc.controller(), loc.location());
            }
            case DuelMessage.Swap swap -> {
                swapCards(swap.loc1(), swap.loc2());
                markZoneDirty(swap.loc1().controller(), swap.loc1().location());
                markZoneDirty(swap.loc2().controller(), swap.loc2().location());
            }

            // ---- Summons ----
            case DuelMessage.Summoning ignored -> { } // card appears via MSG_MOVE
            case DuelMessage.SpSummoning ignored -> { }
            case DuelMessage.FlipSummoning fs -> {
                var loc = fs.location();
                ClientCard card = cardAt(loc.controller(), loc.location(), loc.sequence());
                if (card != null) {
                    if (fs.code() != 0) card.code = fs.code();
                    card.position = loc.position();
                    markZoneDirty(loc.controller(), loc.location());
                }
            }
            case DuelMessage.Summoned ignored -> { }
            case DuelMessage.SpSummoned ignored -> { }
            case DuelMessage.FlipSummoned ignored -> { }

            // ---- Chain ----
            case DuelMessage.Chaining c -> {
                    chain.add(new ChainLink(c.code(), c.location(), c.chainCount()));
                    dirtyFlags.add(DirtyFlag.CHAIN);
            }
            case DuelMessage.ChainEnd ignored -> {
                chain.clear();
                dirtyFlags.add(DirtyFlag.CHAIN);
            }
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

            // ---- Field stat updates ----
            case DuelMessage.UpdateData upd -> {
                var cards = upd.cards();
                if (upd.location() == LOCATION_MZONE || upd.location() == LOCATION_SZONE) {
                    for (int i = 0; i < cards.size(); i++) {
                        applyQuery(cardAt(upd.player(), upd.location(), i), cards.get(i));
                    }
                } else {
                    var list = pile(upd.player(), upd.location());
                    if (list != null) {
                        resize(list, upd.player(), upd.location(), cards.size());
                        for (int i = 0; i < cards.size(); i++) applyQuery(list.get(i), cards.get(i));
                        markZoneDirty(upd.player(), upd.location());
                    }
                }
                dirtyFlags.add(DirtyFlag.FIELD_STATS);
            }
            case DuelMessage.UpdateCard upd -> {
                applyQuery(cardAt(upd.player(), upd.location(), upd.sequence()), upd.card());
                dirtyFlags.add(DirtyFlag.FIELD_STATS);
            }

            // ---- Deck/Hand ----
            case DuelMessage.ShuffleDeck sd -> {
                // edopro takes back every reveal a shuffle invalidates (duelclient.cpp:2691).
                for (var card : deck[sd.player()]) {
                    card.code = 0;
                    card.position = POS_FACEDOWN_DEFENSE;
                }
                dirtyFlags.add(DirtyFlag.PILE_COUNTS);
            }
            case DuelMessage.ReverseDeck ignored -> {
                deckReversed = !deckReversed;
                dirtyFlags.add(DirtyFlag.PILE_COUNTS);
                LOGGER.debug("[State] ReverseDeck: reversed={}", deckReversed);
            }
            case DuelMessage.DeckTop top -> {
                // duelclient.cpp:2862 counts from the top of the pile, which is the end of the list.
                int index = deck[top.player()].size() - 1 - top.offsetFromTop();
                if (index < 0 || index >= deck[top.player()].size()) {
                    LOGGER.warn("[State] DeckTop offset {} outside p{}'s deck of {}",
                            top.offsetFromTop(), top.player(), deckCount(top.player()));
                } else {
                    ClientCard card = deck[top.player()].get(index);
                    card.code = top.code();
                    card.position = top.position();
                    dirtyFlags.add(DirtyFlag.PILE_COUNTS);
                }
            }
            case DuelMessage.ShuffleHand sh -> {
                // edopro re-codes the existing hand objects rather than replacing them.
                var list = hand[sh.player()];
                resize(list, sh.player(), LOCATION_HAND, sh.codes().size());
                for (int i = 0; i < list.size(); i++) list.get(i).code = sh.codes().get(i);
                dirtyFlags.add(handFlag(sh.player()));
            }
            case DuelMessage.ShuffleExtra ignored -> { }
            case DuelMessage.SwapGraveDeck swap -> {
                applySwapGraveDeck(swap);
                dirtyFlags.add(DirtyFlag.PILE_COUNTS);
                LOGGER.debug("[State] SwapGraveDeck: player={}, deck={}, grave={}, extra={}",
                        swap.player(), deckCount(swap.player()), graveCount(swap.player()),
                        extraCount(swap.player()));
            }
            case DuelMessage.ShuffleSetCard set -> applyShuffleSetCard(set);
            case DuelMessage.RemoveCards remove -> applyRemoveCards(remove.cards());

            // ---- Battle (no state change, UI can animate from lastAction) ----
            case DuelMessage.Attack ignored -> { }
            case DuelMessage.Battle ignored -> { }
            case DuelMessage.AttackDisabled ignored -> { }
            case DuelMessage.DamageStepStart ignored -> { }
            case DuelMessage.DamageStepEnd ignored -> { }

            // ---- Selection prompts — set pendingPrompt for the UI ----
            case DuelMessage.SelectIdleCmd sel -> {
                setPrompt(msg);
                buildIdleCmdActions(sel);
                dirtyFlags.add(DirtyFlag.PROMPT);
                LOGGER.info("[State] SelectIdleCmd: summon={}, spSummon={}, repos={}, setMon={}, setST={}, activate={}, battle={}, end={}",
                        sel.summonable().size(), sel.specialSummonable().size(),
                        sel.repositionable().size(), sel.settableMonsters().size(),
                        sel.settableSpells().size(), sel.activatable().size(),
                        sel.canBattle(), sel.canEnd());
                for (var a : sel.activatable()) {
                    LOGGER.info("[State]   activatable: code={}, loc=0x{}, seq={}, desc={}, flag={}",
                            a.code(), Integer.toHexString(a.location()), a.sequence(), a.desc(), a.flag());
                }
            }
            case DuelMessage.SelectBattleCmd sel -> {
                setPrompt(msg);
                buildBattleCmdActions(sel);
                dirtyFlags.add(DirtyFlag.PROMPT);
                LOGGER.debug("[State] Prompt: SelectBattleCmd player={}, actions={}", sel.player(), cardActions.size());
            }
            case DuelMessage.SelectCard sel -> {
                setPrompt(msg);
                dirtyFlags.add(DirtyFlag.PROMPT);
                LOGGER.info("[State] Prompt: SelectCard player={}, min={}, max={}, cards={}",
                        sel.player(), sel.min(), sel.max(), sel.cards().size());
                for (var c : sel.cards()) {
                    LOGGER.info("[State]   card: code={} ctrl={} loc=0x{} seq={} pos=0x{}",
                            c.code(), c.controller(), Integer.toHexString(c.location()),
                            c.sequence(), Integer.toHexString(c.position()));
                }
            }
            case DuelMessage.SelectChain sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectChain player={}, count={}, forced={}", sel.player(), sel.count(), sel.forced()); }
            case DuelMessage.SelectEffectYn sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectEffectYn player={}, code={}", sel.player(), sel.code()); }
            case DuelMessage.SelectYesNo sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectYesNo player={}, desc={}", sel.player(), sel.desc()); }
            case DuelMessage.SelectOption sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectOption player={}, options={}", sel.player(), sel.options()); }
            case DuelMessage.SelectPlace sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectPlace player={}, count={}, field=0x{}", sel.player(), sel.count(), Integer.toHexString(sel.field())); }
            case DuelMessage.SelectDisfield sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectDisfield player={}, count={}, field=0x{}", sel.player(), sel.count(), Integer.toHexString(sel.field())); }
            case DuelMessage.SelectPosition sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectPosition player={}, code={}, pos=0x{}", sel.player(), sel.code(), Integer.toHexString(sel.positions())); }
            case DuelMessage.SelectTribute sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectTribute player={}, min={}, max={}", sel.player(), sel.min(), sel.max()); }
            case DuelMessage.SelectCounter sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectCounter player={}", sel.player()); }
            case DuelMessage.SelectSum sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SelectSum player={}", sel.player()); }
            case DuelMessage.SelectUnselectCard sel -> {
                setPrompt(msg);
                dirtyFlags.add(DirtyFlag.PROMPT);
                LOGGER.info("[State] Prompt: SelectUnselectCard player={} hint=0x{} selectable={} alreadySelected={}",
                        sel.player(), Integer.toHexString(lastHintType),
                        sel.selectableCards().size(), sel.unselectableCards().size());
                for (var c : sel.selectableCards()) {
                    LOGGER.info("[State]   selectable: code={} ctrl={} loc=0x{} seq={}",
                            c.code(), c.controller(), Integer.toHexString(c.location()), c.sequence());
                }
                for (var c : sel.unselectableCards()) {
                    LOGGER.info("[State]   alreadySelected: code={} ctrl={} loc=0x{} seq={}",
                            c.code(), c.controller(), Integer.toHexString(c.location()), c.sequence());
                }
            }
            case DuelMessage.SortCard sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SortCard player={}", sel.player()); }
            case DuelMessage.SortChain sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: SortChain player={}", sel.player()); }
            case DuelMessage.AnnounceRace sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: AnnounceRace player={}", sel.player()); }
            case DuelMessage.AnnounceAttrib sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: AnnounceAttrib player={}", sel.player()); }
            case DuelMessage.AnnounceNumber sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: AnnounceNumber player={}", sel.player()); }
            case DuelMessage.AnnounceCard sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: AnnounceCard player={}", sel.player()); }
            case DuelMessage.RockPaperScissors sel -> { setPrompt(msg); dirtyFlags.add(DirtyFlag.PROMPT); LOGGER.debug("[State] Prompt: RockPaperScissors player={}", sel.player()); }

            // ---- Confirm/reveal ----
            case DuelMessage.ConfirmDeckTop confirm -> {
                revealPileTop(deck[confirm.player()], confirm.cards());
                reveal(owner(confirm.player()) + "Deck (top)", confirm.cards());
                LOGGER.debug("[State] ConfirmDeckTop: player={}, cards={}", confirm.player(), confirm.cards().size());
            }
            case DuelMessage.ConfirmExtraTop confirm -> {
                revealPileTop(extra[confirm.player()], confirm.cards());
                reveal(owner(confirm.player()) + "Extra Deck (top)", confirm.cards());
                LOGGER.debug("[State] ConfirmExtraTop: player={}, cards={}", confirm.player(), confirm.cards().size());
            }
            case DuelMessage.ConfirmCards confirm -> {
                int shown = confirm.cards().isEmpty() ? confirm.player() : confirm.cards().getFirst().controller();
                reveal(owner(shown) + "Revealed Cards", confirm.cards());
                LOGGER.debug("[State] ConfirmCards: player={}, cards={}", confirm.player(), confirm.cards().size());
            }
            case DuelMessage.CardSelected sel -> {
                LOGGER.info("[State] CardSelected: count={}", sel.cards().size());
                highlight(sel.cards());
            }
            case DuelMessage.BecomeTarget bt -> {
                LOGGER.debug("[State] BecomeTarget: count={}", bt.targets().size());
                highlight(bt.targets());
            }
            case DuelMessage.RandomSelected rs -> {
                LOGGER.info("[State] RandomSelected: player={}, count={}", rs.player(), rs.cards().size());
                highlight(rs.cards());
            }
            case DuelMessage.MissedEffect missed -> {
                LOGGER.info("[State] MissedEffect: code={} at p{} loc=0x{} seq={}",
                        missed.code(), missed.location().controller(),
                        Integer.toHexString(missed.location().location()), missed.location().sequence());
                highlight(List.of(missed.location()));
            }

            // ---- Relationships, counters, disabled zones ----
            case DuelMessage.FieldDisabled fd -> {
                disabledZones[0] = fd.field() & 0xFFFF;
                disabledZones[1] = (fd.field() >>> 16) & 0xFFFF;
                dirtyFlags.addAll(EnumSet.of(DirtyFlag.MZONE_0, DirtyFlag.MZONE_1,
                        DirtyFlag.SZONE_0, DirtyFlag.SZONE_1));
                LOGGER.debug("[State] FieldDisabled: p0=0x{}, p1=0x{}",
                        Integer.toHexString(disabledZones[0]), Integer.toHexString(disabledZones[1]));
            }
            case DuelMessage.AddCounter add -> {
                ClientCard card = cardAt(add.controller(), add.location(), add.sequence());
                if (card != null) {
                    card.counters.merge(add.counterType(), add.count(), Integer::sum);
                    markZoneDirty(add.controller(), add.location());
                }
            }
            case DuelMessage.RemoveCounter rem -> {
                ClientCard card = cardAt(rem.controller(), rem.location(), rem.sequence());
                if (card != null) {
                    int left = card.counters.getOrDefault(rem.counterType(), 0) - rem.count();
                    if (left > 0) card.counters.put(rem.counterType(), left);
                    else card.counters.remove(rem.counterType());
                    markZoneDirty(rem.controller(), rem.location());
                }
            }
            case DuelMessage.Equip eq -> {
                ClientCard card = resolveCard(eq.card());
                ClientCard target = resolveCard(eq.target());
                if (card != null && target != null) {
                    detachEquipTarget(card);
                    card.equipTarget = target;
                    target.equippedBy.add(card);
                }
            }
            case DuelMessage.Unequip uneq -> {
                ClientCard card = resolveCard(uneq.card());
                if (card != null) detachEquipTarget(card);
            }
            case DuelMessage.CardTarget ct -> {
                ClientCard card = resolveCard(ct.card());
                ClientCard target = resolveCard(ct.target());
                if (card != null && target != null) {
                    card.targets.add(target);
                    target.targetedBy.add(card);
                    markZoneDirty(ct.target().controller(), ct.target().location());
                }
            }
            case DuelMessage.CancelTarget ct -> {
                ClientCard card = resolveCard(ct.card());
                ClientCard target = resolveCard(ct.target());
                if (card != null && target != null) {
                    card.targets.remove(target);
                    target.targetedBy.remove(card);
                    markZoneDirty(ct.target().controller(), ct.target().location());
                }
            }
            // ---- Player-level hints and match bookkeeping (no UI yet) ----
            case DuelMessage.PlayerHint hint -> {
                var hints = playerHints[hint.player()];
                if (hint.hintType() == PHINT_DESC_ADD) {
                    hints.merge(hint.desc(), 1, Integer::sum);
                } else if (hint.hintType() == PHINT_DESC_REMOVE) {
                    int left = hints.getOrDefault(hint.desc(), 0) - 1;
                    if (left > 0) hints.put(hint.desc(), left);
                    else hints.remove(hint.desc());
                }
            }
            case DuelMessage.MatchKill mk -> {
                matchKillCode = mk.code();
                LOGGER.info("[State] MatchKill: code={}", mk.code());
            }

            case DuelMessage.HandResult res -> {
                rpsHand0 = res.hand0();
                rpsHand1 = res.hand1();
                dirtyFlags.add(DirtyFlag.CHAIN);
                LOGGER.debug("[State] HandResult: hand0={}, hand1={}", res.hand0(), res.hand1());
            }

            // ---- Everything else ----
            default -> LOGGER.debug("[State] Unhandled: {} (type={})", msg.getClass().getSimpleName(), msg.type());
        }
    }

    // ---- Dirty flag helpers ----

    /** Marks the container a card sits in; overlay locations resolve to their host zone. */
    private void markZoneDirty(int controller, int location) {
        switch (location & ~LOCATION_OVERLAY) {
            case LOCATION_HAND -> dirtyFlags.add(handFlag(controller));
            case LOCATION_MZONE -> dirtyFlags.add(mzoneFlag(controller));
            case LOCATION_SZONE -> dirtyFlags.add(szoneFlag(controller));
            case LOCATION_DECK, LOCATION_EXTRA, LOCATION_GRAVE, LOCATION_REMOVED ->
                    dirtyFlags.add(DirtyFlag.PILE_COUNTS);
            default -> { }
        }
    }

    // ---- Hints (edopro duelclient.cpp MSG_HINT :1390, MSG_CARD_HINT :3964) ----

    private void applyHint(DuelMessage.Hint hint) {
        lastHintType = hint.hintType();
        lastHintData = hint.data();
        switch (hint.hintType()) {
            case HINT_SELECTMSG -> selectHint = hint.data();
            case HINT_MESSAGE -> pendingModal = hintText.desc(hint.data());
            case HINT_OPSELECTED, HINT_RACE, HINT_ATTRIB, HINT_CODE, HINT_NUMBER ->
                    toasts.add(toastText(hint));
            case HINT_CARD -> revealCardCode = (int) hint.data();
            case HINT_ZONE -> {
                zoneFlashMask = viewerZoneMask((int) hint.data(), hint.player());
                zoneFlashAt = System.currentTimeMillis();
            }
            // HINT_EVENT and HINT_EFFECT drive edopro's log and card-reveal animation; HINT_SKILL
            // and above belong to Speed Duel skills, which this mod does not run.
            default -> LOGGER.debug("[State] Hint: type={}, player={}, data={}",
                    hint.hintType(), hint.player(), hint.data());
        }
    }

    /** The toast line for a declaration hint, in edopro's system-string templates (`:1419-1462`). */
    private String toastText(DuelMessage.Hint hint) {
        return switch (hint.hintType()) {
            case HINT_OPSELECTED ->
                    template(hint.player() == localPlayer ? 1510 : 1512, hintText.desc(hint.data()));
            case HINT_RACE -> template(1511, CardStringHelper.raceName(hint.data()));
            case HINT_ATTRIB -> template(1511, CardStringHelper.attributeName((int) hint.data()));
            case HINT_CODE -> template(1511, hintText.cardName((int) hint.data()));
            default -> template(1512, String.valueOf(hint.data()));   // HINT_NUMBER
        };
    }

    /** A {@code strings.conf} template with its single {@code {}} filled in, or a built-in copy. */
    private String template(int systemString, String value) {
        String text = hintText.systemString(systemString);
        if (text == null) {
            text = switch (systemString) {
                case 1510 -> "Your choice: [{}]";
                case 1511 -> "Opponent declared: [{}]";
                default -> "Your opponent's choice: [{}]";
            };
        }
        return text.replace("{}", value);
    }

    /**
     * HINT_ZONE masks name zones from the hinted player's side, so a hint about the other side has
     * its two halves swapped to land in the viewer's own layout ({@code duelclient.cpp:1481}).
     */
    private int viewerZoneMask(int field, int player) {
        return player == localPlayer ? field : (field >>> 16) | (field << 16);
    }

    private void applyCardHint(DuelMessage.CardHint hint) {
        var loc = hint.location();
        ClientCard card = cardAt(loc.controller(), loc.location(), loc.sequence());
        if (card == null) return;   // edopro drops a hint whose card it cannot find (`:3969`)
        if (hint.chintType() == CHINT_DESC_ADD) {
            card.descHints.merge(hint.value(), 1, Integer::sum);
        } else if (hint.chintType() == CHINT_DESC_REMOVE) {
            int left = card.descHints.getOrDefault(hint.value(), 0) - 1;
            if (left > 0) card.descHints.put(hint.value(), left);
            else card.descHints.remove(hint.value());
        } else {
            card.hintType = hint.chintType();
            card.hintValue = hint.value();
        }
        markZoneDirty(loc.controller(), loc.location());
    }

    // ---- Reveals ----

    /** "Your " or "Opponent's ", so a reveal panel says whose pile it is showing. */
    private String owner(int player) {
        return player == localPlayer ? "Your " : "Opponent's ";
    }

    private void reveal(String title, List<DuelMessage.ConfirmCard> cards) {
        confirmTitle = title;
        confirmCards = cards;
        dirtyFlags.add(DirtyFlag.CONFIRM);
    }

    /**
     * edopro writes a deck-top or extra-top reveal onto the pile's own card objects, so the codes
     * survive until a shuffle takes them back (duelclient.cpp:2513, :2548). The reveal counts down
     * from the top of the pile, which is the end of the list.
     */
    private void revealPileTop(List<ClientCard> pile, List<DuelMessage.ConfirmCard> cards) {
        for (int i = 0; i < cards.size(); i++) {
            int index = pile.size() - 1 - i;
            if (index < 0) return;
            if (cards.get(i).code() != 0) pile.get(index).code = cards.get(i).code();
        }
        dirtyFlags.add(DirtyFlag.PILE_COUNTS);
    }

    // ---- Card containers ----

    private static void fillBlanks(List<ClientCard> list, int player, int location, int count) {
        for (int i = 0; i < count; i++) {
            list.add(new ClientCard(0, player, location, list.size(), POS_FACEDOWN_DEFENSE));
        }
    }

    /** Grows a pile with blank cards or drops the tail so it matches a queried size. */
    private static void resize(List<ClientCard> list, int player, int location, int size) {
        while (list.size() > size) list.removeLast();
        fillBlanks(list, player, location, size - list.size());
    }

    private static void renumber(List<ClientCard> list) {
        for (int i = 0; i < list.size(); i++) list.get(i).sequence = i;
    }

    /** Puts a card into a container without touching its position (used by Draw and Swap). */
    private void putAt(ClientCard card, int controller, int location, int sequence) {
        card.controller = controller;
        card.location = location;
        card.sequence = sequence;
        if (location == LOCATION_MZONE) {
            mzone[controller][sequence] = card;
            return;
        }
        if (location == LOCATION_SZONE) {
            szone[controller][sequence] = card;
            return;
        }
        var list = pile(controller, location);
        if (list == null) return;
        list.add(Math.min(Math.max(sequence, 0), list.size()), card);
        renumber(list);
    }

    private void place(ClientCard card, LocInfo to) {
        card.position = to.position();
        putAt(card, to.controller(), to.location(), to.sequence());
    }

    /** Takes a card out of the container named by {@code from}, renumbering what is left behind. */
    private void removeFrom(ClientCard card, LocInfo from) {
        if ((from.location() & LOCATION_OVERLAY) != 0) {
            detachMaterial(card, from);
            return;
        }
        if (from.location() == LOCATION_MZONE) {
            clearSlot(mzone[from.controller()], card, from.sequence());
            return;
        }
        if (from.location() == LOCATION_SZONE) {
            clearSlot(szone[from.controller()], card, from.sequence());
            return;
        }
        var list = pile(from.controller(), from.location());
        if (list != null && list.remove(card)) renumber(list);
    }

    private static void clearSlot(ClientCard[] zones, ClientCard card, int sequence) {
        if (sequence >= 0 && sequence < zones.length && zones[sequence] == card) {
            zones[sequence] = null;
            return;
        }
        for (int i = 0; i < zones.length; i++) {
            if (zones[i] == card) zones[i] = null;
        }
    }

    /**
     * The card a message points at. A material is addressed as
     * {@code {host controller, host location | LOCATION_OVERLAY, host sequence, material index}}
     * (`card::get_info_location()`), so `0x84` means "material of the monster zone card".
     */
    private ClientCard resolveCard(LocInfo loc) {
        if (loc.location() == 0) return null;
        if ((loc.location() & LOCATION_OVERLAY) != 0) {
            ClientCard host = cardAt(loc.controller(), loc.location() & ~LOCATION_OVERLAY, loc.sequence());
            if (host == null) return null;
            return loc.position() >= 0 && loc.position() < host.materials.size()
                    ? host.materials.get(loc.position()) : null;
        }
        ClientCard card = cardAt(loc.controller(), loc.location(), loc.sequence());
        if (card != null) return card;
        // Out-of-range sequence into a pile: fall back to the tail so the count stays honest.
        var list = pile(loc.controller(), loc.location());
        if (list != null && !list.isEmpty()) {
            LOGGER.warn("[State] No card at p{} loc=0x{} seq={}; using the last one",
                    loc.controller(), Integer.toHexString(loc.location()), loc.sequence());
            return list.getLast();
        }
        LOGGER.warn("[State] No card at p{} loc=0x{} seq={}",
                loc.controller(), Integer.toHexString(loc.location()), loc.sequence());
        return null;
    }

    // ---- Move handling (edopro duelclient.cpp MSG_MOVE, :3044-3215) ----

    private void applyMove(DuelMessage.Move move) {
        LocInfo from = move.from();
        LocInfo to = move.to();
        ClientCard card = resolveCard(from);

        if (card == null) {
            // A token appearing, or a card this client never saw. Nowhere to go means nothing to do.
            if (to.location() == 0) return;
            ClientCard appeared = new ClientCard(move.code(), to.controller(),
                    to.location(), to.sequence(), to.position());
            if ((to.location() & LOCATION_OVERLAY) != 0) attachMaterial(appeared, to);
            else place(appeared, to);
            return;
        }
        if (to.location() == 0) {
            // Leaves play entirely (a token vanishing): drop every link this card was part of.
            if (move.code() != 0) card.code = move.code();
            clearTargets(card);
            detachEquips(card);
            removeFrom(card, from);
            return;
        }

        boolean fromOverlay = (from.location() & LOCATION_OVERLAY) != 0;
        boolean toOverlay = (to.location() & LOCATION_OVERLAY) != 0;

        if (!fromOverlay && !toOverlay) {
            if (move.code() != 0 || to.location() == LOCATION_EXTRA) card.code = move.code();
            if ((from.location() & LOCATION_ONFIELD) != 0 && to.location() != from.location()) {
                card.counters.clear();
            }
            if (to.location() != from.location()) {
                clearTargets(card);
                detachEquipTarget(card);
            }
            removeFrom(card, from);
            place(card, to);
        } else if (!fromOverlay) {
            // Attach: the card becomes an XYZ material of the host named by `to`.
            if (move.code() != 0) card.code = move.code();
            card.counters.clear();
            clearTargets(card);
            removeFrom(card, from);
            attachMaterial(card, to);
        } else if (!toOverlay) {
            detachMaterial(card, from);
            place(card, to);
        } else {
            detachMaterial(card, from);
            attachMaterial(card, to);
        }
    }

    private void attachMaterial(ClientCard card, LocInfo to) {
        ClientCard host = cardAt(to.controller(), to.location() & ~LOCATION_OVERLAY, to.sequence());
        if (host == null) {
            LOGGER.warn("[State] Overlay attach with no host at p{} loc=0x{} seq={}",
                    to.controller(), Integer.toHexString(to.location()), to.sequence());
            return;
        }
        host.materials.add(card);
        card.controller = to.controller();
        card.location = LOCATION_OVERLAY;
        card.sequence = host.materials.size() - 1;
    }

    private void detachMaterial(ClientCard card, LocInfo from) {
        ClientCard host = cardAt(from.controller(), from.location() & ~LOCATION_OVERLAY, from.sequence());
        if (host == null) return;
        if (host.materials.remove(card)) renumber(host.materials);
    }

    // ---- Pile restructuring (edopro duelclient.cpp :2811, :2881, :4017) ----

    /**
     * The graveyard and the deck trade places, then every card the bitmask flags leaves the new
     * deck for the extra deck, face-down ({@code duelclient.cpp:2811}).
     */
    private void applySwapGraveDeck(DuelMessage.SwapGraveDeck swap) {
        int p = swap.player();
        var wasGrave = List.copyOf(grave[p]);
        grave[p].clear();
        grave[p].addAll(deck[p]);
        deck[p].clear();
        deck[p].addAll(wasGrave);

        for (var card : grave[p]) card.location = LOCATION_GRAVE;
        renumber(grave[p]);

        int index = 0;
        for (var it = deck[p].iterator(); it.hasNext(); index++) {
            ClientCard card = it.next();
            if (maskBit(swap.extraMask(), index)) {
                it.remove();
                card.position = POS_FACEDOWN_DEFENSE;
                putAt(card, p, LOCATION_EXTRA, extra[p].size());
            } else {
                card.location = LOCATION_DECK;
            }
        }
        renumber(deck[p]);
    }

    private static boolean inRange(int sequence, ClientCard[] zone) {
        return sequence >= 0 && sequence < zone.length;
    }

    /** Bit {@code index} of the engine's {@code ProgressiveBuffer}: byte {@code index/8}, bit {@code index%8}. */
    private static boolean maskBit(byte[] mask, int index) {
        int b = index / 8;
        return b < mask.length && (mask[b] & (1 << (index % 8))) != 0;
    }

    /**
     * Every named card loses its code; the second block re-places the ones carrying XYZ materials,
     * swapping each with whatever sits in its new zone ({@code duelclient.cpp:2881}).
     */
    private void applyShuffleSetCard(DuelMessage.ShuffleSetCard set) {
        ClientCard[][] zones = set.location() == LOCATION_MZONE ? mzone : szone;
        var shuffled = new ArrayList<ClientCard>(set.from().size());
        for (var from : set.from()) {
            ClientCard card = cardAt(from.controller(), set.location(), from.sequence());
            shuffled.add(card);
            if (card != null) card.code = 0;
            markZoneDirty(from.controller(), set.location());
        }
        for (int i = 0; i < set.follow().size() && i < shuffled.size(); i++) {
            LocInfo to = set.follow().get(i);
            ClientCard card = shuffled.get(i);
            if (card == null || to.location() == 0) continue;
            ClientCard[] zone = zones[to.controller()];
            int previous = card.sequence;
            if (!inRange(to.sequence(), zone) || !inRange(previous, zone)) continue;
            ClientCard displaced = zone[to.sequence()];
            zone[previous] = displaced;
            zone[to.sequence()] = card;
            card.sequence = to.sequence();
            if (displaced != null) displaced.sequence = previous;
            markZoneDirty(to.controller(), set.location());
        }
    }

    /** Every named card is resolved before any is deleted, so removals do not shift the sequences. */
    private void applyRemoveCards(List<LocInfo> locations) {
        var doomed = new ArrayList<ClientCard>(locations.size());
        for (var loc : locations) doomed.add(resolveCard(loc));
        for (int i = 0; i < doomed.size(); i++) {
            ClientCard card = doomed.get(i);
            if (card == null) continue;
            LocInfo loc = locations.get(i);
            clearTargets(card);
            detachEquips(card);
            removeFrom(card, loc);
            highlighted.remove(card);
            markZoneDirty(loc.controller(), loc.location());
        }
    }

    private void swapCards(LocInfo loc1, LocInfo loc2) {
        ClientCard card1 = resolveCard(loc1);
        ClientCard card2 = resolveCard(loc2);
        if (card1 != null) removeFrom(card1, loc1);
        if (card2 != null) removeFrom(card2, loc2);
        if (card1 != null) putAt(card1, loc2.controller(), loc2.location(), loc2.sequence());
        if (card2 != null) putAt(card2, loc1.controller(), loc1.location(), loc1.sequence());
    }

    // ---- Links, highlights, queries ----

    /** edopro's {@code ClientCard::ClearTarget()}: drops this card from both ends of every link. */
    private static void clearTargets(ClientCard card) {
        for (var target : card.targets) target.targetedBy.remove(card);
        for (var source : card.targetedBy) source.targets.remove(card);
        card.targets.clear();
        card.targetedBy.clear();
    }

    private static void detachEquipTarget(ClientCard card) {
        if (card.equipTarget == null) return;
        card.equipTarget.equippedBy.remove(card);
        card.equipTarget = null;
    }

    /** Detaches both directions, for a card that leaves play altogether. */
    private static void detachEquips(ClientCard card) {
        detachEquipTarget(card);
        for (var equipped : card.equippedBy) equipped.equipTarget = null;
        card.equippedBy.clear();
    }

    private void highlight(List<LocInfo> locations) {
        for (var loc : locations) {
            ClientCard card = resolveCard(loc);
            if (card == null) continue;
            highlighted.add(card);
            markZoneDirty(loc.controller(), loc.location());
        }
    }

    /**
     * edopro's {@code highlighting_card} (`duelclient.cpp:1944-1948`): tints the card a
     * {@code SELECT_EFFECTYN} prompt is asking about. Cleared with every other highlight
     * when the next prompt arrives.
     */
    public void highlightPromptCard(LocInfo loc) {
        highlight(List.of(loc));
    }

    private void clearHighlights() {
        if (highlighted.isEmpty()) return;
        highlighted.clear();
        dirtyFlags.addAll(EnumSet.of(DirtyFlag.MZONE_0, DirtyFlag.MZONE_1,
                DirtyFlag.SZONE_0, DirtyFlag.SZONE_1));
    }

    /** Writes a query result onto a card, self-healing code and position when they are carried. */
    private void applyQuery(ClientCard card, QueriedCard query) {
        if (card == null || query == null) return;
        QueriedCard previous = card.stats;
        card.stats = query;
        boolean visualChanged = false;
        if ((query.flags & (QUERY_LSCALE | QUERY_RSCALE)) != 0 && (previous == null
                || previous.lscale != query.lscale || previous.rscale != query.rscale)) {
            visualChanged = true;   // the pendulum scale badge
        }
        if (query.code != 0 && query.code != card.code) {
            card.code = query.code;
            visualChanged = true;
        }
        if (query.position != 0 && query.position != card.position) {
            card.position = query.position;
            visualChanged = true;
        }
        if ((query.flags & QUERY_OVERLAY_CARD) != 0) {
            for (int i = 0; i < Math.min(query.overlayCards.size(), card.materials.size()); i++) {
                ClientCard material = card.materials.get(i);
                if (material.code != query.overlayCards.get(i)) {
                    material.code = query.overlayCards.get(i);
                    visualChanged = true;
                }
            }
        }
        if ((query.flags & QUERY_COUNTERS) != 0) {
            // The engine packs each counter as type in the low 16 bits, count in the high 16.
            card.counters.clear();
            for (int packed : query.counters) {
                card.counters.put(packed & 0xFFFF, (packed >>> 16) & 0xFFFF);
            }
            visualChanged = true;
        }
        // A corrected code, position or counter total changes what the slot draws, not just its stats.
        if (visualChanged) markZoneDirty(card.controller, card.location);
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

    public void clearCardActions() {
        cardActions.clear();
    }

    private void addAction(int controller, int location, int sequence,
                           int actionType, int listIndex, String label) {
        addAction(controller, location, sequence, actionType, listIndex, label, 0L);
    }

    private void addAction(int controller, int location, int sequence,
                           int actionType, int listIndex, String label, long desc) {
        var key = new CardLocation(controller, location, sequence);
        cardActions.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new CardAction(actionType, listIndex, label, desc));
    }

    private void buildIdleCmdActions(DuelMessage.SelectIdleCmd sel) {
        cardActions.clear();
        for (int i = 0; i < sel.summonable().size(); i++) {
            var c = sel.summonable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.SUMMON, i, "Summon");
        }
        for (int i = 0; i < sel.specialSummonable().size(); i++) {
            var c = sel.specialSummonable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.SPECIAL_SUMMON, i, "Sp. Summon");
        }
        for (int i = 0; i < sel.repositionable().size(); i++) {
            var c = sel.repositionable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.REPOSITION, i, "Reposition");
        }
        for (int i = 0; i < sel.settableMonsters().size(); i++) {
            var c = sel.settableMonsters().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.SET_MONSTER, i, "Set");
        }
        for (int i = 0; i < sel.settableSpells().size(); i++) {
            var c = sel.settableSpells().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.SET_SPELL_TRAP, i, "Set S/T");
        }
        for (int i = 0; i < sel.activatable().size(); i++) {
            var c = sel.activatable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), IdleAction.ACTIVATE, i, "Activate", c.desc());
        }
    }

    private void buildBattleCmdActions(DuelMessage.SelectBattleCmd sel) {
        cardActions.clear();
        for (int i = 0; i < sel.attackable().size(); i++) {
            var c = sel.attackable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), BattleAction.ATTACK, i, "Attack");
        }
        for (int i = 0; i < sel.activatable().size(); i++) {
            var c = sel.activatable().get(i);
            addAction(c.controller(), c.location(), c.sequence(), BattleAction.ACTIVATE, i, "Activate", c.desc());
        }
    }

    public record ChainLink(int code, LocInfo location, int chainIndex) {}
}

package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.OcgConstants.BattleAction;
import com.haxerus.duelcraft.core.OcgConstants.IdleAction;
import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.MessageSanitizer;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.duel.response.SumSelection;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.function.Consumer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Duel handler for solo testing mode.
 * Player 0 is the real player; player 1 is an AI that auto-responds.
 * All non-prompt messages are sent only to the real player.
 */
public class SoloDuelHandler implements DuelEventListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_AI_RETRIES = 3;

    /** The real player; the AI is player 1. */
    private static final int HUMAN_PLAYER = 0;

    private final Consumer<CustomPacketPayload> send;
    private final Consumer<Boolean> complete;
    private final Consumer<byte[]> respond;

    /** Set by DuelManager after creating the session, used for auto-responses. */
    private Runnable pendingAutoResponse;

    // Last prompt routed and who it went to, so a Retry can tell whether the AI's own
    // answer was rejected; reset whenever a new prompt arrives.
    private DuelMessage lastPrompt;
    private int lastPromptTarget = -1;
    private int retryCount;

    // True once MSG_WIN was converted to a DuelEndPayload, so onDuelEnd does not send a second result.
    private boolean winSent;

    public SoloDuelHandler(Consumer<CustomPacketPayload> send, Consumer<Boolean> complete, Consumer<byte[]> respond) {
        this.send = send;
        this.complete = complete;
        this.respond = respond;
    }

    @Override
    public int onMessage(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.Retry ignored -> handleRetry(msg);
            case DuelMessage.Win win -> {
                send.accept(new DuelEndPayload(win.winner(), win.reason()));
                winSent = true;
                yield DUEL_ENDED;
            }

            // Selection prompts — route to human or AI
            case DuelMessage.SelectIdleCmd sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectBattleCmd sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectCard sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectChain sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectEffectYn sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectYesNo sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectOption sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectPlace sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectDisfield sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectPosition sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectTribute sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectCounter sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectSum sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SelectUnselectCard sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SortCard sel -> routePrompt(sel.player(), msg);
            case DuelMessage.SortChain sel -> routePrompt(sel.player(), msg);
            case DuelMessage.AnnounceRace sel -> routePrompt(sel.player(), msg);
            case DuelMessage.AnnounceAttrib sel -> routePrompt(sel.player(), msg);
            case DuelMessage.AnnounceNumber sel -> routePrompt(sel.player(), msg);
            case DuelMessage.AnnounceCard sel -> routePrompt(sel.player(), msg);
            case DuelMessage.RockPaperScissors sel -> routePrompt(sel.player(), msg);

            default -> {
                // Broadcast info messages to the human player only, and only when the host
                // policy lets them see the message at all
                if (MessageSanitizer.recipientsOf(msg).includes(HUMAN_PLAYER)) sendToPlayer(msg);
                yield CONTINUE;
            }
        };
    }

    private int routePrompt(int targetPlayer, DuelMessage msg) {
        lastPrompt = msg;
        lastPromptTarget = targetPlayer;
        retryCount = 0;

        if (targetPlayer == 0) {
            // Human player — send to client as normal
            sendToPlayer(msg);
            return AWAIT_RESPONSE;
        } else {
            // AI player — auto-respond
            byte[] response = buildAutoResponse(msg);
            if (response != null) {
                LOGGER.debug("[Solo AI] Auto-responding to {} with {} bytes",
                        msg.getClass().getSimpleName(), response.length);
                // generic_duel.cpp:1326-1343: the duellist who was not prompted is told to wait.
                sendToPlayer(new DuelMessage.Waiting());
                // Schedule the response to be applied after this message batch completes
                pendingAutoResponse = () -> respond.accept(response);
                // Still pause processing; DuelManager will apply the answer and resume.
                return AWAIT_RESPONSE;
            }
            LOGGER.warn("[Solo AI] No auto-response for {}, sending to player as fallback",
                    msg.getClass().getSimpleName());
            forwardAiPrompt(msg);
            lastPromptTarget = 0;
            return AWAIT_RESPONSE;
        }
    }

    /**
     * MSG_RETRY handling. If the human's response was rejected, forward as before. If the AI's own
     * response was rejected, re-answer with a more careful fallback (bounded) instead of hanging;
     * after too many retries, hand the prompt to the human so the duel isn't wedged.
     */
    private int handleRetry(DuelMessage retryMsg) {
        if (lastPromptTarget != 1 || lastPrompt == null) {
            sendToPlayer(retryMsg);
            return AWAIT_RESPONSE;
        }

        retryCount++;
        if (retryCount > MAX_AI_RETRIES) {
            LOGGER.error("[Solo AI] Giving up after {} retries on {}, forwarding prompt to the player",
                    MAX_AI_RETRIES, lastPrompt.getClass().getSimpleName());
            forwardAiPrompt(lastPrompt);
            lastPromptTarget = 0;
            return AWAIT_RESPONSE;
        }

        byte[] response = buildFallbackResponse(lastPrompt);
        if (response == null) {
            LOGGER.error("[Solo AI] No fallback response for {}, forwarding prompt to the player",
                    lastPrompt.getClass().getSimpleName());
            forwardAiPrompt(lastPrompt);
            lastPromptTarget = 0;
            return AWAIT_RESPONSE;
        }
        LOGGER.warn("[Solo AI] Retry {}/{} on {}, re-answering with fallback",
                retryCount, MAX_AI_RETRIES, lastPrompt.getClass().getSimpleName());
        pendingAutoResponse = () -> respond.accept(response);
        return AWAIT_RESPONSE;
    }

    /** Check if there's a pending AI response that needs to be applied. */
    public Runnable consumePendingAutoResponse() {
        var r = pendingAutoResponse;
        pendingAutoResponse = null;
        return r;
    }

    private void sendToPlayer(DuelMessage msg) {
        send.accept(
                new DuelMessagePayload(MessageSanitizer.forRecipient(msg, HUMAN_PLAYER)));
    }

    /**
     * Hand the human a prompt the AI was asked but cannot answer. It goes out unsanitised on
     * purpose: {@code forRecipient} zeroes candidates the <em>prompted</em> player does not control
     * ({@code generic_duel.cpp:933-977}), which here would blank the human's own cards.
     */
    private void forwardAiPrompt(DuelMessage prompt) {
        send.accept(new DuelMessagePayload(prompt));
    }

    /** The prompt the human is expected to answer; the AI answers its own prompts internally. */
    @Override
    public int pendingPlayer() { return lastPromptTarget; }

    @Override
    public void onDuelEnd() {
        complete.accept(winSent);
    }

    // ─── AI Auto-Response Logic ───────────────────────────────
    // Simple AI: summon/attack when possible, decline optional chains, say yes to effects.

    private static byte[] buildAutoResponse(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.SelectIdleCmd sel -> {
                // Priority: summon > set monster > set S/T > activate > battle > end turn
                if (!sel.summonable().isEmpty())
                    yield ResponseBuilder.selectCmd(IdleAction.SUMMON, 0);
                if (!sel.settableMonsters().isEmpty())
                    yield ResponseBuilder.selectCmd(IdleAction.SET_MONSTER, 0);
                if (!sel.settableSpells().isEmpty())
                    yield ResponseBuilder.selectCmd(IdleAction.SET_SPELL_TRAP, 0);
                if (!sel.activatable().isEmpty())
                    yield ResponseBuilder.selectCmd(IdleAction.ACTIVATE, 0);
                if (sel.canBattle())
                    yield ResponseBuilder.selectCmd(IdleAction.TO_BATTLE, 0);
                yield ResponseBuilder.selectCmd(IdleAction.END_TURN, 0);
            }

            case DuelMessage.SelectBattleCmd sel -> {
                if (!sel.attackable().isEmpty())
                    yield ResponseBuilder.selectCmd(BattleAction.ATTACK, 0); // attack with first
                if (sel.canMain2())
                    yield ResponseBuilder.selectCmd(BattleAction.TO_MAIN2, 0);
                yield ResponseBuilder.selectCmd(BattleAction.END_BATTLE, 0);
            }

            case DuelMessage.SelectCard sel -> {
                // Legal minimum: cancel when nothing is required and cancel is offered,
                // otherwise the first max(min, 1) cards.
                if (sel.min() == 0 && sel.cancelable())
                    yield ResponseBuilder.selectCardsCancel();
                int n = Math.min(Math.max(sel.min(), 1), sel.cards().size());
                int[] indices = new int[n];
                for (int i = 0; i < n; i++) indices[i] = i;
                yield ResponseBuilder.selectCards(indices);
            }

            case DuelMessage.SelectTribute sel -> {
                // Over-tribute is legal, so stop at the first card that meets or exceeds min.
                List<Integer> picks = new ArrayList<>();
                int sum = 0;
                for (int i = 0; i < sel.cards().size() && sum < sel.min(); i++) {
                    picks.add(i);
                    sum += sel.cards().get(i).tributeCount();
                }
                yield ResponseBuilder.selectCards(picks.stream().mapToInt(Integer::intValue).toArray());
            }

            case DuelMessage.SelectChain sel -> {
                if (sel.forced() && sel.chains() != null && !sel.chains().isEmpty())
                    yield ResponseBuilder.selectChain(0);
                yield ResponseBuilder.selectChain(-1); // decline
            }

            case DuelMessage.SelectEffectYn sel ->
                    ResponseBuilder.selectYesNo(true);

            case DuelMessage.SelectYesNo sel ->
                    ResponseBuilder.selectYesNo(true);

            case DuelMessage.SelectOption sel ->
                    ResponseBuilder.selectOption(0);

            case DuelMessage.SelectPlace sel -> {
                int field = sel.field();
                // Find first available monster zone (bits 0-4, 0 = selectable)
                for (int seq = 0; seq < 5; seq++) {
                    if ((field & (1 << seq)) == 0)
                        yield ResponseBuilder.selectPlace(sel.player(), LOCATION_MZONE, seq);
                }
                // Try spell/trap zones (bits 8-12)
                for (int seq = 0; seq < 5; seq++) {
                    if ((field & (1 << (seq + 8))) == 0)
                        yield ResponseBuilder.selectPlace(sel.player(), LOCATION_SZONE, seq);
                }
                yield ResponseBuilder.selectPlace(sel.player(), LOCATION_MZONE, 0); // fallback
            }

            case DuelMessage.SelectDisfield sel -> {
                // Disable the first `count` zones whose bits are clear: own MZONE (0-6), own SZONE
                // (8-15), then the opponent's MZONE (16-22) and SZONE (24-31).
                int fieldMask = sel.field();
                int self = sel.player();
                int other = 1 - self;
                List<int[]> zones = new ArrayList<>();
                for (int seq = 0; seq < 7 && zones.size() < sel.count(); seq++) {
                    if ((fieldMask & (1 << seq)) == 0) zones.add(new int[]{self, LOCATION_MZONE, seq});
                }
                for (int seq = 0; seq < 8 && zones.size() < sel.count(); seq++) {
                    if ((fieldMask & (1 << (8 + seq))) == 0) zones.add(new int[]{self, LOCATION_SZONE, seq});
                }
                for (int seq = 0; seq < 7 && zones.size() < sel.count(); seq++) {
                    if ((fieldMask & (1 << (16 + seq))) == 0) zones.add(new int[]{other, LOCATION_MZONE, seq});
                }
                for (int seq = 0; seq < 8 && zones.size() < sel.count(); seq++) {
                    if ((fieldMask & (1 << (24 + seq))) == 0) zones.add(new int[]{other, LOCATION_SZONE, seq});
                }
                yield ResponseBuilder.selectPlaces(zones);
            }

            case DuelMessage.SelectPosition sel -> {
                int positions = sel.positions();
                // Prefer face-up attack
                if ((positions & POS_FACEUP_ATTACK) != 0) yield ResponseBuilder.selectPosition(POS_FACEUP_ATTACK);
                if ((positions & POS_FACEUP_DEFENSE) != 0) yield ResponseBuilder.selectPosition(POS_FACEUP_DEFENSE);
                if ((positions & POS_FACEDOWN_ATTACK) != 0) yield ResponseBuilder.selectPosition(POS_FACEDOWN_ATTACK);
                yield ResponseBuilder.selectPosition(POS_FACEDOWN_DEFENSE);
            }

            case DuelMessage.SelectCounter sel ->
                    ResponseBuilder.selectCounter(spreadCounters(sel.cards(), sel.count()));

            case DuelMessage.SelectSum sel -> {
                int[] picks = SumSelection.firstViableCombination(sel);
                yield picks != null ? ResponseBuilder.selectSum(picks) : null;
            }

            case DuelMessage.SelectUnselectCard sel -> {
                if (sel.finishable())
                    yield ResponseBuilder.selectUnselectCardFinish();
                yield ResponseBuilder.selectUnselectCard(0);
            }

            case DuelMessage.SortCard sel ->
                    ResponseBuilder.sortCardsDefault();

            case DuelMessage.SortChain sel ->
                    ResponseBuilder.sortCardsDefault();

            case DuelMessage.AnnounceRace sel -> {
                // Pick first N available races
                long available = sel.available();
                long selected = 0;
                int remaining = sel.count();
                for (int bit = 0; bit < 64 && remaining > 0; bit++) {
                    if ((available & (1L << bit)) != 0) {
                        selected |= (1L << bit);
                        remaining--;
                    }
                }
                yield ResponseBuilder.announceRace(selected);
            }

            case DuelMessage.AnnounceAttrib sel -> {
                int available = sel.available();
                int selected = 0;
                int remaining = sel.count();
                for (int bit = 0; bit < 32 && remaining > 0; bit++) {
                    if ((available & (1 << bit)) != 0) {
                        selected |= (1 << bit);
                        remaining--;
                    }
                }
                yield ResponseBuilder.announceAttrib(selected);
            }

            case DuelMessage.AnnounceNumber sel ->
                    ResponseBuilder.announceNumber(0);

            case DuelMessage.AnnounceCard sel -> {
                // No server-side card database in test mode to pick a legal declaration from;
                // the human declares for the AI (routePrompt's null-response fallback).
                LOGGER.info("[Solo AI] AnnounceCard has no AI heuristic, human declares for the AI");
                yield null;
            }

            case DuelMessage.RockPaperScissors sel ->
                    ResponseBuilder.rockPaperScissors(1); // always rock

            default -> null;
        };
    }

    /** Spreads {@code count} across cards without exceeding any single card's own counter count. */
    private static int[] spreadCounters(List<DuelMessage.CounterCard> cards, int count) {
        int[] counts = new int[cards.size()];
        int remaining = count;
        for (int i = 0; i < counts.length && remaining > 0; i++) {
            int take = Math.min(remaining, cards.get(i).counterCount());
            counts[i] = take;
            remaining -= take;
        }
        return counts;
    }

    // ─── Retry fallback ───────────────────────────────────────────────────
    // SelectCard/SelectTribute/SelectCounter/SelectSum already compute a legal answer in
    // buildAutoResponse, so a retry on those just retries buildAutoResponse via the
    // default case below. SelectUnselectCard also accepts
    // cancelable (not just finishable) here, which buildAutoResponse doesn't check.

    private static byte[] buildFallbackResponse(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.SelectUnselectCard sel -> {
                if (sel.finishable() || sel.cancelable())
                    yield ResponseBuilder.selectUnselectCardFinish();
                yield ResponseBuilder.selectUnselectCard(0);
            }

            default -> buildAutoResponse(msg);
        };
    }
}

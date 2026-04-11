package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Duel handler for solo testing mode.
 * Player 0 is the real player; player 1 is an AI that auto-responds.
 * All non-prompt messages are sent only to the real player.
 */
public class SoloDuelHandler implements DuelEventListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player;
    private final UUID duelId;

    /** Set by DuelManager after creating the session, used for auto-responses. */
    private Runnable pendingAutoResponse;

    public SoloDuelHandler(ServerPlayer player, UUID duelId) {
        this.player = player;
        this.duelId = duelId;
    }

    @Override
    public int onMessage(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.Retry ignored -> {
                sendToPlayer(msg);
                yield 1;
            }
            case DuelMessage.Win win -> {
                PacketDistributor.sendToPlayer(player, new DuelEndPayload(win.winner(), win.reason()));
                yield 2;
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
                // Broadcast info messages to the human player only
                sendToPlayer(msg);
                yield 0;
            }
        };
    }

    private int routePrompt(int targetPlayer, DuelMessage msg) {
        if (targetPlayer == 0) {
            // Human player — send to client as normal
            sendToPlayer(msg);
            return 1; // await response
        } else {
            // AI player — auto-respond
            byte[] response = buildAutoResponse(msg);
            if (response != null) {
                LOGGER.debug("[Solo AI] Auto-responding to {} with {} bytes",
                        msg.getClass().getSimpleName(), response.length);
                // Schedule the response to be applied after this message batch completes
                pendingAutoResponse = () -> DuelManager.get().handleSoloAutoResponse(duelId, response);
                // Still return 1 to pause processing; DuelManager will apply and resume
                return 1;
            }
            LOGGER.warn("[Solo AI] No auto-response for {}, sending to player as fallback",
                    msg.getClass().getSimpleName());
            sendToPlayer(msg);
            return 1;
        }
    }

    /** Check if there's a pending AI response that needs to be applied. */
    public Runnable consumePendingAutoResponse() {
        var r = pendingAutoResponse;
        pendingAutoResponse = null;
        return r;
    }

    private void sendToPlayer(DuelMessage msg) {
        PacketDistributor.sendToPlayer(player, new DuelMessagePayload(msg));
    }

    @Override
    public void onDuelEnd() {
        DuelManager.get().endDuel(duelId);
    }

    // ─── AI Auto-Response Logic ───────────────────────────────
    // Simple AI: summon/attack when possible, decline optional chains, say yes to effects.

    private static byte[] buildAutoResponse(DuelMessage msg) {
        return switch (msg) {
            case DuelMessage.SelectIdleCmd sel -> {
                // Priority: summon > set monster > set S/T > activate > battle > end turn
                if (!sel.summonable().isEmpty())
                    yield ResponseBuilder.selectCmd(0, 0);
                if (!sel.settableMonsters().isEmpty())
                    yield ResponseBuilder.selectCmd(3, 0);
                if (!sel.settableSpells().isEmpty())
                    yield ResponseBuilder.selectCmd(4, 0);
                if (!sel.activatable().isEmpty())
                    yield ResponseBuilder.selectCmd(5, 0);
                if (sel.canBattle())
                    yield ResponseBuilder.selectCmd(6, 0);
                yield ResponseBuilder.selectCmd(7, 0); // end turn
            }

            case DuelMessage.SelectBattleCmd sel -> {
                if (!sel.attackable().isEmpty())
                    yield ResponseBuilder.selectCmd(1, 0); // attack with first
                if (sel.canMain2())
                    yield ResponseBuilder.selectCmd(2, 0); // main phase 2
                yield ResponseBuilder.selectCmd(3, 0); // end battle
            }

            case DuelMessage.SelectCard sel ->
                    ResponseBuilder.selectCards(0); // pick first card

            case DuelMessage.SelectTribute sel ->
                    ResponseBuilder.selectCards(0); // tribute first card

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
                        yield ResponseBuilder.selectPlace(sel.player(), 0x04, seq);
                }
                // Try spell/trap zones (bits 8-12)
                for (int seq = 0; seq < 5; seq++) {
                    if ((field & (1 << (seq + 8))) == 0)
                        yield ResponseBuilder.selectPlace(sel.player(), 0x08, seq);
                }
                yield ResponseBuilder.selectPlace(sel.player(), 0x04, 0); // fallback
            }

            case DuelMessage.SelectPosition sel -> {
                int positions = sel.positions();
                // Prefer face-up attack
                if ((positions & 0x1) != 0) yield ResponseBuilder.selectPosition(0x1);
                if ((positions & 0x4) != 0) yield ResponseBuilder.selectPosition(0x4);
                if ((positions & 0x2) != 0) yield ResponseBuilder.selectPosition(0x2);
                yield ResponseBuilder.selectPosition(0x8);
            }

            case DuelMessage.SelectCounter sel -> {
                // Remove counters from first card
                int[] counts = new int[sel.cards().size()];
                counts[0] = sel.count();
                yield ResponseBuilder.selectCounter(counts);
            }

            case DuelMessage.SelectSum sel ->
                    ResponseBuilder.selectCards(0); // pick first

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

            case DuelMessage.RockPaperScissors sel ->
                    ResponseBuilder.rockPaperScissors(1); // always rock

            default -> null;
        };
    }
}

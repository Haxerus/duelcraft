package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.MessageSanitizer;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.UUID;

public class ServerDuelHandler implements DuelEventListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerPlayer player0;
    private final ServerPlayer player1;
    private final UUID duelId;

    // Player index of the last prompt forwarded via sendToPlayer; Retry only reaches this player.
    private int pendingPlayer = -1;

    // True once MSG_WIN was converted to a DuelEndPayload, so onDuelEnd does not send a second result.
    private boolean winSent;

    public ServerDuelHandler(ServerPlayer player0, ServerPlayer player1, UUID duelId) {
        this.player0 = player0;
        this.player1 = player1;
        this.duelId = duelId;
    }

    /** Player index of the last prompt forwarded; the response ownership check compares against it. */
    @Override
    public int pendingPlayer() { return pendingPlayer; }

    @Override
    public int onMessage(DuelMessage msg) {
        switch (msg) {
            case DuelMessage.Retry ignored -> {
                // Bad response — edopro ends the duel here, but Duelcraft continues, so only
                // the player who owns the rejected prompt needs to see it.
                if (pendingPlayer >= 0) sendToPlayer(pendingPlayer, msg);
                return AWAIT_RESPONSE; // wait for a corrected response
            }
            case DuelMessage.Win win -> {
                var payload = new DuelEndPayload(win.winner(), win.reason());
                PacketDistributor.sendToPlayer(player0, payload);
                PacketDistributor.sendToPlayer(player1, payload);
                winSent = true;
                return DUEL_ENDED;
            }
            case DuelMessage.SelectIdleCmd sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectBattleCmd sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectCard sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectChain sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectEffectYn sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectYesNo sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectOption sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectPlace sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectDisfield sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectPosition sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectTribute sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectCounter sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectSum sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SelectUnselectCard sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SortCard sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.SortChain sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.AnnounceRace sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.AnnounceAttrib sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.AnnounceNumber sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.AnnounceCard sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            case DuelMessage.RockPaperScissors sel -> { sendToPlayer(sel.player(), msg); return AWAIT_RESPONSE; }
            default -> {
                broadcast(msg);
                return CONTINUE;
            }
        }
    }

    private void sendToPlayer(int playerIndex, DuelMessage msg) {
        pendingPlayer = playerIndex;
        send(playerIndex, msg);
        // generic_duel.cpp:1326-1343: every duellist but the prompted one is told to wait.
        send(1 - playerIndex, new DuelMessage.Waiting());
    }

    private void send(int playerIndex, DuelMessage msg) {
        var player = playerIndex == 0 ? player0 : player1;
        PacketDistributor.sendToPlayer(player,
                new DuelMessagePayload(MessageSanitizer.forRecipient(msg, playerIndex)));
    }

    /** Sends each player the copy of {@code msg} they are allowed to see, if they may see it at all. */
    private void broadcast(DuelMessage msg) {
        var recipients = MessageSanitizer.recipientsOf(msg);
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            if (recipients.includes(playerIndex)) send(playerIndex, msg);
        }
    }

    @Override
    public void onDuelEnd() {
        if (winSent) {
            DuelManager.get().endDuel(duelId);
        } else {
            // The engine stopped without MSG_WIN; nobody won, but both clients still need a result.
            DuelManager.get().finishDuel(duelId, DuelEndPayload.WINNER_DRAW, 0);
        }
    }
}

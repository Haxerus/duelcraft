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

    public ServerDuelHandler(ServerPlayer player0, ServerPlayer player1, UUID duelId) {
        this.player0 = player0;
        this.player1 = player1;
        this.duelId = duelId;
    }

    /** Player index of the last prompt forwarded (Task 11 uses this for the response ownership check). */
    public int pendingPlayer() { return pendingPlayer; }

    @Override
    public int onMessage(DuelMessage msg) {
        switch (msg) {
            case DuelMessage.Retry ignored -> {
                // Bad response — edopro ends the duel here, but Duelcraft continues, so only
                // the player who owns the rejected prompt needs to see it.
                if (pendingPlayer >= 0) sendToPlayer(pendingPlayer, msg);
                return 1; // stop processing, wait for corrected response
            }
            case DuelMessage.Win win -> {
                var payload = new DuelEndPayload(win.winner(), win.reason());
                PacketDistributor.sendToPlayer(player0, payload);
                PacketDistributor.sendToPlayer(player1, payload);
                return 2;
            }
            case DuelMessage.SelectIdleCmd sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectBattleCmd sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectCard sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectChain sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectEffectYn sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectYesNo sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectOption sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectPlace sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectDisfield sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectPosition sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectTribute sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectCounter sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectSum sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SelectUnselectCard sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SortCard sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.SortChain sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.AnnounceRace sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.AnnounceAttrib sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.AnnounceNumber sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.AnnounceCard sel -> { sendToPlayer(sel.player(), msg); return 1; }
            case DuelMessage.RockPaperScissors sel -> { sendToPlayer(sel.player(), msg); return 1; }
            default -> {
                broadcast(msg);
                return 0;
            }
        }
    }

    private void sendToPlayer(int playerIndex, DuelMessage msg) {
        pendingPlayer = playerIndex;
        send(playerIndex, msg);
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
        DuelManager.get().endDuel(duelId);
    }
}

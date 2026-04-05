package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.DuelEventListener;
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

    public ServerDuelHandler(ServerPlayer player0, ServerPlayer player1, UUID duelId) {
        this.player0 = player0;
        this.player1 = player1;
        this.duelId = duelId;
    }

    @Override
    public int onMessage(DuelMessage msg, byte[] rawData) {
        switch (msg) {
            case DuelMessage.Win win -> {
                PacketDistributor.sendToPlayer(player0, new DuelEndPayload(win.winner(), win.reason()));
                PacketDistributor.sendToPlayer(player1, new DuelEndPayload(win.winner(), win.reason()));
                return 2;
            }
            case DuelMessage.SelectIdleCmd sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectBattleCmd sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectCard sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectChain sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectEffectYn sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectYesNo sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectOption sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectPlace sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectPosition sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectTribute sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectCounter sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectSum sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SelectUnselectCard sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SortCard sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.SortChain sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.AnnounceRace sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.AnnounceAttrib sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.AnnounceNumber sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.AnnounceCard sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            case DuelMessage.RockPaperScissors sel -> {
                sendToPlayer(sel.player(), msg.type(), rawData);
                return 1;
            }
            default -> {
                // Broadcast non-selection messages to both players
                broadcastToBoth(msg.type(), rawData);
                return 0;
            }
        }
    }

    private void sendToPlayer(int playerIndex, int msgType, byte[] rawData) {
        var player = playerIndex == 0 ? player0 : player1;
        PacketDistributor.sendToPlayer(player, new DuelMessagePayload(msgType, rawData));
    }

    private void broadcastToBoth(int msgType, byte[] rawData) {
        var payload = new DuelMessagePayload(msgType, rawData);
        PacketDistributor.sendToPlayer(player0, payload);
        PacketDistributor.sendToPlayer(player1, payload);
    }

    @Override
    public void onDuelEnd() {
        DuelManager.get().endDuel(duelId);
    }
}

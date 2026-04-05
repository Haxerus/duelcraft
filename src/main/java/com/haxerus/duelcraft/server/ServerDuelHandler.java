package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.OcgConstants;
import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
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
    public int onMessage(DuelMessage msg) {
        switch (msg) {
            case DuelMessage.Retry ignored -> {
                // Bad response — broadcast retry so the client re-prompts
                broadcastToBoth(msg);
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
                broadcastToBoth(msg);
                return 0;
            }
        }
    }

    private void sendToPlayer(int playerIndex, DuelMessage msg) {
        var player = playerIndex == 0 ? player0 : player1;
        PacketDistributor.sendToPlayer(player, new DuelMessagePayload(msg));
    }

    private void broadcastToBoth(DuelMessage msg) {
        // Send each player a version with opponent's hidden info removed
        PacketDistributor.sendToPlayer(player0, new DuelMessagePayload(hideInfo(msg, 0)));
        PacketDistributor.sendToPlayer(player1, new DuelMessagePayload(hideInfo(msg, 1)));
    }

    /**
     * Sanitize a message for a specific recipient by zeroing out card codes
     * the player shouldn't see (opponent's hand cards, face-down cards).
     */
    private static DuelMessage hideInfo(DuelMessage msg, int recipient) {
        return switch (msg) {
            case DuelMessage.Draw draw -> {
                if (draw.player() != recipient) {
                    // Opponent drew — hide the codes
                    yield new DuelMessage.Draw(draw.player(),
                            draw.codes().stream().map(c -> 0).toList());
                }
                yield draw;
            }
            case DuelMessage.Move move -> {
                // Hide code if the card is going face-down and we don't control it
                if (move.to().controller() != recipient && isFaceDown(move.to().position())) {
                    yield new DuelMessage.Move(0, move.from(), move.to(), move.reason());
                }
                // Hide code if it was in opponent's hand (source is hand, not ours)
                if (move.from().controller() != recipient
                        && move.from().location() == OcgConstants.LOCATION_HAND
                        && isFaceDown(move.to().position())) {
                    yield new DuelMessage.Move(0, move.from(), move.to(), move.reason());
                }
                yield move;
            }
            case DuelMessage.ShuffleHand sh -> {
                if (sh.player() != recipient) {
                    yield new DuelMessage.ShuffleHand(sh.player(),
                            sh.codes().stream().map(c -> 0).toList());
                }
                yield sh;
            }
            case DuelMessage.Set set -> {
                // Set cards are always face-down — hide code from opponent
                if (set.location().controller() != recipient) {
                    yield new DuelMessage.Set(0, set.location());
                }
                yield set;
            }
            default -> msg;
        };
    }

    private static boolean isFaceDown(int position) {
        return (position & OcgConstants.POS_FACEDOWN) != 0;
    }

    @Override
    public void onDuelEnd() {
        DuelManager.get().endDuel(duelId);
    }
}

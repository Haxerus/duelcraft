package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.DuelEventListener;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
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
    public int onMessage(DuelMessage msg) {
        LOGGER.info("{}{}", "Duel Message", msg.type());
        return 0;
    }

    @Override
    public void onDuelEnd() {
        DuelManager.get().endDuel(duelId);
    }
}

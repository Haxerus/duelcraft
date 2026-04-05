package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.Config;
import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DuelEngine;
import com.haxerus.duelcraft.core.DuelOptions;
import com.haxerus.duelcraft.core.OcgCore;
import com.haxerus.duelcraft.duel.DuelSession;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.*;

public class DuelManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static DuelManager instance;

    private DuelEngine engine;
    private Map<UUID, DuelSession> activeDuels;
    private Map<UUID, UUID> playerToDuel;

    // FIXME: Temporary for testing
    public Map<UUID, UUID> duelInvites; // target -> challenger

    public static DuelManager get() { return instance; }

    public static void onServerStarting(ServerStartingEvent event) {
        instance = new DuelManager();
        instance.init();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public void init() {
        List<String> dbPaths = new ArrayList<>(Config.CARD_DATABASE_PATHS.get());
        List<String> scriptPaths = new ArrayList<>(Config.SCRIPT_SEARCH_PATHS.get());

        engine = new DuelEngine(dbPaths, scriptPaths);

        activeDuels = new HashMap<>();
        playerToDuel = new HashMap<>();
        duelInvites = new HashMap<>();

        int[] version = OcgCore.nGetVersion();
        LOGGER.info("DuelManager initialized — OCG core v{}.{}", version[0], version[1]);
    }

    public void shutdown() {
        for (DuelSession session : activeDuels.values()) {
            session.close();
        }

        activeDuels.clear();
        playerToDuel.clear();
        if (engine != null) {
            try {
                engine.close();
                engine = null;
            } catch (Exception e) {
                LOGGER.error("{}{}", "Failed to shutdown DuelManager ", e);
            }
        }
    }

    public void startDuel(ServerPlayer p1, ServerPlayer p2, DuelOptions options, Deck team1deck, Deck team2Deck) {
        if (playerToDuel.containsKey(p1.getUUID()) || playerToDuel.containsKey(p2.getUUID())) {
            LOGGER.warn("Cannot start duel - a player is already in a duel!");
            return;
        }

        UUID duelId = UUID.randomUUID();
        var handler = new ServerDuelHandler(p1, p2, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(p1.getUUID(), duelId);
        playerToDuel.put(p2.getUUID(), duelId);

        // Tell each client which player they are, the opponent's name, and initial game state
        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        int deckSize = team1deck.main().size();
        int extraSize = team1deck.extra().size();
        PacketDistributor.sendToPlayer(p1, new DuelStartPayload(0, p2.getName().getString(),
                lp0, lp1, deckSize, extraSize));
        PacketDistributor.sendToPlayer(p2, new DuelStartPayload(1, p1.getName().getString(),
                lp0, lp1, deckSize, extraSize));

        session.setupDuel(team1deck, team2Deck);
        session.process();
    }

    public void handleResponse(ServerPlayer player, byte[] response) {
        var duelId = playerToDuel.get(player.getUUID());
        if (duelId == null) return;
        DuelSession session = activeDuels.get(duelId);
        if (session == null || session.isEnded()) return;
        session.setResponse(response);
    }

    public void endDuel(UUID duelId) {
        DuelSession session = activeDuels.remove(duelId);
        if (session != null) {
            session.close();
            // Also clean up playerToDuel entries
            playerToDuel.values().removeIf(id -> id.equals(duelId));
        }
    }

    public UUID getPlayerActiveDuel(ServerPlayer player) {
        return playerToDuel.get(player.getUUID());
    }
}

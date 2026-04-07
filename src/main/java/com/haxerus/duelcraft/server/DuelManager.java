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
    private Map<UUID, SoloDuelHandler> soloHandlers;

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
        soloHandlers = new HashMap<>();
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

    /**
     * Start a solo test duel where player 1 is AI-controlled.
     */
    public void startSoloDuel(ServerPlayer player, DuelOptions options, Deck playerDeck, Deck aiDeck) {
        if (playerToDuel.containsKey(player.getUUID())) {
            LOGGER.warn("Cannot start solo duel - player is already in a duel!");
            return;
        }

        UUID duelId = UUID.randomUUID();
        var handler = new SoloDuelHandler(player, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(player.getUUID(), duelId);
        soloHandlers.put(duelId, handler);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        PacketDistributor.sendToPlayer(player, new DuelStartPayload(0, "AI Opponent",
                lp0, lp1, playerDeck.main().size(), playerDeck.extra().size()));

        session.setupDuel(playerDeck, aiDeck);
        session.process();

        // Check if the AI needs to respond first (e.g., if AI goes first)
        processSoloAutoResponse(duelId, handler);
    }

    /**
     * Handle a queued auto-response from the solo AI.
     * Called by SoloDuelHandler when the AI player receives a prompt.
     */
    public void handleSoloAutoResponse(UUID duelId, byte[] response) {
        DuelSession session = activeDuels.get(duelId);
        if (session == null || session.isEnded()) return;
        session.setResponse(response);

        // Check if the AI needs to respond again (chained prompts)
        // Find the handler — it's the listener on the session
        // We need to check for pending auto-responses after each setResponse
        for (var entry : activeDuels.entrySet()) {
            if (entry.getKey().equals(duelId) && entry.getValue() == session) {
                // Look up the handler through the duelId
                processSoloAutoResponseByDuelId(duelId);
                break;
            }
        }
    }

    private void processSoloAutoResponse(UUID duelId, SoloDuelHandler handler) {
        Runnable autoResponse = handler.consumePendingAutoResponse();
        if (autoResponse != null) {
            autoResponse.run();
        }
    }

    private void processSoloAutoResponseByDuelId(UUID duelId) {
        // We need a way to get the handler. Let's track solo handlers.
        var handler = soloHandlers.get(duelId);
        if (handler != null) {
            processSoloAutoResponse(duelId, handler);
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
        soloHandlers.remove(duelId);
        if (session != null) {
            session.close();
            playerToDuel.values().removeIf(id -> id.equals(duelId));
        }
    }

    public UUID getPlayerActiveDuel(ServerPlayer player) {
        return playerToDuel.get(player.getUUID());
    }
}

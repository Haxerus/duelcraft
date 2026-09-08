package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.Config;
import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.core.DuelEngine;
import com.haxerus.duelcraft.core.DuelOptions;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.OcgCore;
import com.haxerus.duelcraft.duel.DuelSession;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

public class DuelManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static DuelManager instance;

    private DuelEngine engine;
    private Map<UUID, DuelSession> activeDuels;
    private Map<UUID, UUID> playerToDuel;
    private Map<UUID, SoloDuelHandler> soloHandlers;
    private DeckRegistry deckRegistry;
    private final Map<UUID, String> playerCurrentDeck = new HashMap<>();

    // FIXME: Temporary for testing
    public Map<UUID, DuelCommand.PendingChallenge> duelInvites; // target -> pending

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

        java.nio.file.Path decksDir = FMLPaths.GAMEDIR.get().resolve("duelcraft").resolve("decks");
        deckRegistry = DeckRegistry.open(decksDir);

        int[] version = OcgCore.nGetVersion();
        LOGGER.info("DuelManager initialized — OCG core v{}.{}, decks dir: {}",
                version[0], version[1], decksDir);
    }

    public void shutdown() {
        for (DuelSession session : activeDuels.values()) {
            session.close();
        }

        activeDuels.clear();
        playerToDuel.clear();
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    /**
     * Start a solo test duel where player 1 is AI-controlled.
     * The player's deck is shuffled with {@code seed} and the AI's with {@code seed + 1}, so two
     * identical lists still produce different draws. Deck names are used only for logging.
     */
    public void startSoloDuel(ServerPlayer player, long seed, DuelRule rule,
                              Deck playerDeck, Deck aiDeck,
                              String playerDeckName, String aiDeckName) {
        if (playerToDuel.containsKey(player.getUUID())) {
            LOGGER.warn("Cannot start solo duel - player is already in a duel!");
            return;
        }

        DuelOptions options = DuelOptions.of(seed, rule);
        UUID duelId = UUID.randomUUID();
        var handler = new SoloDuelHandler(player, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(player.getUUID(), duelId);
        soloHandlers.put(duelId, handler);

        LOGGER.info("Solo duel {}: seed={}, rule={}, player={}, aiDeck={}",
                duelId, seed, rule.id(), playerDeckName, aiDeckName);

        Deck shuffledPlayer = playerDeck.shuffled(seed);
        Deck shuffledAi = aiDeck.shuffled(seed + 1);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        PacketDistributor.sendToPlayer(player, new DuelStartPayload(0, "AI Opponent",
                lp0, lp1, shuffledPlayer.main().size(), shuffledPlayer.extra().size(), options.flags()));

        session.setupDuel(shuffledPlayer, shuffledAi);
        session.process();

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

    /** Team 1 shuffles with {@code seed}, team 2 with {@code seed + 1}; see {@link #startSoloDuel}. */
    public void startDuel(ServerPlayer p1, ServerPlayer p2, long seed, DuelRule rule,
                          Deck team1Deck, Deck team2Deck,
                          String team1Name, String team2Name) {
        if (playerToDuel.containsKey(p1.getUUID()) || playerToDuel.containsKey(p2.getUUID())) {
            LOGGER.warn("Cannot start duel - a player is already in a duel!");
            return;
        }

        DuelOptions options = DuelOptions.of(seed, rule);
        UUID duelId = UUID.randomUUID();
        var handler = new ServerDuelHandler(p1, p2, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(p1.getUUID(), duelId);
        playerToDuel.put(p2.getUUID(), duelId);

        LOGGER.info("Duel {}: seed={}, rule={}, decks=[{}, {}]",
                duelId, seed, rule.id(), team1Name, team2Name);

        Deck shuffled1 = team1Deck.shuffled(seed);
        Deck shuffled2 = team2Deck.shuffled(seed + 1);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        int deckSize = shuffled1.main().size();
        int extraSize = shuffled1.extra().size();
        PacketDistributor.sendToPlayer(p1, new DuelStartPayload(0, p2.getName().getString(),
                lp0, lp1, deckSize, extraSize, options.flags()));
        PacketDistributor.sendToPlayer(p2, new DuelStartPayload(1, p1.getName().getString(),
                lp0, lp1, deckSize, extraSize, options.flags()));

        session.setupDuel(shuffled1, shuffled2);
        session.process();
    }

    public void handleResponse(ServerPlayer player, byte[] response) {
        var duelId = playerToDuel.get(player.getUUID());
        if (duelId == null) return;
        DuelSession session = activeDuels.get(duelId);
        if (session == null || session.isEnded()) return;
        session.setResponse(response);
        processSoloAutoResponseByDuelId(duelId);
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

    public DeckRegistry getDeckRegistry() { return deckRegistry; }

    public void setPlayerCurrentDeck(UUID player, String deckName) {
        playerCurrentDeck.put(player, deckName);
    }

    public Optional<String> getPlayerCurrentDeck(UUID player) {
        return Optional.ofNullable(playerCurrentDeck.get(player));
    }

    public void clearPlayerCurrentDeck(UUID player) {
        playerCurrentDeck.remove(player);
    }

    /**
     * Resolves the deck a player should use right now.
     * Throws IOException when no current deck is set.
     * Throws when a current deck is set but its file is missing or malformed.
     */
    public Deck resolveDeck(ServerPlayer player) throws IOException, DeckLoader.DeckParseException {
        String name = playerCurrentDeck.get(player.getUUID());
        if (name == null) {
            throw new IOException("No deck set; run /duel deck set <name>");
        }
        return deckRegistry.load(name);
    }
}

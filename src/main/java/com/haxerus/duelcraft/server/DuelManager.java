package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.Config;
import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckValidator;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.haxerus.duelcraft.core.DuelEngine;
import com.haxerus.duelcraft.core.DuelOptions;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.OcgCore;
import com.haxerus.duelcraft.core.PlayerOptions;
import com.haxerus.duelcraft.duel.DuelSession;
import com.haxerus.duelcraft.duel.FirstTurnLobby;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class DuelManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ServerPlayer[] NO_SEATS = new ServerPlayer[0];

    private static DuelManager instance;

    private DuelEngine engine;
    private Map<UUID, DuelSession> activeDuels;
    private Map<UUID, UUID> playerToDuel;
    private Map<UUID, SoloDuelHandler> soloHandlers;
    /** Duellists of each active duel by engine index; index 1 is null in solo mode (the AI). */
    private Map<UUID, ServerPlayer[]> duelSeats;
    private DeckRegistry deckRegistry;
    private final Map<UUID, DuelDeckPayload> playerCurrentDeck = new HashMap<>();

    /** Outstanding challenges, target -> pending; entries expire after {@link DuelCommand#INVITE_TIMEOUT_MS}. */
    public Map<UUID, DuelCommand.PendingChallenge> duelInvites;

    /** First-turn rolls in progress; both duellists map to the same roll. */
    private final Map<UUID, FirstTurnRoll> firstTurnRolls = new HashMap<>();

    public static DuelManager get() { return instance; }

    public static void onServerStarting(ServerStartingEvent event) {
        instance = new DuelManager();
        instance.init();
    }

    /** A duellist who logs out forfeits; their outstanding invites go with them. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        instance.handleLogout(player);
    }

    /** Expires first-turn rolls nobody finished; the invite timeout applies to each step. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (instance != null) instance.expireFirstTurnRolls();
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
        duelSeats = new HashMap<>();
        duelInvites = new HashMap<>();

        Path decksDir = FMLPaths.GAMEDIR.get().resolve("duelcraft").resolve("decks");
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
        playerCurrentDeck.clear();
        playerToDuel.clear();
        duelSeats.clear();
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
    public void startSoloDuel(ServerPlayer player, long seed, DuelRule rule, PlayerOptions playerOptions,
                              Deck playerDeck, Deck aiDeck,
                              String playerDeckName, String aiDeckName) {
        if (playerToDuel.containsKey(player.getUUID())) {
            LOGGER.warn("Cannot start solo duel - player is already in a duel!");
            return;
        }

        DuelOptions options = DuelOptions.of(seed, rule, playerOptions);
        UUID duelId = UUID.randomUUID();
        var handler = new SoloDuelHandler(player, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(player.getUUID(), duelId);
        soloHandlers.put(duelId, handler);
        duelSeats.put(duelId, new ServerPlayer[]{player, null});

        LOGGER.info("Solo duel {}: seed={}, rule={}, options={}, player={}, aiDeck={}",
                duelId, seed, rule.id(), playerOptions, playerDeckName, aiDeckName);

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

    /**
     * Each deck carries its own shuffle seed so the duel seed reproduces the same two hands whichever
     * seat the first-turn roll gave each duellist: the challenger's deck always shuffles with
     * {@code seed} and the accepter's with {@code seed + 1} (see {@link #beginFirstTurnRoll}).
     */
    public void startDuel(ServerPlayer p1, ServerPlayer p2, long seed, DuelRule rule, PlayerOptions playerOptions,
                          Deck team1Deck, long team1Seed, Deck team2Deck, long team2Seed,
                          String team1Name, String team2Name) {
        if (playerToDuel.containsKey(p1.getUUID()) || playerToDuel.containsKey(p2.getUUID())) {
            LOGGER.warn("Cannot start duel - a player is already in a duel!");
            for (ServerPlayer duellist : new ServerPlayer[]{p1, p2}) {
                duellist.sendSystemMessage(Component.literal("A player is already in a duel; the duel was cancelled."));
            }
            return;
        }

        DuelOptions options = DuelOptions.of(seed, rule, playerOptions);
        UUID duelId = UUID.randomUUID();
        var handler = new ServerDuelHandler(p1, p2, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(p1.getUUID(), duelId);
        playerToDuel.put(p2.getUUID(), duelId);
        duelSeats.put(duelId, new ServerPlayer[]{p1, p2});

        LOGGER.info("Duel {}: seed={}, rule={}, options={}, first={}, decks=[{} (shuffle {}), {} (shuffle {})]",
                duelId, seed, rule.id(), playerOptions, p1.getName().getString(),
                team1Name, team1Seed, team2Name, team2Seed);

        Deck shuffled1 = team1Deck.shuffled(team1Seed);
        Deck shuffled2 = team2Deck.shuffled(team2Seed);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        int deck1Size = shuffled1.main().size();
        int extra1Size = shuffled1.extra().size();
        int deck2Size = shuffled2.main().size();
        int extra2Size = shuffled2.extra().size();
        PacketDistributor.sendToPlayer(p1, new DuelStartPayload(0, p2.getName().getString(),
                lp0, lp1, deck1Size, extra1Size, options.flags()));
        PacketDistributor.sendToPlayer(p2, new DuelStartPayload(1, p1.getName().getString(),
                lp0, lp1, deck2Size, extra2Size, options.flags()));

        session.setupDuel(shuffled1, shuffled2);
        session.process();
    }

    public void handleResponse(ServerPlayer player, byte[] response) {
        var duelId = playerToDuel.get(player.getUUID());
        if (duelId == null) return;
        DuelSession session = activeDuels.get(duelId);
        if (session == null || session.isEnded()) return;

        int seat = seatOf(duelId, player.getUUID());
        if (!session.listener().acceptsResponseFrom(seat)) {
            LOGGER.warn("Duel {}: dropping response from {} (seat {}), prompt belongs to player {}",
                    duelId, player.getName().getString(), seat, session.listener().pendingPlayer());
            return;
        }

        session.setResponse(response);
        processSoloAutoResponseByDuelId(duelId);
    }

    /** Engine index of {@code playerUUID} in {@code duelId}, or -1 when they are not a duellist. */
    private int seatOf(UUID duelId, UUID playerUUID) {
        ServerPlayer[] seats = duelSeats.get(duelId);
        if (seats == null) return -1;
        for (int seat = 0; seat < seats.length; seat++) {
            if (seats[seat] != null && seats[seat].getUUID().equals(playerUUID)) return seat;
        }
        return -1;
    }

    /** The player gives up: the opponent wins by surrender. */
    public void forfeit(ServerPlayer player) {
        UUID duelId = playerToDuel.get(player.getUUID());
        if (duelId == null) return;
        int seat = seatOf(duelId, player.getUUID());
        LOGGER.info("Duel {}: {} forfeits", duelId, player.getName().getString());
        finishDuel(duelId, 1 - seat, DuelEndPayload.REASON_SURRENDER);
    }

    private void handleLogout(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        clearPlayerCurrentDeck(playerUUID);
        duelInvites.remove(playerUUID);
        duelInvites.values().removeIf(pending -> pending.challengerUUID().equals(playerUUID));

        cancelFirstTurnRoll(playerUUID, player.getName().getString() + " disconnected.");

        UUID duelId = playerToDuel.get(playerUUID);
        if (duelId == null) return;
        int seat = seatOf(duelId, playerUUID);
        LOGGER.info("Duel {}: {} disconnected", duelId, player.getName().getString());
        finishDuel(duelId, 1 - seat, DuelEndPayload.REASON_DISCONNECT);
    }

    /**
     * End a duel with a result: tell every still-connected duellist, then close the session.
     * The single path for forfeit, disconnect and an engine end without MSG_WIN.
     */
    public void finishDuel(UUID duelId, int winner, int reason) {
        if (!activeDuels.containsKey(duelId)) return;
        var payload = new DuelEndPayload(winner, reason);
        for (ServerPlayer seat : duelSeats.getOrDefault(duelId, NO_SEATS)) {
            if (seat != null && !seat.hasDisconnected()) {
                PacketDistributor.sendToPlayer(seat, payload);
            }
        }
        endDuel(duelId);
    }

    public void endDuel(UUID duelId) {
        DuelSession session = activeDuels.remove(duelId);
        soloHandlers.remove(duelId);
        duelSeats.remove(duelId);
        if (session != null) {
            session.close();
            playerToDuel.values().removeIf(id -> id.equals(duelId));
        }
    }

    public UUID getPlayerActiveDuel(ServerPlayer player) {
        return playerToDuel.get(player.getUUID());
    }

    // --- First-turn roll (edopro's pre-duel rock-paper-scissors, generic_duel.cpp:474-565) ---

    /** One pending roll: the two duellists with everything the duel needs once they have answered. */
    private static final class FirstTurnRoll {
        final ServerPlayer[] players;
        final Deck[] decks;
        /** Shuffle seed per deck, keyed to the challenge role, not to the seat the roll hands out. */
        final long[] deckSeeds;
        final String[] deckNames;
        final long seed;
        final DuelRule rule;
        final PlayerOptions playerOptions;
        final FirstTurnLobby.Hand[] hands = new FirstTurnLobby.Hand[2];
        /** Seat that won the roll and picks who goes first; -1 while the hands are still coming in. */
        int chooser = -1;
        long promptedAtMillis = System.currentTimeMillis();

        FirstTurnRoll(ServerPlayer p0, ServerPlayer p1, long seed, DuelRule rule, PlayerOptions playerOptions,
                      Deck deck0, Deck deck1, String deckName0, String deckName1) {
            this.players = new ServerPlayer[]{p0, p1};
            this.decks = new Deck[]{deck0, deck1};
            this.deckSeeds = new long[]{seed, seed + 1};
            this.deckNames = new String[]{deckName0, deckName1};
            this.seed = seed;
            this.rule = rule;
            this.playerOptions = playerOptions;
        }

        int seatOf(UUID playerUUID) {
            return players[0].getUUID().equals(playerUUID) ? 0 : 1;
        }
    }

    /** Whether the player is in a duel or still answering a first-turn roll. */
    public boolean isBusy(ServerPlayer player) {
        return playerToDuel.containsKey(player.getUUID()) || firstTurnRolls.containsKey(player.getUUID());
    }

    /** Rolls for the first turn; the duel starts once the winner has chosen who goes first. */
    public void beginFirstTurnRoll(ServerPlayer challenger, ServerPlayer accepter, long seed, DuelRule rule,
                                   PlayerOptions playerOptions, Deck challengerDeck, Deck accepterDeck,
                                   String challengerDeckName, String accepterDeckName) {
        var roll = new FirstTurnRoll(challenger, accepter, seed, rule, playerOptions,
                challengerDeck, accepterDeck, challengerDeckName, accepterDeckName);
        firstTurnRolls.put(challenger.getUUID(), roll);
        firstTurnRolls.put(accepter.getUUID(), roll);
        promptHands(roll);
    }

    /** A duellist throws their hand; equal hands are replayed, as edopro replays ties. */
    public void submitHand(ServerPlayer player, FirstTurnLobby.Hand hand) {
        var roll = firstTurnRolls.get(player.getUUID());
        if (roll == null || roll.chooser >= 0) {
            player.sendSystemMessage(Component.literal("No first-turn roll to answer."));
            return;
        }
        int seat = roll.seatOf(player.getUUID());
        if (roll.hands[seat] != null) {
            player.sendSystemMessage(Component.literal("You already chose " + name(roll.hands[seat]) + "."));
            return;
        }
        roll.hands[seat] = hand;
        player.sendSystemMessage(Component.literal("You chose " + name(hand) + "."));
        if (roll.hands[0] == null || roll.hands[1] == null) return;

        for (int seatIndex = 0; seatIndex < 2; seatIndex++) {
            roll.players[seatIndex].sendSystemMessage(Component.literal(
                    "You: " + name(roll.hands[seatIndex]) + " vs " + name(roll.hands[1 - seatIndex])));
        }

        int winner = FirstTurnLobby.resolve(roll.hands[0], roll.hands[1]);
        if (winner == FirstTurnLobby.TIE) {
            promptHands(roll);
            return;
        }
        roll.chooser = winner;
        roll.promptedAtMillis = System.currentTimeMillis();
        roll.players[winner].sendSystemMessage(Component.literal("You won the roll. Go first? ")
                .append(button("Yes", "/duel first yes")).append(" ")
                .append(button("No", "/duel first no")));
        roll.players[1 - winner].sendSystemMessage(Component.literal(
                roll.players[winner].getName().getString() + " won the roll and is choosing who goes first."));
    }

    /** The roll winner answers; whoever goes first becomes engine player 0 (generic_duel.cpp:547-565). */
    public void submitFirstTurnChoice(ServerPlayer player, boolean goFirst) {
        var roll = firstTurnRolls.get(player.getUUID());
        if (roll == null || roll.chooser < 0 || roll.seatOf(player.getUUID()) != roll.chooser) {
            player.sendSystemMessage(Component.literal("Nothing to choose right now."));
            return;
        }
        int first = goFirst ? roll.chooser : 1 - roll.chooser;
        int second = 1 - first;
        removeRoll(roll);
        for (ServerPlayer duellist : roll.players) {
            duellist.sendSystemMessage(Component.literal(
                    roll.players[first].getName().getString() + " goes first."));
        }
        startDuel(roll.players[first], roll.players[second], roll.seed, roll.rule, roll.playerOptions,
                roll.decks[first], roll.deckSeeds[first], roll.decks[second], roll.deckSeeds[second],
                roll.deckNames[first], roll.deckNames[second]);
    }

    private void promptHands(FirstTurnRoll roll) {
        roll.hands[0] = null;
        roll.hands[1] = null;
        roll.promptedAtMillis = System.currentTimeMillis();
        for (ServerPlayer duellist : roll.players) {
            duellist.sendSystemMessage(Component.literal("Rock-paper-scissors for the first turn: ")
                    .append(button("Rock", "/duel hand rock")).append(" ")
                    .append(button("Paper", "/duel hand paper")).append(" ")
                    .append(button("Scissors", "/duel hand scissors")));
        }
    }

    /** Drops rolls nobody answered in time, as {@link DuelCommand#INVITE_TIMEOUT_MS} drops invites. */
    private void expireFirstTurnRolls() {
        if (firstTurnRolls.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (FirstTurnRoll roll : new LinkedHashSet<>(firstTurnRolls.values())) {
            if (now - roll.promptedAtMillis > DuelCommand.INVITE_TIMEOUT_MS) {
                removeRoll(roll);
                for (ServerPlayer duellist : roll.players) {
                    if (!duellist.hasDisconnected()) {
                        duellist.sendSystemMessage(Component.literal(
                                "The first-turn roll timed out; the duel was cancelled."));
                    }
                }
            }
        }
    }

    /** Cancels the roll this player is in, telling whoever is left why; false when there was none. */
    public boolean cancelFirstTurnRoll(UUID playerUUID, String reason) {
        var roll = firstTurnRolls.get(playerUUID);
        if (roll == null) return false;
        removeRoll(roll);
        for (ServerPlayer duellist : roll.players) {
            if (!duellist.hasDisconnected()) {
                duellist.sendSystemMessage(Component.literal(reason + " The duel was cancelled."));
            }
        }
        return true;
    }

    /** Only drops mappings that still point at {@code roll}, so a stale roll cannot unseat a live one. */
    private void removeRoll(FirstTurnRoll roll) {
        for (ServerPlayer duellist : roll.players) firstTurnRolls.remove(duellist.getUUID(), roll);
    }

    private static String name(FirstTurnLobby.Hand hand) {
        return hand.name().charAt(0) + hand.name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** A chat button that runs {@code command} when clicked. */
    private static Component button(String label, String command) {
        return Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    public DeckRegistry getDeckRegistry() { return deckRegistry; }

    public void setPlayerCurrentDeck(UUID player, DuelDeckPayload selection) {
        // The current structural/copy checks are shared by every rule; rechecked when a duel starts.
        var problems = DeckValidator.problems(selection.deck(), DuelRule.MR5);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        playerCurrentDeck.put(player, selection);
    }

    public Optional<String> getPlayerCurrentDeck(UUID player) {
        return Optional.ofNullable(playerCurrentDeck.get(player)).map(DuelDeckPayload::name);
    }

    public void clearPlayerCurrentDeck(UUID player) {
        playerCurrentDeck.remove(player);
    }

    /** Resolves the uploaded snapshot; the server never opens a player's named deck file. */
    public Deck resolveDeck(ServerPlayer player) throws IOException {
        return resolveDeck(player.getUUID());
    }

    Deck resolveDeck(UUID player) throws IOException {
        var selection = playerCurrentDeck.get(player);
        if (selection == null) {
            throw new IOException("No deck set; run /duel deck set <name>");
        }
        return selection.deck();
    }
}

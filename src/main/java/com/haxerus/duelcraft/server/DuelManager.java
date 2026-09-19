package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.core.data.CardData;
import com.haxerus.duelcraft.core.data.CardCatalog;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.server.collection.*;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DuelManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static DuelManager instance;
    private DuelEngine engine;
    private DeckRegistry deckRegistry;
    private final CollectionSnapshotStore snapshots = new CollectionSnapshotStore();
    private final Map<UUID, ManagedDuelSession> activeDuels = new HashMap<>();
    private final Map<UUID, UUID> playerToDuel = new HashMap<>();
    private final Map<UUID, UUID[]> duelSeats = new HashMap<>();
    private final Map<UUID, SoloDuelHandler> soloHandlers = new HashMap<>();
    private final Set<UUID> startingPlayers = new HashSet<>();
    private final CollectionService collections;
    private final CollectionPayloadHandler collectionHandler;
    private final DuelPreparationService preparation;
    private final Players players;
    private final SessionFactory sessions;
    private java.util.function.UnaryOperator<SessionFactory> nextSessionDecorator;

    interface Players {
        boolean online(UUID id);
        String name(UUID id);
        Optional<PlayerCollectionData> data(UUID id);
        void persist(UUID id, PlayerCollectionData data);
        void send(UUID id, CustomPacketPayload payload);
        void message(UUID id, Component message);
    }
    @FunctionalInterface interface SessionFactory {
        ManagedDuelSession create(DuelOptions options, DuelEventListener listener);
    }

    DuelManager(CollectionService collections, Players players, SessionFactory sessions) {
        this.collections = collections;
        this.players = players;
        this.sessions = sessions;
        collectionHandler = new CollectionPayloadHandler(collections, snapshots);
        preparation = new DuelPreparationService(new DuelPreparationService.Host() {
            public boolean online(UUID id) { return players.online(id); }
            public String name(UUID id) { return players.name(id); }
            public boolean isDueling(UUID id) { return playerToDuel.containsKey(id) || startingPlayers.contains(id); }
            public DuelPreparationService.Selection selection(UUID id, DuelRule rule) { return DuelManager.this.selection(id, rule); }
            public boolean start(DuelPreparationService.PreparedDuel duel, int firstSeat) { return startPrepared(duel, firstSeat); }
            public void changed(UUID id, PreparationResult result) { publish(id, result); }
        }, () -> ThreadLocalRandom.current().nextLong());
    }

    /** DEV-only one-shot fault injection; consumed before allocation even if startup throws. */
    void decorateNextSession(java.util.function.UnaryOperator<SessionFactory> decorator) {
        if (net.neoforged.fml.loading.FMLEnvironment.production) throw new IllegalStateException("Development fixture only");
        nextSessionDecorator = Objects.requireNonNull(decorator);
    }

    public static DuelManager get() { return instance; }
    public static void onServerStarting(ServerStartingEvent event) {
        var data = CardData.load().join();
        try {
            var service = new CollectionService(CardCatalog.load(data.database()),
                    new DeckUsePolicy(ServerConfig.requireCardOwnership(), DeckUseCheckEvent.restriction(NeoForge.EVENT_BUS)));
            var engine = new DuelEngine(List.of(data.database().toString()), data.scriptPaths());
            var manager = new DuelManager(service, onlinePlayers(event.getServer()),
                    (options, listener) -> new DuelSession(engine, options, listener));
            manager.engine = engine;
            manager.deckRegistry = DeckRegistry.open(FMLPaths.GAMEDIR.get().resolve("duelcraft/decks"));
            instance = manager;
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot load collection card facts", exception);
        }
    }
    private static Players onlinePlayers(MinecraftServer server) {
        return new Players() {
            private ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
            public boolean online(UUID id) { var p = player(id); return p != null && !p.hasDisconnected(); }
            public String name(UUID id) { var p = player(id); return p == null ? "Opponent" : p.getName().getString(); }
            public Optional<PlayerCollectionData> data(UUID id) { var p = player(id); return p == null ? Optional.empty() : p.getData(CollectionAttachments.COLLECTION).data(); }
            public void persist(UUID id, PlayerCollectionData data) { var p = player(id); if (p != null) p.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(data)); }
            public void send(UUID id, CustomPacketPayload payload) { if (online(id)) PacketDistributor.sendToPlayer(player(id), payload); }
            public void message(UUID id, Component message) { if (online(id)) player(id).sendSystemMessage(message); }
        };
    }
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) instance.revalidate(player.getUUID(), DuelRule.MR5);
    }
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) instance.logout(player.getUUID());
    }
    public static void onServerTick(ServerTickEvent.Post event) {
        if (instance != null) instance.preparation.expire(System.currentTimeMillis());
    }
    public static void onServerStopped(ServerStoppedEvent event) {
        if (instance != null) { instance.shutdown(); instance = null; }
        ServerConfig.reset();
    }
    public void shutdown() {
        for (var id : new ArrayList<>(activeDuels.keySet())) endDuel(id);
        preparation.clear();
        startingPlayers.clear();
        nextSessionDecorator = null;
        snapshots.clear();
        if (engine != null) { engine.close(); engine = null; }
    }
    public DuelPreparationService preparation() { return preparation; }
    public CollectionPayloadHandler collectionHandler() { return collectionHandler; }
    public DeckRegistry getDeckRegistry() { return deckRegistry; }
    public boolean isBusy(ServerPlayer player) { return isBusy(player.getUUID()); }
    boolean isBusy(UUID id) { return playerToDuel.containsKey(id) || preparation.isPreparing(id) || startingPlayers.contains(id); }
    public UUID getPlayerActiveDuel(ServerPlayer player) { return playerToDuel.get(player.getUUID()); }

    /** Rechecks persisted activation without ever deleting the saved list or granting copies. */
    void revalidate(UUID owner, DuelRule rule) {
        var before = players.data(owner).orElse(null);
        if (before == null) return;
        var change = collections.revalidateActive(before, owner, rule);
        if (!change.success()) {
            tell(owner, "Deck permission check failed; try again or contact the server administrator.");
        } else if (!before.equals(change.data())) {
            players.persist(owner, change.data()); snapshots.invalidate(owner);
            tell(owner, "Active deck cleared: " + explain(change.eligibility()));
        }
    }
    private DuelPreparationService.Selection selection(UUID owner, DuelRule rule) {
        var data = players.data(owner).orElse(null);
        var deck = data == null || data.activeDeckId() == null ? null : data.decks().get(data.activeDeckId());
        if (deck == null) {
            tell(owner, "No active deck; use /duel deck set <name> or activate a saved list.");
            notifyUnreadyPeer(owner);
            return new DuelPreparationService.Selection(null, collections.emptyReport());
        }
        var change = collections.revalidateActive(data, owner, rule);
        if (!change.success()) {
            tell(owner, "Deck permission check failed; try again or contact the server administrator.");
            notifyUnreadyPeer(owner);
            return new DuelPreparationService.Selection(null, collections.emptyReport());
        }
        if (!data.equals(change.data())) {
            players.persist(owner, change.data()); snapshots.invalidate(owner);
            tell(owner, "Active deck cleared: " + explain(change.eligibility()));
            notifyUnreadyPeer(owner);
        }
        return new DuelPreparationService.Selection(change.data().activeDeckId() == null ? null : deck, change.eligibility());
    }

    private void notifyUnreadyPeer(UUID owner) {
        var view = preparation.view(owner, System.currentTimeMillis());
        if (view.opponentId() != null) tell(view.opponentId(), "Opponent's deck is not ready");
    }
    public Optional<String> getPlayerCurrentDeck(UUID id) {
        revalidate(id, DuelRule.MR5);
        return players.data(id).map(data -> data.activeDeckId() == null ? null : data.decks().get(data.activeDeckId())).map(SavedDeck::name);
    }
    public Deck resolveDeck(ServerPlayer player) throws IOException { return resolveDeck(player.getUUID()); }
    Deck resolveDeck(UUID owner) throws IOException {
        var selected = selection(owner, DuelRule.MR5);
        if (selected.deck() == null || !selected.eligibility().eligible()) throw new IOException("No eligible active deck");
        return selected.deck().cards().toDuelDeck();
    }
    public boolean clearPlayerCurrentDeck(ServerPlayer player) { return clearPlayerCurrentDeck(player.getUUID()); }
    boolean clearPlayerCurrentDeck(UUID owner) {
        var before = players.data(owner).orElse(null);
        if (before == null) return false;
        var change = collections.clearActive(before, before.revision(), isBusy(owner));
        if (!change.success()) { tell(owner, "Cannot clear deck: " + change.error()); return false; }
        players.persist(owner, change.data()); snapshots.invalidate(owner);
        tell(owner, "Current deck cleared."); return true;
    }
    private boolean current(DuelPreparationService.PreparedPlayer prepared, DuelRule rule) {
        var owner = prepared.id();
        if (!players.online(owner) || playerToDuel.containsKey(owner)) return false;
        var data = players.data(owner).orElse(null);
        if (data == null || !prepared.deck().id().equals(data.activeDeckId())
                || !prepared.deck().equals(data.decks().get(data.activeDeckId()))) {
            tell(owner, "Your active deck changed; prepare the duel again."); return false;
        }
        var selected = selection(owner, rule);
        return selected.deck() != null && selected.eligibility().eligible();
    }
    private boolean startPrepared(DuelPreparationService.PreparedDuel prepared, int firstSeat) {
        // Evaluate both owners so only each owner receives their private reasons.
        boolean a = current(prepared.challenger(), prepared.settings().rule());
        boolean b = current(prepared.accepter(), prepared.settings().rule());
        if (!a || !b) {
            if (!a) tell(prepared.accepter().id(), "Opponent's deck is not ready");
            if (!b) tell(prepared.challenger().id(), "Opponent's deck is not ready");
            return false;
        }
        var first = firstSeat == 0 ? prepared.challenger() : prepared.accepter();
        var second = firstSeat == 0 ? prepared.accepter() : prepared.challenger();
        LOGGER.info("Prepared duel {}: seed={}, rule={}, first={}, decks=[{} (shuffle {}), {} (shuffle {})]",
                prepared.id(), prepared.settings().seed(), prepared.settings().rule().id(), first.id(),
                first.deck().name(), first.shuffleSeed(), second.deck().name(), second.shuffleSeed());
        return start(prepared.settings(), new UUID[]{first.id(), second.id()},
                first.deck().cards().toDuelDeck().shuffled(first.shuffleSeed()),
                second.deck().cards().toDuelDeck().shuffled(second.shuffleSeed()));
    }
    public boolean startSoloDuel(ServerPlayer player, DuelSettings settings, Deck aiDeck) {
        return startSoloDuel(player.getUUID(), settings, aiDeck);
    }
    boolean startSoloDuel(UUID owner, DuelSettings settings, Deck aiDeck) {
        if (isBusy(owner)) { tell(owner, "Cannot start while preparing or dueling."); return false; }
        var flow = preparation.view(owner, System.currentTimeMillis());
        if (flow.flowId() != null) preparation.cancel(owner, flow.flowId(), System.currentTimeMillis());
        startingPlayers.add(owner);
        boolean success = false;
        try {
            var selected = selection(owner, settings.rule());
            if (selected.deck() == null || !selected.eligibility().eligible()) return false;
            var snapshot = new DuelPreparationService.PreparedPlayer(owner, selected.deck(), settings.seed());
            if (!current(snapshot, settings.rule())) return false;
            var human = snapshot.deck().cards().toDuelDeck();
            var ai = aiDeck == null ? human : aiDeck;
            if (!DeckValidator.problems(ai, settings.rule()).isEmpty()) { tell(owner, "The AI deck is not legal."); return false; }
            LOGGER.info("Solo duel: seed={}, rule={}, player={}, deck={}, AI shuffle={}",
                    settings.seed(), settings.rule().id(), owner, snapshot.deck().name(), settings.seed() + 1);
            success = start(settings, new UUID[]{owner, null}, human.shuffled(settings.seed()), ai.shuffled(settings.seed() + 1));
            return success;
        } finally {
            startingPlayers.remove(owner);
            preparation.startFinished(owner, success);
        }
    }
    /** Only verified immutable selections reach this native boundary. */
    private boolean start(DuelSettings settings, UUID[] seats, Deck first, Deck second) {
        UUID id = UUID.randomUUID();
        ManagedDuelSession session = null;
        try {
            java.util.function.Consumer<Boolean> complete = winSent -> {
                if (winSent) endDuel(id); else finishDuel(id, DuelEndPayload.WINNER_DRAW, 0);
            };
            DuelEventListener listener;
            if (seats[1] == null) listener = new SoloDuelHandler(payload -> players.send(seats[0], payload), complete,
                    response -> handleSoloAutoResponse(id, response));
            else listener = new ServerDuelHandler((seat, payload) -> players.send(seats[seat], payload), complete);
            var options = DuelOptions.of(settings.seed(), settings.rule(), settings.options());
            var decorator = nextSessionDecorator;
            nextSessionDecorator = null;
            session = (decorator == null ? sessions : decorator.apply(sessions)).create(options, listener);
            activeDuels.put(id, session);
            duelSeats.put(id, seats);
            for (var owner : seats) if (owner != null) playerToDuel.put(owner, id);
            if (listener instanceof SoloDuelHandler solo) soloHandlers.put(id, solo);
            for (int seat = 0; seat < seats.length; seat++) if (seats[seat] != null) {
                var deck = seat == 0 ? first : second;
                players.send(seats[seat], new DuelStartPayload(seat, seats[1 - seat] == null ? "AI Opponent" : players.name(seats[1 - seat]),
                        options.team1().lp(), options.team2().lp(), deck.main().size(), deck.extra().size(), options.flags()));
            }
            // setupDuel emits refreshes, so every human must receive Start first.
            session.setupDuel(first, second);
            session.process();
            processSoloAutoResponse(id);
            return true; // Synchronous completion is success even if ownership was already removed.
        } catch (RuntimeException exception) {
            LOGGER.error("Duel {} failed to start", id, exception);
            if (session != null && activeDuels.get(id) == session) endDuel(id, false);
            return false;
        }
    }
    public void handleResponse(ServerPlayer player, byte[] response) {
        var id = playerToDuel.get(player.getUUID());
        var session = activeDuels.get(id);
        if (session == null || session.isEnded() || !session.listener().acceptsResponseFrom(seatOf(id, player.getUUID()))) return;
        session.setResponse(response); processSoloAutoResponse(id);
    }
    public void handleSoloAutoResponse(UUID id, byte[] response) {
        var session = activeDuels.get(id);
        if (session == null || session.isEnded()) return;
        session.setResponse(response); processSoloAutoResponse(id);
    }
    private void processSoloAutoResponse(UUID id) {
        var handler = soloHandlers.get(id);
        if (handler != null) { var response = handler.consumePendingAutoResponse(); if (response != null) response.run(); }
    }
    private int seatOf(UUID id, UUID owner) {
        var seats = duelSeats.get(id);
        if (seats == null) return -1;
        return owner.equals(seats[0]) ? 0 : owner.equals(seats[1]) ? 1 : -1;
    }
    public void forfeit(ServerPlayer player) { forfeit(player.getUUID()); }
    void forfeit(UUID owner) {
        var id = playerToDuel.get(owner);
        if (id != null) finishDuel(id, 1 - seatOf(id, owner), DuelEndPayload.REASON_SURRENDER);
        else { var view = preparation.view(owner, System.currentTimeMillis()); if (view.flowId() != null) preparation.cancel(owner, view.flowId(), System.currentTimeMillis()); }
    }
    private void logout(UUID owner) {
        snapshots.invalidate(owner); preparation.logout(owner);
        var id = playerToDuel.get(owner);
        if (id != null) finishDuel(id, 1 - seatOf(id, owner), DuelEndPayload.REASON_DISCONNECT);
    }
    public void finishDuel(UUID id, int winner, int reason) {
        if (!activeDuels.containsKey(id)) return;
        for (var owner : duelSeats.get(id)) if (owner != null) players.send(owner, new DuelEndPayload(winner, reason));
        endDuel(id);
    }
    public void endDuel(UUID id) { endDuel(id, true); }
    private void endDuel(UUID id, boolean notify) {
        var session = activeDuels.remove(id);
        var seats = duelSeats.remove(id);
        soloHandlers.remove(id);
        if (seats != null) for (var owner : seats) if (owner != null) playerToDuel.remove(owner, id);
        try { if (session != null) session.close(); }
        catch (RuntimeException exception) { LOGGER.error("Closing duel {} failed", id, exception); }
        if (notify && seats != null) for (var owner : seats) if (owner != null) preparation.duelEnded(owner);
    }
    private void publish(UUID owner, PreparationResult result) {
        if (!players.online(owner)) return;
        var view = preparation.view(owner, System.currentTimeMillis());
        players.send(owner, new PreparationStatePayload(null, result, view));
        if (result == PreparationResult.START_FAILED) tell(owner, "Duel could not start. Check your active deck and try again.");
        else if (result == PreparationResult.STALE) tell(owner, "Duel preparation expired.");
        else if (result == PreparationResult.OFFLINE) tell(owner, "Duel preparation cancelled because a player disconnected.");
        else if (view.mode() == PreparationView.Mode.INVITED && !view.outgoing())
            players.message(owner, Component.literal(view.opponentName() + " challenged you. ").append(button("Accept", "/duel accept")).append(" ").append(button("Decline", "/duel invite decline")));
        else if (view.mode() == PreparationView.Mode.RPS && !view.ownHandSubmitted())
            players.message(owner, Component.literal("Rock-paper-scissors: ").append(button("Rock", "/duel hand rock")).append(" ").append(button("Paper", "/duel hand paper")).append(" ").append(button("Scissors", "/duel hand scissors")));
        else if (view.mode() == PreparationView.Mode.FIRST_CHOICE && view.canChooseFirst())
            players.message(owner, Component.literal("You won the roll. Go first? ").append(button("Yes", "/duel first yes")).append(" ").append(button("No", "/duel first no")));
    }
    private void tell(UUID owner, String message) { players.message(owner, Component.literal(message)); }
    private static String explain(DeckEligibility.Report report) {
        if (report.restrictionReason() != null) return report.restrictionReason();
        if (!report.problems().isEmpty()) return report.problems().stream().map(issue -> issue.key() + " (card " + issue.code() + ")").collect(java.util.stream.Collectors.joining("; "));
        if (report.ownershipRequired() && !report.missing().isEmpty()) return "Missing deposited copies: " + report.missing();
        return "Deck eligibility could not be confirmed.";
    }
    private static Component button(String label, String command) {
        return Component.literal("[" + label + "]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }
}

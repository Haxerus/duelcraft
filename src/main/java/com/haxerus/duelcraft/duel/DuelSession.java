package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.FieldQuery;
import com.haxerus.duelcraft.duel.message.MessageParser;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DuelSession implements ManagedDuelSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuelSession.class);
    private static final AtomicLong NEXT_TAG = new AtomicLong(1);
    private final DuelEngine engine;
    private final long duelHandle;
    private final DuelEventListener listener;
    /** Tag native log lines carry while this session is inside the engine. */
    private final String logTag = "duel#" + NEXT_TAG.getAndIncrement();
    private boolean ended;
    private boolean closed;

    public DuelSession(DuelEngine engine, DuelOptions options, DuelEventListener listener) {
        this.engine = engine;
        this.listener = listener;
        // Under the tag already: creating the duel loads the bootstrap scripts, which log.
        OcgCore.setCurrentDuel(logTag);
        try {
            this.duelHandle = OcgCore.nCreateDuel(
                engine.getHandle(),
                options.seed(), options.flags(),
                options.team1().lp(), options.team1().startHand(), options.team1().drawPerTurn(),
                options.team2().lp(), options.team2().startHand(), options.team2().drawPerTurn()
            );
        } finally {
            OcgCore.setCurrentDuel(null);
        }
        if (this.duelHandle == 0) {
            throw new IllegalStateException("Failed to create duel");
        }
    }

    public void setupDuel(Deck team1Deck, Deck team2Deck) {
        OcgCore.setCurrentDuel(logTag);
        try {
            addCardsAndStart(team1Deck, team2Deck);
        } finally {
            OcgCore.setCurrentDuel(null);
        }
    }

    private void addCardsAndStart(Deck team1Deck, Deck team2Deck) {
        long eng = engine.getHandle();

        // Team 1 main deck (reverse order for stack behavior)
        var main1 = team1Deck.main();
        for (int i = main1.size() - 1; i >= 0; i--) {
            OcgCore.nDuelNewCard(eng, duelHandle, 0, 0, main1.get(i),
                    0, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
        }
        // Team 1 extra deck
        for (int code : team1Deck.extra()) {
            OcgCore.nDuelNewCard(eng, duelHandle, 0, 0, code,
                    0, OcgConstants.LOCATION_EXTRA, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
        }

        // Team 2 main deck
        var main2 = team2Deck.main();
        for (int i = main2.size() - 1; i >= 0; i--) {
            OcgCore.nDuelNewCard(eng, duelHandle, 1, 0, main2.get(i),
                    1, OcgConstants.LOCATION_DECK, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
        }
        // Team 2 extra deck
        for (int code : team2Deck.extra()) {
            OcgCore.nDuelNewCard(eng, duelHandle, 1, 0, code,
                    1, OcgConstants.LOCATION_EXTRA, 0, OcgConstants.POS_FACEDOWN_DEFENSE);
        }

        OcgCore.nStartDuel(eng, duelHandle);
        // Both extra decks; the opening hands follow as MSG_DRAW (RefreshSchedule.atDuelStart).
        emitRefreshes(RefreshSchedule.atDuelStart());
    }

    /**
     * Process the duel until it needs player input (AWAITING) or ends.
     * Messages are dispatched to the listener as they arrive.
     * Call this after {@link #setupDuel} to start, and after {@link #setResponse} to resume.
     */
    public void process() {
        OcgCore.setCurrentDuel(logTag);
        try {
            processUntilInput();
        } finally {
            OcgCore.setCurrentDuel(null);
        }
    }

    private void processUntilInput() {
        if (ended) return;

        long eng = engine.getHandle();
        int status;
        boolean autoResponded;
        do {
            autoResponded = false;
            status = OcgCore.nDuelProcess(eng, duelHandle);
            byte[] messageBuffer = OcgCore.nDuelGetMessage(eng, duelHandle);
            if (messageBuffer != null && messageBuffer.length > 0) {
                List<DuelMessage> messages = MessageParser.parse(messageBuffer);
                for (DuelMessage msg : messages) {
                    // Auto-pass empty chain prompts (no chainable cards, not forced).
                    // OCG_DuelProcess stops the moment a processor unit needs an answer
                    // (ocgapi.cpp:115-118, processor_visit.cpp:14-21), so the prompt is always the
                    // last record of its batch and nothing is discarded by leaving the loop here.
                    if (msg instanceof DuelMessage.SelectChain chain
                            && chain.count() == 0 && !chain.forced()) {
                        LOGGER.debug("[Session] Auto-passing empty chain for player {}", chain.player());
                        OcgCore.nDuelSetResponse(eng, duelHandle,
                                ResponseBuilder.selectChain(-1));
                        autoResponded = true;
                        break;
                    }

                    emitRefreshes(RefreshSchedule.before(msg));
                    int result = listener.onMessage(msg);
                    if (result != DuelEventListener.CONTINUE) {
                        // Only the listener ends the duel here: AWAIT_RESPONSE leaves a prompt live,
                        // and the post-loop DUEL_STATUS_END check covers an end with no prompt owing.
                        if (result == DuelEventListener.DUEL_ENDED) {
                            ended = true;
                            listener.onDuelEnd();
                        }
                        return;
                    }
                    emitRefreshes(RefreshSchedule.after(msg));
                }
            }
        } while (status == OcgConstants.DUEL_STATUS_CONTINUE || autoResponded);

        if (status == OcgConstants.DUEL_STATUS_END) {
            ended = true;
            listener.onDuelEnd();
        }
    }

    /** Runs each scheduled query and hands the result to the listener as UpdateData / UpdateCard. */
    private void emitRefreshes(List<RefreshSchedule.Refresh> refreshes) {
        for (RefreshSchedule.Refresh refresh : refreshes) {
            if (refresh.isWholeLocation()) emitLocation(refresh);
            else emitSingle(refresh);
        }
    }

    private void emitLocation(RefreshSchedule.Refresh refresh) {
        byte[] data = queryLocation(refresh.flags(), refresh.player(), refresh.location());
        if (data == null || data.length == 0) return;
        List<QueriedCard> cards;
        try {
            cards = FieldQuery.parseLocation(data);
        } catch (RuntimeException e) {
            LOGGER.warn("[Query] Failed to parse location p={} loc=0x{}: {}",
                    refresh.player(), Integer.toHexString(refresh.location()), e.getMessage());
            return;
        }
        listener.onMessage(new DuelMessage.UpdateData(refresh.player(), refresh.location(), cards));
    }

    private void emitSingle(RefreshSchedule.Refresh refresh) {
        byte[] data = query(refresh.flags(), refresh.player(), refresh.location(), refresh.sequence(), 0);
        if (data == null || data.length == 0) return;
        QueriedCard card;
        try {
            card = FieldQuery.parse(data);
        } catch (RuntimeException e) {
            LOGGER.warn("[Query] Failed to parse slot p={} loc=0x{} seq={}: {}",
                    refresh.player(), Integer.toHexString(refresh.location()),
                    refresh.sequence(), e.getMessage());
            return;
        }
        listener.onMessage(new DuelMessage.UpdateCard(refresh.player(), refresh.location(),
                refresh.sequence(), card));
    }

    /**
     * Submit a player response and resume processing.
     */
    public void setResponse(byte[] response) {
        if (ended) return;
        LOGGER.debug("[Session] setResponse: {} bytes", response.length);
        OcgCore.nDuelSetResponse(engine.getHandle(), duelHandle, response);
        process();
    }

    // --- Queries ---

    public int queryCount(int team, int location) {
        return OcgCore.nDuelQueryCount(engine.getHandle(), duelHandle, team, location);
    }

    public byte[] query(int flags, int controller, int location, int sequence, int overlaySequence) {
        return OcgCore.nDuelQuery(engine.getHandle(), duelHandle,
                flags, controller, location, sequence, overlaySequence);
    }

    public byte[] queryLocation(int flags, int controller, int location) {
        return OcgCore.nDuelQueryLocation(engine.getHandle(), duelHandle,
                flags, controller, location);
    }

    public boolean isEnded() {
        return ended;
    }

    public DuelEventListener listener() {
        return listener;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        OcgCore.nDestroyDuel(engine.getHandle(), duelHandle);
    }
}

package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.message.MessageParser;
import com.haxerus.duelcraft.duel.message.ParsedEntry;

import java.util.List;

public class DuelSession implements AutoCloseable {
    private final DuelEngine engine;
    private final long duelHandle;
    private final DuelEventListener listener;
    private boolean ended;

    public DuelSession(DuelEngine engine, DuelOptions options, DuelEventListener listener) {
        this.engine = engine;
        this.listener = listener;
        this.duelHandle = OcgCore.nCreateDuel(
            engine.getHandle(),
            options.seed(), options.flags(),
            options.team1().lp(), options.team1().startHand(), options.team1().drawPerTurn(),
            options.team2().lp(), options.team2().startHand(), options.team2().drawPerTurn()
        );
        if (this.duelHandle == 0) {
            throw new IllegalStateException("Failed to create duel");
        }
    }

    public void setupDuel(Deck team1Deck, Deck team2Deck) {
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
    }

    /**
     * Process the duel until it needs player input (AWAITING) or ends.
     * Messages are dispatched to the listener as they arrive.
     * Call this after {@link #setupDuel} to start, and after {@link #setResponse} to resume.
     */
    public void process() {
        if (ended) return;

        long eng = engine.getHandle();
        int status;
        do {
            status = OcgCore.nDuelProcess(eng, duelHandle);
            byte[] messageBuffer = OcgCore.nDuelGetMessage(eng, duelHandle);
            if (messageBuffer != null && messageBuffer.length > 0) {
                List<ParsedEntry> entries = MessageParser.parse(messageBuffer);
                for (ParsedEntry entry : entries) {
                    int result = listener.onMessage(entry.message(), entry.raw());
                    if (result != 0) {
                        if (result == 2 || status == OcgConstants.DUEL_STATUS_END) {
                            ended = true;
                            listener.onDuelEnd();
                        }
                        return;
                    }
                }
            }
        } while (status == OcgConstants.DUEL_STATUS_CONTINUE);

        if (status == OcgConstants.DUEL_STATUS_END) {
            ended = true;
            listener.onDuelEnd();
        }
    }

    /**
     * Submit a player response and resume processing.
     */
    public void setResponse(byte[] response) {
        if (ended) return;
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

    public byte[] queryField() {
        return OcgCore.nDuelQueryField(engine.getHandle(), duelHandle);
    }

    public boolean isEnded() {
        return ended;
    }

    @Override
    public void close() {
        OcgCore.nDestroyDuel(engine.getHandle(), duelHandle);
    }
}

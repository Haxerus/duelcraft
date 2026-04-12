package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.MessageParser;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.haxerus.duelcraft.duel.message.QueryParser;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import static com.haxerus.duelcraft.core.OcgConstants.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import java.util.List;

public class DuelSession implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuelSession.class);
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
        boolean autoResponded;
        do {
            autoResponded = false;
            status = OcgCore.nDuelProcess(eng, duelHandle);
            byte[] messageBuffer = OcgCore.nDuelGetMessage(eng, duelHandle);
            if (messageBuffer != null && messageBuffer.length > 0) {
                List<DuelMessage> messages = MessageParser.parse(messageBuffer);
                for (DuelMessage msg : messages) {
                    // Auto-pass empty chain prompts (no chainable cards, not forced)
                    if (msg instanceof DuelMessage.SelectChain chain
                            && chain.count() == 0 && !chain.forced()) {
                        LOGGER.debug("[Session] Auto-passing empty chain for player {}", chain.player());
                        OcgCore.nDuelSetResponse(eng, duelHandle,
                                ResponseBuilder.selectChain(-1));
                        autoResponded = true;
                        break;
                    }

                    int result = listener.onMessage(msg);
                    if (result != 0) {
                        sendFieldStats();
                        if (result == 2 || status == OcgConstants.DUEL_STATUS_END) {
                            ended = true;
                            listener.onDuelEnd();
                        }
                        return;
                    }
                }
            }
        } while (status == OcgConstants.DUEL_STATUS_CONTINUE || autoResponded);

        sendFieldStats();

        if (status == OcgConstants.DUEL_STATUS_END) {
            ended = true;
            listener.onDuelEnd();
        }
    }

    /**
     * Query the engine for current field stats and send UpdateData messages
     * to the listener. Called when the engine pauses (waiting for input or end).
     */
    private void sendFieldStats() {
        long eng = engine.getHandle();
        int flags = QUERY_CODE | QUERY_POSITION | QUERY_TYPE | QUERY_LEVEL | QUERY_RANK
                | QUERY_ATTRIBUTE | QUERY_RACE | QUERY_ATTACK | QUERY_DEFENSE
                | QUERY_BASE_ATTACK | QUERY_BASE_DEFENSE | QUERY_STATUS
                | QUERY_LINK | QUERY_IS_PUBLIC;

        for (int player = 0; player < 2; player++) {
            sendLocationStats(eng, flags, player, LOCATION_MZONE);
            sendLocationStats(eng, flags, player, LOCATION_SZONE);
            sendLocationStats(eng, flags, player, LOCATION_EXTRA);
        }
    }

    private void sendLocationStats(long eng, int flags, int player, int location) {
        int slotCount = switch (location) {
            case LOCATION_MZONE -> 7;
            case LOCATION_SZONE -> 8;
            default -> OcgCore.nDuelQueryCount(eng, duelHandle, player, location);
        };
        var cards = new ArrayList<QueriedCard>(slotCount);

        for (int seq = 0; seq < slotCount; seq++) {
            byte[] data = OcgCore.nDuelQuery(eng, duelHandle, flags, player, location, seq, 0);
            if (data == null || data.length == 0) {
                cards.add(null);
            } else {
                try {
                    cards.add(parseSingleNativeQuery(data));
                } catch (Exception e) {
                    LOGGER.warn("[Query] Failed to parse slot p={} loc=0x{} seq={}: {}",
                            player, Integer.toHexString(location), seq, e.getMessage());
                    cards.add(null);
                }
            }
        }

        listener.onMessage(new DuelMessage.UpdateData(player, location, cards));
    }

    /**
     * Parse a single-card native query result: sequential field blocks
     * [u16 fieldSize][u32 flag][data] with no per-card header.
     */
    private static QueriedCard parseSingleNativeQuery(byte[] data) {
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        var card = new QueriedCard();
        while (buf.remaining() >= 6) { // at least u16 + u32
            int fieldSize = Short.toUnsignedInt(buf.getShort());
            if (fieldSize == 0) break;
            readFieldBlock(buf, card, fieldSize);
        }
        return card;
    }

    /** Read one field block: given u16 fieldSize already read, read [u32 flag][data]. */
    private static void readFieldBlock(ByteBuffer buf, QueriedCard card, int fieldSize) {
        int flag = buf.getInt();
        card.flags |= flag;
        int dataSize = fieldSize - 4; // fieldSize includes the flag

        switch (flag) {
            case QUERY_CODE         -> card.code = buf.getInt();
            case QUERY_POSITION     -> card.position = buf.getInt();
            case QUERY_ALIAS        -> card.alias = buf.getInt();
            case QUERY_TYPE         -> card.type = buf.getInt();
            case QUERY_LEVEL        -> card.level = buf.getInt();
            case QUERY_RANK         -> card.rank = buf.getInt();
            case QUERY_ATTRIBUTE    -> card.attribute = buf.getInt();
            case QUERY_RACE         -> card.race = buf.getLong();
            case QUERY_ATTACK       -> card.attack = buf.getInt();
            case QUERY_DEFENSE      -> card.defense = buf.getInt();
            case QUERY_BASE_ATTACK  -> card.baseAttack = buf.getInt();
            case QUERY_BASE_DEFENSE -> card.baseDefense = buf.getInt();
            case QUERY_REASON       -> card.reason = buf.getInt();
            case QUERY_STATUS       -> card.status = buf.getInt();
            case QUERY_IS_PUBLIC    -> card.isPublic = buf.getInt() != 0;
            case QUERY_LSCALE       -> card.lscale = buf.getInt();
            case QUERY_RSCALE       -> card.rscale = buf.getInt();
            case QUERY_COVER        -> card.cover = buf.getInt();
            case QUERY_LINK -> {
                card.linkRating = buf.getInt();
                card.linkMarker = buf.getInt();
            }
            default -> {
                // Skip unknown field data
                if (dataSize > 0 && buf.remaining() >= dataSize) {
                    buf.position(buf.position() + dataSize);
                }
            }
        }
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

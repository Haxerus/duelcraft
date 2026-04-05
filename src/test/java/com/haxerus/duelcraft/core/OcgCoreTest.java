package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the OcgCore JNI bridge.
 *
 * Requires system properties (set in build.gradle):
 *   -Dduelcraft.test.dbPath=path/to/cards.cdb
 *   -Dduelcraft.test.scriptPaths=path/to/scripts;path/to/card/scripts
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OcgCoreTest {

    // --- OCG Constants ---

    // Locations
    static final int LOCATION_DECK  = 0x01;
    static final int LOCATION_HAND  = 0x02;
    static final int LOCATION_EXTRA = 0x40;

    // Positions
    static final int POS_FACEDOWN_DEFENSE = 0x08;

    // Duel rule flags (MR5 = modern rules)
    static final long DUEL_PZONE                       = 0x800L;
    static final long DUEL_EMZONE                      = 0x2000L;
    static final long DUEL_FSX_MMZONE                  = 0x4000L;
    static final long DUEL_TRAP_MONSTERS_NOT_USE_ZONE  = 0x8000L;
    static final long DUEL_TRIGGER_ONLY_IN_LOCATION    = 0x20000L;
    static final long DUEL_MODE_MR5 = DUEL_PZONE | DUEL_EMZONE | DUEL_FSX_MMZONE
            | DUEL_TRAP_MONSTERS_NOT_USE_ZONE | DUEL_TRIGGER_ONLY_IN_LOCATION;

    // Duel status
    static final int OCG_DUEL_STATUS_END       = 0;
    static final int OCG_DUEL_STATUS_AWAITING  = 1;
    static final int OCG_DUEL_STATUS_CONTINUE  = 2;

    // Message types (subset needed for testing)
    static final int MSG_RETRY             = 1;
    static final int MSG_HINT              = 2;
    static final int MSG_START             = 4;
    static final int MSG_WIN               = 5;
    static final int MSG_SELECT_BATTLECMD  = 10;
    static final int MSG_SELECT_IDLECMD    = 11;
    static final int MSG_SELECT_EFFECTYN   = 12;
    static final int MSG_SELECT_YESNO      = 13;
    static final int MSG_SELECT_OPTION     = 14;
    static final int MSG_SELECT_CARD       = 15;
    static final int MSG_SELECT_CHAIN      = 16;
    static final int MSG_SELECT_PLACE      = 18;
    static final int MSG_SELECT_POSITION   = 19;
    static final int MSG_SELECT_TRIBUTE    = 20;
    static final int MSG_SELECT_SUM        = 23;
    static final int MSG_SELECT_UNSELECT_CARD = 26;
    static final int MSG_NEW_TURN          = 40;
    static final int MSG_NEW_PHASE         = 41;
    static final int MSG_DRAW              = 90;

    /**
     * Sample deck: 40-card main deck of Normal and simple Effect Monsters
     * plus basic Spell/Trap cards. All cards whose scripts exist in a
     * standard EDOPro installation.
     *
     * The deck is intentionally simple to exercise the duel loop without
     * complex chains. Cards chosen:
     *
     * Monsters (20):
     *   - 4x Mystical Shine Ball    (39552864)  Level 2, 500/500
     *   - 4x Giant Soldier of Stone (13039848)  Level 3, 1300/2000
     *   - 4x Alexandrite Dragon     (43096270)  Level 4, 2000/100
     *   - 4x Luster Dragon          (11091375)  Level 4, 1900/1600
     *   - 4x Gene-Warped Warwolf    (69247929)  Level 4, 2000/100
     *
     * Spells (14):
     *   - 3x Pot of Greed       (55144522) Draw 2
     *   - 3x Raigeki            (12580477) Destroy all opponent monsters
     *   - 3x Fissure            (66788016) Destroy lowest ATK opponent monster
     *   - 3x Mystical Space Typhoon (5318639) Destroy 1 S/T
     *   - 2x Monster Reborn     (83764718) Revive 1 monster
     *
     * Traps (6):
     *   - 3x Mirror Force       (44095762)
     *   - 3x Magic Cylinder     (62279055)
     */
    static final int[] MAIN_DECK = {
            // Monsters
            39552864, 39552864, 39552864, 39552864,
            13039848, 13039848, 13039848, 13039848,
            43096270, 43096270, 43096270, 43096270,
            11091375, 11091375, 11091375, 11091375,
            69247929, 69247929, 69247929, 69247929,
            // Spells
            55144522, 55144522, 55144522,
            12580477, 12580477, 12580477,
            66788016, 66788016, 66788016,
            5318639,  5318639,  5318639,
            83764718, 83764718,
            // Traps
            44095762, 44095762, 44095762,
            62279055, 62279055, 62279055,
    };

    static final int[] EXTRA_DECK = {}; // no Extra Deck cards

    // --- Test state (shared across ordered tests) ---

    static long engine;
    static long duel;
    /** Pending selection message from the opening sequence, to be handled by the duel loop test. */
    static ParsedMessage pendingSelection;

    @BeforeAll
    static void setUp() {
        String dbPath = System.getProperty("duelcraft.test.dbPath");
        String scriptPathsStr = System.getProperty("duelcraft.test.scriptPaths");
        assertNotNull(dbPath, "duelcraft.test.dbPath system property must be set");
        assertNotNull(scriptPathsStr, "duelcraft.test.scriptPaths system property must be set");

        String[] dbPaths = new String[]{ dbPath };
        String[] scriptPaths = scriptPathsStr.split(";");

        engine = OcgCore.nCreateEngine(dbPaths, scriptPaths);
        assertNotEquals(0, engine, "Engine creation failed — check that cards.cdb and script paths are correct");
    }

    @AfterAll
    static void tearDown() {
        if (duel != 0) {
            OcgCore.nDestroyDuel(engine, duel);
            duel = 0;
        }
        if (engine != 0) {
            OcgCore.nDestroyEngine(engine);
            engine = 0;
        }
    }

    // --- Tests (ordered to form a duel lifecycle) ---

    @Test
    @Order(1)
    void testGetVersion() {
        int[] version = OcgCore.nGetVersion();
        assertNotNull(version);
        assertEquals(2, version.length);
        // ygopro-core v11.0
        assertEquals(11, version[0], "Expected OCG_VERSION_MAJOR = 11");
        assertEquals(0, version[1], "Expected OCG_VERSION_MINOR = 0");
        System.out.println("OCG Version: " + version[0] + "." + version[1]);
    }

    @Test
    @Order(2)
    void testCreateDuel() {
        long[] seed = { 42, 42, 42, 42 };

        duel = OcgCore.nCreateDuel(engine, seed, DUEL_MODE_MR5,
                8000, 5, 1,  // team1: 8000 LP, 5-card start, 1 draw/turn
                8000, 5, 1); // team2: same
        assertNotEquals(0, duel, "Duel creation failed");
        System.out.println("Duel created: handle=" + duel);
    }

    @Test
    @Order(3)
    void testAddCardsAndStart() {
        assertNotEquals(0, duel, "Duel must be created first");

        // Add team 1 main deck (reverse order for stack behavior)
        for (int i = MAIN_DECK.length - 1; i >= 0; i--) {
            OcgCore.nDuelNewCard(engine, duel,
                    0, 0, MAIN_DECK[i], 0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        // Team 1 extra deck
        for (int code : EXTRA_DECK) {
            OcgCore.nDuelNewCard(engine, duel,
                    0, 0, code, 0, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
        }

        // Add team 2 (same deck, mirror match)
        for (int i = MAIN_DECK.length - 1; i >= 0; i--) {
            OcgCore.nDuelNewCard(engine, duel,
                    1, 0, MAIN_DECK[i], 1, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        for (int code : EXTRA_DECK) {
            OcgCore.nDuelNewCard(engine, duel,
                    1, 0, code, 1, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE);
        }

        // Start the duel — no exception means success
        OcgCore.nStartDuel(engine, duel);
        System.out.println("Duel started with " + MAIN_DECK.length + "-card decks");
    }

    @Test
    @Order(4)
    void testQueryCount() {
        assertNotEquals(0, duel, "Duel must be started first");

        // After starting, each player should have drawn their starting hand
        // but queryCount on DECK should reflect the remaining cards
        int team0Deck = OcgCore.nDuelQueryCount(engine, duel, 0, LOCATION_DECK);
        int team1Deck = OcgCore.nDuelQueryCount(engine, duel, 1, LOCATION_DECK);

        // Deck should have 40 cards (hands haven't been drawn until process() runs)
        assertTrue(team0Deck > 0, "Team 0 should have cards in deck, got " + team0Deck);
        assertTrue(team1Deck > 0, "Team 1 should have cards in deck, got " + team1Deck);
        System.out.println("Team 0 deck: " + team0Deck + " cards, Team 1 deck: " + team1Deck + " cards");
    }

    @Test
    @Order(5)
    void testProcessAndGetMessages() {
        assertNotEquals(0, duel, "Duel must be started first");

        // The engine may require multiple process() calls to emit all opening
        // messages. Collect messages until the engine is AWAITING player input.
        List<ParsedMessage> allMessages = new ArrayList<>();
        int status;
        int cycles = 0;

        do {
            status = OcgCore.nDuelProcess(engine, duel);
            byte[] msgBuf = OcgCore.nDuelGetMessage(engine, duel);
            if (msgBuf != null && msgBuf.length > 0) {
                allMessages.addAll(parseMessages(msgBuf));
            }
            cycles++;
        } while (status == OCG_DUEL_STATUS_CONTINUE && cycles < 50);

        assertFalse(allMessages.isEmpty(), "Should have parsed at least one message");

        System.out.println("Opening sequence: " + cycles + " process cycles, "
                + allMessages.size() + " messages, final status=" + statusName(status));
        for (ParsedMessage msg : allMessages) {
            System.out.println("  " + msgName(msg.type) + " (" + msg.type + "), "
                    + msg.body.length + " bytes");
        }

        // Verify key opening messages are present
        boolean hasDraw = allMessages.stream().anyMatch(m -> m.type == MSG_DRAW);
        assertTrue(hasDraw, "Messages should include MSG_DRAW for starting hands");

        boolean hasNewTurn = allMessages.stream().anyMatch(m -> m.type == MSG_NEW_TURN);
        assertTrue(hasNewTurn, "Messages should include MSG_NEW_TURN");

        // If the engine is AWAITING, there should be a selection message in the batch.
        // Stash it so the duel loop test can respond to it first.
        if (status == OCG_DUEL_STATUS_AWAITING) {
            for (int i = allMessages.size() - 1; i >= 0; i--) {
                if (isSelectionMessage(allMessages.get(i).type)) {
                    pendingSelection = allMessages.get(i);
                    break;
                }
            }
            assertNotNull(pendingSelection,
                    "Engine is AWAITING but no selection message found in opening messages");
            System.out.println("Pending selection: " + msgName(pendingSelection.type));
        }
    }

    @Test
    @Order(6)
    void testDuelLoopWithResponses() {
        assertNotEquals(0, duel, "Duel must be started first");

        // Run the duel loop for several turns, auto-responding to prompts.
        // This tests the full process → getMessages → setResponse cycle.
        int maxIterations = 200; // safety limit
        int messagesProcessed = 0;
        int responsesGiven = 0;

        // Handle the pending selection left over from the opening sequence test
        if (pendingSelection != null) {
            byte[] response = buildAutoResponse(pendingSelection);
            assertNotNull(response, "Failed to build response for pending " + msgName(pendingSelection.type));
            OcgCore.nDuelSetResponse(engine, duel, response);
            responsesGiven++;
            pendingSelection = null;
        }
        int turns = 0;
        boolean duelEnded = false;

        for (int iter = 0; iter < maxIterations && !duelEnded; iter++) {
            int status = OcgCore.nDuelProcess(engine, duel);
            byte[] msgBuf = OcgCore.nDuelGetMessage(engine, duel);

            if (msgBuf == null || msgBuf.length == 0) {
                if (status == OCG_DUEL_STATUS_END) {
                    duelEnded = true;
                    break;
                }
                continue;
            }

            List<ParsedMessage> messages = parseMessages(msgBuf);
            messagesProcessed += messages.size();

            for (ParsedMessage msg : messages) {
                if (msg.type == MSG_WIN) {
                    duelEnded = true;
                    ByteBuffer bb = ByteBuffer.wrap(msg.body).order(ByteOrder.LITTLE_ENDIAN);
                    int winner = Byte.toUnsignedInt(bb.get());
                    int reason = bb.getInt();
                    System.out.println("Duel ended! Winner: Player " + winner + ", Reason: " + reason);
                    break;
                }
                if (msg.type == MSG_NEW_TURN) {
                    turns++;
                }
            }

            if (duelEnded) break;

            if (status == OCG_DUEL_STATUS_AWAITING) {
                // Find the last selection message and auto-respond
                ParsedMessage lastSelect = null;
                for (ParsedMessage msg : messages) {
                    if (isSelectionMessage(msg.type)) {
                        lastSelect = msg;
                    }
                }

                if (lastSelect != null) {
                    byte[] response = buildAutoResponse(lastSelect);
                    assertNotNull(response, "Failed to build response for " + msgName(lastSelect.type));
                    OcgCore.nDuelSetResponse(engine, duel, response);
                    responsesGiven++;
                }
            }

            if (status == OCG_DUEL_STATUS_END) {
                duelEnded = true;
            }
        }

        System.out.println("Duel loop completed: " + messagesProcessed + " messages, "
                + responsesGiven + " responses, " + turns + " turns, ended=" + duelEnded);

        assertTrue(messagesProcessed > 0, "Should have processed some messages");
        assertTrue(responsesGiven > 0, "Should have given at least one response");
        assertTrue(turns > 0, "At least one turn should have started");
    }

    @Test
    @Order(7)
    void testQueryField() {
        assertNotEquals(0, duel, "Duel must exist");
        // Query the full field — just verify it returns response without crashing
        byte[] fieldData = OcgCore.nDuelQueryField(engine, duel);
        assertNotNull(fieldData, "Field query should return response");
        assertTrue(fieldData.length > 0, "Field query response should not be empty");
        System.out.println("Field query: " + fieldData.length + " bytes");
    }

    @Test
    @Order(8)
    void testDestroyAndRecreate() {
        // Verify we can cleanly destroy and recreate a duel on the same engine
        OcgCore.nDestroyDuel(engine, duel);

        long[] seed = { 99, 99, 99, 99 };
        long duel2 = OcgCore.nCreateDuel(engine, seed, DUEL_MODE_MR5,
                8000, 5, 1, 8000, 5, 1);
        assertNotEquals(0, duel2, "Should be able to create a new duel after destroying the old one");

        // Add a minimal deck and start
        for (int i = MAIN_DECK.length - 1; i >= 0; i--) {
            OcgCore.nDuelNewCard(engine, duel2, 0, 0, MAIN_DECK[i], 0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
            OcgCore.nDuelNewCard(engine, duel2, 1, 0, MAIN_DECK[i], 1, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
        OcgCore.nStartDuel(engine, duel2);

        int status = OcgCore.nDuelProcess(engine, duel2);
        assertTrue(status != OCG_DUEL_STATUS_END, "New duel should not immediately end");

        OcgCore.nDestroyDuel(engine, duel2);
        duel = 0; // prevent AfterAll from double-destroying
        System.out.println("Destroy/recreate test passed");
    }

    // --- Helpers ---

    record ParsedMessage(int type, byte[] body) {}

    /**
     * Parse the raw message buffer from OCG_DuelGetMessage into individual messages.
     * Format: [uint32 length][uint8 type][body...]  repeated.
     */
    static List<ParsedMessage> parseMessages(byte[] buffer) {
        List<ParsedMessage> messages = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        while (bb.remaining() >= 5) { // at least length(4) + type(1)
            int length = bb.getInt(); // length of type + body
            if (length < 1 || bb.remaining() < length) break;

            int type = Byte.toUnsignedInt(bb.get());
            byte[] body = new byte[length - 1];
            bb.get(body);
            messages.add(new ParsedMessage(type, body));
        }

        return messages;
    }

    static boolean isSelectionMessage(int type) {
        return type == MSG_SELECT_IDLECMD
                || type == MSG_SELECT_BATTLECMD
                || type == MSG_SELECT_CARD
                || type == MSG_SELECT_CHAIN
                || type == MSG_SELECT_EFFECTYN
                || type == MSG_SELECT_YESNO
                || type == MSG_SELECT_OPTION
                || type == MSG_SELECT_PLACE
                || type == MSG_SELECT_POSITION
                || type == MSG_SELECT_TRIBUTE
                || type == MSG_SELECT_SUM
                || type == MSG_SELECT_UNSELECT_CARD;
    }

    /**
     * Build a simple auto-response for each selection message type.
     * These pick the simplest/first valid option to keep the duel moving.
     */
    static byte[] buildAutoResponse(ParsedMessage msg) {
        ByteBuffer bb = ByteBuffer.wrap(msg.body).order(ByteOrder.LITTLE_ENDIAN);

        return switch (msg.type) {
            case MSG_SELECT_IDLECMD -> {
                // Body: [uint8 player][...summon list...][...spsummon...][...reposition...][...setmonster...][...setspell...][...activate...]
                // Response: int32 action_type + int32 index
                // Action 5 = end phase (safest default), or we can try to summon/set/activate
                // Parse to find if we have summonable monsters or activatable cards
                int player = Byte.toUnsignedInt(bb.get());
                // Count of summonable monsters
                int summonCount = bb.getInt();
                if (summonCount > 0) {
                    // Action 0 = Normal Summon, pick first
                    yield intResponse(0, 0);
                }
                // Skip summon entries to get to other options
                for (int i = 0; i < summonCount; i++) {
                    bb.getInt(); // code
                    bb.get(); bb.get(); bb.getInt(); bb.getInt(); // loc_info: con, loc, seq, pos
                }
                int spSummonCount = bb.getInt();
                for (int i = 0; i < spSummonCount; i++) {
                    bb.getInt(); bb.get(); bb.get(); bb.getInt(); bb.getInt();
                }
                int repositionCount = bb.getInt();
                for (int i = 0; i < repositionCount; i++) {
                    bb.getInt(); bb.get(); bb.get(); bb.getInt(); bb.getInt();
                }
                int setMonsterCount = bb.getInt();
                if (setMonsterCount > 0) {
                    yield intResponse(3, 0); // Action 3 = Set monster
                }
                for (int i = 0; i < setMonsterCount; i++) {
                    bb.getInt(); bb.get(); bb.get(); bb.getInt(); bb.getInt();
                }
                int setSpellCount = bb.getInt();
                if (setSpellCount > 0) {
                    yield intResponse(4, 0); // Action 4 = Set S/T
                }
                for (int i = 0; i < setSpellCount; i++) {
                    bb.getInt(); bb.get(); bb.get(); bb.getInt(); bb.getInt();
                }
                int activateCount = bb.getInt();
                if (activateCount > 0) {
                    yield intResponse(5, 0); // Action 5 = Activate
                }
                // Nothing to do — go to battle phase (6) or end turn (7)
                yield intResponse(7, 0); // End turn
            }

            case MSG_SELECT_BATTLECMD -> {
                // Response: int32 action_type + int32 index
                // Parse to check for attackable monsters
                int player = Byte.toUnsignedInt(bb.get());
                int attackCount = bb.getInt();
                if (attackCount > 0) {
                    yield intResponse(1, 0); // Action 1 = Attack with first monster
                }
                yield intResponse(3, 0); // Action 3 = End battle phase
            }

            case MSG_SELECT_CARD -> {
                // Body: [uint8 player][uint8 cancelable][uint32 min][uint32 max][uint32 count][...cards...]
                int player = Byte.toUnsignedInt(bb.get());
                int cancelable = Byte.toUnsignedInt(bb.get());
                int min = bb.getInt();
                int max = bb.getInt();
                int count = bb.getInt();
                // Select the minimum number of cards, picking the first ones
                ByteBuffer resp = ByteBuffer.allocate(4 + 4 * min).order(ByteOrder.LITTLE_ENDIAN);
                resp.putInt(min);
                for (int i = 0; i < min; i++) {
                    resp.putInt(i);
                }
                yield resp.array();
            }

            case MSG_SELECT_CHAIN -> {
                // Body: [uint8 player][uint8 count][uint8 forced][...chains...]
                // Response: int32 index (-1 to decline)
                int player = Byte.toUnsignedInt(bb.get());
                int count = Byte.toUnsignedInt(bb.get());
                int forced = Byte.toUnsignedInt(bb.get());
                if (forced != 0 && count > 0) {
                    yield intResponse(0); // Must chain — pick first
                }
                yield intResponse(-1); // Decline to chain
            }

            case MSG_SELECT_EFFECTYN -> {
                // Response: int32 (1=yes, 0=no)
                yield intResponse(1); // Always say yes
            }

            case MSG_SELECT_YESNO -> {
                // Response: int32 (1=yes, 0=no)
                yield intResponse(1);
            }

            case MSG_SELECT_OPTION -> {
                // Response: int32 index
                yield intResponse(0); // Pick first option
            }

            case MSG_SELECT_PLACE -> {
                // Body: [uint8 player][uint8 count][uint32 selectable_field]
                // Response: [uint8 player][uint8 location][uint8 sequence]
                // The selectable_field is a bitmask. Pick the first available zone.
                int player = Byte.toUnsignedInt(bb.get());
                int count = Byte.toUnsignedInt(bb.get());
                int field = bb.getInt();
                // field is an inverted bitmask (0 = selectable), find first 0 bit
                // Zones: bits 0-4 = player's MMZ, bits 5-6 = EMZ, bits 8-12 = S/T zones
                // For simplicity, pick the first available main monster zone
                for (int seq = 0; seq < 5; seq++) {
                    if ((field & (1 << seq)) == 0) {
                        yield new byte[]{ (byte)player, (byte)0x04, (byte)seq }; // MZONE
                    }
                }
                // Try S/T zones
                for (int seq = 0; seq < 5; seq++) {
                    if ((field & (1 << (seq + 8))) == 0) {
                        yield new byte[]{ (byte)player, (byte)0x08, (byte)seq }; // SZONE
                    }
                }
                yield new byte[]{ (byte)player, (byte)0x04, 0 }; // fallback
            }

            case MSG_SELECT_POSITION -> {
                // Body: [uint8 player][uint32 code][uint8 positions]
                // Response: int32 position — pick first available
                int player = Byte.toUnsignedInt(bb.get());
                int code = bb.getInt();
                int positions = Byte.toUnsignedInt(bb.get());
                // Pick the first set bit (lowest position value)
                for (int pos = 0x1; pos <= 0x8; pos <<= 1) {
                    if ((positions & pos) != 0) {
                        yield intResponse(pos);
                    }
                }
                yield intResponse(0x1); // fallback: face-up attack
            }

            case MSG_SELECT_TRIBUTE -> {
                // Same structure as SELECT_CARD
                int player = Byte.toUnsignedInt(bb.get());
                int cancelable = Byte.toUnsignedInt(bb.get());
                int min = bb.getInt();
                int max = bb.getInt();
                int count = bb.getInt();
                ByteBuffer resp = ByteBuffer.allocate(4 + 4 * min).order(ByteOrder.LITTLE_ENDIAN);
                resp.putInt(min);
                for (int i = 0; i < min; i++) {
                    resp.putInt(i);
                }
                yield resp.array();
            }

            case MSG_SELECT_SUM -> {
                // Body: [uint8 player][uint8 must_just][uint32 total_sum][uint32 min][uint32 max]
                //       [uint32 must_count][...must cards...][uint32 select_count][...select cards...]
                // Response: int32 count + int32[] indices (of the selectable cards)
                int player = Byte.toUnsignedInt(bb.get());
                int mustJust = Byte.toUnsignedInt(bb.get());
                int totalSum = bb.getInt();
                int min = bb.getInt();
                int max = bb.getInt();
                // Just pick the first selectable card
                yield intResponse(1, 0);
            }

            case MSG_SELECT_UNSELECT_CARD -> {
                // Body: [uint8 player][uint8 finishable][uint8 cancelable][uint32 min][uint32 max]
                //       [uint32 select_count][...][uint32 unselect_count][...]
                // Response: int32 index (-1 to finish if finishable)
                int player = Byte.toUnsignedInt(bb.get());
                int finishable = Byte.toUnsignedInt(bb.get());
                if (finishable != 0) {
                    yield intResponse(-1); // finish selection
                }
                yield intResponse(0); // pick first
            }

            default -> null;
        };
    }

    /** Build a response containing one or more little-endian int32 values. */
    static byte[] intResponse(int... values) {
        ByteBuffer bb = ByteBuffer.allocate(4 * values.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) {
            bb.putInt(v);
        }
        return bb.array();
    }

    static String statusName(int status) {
        return switch (status) {
            case OCG_DUEL_STATUS_END -> "END";
            case OCG_DUEL_STATUS_AWAITING -> "AWAITING";
            case OCG_DUEL_STATUS_CONTINUE -> "CONTINUE";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    static String msgName(int type) {
        return switch (type) {
            case 1 -> "MSG_RETRY";
            case 2 -> "MSG_HINT";
            case 3 -> "MSG_WAITING";
            case 4 -> "MSG_START";
            case 5 -> "MSG_WIN";
            case 10 -> "MSG_SELECT_BATTLECMD";
            case 11 -> "MSG_SELECT_IDLECMD";
            case 12 -> "MSG_SELECT_EFFECTYN";
            case 13 -> "MSG_SELECT_YESNO";
            case 14 -> "MSG_SELECT_OPTION";
            case 15 -> "MSG_SELECT_CARD";
            case 16 -> "MSG_SELECT_CHAIN";
            case 18 -> "MSG_SELECT_PLACE";
            case 19 -> "MSG_SELECT_POSITION";
            case 20 -> "MSG_SELECT_TRIBUTE";
            case 22 -> "MSG_SELECT_COUNTER";
            case 23 -> "MSG_SELECT_SUM";
            case 24 -> "MSG_SELECT_DISFIELD";
            case 25 -> "MSG_SORT_CARD";
            case 26 -> "MSG_SELECT_UNSELECT_CARD";
            case 30 -> "MSG_CONFIRM_DECKTOP";
            case 31 -> "MSG_CONFIRM_CARDS";
            case 32 -> "MSG_SHUFFLE_DECK";
            case 33 -> "MSG_SHUFFLE_HAND";
            case 40 -> "MSG_NEW_TURN";
            case 41 -> "MSG_NEW_PHASE";
            case 50 -> "MSG_MOVE";
            case 53 -> "MSG_POS_CHANGE";
            case 54 -> "MSG_SET";
            case 60 -> "MSG_SUMMONING";
            case 61 -> "MSG_SUMMONED";
            case 62 -> "MSG_SPSUMMONING";
            case 63 -> "MSG_SPSUMMONED";
            case 64 -> "MSG_FLIPSUMMONING";
            case 65 -> "MSG_FLIPSUMMONED";
            case 70 -> "MSG_CHAINING";
            case 71 -> "MSG_CHAINED";
            case 72 -> "MSG_CHAIN_SOLVING";
            case 73 -> "MSG_CHAIN_SOLVED";
            case 74 -> "MSG_CHAIN_END";
            case 90 -> "MSG_DRAW";
            case 91 -> "MSG_DAMAGE";
            case 92 -> "MSG_RECOVER";
            case 94 -> "MSG_LPUPDATE";
            case 100 -> "MSG_PAY_LPCOST";
            case 110 -> "MSG_ATTACK";
            case 111 -> "MSG_BATTLE";
            case 112 -> "MSG_ATTACK_DISABLED";
            case 113 -> "MSG_DAMAGE_STEP_START";
            case 114 -> "MSG_DAMAGE_STEP_END";
            case 160 -> "MSG_CARD_HINT";
            case 164 -> "MSG_SHOW_HINT";
            case 165 -> "MSG_PLAYER_HINT";
            default -> "MSG_UNKNOWN_" + type;
        };
    }
}

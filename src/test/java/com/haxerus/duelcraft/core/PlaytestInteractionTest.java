package com.haxerus.duelcraft.core;

import com.haxerus.duelcraft.client.ClientDuelState;
import com.haxerus.duelcraft.duel.MessageSanitizer;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.MessageParser;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.duel.response.ResponseValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real card-script regressions, using the same native/data prerequisites as OcgCoreTest. */
class PlaytestInteractionTest {
    private long engine;
    private long duel;
    private final List<DuelMessage> events = new ArrayList<>();

    @BeforeEach
    void createDuel() {
        createDuel(System.getProperty("duelcraft.test.scriptPaths").split(";"));
    }

    private void createDuel(String[] scriptPaths) {
        engine = OcgCore.nCreateEngine(new String[]{System.getProperty("duelcraft.test.dbPath")}, scriptPaths);
        duel = OcgCore.nCreateDuel(engine, new long[]{42,42,42,42}, DUEL_MODE_MR5, 8000,0,1,8000,0,1);
        assertNotEquals(0, duel);
        for (int player = 0; player < 2; player++) {
            for (int i = 0; i < 40; i++) add(89631139, player, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        }
    }

    @AfterEach
    void close() {
        if (duel != 0) OcgCore.nDestroyDuel(engine, duel);
        if (engine != 0) OcgCore.nDestroyEngine(engine);
    }

    @Test
    void kusanagiCanActivateAfterBeingTributedFromDeck(@TempDir Path scripts) throws IOException {
        // Exercise the current upstream API even when the local EDOPro scripts predate it.
        var paths = Arrays.asList(System.getProperty("duelcraft.test.scriptPaths").split(";"));
        Path ritualScript = paths.stream().map(p -> Path.of(p, "c81560239.lua"))
                .filter(Files::isRegularFile).findFirst().orElseThrow();
        Files.writeString(scripts.resolve("c81560239.lua"), Files.readString(ritualScript) + """

                function c81560239.extraop(mat,e,tp,eg,ep,ev,re,r,rp,tc)
                    Duel.ReleaseRitualMaterial(mat,true)
                end
                """);
        close();
        engine = duel = 0;
        var scriptPaths = new ArrayList<String>();
        scriptPaths.add(scripts.toString());
        scriptPaths.addAll(paths);
        createDuel(scriptPaths.toArray(String[]::new));
        int kusanagi = 82782870;
        int ritual = 81560239;
        add(20295753, 0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE); // Night Sword Serpent
        add(kusanagi, 0, LOCATION_DECK, 0, POS_FACEDOWN_DEFENSE);
        add(18176525, 0, LOCATION_GRAVE, 0, POS_FACEUP_ATTACK); // Saji, a legal recovery target
        add(55397172, 0, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE); // Futsu no Mitama, Level 8
        add(ritual, 0, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE);
        OcgCore.nStartDuel(engine, duel);
        var idle = assertInstanceOf(DuelMessage.SelectIdleCmd.class, passChains(pump()));
        int activate = -1;
        for (int i = 0; i < idle.activatable().size(); i++) {
            if (idle.activatable().get(i).code() == ritual) activate = i;
        }
        assertTrue(activate >= 0, "Mitsurugi Ritual must be activatable");
        DuelMessage next = respond(ResponseValidator.selectCmd(idle, IdleAction.ACTIVATE, activate));
        for (int i = 0; i < 30; i++) {
            if (next instanceof DuelMessage.SelectIdleCmd) break;
            if (next instanceof DuelMessage.SelectEffectYn effect && effect.code() == kusanagi) break;
            if (next instanceof DuelMessage.SelectChain chain
                    && chain.chains().stream().anyMatch(c -> c.code() == kusanagi)) break;
            next = switch (next) {
                case DuelMessage.SelectPlace place -> respond(firstPlace(place));
                case DuelMessage.SelectOption ignored -> respond(ResponseBuilder.selectOption(0));
                case DuelMessage.SelectEffectYn ignored -> respond(ResponseBuilder.selectYesNo(false));
                case DuelMessage.SelectChain chain -> respond(ResponseValidator.selectChain(chain, -1));
                case DuelMessage.SelectCard cards -> respond(ResponseValidator.selectCards(cards, 0));
                case DuelMessage.SelectUnselectCard cards -> respond(ResponseValidator.selectUnselectCard(cards,
                        cards.finishable() ? -1 : 0));
                case DuelMessage.SelectPosition ignored -> respond(ResponseBuilder.selectPosition(POS_FACEUP_ATTACK));
                default -> throw new AssertionError("Unexpected ritual prompt: " + next);
            };
        }
        assertTrue(events.stream().anyMatch(m -> m instanceof DuelMessage.Move move
                && move.code() == kusanagi && move.from().location() == LOCATION_DECK
                && move.to().location() == LOCATION_GRAVE && (move.reason() & REASON_RELEASE) != 0),
                "Kusanagi must have been tributed from the Deck");
        assertTrue(next instanceof DuelMessage.SelectEffectYn effect && effect.code() == kusanagi
                || next instanceof DuelMessage.SelectChain chain
                && chain.chains().stream().anyMatch(c -> c.code() == kusanagi),
                "Kusanagi's tribute trigger must be offered; got " + next);
    }

    @Test
    void linkTwoAndOneOtherMonsterCanFinishWithUnusedMaterialsStillOnField() {
        add(77637979, 0, LOCATION_MZONE, 0, POS_FACEUP_ATTACK); // LANphorhynchus, Link 2
        for (int i = 1; i < 4; i++) add(89631139, 0, LOCATION_MZONE, i, POS_FACEUP_ATTACK);
        add(67598234, 0, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE); // Gaia Saber, 2+ monsters, Link 3
        OcgCore.nStartDuel(engine, duel);
        var idle = assertInstanceOf(DuelMessage.SelectIdleCmd.class, passChains(pump()));
        int summon = -1;
        for (int i = 0; i < idle.specialSummonable().size(); i++) {
            if (idle.specialSummonable().get(i).code() == 67598234) summon = i;
        }
        assertTrue(summon >= 0, "Gaia Saber must be summonable with this field");
        var selection = assertInstanceOf(DuelMessage.SelectUnselectCard.class,
                respond(ResponseBuilder.selectCmd(IdleAction.SPECIAL_SUMMON, summon)));
        selection = selectMaterial(selection, 0);
        selection = selectMaterial(selection, 1);
        assertTrue(selection.finishable());
        // proc_link's min/max are 1/1 per toggle, not the total Link-material bound.
        assertEquals(2, selection.unselectableCards().size());
        assertEquals(4, OcgCore.nDuelQueryCount(engine, duel, 0, LOCATION_MZONE));

        // Negative control: this was the old UI encoding. The actual core must reject it.
        assertInstanceOf(DuelMessage.Retry.class, respond(new byte[]{1,0,0,0,-1,-1,-1,-1}));
        int afterRetry = events.size();
        DuelMessage next = respond(ResponseValidator.selectUnselectCard(selection, -1));
        for (int i = 0; i < 10 && !(next instanceof DuelMessage.SelectIdleCmd); i++) {
            next = next instanceof DuelMessage.SelectPlace place ? respond(firstPlace(place)) : passChains(next);
        }
        assertInstanceOf(DuelMessage.SelectIdleCmd.class, next);
        assertTrue(events.subList(afterRetry, events.size()).stream().noneMatch(m -> m instanceof DuelMessage.Retry));
        assertTrue(events.stream().anyMatch(m -> m instanceof DuelMessage.SpSummoning summonMessage && summonMessage.code() == 67598234));
        assertEquals(3, OcgCore.nDuelQueryCount(engine, duel, 0, LOCATION_MZONE)); // Two unused + Gaia
    }

    @ParameterizedTest
    @ValueSource(ints = {17375316, 64697231}) // Confiscation, Trap Dustshoot
    void handRevealSurvivesSanitizedSelectionAndIsClearedByShuffle(int spell) {
        add(spell, 0, spell == 17375316 ? LOCATION_HAND : LOCATION_SZONE, 0, POS_FACEDOWN_DEFENSE);
        for (int i = 0; i < 4; i++) add(89631139, 1, LOCATION_HAND, i, POS_FACEDOWN_DEFENSE);
        OcgCore.nStartDuel(engine, duel);
        DuelMessage next = pump();
        for (int i = 0; i < 15 && !(next instanceof DuelMessage.SelectCard); i++) {
            if (next instanceof DuelMessage.SelectChain chain) {
                int choice = -1;
                for (int j = 0; j < chain.chains().size(); j++) if (chain.chains().get(j).code() == spell) choice = j;
                next = respond(ResponseValidator.selectChain(chain, choice));
            } else if (next instanceof DuelMessage.SelectIdleCmd idle) {
                int activate = -1;
                for (int j = 0; j < idle.activatable().size(); j++) if (idle.activatable().get(j).code() == spell) activate = j;
                assertTrue(activate >= 0, "Test card must be activatable");
                next = respond(ResponseBuilder.selectCmd(IdleAction.ACTIVATE, activate));
            } else if (next instanceof DuelMessage.SelectPlace place) {
                next = respond(firstPlace(place));
            } else fail("Unexpected activation prompt: " + next);
        }
        var prompt = assertInstanceOf(DuelMessage.SelectCard.class, next);
        assertEquals(4, prompt.cards().size());
        var state = new ClientDuelState(0, "Opponent", 8000,8000,40,15,DUEL_MODE_MR5);
        state.applyMessage(new DuelMessage.Draw(1, java.util.Collections.nCopies(4,
                new DuelMessage.DrawnCard(0, POS_FACEDOWN_DEFENSE))));
        var confirm = events.stream().filter(m -> m instanceof DuelMessage.ConfirmCards).findFirst().orElseThrow();
        assertTrue(MessageSanitizer.recipientsOf(confirm).includes(0));
        state.applyMessage(MessageSanitizer.forRecipient(confirm, 0));
        state.confirmCards = null;
        var sanitized = (DuelMessage.SelectCard) MessageSanitizer.forRecipient(prompt, 0);
        for (var candidate : sanitized.cards()) {
            assertEquals(0, candidate.code(), "Foreign prompt codes are still stripped");
            assertEquals(89631139, state.candidateCode(candidate));
        }
        passChains(respond(ResponseValidator.selectCards(prompt, 0)));
        var shuffle = events.stream().filter(m -> m instanceof DuelMessage.ShuffleHand).findFirst().orElseThrow();
        state.applyMessage(MessageSanitizer.forRecipient(shuffle, 0));
        assertTrue(state.hand[1].stream().allMatch(card -> card.code == 0));
    }

    private DuelMessage.SelectUnselectCard selectMaterial(DuelMessage.SelectUnselectCard prompt, int sequence) {
        for (int i = 0; i < prompt.selectableCards().size(); i++) {
            if (prompt.selectableCards().get(i).sequence() == sequence) {
                return assertInstanceOf(DuelMessage.SelectUnselectCard.class,
                        respond(ResponseValidator.selectUnselectCard(prompt, i)));
            }
        }
        throw new AssertionError("Material not selectable: " + sequence);
    }

    private void add(int code, int player, int location, int sequence, int position) {
        OcgCore.nDuelNewCard(engine, duel, player,0,code,player,location,sequence,position);
    }

    private DuelMessage respond(byte[] response) {
        OcgCore.nDuelSetResponse(engine, duel, response);
        return pump();
    }

    private DuelMessage pump() {
        for (int i = 0; i < 100; i++) {
            int status = OcgCore.nDuelProcess(engine, duel);
            var messages = MessageParser.parse(OcgCore.nDuelGetMessage(engine, duel));
            events.addAll(messages);
            if (status != DUEL_STATUS_CONTINUE) {
                assertFalse(messages.isEmpty(), "Core stopped without a prompt");
                return messages.getLast();
            }
        }
        throw new AssertionError("Core did not reach a prompt");
    }

    private DuelMessage passChains(DuelMessage next) {
        for (int i = 0; i < 20 && next instanceof DuelMessage.SelectChain chain; i++) {
            next = respond(ResponseValidator.selectChain(chain, -1));
        }
        return next;
    }

    private static byte[] firstPlace(DuelMessage.SelectPlace prompt) {
        for (int bit = 0; bit < 32; bit++) {
            if ((prompt.field() & (1 << bit)) == 0) {
                int player = bit < 16 ? prompt.player() : 1 - prompt.player();
                return ResponseBuilder.selectPlace(player, (bit % 16) < 8 ? LOCATION_MZONE : LOCATION_SZONE, bit % 8);
            }
        }
        throw new AssertionError("No available place");
    }
}

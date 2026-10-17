package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.ServerConfig;
import com.haxerus.duelcraft.client.uitest.DuelVisibilityMPClient;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.core.*;
import com.haxerus.duelcraft.duel.*;
import com.haxerus.duelcraft.duel.message.*;
import com.haxerus.duelcraft.duel.response.ResponseValidator;
import com.haxerus.duelcraft.server.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Predicate;
import static com.haxerus.duelcraft.core.OcgConstants.*;

@LDLRegister(name = "duel_visibility_qa_mp", group = "duelcraft", registry = MPScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelVisibilityMPScenario implements MPScenario {
    private static final List<Integer> NORMALS = List.of(32864,549481,732302,756652,1184620,1761063,1784619,1929294,2118022,2311603,
            2468169,2483611,2830619,2863439,2906250,2957055,2964201,2971090,3134241,3170832,
            3557275,3573512,3606209,3732747,3797883,3985011,4035199,4042268,4148264,4179849,
            4392470,4920010,4931562,5053103,5265750,5388481,5434080,5464695,5628232,5818798);

    @Override public void configure(MPScenarioOptions options) { options.clients("A", "B").tags("visibility", "native"); }
    @Override public void define(MPScenarioBuilder s) {
        s.server("capture exact original attachments", sc -> {
            sc.check("requested ownership setting", ServerConfig.requireCardOwnership()
                    == System.getProperty("duelcraft.uitest.mpOwnership", "default").equals("required"));
            for (String role : List.of("A", "B")) {
                var p = sc.player(role);
                sc.put("present" + role, p.hasData(CollectionAttachments.COLLECTION));
                sc.put("original" + role, p.getData(CollectionAttachments.COLLECTION));
            }
        });
        start(s, "painful", List.of(74191942), List.of(), List.of(), List.of());
        s.server("activate real Painful Choice", sc -> {
            command(sc, 0, IdleAction.ACTIVATE, 74191942);
            settle(sc, m -> m instanceof DuelMessage.SelectCard c && c.player() == 0 && c.min() == 5);
        }).client("A", "choose five actual Deck cards", b -> DuelVisibilityMPClient.choose(b, LOCATION_DECK, 0, 5, 4,2,0,3,1))
         .server("opponent choice reached", sc -> {
             settle(sc, m -> m instanceof DuelMessage.SelectCard c && c.player() == 1 && c.min() == 1);
             var c = (DuelMessage.SelectCard) fixture(sc).prompt;
             sc.put("chosenPainful", c.cards().get(2).code());
             sc.put("painfulOffered", c.cards().stream().map(DuelMessage.CardInfo::code).toList());
         }).client("B", "choose non-first revealed Deck candidate", b -> DuelVisibilityMPClient.choose(b, LOCATION_DECK, 0, 1, 2))
         .server("actual Painful Choice continuation", sc -> {
             settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
             answer(sc, 1, "000000000100000002000000");
             int chosen = sc.get("chosenPainful");
             sc.check("chosen card actually enters hand", codes(sc, 0, LOCATION_HAND).contains(chosen));
             List<Integer> offered = sc.get("painfulOffered");
             var grave = codes(sc, 0, LOCATION_GRAVE);
             sc.check("other four actually enter grave", offered.stream().filter(c -> c != chosen).allMatch(grave::contains)
                     && !grave.contains(chosen) && grave.contains(74191942));
             sc.check("native chosen Deck-to-hand move", fixture(sc).trace.messages().stream().anyMatch(m -> m instanceof DuelMessage.Move move
                     && move.code() == chosen && move.from().location() == LOCATION_DECK && move.to().location() == LOCATION_HAND));
         });
        end(s, "painful");

        start(s, "trident", List.of(57568840,75582395), List.of(80159717), List.of(), List.of(67598234,77637979,67598234));
        s.server("normal summon materials on successive turns", sc -> {
            command(sc, 0, IdleAction.SUMMON, 57568840); settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
            command(sc, 0, IdleAction.END_TURN, 0); settle(sc, m -> m instanceof DuelMessage.SelectIdleCmd c && c.player() == 1);
            command(sc, 1, IdleAction.END_TURN, 0); settle(sc, m -> m instanceof DuelMessage.SelectIdleCmd c && c.player() == 0);
            command(sc, 0, IdleAction.SUMMON, 75582395); settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
            command(sc, 0, IdleAction.SPECIAL_SUMMON, 80159717);
            settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
            sc.check("real level-seven Trident on field", codes(sc, 0, LOCATION_MZONE).contains(80159717));
            sc.check("both real materials in grave", codes(sc, 0, LOCATION_GRAVE).containsAll(List.of(57568840,75582395)));
            command(sc, 0, IdleAction.ACTIVATE, 80159717);
            settle(sc, DuelMessage.SelectCard.class::isInstance);
            var cost = (DuelMessage.SelectCard) fixture(sc).prompt;
            sc.check("Trident pays itself as its only field cost", cost.cards().size() == 1 && cost.cards().getFirst().code() == 80159717);
            respond(sc, ResponseValidator.selectCards(cost, new int[]{0}));
            settle(sc, m -> m instanceof DuelMessage.SelectCard c && c.cards().getFirst().location() == LOCATION_EXTRA);
            var choice = (DuelMessage.SelectCard) fixture(sc).prompt;
            sc.check("three ordered real Extra choices", choice.cards().size() == 3 && choice.cards().get(1).code() == 77637979);
         }).client("A", "choose non-first duplicate-aware Extra candidate", b -> DuelVisibilityMPClient.choose(b, LOCATION_EXTRA, 1, 1, 1))
          .server("actual Extra continuation and shuffle", sc -> {
              settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
              answer(sc, 0, "000000000100000001000000");
              sc.check("LANphorhynchus really moved to B grave", codes(sc, 1, LOCATION_GRAVE).equals(List.of(77637979)));
              sc.check("both distinct Gaia copies remain", codes(sc, 1, LOCATION_EXTRA).equals(List.of(67598234,67598234)));
              sc.check("real Extra shuffle", fixture(sc).trace.messages().stream().anyMatch(DuelMessage.ShuffleExtra.class::isInstance));
          });
        end(s, "trident");

        start(s, "bell", List.of(12580477), List.of(), List.of(20228463,89631139), List.of());
        s.server("normal summon Bell on B turn", sc -> {
            command(sc, 0, IdleAction.END_TURN, 0); settle(sc, m -> m instanceof DuelMessage.SelectIdleCmd c && c.player() == 1);
            command(sc, 1, IdleAction.SUMMON, 20228463); settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
            command(sc, 1, IdleAction.END_TURN, 0); settle(sc, m -> m instanceof DuelMessage.SelectIdleCmd c && c.player() == 0);
        }).allClients("real Bell public hand artwork and hover", b -> DuelVisibilityMPClient.bellPublic(b))
         .client("A", "pointer activates Raigeki while B remains hovered", b -> DuelVisibilityMPClient.activateRaigeki(b))
         .server("normal Raigeki resolution", sc -> {
             settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
             sc.check("Bell actually destroyed", codes(sc, 1, LOCATION_GRAVE).contains(20228463));
             sc.check("native hand shuffle negative case", fixture(sc).trace.messages().stream().anyMatch(DuelMessage.ShuffleHand.class::isInstance));
         }).allClients("real Bell permission revocation", b -> DuelVisibilityMPClient.bellRevoked(b));
        end(s, "bell");
        s.teardownAllClients("restore observers and fixture artwork", b -> DuelVisibilityMPClient.finish(b))
         .teardownServer("restore collections and close native sessions", sc -> {
             DuelVisibilitySessionFixture f = sc.get("fixture"); String effect = sc.get("effect");
             if (f != null) f.save(effect);
             DuelVisibilitySessionFixture.clearDecorator();
             for (String role : List.of("A", "B")) {
                 var p = sc.player(role); DuelManager.get().forfeit(p);
                 CollectionAttachment original = sc.get("original" + role);
                 if (original != null) {
                     if (Boolean.TRUE.equals(sc.get("present" + role))) p.setData(CollectionAttachments.COLLECTION, original);
                     else p.removeData(CollectionAttachments.COLLECTION);
                 }
             }
         });
    }

    private static void start(MPScenarioBuilder s, String name, List<Integer> aOpening, List<Integer> aExtra,
                              List<Integer> bOpening, List<Integer> bExtra) {
        s.allClients("observe " + name, b -> DuelVisibilityMPClient.begin(b, name))
         .server("real manager start " + name, sc -> {
             var a = sc.player("A"); var b = sc.player("B");
             seed(sc, a, aOpening, aExtra, 42); seed(sc, b, bOpening, bExtra, 43);
             var prep = DuelManager.get().preparation(); long now = System.currentTimeMillis();
             sc.check("legal invitation", prep.invite(a.getUUID(), b.getUUID(), DuelRule.MR5, 42L, new PlayerOptions(8000,5,0), now).code() == PreparationResult.OK);
             var flow = prep.view(a.getUUID(), now).flowId();
             sc.check("legal acceptance", prep.accept(b.getUUID(), flow, now).code() == PreparationResult.OK);
             var round = prep.view(a.getUUID(), now).roundId();
             prep.hand(a.getUUID(), flow, round, FirstTurnLobby.Hand.ROCK, now);
             prep.hand(b.getUUID(), flow, round, FirstTurnLobby.Hand.SCISSORS, now);
             var f = new DuelVisibilitySessionFixture(); sc.put("fixture", f); sc.put("effect", name); f.install();
             sc.check("native start", prep.first(a.getUUID(), flow, true, now).code() == PreparationResult.OK);
             sc.check("both seats live", DuelManager.get().isBusy(a) && DuelManager.get().isBusy(b) && f.session != null);
             settle(sc, DuelMessage.SelectIdleCmd.class::isInstance);
             sc.check("A actual opening cards", codes(sc, 0, LOCATION_HAND).containsAll(aOpening));
             sc.check("B actual opening cards", codes(sc, 1, LOCATION_HAND).containsAll(bOpening));
         });
    }
    private static void end(MPScenarioBuilder s, String name) {
        s.allClients("verify received " + name + " privacy", b -> DuelVisibilityMPClient.receipts(b))
         .server("save native " + name, sc -> { fixture(sc).save(name); DuelManager.get().forfeit(sc.player("A")); })
         .allClients("restore " + name, b -> DuelVisibilityMPClient.finish(b));
    }
    private static void seed(ServerContext sc, ServerPlayer p, List<Integer> opening, List<Integer> extra, long shuffleSeed) {
        var desired = new ArrayList<>(opening);
        for (int code : NORMALS) if (desired.size() < 40 && !desired.contains(code)) desired.add(code);
        var permutation = new ArrayList<Integer>(); for (int i = 0; i < 40; i++) permutation.add(i);
        Collections.shuffle(permutation, new Random(shuffleSeed));
        var saved = new ArrayList<>(Collections.nCopies(40, 0));
        for (int i = 0; i < 40; i++) saved.set(permutation.get(i), desired.get(i));
        sc.check("inverse shuffle preserves normal manager shuffle", new Deck(saved, extra).shuffled(shuffleSeed).main().equals(desired));
        var deck = new SavedDeck(UUID.fromString("6c004000-0000-4000-8000-000000000002"), "Visibility native fixture", new DeckList(saved, extra, List.of()));
        var counts = new HashMap<Integer, Long>();
        if (ServerConfig.requireCardOwnership()) deck.cards().requiredCopies().forEach((code, count) -> counts.put(code, count.longValue()));
        p.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(new PlayerCollectionData(10, counts, Map.of(deck.id(), deck), deck.id())));
    }
    private static DuelVisibilitySessionFixture fixture(ServerContext sc) { return sc.get("fixture"); }
    private static void respond(ServerContext sc, byte[] bytes) {
        var f = fixture(sc); DuelManager.get().handleResponse(sc.player(f.player == 0 ? "A" : "B"), bytes);
    }
    private static void command(ServerContext sc, int player, int type, int code) {
        var c = (DuelMessage.SelectIdleCmd) fixture(sc).prompt;
        if (c.player() != player) throw new AssertionError("Wrong native turn for command");
        List<Integer> codes = switch (type) {
            case IdleAction.SUMMON -> c.summonable().stream().map(DuelMessage.IdleCmdCard::code).toList();
            case IdleAction.SPECIAL_SUMMON -> c.specialSummonable().stream().map(DuelMessage.IdleCmdCard::code).toList();
            case IdleAction.ACTIVATE -> c.activatable().stream().map(DuelMessage.ActivatableCard::code).toList();
            default -> List.of(0);
        };
        respond(sc, ResponseValidator.selectCmd(c, type, codes.indexOf(code)));
    }
    /** Bounded normal-play setup; only expected place/position, optional chain and Trident material prompts. */
    private static void settle(ServerContext sc, Predicate<DuelMessage> expected) {
        for (int i = 0; i < 30; i++) {
            var prompt = fixture(sc).prompt;
            if (prompt != null && expected.test(prompt)) return;
            byte[] bytes = switch (prompt) {
                case DuelMessage.SelectChain c when !c.forced() -> ResponseValidator.selectChain(c, -1);
                case DuelMessage.SelectPosition p -> ResponseValidator.selectPosition(p, Integer.lowestOneBit(p.positions()));
                case DuelMessage.SelectPlace p -> {
                    int bit = Integer.numberOfTrailingZeros(~p.field());
                    yield ResponseValidator.selectPlace(p, bit >= 16 ? 1 - p.player() : p.player(),
                            (bit % 16) >= 8 ? LOCATION_SZONE : LOCATION_MZONE, bit % 8);
                }
                case DuelMessage.SelectUnselectCard p -> {
                    if (!"trident".equals(sc.get("effect"))) throw new AssertionError("Unexpected material prompt");
                    int index = java.util.stream.IntStream.range(0, p.selectableCards().size()).filter(j ->
                            List.of(57568840,75582395).contains(p.selectableCards().get(j).code())).findFirst().orElse(-1);
                    yield ResponseValidator.selectUnselectCard(p, index);
                }
                case null, default -> throw new AssertionError("Unexpected normal-play setup prompt: " + prompt);
            };
            respond(sc, bytes);
        }
        throw new AssertionError("Normal-play setup exceeded 30 prompts");
    }
    private static List<Integer> codes(ServerContext sc, int player, int location) {
        return FieldQuery.parseLocation(fixture(sc).session.queryLocation(QUERY_CODE, player, location)).stream()
                .filter(Objects::nonNull).map(c -> c.code).toList();
    }
    private static void answer(ServerContext sc, int player, String hex) {
        sc.check("actual accepted non-first response " + hex, fixture(sc).answers.stream().anyMatch(a ->
                a.player() == player && HexFormat.of().formatHex(a.bytes()).equals(hex)));
    }
}

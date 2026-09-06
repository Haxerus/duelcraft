# Duel UI Scaling and Rule-Driven Field Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the duel screen fit every GUI scale from 1 to 4 and draw the field the rule set asks for, verified by JUnit and LDLib2 in-client scenarios.

**Architecture:** The XML root becomes a fixed 960x540 design canvas that a `ModularUIScreen` subclass scales uniformly into the window. A pure-Java `FieldLayout` derived from the duel flags owns zone visibility and the engine-zone-to-slot mapping, and `FieldRenderer` resolves every zone through it against a superset XML. A `DuelRule` enum carries the eight engine presets from `/duel` commands through `DuelOptions` and a new `duelFlags` field on `DuelStartPayload` to the client.

**Tech Stack:** Java 21, NeoForge 21.1.224 on Minecraft 1.21.1, LDLib2 2.2.39.a (XML + LSS + Transform2D + uitest harness), JUnit 5, Gradle 9.2.1 with ModDevGradle 2.0.141.

**Spec:** `docs/superpowers/specs/2026-09-05-duel-ui-scaling-and-field-layout-design.md`

## Global Constraints

- Design canvas is exactly `960` x `540` design pixels; scale factor `k = max(0.25, min(width / 960f, height / 540f))`.
- Slot id vocabulary is fixed: `plr-mon-0..4`, `opp-mon-0..4`, `plr-st-0..4`, `opp-st-0..4`, `plr-field-spell`, `opp-field-spell`, `emz-left`, `emz-right`, `plr-pz-left`, `plr-pz-right`, `opp-pz-left`, `opp-pz-right`, `plr-deck`, `plr-extra-deck`, `plr-graveyard`, `plr-banished` and the `opp-` four. Owner frame for everything except the two EMZ slots, which are in the viewer's frame.
- `DuelRule` presets carry engine flags only. No LP, hand, or draw changes.
- Keep the current visual style. No new textures, colors, or spacing beyond what the restructuring needs.
- The authoritative layout file is `src/main/resources/assets/duelcraft/ui/duel_screen.xml`.
- Harness scenarios live in `src/main/java/com/haxerus/duelcraft/client/uitest/` and are registered `RegistrationEnvironment.DEV_ONLY`.
- Follow `CLAUDE.md` Best Practices: minimal code, surgical diffs, no speculative abstractions, no re-indenting of moved blocks.
- LDLib2 source is checked out at `../LDLib2` (branch `1.21`). Read it for any API question before guessing.
- Run `./gradlew test` after every Java task; all suites must stay green (173 tests before this plan).

---

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `src/main/java/com/haxerus/duelcraft/core/DuelRule.java` | create | Eight engine presets, flags, lowercase id, parse |
| `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java` | modify | `of(seed, rule)`; `standard(seed)` delegates to MR5 |
| `src/test/java/com/haxerus/duelcraft/core/DuelRuleTest.java` | create | Parse round trip, flags equal constants |
| `src/main/java/com/haxerus/duelcraft/server/DuelStartPayload.java` | modify | `duelFlags` field, hand-written codec |
| `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java` | modify | Stores `duelFlags` |
| `src/main/java/com/haxerus/duelcraft/server/DuelManager.java` | modify | Start methods take `DuelRule`, log it, put flags in payload |
| `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java` | modify | Optional trailing `rule` on `test` and `challenge`; `PendingChallenge.rule` |
| `src/main/java/com/haxerus/duelcraft/client/FieldLayout.java` | create | Pure model: visibility and zone-to-slot mapping |
| `src/test/java/com/haxerus/duelcraft/client/FieldLayoutTest.java` | create | Every preset, every mapping, round trip |
| `src/main/resources/assets/duelcraft/ui/duel_screen.xml` | modify | Fixed root, canvas wrapper, pendulum slots and markers, visibility classes |
| `src/main/java/com/haxerus/duelcraft/client/DuelScreen.java` | create | `ModularUIScreen` subclass applying the scale in `init()` |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` | modify | `create()`, constant size provider, canvas lookup, layout construction |
| `src/main/java/com/haxerus/duelcraft/client/ClickDispatcher.java` | modify | Screen-to-canvas coordinate conversion for the context menu |
| `src/main/java/com/haxerus/duelcraft/client/FieldRenderer.java` | rewrite | Slot map keyed by id; all lookups through `FieldLayout` |
| `gradle/ldlib2-uitest.gradle` | create | Harness wiring ported to ModDevGradle |
| `build.gradle` | modify | `apply from:` the harness script |
| `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java` | create | Synthetic payload and populate messages |
| `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenScenario.java` | create | Abstract scenario: open, populate, shared checks, screenshot |
| `src/main/java/com/haxerus/duelcraft/client/uitest/Duel*Scenario.java` (5) | create | One rule and GUI scale each |
| `CLAUDE.md` (gitignored, local) | modify | Document the harness command |

---

### Task 1: `DuelRule` presets and `DuelOptions.of(seed, rule)`

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/core/DuelRule.java`
- Modify: `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java`
- Test: `src/test/java/com/haxerus/duelcraft/core/DuelRuleTest.java`

**Interfaces:**
- Consumes: `OcgConstants.DUEL_MODE_MR1 .. DUEL_MODE_RUSH` (existing `long` constants).
- Produces: `enum DuelRule { MR1, GOAT, MR2, MR3, MR4, MR5, SPEED, RUSH }` with `long flags()`, `String id()`, `static Optional<DuelRule> parse(String)`, `static List<String> ids()`; `DuelOptions.of(long seed, DuelRule rule)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/haxerus/duelcraft/core/DuelRuleTest.java`:

```java
package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class DuelRuleTest {

    @Test
    void everyIdParsesBackToItsRule() {
        for (DuelRule rule : DuelRule.values()) {
            assertEquals(Optional.of(rule), DuelRule.parse(rule.id()), rule.name());
        }
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(Optional.of(DuelRule.MR3), DuelRule.parse("MR3"));
        assertEquals(Optional.of(DuelRule.SPEED), DuelRule.parse("Speed"));
    }

    @Test
    void parseRejectsUnknownIds() {
        assertTrue(DuelRule.parse("mr6").isEmpty());
        assertTrue(DuelRule.parse("").isEmpty());
    }

    @Test
    void flagsMatchTheEnginePresets() {
        assertEquals(DUEL_MODE_MR1, DuelRule.MR1.flags());
        assertEquals(DUEL_MODE_GOAT, DuelRule.GOAT.flags());
        assertEquals(DUEL_MODE_MR2, DuelRule.MR2.flags());
        assertEquals(DUEL_MODE_MR3, DuelRule.MR3.flags());
        assertEquals(DUEL_MODE_MR4, DuelRule.MR4.flags());
        assertEquals(DUEL_MODE_MR5, DuelRule.MR5.flags());
        assertEquals(DUEL_MODE_SPEED, DuelRule.SPEED.flags());
        assertEquals(DUEL_MODE_RUSH, DuelRule.RUSH.flags());
    }

    @Test
    void idsAreLowercaseAndUnique() {
        List<String> ids = DuelRule.ids();
        assertEquals(8, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size());
        for (String id : ids) {
            assertEquals(id.toLowerCase(Locale.ROOT), id);
        }
    }

    @Test
    void standardOptionsUseMr5AndOfUsesTheGivenRule() {
        assertEquals(DUEL_MODE_MR5, DuelOptions.standard(1L).flags());
        assertEquals(DUEL_MODE_MR3, DuelOptions.of(1L, DuelRule.MR3).flags());
        assertArrayEquals(SeedExpander.toFourLongs(1L), DuelOptions.of(1L, DuelRule.MR3).seed());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DuelRuleTest"`
Expected: compilation error, `cannot find symbol: class DuelRule`.

- [ ] **Step 3: Create the enum**

Create `src/main/java/com/haxerus/duelcraft/core/DuelRule.java`:

```java
package com.haxerus.duelcraft.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** The eight ygopro-core rule presets, addressable from chat commands by a lowercase id. */
public enum DuelRule {
    MR1(DUEL_MODE_MR1),
    GOAT(DUEL_MODE_GOAT),
    MR2(DUEL_MODE_MR2),
    MR3(DUEL_MODE_MR3),
    MR4(DUEL_MODE_MR4),
    MR5(DUEL_MODE_MR5),
    SPEED(DUEL_MODE_SPEED),
    RUSH(DUEL_MODE_RUSH);

    private final long flags;

    DuelRule(long flags) {
        this.flags = flags;
    }

    /** Engine flags for {@code OCG_CreateDuel}. */
    public long flags() {
        return flags;
    }

    /** Lowercase id used on the command line, e.g. {@code mr3}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Case-insensitive lookup by id; empty for anything unknown. */
    public static Optional<DuelRule> parse(String id) {
        for (DuelRule rule : values()) {
            if (rule.id().equalsIgnoreCase(id)) return Optional.of(rule);
        }
        return Optional.empty();
    }

    /** Every id, in declaration order, for tab completion. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(DuelRule::id).toList();
    }
}
```

- [ ] **Step 4: Add the factory to `DuelOptions`**

Replace the body of `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java` so the two factories read:

```java
    /** Options for {@code rule} with a fully expanded seed. */
    public static DuelOptions of(long seed, DuelRule rule) {
        return new DuelOptions(
                SeedExpander.toFourLongs(seed),
                rule.flags(),
                PlayerOptions.standard(),
                PlayerOptions.standard());
    }

    /** Master Rule 5 with the given seed. Kept for existing callers and tests. */
    public static DuelOptions standard(long seed) {
        return of(seed, DuelRule.MR5);
    }

    /** Master Rule 5 with a freshly randomized seed. */
    public static DuelOptions standard() {
        return standard(ThreadLocalRandom.current().nextLong());
    }
```

The record header, package, and the `ThreadLocalRandom` import stay as they are. The `OcgConstants` reference in the old `standard(long)` body goes away with it.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DuelRuleTest"`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: Run the whole suite and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no failures.

```bash
git add src/main/java/com/haxerus/duelcraft/core/DuelRule.java src/main/java/com/haxerus/duelcraft/core/DuelOptions.java src/test/java/com/haxerus/duelcraft/core/DuelRuleTest.java
git commit -m "Add DuelRule presets and DuelOptions.of(seed, rule)"
```

---

### Task 2: Duel flags on the start payload, in client state, and through `DuelManager`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelStartPayload.java`
- Modify: `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java` (fields near line 68, constructor at line 150)
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelManager.java` (start methods, lines 87 to 211)
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java` (two call sites, lines 121 and 159)

**Interfaces:**
- Consumes: `DuelRule` and `DuelOptions.of` from Task 1.
- Produces: `DuelStartPayload(int localPlayer, String opponentName, int lp0, int lp1, int deckSize, int extraSize, long duelFlags)`; `ClientDuelState.duelFlags` (`public final long`); `DuelManager.startSoloDuel(ServerPlayer, long seed, DuelRule, Deck, Deck, String, String)` and `DuelManager.startDuel(ServerPlayer, ServerPlayer, long seed, DuelRule, Deck, Deck, String, String)`.

No JUnit test covers this task: the payload codec needs Minecraft classes. The gate is a clean compile plus the manual log check in Task 9.

- [ ] **Step 1: Rewrite `DuelStartPayload`**

Replace the whole file:

```java
package com.haxerus.duelcraft.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: sent when a duel begins, before any duel messages.
 * Tells the client which player they are, the opponent's name, initial game state,
 * and the engine flags the client derives the field layout from.
 */
public record DuelStartPayload(
        int localPlayer,
        String opponentName,
        int lp0,
        int lp1,
        int deckSize,
        int extraSize,
        long duelFlags
) implements CustomPacketPayload {

    public static final Type<DuelStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("duelcraft", "duel_start"));

    // Hand-written because StreamCodec.composite stops at six fields.
    public static final StreamCodec<FriendlyByteBuf, DuelStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.localPlayer());
                buf.writeUtf(payload.opponentName());
                buf.writeVarInt(payload.lp0());
                buf.writeVarInt(payload.lp1());
                buf.writeVarInt(payload.deckSize());
                buf.writeVarInt(payload.extraSize());
                buf.writeLong(payload.duelFlags());
            },
            buf -> new DuelStartPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`DuelNetworking.onRegisterPayloads` keeps calling `registrar.playToClient(DuelStartPayload.TYPE, DuelStartPayload.STREAM_CODEC, ...)` unchanged; `FriendlyByteBuf` is a supertype of `RegistryFriendlyByteBuf`, the same arrangement `DuelMessagePayload` already uses.

- [ ] **Step 2: Store the flags in `ClientDuelState`**

In `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java`, directly under `public final String opponentName;` add:

```java
    // Engine flags this duel was created with; the field layout is derived from them.
    public final long duelFlags;
```

In the constructor, directly under `this.opponentName = startInfo.opponentName();` add:

```java
        this.duelFlags = startInfo.duelFlags();
```

- [ ] **Step 3: Thread the rule through `DuelManager`**

In `src/main/java/com/haxerus/duelcraft/server/DuelManager.java` add the import `import com.haxerus.duelcraft.core.DuelRule;` next to the other `core` imports.

Replace the solo-duel convenience overload and signature:

```java
    /** Convenience: random seed, Master Rule 5. */
    public void startSoloDuel(ServerPlayer player, Deck playerDeck, Deck aiDeck) {
        startSoloDuel(player, java.util.concurrent.ThreadLocalRandom.current().nextLong(), DuelRule.MR5,
                playerDeck, aiDeck, null, null);
    }

    /**
     * Start a solo test duel where player 1 is AI-controlled.
     * Both decks are deterministically shuffled with {@code seed} before being handed to the engine.
     * {@code playerDeckName} and {@code aiDeckName} are used only for logging.
     */
    public void startSoloDuel(ServerPlayer player, long seed, DuelRule rule,
                              Deck playerDeck, Deck aiDeck,
                              String playerDeckName, String aiDeckName) {
```

Inside it, change three lines:

```java
        DuelOptions options = DuelOptions.of(seed, rule);
```

```java
        LOGGER.info("Solo duel {}: seed={}, rule={}, player={}, aiDeck={}",
                duelId, seed, rule.id(),
                playerDeckName != null ? playerDeckName : "<standard>",
                aiDeckName != null ? aiDeckName : "<standard>");
```

```java
        PacketDistributor.sendToPlayer(player, new DuelStartPayload(0, "AI Opponent",
                lp0, lp1, shuffledPlayer.main().size(), shuffledPlayer.extra().size(), options.flags()));
```

Then the two-player duel, same pattern:

```java
    /** Convenience: random seed, Master Rule 5, decks resolved earlier by the caller. */
    public void startDuel(ServerPlayer p1, ServerPlayer p2, Deck team1Deck, Deck team2Deck) {
        startDuel(p1, p2, java.util.concurrent.ThreadLocalRandom.current().nextLong(), DuelRule.MR5,
                team1Deck, team2Deck, null, null);
    }

    public void startDuel(ServerPlayer p1, ServerPlayer p2, long seed, DuelRule rule,
                          Deck team1Deck, Deck team2Deck,
                          String team1Name, String team2Name) {
```

```java
        DuelOptions options = DuelOptions.of(seed, rule);
```

```java
        LOGGER.info("Duel {}: seed={}, rule={}, decks=[{}, {}]",
                duelId, seed, rule.id(),
                team1Name != null ? team1Name : "<standard>",
                team2Name != null ? team2Name : "<standard>");
```

```java
        PacketDistributor.sendToPlayer(p1, new DuelStartPayload(0, p2.getName().getString(),
                lp0, lp1, deckSize, extraSize, options.flags()));
        PacketDistributor.sendToPlayer(p2, new DuelStartPayload(1, p1.getName().getString(),
                lp0, lp1, deckSize, extraSize, options.flags()));
```

- [ ] **Step 4: Keep `DuelCommand` compiling with the MR5 default**

In `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java` add `import com.haxerus.duelcraft.core.DuelRule;` and change the two calls:

```java
        DuelManager.get().startDuel(challenger, player, pending.seed(), DuelRule.MR5,
                challengerDeck, accepterDeck, challengerName, accepterName);
```

```java
        DuelManager.get().startSoloDuel(player, seed, DuelRule.MR5, playerDeck, aiDeck, playerDeckName, aiDeckName);
```

Task 3 replaces `DuelRule.MR5` with the parsed argument.

- [ ] **Step 5: Compile and run the suite**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelStartPayload.java src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java src/main/java/com/haxerus/duelcraft/server/DuelManager.java src/main/java/com/haxerus/duelcraft/server/DuelCommand.java
git commit -m "DuelStartPayload: carry duel flags; DuelManager: take a DuelRule"
```

---

### Task 3: Optional `rule` argument on `/duel test` and `/duel challenge`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java`

**Interfaces:**
- Consumes: `DuelRule.parse`, `DuelRule.ids()`, the `DuelManager` signatures from Task 2.
- Produces: `PendingChallenge(UUID challengerUUID, long seed, DuelRule rule)`; commands `/duel test [aiDeck [seed [rule]]]` and `/duel challenge <player> [seed [rule]]`.

- [ ] **Step 1: Add the imports and the rule helpers**

Add these imports to `DuelCommand.java`:

```java
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
```

Below the existing `DECK_NAMES` suggestion provider add:

```java
    private static final SuggestionProvider<CommandSourceStack> RULE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(DuelRule.ids(), builder);

    private static final DynamicCommandExceptionType UNKNOWN_RULE = new DynamicCommandExceptionType(
            id -> Component.literal("Unknown rule '" + id + "'. Valid rules: " + String.join(", ", DuelRule.ids())));

    private static DuelRule parseRule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "rule");
        return DuelRule.parse(id).orElseThrow(() -> UNKNOWN_RULE.create(id));
    }
```

- [ ] **Step 2: Extend the command tree**

Replace the `challenge` and `test` branches inside `register`:

```java
                        .then(Commands.literal("challenge")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> challenge(ctx, ThreadLocalRandom.current().nextLong(), DuelRule.MR5))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> challenge(ctx, LongArgumentType.getLong(ctx, "seed"), DuelRule.MR5))
                                                .then(Commands.argument("rule", StringArgumentType.word())
                                                        .suggests(RULE_IDS)
                                                        .executes(ctx -> challenge(ctx,
                                                                LongArgumentType.getLong(ctx, "seed"),
                                                                parseRule(ctx)))))))
```

```java
                        .then(Commands.literal("test")
                                .executes(ctx -> test(ctx, null, ThreadLocalRandom.current().nextLong(), DuelRule.MR5))
                                .then(Commands.argument("aiDeck", StringArgumentType.string())
                                        .suggests(DECK_NAMES)
                                        .executes(ctx -> test(ctx,
                                                StringArgumentType.getString(ctx, "aiDeck"),
                                                ThreadLocalRandom.current().nextLong(),
                                                DuelRule.MR5))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> test(ctx,
                                                        StringArgumentType.getString(ctx, "aiDeck"),
                                                        LongArgumentType.getLong(ctx, "seed"),
                                                        DuelRule.MR5))
                                                .then(Commands.argument("rule", StringArgumentType.word())
                                                        .suggests(RULE_IDS)
                                                        .executes(ctx -> test(ctx,
                                                                StringArgumentType.getString(ctx, "aiDeck"),
                                                                LongArgumentType.getLong(ctx, "seed"),
                                                                parseRule(ctx)))))))
```

- [ ] **Step 3: Pass the rule through the handlers**

Change the `challenge` signature and its two affected lines:

```java
    private static int challenge(CommandContext<CommandSourceStack> ctx, long seed, DuelRule rule)
            throws CommandSyntaxException {
```

```java
        DuelManager.get().duelInvites.put(target.getUUID(), new PendingChallenge(sender.getUUID(), seed, rule));
        sender.sendSystemMessage(Component.literal("Sent duel challenge (seed=" + seed + ", rule=" + rule.id() + ")."));
```

In `accept`, replace `DuelRule.MR5` from Task 2 with the pending rule:

```java
        DuelManager.get().startDuel(challenger, player, pending.seed(), pending.rule(),
                challengerDeck, accepterDeck, challengerName, accepterName);
```

Change the `test` signature and its two affected lines:

```java
    private static int test(CommandContext<CommandSourceStack> ctx, String aiDeckName, long seed, DuelRule rule)
            throws CommandSyntaxException {
```

```java
        player.sendSystemMessage(Component.literal("Starting solo test duel vs AI (seed=" + seed + ", rule=" + rule.id() + ")..."));
        DuelManager.get().startSoloDuel(player, seed, rule, playerDeck, aiDeck, playerDeckName, aiDeckName);
```

Replace the record at the bottom of the file:

```java
    /** Pending challenge: who challenged, the agreed seed, and the rule set. */
    public record PendingChallenge(UUID challengerUUID, long seed, DuelRule rule) {}
```

- [ ] **Step 4: Compile and run the suite**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelCommand.java
git commit -m "DuelCommand: optional rule argument on test and challenge"
```

---

### Task 4: `FieldLayout` model

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/client/FieldLayout.java`
- Test: `src/test/java/com/haxerus/duelcraft/client/FieldLayoutTest.java`

**Interfaces:**
- Consumes: `OcgConstants.DUEL_3_COLUMNS_FIELD`, `DUEL_EMZONE`, `DUEL_PZONE`, `DUEL_SEPARATE_PZONE`, `LOCATION_*`; `DuelRule.flags()` in the test.
- Produces:

```java
public record FieldLayout(int columns, boolean emz, PendulumMode pendulum) {
    public enum PendulumMode { NONE, SHARED, SEPARATE }
    public enum Side { PLR, OPP }
    public record Zone(Side side, int location, int sequence) {}
    public static FieldLayout fromFlags(long flags);
    public Optional<String> slotId(Zone zone);
    public List<Zone> zonesOf(String id);
    public Set<String> hiddenSlotIds();
    public int[] pendulumSequences();
    public static List<String> allSlotIds();
}
```

The class must not import any Minecraft or LDLib2 type; the test runs without a client.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/haxerus/duelcraft/client/FieldLayoutTest.java`:

```java
package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.FieldLayout.Side;
import com.haxerus.duelcraft.client.FieldLayout.Zone;
import com.haxerus.duelcraft.core.DuelRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldLayoutTest {

    private static final FieldLayout MR5 = FieldLayout.fromFlags(DuelRule.MR5.flags());
    private static final FieldLayout MR3 = FieldLayout.fromFlags(DuelRule.MR3.flags());
    private static final FieldLayout SPEED = FieldLayout.fromFlags(DuelRule.SPEED.flags());

    private static Zone z(Side side, int location, int sequence) {
        return new Zone(side, location, sequence);
    }

    // ---- fromFlags ----

    @Test
    void presetsResolveToTheThreeGeometries() {
        for (DuelRule r : List.of(DuelRule.MR1, DuelRule.GOAT, DuelRule.MR2)) {
            assertEquals(new FieldLayout(5, false, PendulumMode.NONE), FieldLayout.fromFlags(r.flags()), r.name());
        }
        assertEquals(new FieldLayout(5, false, PendulumMode.SEPARATE), MR3);
        for (DuelRule r : List.of(DuelRule.MR4, DuelRule.MR5)) {
            assertEquals(new FieldLayout(5, true, PendulumMode.SHARED), FieldLayout.fromFlags(r.flags()), r.name());
        }
        for (DuelRule r : List.of(DuelRule.SPEED, DuelRule.RUSH)) {
            assertEquals(new FieldLayout(3, false, PendulumMode.NONE), FieldLayout.fromFlags(r.flags()), r.name());
        }
    }

    @Test
    void threeColumnsNeverHaveExtraMonsterZones() {
        assertFalse(FieldLayout.fromFlags(DUEL_3_COLUMNS_FIELD | DUEL_EMZONE).emz());
    }

    @Test
    void separateBitWithoutPendulumBitMeansNoPendulum() {
        assertEquals(PendulumMode.NONE, FieldLayout.fromFlags(DUEL_SEPARATE_PZONE).pendulum());
    }

    // ---- MR5 ----

    @Test
    void mr5MapsMainZonesInTheOwnersFrame() {
        assertEquals(Optional.of("plr-mon-0"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 0)));
        assertEquals(Optional.of("opp-mon-4"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 4)));
        assertEquals(Optional.of("plr-st-2"), MR5.slotId(z(Side.PLR, LOCATION_SZONE, 2)));
        assertEquals(Optional.of("opp-field-spell"), MR5.slotId(z(Side.OPP, LOCATION_SZONE, 5)));
    }

    @Test
    void mr5CrossMapsExtraMonsterZones() {
        assertEquals(Optional.of("emz-left"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 5)));
        assertEquals(Optional.of("emz-right"), MR5.slotId(z(Side.PLR, LOCATION_MZONE, 6)));
        assertEquals(Optional.of("emz-right"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 5)));
        assertEquals(Optional.of("emz-left"), MR5.slotId(z(Side.OPP, LOCATION_MZONE, 6)));
    }

    @Test
    void mr5HasNoSeparatePendulumZones() {
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_SZONE, 6)).isEmpty());
        assertTrue(MR5.slotId(z(Side.OPP, LOCATION_SZONE, 7)).isEmpty());
        assertArrayEquals(new int[]{0, 4}, MR5.pendulumSequences());
        assertEquals(Set.of("plr-pz-left", "plr-pz-right", "opp-pz-left", "opp-pz-right"), MR5.hiddenSlotIds());
    }

    @Test
    void emzSlotsListTheViewersZoneFirst() {
        assertEquals(List.of(z(Side.PLR, LOCATION_MZONE, 5), z(Side.OPP, LOCATION_MZONE, 6)), MR5.zonesOf("emz-left"));
        assertEquals(List.of(z(Side.PLR, LOCATION_MZONE, 6), z(Side.OPP, LOCATION_MZONE, 5)), MR5.zonesOf("emz-right"));
    }

    // ---- MR3 ----

    @Test
    void mr3UsesSeparatePendulumSlotsAndHidesEmz() {
        assertEquals(Optional.of("plr-pz-left"), MR3.slotId(z(Side.PLR, LOCATION_SZONE, 6)));
        assertEquals(Optional.of("plr-pz-right"), MR3.slotId(z(Side.PLR, LOCATION_SZONE, 7)));
        assertEquals(Optional.of("opp-pz-left"), MR3.slotId(z(Side.OPP, LOCATION_SZONE, 6)));
        assertTrue(MR3.slotId(z(Side.PLR, LOCATION_MZONE, 5)).isEmpty());
        assertArrayEquals(new int[]{6, 7}, MR3.pendulumSequences());
        assertEquals(Set.of("emz-left", "emz-right"), MR3.hiddenSlotIds());
    }

    // ---- Speed ----

    @Test
    void speedShowsOnlyTheMiddleThreeColumns() {
        assertTrue(SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 0)).isEmpty());
        assertEquals(Optional.of("plr-mon-1"), SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 1)));
        assertEquals(Optional.of("opp-st-3"), SPEED.slotId(z(Side.OPP, LOCATION_SZONE, 3)));
        assertTrue(SPEED.slotId(z(Side.OPP, LOCATION_SZONE, 4)).isEmpty());
        assertTrue(SPEED.slotId(z(Side.PLR, LOCATION_MZONE, 5)).isEmpty());
        assertArrayEquals(new int[0], SPEED.pendulumSequences());
        assertEquals(Set.of("plr-mon-0", "plr-mon-4", "plr-st-0", "plr-st-4",
                        "opp-mon-0", "opp-mon-4", "opp-st-0", "opp-st-4",
                        "emz-left", "emz-right",
                        "plr-pz-left", "plr-pz-right", "opp-pz-left", "opp-pz-right"),
                SPEED.hiddenSlotIds());
    }

    @Test
    void sharedPendulumOnThreeColumnsUsesSequencesOneAndThree() {
        assertArrayEquals(new int[]{1, 3},
                FieldLayout.fromFlags(DUEL_3_COLUMNS_FIELD | DUEL_PZONE).pendulumSequences());
    }

    // ---- Piles, unknowns, round trip ----

    @Test
    void pilesMapForEveryRule() {
        for (FieldLayout layout : List.of(MR5, MR3, SPEED)) {
            assertEquals(Optional.of("plr-deck"), layout.slotId(z(Side.PLR, LOCATION_DECK, 0)));
            assertEquals(Optional.of("opp-graveyard"), layout.slotId(z(Side.OPP, LOCATION_GRAVE, 3)));
            assertEquals(Optional.of("plr-banished"), layout.slotId(z(Side.PLR, LOCATION_REMOVED, 0)));
            assertEquals(Optional.of("opp-extra-deck"), layout.slotId(z(Side.OPP, LOCATION_EXTRA, 0)));
        }
    }

    @Test
    void unknownLocationsAndSlotsHaveNoMapping() {
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_HAND, 0)).isEmpty());
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_MZONE, 7)).isEmpty());
        assertTrue(MR5.slotId(z(Side.PLR, LOCATION_SZONE, 8)).isEmpty());
        assertTrue(MR5.zonesOf("no-such-slot").isEmpty());
    }

    @Test
    void supersetHasThirtySixSlots() {
        assertEquals(36, FieldLayout.allSlotIds().size());
    }

    @Test
    void everyVisibleSlotRoundTrips() {
        for (FieldLayout layout : List.of(MR5, MR3, SPEED)) {
            for (String id : FieldLayout.allSlotIds()) {
                if (layout.hiddenSlotIds().contains(id)) continue;
                List<Zone> zones = layout.zonesOf(id);
                assertFalse(zones.isEmpty(), id);
                for (Zone zone : zones) {
                    assertEquals(Optional.of(id), layout.slotId(zone), id);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.FieldLayoutTest"`
Expected: compilation error, `cannot find symbol: class FieldLayout`.

- [ ] **Step 3: Implement `FieldLayout`**

Create `src/main/java/com/haxerus/duelcraft/client/FieldLayout.java`:

```java
package com.haxerus.duelcraft.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Which zones exist for a rule set and which XML slot each engine zone lives in.
 * Plain Java with no Minecraft or LDLib2 types, so JUnit covers it without a client.
 *
 * <p>Zones are viewer-relative ({@link Side}); {@code FieldRenderer} converts absolute player
 * indices before asking. Slot ids are in the owner's frame ({@code opp-st-0} is the opponent's
 * zone 0, mirrored by CSS) except the two Extra Monster Zone slots, which are shared physical
 * positions named in the viewer's frame.
 */
public record FieldLayout(int columns, boolean emz, PendulumMode pendulum) {

    public enum PendulumMode { NONE, SHARED, SEPARATE }

    /** Table side as seen by the local player. */
    public enum Side {
        PLR("plr"), OPP("opp");

        final String prefix;

        Side(String prefix) {
            this.prefix = prefix;
        }
    }

    public record Zone(Side side, int location, int sequence) {}

    /** Total: never throws. Three columns force no EMZ; a separate-pendulum bit needs the pendulum bit. */
    public static FieldLayout fromFlags(long flags) {
        int columns = (flags & DUEL_3_COLUMNS_FIELD) != 0 ? 3 : 5;
        boolean emz = (flags & DUEL_EMZONE) != 0 && columns == 5;
        PendulumMode pendulum = (flags & DUEL_PZONE) == 0 ? PendulumMode.NONE
                : (flags & DUEL_SEPARATE_PZONE) != 0 ? PendulumMode.SEPARATE : PendulumMode.SHARED;
        return new FieldLayout(columns, emz, pendulum);
    }

    /** Engine zone → slot id; empty when this rule has no such zone. */
    public Optional<String> slotId(Zone z) {
        String p = z.side().prefix;
        return Optional.ofNullable(switch (z.location()) {
            case LOCATION_MZONE -> {
                if (z.sequence() <= 4) yield columnVisible(z.sequence()) ? p + "-mon-" + z.sequence() : null;
                if (!emz) yield null;
                if (z.sequence() == 5) yield z.side() == Side.PLR ? "emz-left" : "emz-right";
                if (z.sequence() == 6) yield z.side() == Side.PLR ? "emz-right" : "emz-left";
                yield null;
            }
            case LOCATION_SZONE -> {
                if (z.sequence() <= 4) yield columnVisible(z.sequence()) ? p + "-st-" + z.sequence() : null;
                if (z.sequence() == 5) yield p + "-field-spell";
                if (pendulum != PendulumMode.SEPARATE) yield null;
                if (z.sequence() == 6) yield p + "-pz-left";
                if (z.sequence() == 7) yield p + "-pz-right";
                yield null;
            }
            case LOCATION_EXTRA -> p + "-extra-deck";
            case LOCATION_GRAVE -> p + "-graveyard";
            case LOCATION_REMOVED -> p + "-banished";
            case LOCATION_DECK -> p + "-deck";
            default -> null;
        });
    }

    /** Slot id → the engine zones that can occupy it, viewer's zone first. Empty for ids this rule hides. */
    public List<Zone> zonesOf(String id) {
        List<Zone> zones = new ArrayList<>(2);
        for (Zone zone : allZones()) {
            if (slotId(zone).filter(id::equals).isPresent()) zones.add(zone);
        }
        return zones;
    }

    /** Superset ids the XML contains that this rule does not use. */
    public Set<String> hiddenSlotIds() {
        Set<String> hidden = new LinkedHashSet<>();
        for (String id : allSlotIds()) {
            if (zonesOf(id).isEmpty()) hidden.add(id);
        }
        return hidden;
    }

    /** Engine S/T sequences that act as pendulum zones under this rule. */
    public int[] pendulumSequences() {
        return switch (pendulum) {
            case NONE -> new int[0];
            case SEPARATE -> new int[]{6, 7};
            case SHARED -> columns == 3 ? new int[]{1, 3} : new int[]{0, 4};
        };
    }

    /** Every slot id the XML must contain, for binding. */
    public static List<String> allSlotIds() {
        List<String> ids = new ArrayList<>();
        for (Side side : Side.values()) {
            String p = side.prefix;
            for (int i = 0; i <= 4; i++) ids.add(p + "-mon-" + i);
            for (int i = 0; i <= 4; i++) ids.add(p + "-st-" + i);
            ids.add(p + "-field-spell");
            ids.add(p + "-pz-left");
            ids.add(p + "-pz-right");
            ids.add(p + "-extra-deck");
            ids.add(p + "-graveyard");
            ids.add(p + "-banished");
            ids.add(p + "-deck");
        }
        ids.add("emz-left");
        ids.add("emz-right");
        return ids;
    }

    private boolean columnVisible(int sequence) {
        if (sequence < 0 || sequence > 4) return false;
        return columns == 5 || (sequence >= 1 && sequence <= 3);
    }

    /** Every zone a duel can address: MZONE 0-6, SZONE 0-7, and the four piles, PLR side first. */
    private static List<Zone> allZones() {
        List<Zone> zones = new ArrayList<>();
        for (Side side : Side.values()) {
            for (int s = 0; s <= 6; s++) zones.add(new Zone(side, LOCATION_MZONE, s));
            for (int s = 0; s <= 7; s++) zones.add(new Zone(side, LOCATION_SZONE, s));
            for (int loc : new int[]{LOCATION_EXTRA, LOCATION_GRAVE, LOCATION_REMOVED, LOCATION_DECK}) {
                zones.add(new Zone(side, loc, 0));
            }
        }
        return zones;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.FieldLayoutTest"`
Expected: BUILD SUCCESSFUL, 14 tests passed.

- [ ] **Step 5: Run the whole suite and commit**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no failures.

```bash
git add src/main/java/com/haxerus/duelcraft/client/FieldLayout.java src/test/java/com/haxerus/duelcraft/client/FieldLayoutTest.java
git commit -m "Add FieldLayout: rule-driven zone visibility and slot mapping"
```

---

### Task 5: Fixed design canvas scaled to the viewport

**Files:**
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml` (style lines 6 to 12, tree lines 583 and 791)
- Create: `src/main/java/com/haxerus/duelcraft/client/DuelScreen.java`
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` (`open`, `loadFromXml`, `UIRefresher` constructor)
- Modify: `src/main/java/com/haxerus/duelcraft/client/ClickDispatcher.java` (constructor, `onCardClicked`, `showContextMenu`)

**Interfaces:**
- Consumes: LDLib2 `ModularUIScreen(ModularUI, Component)`, `UIElement.transform(Consumer<Transform2D>)`, `UIElement.getWorldToLocalPose()`, `UI.of(UIElement, List<Stylesheet>, DynamicSizeProvider)`, `com.lowdragmc.lowdraglib2.math.Size.of(int, int)`.
- Produces: `DuelScreen` with `DESIGN_WIDTH = 960`, `DESIGN_HEIGHT = 540`, constructor `DuelScreen(ModularUI, UIElement canvas, Component)`; `LDLibDuelScreen.create(DuelStartPayload)` returning `DuelScreen`; `ClickDispatcher(UI, ClientDuelState, FieldRenderer, PromptController, UIElement canvas, Callbacks)`.

No JUnit gate: this task is verified in `runClient`. The harness automates the same check in Task 7.

- [ ] **Step 1: Make the XML root a fixed canvas with a scalable wrapper**

In `duel_screen.xml`, replace the root style block:

```
        /* ── Root ── */
        #duel-root {
        flex-direction: column;
        flex: 1;
        width: 100%;
        height: 100%;
        }
```

with:

```
        /* ── Root: fixed design canvas. DuelScreen scales #duel-canvas to the window. ── */
        #duel-root {
        width: 960;
        height: 540;
        }

        #duel-canvas {
        flex-direction: column;
        width: 100%;
        height: 100%;
        }
```

In the tree, insert one opening tag directly after `<root id="duel-root">`:

```xml
        <element id="duel-canvas">
```

and one closing tag directly before `</root>`:

```xml
        </element>
```

Every existing child of the root (HUD bar, field area, status label, card banner, zone inspector, prompt overlay, context menu) is now inside the wrapper. Do not re-indent the moved block.

- [ ] **Step 2: Create `DuelScreen`**

Create `src/main/java/com/haxerus/duelcraft/client/DuelScreen.java`:

```java
package com.haxerus.duelcraft.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.network.chat.Component;

/**
 * Hosts the duel UI on a fixed 960x540 design canvas and scales it uniformly to the window.
 * LDLib2 centers the root itself, and Minecraft calls {@link #init()} on open and on every
 * resize, so the scale factor tracks the window with no extra hooks.
 */
public final class DuelScreen extends ModularUIScreen {

    public static final int DESIGN_WIDTH = 960;
    public static final int DESIGN_HEIGHT = 540;
    private static final float MIN_SCALE = 0.25f;

    private final UIElement canvas;

    public DuelScreen(ModularUI modularUI, UIElement canvas, Component title) {
        super(modularUI, title);
        this.canvas = canvas;
    }

    @Override
    public void init() {
        super.init();
        if (width == 0 || height == 0) return; // minimized window
        float k = Math.max(MIN_SCALE,
                Math.min(width / (float) DESIGN_WIDTH, height / (float) DESIGN_HEIGHT));
        // Transform2D.scale assigns, and the default pivot is the center, which sits on the
        // screen center because LDLib2 centered the root.
        canvas.transform(t -> t.scale(k));
    }
}
```

- [ ] **Step 3: Build the screen through `create` with a constant size provider**

In `LDLibDuelScreen.java`:

Add `import com.lowdragmc.lowdraglib2.math.Size;` and remove the now-unused `import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;`.

Replace `open`:

```java
    /**
     * Load the XML UI and open the duel screen.
     */
    public static void open(DuelStartPayload startInfo) {
        Minecraft.getInstance().setScreen(create(startInfo));
        LOGGER.info("Duel screen opened (XML-based) vs. {}", startInfo.opponentName());
    }

    /** Builds the screen without showing it. The UI test harness opens the result itself. */
    public static DuelScreen create(DuelStartPayload startInfo) {
        activeState = new ClientDuelState(startInfo);
        activeUI = loadFromXml();
        refresher = new UIRefresher(activeUI, activeState);
        UIElement canvas = activeUI.ui.selectId("duel-canvas").findFirst()
                .orElseThrow(() -> new IllegalStateException(DUEL_UI + " has no #duel-canvas"));
        return new DuelScreen(activeUI, canvas, Component.literal("Duel vs. " + startInfo.opponentName()));
    }
```

In `loadFromXml`, replace the `UI.of(...)` line and its comment:

```java
        // Fixed design size: LDLib2 centers the root, DuelScreen scales #duel-canvas to fit.
        var ui = UI.of(parsed.rootElement, parsed.stylesheets,
                screenSize -> Size.of(DuelScreen.DESIGN_WIDTH, DuelScreen.DESIGN_HEIGHT));
```

In the `UIRefresher` constructor, replace the `clicks = new ClickDispatcher(...)` statement:

```java
            UIElement canvas = byId("duel-canvas");
            clicks = new ClickDispatcher(ui, state, field, prompt, canvas,
                    response -> LDLibDuelScreen.sendResponse(state, response));
```

- [ ] **Step 4: Convert the context menu position into canvas space**

In `ClickDispatcher.java` add `import org.joml.Vector3f;`, add the field and constructor parameter:

```java
    private final UIElement canvas;

    public ClickDispatcher(UI ui, ClientDuelState state, FieldRenderer field,
                           PromptController prompt, UIElement canvas, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.field = field;
        this.prompt = prompt;
        this.canvas = canvas;
        this.callbacks = callbacks;
        this.contextMenu = byId("context-menu");
    }
```

In `onCardClicked`, replace `showContextMenu(actions, event.x, event.y);` with:

```java
            // UIEvent carries screen coordinates; the menu is positioned inside the scaled canvas.
            var local = canvas.getWorldToLocalPose().transformPosition(new Vector3f(event.x, event.y, 0f));
            showContextMenu(actions, local.x, local.y);
```

In `showContextMenu`, replace the three lines that read the root size:

```java
        // Flip/nudge positioning so the menu never runs off the canvas
        float rootW = canvas.getSizeWidth();
        float rootH = canvas.getSizeHeight();
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Verify in the client at GUI scale 3**

Run: `./gradlew runClient`. Set Options → Video Settings → GUI Scale to 3. Open a single-player world. Type `/duel test alpha 42`.

Expected: the whole field is visible, including the player's hand and both pile columns, centered with empty margins only if the window is not 16:9. Click a monster in your hand: the context menu opens under the cursor. Resize the window: the field rescales without leaving the viewport. Set GUI Scale to 1: the field fills the window at the same proportions. `/duel forfeit` to end.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/assets/duelcraft/ui/duel_screen.xml src/main/java/com/haxerus/duelcraft/client/DuelScreen.java src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java src/main/java/com/haxerus/duelcraft/client/ClickDispatcher.java
git commit -m "Duel screen: fixed 960x540 design canvas scaled to the viewport"
```

---

### Task 6: Superset field XML and `FieldRenderer` on `FieldLayout`

**Files:**
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml` (style block end, `#opp-st-row`, `#plr-st-row`)
- Rewrite: `src/main/java/com/haxerus/duelcraft/client/FieldRenderer.java`
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` (`UIRefresher` constructor, `field = new FieldRenderer(...)`)

**Interfaces:**
- Consumes: `FieldLayout` (Task 4), `ClientDuelState.duelFlags` (Task 2).
- Produces: `FieldRenderer(UI, ClientDuelState, FieldLayout, Callbacks)`. Public methods keep their names and signatures: `wireFieldClicks()`, `refreshMonsterZones(int)`, `refreshSpellZones(int)`, `refreshFieldStats()`, `refreshPiles()`, `highlightValidPlaces(int)`, `refreshTargetHighlights()`, `findSlotForLocation(CardLocation)`, `getFieldBit(int, int, int)`, constant `CARD_BACK_SPRITE`. The eight public slot arrays are removed; nothing outside the class reads them.

- [ ] **Step 1: Add the visibility classes to the stylesheet**

In `duel_screen.xml`, directly before `    </style>` add:

```
        /* ── Rule-driven visibility (FieldRenderer assigns these classes) ── */
        .rule-hidden {
        display: none;
        }

        .pendulum-marker {
        display: none;
        }

        .pendulum > .pendulum-marker {
        display: flex;
        }

        .zone-icon.pendulum-marker.hidden {
        display: none;
        }
```

If LDLib2 rejects `display: flex`, look up the accepted values in `../LDLib2/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/style/LayoutProperties.java` (`DISPLAY`) and use the value that means "laid out as a flex item".

- [ ] **Step 2: Add the separate pendulum slots and markers to both S/T rows**

Replace the whole `<element id="opp-st-row" class="zone-row">` element, from its opening tag to its closing `</element>`, with:

```xml
                    <element id="opp-st-row" class="zone-row">
                        <element id="opp-pz-left" class="square-slot zone-slot">
                            <element class="zone-icon"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="opp-st-0" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="opp-st-1" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="opp-st-2" class="square-slot zone-slot" />
                        <element id="opp-st-3" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                        <element id="opp-st-4" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                        <element id="opp-pz-right" class="square-slot zone-slot">
                            <element class="zone-icon"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                    </element>
```

Replace the whole `<element id="plr-st-row" class="zone-row">` element, from its opening tag to its closing `</element>`, with:

```xml
                    <element id="plr-st-row" class="zone-row">
                        <element id="plr-pz-left" class="square-slot zone-slot">
                            <element class="zone-icon"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="plr-st-0" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="plr-st-1" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/lapis_lazuli.png)" />
                        </element>
                        <element id="plr-st-2" class="square-slot zone-slot" />
                        <element id="plr-st-3" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                        <element id="plr-st-4" class="square-slot zone-slot">
                            <element class="zone-icon pendulum-marker"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                        <element id="plr-pz-right" class="square-slot zone-slot">
                            <element class="zone-icon"
                                     style="background: sprite(minecraft:textures/item/redstone.png)" />
                        </element>
                    </element>
```

The opponent row already has `flex-direction: row_reverse`, so `opp-pz-left` renders on the viewer's right, matching the owner-frame convention.

- [ ] **Step 3: Rewrite `FieldRenderer`**

Replace the whole file. The bodies of `refreshZoneSlot`, `updateMonsterStats`, `setPileBackground`, `setPileTopCard`, `refreshTargetHighlights`, and `getFieldBit` are unchanged from the current file; `refreshExtraDeckPile` takes the slot element instead of looking it up.

```java
package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.FieldLayout.Side;
import com.haxerus.duelcraft.client.FieldLayout.Zone;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.duel.message.QueriedCard;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Owns the field UI: monster zones, spell/trap zones, EMZ, pendulum zones, field spell,
 * piles, and the stat overlays drawn on monster cards. Reacts to dirty flags from
 * ClientDuelState via explicit refresh methods called from the tick loop.
 *
 * Every zone lookup goes through {@link FieldLayout}, which knows the rule set's geometry
 * and the slot each engine zone lives in. This class is the only place that converts
 * between absolute player indices (state, engine) and viewer-relative {@link Side}s (layout, XML).
 *
 * Decoupled from click routing via the {@link Callbacks} interface: slot click handlers are
 * wired here but forward the click decision back to the host, which also owns card image
 * async retry and the card info hover banner.
 */
public class FieldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hooks back into host UI for things not owned by FieldRenderer. */
    public interface Callbacks {
        void setCardImageBackground(UIElement elem, int code);
        void onCardClicked(int player, int location, int sequence, UIEvent event);
        void showCardInfo(int code);
        void hideCardInfo();
        /** Clears async-retry tracking for a slot that's no longer displaying a card. */
        void clearPendingImage(UIElement elem);
    }

    public static final String CARD_BACK_SPRITE = "sprite(duelcraft:textures/card_back.png)";

    private final UI ui;
    private final ClientDuelState state;
    private final FieldLayout layout;
    private final Callbacks callbacks;
    /** Superset slot id → element. Ids the XML lacks are absent. */
    private final Map<String, UIElement> slots = new HashMap<>();

    public FieldRenderer(UI ui, ClientDuelState state, FieldLayout layout, Callbacks callbacks) {
        this.ui = ui;
        this.state = state;
        this.layout = layout;
        this.callbacks = callbacks;
        bindSlots();
    }

    private void bindSlots() {
        for (String id : FieldLayout.allSlotIds()) {
            UIElement el = ui.selectId(id).findFirst().orElse(null);
            if (el == null) {
                LOGGER.warn("duel_screen.xml has no slot #{}; treating it as hidden", id);
                continue;
            }
            slots.put(id, el);
        }
        for (String id : layout.hiddenSlotIds()) {
            UIElement el = slots.get(id);
            if (el != null) el.addClass("rule-hidden");
        }
        if (layout.pendulum() == PendulumMode.SHARED) {
            for (int seq : layout.pendulumSequences()) {
                for (Side side : Side.values()) {
                    slot(new Zone(side, LOCATION_SZONE, seq)).ifPresent(el -> el.addClass("pendulum"));
                }
            }
        }
    }

    // ── Player ↔ side conversion (the only place it happens) ────────────────

    private Side side(int player) {
        return player == state.localPlayer ? Side.PLR : Side.OPP;
    }

    private int player(Side side) {
        return side == Side.PLR ? state.localPlayer : state.opponent();
    }

    private Optional<UIElement> slot(Zone zone) {
        return layout.slotId(zone).map(slots::get);
    }

    private boolean occupied(Zone z) {
        int p = player(z.side());
        if (z.location() == LOCATION_MZONE) {
            return state.mzone[p][z.sequence()] != 0 || state.mzonePos[p][z.sequence()] != 0;
        }
        return state.szone[p][z.sequence()] != 0 || state.szonePos[p][z.sequence()] != 0;
    }

    /** The zone a slot shows: the occupied candidate, else the viewer's own zone (for placement). */
    private Zone shownZone(List<Zone> candidates) {
        return candidates.stream().filter(this::occupied).findFirst().orElse(candidates.get(0));
    }

    // ── Click wiring ────────────────────────────────────────────────────────

    /** Bind click handlers on every visible monster and spell slot. Call once after construction. */
    public void wireFieldClicks() {
        for (String id : FieldLayout.allSlotIds()) {
            UIElement el = slots.get(id);
            List<Zone> zones = layout.zonesOf(id);
            if (el == null || zones.isEmpty()) continue;
            int location = zones.get(0).location();
            if (location != LOCATION_MZONE && location != LOCATION_SZONE) continue; // piles: ZoneInspectorController
            el.addEventListener(UIEvents.CLICK, e -> {
                Zone target = shownZone(zones);
                callbacks.onCardClicked(player(target.side()), target.location(), target.sequence(), e);
            });
        }
    }

    // ── Zone refresh ────────────────────────────────────────────────────────

    public void refreshMonsterZones(int player) {
        Side side = side(player);
        for (int seq = 0; seq <= 6; seq++) {
            refreshZone(new Zone(side, LOCATION_MZONE, seq));
        }
    }

    public void refreshSpellZones(int player) {
        Side side = side(player);
        for (int seq = 0; seq <= 7; seq++) {
            refreshZone(new Zone(side, LOCATION_SZONE, seq));
        }
    }

    /** Re-render the slot holding {@code zone}. Shared EMZ slots re-evaluate both owners. */
    private void refreshZone(Zone zone) {
        layout.slotId(zone).ifPresent(id -> {
            UIElement el = slots.get(id);
            if (el == null) return;
            Zone shown = shownZone(layout.zonesOf(id));
            int p = player(shown.side());
            boolean monster = shown.location() == LOCATION_MZONE;
            int code = monster ? state.mzone[p][shown.sequence()] : state.szone[p][shown.sequence()];
            int pos = monster ? state.mzonePos[p][shown.sequence()] : state.szonePos[p][shown.sequence()];
            refreshZoneSlot(el, code, pos, p, shown.location(), shown.sequence());
        });
    }

    private void refreshZoneSlot(UIElement slot, int code, int position, int player, int locationType, int sequence) {
        if (slot == null) return;
        slot.getChildren().stream()
                .filter(c -> c.hasClass("card") || c.hasClass("card-back")
                        || c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
                .toList()
                .forEach(slot::removeChild);

        // A card is present if we know the code OR if a non-zero position is set
        // (opponent's face-down cards have code=0 but position is still set)
        if (code != 0 || position != 0) {
            boolean faceDown = code == 0
                    || (position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
            boolean defense = (position & (POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE)) != 0;

            var cardVisual = new UIElement();
            if (faceDown) {
                cardVisual.addClass("card-back");

                if (player == state.localPlayer && code != 0) {
                    int hoverCode = code;
                    cardVisual.addEventListener(UIEvents.MOUSE_ENTER, e -> callbacks.showCardInfo(hoverCode));
                    cardVisual.addEventListener(UIEvents.MOUSE_LEAVE, e -> callbacks.hideCardInfo());
                }
            } else {
                cardVisual.addClass("card");
                cardVisual.lss("height", "100%");
                callbacks.setCardImageBackground(cardVisual, code);

                int hoverCode = code;
                cardVisual.addEventListener(UIEvents.MOUSE_ENTER, e -> callbacks.showCardInfo(hoverCode));
                cardVisual.addEventListener(UIEvents.MOUSE_LEAVE, e -> callbacks.hideCardInfo());
            }

            if (defense && locationType == LOCATION_MZONE) {
                cardVisual.addClass("defense");
            }

            // EMZ slots live outside #opponent-side, so opponent's cards need manual 180° flip
            if (locationType == LOCATION_MZONE && (sequence == 5 || sequence == 6)
                    && player != state.localPlayer) {
                cardVisual.addClass("emz-opp");
            }

            slot.addChild(cardVisual);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
        } else {
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
        }
    }

    // ── Stat overlays ──────────────────────────────────────────────────────

    public void refreshFieldStats() {
        for (Side side : Side.values()) {
            for (int seq = 0; seq <= 6; seq++) {
                layout.slotId(new Zone(side, LOCATION_MZONE, seq)).ifPresent(id -> {
                    UIElement el = slots.get(id);
                    if (el == null) return;
                    Zone shown = shownZone(layout.zonesOf(id));
                    int p = player(shown.side());
                    updateMonsterStats(el, state.mzoneStats[p][shown.sequence()], state.mzone[p][shown.sequence()], p);
                });
            }
        }
    }

    private void updateMonsterStats(UIElement slot, QueriedCard stats, int code, int player) {
        if (slot == null) return;

        // Remove old stat labels
        slot.getChildren().stream()
                .filter(c -> c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
                .toList()
                .forEach(slot::removeChild);

        if (code == 0 || stats == null) return;

        boolean faceDown = (stats.position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
        if (faceDown) return;

        // Only show stats for monsters (check if QUERY_ATTACK was present in the flags)
        if ((stats.flags & QUERY_ATTACK) == 0) return;

        boolean isOpp = player != state.localPlayer;

        CardDatabase db = DuelcraftClient.getCardDatabase();
        CardInfo cardInfo = db != null ? db.getCard(code) : null;
        boolean isLink = cardInfo != null && cardInfo.isLink();

        // ATK/DEF label — bottom for own cards, top for opponent's (card visual is flipped 180°)
        // Link monsters have no DEF, so show ATK only.
        var atkDefLabel = new Label();
        atkDefLabel.addClass("stat-atk-def");
        if (isOpp) atkDefLabel.addClass("opp");
        String atkText = stats.attack == -2 ? "?" : String.valueOf(stats.attack);
        if (isLink) {
            atkDefLabel.setText(Component.literal(atkText));
        } else {
            String defText = stats.defense == -2 ? "?" : String.valueOf(stats.defense);
            atkDefLabel.setText(Component.literal(atkText + "/" + defText));
        }

        // Color based on buff/debuff (ATK takes priority)
        if (stats.baseAttack > 0 && stats.attack != stats.baseAttack) {
            if (stats.attack > stats.baseAttack) atkDefLabel.addClass("stat-buffed");
            else atkDefLabel.addClass("stat-debuffed");
        }
        slot.addChild(atkDefLabel);

        // Level/Rank label — top-right for own cards, bottom-right for opponent's
        if (cardInfo != null && cardInfo.isMonster() && !cardInfo.isLink()) {
            int originalLevel = cardInfo.levelOrRank();
            int currentLevel = stats.rank > 0 ? stats.rank : stats.level;
            if (currentLevel != originalLevel && currentLevel > 0) {
                var levelLabel = new Label();
                levelLabel.addClass("stat-level");
                if (isOpp) levelLabel.addClass("opp");
                boolean isXyz = cardInfo.isXyz();
                levelLabel.setText(Component.literal((isXyz ? "R" : "★") + currentLevel));
                if (currentLevel > originalLevel) levelLabel.addClass("stat-buffed");
                else if (currentLevel < originalLevel) levelLabel.addClass("stat-debuffed");
                slot.addChild(levelLabel);
            }
        }
    }

    // ── Piles ───────────────────────────────────────────────────────────────

    public void refreshPiles() {
        for (int p = 0; p < 2; p++) {
            final int player = p;
            Side side = side(player);
            // Deck: always face-down card back when non-empty
            slot(new Zone(side, LOCATION_DECK, 0)).ifPresent(el ->
                    setPileBackground(el, state.deckCount[player] > 0 ? CARD_BACK_SPRITE : null));
            // Extra deck: top face-up card if any, otherwise card back when non-empty
            slot(new Zone(side, LOCATION_EXTRA, 0)).ifPresent(el -> refreshExtraDeckPile(el, player));
            // Graveyard & Banished: top card image when non-empty
            slot(new Zone(side, LOCATION_GRAVE, 0)).ifPresent(el -> setPileTopCard(el, state.grave[player]));
            slot(new Zone(side, LOCATION_REMOVED, 0)).ifPresent(el -> setPileTopCard(el, state.banished[player]));
        }
    }

    private void refreshExtraDeckPile(UIElement slot, int player) {
        if (state.extra[player].isEmpty()) {
            setPileBackground(slot, null);
            return;
        }

        // Find the top-most face-up card (iterate from end)
        int topFaceUpCode = 0;
        var codes = state.extra[player];
        var positions = state.extraPos[player];
        for (int i = codes.size() - 1; i >= 0; i--) {
            int pos = i < positions.size() ? positions.get(i) : 0;
            if ((pos & (POS_FACEUP_ATTACK | POS_FACEUP_DEFENSE)) != 0) {
                topFaceUpCode = codes.get(i);
                break;
            }
        }

        if (topFaceUpCode != 0) {
            // Face-up card — use setCardImageBackground for async retry support
            callbacks.setCardImageBackground(slot, topFaceUpCode);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            // All face-down: static card back, clear any pending image retry
            slot.lss("background", CARD_BACK_SPRITE);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
            callbacks.clearPendingImage(slot);
        }
    }

    private void setPileBackground(UIElement slot, String background) {
        if (slot == null) return;
        if (background != null) {
            slot.lss("background", background);
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            slot.removeClass("has-card");
        }
    }

    private void setPileTopCard(UIElement slot, List<Integer> cards) {
        if (slot == null) return;
        if (!cards.isEmpty()) {
            // Use setCardImageBackground so the pile is registered for async image retry
            callbacks.setCardImageBackground(slot, cards.getLast());
            slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
            slot.addClass("has-card");
        } else {
            slot.lss("background", "built-in(ui-gdp:RECT_RD_DARK)");
            slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
            slot.removeClass("has-card");
            callbacks.clearPendingImage(slot);
        }
    }

    // ── Highlighting ───────────────────────────────────────────────────────

    /**
     * Highlight valid placement zones for a SelectPlace prompt.
     * Bitmask is relative to the asking player (set bit = blocked zone); the asking player is the viewer.
     * Monster bits 0-6 and spell bits 8-15 of the viewer's block; the opponent's block starts at bit 16.
     * EMZ bits (5, 6) are read from the viewer's block only, because the two shared slots are covered there.
     */
    public void highlightValidPlaces(int field) {
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
        for (Side side : Side.values()) {
            int base = side == Side.PLR ? 0 : 16;
            int lastMonster = side == Side.PLR ? 6 : 4;
            for (int seq = 0; seq <= lastMonster; seq++) {
                if ((field & (1 << (base + seq))) == 0) {
                    slot(new Zone(side, LOCATION_MZONE, seq)).ifPresent(el -> el.addClass("target"));
                }
            }
            for (int seq = 0; seq <= 7; seq++) {
                if ((field & (1 << (base + 8 + seq))) == 0) {
                    slot(new Zone(side, LOCATION_SZONE, seq)).ifPresent(el -> el.addClass("target"));
                }
            }
        }
    }

    /** Refresh the .target highlight on field slots based on state.cardActions. */
    public void refreshTargetHighlights() {
        ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));

        for (var entry : state.cardActions.entrySet()) {
            var loc = entry.getKey();
            UIElement slot = findSlotForLocation(loc);
            if (slot != null) slot.addClass("target");
        }
    }

    /** Map an absolute (player, location, sequence) to the corresponding UI slot, or null. */
    public UIElement findSlotForLocation(ClientDuelState.CardLocation loc) {
        return slot(new Zone(side(loc.controller()), loc.location(), loc.sequence())).orElse(null);
    }

    /** Compute the bit position in the SelectPlace bitmask for a given zone. */
    public int getFieldBit(int player, int location, int sequence) {
        // Bitmask is relative: self=0, opponent=1. Map absolute player to bitmask position.
        int bitmaskPlayer = (player == state.localPlayer) ? 0 : 1;
        int offset = bitmaskPlayer * 16;
        if (location == LOCATION_SZONE) offset += 8;
        return 1 << (offset + sequence);
    }
}
```

- [ ] **Step 4: Construct the renderer with the layout**

In `LDLibDuelScreen.UIRefresher`'s constructor, change the first line of the field construction:

```java
            field = new FieldRenderer(ui, state, FieldLayout.fromFlags(state.duelFlags), new FieldRenderer.Callbacks() {
```

The anonymous `Callbacks` body is unchanged.

- [ ] **Step 5: Compile and run the suite**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 6: Verify the three geometries in the client**

Run: `./gradlew runClient`, GUI scale 3, single-player world.

- `/duel test alpha 42`: the field looks as before, with the lapis and redstone markers on S/T 0 and 4 and both EMZ slots present. Summon a monster: the Extra Monster Zone highlight and the context menu still work. `/duel forfeit`.
- `/duel test alpha 42 mr3`: one extra square slot flanks each end of both S/T rows, the two EMZ slots between the phase buttons are gone, and S/T 0 and 4 show no markers. `/duel forfeit`.
- `/duel test alpha 42 speed`: three monster and three spell zones per side, no EMZ, no markers. `/duel forfeit`.
- `/duel test alpha 42 bogus`: chat shows `Unknown rule 'bogus'. Valid rules: mr1, goat, mr2, mr3, mr4, mr5, speed, rush`.

The server log carries `rule=mr3` and `rule=speed` on the duel start lines.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/assets/duelcraft/ui/duel_screen.xml src/main/java/com/haxerus/duelcraft/client/FieldRenderer.java src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "FieldRenderer: resolve zones through FieldLayout; superset field XML"
```

---

### Task 7: Harness wiring, fixture, and the first scenario

**Files:**
- Create: `gradle/ldlib2-uitest.gradle`
- Modify: `build.gradle` (after the closing brace of the `neoForge { ... }` block, line 249)
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenScenario.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale2Scenario.java`

**Interfaces:**
- Consumes: `LDLibDuelScreen.create(DuelStartPayload)` and `LDLibDuelScreen.applyMessage`, `LDLibDuelScreen.close()`; `DuelRule`; `FieldLayout`; LDLib2 `UIScenario`, `ScenarioBuilder`, `ScenarioOptions`, `ElementBounds`, `@LDLRegisterClient`, `RegistrationEnvironment.DEV_ONLY`.
- Produces: `DuelScreenFixture.startPayload(DuelRule)`, `DuelScreenFixture.populate(DuelRule)`; `abstract class DuelScreenScenario implements UIScenario` with constructor `(DuelRule rule, int guiScale)` and `protected abstract void ruleChecks(ScenarioBuilder s)`; scenario `duel_mr5_scale2` in group `duelcraft`.

- [ ] **Step 1: Port the Gradle wiring**

Create `gradle/ldlib2-uitest.gradle`:

```groovy
// LDLib2 in-client UI test harness: Gradle wiring for ModDevGradle.
//
// Ported from LDLib2's gradle/ldlib2-uitest.gradle (LGPL-3.0, published for copying), which
// targets NeoGradle's top-level `runs`; ModDevGradle keeps its runs under `neoForge.runs`.
// The headless block is not ported. Inert unless -PldTest is given.
//
//   gradlew runClient -PldTest=group:duelcraft          every duelcraft scenario
//   gradlew runClient -PldTest=duel_mr5_scale3           one by name
//   gradlew runClient -PldTest=duel_mr5_scale3 -PldTestKeepOpen
//   -> build/ldlib2-uitest/report.json, report.txt, screenshots/
//
// Docs: ../LDLib2/uitest.md

def ldTestOutDir = layout.buildDirectory.dir('ldlib2-uitest').get().asFile.absolutePath
def ldTestOutDirFile = file(ldTestOutDir)

if (project.hasProperty('ldTest')) {
    // System properties rather than program arguments: Minecraft's argument parser owns the
    // command line and rejects what it does not recognise.
    neoForge.runs.matching { it.name == 'client' }.configureEach { run ->
        run.systemProperty 'ldlib2.uitest.run', project.property('ldTest')
        run.systemProperty 'ldlib2.uitest.out', ldTestOutDir
        run.systemProperty 'ldlib2.uitest.exclude', project.findProperty('ldTestExclude') ?: ''
        // Run-wide default; scenarios override it via ScenarioOptions#guiScale.
        run.systemProperty 'ldlib2.uitest.guiScale', project.findProperty('ldTestGuiScale') ?: '2'
        // Empty = maximise. Pass e.g. -PldTestWindow=1280x720 to pin a size for comparable captures.
        run.systemProperty 'ldlib2.uitest.window', project.findProperty('ldTestWindow') ?: ''
        run.systemProperty 'ldlib2.uitest.inputMode', project.findProperty('ldTestInputMode') ?: 'SYNTHETIC'
        run.systemProperty 'ldlib2.uitest.watchdogSec', project.findProperty('ldTestWatchdogSec') ?: '90'
        if (project.hasProperty('ldTestKeepOpen')) {
            run.systemProperty 'ldlib2.uitest.keepOpen', 'true'
        }
    }
}

// Turns report.json into a build failure. Attached with finalizedBy so it runs even when the
// client crashes; a missing report is itself a failure.
tasks.register('verifyUiTest') {
    group = 'verification'
    description = 'Fails the build if the LDLib2 in-client UI test report contains failures.'
    onlyIf { project.hasProperty('ldTest') }
    doLast {
        def reportFile = new File(ldTestOutDirFile, 'report.json')
        if (!reportFile.exists()) {
            throw new GradleException("LDLib2 UI test: no report at ${reportFile}.\n" +
                    "The client crashed, never reached the runner, or -PldTest matched no scenarios.")
        }
        def report = new groovy.json.JsonSlurper().parse(reportFile)
        logger.lifecycle("LDLib2 UI test: ${report.totals.passed}/${report.totals.scenarios} scenarios passed, " +
                "${report.totals.checks - report.totals.checksFailed}/${report.totals.checks} checks passed " +
                "in ${report.durationMs} ms")
        report.scenarios.findAll { it.status != 'PASS' }.each { scenario ->
            logger.error("FAILED scenario '${scenario.name}' (${scenario.className})")
            if (scenario.error) logger.error("  ${scenario.error.type}: ${scenario.error.message}")
            scenario.steps.findAll { it.status != 'PASS' }.each { step ->
                logger.error("  step ${step.index} [${step.kind}] ${step.name} -> ${step.status}")
                if (step.error) logger.error("    error: ${step.error.message}")
                step.checks.findAll { !it.passed }.each { check ->
                    logger.error("    check '${check.desc}'" +
                            (check.expected != null ? " expected=${check.expected} actual=${check.actual}" : ""))
                }
                step.captures.each { logger.error("    shot: ${new File(ldTestOutDirFile, it.path)}") }
            }
        }
        if (report.status != 'PASS') {
            throw new GradleException("LDLib2 UI test ${report.status} - full report: ${reportFile}")
        }
    }
}

// Clears the previous run's output so a client that dies before the runner starts cannot be
// reported with the previous run's verdict.
tasks.register('ldlib2CleanUiTestReport') {
    onlyIf { project.hasProperty('ldTest') }
    doLast { ldTestOutDirFile.deleteDir() }
}

// ModDevGradle creates the run tasks after this script is evaluated, so match runClient lazily.
tasks.configureEach { task ->
    if (task.name == 'runClient') {
        task.finalizedBy 'verifyUiTest'
        task.dependsOn 'ldlib2CleanUiTestReport'
    }
}
```

In `build.gradle`, directly after the closing brace of the `neoForge { ... }` block (the block that begins `neoForge {` on line 172 and ends on line 249), add:

```groovy
// LDLib2 in-client UI test harness. Inert unless -PldTest is given.
apply from: rootProject.file('gradle/ldlib2-uitest.gradle')
```

Verify the wiring is inert: `./gradlew compileJava` must still pass with no new tasks executed.

- [ ] **Step 2: Create the fixture**

Create `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.FieldLayout;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.LocInfo;
import com.haxerus.duelcraft.server.DuelStartPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Synthetic duel state for UI scenarios: no server and no engine, only the messages a client
 * would receive. Card codes come from the standard deck; nothing here depends on the card
 * database or images being present.
 */
@OnlyIn(Dist.CLIENT)
public final class DuelScreenFixture {

    private DuelScreenFixture() {}

    /** Player 0 versus "Fixture", 8000 LP each, 40-card decks, 15-card extra decks, the rule's flags. */
    public static DuelStartPayload startPayload(DuelRule rule) {
        return new DuelStartPayload(0, "Fixture", 8000, 8000, 40, 15, rule.flags());
    }

    /**
     * Fills every zone type the rule has: a face-up monster in each main monster zone on both
     * sides, one in the viewer's first EMZ, a spell in S/T 1, a card in the left separate
     * pendulum zone, and one card in the graveyard. Ten cards are drawn per player first so the
     * hand never runs dry. Call after {@link LDLibDuelScreen#create} has run.
     */
    public static void populate(DuelRule rule) {
        FieldLayout layout = FieldLayout.fromFlags(rule.flags());
        List<Integer> codes = Deck.standard().main();

        for (int p = 0; p < 2; p++) {
            LDLibDuelScreen.applyMessage(new DuelMessage.Draw(p, codes.subList(0, 10)));
        }

        int first = layout.columns() == 3 ? 1 : 0;
        int last = layout.columns() == 3 ? 3 : 4;
        for (int p = 0; p < 2; p++) {
            for (int seq = first; seq <= last; seq++) {
                moveFromHand(p, codes.get(seq), LOCATION_MZONE, seq, POS_FACEUP_ATTACK);
            }
        }
        if (layout.emz()) {
            moveFromHand(0, codes.get(5), LOCATION_MZONE, 5, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, codes.get(30), LOCATION_SZONE, 1, POS_FACEUP_ATTACK);
        if (layout.pendulum() == PendulumMode.SEPARATE) {
            moveFromHand(0, codes.get(31), LOCATION_SZONE, 6, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, codes.get(44), LOCATION_GRAVE, 0, POS_FACEUP_ATTACK);
    }

    /** Moves the first card of the player's hand to the given zone. */
    private static void moveFromHand(int player, int code, int location, int sequence, int position) {
        LDLibDuelScreen.applyMessage(new DuelMessage.Move(code,
                new LocInfo(player, LOCATION_HAND, 0, 0),
                new LocInfo(player, location, sequence, position),
                0));
    }
}
```

- [ ] **Step 3: Create the abstract scenario**

Create `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenScenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.uitest.ElementBounds;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Opens the real duel screen with a synthetic start payload, populates it, and checks that the
 * canvas and both hands fit the viewport. Subclasses pick the rule and GUI scale and add
 * rule-specific visibility checks in {@link #ruleChecks}.
 *
 * Run one with {@code gradlew runClient -PldTest=<name>} or all with {@code -PldTest=group:duelcraft}.
 */
@OnlyIn(Dist.CLIENT)
public abstract class DuelScreenScenario implements UIScenario {

    protected final DuelRule rule;
    protected final int guiScale;

    protected DuelScreenScenario(DuelRule rule, int guiScale) {
        this.rule = rule;
        this.guiScale = guiScale;
    }

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("duel").guiScale(guiScale);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.openScreen("duel " + rule.id(), ctx -> LDLibDuelScreen.create(DuelScreenFixture.startPayload(rule)))
         .awaitModularUI()
         .step("populate " + rule.id(), ctx -> DuelScreenFixture.populate(rule))
         .ticks(2)
         .checkBounds("#duel-canvas", DuelScreenScenario::insideViewport)
         .checkBounds("#opponent-hand", DuelScreenScenario::insideViewport)
         .checkBounds("#player-hand", DuelScreenScenario::insideViewport);
        ruleChecks(s);
        s.screenshot(rule.id() + "-scale" + guiScale)
         .teardown("close", ctx -> {
             LDLibDuelScreen.close();
             ctx.mc().setScreen(null);
         });
    }

    /** Rule-specific checks, appended after the shared bounds checks and before the screenshot. */
    protected abstract void ruleChecks(ScenarioBuilder s);

    /** Bounds are in GUI space, the same space as the window's scaled size. One pixel of slack for rounding. */
    protected static boolean insideViewport(ElementBounds b) {
        var window = Minecraft.getInstance().getWindow();
        float slack = 1f;
        return b.x() >= -slack
                && b.y() >= -slack
                && b.x() + b.width() <= window.getGuiScaledWidth() + slack
                && b.y() + b.height() <= window.getGuiScaledHeight() + slack;
    }
}
```

- [ ] **Step 4: Create the MR5 scale-2 scenario**

Create `src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale2Scenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale2", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale2Scenario extends DuelScreenScenario {

    public DuelMr5Scale2Scenario() {
        super(DuelRule.MR5, 2);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkVisible("#plr-mon-0")
         .checkVisible("#plr-mon-4")
         .checkVisible("#plr-st-0 .pendulum-marker")
         .checkHidden("#plr-st-1 .pendulum-marker")
         .checkHidden("#plr-pz-left")
         .checkHidden("#opp-pz-right");
    }
}
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the scenario**

Run: `./gradlew runClient -PldTest=duel_mr5_scale2 --console=plain`
Expected: the client launches, creates a world, runs the scenario, and exits. Gradle prints `LDLib2 UI test: 1/1 scenarios passed` and BUILD SUCCESSFUL. `build/ldlib2-uitest/report.txt` exists and `build/ldlib2-uitest/screenshots/` contains a `mr5-scale2` capture showing the populated field.

If Gradle reports a configuration cache problem from the new script, rerun with `--no-configuration-cache`, make the run pass, then fix the script so it passes without the flag before committing (the usual cause is a `project` or `file()` call inside a `doLast`).

If a check fails, read `build/ldlib2-uitest/report.txt` for the failing step, look at its screenshot, and fix the cause in the code under test, not the check.

- [ ] **Step 7: Commit**

```bash
git add gradle/ldlib2-uitest.gradle build.gradle src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenScenario.java src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale2Scenario.java
git commit -m "Add LDLib2 UI test harness wiring and the MR5 duel screen scenario"
```

---

### Task 8: Scenarios for scale 3 and 4, MR3, and Speed

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale3Scenario.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale4Scenario.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr3Scale3Scenario.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelSpeedScale3Scenario.java`

**Interfaces:**
- Consumes: `DuelScreenScenario` from Task 7.
- Produces: scenarios `duel_mr5_scale3`, `duel_mr5_scale4`, `duel_mr3_scale3`, `duel_speed_scale3` in group `duelcraft`.

- [ ] **Step 1: MR5 at scale 3, with the hover check**

Create `DuelMr5Scale3Scenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The case from dev/gui_3.png. Hovering a card also proves hit-testing survives the canvas transform. */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale3Scenario extends DuelScreenScenario {

    public DuelMr5Scale3Scenario() {
        super(DuelRule.MR5, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkHidden("#plr-pz-left")
         .checkHidden("#opp-pz-right")
         .checkHidden("#card-info-banner")
         .hover("#plr-mon-2 .card")
         .frames(2)
         .checkVisible("#card-info-banner");
    }
}
```

- [ ] **Step 2: MR5 at scale 4**

Create `DuelMr5Scale4Scenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr5_scale4", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr5Scale4Scenario extends DuelScreenScenario {

    public DuelMr5Scale4Scenario() {
        super(DuelRule.MR5, 4);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#emz-left")
         .checkVisible("#emz-right")
         .checkVisible("#plr-mon-0")
         .checkVisible("#plr-mon-4")
         .checkHidden("#plr-pz-left")
         .checkHidden("#opp-pz-right");
    }
}
```

- [ ] **Step 3: MR3 at scale 3**

Create `DuelMr3Scale3Scenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_mr3_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelMr3Scale3Scenario extends DuelScreenScenario {

    public DuelMr3Scale3Scenario() {
        super(DuelRule.MR3, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkVisible("#plr-pz-left")
         .checkVisible("#plr-pz-right")
         .checkVisible("#opp-pz-left")
         .checkVisible("#opp-pz-right")
         .checkExists("#plr-pz-left .card")
         .checkHidden("#emz-left")
         .checkHidden("#emz-right")
         .checkHidden("#plr-st-0 .pendulum-marker")
         .checkHidden("#plr-st-4 .pendulum-marker");
    }
}
```

- [ ] **Step 4: Speed at scale 3**

Create `DuelSpeedScale3Scenario.java`:

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.core.DuelRule;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(name = "duel_speed_scale3", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class DuelSpeedScale3Scenario extends DuelScreenScenario {

    public DuelSpeedScale3Scenario() {
        super(DuelRule.SPEED, 3);
    }

    @Override
    protected void ruleChecks(ScenarioBuilder s) {
        s.checkHidden("#plr-mon-0")
         .checkHidden("#plr-mon-4")
         .checkHidden("#opp-st-0")
         .checkHidden("#opp-st-4")
         .checkVisible("#plr-mon-1")
         .checkVisible("#plr-mon-3")
         .checkVisible("#opp-st-2")
         .checkHidden("#emz-left")
         .checkHidden("#emz-right")
         .checkHidden("#plr-pz-left")
         .checkHidden("#plr-st-1 .pendulum-marker")
         .checkHidden("#plr-st-3 .pendulum-marker");
    }
}
```

- [ ] **Step 5: Compile and run every scenario**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runClient -PldTest=group:duelcraft --console=plain`
Expected: `LDLib2 UI test: 5/5 scenarios passed`, BUILD SUCCESSFUL, five screenshots under `build/ldlib2-uitest/screenshots/`. Open `mr5-scale3` and `mr5-scale4`: the entire field, both hands, and both pile columns are inside the frame. Open `mr3-scale3`: an extra slot flanks each S/T row with a card in the player's left one. Open `speed-scale3`: three zones per row.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale3Scenario.java src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr5Scale4Scenario.java src/main/java/com/haxerus/duelcraft/client/uitest/DuelMr3Scale3Scenario.java src/main/java/com/haxerus/duelcraft/client/uitest/DuelSpeedScale3Scenario.java
git commit -m "Add duel screen scenarios for scale 3/4, MR3, and Speed"
```

---

### Task 9: Manual verification and local docs

**Files:**
- Modify: `CLAUDE.md` (gitignored; local only, no commit)

No Java changes. This is the integration pass across a real engine-driven duel, which the fixture-driven scenarios cannot cover.

- [ ] **Step 1: Document the harness in `CLAUDE.md`**

In the `## Build Commands` block, after the `./gradlew runServer` line, add:

```
./gradlew runClient -PldTest=group:duelcraft   # LDLib2 in-client UI scenarios; report in build/ldlib2-uitest/
```

In `## Testing`, replace the sentence `The LDLib2 UI has no automated tests; verify it in runClient.` with:

```
The duel screen has LDLib2 harness scenarios in `client/uitest/` (dev-only): `./gradlew runClient -PldTest=group:duelcraft` opens the real screen per rule set and GUI scale, asserts bounds and zone visibility, and writes screenshots to `build/ldlib2-uitest/`.
```

- [ ] **Step 2: Play the three geometries against the engine at GUI scale 3**

Run `./gradlew runClient`, set GUI scale 3, open a world. Decks `alpha` and `beta` already exist in `run/duelcraft/decks/`.

- `/duel test alpha 42`: nothing is clipped; this is the case from `dev/gui_3.png`. Normal summon a monster and confirm the context menu opens under the cursor and the placement highlight covers the right zones. `/duel forfeit`.
- `/duel test alpha 42 mr3`: separate pendulum zones flank both S/T rows and there are no EMZ slots. If the hand has a pendulum monster, activate it in a pendulum zone and confirm it lands in the flanking slot. `/duel forfeit`.
- `/duel test alpha 42 speed`: three columns per side, no EMZ, no markers. Summon into the middle column. `/duel forfeit`.
- Resize the window while a duel is open: the field rescales and stays centered.
- Set GUI scale 1: the field fills the window at the same proportions.

- [ ] **Step 3: Check the mirrored view with a second client**

Run `./gradlew runClient2` alongside the first client, join the same world, and from the first client run `/duel challenge Player2 7 mr5`, then `/duel accept` on the second. On Player2's screen, the opponent's rows are mirrored, an Extra Monster Zone summon by either player lands in the correct shared slot from both viewpoints, and the pendulum markers sit on the correct S/T slots. Repeat once with `mr3` and confirm the separate pendulum zones mirror correctly.

- [ ] **Step 4: Confirm the log lines**

The run console shows one line per duel containing `seed=`, `rule=`, and the deck names, for example `Solo duel <uuid>: seed=42, rule=mr3, player=alpha, aiDeck=alpha`.

- [ ] **Step 5: Commit nothing**

`CLAUDE.md` is gitignored. If any step fails, return to the task that owns the failing component.

---

## Summary of commits

After the plan, `git log --oneline` on the feature branch shows, newest first:

```
<hash> Add duel screen scenarios for scale 3/4, MR3, and Speed
<hash> Add LDLib2 UI test harness wiring and the MR5 duel screen scenario
<hash> FieldRenderer: resolve zones through FieldLayout; superset field XML
<hash> Duel screen: fixed 960x540 design canvas scaled to the viewport
<hash> Add FieldLayout: rule-driven zone visibility and slot mapping
<hash> DuelCommand: optional rule argument on test and challenge
<hash> DuelStartPayload: carry duel flags; DuelManager: take a DuelRule
<hash> Add DuelRule presets and DuelOptions.of(seed, rule)
```

## Notes for the executor

- **Branch.** The spec expects a fresh feature branch. The uncommitted `build.gradle` LDLib2 bump to 2.2.39.a must be on the branch before Task 5: `DuelScreen` relies on `Transform2D` and `UIElement.transform`, and Task 7 relies on the 2.2.34+ harness.
- **No native (C++) changes and no message-format changes.** `OcgCoreTest` and `MessageParserTest` must pass untouched.
- **Two visibility classes on purpose.** `.hidden` already means "a card covers this icon" (FieldRenderer toggles it on `.zone-icon` children) and "opacity 0" on `.phase-btn`. Rule-level hiding uses `.rule-hidden` so the two never collide.
- **One deviation from the spec, deliberate.** `highlightValidPlaces` reads EMZ bits 5 and 6 from the viewer's block only. The two shared physical slots are fully described there, and reading the opponent's block too would double-highlight them. The spec text says "monster bits 0 to 6" for both sides; the plan's code is the intended behavior.
- **XML edits are surgical.** Do not re-indent the 200 lines moved under `#duel-canvas` in Task 5; only the two wrapper tags change.
- **Sequences a rule lacks are skipped silently, not logged.** The spec asks for a debug log when a message names a zone with no slot. `refreshMonsterZones` and `refreshSpellZones` walk every sequence on every refresh, so a log there would fire on each tick for each hidden zone. The skip is the normal path; the warning at bind time covers the real error case, a missing XML id.
- **LDLib2 API questions.** The source is at `../LDLib2` (branch `1.21`, version 2.2.39.a). `ScenarioBuilder`, `ElementRef`, `ElementBounds`, and `ScenarioOptions` are in `src/main/java/com/lowdragmc/lowdraglib2/uitest/`; `Transform2D` is in `gui/ui/data/`; `ModularUIScreen` is in `gui/holder/`.
- **Harness runs take minutes.** A cold `runClient -PldTest=...` spends most of its time on Gradle, mod loading, and world creation. While iterating on a scenario use `-PldTestKeepOpen` and rerun from inside the game with `/ldlib2_autotest run duel_mr5_scale3`.
- **Card images and the database are optional.** On a fresh checkout the fixture renders card backs and codes; every check in this plan still passes. Do not add checks on card names.

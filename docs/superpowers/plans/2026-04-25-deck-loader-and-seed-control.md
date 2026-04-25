# Deck Loader and Seed Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load EDOPro `.ydk` files at runtime (no relaunch) and make duels reproducible via a single user-input seed that drives both the initial deck shuffle and ygopro-core's in-duel RNG.

**Architecture:** Pure helpers (`DeckLoader`, `SeedExpander`, `Deck.shuffled`) are unit-tested in isolation. A server-side `DeckRegistry` rescans `<gameDir>/duelcraft/decks/*.ydk` on every call (zero caching → hot reload for free). `DuelManager` holds an in-memory `Map<UUID, String>` of each player's current deck and resolves it at duel-start time. Java-side Fisher-Yates with `Random(seed)` shuffles the main deck before `OCG_DuelNewCard`; the same seed is expanded via SplitMix64 into the `long[4]` for `OCG_CreateDuel`.

**Tech Stack:** Java 21, JUnit 5, NeoForge 21.11.42, Brigadier (Minecraft commands), ygopro-core JNI (read-only on this branch — no native changes).

**Spec:** `docs/superpowers/specs/2026-04-25-deck-loader-and-seed-control-design.md`

---

## File Structure

**New files:**

| Path | Responsibility |
| --- | --- |
| `src/main/java/com/haxerus/duelcraft/core/SeedExpander.java` | SplitMix64: `long → long[4]` |
| `src/main/java/com/haxerus/duelcraft/core/DeckLoader.java` | Pure `.ydk` parser + IO wrapper, contains nested `DeckParseException` |
| `src/main/java/com/haxerus/duelcraft/core/DeckRegistry.java` | Filesystem scan + load by name, no caching |
| `src/test/java/com/haxerus/duelcraft/core/SeedExpanderTest.java` | Determinism + non-trivial spread |
| `src/test/java/com/haxerus/duelcraft/core/DeckShuffleTest.java` | Same seed → same order; original unmutated |
| `src/test/java/com/haxerus/duelcraft/core/DeckLoaderTest.java` | Parser cases |
| `src/test/java/com/haxerus/duelcraft/core/DeckRegistryTest.java` | Filesystem scan + hot reload |

**Modified files:**

| Path | Change |
| --- | --- |
| `src/main/java/com/haxerus/duelcraft/core/Deck.java` | Add `shuffled(long seed)` |
| `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java` | Add `standard(long seed)` factory |
| `src/main/java/com/haxerus/duelcraft/server/DuelManager.java` | Add registry, per-player deck map, `resolveDeck`, seed plumbing in `startDuel`/`startSoloDuel` |
| `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java` | New `deck` subcommand group; seed/AI-deck args on `test` and `challenge` |

---

### Task 1: `SeedExpander` (SplitMix64)

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/core/SeedExpander.java`
- Test: `src/test/java/com/haxerus/duelcraft/core/SeedExpanderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedExpanderTest {

    @Test
    void sameInputProducesSameOutput() {
        long[] a = SeedExpander.toFourLongs(42L);
        long[] b = SeedExpander.toFourLongs(42L);
        assertArrayEquals(a, b);
    }

    @Test
    void differentInputsProduceDifferentOutputs() {
        long[] a = SeedExpander.toFourLongs(42L);
        long[] b = SeedExpander.toFourLongs(43L);
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void outputHasFourElements() {
        assertEquals(4, SeedExpander.toFourLongs(0L).length);
    }

    @Test
    void zeroSeedDoesNotProduceAllZeroOutput() {
        long[] out = SeedExpander.toFourLongs(0L);
        boolean anyNonZero = false;
        for (long v : out) if (v != 0L) { anyNonZero = true; break; }
        assertTrue(anyNonZero, "SplitMix64 must scramble even seed=0");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.SeedExpanderTest"`
Expected: compilation error (`SeedExpander` does not exist).

- [ ] **Step 3: Write minimal implementation**

```java
package com.haxerus.duelcraft.core;

/** Expands a single user seed into 4 longs of state for ygopro-core's xoshiro256** RNG. */
public final class SeedExpander {

    private SeedExpander() {}

    public static long[] toFourLongs(long seed) {
        long[] out = new long[4];
        long x = seed;
        for (int i = 0; i < 4; i++) {
            x += 0x9E3779B97F4A7C15L;
            long z = x;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z = z ^ (z >>> 31);
            out[i] = z;
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.SeedExpanderTest"`
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/core/SeedExpander.java \
        src/test/java/com/haxerus/duelcraft/core/SeedExpanderTest.java
git commit -m "Add SeedExpander: SplitMix64 long → long[4]"
```

---

### Task 2: `Deck.shuffled(long seed)`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/core/Deck.java`
- Test: `src/test/java/com/haxerus/duelcraft/core/DeckShuffleTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckShuffleTest {

    private static Deck sampleDeck() {
        return new Deck(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
                        29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40),
                List.of(100, 101, 102, 103, 104));
    }

    @Test
    void sameSeedProducesSameMainOrder() {
        Deck a = sampleDeck().shuffled(42L);
        Deck b = sampleDeck().shuffled(42L);
        assertEquals(a.main(), b.main());
    }

    @Test
    void differentSeedsProduceDifferentMainOrder() {
        Deck a = sampleDeck().shuffled(42L);
        Deck b = sampleDeck().shuffled(43L);
        assertNotEquals(a.main(), b.main());
    }

    @Test
    void shuffledMainContainsSameElements() {
        Deck shuffled = sampleDeck().shuffled(42L);
        assertEquals(sampleDeck().main().size(), shuffled.main().size());
        assertTrue(shuffled.main().containsAll(sampleDeck().main()));
        assertTrue(sampleDeck().main().containsAll(shuffled.main()));
    }

    @Test
    void extraDeckIsNotShuffled() {
        Deck shuffled = sampleDeck().shuffled(42L);
        assertEquals(sampleDeck().extra(), shuffled.extra());
    }

    @Test
    void originalDeckIsNotMutated() {
        Deck original = sampleDeck();
        List<Integer> originalMainBefore = List.copyOf(original.main());
        original.shuffled(42L);
        assertEquals(originalMainBefore, original.main());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckShuffleTest"`
Expected: compilation error (`shuffled` method does not exist on `Deck`).

- [ ] **Step 3: Add `shuffled` to `Deck.java`**

Open `src/main/java/com/haxerus/duelcraft/core/Deck.java`. Replace its current contents with:

```java
package com.haxerus.duelcraft.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public record Deck(
    List<Integer> main,
    List<Integer> extra
) {
    /**
     * Returns a copy of this deck with the main list deterministically shuffled.
     * The extra deck is not shuffled — its order does not affect draws.
     */
    public Deck shuffled(long seed) {
        var shuffledMain = new ArrayList<>(main);
        Collections.shuffle(shuffledMain, new Random(seed));
        return new Deck(List.copyOf(shuffledMain), extra);
    }

    public static Deck standard() {
        Integer[] MAIN = {
            // Monster
            89631139, 33750025, 28406301, 55415564, 49238328, 39153655, 39153655, 70095154, 11747708, 55144522, 24094653,
            55144522, 25259669, 25259669, 13039848,
            55144522, 31786629, 31786629, 43096270,
            11091375, 11091375, 11091375, 11091375,
            69247929, 69247929, 69247929, 69247929, 28406301, 55415564,
            // Spells
            55144522, 55144522, 55144522,
            12580477, 12580477, 12580477,
            66788016, 66788016, 66788016,
            5318639,  5318639,  5318639,
            83764718, 83764718,
            // Traps
            44095762, 44095762, 44095762,
            62279055, 62279055, 8131171,
        };

        Integer[] EXTRA = {
                7391448,
                29981921,
                54752875,
                50321796,
                21044178,
                11398059,
                29301450,
                29301450,
                9024198
        };

        return new Deck(Arrays.asList(MAIN), Arrays.asList(EXTRA));
    }
}
```

(The `standard()` body is identical to current code; we only added the `shuffled` method and the imports for `ArrayList`/`Collections`/`Random`. The commented-out alternative `MAIN` block from the current file is dropped — once the registry exists you'll keep alternates as `.ydk` files instead.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckShuffleTest"`
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/core/Deck.java \
        src/test/java/com/haxerus/duelcraft/core/DeckShuffleTest.java
git commit -m "Add Deck.shuffled(long seed) for deterministic Fisher-Yates"
```

---

### Task 3: `DeckLoader` and `DeckParseException`

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/core/DeckLoader.java`
- Test: `src/test/java/com/haxerus/duelcraft/core/DeckLoaderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckLoaderTest {

    @Test
    void parsesWellFormedYdk() {
        String ydk = """
                #created by ...
                #main
                89631139
                55144522
                #extra
                7391448
                29981921
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(89631139, 55144522), deck.main());
        assertEquals(List.of(7391448, 29981921), deck.extra());
    }

    @Test
    void ignoresSideDeck() {
        String ydk = """
                #main
                111
                #extra
                222
                !side
                999
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
        assertEquals(List.of(222), deck.extra());
    }

    @Test
    void toleratesBlankLinesAndWhitespace() {
        String ydk = """
                #main

                   89631139\s
                \t55144522
                #extra
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(89631139, 55144522), deck.main());
        assertEquals(List.of(), deck.extra());
    }

    @Test
    void allowsEmptyExtraDeck() {
        String ydk = """
                #main
                111
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
        assertEquals(List.of(), deck.extra());
    }

    @Test
    void rejectsFileWithoutMainSection() {
        String ydk = """
                #extra
                111
                """;
        DeckLoader.DeckParseException ex = assertThrows(
                DeckLoader.DeckParseException.class,
                () -> DeckLoader.parseYdk(ydk));
        assertTrue(ex.getMessage().toLowerCase().contains("main"));
    }

    @Test
    void rejectsNonIntegerLineInMain() {
        String ydk = """
                #main
                89631139
                not_a_number
                """;
        DeckLoader.DeckParseException ex = assertThrows(
                DeckLoader.DeckParseException.class,
                () -> DeckLoader.parseYdk(ydk));
        assertEquals(3, ex.getLine());
    }

    @Test
    void treatsUnknownHashLineAsComment() {
        String ydk = """
                #created by Player1
                #note: this is a goat deck
                #main
                111
                """;
        Deck deck = DeckLoader.parseYdk(ydk);
        assertEquals(List.of(111), deck.main());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckLoaderTest"`
Expected: compilation error (`DeckLoader` does not exist).

- [ ] **Step 3: Write `DeckLoader`**

Create `src/main/java/com/haxerus/duelcraft/core/DeckLoader.java`:

```java
package com.haxerus.duelcraft.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser for EDOPro {@code .ydk} files. Pure: no validation against the card database. */
public final class DeckLoader {

    private DeckLoader() {}

    /** Reads the given file as UTF-8 and parses it. */
    public static Deck loadFromFile(Path path) throws IOException, DeckParseException {
        return parseYdk(Files.readString(path));
    }

    /**
     * Parses a {@code .ydk} string. Recognized headers: {@code #main}, {@code #extra}, {@code !side}.
     * Other {@code #}-lines are comments. Integer lines are passcodes for the current section.
     * Whitespace and blank lines are tolerated.
     */
    public static Deck parseYdk(String content) {
        List<Integer> main = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        List<Integer> sideSink = new ArrayList<>();

        Section section = Section.NONE;
        boolean sawMain = false;
        int lineNum = 0;

        for (String raw : content.split("\\R", -1)) {
            lineNum++;
            String line = raw.strip();
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                String header = line.toLowerCase();
                if (header.equals("#main")) { section = Section.MAIN; sawMain = true; }
                else if (header.equals("#extra")) section = Section.EXTRA;
                // any other #-line is a comment
                continue;
            }
            if (line.equals("!side")) {
                section = Section.SIDE;
                continue;
            }

            int code;
            try {
                code = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                throw new DeckParseException("Expected card passcode, got: " + line, lineNum);
            }
            if (code <= 0) {
                throw new DeckParseException("Passcode must be positive, got: " + code, lineNum);
            }
            switch (section) {
                case MAIN -> main.add(code);
                case EXTRA -> extra.add(code);
                case SIDE -> sideSink.add(code);
                case NONE -> throw new DeckParseException(
                        "Card before any section header", lineNum);
            }
        }

        if (!sawMain) {
            throw new DeckParseException("Missing #main section", lineNum);
        }
        return new Deck(List.copyOf(main), List.copyOf(extra));
    }

    private enum Section { NONE, MAIN, EXTRA, SIDE }

    /** Thrown when a {@code .ydk} file cannot be parsed. */
    public static final class DeckParseException extends RuntimeException {
        private final int line;

        public DeckParseException(String message, int line) {
            super(message + " (line " + line + ")");
            this.line = line;
        }

        public int getLine() { return line; }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckLoaderTest"`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/core/DeckLoader.java \
        src/test/java/com/haxerus/duelcraft/core/DeckLoaderTest.java
git commit -m "Add DeckLoader: parse EDOPro .ydk files"
```

---

### Task 4: `DeckRegistry`

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/core/DeckRegistry.java`
- Test: `src/test/java/com/haxerus/duelcraft/core/DeckRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.haxerus.duelcraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckRegistryTest {

    private static final String SIMPLE_YDK = """
            #main
            89631139
            #extra
            7391448
            """;

    @Test
    void listsYdkFilesWithoutExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("alpha.ydk"), SIMPLE_YDK);
        Files.writeString(dir.resolve("beta.ydk"), SIMPLE_YDK);
        Files.writeString(dir.resolve("not_a_deck.txt"), "ignored");

        DeckRegistry registry = new DeckRegistry(dir);
        List<String> names = registry.listDeckNames();
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
        assertFalse(names.contains("not_a_deck"));
        assertEquals(2, names.size());
    }

    @Test
    void loadByNameReturnsParsedDeck(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("test.ydk"), SIMPLE_YDK);
        DeckRegistry registry = new DeckRegistry(dir);

        Deck loaded = registry.load("test");
        assertEquals(List.of(89631139), loaded.main());
        assertEquals(List.of(7391448), loaded.extra());
    }

    @Test
    void loadMissingDeckThrowsIOException(@TempDir Path dir) {
        DeckRegistry registry = new DeckRegistry(dir);
        assertThrows(IOException.class, () -> registry.load("nope"));
    }

    @Test
    void rescansOnEachListCall(@TempDir Path dir) throws IOException {
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(), registry.listDeckNames());

        Files.writeString(dir.resolve("late.ydk"), SIMPLE_YDK);
        assertEquals(List.of("late"), registry.listDeckNames());
    }

    @Test
    void rereadsFileOnEachLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hot.ydk");
        Files.writeString(file, "#main\n111\n");
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(111), registry.load("hot").main());

        Files.writeString(file, "#main\n222\n");
        assertEquals(List.of(222), registry.load("hot").main());
    }

    @Test
    void constructorCreatesDirectoryIfMissing(@TempDir Path parent) {
        Path target = parent.resolve("decks_subdir");
        assertFalse(Files.exists(target));
        new DeckRegistry(target);
        assertTrue(Files.isDirectory(target));
    }

    @Test
    void listOnEmptyDirReturnsEmptyList(@TempDir Path dir) {
        DeckRegistry registry = new DeckRegistry(dir);
        assertEquals(List.of(), registry.listDeckNames());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckRegistryTest"`
Expected: compilation error (`DeckRegistry` does not exist).

- [ ] **Step 3: Write `DeckRegistry`**

Create `src/main/java/com/haxerus/duelcraft/core/DeckRegistry.java`:

```java
package com.haxerus.duelcraft.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Lists and loads {@link Deck}s from a directory of {@code .ydk} files. No caching. */
public final class DeckRegistry {

    private static final String EXT = ".ydk";

    private final Path dir;

    public DeckRegistry(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create decks directory: " + dir, e);
        }
    }

    /** Returns alphabetized names (no extension) of every {@code .ydk} file in the directory. */
    public List<String> listDeckNames() {
        try (Stream<Path> stream = Files.list(dir)) {
            List<String> names = new ArrayList<>();
            stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase().endsWith(EXT))
                .map(n -> n.substring(0, n.length() - EXT.length()))
                .forEach(names::add);
            Collections.sort(names);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list decks in " + dir, e);
        }
    }

    /** Loads {@code <name>.ydk} from the registry directory, re-reading on every call. */
    public Deck load(String name) throws IOException {
        Path file = dir.resolve(name + EXT);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Deck file not found: " + file);
        }
        return DeckLoader.loadFromFile(file);
    }

    public Path getDir() { return dir; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.DeckRegistryTest"`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/core/DeckRegistry.java \
        src/test/java/com/haxerus/duelcraft/core/DeckRegistryTest.java
git commit -m "Add DeckRegistry: list and load .ydk files with hot reload"
```

---

### Task 5: `DuelOptions.standard(long seed)` factory

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java`

No new test file — this is a tiny factory and the seed expansion is already covered by `SeedExpanderTest`.

- [ ] **Step 1: Replace `DuelOptions.java` contents**

Open `src/main/java/com/haxerus/duelcraft/core/DuelOptions.java`. Replace contents with:

```java
package com.haxerus.duelcraft.core;

import java.util.concurrent.ThreadLocalRandom;

public record DuelOptions(
    long[] seed,
    long flags,
    PlayerOptions team1,
    PlayerOptions team2
) {
    /** Builds standard options with a fully expanded seed. */
    public static DuelOptions standard(long seed) {
        return new DuelOptions(
                SeedExpander.toFourLongs(seed),
                OcgConstants.DUEL_MODE_MR5,
                PlayerOptions.standard(),
                PlayerOptions.standard());
    }

    /** Builds standard options with a freshly randomized seed. */
    public static DuelOptions standard() {
        return standard(ThreadLocalRandom.current().nextLong());
    }
}
```

- [ ] **Step 2: Run all unit tests to confirm no regression**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.*"`
Expected: all tests pass (the existing `SeedExpanderTest`, `DeckShuffleTest`, `DeckLoaderTest`, `DeckRegistryTest` continue to pass; no test references `DuelOptions` directly).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/core/DuelOptions.java
git commit -m "Add DuelOptions.standard(long seed) factory"
```

---

### Task 6: `DuelManager` — registry, per-player deck map, `resolveDeck`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelManager.java`

This task adds the new state and helper methods only. The existing `startDuel`/`startSoloDuel` signatures are not changed yet — that comes in Task 7.

- [ ] **Step 1: Add registry, deck map, and helpers to `DuelManager`**

Open `src/main/java/com/haxerus/duelcraft/server/DuelManager.java`.

Add these imports at the top (with the existing imports):

```java
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import net.minecraft.server.MinecraftServer;
```

Replace the field declarations block (currently lines 23–29) with:

```java
    private DuelEngine engine;
    private Map<UUID, DuelSession> activeDuels;
    private Map<UUID, UUID> playerToDuel;
    private Map<UUID, SoloDuelHandler> soloHandlers;
    private DeckRegistry deckRegistry;
    private final Map<UUID, String> playerCurrentDeck = new HashMap<>();

    // FIXME: Temporary for testing
    public Map<UUID, UUID> duelInvites; // target -> challenger
```

Replace the `onServerStarting` method (currently lines 33–36) with:

```java
    public static void onServerStarting(ServerStartingEvent event) {
        instance = new DuelManager();
        instance.init(event.getServer());
    }
```

Replace the `init()` method (currently lines 45–58) with:

```java
    public void init(MinecraftServer server) {
        List<String> dbPaths = new ArrayList<>(Config.CARD_DATABASE_PATHS.get());
        List<String> scriptPaths = new ArrayList<>(Config.SCRIPT_SEARCH_PATHS.get());

        engine = new DuelEngine(dbPaths, scriptPaths);

        activeDuels = new HashMap<>();
        playerToDuel = new HashMap<>();
        soloHandlers = new HashMap<>();
        duelInvites = new HashMap<>();

        java.nio.file.Path decksDir = server.getServerDirectory().resolve("duelcraft").resolve("decks");
        deckRegistry = new DeckRegistry(decksDir);

        int[] version = OcgCore.nGetVersion();
        LOGGER.info("DuelManager initialized — OCG core v{}.{}, decks dir: {}",
                version[0], version[1], decksDir);
    }
```

Append these methods at the end of the class (just before the final closing brace):

```java
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
     * Returns {@link Deck#standard()} when no current deck is set (silent fallback).
     * Throws when a current deck is set but its file is missing or malformed.
     */
    public Deck resolveDeck(ServerPlayer player) throws IOException, DeckLoader.DeckParseException {
        String name = playerCurrentDeck.get(player.getUUID());
        if (name == null) {
            return Deck.standard();
        }
        return deckRegistry.load(name);
    }
```

Add to the imports:

```java
import java.io.IOException;
import java.util.Optional;
```

(`java.util.*` is already imported, but `Optional` and `IOException` need explicit imports if the existing wildcard does not cover them — verify after edit and add if needed.)

- [ ] **Step 2: Build to verify no regressions**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL. (Excluding test for speed — unit tests don't exercise this server-side class.)

If `Path` resolution conflicts with existing `Path` imports, use the fully-qualified `java.nio.file.Path` reference shown above.

- [ ] **Step 3: Run unit tests to confirm no regression**

Run: `./gradlew test --tests "com.haxerus.duelcraft.core.*"`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelManager.java
git commit -m "DuelManager: add deck registry and per-player deck state"
```

---

### Task 7: `DuelManager` — seed plumbing in `startDuel`/`startSoloDuel`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelManager.java`

Now we change the duel-start signatures to take a seed and pre-shuffle the decks. Existing callers (currently `DuelCommand.accept` and `DuelCommand.test`) still compile because we keep no-seed convenience overloads as thin wrappers.

- [ ] **Step 1: Replace `startSoloDuel` with seed-aware version**

Replace the current `startSoloDuel` method (currently lines 80–104) with:

```java
    /** Convenience: random seed. */
    public void startSoloDuel(ServerPlayer player, Deck playerDeck, Deck aiDeck) {
        startSoloDuel(player, java.util.concurrent.ThreadLocalRandom.current().nextLong(),
                playerDeck, aiDeck, null, null);
    }

    /**
     * Start a solo test duel where player 1 is AI-controlled.
     * Both decks are deterministically shuffled with {@code seed} before being handed to the engine.
     * {@code playerDeckName} and {@code aiDeckName} are used only for logging.
     */
    public void startSoloDuel(ServerPlayer player, long seed,
                              Deck playerDeck, Deck aiDeck,
                              String playerDeckName, String aiDeckName) {
        if (playerToDuel.containsKey(player.getUUID())) {
            LOGGER.warn("Cannot start solo duel - player is already in a duel!");
            return;
        }

        DuelOptions options = DuelOptions.standard(seed);
        UUID duelId = UUID.randomUUID();
        var handler = new SoloDuelHandler(player, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(player.getUUID(), duelId);
        soloHandlers.put(duelId, handler);

        LOGGER.info("Solo duel {}: seed={}, player={}, aiDeck={}",
                duelId, seed,
                playerDeckName != null ? playerDeckName : "<standard>",
                aiDeckName != null ? aiDeckName : "<standard>");

        Deck shuffledPlayer = playerDeck.shuffled(seed);
        Deck shuffledAi = aiDeck.shuffled(seed);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        PacketDistributor.sendToPlayer(player, new DuelStartPayload(0, "AI Opponent",
                lp0, lp1, shuffledPlayer.main().size(), shuffledPlayer.extra().size()));

        session.setupDuel(shuffledPlayer, shuffledAi);
        session.process();

        processSoloAutoResponse(duelId, handler);
    }
```

- [ ] **Step 2: Replace `startDuel` (multiplayer) with seed-aware version**

Replace the current `startDuel(ServerPlayer p1, ServerPlayer p2, ...)` method (currently lines 142–168) with:

```java
    /** Convenience: random seed, decks resolved later by caller. */
    public void startDuel(ServerPlayer p1, ServerPlayer p2, Deck team1Deck, Deck team2Deck) {
        startDuel(p1, p2, java.util.concurrent.ThreadLocalRandom.current().nextLong(),
                team1Deck, team2Deck, null, null);
    }

    public void startDuel(ServerPlayer p1, ServerPlayer p2, long seed,
                          Deck team1Deck, Deck team2Deck,
                          String team1Name, String team2Name) {
        if (playerToDuel.containsKey(p1.getUUID()) || playerToDuel.containsKey(p2.getUUID())) {
            LOGGER.warn("Cannot start duel - a player is already in a duel!");
            return;
        }

        DuelOptions options = DuelOptions.standard(seed);
        UUID duelId = UUID.randomUUID();
        var handler = new ServerDuelHandler(p1, p2, duelId);
        var session = new DuelSession(engine, options, handler);

        activeDuels.put(duelId, session);
        playerToDuel.put(p1.getUUID(), duelId);
        playerToDuel.put(p2.getUUID(), duelId);

        LOGGER.info("Duel {}: seed={}, decks=[{}, {}]",
                duelId, seed,
                team1Name != null ? team1Name : "<standard>",
                team2Name != null ? team2Name : "<standard>");

        Deck shuffled1 = team1Deck.shuffled(seed);
        Deck shuffled2 = team2Deck.shuffled(seed);

        int lp0 = options.team1().lp();
        int lp1 = options.team2().lp();
        int deckSize = shuffled1.main().size();
        int extraSize = shuffled1.extra().size();
        PacketDistributor.sendToPlayer(p1, new DuelStartPayload(0, p2.getName().getString(),
                lp0, lp1, deckSize, extraSize));
        PacketDistributor.sendToPlayer(p2, new DuelStartPayload(1, p1.getName().getString(),
                lp0, lp1, deckSize, extraSize));

        session.setupDuel(shuffled1, shuffled2);
        session.process();
    }
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew build -x test`
Expected: BUILD FAILED — `DuelCommand.test` and `DuelCommand.accept` still call the old 4-arg signatures (`startSoloDuel(player, options, deck, deck)` and `startDuel(player, challenger, options, deck, deck)`) which no longer exist.

This is intentional — Task 8 fixes the callers. Continue.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelManager.java
git commit -m "DuelManager: thread seed through duel start, log it, shuffle decks"
```

---

### Task 8: `DuelCommand` — new command tree with deck/seed args

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java`

- [ ] **Step 1: Replace `DuelCommand.java` entirely**

Open `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java`. Replace the file contents with:

```java
package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.core.Deck;
import com.haxerus.duelcraft.core.DeckLoader;
import com.haxerus.duelcraft.core.DeckRegistry;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DuelCommand {

    private static final SuggestionProvider<CommandSourceStack> DECK_NAMES =
            (ctx, builder) -> {
                DeckRegistry reg = DuelManager.get().getDeckRegistry();
                if (reg == null) return builder.buildFuture();
                return SharedSuggestionProvider.suggest(reg.listDeckNames(), builder);
            };

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("duel")
                        .then(Commands.literal("challenge")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> challenge(ctx, ThreadLocalRandom.current().nextLong()))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> challenge(ctx, LongArgumentType.getLong(ctx, "seed"))))))
                        .then(Commands.literal("accept")
                                .executes(DuelCommand::accept))
                        .then(Commands.literal("forfeit")
                                .executes(DuelCommand::forfeit))
                        .then(Commands.literal("test")
                                .executes(ctx -> test(ctx, null, ThreadLocalRandom.current().nextLong()))
                                .then(Commands.argument("aiDeck", StringArgumentType.string())
                                        .suggests(DECK_NAMES)
                                        .executes(ctx -> test(ctx,
                                                StringArgumentType.getString(ctx, "aiDeck"),
                                                ThreadLocalRandom.current().nextLong()))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> test(ctx,
                                                        StringArgumentType.getString(ctx, "aiDeck"),
                                                        LongArgumentType.getLong(ctx, "seed"))))))
                        .then(Commands.literal("deck")
                                .then(Commands.literal("list")
                                        .executes(DuelCommand::deckList))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(DECK_NAMES)
                                                .executes(DuelCommand::deckSet)))
                                .then(Commands.literal("get")
                                        .executes(DuelCommand::deckGet))
                                .then(Commands.literal("clear")
                                        .executes(DuelCommand::deckClear)))
        );
    }

    // --- challenge / accept / forfeit ---

    private static int challenge(CommandContext<CommandSourceStack> ctx, long seed)
            throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("You can't challenge yourself."));
            return 0;
        }
        if (DuelManager.get().getPlayerActiveDuel(sender) != null
                || DuelManager.get().getPlayerActiveDuel(target) != null) {
            sender.sendSystemMessage(Component.literal("A player is already in a duel!"));
            return 0;
        }

        DuelManager.get().duelInvites.put(target.getUUID(), new PendingChallenge(sender.getUUID(), seed));
        sender.sendSystemMessage(Component.literal("Sent duel challenge (seed=" + seed + ")."));
        target.sendSystemMessage(Component.literal("You have been challenged to a duel."));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var pending = DuelManager.get().duelInvites.get(player.getUUID());
        if (pending == null) {
            player.sendSystemMessage(Component.literal("No duel invites."));
            return 0;
        }

        var server = ctx.getSource().getServer();
        var challenger = server.getPlayerList().getPlayer(pending.challengerUUID());
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("Challenger is no longer online."));
            DuelManager.get().duelInvites.remove(player.getUUID());
            return 0;
        }

        Deck challengerDeck;
        Deck accepterDeck;
        try {
            challengerDeck = DuelManager.get().resolveDeck(challenger);
            accepterDeck = DuelManager.get().resolveDeck(player);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Failed to load a deck: " + e.getMessage()));
            return 0;
        }
        String challengerName = DuelManager.get().getPlayerCurrentDeck(challenger.getUUID()).orElse(null);
        String accepterName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        DuelManager.get().startDuel(challenger, player, pending.seed(),
                challengerDeck, accepterDeck, challengerName, accepterName);
        DuelManager.get().duelInvites.remove(player.getUUID());
        return 1;
    }

    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID duelID = DuelManager.get().getPlayerActiveDuel(player);
        if (duelID == null) {
            player.sendSystemMessage(Component.literal("No active duel."));
            return 0;
        }
        DuelManager.get().endDuel(duelID);
        return 1;
    }

    // --- test ---

    private static int test(CommandContext<CommandSourceStack> ctx, String aiDeckName, long seed)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (DuelManager.get().getPlayerActiveDuel(player) != null) {
            player.sendSystemMessage(Component.literal("You are already in a duel!"));
            return 0;
        }

        Deck playerDeck;
        Deck aiDeck;
        try {
            playerDeck = DuelManager.get().resolveDeck(player);
            aiDeck = (aiDeckName == null) ? playerDeck : DuelManager.get().getDeckRegistry().load(aiDeckName);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Failed to load a deck: " + e.getMessage()));
            return 0;
        }
        String playerDeckName = DuelManager.get().getPlayerCurrentDeck(player.getUUID()).orElse(null);

        player.sendSystemMessage(Component.literal("Starting solo test duel vs AI (seed=" + seed + ")..."));
        DuelManager.get().startSoloDuel(player, seed, playerDeck, aiDeck, playerDeckName, aiDeckName);
        return 1;
    }

    // --- deck subcommands ---

    private static int deckList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var names = DuelManager.get().getDeckRegistry().listDeckNames();
        if (names.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No decks. Drop .ydk files into <gameDir>/duelcraft/decks/."));
        } else {
            player.sendSystemMessage(Component.literal("Decks: " + String.join(", ", names)));
        }
        return 1;
    }

    private static int deckSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        try {
            DuelManager.get().getDeckRegistry().load(name); // validate at set-time
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Cannot set deck '" + name + "': " + e.getMessage()));
            return 0;
        }
        DuelManager.get().setPlayerCurrentDeck(player.getUUID(), name);
        player.sendSystemMessage(Component.literal("Current deck set to '" + name + "'."));
        return 1;
    }

    private static int deckGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var name = DuelManager.get().getPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal(
                name.map(n -> "Current deck: " + n).orElse("No current deck (using standard).")));
        return 1;
    }

    private static int deckClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DuelManager.get().clearPlayerCurrentDeck(player.getUUID());
        player.sendSystemMessage(Component.literal("Current deck cleared."));
        return 1;
    }

    /** Pending challenge: who challenged + the agreed seed. */
    public record PendingChallenge(UUID challengerUUID, long seed) {}
}
```

- [ ] **Step 2: Update `duelInvites` map type in `DuelManager`**

The map type changes from `Map<UUID, UUID>` to `Map<UUID, DuelCommand.PendingChallenge>`.

Open `src/main/java/com/haxerus/duelcraft/server/DuelManager.java`. Find:

```java
    public Map<UUID, UUID> duelInvites; // target -> challenger
```

Replace with:

```java
    public Map<UUID, DuelCommand.PendingChallenge> duelInvites; // target -> pending
```

Add the import:

```java
import com.haxerus.duelcraft.server.DuelCommand.PendingChallenge;
```

(The qualified type in the field declaration avoids needing this import; either is fine.)

- [ ] **Step 3: Build the project**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL.

If the build fails because of a stale `Optional` import or `IOException` import in `DuelManager`, add them now.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew test`
Expected: all existing and new unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelCommand.java \
        src/main/java/com/haxerus/duelcraft/server/DuelManager.java
git commit -m "DuelCommand: add /duel deck subcommands and seed/aiDeck args"
```

---

### Task 9: Manual verification in `runClient`

No code changes. This is the integration test for the whole feature.

- [ ] **Step 1: Prepare two test `.ydk` files**

The runClient working directory is `run/` (already exists alongside `run/duelcraft/cache/` from the card data pipeline). Create `run/duelcraft/decks/` if it doesn't exist (the registry will also create it on first server start, so launching once and then placing files works too).

Save these files using EDOPro or by hand:

`alpha.ydk`:
```
#main
89631139
89631139
89631139
55144522
55144522
55144522
12580477
12580477
12580477
66788016
66788016
66788016
5318639
5318639
5318639
83764718
83764718
44095762
44095762
44095762
#extra
7391448
29981921
54752875
50321796
21044178
11398059
29301450
9024198
```

`beta.ydk` (a different deck — any valid passcode list works; the test is about loading and shuffling, not duel logic).

- [ ] **Step 2: Launch the client**

Run: `./gradlew runClient`
Open a single-player world.

- [ ] **Step 3: Verify each command behavior**

Type each command in the in-game chat and confirm the listed expectation:

- `/duel deck list` → server message lists `alpha, beta`.
- `/duel deck set alpha` → "Current deck set to 'alpha'."
- `/duel deck set` and tab-complete → suggestions include `alpha`, `beta`.
- `/duel deck get` → "Current deck: alpha".
- `/duel test` → starts a duel; server log shows `seed=<random>, player=alpha, aiDeck=alpha`. Open the duel screen and note the cards in hand.
- `/duel forfeit`, then `/duel test alpha 42` → server log shows `seed=42, player=alpha, aiDeck=alpha`. Same five cards in hand each time you repeat with seed 42.
- `/duel forfeit`, then `/duel test beta 42` → seed 42 again but different deck → different opening hand from the alpha-vs-alpha case (because the input list differs).
- `/duel deck clear`, then `/duel test` → server log shows `player=<standard>, aiDeck=<standard>`; falls back to the hardcoded `Deck.standard()`.
- Edit `alpha.ydk` (e.g., reorder the first two passcodes). `/duel deck set alpha` → "Current deck set to 'alpha'." (validates the new content). `/duel test alpha 42` → opening hand reflects the edited file. **No relaunch needed.**
- Delete `beta.ydk`. `/duel test beta 42` → "Failed to load a deck: Deck file not found: ...".

- [ ] **Step 4: Inspect server logs**

Check the run console for the duel-start log lines. Each duel should print one `INFO` line containing the seed and deck names. This is what makes seed-based reproduction possible after the fact.

- [ ] **Step 5: Commit nothing — manual verification only**

If any test step fails, return to the relevant earlier task to fix.

---

## Summary of commits

After completing the plan, your `git log --oneline` should show:

```
<hash> DuelCommand: add /duel deck subcommands and seed/aiDeck args
<hash> DuelManager: thread seed through duel start, log it, shuffle decks
<hash> DuelManager: add deck registry and per-player deck state
<hash> Add DuelOptions.standard(long seed) factory
<hash> Add DeckRegistry: list and load .ydk files with hot reload
<hash> Add DeckLoader: parse EDOPro .ydk files
<hash> Add Deck.shuffled(long seed) for deterministic Fisher-Yates
<hash> Add SeedExpander: SplitMix64 long → long[4]
<hash> Add deck loader + seed control design spec
```

---

## Notes for the executor

- **No native (C++) changes.** The shuffle happens entirely Java-side before `OCG_DuelNewCard`.
- **No JNI test runs needed.** `OcgCoreTest` requires EDOPro data; nothing in this plan touches the JNI layer or message-parsing code.
- **Existing `Deck.standard()` is preserved** as the silent fallback. Don't delete it.
- **The commented-out alternative `MAIN[]` in the original `Deck.java`** is removed by Task 2's full-file replacement. That's intentional — once the registry exists, alternates live as `.ydk` files instead of comments.
- **`MinecraftServer#getServerDirectory()`** in NeoForge 21.11 returns a `Path`. If your IDE flags it as deprecated or missing, check the import (`net.minecraft.server.MinecraftServer`).
- If the `IOException` or `Optional` imports go missing during edits to `DuelManager.java`, add them — the existing wildcard `java.util.*` covers `Optional` but not `IOException`.

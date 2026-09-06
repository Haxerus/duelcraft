# Card Data Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the duel UI to display real card names, descriptions, stats, and artwork instead of raw integer codes.

**Architecture:** Client-side only pipeline. Downloads `cards.cdb` (SQLite) from BabelCDB GitHub on first run, caches to `.minecraft/duelcraft/cache/`. Java-side sqlite-jdbc reads card text/stats. Card images download on-demand from ygoprodeck API with two-tier caching (disk + GPU memory via DynamicTexture). The C++ bridge still handles card data for the engine; this pipeline is purely for UI display.

**Tech Stack:** sqlite-jdbc (bundled via JarJar), Java 21 HttpClient, Minecraft DynamicTexture/NativeImage, LDLib2 UI

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `src/main/java/com/haxerus/duelcraft/client/carddata/CardInfo.java` | Immutable record: card code, name, desc, type, atk, def, level, race, attribute |
| `src/main/java/com/haxerus/duelcraft/client/carddata/CardStringHelper.java` | Converts bitmask fields (type, race, attribute) to display strings |
| `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabase.java` | Opens a SQLite .cdb, queries `datas`+`texts` tables, caches results in memory |
| `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabaseDownloader.java` | Downloads `cards.cdb` from configurable URL to local cache dir |
| `src/main/java/com/haxerus/duelcraft/client/carddata/CardImageManager.java` | Two-tier image cache (disk+GPU). Async download, DynamicTexture registration |
| `src/test/java/com/haxerus/duelcraft/client/carddata/CardStringHelperTest.java` | Unit tests for bitmask-to-string conversion |
| `src/test/java/com/haxerus/duelcraft/client/carddata/CardDatabaseTest.java` | Integration tests querying real cards.cdb |

### Modified Files

| File | Changes |
|------|---------|
| `build.gradle:259-265` | Add sqlite-jdbc dependency with JarJar bundling |
| `src/main/java/com/haxerus/duelcraft/Config.java` | Add `CARD_DATABASE_URL` and `CARD_IMAGE_BASE_URL` config entries |
| `src/main/java/com/haxerus/duelcraft/DuelcraftClient.java` | Initialize CardDatabase + CardImageManager on client setup |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java:977-983` | Wire `showCardInfo()` to query CardDatabase and display real data |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java:338-377` | Replace code labels in hand cards with card images |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java:738-771` | Replace code labels in field zone slots with card images |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java:934-970` | Replace code labels in zone inspector with card names + images |

---

## Task 1: Add sqlite-jdbc Dependency and Create CardInfo Record

**Files:**
- Modify: `build.gradle:259-265`
- Create: `src/main/java/com/haxerus/duelcraft/client/carddata/CardInfo.java`

- [ ] **Step 1: Add sqlite-jdbc to build.gradle**

In `build.gradle`, replace the dependencies block:

```gradle
dependencies {
    implementation("com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.5:all")

    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    jarJar("org.xerial:sqlite-jdbc:[3.49,4.0)") {
        version {
            prefer "3.49.1.0"
        }
    }

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testRuntimeOnly 'org.slf4j:slf4j-simple:2.0.16'
}
```

- [ ] **Step 2: Verify Gradle sync succeeds**

Run: `./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep sqlite`
Expected: A line showing `org.xerial:sqlite-jdbc:3.49.1.0`

- [ ] **Step 3: Create CardInfo record**

Create `src/main/java/com/haxerus/duelcraft/client/carddata/CardInfo.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Immutable card data read from the SQLite card database.
 * Fields mirror the datas + texts tables in cards.cdb.
 */
public record CardInfo(
        int code,
        String name,
        String desc,
        int type,
        int atk,
        int def,
        int level,
        long race,
        int attribute
) {
    /** Monster level or Xyz rank (lower 8 bits of level field). */
    public int levelOrRank() {
        return level & 0xFF;
    }

    /** Left pendulum scale (bits 24-31 of level field). */
    public int leftScale() {
        return (level >> 24) & 0xFF;
    }

    /** Right pendulum scale (bits 16-23 of level field). */
    public int rightScale() {
        return (level >> 16) & 0xFF;
    }

    public boolean isMonster() { return (type & TYPE_MONSTER) != 0; }
    public boolean isSpell()   { return (type & TYPE_SPELL) != 0; }
    public boolean isTrap()    { return (type & TYPE_TRAP) != 0; }
    public boolean isLink()    { return (type & TYPE_LINK) != 0; }
    public boolean isXyz()     { return (type & TYPE_XYZ) != 0; }
    public boolean isPendulum() { return (type & TYPE_PENDULUM) != 0; }

    /** For Link monsters, the level field stores the Link rating. */
    public int linkRating() { return level & 0xFF; }

    /** For Link monsters, the def field stores link arrow bitmask. */
    public int linkArrows() { return def; }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/main/java/com/haxerus/duelcraft/client/carddata/CardInfo.java
git commit -m "feat: add sqlite-jdbc dependency and CardInfo record"
```

---

## Task 2: Create CardStringHelper with TDD

**Files:**
- Create: `src/test/java/com/haxerus/duelcraft/client/carddata/CardStringHelperTest.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/carddata/CardStringHelper.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/haxerus/duelcraft/client/carddata/CardStringHelperTest.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import org.junit.jupiter.api.Test;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class CardStringHelperTest {

    @Test
    void attributeName_returnsCorrectNames() {
        assertEquals("DARK", CardStringHelper.attributeName(ATTRIBUTE_DARK));
        assertEquals("LIGHT", CardStringHelper.attributeName(ATTRIBUTE_LIGHT));
        assertEquals("FIRE", CardStringHelper.attributeName(ATTRIBUTE_FIRE));
        assertEquals("WATER", CardStringHelper.attributeName(ATTRIBUTE_WATER));
        assertEquals("EARTH", CardStringHelper.attributeName(ATTRIBUTE_EARTH));
        assertEquals("WIND", CardStringHelper.attributeName(ATTRIBUTE_WIND));
        assertEquals("DIVINE", CardStringHelper.attributeName(ATTRIBUTE_DIVINE));
    }

    @Test
    void attributeName_unknownReturnsQuestionMarks() {
        assertEquals("???", CardStringHelper.attributeName(0));
    }

    @Test
    void raceName_returnsCorrectNames() {
        assertEquals("Warrior", CardStringHelper.raceName(RACE_WARRIOR));
        assertEquals("Spellcaster", CardStringHelper.raceName(RACE_SPELLCASTER));
        assertEquals("Dragon", CardStringHelper.raceName(RACE_DRAGON));
        assertEquals("Machine", CardStringHelper.raceName(RACE_MACHINE));
        assertEquals("Cyberse", CardStringHelper.raceName(RACE_CYBERSE));
    }

    @Test
    void typeLine_normalMonster() {
        // Blue-Eyes White Dragon: LIGHT / Level 8 / Dragon / Normal
        var card = new CardInfo(89631139, "Blue-Eyes White Dragon", "",
                TYPE_MONSTER | TYPE_NORMAL, 3000, 2500,
                8, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertEquals("LIGHT / Level 8 / Dragon / Normal", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_effectMonster() {
        // Dark Magician of Chaos: DARK / Level 8 / Spellcaster / Effect
        var card = new CardInfo(40737112, "Dark Magician of Chaos", "",
                TYPE_MONSTER | TYPE_EFFECT, 2800, 2600,
                8, RACE_SPELLCASTER, ATTRIBUTE_DARK);
        assertEquals("DARK / Level 8 / Spellcaster / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_xyzMonster() {
        // Number 39: Utopia: LIGHT / Rank 4 / Warrior / Xyz
        var card = new CardInfo(84013237, "Number 39: Utopia", "",
                TYPE_MONSTER | TYPE_XYZ, 2500, 2000,
                4, RACE_WARRIOR, ATTRIBUTE_LIGHT);
        assertEquals("LIGHT / Rank 4 / Warrior / Xyz", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_linkMonster() {
        // Decode Talker: DARK / Link 3 / Cyberse / Link
        var card = new CardInfo(1861629, "Decode Talker", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_LINK, 2300, 0,
                3, RACE_CYBERSE, ATTRIBUTE_DARK);
        assertEquals("DARK / Link 3 / Cyberse / Link / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_pendulumMonster() {
        // Odd-Eyes Pendulum Dragon: DARK / Level 7 / Dragon / Pendulum / Effect
        var card = new CardInfo(16178681, "Odd-Eyes Pendulum Dragon", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_PENDULUM, 2500, 2000,
                // level=7, left scale=4 (bits 24-31), right scale=4 (bits 16-23)
                7 | (4 << 24) | (4 << 16), RACE_DRAGON, ATTRIBUTE_DARK);
        assertEquals("DARK / Level 7 / Dragon / Pendulum / Effect", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_spellCard() {
        var card = new CardInfo(70368879, "Raigeki", "", TYPE_SPELL,
                0, 0, 0, 0, 0);
        assertEquals("Spell Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_continuousSpell() {
        var card = new CardInfo(12580477, "Messenger of Peace", "",
                TYPE_SPELL | TYPE_CONTINUOUS, 0, 0, 0, 0, 0);
        assertEquals("Continuous Spell Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_trapCard() {
        var card = new CardInfo(44095762, "Mirror Force", "", TYPE_TRAP,
                0, 0, 0, 0, 0);
        assertEquals("Trap Card", CardStringHelper.typeLine(card));
    }

    @Test
    void typeLine_counterTrap() {
        var card = new CardInfo(40605147, "Solemn Judgment", "",
                TYPE_TRAP | TYPE_COUNTER, 0, 0, 0, 0, 0);
        assertEquals("Counter Trap Card", CardStringHelper.typeLine(card));
    }

    @Test
    void atkDefLine_normalMonster() {
        var card = new CardInfo(89631139, "Blue-Eyes White Dragon", "",
                TYPE_MONSTER | TYPE_NORMAL, 3000, 2500, 8, RACE_DRAGON, ATTRIBUTE_LIGHT);
        assertEquals("ATK 3000 / DEF 2500", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_linkMonster_showsLinkRating() {
        var card = new CardInfo(1861629, "Decode Talker", "",
                TYPE_MONSTER | TYPE_EFFECT | TYPE_LINK, 2300, 0,
                3, RACE_CYBERSE, ATTRIBUTE_DARK);
        assertEquals("ATK 2300 / Link 3", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_unknownAtk() {
        // ? ATK monster
        var card = new CardInfo(0, "Test", "", TYPE_MONSTER | TYPE_EFFECT,
                -2, 0, 1, RACE_WARRIOR, ATTRIBUTE_DARK);
        assertEquals("ATK ? / DEF 0", CardStringHelper.atkDefLine(card));
    }

    @Test
    void atkDefLine_spellCard_returnsEmpty() {
        var card = new CardInfo(70368879, "Raigeki", "", TYPE_SPELL,
                0, 0, 0, 0, 0);
        assertEquals("", CardStringHelper.atkDefLine(card));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.carddata.CardStringHelperTest" 2>&1 | tail -5`
Expected: Compilation error — `CardStringHelper` does not exist

- [ ] **Step 3: Implement CardStringHelper**

Create `src/main/java/com/haxerus/duelcraft/client/carddata/CardStringHelper.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import java.util.ArrayList;
import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Converts card bitmask fields to human-readable display strings.
 */
public final class CardStringHelper {

    private CardStringHelper() {}

    public static String attributeName(int attribute) {
        return switch (Integer.highestOneBit(attribute)) {
            case ATTRIBUTE_EARTH  -> "EARTH";
            case ATTRIBUTE_WATER  -> "WATER";
            case ATTRIBUTE_FIRE   -> "FIRE";
            case ATTRIBUTE_WIND   -> "WIND";
            case ATTRIBUTE_LIGHT  -> "LIGHT";
            case ATTRIBUTE_DARK   -> "DARK";
            case ATTRIBUTE_DIVINE -> "DIVINE";
            default -> "???";
        };
    }

    public static String raceName(long race) {
        long primary = Long.highestOneBit(race);
        if (primary == RACE_WARRIOR)          return "Warrior";
        if (primary == RACE_SPELLCASTER)      return "Spellcaster";
        if (primary == RACE_FAIRY)            return "Fairy";
        if (primary == RACE_FIEND)            return "Fiend";
        if (primary == RACE_ZOMBIE)           return "Zombie";
        if (primary == RACE_MACHINE)          return "Machine";
        if (primary == RACE_AQUA)             return "Aqua";
        if (primary == RACE_PYRO)             return "Pyro";
        if (primary == RACE_ROCK)             return "Rock";
        if (primary == RACE_WINGEDBEAST)      return "Winged Beast";
        if (primary == RACE_PLANT)            return "Plant";
        if (primary == RACE_INSECT)           return "Insect";
        if (primary == RACE_THUNDER)          return "Thunder";
        if (primary == RACE_DRAGON)           return "Dragon";
        if (primary == RACE_BEAST)            return "Beast";
        if (primary == RACE_BEASTWARRIOR)     return "Beast-Warrior";
        if (primary == RACE_DINOSAUR)         return "Dinosaur";
        if (primary == RACE_FISH)             return "Fish";
        if (primary == RACE_SEASERPENT)       return "Sea Serpent";
        if (primary == RACE_REPTILE)          return "Reptile";
        if (primary == RACE_PSYCHIC)          return "Psychic";
        if (primary == RACE_DIVINE)           return "Divine-Beast";
        if (primary == RACE_CREATORGOD)       return "Creator God";
        if (primary == RACE_WYRM)             return "Wyrm";
        if (primary == RACE_CYBERSE)          return "Cyberse";
        if (primary == RACE_ILLUSION)         return "Illusion";
        if (primary == RACE_CYBORG)           return "Cyborg";
        if (primary == RACE_MAGICALKNIGHT)    return "Magical Knight";
        if (primary == RACE_HIGHDRAGON)       return "High Dragon";
        if (primary == RACE_OMEGAPSYCHIC)     return "Omega Psychic";
        if (primary == RACE_CELESTIALWARRIOR) return "Celestial Warrior";
        if (primary == RACE_GALAXY)           return "Galaxy";
        if (primary == RACE_YOKAI)            return "Yokai";
        return "???";
    }

    /**
     * Build the type/stats line shown below the card name.
     * Examples:
     *   "DARK / Level 8 / Spellcaster / Effect"
     *   "Continuous Spell Card"
     *   "Counter Trap Card"
     */
    public static String typeLine(CardInfo card) {
        if (card.isSpell()) return spellTypeLine(card.type());
        if (card.isTrap())  return trapTypeLine(card.type());
        return monsterTypeLine(card);
    }

    /**
     * Build the ATK/DEF line.
     * Examples: "ATK 2500 / DEF 2000", "ATK 2300 / Link 3", ""
     */
    public static String atkDefLine(CardInfo card) {
        if (!card.isMonster()) return "";
        String atk = card.atk() == -2 ? "?" : String.valueOf(card.atk());
        if (card.isLink()) {
            return "ATK " + atk + " / Link " + card.linkRating();
        }
        String def = card.def() == -2 ? "?" : String.valueOf(card.def());
        return "ATK " + atk + " / DEF " + def;
    }

    private static String monsterTypeLine(CardInfo card) {
        var sb = new StringBuilder();
        sb.append(attributeName(card.attribute()));
        sb.append(" / ");

        int type = card.type();
        if (card.isLink()) {
            sb.append("Link ").append(card.linkRating());
        } else if (card.isXyz()) {
            sb.append("Rank ").append(card.levelOrRank());
        } else {
            sb.append("Level ").append(card.levelOrRank());
        }

        sb.append(" / ").append(raceName(card.race()));

        List<String> subtypes = new ArrayList<>();
        if ((type & TYPE_FUSION) != 0)    subtypes.add("Fusion");
        if ((type & TYPE_SYNCHRO) != 0)   subtypes.add("Synchro");
        if ((type & TYPE_XYZ) != 0)       subtypes.add("Xyz");
        if ((type & TYPE_LINK) != 0)      subtypes.add("Link");
        if ((type & TYPE_RITUAL) != 0)    subtypes.add("Ritual");
        if ((type & TYPE_PENDULUM) != 0)  subtypes.add("Pendulum");
        if ((type & TYPE_SPIRIT) != 0)    subtypes.add("Spirit");
        if ((type & TYPE_UNION) != 0)     subtypes.add("Union");
        if ((type & TYPE_GEMINI) != 0)    subtypes.add("Gemini");
        if ((type & TYPE_TUNER) != 0)     subtypes.add("Tuner");
        if ((type & TYPE_FLIP) != 0)      subtypes.add("Flip");
        if ((type & TYPE_TOON) != 0)      subtypes.add("Toon");
        if ((type & TYPE_EFFECT) != 0)    subtypes.add("Effect");
        else if ((type & TYPE_NORMAL) != 0) subtypes.add("Normal");

        for (String sub : subtypes) {
            sb.append(" / ").append(sub);
        }
        return sb.toString();
    }

    private static String spellTypeLine(int type) {
        String prefix = "";
        if ((type & TYPE_QUICKPLAY) != 0)   prefix = "Quick-Play ";
        else if ((type & TYPE_CONTINUOUS) != 0) prefix = "Continuous ";
        else if ((type & TYPE_EQUIP) != 0)     prefix = "Equip ";
        else if ((type & TYPE_FIELD) != 0)     prefix = "Field ";
        else if ((type & TYPE_RITUAL) != 0)    prefix = "Ritual ";
        return prefix + "Spell Card";
    }

    private static String trapTypeLine(int type) {
        String prefix = "";
        if ((type & TYPE_CONTINUOUS) != 0) prefix = "Continuous ";
        else if ((type & TYPE_COUNTER) != 0)  prefix = "Counter ";
        return prefix + "Trap Card";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.carddata.CardStringHelperTest" 2>&1 | tail -5`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/carddata/CardStringHelper.java \
        src/test/java/com/haxerus/duelcraft/client/carddata/CardStringHelperTest.java
git commit -m "feat: add CardStringHelper with bitmask-to-string conversion"
```

---

## Task 3: Create CardDatabase with TDD

**Files:**
- Create: `src/test/java/com/haxerus/duelcraft/client/carddata/CardDatabaseTest.java`
- Create: `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabase.java`

Tests use the local EDOPro `cards.cdb` (same pattern as `OcgCoreTest`).

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/haxerus/duelcraft/client/carddata/CardDatabaseTest.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CardDatabase using the local EDOPro cards.cdb.
 * Requires: -Dduelcraft.test.dbPath=path/to/cards.cdb
 */
class CardDatabaseTest {

    private static CardDatabase db;

    @BeforeAll
    static void openDb() throws Exception {
        String dbPath = System.getProperty("duelcraft.test.dbPath");
        assertNotNull(dbPath, "Set -Dduelcraft.test.dbPath");
        db = new CardDatabase(Path.of(dbPath));
    }

    @AfterAll
    static void closeDb() throws Exception {
        if (db != null) db.close();
    }

    @Test
    void getCard_blueEyesWhiteDragon() {
        CardInfo card = db.getCard(89631139);
        assertNotNull(card);
        assertEquals("Blue-Eyes White Dragon", card.name());
        assertEquals(89631139, card.code());
        assertEquals(3000, card.atk());
        assertEquals(2500, card.def());
        assertEquals(8, card.levelOrRank());
        assertTrue(card.isMonster());
        assertFalse(card.isSpell());
        assertEquals(ATTRIBUTE_LIGHT, card.attribute());
        assertEquals(RACE_DRAGON, card.race());
    }

    @Test
    void getCard_raigeki_spellCard() {
        CardInfo card = db.getCard(12580477);
        assertNotNull(card);
        assertTrue(card.isSpell());
        assertFalse(card.isMonster());
    }

    @Test
    void getCard_unknownCode_returnsNull() {
        assertNull(db.getCard(999999999));
    }

    @Test
    void getCard_cachedOnSecondCall() {
        CardInfo first = db.getCard(89631139);
        CardInfo second = db.getCard(89631139);
        assertSame(first, second, "Second call should return cached instance");
    }

    @Test
    void getCard_darkMagician_hasDescription() {
        CardInfo card = db.getCard(46986414);
        assertNotNull(card);
        assertEquals("Dark Magician", card.name());
        assertNotNull(card.desc());
        assertFalse(card.desc().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.carddata.CardDatabaseTest" 2>&1 | tail -5`
Expected: Compilation error — `CardDatabase` does not exist

- [ ] **Step 3: Implement CardDatabase**

Create `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabase.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads card data from a ygopro-format SQLite card database (cards.cdb).
 * Point queries by card code, results cached in memory.
 */
public class CardDatabase implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatabase.class);

    private static final String QUERY = """
            SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute
            FROM datas d JOIN texts t ON d.id = t.id
            WHERE d.id = ?
            """;

    private final Connection connection;
    private final Map<Integer, CardInfo> cache = new ConcurrentHashMap<>();

    public CardDatabase(Path dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    /**
     * Look up a card by code. Returns null if not found.
     * Results are cached — repeated calls for the same code return the same instance.
     */
    public CardInfo getCard(int code) {
        if (code == 0) return null;
        return cache.computeIfAbsent(code, this::queryCard);
    }

    private CardInfo queryCard(int code) {
        try (PreparedStatement stmt = connection.prepareStatement(QUERY)) {
            stmt.setInt(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CardInfo(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("desc"),
                            rs.getInt("type"),
                            rs.getInt("atk"),
                            rs.getInt("def"),
                            rs.getInt("level"),
                            rs.getLong("race"),
                            rs.getInt("attribute")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to query card {}: {}", code, e.getMessage());
        }
        return null;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.haxerus.duelcraft.client.carddata.CardDatabaseTest" 2>&1 | tail -5`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabase.java \
        src/test/java/com/haxerus/duelcraft/client/carddata/CardDatabaseTest.java
git commit -m "feat: add CardDatabase for SQLite card data queries"
```

---

## Task 4: Create CardDatabaseDownloader

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabaseDownloader.java`

No unit tests — this is a thin HTTP wrapper. Verified by running the client.

- [ ] **Step 1: Implement CardDatabaseDownloader**

Create `src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabaseDownloader.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Downloads cards.cdb from a remote URL (typically BabelCDB on GitHub)
 * to a local cache directory. Skips download if file already exists.
 */
public final class CardDatabaseDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDatabaseDownloader.class);

    private CardDatabaseDownloader() {}

    /**
     * Ensure cards.cdb exists at cacheDir/cards.cdb.
     * Downloads from url if not already cached.
     *
     * @return path to the local .cdb file
     */
    public static Path ensureDatabase(String url, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Path dbFile = cacheDir.resolve("cards.cdb");

        if (Files.exists(dbFile) && Files.size(dbFile) > 0) {
            LOGGER.info("Card database already cached at {}", dbFile);
            return dbFile;
        }

        LOGGER.info("Downloading card database from {}...", url);
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<Path> response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(dbFile));

            if (response.statusCode() != 200) {
                Files.deleteIfExists(dbFile);
                throw new IOException("Failed to download card database: HTTP " + response.statusCode());
            }

            LOGGER.info("Card database downloaded ({} bytes)", Files.size(dbFile));
            return dbFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(dbFile);
            throw new IOException("Download interrupted", e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabaseDownloader.java
git commit -m "feat: add CardDatabaseDownloader for auto-downloading cards.cdb"
```

---

## Task 5: Add Config Entries

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/Config.java`

- [ ] **Step 1: Add URL config entries**

Add two new config entries after the existing `SCRIPT_SEARCH_PATHS` definition in `Config.java`. The new lines go between the `SCRIPT_SEARCH_PATHS` block and the `static final ModConfigSpec SPEC = BUILDER.build();` line:

```java
    public static final ModConfigSpec.ConfigValue<String> CARD_DATABASE_URL = BUILDER
            .comment("URL to download the card database from (BabelCDB)")
            .define("cardDatabaseUrl",
                    "https://raw.githubusercontent.com/ProjectIgnis/BabelCDB/master/cards.cdb");

    public static final ModConfigSpec.ConfigValue<String> CARD_IMAGE_BASE_URL = BUILDER
            .comment("Base URL for card images (code.jpg appended)")
            .define("cardImageBaseUrl",
                    "https://images.ygoprodeck.com/images/cards_small/");
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/Config.java
git commit -m "feat: add card database URL and card image URL config entries"
```

---

## Task 6: Create CardImageManager

**Files:**
- Create: `src/main/java/com/haxerus/duelcraft/client/carddata/CardImageManager.java`

No unit tests — depends on Minecraft's TextureManager. Verified by running the client.

- [ ] **Step 1: Implement CardImageManager**

Create `src/main/java/com/haxerus/duelcraft/client/carddata/CardImageManager.java`:

```java
package com.haxerus.duelcraft.client.carddata;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Two-tier card image cache: disk files + GPU-registered DynamicTextures.
 * Downloads card art on-demand from ygoprodeck API in a background thread pool.
 * Returns a placeholder texture while loading.
 */
public class CardImageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardImageManager.class);

    private final String baseUrl;
    private final Path cacheDir;
    private final Map<Integer, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private final Set<Integer> loading = ConcurrentHashMap.newKeySet();
    private final Set<Integer> failed = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "CardImageLoader"); t.setDaemon(true); return t; });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CardImageManager(String baseUrl, Path cacheDir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create image cache directory: {}", e.getMessage());
        }
    }

    /**
     * Get the texture ResourceLocation for a card image.
     * Returns null if the image is not yet loaded (triggers async download).
     * Returns null for code 0 (face-down/unknown) or previously failed downloads.
     */
    public ResourceLocation getCardTexture(int code) {
        if (code == 0) return null;

        // L1: Already registered as DynamicTexture
        ResourceLocation cached = textureCache.get(code);
        if (cached != null) return cached;

        // Don't retry known failures
        if (failed.contains(code)) return null;

        // L2: Check disk cache
        Path diskFile = cacheDir.resolve(code + ".jpg");
        if (Files.exists(diskFile)) {
            ResourceLocation loc = loadFromDisk(code, diskFile);
            if (loc != null) return loc;
        }

        // L3: Start async download
        if (loading.add(code)) {
            executor.submit(() -> downloadAndCache(code));
        }

        return null;
    }

    /** Check if a specific card's image is loaded and ready. */
    public boolean isLoaded(int code) {
        return textureCache.containsKey(code);
    }

    private ResourceLocation loadFromDisk(int code, Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            return registerTexture(code, data);
        } catch (IOException e) {
            LOGGER.warn("Failed to load cached card image {}: {}", code, e.getMessage());
            return null;
        }
    }

    private void downloadAndCache(int code) {
        try {
            String url = baseUrl + code + ".jpg";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                byte[] data = response.body();
                // Save to disk cache
                Files.write(cacheDir.resolve(code + ".jpg"), data);
                // Register texture on the render thread
                Minecraft.getInstance().execute(() -> registerTexture(code, data));
                LOGGER.debug("Downloaded card image for {}", code);
            } else {
                LOGGER.debug("Card image not found for {}: HTTP {}", code, response.statusCode());
                failed.add(code);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to download card image for {}: {}", code, e.getMessage());
            failed.add(code);
        } finally {
            loading.remove(code);
        }
    }

    private ResourceLocation registerTexture(int code, byte[] data) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("duelcraft",
                    "dynamic/card_" + code);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            textureCache.put(code, loc);
            return loc;
        } catch (IOException e) {
            LOGGER.warn("Failed to decode card image for {}: {}", code, e.getMessage());
            failed.add(code);
            return null;
        }
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation loc : textureCache.values()) {
            textureManager.release(loc);
        }
        textureCache.clear();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/carddata/CardImageManager.java
git commit -m "feat: add CardImageManager with async download and DynamicTexture caching"
```

---

## Task 7: Initialize Pipeline in DuelcraftClient

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/DuelcraftClient.java`
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` (add static accessors)

- [ ] **Step 1: Add card data fields and initialization to DuelcraftClient**

Replace the entire `DuelcraftClient.java` with:

```java
package com.haxerus.duelcraft;

import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardDatabaseDownloader;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@Mod(value = Duelcraft.MODID, dist = Dist.CLIENT)
public class DuelcraftClient {

    private static @Nullable CardDatabase cardDatabase;
    private static @Nullable CardImageManager cardImageManager;

    public DuelcraftClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(DuelcraftClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        Duelcraft.LOGGER.info("Duelcraft client setup");
        event.enqueueWork(DuelcraftClient::initCardData);
    }

    private static void initCardData() {
        try {
            Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
            Path cacheDir = gameDir.resolve("duelcraft").resolve("cache");

            // Download card database if needed
            String dbUrl = Config.CARD_DATABASE_URL.get();
            Path dbPath = CardDatabaseDownloader.ensureDatabase(dbUrl, cacheDir);
            cardDatabase = new CardDatabase(dbPath);
            Duelcraft.LOGGER.info("Card database loaded from {}", dbPath);

            // Initialize image manager
            String imageBaseUrl = Config.CARD_IMAGE_BASE_URL.get();
            Path imageDir = cacheDir.resolve("images");
            cardImageManager = new CardImageManager(imageBaseUrl, imageDir);
            Duelcraft.LOGGER.info("Card image manager initialized");

        } catch (Exception e) {
            Duelcraft.LOGGER.error("Failed to initialize card data pipeline", e);
        }
    }

    public static @Nullable CardDatabase getCardDatabase() {
        return cardDatabase;
    }

    public static @Nullable CardImageManager getCardImageManager() {
        return cardImageManager;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/DuelcraftClient.java
git commit -m "feat: initialize card data pipeline on client setup"
```

---

## Task 8: Wire Card Text into UI

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

This task wires CardDatabase + CardStringHelper into three UI areas:
1. `showCardInfo()` — the left card-info banner on hover
2. Zone inspector — card names instead of code numbers
3. Pending prompt card lists — card names in selection dialogs

- [ ] **Step 1: Add imports to LDLibDuelScreen**

Add these imports at the top of `LDLibDuelScreen.java`, after the existing imports:

```java
import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.carddata.CardDatabase;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.carddata.CardStringHelper;
```

- [ ] **Step 2: Replace showCardInfo() method**

In `LDLibDuelScreen.java`, replace the `showCardInfo` method (around line 977-983) with:

```java
        private void showCardInfo(int code) {
            if (code == 0 || cardInfoBanner == null) return;
            cardInfoBanner.removeClass("hidden");

            CardDatabase db = DuelcraftClient.getCardDatabase();
            CardInfo card = db != null ? db.getCard(code) : null;

            var nameLabel = byId("card-name-label");
            var statsLabel = byId("card-stats-label");
            var textLabel = byId("card-text");
            var atkDefLabel = byId("card-atk-def-label");
            var imageArea = byId("card-image-area");

            if (card != null) {
                if (nameLabel instanceof Label lbl)
                    lbl.setText(Component.literal(card.name()));
                if (statsLabel instanceof Label lbl)
                    lbl.setText(Component.literal(CardStringHelper.typeLine(card)));
                if (textLabel instanceof Label lbl)
                    lbl.setText(Component.literal(card.desc()));
                if (atkDefLabel instanceof Label lbl)
                    lbl.setText(Component.literal(CardStringHelper.atkDefLine(card)));

                // Card image
                CardImageManager images = DuelcraftClient.getCardImageManager();
                if (images != null && imageArea != null) {
                    var loc = images.getCardTexture(code);
                    if (loc != null) {
                        imageArea.lss("background", "sprite(" + loc + ")");
                    } else {
                        imageArea.lss("background", "sdf(#3c3c50, 3, 2)");
                    }
                }
            } else {
                if (nameLabel instanceof Label lbl)
                    lbl.setText(Component.literal("Card #" + code));
                if (statsLabel instanceof Label lbl)
                    lbl.setText(Component.literal(""));
                if (textLabel instanceof Label lbl)
                    lbl.setText(Component.literal(""));
                if (atkDefLabel instanceof Label lbl)
                    lbl.setText(Component.literal(""));
                if (imageArea != null)
                    imageArea.lss("background", "sdf(#3c3c50, 3, 2)");
            }
        }
```

- [ ] **Step 3: Update zone inspector to show card names**

In the `handlePileClick` method (around line 934-970), replace the label creation inside the for loop. Find this block:

```java
                           var label = new Label();
                           label.setText(Component.literal(String.valueOf(code)));
                           label.lss("font-size", "6");
                           label.lss("horizontal-align", "center");
                           card.addChild(label);
```

Replace with:

```java
                           var label = new Label();
                           CardDatabase db = DuelcraftClient.getCardDatabase();
                           CardInfo cardInfo = db != null ? db.getCard(code) : null;
                           String displayName = cardInfo != null ? cardInfo.name() : String.valueOf(code);
                           label.setText(Component.literal(displayName));
                           label.lss("font-size", "6");
                           label.lss("horizontal-align", "center");
                           card.addChild(label);
```

- [ ] **Step 4: Update hand card labels to show card names**

In the `rebuildHand` method (around line 338-377), find the block that creates the label for local hand cards:

```java
                    var label = new Label();
                    label.setText(Component.literal(String.valueOf(code)));
                    label.lss("font-size", "6");
                    label.lss("horizontal-align", "center");
                    card.addChild(label);
```

Replace with:

```java
                    var label = new Label();
                    CardDatabase db = DuelcraftClient.getCardDatabase();
                    CardInfo cardInfo = db != null ? db.getCard(code) : null;
                    String displayName = cardInfo != null ? cardInfo.name() : String.valueOf(code);
                    label.setText(Component.literal(displayName));
                    label.lss("font-size", "5");
                    label.lss("horizontal-align", "center");
                    label.lss("text-wrap", "wrap");
                    card.addChild(label);
```

- [ ] **Step 5: Update field zone slot labels to show card names**

In the `refreshZoneSlot` method (around line 738-771), find the block that creates the label for face-up cards:

```java
                    cardVisual.addClass("card-image");
                    // TODO: Replace with card image
                    var label = new Label();
                    label.setText(Component.literal(String.valueOf(code)));
                    label.lss("font-size", "6");
                    cardVisual.addChild(label);
```

Replace with:

```java
                    cardVisual.addClass("card-image");
                    var label = new Label();
                    CardDatabase db = DuelcraftClient.getCardDatabase();
                    CardInfo cardInfo = db != null ? db.getCard(code) : null;
                    String displayName = cardInfo != null ? cardInfo.name() : String.valueOf(code);
                    label.setText(Component.literal(displayName));
                    label.lss("font-size", "5");
                    label.lss("text-wrap", "wrap");
                    cardVisual.addChild(label);
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run all tests to check for regressions**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All tests PASS (existing + new)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: wire card names and info into duel screen UI"
```

---

## Task 9: Manual Verification and Polish

This task verifies the full pipeline by running the Minecraft client.

- [ ] **Step 1: Run the client**

Run: `./gradlew runClient`

1. Observe the console for: `Card database loaded from ...` and `Card image manager initialized`
2. If this is the first run, also look for: `Downloading card database from ...`
3. Start a solo duel with `/duel test`

- [ ] **Step 2: Verify card names appear**

1. Your hand cards should show card names instead of integer codes
2. Hover over a card in your hand — the left card-info banner should show:
   - Card name (yellow, top)
   - Type line (e.g., "DARK / Level 7 / Spellcaster / Effect")
   - Card description text
   - ATK/DEF line
3. Play a card to the field — the zone slot should show the card name
4. Click the Graveyard pile — the inspector should list cards by name

- [ ] **Step 3: Verify card images load**

1. Hover over a card — the card-image-area should eventually show the card artwork
   (first hover may show the placeholder gray box; re-hover after a moment)
2. Check `.minecraft/duelcraft/cache/images/` for downloaded `.jpg` files

- [ ] **Step 4: Fix any issues found during manual testing**

Address any issues discovered. Common things to check:
- sprite() ResourceLocation format — if images don't appear, try alternate path formats
- Font sizes — card names may be too long for small slots; adjust font-size if needed
- Image aspect ratio — card art may be stretched in the 150x150 area; adjust dimensions if needed

- [ ] **Step 5: Final commit with any polish fixes**

```bash
git add -A
git commit -m "feat: complete card data pipeline — names, stats, and artwork in duel UI"
```

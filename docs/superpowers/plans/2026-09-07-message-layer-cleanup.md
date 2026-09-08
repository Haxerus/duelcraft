# Message Layer Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the 2026-09-07 walkthrough cleanup: finish the QueryParser removal already in the working tree, fix two pre-existing engine-format bugs (u8 `QUERY_IS_PUBLIC`, 23-byte `MSG_SELECT_CHAIN` entries) plus two latent ones, and make deck errors and docs match the code.

**Architecture:** No new abstractions. `FieldQuery` becomes the single reader for the native per-slot query buffer (`[u16 size][u32 flag][data]`... `QUERY_END`) and resyncs on every block size. `MessageParser` reads the layouts ygopro-core actually writes. Wire records (`SelectChain`, `ActivatableCard`, `ShuffleExtra`, `Hint`), `DuelMessageCodec`, and the client are untouched.

**Tech Stack:** Java 21, NeoForge 21.1 (MC 1.21.1), JUnit 5 via Gradle 9, LDLib2 UI test harness. Windows, commands run in Git Bash.

**Spec:** none. Each task cites the ygopro-core source (`native/ygopro-core/`) that defines the layout it fixes; the engine is the authority.

## Global Constraints

- Repo `CLAUDE.md` best practices bind every task: minimal, surgical, human-readable. Every changed line must trace to its task. Do not reformat, "improve", or comment adjacent code.
- Engine layouts are authoritative: `native/ygopro-core/card.cpp` (`card::get_infos`), `playerop.cpp` (`select_chain`, idle/battle commands), `field.cpp` (shuffle, player hints). Cite them in code comments only where the plan shows a comment.
- Branch `message-layer-cleanup` (already checked out, carrying the owner's uncommitted work). Stage only the paths each task names. Never `git add -A`, `git add .`, or `git add <dir>` beyond the listed files. Leave untracked `AGENTS.md`, `dev/`, `docs/Duelcraft Feature Checklist.md` alone. `docs/engine-implementation-checklist.md` shows an end-of-line-only working-tree diff; only Task 4 touches it.
- Commit messages: one imperative sentence, no `feat:` style prefixes, matching the repo history (`Add click scenario for the context menu; ...`).
- Tests: `./gradlew test --console=plain` must end `BUILD SUCCESSFUL`. The suite needs EDOPro data at `C:/ProjectIgnis` (present on this machine). Run Gradle from the repo root with Bash timeout 600000 ms.
- UI harness: `./gradlew runClient -PldTest=group:duelcraft --console=plain` opens a game window and must print `6/6 scenarios passed`; details in `build/ldlib2-uitest/report.txt`.
- `CLAUDE.md` is gitignored (`.gitignore:48`). When a task edits it, change the local file and never stage it.
- Record shapes stay as they are: `ActivatableCard(int code, int controller, int location, int sequence, long desc, int flag)`, `ShuffleExtra(int player)`, `Hint(int hintType, int player, long data)`, `SelectChain(int player, int speCount, boolean forced, int hint0, int hint1, List<ActivatableCard> chains)`.

---

### Task 1: Finish the QueryParser removal and green the tree

The working tree already holds the owner's refactor: `QueryParser` and `QueryParserTest` deleted (staged), `FieldQuery` created with the block reader, `MessageParser` and `MessageParserTest` stripped of the `MSG_UPDATE_DATA`/`MSG_UPDATE_CARD` cases and tests, `Deck.standard()` commented out, `DuelEngine.close()` no longer throwing, `DuelManager.startDuel` shuffling team 2 with `seed+1`. This task completes it: the fixture indexes past its 28-code list (every harness scenario would throw `IndexOutOfBoundsException`), the solo duel still shuffles both decks with the same seed, commented-out code and an orphaned test helper remain, and the git index holds only the deletions plus an empty `FieldQuery` stub. Leave `FieldQuery.java` content exactly as it is; Task 2 rewrites it.

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java` (whole file below)
- Modify: `src/main/java/com/haxerus/duelcraft/core/Deck.java` (whole file below)
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelManager.java:84-88, 90-94, 112-115, 118, 167-171, 190-193, 196`
- Modify: `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java:581-621` (delete)
- Stage as-is: `src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java`, `.../duel/message/MessageParser.java`, `.../duel/DuelSession.java`, `.../core/DuelEngine.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `FieldQuery.readFieldBlock(ByteBuffer, QueriedCard, int)` is committed unchanged for Task 2 to replace with `FieldQuery.parse(byte[])`.

- [ ] **Step 1: Replace `DuelScreenFixture.java` with this content**

```java
package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.client.FieldLayout;
import com.haxerus.duelcraft.client.FieldLayout.PendulumMode;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
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
 * would receive. Card codes are a fixed list of passcodes; nothing here depends on the card
 * database or images being present.
 */
@OnlyIn(Dist.CLIENT)
public final class DuelScreenFixture {

    private static final List<Integer> CODES = List.of(
            89631139, 33750025, 28406301, 55415564,
            49238328, 39153655, 39153655, 70095154,
            11747708, 55144522, 24094653, 55144522,
            25259669, 25259669, 13039848, 55144522,
            31786629, 31786629, 43096270, 11091375,
            11091375, 11091375, 11091375, 69247929,
            69247929, 69247929, 69247929, 28406301);

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

        for (int p = 0; p < 2; p++) {
            LDLibDuelScreen.applyMessage(new DuelMessage.Draw(p, CODES.subList(0, 10)));
        }

        int first = layout.columns() == 3 ? 1 : 0;
        int last = layout.columns() == 3 ? 3 : 4;
        for (int p = 0; p < 2; p++) {
            for (int seq = first; seq <= last; seq++) {
                moveFromHand(p, CODES.get(seq - first), LOCATION_MZONE, seq, POS_FACEUP_ATTACK);
            }
        }
        if (layout.emz()) {
            moveFromHand(0, CODES.get(5), LOCATION_MZONE, 5, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, CODES.get(20), LOCATION_SZONE, 1, POS_FACEUP_ATTACK);
        if (layout.pendulum() == PendulumMode.SEPARATE) {
            moveFromHand(0, CODES.get(21), LOCATION_SZONE, 6, POS_FACEUP_ATTACK);
        }
        moveFromHand(0, CODES.get(27), LOCATION_GRAVE, 0, POS_FACEUP_ATTACK);
    }

    /**
     * Sends an idle command whose only entry makes the monster at {@code (player, MZONE, sequence)}
     * repositionable, so clicking that zone opens the context menu. Card codes follow
     * {@link #populate}'s five-column mapping, where monster zone N holds {@code CODES.get(N)}.
     */
    public static void promptRepositionOf(int player, int sequence) {
        int code = CODES.get(sequence);
        LDLibDuelScreen.applyMessage(new DuelMessage.SelectIdleCmd(player,
                List.of(), List.of(),
                List.of(new DuelMessage.ReposCard(code, player, LOCATION_MZONE, sequence)),
                List.of(), List.of(), List.of(),
                true, true, false));
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

(`ClientDuelState` copies draw codes with `addAll`, so the immutable sublist is safe.)

- [ ] **Step 2: Replace `Deck.java` with this content**

```java
package com.haxerus.duelcraft.core;

import java.util.ArrayList;
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
}
```

- [ ] **Step 3: Edit `DuelManager.java`**

Delete the two unused convenience overloads (no callers anywhere in `src/`):

```java
    /** Convenience: random seed, Master Rule 5. */
    public void startSoloDuel(ServerPlayer player, Deck playerDeck, Deck aiDeck) {
        startSoloDuel(player, java.util.concurrent.ThreadLocalRandom.current().nextLong(), DuelRule.MR5,
                playerDeck, aiDeck, null, null);
    }
```

```java
    /** Convenience: random seed, Master Rule 5, decks resolved earlier by the caller. */
    public void startDuel(ServerPlayer p1, ServerPlayer p2, Deck team1Deck, Deck team2Deck) {
        startDuel(p1, p2, java.util.concurrent.ThreadLocalRandom.current().nextLong(), DuelRule.MR5,
                team1Deck, team2Deck, null, null);
    }
```

Replace the `startSoloDuel` javadoc with:

```java
    /**
     * Start a solo test duel where player 1 is AI-controlled.
     * The player's deck is shuffled with {@code seed} and the AI's with {@code seed + 1}, so two
     * identical lists still produce different draws. Deck names are used only for logging.
     */
```

Replace the solo log call (the `"<standard>"` ternaries go away; names are never null now that `DuelCommand` is the only caller):

```java
        LOGGER.info("Solo duel {}: seed={}, rule={}, player={}, aiDeck={}",
                duelId, seed, rule.id(), playerDeckName, aiDeckName);
```

Change `Deck shuffledAi = aiDeck.shuffled(seed);` to:

```java
        Deck shuffledAi = aiDeck.shuffled(seed + 1);
```

Add this javadoc directly above `public void startDuel(ServerPlayer p1, ServerPlayer p2, long seed, DuelRule rule,`:

```java
    /** Team 1 shuffles with {@code seed}, team 2 with {@code seed + 1}; see {@link #startSoloDuel}. */
```

Replace the duel log call:

```java
        LOGGER.info("Duel {}: seed={}, rule={}, decks=[{}, {}]",
                duelId, seed, rule.id(), team1Name, team2Name);
```

Change `Deck shuffled2 = team2Deck.shuffled(seed+1);` to `Deck shuffled2 = team2Deck.shuffled(seed + 1);` (spacing only).

- [ ] **Step 4: Delete the orphaned helper in `MessageParserTest.java`**

Delete from the line `    // ---- State Update Messages ----` through the closing brace of `static byte[] buildCardQueryBlock(int code, int attack, int defense)` (its javadoc included), so `parseSelectUnselectCard`'s closing brace is followed by one blank line and `    // ---- Misc ----`. The helper described the deleted `QueryParser` framing and has no callers.

- [ ] **Step 5: Compile and run the JUnit suite**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`, no failures. (`DeckShuffleTest.differentSeedsProduceDifferentMainOrder` already covers two identical lists diverging under `seed` and `seed + 1`; no new test.)

- [ ] **Step 6: Run the UI harness to prove the fixture indices**

Run: `./gradlew runClient -PldTest=group:duelcraft --console=plain` (a game window opens; wait for it to close)
Expected: `6/6 scenarios passed` in the output and in `build/ldlib2-uitest/report.txt`. Before this task every scenario failed in `DuelScreenFixture.populate` with `IndexOutOfBoundsException`.

- [ ] **Step 7: Commit in two pieces**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java \
        src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/main/java/com/haxerus/duelcraft/duel/DuelSession.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "Remove QueryParser: the engine never emits MSG_UPDATE_DATA/CARD; FieldQuery holds the native block reader"
git add src/main/java/com/haxerus/duelcraft/core/Deck.java \
        src/main/java/com/haxerus/duelcraft/core/DuelEngine.java \
        src/main/java/com/haxerus/duelcraft/server/DuelManager.java \
        src/main/java/com/haxerus/duelcraft/client/uitest/DuelScreenFixture.java
git commit -m "Drop Deck.standard(): duels require a set deck; shuffle the second deck with seed + 1"
```

The `QueryParser` deletions are already staged and ride the first commit. `DuelEngine` must go with `DuelManager` (its `shutdown` no longer catches the removed `throws Exception`). After both commits `git status --short` shows only the three untracked paths and the end-of-line-only `docs/engine-implementation-checklist.md`.

---

### Task 2: FieldQuery owns the native query framing and reads engine widths

`card::get_infos` (`native/ygopro-core/card.cpp:100-215`) writes each requested field as `[u16 size][u32 flag][data]` with `size` counting the flag and the data, and closes the buffer with `[u16 4][u32 QUERY_END]`. `QUERY_OWNER`, `QUERY_IS_PUBLIC`, and `QUERY_IS_HIDDEN` are `uint8_t`; `QUERY_RACE` is `uint64_t`; `QUERY_LINK` is two `uint32_t`; everything else is `uint32_t`. The current reader takes `QUERY_IS_PUBLIC` as four bytes, swallowing the start of the `QUERY_LINK` block that follows it in bit order: `isPublic` reads true for every card, so `ServerDuelHandler.sanitizeCard` never strips face-down cards (their codes reach the opponent's client), and Link monsters get misaligned stats. The framing loop still lives in `DuelSession.parseSingleNativeQuery`, and its `fieldSize == 0` stop never fires because the engine terminates with `QUERY_END`, whose flag currently pollutes `card.flags`.

**Files:**
- Create: `src/test/java/com/haxerus/duelcraft/duel/message/FieldQueryTest.java`
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java` (whole file below)
- Modify: `src/main/java/com/haxerus/duelcraft/duel/DuelSession.java:150, 166-175` and its imports

**Interfaces:**
- Consumes: `QueriedCard` public fields; `OcgConstants.QUERY_*` including `QUERY_END = 0x80000000` (already defined at `OcgConstants.java:207`).
- Produces: `public static QueriedCard parse(byte[] data)` on `FieldQuery`; `readFieldBlock` disappears.

- [ ] **Step 1: Write the failing tests**

Create `FieldQueryTest.java`:

```java
package com.haxerus.duelcraft.duel.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.haxerus.duelcraft.core.OcgConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/** Byte layouts follow ygopro-core card::get_infos: [u16 size][u32 flag][data], size counting flag and data. */
class FieldQueryTest {

    private static ByteBuffer buf(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void u32Block(ByteBuffer b, int flag, int value) {
        b.putShort((short) 8); b.putInt(flag); b.putInt(value);
    }

    private static void u8Block(ByteBuffer b, int flag, int value) {
        b.putShort((short) 5); b.putInt(flag); b.put((byte) value);
    }

    private static void endBlock(ByteBuffer b) {
        b.putShort((short) 4); b.putInt(QUERY_END);
    }

    @Test
    void readsU32BlocksAndStopsAtQueryEnd() {
        ByteBuffer b = buf(10 + 10 + 6);
        u32Block(b, QUERY_CODE, 89631139);
        u32Block(b, QUERY_ATTACK, 3000);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(89631139, card.code);
        assertEquals(3000, card.attack);
        assertEquals(QUERY_CODE | QUERY_ATTACK, card.flags);
    }

    @Test
    void isPublicIsOneByteSoLinkStaysAligned() {
        ByteBuffer b = buf(7 + 14 + 6);
        u8Block(b, QUERY_IS_PUBLIC, 0);
        b.putShort((short) 12); b.putInt(QUERY_LINK); b.putInt(3); b.putInt(0b1010);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertFalse(card.isPublic);
        assertEquals(3, card.linkRating);
        assertEquals(0b1010, card.linkMarker);
    }

    @Test
    void publicFlagSetReadsTrue() {
        ByteBuffer b = buf(7 + 6);
        u8Block(b, QUERY_IS_PUBLIC, 1);
        endBlock(b);

        assertTrue(FieldQuery.parse(b.array()).isPublic);
    }

    @Test
    void unhandledFieldIsSkippedBySize() {
        ByteBuffer b = buf(7 + 10 + 6);
        u8Block(b, QUERY_IS_HIDDEN, 1);   // no case in FieldQuery; one data byte
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(46986414, card.code);
        assertTrue((card.flags & QUERY_IS_HIDDEN) != 0);
    }

    @Test
    void trailingBytesInABlockDoNotShiftTheNextBlock() {
        ByteBuffer b = buf(12 + 10 + 6);
        b.putShort((short) 10); b.putInt(QUERY_REASON); b.putInt(1); b.putShort((short) 0x5555); // 2 bytes past the u32
        u32Block(b, QUERY_CODE, 46986414);
        endBlock(b);

        QueriedCard card = FieldQuery.parse(b.array());
        assertEquals(1, card.reason);
        assertEquals(46986414, card.code);
    }
}
```

- [ ] **Step 2: Run the new test class to see it fail**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.FieldQueryTest" --console=plain`
Expected: compilation of the test source fails (`FieldQuery.parse` does not exist).

- [ ] **Step 3: Replace `FieldQuery.java` with this content**

```java
package com.haxerus.duelcraft.duel.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Parses one card's answer to a native per-slot query ({@code OcgCore.nDuelQuery}): a run of
 * {@code [u16 size][u32 flag][data]} blocks, {@code size} counting the flag and the data, closed by a
 * {@code QUERY_END} block with no data (ygopro-core {@code card::get_infos}). Widths follow the engine:
 * {@code QUERY_RACE} is u64; {@code QUERY_OWNER}, {@code QUERY_IS_PUBLIC} and {@code QUERY_IS_HIDDEN}
 * are u8; every other scalar is u32. Each block is skipped to its declared size after reading, so a
 * field this class does not understand, or reads too narrowly, cannot shift the blocks after it.
 */
public final class FieldQuery {

    private FieldQuery() {}

    public static QueriedCard parse(byte[] data) {
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        var card = new QueriedCard();
        while (buf.remaining() >= 6) {
            int size = Short.toUnsignedInt(buf.getShort());
            int end = buf.position() + size;
            int flag = buf.getInt();
            if (flag == QUERY_END) break;
            card.flags |= flag;
            readField(buf, card, flag);
            buf.position(end);
        }
        return card;
    }

    private static void readField(ByteBuffer buf, QueriedCard card, int flag) {
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
            case QUERY_IS_PUBLIC    -> card.isPublic = buf.get() != 0;
            case QUERY_LSCALE       -> card.lscale = buf.getInt();
            case QUERY_RSCALE       -> card.rscale = buf.getInt();
            case QUERY_COVER        -> card.cover = buf.getInt();
            case QUERY_LINK -> {
                card.linkRating = buf.getInt();
                card.linkMarker = buf.getInt();
            }
            default -> { } // not requested by DuelSession; parse() skips the block by its size
        }
    }
}
```

- [ ] **Step 4: Point `DuelSession` at `FieldQuery.parse`**

In `sendFieldStats`, change `cards.add(parseSingleNativeQuery(data));` to `cards.add(FieldQuery.parse(data));`. Delete the whole `private static QueriedCard parseSingleNativeQuery(byte[] data)` method and the two blank lines after it. Remove `import java.nio.ByteBuffer;` and `import java.nio.ByteOrder;` (nothing else in the file uses them; keep the `QueriedCard` import, `sendFieldStats` still declares `List<QueriedCard>`).

- [ ] **Step 5: Run the new tests, then the whole suite**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.FieldQueryTest" --console=plain`
Expected: 5 tests pass.

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL` (`OcgCoreTest` exercises live `nDuelQuery` buffers through the new parser).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java \
        src/main/java/com/haxerus/duelcraft/duel/DuelSession.java \
        src/test/java/com/haxerus/duelcraft/duel/message/FieldQueryTest.java
git commit -m "FieldQuery owns the native query framing: u8 IS_PUBLIC, stop at QUERY_END, resync each block by size"
```

---

### Task 3: MessageParser reads the layouts ygopro-core writes

Three mismatches, all verified in the engine source:

1. `MSG_SELECT_CHAIN` (`playerop.cpp:477-491`): each entry is `[u32 code][loc_info][u64 desc][u8 mode]` where `loc_info` is `[u8 con][u8 loc][u32 seq][u32 pos]`, 23 bytes. `parseSelectChain` uses `ActivatableCard.read`, the 19-byte idle/battle layout without position (`playerop.cpp:29-34` and `:117-126` confirm those two prompts omit it). Every non-empty chain prompt drifts 4 bytes per entry; the second and later options carry garbage codes.
2. `MSG_SHUFFLE_EXTRA` (`field.cpp:967-971`): `[u8 player][u32 count][u32 code]*count`, the same as `MSG_SHUFFLE_HAND`. The parser reads one byte and relies on drift correction.
3. `MSG_PLAYER_HINT` (`field.cpp:1332-1342`): `[u8 player][u8 type][u64 desc]`. It is routed to `parseHint`, whose `MSG_HINT` layout is `[u8 type][u8 player][u64 desc]` (`libduel.cpp:3032-3035`), so the two bytes land swapped and `PHINT_DESC_ADD` (6) collides with `HINT_RACE` (6). No UI consumes player hints; it falls back to `Raw` until one does.

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java:78, 82, 294-302 (add a method after `parseShuffleHand`), 431-444`
- Test: `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java` (add three tests; helpers `body`, `msg`, `putLocInfo` already exist)

**Interfaces:**
- Consumes: `LocInfo.read(BufferReader)`, `BufferReader.readInt32/readInt64/readUint8/skip`, `DuelMessage.ActivatableCard` constructor, `DuelMessage.Raw(int type, byte[] body)`, constants `PHINT_DESC_ADD`, `POS_FACEDOWN_ATTACK`, `MSG_PLAYER_HINT`, `MSG_SHUFFLE_EXTRA`.
- Produces: nothing new; record shapes unchanged.

- [ ] **Step 1: Write the failing chain test**

Add to `MessageParserTest.java` directly after the existing `parseSelectChain` test:

```java
    @Test
    void parseSelectChain_entriesCarryLocInfoWithPosition() {
        // playerop.cpp: [u8 player][u8 specount][u8 forced][u32 hint0][u32 hint1][u32 count]
        // then per entry [u32 code][u8 con][u8 loc][u32 seq][u32 pos][u64 desc][u8 mode] = 23 bytes
        ByteBuffer b = body(3 + 4 + 4 + 4 + 2 * 23);
        b.put((byte) 1); b.put((byte) 0); b.put((byte) 0);
        b.putInt(0); b.putInt(0);
        b.putInt(2);
        b.putInt(89631139); putLocInfo(b, 1, LOCATION_MZONE, 2, POS_FACEUP_ATTACK); b.putLong((89631139L << 20) | 1); b.put((byte) 0);
        b.putInt(46986414); putLocInfo(b, 1, LOCATION_SZONE, 4, POS_FACEDOWN_ATTACK); b.putLong((46986414L << 20) | 2); b.put((byte) 1);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_SELECT_CHAIN, b.array()));
        assertEquals(1, msgs.size());
        var sc = (DuelMessage.SelectChain) msgs.getFirst();
        assertEquals(1, sc.player());
        assertFalse(sc.forced());
        assertEquals(2, sc.count());
        var second = sc.chains().get(1);
        assertEquals(46986414, second.code());
        assertEquals(LOCATION_SZONE, second.location());
        assertEquals(4, second.sequence());
        assertEquals((46986414L << 20) | 2, second.desc());
        assertEquals(1, second.flag());
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" --console=plain`
Expected: `parseSelectChain_entriesCarryLocInfoWithPosition` fails on the second entry's code (the 19-byte read misaligns it).

- [ ] **Step 3: Fix `parseSelectChain`**

Replace the entry loop in `parseSelectChain`:

```java
        List<DuelMessage.ActivatableCard> chains = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // playerop.cpp select_chain: [u32 code][loc_info with position][u64 desc][u8 mode]; the idle and
            // battle commands write their activatable entries without the position (ActivatableCard.read).
            int code = r.readInt32();
            LocInfo loc = LocInfo.read(r);
            long desc = r.readInt64();
            int flag = r.readUint8();
            chains.add(new DuelMessage.ActivatableCard(code, loc.controller(), loc.location(), loc.sequence(), desc, flag));
        }
```

- [ ] **Step 4: Run the parser tests, then commit**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" --console=plain`
Expected: all pass.

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "MessageParser: MSG_SELECT_CHAIN entries carry a full loc_info (23 bytes)"
```

- [ ] **Step 5: Write the shuffle and player-hint tests**

Add after the existing `parseShuffleHand` test:

```java
    @Test
    void parseShuffleExtra_consumesCountAndCodes() {
        // field.cpp: [u8 player][u32 count][u32 code]*count, same shape as MSG_SHUFFLE_HAND
        ByteBuffer b = body(1 + 4 + 4 * 2);
        b.put((byte) 1);
        b.putInt(2);
        b.putInt(7391448);
        b.putInt(29981921);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_SHUFFLE_EXTRA, b.array()));
        assertEquals(1, msgs.size());
        assertEquals(1, ((DuelMessage.ShuffleExtra) msgs.getFirst()).player());
    }
```

Add after the existing `parseHint` test:

```java
    @Test
    void playerHintStaysRaw() {
        // field.cpp: [u8 player][u8 type][u64 desc]; not MSG_HINT's [u8 type][u8 player][u64 desc]
        ByteBuffer b = body(10);
        b.put((byte) 0);
        b.put((byte) PHINT_DESC_ADD);
        b.putLong(1160L);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_PLAYER_HINT, b.array()));
        var raw = (DuelMessage.Raw) msgs.getFirst();
        assertEquals(MSG_PLAYER_HINT, raw.type());
        assertEquals(10, raw.body().length);
    }
```

- [ ] **Step 6: Run them**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" --console=plain`
Expected: `playerHintStaysRaw` fails with a `ClassCastException` (`Hint` is not `Raw`). `parseShuffleExtra_consumesCountAndCodes` already passes: drift correction realigns the frame today, so this test pins the layout rather than catching a wrong result; the observable change is that the `[Parse] msg type 39 drifted` warning disappears.

- [ ] **Step 7: Fix the two cases**

In the dispatch switch change `case MSG_SHUFFLE_EXTRA -> new DuelMessage.ShuffleExtra(reader.readUint8());` to:

```java
                case MSG_SHUFFLE_EXTRA -> parseShuffleExtra(reader);
```

Delete the line `case MSG_PLAYER_HINT   -> parseHint(reader);` and put this comment in its place:

```java
                // MSG_PLAYER_HINT ([u8 player][u8 type][u64 desc], field.cpp) stays Raw: its layout is not
                // MSG_HINT's and no UI consumes it yet.
```

Add after `parseShuffleHand`:

```java
    /** [u8 player][u32 count][u32 code]*count (field.cpp). The codes are skipped: the client treats the extra deck as an unordered pile. */
    private static DuelMessage.ShuffleExtra parseShuffleExtra(BufferReader r) {
        int player = r.readUint8();
        r.skip(4 * r.readInt32());
        return new DuelMessage.ShuffleExtra(player);
    }
```

- [ ] **Step 8: Run the whole suite, then commit**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL`.

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "MessageParser: consume MSG_SHUFFLE_EXTRA codes; leave MSG_PLAYER_HINT raw"
```

---

### Task 4: Deck errors name the player; docs follow the code

With the `Deck.standard()` fallback gone, `/duel accept` fails when either player has no deck, but the message goes only to the accepter and never says whose deck failed; the challenger never learns why the duel did not start. `/duel deck get` still mentions the removed fallback, and `CLAUDE.md` still names `QueryParser`, `QueryParserTest`, and the fallback. The engine checklist still lists the identical-hands bug as open.

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/DuelCommand.java:135-142, 222`
- Modify: `CLAUDE.md:106, 148, 168` (gitignored; edit the local file, do not stage it)
- Modify: `docs/engine-implementation-checklist.md:58, 133`

**Interfaces:**
- Consumes: `DuelManager.resolveDeck(ServerPlayer)` throwing `IOException | DeckLoader.DeckParseException`.
- Produces: nothing.

- [ ] **Step 1: Split the deck resolution in `accept`**

Replace

```java
        Deck challengerDeck;
        Deck accepterDeck;
        try {
            challengerDeck = DuelManager.get().resolveDeck(challenger);
            accepterDeck = DuelManager.get().resolveDeck(player);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Failed to load a deck: " + e.getMessage()));
            return 0;
        }
```

with

```java
        Deck challengerDeck;
        try {
            challengerDeck = DuelManager.get().resolveDeck(challenger);
        } catch (IOException | DeckLoader.DeckParseException e) {
            String who = challenger.getName().getString();
            player.sendSystemMessage(Component.literal(who + "'s deck could not be loaded: " + e.getMessage()));
            challenger.sendSystemMessage(Component.literal("Your deck could not be loaded, so "
                    + player.getName().getString() + " could not accept: " + e.getMessage()));
            return 0;
        }
        Deck accepterDeck;
        try {
            accepterDeck = DuelManager.get().resolveDeck(player);
        } catch (IOException | DeckLoader.DeckParseException e) {
            player.sendSystemMessage(Component.literal("Your deck could not be loaded: " + e.getMessage()));
            return 0;
        }
```

The invite intentionally stays pending: once the deck is fixed, `/duel accept` works without a new challenge.

- [ ] **Step 2: Fix the `deck get` text**

Change `.orElse("No current deck (using standard).")` to `.orElse("No deck set; run /duel deck set <name>.")`.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Update `CLAUDE.md`**

Line 106: replace `# BufferReader, QueryParser, QueriedCard, LocInfo` with `# BufferReader, FieldQuery (native per-slot query blocks), QueriedCard, LocInfo`, keeping the box-drawing prefix and alignment of that line.

Line 148: replace the whole numbered item with:

```
2. Decks come from `<gameDir>/duelcraft/decks/*.ydk` via `/duel deck list|set|get|clear` (re-read on every use); a player with no current deck cannot start or accept a duel. Both decks are shuffled Java-side before `setupDuel`, player 1 with the duel seed and player 2 (or the solo AI) with seed + 1 — ygopro-core never shuffles the starting deck; the engine seed only drives in-duel RNG. The seed is logged at duel start.
```

Line 168: replace `MessageParserTest, QueryParserTest, ResponseBuilderTest` with `MessageParserTest, FieldQueryTest, ResponseBuilderTest`.

- [ ] **Step 5: Update `docs/engine-implementation-checklist.md`**

Line 58: replace `- [ ] MSG_PLAYER_HINT (165) — Player-specific hint` with `- [ ] MSG_PLAYER_HINT (165) — Player-specific hint; parsed as Raw (layout [u8 player][u8 type][u64 desc], not MSG_HINT's)`.

Line 133: replace the whole bullet with:

```
- [x] **Identical decks produce identical opening hands.** Fixed 2026-09-07: `DuelManager` shuffles team 2 (and the solo AI deck) with `seed + 1`, so a duel stays reproducible from one seed while equal card lists diverge. `DeckShuffleTest.differentSeedsProduceDifferentMainOrder` covers the divergence.
```

Change nothing else in the file; `git diff docs/engine-implementation-checklist.md` should show only these two lines (the file's end-of-line normalization is silent).

- [ ] **Step 6: Verify no stale text remains, then commit**

Run: `grep -n "standard" src/main/java/com/haxerus/duelcraft/server/DuelCommand.java CLAUDE.md; grep -rn "QueryParser" CLAUDE.md src/`
Expected: no output.

```bash
git add src/main/java/com/haxerus/duelcraft/server/DuelCommand.java docs/engine-implementation-checklist.md
git commit -m "Deck errors name the player; docs follow the QueryParser removal and the seed + 1 shuffle"
```

`CLAUDE.md` is gitignored; its edit lives in the local file only and is never staged.

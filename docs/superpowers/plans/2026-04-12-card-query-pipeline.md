# Card Query Pipeline + On-Field Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forward card stat updates from the engine to the client so the duel UI can display current ATK/DEF and modified Level/Rank on field monsters.

**Architecture:** The engine already sends MSG_UPDATE_DATA (6) and MSG_UPDATE_CARD (7) in its message stream — they currently fall through to `DuelMessage.Raw`. We parse them using the existing `QueryParser`, serialize `QueriedCard` for network transport, sanitize face-down cards for the opponent, store per-zone stats in `ClientDuelState`, and render ATK/DEF + Level/Rank labels on monster zone slots.

**Tech Stack:** Existing QueryParser, DuelMessageCodec (FriendlyByteBuf), LDLib2 UI labels + LSS styling

---

## File Map

### Modified Files

| File | Changes |
|------|---------|
| `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java` | Add `UpdateData` and `UpdateCard` records |
| `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java` | Parse MSG_UPDATE_DATA (6) and MSG_UPDATE_CARD (7) |
| `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java` | Encode/decode UpdateData, UpdateCard, and QueriedCard fields |
| `src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java` | Sanitize face-down card stats for opponent; broadcast UpdateData/UpdateCard |
| `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java` | Add `mzoneStats`/`szoneStats` arrays, `FIELD_STATS` dirty flag, handle UpdateData/UpdateCard |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` | Render ATK/DEF + Level/Rank labels on monster zone slots |
| `src/main/resources/assets/duelcraft/ui/duel_screen.xml` | CSS for stat label positioning, font-size, color classes |
| `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java` | Tests for UPDATE_DATA/CARD parsing |

---

## Task 1: Add UpdateData and UpdateCard Records to DuelMessage

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java`

- [ ] **Step 1: Add the two new records**

Add these records to `DuelMessage.java` alongside the existing records. Place them after the lifecycle records (Start, Win) since they're state-update messages:

```java
    record UpdateData(int player, int location, List<QueriedCard> cards) implements DuelMessage {
        public int type() { return MSG_UPDATE_DATA; }
    }

    record UpdateCard(int player, int location, int sequence, QueriedCard card) implements DuelMessage {
        public int type() { return MSG_UPDATE_CARD; }
    }
```

Also add the import for `QueriedCard` if not already present (it's in the same package so no import needed).

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java
git commit -m "feat: add UpdateData and UpdateCard records to DuelMessage"
```

---

## Task 2: Parse MSG_UPDATE_DATA and MSG_UPDATE_CARD in MessageParser

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java`
- Test: `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java`

- [ ] **Step 1: Add parse cases to MessageParser**

In `MessageParser.java`, add two new cases in the `switch (msgType)` statement. Place them before the `default` case. The reader is positioned after the type byte.

For MSG_UPDATE_DATA (type 6):
```java
case MSG_UPDATE_DATA -> {
    int player = reader.readUint8();
    int location = reader.readUint8();
    byte[] queryData = new byte[reader.remaining()];
    System.arraycopy(buffer, reader.position(), queryData, 0, queryData.length);
    var cards = QueryParser.parseLocation(queryData);
    yield new DuelMessage.UpdateData(player, location, cards);
}
```

For MSG_UPDATE_CARD (type 7):
```java
case MSG_UPDATE_CARD -> {
    int player = reader.readUint8();
    int location = reader.readUint8();
    int sequence = reader.readUint8();
    byte[] queryData = new byte[reader.remaining()];
    System.arraycopy(buffer, reader.position(), queryData, 0, queryData.length);
    var card = QueryParser.parseSingle(queryData);
    yield new DuelMessage.UpdateCard(player, location, sequence,
            card != null ? card : new QueriedCard());
}
```

Note: `reader.position()` gives the current byte offset. The remaining bytes are the query buffer that QueryParser can parse. Check the `BufferReader` API — if `position()` isn't available, use `buffer.length - reader.remaining()`.

- [ ] **Step 2: Add test for MSG_UPDATE_DATA parsing**

In `MessageParserTest.java`, add a test that constructs a binary payload matching the MSG_UPDATE_DATA format. The query data embedded should use the standard format that QueryParser handles:

```java
@Test
void parseUpdateData_monsterZone() {
    // Build a MSG_UPDATE_DATA for player 0, LOCATION_MZONE
    // with one card: code=89631139, ATK=3000, DEF=2500, baseATK=3000, baseDEF=2500
    var buf = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
    buf.put((byte) MSG_UPDATE_DATA);  // msg type
    buf.put((byte) 0);                // player
    buf.put((byte) LOCATION_MZONE);   // location

    // Query block for one card (standard query format):
    // [u32 total_size] then [u32 field_size][field_data] per field
    int startPos = buf.position();
    buf.putInt(0); // placeholder for total size

    // QUERY_CODE
    buf.putInt(8); // field_size = 4 (header) + 4 (int32)
    buf.putInt(89631139);

    // QUERY_ATTACK
    buf.putInt(8);
    buf.putInt(3000);

    // QUERY_DEFENSE
    buf.putInt(8);
    buf.putInt(2500);

    // QUERY_BASE_ATTACK
    buf.putInt(8);
    buf.putInt(3000);

    // QUERY_BASE_DEFENSE
    buf.putInt(8);
    buf.putInt(2500);

    // Write total size
    int totalSize = buf.position() - startPos - 4;
    buf.putInt(startPos, totalSize);

    byte[] data = new byte[buf.position()];
    buf.flip();
    buf.get(data);

    var messages = MessageParser.parse(data);
    assertFalse(messages.isEmpty());
    var parsed = messages.getFirst().message();
    assertInstanceOf(DuelMessage.UpdateData.class, parsed);
    var update = (DuelMessage.UpdateData) parsed;
    assertEquals(0, update.player());
    assertEquals(LOCATION_MZONE, update.location());
    assertFalse(update.cards().isEmpty());

    var card = update.cards().getFirst();
    assertEquals(89631139, card.code);
    assertEquals(3000, card.attack);
    assertEquals(2500, card.defense);
}
```

IMPORTANT: Check how the existing `MessageParserTest` constructs test data. The test above follows the QueryParser's expected format. The `MessageParser.parse()` returns `List<ParsedEntry>` where each entry has a `.message()` accessor. Verify this matches the actual API before implementing.

- [ ] **Step 3: Run tests to verify parsing works**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" 2>&1 | tail -10`
Expected: All tests PASS including the new one

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "feat: parse MSG_UPDATE_DATA and MSG_UPDATE_CARD in MessageParser"
```

---

## Task 3: Serialize QueriedCard in DuelMessageCodec

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java`

- [ ] **Step 1: Add QueriedCard write/read helpers**

Add these private helper methods to `DuelMessageCodec.java`:

```java
private static void writeQueriedCard(FriendlyByteBuf buf, QueriedCard card) {
    buf.writeInt(card.flags);
    buf.writeInt(card.code);
    buf.writeInt(card.position);
    buf.writeInt(card.type);
    buf.writeInt(card.level);
    buf.writeInt(card.rank);
    buf.writeInt(card.attribute);
    buf.writeLong(card.race);
    buf.writeInt(card.attack);
    buf.writeInt(card.defense);
    buf.writeInt(card.baseAttack);
    buf.writeInt(card.baseDefense);
    buf.writeInt(card.status);
    buf.writeInt(card.linkRating);
    buf.writeInt(card.linkMarker);
    buf.writeBoolean(card.isPublic);
}

private static QueriedCard readQueriedCard(FriendlyByteBuf buf) {
    var card = new QueriedCard();
    card.flags = buf.readInt();
    card.code = buf.readInt();
    card.position = buf.readInt();
    card.type = buf.readInt();
    card.level = buf.readInt();
    card.rank = buf.readInt();
    card.attribute = buf.readInt();
    card.race = buf.readLong();
    card.attack = buf.readInt();
    card.defense = buf.readInt();
    card.baseAttack = buf.readInt();
    card.baseDefense = buf.readInt();
    card.status = buf.readInt();
    card.linkRating = buf.readInt();
    card.linkMarker = buf.readInt();
    card.isPublic = buf.readBoolean();
    return card;
}

private static void writeQueriedCardList(FriendlyByteBuf buf, List<QueriedCard> cards) {
    buf.writeInt(cards.size());
    for (QueriedCard card : cards) {
        writeQueriedCard(buf, card);
    }
}

private static List<QueriedCard> readQueriedCardList(FriendlyByteBuf buf) {
    int count = buf.readInt();
    var cards = new ArrayList<QueriedCard>(count);
    for (int i = 0; i < count; i++) {
        cards.add(readQueriedCard(buf));
    }
    return cards;
}
```

- [ ] **Step 2: Add encode cases for UpdateData and UpdateCard**

In the `encode` method's switch statement, add:

```java
case DuelMessage.UpdateData m -> {
    buf.writeByte(m.player());
    buf.writeByte(m.location());
    writeQueriedCardList(buf, m.cards());
}
case DuelMessage.UpdateCard m -> {
    buf.writeByte(m.player());
    buf.writeByte(m.location());
    buf.writeByte(m.sequence());
    writeQueriedCard(buf, m.card());
}
```

- [ ] **Step 3: Add decode cases for UpdateData and UpdateCard**

In the `decode` method's switch statement, add:

```java
case MSG_UPDATE_DATA -> new DuelMessage.UpdateData(
        buf.readByte(), buf.readByte(), readQueriedCardList(buf));
case MSG_UPDATE_CARD -> new DuelMessage.UpdateCard(
        buf.readByte(), buf.readByte(), buf.readByte(), readQueriedCard(buf));
```

- [ ] **Step 4: Verify compilation and run existing tests**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

Run: `./gradlew test 2>&1 | grep -E "tests completed|FAILED"`
Expected: No new failures

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java
git commit -m "feat: serialize QueriedCard in DuelMessageCodec for network transport"
```

---

## Task 4: Sanitize Face-Down Stats in ServerDuelHandler

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java`

- [ ] **Step 1: Add sanitizeCard helper**

Add a private method that creates a sanitized copy of a QueriedCard for face-down cards sent to the opponent:

```java
/**
 * Strip private fields from a QueriedCard for the opponent.
 * Face-down cards only reveal position and isPublic.
 * If isPublic is true, all data is sent regardless of position.
 */
private static QueriedCard sanitizeCard(QueriedCard card) {
    if (card == null) return null;
    boolean faceDown = (card.position & OcgConstants.POS_FACEDOWN) != 0;
    if (!faceDown || card.isPublic) return card;

    // Face-down, not public: strip everything except position
    var sanitized = new QueriedCard();
    sanitized.flags = card.flags;
    sanitized.position = card.position;
    return sanitized;
}
```

- [ ] **Step 2: Add hideInfo cases for UpdateData and UpdateCard**

In the `hideInfo` method, add cases before the `default`:

```java
case DuelMessage.UpdateData upd -> {
    if (upd.player() != recipient) {
        // Opponent's cards: sanitize each card
        var sanitized = upd.cards().stream()
                .map(ServerDuelHandler::sanitizeCard)
                .toList();
        yield new DuelMessage.UpdateData(upd.player(), upd.location(), sanitized);
    }
    yield upd;
}
case DuelMessage.UpdateCard upd -> {
    if (upd.player() != recipient) {
        yield new DuelMessage.UpdateCard(upd.player(), upd.location(),
                upd.sequence(), sanitizeCard(upd.card()));
    }
    yield upd;
}
```

Note: Check the exact signature of `hideInfo`. It takes `(DuelMessage msg, int recipient)` and returns `DuelMessage`. It uses a switch expression with `yield`. Match the existing pattern.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java
git commit -m "feat: sanitize face-down card stats for opponent in ServerDuelHandler"
```

---

## Task 5: Store Stats in ClientDuelState

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java`

- [ ] **Step 1: Add FIELD_STATS dirty flag**

In the `DirtyFlag` enum, add `FIELD_STATS`:

```java
public enum DirtyFlag {
    LP, TURN_PHASE,
    HAND_0, HAND_1,
    MZONE_0, MZONE_1,
    SZONE_0, SZONE_1,
    PILE_COUNTS, CHAIN, PROMPT, WINNER,
    FIELD_STATS
}
```

- [ ] **Step 2: Add per-zone stat storage**

Add these fields alongside the existing `mzone`/`szone` arrays:

```java
// Per-zone card stats (updated by MSG_UPDATE_DATA/CARD)
public final QueriedCard[][] mzoneStats = new QueriedCard[2][7];
public final QueriedCard[][] szoneStats = new QueriedCard[2][6];
```

Add the import for `QueriedCard`:
```java
import com.haxerus.duelcraft.duel.message.QueriedCard;
```

- [ ] **Step 3: Handle UpdateData and UpdateCard in applyMessage**

Add these cases to the `applyMessage` switch:

```java
case DuelMessage.UpdateData upd -> {
    var cards = upd.cards();
    if (upd.location() == LOCATION_MZONE) {
        for (int i = 0; i < Math.min(cards.size(), mzoneStats[upd.player()].length); i++) {
            mzoneStats[upd.player()][i] = cards.get(i);
        }
    } else if (upd.location() == LOCATION_SZONE) {
        for (int i = 0; i < Math.min(cards.size(), szoneStats[upd.player()].length); i++) {
            szoneStats[upd.player()][i] = cards.get(i);
        }
    }
    dirtyFlags.add(DirtyFlag.FIELD_STATS);
}
case DuelMessage.UpdateCard upd -> {
    if (upd.location() == LOCATION_MZONE && upd.sequence() < mzoneStats[upd.player()].length) {
        mzoneStats[upd.player()][upd.sequence()] = upd.card();
    } else if (upd.location() == LOCATION_SZONE && upd.sequence() < szoneStats[upd.player()].length) {
        szoneStats[upd.player()][upd.sequence()] = upd.card();
    }
    dirtyFlags.add(DirtyFlag.FIELD_STATS);
}
```

- [ ] **Step 4: Clear stats when cards move out of zones**

In the existing `applyMove` method (or wherever card zone data is cleared on Move), add:

```java
// Clear stats for the source zone
if (from.location() == LOCATION_MZONE && from.sequence() < mzoneStats[from.controller()].length) {
    mzoneStats[from.controller()][from.sequence()] = null;
}
if (from.location() == LOCATION_SZONE && from.sequence() < szoneStats[from.controller()].length) {
    szoneStats[from.controller()][from.sequence()] = null;
}
```

Find the `applyMove` method by searching for where `mzone[...]` is set to 0 on card departure. Add the stat clearing alongside.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java
git commit -m "feat: store per-zone card stats in ClientDuelState from UpdateData/UpdateCard"
```

---

## Task 6: Display ATK/DEF and Level/Rank on Monster Zones

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml`

- [ ] **Step 1: Add CSS classes for stat labels**

In `duel_screen.xml`, add these styles (inside the `<style>` block, after the existing `.card-back` style):

```css
/* ── Monster stat labels ── */
.stat-atk-def {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    font-size: 5;
    horizontal-align: center;
    background: rect(#cc000000);
    text-color: #FFFFFF;
}

.stat-level {
    position: absolute;
    top: 0;
    right: 0;
    font-size: 5;
    background: rect(#cc000000);
    text-color: #FFFFFF;
    padding-horizontal: 1;
}

.stat-buffed {
    text-color: #55FF55;
}

.stat-debuffed {
    text-color: #FF5555;
}
```

- [ ] **Step 2: Add FIELD_STATS handling in onTick**

In the `onTick` method, add after the SZONE dirty flag handling:

```java
if (flags.contains(DirtyFlag.FIELD_STATS))
    refreshFieldStats();
```

- [ ] **Step 3: Implement refreshFieldStats method**

Add this method to the UIRefresher class (after `refreshSpellZones`):

```java
private void refreshFieldStats() {
    for (int p = 0; p < 2; p++) {
        for (int i = 0; i < 5; i++) {
            updateMonsterStats(monsterSlots[p][i], state.mzoneStats[p][i], state.mzone[p][i]);
        }
    }
    // EMZ slots
    updateMonsterStats(emzSlots[0], state.mzoneStats[0][5], state.mzone[0][5]);
    updateMonsterStats(emzSlots[1], state.mzoneStats[1][5], state.mzone[1][5]);
}

private void updateMonsterStats(UIElement slot, QueriedCard stats, int code) {
    if (slot == null) return;

    // Remove old stat labels
    slot.getChildren().stream()
            .filter(c -> c.hasClass("stat-atk-def") || c.hasClass("stat-level"))
            .toList()
            .forEach(slot::removeChild);

    if (code == 0 || stats == null) return;

    boolean faceDown = (stats.position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
    if (faceDown) return;

    boolean isMonster = (stats.flags & QUERY_ATTACK) != 0;
    if (!isMonster) return;

    // ATK/DEF label
    var atkDefLabel = new Label();
    atkDefLabel.addClass("stat-atk-def");
    String atkText = stats.attack == -2 ? "?" : String.valueOf(stats.attack);
    String defText;
    if (stats.linkRating > 0) {
        defText = "L" + stats.linkRating;
    } else {
        defText = stats.defense == -2 ? "?" : String.valueOf(stats.defense);
    }
    atkDefLabel.setText(Component.literal(atkText + "/" + defText));

    // Color the label based on buff/debuff
    // Use the more impactful change (ATK takes priority for color)
    if (stats.baseAttack > 0) {
        if (stats.attack > stats.baseAttack) atkDefLabel.addClass("stat-buffed");
        else if (stats.attack < stats.baseAttack) atkDefLabel.addClass("stat-debuffed");
    }
    slot.addChild(atkDefLabel);

    // Level/Rank label — only if modified from original
    CardDatabase db = DuelcraftClient.getCardDatabase();
    CardInfo cardInfo = db != null ? db.getCard(code) : null;
    if (cardInfo != null && cardInfo.isMonster() && !cardInfo.isLink()) {
        int originalLevel = cardInfo.levelOrRank();
        int currentLevel = stats.rank > 0 ? stats.rank : stats.level;
        if (currentLevel != originalLevel && currentLevel > 0) {
            var levelLabel = new Label();
            levelLabel.addClass("stat-level");
            boolean isXyz = cardInfo.isXyz();
            levelLabel.setText(Component.literal((isXyz ? "R" : "\u2605") + currentLevel));
            if (currentLevel > originalLevel) levelLabel.addClass("stat-buffed");
            else if (currentLevel < originalLevel) levelLabel.addClass("stat-debuffed");
            slot.addChild(levelLabel);
        }
    }
}
```

Add the necessary imports at the top of LDLibDuelScreen.java:
```java
import com.haxerus.duelcraft.duel.message.QueriedCard;
```

Also import `QUERY_ATTACK` from OcgConstants (already star-imported via `import static com.haxerus.duelcraft.core.OcgConstants.*;`).

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | grep -E "tests completed|FAILED"`
Expected: Only the pre-existing `selectUnselectCardFinish` failure

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java \
        src/main/resources/assets/duelcraft/ui/duel_screen.xml
git commit -m "feat: display ATK/DEF and modified Level/Rank on monster zone slots"
```

---

## Task 7: Manual Verification

- [ ] **Step 1: Run the client**

Run: `./gradlew runClient`

1. Start a solo duel with `/duel test`
2. Summon a monster — verify ATK/DEF label appears at the bottom of the zone slot
3. Check color coding: summon a monster, use an ATK-modifying effect if possible
4. Check that face-down set monsters do NOT show stats
5. Verify opponent's face-down monsters don't leak stats

- [ ] **Step 2: Verify Level/Rank display**

1. Use an effect that modifies a monster's level (if testable)
2. The Level label should appear in the top-right only when modified
3. Verify XYZ monsters show "R4" format, level monsters show "★4"

- [ ] **Step 3: Verify card info banner still works**

1. Hover face-up monsters — card info banner should still show full details
2. Hover hand cards — same behavior
3. Click piles — zone inspector should still work

- [ ] **Step 4: Commit any polish fixes**

```bash
git add -u
git commit -m "fix: polish on-field stats rendering"
```

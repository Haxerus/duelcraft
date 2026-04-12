# Card Query Pipeline + On-Field Stats Design

**Goal:** Forward card stat updates from the engine to the client so the duel UI can display current ATK/DEF and modified Level/Rank on field monsters.

## Data Flow

```
Engine → MSG_UPDATE_DATA/CARD in message stream
    ↓
MessageParser → DuelMessage.UpdateData / UpdateCard
    (existing QueryParser parses embedded query buffer)
    ↓
ServerDuelHandler → sanitize per-client
    (face-down: zero private fields per edopro mask)
    ↓
DuelMessageCodec → serialize QueriedCard fields
    ↓
ClientDuelState → store per-zone QueriedCard
    ↓
LDLibDuelScreen → ATK/DEF + Level/Rank labels on monster slots
```

## Message Formats

### MSG_UPDATE_DATA (6) — Bulk update for all cards in a location

```
[u8]  player
[u8]  location
[...] QueryStream: sequence of per-card query blocks
      (same format as QueryParser.parseLocation)
```

### MSG_UPDATE_CARD (7) — Single card update

```
[u8]  player
[u8]  location
[u8]  sequence
[...] Single card query block
      (same format as QueryParser.parseSingle)
```

The embedded query data uses the standard ygopro-core query format that QueryParser already handles. No new binary parsing needed — just read the header bytes and delegate to QueryParser.

## Server Side

### MessageParser

New cases for MSG_UPDATE_DATA (6) and MSG_UPDATE_CARD (7):

```java
record UpdateData(int player, int location, List<QueriedCard> cards) implements DuelMessage
record UpdateCard(int player, int location, int sequence, QueriedCard card) implements DuelMessage
```

- UPDATE_DATA: read player (u8) + location (u8), pass remaining bytes to `QueryParser.parseLocation()`
- UPDATE_CARD: read player (u8) + location (u8) + sequence (u8), pass remaining bytes to `QueryParser.parseSingle()`

### ServerDuelHandler — Sanitization

For the opponent's copy of UPDATE_DATA/CARD, sanitize face-down cards by zeroing private fields. Following edopro's `private_queries` mask:

```
QUERY_CODE | QUERY_ALIAS | QUERY_TYPE | QUERY_LEVEL | QUERY_RANK |
QUERY_ATTRIBUTE | QUERY_RACE | QUERY_ATTACK | QUERY_DEFENSE |
QUERY_BASE_ATTACK | QUERY_BASE_DEFENSE | QUERY_STATUS |
QUERY_LSCALE | QUERY_RSCALE | QUERY_LINK
```

A face-down card's QueriedCard is replaced with one that only has position and isPublic set. If `isPublic` is true (card revealed by effect), send full data regardless of position.

### DuelMessageCodec

Serialize/deserialize QueriedCard for network transport. Fields to include: flags, code, position, type, level, rank, attribute, race, attack, defense, baseAttack, baseDefense, status, linkRating, linkMarker. Counters, equip, and target info deferred to later work.

## Client Side

### ClientDuelState

New per-zone stat storage:

```java
public final QueriedCard[][] mzoneStats = new QueriedCard[2][7];
public final QueriedCard[][] szoneStats = new QueriedCard[2][6];
```

`applyMessage` handles:
- `UpdateData` — overwrite all stats for the given player+location
- `UpdateCard` — overwrite stats for specific player+location+sequence
- Existing `Move` handling clears stats when a card leaves a zone

New dirty flag: `FIELD_STATS` — set when mzoneStats/szoneStats change.

### LDLibDuelScreen — Monster Stat Overlay

Each monster zone slot gets stat labels, updated on `FIELD_STATS` dirty flag:

**ATK/DEF label** — bottom of slot
- Format: `2500/2000`
- Link monsters: `2300/L3` (ATK + Link rating, no DEF)
- `?` ATK/DEF shown as `?`
- Color per stat independently:
  - White: current == base (or base unknown)
  - Green: current > base (buffed)
  - Red: current < base (debuffed)

**Level/Rank label** — top-right of slot
- Only shown when current level/rank differs from the card's original (looked up via CardDatabase from the card data pipeline)
- Format: `★N` for level, `RN` for rank
- Color: green if raised, red if lowered vs original

Labels only shown for face-up monsters. Hidden when slot is empty or card is face-down.

## Files Changed

### New
- None (all changes to existing files)

### Modified
- `MessageParser.java` — parse MSG_UPDATE_DATA/CARD
- `DuelMessage.java` — add UpdateData, UpdateCard records
- `DuelMessageCodec.java` — serialize/deserialize QueriedCard
- `ServerDuelHandler.java` — sanitize face-down stats for opponent
- `ClientDuelState.java` — mzoneStats/szoneStats arrays, FIELD_STATS dirty flag
- `LDLibDuelScreen.java` — stat overlay rendering on monster zones
- `duel_screen.xml` — CSS for stat labels (positioning, font-size, color classes)

## Testing

- **QueryParser** — already has 123 passing tests for query parsing
- **MessageParser** — add tests for UPDATE_DATA/CARD parsing with known binary payloads
- **DuelMessageCodec** — add round-trip tests for UpdateData/UpdateCard serialization
- **Manual verification** — runClient, summon monsters, check ATK/DEF display, use stat-modifying effects, verify color coding

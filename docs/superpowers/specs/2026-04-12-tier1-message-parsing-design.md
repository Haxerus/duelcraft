# Tier 1 Message Parsing Design

**Goal:** Parse three missing Tier 1 messages so players can see revealed cards and RPS results.

## Messages

### MSG_CONFIRM_DECKTOP (30) — Revealed deck top cards

**Binary format (after type byte):**
```
[u8 player]
[u32 count]
Per card (× count):
  [u32 code]
  [u8 controller]
  [u8 location]
  [u32 sequence]
```

**Client handling:** Show the revealed cards in the zone inspector panel with title "Revealed Cards". Each card gets a card-slot entry with card image and hover-to-inspect, reusing the existing `handlePileClick` inspector pattern. Inspector opens automatically (no click needed — the engine is telling us to look at these cards).

**Broadcast:** Both players.

### MSG_CONFIRM_CARDS (31) — Confirmed/revealed cards

**Binary format:** Identical to CONFIRM_DECKTOP.

**Client handling:** Same approach — zone inspector with title "Confirmed Cards". Same display logic. Can share the same DuelMessage record type or use a separate one with a different title.

**Broadcast:** Both players.

### MSG_HAND_RES (133) — RPS result

**Binary format (after type byte):**
```
[u8 result]
  bits 0-1: player 0's choice (1=Rock, 2=Paper, 3=Scissors)
  bits 2-3: player 1's choice (same encoding)
```

**Client handling:** Show result via the status label overlay for a few seconds. Format: "Rock vs Scissors" or similar. The existing status label (`#status-label`) can display this — it's already used for chain count and waiting messages.

**Broadcast:** Both players.

## Deferred

**MSG_SWAP_GRAVE_DECK (35)** — Requires deck card tracking (ClientDuelState currently only stores deckCount, not individual cards). Deferred to a future session.

## DuelMessage Records

```java
record ConfirmCards(int player, List<CardLocation> cards) implements DuelMessage {
    // Used for both MSG_CONFIRM_DECKTOP and MSG_CONFIRM_CARDS
    record CardLocation(int code, int controller, int location, int sequence) {}
}
record HandResult(int hand0, int hand1) implements DuelMessage {
    // hand values: 1=Rock, 2=Paper, 3=Scissors
}
```

CONFIRM_DECKTOP and CONFIRM_CARDS share the same format and display. They can use the same record type with a `type()` that returns the original message type (30 or 31) to distinguish them for the title.

## Files Modified

- `DuelMessage.java` — add ConfirmCards (with inner CardLocation), HandResult records
- `MessageParser.java` — add cases for MSG_CONFIRM_DECKTOP (30), MSG_CONFIRM_CARDS (31), MSG_HAND_RES (133)
- `DuelMessageCodec.java` — encode/decode the new records
- `ClientDuelState.java` — store pending confirm cards list + RPS result, add dirty flags
- `LDLibDuelScreen.java` — auto-open inspector for confirm cards, show RPS result in status label

## Testing

- MessageParser tests for all three message types with known binary payloads
- Manual verification via runClient

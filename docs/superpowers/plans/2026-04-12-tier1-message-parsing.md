# Tier 1 Message Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse MSG_CONFIRM_DECKTOP, MSG_CONFIRM_CARDS, and MSG_HAND_RES so players can see revealed cards and RPS results.

**Architecture:** Follow the existing message pipeline pattern: add records to DuelMessage, parse in MessageParser, encode/decode in DuelMessageCodec, handle in ClientDuelState, display in LDLibDuelScreen. Confirm messages auto-open the zone inspector. RPS result shown in the status label.

**Tech Stack:** Existing MessageParser/DuelMessageCodec/ClientDuelState/LDLib2 UI

---

## File Map

| File | Changes |
|------|---------|
| `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java` | Add ConfirmDeckTop, ConfirmCards, HandResult records + ConfirmCard inner record |
| `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java` | Parse cases for MSG_CONFIRM_DECKTOP (30), MSG_CONFIRM_CARDS (31), MSG_HAND_RES (133) |
| `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java` | Encode/decode for all three new records |
| `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java` | Store confirm card lists, add CONFIRM dirty flag, handle HandResult |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` | Auto-open inspector for confirms, show RPS result in status label |
| `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java` | Tests for all three message types |

---

## Task 1: Add DuelMessage Records + DuelMessageCodec

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java`
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java`

These must be done together because the sealed interface's exhaustive switch in the codec requires all records to have encode/decode cases.

- [ ] **Step 1: Add records to DuelMessage.java**

Add an inner record for card entries in confirm messages (similar to CardInfo but without position):

```java
    record ConfirmCard(int code, int controller, int location, int sequence) {
        public static ConfirmCard read(BufferReader reader) {
            return new ConfirmCard(
                reader.readInt32(),
                reader.readUint8(),
                reader.readUint8(),
                reader.readInt32()
            );
        }
    }
```

Add three new message records:

```java
    record ConfirmDeckTop(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_DECKTOP; }
    }

    record ConfirmCards(int player, List<ConfirmCard> cards) implements DuelMessage {
        public int type() { return MSG_CONFIRM_CARDS; }
    }

    record HandResult(int hand0, int hand1) implements DuelMessage {
        public int type() { return MSG_HAND_RES; }
    }
```

- [ ] **Step 2: Add encode/decode to DuelMessageCodec.java**

Add helper methods for ConfirmCard serialization:

```java
    private static void writeConfirmCard(FriendlyByteBuf buf, DuelMessage.ConfirmCard card) {
        buf.writeInt(card.code());
        buf.writeByte(card.controller());
        buf.writeByte(card.location());
        buf.writeInt(card.sequence());
    }

    private static DuelMessage.ConfirmCard readConfirmCard(FriendlyByteBuf buf) {
        return new DuelMessage.ConfirmCard(buf.readInt(), buf.readByte(), buf.readByte(), buf.readInt());
    }

    private static void writeConfirmCardList(FriendlyByteBuf buf, List<DuelMessage.ConfirmCard> list) {
        buf.writeInt(list.size());
        for (var c : list) writeConfirmCard(buf, c);
    }

    private static List<DuelMessage.ConfirmCard> readConfirmCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        var list = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) list.add(readConfirmCard(buf));
        return list;
    }
```

Add encode cases:

```java
    case DuelMessage.ConfirmDeckTop m -> { buf.writeByte(m.player()); writeConfirmCardList(buf, m.cards()); }
    case DuelMessage.ConfirmCards m -> { buf.writeByte(m.player()); writeConfirmCardList(buf, m.cards()); }
    case DuelMessage.HandResult m -> { buf.writeByte(m.hand0()); buf.writeByte(m.hand1()); }
```

Add decode cases:

```java
    case MSG_CONFIRM_DECKTOP -> new DuelMessage.ConfirmDeckTop(buf.readByte(), readConfirmCardList(buf));
    case MSG_CONFIRM_CARDS -> new DuelMessage.ConfirmCards(buf.readByte(), readConfirmCardList(buf));
    case MSG_HAND_RES -> new DuelMessage.HandResult(buf.readByte(), buf.readByte());
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java \
        src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java
git commit -m "feat: add ConfirmDeckTop, ConfirmCards, HandResult records and codec"
```

---

## Task 2: Parse in MessageParser + Tests

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java`
- Modify: `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java`

- [ ] **Step 1: Add parser methods and switch cases**

Add parser methods:

```java
    private static DuelMessage.ConfirmDeckTop parseConfirmDeckTop(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        var cards = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.ConfirmCard.read(r));
        }
        return new DuelMessage.ConfirmDeckTop(player, cards);
    }

    private static DuelMessage.ConfirmCards parseConfirmCards(BufferReader r) {
        int player = r.readUint8();
        int count = r.readInt32();
        var cards = new ArrayList<DuelMessage.ConfirmCard>(count);
        for (int i = 0; i < count; i++) {
            cards.add(DuelMessage.ConfirmCard.read(r));
        }
        return new DuelMessage.ConfirmCards(player, cards);
    }

    private static DuelMessage.HandResult parseHandResult(BufferReader r) {
        int result = r.readUint8();
        int hand0 = result & 0x3;
        int hand1 = (result >> 2) & 0x3;
        return new DuelMessage.HandResult(hand0, hand1);
    }
```

Add switch cases (before the `default` case, after existing info messages):

```java
    case MSG_CONFIRM_DECKTOP -> parseConfirmDeckTop(reader);
    case MSG_CONFIRM_CARDS   -> parseConfirmCards(reader);
    case MSG_HAND_RES        -> parseHandResult(reader);
```

- [ ] **Step 2: Add tests**

Add tests to `MessageParserTest.java`:

```java
    // ---- Confirm / Reveal Messages ----

    @Test
    void parseConfirmDeckTop() {
        ByteBuffer b = body(32);
        b.put((byte) 0);       // player
        b.putInt(2);            // count
        // Card 1: code=89631139, controller=0, location=DECK, sequence=39
        b.putInt(89631139);
        b.put((byte) 0);
        b.put((byte) LOCATION_DECK);
        b.putInt(39);
        // Card 2: code=46986414, controller=0, location=DECK, sequence=38
        b.putInt(46986414);
        b.put((byte) 0);
        b.put((byte) LOCATION_DECK);
        b.putInt(38);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_CONFIRM_DECKTOP, b.array()));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.ConfirmDeckTop.class, msgs.getFirst());
        var confirm = (DuelMessage.ConfirmDeckTop) msgs.getFirst();
        assertEquals(0, confirm.player());
        assertEquals(2, confirm.cards().size());
        assertEquals(89631139, confirm.cards().get(0).code());
        assertEquals(46986414, confirm.cards().get(1).code());
    }

    @Test
    void parseConfirmCards() {
        ByteBuffer b = body(16);
        b.put((byte) 1);       // player
        b.putInt(1);            // count
        b.putInt(12580477);     // code
        b.put((byte) 1);       // controller
        b.put((byte) LOCATION_HAND);
        b.putInt(0);            // sequence

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_CONFIRM_CARDS, b.array()));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.ConfirmCards.class, msgs.getFirst());
        var confirm = (DuelMessage.ConfirmCards) msgs.getFirst();
        assertEquals(1, confirm.player());
        assertEquals(1, confirm.cards().size());
        assertEquals(12580477, confirm.cards().getFirst().code());
    }

    @Test
    void parseHandResult() {
        // Player 0 chose Rock (1), Player 1 chose Scissors (3)
        // result byte: (3 << 2) | 1 = 0b1101 = 13
        ByteBuffer b = body(1);
        b.put((byte) 13);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_HAND_RES, b.array()));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.HandResult.class, msgs.getFirst());
        var res = (DuelMessage.HandResult) msgs.getFirst();
        assertEquals(1, res.hand0());  // Rock
        assertEquals(3, res.hand1());  // Scissors
    }
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" 2>&1 | tail -5`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "feat: parse MSG_CONFIRM_DECKTOP, MSG_CONFIRM_CARDS, MSG_HAND_RES"
```

---

## Task 3: Handle in ClientDuelState

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java`

- [ ] **Step 1: Add CONFIRM dirty flag and state fields**

Add `CONFIRM` to the DirtyFlag enum:

```java
    public enum DirtyFlag {
        LP, TURN_PHASE,
        HAND_0, HAND_1,
        MZONE_0, MZONE_1,
        SZONE_0, SZONE_1,
        PILE_COUNTS, CHAIN, PROMPT, WINNER,
        FIELD_STATS, CONFIRM
    }
```

Add fields to store the pending confirm data and RPS result:

```java
    // Confirm/reveal card display
    public String confirmTitle;
    public List<DuelMessage.ConfirmCard> confirmCards;

    // RPS result display
    public int rpsHand0;
    public int rpsHand1;
```

- [ ] **Step 2: Handle messages in applyMessage**

Add cases for the three messages:

```java
    // ---- Confirm/reveal ----
    case DuelMessage.ConfirmDeckTop confirm -> {
        confirmTitle = "Revealed Cards";
        confirmCards = confirm.cards();
        dirtyFlags.add(DirtyFlag.CONFIRM);
        LOGGER.debug("[State] ConfirmDeckTop: player={}, cards={}", confirm.player(), confirm.cards().size());
    }
    case DuelMessage.ConfirmCards confirm -> {
        confirmTitle = "Confirmed Cards";
        confirmCards = confirm.cards();
        dirtyFlags.add(DirtyFlag.CONFIRM);
        LOGGER.debug("[State] ConfirmCards: player={}, cards={}", confirm.player(), confirm.cards().size());
    }
    case DuelMessage.HandResult res -> {
        rpsHand0 = res.hand0();
        rpsHand1 = res.hand1();
        dirtyFlags.add(DirtyFlag.CHAIN); // Reuse CHAIN flag to trigger status label update
        LOGGER.debug("[State] HandResult: hand0={}, hand1={}", res.hand0(), res.hand1());
    }
```

Note: HandResult reuses `DirtyFlag.CHAIN` because `updateStatusLabel` is already triggered by CHAIN and can be extended to show RPS results.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java
git commit -m "feat: handle ConfirmDeckTop, ConfirmCards, HandResult in ClientDuelState"
```

---

## Task 4: Display in LDLibDuelScreen

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add CONFIRM handling in onTick**

In the `onTick` method, add after the WINNER check:

```java
    if (flags.contains(DirtyFlag.CONFIRM))
        showConfirmCards();
```

- [ ] **Step 2: Implement showConfirmCards**

Add this method to the UIRefresher class. It auto-opens the zone inspector with the confirmed/revealed cards:

```java
    private void showConfirmCards() {
        if (state.confirmCards == null || state.confirmCards.isEmpty()) return;
        if (zoneInspector == null) return;

        zoneInspector.removeClass("hidden");
        var titleLabel = byId("zone-inspector-title");
        var zoneInspectorList = byId("zone-inspector-list", ScrollerView.class);
        if (titleLabel instanceof Label lbl) lbl.setText(Component.literal(state.confirmTitle));

        if (zoneInspectorList != null) {
            zoneInspectorList.clearAllScrollViewChildren();
            for (var confirmCard : state.confirmCards) {
                int code = confirmCard.code();

                var card = new UIElement();
                card.addClasses("card-slot", "hand-card");
                setCardImageBackground(card, code);

                card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
                card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());

                zoneInspectorList.addScrollViewChild(card);
            }
        }

        // Clear after displaying
        state.confirmCards = null;
    }
```

- [ ] **Step 3: Add RPS result display to updateStatusLabel**

Extend `updateStatusLabel` to show RPS results. Add this check at the beginning, before the chain check:

```java
    if (state.rpsHand0 > 0 && state.rpsHand1 > 0) {
        statusLabel.removeClass("hidden");
        String h0 = rpsName(state.rpsHand0);
        String h1 = rpsName(state.rpsHand1);
        int local = state.localPlayer == 0 ? state.rpsHand0 : state.rpsHand1;
        int remote = state.localPlayer == 0 ? state.rpsHand1 : state.rpsHand0;
        if (statusLabel instanceof Label lbl)
            lbl.setText(Component.literal("You: " + rpsName(local) + " vs " + rpsName(remote)));
        state.rpsHand0 = 0;
        state.rpsHand1 = 0;
        return;
    }
```

Add the helper method:

```java
    private static String rpsName(int hand) {
        return switch (hand) {
            case 1 -> "Rock";
            case 2 -> "Paper";
            case 3 -> "Scissors";
            default -> "???";
        };
    }
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | grep -E "tests completed|FAILED"`
Expected: Only the pre-existing `selectUnselectCardFinish` failure

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: auto-open inspector for confirmed cards, show RPS result in status"
```

---

## Task 5: Manual Verification

- [ ] **Step 1: Test card reveal** — Use a card like Pot of Duality or Card Destruction that reveals/confirms cards. The zone inspector should auto-open showing the revealed cards with images and hover info.

- [ ] **Step 2: Test RPS** — Start a duel (RPS happens at the start). The status label should briefly show "You: Rock vs Scissors" or similar.

- [ ] **Step 3: Commit any fixes**

```bash
git add -u
git commit -m "fix: polish tier 1 message display"
```

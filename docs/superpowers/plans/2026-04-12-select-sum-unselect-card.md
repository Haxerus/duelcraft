# SELECT_SUM & SELECT_UNSELECT_CARD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add interactive prompt UIs for sum-based material selection (Synchro/Ritual/XYZ) and incremental toggle card selection (Fusion/Link materials).

**Architecture:** SelectUnselectCard uses per-click immediate responses (engine round-trip each click). SelectSum uses local toggle with running sum validation and batch response on Finish. Both reuse the prompt overlay with `.card` elements showing card images, and highlight on-field targets with `.target` class.

**Tech Stack:** Existing DuelMessage/MessageParser/DuelMessageCodec pipeline, LDLib2 prompt overlay UI, ResponseBuilder

---

## File Map

| File | Changes |
|------|---------|
| `DuelMessage.java` | Add `SumCard` inner record with opParam. Replace `SelectSum.rawBody` with parsed fields: `mustSelect` and `selectable` lists of `SumCard`. |
| `MessageParser.java` | Rewrite `parseSelectSum` to read must-select + selectable card lists with opParam |
| `DuelMessageCodec.java` | Update SelectSum encode/decode for new fields (SumCard lists replace rawBody) |
| `LDLibDuelScreen.java` | Add `rebuildPrompt` cases for SelectUnselectCard and SelectSum. Card grid UI with images, running sum for SelectSum. |
| `duel_screen.xml` | Add `.selected` CSS class (green overlay for selected cards) |
| `MessageParserTest.java` | Test for SelectSum parsing |

---

## Task 1: Update SelectSum Record, Parsing, and Codec

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java`
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java`
- Modify: `src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java`
- Test: `src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java`

Must be done together due to sealed interface exhaustive switch.

- [ ] **Step 1: Add SumCard inner record to DuelMessage.java**

Add next to the existing `CardInfo` inner record:

```java
    /** Card entry in SelectSum: card info + opParam encoding the sum value(s). */
    record SumCard(int code, int controller, int location, int sequence, long opParam) {
        public static SumCard read(BufferReader reader) {
            return new SumCard(
                reader.readInt32(),   // code
                reader.readUint8(),   // controller
                reader.readUint8(),   // location
                reader.readInt32(),   // sequence
                reader.readInt64()    // opParam (u64 in non-compat)
            );
        }

        /** Primary sum value. */
        public int value1() { return (int)(opParam & 0xFFFF); }

        /** Secondary sum value (for dual-value mode). */
        public int value2() { return (int)((opParam >> 16) & 0xFFFF); }
    }
```

- [ ] **Step 2: Replace SelectSum record**

Replace the existing `SelectSum` record (which stores rawBody) with:

```java
    record SelectSum(int player, boolean selectMode, int targetSum,
                     int min, int max, List<SumCard> mustSelect,
                     List<SumCard> selectable) implements DuelMessage {
        public int type() { return MSG_SELECT_SUM; }
    }
```

Note: `selectMode` renamed from `mustExact` for clarity. `true` = dual-value mode.

- [ ] **Step 3: Rewrite parseSelectSum in MessageParser.java**

Replace the existing method:

```java
    private static DuelMessage.SelectSum parseSelectSum(BufferReader r, int bodyLength) {
        int player = r.readUint8();
        boolean selectMode = r.readUint8() != 0;
        int targetSum = r.readInt32();
        int min = r.readInt32();
        int max = r.readInt32();

        int mustCount = r.readInt32();
        var mustSelect = new ArrayList<DuelMessage.SumCard>(mustCount);
        for (int i = 0; i < mustCount; i++) {
            mustSelect.add(DuelMessage.SumCard.read(r));
        }

        int selectCount = r.readInt32();
        var selectable = new ArrayList<DuelMessage.SumCard>(selectCount);
        for (int i = 0; i < selectCount; i++) {
            selectable.add(DuelMessage.SumCard.read(r));
        }

        return new DuelMessage.SelectSum(player, selectMode, targetSum,
                min, max, mustSelect, selectable);
    }
```

Also update the switch case if it passes `bodyLength` — check the current signature. The new version doesn't need bodyLength since we read all fields explicitly.

- [ ] **Step 4: Update DuelMessageCodec.java**

Add SumCard helpers:

```java
    private static void writeSumCard(FriendlyByteBuf buf, DuelMessage.SumCard card) {
        buf.writeInt(card.code());
        buf.writeByte(card.controller());
        buf.writeByte(card.location());
        buf.writeInt(card.sequence());
        buf.writeLong(card.opParam());
    }

    private static DuelMessage.SumCard readSumCard(FriendlyByteBuf buf) {
        return new DuelMessage.SumCard(buf.readInt(), buf.readByte(), buf.readByte(),
                buf.readInt(), buf.readLong());
    }

    private static void writeSumCardList(FriendlyByteBuf buf, List<DuelMessage.SumCard> list) {
        buf.writeInt(list.size());
        for (var c : list) writeSumCard(buf, c);
    }

    private static List<DuelMessage.SumCard> readSumCardList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        var list = new ArrayList<DuelMessage.SumCard>(count);
        for (int i = 0; i < count; i++) list.add(readSumCard(buf));
        return list;
    }
```

Replace SelectSum encode case:

```java
    case DuelMessage.SelectSum m -> {
        buf.writeByte(m.player());
        buf.writeBoolean(m.selectMode());
        buf.writeInt(m.targetSum());
        buf.writeInt(m.min()); buf.writeInt(m.max());
        writeSumCardList(buf, m.mustSelect());
        writeSumCardList(buf, m.selectable());
    }
```

Replace SelectSum decode case:

```java
    case MSG_SELECT_SUM -> new DuelMessage.SelectSum(buf.readByte(), buf.readBoolean(),
            buf.readInt(), buf.readInt(), buf.readInt(),
            readSumCardList(buf), readSumCardList(buf));
```

- [ ] **Step 5: Add parsing test**

Add to `MessageParserTest.java`:

```java
    @Test
    void parseSelectSum() {
        ByteBuffer b = body(64);
        b.put((byte) 0);       // player
        b.put((byte) 0);       // selectMode (single value)
        b.putInt(8);            // targetSum = 8
        b.putInt(1);            // min
        b.putInt(3);            // max

        // 1 must-select card: level 4 tuner
        b.putInt(1);            // must_count
        b.putInt(56832966);     // code
        b.put((byte) 0);       // controller
        b.put((byte) LOCATION_MZONE);
        b.putInt(0);            // sequence
        b.putLong(4L);          // opParam = level 4

        // 2 selectable cards
        b.putInt(2);            // select_count
        b.putInt(89631139);     // Blue-Eyes (level 8)
        b.put((byte) 0);
        b.put((byte) LOCATION_MZONE);
        b.putInt(1);
        b.putLong(8L);
        b.putInt(46986414);     // Dark Magician (level 7)
        b.put((byte) 0);
        b.put((byte) LOCATION_MZONE);
        b.putInt(2);
        b.putLong(7L);

        List<DuelMessage> msgs = MessageParser.parse(msg(MSG_SELECT_SUM, b.array()));
        assertEquals(1, msgs.size());
        assertInstanceOf(DuelMessage.SelectSum.class, msgs.getFirst());
        var sum = (DuelMessage.SelectSum) msgs.getFirst();
        assertEquals(0, sum.player());
        assertEquals(8, sum.targetSum());
        assertEquals(1, sum.mustSelect().size());
        assertEquals(4, sum.mustSelect().getFirst().value1());
        assertEquals(2, sum.selectable().size());
    }
```

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew test --tests "com.haxerus.duelcraft.duel.message.MessageParserTest" 2>&1 | tail -5`
Expected: All tests PASS

```bash
git add src/main/java/com/haxerus/duelcraft/duel/message/DuelMessage.java \
        src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java \
        src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java \
        src/test/java/com/haxerus/duelcraft/duel/message/MessageParserTest.java
git commit -m "feat: complete SelectSum parsing with SumCard lists and opParam"
```

---

## Task 2: Add .selected CSS Class

**Files:**
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml`

- [ ] **Step 1: Add .selected CSS class**

Add after the existing `.target` and `.selectable` classes:

```css
        .selected {
        overlay: rect(#6600FF00);
        }
```

Green overlay for cards that have been selected/toggled.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/assets/duelcraft/ui/duel_screen.xml
git commit -m "feat: add .selected CSS class for toggled card overlay"
```

---

## Task 3: SelectUnselectCard Prompt UI

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add SelectUnselectCard case in rebuildPrompt**

Find the `rebuildPrompt` method's switch statement. Add a case for `SelectUnselectCard`:

```java
    case DuelMessage.SelectUnselectCard sel -> {
        buildUnselectCardPrompt(sel);
    }
```

- [ ] **Step 2: Implement buildUnselectCardPrompt**

Add this method to the UIRefresher class:

```java
    private void buildUnselectCardPrompt(DuelMessage.SelectUnselectCard sel) {
        promptOverlay.removeClass("hidden");
        int totalSelected = sel.unselectableCards().size();
        if (promptTitle instanceof Label t)
            t.setText(Component.literal("Select Materials (" + totalSelected + " selected)"));
        clearPromptContent();

        // Show selectable cards (can toggle ON)
        for (int i = 0; i < sel.selectableCards().size(); i++) {
            var cardInfo = sel.selectableCards().get(i);
            int code = cardInfo.code();
            int index = i;

            var card = new UIElement();
            card.addClass("card");
            setCardImageBackground(card, code);

            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                LDLibDuelScreen.sendResponse(state,
                        ResponseBuilder.selectUnselectCard(index));
            });

            promptBody.addChild(card);
        }

        // Show already-selected cards (can toggle OFF) with .selected indicator
        for (int i = 0; i < sel.unselectableCards().size(); i++) {
            var cardInfo = sel.unselectableCards().get(i);
            int code = cardInfo.code();
            int index = i;

            var card = new UIElement();
            card.addClasses("card", "selected");
            setCardImageBackground(card, code);

            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                // Toggle OFF: send the index from selectable count offset
                LDLibDuelScreen.sendResponse(state,
                        ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + index));
            });

            promptBody.addChild(card);
        }

        // Finish/Cancel buttons
        if ((sel.finishable() || sel.cancelable()) && !sel.unselectableCards().isEmpty()) {
            var finishBtn = new Button();
            finishBtn.setText(Component.literal("Finish"));
            finishBtn.addClasses("prompt-btn");
            finishBtn.setOnClick(e ->
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCardFinish()));
            promptButtons.addChild(finishBtn);
        }
        if (sel.cancelable() && sel.unselectableCards().isEmpty()) {
            var cancelBtn = new Button();
            cancelBtn.setText(Component.literal("Cancel"));
            cancelBtn.addClasses("prompt-btn");
            cancelBtn.setOnClick(e ->
                    LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCardFinish()));
            promptButtons.addChild(cancelBtn);
        }
    }
```

IMPORTANT: Check how the existing `clearPromptContent` method works. It should clear `promptBody` and `promptButtons` children. If it doesn't exist, use the pattern from other prompt builders (clear children of promptBody and promptButtons manually).

Also check the index for unselecting already-selected cards. In edopro, clicking an already-selected card sends `selectableCards.size() + index` as the card index. Verify this against `ResponseBuilder.selectUnselectCard` and the engine's `playerop.cpp`.

- [ ] **Step 3: Update onCardClicked for SelectUnselectCard field targets**

In the `onCardClicked` method, add a handler for SelectUnselectCard after the existing SelectPlace handler:

```java
    else if (state.pendingPrompt instanceof DuelMessage.SelectUnselectCard sel) {
        // Find the card in the selectable list by location
        for (int i = 0; i < sel.selectableCards().size(); i++) {
            var c = sel.selectableCards().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectUnselectCard(i));
                return;
            }
        }
        // Check unselectable list (toggle off)
        for (int i = 0; i < sel.unselectableCards().size(); i++) {
            var c = sel.unselectableCards().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                LDLibDuelScreen.sendResponse(state,
                        ResponseBuilder.selectUnselectCard(sel.selectableCards().size() + i));
                return;
            }
        }
    }
```

- [ ] **Step 4: Verify compilation and commit**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add SelectUnselectCard prompt UI with per-click responses"
```

---

## Task 4: SelectSum Prompt UI with Running Sum

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add SelectSum case in rebuildPrompt**

```java
    case DuelMessage.SelectSum sel -> {
        buildSelectSumPrompt(sel);
    }
```

- [ ] **Step 2: Implement buildSelectSumPrompt**

```java
    private void buildSelectSumPrompt(DuelMessage.SelectSum sel) {
        selectedIndices.clear();
        promptOverlay.removeClass("hidden");
        clearPromptContent();

        // Calculate must-select sum contribution
        int mustSum = 0;
        for (var c : sel.mustSelect()) {
            mustSum += c.value1();
        }
        final int baseMustSum = mustSum;

        // Title with running sum
        var titleRef = new int[]{ baseMustSum }; // mutable reference for lambda
        Runnable updateTitle = () -> {
            int currentSum = titleRef[0];
            if (promptTitle instanceof Label t)
                t.setText(Component.literal("Select Materials (Sum: " + currentSum + " / " + sel.targetSum() + ")"));
        };
        updateTitle.run();

        // Show must-select cards (locked, can't be deselected)
        for (var mustCard : sel.mustSelect()) {
            var card = new UIElement();
            card.addClasses("card", "selected");
            setCardImageBackground(card, mustCard.code());
            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(mustCard.code()));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
            // No click handler — locked
            promptBody.addChild(card);
        }

        // Show selectable cards (toggleable)
        for (int i = 0; i < sel.selectable().size(); i++) {
            var sumCard = sel.selectable().get(i);
            int code = sumCard.code();
            int value = sumCard.value1();
            int index = i;

            var card = new UIElement();
            card.addClass("card");
            setCardImageBackground(card, code);

            card.addEventListener(UIEvents.MOUSE_ENTER, ev -> showCardInfo(code));
            card.addEventListener(UIEvents.MOUSE_LEAVE, ev -> hideCardInfo());
            card.addEventListener(UIEvents.CLICK, ev -> {
                ev.stopPropagation();
                if (selectedIndices.contains(index)) {
                    selectedIndices.remove(Integer.valueOf(index));
                    card.removeClass("selected");
                    titleRef[0] -= value;
                } else {
                    selectedIndices.add(index);
                    card.addClass("selected");
                    titleRef[0] += value;
                }
                updateTitle.run();
            });

            promptBody.addChild(card);
        }

        // Confirm button — enabled when sum matches target and count in range
        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("Confirm"));
        confirmBtn.addClasses("prompt-btn");
        confirmBtn.setOnClick(e -> {
            int totalSelected = sel.mustSelect().size() + selectedIndices.size();
            if (titleRef[0] == sel.targetSum()
                    && totalSelected >= sel.min() && totalSelected <= sel.max()) {
                // Build indices: must-select indices (0..mustCount-1) + selected selectable indices (offset by mustCount)
                int mustCount = sel.mustSelect().size();
                int[] allIndices = new int[totalSelected];
                for (int i = 0; i < mustCount; i++) allIndices[i] = i;
                int j = mustCount;
                for (int idx : selectedIndices) {
                    allIndices[j++] = mustCount + idx;
                }
                LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectSum(allIndices));
            }
        });
        promptButtons.addChild(confirmBtn);
    }
```

NOTE: The response indices for SelectSum are: must-select cards first (indices 0..mustCount-1), then selectable cards (indices mustCount..mustCount+selectCount-1). The engine expects all selected indices in this combined space. Verify against `ResponseBuilder.selectSum` and edopro's response handling.

- [ ] **Step 3: Update onCardClicked for SelectSum field targets**

In `onCardClicked`, add after the SelectUnselectCard handler:

```java
    else if (state.pendingPrompt instanceof DuelMessage.SelectSum sel) {
        // Find the card in the selectable list by location and toggle
        for (int i = 0; i < sel.selectable().size(); i++) {
            var c = sel.selectable().get(i);
            if (c.controller() == player && c.location() == location && c.sequence() == sequence) {
                // Simulate clicking the corresponding card element in the prompt
                // For field-only selection this would need different handling
                // For now, rebuild prompt which handles the toggle
                if (selectedIndices.contains(i)) {
                    selectedIndices.remove(Integer.valueOf(i));
                } else {
                    selectedIndices.add(i);
                }
                // Rebuild to update visual state
                rebuildPrompt();
                return;
            }
        }
    }
```

- [ ] **Step 4: Verify compilation and commit**

Run: `./gradlew compileJava 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add SelectSum prompt UI with local toggle and running sum"
```

---

## Task 5: Manual Verification

- [ ] **Step 1: Test SelectUnselectCard** — Start a duel, try a Fusion or Link summon that triggers material selection. Cards should appear in the prompt overlay. Clicking toggles them (engine round-trip). Finish button appears when valid.

- [ ] **Step 2: Test SelectSum** — Try a Synchro or Ritual summon. Must-select cards (tuner) shown locked. Selectable cards toggleable. Running sum updates. Confirm button works when sum matches target.

- [ ] **Step 3: Fix any issues and commit**

```bash
git add -u
git commit -m "fix: polish SelectSum and SelectUnselectCard prompt UIs"
```

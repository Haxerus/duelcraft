# Field Selection Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the modal prompt dialog with direct field clicking when a SelectCard prompt targets only on-field cards (MZONE/SZONE).

**Architecture:** Add a "field selection mode" to UIRefresher that highlights valid targets with a `.selectable` CSS class, shows a minimal indicator via the existing `#status-label`, and handles cancel via right-click. The existing `handleCardSelection` click path already works — we just need to stop showing the modal and add visual cues instead.

**Tech Stack:** Java 21, LDLib2 UI framework (XML + LSS styling), NeoForge 1.21.11

**Spec:** `docs/superpowers/specs/2026-04-11-field-selection-mode-design.md`

---

### Task 1: Add `.selectable` CSS class

**Files:**
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml:445-447`

- [ ] **Step 1: Add the `.selectable` style rule**

In `duel_screen.xml`, after the existing `.target` rule (line 445-447), add:

```css
.selectable {
    overlay: rect(#66FF4444);
}
```

This goes right after:
```css
.target {
    overlay: rect(#6600FFFF);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/assets/duelcraft/ui/duel_screen.xml
git commit -m "style: add .selectable CSS class for field target highlighting"
```

---

### Task 2: Add `isFieldOnlySelection()` helper

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add the detection method**

In the `UIRefresher` class, in the helpers section (after `findSlotForLocation` around line 960), add:

```java
private boolean isFieldOnlySelection(DuelMessage.SelectCard sel) {
    return !sel.cards().isEmpty()
            && sel.cards().stream()
                    .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0);
}
```

This uses the existing `LOCATION_ONFIELD` constant (`LOCATION_MZONE | LOCATION_SZONE`) already imported via `import static com.haxerus.duelcraft.core.OcgConstants.*`.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add isFieldOnlySelection() detection helper"
```

---

### Task 3: Add `enterFieldSelectionMode()` and `exitFieldSelectionMode()`

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add a tracking field**

In the UIRefresher field declarations, near the existing `selectedIndices` and `isBattleCmd` fields (line 150-152), add:

```java
private boolean inFieldSelectionMode;
```

- [ ] **Step 2: Add `enterFieldSelectionMode()`**

Add this method in the helpers section, near `highlightValidPlaces`:

```java
private void enterFieldSelectionMode(DuelMessage.SelectCard sel) {
    inFieldSelectionMode = true;
    selectedIndices.clear();
    promptOverlay.addClass("hidden");

    // Highlight valid targets on the field
    for (var card : sel.cards()) {
        var loc = new ClientDuelState.CardLocation(
                card.controller(), card.location(), card.sequence());
        UIElement slot = findSlotForLocation(loc);
        if (slot != null) slot.addClass("selectable");
    }

    // Show minimal indicator via status label
    if (statusLabel instanceof Label lbl) {
        String text = sel.min() == sel.max()
                ? "Select " + sel.min() + " card(s)"
                : "Select " + sel.min() + "-" + sel.max() + " card(s)";
        if (sel.cancelable()) text += "  (Right-click to cancel)";
        lbl.setText(Component.literal(text));
        statusLabel.removeClass("hidden");
    }
}
```

- [ ] **Step 3: Add `exitFieldSelectionMode()`**

Add immediately after `enterFieldSelectionMode`:

```java
private void exitFieldSelectionMode() {
    if (!inFieldSelectionMode) return;
    inFieldSelectionMode = false;
    ui.rootElement.select(".selectable").forEach(e -> e.removeClass("selectable"));
    if (statusLabel != null) statusLabel.addClass("hidden");
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add enterFieldSelectionMode() and exitFieldSelectionMode()"
```

---

### Task 4: Wire up `rebuildPrompt` to branch on field selection

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java:404`

- [ ] **Step 1: Modify the SelectCard case in rebuildPrompt**

Change line 404 from:

```java
case DuelMessage.SelectCard sel -> buildCardSelectionPrompt(sel);
```

to:

```java
case DuelMessage.SelectCard sel -> {
    if (isFieldOnlySelection(sel)) enterFieldSelectionMode(sel);
    else buildCardSelectionPrompt(sel);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: route on-field SelectCard to field selection mode"
```

---

### Task 5: Add right-click cancel and cleanup on response

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add right-click cancel listener**

In the UIRefresher constructor, after the existing root click listener that dismisses the context menu (after line 226), add:

```java
// Right-click to cancel field selection
ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
    if (e.button == 1 && inFieldSelectionMode
            && state.pendingPrompt instanceof DuelMessage.SelectCard sel
            && sel.cancelable()) {
        e.stopPropagation();
        exitFieldSelectionMode();
        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCards());
    }
});
```

- [ ] **Step 2: Call `exitFieldSelectionMode()` in `onResponseSent()`**

In the existing `onResponseSent()` method (line 787-792), add `exitFieldSelectionMode()`:

```java
void onResponseSent() {
    if (promptOverlay != null) promptOverlay.addClass("hidden");
    hideContextMenu();
    exitFieldSelectionMode();
    ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
    updatePhaseButtons();
}
```

This ensures cleanup happens for ALL response paths — whether the player clicks a target, cancels, or some other code path sends a response.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add right-click cancel and cleanup for field selection mode"
```

---

### Task 6: Manual smoke test

- [ ] **Step 1: Build and launch**

```bash
./gradlew runClient
```

- [ ] **Step 2: Test attack target selection**

1. Start a solo duel: `/duel test`
2. Summon a monster in Main Phase 1
3. Enter Battle Phase (click the phase button)
4. Click your monster → context menu shows "Attack" icon
5. Click the "Attack" icon
6. **Verify:** No modal prompt appears. Opponent's monsters are highlighted with a red-orange overlay. The status label at the bottom shows "Select 1 card(s) (Right-click to cancel)".
7. Click an opponent's highlighted monster
8. **Verify:** Attack proceeds, highlights clear, status label hides

- [ ] **Step 3: Test right-click cancel**

1. Repeat steps 1-6 above
2. Instead of clicking a target, right-click anywhere
3. **Verify:** Field selection mode exits, highlights clear, attack is canceled (returns to SelectBattleCmd)

- [ ] **Step 4: Test fallback to prompt overlay**

1. During a duel, trigger a card effect that selects from GY or hand (e.g., a card that targets a card in the graveyard)
2. **Verify:** The existing modal prompt overlay still appears for non-field selections

- [ ] **Step 5: Commit any fixes if needed**

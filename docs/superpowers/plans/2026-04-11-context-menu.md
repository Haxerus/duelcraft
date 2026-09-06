# Card Action Context Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the prompt-overlay action menu with a small, cursor-anchored horizontal icon strip for card actions.

**Architecture:** An empty `#context-menu` UIElement defined in XML, styled with `position: absolute`, populated and positioned dynamically in Java on card click. Icons are `sdf()` colored fills with tooltips. The existing prompt overlay is untouched and continues serving all other prompt types.

**Tech Stack:** LDLib2 UI (XML + LSS), NeoForge 1.21.11, Java 21

**Spec:** `docs/superpowers/specs/2026-04-11-context-menu-design.md`

---

### Task 1: Add context menu XML element and CSS styles

**Files:**
- Modify: `src/main/resources/assets/duelcraft/ui/duel_screen.xml`

- [ ] **Step 1: Add CSS styles for context menu**

In the `<style>` block, after the `.target` rule (around line 446), add the context menu styles:

```css
/* ── Card Action Context Menu ── */
#context-menu {
    position: absolute;
    flex-direction: row;
    gap-all: 1;
    padding-all: 1;
    background: rect(#1A1A2E, 2, 1, #4444AA);
    z-index: 10;
}

#context-menu.hidden {
    display: none;
}

.ctx-action {
    width: 14;
    height: 14;
    align-items: center;
    justify-content: center;
}
```

- [ ] **Step 2: Add context menu element to the overlays section**

After the prompt overlay closing tag (`</element>` at line 653), before `</root>`, add:

```xml
<!-- Card Action Context Menu (positioned at cursor on card click) -->
<element id="context-menu" class="hidden" />
```

- [ ] **Step 3: Verify XML is well-formed**

Run: `./gradlew build`

Expected: Build succeeds. The new element is hidden by default and has no visible effect yet.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/duelcraft/ui/duel_screen.xml
git commit -m "feat: add context menu XML element and CSS styles"
```

---

### Task 2: Add context menu field and action icon helper to UIRefresher

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add context menu field**

In the `UIRefresher` class, after the existing overlay fields (around line 145), add:

```java
// Context menu (cursor-anchored action icons)
private final UIElement contextMenu;
```

In the constructor, after `promptButtons = byId("prompt-buttons");` (around line 201), add:

```java
contextMenu = byId("context-menu");
```

- [ ] **Step 2: Add action icon helper record and lookup method**

After the `clearPromptContent` method (around line 961), add:

```java
private record ActionIconInfo(String color, String tooltip) {}

private ActionIconInfo getActionIconInfo(int actionType, boolean isBattleCmd) {
    if (isBattleCmd) {
        return switch (actionType) {
            case 1 -> new ActionIconInfo("#FF4444", "Attack");
            case 2 -> new ActionIconInfo("#FF6644", "Activate");
            default -> new ActionIconInfo("#AAAAAA", "Action");
        };
    }
    return switch (actionType) {
        case 0 -> new ActionIconInfo("#FFCC00", "Summon");
        case 1 -> new ActionIconInfo("#44CC44", "Special Summon");
        case 2 -> new ActionIconInfo("#44AAFF", "Reposition");
        case 3 -> new ActionIconInfo("#6688FF", "Set");
        case 4 -> new ActionIconInfo("#8866FF", "Set S/T");
        case 5 -> new ActionIconInfo("#FF6644", "Activate");
        default -> new ActionIconInfo("#AAAAAA", "Action");
    };
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew build`

Expected: Build succeeds. No behavioral changes yet — the field is wired but unused.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: add context menu field and action icon info helper"
```

---

### Task 3: Implement showContextMenu and hideContextMenu methods

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Add isBattleCmd tracking field**

In the `UIRefresher` class, near the `selectedIndices` field (around line 148), add:

```java
private boolean isBattleCmd;
```

- [ ] **Step 2: Set isBattleCmd when prompts arrive**

In the `rebuildPrompt` method, update the `SelectIdleCmd` and `SelectBattleCmd` cases.

In the `SelectIdleCmd` case (around line 362), add before the existing lines:

```java
case DuelMessage.SelectIdleCmd ignored -> {
    isBattleCmd = false;
    promptOverlay.addClass("hidden");
    refreshTargetHighlights();
}
```

In the `SelectBattleCmd` case (around line 366), add before the existing lines:

```java
case DuelMessage.SelectBattleCmd ignored -> {
    isBattleCmd = true;
    promptOverlay.addClass("hidden");
    refreshTargetHighlights();
}
```

- [ ] **Step 3: Add hideContextMenu method**

After the `getActionIconInfo` method, add:

```java
private void hideContextMenu() {
    if (contextMenu != null) contextMenu.addClass("hidden");
}
```

- [ ] **Step 4: Add showContextMenu method**

After `hideContextMenu`, add:

```java
private void showContextMenu(List<ClientDuelState.CardAction> actions, float mouseX, float mouseY) {
    if (contextMenu == null) return;

    contextMenu.clearAllChildren();

    for (var action : actions) {
        var icon = new UIElement();
        icon.addClass("ctx-action");

        var info = getActionIconInfo(action.actionType(), isBattleCmd);
        icon.lss("background", "sdf(" + info.color() + ", 3, 2)");
        icon.lss("tooltips", info.tooltip());

        icon.addEventListener(UIEvents.CLICK, e -> {
            LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(action.actionType(),
                    action.listIndex()));
        });

        contextMenu.addChild(icon);
    }

    // Flip/nudge positioning
    var root = ui.rootElement;
    float rootW = root.getLayoutWidth();
    float rootH = root.getLayoutHeight();
    // Estimate menu size: each icon is 14 wide + 1 gap + 1 padding each side
    float menuW = actions.size() * 15f + 2f;
    float menuH = 16f;

    float x = mouseX;
    float y = mouseY;
    if (x + menuW > rootW) x = mouseX - menuW;
    if (y + menuH > rootH) y = mouseY - menuH;
    // Clamp to not go negative
    if (x < 0) x = 0;
    if (y < 0) y = 0;

    contextMenu.lss("left", String.valueOf((int) x));
    contextMenu.lss("top", String.valueOf((int) y));
    contextMenu.removeClass("hidden");
}
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew build`

Expected: Build succeeds. Methods exist but are not called yet.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: implement showContextMenu and hideContextMenu methods"
```

---

### Task 4: Wire context menu into card click handling

**Files:**
- Modify: `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`

- [ ] **Step 1: Update onCardClicked to accept UIEvent and use context menu**

Replace the existing `onCardClicked` method (around line 779):

```java
private void onCardClicked(int player, int location, int sequence, UIEvent event) {
    var loc = new ClientDuelState.CardLocation(player, location, sequence);
    var actions = state.cardActions.get(loc);

    if (actions != null && !actions.isEmpty()) {
        showContextMenu(actions, event.x, event.y);
        return;
    }

    // No actions — dismiss context menu if open
    hideContextMenu();

    if (state.pendingPrompt instanceof DuelMessage.SelectCard) {
        handleCardSelection(player, location, sequence);
    } else if (state.pendingPrompt instanceof DuelMessage.SelectPlace) {
        handlePlaceSelection(player, location, sequence);
    }
}
```

- [ ] **Step 2: Update hand card click listeners to pass UIEvent**

In the `rebuildHand` method (around line 341), change:

```java
card.addEventListener(UIEvents.CLICK, e -> onCardClicked(player, LOCATION_HAND, seq));
```

to:

```java
card.addEventListener(UIEvents.CLICK, e -> onCardClicked(player, LOCATION_HAND, seq, e));
```

- [ ] **Step 3: Update zone click listeners to pass UIEvent**

In the `handleZoneClicks` method (around line 839), change the monster slot listener:

```java
monsterSlots[p][i].addEventListener(UIEvents.CLICK,
        e -> onCardClicked(player, LOCATION_MZONE, seq));
```

to:

```java
monsterSlots[p][i].addEventListener(UIEvents.CLICK,
        e -> onCardClicked(player, LOCATION_MZONE, seq, e));
```

And the spell slot listener (around line 843):

```java
spellSlots[p][i].addEventListener(UIEvents.CLICK,
        e -> onCardClicked(player, LOCATION_SZONE, seq));
```

to:

```java
spellSlots[p][i].addEventListener(UIEvents.CLICK,
        e -> onCardClicked(player, LOCATION_SZONE, seq, e));
```

- [ ] **Step 4: Update onResponseSent to hide context menu**

In the `onResponseSent` method (around line 772), add `hideContextMenu()`:

```java
void onResponseSent() {
    if (promptOverlay != null) promptOverlay.addClass("hidden");
    hideContextMenu();
    ui.rootElement.select(".target").forEach(e -> e.removeClass("target"));
    updatePhaseButtons();
}
```

- [ ] **Step 5: Add root click listener for dismiss-on-outside-click**

In the `UIRefresher` constructor, after `handlePileClicks();` (around line 211), add:

```java
// Dismiss context menu when clicking outside it
ui.rootElement.addEventListener(UIEvents.CLICK, e -> {
    if (contextMenu != null && !contextMenu.hasClass("hidden")) {
        // Check if the click target is inside the context menu
        if (!contextMenu.isAncestorOf(e.target)) {
            hideContextMenu();
        }
    }
});
```

This fires on every click due to event bubbling. When the click target is inside the context menu (an action icon), `isAncestorOf` returns true and we skip the dismiss — the icon's own handler sends the response. When the click is anywhere else, the menu hides.

- [ ] **Step 6: Delete the old showActionMenu method**

Remove the entire `showActionMenu` method (around line 914-940). It is fully replaced by `showContextMenu`.

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew build`

Expected: Build succeeds. No references to `showActionMenu` remain.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java
git commit -m "feat: wire context menu into card click handling, remove showActionMenu"
```

---

### Task 5: Manual in-game test

**Files:** None (testing only)

- [ ] **Step 1: Launch the client**

Run: `./gradlew runClient`

- [ ] **Step 2: Start a solo duel**

In-game, run `/duel test` to start a solo duel against AI.

- [ ] **Step 3: Test context menu appears**

Click a card in your hand that has available actions (indicated by the yellow target highlight). Verify:
- A small horizontal icon strip appears near the cursor
- Icons have distinct colors matching their action type
- Hovering an icon shows a tooltip with the action name

- [ ] **Step 4: Test immediate switching**

Click a different highlighted card. Verify:
- The context menu immediately jumps to the new card's position
- The actions update to reflect the new card's available actions

- [ ] **Step 5: Test dismiss behavior**

Click an empty area on the field (no target highlight). Verify:
- The context menu disappears

- [ ] **Step 6: Test action execution**

Click a card, then click one of the action icons (e.g., Summon). Verify:
- The response is sent (card moves to the appropriate zone)
- The context menu disappears
- The game advances to the next prompt

- [ ] **Step 7: Test edge positioning**

Click a card near the right edge or bottom of the screen. Verify:
- The menu flips to the left or above the cursor instead of going off-screen

- [ ] **Step 8: Test prompt overlay still works**

Advance the duel until a different prompt appears (e.g., SelectPosition, SelectYesNo). Verify:
- The centered prompt overlay still appears and functions correctly
- It is separate from the context menu

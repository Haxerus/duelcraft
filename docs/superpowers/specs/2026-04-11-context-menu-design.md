# Card Action Context Menu

Replaces the prompt-overlay-based action menu with a small, cursor-anchored icon strip for card actions during SelectIdleCmd and SelectBattleCmd prompts.

## Problem

The current `showActionMenu` reuses the centered prompt overlay to display card actions (Summon, Set, Activate, etc.). This covers the field, breaks flow, and feels heavy for a quick 1-click action. EDOPro-style simulators use a small floating menu near the card instead.

## Design

### Visual

A horizontal strip of 14x14 colored icon buttons, positioned at the mouse cursor. No text labels — each icon has a tooltip on hover via LDLib2's tooltip system. The strip has a dark background with a thin border, matching the existing UI palette.

### Action Icon Mapping

| Action Type | Idle Cmd Index | Battle Cmd Index | Color | Tooltip |
|-------------|---------------|-----------------|-------|---------|
| Summon | 0 | — | Gold (#FFCC00) | "Summon" |
| Sp. Summon | 1 | — | Green (#44CC44) | "Special Summon" |
| Reposition | 2 | — | Cyan (#44AAFF) | "Reposition" |
| Set (monster) | 3 | — | Blue (#6688FF) | "Set" |
| Set S/T | 4 | — | Purple (#8866FF) | "Set S/T" |
| Activate | 5 (idle) | 2 (battle) | Red/orange (#FF6644) | "Activate" |
| Attack | — | 1 | Red (#FF4444) | "Attack" |

Icons start as `sdf()` colored fills as placeholders. Real 16x16 textures in `textures/ui/actions/` can be swapped in later.

### XML Structure

Added to the overlays section of `duel_screen.xml`, after the existing overlays:

```xml
<element id="context-menu" class="hidden" />
```

CSS in the `<style>` block:

```css
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

### Behavior

**Opening and switching:**

1. `onCardClicked` checks `cardActions` for the clicked card's location
2. If actions exist: clear context menu children via `clearAllChildren()`, populate with icon buttons, position at cursor, show
3. If clicking a different actionable card while the menu is open, it immediately rebuilds at the new position — no dismiss-then-reopen needed

**Positioning (flip/nudge):**

- Default: menu opens to the right and below the cursor
- If cursor X + menu width > root width: open to the left of the cursor
- If cursor Y + menu height > root height: open above the cursor
- Position set via `lss("left", ...)` and `lss("top", ...)` on each open

**Dismissing:**

- Clicking an action icon sends the response via `sendResponse` (which calls `onResponseSent`)
- Clicking a non-actionable area (card with no actions, empty field, other UI) hides the menu
- `onResponseSent` hides the context menu alongside its existing cleanup

**Icon button wiring:**

Each icon's CLICK handler calls `LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCmd(actionType, listIndex))` — same as the current `showActionMenu` buttons.

Each icon gets a tooltip via LDLib2's tooltip API with the action name.

## Files Changed

### Modified

- **`duel_screen.xml`** — Add `#context-menu` element and CSS styles (`.ctx-action`, positioning, hidden state)
- **`LDLibDuelScreen.java`** — Replace `showActionMenu` with context menu logic:
  - New field: `contextMenu` (UIElement reference, looked up by ID)
  - New method: `showContextMenu(List<CardAction> actions, double mouseX, double mouseY)` — clears, populates icons, positions, shows
  - New method: `hideContextMenu()` — adds hidden class
  - New helper: `getActionIcon(int actionType)` — returns icon color/style and tooltip text for an action type
  - Update `onCardClicked` signature to `onCardClicked(int player, int location, int sequence, UIEvent event)` so it has access to the mouse position from the click event. All call sites (hand cards, zone slots) pass the event through. Extract mouse coordinates from the event to pass to `showContextMenu`
  - Update `onResponseSent`: call `hideContextMenu()`
  - Add root CLICK listener for dismiss-on-outside-click
  - Remove `showActionMenu` method

### Not Changed

- **`ClientDuelState.java`** — No changes. `cardActions`, `CardAction`, `buildIdleCmdActions`, `buildBattleCmdActions` all work as-is
- **`rebuildPrompt`** — SelectIdleCmd/SelectBattleCmd cases unchanged (they hide prompt overlay and refresh highlights)
- **`ResponseBuilder`** — `selectCmd` already exists
- **Prompt overlay** — Continues to serve all other prompt types (SelectCard, SelectYesNo, SelectPosition, SelectChain, etc.)

## Scope Boundary

This feature only replaces the action menu for cards with idle/battle command actions. All other prompt types continue using the centered prompt overlay.

# Field Selection Mode for On-Field Card Targeting

**Date:** 2026-04-11
**Status:** Approved

## Problem

When the ygopro-core engine asks the player to select a card that's on the field (e.g., choosing an attack target after declaring an attack), the UI currently shows a modal `buildCardSelectionPrompt` dialog. This is the same overlay used for selecting cards from piles (GY, banished, deck). For on-field cards, the player should instead click the card directly on the field, with highlighted targets and a minimal indicator bar — matching the EDOPro UX.

## Scope

- **In scope:** `SelectCard` prompts where all selectable cards are on the field (MZONE/SZONE). Minimal indicator bar. Right-click to cancel. Visual highlighting of valid targets.
- **Out of scope:** `SelectTribute` field selection (same pattern, future work). Pile selection prompt redesign (separate effort). Attack arrow rendering.

## Design

### Detection: `isFieldOnlySelection()`

A helper method checks whether all cards in a `SelectCard` have on-field locations using the existing `LOCATION_ONFIELD` constant (`LOCATION_MZONE | LOCATION_SZONE`):

```java
private boolean isFieldOnlySelection(DuelMessage.SelectCard sel) {
    return sel.cards().stream()
            .allMatch(c -> (c.location() & LOCATION_ONFIELD) != 0);
}
```

In `rebuildPrompt`, the `SelectCard` case branches:

```java
case DuelMessage.SelectCard sel -> {
    if (isFieldOnlySelection(sel)) enterFieldSelectionMode(sel);
    else buildCardSelectionPrompt(sel);
}
```

### Target Highlighting

New `.selectable` CSS class in `duel_screen.xml`, distinct from `.target` (cyan, used for "your cards with actions"). Uses a warm red-orange overlay matching the Attack context menu color:

```css
.selectable {
    overlay: rect(#66FF4444);
}
```

`enterFieldSelectionMode()` iterates `sel.cards()`, maps each to its field slot via `findSlotForLocation()`, and adds the `.selectable` class.

### Selection Indicator Bar

Reuses the existing `#status-label` element (absolute-positioned overlay at screen bottom, semi-transparent black background, amber text). In field selection mode:

- Text: `"Select N card(s)"` (derived from `sel.min()`/`sel.max()`)
- If cancelable, append `" (Right-click to cancel)"`
- Show by removing the `hidden` class

No new XML elements needed.

### Click Handling

The existing `onCardClicked` -> `handleCardSelection` path already handles field clicks during a `SelectCard` prompt. It matches the clicked `(player, location, sequence)` against the card list and auto-sends when `selectedIndices` reaches `max`. No changes needed to the click handler.

For single-target selection (attack targets: min=max=1), clicking a valid target immediately sends the response.

After the response is sent (when `selectedIndices.size() == sel.max()`), call `exitFieldSelectionMode()` to clean up highlights and hide the indicator. This requires adding the cleanup call inside `handleCardSelection`'s send branch.

### Cancel via Right-Click

Add a `CLICK` listener on the root element that checks `event.button == 1` (right mouse button). When in field selection mode and the prompt is cancelable, send `ResponseBuilder.selectCards()` (empty = cancel). If the prompt is not cancelable, right-click is ignored.

### Cleanup: `exitFieldSelectionMode()`

A single method called after successful selection or cancel:

1. Remove `.selectable` from all elements: `ui.rootElement.select(".selectable").forEach(e -> e.removeClass("selectable"))`
2. Hide the status label
3. Reset tracking state (clear `selectedIndices`)

Called from two places:
- After `handleCardSelection` sends a response (in the `selectedIndices.size() == sel.max()` branch)
- After right-click cancel sends the cancel response

## Files Modified

| File | Change |
|------|--------|
| `src/main/resources/assets/duelcraft/ui/duel_screen.xml` | Add `.selectable` CSS class |
| `src/main/java/.../client/LDLibDuelScreen.java` | Add `isFieldOnlySelection()`, `enterFieldSelectionMode()`, `exitFieldSelectionMode()`. Modify `rebuildPrompt` SelectCard case. Add right-click cancel listener. Call `exitFieldSelectionMode()` after selection completes. |

## Message Flow

```
Engine sends SelectCard (all cards on field)
  -> rebuildPrompt: isFieldOnlySelection() = true
  -> enterFieldSelectionMode():
       - Hide prompt overlay
       - Highlight valid targets (.selectable)
       - Show status label ("Select 1 card(s)")
  -> Player clicks highlighted monster
  -> onCardClicked -> handleCardSelection matches card
  -> selectedIndices reaches max -> sends ResponseBuilder.selectCards(...)
  -> exitFieldSelectionMode(): clear highlights, hide indicator
```

## Future Extensions

- **SelectTribute:** Same pattern — check if all tribute cards are on-field, highlight them, click to select. Needs multi-select with confirm since tributes often have min != max.
- **Attack arrow:** Render a line from the attacking monster to the cursor/target during field selection mode. Purely visual, additive to this design.
- **Pile selection redesign:** Modal prompt stays for non-field cards but will be redesigned with card images in a scrollable list (separate effort).

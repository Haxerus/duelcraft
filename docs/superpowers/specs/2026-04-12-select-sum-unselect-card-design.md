# SELECT_SUM and SELECT_UNSELECT_CARD Prompt UI Design

**Goal:** Add interactive UI for the two remaining Tier 1 prompt messages so players can select materials for Synchro/Ritual/XYZ/Fusion summoning and toggle card selections.

## Shared Pattern

Both prompts follow the same display approach:
- **On-field targets**: highlight with `.target` class, click directly on field card elements
- **Off-field targets** (hand, GY, banished, extra): show card elements in the prompt overlay with images and hover-to-inspect
- **Mixed locations**: field targets highlighted + prompt overlay for off-field cards simultaneously

Cards shown as actual `.card` elements with images, not text buttons.

## MSG_SELECT_UNSELECT_CARD (26)

**Gameplay:** Fusion/Link material selection, flexible "select up to N" effects. Player toggles cards on/off one at a time.

**Interaction model:** Incremental per-click loop. Each card click sends an immediate response to the engine. The engine re-evaluates constraints and sends a fresh `SelectUnselectCard` message with updated lists. The UI rebuilds naturally each cycle — no local toggle state needed.

**Binary format (already parsed):**
```
[u8 player][u8 finishable][u8 cancelable][u32 min][u32 max]
[u32 count1][CardInfo × count1]   — selectable (can toggle ON)
[u32 count2][CardInfo × count2]   — already selected (can toggle OFF)
```

**Display:**
- Selectable cards (list 1): clickable `.card` elements — on-field via `.target` highlights, off-field in prompt overlay
- Already-selected cards (list 2): shown with `.selected` visual indicator (e.g., green overlay) in the same prompt view
- Prompt title: `"Select Materials (N selected)"` or card-specific hint from the engine

**Actions:**
- Click selectable card → `ResponseBuilder.selectUnselectCard(index)` → sent immediately
- Click already-selected card → same response (engine toggles it off)
- "Finish" button: shown when `(finishable || cancelable) && count2 > 0` → sends `selectUnselectCardFinish()`
- "Cancel" button: shown when `cancelable && count2 == 0` → sends `selectUnselectCardFinish()`

**Response format:** Per-click: `[i32 1][i32 cardIndex]`. Finish/cancel: `[i32 -1]`.

## MSG_SELECT_SUM (23)

**Gameplay:** Synchro/Ritual/XYZ summoning where materials must sum to a target level/rank/value. Some cards are mandatory (must-select).

**Interaction model:** Batch selection with local validation. Player clicks cards to toggle selection, running sum updates in real-time, Finish button enables when the target sum is reached.

**Binary format (needs complete parsing — currently stores rawBody):**
```
[u8 player][u8 select_mode][u32 target_sum][u32 min][u32 max]
[u32 must_count][must_count × (u32 code, CardInfo loc, u32 opParam)]
[u32 select_count][select_count × (u32 code, CardInfo loc, u32 opParam)]
```

Where `opParam` encodes the card's value contribution:
- `select_mode=0`: single value — `opParam` is the card's level/rank/value
- `select_mode=1`: dual value — `op1 = opParam & 0xFFFF`, `op2 = (opParam >> 16) & 0xFFFF`. Card can contribute either value.

**Display:**
- Must-select cards: shown with a locked/mandatory indicator, always included in the sum, cannot be deselected
- Selectable cards: clickable `.card` elements with on-field/off-field split
- Running sum in prompt title: `"Select Materials (Level Sum: 4 / 8)"`
- Sum updates immediately on each local toggle

**Validation:**
- Client tracks selected cards and running sum locally
- For dual-value mode: each selected card contributes either op1 or op2 — pick whichever value is needed (simplified: try both, check if target is reachable)
- "Finish" button enables when `sum == target && selectedCount >= min && selectedCount <= max`

**Response format:** Standard card selection: `ResponseBuilder.selectSum(indices...)` — same format as selectCards.

## CSS Addition

```css
.selected {
    overlay: rect(#6600FF00);
}
```

Green overlay for selected/toggled cards, similar to `.target` (cyan overlay for valid targets).

## Files Modified

| File | Changes |
|------|---------|
| `DuelMessage.java` | New inner record `SumCard(int code, int controller, int location, int sequence, int position, int opParam)`. Replace SelectSum rawBody with parsed fields: `player, selectMode, targetSum, min, max, mustSelect (List<SumCard>), selectable (List<SumCard>)` |
| `MessageParser.java` | Complete SelectSum parsing (currently stores raw bytes) |
| `DuelMessageCodec.java` | Encode/decode for updated SelectSum with SumCard lists |
| `ClientDuelState.java` | Build card actions for both prompts — map card locations to action indices |
| `LDLibDuelScreen.java` | New `rebuildPrompt` cases: SelectSum (local toggle + running sum) and SelectUnselectCard (per-click immediate response). Prompt overlay shows card grids with images. |
| `duel_screen.xml` | `.selected` CSS class |

## Testing

- MessageParser tests for SelectSum with known binary payloads (must-select + selectable cards)
- Manual verification: Synchro summon (SelectSum), Fusion/Link summon (SelectUnselectCard)

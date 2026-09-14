# Duelcraft UI design guide

## Scope and visual direction

Keep the existing Minecraft font, dark beveled field, card artwork, zone order, and 960 × 540 design canvas. This is a layout and legibility pass over implemented duel UI, not a new visual identity. The companion [HTML mockup](ui-design-mockup.html) illustrates the component rules; LDLib2 screenshots are the rendering authority.

Source baseline: main at `ed0bf02`, inspected existing captures in `build/ldlib2-uitest/screenshots` on 2026-09-13. The directory contains 22 captures across 21 scenarios, while its latest report covers only six scenarios: old images alone do not establish current coverage.

## Observed defects

- `duel_announce_number`: four choices extend past the dialog edges.
- `duel_pause_menu`: title is visibly right of the dialog center.
- `duel_announce_race`: selection caption is off center; Spellcaster nearly touches its button edges. Only three races are exercised, hiding the full-list overflow.
- `duel_sort_card`: lengthy heading needs wrapping instead of a single fixed-height line.
- `duel_announce_card`: search results are tightly packed; long names need a bounded, wrapping list.
- `duel_feedback`: the LP delta and log title share the upper-left area.
- Field screenshots: long card text contains visible carriage-return glyphs; metadata needs wrapping and the description must remain scrollable.

## Tokens (design units, before canvas scaling)

| Token | Value | Use |
|---|---|---|
| Dialog surface | `#1A1A2E` | Preserve the existing indigo dialog surface |
| Dialog edge | `#4444AA` | Existing one-unit outline |
| Primary text | `#FFFFFF` | Titles and actions |
| Secondary text | `#CCCCCC` | Explanations and metadata |
| Selection | existing green overlay | Checked choices and selected cards |
| Spacing | 2, 4, 8, 12 | Internal details, related controls, sections, dialog inset |
| Prompt body text | 8 | Choice descriptions and buttons |
| Card description | 9 | Preserve the existing card-panel text size |
| Title | 9 | Prompt, pause and panel titles |
| Result title | 12 | End-of-duel outcome |
| Action height | 20 | Short dialog actions |
| Action width | content + padding, minimum 32 | Avoid large Yes/No/number buttons |

## Component rules

1. Dialogs are centered in the design canvas. Standard prompts are 280 units wide; small pause/result/message dialogs retain 200. Contents stay inside the surface with at least 8 units of inset. Titles occupy the full content width, center their text, wrap, and grow vertically.
2. Short actions use content width and 6 units of horizontal padding. Rows center and wrap with 4-unit gaps. Button text is vertically centered. A fixed width is appropriate for a deliberate grid, not arbitrary prose.
3. Effect descriptions use a vertical, scrollable choice list. Labels wrap and remain readable in full. Number/race/attribute choices use a wrapping grid. Dense choice areas have a bounded height and scroll; the dialog must never grow beyond the canvas.
4. Card choices keep their aspect ratio and use a horizontal scroller. Rotated defense cards fit their columns; captions have explicit centered width. Many cards must scroll rather than compress. Both hands center their cards when they fit. Overflowing hands retain their full first-to-last scroll range, and shrinking a scrolled hand recenters its remaining cards without changing the board width.
5. Side panels keep the existing 210-unit footprint. Titles, metadata and card names wrap; body text scrolls. Close actions size to their labels and center. Normalize CRLF card text before rendering. Allow 500 ms to move from a card to its details panel, then keep the panel open while hovered. Card details sit above the selection dimmer so their scrollbar remains usable; result, message and pause overlays sit above the details. Opening the log hides card details.
6. HUD feedback has separate vertical space: player hints start at 20, LP changes at 30 and side panels at 44 units from the top. Footer offsets from the bottom are toast 4, prompt action 24, status 48, chain depth 62 and banner 76. Feedback labels grow to fit their font and padding. Auxiliary controls must not move the zone grid or obscure selectable zones.
7. Reveal and pile lists use four fixed card columns, filled left to right and then down. Distribute spare horizontal width between the columns, with balanced outer insets; sparse rows and incomplete final rows retain the same column positions and card sizes as full rows. A 68-unit control panel sits outside the board flow beside the hand's lower-right corner, with four-unit padding and gaps. Chain, Log and Concede stay at fixed positions; optional Shuffle and Hide/Show Prompt appear above them. Separate Concede with a divider and use the leave-duel confirmation dialog. The panel stays clear of the right-side inspector. Showing optional controls must preserve the field, hand, zone, phase-control and permanent-button bounds. The top HUD contains only player/LP and turn information.
8. Visible siblings must not overlap unless they are intentional card badges, field backgrounds, dimmers, or stacked modal overlays. Scroller content is intentionally larger than its viewport, but the viewport and action footer stay inside the owning surface.
9. Support MR3, MR5 and Speed layouts at GUI scales 2–4, 1280 × 720 and 1920 × 1080 windows. GUI scaling must preserve these proportions and pointer hit targets. A 1024 × 768 window is an additional aspect-ratio check.

## Prompt controls

- **Hide Prompt / Show Prompt:** hide the dialog and its dimmer while retaining its choices. Card details and zone inspectors remain available; field clicks and RMB cannot change or answer the hidden prompt. A new prompt or retry shows the dialog again. Field-only prompts already expose the board and do not need this button.
- **Chain: ON / Chain: OFF:** ON is the default for each duel. OFF automatically passes optional chain prompts; forced chain choices and all other prompts still appear. The saved setting survives reopening the duel screen.
- **Hold C:** temporarily skip optional chains. The dock reads **OFF (hold C)** until release. Typing, modifier combinations, loss of window focus, and leaving the screen clear or suppress the hold. Releasing C restores the saved setting. RMB keeps its existing Cancel/Finish/back behavior.
- Render the dock above the prompt dimmer, but below the leave-duel, result, and message overlays. LDLib2 sorts rendering by sibling subtrees, so the dock belongs to the canvas alongside these overlays; a layout listener anchors it to the field bounds.

## Acceptance and evidence

Automated geometry checks cover surface containment, sibling separation, text bounds and title alignment; interaction checks cover selection, scrolling and closing. Checks operate in GUI space with one pixel of rounding tolerance. Tests use synthetic messages and do not assert downloaded names or artwork. Use injected display text for long-text stress cases.

Inspect every resulting capture, including intermediate states. Automated bounds cannot prove glyph centering, visual hierarchy, scrollbar usability or texture quality. Preserve reports and screenshots for each tested resolution under `build/ui-review/`, because the harness clears its output at each launch. Record failures and the final verification commands in the implementation plan.

## Coverage sources

- `docs/engine-gap-analysis.md` §§1–2, 8, 12: all prompt types, HUD feedback, chains, hints and inspectors.
- `docs/engine-wiki/README.md`: router for semantic contracts; this pass preserves responses and engine behavior.
- `PromptController`, `LDLibDuelScreen`, `ZoneInspectorController`, `FieldRenderer`, `DuelScreen`: current implemented surfaces override older wiring-guide examples.
- `docs/Duelcraft Feature Checklist.md`: home screen, collection, deck builder and packs are future UI and outside this audit.

# Collection controls QA.3 design

Approved in conversation on 2026-10-01 after interactive mockup review. The mockup guides changed controls, not a replacement for the complete editor.

## Scope

- Preserve the dark 1280x720 editor: inspector left, deck centre, card browser right. Preserve the in-game browser, rich filters, artwork, Deck/Side routing, transfer controls and quiet empty inspector.
- Heading: **My Collection**. Compact editable deck name without an explanatory label. Short active/saved/unsaved state must fit without clipping.
- Top-level New deck, My decks, Import / export, Help (`?`) and Close. Save and Use for duels remain available with existing authoritative state/pending guards.
- My decks becomes a selectable scrollable saved-list view, showing at least five complete entries. Selecting a row does not immediately replace the draft; Open does. Indicate the active list. Open, Make a copy and Delete act on the selected saved list. Keep one close control (`X`), not a duplicate Done button. Preserve a way to clear activation without bringing back the crowded menu.
- Import/export is separate: a dropdown of YDK files from `<instance root>/duelcraft/decks` with a normal-sized import action, then a normal-sized export action below. No OS file picker, folder caption or explanatory subtitles. Preserve safe filename handling, overwrite confirmation, ordered Main/Extra/Side export and the existing import authority.
- Help owns instructional text, ownership explanation and supported-check information. No routine explanatory footer or success narration for ordinary editing. Keep actionable errors, policy denial details, necessary pending state and confirmations. Short persistent state such as Active and Unsaved changes remains useful.
- Save changes uses concise copy and Save / Discard / Keep editing. Preserve empty-unsaved bypass, failed-save draft retention and deferred navigation. Esc closes the topmost dropdown/dialog/submenu before the screen.
- Dropdowns with enough choices show at least five complete entries before scrolling, bounded by the screen.
- Sort retains existing type ordering and groups identical exact passcodes consecutively within each section. Preserve first-seen order between different cards of the same type; sorting twice is idempotent. No cards cross Main/Extra/Side boundaries or change quantity.

## Boundaries

Ownership remains SERVER-default-false and exact-passcode-based when enabled. No changes to collection authority, transfers, busy checks, companion policy, preparation, payloads or protocol 7. Drafts remain savable without ownership. Acquisition stays outside core.

Final Countdown / Gold Sarcophagus indicators, pre-errata alias limits, Question restrictions and summon-method selection are separate future passes. M5 and M6 are not implemented by this work. No merge or push.

## Workflow and acceptance

Use mockup agreement before production UI refinement; focused tests and real UI scenarios while iterating; full regression once the batch is settled. User review of actual in-game spacing is the next visual acceptance checkpoint. Preserve historical QA.1/QA.2 artifacts and evidence. User reported omitted QA.2 checklist items passed; that is manual evidence and does not erase the historical automated GLFW blocker.

Reference mockup: `C:/Users/haxer/.codex/visualizations/2026/09/19/01a0ba1a-0440-7361-bedc-6b0ba6073c72/collection-refinement.html`.

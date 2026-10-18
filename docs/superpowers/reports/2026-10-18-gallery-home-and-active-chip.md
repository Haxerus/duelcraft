# Gallery Home, saved-deck chip and field refinements

## Accepted playtest scope

The user reported no issues with editing, filtering, import/export, live dueling, lobby controls, inspector/log controls or resizing. Carry that acceptance forward. These refinements address the remaining presentation feedback; multiplayer acceptance remains pending and nonblocking.

On 2026-10-18 the user tested the final updated JAR identified below, reported that everything looks good, and requested a PR into `main`. The focused manual recheck is accepted. Remaining [multiplayer and associated developer checks](../../testing/player-interaction-manual.md) remain pending and nonblocking.

## Changes

- Home artwork and its fade now render across the entire window, separately from the centered 1280×720 control canvas. Aspect-preserving cropping and the missing-art fallback remain. This removes side bars in wider window proportions without moving the controls.
- My decks has a fixed-size, inset Active chip inside the saved entry's continuous surface. Hover or keyboard focus changes it to red Clear. The separate Clear active row is removed. The tooltip says “Clear active deck”. During review the user requested sharp corners to match the other Gallery controls; the final chip uses zero corner radius in every state.
- Clear uses the existing revisioned request and acknowledgement path. It does not select the row, remove saved lists or clear activation optimistically. Pending operations disable the chip. My decks supports Tab/Shift+Tab traversal and Enter/Space actions; higher confirmation layers prevent background keyboard actions.
- The field surface has a one-unit Gallery Control edge outline. Its dimensions and transparent world backdrop are unchanged.

## Verification record

The initial wide-window run at 1920×1040 / GUI scale 3 passed Home, Collection and duel-panel scenarios. The new chip scenario stopped because the test tried to release its opening click on a control occluded by the popup. It was changed to the existing modal-safe input helper.

The subsequent input run exposed delayed hover-label changes: LDLib listeners could observe the previous hover state. The implementation now consumes enter/leave intent directly, as it does focus events. That run also identified two assertion issues: rectangle record equality compared corner-array identity, and a selection-color check observed the newly supported focus outline. The checks now compare numeric bounds and inspect selection with focus cleared. These failed runs remain under `build/gallery-production/refinements-wide-2026-10-18` and `refinements-input-2026-10-18`.

Final verification:

- `refinements-final-2026-10-18`: 1280×720 / GUI scale 2; all four affected scenarios passed, 5884/5884 checks. This run preceded only the requested corner-radius change.
- `refinements-square-chip-2026-10-18`: final square-corner styling at 1024×768 / GUI scale 2; `collection_active_chip` and `duelcraft_home` passed, 43/43 checks. Mouse, Enter and Space each sent one revisioned clear request, retained row selection, waited for acknowledgement, and preserved both saved rows.
- `refinements-build-2026-10-18`: `build -PskipNative` passed; 1113 JUnit cases, 1112 passed, one existing Windows symlink-privilege skip, zero failures/errors. Each final archive records no runtime-source changes during execution. Existing native DLLs were reused.
- Native captures opened for review: wide Home featured artwork, wide field/long-card-info, wide active-deck popup; 1280 chip rest/hover and keyboard focus; final 1024 square chip rest and keyboard focus, plus Home featured artwork. The latter confirms full-window coverage at 4:3, complementing the wide-window proof. No full unrelated UI-suite or multiplayer rerun was performed.
- PR preparation rechecked `build -PskipNative` at `d395fea`: successful with all 15 tasks up-to-date and no runtime-source changes (`pr-build-2026-10-18`). This reused the passing test results above; it was not a fresh test execution. Fresh `origin/main` (`ef0599d`) is already an ancestor of the branch, and `git diff --check` passed.

Updated candidate: `build/manual-test/duelcraft-gallery-playtest-fix-2.jar`.
SHA-256: `FC63D04F20F836089A61162ED9818AA880A491743F3B49A153908DC04CFACEE4`.
The packaged layout was checked for the square-corner style, and both Windows native libraries and SQLite were confirmed present. Replace the previous Duelcraft JAR and retain LDLib2 2.2.39.a. Internal mod version remains `1.0.0-m6-qa.2`; filenames distinguish the playtest candidates.

## Focused manual recheck

1. Open Home in the window size that previously showed side bars, then resize it. Artwork should reach the window edge without stretching; menu controls should remain readable.
2. Open My decks. Check the integrated gold Active chip and the absence of the separate Clear active row.
3. Hover the chip, move away, then Tab to it. Active/Clear should switch without shifting the chip or deck name. Use the mouse, Enter or Space to clear activation; the saved deck must remain. Selecting another row first should not make Clear target that other deck.
4. Open a duel and inspect the thin field outline against your world. The surroundings should remain visible.

No need to repeat the already accepted broad functionality checklist for this presentation pass.

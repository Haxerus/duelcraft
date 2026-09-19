# Collection editor playtest fixes — 2026-09-18

Follow-up to M2 on `codex/player-collections`, based on `658283a`, implemented in `b0bd4e5`. These changes address the eight issues found in manual playtesting while retaining the approved dark duel UI styling.

## Changes

- **Deck / Side controls:** Deck additions route Fusion, Synchro, Xyz and Link cards to Extra; other cards go to Main. Pendulum alone does not imply Extra. Existing misplaced or unknown saved copies remain removable from their actual section. Saved drafts remain unrestricted card lists.
- **Scrolling artwork:** mounted virtual rows apply cached textures immediately instead of waiting for the next client tick. Requests remain bounded; polling still handles later downloads.
- **Dropdowns:** editor styles no longer cover internal selector overlays. Rows have sufficient text height and retain hover scrolling for long names. Popups inherit the root scale and are positioned before drawing in the shared 1280×720 design coordinates.
- **Selection:** cards use a border highlight; selected mode/filter buttons use a dark pressed appearance instead of a green overlay.
- **Header:** Saved lists and Save list have equal width and height.
- **Scrollbars:** internal arrow buttons retain compact dimensions instead of inheriting the editor's action-button height.
- **Inspector:** a separate `getCardArt` supplier provides square 192×192 cropped artwork. Deck and collection tiles retain full-card images.
- **Tokens:** search excludes token types, including exact-passcode and ownership searches. Existing saved data is not deleted.

The dropdown correction is collection-local. Pinned LDLib2 2.2.39.a portals selectors to the UI root and clamps using unscaled geometry. Shared-root positioning avoids both the incorrect clamp and dependence on render-time transform caches. No dependency changes were needed.

## Verification

- Full `gradlew.bat test --console=plain`, with normal native prerequisites: **858 tests / 51 suites, zero failures, errors or skips**, BUILD SUCCESSFUL in 58 seconds.
- Actual dropdown mouse input at **1920×1080 / GUI 3** and **1280×720 / GUI 2**: **19/19 checks each**, including all four selectors, bounds/hitboxes, header dimensions, compact arrows and separate inspector art.
- Overflow scenario: **88/88 checks**. In the 16,000-card fixture, real wheel input advances the scroller and replaces mounted tiles; every replacement already has its cached texture in the same input step, before another tick.
- Existing layout, search and saved-list scenarios passed. The real save/read/activation scenario passed after its fixture was updated for distinct Main and Extra cards. These results span separate runs, not one all-green combined run.
- Independent review's long-name scrolling and first-frame popup findings were resolved. No findings remain open.

Root inspected the open dropdown at 1920×1080 and saved-list modal at 1280×720. Deterministic artwork fixtures use a cached card back and a distinct square block texture; they do not establish how every downloaded card image looks.

Evidence lives under ignored `build/playtest-fixes/`, including green scale/overflow reports, earlier failures, review notes, the implementation report and final full-test XML/HTML. See the implementation report for individual run details and evidence limitations.

No M3 work, persistence changes, physical transfers or duel-enforcement changes are included. The branch remains local and unmerged.

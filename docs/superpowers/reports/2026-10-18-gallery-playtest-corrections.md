# Gallery playtest corrections — October 18

The first installed-JAR feedback accepted the Gallery direction and identified two presentation regressions. The Collection inspector loads cropped artwork, but a Gallery override gave its tile portrait dimensions (140×204). It now uses 204×204 while keeping the surrounding layout height. The live duel had gained both a full-window fill and an opaque canvas fill; both were removed so the Minecraft world remains visible around the board. Board, reading-panel and control surfaces retain Gallery styling.

The Collection proof previously supplied full-card images to the inspector as well as the grids. Its normal screenshot now uses a square artwork crop from the local fixture, and a bounds assertion checks the rendered artwork is square. Production still uses its existing separate cropped-art download channel. The style guide now records both the square-art and transparent-duel requirements.

## Verification

- `playtest-fixes-2026-10-18`: real-client `gallery_collection,duel_panel_layout` at 1280×720, GUI scale 2; 2/2 scenarios, 5845/5845 checks passed.
- `playtest-cropped-proof-2026-10-18`: after correcting the fixture's artwork, `gallery_collection` passed 3717/3717 checks. No production code changed between these runs.
- Opened and reviewed the corrected square-art screenshot and the live-duel screenshot showing the loaded Minecraft world behind the interface. The initial capture with a full-card fixture is retained as superseded evidence.
- `assemble -PskipNative` succeeded. Existing Windows DLLs were reused. No full JUnit or full UI-suite rerun was needed for these scoped presentation corrections; prior results remain historical evidence.
- Runtime source was unchanged during each recorded UI run. Reports and screenshots are retained under `build/gallery-production/` in the named archives.

Candidate: `build/manual-test/duelcraft-gallery-playtest-fix-1.jar`. Replace the previous Duelcraft JAR; retain the same LDLib2 dependency. Internal mod version remains `1.0.0-m6-qa.2`.

SHA-256: `6F8381A7031A50D3138E721B188BD5F04165FEF5038FA570A2ABC2A8BB38CCED`.

Recheck the Collection inspector with Oasis of Dragon Souls or another loaded artwork, then open a duel in your world and confirm the surroundings remain visible. Continue the existing checklist; multiplayer remains carried forward.

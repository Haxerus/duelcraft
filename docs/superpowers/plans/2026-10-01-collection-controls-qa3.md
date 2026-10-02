# Collection Controls QA.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement the approved collection-control refinement and identical-card sorting without changing the existing browser or server policy.

**Architecture:** Extend the existing CollectionController/SavedDeckController and XML layout. Keep DeckEditorModel sorting local and pure. Reuse existing YDK services, authoritative collection requests and UI harness.

**Tech Stack:** Java 21, NeoForge 21.1.224, LDLib2 XML/LSS, JUnit, Gradle on Windows x64.

**Spec:** `docs/superpowers/specs/2026-10-01-collection-controls-qa3-design.md`

## Global Constraints

- Work only in the existing `deck-editor` worktree on `codex/qa-m4`; starting HEAD `4fe1885`. Preserve later user changes.
- Preserve the dark 1280x720 editor: inspector left, deck centre, card browser right; browser filters, artwork, transfers and Deck/Side routing remain intact.
- Ownership remains SERVER-default-false and exact-passcode-based when enabled. Preserve policy, busy/revision guards and protocol 7.
- Import only from `<instance root>/duelcraft/decks`; no OS picker. Keep safe export/overwrite and ordered sections.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Run focused checks during iteration; full regression once the batch is settled.
- Do not merge, push, modify historical QA artifacts, implement other card fixes, or begin M5/M6.

## Review Focus

- Duplicate display names and more than five saved decks: selection uses UUID, list scrolls, Open targets the selected row.
- Dirty draft plus Open/New/Import/Close: save acknowledgement gates navigation; failed save retains work; empty new draft closes quietly.
- Nested selectors/export/overwrite/help: Esc dismisses the topmost surface and preserves the editor.
- Long names and policy errors at supported scales: controls stay in bounds and errors remain available without routine caption clutter.
- Pending mutation/empty imports/deleted selection: actions remain guarded, no stale selection changes a different saved deck.

### Task 1: Group identical cards in type sorting

**Files:** Modify `src/main/java/com/haxerus/duelcraft/client/collection/DeckEditorModel.java`; test `src/test/java/com/haxerus/duelcraft/client/collection/DeckEditorModelTest.java`.

**Interfaces:** Preserve `sortByType(IntUnaryOperator cardType)`, `draft()`, `dirty()` and existing frozen guard. No new public API.

- [x] Run the existing focused test as baseline, then add a regression with interleaved same-type cards in Main, Extra and Side. Example all-spell Main `[7,8,7,8,7]` must become `[7,7,7,8,8]`, while Side `[8,7,8]` becomes `[8,8,7]`. Assert per-section multisets, unchanged ownership, dirty flag, idempotence after markSaved, empty sections and unknown types.
- [x] Run `./gradlew.bat test --tests '*DeckEditorModelTest' --console=plain` and record the expected new regression failure.
- [x] Implement minimal per-section stable grouping: build first-seen indices for that section; compare `typeOrder(cardType.applyAsInt(code))`, then the section's first index for that exact passcode. Do not replace grouping with a global numeric/name sort.
- [x] Rerun the focused test, inspect the diff, commit only task files, and report command/output evidence.

### Task 2: Refine collection menus and controls

**Files:** `src/main/resources/assets/duelcraft/ui/collection_screen.xml`, `src/main/resources/assets/duelcraft/lang/en_us.json`, `src/main/java/com/haxerus/duelcraft/client/collection/CollectionController.java`, `SavedDeckController.java` only where needed, affected collection tests/scenarios under `src/test/java/com/haxerus/duelcraft/client/collection/` and `src/main/java/com/haxerus/duelcraft/client/uitest/`.

**Interfaces:** Reuse SavedDeckController navigation/save/request methods, CollectionController selector/modal wiring, DeckImportService and DeckExportService. Keep server commands/payload shapes unchanged. Task 1's `sortByType` signature is unchanged.

- [x] Read controller, XML, saved-deck tests/scenarios and `docs/ldlib2-ui-guide.md` / `docs/ui-wiring-guide.md`. Map existing controls and pending-state paths before editing. Keep changes within current classes; no new generic UI framework.
- [x] Add/update meaningful scenario checks before implementing: at least six saved decks with duplicate names/different UUIDs, explicit Open, dirty navigation/cancel/failure, empty new close, nested Esc, empty import list, and long name/busy state. Use existing `ScenarioBuilder` clicks/checks and real controller requests. Update only obsolete assertions affected by approved text/control changes.
- [x] Implement compact header and separate top-level navigation. Replace `list-picker` with a scrollable saved list and explicit Open. Keep selection separate from the currently edited list; selected-row copy/delete must use the selected saved list. Preserve clear-activation access as a context action near active status. Use one X on My decks.
- [x] Move existing import picker/export launch into a separate modal: dropdown plus Import YDK row, Export YDK row below; normal button sizes. Retain safe exporter/overwrite dialog. Add Help modal with ownership and supported-check text. Remove routine helper captions and edit-success narration; preserve actionable failures and eligibility details.
- [x] Configure selectors for at least five visible rows where enough entries exist. Update Esc handling for all new surfaces. Use concise Save changes / Save / Discard / Keep editing wording and short active status. Preserve complete browser, inspector, deck body and transfer behavior.
- [x] Run focused unit tests: `./gradlew.bat test --tests 'com.haxerus.duelcraft.client.collection.*' --console=plain`. Run focused real UI scenarios at `1280x720` scale 2 and `1920x1080` scale 3 using `-PldTest=<affected scenario names> -PldTestWindow=<size> -PldTestGuiScale=<scale>`. Archive fresh output/screenshots under `build/evidence/collection-controls-qa3/`; inspect screenshots. Report actual failures/blockers without substituting old evidence.
- [x] Self-review, commit scoped source/tests, and write full test/evidence report. Do not run the entire suite during visual iteration.

### Task 3: Regression, review and playtest checkpoint

The [QA.3 verification report](../reports/2026-10-01-collection-controls-qa3-verification.md) preserves the initial and intermediate UI failures, final fixes, passing group, source provenance and package checks. User visual and packaged two-client acceptance remain open.

**Files:** New `docs/superpowers/reports/2026-10-01-collection-controls-qa3-verification.md`; update this plan and `docs/superpowers/handoffs/2026-09-17-deck-editor-implementation.md`. Packaging may update `gradle.properties` and add `docs/qa-m4-qa3.md` using established QA packaging procedure; preserve QA.2 files.

**Interfaces:** Consume task commits and fresh reports; retain protocol 7 and pinned dependencies.

- [x] Run full `./gradlew.bat build --console=plain` at the QA.3 version checkpoint. Parse actual XML counts and preserve logs. Run the complete `group:duelcraft` UI harness against final source; retain initial failures and fix only regressions through the implementer. The final-source UI group passed; the earlier full unit build was not repeated after review fixes.
- [x] Obtain independent whole-change and scoped fix reviews from starting HEAD, fix substantive findings through the implementer and run covering checks. Do not repeat successful unrelated suites without changed code or an unresolved concern.
- [x] Record source commits, test counts, real screenshot inspection and limitations in verification report. Include concise manual checks for My decks scrolling/selection, New/save/close, folder-limited import/export/overwrite, Help/Esc, identical sorting, intact filters/artwork and pending errors. Record previous omitted QA.2 items as user-reported passes, distinct from historical automation failures.
- [x] Produce a local versioned QA.3 JAR/package after automated checks, following the existing QA.2 package procedure. Verify JAR version/protocol, native dependencies and hashes; no upload. Mark packaged runtime and user visual acceptance pending.
- [x] Update checkboxes/handoff, commit scoped documentation/build changes and report remaining manual checkpoint. No merge or push.

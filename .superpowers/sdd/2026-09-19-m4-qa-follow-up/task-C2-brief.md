# QA Collection Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make deck editing, saving, dismissal and YDK export predictable while preserving authoritative collection policy.

**Architecture:** Keep `DeckEditorModel` responsible for draft operations and `SavedDeckController` responsible for identity, persistence and navigation. Route every editing gesture through those existing boundaries; add a pure YDK writer and a small client-local export service.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, Windows x64.

**Spec:** [Approved QA follow-up design](../specs/2026-09-19-m4-qa-follow-up-design.md). Execution/checkpoint owner: [QA index](2026-09-19-m4-qa-follow-up.md).

## Global Constraints

- Work in `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`, baseline `codex/qa-m4` at `1e95c277a1de7c8a014b77df1bd7df7744ee8365`; preserve subsequent changes.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Keep Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a and Windows x64.
- Preserve the dark duel UI, 1280x720 editor, rich filters, Deck/Side routing, stable sorting, quiet empty inspector and centered feedback.
- Core SERVER `requireCardOwnership` defaults false. Enabled ownership counts deposited exact-passcode copies across Main/Extra/Side. Supported legality and companion restrictions apply in either mode. Draft saving stays permissive. Preserve authentication, revisions, busy checks and atomic transfers. Deposits never auto-activate or create copies.
- Protocol baseline 7 changes only for incompatible payload shapes. No native/dependency upgrade, M5/M6 implementation, merge or push. Preserve historical evidence and leave blocked runtime checks open.

## File map

Production Java paths below use `src/main/java/com/haxerus/duelcraft/`; test paths use `src/test/java/com/haxerus/duelcraft/`. Resource paths are written in full. Tasks are sequential because they share the controller and layout.

| Files | Responsibility |
|---|---|
| Modify `client/collection/DeckEditorModel.java` | Copy cap and index-specific removal |
| Modify `client/collection/CollectionCardGrid.java`, `CollectionController.java`, `CollectionScreen.java` | Gestures, lifecycle wiring, Escape and simplified management |
| Modify `client/collection/SavedDeckController.java` | One-time initial load, discard decision, snapshot and explicit save lifecycle |
| Create `collection/DeckListWriter.java`, `client/collection/DeckExportService.java` | Pure YDK serialization and contained client file output |
| Modify `src/main/resources/assets/duelcraft/ui/collection_screen.xml`, `src/main/resources/assets/duelcraft/lang/en_us.json` | Controls, dialog layout and feedback |
| Extend tests `client/collection/DeckEditorModelTest.java`, `SavedDeckControllerTest.java`; create `collection/DeckListWriterTest.java`, `client/collection/DeckExportServiceTest.java` | Focused model/lifecycle/filesystem coverage |
| Create `client/uitest/CollectionEditorQAScenario.java`, DEV_ONLY name `collection_editor_qa` | Real pointer, dialog and save-flow regressions |
| Extend `client/uitest/CollectionSavedDeckScenario.java`, `CollectionPreparationRetentionScenario.java`, `CollectionPlaytestScale2Scenario.java`, `CollectionPlaytestScale3Scenario.java`, `CollectionOverflowScenario.java` as affected | Existing saved-list, restoration and layout acceptance |

Read existing `DeckImportService`, `DeckListLoader`, `SavedDeckControllerTest`, `CollectionWidgetPlaytest`, `CollectionFixture` and `CollectionRuntimeFixture` before wiring. LDLib2 source is `C:/Users/haxer/Documents/Programming/Modding/LDLib2`, not the nested worktree's sibling. Its `UIEvents.DOUBLE_CLICK`, `UIEvent.button` and propagation methods already exist. Verify their actual dispatch behavior before adding handlers.


## Task C2: Open the active list and make dismissal layered

**Interfaces:** Add `public void openInitial(ClientCollectionState.View view)` and `public boolean needsDiscardConfirmation()` to `SavedDeckController`. Add `public boolean dismissTopLayer()` to `CollectionController` (true only when one child layer was dismissed). Keep `requestClose()` as the common top-level navigation path. `CollectionScreen.keyPressed(int,int,int)` consumes Escape explicitly.

- [x] Add tests to `SavedDeckControllerTest`: initial active saved ID loads once with its exact contents; null active ID starts empty; missing/deleted/failed active read reports failure without selecting some other list; delayed read cannot overwrite edits, another selection, a newer view, or disposal. Resume refresh preserves a dirty suspended draft. Use the existing request futures/read-token fixture and assert request identity and resulting UUID/cards, not just captions.
- [x] Add a separate discard-decision test so an empty unsaved draft stays savable:

  ```java
  @Test void emptyUnsavedDraftNeedsNoDiscardDecision() {
      var c = productionController();
      c.applyView(new ClientCollectionState.View(4, Map.of(), List.of(), null));
      c.newDraft();
      assertTrue(c.dirty());
      assertFalse(c.needsDiscardConfirmation());
      model.add(DeckEditorModel.Section.MAIN, 7);
      assertTrue(c.needsDiscardConfirmation());
      model.removeAt(DeckEditorModel.Section.MAIN, 0);
      assertFalse(c.needsDiscardConfirmation());
  }
  ```

  Also assert that an acknowledged saved list emptied or renamed remains subject to the normal dirty decision, and a pending save defers navigation until its actual success. Failure keeps the draft and editor. Existing stale-callback/disconnect tests remain required.
- [x] Run `./gradlew.bat test --tests '*SavedDeckControllerTest'`; confirm behavioral failures before the lifecycle changes.
- [x] Implement `openInitial` as a one-shot startup operation: create the blank draft, apply the initial view, and read its active ID using the existing `select` guards if nonnull. For the no-initial-view startup path, perform one refresh and feed that result through this method. Do not put auto-selection in ordinary `applyView` or `resume`. Preserve catalog-loading independence. Implement the decision predicate independently of `dirty()`:

  ```java
  if (!stored && model.draft().main().isEmpty()
          && model.draft().extra().isEmpty() && model.draft().side().isEmpty()) {
      return false;
  }
  return dirty();
  ```

  Use it only for navigation/discard decisions. Keep pending-save gating before this decision and retain existing save acknowledgement snapshots/revisions.
- [x] Add a visible Close control in the collection header and visible dismissal controls on child dialogs. Implement `dismissTopLayer` against actual owned controls in visual stacking order: an open selector/dropdown first; then the topmost confirmation/export/delete dialog; then My decks/eligibility/filter subview. Dismissing the unsaved-changes dialog means Keep editing and cancels the queued navigation. Dismissing an overwrite dialog cancels only export. Do not discard the draft on child dismissal.
- [x] Override Escape handling so Minecraft's default screen-close branch cannot run for the same event after a child closes:

  ```java
  @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
          if (!controller.dismissTopLayer()) controller.requestClose();
          return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
  }
  ```

  Verify actual LDLib2 selector-dialog ownership/visibility; closing a dropdown must not leave its input capture or focus behind. Use existing dialog close APIs, not just hiding the wrong parent. Main Close and top-level Escape share `requestClose` and server-pending safety. Do not cancel an in-flight server save when merely cancelling deferred navigation.
- [x] Extend `collection_editor_qa` with nested dropdown→My decks→editor Escape presses, delete/cancel, dirty/empty/saved-empty cases and delayed save success/failure. Use actual key events and press/release input. Rerun focused unit tests, `./gradlew.bat runClient -PldTest=collection_editor_qa`, and `./gradlew.bat runClient -PldTest=collection_preparation_retention`. Expected: one layer per Escape, active list only on fresh opening and retained dirty draft after suspension. Commit `fix: open active decks and close editor layers predictably`.

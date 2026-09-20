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

## Task C1: Enforce the manual copy cap and add pointer editing

**Interfaces:** Change `DeckEditorModel.add(Section,int)` and `addToDeck(int type,int code)` to return `boolean` (true only when added). Add `public boolean canAdd(int code)` and `public boolean removeAt(Section section, int index)`. Existing calls that ignore the new return remain source-compatible but controller feedback must consume it. Keep bulk loading/import/constructor behavior separate from manual additions. Extend grid callback wiring with an `IntConsumer` add action; existing single-click selection keeps its meaning.

- [x] Add these concrete failing tests in `DeckEditorModelTest`:

  ```java
  @Test void capCountsAllSectionsWithoutChangingImportedDrafts() {
      var cards = new DeckList(List.of(7), List.of(7), List.of(7));
      var model = new DeckEditorModel(cards, Map.of());
      assertFalse(model.add(DeckEditorModel.Section.SIDE, 7));
      assertEquals(cards, model.draft());
      assertFalse(model.dirty());
      assertTrue(model.add(DeckEditorModel.Section.MAIN, 8));
      var imported = new DeckList(List.of(7, 7, 7, 7), List.of(), List.of());
      model.load(imported);
      assertEquals(imported, model.draft());
      assertFalse(model.canAdd(7));
  }

  @Test void rightClickRemovalKeepsOtherCopiesInPlace() {
      var model = new DeckEditorModel(
              new DeckList(List.of(7, 8, 7), List.of(), List.of()), Map.of());
      assertTrue(model.removeAt(DeckEditorModel.Section.MAIN, 2));
      assertEquals(List.of(7, 8), model.draft().main());
  }
  ```

  Add parameterized Main/Extra/Side and natural type routing, fourth-copy rejection, remove-then-add, invalid index, and frozen mutation cases. Preserve the server's existing draft-save tests for missing ownership and illegal lists; the new cap must not become a save-time rejection.
- [x] Run `./gradlew.bat test --tests '*DeckEditorModelTest'`. After adding new method signatures, confirm a behavioral red for the missing cap/removal rather than recording only a compiler error.
- [x] Implement the manual cap in the model before replacing any lists. `canAdd` uses `draft.requiredCopies().getOrDefault(code, 0) < 3`; `add` still calls `requireEditable` first and returns false without dirtying the draft at the cap. `addToDeck` returns the result from `add(naturalSection(type), code)`. `removeAt` removes the given index from that section and leaves other copies in order. Do not truncate a loaded/imported draft or alter server deck policy.
- [x] Add real-input cases to `collection_editor_qa`: one click only inspects; two left clicks produce one add; a second double-click produces one more; fourth copy via any route fails; right click removes precisely the clicked tile and does not fire a left-click action. Include Deck/Side, Extra type routing, filter refresh/virtualized rows and pending/frozen state. Bind double click to LDLib2's existing event instead of inventing a timing system:

  ```text
  collection single-left -> existing selection/inspector callback only
  collection DOUBLE_CLICK with button 0 -> shared add callback once
  deck tile right-click with button 1 -> removeAt(renderedSection, renderedIndex)
  ```

  Consume right-click propagation and verify press/release behavior. Ignore callbacks from a stale rendered draft after the model changed; capture the rendered draft reference or an existing generation token. Keep pending/busy guards before mutation and issue centered cap feedback instead of a false success message. Refresh button enablement and gestures from the same `canAdd` result.
- [x] Run focused unit tests and `./gradlew.bat runClient -PldTest=collection_editor_qa`. Fix overflow fixtures that previously manufactured 4+ copies via manual `add` by constructing an imported `DeckList`, retaining their original overflow purpose. Commit `feat: add bounded pointer editing for deck lists`.

## Task C2: Open the active list and make dismissal layered

**Interfaces:** Add `public void openInitial(ClientCollectionState.View view)` and `public boolean needsDiscardConfirmation()` to `SavedDeckController`. Add `public boolean dismissTopLayer()` to `CollectionController` (true only when one child layer was dismissed). Keep `requestClose()` as the common top-level navigation path. `CollectionScreen.keyPressed(int,int,int)` consumes Escape explicitly.

- [ ] Add tests to `SavedDeckControllerTest`: initial active saved ID loads once with its exact contents; null active ID starts empty; missing/deleted/failed active read reports failure without selecting some other list; delayed read cannot overwrite edits, another selection, a newer view, or disposal. Resume refresh preserves a dirty suspended draft. Use the existing request futures/read-token fixture and assert request identity and resulting UUID/cards, not just captions.
- [ ] Add a separate discard-decision test so an empty unsaved draft stays savable:

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
- [ ] Run `./gradlew.bat test --tests '*SavedDeckControllerTest'`; confirm behavioral failures before the lifecycle changes.
- [ ] Implement `openInitial` as a one-shot startup operation: create the blank draft, apply the initial view, and read its active ID using the existing `select` guards if nonnull. For the no-initial-view startup path, perform one refresh and feed that result through this method. Do not put auto-selection in ordinary `applyView` or `resume`. Preserve catalog-loading independence. Implement the decision predicate independently of `dirty()`:

  ```java
  if (!stored && model.draft().main().isEmpty()
          && model.draft().extra().isEmpty() && model.draft().side().isEmpty()) {
      return false;
  }
  return dirty();
  ```

  Use it only for navigation/discard decisions. Keep pending-save gating before this decision and retain existing save acknowledgement snapshots/revisions.
- [ ] Add a visible Close control in the collection header and visible dismissal controls on child dialogs. Implement `dismissTopLayer` against actual owned controls in visual stacking order: an open selector/dropdown first; then the topmost confirmation/export/delete dialog; then My decks/eligibility/filter subview. Dismissing the unsaved-changes dialog means Keep editing and cancels the queued navigation. Dismissing an overwrite dialog cancels only export. Do not discard the draft on child dismissal.
- [ ] Override Escape handling so Minecraft's default screen-close branch cannot run for the same event after a child closes:

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
- [ ] Extend `collection_editor_qa` with nested dropdown→My decks→editor Escape presses, delete/cancel, dirty/empty/saved-empty cases and delayed save success/failure. Use actual key events and press/release input. Rerun focused unit tests, `./gradlew.bat runClient -PldTest=collection_editor_qa`, and `./gradlew.bat runClient -PldTest=collection_preparation_retention`. Expected: one layer per Escape, active list only on fresh opening and retained dirty draft after suspension. Commit `fix: open active decks and close editor layers predictably`.

## Task C3: Simplify My decks and make Save the explicit persistence action

**Interfaces:** Keep `SavedDeckController.save()`, `newDraft()`, `duplicate()`, `rename(String)`, `importDraft(SavedDeck)`, `select(UUID)` and existing activation/deletion methods. No new server command. Preserve useful selectors such as `#saved-lists`, `#list-name`, `#list-picker`, `#lists-dialog`, `#lists-close`; update affected fixture selectors in the same commit if a control is deliberately removed.

- [ ] Extend controller/UI tests around a single flow: choose an existing deck, edit and Save; its UUID remains constant and one acknowledged save updates the baseline. New/Make a copy create distinct unsaved identities and do not send a save until requested. Import loads all three sections as a draft; Save is explicit. Rename edits the current name and Save persists it. Deleting another saved entry does not accidentally delete the current dirty draft. Save never sends Activate, and activation failure keeps both list and explanatory feedback.
- [ ] Add real-screen expectations to `collection_editor_qa` before rewriting layout. Use the existing fixture command capture and assert operation counts:

  ```text
  New or Make a copy or Import -> 0 Save requests, 0 Activate requests
  Save current draft         -> 1 Save request with current UUID/cards/name
  successful Save            -> stored baseline updated, active ID unchanged
  Use for duels              -> explicit existing activation path and policy result
  failed Save                -> dirty draft retained, no implicit navigation
  ```

- [ ] Run the focused saved-controller suite and the new UI scenario to capture failing operation counts/layout expectations.
- [ ] Reorganize the header around the current name, Save, My decks, Active status and separate Use for duels. Consolidate list management into one My decks surface; secondary New, Make a copy, Import, Export and Delete remain accessible there. Remove redundant Rename-as-save behavior; renaming edits the name and the primary Save commits it. Keep clear-active capability where it currently exists. Route deck switching/New/Import through `navigate`; Make a copy deliberately copies the current draft without discarding it. Remove automatic `save()` calls from GUI duplicate/import callbacks, but keep the compatibility command's `saveThenActivate` behavior intact.
- [ ] Keep the dark styles, centered feedback and 1280x720 bounds. Preserve the existing filter controls and card/detail sizing as far as the changed header allows. Test long names, zero/many saved lists, active-but-different selection, keyboard focus, stale selection reads, deletion confirmation and pending actions. Do not add folders, favorites, another navigation shell or new persistence layers.
- [ ] Run the exact focused commands below. Update assertions for intentional explicit-Save semantics without removing persistence checks. Inspect screenshots at scales 2/3. Commit `refactor: simplify deck save and list management`.

  ```powershell
  ./gradlew.bat test --tests '*SavedDeckControllerTest' --tests '*DeckImportServiceTest'
  ./gradlew.bat runClient -PldTest=collection_editor_qa
  ./gradlew.bat runClient -PldTest=collection_saved_decks
  ./gradlew.bat runClient -PldTest=collection_playtest_scale2
  ./gradlew.bat runClient -PldTest=collection_playtest_scale3
  ```

## Task C4: Export the current list as a client-local YDK

**Interfaces:** Create `DeckListWriter.toYdk(DeckList cards): String`. Create `DeckExportService(Path directory)` with `public static String suggestFileName(String deckName)`, `public Path export(String fileName, DeckList cards, boolean replace) throws IOException`. `fileName` is a single `.ydk` filename, never a path. Add `public SavedDeck snapshot()` to `SavedDeckController`, returning an immutable snapshot of current UUID/name/cards without acknowledging or persisting it. UI validates the current name before using this method.

- [ ] Write `DeckListWriterTest` with exact format and parser round-trip assertions:

  ```java
  @Test void roundTripsAllSectionsAndPreservesOrder() {
      var cards = new DeckList(List.of(7, 8, 7, 7, 7), List.of(9), List.of(10, 7));
      String text = DeckListWriter.toYdk(cards);
      assertEquals("#created by Duelcraft\n#main\n7\n8\n7\n7\n7\n#extra\n9\n!side\n10\n7\n", text);
      assertEquals(cards, DeckListLoader.parseYdk(text));
  }
  ```

  Include empty sections and positive unknown passcodes. Export serializes valid draft data without applying eligibility or the manual three-copy cap.
- [ ] Write `DeckExportServiceTest` using `@TempDir`: new file; collision leaves old bytes untouched without confirmation; confirmed replacement; traversal/absolute/separator filenames; reserved Windows device names; trailing dots/spaces; empty/overlong filename; directory target; write failure before replacement; invalid parent; symlink target where test permissions allow it. Assert no output outside the configured directory and no partial overwrite of the previous file on preparation failure. Document any platform-skipped symlink case separately.
- [ ] Run `./gradlew.bat test --tests '*DeckListWriterTest' --tests '*DeckExportServiceTest'`. Establish failing behavioral tests after interfaces compile.
- [ ] Implement deterministic UTF-8 serialization with `#main`, `#extra`, `!side` and a final newline. Implement filename suggestion by replacing Windows-invalid characters, trimming trailing dots/spaces, bounding the basename to 96 characters and appending `.ydk`; empty/reserved names fall back to `deck.ydk`. Validate the submitted filename independently of the suggestion: one basename, `.ydk` suffix, no separators/absolute path/drive prefix, no Windows reserved basename, and bounded length. Resolve under the configured client's `duelcraft/decks` directory, reject escaping or symlink targets, and never take the output directory from the server or the text field.
- [ ] Write to a temporary file in that same directory, close it successfully, then move into place. For new files, use a move without `REPLACE_EXISTING` so a racing file creation becomes an overwrite question rather than data loss. For confirmed replacement, use an atomic replacement; if the filesystem does not support it, report the inability to replace safely and leave the old file intact. Clean up only this operation's temporary file. Do not truncate an existing deck before producing the full new content.
- [ ] Wire Export .ydk into My decks. Capture one immutable snapshot before file I/O; ask for a filename with a sensible suggestion, then request overwrite confirmation only on collision. Confirmation applies to the shown filename and captured snapshot. Changing either restarts the decision. Keep export cancellation local, independent of Save/Activate/collection revision. Run disk work off the client render thread using the project's existing executor pattern; deliver feedback on the client thread and ignore disposed-screen callbacks. Do not add a generic job framework.
- [ ] Extend `collection_editor_qa` with new-file export, collision cancel/confirm, filename error, and export of an unsaved current draft. Assert name/cards/dirty flag, active ID and collection counts remain unchanged, with zero server mutation requests. Confirm Escape cancels only the filename/overwrite dialog and output feedback names the written file. Load the result through `DeckListLoader`/existing import picker and compare all sections. Use a temporary test export directory rather than overwriting a user's deck files.
- [ ] Rerun writer/export/controller suites and `./gradlew.bat runClient -PldTest=collection_editor_qa`. Commit `feat: export deck drafts to local YDK files`. Update the QA index and pass C's work to I1 for full UI, reconnect/restart and packaged acceptance.

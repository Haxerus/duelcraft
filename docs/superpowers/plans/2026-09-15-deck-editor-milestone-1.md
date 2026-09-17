# In-game Deck Editor Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if delegation is authorized for execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the accepted deck editor into Minecraft with deterministic sample data so players can test layout, search, editing, and scaling before server integration.

**Architecture:** Keep the editor's draft and filters outside its LDLib widgets. Introduce an immutable Main/Extra/Side list model, a small editor model and pure search function, then bind a separate 1280x720 XML screen. Inject card metadata, textures, and an in-memory save callback; subsequent milestones supply authoritative player data without changing the editing contract.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, JUnit 5, existing LDLib2 UI harness.

**Spec:** [Player interaction and collection design](../specs/2026-09-15-player-interaction-design.md). Read the [whole integration roadmap](2026-09-15-player-interaction-roadmap.md) to understand which production features follow this milestone.

## Global constraints

- Use the term **collection**.
- The editor uses a 1280x720 design canvas, centered and uniformly scaled to the available GUI-space viewport.
- Keep `core.Deck` as the simulation input.
- Saved decks are card lists. Saving a draft does not require owning its cards or satisfying duel legality.
- Multiple lists can reference the same owned copies. Saving or activating a list does not consume or reserve cards.
- Main, Extra, and Side headers remain visible.
- Card-image or database availability must not determine whether layout scenarios pass.
- This milestone has no collection persistence, real transfers, activation, lobby actions, or player-facing hotkey. Disabled controls say why; do not report a real-world save or transfer.
- Keep `DuelScreen` and `duel_screen.xml` unchanged. Do not add a generalized screen framework for two scaling methods.
- Keep existing user edits intact; at planning time `docs/engine-gap-analysis.md` was already modified.

## File structure and contracts

Java paths below are relative to `src/main/java/com/haxerus/duelcraft/`; tests use the corresponding package under `src/test/java/`.

| File | Responsibility |
| --- | --- |
| `collection/DeckList.java` | Immutable Main/Extra/Side card lists, copy counts, conversion to engine input |
| `client/collection/DeckEditorModel.java` | Local draft, initial saved snapshot, owned-count view, add/remove and dirty state |
| `client/collection/CardSearch.java` | Pure text/filter/sort operation and filter value types |
| `client/collection/CollectionScreen.java` | Fixed-canvas host, XML load, close/resize lifecycle |
| `client/collection/CollectionController.java` | Bind screen elements to editor, search, selection, density, and save callback |
| `client/collection/CollectionCardGrid.java` | Four-column virtual collection rows and visible-card presentation |
| `client/uitest/CollectionFixture.java` | Deterministic metadata, ownership, draft, and null-texture provider |
| `client/uitest/CollectionLayoutScenario.java` | Standard layout and real transformed interactions |
| `client/uitest/CollectionOverflowScenario.java` | Expanded Side, Large density, long text, resize/overflow checks |
| `client/uitest/CollectionSearchScenario.java` | Filter/search correctness and large-catalog mounted-row bounds |
| `src/main/resources/assets/duelcraft/ui/collection_screen.xml` | Layout and LSS, using current mod styling conventions |
| `src/main/resources/assets/duelcraft/lang/en_us.json` | Labels, sample-data status, unavailable-action explanations |

Keep `CardInfo` unchanged for this milestone: it already has name, effect text, type, race, attribute, and packed stats required for the specified filters. Do not use `searchDeclarable` as collection search: its token/alias/announcement rules serve a different purpose.

Before implementation, preserve the prototype HTML in the implementation workspace or open the absolute reference in the spec. All critical measurements are in the spec so its absence does not block work.

## Task 1: Deck list and local editing model

**Create:** `collection/DeckList.java`, `client/collection/DeckEditorModel.java`.
**Tests:** `collection/DeckListTest.java`, `client/collection/DeckEditorModelTest.java`.

**Interfaces produced:**

```java
// Defensive copies in the record constructor; positive IDs are representation validation.
public record DeckList(List<Integer> main, List<Integer> extra, List<Integer> side) {
    public Map<Integer, Integer> requiredCopies(); // count across all three sections
    public Deck toDuelDeck(); // defensive Main/Extra snapshot; excludes Side
}

public final class DeckEditorModel {
    public enum Section { MAIN, EXTRA, SIDE }
    public DeckEditorModel(DeckList initial, Map<Integer, Long> owned);
    public DeckList draft();
    public Map<Integer, Long> owned(); // immutable snapshot
    public void add(Section section, int code);
    public boolean remove(Section section, int code); // one copy, false if absent
    public long missing(int code); // max(0, required - owned)
    public boolean dirty();
    public void markSaved(); // reset baseline only after the supplied save action succeeds
}
```

- [x] Write behavior tests before implementation. Include these cases, adding imports/package normally:

```java
@Test void missingCardsDoNotPreventEditingOrSavingADraft() {
    var model = new DeckEditorModel(new DeckList(List.of(), List.of(), List.of()), Map.of());
    model.add(DeckEditorModel.Section.MAIN, 89631139);
    assertEquals(1, model.missing(89631139));
    assertTrue(model.dirty());
    model.markSaved();
    assertFalse(model.dirty());
    assertEquals(List.of(89631139), model.draft().main());
}

@Test void sideCopiesCountButDoNotEnterEngineDeck() {
    var list = new DeckList(List.of(1), List.of(2), List.of(1));
    assertEquals(2, list.requiredCopies().get(1).intValue());
    assertEquals(new Deck(List.of(1), List.of(2)), list.toDuelDeck());
}

@Test void laterInputMutationCannotChangeTheDraft() {
    var input = new ArrayList<>(List.of(1));
    var list = new DeckList(input, List.of(), List.of());
    input.clear();
    assertEquals(List.of(1), list.main());
}
```

- [x] Run `./gradlew test --tests '*DeckListTest' --tests '*DeckEditorModelTest'`; confirm failure because the new behavior is absent, not an environment failure.
- [x] Implement the stated interfaces using copies of lists/maps. Do not call `DeckValidator` on draft save. Allow incomplete/over-limit drafts in this milestone and expose count warnings in the view; gameplay validation belongs to activation. Adding a card only modifies the local list.
- [x] Add removal-absent and fourth-copy draft tests; run the same targeted command and confirm pass.
- [x] Review and commit just these models and tests: `feat: add deck-list and editor draft models`.

## Task 2: Rich filtering over injected card metadata

**Create:** `client/collection/CardSearch.java`.
**Tests:** `client/collection/CardSearchTest.java`.

**Consumes:** `CardInfo`, `DeckList`, immutable owned counts.
**Produces:** the following pure search API; nest its value types in `CardSearch` to keep the scope local.

```java
public enum Ownership { ALL, OWNED, MISSING, EXTRAS }
public enum Measure { ANY, LEVEL, RANK, LINK }
public enum Sort { NAME, PASSCODE, ATK, DEF }
public record Range(int min, int max) {}
public record Filters(int categoryAny, int subtypeAny, int requiredProperties,
        long raceAny, int attributeAny, Measure measure,
        Range measureRange, Range atkRange, Range defRange, Range scaleRange,
        Ownership ownership, Sort sort) {
    // Zero masks and null ranges mean unrestricted.
    public static final Filters ALL = new Filters(0, 0, 0, 0, 0,
            Measure.ANY, null, null, null, null, Ownership.ALL, Sort.NAME);
}
public static List<CardInfo> search(List<CardInfo> cards, String query,
        Filters filters, Map<Integer, Long> owned, DeckList draft);
```

- [x] Add tests for effect-text search and ownership shortages:

```java
@Test void searchesEffectTextWithoutRequiringNameMatch() {
    var card = new CardInfo(10, "Lantern", "Draw two cards.",
            OcgConstants.TYPE_SPELL, 0, 0, 0, 0, 0);
    var results = CardSearch.search(List.of(card), "draw two", CardSearch.Filters.ALL,
            Map.of(), new DeckList(List.of(), List.of(), List.of()));
    assertEquals(List.of(card), results);
}

@Test void missingMeansShortageInThisDraft() {
    var filters = new CardSearch.Filters(0, 0, 0, 0, 0,
            CardSearch.Measure.ANY, null, null, null, null,
            CardSearch.Ownership.MISSING, CardSearch.Sort.NAME);
    var card = new CardInfo(10, "Lantern", "", OcgConstants.TYPE_SPELL, 0, 0, 0, 0, 0);
    var draft = new DeckList(List.of(10), List.of(), List.of(10));
    assertEquals(List.of(card), CardSearch.search(List.of(card), "", filters, Map.of(10, 1L), draft));
    assertTrue(CardSearch.search(List.of(card), "", filters, Map.of(10, 2L), draft).isEmpty());
}
```

- [x] Run `./gradlew test --tests '*CardSearchTest'`, confirming the intended failure.
- [x] Implement case-insensitive name/effect substring matching and exact numeric passcode matching. An exact passcode match takes precedence over exact name, then partial matches; chosen sort plus passcode breaks ties within each match tier. Treat `%` and `_` as literal text; there is no SQL in this milestone.
- [x] Implement filters with this table as the truth contract:

| Input | Matching rule |
| --- | --- |
| Category/subtype/race/attribute masks | Any selected bit in that group; zero means unrestricted |
| Required properties | All requested bits must be present |
| Level/Rank/Link | Level excludes Xyz/Link; Rank requires Xyz; Link requires Link; use `CardInfo.levelOrRank()`/`linkRating()` |
| ATK/DEF range | Monsters only; unknown negative values do not satisfy a numeric range; Link has no DEF |
| Pendulum scale | Pendulum only; accept if either scale is in range; left = `(level >>> 24) & 0xff`, right = `(level >>> 16) & 0xff`, matching `native/jni-bridge/src/card_database.cpp` |
| Owned | `owned.getOrDefault(code, 0L) > 0` |
| Missing | required copies in current draft exceed owned |
| Extras | owned exceeds required copies in current draft |

- [x] Add parameterized tests for every table row, including combined groups, OR subtypes, AND properties, nonmonster stats, Link DEF exclusion, exact-first ordering, and stable ties. Build small `CardInfo` fixtures; no external database or network.
- [x] Run targeted tests, then commit: `feat: add collection card filtering`.

## Task 3: Bounded XML screen and sample fixture

**Create:** `CollectionScreen.java`, `CollectionController.java`, `collection_screen.xml`, `CollectionFixture.java`, `CollectionLayoutScenario.java` at the paths above. **Modify:** `en_us.json`.

**Consumes:** Tasks 1 and 2.
**Produces:**

```java
public final class CollectionScreen extends ModularUIScreen {
    public static final int DESIGN_WIDTH = 1280;
    public static final int DESIGN_HEIGHT = 720;
    public static CollectionScreen create(DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, Consumer<DeckList> saveDraft);
}
// Texture provider may return null. Null uses a deterministic placeholder with card name/passcode.
// CollectionController owns selected code, selected section, filter state, density, and pane state.
```

- [x] Register `CollectionLayoutScenario` with the existing `@LDLRegisterClient` pattern, group `duelcraft`, name `collection_layout`, environment `DEV_ONLY`. Open the real factory with synthetic data and a callback that records the saved list in the test context. Fixture contains Main40, Extra5, Side0, an ownership shortage, long text, and recognizable placeholder names; it grants no inventory items.
- [x] Add bounds checks for all required selectors below, then run `./gradlew runClient -PldTest=collection_layout -PldTestWindow=1280x720`. Record the initial expected failure; a launch/native failure is not evidence of missing layout behavior.
- [x] Load XML using the same `XmlUtils.loadXml`, `UI.of`, `Size.of`, and `ModularUI.of` pattern as `LDLibDuelScreen.loadFromXml`. Keep the new host independent from duel-specific static state. Use this scale calculation after `super.init()`:

```java
if (width > 0 && height > 0) {
    float scale = Math.min(width / 1280f, height / 720f);
    canvas.transform(t -> t.scale(scale));
}
```

- [x] Implement XML in LSS-supported properties, starting with fixed header/footer and a bounded horizontal body. Use built-in borders and the mod's current palette as the Minecraft styling baseline. Required IDs:

```text
collection-root, collection-canvas, editor-header, save-deck, editor-body,
card-inspector, card-details-scroll, card-edit-controls, add-card, remove-card,
main-header, main-grid, extra-header, extra-grid, side-header, side-grid,
collection-search, collection-results, filter-panel, filter-apply,
editor-footer, card-density, editor-status
```

Use explicit grid row heights inside the deck scrollers, as the existing inspector XML does:

```css
#main-grid .__scroller_view_view-container__ {
    display: grid;
    grid-template-columns: repeat(10, 54px);
    grid-auto-rows: 79px;
    gap-all: 4;
}
```

Apply matching density classes to all deck sections (Large: eight columns, 70px cells, 103px rows). Fit the actual center width and padding before freezing card widths. Main consumes remaining vertical space; Extra starts at 128 units (150 in Large); collapsed Side32, expanded Side150. Keep section headers outside their scrollers.

- [x] Bind labels/counts/selection to the model, add readable placeholder tiles, and keep all transfer/activation controls disabled with the sample-data explanation. Header Save means "Save sample draft" in this milestone. Do not install a global card-image callback; refresh mounted texture references through the injected provider during the screen lifecycle.
- [x] Run the layout scenario and inspect its screenshot, not just its assertion count. Confirm all required selectors exist; check absence explicitly rather than letting a hidden check pass on a misspelled ID. Commit: `feat: add bounded collection editor screen`.

## Task 4: Interactive grids, search, and save/close behavior

**Create:** `CollectionCardGrid.java`, `CollectionSearchScenario.java`. **Modify:** controller, screen, fixture, XML, language labels.

**Consumes:** `CardSearch.search`, `DeckEditorModel`, injected texture/save functions.
**Produces:** `CollectionCardGrid extends VirtualScrollerView<List<CardInfo>>`, with constructor `(IntFunction<ResourceLocation> textures, IntConsumer selectCard)` and `void showCards(List<CardInfo> cards)` that groups results into four-card rows.

- [x] Write the real-screen scenario using these interactions: select a collection card; Add into Main; select the matching deck tile; Remove one; select Side and Add; type an effect-text query; apply two filters; clear filters; save a draft with missing copies. Assert counts, inspector selection, Side expansion, result IDs, and the exact list passed to the save callback.
- [x] Use the pinned `VirtualScrollerView` row provider; `setItems` receives four-card rows, `setItemUIProvider` creates each mounted row. Fix row height and use one-row overscan as the initial setting. Request textures only for mounted cards and the inspector. Hold selection/draft/filter state in the controller, not the recycled widgets.
- [x] Wire add/remove/save using this operation ordering:

```java
model.add(selectedSection, selectedCode);
// Update deck counts, affected grid, owned/used labels, and current shortage indicators.
// Preserve selected card and independent scroll offsets; clamp offsets if content shrinks.

saveDraft.accept(model.draft());
model.markSaved();
// Set the sample-save acknowledgement only after callback success.
```

If the callback throws, retain the draft and dirty state and show the failure. Add/Remove never changes owned counts. A card click inspects by default; explicit buttons make edits. Count warnings explain incomplete Main, oversized sections, and too many copies but do not block saving a draft.

- [x] Keep filters inside the right column, with Apply reachable while its contents scroll. Search/filter/sort changes reset collection results to the top; card edits and inspection preserve scrolling unless an ownership-dependent result set changes. Retain the inspector selection even if filtering hides its tile.
- [x] Implement dirty-close Save/Discard/Cancel. In a close attempt, Escape opens that prompt; Save invokes callback then closes, Discard closes without callback, Cancel returns to the draft. Resizing reconstructs widgets if LDLib requires it but retains the same model/controller state. No prompt on a clean draft.
- [x] Add scenario cases for failed save preserving the draft and for dirty-close Cancel/Discard. Run `./gradlew runClient -PldTest=collection_search -PldTestWindow=1280x720`; inspect screenshots and commit: `feat: wire editor grids filters and draft actions`.

## Task 5: Overflow, scale, and realistic catalog verification

**Create:** `CollectionOverflowScenario.java`. **Modify:** fixture/scenarios only as needed to exercise the real screen; production fixes require the failing scenario first.

**Consumes:** complete screen and grid behavior.
**Produces:** repeatable playtest evidence and a concise implementation record appended to the roadmap's milestone 1 section.

- [x] Generate 16,000 distinct synthetic `CardInfo` records in the fixture. Do not call texture services for this scenario. Assert `CollectionCardGrid.getMountedItemCount()` remains bounded by visible rows plus overscan, and that scrolling near the end selects the expected passcode. This tests mounting/click correctness, not just search speed.
- [x] Add an overflow scenario with Main60, Extra15, Side15, expanded Side, Large cards, a long description, and enough filters/chips to scroll. Check all headers, Save, Add/Remove, and Apply bounds. Verify adjacent card bounds never overlap and scrolling one pane leaves the others unchanged. Click the last reachable tile and assert the selected code.
- [x] Run the new scenarios at GUI scales 2/3/4, window sizes 1280x720, 960x540, and 1920x1080. Use the existing `ScenarioOptions.guiScale` for explicit scenario variants if a scenario overrides the Gradle default. Example commands for a scenario that does not override it:

```powershell
./gradlew runClient -PldTest=collection_overflow -PldTestWindow=1280x720 -PldTestGuiScale=2
./gradlew runClient -PldTest=collection_overflow -PldTestWindow=960x540 -PldTestGuiScale=3
./gradlew runClient -PldTest=collection_overflow -PldTestWindow=1920x1080 -PldTestGuiScale=4
```

Record actual framebuffer/GUI sizes and effective GUI scale from the test run; Minecraft may clamp a requested scale on a small window. Verify a non-16:9 window manually and resize with a dirty draft. Do not claim a 960x540 design canvas: that case is the 1280x720 screen scaled into a smaller viewport.

- [x] Run `./gradlew test`, then `./gradlew runClient -PldTest=group:duelcraft` after fixes. Full tests need the native DLLs and configured EDOPro data. Use the documented build prerequisites; do not introduce a fake passing fallback for missing prerequisites. Keep reports/screenshots from separate matrix runs before the harness clears its output.
- [x] Open the sample screen for the user's in-game playtest with `./gradlew runClient -PldTest=collection_layout -PldTestWindow=1280x720 -PldTestKeepOpen` when launching the game is authorized. Evaluate text size, click targets, density, scroll behavior, filter discoverability, and the inspector's usefulness. Do not invent performance/readability thresholds on the user's behalf; record measurements and observed problems.
- [x] Record test commands/results, screenshots, actual viewport scales, and any resulting layout changes. Commit: `test: cover collection editor scale and overflow`.

## Completion boundary

Milestone 1 is complete when the real Minecraft editor supports the described sample interactions and the bounds/scroll/click tests pass, with readable screenshots available for user playtesting. It is not a usable personal collection yet. Mark the roadmap's next milestone ready for its storage/network implementation plan; do not silently implement persistence or mint sample cards into a real world.

Execution can proceed inline with `executing-plans`; delegation is an optional execution choice. This document itself does not start implementation.

# Gallery UI tooling implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the selected Gallery design in Minecraft with a small specimen, resource checks, an evidence gallery and two real-controller fixtures before migrating production screens.

**Architecture:** Extend the existing LDLib2 `UIScenario` harness and its reports. Use literal resource LSS, the library's XML/LSS parsers and existing controllers. The collection and lobby receive only package-private fixture layout seams; normal factories continue loading their current layouts.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.224, pinned LDLib2 2.2.39.a, XML/LSS, existing Gradle harness, PowerShell for static evidence indexing.

**Spec:** [Gallery style guide](../specs/2026-10-17-gallery-style-guide.md), [redesign brief](../specs/2026-10-17-ui-redesign-brief.md), [LDLib2 audit](../reports/2026-10-17-ui-ldlib-audit.md).

## Global constraints

- Management and collection retain a **1280×720** design canvas; duel retains **960×540**. Do not change either scaling path.
- Start with the existing Minecraft font. Segoe UI and Georgia in the browser sketches are not approved assets.
- Candidate management typography: Home display **48**, title **24**, section **20**, body/control **16**, metadata **14**; regular controls **40**, dense controls **32**. The duel specimen starts at **0.75×** except the Home title, including **30/24** control heights. These are measurements to prove, not an instruction to enlarge production text blindly.
- Management spacing uses **4/8/12/16/24/32** units. Shared color/semantic classes contain literal supported values; canvas-specific dimensions stay separate.
- Gallery roles include deep `#19151f`, canvas `#211c2a`, surface `#34273e`, text `#eee5d4`, muted text `#b8afbe`, gold `#c6ab77`, focus `#efd18e` and interactive edge `#8c7495`. Follow the style guide for the remaining roles and states.
- Preserve production behavior, controller-required IDs, filters, explicit Save/activation, privacy and ownership semantics. No route, networking, animation system or installed-JAR acceptance changes belong here.
- Retain collection popup alignment and all current coordinate workarounds. Version-label agreement is not proof of identical source and runtime behavior.
- Register new scenarios with `RegistrationEnvironment.DEV_ONLY`, `UIScenario.REGISTRY`, group `duelcraft_gallery`, and `requiresWorld(false)`. Do not add player commands, menu entries or a general preview framework.
- DEV_ONLY limits registration, not JAR contents. Fixture resources may be packaged like existing test resources, but no production factory may reference them. Do not build a new source set solely to remove dormant fixtures.
- No CSS variables, token compiler, HTML-to-LSS transpiler, screenshot renderer, generic component framework, unconditional overlap lint or new dependency.

## Review focus

- A larger font can outgrow `Label`'s default height or an adaptive width can escape a bounded region: Task 1 measures rendered text and tests long labels.
- A declaration can be ignored or return a lazy null value while parsing appears successful: Task 2 distinguishes errors from values still requiring a runtime check.
- Old reports or unknown viewport metadata can make a screenshot look like fresh evidence: Task 3 preserves provenance and displays unknown values honestly.
- A dense deck with expanded Side can lose usable card space while all outer bounds still pass: Task 4 checks visible card capacity, scroll endpoints and selected-card readability.
- A root-hosted popup can draw above a modal while input/focus goes elsewhere: Task 5 checks actual mouse selection and one-layer Escape dismissal.

## Verified interfaces and scope

Inspected Duelcraft HEAD `1ab2bb15dd44643e6b82ae7269f2beb299da55fa` and the sibling LDLib2 source identified in the audit. No fresh build, client run or test pass is claimed by this planning document.

- `UI.of(Document)` consumes `<style>` and `<stylesheet location="namespace:lss/file.lss"/>`. `StylesheetManager` discovers resource sheets under `lss/`; use `assets/duelcraft/lss/gallery.lss`, not a stylesheet hidden under `ui/`.
- `Stylesheet.parse(String)`, `Stylesheet.RULE`, `Stylesheet.DECL`, `Stylesheet.parseStyleValues(String)`, `PropertyRegistry.byName(String)` and `Property.valueParser` are available. Parsing normally logs unknown/invalid declarations rather than throwing them to callers. `StyleValue.compute()` can return null.
- `CollectionFixture.model(true)` supplies Main **60**, Extra **15**, Side **15**; `cards()` has 80 deterministic cards and a long effect; `query()` executes the existing `CardSearch` synchronously. `CollectionCardGrid` uses four cards per fixed **128**-unit virtual row.
- `CollectionScreen.createWithArt(...)` and its constructor are private and hardcode `ui/collection_screen.xml`; `CollectionController` is package-private. `DuelLobbyScreen` similarly passes the fixed `duel_lobby` resource into `ManagementScreen`. A fixture cannot swap layouts through the existing public factories.
- `DuelUiAssertions.contains(...)` is reusable. Its text measurement method is currently private; expose that method for fixture reuse. Do not apply `surface(...)` blindly: it assumes `#duel-canvas` and disallows intentional sibling overlaps.
- `ManagementTestInput.click(TestContext, String)` dispatches press/release at retained screen coordinates. `CollectionWidgetPlaytest.choose(...)` already demonstrates scaled popup bounds and click selection, but is private. Reuse its check pattern rather than adding another input driver.
- `CollectionLayoutScenario.recordViewport(TestContext)` records requested window/scale, actual window/framebuffer/GUI dimensions and effective scale. Reports use schema **1**, `scenarios[].steps[].attachments`, and `steps[].captures[].path` relative to the report directory.
- `gradle/ldlib2-uitest.gradle` supplies `ldTest`, `ldTestWindow`, `ldTestGuiScale`, `ldTestInputMode`, `ldTestKeepOpen`; `runClient` clears `build/ldlib2-uitest` and finalizes with `verifyUiTest`. Archive every wanted run before the next launch.

File ownership follows tasks below. The only shared production edits are the two layout seams. Do not redesign current XML or change controller logic while preparing these tools.

Run the listed commands from this worktree in PowerShell with `$env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'`, as in the current implementation handoff.

### Task 1: Literal Gallery sheet and component specimen

**Files**

- Create `src/main/resources/assets/duelcraft/lss/gallery.lss`.
- Create `src/main/resources/assets/duelcraft/ui/uitest/gallery_specimen.xml`.
- Create `src/main/java/com/haxerus/duelcraft/client/uitest/GallerySpecimenScenario.java`.
- Modify `src/main/java/com/haxerus/duelcraft/client/uitest/DuelUiAssertions.java`: make `textFits(TestContext, TextElement)` public without changing its algorithm.
- Create `docs/superpowers/reports/2026-10-17-gallery-runtime-proof.md` for observed provenance and proof results, not assumed passes.

**Interfaces**

- Consumes `XmlUtils.loadXml(ResourceLocation)`, `UI.of(Document)`, `UI.of(root, sheets, DynamicSizeProvider)`, `ModularUI.of(UI)`, and `DuelUiAssertions.textFits(...)`.
- Produces scenario `gallery_specimen`; resource `duelcraft:lss/gallery.lss`; literal semantic classes prefixed `gallery-`, with dimensions scoped under `.gallery-management` and `.gallery-duel`.
- Keep the specimen screen host local to this scenario. Its factory accepts design width/height and the canvas class; it applies the same minimum-ratio root scale used by the corresponding current screen.

- [ ] Record `git rev-parse HEAD`, `git -C C:/Users/haxer/Documents/Programming/Modding/LDLib2 rev-parse HEAD`, and `./gradlew.bat dependencyInsight --dependency ldlib2 --configuration runtimeClasspath`. Locate the resolved `all` JAR; use `Get-FileHash -Algorithm SHA256 -LiteralPath <resolved-jar>` and `javap -classpath <resolved-jar> -p -c com.lowdragmc.lowdraglib2.gui.ui.elements.Selector` to compare `updateDialogPosition`, `show` and `screenTick` with the source; also confirm the parser methods above. In the launched scenario attach the loaded LDLib2 version and `UI.class` code-source location. A matching version string alone leaves identity unverified. If the resolved API differs, use the artifact's API and record the difference before proceeding.
- [ ] Establish unchanged-layout baselines with `./gradlew.bat runClient -PldTest=collection_layout -PldTestWindow=1280x720 -PldTestGuiScale=2` and `./gradlew.bat runClient -PldTest=duel_lobby -PldTestWindow=1280x720 -PldTestGuiScale=2`. Copy each `build/ldlib2-uitest` directory to a fresh dated directory before the next launch. Record failures without fixing unrelated production code.
- [ ] Define the specimen's failing checks first: nonempty title/control text fits its measured box; explicit label heights accommodate font plus padding; long deck/card names use the guide's bounded treatment; disabled buttons do not increment a fixture counter; selected, focused and pending states are separately observable. Include a text field, selector with at least six entries, scrollable rows, pending/error feedback and a confirmation layer. Use a future/counter local to the fixture, never a real server operation.
- [ ] Add the minimum shared LSS and specimen XML to satisfy those checks. Use supported `background`, `text-color`, `font-size`, `height`, padding/gap and texture values. Preserve real `Button`, `TextField`, `Selector` widgets and implement the specimen's necessary focus/keyboard bindings explicitly; appearance does not add Enter/Space behavior. Do not copy every production widget into a library.
- [ ] Run `./gradlew.bat runClient -PldTest=gallery_specimen -PldTestWindow=1280x720 -PldTestGuiScale=2`. Capture management and duel-size specimens sequentially. Assert actual mouse activation, Tab/Shift-Tab, Enter/Space, disabled non-activation by mouse and keyboard, pending duplicate-submit prevention, focus restoration after dismissal, and text fit including accents/fallback glyphs. Attach complete viewport metadata for each canvas, including the design size. The report must be PASS with both captures present; record human legibility/sharpness judgment separately.
- [ ] Review the specimen with the style guide and adjust candidate font/control values only where captures justify it. Update the guide and proof report together. Stage only this task's files and commit `feat: add dev Gallery component specimen`.

**Acceptance:** One real rendered vocabulary proves the initial font and control treatment at both canvas sizes. Existing screens still use their original layouts and styles. Runtime provenance is recorded; no claim that the alignment workaround is redundant follows from this task.

### Task 2: Small resource preflight using the pinned parser

**Files**

- Create `src/main/java/com/haxerus/duelcraft/client/uitest/GalleryPreflight.java`.
- Create `src/main/java/com/haxerus/duelcraft/client/uitest/GalleryPreflightScenario.java`.
- Modify the new specimen scenario to preflight its resource before opening it.

**Interfaces**

- `public static Result check(ResourceLocation layout, Set<String> requiredIds)` loads a resource and delegates to `public static Result check(Document document, String source, Set<String> requiredIds)`; the second overload supports negative in-memory examples. Nested `record Result(List<String> errors, List<String> uncheckedValues)` carries diagnostics.
- `public static Set<String> ids(Document document)` gathers nonblank declared IDs; duplicate detection belongs in `check`, not in this set-producing helper.
- Scenario `gallery_preflight` runs positive and negative examples in the initialized client, where LDLib2 properties, resources and fonts exist. Keep negative documents/strings in the scenario; no separate parser, manifest generator or JUnit bootstrap is needed.

- [x] Add negative assertions for duplicate `id`, missing required ID, a missing `<stylesheet>` resource, unknown `font-family`, and malformed numeric/unsupported-unit values. Assert the diagnostic includes the resource and offending ID/property. A lazy null must appear in `uncheckedValues`, never be silently accepted as a verified value.
- [x] Implement traversal with the existing DOM and LDLib2 declaration patterns. Check inline `style` attributes, embedded `<style>` blocks and linked literal LSS resources; look up property names with `PropertyRegistry.byName`, invoke the registered parser and evaluate its value while the client is initialized. Use `UI.of(document)` to exercise actual construction. Detect missing literal linked resources; do not claim coverage for Java-generated textures or dynamic resource names.
- [x] Reject unsupported browser constructs used accidentally in these fixtures (`@media`, CSS custom properties, `var(...)`) with a direct diagnostic. Reuse `Stylesheet.RULE`/`DECL` for declaration enumeration and the library for interpretation; do not invent grammar or attempt to validate all possible LSS. Preserve source resource and selector/element context. Document selector/value limitations that the parser cannot prove.
- [x] Run `./gradlew.bat runClient -PldTest=gallery_preflight -PldTestWindow=1280x720 -PldTestGuiScale=2`. Expected PASS means each intentionally invalid example was detected and the specimen has zero errors. Report every unchecked value with its later runtime assertion or visible-review owner; zero errors is not a complete value-validation claim.
- [x] Commit `test: preflight Gallery fixture resources` after reviewing the diagnostics for actionable wording.

**Acceptance:** A typo cannot disappear into debug logging unnoticed in new Gallery resources. Required IDs are explicit at the call site. Existing production resources are read as contract references, not rewritten to satisfy a new blanket lint.

### Task 3: Retained evidence gallery over existing reports

**Files**

- Create `tools/ui-gallery.ps1`.
- Create `tools/ui-gallery.test.ps1` (plain PowerShell assertions; no Pester dependency).
- Update the runtime proof report with the exact gallery command and generated output location.

**Interfaces**

- `tools/ui-gallery.ps1 -ReportDir <existing report directory> -OutputDir <fresh archive directory> [-SourceRevision <sha>] [-SourceDirty <true|false>] [-DependencySha256 <sha>]` copies that report and its referenced captures into the archive and generates `index.html` plus `provenance.json` there.
- Read dependency version and runtime environment from the report; read requested/effective viewport and design size from scenario attachments. Optional source/hash arguments describe this capture only, never the current checkout retroactively applied to historical evidence.

- [x] Write the small script test against temporary synthetic schema-1 reports: one PASS capture, one FAIL capture, missing viewport/provenance, a missing capture file and HTML-sensitive text. Assert links stay relative and usable, unknown metadata displays as **unknown**, missing files are visible diagnostics, escaped text is not executable markup, and an existing output directory is refused rather than overwritten. Run `powershell -NoProfile -File tools/ui-gallery.test.ps1` and observe the initial failure.
- [x] Implement a static index of report scenarios/steps/captures using `ConvertFrom-Json` and HTML escaping. Display status, state/step name, capture, original report link, source/dirty state when supplied, dependency version/hash, requested window/scale, actual framebuffer/GUI/effective scale and design canvas. Do not turn a screenshot into an automatic visual verdict. No server, bundler, image re-rendering or interactive filtering is required.
- [x] Run the script test again; expect a successful exit and all assertions passing. Run `powershell -NoProfile -File tools/ui-gallery.ps1 -ReportDir build/ldlib2-uitest -OutputDir build/ui-gallery/2026-10-17-specimen-01` against a real retained specimen report. Add known provenance arguments only when they describe that exact run. Open the resulting index and verify image/report links and narrow-window readability.
- [x] Archive one failing or incomplete synthetic example to confirm the gallery visibly distinguishes it from a passing run. Commit `chore: index retained UI harness evidence`; generated galleries remain under ignored `build/`.

**Acceptance:** A reviewer can compare actual rendered states and identify their source, viewport and test verdict without opening JSON manually. Historical missing metadata remains unknown. A second launch cannot erase the archived first run.

### Task 4: Dense collection composition with the real controller

**Files**

- Create `src/main/resources/assets/duelcraft/ui/uitest/gallery_collection.xml`.
- Create `src/main/java/com/haxerus/duelcraft/client/collection/CollectionGalleryScenario.java` in the screen's package so its factory seam stays package-private.
- Modify `src/main/java/com/haxerus/duelcraft/client/collection/CollectionScreen.java`: narrow layout injection only.
- Reuse `CollectionFixture`, `GalleryPreflight`, `DuelUiAssertions`, `ManagementTestInput` and `gallery.lss`; update the proof report.

**Interfaces**

- Add package-private `static CollectionScreen createFixture(ResourceLocation layout, DeckEditorModel model, List<CardInfo> cards, IntFunction<ResourceLocation> textures, UUID id, DeckSaveHandler saveDraft, CollectionQuery search)`.
- Thread the layout into the existing private factory; existing public paths always supply `duelcraft:ui/collection_screen.xml`. Preserve existing request/refresh defaults, close lifecycle and controller construction. The fixture alone supplies `duelcraft:ui/uitest/gallery_collection.xml`.
- Produce scenario `gallery_collection`. Reuse `CollectionFixture.model(true)`, `cards()` and `query()`; null artwork is the deliberate missing-art case. Use a locally controlled `CompletableFuture<SavedDeck>` for Save acknowledgement/error.

- [ ] Before implementing layout changes, add checks for selected card, 60 Main/15 Extra/15 Side, both deck densities, expanded Side, long name/effect, empty search, pending/failing Save and an open filter/import surface. Include deck names of 1 and 128 Java string units and export base names of 1 and 96 units (5 and 100 including `.ydk`), without changing model limits. Check filename text in the real export field without submitting exports. Require every static ID from the original collection document in the fixture document through `GalleryPreflight`; retain dynamic controller IDs and widget types. Use the existing unchanged layout as the behavioral baseline.
- [x] Add the narrow seam and fixture XML. Keep inspector left, deck center, browser right. New deck stays in the existing top action region; Sort/card size fit into the Main heading. Keep a small scrollable My decks surface and separate import/export. Do not add a toolbar row, folder caption, OS picker or permanent instructional block. Do not migrate the production XML.
- [x] Keep the existing four-column, 128-unit browser virtualization contract for this proof. Adjust local geometry around it; if the design cannot retain usable content under that constraint, record the limitation for review rather than silently changing `CollectionCardGrid`. All columns must show card content at rest; Main must show at least one complete card row with Extra and Side expanded. Capture counts and measured card/viewport bounds alongside the image.
- [x] Exercise actual browser right-click add, deck right-click remove, visible Add/Remove, independent effect/Main/browser scrolling, Main and Side endpoints, empty results and a long selected name. Capture both densities, expanded filters, import dropdown, pending Save and failed Save. Before Save remove one card from the full draft so the edited draft remains valid. Pending must not mark it saved; exceptional completion retains it and shows the existing actionable error. For the import picker follow `CollectionWidgetPlaytest`'s real file-discovery/input path, but allocate with `Files.createTempFile(deckDirectory, "gallery-fixture-", ".ydk")`, write `DeckListWriter.toYdk(model.draft())`, and retain that exact path for teardown. Never delete/reuse its fixed `collection-import-ui.ydk` name or touch pre-existing files. Select the generated basename's real dropdown row, assert scaled anchor/clamp and selected value, then close without submitting import/activation (the sample request handler deliberately rejects server actions). Preserve the current popup workaround.
- [x] Resize the open fixture through `GLFW.glfwSetWindowSize(ctx.mc().getWindow().getWindow(), width, height)` as in `CollectionWidgetPlaytest`, wait for settled bounds and assert draft, selection and meaningful scroll position remain; do not reconstruct the fixture. Run `./gradlew.bat runClient -PldTest=gallery_collection -PldTestWindow=1280x720 -PldTestGuiScale=2`, archive it through Task 3, then complete both `1280x720` and `1920x1080` at requested scales `2`, `3`, `4`, plus `1024x768` at scale `2`. Distinguish requested from effective scale; unavailable combinations are reported, not counted as proven.
- [x] Run unchanged `collection_layout` and `collection_overflow` scenarios after the factory change. Require PASS and inspect Gallery captures for readable text, useful card area and visible focus. Update the proof report, then commit `feat: add dev Gallery collection composition proof`.

**Execution note:** The complete behavior/geometry coverage and implementation acceptance passed, but the first checkbox remains open for chronology: only the missing-layout preflight red preceded the initial seam/resource draft; the full behavior scenario was completed afterward. Later Side-heading, Collapse-height and expanded-filter text fixes have retained failing checks followed by passing runs. This does not retroactively satisfy the initial full test-first ordering.

**Acceptance:** Gallery hierarchy works in the actual collection controller and virtualization path with dense content. No normal screen is restyled. Any candidate type/density tradeoff that requires product behavior changes is a documented follow-up, not an unreviewed workaround.

### Task 5: Lobby Settings layering proof and tooling handoff

**Files**

- Create `src/main/resources/assets/duelcraft/ui/uitest/gallery_lobby.xml`.
- Create `src/main/java/com/haxerus/duelcraft/client/interaction/LobbyGalleryScenario.java`.
- Modify `src/main/java/com/haxerus/duelcraft/client/interaction/DuelLobbyScreen.java`: narrow layout injection only.
- Update `docs/superpowers/reports/2026-10-17-gallery-runtime-proof.md` and the style guide's measured-value/proof notes.

**Interfaces**

- Add package-private `static DuelLobbyScreen createFixture(String layout, ClientPreparationState preparation, ClientCollectionState.View collection, List<Target> targets, Function<PreparationCommand, CompletionStage<PreparationStatePayload>> send, Consumer<ScreenRoute> navigate)`.
- Pass the layout to the private constructor and existing `ManagementScreen(String resource, String title)`; public factories still supply `duel_lobby`. The fixture supplies `uitest/gallery_lobby`. No changes to `ManagementScreen` behavior are required.
- Produce scenario `gallery_lobby`, following the existing `DuelLobbyScenario` fixture state and real input checks. All preparation commands remain local test captures.

- [x] Add checks for a populated opponent list, selected opponent, Settings with six or more rule entries, first and scrolled-last mouse selection, visible focus and invalid settings. Require the production document's static IDs in the new layout; retain actual selectors, text fields and buttons.
- [x] Create the Gallery layout using the proved shared vocabulary: Change deck beside Settings at the upper right, bottom strip containing deck/rule information only. Keep popup styling reachable at the UI root (the selector dialog is reparented there); preserve the current modal/dialog ordering of **90/95**. Do not use a deeply nested high z-index as an assumed clipping escape. Keep explicit screen exit separate from cancelling an invitation.
- [x] Assert that at least five rule rows fit when space permits; their text and click bounds do not overlap. Mouse-select a middle rule and the scrolled final rule. Escape first closes only the selector, the next Escape closes Settings, and typing afterward cannot modify its hidden field. Verify Tab stays within the visible modal surface, hidden-field focus is cleared, and a pending invite disables its action. These checks must use routed input, not listener calls. Separately record the focused element before opening and after dismissing Settings, in both baseline and Gallery fixtures. `ManagementScreen.hide` explicitly clears hidden focus but does not explicitly restore the initiating control; if runtime does not restore it, retain the reproduction as an unmet style-guide migration requirement. Do not add a production behavior fix to this layout seam or label a failing assertion PASS.
- [ ] Run `./gradlew.bat runClient -PldTest=gallery_lobby -PldTestWindow=1280x720 -PldTestGuiScale=2`, then complete Task 4's full viewport/scale matrix, archiving each run. Run unchanged `duel_lobby` once after the seam change. Require PASS for the specified automated contracts, visible popup/focus captures and a human legibility review; report restoration observations separately.
- [x] Run `./gradlew.bat runClient -PldTest=group:duelcraft_gallery -PldTestWindow=1280x720 -PldTestGuiScale=2` for the combined deliverable and archive it. Run `./gradlew.bat test` once for final regression verification, not between aesthetic tweaks. Check `git diff --check` and review production factory call sites: every normal route must still select original resources. Commit `feat: prove Gallery lobby layers in the dev harness`.
- [x] Record accepted measurements, unresolved visual judgments and links to retained evidence. State that Home/duel browser navigation is still composition reference: do not add functional section navigation to a live duel. Actual production Home, collection, lobby and duel migration requires its own reviewed plan; this tooling proof does not close multiplayer or privacy acceptance.

Verification note (2026-10-17): all seven requested Gallery cases PASS 186/186 each, unchanged lobby PASS 58/58, combined group PASS 3470/3470; final JUnit 1112 passed / 1 Windows symlink assumption skip / 0 failures or errors. Human legibility/hierarchy/sharpness acceptance remains pending, so its combined checkbox stays open. Focus restoration and scale-dependent Gallery popup alignment/dirty-layout warnings remain explicitly unmet migration requirements in the runtime proof; no production behavior fix is included.

**Acceptance:** Rendering/input evidence is complete for a real root-hosted popup and modal transition, with guide gaps recorded explicitly; this does not mean all Gallery product requirements are implemented. The handoff contains reproducible commands and evidence, while shipped screen behavior and layouts retain their defaults.

## Execution notes and open evidence

- These are five sequential deliverables. Preserve the chosen subagent/review workflow where useful; each task exposes the interfaces its successor needs. Do not add more tasks merely to separate configuration from its owning deliverable.
- The cached `2.2.39.a-all.jar` inspected while planning has SHA-256 `563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46`. Bytecode output contains `Selector.updateDialogPosition`, `localToWorld`, `worldToLocalLayoutOffset` and anchor fields matching the inspected source; one follow-up `javap` emitted an access error. This is partial inspection of a cached file, not proof of the next run's resolved/loaded artifact or full binary/source identity. Task 1 owns that evidence.
- Run commands assume the worktree's documented native DLLs, generated VS project files, Java and game dependencies are already available. Do not invent a `skipNative` flag: this checkout does not define one. A missing prerequisite is a setup limitation to resolve explicitly, not a reason to weaken assertions or claim PASS.
- The style guide's dimensions remain candidates until measured in these fixtures. Fractional scaling, Minecraft glyph metrics and texture sampling require actual capture review; no parser or geometry check alone establishes comfortable reading.
- The plan intentionally duplicates two layout documents temporarily, because it allows real controllers to prove a new composition without turning on unfinished production presentation. Keep their required-ID checks tied to the originals and delete these proof copies when a later approved migration makes them redundant.

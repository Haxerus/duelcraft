# Gallery runtime proof — 2026-10-17

Task 1 is fixture-only. Production screens still load their original XML/styles. Human approval of hierarchy, legibility and sharpness is **pending**; an automated PASS is not that approval.

## Runtime provenance

- Duelcraft starting HEAD: `9f6b2aca1f4943e07000a1e604bf1a21abeafc3c`.
- LDLib2 source checkout: `C:/Users/haxer/Documents/Programming/Modding/LDLib2`, HEAD `d30e86b56b2a31982063ef907a335d36371b0bc4`.
- `GRADLE_USER_HOME=C:/Users/haxer/.gradle` for every Gradle invocation.
- `./gradlew.bat dependencyInsight --dependency ldlib2 --configuration runtimeClasspath`: BUILD SUCCESSFUL; resolves `com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.39.a` runtimeElements.
- Resolved artifact: `C:/Users/haxer/.gradle/caches/modules-2/files-2.1/com.lowdragmc.ldlib2/ldlib2-neoforge-1.21.1/2.2.39.a/ef1735423509837e5d6788fbfafeda5d3305197b/ldlib2-neoforge-1.21.1-2.2.39.a-all.jar`.
- `Get-FileHash -Algorithm SHA256 -LiteralPath <artifact>`: `563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46`.
- `javap -classpath <artifact> -p -c com.lowdragmc.lowdraglib2.gui.ui.elements.Selector` confirms the cited source behavior: `updateDialogPosition` transforms the anchor with `localToWorld` then root `worldToLocalLayoutOffset`; `show` reparents the dialog to root, positions and focuses it; `screenTick` dismisses it when the anchor moves more than 0.5 units. Its existing layout listener still calls `adaptPositionToScreen`.
- `javap -p` confirms `XmlUtils.loadXml(ResourceLocation)`, `UI.of(Document)`, `UI.of(UIElement,List<Stylesheet>,UI.DynamicSizeProvider)`. No API substitution was necessary.
- Both launched canvases attach loaded LDLib `2.2.39.a` and `UI.class` code-source `union:/C:/Users/haxer/.gradle/caches/modules-2/files-2.1/com.lowdragmc.ldlib2/ldlib2-neoforge-1.21.1/2.2.39.a/ef1735423509837e5d6788fbfafeda5d3305197b/ldlib2-neoforge-1.21.1-2.2.39.a-all.jar%23195!/`. This matches the hashed artifact, not merely its version label.

## Baselines and red/green evidence

Every client command used `-PldTestWindow=1280x720 -PldTestGuiScale=2`. Each report directory was copied before the subsequent launch. Archives live under ignored `build/gallery-proof/` and must be preserved with this checkout.

| Run | Result | Archive |
| --- | --- | --- |
| `./gradlew.bat runClient -PldTest=collection_layout ...` | PASS 89/89; BUILD SUCCESSFUL | `2026-10-17-task1-baseline-collection` |
| `./gradlew.bat runClient -PldTest=duel_lobby ...` | PASS 58/58; BUILD SUCCESSFUL | `2026-10-17-task1-baseline-lobby` |
| First Gallery attempt | ERROR: harness rejects `text, label` selector lists; corrected to `*` + instance filter | `2026-10-17-task1-red-query-error` |
| Text red | FAIL 66/160: 8/6-unit boxes clip 16/12-unit glyphs; long unwrapped name overflows | `2026-10-17-task1-red-text` |
| Initial green | PASS 160/160, both canvases captured | `2026-10-17-task1-green-initial` |
| Refined states, selector and scrolling | PASS 213/213 | `2026-10-17-task1-refine` |
| Popup alignment red | FAIL 229/231; only `scaled popup stays aligned with selector`, on both canvases | `2026-10-17-task1-red-popup` |
| Local alignment correction | PASS 237/237 | `2026-10-17-task1-aligned` |

A test compile attempt also caught an inferred `TextElement` loop variable assigned its `UIElement` parent; corrected before the valid text red. Neither that compiler error nor the selector-list error is counted as intended red evidence.

The popup regression proves the existing root-coordinate workaround is still required even with source/bytecode agreement. Only the specimen copies `CollectionController`'s small guarded alignment correction; no production workaround or controller changed.

## Owners and measured vocabulary

| Owner | Content / behavior |
| --- | --- |
| `assets/duelcraft/lss/gallery.lss` | Literal semantic palette and states; management/duel dimensional scopes; real button base/hover/pressed textures |
| `assets/duelcraft/ui/uitest/gallery_specimen.xml` | Fixture composition, titles, bounded full name, fields, six-choice selector, owned scroller, reserved feedback, confirmation layer |
| `GallerySpecimenScenario.Specimen` | Local scaled screen host, explicit button Enter/Space, deterministic Tab/Shift-Tab, selector keyboard handling, active modal focus, fake future/counters, local selector clamp |
| `DuelUiAssertions.textFits` | Existing measured glyph algorithm; visibility changed to public only |

Retained candidate font sizes: management display/title/section/body/metadata 48/24/20/16/14; duel omits Home display and uses 18/15/12/10.5. Ordinary controls remain 40/30; dense confirmation opener 32/24. Text boxes reserve explicit height above their glyph size. Selected detail wraps a long accented name in a fixed width, rather than shrinking text; the text field owns a bounded editable viewport. Twelve fixed-height deck rows scroll without moving the heading or error. Existing Minecraft font only; no assets/dependencies added.

Exceptions: the focus edge uses two design units so it remains separately visible at root scale; selection keeps its gold text and explicit Selected/Active marker. Dialog and popup are fixture-local layers. No new generic modal/controller framework is introduced. The Home display title has no duel equivalent.

## Review limits

The agent inspected real PNG captures at both design sizes. Initial review found opaque button defaults masking selected/pending/focus styles, and default gray scroller backgrounds; corrected specificity and textures. Subsequent captures show separate pale focus, gold selection, warning pending, pink errors, bounded wrapped text and readable accents/fallback glyphs. Font/control candidates did not require reduction at this tested viewport. These are agent observations, not human legibility/sharpness approval or a wider viewport-matrix claim.

Only 1280×720 requested window at GUI scale 2 is proved here. The full 1920×1080, scales 2–4, 1024×768 matrix and production migration remain later work. No installed-JAR, networking, privacy or ownership acceptance is implied.

## Final acceptance

`./gradlew.bat runClient -PldTest=gallery_specimen -PldTestWindow=1280x720 -PldTestGuiScale=2`: **PASS 255/255**, 154 steps, 10 captures, BUILD SUCCESSFUL in 54s; run `354996_18ed8b75`.

The acceptance archive is [build/gallery-proof/2026-10-17-task1-acceptance](../../../build/gallery-proof/2026-10-17-task1-acceptance/). Requested and actual window/framebuffer: 1280×720; configured/effective GUI scale: 2 / 2.0; GUI viewport: 640×360. Management design 1280×720 uses root scale 0.5; duel design 960×540 uses 0.6666667. Both therefore render corresponding controls at the same physical size. No canvas scaling path changed.

Agent visually inspected these acceptance PNGs:

- [Management vocabulary](../../../build/gallery-proof/2026-10-17-task1-acceptance/screenshots/gallery_specimen/34_gallery-management.png)
- [Duel vocabulary](../../../build/gallery-proof/2026-10-17-task1-acceptance/screenshots/gallery_specimen/111_gallery-duel.png)
- [Aligned duel selector](../../../build/gallery-proof/2026-10-17-task1-acceptance/screenshots/gallery_specimen/131_gallery-duel-selector.png)
- [Management confirmation](../../../build/gallery-proof/2026-10-17-task1-acceptance/screenshots/gallery_specimen/43_gallery-management-confirmation.png)
- [Management pressed state](../../../build/gallery-proof/2026-10-17-task1-acceptance/screenshots/gallery_specimen/76_gallery-management-pressed.png)

Additional captures retained: `54_gallery-management-selector.png`, `73_gallery-management-hover.png`, `120_gallery-duel-confirmation.png`, `150_gallery-duel-hover.png`, `153_gallery-duel-pressed.png`.

Automated coverage includes measured text fit and explicit height room (including visible confirmation, accents and fallback glyphs), bounded wrapped full name, mouse activation, forward/reverse Tab, Enter/Space, disabled mouse/keyboard nonactivation, a single pending submission despite duplicates, failed future feedback, actual resolved selected/focus/pending RectTexture colors, modal focus trapping/restoration, wheel reach to the final deck row, six real selector entries with five full visible rows, anchored popup bounds, routed mouse selection, selector keyboard choice/open/Escape, text entry and real Button hover/pressed state. Synthetic harness events route through the actual screen/widget handlers; this is not an OS physical input claim.

Human font legibility/sharpness approval remains **pending**.

## Project tests

`GRADLE_USER_HOME=C:/Users/haxer/.gradle ./gradlew.bat test`: BUILD SUCCESSFUL in 1m48s. Fresh JUnit XML totals: 72 suites, 1113 tests, zero failures/errors, one skipped (`DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile`: Windows symlink privilege unavailable). Raw output: `build/gallery-proof/task-1-tests.log`; XML archive: `build/gallery-proof/2026-10-17-task1-junit/`. `git diff --check` passed; Git emitted only environment ignore-file access and line-ending warnings.

## Task 2 — parser-backed resource preflight

Added fixture-only `GalleryPreflight` resource/Document overloads and `ids(Document)`, plus DEV_ONLY `gallery_preflight` in `duelcraft_gallery` without a world. The specimen calls preflight before opening either design canvas and attaches errors/unchecked diagnostics. Required IDs are explicit in the scenario. No production resources or scaling/popup workarounds changed.

The preflight uses LDLib2 `Stylesheet.RULE`/`DECL`, `PropertyRegistry.byName`, the registered value parser and `compute()`, and `UI.of(document)`. It inspects inline declarations, embedded style blocks and literal linked LSS resources. Unknown properties, duplicates, missing required IDs/resources and accidental browser constructs receive source/element-or-selector/property context instead of disappearing into LDLib2 debug logs.

TDD evidence: an empty diagnostic implementation produced **FAIL 1/12** (11 expected failed checks), archived in `build/gallery-proof/2026-10-17-task2-red/` (run `367532_18ed8b75`, log `build/gallery-proof/task2-red.log`). Final requested command `GRADLE_USER_HOME=C:/Users/haxer/.gradle ./gradlew.bat runClient -PldTest=gallery_preflight -PldTestWindow=1280x720 -PldTestGuiScale=2` produced **PASS 12/12**, one step, run `371808_13f255ed`, BUILD SUCCESSFUL in 40s. Archive: `build/gallery-proof/2026-10-17-task2-preflight-acceptance/`; raw log: `build/gallery-proof/task2-preflight-acceptance.log`.

The positive specimen's result is `errors=[]`, `uncheckedValues=[]`. The actual runtime has no registered `font-family` property: the negative example reports `memory:font <root #bad-font> property 'font-family' value 'missing-gallery-font': unknown property in pinned LDLib2`. This is a property diagnostic, not font asset availability scanning.

The only unchecked values in the negative examples are `memory:numeric <root #bad-number> property 'width' value 'twelve'` and the same element's `height: 2rem`: registered parsers return null. Both appear in `uncheckedValues` with “not verified; requires runtime assertion or visual review”. The negative scenario asserts their detection and never accepts them as fixture values. For future accepted fixture values, the named owners are `GallerySpecimenScenario` runtime text/bounds/state assertions and agent visual review; the current accepted resource has none outstanding.

Coverage limits: matched declaration patterns share LDLib2's comment/enumeration limitations, rather than implementing a new grammar. No claim is made that all malformed syntax or selectors are rejected, selectors match intended widgets, non-null values look correct, font glyphs render correctly, or layout/state/overlap/clipping is proved by preflight. Those need specimen assertions and visual review. Literal linked stylesheet existence is checked; Java-generated textures and dynamic resource names are not covered. Existing production XML/LSS remain contract references, not targets of a blanket lint. Human legibility/sharpness approval and the wider viewport matrix remain pending as above.

Final specimen command `./gradlew.bat runClient -PldTest=gallery_specimen -PldTestWindow=1280x720 -PldTestGuiScale=2` retains **PASS 255/255**, now 155 steps including the preflight report step, with ten captures. Acceptance archive: `build/gallery-proof/2026-10-17-task2-specimen-acceptance/`; raw log: `build/gallery-proof/task2-specimen-acceptance.log`. The preflight attachment again reports zero errors and zero unchecked values. Existing canvas/runtime/input acceptance is unchanged; no new visual approval is implied. Full JUnit is deferred to final Task 5 by the execution scope.

## Task 3 — retained evidence gallery

`tools/ui-gallery.ps1` produces a standalone static archive with relative capture/report links, HTML-escaped scenario/step/attachment/error text and capture diagnostics. Referenced images retain their report-relative paths, so the original JSON remains usable. Existing output directories are refused; capture traversal, absolute paths and reparse-point paths are rejected. No checkout metadata is inferred.

Exact real archive command (Windows PowerShell process execution policy override was necessary on this machine):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/ui-gallery.ps1 -ReportDir build/gallery-proof/2026-10-17-task2-specimen-acceptance -OutputDir build/ui-gallery/2026-10-17-specimen-02
```

Output: [retained gallery](../../../build/ui-gallery/2026-10-17-specimen-02/index.html). Source revision/dirty state and dependency hash remain **unknown**: the historical capture was taken with uncommitted Task 2 contents, so the later commit is not assigned retroactively. LDLib2 version and environment come directly from the report; requested window/scale, actual framebuffer/GUI/effective scale and design sizes are shown in the original viewport attachments. Top-level environment dimensions can differ in meaning from the detailed attachments; both remain unmodified evidence.

TDD: initial `powershell -NoProfile -File tools/ui-gallery.test.ps1` was blocked by the machine execution policy; rerun with process-only `-ExecutionPolicy Bypass` failed because the implementation did not exist. Final `powershell -NoProfile -ExecutionPolicy Bypass -File tools/ui-gallery.test.ps1` passes all eight assertions covering PASS/FAIL, missing metadata, missing/unsafe capture diagnostics, escaped HTML, relative links/copying and existing archive refusal. Final incomplete synthetic archive: `build/gallery-proof/task3-test-e33acfc1292248ca893dd8b0536fde20/archive/index.html` (FAIL with missing/unsafe captures).

Headless installed Edge via bundled Playwright opened the real static index over loopback HTTP: all ten images loaded, the original report link opened successfully, attached requested viewport text was present, and a 390×844 viewport had no horizontal overflow. Agent inspected [narrow viewport screenshot](../../../build/gallery-proof/task3-gallery-narrow.png): readable wrapping, visible source unknowns, report links and PASS verdict. Browser check script: `build/gallery-proof/task3-browser-proof.js`. This checks gallery usability, not Minecraft screenshot aesthetics or human font acceptance. No client/Gradle launch was required for the gallery. `git diff --check` passed.


## Task 4 — real-controller dense collection composition

Only `CollectionScreen`'s package-private layout-injection seam changes production Java. All public factories keep `duelcraft:ui/collection_screen.xml`, controller construction, sample request/refresh defaults, lifecycle and fixed-canvas scaling. Production XML/styles/routes and the existing popup correction are unchanged. `CollectionGalleryScenario` is DEV_ONLY, group `duelcraft_gallery`, without a world, using the real controller, `CollectionFixture.model(true)`, cards/query, null artwork and a controlled Save future.

The starting capture revision is `3dac3ddb434346b6c6160d808b9d464c82175d8b` with Task 4 changes dirty. These captures are **not** attributed to the later commit. Exact frozen capture sources and hashes are retained in `build/gallery-proof/task4-reviewed-source/`. The runtime artifact was rehashed at the path documented above: LDLib2 `2.2.39.a`, SHA256 `563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46`. All Gradle commands used `GRADLE_USER_HOME=C:/Users/haxer/.gradle`.

### Fresh reviewed matrix

Each Gallery case ran `./gradlew.bat runClient -PldTest=gallery_collection -PldTestWindow=<window> -PldTestGuiScale=<requested>`. The unchanged baselines used their existing scenario names with 1280×720/2. Every command exited 0 with BUILD SUCCESSFUL. Every report was archived before the next launch, through `powershell -NoProfile -ExecutionPolicy Bypass -File tools/ui-gallery.ps1 -ReportDir <raw archive> -OutputDir <fresh gallery>`, with explicit base revision/dirty state and dependency hash. `build/gallery-proof/task4-matrix.ps1` retains the exact matrix driver.

| Case | Requested window / scale | Effective scale | GUI | Checks | Retained gallery |
| --- | --- | --- | --- | --- | --- |
| collection_layout | 1280x720 / 2 | 2.0 | 640x360 | PASS 89/89 | [354544_18ed8b75](../../../build/ui-gallery/task4-reviewed-collection_layout-1280x720-s2/index.html) |
| collection_overflow | 1280x720 / 2 | 2.0 | 640x360 | PASS 88/88 | [361528_18ed8b75](../../../build/ui-gallery/task4-reviewed-collection_overflow-1280x720-s2/index.html) |
| Gallery | 1024x768 / 2 | 2.0 | 512x384 | PASS 3017/3017 | [372740_18ed8b75](../../../build/ui-gallery/task4-reviewed-gallery_collection-1024x768-s2/index.html) |
| Gallery | 1280x720 / 2 | 2.0 | 640x360 | PASS 3017/3017 | [373228_18ed8b75](../../../build/ui-gallery/task4-reviewed-gallery_collection-1280x720-s2/index.html) |
| Gallery | 1280x720 / 3 | 3.0 | 427x240 | PASS 3017/3017 | [352828_6779959d](../../../build/ui-gallery/task4-reviewed-gallery_collection-1280x720-s3/index.html) |
| Gallery | 1280x720 / 4 | 3.0 | 427x240 | PASS 3017/3017 | [349188_335a45a8](../../../build/ui-gallery/task4-reviewed-gallery_collection-1280x720-s4/index.html) |
| Gallery | 1920x1080 / 2 | 2.0 | 960x540 | PASS 3017/3017 | [353788_18ed8b75](../../../build/ui-gallery/task4-reviewed-gallery_collection-1920x1080-s2/index.html) |
| Gallery | 1920x1080 / 3 | 3.0 | 640x360 | PASS 3017/3017 | [376672_18ed8b75](../../../build/ui-gallery/task4-reviewed-gallery_collection-1920x1080-s3/index.html) |
| Gallery | 1920x1080 / 4 | 4.0 | 480x270 | PASS 3017/3017 | [368092_18ed8b75](../../../build/ui-gallery/task4-reviewed-gallery_collection-1920x1080-s4/index.html) |

There are seven requested Gallery runs and six distinct available effective combinations. **Requested scale 4 at 1280×720 clamps to effective 3 and is unavailable, not proven as effective scale 4.** Effective scale 4 is proven at 1920×1080. Framebuffer dimensions match the requested window in each initial viewport attachment. Full raw reports/logs are under matching `build/gallery-proof/task4-reviewed-*` paths; aggregate metadata is `build/gallery-proof/task4-reviewed-summary.json`.

### Content, input and bounds

Every Gallery run begins with the unchanged collection layout as a selected-card behavioral baseline, then opens the fixture through the real controller. `GalleryPreflight` requires all 151 original static IDs; independent XML comparison also confirms every original widget tag remains unchanged (152 fixture IDs including the lower-row wrapper). Dynamic deck/filter/browser/selector IDs and widget types are exercised. Preflight reports zero errors and unchecked values.

The proof uses actual 60 Main / 15 Extra / 15 Side with Side expanded in both densities, a long selected name and effect, missing artwork and empty search. It exercises browser right-click Add, deck right-click Remove, visible Add/Remove, independent effect/Main/browser wheel scrolling, Main/Extra/Side endpoints, expanded rich filters and a real Measure popup, populated My decks and a focused name field. Typed deck-name boundaries are 1 and 128 Java string units; typed export-name boundaries are 1 and 96 base units (5 and 100 including `.ydk`) in the actual export field, without exporting.

The import proof allocates with `Files.createTempFile(deckDirectory, "gallery-fixture-", ".ydk")`, writes `DeckListWriter.toYdk`, discovers it through the real picker, clicks its exact generated basename, checks scaled popup anchor/clamp and selected value, and closes without submitting Import/activation. Teardown deletes only the exact allocated path. It never reuses/deletes `collection-import-ui.ydk` or pre-existing decks. No fixture files remain after the completed runs.

Before Save, the full draft is edited to valid 59/15/15. The controlled future verifies pending does not acknowledge the draft, and exceptional completion retains it, restores editing and shows the existing actionable error. GLFW resizes the same live fixture to 1100×800, waits for settled layout, verifies the draft, long selected name and meaningful Main/browser pixel offsets survive, then restores the requested size. Eleven captures per Gallery run retain Standard, Large, empty, filters, field focus, My decks, import popup, export name, pending/failing Save and live resize states.

At 1280×720/effective 2, measured GUI bounds are Main 308×155 with first card 27×39.5 Standard or 35×51.5 Large; Extra 152×46 Standard or 152×57 Large; Side 152×57; browser 174×168. These correspond to a 616×310 Main area and 54×79 / 70×103 cards on the 1280×720 design canvas. All four browser columns show complete artwork at rest; Main shows at least one complete row with Extra/Side expanded, and each lower-section first-row card is contained in its viewport. Each capture's adjacent attachments retain full counts, card/viewport bounds, mounted rows, fixed 128-unit browser row height, requested/effective scale, framebuffer, GUI and canvas transform.

The reusable pre-capture audit covers all visible/displayed button text containment and rendered TextElement lines, allowing intentional scroller overflow and existing clipped saved-name rows with full-name tooltips. Reviewed runs have 3,017 checks each; the drop from 3,041 is exactly the metrics for the explicitly removed/hidden permanent instruction labels, with behavioral/geometry assertions retained.

### Red evidence, visual review and limits

**Test-first ordering deviation:** Only the missing-layout preflight scaffold red preceded the initial seam/resource draft. The complete behavior/geometry scenario was filled out afterward, so the first Task 4 checklist item is left open for its missed ordering requirement. Implementation acceptance and the fresh matrix are complete; this is a procedural deviation, not missing runtime coverage. The later Side-heading, Collapse-height and expanded-filter fixes have the actual retained red/green evidence described below.

Retained red galleries: `task4-red-missing-layout` (0/1 expected missing-resource preflight); `task4-textfit-red` (81/83, Side heading needs 206.67 units vs 190 available at size 20); `task4-toggle-height-red` (117/119, inherited Collapse height 24 cannot contain its 28-unit label); `task4-all-text-red` (3040/3041, expanded Close filters text 112 units exceeds width 104). Earlier interaction/geometry passes and the caption-removal review are retained separately; interrupted matrix launches are incomplete evidence and are not counted in the reviewed matrix.

Fixture-only measured exceptions are recorded in the [style guide](../specs/2026-10-17-gallery-style-guide.md#task-4-collection-proof-measurements): Side heading 18, Collapse height 32, Import/export width 160, Filters width 128, four Standard/three Large columns in the half-width lower sections. Main remains 10/8 columns; browser remains four columns, 74×108 cards, 128-unit virtual rows and compact 10-unit captions. Permanent search-help and export-folder captions are omitted/hidden; all required IDs remain.

Agent inspection covered reviewed 720p Standard/Large/filter/focus/My decks/import/export/failed-Save captures, 1080p Large/import captures and 1024×768 Standard/import captures. Text and card areas are usable and the real field focus is visible. At extreme selected-name length the effect starts below the initial details viewport and remains independently scroll-accessible; simultaneous full artwork/name/stats/effect display is not claimed. My decks dynamic rows retain some inherited selected-button treatment. At 1024×768 the fixed 16:9 canvas is 1024×576 with the unchanged Minecraft menu background visible in the letterbox margins, and compact browser captions are about 8 physical pixels. **Human hierarchy, legibility and sharpness approval remains pending; production migration is not approved.**

Self-review confirms the factory defaults and popup correction are preserved, scoped source matches the capture snapshot, original IDs/types remain, and no production layout/style/route changed. `git diff --check` passed. Full JUnit is deliberately deferred to Task 5 as scoped; these are dev-client fixture and unchanged-layout checks, not installed-JAR/multiplayer acceptance.

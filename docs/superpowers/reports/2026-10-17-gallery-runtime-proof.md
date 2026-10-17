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

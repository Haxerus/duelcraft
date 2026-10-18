# Gallery fields and scrollbars — 2026-10-17

User review found field text too close to its border and scrollbars that retained LDLib's stock dark appearance with disproportionately thin tracks. Starting point: clean `a0897ad`, branch `codex/ui-redesign` in the existing deck-editor worktree.

## Cause and correction

The previous correction explicitly restored stock GDP arrow/thumb state textures and enforced 4/3-unit tracks below 12/9-unit arrows. Its assertions encoded that appearance. This was a Gallery styling and review error, not evidence of a new LDLib regression. Gallery text fields styled their borders but inherited TextField's two-unit content padding.

Gallery now sets 10-unit horizontal content padding (7.5 on the duel canvas). LDLib TextField uses its content coordinates for text, caret, selection and mouse positioning, so these stay aligned. Scroller tracks and thumbs match the 12/9-unit arrow width. Flat mauve thumbs lighten on hover and turn amber while pressed; authored flat white arrow silhouettes are tinted to Gallery muted/cream/amber colors. No shaded stock thumb remains. Selector popups keep their intentionally hidden arrows.

Only Gallery LSS, two fixture arrow textures, development scenarios and documentation change. Production screens do not load this stylesheet; production UI and LDLib itself are unchanged. The owning guide now records these rules and corrects a stale half-width-section sentence from the preceding refinement.

## Verification

The updated component contract failed before the LSS correction: native specimen run `345704_76685ab0`, 301/311 checks. Exactly five checks failed on each design canvas: width consistency, both arrow palettes, flat thumb states and field inset. Archive: `build/gallery-proof/field-scroll-red/`; log: `field-scroll-red.log`.

A subsequent capture helper initially used the wrong InputDriver method name and failed compilation; this was corrected to `moveTo` before the runtime run. The compile log is retained separately as `field-scroll-compile.log`. The inset assertion uses local coordinates on both sides, and screenshot mouse input uses transformed screen coordinates.

Final runtime and visual review results follow. Full JUnit and unrelated production tests are not rerun for this fixture-only styling change; earlier results remain historical.

The first corrected runtime passed 4281/4281 checks, but native image review rejected the stock white arrow masks: their baked dark outline still looked like pushpins. That intermediate evidence is retained as `field-scroll-stock-mask/` and is not final visual acceptance. Two small authored 12×12 transparent pixel glyphs replace those masks; they contain only a flat white triangle, tinted by LSS. This visual finding is why property assertions do not replace screenshot review.

## Final evidence

With `GRADLE_USER_HOME=C:/Users/haxer/.gradle`:

```powershell
./gradlew.bat runClient '-PldTest=gallery_specimen,gallery_collection,gallery_lobby' -PldTestWindow=1280x720 -PldTestGuiScale=2 -PskipNative
./gradlew.bat runClient '-PldTest=gallery_specimen,gallery_collection,gallery_lobby' -PldTestWindow=1024x768 -PldTestGuiScale=2 -PskipNative
```

- 1280×720, scale 2: run `347792_7bbe5548`, **3/3 scenarios, 4285/4285 checks**, exit 0. Archive `field-scroll-1280/`, log `field-scroll-green.log`.
- 1024×768, scale 2: run `372952_36a7586f`, **3/3 scenarios, 4285/4285 checks**, exit 0. Archive `field-scroll-1024/`, log `field-scroll-small.log`.
- Both runs cover management and duel component canvases, actual thumb hover/held press, editable-field insets, input flows, popup selection, collection stress/scrolling and live resize. Runtime source stayed unchanged between final runs. SHA256 values for the seven changed runtime/fixture files are retained in `field-scroll-source-hashes.json`.
- Root inspected final normal collection, both thumb states and Settings at 1280, and normal/Large collection at 1024. Field text has a clear inset, the quantity field remains readable, and the scrollbar uses flat arrow silhouettes without a dark outline. Generic runtime shader/Gradle warnings remain; this change does not claim to resolve them.

All archive paths above are relative to `build/gallery-proof/`. Human aesthetic approval and production migration remain separate.

Independent subagent review opened collection29, Settings70/popup75 and specimen11/15/118/122 from both final archives (14 images). No remaining findings: editable insets are comfortable, flat arrow silhouettes have no GDP shading, thumbs have the intended width and visible hover/press colors, and production imports are unchanged. The reviewer rejected the intermediate stock mask before approving these final captures.

Review screenshots: [collection](../../../build/gallery-proof/field-scroll-1280/screenshots/gallery_collection/29_gallery-collection-normal.png), [Settings](../../../build/gallery-proof/field-scroll-1280/screenshots/gallery_lobby/70_gallery-lobby-settings.png), [held thumb](../../../build/gallery-proof/field-scroll-1280/screenshots/gallery_specimen/15_gallery-management-scroll-pressed.png).

# Gallery proof correction — 2026-10-17

## Task 1 — component correction

Production resources and behavior are unchanged. Gallery fixture action Buttons have an explicit `gallery-action` role. Verified controller-created collection Button roles `saved-name`, `filter-choice`, and `filter-chip` share the action styles; the `saved-row` itself is a UIElement, so it is not treated as a Button. Native selector/scroller internal Buttons receive no ordinary action backgrounds or heights. Future lobby avatar-card renderers own their multi-element typography and geometry.

LDLib2 source confirmed the cause: Button uses a horizontal flex row, centers its main axis, and centers text inside its child TextElement; Gallery gave that child a fixed height without centering the cross axis. Gallery also selected every descendant Button, replacing native scroller arrow textures and 5-unit dimensions with ordinary 40/30-unit actions. The correction centers the child box and keeps its centered line alignment, preserving Minecraft's font and sizes.

Scrollbar components now explicitly use 12×12 management / 9×9 duel arrows with the original UP/DOWN_ARROW_NO_BAR_S base/light/white textures. Track/thumb widths are 4/3 design units. Textless thumb padding is zero: Button's native padding2 otherwise forces a four-unit minimum thumb into the duel's three-unit track. The original native thumb sprite state textures remain unchanged.

`GalleryVisualAssertions.control(ctx, selector)` checks child-box center, rendered single-line center, horizontal alignment and fit. Attachments include transformed outer bounds, layout content bounds, line start Y, font/alignment, winning StyleBag slots, and resolved rect/sprite values. `scroller(ctx, selector)` checks compact arrow dimensions, track/thumb thickness, exact native arrow/thumb texture identities across default/hover/pressed states, and exclusion from ordinary action roles. The helpers are fixture-specific and use existing ElementRef/StyleBag APIs.

Evidence (all under `build/`, retained locally):

- `gallery-proof/correction-red` / `ui-gallery/correction-red`: original failing checks, 269/283; four centering and three arrow checks fail on each canvas. Command: `./gradlew runClient -PldTest=gallery_specimen -PldTestWindow=1280x720 -PskipNative` with `GRADLE_USER_HOME=C:/Users/haxer/.gradle`.
- `correction-intermediate`: diagnostic-only run launched before the align-items edit was copied. Excluded from acceptance because edits overlapped launch.
- `correction-green`: frozen first green, 285/285; native five-unit arrow geometry proved too small/dark on actual image inspection. Superseded by explicitly sized arrows.
- `correction-preflight`: 12/12, resource parser diagnostics pass. Command substitutes `-PldTest=gallery_preflight`.
- `correction-readable`: frozen candidate with 12-unit arrows on both canvases. Superseded by the agreed duel9 mapping.
- `correction-components-final`: frozen 12/9 candidate caught duel thumb minimum-width overflow. Retained as focused red evidence for the padding correction.
- `correction-components-v2`: 287/287. Actual state-image review rejected primary focus contrast (black text on dark fill) and modal-obscured focus crops after dense press. Retained as visual-red evidence; not acceptance.
- `correction-components-v3`: focused-primary fill and dense focus capture setup corrected; existing pending-state checks exposed focus-rule precedence over pending fill (two failures). Retained as targeted red evidence.
- `correction-components-v4`: 295/295. Primary focus/pending visual precedence corrected. Dense pressed capture still showed confirmation-overlay dimming because the bound action opens the modal during press; this was rejected during actual image inspection.
- `correction-components-v5`: final frozen source snapshot and SHA256 manifest. After the earlier confirmation input tests, the specimen dense Button uses a no-op action solely during component-state captures so held press remains unobscured; pointer leaves before focus capture. Native hover/press/focus input is retained. Exact raw report, captures and review-image mapping are retained here.

Each raw run was archived before a later launch overwrote `build/ldlib2-uitest`; HTML galleries were built by `powershell -ExecutionPolicy Bypass -File tools/ui-gallery.ps1` into fresh `build/ui-gallery/correction-*` directories. Frozen snapshots identify the six runtime files exactly rather than claiming a dirty base commit alone identifies the tested source. Base: `ecff5eae42e1805aab7623902f4ea352602f92ed`. Runtime LDLib2: `2.2.39.a`, SHA256 `563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46`.

Actual viewport: window/framebuffer1280×720, GUI640×360, configured/effective GUI scale2; management design1280×720/root0.5 and duel design960×540/root2/3. At this viewport both component sets occupy matching physical-pixel sizes. The broader matrix and full JUnit run belong to Task3 after independent component/composition review.

Functional verification and visual acceptance are separate. Independent component review and human approval remain pending. Approved HTML reference captures are `build/gallery-proof/correction-reference/collection-full.png` and `lobby-full.png`; exact approved HTML sources and capture metadata are retained alongside them. Do not promote diagnostic or rejected images to accepted goldens.

Final Task1 functional verdict: PASS, `correction-components-v5`, 1/1 scenario, 295/295 checks, 90 captures, 195 steps, 19363ms; Gradle exit0 / BUILD SUCCESSFUL. Preflight12/12 is retained separately. No full matrix or full JUnit was run in Task1. Normal Minecraft/NeoForge shader/asset/command warnings remain in launch logs; no Gallery check failures in the final run.

Implementer image inspection opened both approved reference PNGs and all fifteen copied native images in `build/gallery-proof/correction-components-v5/review/`:

- `management-full.png`, `duel-full.png`
- `management-normal-default.png`, `management-dense-default.png`, `duel-normal-default.png`, `duel-dense-default.png`
- `normal-hover.png`, `normal-pressed.png`, `normal-focused.png`, `disabled.png`
- `dense-hover.png`, `dense-pressed.png`, `dense-focused.png`
- `management-scrollbar.png`, `duel-scrollbar.png`

`review/capture-map.txt` maps each exact copy back to its retained original capture. These are copies at native resolution, not enlarged or retouched. Observed: ordinary/dense glyphs remain centered and unclipped with mixed case/descenders; hover and held press retain readable labels; keyboard focus is a gold edge independent of selected/pending state; disabled captions remain visible. Scroller arrow shapes are compact and directional, track/thumb thin, with no elongated blank action rectangles. Native arrows remain the library's darker default sprites, with brighter hover/pressed sprites; independent review must judge their visibility. Minecraft raster typography differs from the HTML reference's smooth browser font as required. The specimen is a component inventory, so this verdict makes no collection/lobby composition claim. Independent C1/C2/C7 review remains pending and its checkbox is intentionally open.

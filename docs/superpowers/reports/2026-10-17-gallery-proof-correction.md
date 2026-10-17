# Gallery proof correction â€” 2026-10-17

## Task 1 â€” component correction

Production resources and behavior are unchanged. Gallery fixture action Buttons have an explicit `gallery-action` role. Verified controller-created collection Button roles `saved-name`, `filter-choice`, and `filter-chip` share the action styles; the `saved-row` itself is a UIElement, so it is not treated as a Button. Native selector/scroller internal Buttons receive no ordinary action backgrounds or heights. Future lobby avatar-card renderers own their multi-element typography and geometry.

LDLib2 source confirmed the cause: Button uses a horizontal flex row, centers its main axis, and centers text inside its child TextElement; Gallery gave that child a fixed height without centering the cross axis. Gallery also selected every descendant Button, replacing native scroller arrow textures and 5-unit dimensions with ordinary 40/30-unit actions. The correction centers the child box and keeps its centered line alignment, preserving Minecraft's font and sizes.

Scrollbar components now explicitly use 12Ã—12 management / 9Ã—9 duel arrows with the original UP/DOWN_ARROW_NO_BAR_S base/light/white textures. Track/thumb widths are 4/3 design units. Textless thumb padding is zero: Button's native padding2 otherwise forces a four-unit minimum thumb into the duel's three-unit track. The original native thumb sprite state textures remain unchanged.

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

Actual viewport: window/framebuffer1280Ã—720, GUI640Ã—360, configured/effective GUI scale2; management design1280Ã—720/root0.5 and duel design960Ã—540/root2/3. At this viewport both component sets occupy matching physical-pixel sizes. The broader matrix and full JUnit run belong to Task3 after independent component/composition review.

Functional verification and visual acceptance are separate. Independent component review and human approval remain pending. Approved HTML reference captures are `build/gallery-proof/correction-reference/collection-full.png` and `lobby-full.png`; exact approved HTML sources and capture metadata are retained alongside them. Do not promote diagnostic or rejected images to accepted goldens.

Final Task1 functional verdict: PASS, `correction-components-v5`, 1/1 scenario, 295/295 checks, 90 captures, 195 steps, 19363ms; Gradle exit0 / BUILD SUCCESSFUL. Preflight12/12 is retained separately. No full matrix or full JUnit was run in Task1. Normal Minecraft/NeoForge shader/asset/command warnings remain in launch logs; no Gallery check failures in the final run.

Implementer image inspection opened both approved reference PNGs and all fifteen copied native images in `build/gallery-proof/correction-components-v5/review/`:

- `management-full.png`, `duel-full.png`
- `management-normal-default.png`, `management-dense-default.png`, `duel-normal-default.png`, `duel-dense-default.png`
- `normal-hover.png`, `normal-pressed.png`, `normal-focused.png`, `disabled.png`
- `dense-hover.png`, `dense-pressed.png`, `dense-focused.png`
- `management-scrollbar.png`, `duel-scrollbar.png`

`review/capture-map.txt` maps each exact copy back to its retained original capture. These are copies at native resolution, not enlarged or retouched. Observed: ordinary/dense glyphs remain centered and unclipped with mixed case/descenders; hover and held press retain readable labels; keyboard focus is a gold edge independent of selected/pending state; disabled captions remain visible. Scroller arrow shapes are compact and directional, track/thumb thin, with no elongated blank action rectangles. Native arrows remain the library's darker default sprites, with brighter hover/pressed sprites; independent review must judge their visibility. Minecraft raster typography differs from the HTML reference's smooth browser font as required. The specimen is a component inventory, so this verdict makes no collection/lobby composition claim. Independent C1/C2/C7 review remains pending and its checkbox is intentionally open.


## Task 2 — Collection composition correction

Status: implementation complete; independent C1–C4/C7 visual review and human approval remain pending. Task1 independent review approved C1/C2/C7, and its final checkbox is now marked complete.

### Changes

The fixture now has one 48-unit action row: a bounded editable title (274 units), immediately adjacent Active label, flexible space, New deck, My decks, Import / export, secondary Save, primary Use for duels, Help and Close. The obsolete navigation row is hidden/structural, with all required controller IDs/types retained. Clear active moved unchanged into My decks, where its active-state visibility, compact centered label and real scroller are tested. Help and Close are exercised through routed input; closing an edited title still opens the real discard/save decision.

Saved list / Unsaved changes now appears inside Main's heading, beside the full unchanged production Main count caption. The count-only flex selector avoids making the status grow into a spacer. Available center width616 = Main248 + status120 + Sort76 + density132 + gaps24 + padding16. Main's18-unit text measured224; the longest12-unit status117.33, Sort65.33 and density128 fit their allocated controls. Main and metadata line alignment is centered. Compact Sort/density retain32-unit heights. Explicit primary text color corrects the pre-existing white-on-cream fixture override.

Normal composition uses the real controller with40 Main /5 Extra /0 Side, ordinary Dragon study title, authoritative fixture active ID and a real rename/Save acknowledgement. Four existing local mockup PNGs are copied into fixture-only textures and injected through the existing createFixture texture function. No new seam, download, screenshot mutation, production renderer or controller change. Corresponding display names/types/stats are supplied for Blue-Eyes White Dragon, The13th Grave, Labrynth Cooclock and Limit Reverse; fixture codes/effect description remain synthetic. All normal cards have owned copies. The actual browser is put into Passcode order through its real selector so the local artwork is mixed. The separate original synthetic60/15/15 missing-art and long-name stress fixture remains, with Standard/Large density and the real four-column128-unit browser.

The inspector remains left, deck center, browser right. Full local card art uses140×204 inside212-unit inspector art region. Rich filters, ownership/sort/alternate-format controls, Deck/Side routing, all file surfaces, unique temporary-file teardown and production popup correction remain intact.

### Verification and TDD evidence

All commands used GRADLE_USER_HOME=C:/Users/haxer/.gradle and the same focused command:

`./gradlew runClient -PldTest=gallery_collection -PldTestWindow=1280x720 -PskipNative`

The initial sandbox run could not open the shared Gradle wrapper lock; the normal authorized escalation ran it successfully. No sandbox rejection or unresolved permission block.

RED before layout edits: correction-collection-red retained3025 checks,8 expected failures,1 failed scenario,499 steps,12 captures. Five action-row alignment checks failed, Active was disconnected, Saved feedback was outside Main, and the auxiliary navigation row remained. These are actual runtime failures against the original composition. Later checks strengthened bounded long-field geometry, field/action content centers, emphasis, status text fit/nonoverlap and relocated active-action access as real rendering exposed issues.

Iteration evidence (each archived before the next launch):

- v1:2886 checks,0 assertion failures, resize wait ERROR; native normal capture exposed crowded Main status, primary color and title initialization issues.
- v2:3576 checks,3 expected status-width failures; resize passed. The inherited flex shorthand had collapsed the status basis.
- v3:3341 checks,15 repeated Main text-fit failures and resize ERROR. Native diagnostics showed framebuffer still1280×720 with layout clean, rather than a dirty-layout loop. A brief preceding compile-only attempt used int instead of long for fixture race constants; fixed before runtime.
- v4:3623/3623,661 steps,14 captures,1/1 scenario,Gradle exit0. Narrowing flex to the three count IDs preserved an18-unit Main caption. Minimal resize handling restores only a maximized fixture window and polls native events; the actual framebuffer/layout-settle assertions remain.
- Final frozen v5:3637/3637,679 steps,15 captures,1/1 scenario,78621ms,Gradle exit0 / BUILD SUCCESSFUL. Adds active My decks/Clear active, routed Help/Close and Main caption vertical-center coverage. No runtime assertion failures.

Real routed checks retain edits by right-click and Add/Remove, independent inspector/Main/browser scroll and section endpoints, full60/15/15 counts, filter popup choice, imported basename selection through the real dropdown, export filename limits,128-unit editable names, Save pending/error retaining the dirty draft, and same-screen resize preserving draft/selection/meaningful scroll. Task1 control/scroller audits cover all seven header actions, Clear active, Main/inspector/browser/saved-list scrollers. All original static IDs are checked against production XML.

Actual final viewport: window/framebuffer1280×720,GUI640×360,requested/effective scale2; management1280×720 canvas/root0.5. Live resize: native/framebuffer1100×800,GUI550×400,effective2; canvas GUI bounds(0,45.3,550,309.4). Both final v4/v5 resize runs passed native1100×800/framebuffer1100×800/layoutDirty=false; final pre-resize maximized=0. Previous timeout evidence remains retained. Normal shader/asset/command/bootstrap warnings remain in launch.log; these are not claims of pristine Minecraft output. Full matrix and full JUnit intentionally belong to Task3.

### Exact source and evidence

Base c2a1893 (c2a1893 full source retained by Git) plus final frozen fixture files. Before the final launch, copies and SHA256 hashes were saved at build/gallery-proof/correction-collection-v5-source; the archive includes the identical directory at correction-collection-v5/source. Java SHA256 a91c5c1a827f3ab9dc8c68087c8e904366ff8acfa329ba7275b625eff3c0ce32; XML ea37a8e35060f350a29995f0f5a78064177a5ac7457ee452ce3d905304d6048c. The manifest also identifies unchanged gallery.lss, GalleryVisualAssertions and all four PNGs. No source edits occurred during the final launch or after its freeze. Documentation-only edits followed verification.

Runtime LDLib2 2.2.39.a, SHA256563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46 (same pinned dependency as approved Task1). Raw final report/screenshots/log: build/gallery-proof/correction-collection-v5. Navigable original-report gallery: build/ui-gallery/correction-collection-v5/index.html. No diagnostic/rejected run is an accepted golden.

### Actual visual inspection and deviations

Read docs/mockups/ui-redesign/gallery.html and opened the exact approved build/gallery-proof/correction-reference/collection-full.png and collection-surface.png. The surface excludes the HTML-only66-pixel global navigation; that fake navigation was not copied into the fixture. Also opened the native reference/runtime comparison pair.

Opened this compact final list under build/gallery-proof/correction-collection-v5/review (capture-map.txt maps copies/crops to the original PNGs):

- normal-full.png; toolbar-normal.png; toolbar-long.png; main-heading.png
- browser-scrollbar.png; active-decks.png
- stress-standard.png; stress-large.png; scrolled.png
- save-pending.png; save-failed.png; live-resize.png
- filters.png; import-dropdown.png; reference-runtime-pair.png

Full screenshots and native crops are unretouched; the pair labels two native-resolution originals side by side (the app may scale its preview). C1 self-review: centered, unclipped ordinary header and compact action labels, readable cream primary/dark text, Clear active centered. C2: distinguishable native up/down arrows and thin track/thumb; no elongated blank action rectangles, including browser/filter/saved-list surfaces. C3: one clear editable-title/action row in normal and128-character title states, adjacent Active, Save secondary and Use for duels primary. C4: Saved/dirty feedback now visibly belongs beside Main, with no caption/control overlap; Help/Close remain visible and routed. Card-space/browser preservation:40 Main cards occupy four complete Standard rows in normal composition; the stress fixture retains visible complete rows and scrolls60/15/15, both densities, four browser columns and rich filters.

Departures from HTML are explicit: Minecraft raster font, editable framed title, full production count/status wording, retained ownership/alternate-format/transfer/footer controls, wider260-unit inspector and retained348-unit browser/four74×108 artwork columns. Normal catalog repeats four local artwork choices across80 synthetic codes, rather than pretending to be a real card database. Normal Extra5/Side0 differs from HTML Extra0/Side2; full15/15 stress is separate. Full-card inspector aspect is now preserved, but production metadata/transfer controls leave the effect below the initial detail viewport; independent detail scrolling remains required and tested, especially for long names. Main18 and status/Sort/density12 are local measured fixture exceptions to the guide's candidate20/14/16 typography. The comparison pair makes these differences reviewable. No pixel-perfect browser-font or final human hierarchy/sharpness approval is claimed.

### Scope and self-review

Changed only CollectionGalleryScenario.java, gallery_collection.xml, four fixture-only PNGs, correction reports and progress checkboxes. Production resources, controllers, routing/policy/networking, translations, JNI and dependencies are unchanged. Reused existing texture/controller seams and Task1 diagnostics; no abstraction or broad native-button selector was added. The count-only selector and explicit primary text color fix issues found by actual image review rather than weakening assertions. git diff --check passes. Temporary import teardown still deletes only its own allocated file.

Runtime ownership is released after this task; no Gradle/client process remains owned by the implementer. Independent review is the next step; human approval and Task3 matrix/full-suite remain open. No push/merge.

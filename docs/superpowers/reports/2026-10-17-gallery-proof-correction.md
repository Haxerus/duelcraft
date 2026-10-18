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


# Task 3 — Lobby composition correction (focused implementation)

Status: focused implementation and self-review complete. Independent C1/C2/C5/C6/C7 review is next; human approval, final matrix, unchanged production scenarios and full JUnit remain deferred as instructed. Runtime ownership is released. Task 2 independent review is approved and its checklist is marked; the known fractional-scale one-pixel collection border loss remains for the final matrix.

## Changes and scope

The normal fixture uses deterministic Alex, Rowan and Mira cards in a centered horizontal row. Each real selectable Button has a 76-unit circular initial, separate 20-unit name and 14-unit availability text; the selected card has a gold edge and explicit checkmark beside Available. The controller owns IDs, binding and selected target, and recreates the same card structure after settings and selection updates. Invite is centered beneath the row. Change deck and Settings are grouped at the upper right; Home, Help and Close remain accessible at the left. The bottom strip has local Blue-Eyes artwork, the real active Dragon study summary (40/0/2), and real current rule/options.

Added only the permitted package-private BiFunction<Target,Boolean,Button> fixture overload and renderer field in DuelLobbyScreen. All existing factories delegate to targetRow, whose text is exactly the original target name plus optional two spaces/checkmark. IDs/classes/binding remain in the original update loop. Production resources, navigation, input semantics, networking, policy and controllers are unchanged; no resource-name gating or screenshot-time replacement.

Zero players hides an unowned fixture wrapper, so controller setDisplay cannot expose empty native scroller chrome. One player retains its centered avatar/card identity. Eighteen players wrap three cards per row in the same bounded central scroller; routed wheel input reaches the last row and a real click selects Player 18. Invite/deck/rule controls remain visible. The renderer never falls back to text rows.

Settings is a 382-unit full-height right sheet with aligned label/control pairs and paired hand/draw fields. The Gallery-only rule popup uses the same pinned-runtime alignment correction as CollectionController: replace the default dialog LAYOUT_CHANGED clamp listener, position in root layout coordinates, and avoid mutations when already aligned. The correction lives entirely in LobbyGalleryScenario. No production focus-restoration fix: baseline and Gallery still record the initiator restoration requirement as UNMET, while asserting hidden field focus clears.

Native disabled styling was a shared-owner issue exposed by final image review: gallery.lss styled only manual gallery-disabled, and the specimen masked native setActive(false) with that manual class. Per controller authorization, the shared role now aliases gallery-action.__disabled__, the specimen removes that masking class, and GalleryVisualAssertions.disabled verifies real native state, subdued fill/readable text and centering on both canvases and real pending Invite. Explicit pending roles retain amber precedence. The two existing primary text ID overrides (lobby Invite and collection Use for duels) each gain one native-disabled text override. This is the only additional Task 2 XML change; its composition/controller is unchanged.

Native Selector intentionally hides popup arrows: source C:/Users/haxer/Documents/Programming/Modding/LDLib2/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/elements/Selector.java:203–206. Popup checks preserve those hidden native arrows, thin track/thumb, native thumb texture and exclusion from gallery-action. The visible roster retains the generic compact-arrow/native-texture audit. No broad button selector was added.

## TDD and command results

All runs used GRADLE_USER_HOME=C:/Users/haxer/.gradle. Initial sandbox wrapper lock access failed; normal requested escalation succeeded, with no approval rejection or unresolved permission block.

Initial RED, before composition implementation:
`./gradlew.bat runClient -PldTest=gallery_lobby -PldTestWindow=1280x720 -PskipNative`

Retained correction-lobby-red: 186/203 checks, 17 expected composition failures, 128 steps, 0/1 scenarios, Gradle exit 1. Failures demonstrated full-width vertical text rows, missing avatar/name/availability components, off-center Invite and absent artwork. This is runtime red evidence, not a static expectation.

Each run was archived before the next launch overwrote build/ldlib2-uitest:

- v1: 225/229, four failures. Native review exposed Button default row direction causing child overflow and incorrect hits, unsupported image syntax, popup offset/dirty loop. Fixed explicit card column, sprite texture and fixture popup alignment.
- v2: 225/227, two failures, rejected 1×1 framebuffer/flat images. Added fixture-only iconified/1×1 window recovery using requested dimensions and an explicit reviewable-framebuffer check. No claim based on these captures.
- v3: 266/268, two long-roster endpoint/click failures. Native 1280×720, popup correctly aligned, zero dirty warnings. Native review also caught collapsed title width and insufficient viewport-background specificity.
- v4: 284/285, one overly generic popup-arrow assertion. Confirmed native Selector hides its arrows; replaced with popup-specific native contract, preserved the visible roster audit. Image review caught zero-height scroller chrome; hiding its fixture wrapper fixes it.
- v5: 283/283, 161 steps, 13 captures, 1/1, 18936 ms, exit 0. All functional lobby checks passed. Image review exposed the real disabled action still looking enabled; retained as pre-native-disabled evidence.

Final frozen GREEN:
`./gradlew.bat runClient '-PldTest=gallery_specimen,gallery_lobby' -PldTestWindow=1280x720 -PskipNative`

v6: 599/599 checks, zero failures/errors, 357 steps, 103 captures, 2/2 scenarios, 34364 ms, Gradle exit 0 / BUILD SUCCESSFUL. Lobby 16172 ms; specimen 15884 ms. Includes native disabled regression on both canvases, real pending Invite, selected target/RUSH rule dispatch, invalid LP gating, real first/middle/scrolled-last rule choices, eighteen Tab traversals inside Settings, Escape popup-before-sheet, hidden field input clearing, zero/one/long roster and preserved card identity after controller updates. Existing specimen activation, pending duplicate gating, failure, modal, selector, hover/press/focus and scroller checks passed. No dirty-layout or flat-capture warnings in v6. Normal Minecraft/NeoForge/bootstrap/shader warnings remain; output is not claimed pristine.

Actual v6 native window/framebuffer 1280×720, GUI 640×360, requested/effective scale 2. Management design 1280×720/root scale 0.5; specimen duel design 960×540/root scale 0.6666667. Recovery diagnostic iconified=0/framebuffer=1280×720 in v6. Full matrix and full JUnit have not been run during this implementation stage.

## Exact source and evidence

Base 7a732b772f2596a3fba706d53eb69cbf711a7ea0 plus frozen v6 source. All eight changed source/resource files, unchanged local artwork, exact SHA256 hashes and base identity were copied before the final launch to build/gallery-proof/correction-lobby-v6-source; the raw archive also contains source/. No source edits followed that freeze; only documentation/proof packaging followed.

DuelLobbyScreen SHA256 9DEC9D2333B4BF1E18F3D8131D6A702F5B06C98B7A1F661203193002E33D1550; LobbyGalleryScenario 2BF04627B571534F7466C6E417A87FC61B9995BB25CFCF06EB24F1BBA7F24117; gallery_lobby.xml 3606CCD113E18DCDB5C0167247BAABC66FAA7B2F574E838D1FF386965BAB16DA. Remaining exact hashes are in source/sha256.txt, including shared LSS, assertions, specimen Java/XML, the single collection XML override and reused artwork.

LDLib2 runtime 2.2.39.a; runtime jar SHA256 563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46. Report records the exact Gradle-cache codeSource. Raw final report/original screenshots/log: build/gallery-proof/correction-lobby-v6. Navigable original-report gallery: build/ui-gallery/correction-lobby-v6/index.html. Earlier red/rejected runs remain separate; none is an accepted golden.

## Actual visual self-review

Read docs/mockups/ui-redesign/gallery.html and the Gallery guide. Opened exact approved correction-reference/lobby-full.png, lobby-surface.png, lobby-actions.png and lobby-settings.png before implementation. Opened diagnostic v1/v3/v4 captures and all final compact images below under build/gallery-proof/correction-lobby-v6/review:

- normal-full.png; selected-full.png; empty-full.png; one-full.png; long-roster-full.png
- settings-full.png; rule-popup-full.png; invalid-settings.png; pending-full.png
- actions.png; deck-strip.png; roster-scrollbar.png
- native-disabled-management.png; native-disabled-duel.png
- specimen-management-full.png; specimen-duel-full.png; reference-runtime-pair.png

capture-map.txt maps every copy/crop to its original and native rectangle. The comparison pair places unscaled original reference/runtime images side by side below a 32-pixel annotation band; previews may scale it. No screenshot mutation/repainting, resizing or online artwork dependency.

C1 self-review: ordinary action labels centered and unclipped; disabled real actions are visibly subdued/readable, both native-disabled crops centered, explicit pending Save still amber. C2: roster arrows distinguishable and compact with thin track/thumb; popup retains native hidden arrows/thin thumb, no action rectangles applied internally. C5: normal horizontal avatars/name/Available, centered Invite, upper-right deck/settings, lower real deck/art/options strip. C6: empty region has no nested panel/chrome, one centered avatar, long roster retains three-card rows and selected last identity; pending visibly disables the real action. C7 exact reference/capture/deviation record supplied. Independent visual verdict is still required.

Departures from HTML are explicit: Minecraft font; retained production captions Invite a player / Invite / MR5 and complete LP/hand/draw wording; counts share the production multiline deck-summary text at size 20; no fabricated browser global navigation; Home/Help/Close remain in the local header. Footer is anchored to the 720-unit canvas. The Settings sheet uses the existing single top-close action, with no duplicate Done; seed placeholder remains native Empty. No pixel-perfect browser font, human aesthetic approval, server-derived availability or real remote roster is claimed; the names/availability/content are deterministic fixture data. Existing collection missing-art stress remains separate.

## Self-review and handoff

Changed only the permitted renderer seam, Gallery fixtures/styles/assertions, guide and correction progress/report docs. Read the diff; native defaults and all production XML/LSS, behavior, network, policy, translations, JNI and dependencies remain unchanged. Every added style targets an explicit fixture component or semantic action role. Existing reports are preserved. git diff --check passes. Review the additional shared native-disabled changes with C1 and the one-line collection text override before the final matrix. Retain the historical focus-restoration gap and collection fractional-scale one-pixel border observation. No push/merge. Runtime ownership is released; independent visual review is the next step.
## Final correction verification (2026-10-17)

Functional verification passed on frozen runtime source `0ce1a6acf99584adcebfb73cd7e9b5b998df1af2`. Visual proof is **not complete**: the 1024×768 case has missing control/card outlines and a prominent Minecraft title-screen backdrop outside the fixed 16:9 canvas. Whole-change review must assess that finding before acceptance. Human approval and production migration remain pending. No runtime source changed during this verification.

Independent focused reviews are retained in `.superpowers/sdd/2026-10-17-gallery-proof-correction/task-{1,2,3}-review.md`: Task 1 approved C1/C2/C7, Task 2 approved C1–C4/C7, and Task 3 approved C1/C2/C5/C6/C7 at 1280×720/effective 2. Task 3 includes the shared native-disabled owner fix and collection primary-text override. The small Change deck horizontal padding difference is nonblocking; no source churn was made for it. These focused approvals do not approve the later 1024 finding.

### Frozen matrix and actual viewports

Each case ran `./gradlew.bat runClient '-PldTest=group:duelcraft_gallery' -PldTestWindow=<window> -PldTestGuiScale=<request> -PskipNative`, with `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Each original report/screenshots/log was archived before the next launch under `build/gallery-proof/correction-final-gallery-<window>-s<request>`; matching browsable galleries are under `build/ui-gallery/`. All seven cases passed **4/4 scenarios, 4248/4248 checks, 1037 steps, 118 captures** each: **28 scenario executions, 29,736 checks, 7,259 steps, 826 captures**, no failed/error/skipped checks or retries.

| Requested framebuffer | Requested scale | Live effective scale | Live GUI | Scenario duration |
|---|---:|---:|---|---:|
| 1280×720 | 2 | 2 | 640×360 | 112100 ms |
| 1280×720 | 3 | 3 | 427×240 | 101072 ms |
| 1280×720 | 4 | 3 | 427×240 | 104908 ms |
| 1920×1080 | 2 | 2 | 960×540 | 114600 ms |
| 1920×1080 | 3 | 3 | 640×360 | 106026 ms |
| 1920×1080 | 4 | 4 | 480×270 | 107134 ms |
| 1024×768 | 2 | 2 | 512×384 | 100575 ms |

`build/gallery-proof/correction-final-verified-summary.json` and each `verified-metadata.json` derive actual per-scenario viewport attachments, including framebuffer, GUI and effective scale. All non-resize steps match the driver request; no group resize leakage. The collection's intentional 1100×800 live resize temporarily clamps requested 4 to effective 3 and restores 4 afterward at 1920×1080. Requested 1280 scale 4 duplicates effective 3, not new effective-scale coverage.

The specimen's attachment prefix has a stale literal `requestedWindow=1280x720 requestedScale=2`. It is not request evidence. Driver metadata plus live `configuredScale`, window/framebuffer/GUI/effective fields establish the table above. Original reports remain unmodified; original driver convenience `viewports` arrays were empty, and the separate verified summaries supply the extracted evidence. This metadata issue remains a minor whole-change review item.

Frozen manifest `build/gallery-proof/correction-final-source/manifest.json` covers 265 main Java/resource/build-property files, including native resources; SHA256 `2DBE9699A8C6FCC947D9A20B3A95C6AFE6014912E748853FB308E4C6B0D64B5A`. All hashes matched before each launch and after final tests. LDLib2 `2.2.39.a` runtime jar SHA256 `563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46` was retained/rechecked. Runtime source and dependencies stayed frozen. Build-only driver/metadata scripts are retained beside the evidence.

### Native visual inspection and unresolved acceptance

Opened the approved `correction-reference/collection-{full,surface}.png` and `lobby-{full,settings}.png` again. In **each of seven** matrix archives opened these exact native images (37 matrix images total):

- `screenshots/gallery_collection/28_gallery-collection-normal.png`
- `screenshots/gallery_collection/676_gallery-collection-live-resize.png`
- `screenshots/gallery_lobby/41_gallery-lobby-selected.png`
- `screenshots/gallery_lobby/74_gallery-lobby-rule-popup.png`
- `screenshots/gallery_specimen/135_gallery-duel.png`

Additionally opened the 1024 archive's `screenshots/gallery_lobby/140_gallery-lobby-empty.png` and `161_gallery-lobby-long-roster.png`. Each archive's `visual-inspection.json` lists the exact inspected files and findings. Higher-size cases preserve composition, readable hierarchy, controls and aligned native popup. Collection live resize retains fractional-scale outline loss already noted in Task 2. At 1024×768, several header buttons reduce to partial underline/side fragments, **the selected Alex card loses its whole right edge**, and the Minecraft title/logo is prominent outside the canvas. Text and routed state remain functional, but passing geometry is not visual approval. Do not bless this capture as an accepted golden or describe this solely as tiny one-pixel loss.

Exact unresolved native capture:

![1024×768 selected lobby: missing button and selected-card outlines, with prominent title backdrop](../../../build/gallery-proof/correction-final-gallery-1024x768-s2/screenshots/gallery_lobby/41_gallery-lobby-selected.png)

[Open original 1024 capture](../../../build/gallery-proof/correction-final-gallery-1024x768-s2/screenshots/gallery_lobby/41_gallery-lobby-selected.png). Parent independently opened this exact image and requested whole-change assessment; no corrective runtime edit is authorized during this frozen run.

### Unchanged production scenarios and full JUnit

`./gradlew.bat runClient '-PldTest=collection_layout,collection_overflow,duel_lobby' -PldTestWindow=1280x720 -PldTestGuiScale=2 -PskipNative` exited 0: **3/3 scenarios, 235/235 checks, 423 steps, 13 captures, 38492 ms**. Per scenario: collection_layout 89 checks, collection_overflow 88, duel_lobby 58. Archive: `build/gallery-proof/correction-final-production-regressions`; browsable HTML: `build/ui-gallery/correction-final-production-regressions/index.html`. Opened native `collection_layout/105_collection-standard.png`, `collection_overflow/78_collection-large-expanded.png`, `duel_lobby/45_m5-lobby-idle.png`, and `duel_lobby/17_m5-rule-dropdown.png` under that archive's screenshots directory. Original gray production surfaces and text-only player rows remain intact.

`./gradlew.bat test` ran **once**, actually executed the test task, exited 0 (`BUILD SUCCESSFUL in 2m 5s`): **72 suites, 1113 tests, 1112 passed, zero failures/errors, one skip**. The existing `DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile()` skip reports Windows symlink creation unavailable because the client lacks the required privilege. Fresh XML/HTML retained as `correction-final-junit-results` / `correction-final-junit-html`; log and exact summary: `correction-final-junit.log` / `correction-final-junit-summary.json`, all under `build/gallery-proof`.

No dirty-layout or flat-image warnings in the seven matrix launches or unchanged scenarios. Generic Minecraft/NeoForge resource, command, mipmap, sound/shader and Gradle warnings remain in original logs; this is not a warning-free claim. Preflight typo-property/`--color` DEBUG messages are deliberate negative checks. Existing production focus restoration remains unmet and unchanged. Production XML/LSS, navigation/network/policy remain unchanged; the narrow renderer seam defaults to the original text row. `src/test` is unchanged from frozen source; `git diff --check c5cf505..HEAD` passed. Archive tooling was unchanged, so no extra tool test run was needed.

Runtime/Gradle ownership is released after this documentation handoff. Whole-change review is next; overall visual completion stays open for the 1024 finding. No merge or push.
## Final review fix wave (2026-10-17; supersedes the unresolved findings above)

The four final-review findings were corrected together using fixture-only changes. Straight Gallery borders now use 1.25 design units, the minimum width that reaches one framebuffer pixel at the tested 0.8 scale. Two-unit focus edges, semantic colors, disabled state, centered text and native scrollbar arrows remain unchanged. The Change deck fixture button is 144 units wide rather than 120, with its font unchanged.

GalleryBackdrop draws the neutral Canvas color across the actual GUI viewport before each exact registered Gallery screen renders. It uses NeoForge ScreenEvent.Render.Pre, scoped by screen identity; scenario teardown unregisters the same Consumer and clears its screen set. Both specimen canvases, both collection fixtures and all Gallery lobby roster states opt in. Baseline screens do not opt in. Production ManagementScreen, controllers, layout resources, canvas sizing and navigation are untouched. Local NeoForge ClientHooks source confirms Pre precedes screen.renderWithTooltip; local bus 8.0.5 bytecode confirms unregister removes listener entries keyed by the Consumer. No LDLib source or dependency change.

Specimen requestedWindow/requestedScale now read the actual harness system properties. Historical original reports, including the seven-case pre-fix matrix and its stale literal metadata, remain untouched. Those seven cases are pre-fix evidence, not final-source coverage.

Source was frozen before launch at c98119656e5c0a09b2b04a24b06b2182dc2a876d plus the retained runtime diff and GalleryBackdrop.java. The 266-file Java/resource/build-property manifest is build/gallery-proof/correction-fix-source/manifest.json, SHA256 93A0E9C29CC179B7E72DD1197EA01BEE46AE9A74EBCD40B1D206B8D7F1996DFB. LDLib2 remains 2.2.39.a, runtime SHA256 563F4D2A60C5ACF63992BAB9917045A35B740BB543C134BFDBFAA8DE64DF6F46. Rechecked hashes before each launch. Runtime source has not changed after freeze; only proof documentation and build-only diagnostics followed.

The initial sandbox attempt failed at the Gradle wrapper cache lock before compilation. Its archive correction-fix-gallery-1024x768-s2 is explicitly REJECTED: its copied report was stale production evidence. The escalated retry uses correction-fix-v1-gallery-* archives. No auto-review rejection occurred.

Each final-source command used GRADLE_USER_HOME=C:/Users/haxer/.gradle and `./gradlew.bat runClient '-PldTest=group:duelcraft_gallery' -PldTestWindow=<window> -PldTestGuiScale=<scale> -PskipNative`. All three fresh runs exited 0, each with 4/4 scenarios, 4248/4248 checks, 1043 steps and 118 captures: total 12 scenario executions, 12,744 checks, 3,129 steps and 354 captures. Six additional per-run install/teardown steps account for the step-count change; no repeated assertion inflation. No dirty-layout or flat-capture warnings. Generic Minecraft/NeoForge/Gradle warnings remain in the logs.

| Final archive suffix | Requested/effective scale | Actual GUI | Duration |
|---|---|---|---|
| 1024x768-s2 | 2 / 2 | 512x384 | 100791 ms |
| 1280x720-s2 | 2 / 2 | 640x360 | 103554 ms |
| 1920x1080-s4 | 4 / 4 | 480x270 | 109791 ms |

Archive prefix is `build/gallery-proof/correction-fix-v1-gallery-`; each has original report, screenshots, launch log and verified-metadata.json. Matching browsable reports use `build/ui-gallery/correction-fix-v1-gallery-<suffix>/index.html`. Actual framebuffer matches the requested suffix in every non-resize viewport attachment. Built-in 1100x800 live resize was covered in every run: effective 2/GUI 550x400 for the first two cases; effective 3/GUI 367x267 for requested scale 4, restored to effective 4 afterward. `build/gallery-proof/correction-fix-verified-summary.json` retains exact per-scenario fields and the corrected specimen request strings. No viewport leakage.

Unchanged production regression command: `./gradlew.bat runClient '-PldTest=collection_layout,collection_overflow,duel_lobby' -PldTestWindow=1280x720 -PldTestGuiScale=2 -PskipNative`. Exit 0: 3/3 scenarios, 235/235 checks, 423 steps, 13 captures, 39376 ms. Archive: build/gallery-proof/correction-fix-production-regressions; matching gallery under build/ui-gallery. Opened exact native screenshots collection_layout/105_collection-standard.png, collection_overflow/78_collection-large-expanded.png, duel_lobby/45_m5-lobby-idle.png and duel_lobby/17_m5-rule-dropdown.png. The original gray layouts and text-only rows remain intact; no dirty-layout/flat-capture warnings.

Full `./gradlew.bat test` ran once after final source, actually executing :test, exit 0 / BUILD SUCCESSFUL in 1m 4s: 72 suites, 1113 tests, 1112 passed, zero failures/errors and one existing skip. DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile() reports unavailable Windows symlink privilege. Fresh XML/HTML are retained under correction-fix-junit-results / correction-fix-junit-html, with correction-fix-junit.log and correction-fix-junit-summary.json. All are under build/gallery-proof. All 266 runtime/source hashes matched again after JUnit; archive tooling and tests are unchanged, so no archive-tool test rerun was required. Production source/resources/dependencies remain unchanged in this fix wave. Diff whitespace check passes.

Visual self-review opened correction-reference/collection-surface.png and lobby-surface.png; original correction-final-gallery-1024x768-s2/screenshots/gallery_lobby/41_gallery-lobby-selected.png; original correction-final-gallery-1280x720-s2/screenshots/gallery_collection/28_gallery-collection-normal.png and 676_gallery-collection-live-resize.png. These are comparison references, not accepted goldens.

In **each** of the three final archives, opened these five exact native screenshots and four unscaled review crops:

- screenshots/gallery_collection/29_gallery-collection-normal.png
- screenshots/gallery_collection/677_gallery-collection-live-resize.png
- screenshots/gallery_lobby/42_gallery-lobby-selected.png
- screenshots/gallery_lobby/75_gallery-lobby-rule-popup.png
- screenshots/gallery_specimen/136_gallery-duel.png
- review/lobby-actions.png
- review/selected-alex.png
- review/collection-actions.png
- review/resize-actions.png

Additionally opened the 1024 archive's screenshots/gallery_lobby/141_gallery-lobby-empty.png and 162_gallery-lobby-long-roster.png, plus the two native `88_management-dense-hover-gallery-confirm__...` and `185_duel-dense-hover-gallery-confirm__...` crops under screenshots/gallery_specimen. Each archive's visual-inspection.json supplies the full exact filenames (31 final images total). Compared normal, fractional and large images with the reference and retained failure images. The whole selected Alex boundary and ordinary header sides are complete; dense button tops are present; normal outlines remain thin. Change deck text has clear side padding without font reduction. Neutral margins remove the uncontrolled logo/panorama/version text, including live resize and empty/long rosters. Selected/focus/disabled/pending colors, centered labels, compact native arrows, collection column hierarchy and Settings popup anchoring remain intact. These are self-review findings awaiting independent rereview.

Build-only correction-fix-pixels.py samples the deterministic selected Alex perimeter and all letterbox rows outside its unchanged design canvas. The retained red original has 0/130 matching right-edge samples; final 1024 has 130/130, with top/bottom 133/133 and left 130/130. Its 96-pixel outer margins are uniformly Canvas-colored. The diagnostic allows a two-pixel coordinate search around the expected boundary, excludes corners, and is a focused defect probe, not a screenshot golden or substitute for image review. Originals are never rewritten. Native crops are unscaled, with exact source/rectangle mappings under each final archive's review/capture-map.json.

Final independent rereview remains pending. Human aesthetic approval, production migration and the historical focus-restoration limitation remain open. No push or merge.

## Independent final rereview closure

The original whole-change reviewer independently reviewed `c981196..c7e5f0a` and approved all four fixes. It opened the approved lobby reference, retained 1024 failure image and 15 corrected native fullscreens/crops across 1024, 1280 and 1920 cases. Complete borders, quiet letterboxing, metadata accuracy and Change deck padding were confirmed; exact-instance backdrop registration and cleanup were checked against the harness. No introduced breakage was found, and no suites were rerun for review. The exact reviewed image list and earlier per-task reports are retained in `build/gallery-proof/correction-reviews/final-rereview.md`.

The earlier pending and rejected checkpoints above are historical. Current fixture implementation and independent visual review are complete; human aesthetic approval, production migration and the existing production focus-restoration limitation remain separate. Production UI behavior is unchanged. Branch and worktree are retained without merge or push.

# LDLib2 feasibility and tooling audit

Date: 2026-10-17. Read-only source audit for the UI redesign; only this report was written. No game launch, build, dependency change or product implementation occurred.

## Recommendation

Make the three distinct mockups and critique them before building tooling or product UI. Treat HTML as a composition and interaction sketch. Prove the chosen direction with one real collection-editor fixture in LDLib2 before spreading it across Home, lobby and duel. Reuse the existing fixtures, screenshots, mouse-input helpers and text-fit assertions; they already provide most of a preview environment.

Preserve every existing action, server-authoritative route, Save acknowledgement, explicit activation, ownership policy, private collection/preparation state and hidden-card rule. Presentation work must not convert fixture state into production truth. Deferred installed-JAR multiplayer acceptance remains a separate, nonblocking redesign item; this audit does not close it.

## Evidence and limits

- Duelcraft worktree: `codex/ui-redesign`, inspected HEAD `ef0599d8b116830ec29d2a4a03391efb3d033dff`.
- Pinned dependency: `build.gradle:266`, LDLib2 `2.2.39.a`.
- Sibling LDLib2 checkout: `C:/Users/haxer/Documents/Programming/Modding/LDLib2`, inspected HEAD `d30e86b56b2a31982063ef907a335d36371b0bc4`; its `gradle.properties:9` also declares `2.2.39.a`. Version agreement is established; binary/source identity was not verified.
- Read repository `AGENTS.md`, `docs/ldlib2-ui-guide.md` and `docs/ui-wiring-guide.md`. The latter two retain early implementation sketches and TODOs superseded by current code. In particular, do not restore their old prompt or close behavior. The hand-layout warning in `docs/ldlib2-ui-guide.md` and current source remain useful.
- Inspected the historical October 16 collection screenshot at `build/interaction-release/m6-local-2026-10-16/full-1280x720-scale2-retry1/ui/screenshots/collection_layout/105_collection-standard.png`. It shows the current three-column structure, small captions and broad use of equal-weight beveled surfaces. That visual observation supports testing clearer hierarchy; it does not establish a new runtime failure. Also inspected a September screenshot, which has an older Saved lists interface and must not be used as the current baseline.
- No fresh test pass is claimed. Existing acceptance remains as recorded in `docs/superpowers/handoffs/2026-09-17-deck-editor-implementation.md:5–13`; the corrected lobby dropdown is already human-accepted.

Source references below use repository-relative paths. `LDLib2/...` denotes the sibling checkout, not a Duelcraft directory. Line numbers refer to the inspected revisions.

## Actual LSS surface versus browser CSS

| Area | Verified behavior | Consequence for mockups |
| --- | --- | --- |
| Layout | `LDLib2/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/style/LayoutStyle.java:32–85` registers flex and grid properties. The collection already uses `display: grid`, fixed tracks and `repeat(...)` in `src/main/resources/assets/duelcraft/ui/collection_screen.xml:96–99`. | Grid is available. Do not incorrectly constrain the design to flexbox, but prototype the exact track expression used. |
| Units | `LDLib2/.../gui/ui/style/values/LPAValue.java:34–72` parses numbers, `px`, `%`, auto and intrinsic keywords. | Use design units, explicit percentages and supported intrinsic sizing. That parser does not accept `rem`, `vh`, `vw` or `calc(...)`; a serialization case mentioning CALC is not parsing support. |
| Stylesheets | `LDLib2/.../gui/ui/style/Stylesheet.java:19–20,142–190` uses rule/declaration regexes and registered property parsers. Unknown properties are debug-logged; invalid parse exceptions are warnings. | Browser CSS can appear to load while declarations have no effect. Do not rely on CSS custom properties, `var(...)`, nested rules or `@media` as an established feature; no support was found in this path. |
| Selectors | `LDLib2/.../gui/ui/style/HierarchicalStyleMatcher.java:12–34` supports descendant/child matching and maps pseudo-state syntax to internal classes. | Classes, IDs and child selectors are useful. A browser pseudo-class name alone does not establish that LDLib2 supplies the corresponding runtime state. |
| Cascade | `LDLib2/.../gui/ui/style/StyleOrigin.java:8–47` and `StyleSlot.java:23–30` prioritize component defaults, stylesheet, inline code, animation, then IMPORTANT. | Increasing selector specificity cannot defeat an inline or IMPORTANT dimension. Inspect Java setters before adding another CSS-like override. |
| Paint | `LDLib2/.../gui/ui/style/PropertyRegistry.java:147–159,234–245` registers textures, overlays, transforms, opacity, font, text color and alignment. Current XML uses `built-in(...)`, `rect(...)`, `sprite(...)`. | Translate color, surface and art choices into supported textures and text properties. Browser `background-color`, `font-family`, `box-shadow` and `border-radius` are not drop-in equivalents. |
| Text | `LDLib2/.../gui/ui/elements/TextElement.java:203–222,275–338` measures and draws Minecraft text with explicit font size, wrap and line spacing. | Browser text widths, line boxes, kerning and baselines cannot approve Minecraft text fit. |

## Evidence-backed sources of friction

### Scaling and sizing have several owners

Collection uses a fixed 1280×720 design root, scaled by the smaller screen/design ratio in `src/main/java/com/haxerus/duelcraft/client/collection/CollectionScreen.java:31–32,124–135`. Home/lobby use the same dimensions in `client/interaction/ManagementScreen.java:25–26,50–53`. Duel uses a separate 960×540 canvas in `client/DuelScreen.java:11–19`. A 12-unit management caption and 12-unit duel caption therefore do not imply the same physical size in the same window.

The root scale is based on GUI dimensions; framebuffer, effective GUI scale and letterboxing still matter. The existing `CollectionLayoutScenario.recordViewport` records requested window/scale, actual window, framebuffer, GUI dimensions and effective scale. Keep that metadata with screenshots instead of naming a capture only “scale 3”.

Several fixed-height regions share the collection height: header/navigation, footer, Extra and expanded Side, with Main taking the remainder (`collection_screen.xml:24–43,84–99,137–153`; `CollectionController.java:830–836`). More permanent toolbars or larger text consume card space even when nothing technically overlaps. This is a capacity constraint, not evidence of a broken layout engine.

`Label` starts with a height of 9 (`LDLib2/.../gui/ui/elements/Label.java:29–33`). Changing font size alone need not increase that height. Lobby now explicitly gives selector labels 28 units (`duel_lobby.xml:32`), with fit/click checks in `client/uitest/DuelLobbyScenario.java:38–74`. Treat this as a known failure mechanism with a current correction, not an unresolved dropdown bug.

Adaptive text size is another owner: `TextElement.java:217,222` writes width/height through `Style.importantPipeline`. Thus `adaptive-width: true` can defeat an intended constrained text box. Collection already sets `adaptive-width: false; width: 100%` for its density button (`collection_screen.xml:139`). Wrapping, bounded width and adaptive height must be chosen together.

### Layers affect drawing, clipping, hit testing and focus separately

LDLib2 sorts children by z-index and structural order (`UIElement.java:1261–1289`), traverses hit tests and accumulated z-index (`1334–1364`), translates drawing depth (`1839–1842`) and clips children for hidden overflow (`1951` onward). Raising a deeply nested element is not a universal escape from its ancestor's drawing/clipping structure. The duel guide already documents placing the action dock beside overlays in the canvas (`docs/ui-design-guide.md:55`).

Selector dialogs are attached to the root on opening (`LDLib2/.../gui/ui/elements/Selector.java:355–395`). Therefore a styling rule requiring ancestry under a settings panel will no longer match the open popup. The current lobby uses the root-reaching `.__selector_dialog__` class and z-index 95; its modal is 90 (`duel_lobby.xml:29–33`). Collection separately raises the import picker and installs alignment handling (`CollectionController.java:224–226,806–827`).

The collection workaround says the pinned runtime clamps dialogs in unscaled coordinates. The sibling source already contains coordinate conversion and anchor tracking (`Selector.java:355–380`). This is a reason to verify the shipped artifact before changing/removing the workaround, not proof it is currently necessary or obsolete. Keep it intact during mockup work.

### Input behavior is not supplied by appearance

`ManagementScreen.java:31–46,56–94` explicitly supplies focusability, Enter/Space activation, scroll-to-focused-button, visible-surface filtering and Tab traversal. The lobby adds its own selector keyboard behavior (`DuelLobbyScreen.java:117–131`). Replacing widgets with decorative panels would lose this behavior unless explicitly retained.

Button activation is attached to `MOUSE_DOWN` in `LDLib2/.../gui/ui/elements/Button.java:121`, so opening a modal or replacing a screen during a press changes the tree before release. `client/uitest/ManagementTestInput.java:5–15` preserves coordinates across that transition. Existing playtests use real routed mouse events for popups; direct listener invocation alone cannot prove that a visible target receives clicks.

Collection card browsing is already virtualized: fixed rows of four cards, 128 units high, with mounted artwork requests (`CollectionCardGrid.java:19–76`). A design with five columns, variable-height names or different density must deliberately change row grouping and virtualization dimensions as well as LSS. Retain right-click/double-click add, explicit Add/Remove controls and independent scroll positions. The existing guard against rebuilding unchanged rows (`81–91`) and post-layout scroll restoration (`CollectionController.java:795–803`) are relevant behavior, not styling noise.

## Smallest useful tooling after critique

1. **An evidence gallery, not another renderer.** Generate a static index over the existing harness screenshots/report. Show scenario/state, source revision, dependency version, requested and effective viewport, and a link to the report beside each capture. Retain dated outputs. Existing `gradle/ldlib2-uitest.gradle:1–10,74–100` already offers scenario selection, fixed window, GUI scale, input mode and keep-open. A gallery is only navigation; it must not imply screenshots prove behavior.
2. **A short shared LSS vocabulary.** Once a direction is chosen, extract only repeated surface, spacing, heading, body, muted, warning, focus and button classes into a resource stylesheet. Keep literal supported values; document the two canvas coordinate systems. Do not introduce a token compiler, CSS transpiler or universal screen/component framework. Existing XML stylesheet inclusion suffices. Screen-specific geometry stays local.
3. **Small preflight checks where silent mistakes recur.** Check duplicate XML IDs, controller-required IDs, resource existence and unsupported property names against the pinned registry/schema. Parse XML/styles with the existing LDLib2 path where practical; do not invent a second LSS parser. Mark a value check incomplete when a lazy parser returns null rather than throwing. XML schema validation alone does not prove inline LSS values, runtime tree shape or text fit.
4. **Reuse measured geometry and input checks.** Extend the existing checks at the proof screen, rather than building generic infrastructure in advance. `DuelUiAssertions.java:64–120` already measures rendered lines, checks bounds and distinguishes intentional scrolling. `CollectionWidgetPlaytest.java:211–231` checks scaled popup geometry and actual selection. Avoid an unconditional “no overlapping descendants” lint: card overlays, badges and modal dimmers overlap intentionally.

## Representative proof screen and acceptance

Use **Collection with a selected card, a full Main deck, Extra cards, expanded Side, search/filter controls and one modal/dropdown** as the first LDLib2 proof after mockup critique. It exercises the most constraints in one place: image density, text metrics, virtualization, independent scroll areas, editable input, acknowledged status and root-hosted popups. Keep it fixture-driven through `CollectionFixture`, `CollectionLayoutScenario`, `CollectionWidgetPlaytest` and `CollectionOverflowScenario`; add only missing states of the chosen design.

Compare the three HTML concepts using the same realistic content and viewport, including a long card name/effect, long deck name, unavailable artwork, empty results, unsaved state, Save pending/error and ownership-required/optional messaging. Visually distinguish concepts by information organization and action hierarchy, not only accent color. Label visual placeholders as such in the review artifact.

For the chosen proof, verify 1280×720 and 1920×1080 with effective GUI scales 2–4 where available, plus 1024×768 letterboxing. Check rendered text fit, scroll endpoints, popup anchor/clamp, mouse hit targets, keyboard traversal, one-layer Escape dismissal, preserved draft and selection on resize, and legibility in actual screenshots. The physical-size and pixel sharpness judgment remains human; automatic geometry checks cannot decide whether a 9-unit caption is pleasant to read.

HTML approval establishes hierarchy, content and direction only. Minecraft font glyph advances, line height, shadow, translation length, fallback glyphs, texture sampling and fractional transforms can all change the result. Custom font assets are technically addressable by the `font` property but are an additional design/asset decision, not an automatic browser-font migration. Prefer proving the existing Minecraft font first unless the selected direction explicitly requires a replacement.

Home and lobby then supply focused follow-up proofs for keyboard and modal transitions; duel needs its own 960×540 field/prompt proof and existing privacy/selection coverage. None of these visual proofs requires closing deferred two-player acceptance, and none can substitute for it.

## Unverified hypotheses to test, not preemptively fix

- A calmer surface hierarchy and fewer always-visible low-priority controls may improve scan speed. The screenshot supports the design question; no timed usability comparison was performed.
- Fractional transforms may contribute to uneven text or border sharpness at some viewport combinations. Rendering code makes that plausible; this audit did not isolate it experimentally.
- The collection dropdown alignment workaround may be redundant with the inspected sibling source. Confirm the resolved runtime artifact and reproduce the existing tests before touching it.
- A shared stylesheet may reduce drift between screens. Extract it only after the three concepts reveal which values are truly shared; premature abstractions would simply preserve the current design assumptions.

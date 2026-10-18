# Duelcraft Gallery design and style guide

Date: 2026-10-17. **The user approved the corrected native Gallery proofs through `63168de` and authorized production migration.** The [migration plan](../plans/2026-10-17-gallery-production-migration.md) and [verification report](../reports/2026-10-17-gallery-production-migration.md) own current implementation status. Historical proof notes below retain their original evidence and limitations.

## Authority and scope

Use the [approved Gallery prototype](../../mockups/ui-redesign/gallery.html), including the refinements through `1ab2bb1`, for composition. Use this guide for shared rules and the [tooling plan](../plans/2026-10-17-ui-tooling.md) for the next implementation checkpoint. The [surface audit](../reports/2026-10-17-ui-surface-audit.md) inventories the existing workflows; the [LDLib2 audit](../reports/2026-10-17-ui-ldlib-audit.md) records rendering constraints.

This guide replaces the visual direction and token proposals in the [brief](2026-10-17-ui-redesign-brief.md). The older [duel design guide](../../ui-design-guide.md) remains historical evidence and a source of regression cases. Its indigo palette, beveled surfaces, fixed panel measurements and rule that opening the log hides card details do not govern Gallery. Preserve current production behavior where older prose or simplified mockups disagree. Engine contracts remain in the engine wiki.

The work covers Home, My Collection, saved decks/file tools, lobby and live-duel surfaces. Progression, shop features, custom rule expansion, engine changes and the animation system remain separate work. Carried-forward multiplayer checks remain open and nonblocking.

## Visual priorities

Gallery uses dark plum backgrounds, parchment-colored actions and muted gold selection. Monster artwork supplies the character. Keep decorative treatment away from card effects, player choices and deck contents.

1. Give the deck, card inspector, browser and duel field their space before auxiliary controls. A toolbar must earn the space it takes from cards.
2. Group with alignment and spacing first. Use a surface when content scrolls independently or needs separation from the scene; do not place identical bordered panels inside one another.
3. Make the current decision obvious. Give one action primary emphasis within a decision region; do not make every available action a filled button.
4. Keep positions stable during routine updates. Adding a card, receiving a log entry or changing status must not move the field or the next click target.
5. Preserve the successful workflows. A less cluttered screen must still expose the existing filters, routes, statuses and escape paths.

## Color roles

These are opaque sRGB values. Implement them with supported LSS textures/text colors, not browser property names. Role names are documentation vocabulary; they do not imply support for CSS variables.

| Role | Value | Usage |
| --- | --- | --- |
| Canvas | `#211c2a` | Main background; solid area behind Home menu |
| Recess | `#19151f` | Navigation backing and recessed regions |
| Surface | `#34273e` | Dialogs, settings sheet, selected navigation |
| Inspector | `#2b2233` | Quiet card-reading surface |
| Hover | `#493851` | Hovered neutral controls; retain the selected marker |
| Primary text / action fill | `#eee5d4` | Ordinary text; primary button with Canvas-colored text |
| Secondary text | `#b8afbe` | Counts, metadata and supporting status |
| Selection | `#c6ab77` | Selected cards/items and current choice |
| Focus | `#efd18e` | Separate visible keyboard-focus outline |
| Divider | `#57465f` | Nonessential separation only |
| Control edge | `#8c7495` | Input and outlined-button boundaries |
| Success | `#a8c8ac` | Successful result, paired with text |
| Warning | `#e8b86d` | Attention needed, paired with the reason |
| Error / destructive | `#f08f9b` | Error text and destructive confirmation emphasis |

Control edge and semantic colors extend the mockup palette for the proof. Avoid creating extra near-identical shades for each screen. Selected, active, focused and pending are different states; gold alone cannot communicate all four.

Target at least 4.5:1 for ordinary text and 3:1 for essential control boundaries and focus against their actual adjacent fill. These are project review targets, not a certification claim. Calculated solid-color examples: Primary text on Canvas 13.28:1; Secondary text on Surface 6.58:1; Selection on Surface 6.30:1; Control edge on Surface 3.35:1. Decorative dividers need not carry an interactive boundary. Check composite colors again when transparency or artwork changes the background. Do not dim a whole disabled subtree until its label becomes unreadable; use Secondary text, subdued fill and actual input gating.

Keep existing engine-specific meanings such as Link markers and legal/selected targets when applying the palette; do not recolor them solely for decoration.

## Typography, scale and spacing

Start the proof with the existing Minecraft font. Segoe UI and Georgia in the browser are substitutes; this guide does not approve bundling either font. Test a restrained title using Minecraft text before proposing a custom display font. Use sentence case and left-aligned reading text; center the lobby's opponent decision and short choice groups.

Keep the existing 1280×720 management/editor canvas and 960×540 duel canvas. The table gives **candidate design-unit sizes before canvas scaling**, not framebuffer pixels. The 0.75 duel mapping is an initial equal-perceived-size hypothesis to validate, not a command to rescale all existing duel geometry.

| Role | Management | Duel proof equivalent | Rule |
| --- | ---: | ---: | --- |
| Home display title | 48 | n/a | One title; never use this scale for routine panels |
| Screen / major dialog title | 24 | 18 | Wrap long titles and grow their box |
| Section / selected card title | 20 | 15 | Card names may wrap; no cutoff |
| Body / ordinary control | 16 | 12 | Main reading and interaction text |
| Supporting metadata | 14 | 10.5 | Secondary information only |
| Ordinary control height | 40 | 30 | Center measured text within explicit bounds |
| Dense control height | 32 | 24 | Sort, density and compact contextual actions |
| Icon-only target minimum | 32×32 | 24×24 | Draw the icon smaller inside the target |

Do not shrink text to conceal overflow. Measure Minecraft glyphs, line spacing and padding. Prefer one line for ordinary action labels, wrapping for card/deck names and effects, and bounded scrolling for long content. Secondary metadata is the smallest proposed general text; specialized board counters need their own readability proof before changing them. Round dimensions at the rendered boundary if needed, without accumulating layout drift.

Use management spacing steps **4, 8, 12, 16, 24, 32**: 4 for a badge/detail, 8 within a compact group, 12 between related controls, 16 panel inset, 24 between sections and 32 at spacious screen edges. The corresponding duel starting values are **3, 6, 9, 12, 18, 24**. Existing art and field geometry may need exceptions; record the reason at the component rather than introducing new global tokens. The approved mockup's 14-unit gutters and 66-unit header are composition evidence, not a requirement for those exact values in LSS.

Text owners must set height as well as font size. LDLib2 Label's default height is too small for these proposed sizes. Constrain width before enabling wrapping; avoid adaptive width on bounded labels. Reserve error/pending space within the relevant region or use an overlay that does not move cards.

Use quiet edges and at most a small corner treatment for controls, with a slightly larger treatment for dialogs. Gallery fixtures use 1.25 design-unit straight edges: at the tested minimum framebuffer scale of 0.8 this preserves one rendered pixel on every side; a one-unit edge can disappear. Focus edges remain two units. Check native pixels after scaling, including live resize; geometry alone cannot prove an outline. Test supported textures before depending on rounded corners or shadows. Flat edges with the approved palette are the first proof fallback. Avoid thick bevels and drop shadows on each panel.

## Screen composition

### Home

Keep featured monster art on the right, fading into a solid menu on the left. Render this decorative backdrop across the full window, including space outside the centered management canvas. Keep controls on the stable canvas. Place the title, active deck and short vertical action list in that safe area. Crop artwork without stretching it; keep it out from behind menu text. Missing art leaves a complete, usable menu on the Canvas fill. Do not add promotional tiles, level bars or a decorative dashboard.

No active deck, suspended draft, incoming invitation and request failure are real states. Show their relevant action and concise status in the menu area. Do not combine an absent deck with a fabricated deck name or successful status.

### My Collection

Preserve inspector left, deck center and browser right. Allocate most flexible width to the deck; retain enough inspector width to read effects and enough browser width for the complete search controls. The prototype's 230/596/386 column widths are a starting composition only. A density or column-count change must also update virtualization and hit targets.

Keep New deck in the existing top action row with My decks and Import / Export. Keep Save separate from Use for duels. Put Sort and Larger/Smaller cards beside the Main heading. Do not reintroduce an auxiliary toolbar above the deck. Keep the deck name editable with a bounded field; long names must not push actions out of view. Stress the existing 128-Java-string-unit deck-name limit and export filenames of 100 units including `.ydk` (96 before the extension); do not shorten model limits to fit the layout.

Main, Extra and Side form three vertically stacked, full-width sections. Use the same column count, card size and horizontal spacing in all three sections at each card density. Center all three heading lines vertically within equal-height headers. This user refinement supersedes the side-by-side Extra/Side arrangement in the original mockup. Each section retains clear headings/counts and independent usable content. Preserve the Side collapse affordance and Deck/Side add routing. Prove a full 60/15/15 draft, both card densities and expanded Side before freezing section heights. Normal 40-card content should remain comfortably visible; when capacity exceeds the available region, scroll cards instead of shrinking them or moving the section actions offscreen.

Preserve category, subtype/properties, race, attributes, Level/Rank/Link, ATK/DEF/scale ranges, ownership, sorting and alternate-format filters. The HTML filter panel is incomplete. The user likes the production browser's rich controls; start from those controls and fit Gallery's colors and hierarchy around them. Keep active filters and a clear/reset action visible. Filtering must not reset inspector, selection or unrelated scroll positions.

Keep the empty inspector quiet. Show the card name, artwork, essential stats and scrollable effect when selected. The Collection inspector uses cropped artwork in a square; deck and browser tiles use full portrait cards. Unknown/hidden cards show only permitted information. Show explicit Add/Remove and transfer controls where applicable, so the interface remains usable without knowing mouse shortcuts.

### My decks and file tools

Use a compact scrollable saved-deck list with a clear selection and active marker. Keep Open, Duplicate and Delete scoped to that selection. New deck stays outside this menu. Show a single close affordance; do not add a Done button with the same effect.

Inset the active marker within the saved entry's continuous surface. Use a fixed-size, square-cornered gold Active chip that becomes red Clear on hover or keyboard focus, with a “Clear active deck” tooltip. Clicking it or pressing Enter/Space clears activation through the existing acknowledgement flow without selecting the row or deleting the saved deck. Remove the separate Clear active row. Tab/Shift+Tab stays within My decks while it is the top layer; scrolling keeps focused entries visible.

Import / Export remains a separate task. Use a dropdown of YDK files from `<instance root>/duelcraft/decks`; no operating-system file dialog or permanent folder caption. Stack the import group and export action vertically with consistent widths. Preserve export filename validation and overwrite confirmation. Place Help behind its own control, without a paragraph footer in each menu.

### Duel lobby

Use the centered opponent decision and clear invitation action from Gallery. Organize the header by purpose: a quiet back-to-Home control beside the Duel title on the left; Change deck and Settings together on the right; Help and Close after a larger gap and subtle divider as screen utilities. Align Change deck and Settings together at the upper right. The bottom strip contains active-deck and rule information only. Selection needs a marker plus the player's name; availability needs readable text.

A long roster scrolls in a bounded central region while preparation actions and deck/rule summary stay visible. Zero-player and one-player states should not retain an empty nested panel. Incoming invitation, waiting, RPS, tie, first-turn choice and startup each use that decision area with only the actions allowed by the current state. Do not promise deck/settings edits after the server has locked preparation. Closing the screen and cancelling preparation remain distinct.

Settings use the side-sheet composition. Keep label/control pairs aligned, numeric fields compact, and the rule dropdown large enough to read. The current options are enough for this pass; custom-rule expansion comes later.

### Live duel

Keep the field, hands, LP, turn and prompt decisions dominant. Preserve all supported rule topologies and auxiliary zones. The mockup's management-style top navigation is a comparison convenience, not approval for new live-duel navigation or bypassing leave confirmation.

Card inspection stays left. Zone inspector sits above the duel log on the right. Each keeps its own scroll position. Appended log entries follow only when the reader was already at the bottom. One visible panel uses the full column; two use a split. Expand temporarily gives one panel the column while preserving the other's visibility intent and reading position; Restore returns to the prior visibility and split state; it must not reopen a panel that was already closed. Closing a panel is distinct from expanding another. Do not change the board bounds when either panel opens, closes or expands.

Prompt source, instruction, candidates, selected count and response actions need distinct hierarchy. Revealed candidates belong in the selection prompt in the engine's response order; do not require the zone inspector to interpret a blank choice. Longer effect/condition choices wrap and scroll, while response actions stay visible. Preserve waiting, retry, optional-chain controls, hidden-prompt behavior, leave confirmation and result flow.

Keep persistent effect indications separate from transient feedback. Their placement under heavy load needs a dedicated duel composition proof; the approved static sketch does not settle this case. Preserve existing effect semantics and do not reintroduce previously fixed hidden-information bugs as part of styling.

## Components and states

| Component | Required behavior and treatment |
| --- | --- |
| Primary button | Parchment fill, dark text; one emphasized action per decision region. Hover/press alter fill without moving geometry. |
| Secondary / quiet action | Outline for major alternatives; plain text for Sort, density and similar utilities. Same readable label and hit-target rules as primary. |
| Selected item | Gold outline/marker plus persistent selected state. Hover and keyboard focus can coexist with it. Active deck additionally says Active. |
| Text field | Visible boundary, caret/focus, bounded width; keep typed content on validation failure. Provide a name/accessibility label even when an obvious visible caption is omitted. |
| Selector | Explicit label and row heights; at least five complete options when space permits. Bounded popup scrolls; selected value remains identifiable. Clamp to viewport and keep selected row reachable. |
| List row | Stable height or intentional wrapped layout; distinguish selected and active. Keep row actions separate from selection hit targets. |
| Card tile | Preserve artwork ratio, orientation and count badge. Selection/focus must not obscure artwork or overlap the neighboring target. No hover movement needed in the first proof. |
| Scroller | Scroll only the owned content; keep heading/actions visible. Retain first-to-last reach and reading position after resize/reflow. Do not consume wheel input for another panel. |
| Dialog / sheet | One title and close affordance; bounded body, visible action footer. Focus stays on the active surface, then returns to the initiating control. |
| Feedback | Pending status beside the action; concise centered result/error when needed. Errors state what happened and the available remedy. Routine add/remove/search clicks remain quiet. |

For each interactive specimen cover default, hover, pressed, keyboard focus, selected where applicable, disabled, pending and failure. Disabled controls must reject activation through both mouse and keyboard. Pending operations must not submit twice. Leave failures recoverable without discarding a draft or silently claiming success.

Saved, active and current draft are separate facts. A Save becomes successful only after its server acknowledgement. Use for duels stays explicit; deposits never activate a deck. Ownership-required and optional modes retain their existing legality/companion policy behavior. A style class must not become a new authority check.

## Input and layers

Preserve browser right-click add, deck right-click remove, double-left-click add and the existing copy limit/route rules. Keep keyboard traversal deterministic and focus visible, including scrolling focused rows into view. Do not consume typing or modified keys as duel shortcuts. A visible icon-only action needs a tooltip/name; essential instructions cannot depend on hover alone.

For management surfaces, Escape closes the top selector first, then its owning dialog or sheet, then the screen through the existing dirty-draft rules. Empty unsaved decks do not gain a new discard warning. A dirty navigation confirmation offers Save changes, Discard changes and Cancel with clear consequences. Live-duel Escape keeps its current leave-confirmation behavior, including when another prompt is open.

Management layering order is base content, local filter surface, modal dimmer + dialog, its dropdown, then nested confirmation. Noninteractive feedback stays visible without intercepting clicks. Closing a higher layer restores focus and preserves the lower layer's content. Use existing close controllers; do not create a second modal state machine for the theme.

For live duels, preserve the established ordering and allowed input of prompt dimmers, card details, action dock and leave/result/message overlays. A generic management modal helper must not replace this contract. Verify draw order, clipping, hit tests and keyboard ownership separately.

LDLib2 selectors reparent to the root. Apply popup styles using a scoped root-visible class or the existing safe selector pattern; ancestry-only styles cannot be relied upon. Do not remove the existing popup coordinate workaround until the resolved runtime artifact and real scaled-input checks prove it unnecessary.

## Text, artwork and motion

Use short verbs: Save, New deck, Change deck, Invite, Cancel invitation. Keep nouns consistent across screens. Prefer “No players online” to explanatory paragraphs. Put control instructions in Help. Do not add implementation language, ownership-option captions or file-format limits as permanent decoration. Display validation only when the value needs correction; this guide does not alter supported validation rules.

Truncate long names only in compact rows, with the full name available in the selected detail or tooltip. Never truncate card effects, errors or selectable condition text with no way to read the rest. Test accents, fallback glyphs, long names and multiline effects with the real font. Avoid hardcoded English string widths in reusable components.

Use existing card assets and fallback art paths. Do not stretch cards or reveal hidden artwork via hover, placeholder caching or a transition. Original Duelcraft art can replace the featured artwork later without rearranging Home.

The live duel keeps the Minecraft world visible behind the interface. Do not fill the full window or duel canvas with an opaque Canvas color. Gallery surfaces belong to the board, reading panels and controls; management screens retain their opaque backgrounds.

Give the main field surface one thin Control edge outline to separate it from the world. Do not add a bevel, nested frame or gold selection color to this boundary.

The first proof needs no animation. Later motion should explain zone movement, reveal or changed selection, with stable origin/destination anchors. Define interruption, queued events and reduced-motion behavior in that separate task. Do not add idle pulses, automatic art carousels or decorative entrance animations here.

## Design-to-LDLib2 workflow

1. Sketch a meaningful change in HTML using realistic content; review composition before production edits. Minor spacing refinements need only targeted checks.
2. Implement shared styles and specimens against the pinned LDLib2 runtime. Use literal supported LSS classes and explicit state bindings. Keep screen geometry local and avoid a token compiler or HTML-to-LSS translation layer.
3. Build a fixture-only dense collection composition and lobby popup proof using real controllers. Start with current browser controls. Record font sizes, physical scale, popup geometry and input results next to screenshots.
4. Review the real rendering with the user. Refine the measured values here when the proof changes them. Only then plan production screen migration in small batches.

For each component record the XML/style owner, any Java override, states, layout assumptions and fixture. The short exception log belongs with the specimen evidence; it should explain each necessary departure from this guide. Avoid wrappers around individual LDLib2 widgets unless the proof shows repeated behavior that needs one owner.

## Acceptance and remaining decisions

The tooling plan owns component, dense-collection and lobby-layering proof checks. Before product migration, review actual captures at 1280×720 and 1920×1080, requested GUI scales 2–4, plus 1024×768 for letterboxing. Record effective GUI scale, framebuffer and canvas transform; a requested scale may be clamped. Below this reviewed matrix, retain current behavior and make no new usability claim.

Check long text, empty/unknown artwork, full 60/15/15 content, expanded filters, both densities, popup edges, disabled/pending/error states, Tab/Enter/Space, wheel input, Escape order, focus restoration and state preservation across resize. Measure text fit and routed hit targets. Human review still judges hierarchy, legibility and pixel sharpness. Native browser dropdowns do not prove any of these LDLib2 properties.

Final font metrics, tight-window compromises, texture corner treatment and loaded-duel density remain proof decisions. The guide supplies starting values rather than invented runtime evidence. Home/lobby interaction-state coverage and the complete live-duel prompt/rule matrix belong to subsequent screen-migration plans; do not infer their completion from the tooling specimens. The [manual multiplayer checklist](../../testing/player-interaction-manual.md) remains separate.

## Task 1 acceptance capture

The fixture-only `gallery_specimen` run on 2026-10-17 passed 255/255 automated checks at requested 1280×720, GUI scale 2, for both 1280×720 management and 960×540 duel canvases. The candidate font/control sizes above were retained; the Home display is omitted from the duel specimen. The real selector still required the existing root-coordinate clamp correction, copied locally into the fixture. See [runtime proof](../reports/2026-10-17-gallery-runtime-proof.md) for artifact identity, captures, input checks and exceptions. Agent visual inspection is recorded there; human hierarchy, legibility and sharpness approval and the broader viewport matrix remain pending. This does not approve production migration.


## Task 4 collection proof measurements

The fixture-only collection composition keeps the 1280×720 management canvas and production controller. Inspector is left, Main is center, and the browser is right. Extra and Side share the lower center row; New deck remains in the existing top action region and Sort/card size sit in the Main heading. Production XML, styles, routes and popup correction remain unchanged.

The browser retains four columns of 74×108 card artwork with 128-unit virtual rows and 10-unit copy captions. These compact captions are an explicit retained browser-contract exception to candidate 14-unit metadata. Main retains 10 Standard columns of 54×79 cards and eight Large columns of 70×103 cards. The full-width Extra and Side sections share Main's Standard and Large grids and horizontal spacing. Extra retains the controller's 128/150-unit section heights and expanded Side retains 150.

The Side heading uses 18 units: the real Minecraft font measured 206.67 units at candidate size 20 against 190 available beside Collapse. Other section headings remain 20. Collapse uses dense height 32 so its 28-unit label fits. Import / export uses width 160 and Filters uses width 128 to fit its expanded “Close filters” caption at size 16. These are local fixture measurements, not approval to change production typography or behavior.

The fixture omits the permanent browser search instruction and hides the export folder/instruction caption while retaining the controller-required export-copy label and type. Existing browser controls, file actions and the small scrollable My decks surface remain. Long selected names and effects retain wrapping and independent scrolling: the extreme selected name can push the effect below the initial details viewport, so the proof does not claim simultaneous display of artwork, full name, stats and full effect. Saved-name rows retain clipped text plus the full-name tooltip and some inherited selected-button treatment; complete production styling for these rows remains a migration follow-up. Human hierarchy, legibility and sharpness acceptance remains pending.

## Task 5 lobby proof measurements and migration gap

The fixture-only lobby uses the unchanged 1280×720 management canvas, real preparation controller and original static IDs/widgets. Change deck and Settings share the upper-right action row. The bottom 64-unit strip contains only active-deck and rule/options information; invitation actions and explicit screen Close remain separate. Outer padding is 24, panel gaps are 16, row gaps are 12, modal padding is 24 and modal gaps are 8. Existing Minecraft typography remains: title 24, section 20, body/control 16, metadata 14 and regular control height 40. The Settings panel is 560 units wide.

Popup styling is owned by `gallery_lobby.xml` selectors rooted at `#lobby-root`, since the real rule selector reparents its dialog directly to the UI root. Modal/dialog ordering remains 90/95. Existing six-row/180-unit selector viewport and 28-unit rule rows are retained: six complete rows fit, with the final entries wheel-accessible. The 28-unit popup row is a measured local exception to candidate dense height 32. Root-hosted scroll controls must not inherit management-action minimum widths: a targeted failing containment check reproduced that overflow, then narrowing the action selector removed it. This fixture adds no popup coordinate correction or clipping workaround and changes no existing production correction.

Both unchanged baseline and Gallery fixtures reproduce the focus-restoration gap: Settings is keyboard-focused before opening; after focusing the life-points field and dismissing Settings, the focused element is `none`. Hidden focus is correctly cleared and subsequent typing cannot change that field. **Restoring the initiating control remains an unmet production migration requirement**, recorded as an observation rather than a passing restoration assertion. Routed Tab remains inside visible Settings; Escape closes the selector first and Settings next. Do not silently fix restoration in the layout injection seam.

The [runtime proof](../reports/2026-10-17-gallery-runtime-proof.md#task-5--real-lobby-popup-modal-and-handoff) links the frozen source, matrix, input checks and captures. Agent visual inspection supports this fixture's layout/input handoff; human hierarchy, legibility and sharpness approval remains pending. Dynamic opponent rows still retain the real controller's centered caption and checkmark selected treatment. Production Home, collection, lobby and duel migration needs its own reviewed plan. Home/duel browser navigation remains composition reference; do not add functional section navigation to a live duel. Tooling evidence does not close multiplayer or privacy acceptance.

At requested 1280×720 scales 3/4 (effective 3), retained screenshot pixel measurements put the selector at (386,216), 512×40, and popup at (428,256), 512×180: a 42-physical-pixel horizontal offset. This is an observed Gallery runtime limitation, not baseline-reproduced evidence; pixel-stable anchor alignment remains an unmet migration requirement. Rows remain visible and actual routed mouse selection passes; the proof does not claim pixel-stable anchor alignment at every scale. No coordinate listener was replaced in this lobby fixture or seam. Any alignment migration must be reviewed against the exact pinned runtime, preserving the collection's existing workaround.


At 1920×1080/effective4, raster selector `(576,324)` / 768×60 and popup `(600,384)` / 768×270 show a further 24-physical-pixel horizontal offset. The affected 720p/effective3, 1080p/effective4 and 1024×768/2 logs also emit repeated LDLib2 dirty-layout warnings; these remain unresolved Task 5 runtime limitations rather than baseline noise. Review exact pinned-runtime coordinate handling before production migration.

## Component correction contracts (2026-10-17)

Gallery action presentation belongs to the explicit `gallery-action` role on fixture buttons. Never scope action heights or button textures to every descendant `button`: LDLib2 selectors and scrollers construct their own Button children. Fixture XML annotations identify actions; vertical-scroller head/tail use explicit compact 12×12 management and 9×9 duel geometry, with matching 12/9-unit track and thumb widths. Use flat Gallery mauve thumbs (hover: muted cream; pressed: amber) and flat arrow glyphs tinted to the Gallery text palette. Stock shaded GDP scrollbar textures are not the Gallery style. Shared text colors may remain canvas-scoped. Controller-created collection Buttons have verified semantic roles `saved-name` (inside the `saved-row` container), `filter-choice` and `filter-chip`; these roles share action styling explicitly. Lobby player-card renderers own their multi-element typography and geometry. Real disabled actions use the native LDLib2 `__disabled__` class; retain `gallery-disabled` as a vocabulary alias, but test native input gating without that alias masking presentation. Local primary-text overrides must also supply readable disabled text. Explicit pending roles keep their amber treatment. Native Selector popup scrollers intentionally hide their arrow buttons; verify those hidden arrows, matching track/thumb widths and Gallery textures instead of applying the visible-list arrow-size contract. Dense actions retain the same font and centering contract with a shorter control height.

A centered line inside a top-aligned child box is still an off-center button label. Center the child on the row's cross axis (`align-items: center`) and retain the TextElement's centered horizontal/vertical line alignment. `GalleryVisualAssertions.control(ctx, selector)` attaches outer/content bounds, actual line start Y, font/alignment and winning style slots, then checks both box and line center. `scroller(ctx, selector)` checks compact arrows, consistent widths and all three Gallery arrow/thumb state textures and exclusion from ordinary action roles. These helpers are Gallery fixture diagnostics, not a general UI inspector.

Review native-resolution ordinary/dense crops with mixed case and descenders, plus full specimens on both 1280×720 management and 960×540 duel design canvases. Open default, hover, held press, disabled and keyboard focus captures; inspect glyph balance and clipping independently of line-box arithmetic. For scrollbars, open the actual list crop and verify distinguishable up/down arrows, matching track/thumb width and palette and absence of elongated blank action rectangles. Record exact source hashes, dependency identity, actual framebuffer/GUI scale and each opened image. Compare composition and hierarchy with the approved HTML reference pair. Keep functional pass/fail and visual pass/fail separate; a passing harness is not visual or human approval.

Field-like controls share one horizontal content-inset declaration per canvas in `gallery.lss`: 10 design units on management canvases and 7.5 on duel canvases. The grouped rule owns TextField content, Selector display, short popup lists and scrolling popup viewports. Do not add per-screen numeric copies. LDLib's Selector display is the direct `element` child; target it rather than adding padding to both parent and preview, and reset its explicit left edge to auto so the horizontal inset applies. Put the Gallery canvas class on the actual UI root so reparented popups inherit it. TextField text, caret, selection and horizontal scrolling retain their shared content box. Verify actual text starts in deck name, search, quantity, selected dropdown values and both popup variants. Centered action captions and ordinary headings/prose retain their alignment/layout rules; do not blanket-pad every nested TextElement. Any deliberate control-inset exception must be documented beside its rule.

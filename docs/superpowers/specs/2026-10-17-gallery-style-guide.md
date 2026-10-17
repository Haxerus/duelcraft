# Duelcraft Gallery design and style guide

Date: 2026-10-17. **Gallery's visual direction and refined layouts are user-approved. This guide specifies the next LDLib2 proof; its new measurements and component details still need that proof and review.** No production UI migration accompanies this document.

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

Use one-unit quiet edges and at most a small corner treatment for controls, with a slightly larger treatment for dialogs. Test supported textures before depending on rounded corners or shadows. Flat edges with the approved palette are the first proof fallback. Avoid thick bevels and drop shadows on each panel.

## Screen composition

### Home

Keep featured monster art on the right, fading into a solid menu on the left. Place the title, active deck and short vertical action list in that safe area. Crop artwork without stretching it; keep it out from behind menu text. Missing art leaves a complete, usable menu on the Canvas fill. Do not add promotional tiles, level bars or a decorative dashboard.

No active deck, suspended draft, incoming invitation and request failure are real states. Show their relevant action and concise status in the menu area. Do not combine an absent deck with a fabricated deck name or successful status.

### My Collection

Preserve inspector left, deck center and browser right. Allocate most flexible width to the deck; retain enough inspector width to read effects and enough browser width for the complete search controls. The prototype's 230/596/386 column widths are a starting composition only. A density or column-count change must also update virtualization and hit targets.

Keep New deck in the existing top action row with My decks and Import / Export. Keep Save separate from Use for duels. Put Sort and Larger/Smaller cards beside the Main heading. Do not reintroduce an auxiliary toolbar above the deck. Keep the deck name editable with a bounded field; long names must not push actions out of view. Stress the existing 128-Java-string-unit deck-name limit and export filenames of 100 units including `.ydk` (96 before the extension); do not shorten model limits to fit the layout.

Main, Extra and Side retain clear headings/counts and independent usable content. Preserve the Side collapse affordance and Deck/Side add routing. Prove a full 60/15/15 draft, both card densities and expanded Side before freezing section heights. Normal 40-card content should remain comfortably visible; when capacity exceeds the available region, scroll cards instead of shrinking them or moving the section actions offscreen.

Preserve category, subtype/properties, race, attributes, Level/Rank/Link, ATK/DEF/scale ranges, ownership, sorting and alternate-format filters. The HTML filter panel is incomplete. The user likes the production browser's rich controls; start from those controls and fit Gallery's colors and hierarchy around them. Keep active filters and a clear/reset action visible. Filtering must not reset inspector, selection or unrelated scroll positions.

Keep the empty inspector quiet. Show the card name, artwork, essential stats and scrollable effect when selected. Unknown/hidden cards show only permitted information. Show explicit Add/Remove and transfer controls where applicable, so the interface remains usable without knowing mouse shortcuts.

### My decks and file tools

Use a compact scrollable saved-deck list with a clear selection and active marker. Keep Open, Duplicate and Delete scoped to that selection. New deck stays outside this menu. Show a single close affordance; do not add a Done button with the same effect.

Import / Export remains a separate task. Use a dropdown of YDK files from `<instance root>/duelcraft/decks`; no operating-system file dialog or permanent folder caption. Stack the import group and export action vertically with consistent widths. Preserve export filename validation and overwrite confirmation. Place Help behind its own control, without a paragraph footer in each menu.

### Duel lobby

Use the centered opponent decision and clear invitation action from Gallery. Align Change deck and Settings together at the upper right. The bottom strip contains active-deck and rule information only. Selection needs a marker plus the player's name; availability needs readable text.

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

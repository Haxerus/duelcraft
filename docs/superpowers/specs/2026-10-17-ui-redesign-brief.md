# Duelcraft UI redesign: audit and design brief

Date: 2026-10-17. **Gallery selected for refinement; product implementation is not yet approved.**

## Purpose and scope

Give players a coherent interface for managing cards, preparing duels and following the simulation. The user wants the discipline of a mature application: a documented visual system, predictable controls, clear hierarchy and a repeatable design-to-LDLib2 workflow. The visual identity still belongs to Duelcraft.

PR #7 merged into `main` at `ef0599d`. This design work uses `codex/ui-redesign` in the existing deck-editor worktree. Carried-forward multiplayer acceptance remains tracked in the [manual checklist](../../testing/player-interaction-manual.md); the user has confirmed it does not block new work.

This checkpoint contains a source audit, this brief and three HTML review directions. Next come critique, a selected design/style guide, a small tooling plan and a real LDLib2 proof. Product migration follows those decisions. This checkpoint does not change Java, LSS, assets packaged in the mod, server policy or dependencies.

## What we have learned

The [surface audit](../reports/2026-10-17-ui-surface-audit.md) records current controls, journeys and difficult states with source references. The [LDLib2 audit](../reports/2026-10-17-ui-ldlib-audit.md) distinguishes verified LDLib2 behavior from assumptions about the browser sketches.

| Finding | Design response |
| --- | --- |
| The lobby nests equal-weight panels and a fixed player scroller inside a large frame. | Compose the screen around choosing an opponent, then the current preparation decision. Use whitespace and alignment before adding containers. |
| Home already has the featured-art fade; the editor already has the requested columns and rich controls. | Keep those strengths. Improve hierarchy without making players relearn the successful workflows. |
| Header rows, status text and repeated instructions compete with cards for height. | Keep action labels short. Move explanation into Help or a response to the action that needs it. |
| Management/editor and duel use different design canvases and scale paths. | Define one perceived size/spacing system. Prove its mapping in both render paths before changing either canvas. |
| Text height, Java overrides and popup reparenting can defeat plausible stylesheet changes. | Check actual text bounds, the owning style layer and root-level popup behavior in the real library. |
| Current automation already provides mouse input, screenshots, viewport metadata and difficult fixtures. | Extend the existing harness into a useful design workspace. Avoid building another renderer or test framework. |

## Decisions already supplied by the user

- Keep the inspector on the left, the deck in the middle and the browser on the right. Preserve the existing rich browser filters and search behavior.
- Home uses featured monster artwork fading into the clean menu on the left. Original Minecraft-styled artwork can replace it later.
- Keep a small, scrollable My decks interface. New deck stays at the top level; import/export is separate, with a picker limited to `<instance root>/duelcraft/decks`. Do not add a folder caption or an operating-system file picker.
- Keep Help separate. Avoid permanent explanatory paragraphs, obvious-field labels and redundant Close/Done actions.
- Keep explicit exit controls and predictable Escape behavior. Closing a lobby screen and cancelling an invitation are distinct actions. Management Escape dismisses the top layer; live-duel Escape currently opens the leave confirmation even over a prompt. Preserve that distinction unless the user approves a change.
- Keep the duel log below the zone inspector in the right column; either can use the whole column when the other is hidden. Preserve reading positions.
- Right-click in the browser adds a card; right-click in the deck removes it. Double-left-click remains a secondary add method. Visible controls remain available.
- Preserve saved/active/draft distinctions, supported legality, authoritative operations, hidden-information boundaries and companion policies. Design clarity cannot weaken those contracts.
- Progression and duelist levels belong to the companion-mod discussion. The full animation system is a later task; this design should leave room for meaningful motion.

## Provisional design rules

These are starting points to test, not a finished style guide.

| Area | Proposed rule and how to judge it |
| --- | --- |
| Hierarchy | One clear primary action per decision region. Save and Use for duels remain separate. A selected item, keyboard focus and a pending operation must look different. |
| Spacing | Use a 4-unit base with 8/12/16/24/32 spacing roles. Group controls by task; use larger gaps between tasks. Check alignment on real screenshots, including expanded selectors. |
| Type | Separate screen title, section title, body/control text and secondary metadata. Browser body text is approximately 14–16px, titles 24–32px. Final sizes and font face require Minecraft proof; do not copy browser pixels into LSS blindly. |
| Color | Define canvas, surface, raised layer, divider, primary/secondary text, selection, focus and semantic feedback roles. Use text/icons as well as color for status. Measure contrast after selection; no accessibility compliance claim yet. |
| Surfaces | A border marks an interactive region or useful grouping. Avoid a bordered panel inside another panel merely to fill space. Use bevel/pixel details only where the selected identity needs them. |
| Controls | Align text baselines; specify control and popup-row height, padding, minimum width, hover, pressed, disabled and focus states. Show at least five selector entries when space permits; scroll within a bounded popup. |
| Density | Protect the field and card grid first. Use independent scroll regions with stable positions. Long card/deck names, effects, logs and denied actions need deliberate treatment. |
| Layers | Define menu, dropdown, confirmation and prompt layers as a small documented stack. Drawing order, input interception and keyboard focus must agree. |
| Feedback | Routine editing remains quiet. Show pending state near the affected action, then a brief result or actionable error. Do not turn every click into a toast in production. Prototype messages only identify simulated behavior. |
| Motion | Use motion to explain origin, destination and state change. Keep source/destination anchors stable and never reveal a hidden card through animation. Define interruption and reduced-motion behavior before later animation implementation. |

## Three review directions

Open [the comparison page](../../mockups/ui-redesign/index.html). Each direction includes Home, a populated lobby, a 40-card editor and a live-duel composition with both right panels. Browser controls use local fictional state. The same artwork and core content support comparison; these are not engine-generated duel snapshots.

| Direction | Composition and character | Tradeoff |
| --- | --- | --- |
| **A: Studio** | Slate surfaces, restrained blue selection, persistent horizontal navigation. Flat roster with rules alongside it. Compact editor controls and predictable columns. | Best starting point for frequent editing and minimal workflow disruption. It needs enough character from artwork and the board to avoid feeling like a utility app. |
| **B: Gallery** | Deep plum, parchment actions and muted gold selection. Centered section navigation, artwork-led Home and a focused opponent decision. Secondary editing controls share existing headings; the selected card retains a larger treatment. | Stronger atmosphere and focus. Larger elements consume card space; review whether the comfort gain earns that space. |
| **C: Workbench** | Cool stone surfaces, dark ink, restrained teal, square controls and a compact mode rail. Structured roster and adjacent setup area; a tabletop field. | Strongest change in identity and a closer material relationship to Minecraft. Check long-session comfort and whether labels/borders add too much visual weight. |

The user selected Gallery for its polished, game-oriented presentation, with Studio a close second. Workbench was rejected as too similar to a business SaaS application. Gallery Home and lobby were well received. For My Collection, the deck list, inspector and browser take priority; auxiliary controls must fit around those surfaces rather than reserve a separate toolbar row. The revised mockup moves New deck into the existing top action row and Sort/card size into the Main heading. These placement details remain open to critique.

Prototype palette roles, ordered canvas / surface / divider / primary text / secondary text / accent:

| Direction | Six starting colors | Browser type treatment |
| --- | --- | --- |
| Studio | `#171c24` / `#222a35` / `#384454` / `#e9edf3` / `#aab7c8` / `#a7c9ff` | Segoe UI; compact 15px body, medium-weight headings. |
| Gallery | `#211c2a` / `#34273e` / `#57465f` / `#eee5d4` / `#b8afbe` / `#c6ab77` | Segoe UI at 16px; Georgia reserved for the Home title. |
| Workbench | `#e5ebeb` / `#f5f7f6` / `#cad4d3` / `#263a3c` / `#586c6e` / `#28756f` | Segoe UI at 14px; strong labels, square controls and a small pixel motif. |

These browser fonts are comparison aids, not a decision to bundle fonts. The selected typography needs a Minecraft rendering proof and an explicit font choice before approval.

The sketches simplify some filter contents and game actions. They do not authorize removing any production filter, prompt family, zone, deck action or entry route. Native browser selectors, font metrics, shadows and scaling do not establish LDLib2 support. A 960px preview uniformly scales the canvas; it does not demonstrate production responsive reflow or Minecraft GUI-scale behavior.

## Subsequent tooling checkpoint

After choosing and refining a direction, specify the minimum tools needed to implement it reliably:

1. A screenshot/state gallery over existing LDLib2 reports, with screen, fixture, window, framebuffer and effective GUI scale visible.
2. A small reusable component specimen screen for buttons, text fields, selectors, rows, feedback and layered dialogs. Include long text, disabled/pending/focus states and real mouse/keyboard input.
3. Shared literal LSS classes and documented token roles. First test whether a tiny generation step earns its maintenance cost; do not assume browser CSS variables work in LSS.
4. Checks for unsupported declarations, missing/duplicate wired IDs, text fit, popup bounds and click targets. Reuse parser behavior and existing assertions where possible.

Use the collection editor as the first dense composition proof, including expanded filters and an open import selector, then the lobby Settings popup as the layering proof. Verify the pinned runtime binary before removing existing coordinate workarounds. Do not introduce a general HTML-to-LSS compiler.

## Review and completion criteria

This first review asks which direction feels right and which arrangements should change. It does not request approval to implement the entire UI.

Before product migration, the chosen direction must cover: Home with no deck/invitation/error; editor with full Main/Extra/Side, long text, empty search, unknown artwork, pending/failing Save and policy denial; dirty navigation and destructive confirmation; lobby with no players, invite/wait/RPS/tie/first choice/start failure; duel with long hand/log/zone contents, revealed-card prompts, waiting, leave confirmation and result. Preserve behavior while measuring each of these states.

Success means a player can identify the current state and next action; controls remain legible and clickable at supported sizes; field/card content keeps usable space; and another developer can build a matching screen from the guide and component specimens. The user will critique the mockups before the style guide and tooling implementation plan are finalized.

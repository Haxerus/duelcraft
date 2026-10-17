# UI direction checkpoint — 2026-10-17

Baseline: merged `main`, `ef0599d`, in the existing deck-editor worktree. Branch: `codex/ui-redesign`. The user authorized an audit, design brief and three mockup directions. The user will critique these before style-guide finalization, tooling implementation or product changes.

## Delivered

- [Design brief](../specs/2026-10-17-ui-redesign-brief.md), including existing preferences, proposed design rules, direction tradeoffs and the next LDLib2 proof.
- [Surface/journey audit](2026-10-17-ui-surface-audit.md) and [LDLib2/tooling audit](2026-10-17-ui-ldlib-audit.md), prepared independently by subagents and checked against current source.
- [Interactive comparison](../../mockups/ui-redesign/index.html): Studio, Gallery and Workbench, each showing Home, lobby, collection and duel. The parent built Studio/comparison/shared fixture; two subagents built the other directions in separate files.

The prototypes use existing local artwork with provenance in their [README](../../mockups/ui-redesign/README.md). They contain fictional data and local interaction samples. No production source, packaged resource, dependency or server configuration changed. No merge or push belongs to this checkpoint.

## Review checks

The in-app browser-control runtime failed to initialize (`failed to write kernel assets`). A subagent's direct `file://` preview was also rejected because that protocol is unsupported. Review used a loopback-only HTTP server exposing `docs/mockups` and a separate headless Edge renderer through the bundled Playwright package. It did not control the user's browser session or require an installed dependency change. The local comparison URL was queued to open in Codex.

The final browser pass rendered all 12 screen/direction combinations at 1280×720 with zero JavaScript errors or broken visible images. A basic viewport-bound scan found no out-of-frame controls outside designated scroll regions. Screenshots were inspected, including representative filter/settings/file dialogs. Initial inspection found Studio grid overlap and clipped filter actions, Gallery's missing field zones, and Workbench's reversed Home art/menu orientation; those were corrected and rendered again.

The interaction pass exercised Settings/Escape, invite/cancel, My decks, Import/export, filters, search input and log toggles in each direction. All 24 recorded flow/fit checks completed; duel canvases also fit a 960×540 browser viewport. These checks cover the prototype paths, not rule legality, full keyboard accessibility, semantic filter equivalence, every responsive state, Minecraft or LDLib2 rendering. The browser uses its own native selectors.

Local evidence: `build/ui-redesign-review/`, with render and interaction scripts, JSON results and screenshots. The full Gradle suite and Minecraft harness were not rerun for HTML/document-only work. Existing M6 evidence remains unchanged; carried-forward multiplayer acceptance remains nonblocking as instructed by the user.

An independent brief review checked local links, image provenance and claims. It prompted corrections to distinguish current virtualization constraints from a fixed design mandate, and management Escape behavior from live-duel leave confirmation. No design direction is approved by those checks.

## Next critique

Compare the same screen across directions before choosing. Assess lobby decision clarity, card density, control hierarchy and the balance between artwork and usable space. Refine the chosen direction, then cover difficult states and write the style guide/tooling plan. Keep product implementation and animation work for later checkpoints.

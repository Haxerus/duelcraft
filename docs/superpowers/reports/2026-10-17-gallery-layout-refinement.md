# Gallery layout refinement — 2026-10-17

The user requested full-width Main, Extra and Side sections with matching card spacing and centered headings, plus a more deliberate lobby header. These refinements affect Gallery fixtures only. Production layouts, controllers, navigation and policies are unchanged.

The collection fixture now inherits the same grid columns and card spacing for all three sections, at Standard and Large density. All section headings share a 32-unit row with centered text. The lobby groups Home with the page title on the left, Change deck with Settings on the right, then Help and Close after a subtle divider. The style guide records this refinement to the earlier mockup.

## Verification

Starting point: clean `00d0b2d` on `codex/ui-redesign` in the existing deck-editor worktree. New native assertions cover section order, width, spacing, heading alignment and lobby action order. They were added before the layout changes.

| Run | Result | Local archive under `build/gallery-proof/` |
| --- | --- | --- |
| Red, 1280×720, scale 2 | 3662/3676 checks; 14 expected layout failures; also an existing resize-settle timeout | `stack-header-red/` |
| Green, 1280×720, scale 2 | 2/2 scenarios, 3965/3965 checks; exit 0 | `stack-header-1280/` |
| Green, 1024×768, scale 2 | 2/2 scenarios, 3965/3965 checks; exit 0 | `stack-header-1024/` |

Both green runs include Standard/Large density, 60/15/15 stress, expanded Side, scrolling, popup/input flows and live resize to 1100×800. The initial test edit had a duplicate local variable; that compile failure was corrected before the recorded red run. Its separate log is retained. No runtime sources changed between the two green runs.

Commands used the shared `GRADLE_USER_HOME=C:/Users/haxer/.gradle`:

```powershell
./gradlew.bat runClient '-PldTest=gallery_collection,gallery_lobby' -PldTestWindow=1280x720 -PldTestGuiScale=2 -PskipNative
./gradlew.bat runClient '-PldTest=gallery_collection,gallery_lobby' -PldTestWindow=1024x768 -PldTestGuiScale=2 -PskipNative
```

Logs: `stack-header-compile.log`, `stack-header-red.log`, `stack-header-green.log`, `stack-header-small.log`. The known generic shader/Gradle warnings remain. Full JUnit and the broader production matrix were not repeated for this fixture-only layout refinement; earlier evidence remains separate.

Native visual inspection covered normal collection, both stress densities, live resize, selected lobby and Settings at the smaller viewport, plus normal collection and selected lobby at 1280×720. Card pitch matches across the stack, headings align, and the lobby groups retain legible controls and complete borders. Partial card rows in scrolled stress captures are clipped by their scroll viewport as expected.

Review screenshots:

- [Collection, 1280×720](../../../build/gallery-proof/stack-header-1280/screenshots/gallery_collection/29_gallery-collection-normal.png)
- [Lobby, 1280×720](../../../build/gallery-proof/stack-header-1280/screenshots/gallery_lobby/42_gallery-lobby-selected.png)
- [Large density, 1024×768](../../../build/gallery-proof/stack-header-1024/screenshots/gallery_collection/213_gallery-collection-large.png)

Independent subagent review found no actionable issues after inspecting the source diff and opening normal collection, Standard/Large stress, live resize, selected lobby and Settings captures from both green archives, plus the small-viewport rule popup (13 images). It confirmed consistent spacing and heading alignment, coherent header groups, readable controls and unchanged production scope. Human aesthetic approval and production migration remain separate checkpoints.

# Gallery production migration — verification

Started from clean `63168de` on `codex/ui-redesign` in the existing deck-editor worktree. The user approved the corrected Gallery native proofs and authorized production migration with functionality retained. This report records new production evidence; prior fixture results remain in their original reports.

Plan: [production migration](../plans/2026-10-17-gallery-production-migration.md). Visual authority: [Gallery guide](../specs/2026-10-17-gallery-style-guide.md), including later stacked deck sections, grouped lobby controls and shared field/dropdown inset refinements.

## Scope and boundaries

Collection, Home, lobby and live-duel presentation are migrated in scoped commits. Existing collection/server authority, preparation policy and engine message contracts remain unchanged. Multiplayer checks in `docs/testing/player-interaction-manual.md` remain carried forward and nonblocking. Native development-harness evidence is not installed-JAR multiplayer acceptance or human aesthetic approval.

## Results

Collection completed in `46d8f20`; Home/lobby and live duel remain in progress.

### Collection migration checks in progress

The first production-path Gallery run passed 3704/3704 checks (`build/gallery-production/task1-initial`). An isolated-world launch subsequently timed out before executing any scenarios while starting card data; retained as `task1-pre-back-isolated-hung`, not counted as product validation. A later focused run reproduced two obsolete visual expectations: My decks below Save and five saved rows visible in the compact list. The approved composition uses a single header and a smaller scrollable saved-list viewport; functional selection/reach checks are retained when those expectations are updated.

Independent review also caught a real routed-navigation regression in the proof: Back was under the hidden old toolbar. The production migration moves the conditional Back action into the compact header and checks its actual callback. Final results follow when complete.


### Collection acceptance — `46d8f20`

Final acceptance run `369224_6779959d` passed **3/3 scenarios and 3795/3795 checks** (31 captures), archive `build/gallery-production/task1-acceptance`. Command: `./gradlew.bat runClient -PskipNative -PldTest=gallery_collection,collection_controls,collection_playtest_scale2 -PldTestWindow=1280x720 -PldTestGuiScale=2`, with `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. The preceding frozen-source regression run also passed layout, overflow and search. All 151 controller XML IDs were preserved. Arrow assets now live under production `textures/gui/gallery`; the redundant collection proof XML and layout-injection factory were removed.

Independent code review approved the navigation correction and focused assertion updates. Independent visual review opened normal, active-decks, Standard/Large, scrolled, long-toolbar, import/export, long-saved-name, selected saved/filter rows and Back-enabled normal captures. It found no material layout or component regressions at 1280×720. Root also opened the Back-enabled normal capture. Final-source images in the acceptance archive retain the approved compact header, equal stacked grids, shared text inset and flat matching-width scrollbars. Fractional-size migration evidence follows the final combined run.

# Gallery production migration — verification

The approved Gallery design is now used by production Collection, Home, Duel lobby and live-duel screens on `codex/ui-redesign`. Implementation range: `63168de..0cc5f3f`. No server, engine or network protocol changes were made. No merge or push.

[Implementation plan](../plans/2026-10-17-gallery-production-migration.md) · [Gallery style guide](../specs/2026-10-17-gallery-style-guide.md)

## Changes

- Collection uses the approved compact toolbar and stacked Main/Extra/Side grids, preserving filters, quantities, Deck/Side routing, saved/active state, transfers and navigation. All 151 controller XML IDs were retained. Its proof now loads the production layout.
- Home uses featured artwork fading into the left menu. Lobby uses selectable player cards with real skin lookup and deterministic fallback, grouped header actions and bounded long-name content. Both retain their existing navigation and preparation behavior. Modal closing restores its initiating control.
- Live duel uses Gallery reading surfaces, controls, overlays and scrollbar chrome while retaining field topology, specialized board metrics and semantic state colors. Zone inspector and log can expand independently; Restore preserves reading position and intentional bottom-follow behavior.
- Shared field/dropdown insets remain centralized at 10 management / 7.5 duel units. Focus and disabled styles coexist correctly, including primary actions and selected lobby cards. Scrollbar assets now have production paths.

## Commits

| Commit | Scope |
|---|---|
| `46d8f20` | Production collection and shared assets |
| `b4369ea` | Migration plan and initial evidence |
| `f328ab0` | Production Home and lobby |
| `45fdab0` | Live-duel Gallery treatment and panel expansion |
| `0cc5f3f` | Final focus, scroll-intent and caption corrections |

The final correction prevents viewport clamping from being mistaken for a user scroll. Value callbacks capture input intent before LDLib updates layout; layout restoration preserves that intent. Tests cover high offsets, content changes during expansion, manual expanded scrolling, closed/suppressed panels and append-follow behavior. The correction also fixes focus-selector precedence and restores specialized prompt-caption metrics. One old log-color assertion was updated to Gallery gold while retaining a distinct-color check.

## Final verification

All commands used `GRADLE_USER_HOME=C:/Users/haxer/.gradle`, Java 21 and pinned LDLib2 2.2.39.a. Runtime source was frozen at `0cc5f3f`; each final archive's `run.json` records fresh results and no source changes.

| Archive under `build/gallery-production/` | Result |
|---|---|
| `final-build-fixed` | Build passed. 1113 JUnit tests: 1112 passed, zero failures/errors, one existing Windows symlink-privilege skip. |
| `final-ui-1280` | Combined run: 57/61 scenarios passed, three hover scenarios failed four checks, and collection live resize timed out. The native window was iconified; see explanation below. This run is retained as failed evidence. |
| `hover-rerun-1280` | Unchanged source: 3/3 scenarios, 4066/4066 checks passed. |
| `final-collection-1280` | Unchanged source: 1/1 scenario, 3716/3716 checks passed, including live resize. |
| `final-ui-1024` | 1024×768, effective GUI scale 2: 5/5 scenarios, 6569/6569 checks passed. |
| `final-ui-1920` | 1920×1080, effective GUI scale 4: 5/5 scenarios, 6569/6569 checks passed. |

The combined run and unchanged-source reruns provide passing executions for **all 58 regression scenarios and all three Gallery proofs: 61 unique scenarios, 17450 checks**. This is coverage across the recorded runs, not a claim that the failed combined run was green. The per-scenario source archive and count are recorded in `final-passing-coverage.json`.

The JUnit skip is `DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile`, which requires Windows symlink privilege. `-PskipNative` reused the existing verified Windows DLLs; it did not rebuild native sources. Dependency/native hashes are in `environment.json`. Built JAR: `build/libs/duelcraft-1.0.0-m6-qa.2.jar`, SHA-256 `017B43787FD6A363B00098056C4C6A2E53C667244ECCAA71C0B172F823FCD19F`.

### Minimized-window interruption

The failed combined report records native window size 0×0 while its retained framebuffer is 1280×720 and GUI scale remains 2. The resize timeout records a clean layout; the following lobby scenario explicitly records `iconified=1` before restoring the window. Iconification therefore explains the collection resize failure, not a production reflow defect.

LDLib's synthetic cursor conversion also uses native window dimensions. Zero dimensions can invalidate later render-hover ancestry even though immediate synthetic actions use correct GUI coordinates. That is consistent with the three delayed inspector-retention failures; the report does not timestamp the start of minimization, so the hover connection is strongly supported rather than proven. All affected scenarios passed unchanged in fresh clients. The original failure reports remain available.

Keep the native test window unminimized during harness runs. A future tooling improvement can restore/guard iconified windows consistently; the collection proof currently restores maximized windows, while the lobby helper also handles iconification. No speculative production hover fix was made.

### Commands

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'
./gradlew.bat build -PskipNative
./gradlew.bat runClient -PskipNative -PcollectionTransferUi -PldTest=group:duelcraft,gallery_specimen,gallery_collection,gallery_lobby -PldTestWatchdogSec=240 -PldTestWindow=1280x720 -PldTestGuiScale=2
./gradlew.bat runClient -PskipNative -PcollectionTransferUi -PldTest=duel_prompt_layout,duel_public_hand_hover,duel_toast_readability -PldTestWatchdogSec=240 -PldTestWindow=1280x720 -PldTestGuiScale=2
./gradlew.bat runClient -PskipNative -PcollectionTransferUi -PldTest=gallery_collection -PldTestWatchdogSec=240 -PldTestWindow=1280x720 -PldTestGuiScale=2
./gradlew.bat runClient -PskipNative -PcollectionTransferUi -PldTest=gallery_specimen,gallery_collection,gallery_lobby,duelcraft_home,duel_panel_layout -PldTestWatchdogSec=240 -PldTestWindow=1024x768 -PldTestGuiScale=2
./gradlew.bat runClient -PskipNative -PcollectionTransferUi -PldTest=gallery_specimen,gallery_collection,gallery_lobby,duelcraft_home,duel_panel_layout -PldTestWatchdogSec=240 -PldTestWindow=1920x1080 -PldTestGuiScale=4
```

## Independent review and retained evidence

Independent task reviews and a final whole-change review examined production routing, scope, behavior preservation and test coverage. Final scoped rereview approved all four correction findings with no new issue. Independent visual review opened 17 native captures covering focus, selection, pending/disabled state, default/hover/press treatment and expanded/restored panels; no blocking defect was found at 1280×720/2. Root also opened production Collection and before/after Home focus captures. Independent resolution review opened another 13 captures at 1024×768/2 and 16 at 1920×1080/4: 46 native images in total, with no blocking defect found in the reviewed scope. Live-resize captures at 1100×800 are identified separately. Root also opened the final 1024 collection and 1920 selected-lobby captures. Exact image paths and review limits are retained in `review-records/final-visual-review.md`.

Task archives preserve the earlier evidence: collection acceptance 3795/3795; Home/lobby 422/422; focused panel 1819/1819; final lobby/specimen 700/700. Failed attempts, including the red high-offset and focus checks, remain under `task1-*`, `task2-*`, `task3-*` and `final-fix-*`. Their results apply only to their recorded source revisions. The task reports, review reports, minimized-window diagnosis and decision ledger are retained under `review-records/`.

Implementation decisions:

1. Active-deck covers use the existing card back because snapshots expose summaries, not card contents. A custom cover can be added later if desired.
2. My decks keeps the approved compact scrolling viewport. The five-visible-entry requirement applies to dropdowns; all saved rows remain reachable. A different preference would require a later height adjustment.
3. The high-offset zone defect was carried into the final correction pass before acceptance, adding a targeted regression instead of leaving a known reading-position loss. Its cost was another scoped correction/test.

## Manual production playtest

- Open Home and Tab through Duel and Collection. Inspect the art fade, then open/close Help and confirm focus returns to its opener.
- In Collection, rename, save, activate and switch lists; exercise rich filters, Deck/Side routing, transfers and import/export. Check stacked grids and dropdowns at your usual GUI scale.
- Open Duel lobby, inspect Settings and select a player when one is available. Check Invite's focused, pending and disabled states.
- In a solo duel, open a long pile and the log. Scroll near the bottom, Expand/Restore, scroll while expanded and let new log entries arrive. Confirm retained positions and deliberate bottom-follow.
- Inspect prompts, card details, pause and results at your normal window size.

Human aesthetic approval of the migrated production screens remains separate from agent review. Live multiplayer/installed-JAR playtests remain on the existing [carried-forward checklist](../../testing/player-interaction-manual.md) and do not block this UI work. No multiplayer or human visual acceptance is inferred from the harness.

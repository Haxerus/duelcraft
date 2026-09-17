# Deck editor milestone 1 verification

Implementation workspace: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.worktrees/deck-editor-m1`.
Branch: `codex/deck-editor-m1`, based on `a957157`.

Tasks 3 and 4 share one screen/controller implementation and were committed together as `b8867d2` (`feat: add sample collection deck editor`), after the layout/search scenarios and review passed. Task 5 carries the overflow/regression scenario and this verification record.

The six uncommitted handoff documents and accepted HTML prototype were copied from the main checkout before implementation. The main checkout's modification to `docs/engine-gap-analysis.md` was not copied or edited. The editor uses injected sample metadata, ownership and a texture provider, with an in-memory save callback. It has no persistent collection, inventory transfer, activation or production entry point.

## Model and search

- Baseline `.\gradlew.bat test`: 593 tests passed, zero failures/errors/skips. Native libraries built in the isolated workspace with the initialized engine submodule and copied VS project files.
- Initial model/search RED: 91 tests, 90 failures against API shells; two further edge tests failed as expected. Logs: `build/model-search-red.log`, `build/model-search-red-edge.log`.
- Review identified a missing numeric-range check for the default Any measure. The accepted prototype applies number bounds to every monster measure. Added eight cases; the targeted measure run produced 21 tests with exactly five expected failures (`build/measure-any-red.log`).
- `.\gradlew.bat test --tests '*DeckListTest' --tests '*DeckEditorModelTest' --tests '*CardSearchTest'`: **101 passed**, zero failures/errors/skips (6 DeckList, 11 editor model, 84 search). Log: `build/model-search-green.log`.
- Independent model/search review passed after the Any-measure fix. Commits: `d96ef7c` (models), `3293a4d` (search).

## Real Minecraft screen checks

Every run uses the actual `CollectionScreen` and LDLib2 harness. Fixtures do not query a card database or image service. The sample screens run without a world where possible; the existing duel group retains its original world requirements.

- Layout RED at 1280x720: Minecraft reached the scenario and failed with `Collection layout is not implemented` before production XML existed. Report and screenshot retained under `build/collection-evidence/layout-red/`.
- Initial combined screen run: all three scenarios exposed a selector initialization exception before opening. Report retained under `build/collection-evidence/first/`. This is failure evidence, not a completed visual check.

- The first complete interaction run passed **247/247 checks** across all three scenarios. Review then added regressions for unapplied filters, unchanged-row edit scrolling, row growth/shrink, and resize retention.
- A stricter Large-density check exposed repeated layout recalculation: **84/85 checks passed**, with `Large density layout settles` failing. The pinned style engine showed the density button's adaptive text width oscillating between 87.99999 and 88.00001. Giving that text the already fixed button width resolved the loop. Diagnostic code was removed. RED report: `build/collection-evidence/style-diagnostic-red/`.
- After the layout-loop fix, the 1280x720 / requested scale 2 run passed **3/3 scenarios, 249/249 checks**, with no repeated layout warnings. The 16,000-card catalog comprises 4,000 virtual rows; **5 rows mounted** at both the start and end against a calculated bound of 7. The injected texture recorder stays bounded to mounted cards plus the inspector.
- Tests use actual transformed mouse input for card selection, editing, filter buttons and close actions. The pinned buttons act on press, so transient buttons retain the original pointer for release after hiding themselves. Scroll tests exercise wheel input and move the scrollbar to reach distant final rows; the pinned wheel handler uses direction rather than the supplied magnitude.
- Visual inspection of the 1280x720 Standard capture confirmed separated columns, fixed editing controls, readable placeholder names/passcodes, ownership counts and explicit sample labels. The 1100x800 Large capture is centered with letterboxing; its dirty draft, selected card and Main pixel offset survive the actual GLFW resize. Selector contrast and title/sample translation were corrected after earlier captures exposed the problems.
- Final review found collapsed Side lost its scroll after an unrelated Main edit because hidden Taffy geometry becomes zero. The new real-screen assertion failed at 960x540; evidence is in `build/collection-evidence/collapsed-side-red/`. Hidden grids now retain their existing scrollbar position rather than capturing/restoring zero layout offsets. The final matrix includes this regression. Two downstream RED assertions were caused by the regression fixture removing its original first duplicate; the test now adds/removes a code absent from Main, preserving the original order.

## Final viewport matrix

Every row ran `.\gradlew.bat runClient -PldTest=tag:collection -PldTestWindow=<framebuffer> -PldTestGuiScale=<requested>`. All **27 scenario runs / 2,250 checks passed**, with zero failures, errors, skips or repeated layout warnings. Each run captured ten screenshots. Scenario execution took 51–54 seconds per run; this is harness duration, not a frame-rate benchmark.

| Actual framebuffer | Requested scale | Effective scale | Actual GUI viewport | Checks |
| --- | --- | --- | --- | --- |
| 960x540 | 2 | 2 | 480x270 | 250/250 |
| 960x540 | 3 | 2 | 480x270 | 250/250 |
| 960x540 | 4 | 2 | 480x270 | 250/250 |
| 1280x720 | 2 | 2 | 640x360 | 250/250 |
| 1280x720 | 3 | 3 | 427x240 | 250/250 |
| 1280x720 | 4 | 3 | 427x240 | 250/250 |
| 1920x1080 | 2 | 2 | 960x540 | 250/250 |
| 1920x1080 | 3 | 3 | 640x360 | 250/250 |
| 1920x1080 | 4 | 4 | 480x270 | 250/250 |

The design canvas remains 1280x720 in all cases. Each overflow scenario also resizes a dirty editor to 1100x800 and back, verifying draft, inspector and Main scroll retention. Reports contain viewport attachments for those intermediate sizes.

Evidence is retained under `build/collection-evidence/final/<framebuffer>-scale<requested>/`; each directory contains `report.json`, `report.txt` and `screenshots/`. Logs are `build/collection-final-<framebuffer>-scale<requested>.log`. Machine-readable aggregate: `build/collection-evidence/matrix-summary.json`.

Representative inspected captures, relative to that case directory:

- `1280x720-scale2/screenshots/collection_layout/99_collection-standard.png`
- `960x540-scale2/screenshots/collection_layout/99_collection-standard.png`
- `1920x1080-scale2/screenshots/collection_overflow/74_collection-large-expanded.png`
- `1280x720-scale2/screenshots/collection_overflow/216_collection-dirty-resized.png`
- `1280x720-scale2/screenshots/collection_overflow/241_collection-catalog-end.png`

Human readability and comfort assessment remain a user playtest, separate from automated bounds and interaction assertions.

## Full regression checks

- Final `.\gradlew.bat test`: **694 passed, zero failures/errors/skips**, including native integration and the 101 new model/search cases. Build completed successfully in 38 seconds. Log: `build/collection-full-test.log`; aggregate: `build/collection-evidence/junit-summary.json`.
- Final `.\gradlew.bat runClient -PldTest=group:duelcraft`: **32/32 scenarios and 7,691/7,691 checks passed**, zero failures/errors/skips, with 121 captures. The harness took 179,475 ms; the Gradle run completed in 3m43s. This includes the existing real deck-upload scenario against the integrated server. Report/screenshots: `build/collection-evidence/duel-group/`; log: `build/collection-duel-group.log`.
- Final source review approved the implementation after the reproduced hidden-Side fix. Existing `DuelScreen`, `LDLibDuelScreen`, `duel_screen.xml`, `core.Deck`, and `CardInfo` match the main checkout byte-for-byte.

## Open the sample editor

From the implementation workspace:

```powershell
.\gradlew.bat runClient -PldTest=collection_layout -PldTestWindow=1280x720 -PldTestKeepOpen
```

The development-only scenario opens the real editor with 80 synthetic cards, Main40/Extra5/Side0, placeholder artwork and an ownership shortage. It exercises the screen, then leaves it open. Select a card to inspect it; choose Main/Extra/Side and use Add/Remove; use Filters and Apply; switch Standard/Large with the footer button. Save records a snapshot in memory only. Escape prompts for Save/Discard/Cancel when dirty. Restarting the sample resets its state.

The keep-open launch was performed at completion: **1/1 scenario and 87/87 checks passed**, and the runner logged that it was leaving the game running. Evidence: `build/collection-evidence/playtest/`; log: `build/collection-playtest.log`. Minecraft was left open for the user. The Gradle task remains running until the game closes; its live state is intentional.

At 960x540, the design canvas scales to 75% of its nominal framebuffer size and small text becomes noticeably dense. Large density enlarges deck cards; it does not enlarge every label. No comfortable minimum window size or performance target has been declared. The resize/scroll/click checks and inspected captures establish behavior and geometry; the user's in-game playtest decides comfort and readability.

All five milestone tasks are complete and no required automated checks are blocked. Milestone 2 is ready for its storage/network plan, but has not been implemented. The implementation branch/worktree are retained; nothing was merged or pushed. The main checkout's pre-existing `docs/engine-gap-analysis.md` modification remains untouched (SHA-256 `EE936ED651173B30F4E32CFD285C5FBC4FDB6D2517CB5DB81CA7EED211809581` before/after final verification).

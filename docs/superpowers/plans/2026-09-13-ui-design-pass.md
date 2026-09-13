# Duel UI visual audit implementation plan

**Goal:** Bring every implemented duel surface within the [design guide](../../ui-design-guide.md), with repeatable screenshots and layout regression checks.

**Architecture:** Keep the existing XML/LSS and controllers. Add shared test assertions and focused synthetic scenarios; fix layout at the shared component where the defect originates. No engine or network changes.

**Tech stack:** Minecraft 1.21.1, Java 21, NeoForge 21.1.224, LDLib2 2.2.39.a; native DLLs copied from the main checkout for UI-only builds.

## Execution

- [x] Create `codex/ui-design-pass` in `.worktrees/ui-design-pass`, initialize submodules and copy existing DLLs.
- [x] Inspect existing screenshots and document tokens, layout rules and an HTML component mockup.
- [x] Run baseline JUnit and 21 existing scenarios at 1280 × 720; save evidence.
- [x] Add `DuelUiAssertions` for dialog containment, title alignment, button text fit, sibling overlap and panel containment. Prove assertions fail against existing layouts.
- [x] Add prompt stress coverage with every prompt family listed below and screenshots before responses/after selections.
- [x] Fix XML/LSS and minimal controller layout decisions; repeat failing scenarios and inspect captures.
- [x] Exercise side panels, context menus, overlays and feedback. Add long content/large pile coverage and fix remaining defects.
- [x] Run complete harness at 1280 × 720 and 1920 × 1080, plus 1024 × 768; inspect captures. Run JUnit and review diff.

## Coverage matrix

| Surface | Existing scenario | Added coverage |
|---|---|---|
| MR3/MR5/Speed field, GUI scales 2/3/4 | `duel_mr*`, `duel_speed_scale3` | Window matrix, board/panel bounds |
| Idle, battle, phase/shuffle controls, context menu | `duel_idle_shuffle`, `duel_mr5_scale3_click` | Multiple actions, effect chooser, phase availability |
| Yes/no, effect yes/no | none | Long wrapping title; both action labels fit |
| Select option, effect disambiguation | none | Long descriptions, many rows, scroll |
| Announce number/race/attribute | number/race only | Many numbers, all 26 races, all seven attributes; selection feedback |
| Announce card | `duel_announce_card` | Long result names and scroll; fallback input |
| Select card | `duel_select_card_finish` | Dialog candidates, large list, cancel/finish |
| Select tribute | none | Field highlights, progress, action |
| Select unselect | none | Field and dialog variants, finishable/cancelable |
| Select sum | none | Field and dialog variants, mandatory choices, progress |
| Select chain | none | Optional/forced; effect chooser |
| Sort card/chain | `duel_sort_card` | Long caption, large candidates; ordinal labels |
| Select position | `duel_select_position` | Four orientations, caption alignment |
| Select place/disfield/counter | disfield/counter | Select place, progress/action geometry |
| Rock/paper/scissors | none | Three choices fit |
| Waiting/retry | `duel_waiting` | Prompt restored after retry |
| Pause/result | `duel_pause_menu`, `duel_result_overlay` | Centered titles, win/loss/draw, close/stay |
| Toast/banner/message, LP changes, chain state, log | `duel_feedback`, `duel_hints` | Modal bounds, long log, concurrent header feedback |
| Card info, pile/reveal inspector | board/card-model scenarios | Long metadata, CRLF text, large/empty piles, close; scroll during a selection prompt |
| Card stats, counters, targets, material badges, disabled zones, scales | `duel_card_model` | Retain badges and board geometry; no separate material inspector is implemented |

## Reproduction

In this worktree, set `JAVA_HOME` to `C:/Program Files/Eclipse Adoptium/jdk-21.0.1.12-hotspot`.

```powershell
./gradlew.bat test -x copyNative --console=plain
./gradlew.bat runClient '-PldTest=group:duelcraft' '-PldTestWindow=1280x720' -x copyNative --console=plain
```

`-x copyNative` reuses the DLLs already copied into resources; it does not skip Java compilation or UI assertions. The worktree's submodules are initialized so native header tests can run. Copy each `build/ldlib2-uitest` result into `build/ui-review/<run-name>` before the next run clears it.

For the final matrix, also pass `-PldTestGuiScale=2` at 1280 × 720, `=3` at 1024 × 768 and `=4` at 1920 × 1080. Existing board scenarios explicitly exercise their own GUI scales; the four new stress scenarios inherit this launch setting.

## Verification log

- Baseline: 567 JUnit tests passed, with no failures, errors or skips; 21 UI scenarios passed 187 checks at 1280 × 720. Saved under `build/ui-review/baseline-1280/`.
- New assertions first reproduced overflowing choice grids, cramped/wrongly aligned labels, a zero-width sort ordinal, insufficient HUD text height and overlapping feedback. Interaction probes also reproduced card details being hidden or blocked by the selection dimmer while trying to scroll.
- Fixed shared XML/LSS layout, bounded long-choice lists, compact actions, side-panel headers, feedback placement, card-text line endings and card-panel hover/layering. Existing response construction and engine behavior are unchanged.
- Four new scenarios cover dense prompts, field/context selections, side panels and synthetic card search. Existing scenarios now run the shared geometry audit. Scroll probes check that content actually moves and that the last choice remains selectable; retry restores the prompt.
- JUnit rerun after the production fixes: 567 tests passed, no failures, errors or skips. Initial complete UI rerun: 25/25 scenarios, 4,218 checks and 67 screenshots at 1280 × 720. All captures visually reviewed. Final matrix adds explicit multi-action context-menu coverage.

| Final window | Launch GUI scale | Scenarios | Checks | Captures |
|---|---:|---:|---:|---:|
| 1280 × 720 | 2 | 25/25 | 4,326/4,326 | 70 |
| 1920 × 1080 | 4 | 25/25 | 4,326/4,326 | 70 |
| 1024 × 768 | 3 | 25/25 | 4,326/4,326 | 70 |

Final reports and screenshots are in `build/ui-review/verified-<width>x<height>/`; `build/ui-review/index.html` indexes all 210 captures. These generated artifacts are local, gitignored evidence. All final captures were inspected with contact sheets and full-size detail checks. A follow-up code review found no remaining actionable issues after the two regression fixes above.

Limits: this validates implemented duel surfaces using synthetic harness interactions, not a live multiplayer match or future home/deck/collection screens. The HTML file is a component design reference; acceptance is based on the Minecraft captures. Native libraries were reused, not rebuilt. LDLib2 emits transient layout-dirty warnings during some updates; the final runs have no failed assertions.

## Follow-up: reveal spacing and Shuffle

User review identified two gaps in the original visual acceptance: fixed card gaps left spare width on the reveal panel's right edge, and offering Shuffle widened the center controls and shifted zones. Added regression checks reproduced both before the fixes.

- Reveal/pile scrollers now distribute spare width between cards. The 60-card scenario checks balanced outer insets, equal horizontal gaps and scrolling.
- Shuffle is anchored just outside the hand's lower-right corner, outside normal layout flow. Its scenario toggles availability in MR3, MR5 and Speed, checks unchanged field/hand/center/zone bounds, verifies the button fits the canvas beside the hand, and presses it to confirm it remains usable.
- The production change is limited to XML/LSS. The response handler is unchanged.
- Updated captures and reports are saved under `build/ui-review/followup-<width>x<height>/`; `build/ui-review/followup.html` shows the corrected surfaces. The original gallery links to this follow-up so its old captures are not mistaken for the latest layouts.
- Verification: full group at 1280 × 720 passed 25/25 scenarios and 4,376/4,376 checks (72 captures). Focused `duel_idle_shuffle,duel_panel_layout` runs at 1920 × 1080 and 1024 × 768 each passed 2/2 scenarios and 501/501 checks (13 captures each). Reviewed all 98 captures. Shuffle's scenario retains GUI scale 3; the panel scenario uses launch scales 2, 4 and 3 respectively. XML parsing and `git diff --check` also passed. Independent review found no production defect; its click-test finding was fixed using the existing press helper before these final runs.

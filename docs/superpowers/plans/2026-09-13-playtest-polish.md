# Playtest polish implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement each bounded task. Read the linked backlog before starting.

**Goal:** Fix the remaining Extra Deck identity issue and improve static duel readability.

**Architecture:** Keep authoritative state and recipient filtering unchanged. Fix shuffle invalidation in the client state model, then iterate on existing LDLib2 controllers and layouts with real screenshots.

**Tech Stack:** Java 21, NeoForge 21.1.224, LDLib2, JUnit 5, ygopro-core 7471af3c.

**Spec:** [Playtest issues](../../playtest-issues.md).

## Constraints and preservation

- Work on `codex/playtest-polish`, based on main `a957157` (which includes PR #4).
- The retired UI worktree is preserved in `.worktrees/_archives/ui-design-pass-2026-09-14/` outside this worktree. All 40,633 files were verified by SHA-256; the old branch remains available.
- Keep existing hand-reveal behavior and server privacy filtering.
- Animations, delays and presentation queues belong to a separate pass.

## First task: Extra Deck shuffle identity invalidation

Files: `ClientDuelState.java`, `ClientDuelStateTest.java`, `docs/engine-wiki/edopro-host.md`, `docs/playtest-issues.md`.

- [x] Verify a clean baseline with `./gradlew test assemble -x copyNative` using the main checkout's native DLLs.
- [x] Reproduce a revealed opponent Extra Deck card retaining its identity after `ShuffleExtra` and a sanitized query refresh. Include a face-up Pendulum card and an unaffected other player's pile.
- [x] Verify the new regressions fail against the ignored `ShuffleExtra` handler (both failed on retained codes).
- [x] Clear the shuffled pile's non-face-up identities and cached query stats. Preserve face-up cards. Mark the pile dirty so an open inspector redraws; the existing post-shuffle query restores permitted identities in their new order.
- [x] Verify owner refresh restores reordered identities, then run the complete test suite and assemble: 595 passed, zero skipped/failures/errors.
- [x] Document the invalidation/refresh contract and mark the backlog item complete.

## Static UI work order

Each item gets its own implementation detail and screenshot review before proceeding to the next. Use `docs/ui-design-guide.md` and the existing LDLib2 harness.

1. Selection captions: show candidate source zones, including mixed zones, and distinguish an iterative toggle from total material requirements. Verify single/mixed zones and partial legal material selections.
2. Zone inspector: highlight cards with available activation/summon actions. Verify selectable and inactive cards in sparse and full grids.
3. Card details: add Link markers and correct annotation backgrounds. Verify all marker directions and single/multiple-digit labels.
4. Xyz materials: display cards beneath the host, preserving hit targets and field bounds. Verify zero, one and several materials.
5. Duel log: phase context, available chain link numbers and card-name styling. Use only events actually exposed by the engine. Verify long entries, scrolling and mixed event types.

Run targeted LDLib2 scenarios after each visual change, inspect the generated screenshots, and run the full relevant harness before the next PR. Keep animation proposals in the separate backlog section.

## Execution evidence — 2026-09-14

- Resumed the existing `codex/playtest-polish` worktree and preserved its uncommitted Extra Deck fix. Fresh baseline `test --rerun -x copyNative`: 595 tests passed, zero failures/errors/skips. `test assemble -x copyNative` succeeded. The worktree uses the existing native DLLs; native source is unchanged.
- Baseline `runClient -x copyNative -PldTest=duel_card_model -PldTestWindow=1280x720`: 27/27 checks passed. The actual rendered screenshot confirms the narrow annotation backgrounds; preserved under `build/ui-review/baseline-1280x720/`.
- Selection-caption first pass: `runClient -x copyNative -PldTest=duel_selection_captions -PldTestWindow=1280x720` passed 313/313 checks. All eight captures were inspected and preserved under `build/ui-review/task1-1280x720/`. Visual review rejected the tiny fixed-height per-card source labels; larger, wrapping captions and a long opponent-zone case are required before acceptance.
- Selection captions accepted after correction: the same command passed 364/364 checks. All nine captures were inspected under `build/ui-review/task1-final-1280x720/`; the long ownership caption wraps beneath intact card art. Eight focused unit tests passed. Independent code review approved source ordering, click/response indices, privacy, iterative counts and Finish/Cancel behavior.
- Existing caption/privacy regressions: `runClient -x copyNative -PldTest=duel_prompt_layout,duel_selection_surfaces,duel_revealed_hand -PldTestWindow=1280x720` passed 3,869/3,869 checks. All 35 captures under `build/ui-review/caption-regressions-1280x720/` passed visual review.
- Zone inspector accepted: `runClient -x copyNative -PldTest=duel_zone_inspector_actions -PldTestWindow=1280x720` passed 54/54 checks after reproducing seven missing-highlight failures before implementation. All five captures under `build/ui-review/task2-1280x720/` were inspected; sparse/full grids and cleared actions are correct. Independent review approved the 11-line production change and regression coverage.
- Card details/annotations accepted: `runClient -x copyNative -PldTest=duel_card_model,duel_link_markers -PldTestWindow=1280x720` passed 150/150 checks, with all five captures inspected under `build/ui-review/task3-1280x720/`. The old backgrounds first failed eight rendered-text containment checks (preserved in `build/ui-review/task3-red-1280x720/`). All eight Link arrows, live overrides/zero values and unknown-card clearing were verified. Card string/database tests passed (32 focused tests). Independent review approved production behavior and recorded a minor test-fixture cleanup concern for final review.
- Xyz stacks accepted: `runClient -x copyNative -PldTest=duel_xyz_materials,duel_card_model -PldTestWindow=1280x720` passed 211/211 checks. All four captures under `build/ui-review/task4-1280x720/` were inspected. Actual material cards replace the count pip, retain unchanged hosts and slot bounds, and support defense/opponent/EMZ orientation, host edge-clicks, detach and transfer. Independent review approved. A subsequent harness-only RMB dismissal cleans the final refresh screenshot and will be exercised by the full matrix.

- Duel log accepted: `runClient -x copyNative -PldTest=duel_feedback -PldTestWindow=1280x720` passed 141/141 checks. All four captures under `build/ui-review/task5-1280x720/` were inspected. Real phase/damage-step context, engine chain numbers, semantic card-name colors, long-entry wrapping, scrolling and existing whole-line detail clicks passed. Focused log/state tests passed (77 tests). Independent Task 5 review passed.
- Final review found a remaining single-source caption gap in chain/sort dialogs. Three new assertions reproduced the gap (517/520 passed). Applying the existing source-summary helper to both titles fixed it; `duel_selection_captions,duel_sort_card,duel_prompt_layout` passed 3,521/3,521 checks. Evidence is under `build/ui-review/caption-gap-red-1280x720/` and `caption-gap-green-1280x720/`. Independent review approved both the correction and the complete implementation.
- The first full 1280×720 run passed 8,142/8,143 checks across 32/33 scenarios. Its only failure was the new Xyz screenshot-cleanup step: right-clicking the actionable host reopened its menu. The harness now clicks an empty zone through the existing dismissal path; production behavior and host-click assertions are unchanged. This failed run is preserved under `build/ui-review/pre-final-1280x720/`.

- Fresh final `test --rerun assemble -x copyNative` succeeded: **611 tests passed, zero failures/errors/skips**. The JAR was assembled from the corrected sources and existing native DLLs.
- Final full `group:duelcraft` runs passed at every required window size: **33/33 scenarios and 8,296/8,296 checks at 1280×720**, **33/33 and 8,296/8,296 at 1920×1080**, **33/33 and 8,299/8,299 at 1024×768**. Each run produced 136 screenshots; total 99 scenario runs, 24,891 checks and 408 captures. Archives are `build/ui-review/final-1280x720/`, `final-1920x1080/` and `final-1024x768/`. The representative layout scenarios cover MR3/Speed at configured GUI scale 3 and MR5 at configured scales 2–4 in each window.

- Visual review identified a retained native Chain-toggle tooltip in several 1024×768 prompt captures. An isolated `duel_prompt_layout` run passed 2,920/2,920 checks and produced 19 supplemental captures without the prior scenario's hover state, preserved in `build/ui-review/prompt-evidence-1024x768/`. No production or harness source changes were required. The longstanding long bottom-toast/open-sidebar overlap is recorded as a nonblocking baseline limitation.

- Final visual review passed all **408 matrix captures**, with each distinct PNG inspected and exact duplicate images accounted for by SHA-256 manifests. Each final archive contains `visual-review.md` and a complete path/hash manifest. Independent whole-change code review has no remaining findings.

**Final acceptance: passed.** The Extra Deck fix and all five static UI tasks are complete; tests, assembly, full UI matrix and screenshot review pass. Animations remain the explicitly separate backlog. Changes remain uncommitted in the existing worktree.

Commands use Java 21 (`C:/Program Files/Eclipse Adoptium/jdk-21.0.1.12-hotspot`) and `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. The screenshot gallery, reports, built JAR and repeat commands are in [the evidence index](../../../build/ui-review/README.md).

## User design feedback — 2026-09-14

Implemented the four requested revisions after the initial static-polish acceptance:

- Selection sources are larger gold titles above white detailed instructions, on field and dialog prompts. Mixed-source card attribution remains visible. A shared footer stack keeps simultaneous phase, chain and selection feedback separate from the board and Finish button.
- Effect questions substitute EDOPro's `%ls` card/location arguments, including the reported Dracotail trigger question. Default questions, plain descriptions, trigger help text and literal percent sequences are covered.
- Link markers occupy a 3×3 grid with an empty center, red active arrows and gray inactive arrows. Live query overrides and unknown/non-Link clearing remain intact.
- Sanitized log targets read “Face-down card” or “Unknown card” with plain styling and no hidden identity lookup or clickable code. Known names and detail links remain intact.

New regressions reproduced the old formatting, source layout, linear markers and zero-code log labels before implementation. Combined-state screenshots also exposed the footer collision and verified its correction. Harness fixes compare identical selected-card/button states and establish the active-window precondition that LDLib2's synthetic input intentionally omits; the explicit lost-focus guard regression remains intact.

Fresh `test --rerun assemble -x copyNative` passed **617 tests with zero failures/errors/skips** and assembled the JAR. UI acceptance: **34/34 scenarios, 9,427 checks at 1920×1080** (full group); **5/5, 2,121 at 1280×720** (revised features and controls); **9/9, 1,819 at 1024×768** (revised features and representative rule/scale layouts). Total: **48 scenario runs, 13,367 checks and 206 screenshots**. Production review passed with no findings. Changes remain uncommitted.

The [feedback evidence index](../../../build/ui-review/feedback-README.md) contains current screenshots, per-run reports/reviews, JUnit/build results and repeat commands. Earlier failed and historical runs are retained for traceability.

**Feedback acceptance: passed.** All 206 final captures passed visual review; complete path/hash manifests account for every screenshot, with exact duplicates mapped to inspected representatives. Tests, assembly, UI checks and final code review pass.

## Toast and standing-effect follow-up — 2026-09-14

User chose toast placement near the board's center and requested a translucent backdrop and wrapping for lingering effects. Implemented a padded, wrapped font-10 toast panel with pointer-through input and placement above open dialogs; existing queue and expiry are unchanged. Standing effects now use wrapped font-8 paragraphs and shaded owner-side blocks, with side panels moving below the text and restoring their space when it clears. Field and control geometry remain fixed.

New UI regressions reproduced the old placement, overflow, small text and missing backing. The first GREEN run passed 874 checks. Added real hit-testing, tall-dialog coverage and precise backdrop/padding checks; full 1920×1080 passed 36/36 scenarios and 10,494 checks. Final 1024×768 passed 9/9 and 1,763 checks. A supplemental 1280×720 toast run passed 1/1 and 1,062 checks with a corrected hover fixture that retains card details for the screenshot. Production stayed unchanged during these final runs.

Fresh `test --rerun assemble -x copyNative` passed **617 tests with no failures/errors/skips** and assembled the JAR. Total UI evidence: **50 scenario runs, 14,193 checks and 199 screenshots**. Final production review passed; the [current evidence gallery](../../../build/ui-review/hint-readability-README.md) contains screenshots, per-run reports and visual reviews. Changes remain uncommitted.

**Readability acceptance: passed.** All 199 captures passed visual review with complete path/hash manifests. The corrected small-window and supplemental captures explicitly show toasts with both card details and the right inspector open.

# QA Duel Visibility and UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display authorized card identities in the correct prompt, support simultaneous inspectors/log, and make banished positions and Xyz materials inspectable.

**Architecture:** Repair recipient-specific state and presentation using the existing sanitizer, card objects and prompt controller. Keep read-only inspection separate from engine responses, and reuse the existing zone inspector for manual pile/material inspection.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, ygopro-core API 11.0, Windows x64.

**Spec:** [Approved QA follow-up design](../specs/2026-09-19-m4-qa-follow-up-design.md). Execution/checkpoint owner: [QA index](2026-09-19-m4-qa-follow-up.md).

## Global Constraints

- Work in `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`, baseline `codex/qa-m4` at `1e95c277a1de7c8a014b77df1bd7df7744ee8365`; preserve subsequent changes.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Keep Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a and Windows x64.
- Preserve the dark duel UI and 1280x720 collection editor. No native submodule/dependency upgrade, M5/M6, merge or push. Protocol baseline 7 changes only for incompatible payload shapes.
- Preserve optional core ownership, permissive saved drafts, companion policy, revisions, busy checks and atomic transfers exactly as specified in the linked design.
- Never disclose unauthorized identities in packets, hover text or logs. A recipient-known code and permission to show that card face-up are different facts.
- Preserve historical evidence and leave blocked runtime checks open.

## Source baseline and file map

Java production paths below are relative to `src/main/java/com/haxerus/duelcraft/`; unit paths to `src/test/java/com/haxerus/duelcraft/`. This prefix convention applies to every file entry in this plan.

Read `docs/engine-wiki/README.md`, `source-versions.md`, `edopro-host.md`, `queries.md`, `responses.md` and the relevant `message-index.md` entries before changing integration contracts. Verify pinned sources: core `122e0d091a0f399221a4510cc98a406ae485905f`; local EDOPro `48ec006c49d7899b39f39420b9a71dea5ecdd22a` at `C:/Users/haxer/Documents/Programming/Modding/edopro`. Its path is not relative to the nested worktree. Reverify SHAs at execution time.

Observed leads at the planning SHA:

- `duel/MessageSanitizer.java` zeroes foreign candidates for several selection types. EDOPro likewise resolves these through recipient card state; removing that rule globally is not the proposed fix.
- `client/ClientDuelState.java` retains general `ConfirmCards` identities only for HAND, while top-of-pile reveals have separate handling. `candidateCode(CardInfo)` already resolves a zeroed candidate through `cardAt(controller, location, sequence, position)`.
- `client/LDLibDuelScreen.java` renders opponent hands as backs regardless of public query state. `applyQuery` in client state only treats certain identity/position changes as visual changes; public/hidden transitions also need inspection.
- `client/ZoneInspectorController.java#showConfirmCards` consumes the reveal list and opens the side panel. `PromptController` already renders selection candidates independently.
- `LDLibDuelScreen` hides left card details when the log is open. `ClickDispatcher` currently offers context actions only for actionable idle/battle cards.

| Files | Responsibility |
|---|---|
| Modify `duel/MessageSanitizer.java`, `RefreshSchedule.java` only where traced evidence requires it | Recipient disclosure and query timing |
| Modify `client/ClientDuelState.java`, `ClientCard.java`, `LDLibDuelScreen.java` | Track identities/public state, dirty refresh and hand rendering |
| Modify `client/PromptController.java`, `ZoneInspectorController.java` | Prompt-owned reveals, candidate artwork, pile/material inspection |
| Modify `client/ClickDispatcher.java`; `FieldRenderer.java` only for necessary click wiring | UI-only Materials action |
| Modify `src/main/resources/assets/duelcraft/ui/duel_screen.xml`, `src/main/resources/assets/duelcraft/lang/en_us.json` | Right column, markers, action captions |
| Extend unit tests `client/ClientDuelStateTest.java`, `duel/MessageSanitizerTest.java`, `duel/RefreshScheduleTest.java`, `core/OcgCoreTest.java` | State, recipient privacy, native/query regressions |
| Extend `client/uitest/DuelRevealedHandScenario.java`, `DuelPanelLayoutScenario.java`, `DuelXyzMaterialsScenario.java`, `DuelZoneInspectorActionsScenario.java`; create `client/uitest/DuelVisibilityQAScenario.java` | Real-screen evidence, registered new name `duel_visibility_qa` |
| Create `uitest/DuelVisibilityMPScenario.java` and `client/uitest/DuelVisibilityMPClient.java` | Real dedicated native/script scenario, registered name `duel_visibility_qa_mp` |
| Create `docs/superpowers/reports/2026-09-19-qa-visibility-trace.md`; update owning engine-wiki pages when their contract changes | Source-backed trace and verification boundaries |


## Task A3: Stack zone inspector and log on the right

**Interfaces:** Keep existing `#duel-log`, `#zone-inspector` and `#card-info-banner` selectors. Create `#duel-right-column` in duel XML. Add `private void refreshRightPanels()` in `LDLibDuelScreen`'s existing UI refresher; invoke it on either panel's visibility change and layout refresh. No wire or server changes.

- [x] Extend `DuelPanelLayoutScenario` for all four visibility states, at scales 2 and 3: neither; zone only; log only; both. Assert the right panels stay inside the column, share no area, and use its full height when alone. Show left card details while both are visible and click a log entry. Record independent scroll positions, toggle a sibling and assert preservation. Include active prompt, player hints and long contents.
- [x] Run `./gradlew.bat runClient -PldTest=duel_panel_layout`; archive failing bounds/visibility checks.
- [x] Move log XML into the right column with zone above/log below. Use the existing column width and margins; split the available height equally only when both display. The state table for `refreshRightPanels()` is:

  ```text
  zone=false, log=false -> column hidden
  zone=true,  log=false -> zone 100% available height
  zone=false, log=true  -> log 100% available height
  zone=true,  log=true  -> zone 50%, log 50% after the existing gap
  ```

  Remove all log/card-info mutual exclusion in `wireAuxiliaryPanels`, log click handlers and `showCardInfo`. Keep board/prompt geometry unchanged. Reflow player-hint offsets against the new column. Preserve scroll state on resize/rebuild; append-only log updates should stay at the bottom only if the user was already at the bottom. Do not clear the zone's inspected selection when toggling the log.
- [x] Rerun the scenario, inspect screenshots at both scales and check existing `DuelHandLayoutScenario`/rule-layout group coverage at I1. Expected: details, log and zone contents simultaneously usable, with no field/prompt overlap. Commit `fix: stack duel log and zone inspector on the right`.

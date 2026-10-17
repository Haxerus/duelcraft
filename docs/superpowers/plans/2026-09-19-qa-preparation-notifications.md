# QA Preparation Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Prevent repeated preparation prompts and prove that repeated invitations cannot create multiple simultaneous flows.

**Architecture:** Keep `DuelPreparationService` authoritative. Separate per-recipient chat announcements from state publication in `DuelManager`; deduplicate announcements by actionable stage and round, never by suppressing authoritative payloads.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, Windows x64.

**Spec:** [Approved QA follow-up design](../specs/2026-09-19-m4-qa-follow-up-design.md). Execution/checkpoint owner: [QA index](2026-09-19-m4-qa-follow-up.md).

## Global Constraints

- Work in `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`, baseline `codex/qa-m4` at `1e95c277a1de7c8a014b77df1bd7df7744ee8365`; preserve subsequent changes.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Keep Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a and Windows x64.
- Preserve the dark duel UI and 1280x720 editor. No M5/M6 implementation, native/dependency upgrade, merge or push. Protocol baseline 7 changes only for incompatible payload shapes.
- Core SERVER `requireCardOwnership` defaults false. Preserve deposited exact-passcode Main/Extra/Side ownership, legality and companion checks, draft saving, authenticated revisions, busy checks and atomic transfers. Deposits never auto-activate or create copies.
- Keep request acknowledgements, state revisions, role privacy, per-step timeouts and cleanup. Preserve historical evidence and leave blocked checks open.

## File map and source lead

Java paths use `src/main/java/com/haxerus/duelcraft/`; test paths use `src/test/java/com/haxerus/duelcraft/`.

- Modify `server/DuelManager.java`: announcement lifetime and publication.
- Modify `server/DuelPreparationService.java` only if a failing flow-cardinality test demonstrates an authority defect; do not create a second invitation registry.
- Create test `server/PreparationNotificationTest.java`; extend tests `server/DuelPreparationServiceTest.java` and `DuelStartPolicyTest.java` only for needed fixture capture/coverage.
- Create `uitest/PreparationNotificationScenario.java`, DEV_ONLY MP name `duel_preparation_notifications`, reusing `uitest/DuelPreparationCommandScenario.java`'s dedicated fixture pattern. Create `client/uitest/PreparationNotificationClient.java` only for client chat/state capture and keep it client-only.
- Update `docs/superpowers/reports/2026-09-19-m4-qa-follow-up-verification.md` during integration with exact outcome counts.

At the planning SHA, `DuelManager.publish` sends an RPS chat prompt whenever the recipient is in RPS and has not submitted a hand. Submitting the other player's hand republishes views for both players. This is a concrete duplication path to reproduce; it does not by itself prove multiple invitations exist.

## Task B1: Deduplicate announcements while preserving all state updates

**Interfaces:** Consume `PreparationView` fields `flowId`, `roundId`, `mode`, `outgoing`, `ownHandSubmitted`, `canChooseFirst`, and existing `Players.send`/`Players.message`. Produce private `DuelManager.shouldAnnounce(UUID owner, PreparationView view)` plus private nested `AnnouncementKey(UUID flowId, PreparationView.Mode mode, UUID roundId)` and a per-owner map. No new payload or public service API is needed for the observed notification defect.

- [x] Add a failing adapter-level regression using the existing fake-player fixture; it already captures messages per recipient and `ready()` executes invitation, acceptance and both hands:

  ```java
  @Test void opponentSubmittingDoesNotRepeatRpsPrompt() {
      var fixture = new DuelStartPolicyTest.Fixture(false);
      fixture.ready();
      for (var owner : List.of(fixture.a, fixture.b)) {
          assertEquals(1L, fixture.messages.get(owner).stream()
                  .filter(text -> text.startsWith("Rock-paper-scissors:"))
                  .count());
      }
  }
  ```

  Add assertions that both recipients still receive updated views after each hand, the waiting opponent never receives the other's hand value, and first-choice goes only to the winner. Extend the fixture's recipient-tagged packet capture rather than making production internals public for testing.
- [x] Add deterministic service/adapter cases for repeated A→B invitation, crossed B→A invitation, A→C while A→B is outstanding, repeated accept, repeated Hand with the same round ID, old-round Hand after a tie, replay after cancellation, expiry and disconnect, and a new invitation after cleanup. Assert at most one flow, one engine allocation for the completed start, and correct BUSY/STALE results. Use explicit timestamps; do not sleep. Assert exactly one invitation announcement, one RPS announcement per recipient per round, and one winner announcement per first-choice stage. A tie must produce exactly two total RPS announcements per recipient after the second round starts.
- [x] Run `./gradlew.bat test --tests '*PreparationNotificationTest' --tests '*DuelPreparationServiceTest' --tests '*DuelStartPolicyTest'`. Record which behavioral test fails. Existing passing cardinality tests establish that the report may be notification-only; retain them without speculative service rewrites.
- [x] Implement the private semantic announcement gate. Compute a key only for an incoming INVITED view, unsubmitted RPS view or permitted FIRST_CHOICE view. Exclude `revision` and `remainingMillis`: both can change without a new announcement. For an actionable view, the essential operation is:

  ```java
  var key = new AnnouncementKey(view.flowId(), view.mode(), view.roundId());
  return !key.equals(announcements.put(owner, key));
  ```

  Here `announcements` is the private `Map<UUID, AnnouncementKey>`. Determine actionability before this operation. Keep the last key through non-actionable updates in the same flow. Clear on terminal IDLE/DUEL, logout and server teardown, including offline paths that skip `publish`. The flow ID distinguishes a later challenge and the round ID distinguishes a tie. Do not introduce a timer-based debounce.
- [x] Leave `players.send(owner, new PreparationStatePayload(null, result, view))` unconditional for online recipients. Gate only the three normal chat announcement branches. Keep existing terminal error explanations and correlated payload-handler replies. If tests reveal actual duplicate flows, fix that invariant inside `DuelPreparationService` using its existing membership/state transitions; rerun replay/authentication tests before continuing.
- [x] Rerun the focused suites plus `./gradlew.bat test --tests '*PreparationPayloadTest' --tests '*DuelCommandPolicyTest'`. Expected: cardinality tests pass, state updates are preserved and no choice leaks. Old text-chat buttons currently resolve current flow/round through command handlers; do not falsely claim they are bound to historical IDs. Typed packet stale-ID rejection remains required. M5 UI replacement stays outside this task.
- [x] Build `duel_preparation_notifications` using real dedicated clients A/B. Count chat prompts by recipient at each stage and assert current flow/round IDs server-side; use a fresh per-run capture rather than matching historical chat. Drive repeated and crossed commands, accept, one hand, a tie, next round, first-choice, cancellation and disconnect/retry. Record actual two-client state and exactly one native start for a completed flow. Restore test fixtures and keep disconnect last or in its own run.
- [x] Run and preserve both reports before output is overwritten:

  ```powershell
  ./gradlew.bat runMpTest -PldMpTest=duel_preparation_notifications -PldMpOwnership=default
  ./gradlew.bat runMpTest -PldMpTest=duel_preparation_notifications -PldMpOwnership=required
  ```

  Expected: one prompt per actionable stage/round, all authoritative state delivered, one active flow/start, and locks released on cancel/disconnect. Required mode seeds deposited copies rather than bypassing eligibility. A fast scenario without counted assertions does not establish this result.
- [x] Review and commit only the task's source/tests as `fix: announce preparation choices once per round`. Record whether the defect was notification-only or included actual flow duplication, update the QA index and hand off to I1. Do not claim multiplayer acceptance if the dedicated run was blocked.

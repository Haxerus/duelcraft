# QA Duel Visibility and UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

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

## Task A1: Correct revealed identities and continuous public visibility

**Interfaces:** Consume existing `MessageSanitizer.forRecipient(DuelMessage, int)`, `ClientDuelState.applyMessage(DuelMessage)`, `cardAt(int,int,int,int)` and `candidateCode(DuelMessage.CardInfo)`. Produce corrected existing behavior plus `public int handDisplayCode(ClientCard card)` on `ClientDuelState`: return the recipient-permitted artwork/hover code for a hand card, or zero. This helper is presentation-specific; it must not become a global authorization rule for all locations.

- [x] Capture bounded producer-to-render traces for Painful Choice, an actual script that reveals/views an opponent Extra Deck, and Ceremonial Bell. Verify the exact selected Extra Deck fixture from local scripts/database and record its name, passcode, setup and script hash before writing the scenario. Compare native `libduel.cpp` ConfirmCards, `playerop.cpp` selection emission, `card.cpp` QUERY_IS_PUBLIC, EDOPro `generic_duel.cpp` recipient routing and `duelclient.cpp` reveal/selection handling. Record raw message type, recipient, location/sequence, sanitized code, query visibility flags and selected response index in the trace report. Raw test-only traces stay in local evidence, never ordinary multiplayer logs. Identify the first loss of authorized information in each reproduction.
- [x] Add a failing state test that reveals out-of-order opponent Extra Deck candidates and proves lookup uses the card location, not reveal order. This complete test body fits `ClientDuelStateTest` with its existing imports:

  ```java
  @Test void extraRevealResolvesCandidatesByLocation() {
      var state = new ClientDuelState(0, "Opponent", 8000, 8000, 40, 15, 0L);
      state.applyMessage(new DuelMessage.ConfirmCards(0, List.of(
              new DuelMessage.ConfirmCard(46986414, 1, LOCATION_EXTRA, 2),
              new DuelMessage.ConfirmCard(89631139, 1, LOCATION_EXTRA, 0))));
      assertEquals(89631139, state.candidateCode(new DuelMessage.CardInfo(
              0, 1, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE)));
      assertEquals(46986414, state.candidateCode(new DuelMessage.CardInfo(
              0, 1, LOCATION_EXTRA, 2, POS_FACEDOWN_DEFENSE)));
      assertEquals(0, state.candidateCode(new DuelMessage.CardInfo(
              0, 1, LOCATION_EXTRA, 1, POS_FACEDOWN_DEFENSE)));
  }
  ```

- [x] Add paired recipient tests from the traced messages: allowed reveal/Extra inspection versus unrelated hidden cards; public hand effect on/off; `QUERY_IS_HIDDEN` precedence; zero/missing QUERY_CODE; duplicate passcodes at different positions; movement/resequencing; deck/hand/Extra shuffles; disconnect/reset. Assert actual sanitized fields as well as client lookup. A query that merely omits a code must not erase legitimately remembered identity. Ending a public effect must still change opponent artwork/hover back to hidden even if the cached code remains nonzero.

  Use this concrete public-state regression alongside the paired packet tests (import `QueriedCard`):

  ```java
  @Test void endingPublicEffectHidesKnownOpponentHand() {
      var state = new ClientDuelState(0, "Opponent", 8000, 8000, 40, 15, 0L);
      var card = new ClientCard(89631139, 1, LOCATION_HAND, 0, POS_FACEDOWN_DEFENSE);
      state.hand[1].add(card);
      var visible = new QueriedCard();
      visible.flags = QUERY_IS_PUBLIC;
      visible.isPublic = true;
      state.applyMessage(new DuelMessage.UpdateCard(1, LOCATION_HAND, 0, visible));
      assertEquals(89631139, state.handDisplayCode(card));
      var hidden = new QueriedCard();
      hidden.flags = QUERY_IS_PUBLIC;
      hidden.isPublic = false;
      state.applyMessage(new DuelMessage.UpdateCard(1, LOCATION_HAND, 0, hidden));
      assertEquals(0, state.handDisplayCode(card));
      assertEquals(89631139, card.code);
  }
  ```
- [x] Run `./gradlew.bat test --tests '*ClientDuelStateTest' --tests '*MessageSanitizerTest' --tests '*RefreshScheduleTest'`. Record the failing behavioral assertions before implementation; do not count compilation errors or missing native dependencies as a reproduced bug.
- [x] Extend general reveal application to all source-supported card locations, resolving the actual card object before storing nonzero identity. Preserve existing specialized deck-top sequence semantics. Keep prompt resolution in `candidateCode`; add only the minimum context needed by the traced rules for identity association/invalidation. Do not introduce a global passcode cache. The core operation remains location-based:

  ```java
  ClientCard card = cardAt(info.controller(), info.location(), info.sequence(), 0);
  if (card != null && info.code() != 0) card.code = info.code();
  ```

  Here `info` is an existing `ConfirmCard`; overlay candidate addressing still uses its own position index through `candidateCode`, not this snippet. If the producer emits an untracked location, keep the presentation-only reveal scoped to that event rather than assigning it to a guessed slot. Any new wire metadata requires a documented producer-backed reason and protocol review.
- [x] Implement `handDisplayCode` using current recipient permission, ownership and authoritative public/hidden flags, separately from remembered identity. Have hand rendering and hover use the same returned code. Keep foreign gameplay actions unavailable. Mark HAND dirty when visibility flags change, including changes with unchanged code and position. Honor queried-field presence when applying incremental results. Adjust sanitizer/refresh schedule only when the trace demonstrates a disclosure/timing defect; document the precise source contract and keep negative privacy tests.
- [x] Run the focused suites again and add/run named native regressions in `OcgCoreTest` with real configured data. Expected: reproduced tests green, both recipients correct, no broader hidden fields exposed. Update the visibility trace and affected engine-wiki contract. Commit only A1 files as `fix: preserve authorized reveal and public hand state`.

## Task A2: Put reveals in the correct prompt and verify real effects

**Interfaces:** Consume A1's `candidateCode` and `handDisplayCode`. Produce `public void refreshReveals()` on `PromptController`, consuming the existing pending confirmation state when safe. Replace `ZoneInspectorController.showConfirmCards()` call sites; remove that method after all callers migrate. Existing selection response APIs and original indices remain unchanged. For multiple pending reveals, use a small ordered queue of immutable reveal records in client state only if the reproduced batching behavior requires it; do not overwrite an unconsumed event.

- [x] Extend `DuelRevealedHandScenario` and create DEV_ONLY `duel_visibility_qa` using actual pointer input. Cover hand/deck/Extra, reversed reveal order, duplicate identities, unknown candidates, same-tick reveal+selection and reveal-before-selection. Add read-only reveal with no subsequent selection, multiple reveal events, Retry, Waiting and dialog dismissal. Assert zone inspector stays closed and that a previously manually opened inspector keeps its contents. The existing scenario-builder pattern is:

  ```java
  s.step("authorized reveal", ctx -> LDLibDuelScreen.applyMessage(
          new DuelMessage.ConfirmCards(0, List.of(
                  new DuelMessage.ConfirmCard(89631139, 1, LOCATION_EXTRA, 0)))))
   .step("zeroed foreign candidate", ctx -> LDLibDuelScreen.applyMessage(
          new DuelMessage.SelectCard(0, false, 1, 1, List.of(
                  new DuelMessage.CardInfo(0, 1, LOCATION_EXTRA, 0, POS_FACEDOWN_DEFENSE)))))
   .ticks(3).checkVisible("#prompt-overlay").checkHidden("#zone-inspector");
  ```

  Supply initialized Extra Deck card objects through the existing `DuelScreenFixture` before these steps. Assert candidate artwork identity and hover, not just panel visibility. Click a non-first candidate and capture response bytes to prove the original selection index is used; inspect `ResponseBuilder`/`ResponseValidator` for the exact encoding instead of assuming a byte width.
- [x] Run `./gradlew.bat runClient -PldTest=duel_visibility_qa` before changing presentation and archive the expected failures. Keep fixture art independent of a downloaded image/database, using deterministic textures and explicit candidate identity assertions.
- [x] Route confirmation refresh through `PromptController.refreshReveals`. When a mandatory selection consumes the relevant reveal, render its choices directly and dismiss the associated read-only reveal without sending a response. Otherwise present a prompt-owned read-only reveal with an explicit Close/Continue acknowledgement local to the UI. Queue unrelated inspection reveals behind an unanswered mandatory choice; do not replace its selected indices, min/max, cancelability or response gate. Coalesce the current received-message batch before rebuilding surfaces. Do not delay the engine waiting for a fabricated confirmation response.
- [x] Audit card/unselect/tribute/sum/sort candidate display paths for direct code-only rendering. Where source contracts require recipient-state lookup, route through the same location-aware resolver without changing response order. Bind artwork and hover to one resolved code. Keep unknowns anonymous. Run existing selection and response tests after these edits:

  ```powershell
  ./gradlew.bat test --tests '*ClientDuelStateTest' --tests '*ResponseBuilderTest' --tests '*ResponseValidatorTest' --tests '*DuelMessageCodecTest'
  ./gradlew.bat runClient -PldTest=duel_visibility_qa
  ./gradlew.bat runClient -PldTest=duel_revealed_hand
  ```

- [x] Implement DEV_ONLY `duel_visibility_qa_mp` with separate A/B clients and the real manager/native session. Use the existing dedicated fixture pattern, restore collection/inventory fixtures on teardown, and keep client-only helpers in `client/uitest`. Reproduce all three traced effects from A1 using real scripts. Capture both recipients' received sanitized records and actual prompt artwork/response; assert the native continuation resolves the chosen candidate. Ceremonial Bell must show/remove public hand visibility on both clients, with an actual shuffle/move negative case. An injected `ConfirmCards` UI scenario alone does not close this step.
- [x] Run and archive:

  ```powershell
  ./gradlew.bat runMpTest -PldMpTest=duel_visibility_qa_mp -PldMpOwnership=default
  ./gradlew.bat runMpTest -PldMpTest=duel_visibility_qa_mp -PldMpOwnership=required
  ```

  Expected: all checks pass with recipient-specific identities and correct engine choices; ownership fixtures provide real deposited copies in required mode. If native setup or the exact Extra Deck effect cannot be reproduced, leave that acceptance item open and record the trace/setup limitation. Commit `fix: present revealed choices in selection prompts` with evidence references.

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

## Task A4: Expose face-down banish status and Xyz materials

**Interfaces:** Add `public void openMaterials(ClientCard host)` to `ZoneInspectorController`. Add `void inspectMaterials(ClientCard host)` to `ClickDispatcher.Callbacks`, wired by `LDLibDuelScreen` to that method. This is a UI-only action, not an `IdleAction`/`BattleAction`, and it must never enter `ResponseBuilder`.

- [x] Extend `DuelZoneInspectorActionsScenario` and `DuelXyzMaterialsScenario`: own known face-down banished card; opponent unknown face-down card; public face-up card; both turn players; zero/one/multiple/unknown materials; own/opponent host; no actionable engine menu; a host with summon/activate actions; detach/reorder/control-change/host departure; mandatory field selection. Assert `Face-down` marker separately from art. Assert material order, hover privacy and zero response sends when opening/closing the material inspector.
- [x] Run `./gradlew.bat runClient -PldTest=duel_xyz_materials` and `./gradlew.bat runClient -PldTest=duel_zone_inspector_actions`; capture failures for absent action/badges.
- [x] Add a text/badge overlay using `ClientCard.isFaceDown()` for banished tiles independently of resolved identity. Use a localized caption and preserve unknown backs/anonymous hover. Do not reveal a code because the viewer is the turn player.
- [x] Add the Materials (N) action to the context menu when `!host.materials.isEmpty()`. Keep engine choices' original action indices. A mandatory selection click routes to selection first; read-only inspection is available in normal browsing/waiting and existing explicit inspect mode without clearing that prompt. The action body is deliberately separate:

  ```java
  callbacks.inspectMaterials(host);
  // No sendResponse call and no native action identifier.
  ```

  Wire `openMaterials` to the actual host object and its `materials` list in engine order, refresh when materials change and clear if that host no longer occupies a field slot. Respect the A3 split layout. Update the existing `.ctx-action` count assertion in `DuelXyzMaterialsScenario` for the additional action rather than deleting its original host-click coverage.
- [x] Rerun both scenarios and `./gradlew.bat test --tests '*ClientDuelStateTest' --tests '*ResponseValidatorTest'`. Inspect menu bounds and badge readability at scales 2/3. Commit `feat: inspect Xyz materials and mark face-down banished cards`.

## Track completion

- [ ] All four tasks passed their focused regressions and review gates; update the index.
- [ ] Trace report names the tested effects, engine/EDOPro/script versions, source decisions, both-recipient results and any remaining runtime blockers.
- [ ] Pass completed interfaces to integration I1; do not start M5 or claim packaged QA acceptance here.

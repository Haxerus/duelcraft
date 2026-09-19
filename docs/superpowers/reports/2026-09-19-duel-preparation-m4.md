# M4 collection-backed duel preparation verification

Status: complete, verified and independently reviewed on `codex/player-collections` in the existing `.superpowers/worktrees/deck-editor` worktree. Started from clean `0fbe8c8`; user accepted all M3 manual playtests with no issues and authorized M4 with subagent-driven development. M5/M6 remain later checkpoints. No merge or push.

## Completed task 1: YDK imports

Commits: `55e6da2 feat: import complete YDK deck lists`, `f6862dc fix: handle empty local deck picker`.

Side is retained by `DeckListLoader`; `DeckLoader` still projects Main/Extra for the engine. Positive unknown IDs and incomplete lists can import, bounded to 512 total cards. Local selection enumerates the client deck directory and checks real-path containment. Every import creates a new saved UUID and grants no cards. UI Import saves a draft; `/duel deck set` saves then attempts activation only after the matching Save acknowledgement and returned revision. Rejected activation retains the saved list.

Verification (all Gradle commands use `GRADLE_USER_HOME=C:/Users/haxer/.gradle` and this worktree):

| Check | Result | Local evidence |
|---|---|---|
| Full suite after initial import implementation | 944 tests / 60 suites, no failures/errors/skips | `build/evidence/collection-m4/task1/full-test-xml/` |
| Focused parser/import/registry/controller suite after fix | 53 tests / 5 suites, no failures/errors/skips | `build/evidence/collection-m4/task1-fix/focused-tests.log`, `focused-test-xml/` |
| Actual 1280x720 GUI-scale-2 editor and Import picker | 1 scenario, 44/44 checks, 123 steps, 7 captures | `build/evidence/collection-m4/task1-ui/fixed-final-report/` |
| Independent task review and scoped fix review | Both findings addressed; no remaining findings | Controller SDD ledger; code committed above |

The UI scenario opens Saved lists with an empty local directory, then writes a controlled YDK fixture, clicks its actual selector row, imports it and checks Main/Extra/Side plus acknowledged saved state. It restores the original draft and removes the fixture. Final screenshots were visually inspected: dark styling, translated import label and readable layout are retained.

Failed evidence is preserved: the original UI run crashed on a null selector preview with an empty folder (`task1-ui/failed-report/`); a first fixed run used a text assertion LDLib2 could not introspect (`task1-fix/ui-text-assertion/`); screenshot inspection found a literal translation key, then an explicit text assertion reproduced that failure (`task1-fix/ui-label-red.log`). The final corrected run passed. A transient zero-size Minecraft window stopped one intervening launch before the scenario; that attempt is not counted as passing verification.

Task 1 left the old upload server payload until the Tasks 3/4 cutover; the final M4 code removes it.

## Completed task 2: preparation state machine

Commit: `279245d feat: model authoritative duel preparation`. Independent review approved spec compliance and code quality, with one deferred minor about expected failure-test log noise.

`DuelPreparationService` keeps one shared flow per pair, permits edits while invited, snapshots current eligible lists on acceptance, and locks both players through RPS, first choice and STARTING. Recipient views omit opponent choices/deck contents. Fresh round IDs on ties, exact 60-second step expiry and actor/flow checks reject delayed actions. Challenger/accepter shuffle seeds retain their identities when seat order changes. Immediate synchronous completion counts as a successful start.

Focused tests: 40 passed. Full suite: 984 tests across 61 suites, zero failures/errors/skips; actual redirected logs and XML are in `build/evidence/collection-m4/task2/`. The controller independently counted the archived full-suite XML. An explicit reentrant-expiry test reproduced duplicate finish notifications before the identity guard fixed it. No native/runtime behavior is claimed from these pure tests.

## Completed tasks 3/4: live authority and protocol

The migrated `duel_deck_import` real integrated-client scenario passes 10/10 checks (1/1 scenario). It exercises registered local list/set/get/clear commands, Save then Activate over the real collection protocol, known Main/Extra/Side cards with no deposited ownership, and source-file deletion without changing the saved snapshot. Evidence: `build/evidence/collection-m4/integration/import-runtime.log` and `import-runtime-report/`. Combined integration commit: `d015de4`. The dedicated command retry passed 7/7 checks (52 steps, two client captures), and the full suite passed 1,003 tests in 64 suites with no failures/errors/skips. Evidence: `integration/command-mp-default-report/`, `command-mp-default-retry.log`, `full-suite.log`, and `full-suite-xml/`. Independent integration review and scoped fix review are complete; the draft-preservation finding below is addressed. The original dedicated run failed before native startup because the S-to-C preparation-status registration was missing; its report/logs are retained under `command-mp-default-first-report/` and `command-mp-default.log`. The corrected retry exercises actual native startup, first-turn selection and forfeit.
The controller also ran the real 1920x1080 GUI-scale-3 editor regression: three scenarios, 165/165 checks, 479 steps and 18 captures. Evidence: `build/evidence/collection-m4/editor-regression/scale3-report/` and `scale3.log`. Visual inspection of the Import dialog, sorted deck and optional companion denial confirms the approved dark layout and centered feedback remain intact. Sample-data editor scenarios do not prove live transfer behavior.

## Task 5 runtime verification

Commit: `cc71703 test: close collection duel preparation races`.

The integrated `collection_duel_policy` scenario passes **17/17 checks, 61 steps, two captures**. It uses the registered activation and solo commands with a legal list and empty deposited counts under default ownership. Actual Save/deposit packets return BUSY while preserving the exact attachment and fixture slot-0 count; the same operations succeed after release. Forfeit/result close restores the original dirty editor name, UUID, cards and controller. A one-shot wrapper delegates real native setup, then throws; the actual close path runs once, START_FAILED removes the duel screen without a result overlay, restores the draft and preserves selection, and a subsequent native retry succeeds. The controller parsed the actual report and inspected the recovery capture. Evidence: `build/evidence/collection-m4/task5/solo/`. This exercises JNI destruction, not independent native allocation/leak measurement.

Final dedicated policy runs passed **74/74 checks in each ownership mode** (`task5/mp-final-default/`, `task5/mp-final-required/`). They cover solo/multiplayer policy with no hook, approval, denial and failure; changed progression after invitation/acceptance; exact Side ownership; RPS/FIRST_CHOICE/STARTING/live mutation locks; native failure/retry; and actual disconnect cleanup. The policy matrix invokes the production manager/preparation service on the real dedicated server with an installed event listener and real native sessions. Mutation/disconnect checks use actual game connections except synchronous STARTING probes, which call the authenticated handler while native startup holds the server thread. Successful command journeys are verified separately; each denying combination is not a separate client-command send.

Two initial dedicated attempts produced zero checks and missing participant reports. A client-only screen reference prevented the scenario from loading on the dedicated server; the corrected helper isolates client classes. Failed evidence remains in `task5/mp-default-attempt1/` and `mp-default-attempt2/`, separate from intermediate 55/55 and 65/65 passes and the final runs. A resource warning occurred under observed memory pressure. Explicit MP-only limits of 1536 MiB and two processors were verified on all three JVMs (`task5/mp-runtime-vm-flags.txt`); an earlier environment-variable attempt did not reach child JVMs. The disconnecting scenario has its own group so normal multiplayer group runs remain connected. The controller also inspected the required-mode native-retry capture: actual Main Phase 1, populated hand, deck/Extra counts and duel controls render after recovery.
The separate retained M4 lifecycle passed **37 checks** across five JVMs: default 5, ownership 7, throwing hook 10, denial 7, removal 8. Authenticated UUID `a6d86665-4bbd-3a31-9ffc-f8bc009cb7ed` remained the same; game PIDs were 14916, 12332, 29488, 12768 and 2592. Default revision10 had empty counts and an active legal list; ownership revalidation cleared it at login to revision11, then an explicit owned fixture established revision12. Throwing evaluations at login/use/Open retained revision12 and activation. Denial cleared activation once to revision13; removal retained revision13 inactive. Exact saved contents, including an unknown saved list, survived. Evidence: `task5/lifecycle/*-attempt1/` and the separate `run-collection-m4-lifecycle/saves/collection_m4_login_20260919` world. Test restrictions were installed on the actual event bus before login; no runtime addon hot-swap is claimed.

Both historical M2 manifest/level.dat hashes and the captured M3 hashes still match after these runs. Evidence: `task5/m2-world-preservation.json` (controller check against the committed M3 report) and `task5/historical-world-preservation.json`. No old retained world was opened.
Final migrated M3 transfer isolation plus dedicated command runs passed **42/42 checks under each ownership setting**, two scenarios and 162 steps per run (`task5/migration-default/`, `migration-required/`). The final unit/JNI suite executed successfully: **1,007 tests across 64 suites, zero failures/errors/skips** (`task5/full-suite/gradle.log`, `xml/`). The controller independently counted the XML. Four new deterministic regressions passed existing correct behavior immediately; no failing-first run is claimed for those additions.

Task 5's final runtime runs total **286 checks across 12 scenario executions**: solo 17, dedicated policy 74 + 74, migrated checks 42 + 42, and lifecycle 37. Earlier intermediate/failing attempts are excluded.

## Review completion and evidence limits

All task reviews and the final whole-M4 review passed with no remaining Critical/Important findings. Final review covered `0fbe8c8..12cf9e3` and independently verified the final unit/runtime totals. Subsequent edits record this verdict and clarify evidence; they do not change source behavior. Review records and the execution ledger are archived locally under `build/evidence/collection-m4/reviews/`.

Two nonblocking observations remain: the synchronous STARTING probe checks collection data but not physical inventory, and intentional fault diagnostics make logs noisy. BUSY rejection occurs before command dispatch or inventory access. Other multiplayer busy phases compare all 41 slots; the solo probe checks the attachment and fixture slot-0 count. The decision is to retain these as test-quality follow-ups: the costs are a narrower STARTING regression assertion and less readable logs, not a known production mutation defect.

The local dedicated harness uses offline-mode identities. It verifies connection-derived isolation, not online-account authentication or a new multi-machine LAN/tunnel test. Older-version rejection is simulated against registered channels; native cleanup is exercised without independent leak measurement. These are explicit evidence limits, not claimed passes. Manual M4 user acceptance remains pending. M5/M6 have not started.

Earlier M2/M3 evidence and retained worlds remain separate. M3 retained-world hashes captured before this milestone are in `build/evidence/collection-m4/m3-world-baseline-hashes.json`.

## Implementation decisions

Task 2 adds a small duelEnded(UUID actor) notification method because the planned interface had no way to revise/publish DUEL-to-IDLE after live completion. It changes no locks itself; the manager calls it after removing live ownership. If this interface choice changes, only the service/adapter notification needs adjustment. Synchronous completion during STARTING must still retain the preparation lock until startup returns.

Tasks 3/4 are implemented and reviewed as one integration block: Task3 needs Task4's client failure-status path, while the authority cutover invalidates old upload APIs/tests. The combined block bumps protocol 6 to 7 once. This sequencing decision increases the review surface but avoids temporary compatibility code; if changed, commit/task boundaries need adjustment, not product scope.

The lifecycle API also exposes startFinished(UUID, boolean) for the final solo status after releasing its startup guard and clear() for shutdown. A package-private, production-guarded one-shot session decorator permits DEV tests to fail after real native allocation while preserving production startup/cleanup. The factory stays fixed and the decorator is consumed before creation. If this test boundary needs revision, the cost is limited service/fixture changes.


## Manual playtest checklist (pending M4 acceptance)

Use matching client/server builds (protocol 7) and keep existing M2/M3 test worlds separate. The default core SERVER setting is requireCardOwnership=false; restart after changing it.

1. Import a disposable copy of a local YDK containing Main, Extra and Side through Saved lists. Confirm all sections survive, the result is a saved draft and no owned copies appear. Use `/duel deck set "A spaced name"`; wait for save/activation feedback. Remove or edit the local source, reconnect, and confirm the saved server list is unchanged.
2. With a legal active list and no deposited copies under default ownership, run `/duel test`. End the duel and confirm the active saved selection persists. Repeat a two-player `/duel challenge <player>` -> `/duel accept` -> `/duel hand rock|paper|scissors` -> winner `/duel first yes|no` journey; ties should offer a new round.
3. Invite another player, then open the collection editor and make unsaved edits. Have the other player accept while your editor is open. Confirm deck edits and transfers are blocked after acceptance. Cancel through `/duel invite cancel` or `/duel forfeit` before startup; confirm the draft returns and controls work. Decline an incoming invitation and confirm both players can prepare again.
4. Disconnect during an invitation, RPS and a live duel; confirm the remaining player is released and can begin another flow. After a live surrender/end, reopen the collection and confirm the saved list remains active.
5. Restart a separate test world with ownership enabled. An unowned active list should become inactive while the list stays saved. Depositing exact passcodes across Main/Extra/Side should permit explicit activation; matching cards still held in inventory should not count. Deposits alone must not activate the deck.
6. With a companion restriction installed, confirm denial applies with ownership both disabled and enabled. Change companion eligibility between invitation and acceptance, and between acceptance and first-turn choice: startup must recheck and release both players on failure. A failing hook should show a generic actionable error while retaining recoverable collection data.
7. Run the DEV startup-failure fixture with `./gradlew.bat runClient -PldTest=collection_duel_policy -PcollectionTransferUi -PldTestWindow=1280x720 -PldTestGuiScale=2 -PldTestWatchdogSec=240`. After Start followed by failure, confirm no victory overlay or stuck duel screen remains, collection controls recover, and a subsequent ordinary duel starts.

## Review observations

Integration review found one blocking draft-preservation defect: the collection command could open a second editor while a dirty one was suspended, allowing a later preparation update to dispose the original. Fix `2175879` blocks management opening during preparation/live and invalidates pending opens across those transitions. The real-screen regression passes 7/7 checks (38 steps), with 67 focused unit tests also passing. It uses simulated preparation pushes, real commands/editor and delayed real collection replies, and preserves name, ID, cards and controller identity. The controller inspected the final retained-draft capture. Evidence: `integration/review-fix/green-final-runtime-report/`, `green-final-runtime.log`, `scoped-unit-xml/`; original failing run retained. Scoped re-review approved the fix with no new Critical/Important findings.

Passing fault-injection tests deliberately log hook/start/close errors. Runtime logs also contain existing fixture/environment diagnostics (world-generator properties, missing example assets/scripts, shader warnings, and post-PASS harness process messages). Reports show child processes exiting 0 and successful scenario/test counts; these logs are retained and are not described as pristine. Simulated older-version channel negotiation is tested against the registered protocol; no actual older client handshake or independent native leak measurement is claimed. The original requested old-upload test selection was invoked while combined integration APIs were intentionally incomplete, producing compile-red evidence; its earlier baseline was green, and replacement import tests establish current behavior.

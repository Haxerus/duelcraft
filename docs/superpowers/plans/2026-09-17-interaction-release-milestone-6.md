# Interaction Release Validation Implementation Plan — Milestone 6

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to complete this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Verify the complete collection/deck/lobby experience on the release build, fix demonstrated defects, and document its operation and limits.

**Architecture:** Exercise the existing pure/service tests, real LDLib2 UI scenarios, and a separate two-client dedicated-server playtest against the same artifact. Preserve evidence per run and tie each correction to a failing test or repeatable reproduction. This milestone adds no new product subsystem.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, Gradle, JUnit 5, LDLib2 harness, Windows x64 native build.

**Spec:** [Design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: completed [milestone 5](2026-09-17-player-entry-points-milestone-5.md), including all prior milestones.

## Global constraints

- Use the term **collection**.
- Determine final readability, density, and minimum comfortable window size through in-game playtesting.
- Support the hotkey, binder, and mat through the same server policies and player data.
- No unrequested CI/platform overhaul, banlists, progression economy, public matchmaking, or native engine upgrade.
- Release claims require actual results. A skipped check remains skipped; a blocked dedicated-server test cannot be reported as passing because integrated singleplayer passed.

Paths are repository-relative in this document. Keep existing user/other-session modifications separate from this release work.

## Task 1: Establish release candidate and test prerequisites

**Create:** `docs/testing/player-interaction-release.md` as an execution report, populated with observed facts during this task. **Read:** `AGENTS.md`, all six milestone plans, `build.gradle`, `gradle/ldlib2-uitest.gradle`, native build instructions.

**Interfaces:** report contains candidate commit/branch, dirty-diff inventory, native submodule SHA, mod/library/JDK versions, native DLL provenance, commands with exit status/time, installed JAR SHA256, actual test environment, links to screenshots, and unresolved checks. The report is evidence, not a template claiming future success.

- [ ] Inspect `git status --short`, `git log -1`, and `git -C native/ygopro-core rev-parse HEAD`. Record the actual implementation commit and any uncommitted differences; do not call HEAD alone the tested artifact when code is dirty.
- [ ] Check Java21, native DLLs, managed card database/scripts, and the test EDOPro paths configured in build.gradle. Resolve normal build prerequisites without broadening the release into an unrelated native/CI refactor. Do not assume a `skipNative` property exists.
- [ ] Run the full build and retain its output:

```powershell
./gradlew.bat build
Get-ChildItem -LiteralPath build/libs -Filter '*.jar' | Select-Object Name,Length
```

Record and hash the built runtime JAR. Exclude sources/javadoc artifacts and stop if the remaining choice is ambiguous:

```powershell
$releaseJars = @(Get-ChildItem -LiteralPath build/libs -Filter '*.jar' | Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' })
if ($releaseJars.Count -ne 1) { throw 'Identify the intended runtime JAR before testing.' }
Get-FileHash -Algorithm SHA256 -LiteralPath $releaseJars[0].FullName
```
- [ ] Use the same candidate JAR on both clients and dedicated server. Record the effective server setting and test hook installation per run; test both ownership settings without a companion and a companion restriction under both settings. Restart between configuration/addon changes. Check protocol version is the implemented compatible version (planned4); verify an older version fails with a clear incompatibility message. Do not distribute/publish the artifact as part of verification.
- [ ] Record failures as blockers, fix within the owning milestone, and rerun the affected checks. Commit the populated initial report with `docs: record interaction release candidate` when it describes a real candidate, not empty checkboxes presented as evidence.

## Task 2: Automated correctness and UI matrix

**Modify as needed:** tests/scenarios created in milestones 1–5; only production files implicated by reproductions. **Artifacts:** `build/ldlib2-uitest/`, copies in `build/interaction-release/<run-name>/`; update `docs/testing/player-interaction-release.md` with results and artifact paths.

- [ ] Run `./gradlew.bat test` on the candidate and record suite totals/failures, including existing engine/duel regressions. Native/data failures are environment failures until diagnosed; do not remove/disable tests to get a clean result.
- [ ] Run the complete registered harness:

```powershell
./gradlew.bat runClient -PldTest=group:duelcraft -PldTestWindow=1280x720
New-Item -ItemType Directory -Force -Path build/interaction-release | Out-Null
Copy-Item -LiteralPath build/ldlib2-uitest -Destination build/interaction-release/full-1280x720 -Recurse
```

Use a fresh run-name if that destination exists; do not recursively delete old evidence. The harness clears its own output before each run, so copy before starting the next run. Inspect report.json status/scenario counts and actual screenshots.
- [ ] For `collection_overflow`, `collection_search`, `duelcraft_home`, and `duel_lobby`, run the 3x3 combination of window1280x720/960x540/1920x1080 and requested GUI scale2/3/4. Use `-PldTestGuiScale=N` only if the scenario does not override it; add explicit scenario variants otherwise. Record effective framebuffer and GUI dimensions. Verify a non-16:9 viewport and resize while editing. No fixed minimum comfort promise until the user playtests it.
- [ ] Check visual and interaction invariants: all section headers/actions remain in bounds, card rows do not overlap, scrollbars are usable, keyboard focus stays visible, filters/long text scroll within their panes, selected card survives reflow, dirty draft survives resize, and click hitboxes follow transforms. Check missing-card indicators with color-independent text/counts.
- [ ] Profile the 16,000 synthetic catalog and real managed catalog: record counts, warm/cold search latency, mounted rows, scrolling/frame behavior, and pending image loads. Validate bounded row/widget creation and latest-query results. Simulate unavailable images/database without deleting user caches: inject the failure in a fixture or use a disposable cache. Check visible placeholders and retry behavior. Do not claim an FPS guarantee from the synthetic fixture alone.
- [ ] For any failure, first add a failing assertion or write exact reproduction/expected/observed steps, then fix the responsible code and rerun affected tests plus the relevant regression suite. Commit fixes separately from report changes with messages naming the demonstrated behavior.

## Task 3: Dedicated-server two-player acceptance

**Create:** `docs/testing/player-interaction-manual.md` containing the procedure/table below and recorded outcomes. Use a disposable world with both players' permission to change that world's test inventories; never overwrite a personal world save as a test setup.

**Interfaces:** record Alice/Bob UUIDs and inventory/collection/list snapshots before and after. A scenario passes only when the observed quantities/selection/visibility match the expected state.

| Sequence | Expected result |
| --- | --- |
| Alice/Bob join with no binder/mat; J -> Collection/Duels | Both menus available; no item requirement |
| Alice saves legal list with missing cards | Draft survives close/reopen; default ownership off permits activation, ownership on rejects with shortages |
| Grant20 canonical copies, deposit20, withdraw5 | Physical5, collection15; aggregate20; no cards dropped |
| Fill main inventory, request withdrawal | No inventory/count/revision change; capacity message |
| Deposit a normal card beside a customized unsupported card | Supported copies stored; unsupported stack preserved and reported |
| Create two lists referencing same copies | Both save; selection does not consume or reserve copies |
| Activate valid list; withdraw one required copy | Ownership off/no hook preserves selection; ownership on clears it; saved list unchanged in both cases |
| Deposit missing copy again | Ownership shortage resolved; companion restrictions still apply; cleared lists are not auto-selected |
| Two players use the same binder/mat type or same placed mat | Each sees only their own data; blocks own no collection |
| Send invite; sender withdraws required copy; target accepts | Ownership on rejects acceptance; ownership off/no hook permits it if otherwise eligible |
| Accept valid invitation, then send transfer/save/clear through stale UI and commands | BUSY; no mutation throughout RPS/first selection/start/live duel |
| RPS tie, then winner chooses second | New round ID; correct first player; seeded shuffle identity preserved |
| Disconnect/cancel/timeout during preparation | Both locks released, no orphan duel, saved data intact |
| Attempt second invitation involving a participant with outstanding invite | Clear conflict; no silent replacement |
| Dirty editor receives invite, then later accepted preparation | Pending invite does not discard edits; accepted flow suspends draft |
| Complete/concede duel, return home | Collection usable; retained active selection remains if eligible |
| Send old flow/round action after new preparation begins | Old action rejected; current flow intact |
| Death/respawn, End return, dimension change, logout/rejoin, server restart | Deposited collection and saved lists retained; physical cards follow normal inventory rules |
| Alice attempts Bob's snapshot/list ID through test payload harness | No other-player data returned or mutated |
| Restart with ownership enabled or a restrictive companion added | Previously active unowned/denied list cannot bypass current policy; list/counts preserved |
| Restart with ownership disabled or companion removed | Current policy governs next use; no automatic reactivation or collection credit |
| Companion approves an illegal/unowned deck with core ownership enabled | Core rejection still wins |
| Companion denies or throws during final startup recheck | No duel starts; private explanation, safe cleanup, no inventory/count change |
| Local YDK import includes Side and unknown ID | List saved with Side/placeholder; no cards granted; activation rejected appropriately |

- [ ] Execute each sequence against the same release candidate JAR and record pass/fail plus evidence. Use operator give solely for seeding physical test cards. Test forged/stale requests through a dev-only fixture; do not ship a client-accessible generic mutation console.
- [ ] Test an engine-start failure with an injected dev test failure at the start boundary: both players leave STARTING, partial sessions close, client screens clear failed-start state, and a later valid invite works. Remove or keep the injection strictly DEV_ONLY.
- [ ] Complete a real duel through at least one normal win condition, plus concede/disconnect paths. Exercise solo with an empty human collection under default ownership off, and with an owned list under ownership on; server AI content needs no collection. Verify companion denial/failure prevents human startup under both settings. Full core simulation behavior is covered by existing tests; this task proves the new entry-to-exit integration.
- [ ] Record human feedback on inspector readability, deck-grid density, filter discoverability, and keyboard focus. Make bounded layout fixes with corresponding screenshot/bounds updates; do not redesign the information architecture without a concrete finding.
- [ ] Commit the procedure and actual outcomes as `test: document dedicated-server interaction acceptance`. List incomplete checks explicitly if players/server access are unavailable; release sign-off remains incomplete until they run.

## Task 4: Documentation, compatibility, and removal audit

**Modify:** `docs/multiplayer-decks.md`, create `docs/player-collections.md`, update relevant `AGENTS.md` sections and `docs/engine-wiki/duelcraft-jni.md` host-policy descriptions, roadmap/handoff links. **Inspect:** every caller of old upload/selection APIs and item routes.

- [ ] Audit sources for legacy paths using:

```powershell
rg -n 'DuelDeckPayload|playerCurrentDeck|setPlayerCurrentDeck|resolveDeck|startSoloDuel|startDuel' src/main/java
rg -n 'setData|replaceCounts|CollectionCommand|isBusy|isPreparing' src/main/java/com/haxerus/duelcraft
```

For each remaining start/mutation caller, identify the authenticated player, busy gate, revision/eligibility gate, and owner of the resulting state. Search results are an audit aid, not proof on their own. Old unsafe handlers/types must be removed or fail-closed, never quietly left registered. Do not remove the legitimate core Deck model, AI deck registry, or native engine code.
- [ ] Document the J key/rebinding, home/binder/mat routes, deposit/withdraw semantics and unsupported stacks, draft versus active list, missing-card messages, preparation locks, save acknowledgement, Side storage without match play, same-world/server ownership, and limitations of current legality checks. Explain `requireCardOwnership=false` as the standalone default, how to enable the SERVER setting and restart, and that physical cards must be deposited to satisfy enabled ownership. Withdrawals always require stored copies; deck use never creates cards. Document the optional companion restriction hook and its denial/failure contract separately from acquisition content.
- [ ] Document YDK import and updated command semantics, matching client/server protocol, and upgrade behavior: old local YDK files remain available to import, but import grants no ownership. Do not auto-credit collection counts from old deck files. Explain admin test-card grant permissions and that acquisition recipes/economy are separate work.
- [ ] Update stale testing/scenario counts only after checking actual registration. Keep source-version/engine protocol material unchanged unless this integration changed that contract; preserve concurrent wiki edits. Replace handoff statements saying later plans still need writing with links to the completed plans.
- [ ] Validate Markdown links and resource JSON/XML loading, run `git diff --check`, and review the final diff for unrelated edits. Commit `docs: explain collection and duel interaction flows`.

## Task 5: Final candidate and sign-off

**Modify:** execution report and roadmap milestone status only, plus fixes already justified above.

- [ ] After the last code change run the full tests/build and relevant UI regression scenarios. If fixes changed persistence, transfers, or preparation, repeat the affected dedicated-server sequences on the rebuilt candidate; a previous JAR's pass does not transfer automatically.
- [ ] Record the final commit/diff and JAR hash with test results. Confirm packet version, client/server artifact identity, no missing models/resources, and no reported errors in logs during the accepted journey.
- [ ] Summarize implemented behavior, observed verification, remaining supported-rule/content limitations, and any blocked checks. Link real screenshots/report files rather than announcing unperformed tests. Update plan checkboxes only for completed work.
- [ ] Finish the implementation branch according to the user's requested workflow. Do not push, merge, publish the mod, alter a live server, or deploy the JAR merely because validation passed; those are separate user actions/requests.

## Completion

Release validation is complete only when the candidate passes automated checks and the dedicated two-player journey, with human readability feedback recorded. The final report identifies exactly what was tested. This milestone can be blocked by missing human/server access even if all automated tests pass; report that boundary candidly.

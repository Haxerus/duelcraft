# Interaction Release Validation Implementation Plan — Milestone 6

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to complete this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Verify the complete collection/deck/lobby experience on the release build, fix demonstrated defects, and document its operation and limits.

**Architecture:** Exercise the existing pure/service tests, real LDLib2 UI scenarios, and a separate two-client dedicated-server playtest against the same artifact. Preserve evidence per run and tie each correction to a failing test or repeatable reproduction. This milestone adds no new product subsystem.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, Gradle, JUnit 5, LDLib2 harness, Windows x64 native build.

**Spec:** [Design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: completed [milestone 5](2026-09-17-player-entry-points-milestone-5.md), including all prior milestones.

## Global constraints

**2026-10-16 continuation:** the user accepted the lobby dropdown's readable entries and Escape behavior, closing its focused human recheck. Run the remaining non-multiplayer checks, with subagents where useful. The full UI redesign remains separate; installed two-player acceptance stays pending until friends are available. Preserve the completed Task 4 audit and prior verification evidence.

**2026-10-16 audit scope:** carry out the documentation/compatibility audit now; defer multiplayer until friends are available and keep the full UI design/style-guide pass separate. Preserve Oct 8 acceptance of the non-multiplayer checklist, with only the QA.2 dropdown human retest pending from its scoped correction. [Release evidence](../../testing/player-interaction-release.md), [authority audit](../../testing/player-interaction-audit.md) and [manual procedure](../../testing/player-interaction-manual.md) record this boundary. No new build or client/server/UI launch belongs to this checkpoint. Task 4 audit/documentation review is complete; existing evidence does not close final-candidate gates.

**2026-10-06 acceptance carry-over:** include the open checks in the [QA.3 carry-forward register](../reports/2026-10-06-qa3-carry-forward.md) in this milestone's editor, input, policy and dedicated-server passes. They no longer block starting M5 and have not been marked passed. Retain M5's own verification requirements.

**2026-10-08 M5 feedback:** the user passed the non-multiplayer checklist except lobby presentation and the rule dropdown; multiplayer carries forward. See [the playtest update](../reports/2026-10-08-m5-playtest.md). The lobby redesign belongs to a separate full UI pass that first establishes an LDLib2 style guide and mockup-to-widget review process. Do not treat functional bounds checks as visual approval or expand the scoped dropdown correction into that redesign.

- Use the term **collection**.
- Determine final readability, density, and minimum comfortable window size through in-game playtesting.
- Support the hotkey, binder, and mat through the same server policies and player data.
- No unrequested CI/platform overhaul, banlists, progression economy, public matchmaking, or native engine upgrade.
- Release claims require actual results. A skipped check remains skipped; a blocked dedicated-server test cannot be reported as passing because integrated singleplayer passed.

Paths are repository-relative in this document. Keep existing user/other-session modifications separate from this release work.

## Task 1: Establish release candidate and test prerequisites

**Create:** `docs/testing/player-interaction-release.md` as an execution report, populated with observed facts during this task. **Read:** `AGENTS.md`, all six milestone plans, `build.gradle`, `gradle/ldlib2-uitest.gradle`, native build instructions.

**Interfaces:** report contains candidate commit/branch, dirty-diff inventory, native submodule SHA, mod/library/JDK versions, native DLL provenance, commands with exit status/time, installed JAR SHA256, actual test environment, links to screenshots, and unresolved checks. The report is evidence, not a template claiming future success.

- [x] Inspect `git status --short`, `git log -1`, and `git -C native/ygopro-core rev-parse HEAD`. Record the actual implementation commit and any uncommitted differences; do not call HEAD alone the tested artifact when code is dirty. Audit baseline and existing artifact provenance recorded on 2026-10-16; no new build claimed.
- [x] Check Java21, native DLLs, managed card database/scripts, and the test EDOPro paths configured in build.gradle. Recorded in the [local verification report](../../testing/player-interaction-local-verification.md) and `prerequisites.json`; normal Gradle cache access uses the existing approved elevated invocation. No native/CI refactor or assumed `skipNative` flag.
- [x] Run the full build and retain its output: final `test --rerun build` passed on `66b19e2` in 2m4s. [Local evidence](../../testing/player-interaction-local-verification.md) identifies the M6 QA.1 JAR, SHA256 and unchanged bundled native hashes.

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
- [ ] Use the same candidate JAR on both clients and dedicated server. Record the effective server setting and test hook installation per run; test both ownership settings without a companion and a companion restriction under both settings. Restart between configuration/addon changes. Check protocol version is the implemented compatible version (**8** as of M5; the original plan's expected `4` predates M2–M5 protocol changes); verify an older version fails with a clear incompatibility message. Do not distribute/publish the artifact as part of verification.
- [x] Record failures as blockers, fix within the owning milestone, and rerun the affected checks. The populated [local report](../../testing/player-interaction-local-verification.md) records the real candidate, retained failures/corrections, passing reruns and independent final approval. Report updates are committed separately from the fix and fixture commits.

## Task 2: Automated correctness and UI matrix

**Modify as needed:** tests/scenarios created in milestones 1–5; only production files implicated by reproductions. **Artifacts:** `build/ldlib2-uitest/`, copies in `build/interaction-release/<run-name>/`; update `docs/testing/player-interaction-release.md` with results and artifact paths.

- [x] Run `./gradlew.bat test` on the candidate and record suite totals/failures, including existing engine/duel regressions. The audit-baseline run on `0d251da` and final `test --rerun build` on `66b19e2` each passed 1,113 cases: 1,112 passed, one existing Windows symlink skip, zero failures/errors. Both runs are retained separately; no tests were disabled.
- [x] Run the complete registered harness: 58/58 scenarios and 12,338/12,338 checks passed in the Oct 16 local run; see [results and preserved evidence](../../testing/player-interaction-local-verification.md). The later ownership metadata-only addition is covered by repeated solo runs.

```powershell
./gradlew.bat runClient -PldTest=group:duelcraft -PldTestWindow=1280x720
New-Item -ItemType Directory -Force -Path build/interaction-release | Out-Null
Copy-Item -LiteralPath build/ldlib2-uitest -Destination build/interaction-release/full-1280x720 -Recurse
```

Use a fresh run-name if that destination exists; do not recursively delete old evidence. The harness clears its own output before each run, so copy before starting the next run. Inspect report.json status/scenario counts and actual screenshots.
- [x] For `collection_overflow`, `collection_search`, `duelcraft_home`, and `duel_lobby`, run the 3x3 combination of window1280x720/960x540/1920x1080 and requested GUI scale2/3/4. All nine launches passed: 36 required surface executions, 2,151 checks. The [local report](../../testing/player-interaction-local-verification.md) records actual scale clamps/framebuffers and successful non-16:9 dirty resize evidence; two minimized-window attempts are explicitly excluded from actual resize proof. No minimum comfort promise.
- [x] Check visual and interaction invariants: bounds, non-overlap, independent pane/filter scrolling, visible keyboard focus, selection/draft preservation, transformed clicks and color-independent missing-copy text/counts passed. All four surface captures were inspected per matrix row. The small dropdown-edge visual observation is carried to the separate UI pass, not described as design approval.
- [x] Profile the 16,000 synthetic catalog and real managed catalog: counts, first/repeat query timings, bounded mounted rows/widgets, wheel cadence and pending image loads are recorded in the [local report](../../testing/player-interaction-local-verification.md). The controlled unavailable-data/image probes passed. Debounce, uncontrolled caches and restart-only production recovery are explicit limits; no cold-machine or FPS guarantee is claimed.
- [x] For any failure, first add a failing assertion or write exact reproduction/expected/observed steps, then fix the responsible code and rerun affected tests plus the relevant regression suite. The reproduced card atlas warning was corrected in `7facebe` and verified by the real baked-model check; the catalog fixture precondition was corrected with the original failed run retained. Reviewed fixture/version changes are in `66b19e2`, separate from reports.

## Task 3: Dedicated-server two-player acceptance

The [consolidated manual guide](../../testing/player-interaction-manual.md) contains the procedure and pending outcomes. Human friend steps and developer-only forged/failure fixtures are separate. Friends are unavailable at this checkpoint; those rows remain pending. Preserve the accepted Oct 8 single-player results and Oct 16 dropdown retest; carry only outstanding multiplayer/final-candidate gates forward.

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
- [x] Available single-client portion: solo native startup/concede/retry, busy/stale rejection, real setup failure/close, retained draft and final companion denial/exception passed 53 checks per effective ownership setting. [Local evidence](../../testing/player-interaction-local-verification.md) and manual D5 record completion; D1/D3/D4 retain the unrun two-player portions. Normal-win/disconnect friend acceptance above remains pending.
- [ ] Record human feedback on inspector readability, deck-grid density, filter discoverability, and keyboard focus. Make bounded layout fixes with corresponding screenshot/bounds updates; do not redesign the information architecture without a concrete finding.
- [ ] Commit the procedure and actual outcomes as `test: document dedicated-server interaction acceptance`. List incomplete checks explicitly if players/server access are unavailable; release sign-off remains incomplete until they run.

## Task 4: Documentation, compatibility, and removal audit

**2026-10-16 complete:** independent review approved the audit/player documentation after the Delete-draft prose correction. The audit found no production authority bypass requiring a code fix. Static checks parsed source/packaged resources and checked 10 documents / 257 local link targets with zero errors; `git diff --check` passed. Reviewed guides/wiki/audit are committed locally as `1167103`; tracking updates follow separately. The local AGENTS.md update remains ignored under existing policy. Resource syntax/presence is not runtime loading; other M6 gates remain open.

**Modify:** `docs/multiplayer-decks.md`, create `docs/player-collections.md`, update relevant `AGENTS.md` sections and `docs/engine-wiki/duelcraft-jni.md` host-policy descriptions, roadmap/handoff links. **Inspect:** every caller of old upload/selection APIs and item routes.

- [x] Audit sources for legacy paths using:

```powershell
rg -n 'DuelDeckPayload|playerCurrentDeck|setPlayerCurrentDeck|resolveDeck|startSoloDuel|startDuel' src/main/java
rg -n 'setData|replaceCounts|CollectionCommand|isBusy|isPreparing' src/main/java/com/haxerus/duelcraft
```

For each remaining start/mutation caller, identify the authenticated player, busy gate, revision/eligibility gate, and owner of the resulting state. Search results are an audit aid, not proof on their own. Old unsafe handlers/types must be removed or fail-closed, never quietly left registered. Do not remove the legitimate core Deck model, AI deck registry, or native engine code.
- [x] Document the J key/rebinding, home/binder/mat routes, deposit/withdraw semantics and unsupported stacks, draft versus active list, missing-card messages, preparation locks, save acknowledgement, Side storage without match play, same-world/server ownership, and limitations of current legality checks. Explain `requireCardOwnership=false` as the standalone default, how to enable the SERVER setting and restart, and that physical cards must be deposited to satisfy enabled ownership. Withdrawals always require stored copies; deck use never creates cards. Document the optional companion restriction hook and its denial/failure contract separately from acquisition content.
- [x] Document YDK import and updated command semantics, matching client/server protocol, and upgrade behavior: old local YDK files remain available to import, but import grants no ownership. Do not auto-credit collection counts from old deck files. Explain admin test-card grant permissions and that acquisition recipes/economy are separate work.
- [x] Update stale testing/scenario counts only after checking actual registration. Keep source-version/engine protocol material unchanged unless this integration changed that contract; preserve concurrent wiki edits. Replace handoff statements saying later plans still need writing with links to the completed plans.
- [x] Validate Markdown links and resource JSON/XML loading, run `git diff --check`, and review the final diff for unrelated edits. Commit `docs: explain collection and duel interaction flows`.

## Task 5: Final candidate and sign-off

**Modify:** execution report and roadmap milestone status only, plus fixes already justified above.

- [x] After the last code change run the full tests/build and relevant UI regression scenarios. Final source `66b19e2` passed fresh full tests/build, the nine-case matrix and both effective ownership solo runs. The complete UI group precedes only the two-line ownership reporting addition, which was retested in solo. No persistence/transfer/preparation production behavior changed; installed same-JAR acceptance remains pending separately.
- [ ] Record the final commit/diff and JAR hash with test results. Local candidate source/hash and protocol 8 are recorded; the card model warning is fixed and deliberate injected errors are identified. The [template cleanup report](../../testing/template-cleanup.md) records M6 QA.2 and closes the `example_block`/`example_item` resource-warning exceptions. Same-JAR client/server acceptance remains open; do not claim blanket warning-free loading.
- [x] Summarize implemented behavior, observed verification, remaining supported-rule/content limitations, and blocked checks. The [local report](../../testing/player-interaction-local-verification.md) links actual reports/captures and records review approval; the manual guide retains pending multiplayer acceptance. No release-complete claim.
- [ ] Finish the implementation branch according to the user's requested workflow. Do not push, merge, publish the mod, alter a live server, or deploy the JAR merely because validation passed; those are separate user actions/requests.

## Completion

Release validation is complete only when the candidate passes automated checks and the dedicated two-player journey, with human readability feedback recorded. The final report identifies exactly what was tested. This milestone can be blocked by missing human/server access even if all automated tests pass; report that boundary candidly.

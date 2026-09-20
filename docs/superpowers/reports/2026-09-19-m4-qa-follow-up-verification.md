# M4 QA follow-up verification

## B1 preparation notifications

Implementation commit: `c950737 fix: announce preparation choices once per round`.

The reproduced defect was notification-only. Both existing and new cardinality tests kept one shared preparation flow and one completed start while the adapter repeated the waiting player's RPS prompt after the opponent submitted. The private `DuelManager` announcement key now contains only `flowId`, actionable `mode`, and `roundId`. Authoritative `PreparationStatePayload` delivery remains unconditional for online recipients. Terminal IDLE/DUEL publication, logout, and shutdown clear retained keys.

The focused RED was deliberately recreated after the initial observation by temporarily reverting only the production gate. It ran 63 tests and failed the two notification regressions: the waiting player received 2 prompts instead of 1, and the tie path produced 3 prompts instead of 2. Service cardinality stayed green. Restoring the gate made all 63 pass. The final five-suite selection passed 72 tests with no failures, errors, or skips. The final full suite passed 1,021 tests across 65 XML suites with no failures, errors, or skips.

Both real dedicated two-client runs passed 57/57 checks and 238 merged steps. Default and required ownership each proved one shared flow after repeated/crossed commands, recipient-specific state convergence after every hand, one RPS prompt per recipient per round, two total after the tie replay, one winner prompt only for A, cancellation/expiry/disconnect cleanup, and exactly one delegated native `setupDuel` call. Required mode seeded exact deposited passcodes for both players. The scenario clears chat at its start and attaches the current run's captured strings to each counted step.

Evidence:

- `build/evidence/qa-m4-follow-up/b1/unit-red/` — recreated RED log/XML.
- `build/evidence/qa-m4-follow-up/b1/unit-green/` — initial GREEN log/XML.
- `build/evidence/qa-m4-follow-up/b1/focused-final/` — 72-test final focused run.
- `build/evidence/qa-m4-follow-up/b1/mp-default-attempt1/` — harness-only dedicated-server classloading failure, zero behavior checks.
- `build/evidence/qa-m4-follow-up/b1/mp-default/` — default ownership, PASS 57/57.
- `build/evidence/qa-m4-follow-up/b1/mp-required/` — required ownership, PASS 57/57.
- `build/evidence/qa-m4-follow-up/b1/full-suite/` — 1,021-test final suite.

The first dedicated attempt loaded a client-only method reference while defining the scenario on the dedicated server. It is preserved but is not counted as behavior RED. Deferred client-segment lambdas fixed the harness wiring. In the required run, client A wrote its complete PASS report and then its child Gradle process exited 1 because `build/reports/problems/problems-report.html` disappeared during shutdown; the orchestrator merged all three complete reports, verified 57/57 checks, and the requested outer Gradle command exited successfully. Old text-chat buttons still resolve the current flow/round through command handlers; historical-ID binding is not claimed. Typed packet stale-ID rejection remains covered, and M5 UI replacement remains outside B1.

## I1 combined verification (execution in progress, 2026-09-20)

The integration work started at `dbcf57ac632b8157d4c2d76e10ffbcb86ba093bc` on `codex/qa-m4`. The only pre-existing tracked edit was the approved C4 index checkbox. The new verification fixtures use `run-qa-m4-follow-up-lifecycle` and `collection_qa2_login_20260919`; they do not open historical M2/M3/M4 saves. Both new scenarios use DEV_ONLY registration, and the client lifecycle subscriber returns immediately in production. The dedicated scenario defers client helper access until its client segments. No wire record or codec changed from baseline protocol 7, which remains the registrar version.

Fresh full unit/native execution: **1,098 cases in 67 XML suites; 1,097 passed, one expected skip, zero failures/errors**. Windows denied symlink creation in `DeckExportServiceTest`; no other test skipped. The archived run contains XML, HTML, console and source/dirty-state metadata under `build/evidence/qa-m4-follow-up/i1/full-unit-native/`.

Fresh lifecycle observations: **87/87 checks**. Optional seed and rejoin each passed 7 checks in PID 29760. Optional restart passed 7 in PID 42632. Required-missing passed 7 in PID 32080: only activation cleared on login, saved data persisted, and the first editor opened blank. Required-owned seed passed 52 in PID 40144, depositing all 43 exact passcodes through the production handler and explicitly activating. Required-owned restart passed 7 in PID 15032, preserving activation and the initial active draft. The integrated server shares each client JVM; manifests record that fact, UUID, loaded config path and effective policy. Console logs independently record config loading.

Fresh dedicated `collection_qa2_reconnect`: **11/11 checks in each ownership mode**, all three child Gradle exits 0. Real acceptance suspends both dirty editors; cancellation restores the same controller, draft ID, name and cards. A second acceptance followed by B's real logout disposes its suspended controller and private connection state, restores A's draft, and releases the survivor's lock. Each run archives actual server/client PIDs separately from the integrated lifecycle.

The first new config-preparation task failed before changing policy because it called `project.file` during task execution with configuration cache enabled. The preserved RED log names that failure. Capturing the fixed directory at configuration time made the guarded task pass, followed by the three required-policy JVMs above. This changed verification wiring only.

The corrected full UI group passed **51/51 scenarios, 12,036/12,036 checks, 4,218 steps and 224 captures**, with no errors/skips and outer exit 0. This includes collection GUI scales 2/3, every existing rule-layout scenario, reveal prompts, Materials, panel coexistence and export. The original ownership=true config was restored byte for byte after each optional run (SHA256 `1D9C7974CF6691D3A52C774EBC42B4037ECD8DF4C5E80DB3CEBB383367ADCD94`).

The first full group is preserved separately: 46 PASS, 2 FAIL, 3 ERROR; 11,782 checks with 22 failed. Artificial fixture teardown restored older server authority while the client retained a newer required revision, breaking persistence/retention/import. Two private synthetic SQLite schemas omitted the queried `ot` column, breaking effect and link captions. A seven-scenario focused RED reproduced all five failures (472 checks, 22 failed). Resetting the client connection state after authority restoration and adding `ot=1` to the two private schemas fixed those failures. The first GREEN then exposed a helper-only duplicate selector at persistence step 84 (720 checks, no failed assertion); assigning unique per-list test IDs fixed it. Final focused GREEN passed all seven scenarios and 726 checks. Production revision guards, queries and rendering were unchanged. All failed and corrected reports remain under `i1/{full-ui-group,ui-fixture-red,ui-fixture-green,ui-fixture-green-final,full-ui-group-green}`.

The ten existing dedicated runs passed **2,712/2,712 checks**, with all 30 final server/A/B child exits 0 and all outer exits 0:

| Dedicated scenario | Optional checks | Required checks |
|---|---:|---:|
| `duel_visibility_qa_mp` | 1,183 | 1,183 |
| `duel_preparation_notifications` | 57 | 57 |
| `duel_preparation_commands` | 8 | 8 |
| `collection_transfer_isolation` | 34 | 34 |
| `collection_duel_policy_mp` (alone) | 74 | 74 |

`mp-matrix-summary.json` identifies each final archive. Visibility preserves raw native and sanitized recipient traces separately; effective role configs, engine-data hashes, reports and child exits accompany every run. The two policy replacement paths are `mp-collection_duel_policy_mp-default-report-option-verified` and `mp-collection_duel_policy_mp-required-report-option`; the shutdown/propagation caveat below explains why.

Committed-source build/package and actual packaged acceptance remain in progress. Final results and hashes will replace this execution note after observation.

Runtime diagnostics are kept in the archived consoles: the corrected full UI group emitted 73 LDLib dirty-layout warnings (16 in `duel_prompt_controls`, 57 in `duel_selection_surfaces`) and five ERROR-level entries from deliberately failed native-start/throwing companion-policy fixtures; all scenario checks passed. Missing example models, vanilla sound/shader warnings, synthetic unknown-art requests and Gradle deprecations also remain visible. The dedicated visibility screenshots use deterministic gold/diamond fixture textures to verify authorized identity versus backs; their real native script, sanitized packet and selected-card continuation assertions are distinct from packaged card-art acceptance.

Lifecycle and dedicated engine runs used the mod-managed public cache after its normal startup refresh, with `cards.cdb` SHA256 `E7D62831EA192B0A5E696D605DC2416D2E61AC362F263C935A01DD69013EDB0A`. This differs from the preflight cache snapshot. `lifecycle-data-provenance.json` and each dedicated role's `data-provenance.json` record actual paths and script/revision hashes. Unit/native tests use the separate configured ProjectIgnis files, hashed in `full-unit-native/input-hashes.json`; these layers are not described as identical inputs.

The first optional `collection_duel_policy_mp` run completed all 74 behavior checks and its outer command exited 0, but child B exited 1 while Gradle moved its experimental problems HTML into the shared output path (`FileAlreadyExistsException`). The archive is retained as an infrastructure failure. A direct installed-Gradle probe verified `-Dorg.gradle.problems.report=false`; an initial cached invocation showed that only the root wrapper received it, so that propagation attempt is retained without a workaround claim. The setting is appended to existing `GRADLE_OPTS` and restored in `finally`. The required run rebuilt its cached configuration because the ownership property changed; scoped live-process capture confirms the flag on root/server/A/B wrappers. The final optional run explicitly adds `--no-configuration-cache` to avoid reuse of an earlier launcher environment. Final acceptance uses a separately archived run per policy with all child exits checked. No global Gradle setting or project source change was needed.

The precommit `build` completed successfully in 42 seconds on the recorded dirty integration tree, executing the test task again: **1,098 cases / 67 suites, zero failures/errors, one expected symlink skip**. JAR inspection found both Windows x64 native DLLs and nested SQLite JDBC 3.49.1.0. Expanded metadata declares version 1.0.0-m4-qa.2, NeoForge minimum 21.1.224 and the unchanged LDLib range [2.2.5,); the build and package pin LDLib2 2.2.39.a. Extracted metadata, entry sizes and hashes are in `i1/jar-inspection/`. The committed-source build and package will record their exact clean source separately.

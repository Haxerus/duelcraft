# Deck-use policy follow-up verification — 2026-09-19

Status: implementation, execution checks and implementer self-review complete. Independent Task 3 and final follow-up review remain pending before M3. This report is separate from the historical M1/M2 reports.

## Scope and code

The follow-up adds startup-captured `ServerConfig.requireCardOwnership` (default false), server `DeckUsePolicy`, synchronous deny-only `DeckUseCheckEvent`, authoritative policy reports and Opened snapshots, neutral optional-shortage presentation, and persisted-active revalidation on Open. Task 1 commits: `7706965`, `2783248`; Task 2: `37a963d`. Task 3 starts from `37a963d` on `codex/player-collections`; its production/test/harness code is commit `2097f0c4b2fc3e217a112fbb65581beb851e00a7`. This report and synchronized status documents follow in a documentation-only commit.

Open runs `revalidateActive` under the existing authenticated/busy gates. A normal policy denial clears only active ID, increments revision once, persists once, and returns a successful private Opened snapshot with its clearance report. An evaluation failure returns DATA_UNAVAILABLE without attachment mutation. A later ordinary Open has no clearance report and performs no write.

Real restart testing found that the development collection command originally discarded its preliminary Open report before the editor issued another Open. The command now obtains one complete fresh private snapshot and passes that view into screen initialization. Subsequent editor refreshes still pull authoritative snapshots. Typed refresh rejection preserves the existing private access-error message. Connection generation safeguards remain in CollectionClient, and the command checks that the player instance is still current before opening the screen.

## Configuration and hook state

Pinned NeoForge 21.1.224 `ServerLifecycleHooks.handleServerAboutToStart` calls FML 4.0.42 `ConfigTracker.loadConfigs(SERVER, CONFIGDIR, world/serverconfig)`. `ConfigTracker.resolveBasePath` uses the world path only if the override file already exists. Otherwise it generates and loads `<gameDirectory>/config/duelcraft-server.toml`:

```toml
[ownership]
    requireCardOwnership = false
```

This corrects the earlier research/report assumption that a fresh SERVER config is always generated under world/serverconfig. The policy lifecycle edits only its isolated generated config between stopped JVMs. The dedicated required-mode run uses an explicit world/serverconfig override; the default run uses the generated default.

The dev-only policy scenario explicitly registers/unregisters an inert `CollectionPolicyTestRestriction` instance on NeoForge.EVENT_BUS. It has no automatic subscriber registration or production activation property. Deny reason: `Policy lifecycle fixture restriction`; throwing message: `Policy lifecycle fixture failure`. Both modes are removed normally and in failure teardown. Count seeding is an isolated test fixture; M3 physical transfers are not implemented.

## Controller decisions and boundaries

- Incompatible intermediate changes bump the channel protocol separately: Report changed 3→4 in Task 1; Opened changed 4→5 in Task 2. Cost: extra development protocol numbers, avoiding silently compatible incompatible builds.
- Opened/View carry nullable `clearedActivation` Report alongside authoritative `ownershipRequired`. This preserves usable management access while privately explaining first-Open clearance. Cost: a bounded optional field in the planned wire change.
- Prior-wire rejection is checked through pinned NeoForge negotiation using the actual initialized runtime channel registry and prior-version channel metadata, alongside real current-version dedicated A/B clients. No production protocol override or rejected-client coordinator was added. No old-binary disconnect screen was observed; this is the explicit limit of that evidence.
- M3–M6 remain unimplemented. M4 still owns shared-policy cutover of legacy upload/command and actual solo/multiplayer duel startup routes. This report does not certify those routes.

## Execution evidence

All commands use `GRADLE_USER_HOME=C:/Users/haxer/.gradle` in the worktree. Local evidence root: `build/evidence/deck-use-policy/task-3/`. These runtime artifacts are retained locally and are not committed binaries.

Producer TDD: `./gradlew.bat test --tests '*CollectionHandlerTest' --console=plain` failed with the intended two behavioral failures, then passed all 7 tests. Logs: `producer-red.log`, `producer-green.log`.

Entry-path regression: the first ownership runtime phase reached 22/23 checks; the only failed check was the missing first-Open clearance report. Exact failed state, report, screenshots and original passing default phase are preserved under `failed-entry-world/`; log `entry-runtime-red.log`. The new typed refresh rejection test then failed on the missing API (`entry-red.log`). Focused green command `./gradlew.bat test --tests '*CollectionClientTest' --tests '*CollectionHandlerTest' --tests '*SavedDeckControllerTest' --console=plain` passed 47 tests (`entry-green.log`).

An earlier fixture-only attempt used a 20-character test username; Minecraft's 16-character login bound rejected it before scenarios ran. The 240-second watchdog stopped the process normally. Its world/log remain under `failed-username-world/` and `failed-username.log`; the fixture now uses `PolicyTest`. This was not a product defect.

An initial full run passed 900 tests/53 suites, before the entry-path fix. XML is retained under `full-test-xml/`; it is not the final post-fix verification result.

Post-fix full command: `./gradlew.bat test --console=plain` passed in 52 seconds: **901 tests, 53 suites, zero failures/errors/skips**. Evidence: `final-full-test.log`, `final-full-test-xml/`.

The next ownership attempt proved first-Open delivery and its caption (9 checks passed), then a fixture click failed because the now-visible clearance dialog had not been dismissed. The fixture now presses its normal Done button before opening Saved lists. That attempt is retained at `failed-modal-fixture/` and `failed-modal.log`; no production change was needed.

### Final retained-world sequence

```text
./gradlew.bat runClient -PcollectionPolicyLifecycle=default --console=plain
./gradlew.bat prepareCollectionPolicyOwnershipRequired --console=plain
./gradlew.bat runClient -PcollectionPolicyLifecycle=ownership --console=plain
./gradlew.bat runClient -PcollectionPolicyLifecycle=restricted --console=plain
./gradlew.bat runClient -PcollectionPolicyLifecycle=removed --console=plain
```

All five commands succeeded. Evidence: `lifecycle/<phase>/` contains the completed LDLib report, screenshots, authoritative manifest and exact loaded config for that phase; `lifecycle-<phase>.log` records startup/shutdown and generated config load path. Each stage used a separate JVM and the same retained world `run-collection-policy-lifecycle/saves/collection_policy_followup_20260919`.

| Phase | PID | Checks | Effective ownership | Hook checks | Final revision | Count entries | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| default | 3116 | 15/15 | false | none, deny, throw, removed | 4 | 0 | yes |
| ownership | 12420 | 24/24 | true | throwing first Open; none, deny, throw, removed | 9 | 43 | yes |
| restricted | 30452 | 11/11 | true | deny on first Open, removed | 10 | 43 | no |
| removed | 34584 | 10/10 | true | none | 10 | 43 | no |

Authenticated UUID: `cd662e46-9497-3e5f-9f3a-90a95b2d4c65`; saved UUID: `e246f5bb-15b3-47eb-bf57-cb63236545ac`. The legal fixture contains Main40, Extra1, Side2, with exact passcodes preserved in each manifest. The default starts from no world, manifest or SERVER TOML and empty attachment. Save uses the real authenticated packet path; successful activation uses the actual editor button. Denial/failure probes use real request/reply packets. Ownership phase proves failed first-Open evaluation leaves revision4 unchanged, then required ownership clears it to revision5, exact test counts seed revision6, UI activation reaches7, clear reaches8, and final permitted activation reaches9. Restriction restart clears once to10; removal and subsequent Open remain10. Counts are never consumed or credited by activation. Teardown removes all temporary listeners.

Screenshots were visually inspected for the optional active list, required clearance and companion clearance. They preserve the dark editor and the prior-list wording. The first optional capture contains Minecraft's new-world movement tutorial toast; it does not cover the tested policy footer. Final regular UI captures below use the requested sizes/scales separately.

Original M2 `run-collection-lifecycle`, its retained save/manifest, and `build/evidence/collection-m2/retained-world` were neither reseeded nor deleted. Original manifest/level hashes are recorded in `original-m2-hashes.txt`. Failed new policy worlds were archived with exact path checks after process shutdown; no test cleanup touched the historical world.

### Dedicated current-version clients and prior-wire negotiation

Default command: `./gradlew.bat runMpTest -PldMpTest=collection_privacy -PldMpOwnership=default --console=plain` passed in 2m26s, 1/1 scenario and 61/61 checks. Server PID35768, client A PID6376, client B PID4408. Evidence: `mp-default/` (merged report, server/A/B reports and logs, screenshots, generated false config), `mp-default.log`. The server loaded `runs/mpServer/config/duelcraft-server.toml`.

The server fixture reads the initialized `NetworkRegistry.PAYLOAD_REGISTRATIONS`, confirms all eight mandatory Duelcraft play channels advertise current version5 including both collection payloads, and invokes `NetworkComponentNegotiator.negotiate`. Current/current succeeds. A copied client channel set advertising prior version4 for those channels fails on all eight IDs with `neoforge.network.negotiation.failure.version.mismatch`, arguments `[5, 4]`; other mods' metadata stays unchanged. The merged report records the exact failure components. This uses NeoForge's negotiation algorithm and actual registration metadata, not a constant-equality assertion. The real connected A/B clients then exercise the current payload codecs and private routing.

Required command: `./gradlew.bat runMpTest -PldMpTest=collection_privacy -PldMpOwnership=required --console=plain` passed in 2m24s, 1/1 scenario and 61/61 checks. Server PID18740, client A PID30304, client B PID35604. Evidence: `mp-required/` and `mp-required.log`. NeoForge loaded `runs/mpServer/world/serverconfig/duelcraft-server.toml`, with `[ownership] requireCardOwnership = true`, while the instance default remained false. The loader supplied the schema's comment to the fixture TOML; the effective true setting passed its server-side assertion.

Both runs use real separate dedicated/client JVMs with channel version5, authenticate two distinct players, save and activate owned all-section lists, reload exact private snapshots/ReadDeck contents, reject foreign snapshot capabilities and saved UUIDs, and observe zero collection packets at the idle peer's receive boundary before request-ID filtering. Both restore the original attachments and client receivers in teardown. Exact protocol evidence is step1, `capture and seed distinct private attachments`, in each merged `report.json` and server report: current/current accepted, prior4 rejected with the recorded mismatch reasons.

### Final editor and persistence runs

```text
./gradlew.bat runClient -PldTest=collection_eligibility,collection_saved_decks,collection_persistence -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain
./gradlew.bat runClient -PldTest=collection_eligibility,collection_saved_decks -PldTestWindow=1920x1080 -PldTestGuiScale=3 --console=plain
```

Scale2 run `26108_643f8ebe` passed 3/3 scenarios and 71/71 checks, with nine captures (73-second build). Scale3 run `4188_643f8ebe` passed 2/2 scenarios and 52/52 checks, with seven captures (61-second build). Exact reports and screenshots are retained at `ui-scale2/` and `ui-scale3/`, with command logs `ui-scale2.log` and `ui-scale3.log`.

The eligibility scenarios cover optional/required ownership and companion denial; saved-deck scenarios perform real legal-unowned activation. Persistence verifies the private all-section state across actual respawn. Visual inspection included `ui-scale3/screenshots/collection_eligibility/49_optional-companion-denial.png`, `ui-scale3/screenshots/collection_saved_decks/124_collection-real-optional-activation.png`, and `ui-scale2/screenshots/collection_persistence/82_collection-owned-active-after-respawn.png`. Denial dialogs show the applicable reason, optional shortages remain neutral, and the editor retains its dark layout without observed clipping at either requested scale.

## Self-review and handoff

The production diff was reviewed for authenticated ownership, busy/error ordering, one persistence write on clearance, snapshot invalidation, private report delivery, connection-generation checks and fresh screen initialization. The dev fixtures were reviewed for explicit registration/teardown, phase/PID/report guards, separate directories and preservation of historical evidence. `git diff --check` passed. The final full suite and runtime runs exercised the final behavior; subsequent edits were documentation and one whitespace-only test-method separation.

The original M2 manifest and level hashes were rechecked unchanged at handoff: SHA256 `4BC6BAB42ACA13365FF2750045F2FB47998E06A7B7F187B341BB67673FFA2489` and `D28F2EA9A04FD2242A90D9DAE9C9008CF4F753FFCDB55BF4EA2897834FB30AE1`, respectively. The isolated policy world remains retained after the removed phase, with ownership required, no listener, revision10 and no active list.

No unresolved implementation defect is known from these checks. Independent Task 3 and final follow-up review remain pending. No merge/push was performed, and the worktree remains available for review. Evidence establishes the stated collection/editor and negotiation behavior; it does not establish an old-binary disconnect screen or policy enforcement at legacy duel startup routes.

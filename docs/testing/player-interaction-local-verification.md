# M6 local verification — 2026-10-16

**Follow-up:** [Template cleanup verification](template-cleanup.md) records M6 QA.2 and closes the template-resource warnings noted below. This report retains the preceding QA.1 results.

This continues the completed [audit checkpoint](player-interaction-release.md) from clean `cfb33a5861364f33e610bfee4089356b0ac76d4c` on `codex/qa-m4`, in the existing deck-editor worktree. The user accepted the lobby dropdown's readable entries and Escape behavior. They authorized remaining checks that need no multiplayer session. The full UI redesign remains separate; the [friend procedure](player-interaction-manual.md) remains pending.

## Candidate and environment

The local candidate version is `1.0.0-m6-qa.1`, protocol **8**, source `66b19e2c6df1a5295736688e57d57b8a0b849ba8`. The only production correction in this batch registers the existing physical card-back texture in Minecraft's item atlas (`7facebe`). Other source changes instrument DEV_ONLY scenarios; they do not change collection, transfer, persistence or duel policy behavior. Independent review approved the fixtures, the atlas correction and the final ownership metadata addition before their scoped commits.

Runtime JAR: [duelcraft-1.0.0-m6-qa.1.jar](../../build/libs/duelcraft-1.0.0-m6-qa.1.jar), 17,353,272 bytes. SHA256: `BA404D16FB5F94DAD405D7D35ACC40DF4F45A9E2E7E827B5DA45312412FCFFEA`. `candidate.json` records an empty source/build diff at that source commit; remaining changes are documentation. This exact version was selected explicitly alongside preserved older JARs. It has not been installed on a pair of clients and dedicated server or distributed.

Source and packaged assets parsed successfully: eight JSON and four XML files each. The atlas entry, model and PNG are present in the JAR. Bundled JNI DLL SHA256 is `BCC0E48C02BEAEE705C0F34D6D015C8427C29F6713EA3EB525B7A5A44CF50F18`; ocgcore is `15B9FE19E0755086CCADE2BDF4DFC73A063B4358F6EF93B3C23484B043C64EF1`. Both match the source-resource files and prerequisite hashes. Native build tasks were UP-TO-DATE; this is a fresh JAR, not a claim of fresh native compilation.

Evidence is preserved under `build/interaction-release/m6-local-2026-10-16/`. Each client run has its own log and copied `report.json`, text report and screenshots. These are local build artifacts. The original QA.2 JAR and preceding UI output were copied to `previous-qa2.jar` and `previous-ui-output/` before any new run. QA.2 SHA256 remains `2D9A2FEDCC6177C6E81AE455EECF1A9C402AB8756B72B43A2BBBB9E87AA22397`.

| Prerequisite | Observed value |
| --- | --- |
| Host | Windows 11 amd64 |
| Minecraft / NeoForge / LDLib2 | 1.21.1 / 21.1.224 / 2.2.39.a |
| Gradle / JDK | 9.2.1 / Eclipse Adoptium Java 21.0.1 through `JAVA_HOME`; PATH Java 8 is not the Gradle runtime |
| Gradle cache | `GRADLE_USER_HOME=C:/Users/haxer/.gradle` |
| Native submodule | `122e0d091a0f399221a4510cc98a406ae485905f` |
| Initial managed snapshot | 24,844 database rows, 22,793 Lua files; subsequent managed refresh produced a different measured catalog, identified with the profiling results below |
| Unit-test data | `C:/ProjectIgnis/expansions/cards.cdb`, 13,728 rows; configured script directories exist |
| Runtime directory | `run-collection-m3-ui`, fresh harness worlds; ordinary ownership-enabled development configuration and personal worlds preserved |

`prerequisites.json` records database, configuration and native DLL hashes. A single client/integrated server is not a two-client dedicated-server acceptance run. Harness commands use development classes/resources rather than an installed distribution JAR.

## Reproductions and corrections

The initial six-scenario run exited 1: **5/6 scenarios, 305/306 checks passed**. Its log and report remain in `targeted-initial.log` and `targeted-initial/`.

- `collection_release` incorrectly expected an empty browser while its fixture owned 79 unknown passcodes. Those fallback entries correctly hid the empty-results message. The corrected fixture keeps its saved draft with empty deposited counts; it still checks unknown-card inspection and metadata recovery. This was a fixture precondition error, not a product correction.
- Startup reported `Missing textures in model duelcraft:card#inventory` for `duelcraft:card_back`. The PNG existed at the root of `textures/`, outside the vanilla atlas directory sources. A single-source `assets/minecraft/atlases/blocks.json` now includes it without moving or duplicating the image. The existing `collection_transfers` scenario checks the actual baked item sprite. The complete suite passed that assertion and the warning disappeared. Reviewed correction: `7facebe` (`fix: include physical card texture in item atlas`).
- Managed-catalog row counts were initially observations only. Both catalog profiles now assert viewport-derived mounted-row limits and at most four card widgets per mounted row.

## Runtime results

The complete group passed **58/58 scenarios and 12,338/12,338 checks**, with 257 captures and 495,106ms harness duration. `full-1280x720-scale2-retry1/` contains the fresh report and logs; Gradle exited 0 after 9m15s. The first launch attempt (`full-1280x720-scale2/`) could not access the Gradle wrapper cache, so no client started. Its `run.json` explicitly rejects the copied old UI report as stale. The elevated retry used the same configured cache and produced the passing evidence.

Command: `./gradlew.bat runClient -PldTest=group:duelcraft -PcollectionTransferUi -PldTestWindow=1280x720 -PldTestGuiScale=2`.

Retained local evidence: [full report](../../build/interaction-release/m6-local-2026-10-16/full-1280x720-scale2-retry1/ui/report.json), [physical five / stored fifteen](../../build/interaction-release/m6-local-2026-10-16/full-1280x720-scale2-retry1/ui/screenshots/collection_transfers/58_physical-five-stored-fifteen.png), [managed catalog](../../build/interaction-release/m6-local-2026-10-16/full-1280x720-scale2-retry1/ui/screenshots/collection_release/61_collection-release-managed.png), and [unavailable database with retained passcodes](../../build/interaction-release/m6-local-2026-10-16/full-1280x720-scale2-retry1/ui/screenshots/collection_release/85_collection-release-missing-database.png).

The full group includes the corrected catalog probe and card atlas assertion. A subsequent two-line fixture addition records the effective server ownership value; the affected solo scenario is repeated with that metadata under both configurations. It changes no assertion or production behavior.

Log review: the card texture warning is absent. ERROR entries are the intentional native-start and companion-exception probes, followed by passing cleanup assertions. Existing missing model/blockstate warnings for template `example_block` and `example_item` remain outside this correction. Vanilla sound/shader and LDLib shader warnings also remain. This is not a blanket claim of warning-free resource loading; the final no-missing-resources sign-off retains those known exceptions. Gradle reports existing deprecations for version 10, and compilation retains the existing unchecked `CollectionSearchScenario` warning.

Initial targeted verification passed `collection_duel_policy`, `collection_overflow`, `collection_search`, `duel_lobby` and `duelcraft_home`. Its failed catalog result is retained above and does not count as final acceptance.

### Window and scale matrix

All nine fresh launches exited 0: **36/36 required surface executions, 2,151/2,151 checks**. Each ran `collection_overflow,collection_search,duelcraft_home,duel_lobby` with `-PcollectionTransferUi`, the listed `-PldTestWindow` and `-PldTestGuiScale`. The first row additionally ran `collection_duel_policy` (53 checks), for 292 total checks in that report. Other reports each contain 239 checks. Evidence directories use `matrix-<window>-scale<requested>/` with logs, `run.json` and `ui/report.json`.

| Requested window / actual framebuffer | Requested scale | Effective scale | GUI dimensions | Result |
| --- | --- | --- | --- | --- |
| 1280×720 | 2 | 2 | 640×360 | Pass |
| 1280×720 | 3 | 3 | 427×240 | Pass |
| 1280×720 | 4 | 3 | 427×240 | Pass; clamped |
| 960×540 | 2 | 2 | 480×270 | Pass |
| 960×540 | 3 | 2 | 480×270 | Pass; clamped, native-window caveat below |
| 960×540 | 4 | 2 | 480×270 | Pass; clamped, native-window caveat below |
| 1920×1080 | 2 | 2 | 960×540 | Pass |
| 1920×1080 | 3 | 3 | 640×360 | Pass |
| 1920×1080 | 4 | 4 | 480×270 | Pass |

All four surfaces' saved captures were inspected per row; [the matrix summary](../../build/interaction-release/m6-local-2026-10-16/ui-matrix-summary.json) lists all 36 exact screenshot paths, parsed per-scenario viewports, commands and timestamps. Existing assertions cover bounded virtual rows, non-overlap, section/actions bounds, independent pane/filter scrolling, focused search/settings fields, selected card and dirty draft retention, and transformed click targets. Missing cards retain text/count indicators. The full run and seven matrix rows verified an actual 1100×800 dirty-editor resize and return. At 960×540 requested scales 3/4, native window dimensions became 0×0 while the correctly sized framebuffer and captures remained available; their attempted resize retained the 960×540 framebuffer. Those two attempts do **not** count as actual resize evidence. All results retain exact viewport metadata rather than assuming requested scale/window values.

At effective scale 3, some dropdown captures show a narrow fringe of the underlying settings labels to the left of the popup. Rule entries remain readable and actions unobstructed; this is recorded for the separate UI/style pass. No human minimum-size or design approval follows from the functional matrix.

### Single-client policy and failure checks

`collection_duel_policy` passed **53/53 checks under each setting**. The first matrix row attaches effective server ownership `false`; the separate fresh JVM in [solo-ownership-on/ui/report.json](../../build/interaction-release/m6-local-2026-10-16/solo-ownership-on/ui/report.json) attaches `true`. The ownership-on run exited 0 in 1m16s, with 24,702ms harness duration. The disposable SERVER config was backed up, changed for this run, and restored to its original bytes in `finally`. Both configuration hashes match `prerequisites.json`; ordinary development ownership remained enabled throughout.

Both runs exercised native solo startup (empty human collection when off; deposited exact-passcode requirements when on), live and STARTING mutation rejection with unchanged attachment/inventory, stale replay after release, concede cleanup and dirty draft restoration, an injected real native setup failure with one close and successful retry, and companion denial/exception on the final startup recheck. Hook outcomes preserve saved lists/counts and inventory; ordinary denial clears activation while an evaluation exception leaves the attachment unchanged. Owner-facing explanations were checked. AI content follows the existing server deck route.

These are DEV_ONLY single-client fixtures using registered commands and real handlers/JNI. Failed-start draft recovery explicitly invokes Resume; it is not a claim of automatic navigation. They close D5 and the solo portions of D1/D3/D4. They do not prove a peer sees the correct private/public explanation, two-player RPS/first-choice locks, both-player cleanup, a subsequent invitation, normal-win multiplayer completion or disconnect handling.

### Final tests and build

On source `66b19e2`, `./gradlew.bat test --rerun build` exited **0**, `BUILD SUCCESSFUL` in **2m4s**. There were **1,113 test cases across 72 suites: 1,112 passed, zero failures/errors, one existing Windows skip** (`DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile()`). Evidence: `test-build.log`, `test-summary.json` and copied `junit/`. The test and JAR tasks executed; the other 13 tasks were UP-TO-DATE. Artifact/resource inspection passed with the hash and native identities recorded above.

Documentation validation checked 11 documents and 275 local link targets with no errors; `git diff --check` passed. Independent final review approved scope, evidence accuracy and quality with no findings after rechecking artifact/configuration hashes, raw UI reports, JUnit XML, catalog attachments and warning logs. The locally ignored `AGENTS.md` count now reflects 58 registered UI scenarios and remains outside commits under the existing ignore policy.

## Catalog and unavailable assets

`collection_release` passed **26/26 checks** in 8,144ms. `catalog-measurements.json` contains report/capture hashes, all query timings and 24 wheel samples. All five saved captures were inspected, including actual artwork, missing metadata and image recovery.

| Passing-run measurement | Synthetic | Managed |
| --- | --- | --- |
| Raw metadata / filtered results | 16,000 / 16,000 | 24,893 / 14,761 |
| Total virtual rows | 4,000 | 3,691 |
| Mounted rows: top / wheel / end | 4 / 5 / 4 | 4 / 5 / 4 |
| Mounted card widgets: top / wheel / end | 16 / 20 / 16 | 16 / 20 / 13 |
| Viewport-derived row limit | 6 | 6 |
| First empty query, ms | 159.980 | 197.136 |
| First / repeated exact-passcode query, ms | 162.919 / 163.200 | 193.641 / 179.994 |
| Latest query, ms | 162.679 | 179.212 |
| Wheel input dispatch range, ms | 1.091–1.660 | 0.897–1.694 |
| Nonzero frame/step interval range, ms | 13.874–19.551 | 8.170–23.823 |
| Maximum pending full-card loads during wheel samples | 0; no image manager | 4; zero at final observation |

The managed database was `run-collection-m3-ui/duelcraft/cache/card-data/snapshot-f5ccdf33-5442-4a18-89b8-de569b4b88f2/cards.cdb`, SHA256 `BECFA2AF5369182EE4581D3F8ED460C1760F3E3AA35FE5FB126EA840F14F0D6B`. Its independent metadata read took 91.897ms. It supersedes the initial prerequisite snapshot for these measurements. Default filtering excludes tokens, hidden cards and alternate formats, accounting for the smaller result count. Both catalogs used a 356px viewport and 128px estimated rows; all row/tile bounds passed. Pending cropped-art loads stayed zero.

Search measurements include the existing 150ms debounce, worker scheduling and delivery to the client thread. “First” and “repeat” refer to a query on the same screen; OS/JVM caches are uncontrolled. Wheel samples measure harness frame/step cadence and input dispatch, not isolated rendering CPU time or guaranteed FPS. Image counts observe the shared manager's pending sets.

The unavailable-database probe reads a deliberately absent temporary path. Failure/recovery presentation uses the controller's fixture seam; it does not demonstrate a production retry action. Production keeps a one-shot catalog future: correcting the underlying data requires a process restart after a failed load. That restart requirement follows from source inspection; this probe did not execute a process restart for recovery. When owned unknown passcodes remain searchable, they are shown and the empty-results failure caption is hidden.

The unavailable-image probe uses a separate manager, a unique temporary cache and a refused localhost URL. It checks visible placeholders, writes a tiny PNG only into that disposable cache, verifies the existing manager retains its failed-code placeholder, then recreates the manager and verifies the restored image loads. Production failures remain cached until process restart. No user cache is deleted, replaced or used for failure injection; the tiny fixture cache is retained at the path attached to the report.

## Remaining acceptance

All installed same-JAR friend checks remain pending, including owner/peer privacy, real item/offhand dispatch, invitations/RPS/first-choice races, disconnects, lifecycle restarts and incompatible-peer rejection. Single-client fixtures can cover only their stated portions of developer rows D1/D3/D4/D5. No two-player result or release sign-off is implied by local automation.

The existing lobby presentation feedback belongs to the separate UI design/style-guide pass. Automated bounds checks and screenshots do not replace human comfort or visual approval.

# Player interaction release evidence

**Dropdown acceptance, 2026-10-16:** the user confirmed readable lobby rule entries and working Escape behavior. The scoped dropdown recheck is complete. They authorized the remaining checks that need no multiplayer session; the full UI redesign and friends acceptance stay separate. The audit checkpoint below records the preceding batch.

## Local verification continuation

The subsequent [template cleanup](template-cleanup.md) removes the remaining example content and logging. Its M6 QA.2 candidate passed the targeted real-client checks and fresh full test/build; the template-resource warning exception is closed. Installed multiplayer acceptance remains pending.

The [local verification report](player-interaction-local-verification.md) records the subsequent client runs, reproduced card texture defect and candidate preparation. Its current results supersede the pending local checks in the historical audit below. Multiplayer acceptance and the full UI redesign remain separate.

## Historical audit checkpoint: 2026-10-16

M6 is in progress; Task 4's authority/compatibility audit and player documentation are complete after independent review. This checkpoint also includes test-only verification. The user deferred multiplayer acceptance until friends are available and kept the full UI design pass separate. No new build, client/server launch, code change, merge or push forms part of this audit. The remaining final-candidate gates below stay open.

Worktree: `.superpowers/worktrees/deck-editor`, branch `codex/qa-m4`. The audit began from clean `0d251daecd0c5769bdd82a13f5e11cc3ebcb75e3`. Its working diff consists of documentation and tracking updates; it does not change the source of the existing QA.2 artifact. See the [authority audit](player-interaction-audit.md), [collection guide](../player-collections.md), [multiplayer guide](../multiplayer-decks.md), and [remaining manual procedure](player-interaction-manual.md).

The comparison from artifact source `1dd8ee0` to audit baseline `0d251da` contains no changes under `src`, `build.gradle`, `gradle` or `gradle.properties`. This supports retaining the recorded source identity; it does not turn the existing JAR into a new build.

## Candidate identity and prerequisites

| Item | Observed value / evidence boundary |
| --- | --- |
| Existing runtime artifact | `build/libs/duelcraft-1.0.0-m5-qa.2.jar`; no rebuild on 2026-10-16 |
| SHA256 rechecked on 2026-10-16 | `2D9A2FEDCC6177C6E81AE455EECF1A9C402AB8756B72B43A2BBBB9E87AA22397` |
| Artifact source | `1dd8ee00c9fc64ef36a51f8b2b908add323dbe7f`, recorded by the [Oct 8 build report](../superpowers/reports/2026-10-08-m5-playtest.md); audit HEAD is a later documentation checkpoint |
| Mod / network | `1.0.0-m5-qa.2`, protocol **8**; M4 protocol-7 peers are incompatible |
| Minecraft / NeoForge / LDLib2 | `1.21.1` / `21.1.224` / `2.2.39.a` |
| Gradle / required Java | `9.2.1` / Java 21 |
| Local JDK configuration | `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.1.12-hotspot`; Java 21.0.1 confirmed there. PATH resolves Java 8; Gradle's launcher and daemon use Java 21.0.1 |
| Engine submodule | `122e0d091a0f399221a4510cc98a406ae485905f` |
| Source-resource JNI DLL SHA256 | `BCC0E48C02BEAEE705C0F34D6D015C8427C29F6713EA3EB525B7A5A44CF50F18` |
| Source-resource ocgcore DLL SHA256 | `15B9FE19E0755086CCADE2BDF4DFC73A063B4358F6EF93B3C23484B043C64EF1` |
| Native provenance limit | Existing Windows x64 resource DLLs; hashes identify files, not a fresh native compilation |
| EDOPro prerequisites | Configured `C:/ProjectIgnis/expansions/cards.cdb`, `C:/ProjectIgnis/script` and `C:/ProjectIgnis/script/official` exist; presence alone does not establish a new runtime pass |

The audit's fresh static-check outputs belong under `build/interaction-release/m6-audit-2026-10-16/`. They cover source/resource/document checks and artifact identity, not gameplay. No final candidate has been installed and accepted on two clients and a dedicated server in this checkpoint.

## Fresh audit checks

| Check | Result / evidence |
| --- | --- |
| Java version / Gradle version | Java 21.0.1 and Gradle 9.2.1 confirmed on Windows 11 amd64; `java-version.log`, `gradle-version-retry.log`. Initial sandbox Gradle attempt hit cache-lock access denial (`gradle-version.log`); elevated retry passed |
| Existing artifact/resources | Exit 0, `artifact-resources.json`: existing JAR is 17,330,713 bytes; SHA256 above matches; both bundled DLL hashes match source-resource files; expected Home/lobby/collection XML entries present |
| Resource syntax | Same report: parsed source 7 JSON + 4 XML files and packaged assets/data 7 JSON + 4 XML files without errors. Syntax/presence checks do not prove runtime resource loading |
| `gradlew.bat test --rerun` | Exit 0, `BUILD SUCCESSFUL` in 2m50s: 1,113 cases, 1,112 passed, zero failures/errors, one existing Windows symlink skip (`DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile()`). Fresh evidence: `test.log`, `test-summary.json`, copied `junit/` |

The test task executed once; native compilation/resources and the other 12 tasks were UP-TO-DATE. This was a fresh test-suite execution against the audit baseline, with no full build, replacement JAR or UI/client/server launch. Repeat final-candidate verification after any code change. Gradle retained the existing warning that deprecated build features will be incompatible with Gradle 10; changing the build is outside this audit. The elevated retry resolved the initial cache-lock access failure; no unresolved build blocker remains from that check.

Independent review approved the audit/documentation batch after correcting one explanation: deleting the open saved list blanks its draft; deleting another list keeps the current draft. The source audit found no production authority bypass requiring a code fix. Final document checks covered 10 documents and 257 local link targets with zero errors; `git diff --check` passed. Resource syntax/presence checks remain distinct from runtime loading. Reviewed guides/wiki/audit are committed locally as `1167103` (`docs: explain collection and duel interaction flows`); tracking updates follow separately. This documentation commit is distinct from QA.2 artifact source `1dd8ee0`. The local `AGENTS.md` guidance update follows the repository's existing ignore policy and is not included in the committed documentation.

## Accepted and historical evidence

| Date / source | Result | What remains distinct |
| --- | --- | --- |
| [Oct 7 M5 verification](../superpowers/reports/2026-10-07-player-entry-m5.md) | 1,113 JUnit cases: 1,112 passed, one existing platform skip; 57/57 UI scenarios, 12,227/12,227 checks; both-policy dedicated fixture journeys | QA.1 source/artifact, synthetic input and fixture-driven routes; no same-JAR human three-process acceptance |
| [Oct 8 dropdown correction](../superpowers/reports/2026-10-08-m5-playtest.md) | QA.2 `test build` passed with 1,112 passed and one existing skip; targeted real-screen dropdown regression passed 22 checks at each of four window/scale combinations | Targeted UI coverage; no new full-group or multiplayer pass |
| Oct 8 user playtest, recorded in the same report | User accepted all non-multiplayer M5 checks except lobby presentation and the rule dropdown | Exact installed hash was not independently checked. Keep accepted checks accepted; QA.2 dropdown human retest remains open |
| [QA.3 carry-forward](../superpowers/reports/2026-10-06-qa3-carry-forward.md) and older milestone reports | Preserve their dated results, worlds and failures | Their open final-candidate checks do not become current passes through this audit |

Local runtime evidence remains under `build/evidence/collection-m5/` and `build/evidence/m5-dropdown-2026-10-08/`. The reports identify captures and failed attempts. These directories are local build evidence, not committed release assets.

## Outstanding gates at the audit checkpoint

- Same identified JAR on both clients and a dedicated server: execute the carried two-player procedure, privacy, preparation locks/races, real client binder/mat/offhand dispatch, post-duel transfers, lifecycle and policy-restart checks in the [manual guide](player-interaction-manual.md). Record friend availability as pending, not failed or passed.
- Developer fixtures: forged/stale actions, companion denial/exception at startup, injected engine-start failure and cleanup, and incompatible-peer rejection. Keep fixture evidence separate from human outcomes.
- Final-candidate full build/tests and relevant UI regressions after the last code change; retain command outputs, source/diff identity and JAR hashes. The unrun full window/scale/non-16:9/resize matrix, managed-catalog performance and unavailable-data/image checks remain explicit M6 gates; previous partial coverage does not close them.
- Exact final-candidate identity and release sign-off. Task 4's audit/documentation review and static validation are complete; the remaining build/UI/multiplayer gates above stay open.

The lobby design, LDLib2 style guide and mockup-to-widget review belong to the separate full UI pass requested by the user. Functional bounds checks do not approve the lobby's visual design. Acquisition/economy, banlists, match play/sideboarding, broader legality and platform/CI expansion remain outside this integration. No release-complete claim follows from this documentation checkpoint.

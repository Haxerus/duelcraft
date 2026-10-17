# Template cleanup verification — 2026-10-16

The user authorized removal of the remaining mod-template content before the separate UI redesign. Work began from clean `d73e59e` on `codex/qa-m4`, in the existing deck-editor worktree. Source commit: `60e7e46`.

## Change

Removed the `duelcraft:example_block` block/item and `duelcraft:example_item`, their creative entries and translations, the unused dirt/magic-number config options, and common/server startup greeting logs. The creative tab is now `duelcraft:duelcraft`, displays **Duelcraft**, and uses the Card Binder icon. It retains the binder and Duel Mat entries. Commented template examples were removed from metadata and build configuration; active metadata and publishing behavior are unchanged.

These example registry IDs are retired, without remapping them to real Duelcraft content. NeoForge corrected the disposable client's old common config on first load after the unused options were removed. Card-data configuration and server lifecycle listeners remain intact. No ownership, transfer, duel policy, networking payload or UI layout changes were made.

## Fresh verification

All Gradle commands used `GRADLE_USER_HOME=C:/Users/haxer/.gradle`, Java 21.0.1, Minecraft 1.21.1, NeoForge 21.1.224 and LDLib2 2.2.39.a on Windows x64. The client used the disposable `run-collection-m3-ui` instance and a fresh harness world.

| Check | Result |
| --- | --- |
| `runClient -PldTest=duelcraft_entry_points -PcollectionTransferUi -PldTestWindow=1280x720 -PldTestGuiScale=2`, before cleanup | Exit 1, 1m 15s; exactly the three new registry/tab assertions failed |
| Same client command, after cleanup | Exit 0, 1m 15s; 1/1 scenario and 7/7 checks passed, including existing binder/mat/hotkey entry routes |
| Fresh client log comparison | Template resource warnings: 3 → 0; template startup log lines: 4 → 0; no missing Duelcraft resource warnings found |
| `test --rerun build` | Exit 0, 1m 2s; 1,113 cases, 1,112 passed, one existing Windows symlink skip, zero failures/errors |
| `build`, after final comment-only Gradle cleanup | Exit 0, 4s; tasks up-to-date |
| Resource/package inspection | Eight JSON and four XML resources parsed in source and JAR; physical-card atlas fix retained; packaged native DLL hashes match the preceding candidate |
| Focused independent source review | Approved; server lifecycle and card-data loading preserved; parsed metadata unchanged |

The skipped case is `DeckExportServiceTest.rejectsSymlinkTargetWithoutChangingOutsideFile()`. Remaining runtime messages include vanilla command/sound/shader warnings, an LDLib shader warning, the existing card texture mip-level notice and the first-load common-config correction. Gradle still reports deprecations. This closes the template-resource exception; it does not claim an entirely warning-free log.

Evidence: `build/interaction-release/template-cleanup-2026-10-16-235002/`, including red/green reports and client logs, `log-checks.json`, fresh JUnit results, build logs, `candidate.json` and `review.md`. The final two template-only Gradle comments noted by the reviewer were also removed, followed by the successful build above.

## Candidate and remaining acceptance

[duelcraft-1.0.0-m6-qa.2.jar](../../build/libs/duelcraft-1.0.0-m6-qa.2.jar): **17,351,214 bytes**, protocol **8**. SHA256: `E23AB7C6BCDFD8F559C66949DBDD74DE70F4CE6DBBA13F4473EBF8B81A39D416`. The source/build diff at `60e7e46` is empty. Native tasks were up-to-date; this is not a fresh native compilation claim.

The previous M6 QA.1 JAR and all earlier evidence are preserved. Its SHA256 remains `BA404D16FB5F94DAD405D7D35ACC40DF4F45A9E2E7E827B5DA45312412FCFFEA`. Ordinary and disposable SERVER config hashes remain unchanged. The ordinary development world was not opened.

This targeted run supplements the [preceding full UI/matrix verification](player-interaction-local-verification.md); that matrix was not repeated for this registration/config cleanup. Same-installed-JAR multiplayer acceptance remains pending in the [manual guide](player-interaction-manual.md). The full UI redesign remains separate. No merge, push or distribution occurred.

# M3 physical cards and collection transfers

User acceptance: the user completed the manual playtest list, reported no issues, and authorized M4 with subagent-driven development on 2026-09-19.

Completed on 2026-09-19 in `.superpowers/worktrees/deck-editor`, branch `codex/player-collections`, starting from clean `00b76ba`. M4–M6 remain later checkpoints. No merge or push was performed.

## Implementation

- `1cc975c` adds the registered `duelcraft:card`, persistent/network positive `duelcraft:card_code`, canonical stack helpers, existing card-back model and client-only cached-name/passcode tooltip. No component-less card is offered in the creative tab.
- `9157898` adds the pure inventory planner and server-thread transfer service. Main slots 0–35 and offhand 40 may deposit; only main slots receive withdrawals. Exact requested transfers are all-or-nothing. Bulk deposits move only supported stacks and count skipped copies. Customized stacks retain their objects and components. Unknown deposited IDs can be withdrawn.
- `f77f0e9` connects Deposit, Withdraw and DepositAll packets and editor controls; protocol changes from **5 to 6**. Current clients and servers must match. The editor distinguishes carried/deposited quantities, waits for authoritative snapshots, preserves the draft and selection, and reports capacity, stale, busy, missing and unsupported-card errors.
- The final `test: verify physical collection transfers` commit adds the operator grant, real-player fixtures, retained-world launcher and review fixes. `/duel card give <player> <passcode> <count>` requires level 2, a known playable passcode, count 1–4096, sufficient main-inventory capacity and an idle target. It never writes collection counts or drops overflow.

Transfers reuse `CollectionService.replaceCounts` and `DeckUsePolicy`. A valid mutation evaluates proposed deposited counts before committing either side. Ownership defaults false. Shortages alone retain an otherwise legal active deck in optional mode and clear activation in required mode. Companion denials clear activation in either mode; failed evaluation changes neither side. Saved lists remain. Deposits never activate a list or create copies. Acquisition and progression remain companion responsibilities.

Implementation details relative to the plan: the shared `SavedDeckController` retains acknowledgement/pending/snapshot handling; `CardTransferController` owns only transfer form/command construction. The existing `ClientCollectionState` assembler needed no schema change. The `Changed.skipped` model field landed with the service, then its wire encoding with task 3. This avoids a second transfer acknowledgement path. The adapter has an `Owner` seam shared by the real player boundary and tests. The grant uses the same planner and stack patch builder.

Independent read-only review found two issues, both fixed and re-reviewed: missing catalog IDs now have selectable owned-card placeholders, and arriving catalog metadata refreshes a selected placeholder without resetting inspector scroll on ordinary transfer refreshes. A BUSY rejection locks request controls until a successful authoritative Open restores readiness. A targeted regression proved this lock, followed by the passing full suite.

## Verification

All commands used `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Local evidence is under `build/evidence/collection-m3/`; reports/screenshots were copied before subsequent runs overwrote harness output. No prior M1/M2 evidence is claimed as M3 execution.

| Check | Measured result | Evidence |
| --- | --- | --- |
| Final `./gradlew.bat test --console=plain` | **930 tests, 58 suites; 0 failures/errors/skips**, 1m01s | `final-full-tests.log`, `final-full-test-xml/` |
| 1280×720, GUI scale 2: persistence, editor polish and transfers | **3/3 scenarios, 86/86 checks**, 1m36s | `ui-third.log`, `ui-scale2/` |
| 1920×1080, GUI scale 3: eligibility, overflow, editor polish and transfers | **4/4 scenarios, 188/188 checks**, 1m36s | `ui-scale3.log`, `ui-scale3/` |
| Dedicated A/B, ownership optional | **1/1 scenario, 28/28 checks**, 2m51s | `mp-default.log`, `mp-default/` |
| Dedicated A/B, ownership required | **1/1 scenario, 28/28 checks**, 3m12s | `mp-required.log`, `mp-required/` |
| New retained-world seed and same-JVM rejoin | **7/7 + 8/8 checks**, PID 9984 | `restart-seed.log`, `restart/seed/`, `restart/rejoin/` |
| Distinct-JVM restart/load | **8/8 checks**, PID 20668 | `restart-verify.log`, `restart/verify/` |

The initial full run passed 928 tests. The final 930 adds the BUSY UI lock regression and full registered ItemStack storage/network round trip. Targeted red/green logs cover missing item/transfer/packet/form/grant behavior and the unknown-owned-card and BUSY-control regressions. Task 1's three component tests are preserved in `task1/`; later item coverage has four tests. The unchanged baseline test invocation was up-to-date, not a fresh 904-test execution.

The real `collection_transfers` scenario saves an unowned sixteen-copy draft, uses Deposit20 and Withdraw5 in the actual editor, and observes physical/stored **20/0 → 0/20 → 5/15** on the server thread and synchronized client counts. It verifies the missing-copy indicator, unchanged list, full-capacity rejection, exact retry after freeing a slot, retained selected card/inspector scroll, main/offhand bulk deposits, an untouched named stack with four skipped copies, empty-bulk rejection, and recovery of owned `2147483647` without a saved list. A separate legal Main40/Extra1/Side2 fixture exercises both ownership modes with no hook, a real event-bus denial, and a throwing listener against actual player inventory. Each scenario restores captured inventory and attachment in teardown.

The dedicated scenario uses separate server/A/B JVMs. Both authenticated players deposit20, withdraw5, replay the original withdrawal revision, and withdraw a required active-list copy. Server assertions verify each owner's exact state, the opposite owner's unchanged attachment, no foreign replies at the client's receive boundary, mode-dependent activation, and saved-list retention. An actual two-player first-turn roll locks both participants; a transfer then returns BUSY with no mutation. Inventories, attachments and observers are restored in teardown. Both runs negotiate and use current protocol 6. A full old-binary disconnect-screen test remains outside this milestone.

The retained world is `run-collection-transfer-lifecycle/saves/collection_m3_transfers_20260919`, authenticated UUID `97c3d102-0b21-390b-ac46-3a403f2b999c`. Real packets produce revision2, fifteen stored passcode89631139 copies and five canonical physical copies. Normal save/rejoin and the separate JVM load preserve that exact result. This verifies Minecraft's normal save lifecycle, not durability across a forced crash during disk writes.

Actual screenshots at both requested sizes were inspected: transfer amount/controls, distinct quantity labels, neutral optional shortage, bulk skipped count, dark styling and centered deck feedback remain readable. Existing scale2/scale3 polish and overflow checks passed. New UI runs use `-PcollectionTransferUi`, selecting `run-collection-m3-ui`; the ordinary development client's ownership=true configuration was preserved.

Final commands:

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'
./gradlew.bat test --console=plain
./gradlew.bat runClient '-PldTest=collection_transfers,collection_persistence,collection_playtest_scale2' -PcollectionTransferUi -PldTestWindow=1280x720 -PldTestGuiScale=2 -PldTestWatchdogSec=240 --console=plain
./gradlew.bat runClient '-PldTest=collection_transfers,collection_playtest_scale3,collection_eligibility,collection_overflow' -PcollectionTransferUi -PldTestWindow=1920x1080 -PldTestGuiScale=3 -PldTestWatchdogSec=240 --console=plain
./gradlew.bat runMpTest -PldMpTest=collection_transfer_isolation -PldMpOwnership=default --console=plain
./gradlew.bat runMpTest -PldMpTest=collection_transfer_isolation -PldMpOwnership=required --console=plain
./gradlew.bat runClient -PcollectionTransferLifecycle=seed --console=plain
./gradlew.bat runClient -PcollectionTransferLifecycle=verify --console=plain
```

The lifecycle seed intentionally refuses an existing save/manifest. Preserve the completed world and evidence; use a separately archived attempt before reseeding. The verify phase requires the completed rejoin manifest. Multiplayer harness runs use disposable worlds, so archive its report before another run.

## Failed attempts and preserved history

- `task1/dedicated-attempt/`: the dedicated server loaded item/component registration successfully, but the original privacy scenario timed out waiting for a fresh client's catalog download. It reached seven passing checks; it did not verify M3 transfers. Subsequent isolated clients reused the completed server card-data snapshot and passed the new transfer scenario.
- `ui-first/`: the ordinary development configuration has ownership=true, incompatible with the older persistence fixture's default-mode expectation. That fixture also left a client revision above the new transfer fixture's initial revision. The M3 UI client is now isolated with default config; the transfer fixture seeds above the restored revision. The development config was not changed.
- `ui-second/`: a harness exact-text wait expected `20` while the correct UI displayed `In collection: 20`. Corrected waits and the Amount label's XML wiring passed in the final runs. Failed reports/screenshots remain available.
- Compilation attempts caught fixture API/command-builder mistakes before runtime; the final suite compiles all fixtures. Expected red test failures are retained separately and are not counted as passing evidence.

Original M2 retained-world SHA256 values still match the handoff: manifest `4BC6BAB42ACA13365FF2750045F2FB47998E06A7B7F187B341BB67673FFA2489`; level.dat `D28F2EA9A04FD2242A90D9DAE9C9008CF4F753FFCDB55BF4EA2897834FB30AE1`. Earlier policy and polish evidence remains in its original directories. No M3 acceptance check remains blocked.

## Manual playtest

1. In a test world with cheats/operator access, run `/duel card give <your-player-name> 89631139 20`. Hover the cards to see passcode/name and confirm the card-back item model. A nonoperator must not be able to use this command.
2. Open `/duel collection`, search `89631139`, select it, enter20 and Deposit. Confirm Carried0 and In collection20. Withdraw5; confirm Carried5/In collection15 and a server acknowledgement. Save an incomplete or sixteen-copy draft; saving must remain allowed.
3. Fill main inventory and try withdrawing; neither quantity should change. Free space and retry. Put canonical copies in offhand and use Deposit carried cards. Name another card stack with an anvil: it must remain untouched and be counted as skipped. Empty eligible bulk deposits must not advance revision.
4. With an otherwise legal active saved list, withdraw a required deposited copy. With ownership=false it stays active; after reopening the world with ownership=true it clears activation and retains the saved list, showing the withdrawal reason. Replenishing counts must not auto-activate. Additional companion denials apply in either mode; a failing hook must not move anything.
5. Close/reopen the world and verify physical plus deposited totals. On a dedicated server, repeat with two players and during a first-turn roll/live duel; the other player's collection must stay private and busy transfers must fail.

M4 still owns collection-backed duel preparation, imports and removal of legacy upload/command bypasses. M5 owns Home/hotkey/binder/mat entry points. M6 owns release acceptance. M3 completion does not certify current legacy duel-start routes for a collection-enforced public release.

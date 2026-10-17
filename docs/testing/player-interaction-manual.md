# Remaining player interaction acceptance

## Scope and outcome recording

The user accepted the non-multiplayer M5 checklist on 2026-10-08 except lobby presentation and the rule dropdown. Keep that acceptance; do not ask them to repeat the full single-player checklist. QA.2 fixes the dropdown with automated mouse-input coverage; its human recheck remains pending. The full lobby/UI design pass is separate. Multiplayer checks wait for friends. See [release evidence](player-interaction-release.md) for candidate identity and the historical reports.

For each run, record date, operator, exact source/diff, JAR SHA256 on both clients and server, player UUIDs, effective ownership setting, companion/version, window/GUI scale, result and evidence path. Use one identified protocol-8 Windows x64 candidate on all three processes with Java 21, Minecraft 1.21.1, NeoForge 21.1.224 and LDLib2 2.2.39.a. Preserve logs/screenshots and relevant before/after inventory, collection revision, saved-list and active-selection snapshots. Use a disposable world with both players' permission to seed inventories. Preserve existing worlds and evidence.

First run without a companion and ownership off, then restart with ownership on and repeat the affected sequences. Restart between companion/config changes and record the effective setting, including any world override. Seed physical cards with an operator's level-2 `/duel card give <player> <passcode> <count>` only where needed; this is test setup, not an acquisition feature. Follow the [collection](../player-collections.md) and [multiplayer](../multiplayer-decks.md) guides for supported controls.

Current outcome for all unrun rows: **pending**. No friend session or developer failure fixture ran during the 2026-10-16 audit. Record pass/fail and a concrete evidence path per row when executed; automated historical evidence alone does not change the outcome column.

## Focused human recheck

| Procedure | Expected result | Outcome / evidence |
| --- | --- | --- |
| On QA.2, open lobby Settings; select a middle rule, scroll to RUSH and select it; press Escape twice | Readable entries and matching click targets; first Escape closes dropdown, next closes Settings | Pending human retest; Oct 8 automated regression passed |

## Friend session on the same installed candidate

These rows consolidate M6 Task 3 and critical carried multiplayer checks. Check the integration between two players rather than reopening accepted single-player controls.

| ID / sequence | Expected result | Outcome / evidence |
| --- | --- | --- |
| H1: Alice/Bob join without binder/mat; J -> Collection/Duels; use the same placed mat and each binder in main/offhand | Menus need no item; each sees own data; ordinary client clicks, occupied hands and binder-on-mat precedence open one route only; breaking mat preserves data | Pending friends |
| H2: Save legal missing-card lists; two lists share copies | Drafts persist; ownership off permits eligible activation; ownership on reports shortages; selection consumes/reserves no cards | Pending friends |
| H3: Grant 20 canonical copies, deposit 20, withdraw 5; try inventory with no capacity for the requested copies and a supported card beside an unsupported customized stack | Physical 5 + collection 15 = 20, no drops; failed capacity changes no count/revision; unsupported stack remains with explanation | Pending friends |
| H4: Activate, withdraw a required copy, deposit it again | Off/no hook keeps selection; on clears selection while preserving list; shortage resolves after deposit without automatic selection | Pending friends |
| H5: Invite; sender withdraws required copy; target accepts | Ownership on rejects acceptance; off/no hook permits otherwise eligible use | Pending friends |
| H6: Simultaneous/second invitation, offline target, expiry, Decline/Cancel; close and reopen lobby | Conflicts explain failure without replacing another flow; closing alone leaves invitation pending; completion/cancel clears correct locks | Pending friends |
| H7: Receive invitation while editing, then accept preparation; cancel and Resume draft; observe a late Save acknowledgement | Pending invite preserves dirty draft; accepted flow suspends it; Resume returns after cancellation; late reply cannot replace preparation/duel | Pending friends |
| H8: Accept through Home and through mat; try transfer/save/clear/activation through existing screens and commands during RPS, first choice and duel | Server redirects entry and rejects mutation as BUSY through the full preparation/live interval | Pending friends; forged requests belong to D1 |
| H9: Tie RPS, then winner chooses second; repeat first choice; close/reopen preparation | Correct player/round/seat; no opponent hidden cards or private policy reason exposed; flow remains usable | Pending friends |
| H10: Cancel, expire and disconnect during preparation; invite again | Both locks release; no orphan session; saved data stays intact | Pending friends |
| H11: Finish a real normal-win duel, concede another, disconnect in another; return Home and withdraw afterward | Result/cleanup works; eligible selection and lists survive; collection and transfers unlock | Pending friends |
| H12: Death/respawn, End return, dimension change, logout/rejoin and full server restart | Deposited data/lists persist; physical cards follow ordinary inventory rules; unsaved local drafts clear on disconnect | Pending friends |
| H13: Restart with ownership enabled/restrictive companion, then disabled/removed | Next use obeys current policy; no bypass by persisted activation, no list/count loss, collection credit or automatic reactivation | Pending friends; install test companion with developer |
| H14: Companion denial under both ownership settings, seen by owner and peer | Human startup stops and owner sees reason; other player sees no private reason | Pending friends; developer supplies hook |
| H15: Local YDK import with Side and unknown passcode during the shared session | List retains Side/placeholder, grants no ownership; invalid activation explains rejection | Pending friends if not already covered on the identified candidate |

Record only newly observed readability/input defects and their window/scale. The accepted single-player checklist stays accepted. Broader inspector/layout/typography redesign belongs to the separate UI pass; correctness findings still require a reproduction and owning-milestone fix.

## Developer-only fixture checks

Friends do not need to forge packets, configure failure injection or inspect internal snapshots. Run these in disposable developer fixtures; keep any injection DEV_ONLY and provide evidence separately.

| ID / procedure | Expected result | Outcome / evidence |
| --- | --- | --- |
| D1: Replay transfer/save/clear/activation/upload actions from stale screens through accepted preparation, first selection, STARTING and live duel | Authenticated busy gate rejects mutation; immutable prepared decks remain unchanged | Pending final candidate |
| D2: Send an old flow/round action after a new preparation starts; forge Alice's request with Bob's list ID/snapshot target | Old action rejected without disturbing current flow; no other-player data read or mutation | Pending final candidate |
| D3: Companion approves illegal/unowned deck with ownership on; denies or throws during final startup recheck under both settings | Core rejection wins; denial/failure prevents startup; private reason, safe cleanup and no inventory/count change | Pending final candidate |
| D4: Inject engine-start failure at startup boundary, then send valid invitation | Both players leave STARTING; partial sessions close and screens clear failed state; later invitation works | Pending final candidate |
| D5: Solo human empty collection with default off, then owned list with on; companion denial/failure in both modes | Human follows shared policy; AI requires no collection; hook failure cannot start human duel | Pending affected final-candidate gate; preserve accepted single-player evidence |
| D6: Connect incompatible network peer | Clear incompatibility rejection; current protocol is 8 (original M6 plan's expected 4 is historical) | Pending final candidate |

## Sign-off

After the last code change, rerun affected checks on the rebuilt candidate and record its identity. The [M6 plan](../superpowers/plans/2026-09-17-interaction-release-milestone-6.md) retains full build/test, UI matrix, catalog/failure checks and final review gates. Use explicit pending/failed results when access or fixtures are unavailable. A friend session, a developer fixture and historical acceptance provide different evidence; retain all three with their actual limits.

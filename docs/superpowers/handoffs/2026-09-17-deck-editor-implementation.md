# Collection and deck editor implementation handoff

Updated 2026-09-19. This replaces the original M1 startup handoff. **M3 is implemented, verified and user-accepted after all manual checks passed. M4 is authorized and in progress using subagent-driven development; M5–M6 remain later checkpoints.** The user has accepted the M2 follow-up and the final editor polish after manual playtesting. Continue the approved design and implementation plans. See the [M3 verification report and manual playtest](../reports/2026-09-19-card-transfers-m3.md) for the completed transfer work.

## Workspace and starting point

- Implementation worktree: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`
- Branch: `codex/player-collections`
- Pre-M3 baseline: `b407f7b` (`Polish deck editor feedback and add type sorting`), followed by handoff commit `00b76ba`. The M3 completion addendum below records subsequent work; inspect current HEAD before editing.
- Main checkout: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft`. It is not the implementation starting point. Work directly in the existing worktree; do not restart from main or the old `deck-editor-m1` worktree.
- This handoff and its [kickoff prompt](2026-09-17-deck-editor-kickoff.md) live in the implementation worktree. Use the absolute worktree paths when opening them from a fresh session.
- Check `git status`, branch, and HEAD before editing. The polish is committed; preserve any later user changes. No merge or push has been requested.

Read in this order:

1. Repository `AGENTS.md` and any applicable worktree instructions.
2. This handoff and the [ownership amendment](2026-09-18-optional-ownership-amendment.md).
3. [Design specification](../specs/2026-09-15-player-interaction-design.md) and [implementation contracts](../specs/2026-09-17-player-interaction-contracts.md).
4. [Integration roadmap](../plans/2026-09-15-player-interaction-roadmap.md).
5. Completed [M3 implementation plan](../plans/2026-09-17-card-transfers-milestone-3.md) and its [verification report](../reports/2026-09-19-card-transfers-m3.md). M4 is now authorized: [executable preparation plan](../plans/2026-09-17-duel-preparation-milestone-4.md).
6. [M2 policy follow-up report](../reports/2026-09-19-deck-use-policy-follow-up.md), for verified policy behavior and runtime test setup.

The ownership amendment supersedes older unconditional ownership language. The implemented dark Minecraft UI and subsequent user feedback supersede the early HTML prototype's visual styling. Consult current source for API details; plan snippets are contracts, not code to paste unchanged.

## M3 completion addendum

M3 commits begin with `1cc975c` (item), `9157898` (atomic service), and `f77f0e9` (packets/editor); the final task adds grant/runtime fixtures and review fixes. Final verification passed 930 unit/JNI tests, 86 scale2 UI checks, 188 scale3 UI checks, 28 dedicated checks per ownership mode, and 23 retained-world/restart checks. Evidence: `build/evidence/collection-m3/`. The original worktree and earlier evidence/worlds are preserved. No merge or push. The next checkpoint is M4, which has not started.

## Completed work

**M1:** The 1280x720 LDLib2 collection/deck editor, local deck model, rich card filtering/search, virtual collection rows, and real-screen test fixtures. Its approved styling matches the existing dark duel UI. [M1 report](../reports/2026-09-17-deck-editor-m1.md).

**M2:** Per-player persistent collections, saved deck lists, explicit activation, private/revisioned networking, production card metadata/search, and persistence/privacy validation. [M2 report](../reports/2026-09-17-player-collections-m2.md).

**M2 policy follow-up:** Core optional ownership enforcement, shared `DeckUsePolicy`, the deny-only companion event, authoritative policy reports, neutral optional shortages, and persisted-active revalidation on Open. The development collection command passes its first fresh snapshot into the screen, retaining any active-clearance explanation. Independent reviews and runtime checks are complete. [Plan](../plans/2026-09-19-deck-use-policy-follow-up.md) and [report](../reports/2026-09-19-deck-use-policy-follow-up.md).

**Editor playtest fixes:** Deck/Side editing routes Extra Deck monsters automatically; dropdowns work; scrolling avoids repeated artwork rebuilds; selection is subtle; Saved lists and Save list have equal dimensions; scrollbar arrows have usable dimensions; the inspector uses cropped artwork and a quiet unselected state; tokens are excluded; alternate-format cards have a toggle; missing images use the card back; the deck name is the heading. [Earlier fixes report](../reports/2026-09-18-collection-editor-playtest-fixes.md).

**Latest polish (`b407f7b`):** Centered ownership feedback and Missing badges; localized saved-list help for the effective ownership mode; added Sort deck in the Main Deck header. Sorting groups Normal Monsters, then other monsters (including Effect Monsters), then Spells, then Traps. It preserves order within each group and keeps Main/Extra/Side separate; unknown metadata sorts last. Sorting changes only the draft, requires a save to persist, and is blocked during pending operations. The user approved the result.

## Product rules to preserve

- Use **collection**, not archive. Each player owns their world/server collection, like an Ender Chest. Physical storage pressure and poor binder searching were the original pain points.
- Preserve the dark duel-UI styling, three-column editor, 1280x720 design canvas, Standard/Large density, independent scrolling, and existing filters. The duel screen retains its own layout/scaling.
- Decks are lists. Saving/editing a well-formed draft does not require ownership or playability. Lists can share the same deposited copies without consuming or reserving them.
- Core SERVER setting `requireCardOwnership` defaults to `false`. With ownership off, an otherwise eligible list can activate without deposited copies. With ownership on, count deposited copies only, by exact passcode, across Main + Extra + Side, including single duels. Inventory cards do not count until deposited.
- Supported legality remains mandatory for activation/use in either mode. The synchronous `DeckUseCheckEvent` on `NeoForge.EVENT_BUS` allows companion mods to deny additional use in either mode; they cannot override core legality, enabled ownership, authentication, or busy checks.
- Evaluate proposed post-mutation state. An ordinary policy denial after a valid mutation clears active selection and preserves the mutation and saved list. A failed policy evaluation rejects the mutation without changing inventory or collection. Keep actual denial reasons distinct from neutral optional shortages.
- A withdrawal-created shortage alone clears an otherwise valid active deck only when ownership is required. A companion denial can clear it in either mode. Withdrawals always require real stored copies and inventory capacity. Deposits do not auto-activate a deck.
- Keep transfers and deck changes locked during preparation/live duels, with server enforcement. M3 must respect existing busy gates; M4 owns the full preparation integration and legacy duel-route cutover.
- Configuration changes require restart/world reopening. The server captures the effective setting for its lifecycle. Revalidate persisted activation after config/companion changes; removing a restriction does not auto-reactivate a deck.
- Acquisition, packs, rewards, progression, and extra restrictions belong to the companion. The user will handle acquisition later; it is not a blocking design question for M3.
- Later entry points remain: binder opens collection/deckbuilding; duel mat opens invitation/preparation; remappable hotkey opens Home, so players need not carry items.

## M3 scope and remaining milestones

| Milestone | Status and implementation plan |
| --- | --- |
| M3: Physical cards and transfers | **Complete, verified.** [Report](../reports/2026-09-19-card-transfers-m3.md). [Plan](../plans/2026-09-17-card-transfers-milestone-3.md): canonical passcode card item; inventory transaction planner/service; transfer packets and editor controls; restricted card-grant command and real inventory scenarios. |
| M4: Duel preparation and import | Not implemented. [Plan](../plans/2026-09-17-duel-preparation-milestone-4.md): shared preparation service, YDK import retaining Side, immutable prepared decks, actual solo/multiplayer policy checks, and removal of legacy bypass paths. |
| M5: Player entry points | Not implemented. [Plan](../plans/2026-09-17-player-entry-points-milestone-5.md): Home/hotkey, binder, mat, private lobby and state-aware navigation. |
| M6: Release validation | Not implemented. [Plan](../plans/2026-09-17-interaction-release-milestone-6.md): full UI/runtime matrix, dedicated two-player acceptance, compatibility and documentation audit. |

M3 completed all four tasks, including conservation/capacity/revision tests, real inventory UI flows, retained-world restart checks, and dedicated-player isolation. Transfers use the existing `CollectionService` and shared policy. Preserve the contracts for accessible slots, canonical components, amount bounds, and all-or-nothing transfers.

M3 bumped the request/reply protocol from `5` to `6`; clients and servers must match. Transfer controls are connected in the existing editor. The physical item reuses the existing card-back texture.

M3 checkboxes, exact verification evidence and manual checks are complete. Keep M4–M6 as separate milestone checkpoints. M3 completion does not certify legacy duel-start routes: actual collection-backed duel enforcement and bypass removal belong to M4. No public collection-enforced release before that cutover.

## Historical pre-M3 verification baseline

Final polish verification on 2026-09-19:

- `./gradlew.bat test --console=plain`: **904 tests in 53 suites, zero failures/errors/skips**. The full execution passed in 2m19s. A pre-commit invocation subsequently succeeded with all tasks up to date; it was not a second execution of the tests.
- `./gradlew.bat runClient -PldTest=collection_playtest_scale2,collection_eligibility -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain`: **2/2 scenarios, 69/69 checks**.
- `./gradlew.bat runClient -PldTest=collection_playtest_scale3,collection_eligibility,collection_overflow -PldTestWindow=1920x1080 -PldTestGuiScale=3 --console=plain`: **3/3 scenarios, 157/157 checks**.
- Inspected actual Minecraft screenshots for sorting, centered badges/footer, optional/required localized help, and warning/overflow layouts. The user then reported that everything looked good.
- `git diff --check` passed before the polish commit.

Local evidence root: `build/evidence/editor-polish-2026-09-19/`. It contains `tests.log`, `full-test-xml/`, `scale2/` and `scale3/` reports/screenshots, and their command logs. `first-scale2/` retains an initial test-harness failure: the newly added modal test tried to release a click on a button covered by the dialog it opened. The corrected press/release sequence passed; no production correction was needed for that failure. These local artifacts are not committed binaries.

Earlier M2 policy evidence is separate: `build/evidence/deck-use-policy/task-3/` and the linked report record 60 retained-world lifecycle checks and 61 dedicated multiplayer checks in each ownership mode, along with UI/persistence checks. Do not describe those historical runs as new M3 transfer evidence. Preserve retained worlds and reports.

## Working and testing notes

Run from the implementation worktree with PowerShell:

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'
./gradlew.bat test --console=plain
```

Use the shared Gradle cache; do not create a worktree-local `.gradle-user`. Native DLLs/build setup are already present here. Tests use the configured EDOPro data (`C:/ProjectIgnis/expansions/cards.cdb`, `C:/ProjectIgnis/script`, `C:/ProjectIgnis/script/official`). A separate fresh worktree needs the submodule/native setup in AGENTS.md; reusing this one avoids that setup.

The current development entry point is `/duel collection`. M3 connects Deposit/Withdraw/Deposit carried cards and adds the level-2 `/duel card give <player> <passcode> <count>` command. Current channel protocol is 6. The production hotkey, binder, and mat belong to M5.

LDLib2 source is `C:/Users/haxer/Documents/Programming/Modding/LDLib2`, not a sibling of this nested worktree. UI guidance: `docs/ldlib2-ui-guide.md` and `docs/ui-wiring-guide.md`. For engine-related changes, begin with `docs/engine-wiki/README.md`.

The SERVER config normally loads from `<gameDir>/config/duelcraft-server.toml`; an existing `<world>/serverconfig/duelcraft-server.toml` override takes precedence. Consult the policy report for verified lifecycle test setup rather than guessing the active config file.

LDLib UI runs overwrite `build/ldlib2-uitest`. Copy reports/screenshots into a distinct evidence directory before the next run. Use disposable fixtures for new transfer tests and preserve existing retained-world evidence. Report unrun or blocked checks explicitly. Keep changes scoped, commit milestone work, and do not merge or push without a request.

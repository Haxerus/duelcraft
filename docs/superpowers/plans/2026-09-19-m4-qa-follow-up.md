# M4 QA Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the approved friend-playtest feedback and produce a verified M4 QA.2 checkpoint before M5.

**Architecture:** Three independently reviewable tracks cover duel visibility/UI, preparation notifications, and collection-editor UX/export. Preserve the existing authoritative services and client models; integrate the tracks through one runtime and packaging checkpoint.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, ygopro-core API 11.0, Windows x64.

**Spec:** [Approved QA follow-up design](../specs/2026-09-19-m4-qa-follow-up-design.md).

## Global Constraints

- Work in `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`; baseline branch `codex/qa-m4`, HEAD `1e95c277a1de7c8a014b77df1bd7df7744ee8365`. Preserve later user changes.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. Keep Minecraft 1.21.1, NeoForge 21.1.224, Java 21 and LDLib2 2.2.39.a. Native deployment remains Windows x64.
- Preserve the dark duel UI and 1280x720 editor, filters, Deck/Side routing, sorting, quiet empty inspector and centered feedback.
- Core SERVER `requireCardOwnership` defaults false. Enabled ownership counts deposited exact-passcode copies across Main/Extra/Side. Legality and companion restrictions apply in either mode; drafts remain savable without eligibility. Preserve authentication, revisions, busy checks and atomic transfers. Deposits never auto-activate or create copies.
- Protocol baseline is 7; bump only for incompatible payload shapes. No native submodule/dependency upgrade, M5/M6 implementation, merge or push.
- Preserve historical evidence. Runtime checks that cannot run remain unchecked with their blocker recorded.

## Read first and establish execution state

Read `AGENTS.md`, [current handoff](../handoffs/2026-09-17-deck-editor-implementation.md), [interaction contracts](../specs/2026-09-17-player-interaction-contracts.md), [ownership amendment](../handoffs/2026-09-18-optional-ownership-amendment.md), [M4 plan](2026-09-17-duel-preparation-milestone-4.md), and [QA.1 guide](../../qa-m4.md). Do not reset to the planning SHA or return to `codex/player-collections` merely because older plans name it.

```powershell
Set-Location 'C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor'
$env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'
git branch --show-current
git rev-parse HEAD
git status --short
git submodule status
```

Planning was read-only until these documents were written. Source observations below are leads, not claims of reproduced runtime failures. The historical baseline is 1,007 passing tests in 64 suites and M4's 286 final runtime checks; these do not establish that the QA follow-up passes.

## File and task ownership

| Track | Owning files | Deliverable / order |
|---|---|---|
| [A: Duel visibility and UI](2026-09-19-qa-duel-visibility-ui.md) | `duel/MessageSanitizer.java`, `RefreshSchedule.java`, client state/prompt/panel controllers, duel XML, engine wiki and associated tests | A1 identity/public-state regressions; A2 prompt-owned reveals; A3 right panels; A4 banish/material inspection |
| [B: Preparation notifications](2026-09-19-qa-preparation-notifications.md) | `server/DuelManager.java`, preparation regression tests and dedicated scenario | B1 one flow and one announcement per actionable stage/round |
| [C: Collection editor](2026-09-19-qa-collection-editor.md) | `client/collection/`, `collection/DeckListWriter.java`, collection XML, tests | C1 copy cap/gestures; C2 startup/close; C3 save management; C4 YDK export |
| Integration below | `gradle.properties`, `docs/qa-m4-qa2.md`, verification report, existing handoff; protocol registration only if needed | I1 combined runtime evidence and QA.2 packaging |

Java paths in this table are relative to `src/main/java/com/haxerus/duelcraft/`; child plans give exact prefixes and file maps. Shared localization files must be integrated by one worker at a time. A1/A2 share client state; A3/A4 share duel controllers; all C tasks share the collection controller. Do not let concurrent agents edit those shared files. Track B can run independently of A; C can run independently after its worker has read the same accepted spec. Use fresh implementation workers and spec/code review gates per task, as the user's preferred subagent-driven execution workflow requires.

## Checkpoints

- [x] A1: source-backed hidden/public identity handling, with red/green regressions and engine trace.
- [ ] B1: invitation/notification cardinality, including tie rounds and dedicated evidence.
- [ ] A2: reveal/selection integration, original candidate indices and read-only reveal presentation.
- [ ] A3: independent left details and right stacked zone/log panels.
- [ ] A4: face-down banish badges and read-only Materials action.
- [ ] C1: aggregate copy cap and pointer gestures.
- [ ] C2: initial active selection, empty draft dismissal and layered Escape/Close.
- [ ] C3: simplified My decks and explicit Save lifecycle.
- [ ] C4: safe client-local YDK export.
- [ ] I1: full regression, packaged QA.2 and documentation below.

A1/B1 are the correctness priority. Do not close either using screenshots alone. Update both this index and each task's detailed checkboxes as evidence arrives.

## Task I1: Verify and package the combined checkpoint

**Files:** Modify `gradle.properties` and `docs/superpowers/handoffs/2026-09-17-deck-editor-implementation.md`. Create `docs/qa-m4-qa2.md` and `docs/superpowers/reports/2026-09-19-m4-qa-follow-up-verification.md`. Modify `src/main/java/com/haxerus/duelcraft/server/DuelNetworking.java` only if an implemented track actually changes wire compatibility. Keep `docs/qa-m4.md` as the historical QA.1 guide.

**Interfaces:** Consumes the completed A/B/C contracts and scenario names from the linked plans. Produces version `1.0.0-m4-qa.2`, an exact source-commit reference, SHA-256 manifest, a Windows x64 ZIP containing both pinned mod JARs, and a report distinguishing each verification layer.

- [ ] Review the combined diff and all task review findings. Ensure debug fixtures are DEV_ONLY, client classes do not load on a dedicated server, localization keys exist, and no raw hidden identities were added to public logs or packets. Resolve shared-file integration issues before full testing.
- [ ] Run the complete unit/native suite and real UI group once after integration. Archive each report before the next runtime overwrites its output:

  ```powershell
  $env:GRADLE_USER_HOME = 'C:/Users/haxer/.gradle'
  ./gradlew.bat test
  ./gradlew.bat runClient -PldTest=group:duelcraft
  ```

  Expected: all tests pass, zero unexpected skips, real Minecraft scenarios complete without clipping/overlap or privacy failures. Count actual tests and checks; do not reuse or predict the historical count. Preserve logs/screenshots under `build/evidence/qa-m4-follow-up/` with separate run directories and a source SHA. Include scales 2 and 3 and each existing rule-layout scenario. Do not erase old failures to make the report appear clean.
- [ ] Run A's dedicated native visibility scenario and B's dedicated notification scenario under both policy modes using their child-plan commands. Then run the existing dedicated regressions separately, preserving each report before continuing. `collection_duel_policy_mp` must run alone because it deliberately disconnects clients:

  ```powershell
  ./gradlew.bat runMpTest -PldMpTest=duel_preparation_commands -PldMpOwnership=default
  ./gradlew.bat runMpTest -PldMpTest=duel_preparation_commands -PldMpOwnership=required
  ./gradlew.bat runMpTest -PldMpTest=collection_transfer_isolation -PldMpOwnership=default
  ./gradlew.bat runMpTest -PldMpTest=collection_transfer_isolation -PldMpOwnership=required
  ./gradlew.bat runMpTest -PldMpTest=collection_duel_policy_mp -PldMpOwnership=default
  ./gradlew.bat runMpTest -PldMpTest=collection_duel_policy_mp -PldMpOwnership=required
  ```

  Keep integrated, dedicated and synthetic outcomes separate.
- [ ] Recheck fresh editor startup after leaving/rejoining, and after stopping/restarting the server JVM: active saved list loads under ownership off; missing ownership under ownership on clears activation but keeps the saved list and opens a blank draft. Confirm that valid required ownership preserves activation. Exercise suspended dirty editor restoration during invite acceptance/cancellation, and disconnect disposal. Use disposable worlds, preserving M2/M3/M4 restart evidence. Record each server/client PID and effective config path; changing a file without restarting is not a restart check.
- [ ] Review protocol compatibility against baseline 7. Keep 7 for internal-only changes. If wire shapes changed, bump the current registrar version, test codec round trips and actual old-client rejection, and put the new number in the QA guide. Do not imply a version bump fixes privacy logic.
- [ ] Set `mod_version=1.0.0-m4-qa.2`, run `./gradlew.bat build`, inspect the JAR for native DLLs/SQLite and pinned dependency metadata, and record actual build results. Keep QA.1 artifacts intact. Stage only reviewed source/docs/version files and commit `build: prepare M4 QA follow-up checkpoint`; do not use a blanket `git add .`.
- [ ] Assemble `build/distributions/duelcraft-m4-qa.2-windows-x64.zip` with `mods/duelcraft-1.0.0-m4-qa.2.jar`, `mods/ldlib2-neoforge-1.21.1-2.2.39.a-all.jar`, README, `BUILD-INFO.txt` and `SHA256SUMS.txt`. Obtain SHA-256 with `Get-FileHash -Algorithm SHA256`; identify the build's exact committed source and record whether its tree was clean. Use the README content from the new QA guide. Do not upload the ZIP or push the branch.
- [ ] Boot the packaged JARs in separate Windows x64 client and dedicated-server test instances with Java 21/NeoForge 21.1.224. Test the QA collection entry point, one export/reimport and a two-client duel. Verify DLL loading without relying on the development native-library path. Existing native-only smoke evidence cannot substitute for this launch. If launcher access, a second account or runtime prerequisites block this, record the precise gap and leave packaged acceptance open.
- [ ] Write `docs/qa-m4-qa2.md` with installation requirements, exact build/protocol, changes, known limitations and the manual steps below. Add the verification report link to the handoff and update task checkboxes only for observed results. Commit the evidence documentation separately if it postdates the build commit; retain the build commit in BUILD-INFO.

### Manual friend-playtest checklist for the new guide

1. Open `/duel collection` with an active deck; confirm its name, cards and Active badge. Close a fresh empty draft; then empty a saved deck and confirm its unsaved-changes decision still appears.
2. Add three copies split across Main/Extra/Side where applicable; try a fourth by button and double click. Import a four-copy list and confirm it is preserved as a draft, removable and rejected for activation by supported legality. Test natural Extra routing and right-click the final duplicate in an interleaved list.
3. Open each dropdown/submenu/dialog; press Escape once and verify only that layer closes. Use visible Close on the main editor, choose Keep editing, then Save and close. Repeat while a save is pending.
4. New, Save, Make a copy, Rename, Import and Delete through My decks. Saving must not activate. Export each section to YDK, reimport it, compare order/counts, and exercise cancel/confirm overwrite. Saved server lists and deposited quantities stay unchanged by export.
5. Run Painful Choice, the recorded opponent Extra Deck inspection fixture, and Ceremonial Bell from both seats. Confirm art/names in the choice itself, no selection reveal in the zone inspector, correct chosen card despite a different reveal order, and backs after visibility is revoked or identity is shuffled. Inspect both clients for unintended disclosures.
6. Open zone inspector and log together, hover a card to show left details, scroll both right panels, and hide/reopen each. Repeat at scales 2/3.
7. Repeated/crossed invites, accept, choose one RPS hand, then the other. Count prompts: one per round/player, with exactly one new pair after a tie. Cancel/disconnect, start a new flow, and verify no stale state or lock survives.
8. Banish face-down, inspect both perspectives, then change turn. Verify the Face-down marker without gaining unauthorized artwork. Open an Xyz host's Materials action, detach a material and move the host; the list updates or closes and no gameplay choice fires.
9. Rejoin and restart for saved/active persistence under both ownership modes. Finish a duel, cancel preparation and transfer cards afterward to verify locks release.

## Planning review

Coverage: original feedback 1/4 → C1; 2/3 and added Close/Escape → C2; 6 → C3; 7 → C4; 5 plus revised panel layout → A3; 8 and the broader hidden-information clarification → A1/A2; 9 → B1; 10/11 → A4. I1 owns combined QA and distribution. Execution remains pending; creating these documents does not mark any implementation or runtime check complete.

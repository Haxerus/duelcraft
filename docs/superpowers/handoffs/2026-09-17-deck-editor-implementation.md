# Collection and deck editor implementation handoff

## Initial Gallery playtest corrections (2026-10-18)

User feedback accepted the Gallery direction and identified stretched Collection inspector artwork and an opaque live-duel backdrop. The inspector is now square; full-window and canvas fills are removed from the duel while keeping Gallery board/panel surfaces. The proof now displays cropped artwork and checks its bounds. [Correction report](../reports/2026-10-18-gallery-playtest-corrections.md) records focused real-client passes, visual review and the replacement test JAR. Remaining human checklist items and multiplayer checks are not inferred complete.

## Gallery production migration complete (2026-10-17)

Production Collection, Home, Duel lobby and live-duel presentation now use the approved Gallery design on `codex/ui-redesign`, through source commit `0cc5f3f`. Existing collection authority, preparation workflows, engine/network contracts and field topology are preserved. The earlier “production unchanged” checkpoints below are historical.

The [migration report](../reports/2026-10-17-gallery-production-migration.md) records the scoped commits, manual playtest steps and exact evidence. Final build: 1112 JUnit passes, one existing Windows symlink-privilege skip. All 58 regression scenarios and three Gallery proofs have passing executions on the final source across the combined run and unchanged-source reruns (17450 checks). The combined run itself remains recorded as failed: its native window was iconified, preventing resize and plausibly invalidating synthetic hover; all affected checks passed in fresh clients. Both resolution runs passed 5/5 scenarios and 6569/6569 checks at 1024×768/2 and 1920×1080/4. Independent source review approved the corrections; independent visual review opened 46 native images with no blocking defect in the reviewed scope.

Agent implementation and verification are complete. Human aesthetic approval remains separate. Multiplayer/installed-JAR playtests remain carried forward and nonblocking. Keep harness windows unminimized; consistent iconification recovery is a future tooling follow-up. No merge or push. Branch and worktree are preserved.
## Current Gallery shared-inset correction (2026-10-17)

Fields, selected dropdown values and both short/scrolled popup layouts now share one Gallery inset declaration per design canvas. The Selector's explicit left edge falls back to that rule, and the collection's Gallery class lives on the actual UI root so detached popups inherit it. Production UI remains unchanged. [Evidence](../reports/2026-10-17-gallery-shared-control-inset.md): two final native runs passed 6/6 scenario executions and 8722/8722 checks, with independent screenshot review finding no issues. Human review and production migration remain separate. Earlier checkpoints retain their historical results.

## Current Gallery field and scrollbar correction (2026-10-17)

User review exposed two incorrectly specified component defaults: two-unit editable padding and stock shaded scrollbar textures with tracks one-third of arrow width. Gallery fields now have explicit 10/7.5-unit horizontal insets; scrollbars use matching 12/9-unit widths, flat Gallery colors and authored unshaded arrow silhouettes. Production UI remains unchanged. [Correction evidence](../reports/2026-10-17-gallery-field-scrollbar-correction.md) records red checks, a visually rejected intermediate stock mask, final native runs at 1280×720 and 1024×768 (6/6 scenario executions, 8570/8570 checks), and independent visual approval. Human aesthetic review and production migration remain separate checkpoints.

## Current Gallery layout refinement (2026-10-17)

Following user review, the Gallery collection proof restores full-width stacked Main/Extra/Side sections with shared card spacing and centered headings. The lobby header now groups Home with its title, deck/setup actions together, and Help/Close after a divider. Production UI remains unchanged. [Refinement evidence](../reports/2026-10-17-gallery-layout-refinement.md) records the red checks, two passing real-client runs (4/4 scenario executions, 7930/7930 checks), live resize and independent screenshot review with no actionable findings. Human review of this refinement and production migration remain separate checkpoints. Earlier proof evidence below remains historical.

## Current Gallery final-fix checkpoint (2026-10-17)

The four whole-change review findings are corrected in fixture/tooling scope: 1.25-unit straight borders survive fractional scaling; exact Gallery screen instances receive a neutral full-viewport backdrop; specimen request metadata reads actual properties; Change deck is widened without reducing its font. Production layouts/controllers/navigation and both fixed design canvases are unchanged. [Final fix evidence](../reports/2026-10-17-gallery-proof-correction.md#final-review-fix-wave-2026-10-17-supersedes-the-unresolved-findings-above) records the frozen 266-file source manifest, unchanged LDLib2 hash, native comparisons, retained red originals and exact final screenshots.

Final-source Gallery verification passed at 1280x720/effective 2, 1024x768/effective 2 and 1920x1080/effective 4: 12/12 scenario executions, 12,744/12,744 checks, plus the built-in 1100x800 resize in each case. Native self-review opened 31 final images, including unscaled affected crops; selected Alex's previously absent right border is complete, and the surrounding viewport is uniformly quiet. The retained seven-case matrix below is explicitly pre-fix evidence and is not attributed to this final source.

Fresh unchanged production regressions passed 3/3 scenarios and 235/235 checks. Full JUnit ran once: 72 suites, 1113 tests, 1112 passed, no failures/errors, one existing Windows symlink-privilege skip. All frozen source hashes still match. Exact final-source archives use correction-fix-v1-gallery-*, correction-fix-production-regressions and correction-fix-junit-* under build/gallery-proof. Runtime/Gradle ownership is released.

**Independent final rereview approved all four fixes at `c7e5f0a`.** Actual normal, fractional and large captures were inspected; fixture implementation and agent visual review are complete. Human aesthetic approval and production migration remain pending; the historical focus-restoration limitation is unchanged. No push or merge. Earlier checkpoints below retain their historical findings and results.
## Historical pre-fix Gallery correction verification checkpoint (2026-10-17)

Runtime source is frozen at `0ce1a6a`. [Correction verification](../reports/2026-10-17-gallery-proof-correction.md#final-correction-verification-2026-10-17) records independent focused Task 1–3 approvals, seven archived Gallery cases (29,736/29,736 checks), unchanged production scenarios (235/235), and full JUnit once (1113 tests; 1112 passed, one existing Windows symlink privilege skip; no failures/errors). Actual per-scenario framebuffer/GUI/effective scale fields confirm no resize leakage; 1280 requested scale 4 clamps to 3. The specimen's requested-size literal is stale; use driver request and live viewport metadata.

**Visual completion remains open.** The [exact 1024×768 selected lobby capture](../../../build/gallery-proof/correction-final-gallery-1024x768-s2/screenshots/gallery_lobby/41_gallery-lobby-selected.png) loses several button-outline segments and the entire selected card right edge; the Minecraft title backdrop is prominent outside the canvas. Whole-change review must assess this repeatable visual finding despite green geometry. Fractional collection live-resize border loss also remains. Human visual approval and production migration are pending. Focus restoration is still the unchanged baseline limitation; Gallery popup alignment is now fixture-corrected, with no dirty-layout/flat-image warnings in this frozen verification. Generic runtime/Gradle warnings remain archived. No runtime edit during matrix; production resources/navigation/network remain unchanged. Runtime ownership released; no merge/push. Earlier checkpoints below are historical, including the initial Task 4 TDD chronology exception and carried-forward M6/installed-JAR/multiplayer gates.

## Current Gallery tooling implementation checkpoint (2026-10-17)

Gallery tooling Tasks 1–5 are implemented on `codex/ui-redesign` through `a8609b0`; this cleanup synchronizes the handoff and adds nested capture-path regression coverage. The [runtime proof](../reports/2026-10-17-gallery-runtime-proof.md#task-5--real-lobby-popup-modal-and-handoff) links the retained [combined Gallery evidence](../../../build/ui-gallery/task5-reviewed-group-duelcraft_gallery-1280x720-s2/index.html): **4/4 scenarios, 3470/3470 checks**. Final JUnit verification recorded **1113 tests, 1112 passed, zero failures/errors, one existing Windows symlink-privilege skip**. Seven collection cases passed 3017 checks each; seven lobby cases passed 186 each. Requested 1280×720 scale 4 clamps to effective 3; it is not effective-scale-4 proof.

The scoped final review found no critical or blocking tooling issue, with four minor cleanup findings addressed for rereview. Human hierarchy/legibility/sharpness acceptance remains pending, and production migration is not approved. Focus restoration remains unmet; Gallery popup offsets and repeated dirty-layout warnings require investigation against the pinned artifact before migration. Preserve the collection popup correction and Task 4's explicit initial test-first chronology exception. Production layouts/styles/routes remain unchanged. The retained ignored evidence archives must stay with this checkout. No merge or push; use the separate reviewed migration plan after proof acceptance. Earlier checkpoints below describe their original state.

## Gallery style and tooling checkpoint (2026-10-17)

The user approved Gallery, including the content-first collection layout and Change deck beside Settings in the lobby. The [Gallery style guide](../specs/2026-10-17-gallery-style-guide.md) records that direction, shared component rules and candidate measurements for the real-client proof. The [LDLib2 tooling plan](../plans/2026-10-17-ui-tooling.md) defines the next work; its tasks remain unchecked. Use `codex/ui-redesign` in this existing deck-editor worktree. Current work changes documentation only; no new production UI, tooling, game build or runtime acceptance is claimed.

Next: review the guide/plan, build the narrow tooling and fixture proofs, then review real LDLib2 rendering before planning screen migration. Preserve rich browser controls and existing policies/input semantics. Earlier mockup approval does not establish Minecraft text fit or dropdown behavior. Carried-forward multiplayer checks remain nonblocking and retain their evidence. Do not push or merge without a request. Dated checkpoints below describe their original state.

## UI redesign begins (2026-10-17)

The user merged PR #7 into `main` (`ef0599d`) and explicitly carried open multiplayer playtests forward without blocking new work. The next task is the UI redesign. Its [audit/design brief](../specs/2026-10-17-ui-redesign-brief.md) and [three-direction review checkpoint](../reports/2026-10-17-ui-directions-review.md) live on `codex/ui-redesign` in this existing worktree. This phase changes only documentation/mockups; the user will critique the directions before the style guide, tooling and LDLib2 implementation. The earlier M6 checkpoints below retain their original acceptance limits.

## Template cleanup (2026-10-16)

Source `60e7e46` removes template items/block, unused config/logging and sample metadata comments, and gives the Duelcraft tab a Card Binder icon. [Verification and M6 QA.2 artifact identity](../../testing/template-cleanup.md): targeted real-client 7/7 checks, zero template resource/logging matches, full test/build successful (1,112 passed, one existing skip). The template-resource warning exception is closed. Multiplayer acceptance remains pending; the full UI redesign stays separate. No merge or push. Earlier checkpoints retain their original evidence.

## Local verification continuation (2026-10-16)

The user accepted the corrected lobby dropdown: entries are readable and Escape works as expected. Local continuation began from clean `cfb33a5` on `codex/qa-m4`; [the verification report](../../testing/player-interaction-local-verification.md) records the new candidate and evidence. The full UI group passed 58 scenarios/12,338 checks; all nine window/scale combinations passed 36 required surface executions/2,151 checks. Solo policy/failure fixtures passed 53 checks under each effective ownership setting. Real-catalog and controlled asset-failure probes passed with their retry/timing limits documented.

The reproduced physical card texture warning was fixed in `7facebe`; DEV_ONLY verification and candidate version changes are in `66b19e2`. Candidate `1.0.0-m6-qa.1`, protocol 8, has SHA256 `BA404D16FB5F94DAD405D7D35ACC40DF4F45A9E2E7E827B5DA45312412FCFFEA`. Both ordinary and disposable configuration hashes are preserved. Final test/build status and independent report review are recorded in the linked report. No merge or push.

M6 release sign-off remains open for installed same-JAR two-player acceptance and its associated developer checks. Keep the full UI redesign separate, including the small popup-edge styling observation. Preserve Task 4's completed audit, accepted single-player checks and all historical evidence. The known template model warnings remain explicit exceptions to the resource-loading sign-off. New evidence lives under `build/interaction-release/m6-local-2026-10-16/`; older dated checkpoints below describe their original state.

## Current M6 audit checkpoint (2026-10-16)

Work in the existing deck-editor worktree on `codex/qa-m4`, starting from clean `0d251daecd0c5769bdd82a13f5e11cc3ebcb75e3`. M5 is implemented; M6 Task 4's audit/documentation is complete after independent review, while M6 and release sign-off remain open. The [release evidence](../../testing/player-interaction-release.md), [authority audit](../../testing/player-interaction-audit.md), [collection guide](../../player-collections.md) and [consolidated manual procedure](../../testing/player-interaction-manual.md) distinguish historical runtime evidence, fresh static checks and outstanding candidate gates.

The user accepted the Oct 8 non-multiplayer M5 checklist except lobby presentation and the rule dropdown. Keep that acceptance. QA.2 has the automated dropdown correction; its focused human recheck remains pending. Carry multiplayer until friends are available. Keep the full UI design/style-guide pass separate. No new build or client/server/UI run accompanies this audit; consult the release report for any fresh test-only verification. No merge or push.

Current artifact: `1.0.0-m5-qa.2`, protocol **8**, source `1dd8ee00c9fc64ef36a51f8b2b908add323dbe7f`; existing JAR hash rechecked as `2D9A2FEDCC6177C6E81AE455EECF1A9C402AB8756B72B43A2BBBB9E87AA22397`. Documentation HEAD is not a new build source. Task 4 review found no production authority bypass requiring a code change; remaining M6 runtime and final-candidate gates stay open.

The dated checkpoints below retain their original evidence and scheduling statements; use this section and the current milestone table for present status.

## M5 user playtest update (2026-10-08)

The user tested everything in the M5 playtest except multiplayer and reports no issues beyond the lobby's visual design and the Settings rule dropdown. Carry multiplayer forward. Treat the lobby's nested panels, dead space and placement as work for a separate full UI pass with an LDLib2 style guide and mockup-to-widget review process. The dropdown has a scoped correction and real mouse-input regression; [the follow-up report](../reports/2026-10-08-m5-playtest.md) records its verification and artifact. Do not restart the approved M5 design or treat broader visual acceptance as complete.

## Current execution checkpoint (2026-10-07)

M5 Home, remappable J entry, private lobby, Card Binder and Duel Mat are implemented at `a07b651` on `codex/qa-m4` in the existing deck-editor worktree. Final verification passed 1,112 JUnit cases with one existing skip, 57/57 UI scenarios and 12,227 checks, plus both-policy dedicated journeys. The user approved the featured monster artwork fading into the left menu. Browser right-click now adds through the existing editor rules, and Help reflects the corrected controls. Protocol is **8**; persistence formats are unchanged. See [the M5 verification report](../reports/2026-10-07-player-entry-m5.md) for exact automated results, review fixes and artifact hash, and [the M5 playtest guide](../../qa-m5.md) for the remaining installed-build checks.

M6 owns consolidated QA.3/M5 packaged acceptance, documentation/compatibility audit and final polish. Fixture results do not close human readability, OS key rebinding, actual client item/offhand dispatch or the same installed-JAR two-client/server matrix. Preserve the ordinary development ownership-enabled configuration and earlier evidence/worlds. No merge or push.

## Scheduling checkpoint (2026-10-06)

The user approved moving into M5 with Home/lobby mockups first, followed by M5 implementation and M6 consolidated QA/polish. The worktree is on `codex/qa-m4`, clean at `22e5f70` before this documentation/mockup batch. [The review note](../specs/2026-10-06-m5-entry-screen-review.md) links the interactive mockup and tracks the next checkpoint. [The QA.3 carry-forward register](../reports/2026-10-06-qa3-carry-forward.md) retains open checks and corrects the computer-control trial's browser input expectation: browser right-click adds, deck right-click removes, double-left-click is a second add method. That code correction remains pending. No production M5 implementation, merge or push has occurred. Earlier reports below remain historical evidence; their outstanding acceptance no longer blocks beginning M5.

Updated 2026-09-19. This replaces the original M1 startup handoff. **M3 is implemented, verified and user-accepted after all manual checks passed. M4 is complete, verified and independently reviewed. The user reported no playtest issues and confirmed rejoin persistence and ownership invalidation; two-player manual acceptance remains pending. M5–M6 remain later checkpoints.** The user has accepted the M2 follow-up and the final editor polish after manual playtesting. Continue the approved design and implementation plans. See the [M3 verification report and manual playtest](../reports/2026-09-19-card-transfers-m3.md) for the completed transfer work.

QA follow-up: `codex/qa-m4` packages the reviewed checkpoint for Windows x64 friend testing, with the existing collection command enabled in packaged clients. Main `bc2fcba` was already included; `codex/player-collections` remains at `45b7460`. See the [QA installation and test guide](../../qa-m4.md). This is separate from M5 implementation.

## M4 QA.3 collection checkpoint (2026-10-02)

`codex/qa-m4` includes the approved collection controls and identical-card sort batch (`fcded07`, `3029b27`, `fdc8741`, `9d927ca`), version/guide commit `6c18203`, and final review fixes `9ba98f2`. QA.3 is `1.0.0-m4-qa.3`, protocol 7, with pinned dependencies. The full build at `6c18203` passed 1,099/1,100 JUnit tests with one expected Windows symlink skip. The first UI group failed only a stale duel caption fixture (52/53, 12,250/12,257), reproduced in isolation; a later full group exposed one remaining fixture code mismatch (52/53, 12,219/12,220). The final-source real UI group passed **53/53 scenarios and 12,190/12,190 checks**. Independent whole-change and scoped fix reviews approved the batch. See [the QA.3 verification report](../reports/2026-10-01-collection-controls-qa3-verification.md) for preserved failures, screenshots, source-specific evidence and limits.

The local Windows x64 ZIP is `build/distributions/duelcraft-m4-qa.3-windows-x64.zip`, SHA256 `C80D2ED5E94A7611A576FE671A02C1668C7FD424611D0D060F698C25E121DE63`, built from clean source `9ba98f2034c0ab244420d2760b8cb53b76fbf023`; README, BUILD-INFO, hashes, Duelcraft and pinned LDLib2 JARs are included. The packaged README uses plain source-checkout paths for documents outside the ZIP. The [QA.3 playtest guide](../../qa-m4-qa3.md) lists the collection checks. Packaged two-client behavior and the user's in-game visual spacing acceptance remain open. Historical QA.2 artifacts and worlds are preserved; no merge or push was performed.

## M4 QA.2 integration (2026-09-20)

**Current QA.2 source is `e2da8c05071a8fcdbf5396c49177a592c28da3f5`, correcting final-review R1. I1 and packaged two-client duel acceptance remain open.** Face-down field reveals now appear in prompt-owned choices without changing ordinary privacy or response indices. Fresh checks passed: 300 focused unit tests; eight UI scenarios/2,004 checks; both-policy native visibility/2,366 checks with all six child exits 0; and the full build (1,097 passed, one expected skip). Scoped independent rereview approved this correction and its package provenance; R1 is closed with no new breakage.

The replacement ZIP hash is `C9493341445CC14A968C76356C53CCF3BED3FAACD45C15A0A9AB2FCBE47F85CE`, built from that clean source. Manifest/native/SQLite/README checks passed. Fresh production server/B startup, join and loaded-module provenance passed, followed by normal exits 0. The user stopped Computer Use, so the new editor GUI was not rerun; earlier export acceptance below remains tied to the original artifact. No further A launch was made, and the historical GLFW cause remains unproven. Evidence: `build/evidence/qa-m4-follow-up/final-fix/`. Original ZIP, sidecar and staging remain at `build/distributions/preserved-qa2-7985fca-a110ca7d/`.

The approved A/B/C follow-up fixes are integrated on `codex/qa-m4`. QA.2 retains protocol 7 and the pinned dependencies. The [QA.2 friend guide](../../qa-m4-qa2.md) describes the command entry points, collection save/export behavior and manual checks; the [combined verification report](../reports/2026-09-19-m4-qa-follow-up-verification.md) distinguishes unit/native, synthetic UI, dedicated transport, fresh lifecycle and packaged acceptance. The QA.1 guide/artifact and historical worlds remain separate evidence. This does not implement M5 or complete M6.

I1 built the original QA.2 from clean source `7985fca2043a3808b1106849695e1d99a67318b9`. The unit/native suite passed 1,097 of 1,098 cases with one expected Windows symlink-privilege skip; the corrected full UI group passed 51 scenarios and 12,036 checks. Fresh lifecycle passed 87 checks, dedicated reconnect passed 11 in each mode, and the ten required dedicated runs passed 2,712 checks with all 30 child exits 0. Initial fixture failures and narrow corrections remain archived. The ZIP SHA256 is `A110CA7D9102E1B4C022C06B9B6B3CC96279F15C3DA6624BE125FEB2C8B80F14`; its BUILD-INFO retains the build source despite later documentation commits.

Actual packaged server and two clients booted with native libraries verified against the JAR. Client B passed collection entry, real artwork, export/reimport with exact ordered 40/3/2 sections, and dirty-draft save/close. Packaged two-client duel acceptance remains **open**: client A suffered the same native GLFW access violation in three bounded launches, before mod UI input. The report records every PID, fatal report and exit; no crash fix or successful packaged duel is claimed. Independent implementation/package review is approved; I1's parent gate remains open for runtime acceptance. This local offline loopback test did not change existing accounts, Prism instances or historical worlds.

## Workspace and starting point

- Implementation worktree: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`
- Current branch: `codex/qa-m4`; `codex/player-collections` is the historical pre-M3 branch below.
- Pre-M3 baseline: `b407f7b` (`Polish deck editor feedback and add type sorting`), followed by handoff commit `00b76ba`. The M3 completion addendum below records subsequent work; inspect current HEAD before editing.
- Main checkout: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft`. It is not the implementation starting point. Work directly in the existing worktree; do not restart from main or the old `deck-editor-m1` worktree.
- This handoff and its [kickoff prompt](2026-09-17-deck-editor-kickoff.md) live in the implementation worktree. Use the absolute worktree paths when opening them from a fresh session.
- Check `git status`, branch, and HEAD before editing. The polish is committed; preserve any later user changes. No merge or push has been requested.

Read in this order:

1. Repository `AGENTS.md` and any applicable worktree instructions.
2. This handoff and the [ownership amendment](2026-09-18-optional-ownership-amendment.md).
3. [Design specification](../specs/2026-09-15-player-interaction-design.md) and [implementation contracts](../specs/2026-09-17-player-interaction-contracts.md).
4. [Integration roadmap](../plans/2026-09-15-player-interaction-roadmap.md).
5. Completed [M3 implementation plan](../plans/2026-09-17-card-transfers-milestone-3.md) and its [verification report](../reports/2026-09-19-card-transfers-m3.md). M4: [executable preparation plan](../plans/2026-09-17-duel-preparation-milestone-4.md).
6. [M2 policy follow-up report](../reports/2026-09-19-deck-use-policy-follow-up.md), for verified policy behavior and runtime test setup.

The ownership amendment supersedes older unconditional ownership language. The implemented dark Minecraft UI and subsequent user feedback supersede the early HTML prototype's visual styling. Consult current source for API details; plan snippets are contracts, not code to paste unchanged.

## M3 completion addendum

M3 commits begin with `1cc975c` (item), `9157898` (atomic service), and `f77f0e9` (packets/editor); the final task adds grant/runtime fixtures and review fixes. Final verification passed 930 unit/JNI tests, 86 scale2 UI checks, 188 scale3 UI checks, 28 dedicated checks per ownership mode, and 23 retained-world/restart checks. Evidence: `build/evidence/collection-m3/`. The original worktree and earlier evidence/worlds are preserved. No merge or push. The user accepted all M3 manual checks and authorized M4; its implementation, required checks and independent reviews are complete. See the [M4 verification report](../reports/2026-09-19-duel-preparation-m4.md).

## M4 completion evidence

Implementation commits: `55e6da2`, `f6862dc`, `279245d`, `d015de4`, `2175879`, `cc71703`. The final suite passed **1,007 unit/JNI tests** with no failures/errors/skips. Task 5 passed **286 runtime checks** across native solo, dedicated policy checks under both ownership modes, migrated transfer/command scenarios and five separate restart JVMs. Earlier import/editor runs and review-fix evidence are listed in the [M4 report and manual checklist](../reports/2026-09-19-duel-preparation-m4.md). Evidence root: `build/evidence/collection-m4/`. Protocol is **7**. M2/M3 retained worlds and the ordinary development ownership config are preserved.

M4 supplies command-driven preparation, persistent collection-backed selection and Main/Extra/Side import. M5 still owns Home/hotkey, binder, mat and lobby screens. M6 owns the release-validation checkpoint. M4 manual acceptance remains for the user; no M5 work, merge or push was performed.

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
- M5 entry points are implemented: binder opens collection/deckbuilding; duel mat opens invitation/preparation; remappable hotkey opens Home, so players need not carry items.

## Current milestone status

| Milestone | Status and implementation plan |
| --- | --- |
| M3: Physical cards and transfers | **Complete, verified.** [Report](../reports/2026-09-19-card-transfers-m3.md). [Plan](../plans/2026-09-17-card-transfers-milestone-3.md): canonical passcode card item; inventory transaction planner/service; transfer packets and editor controls; restricted card-grant command and real inventory scenarios. |
| M4: Duel preparation and import | Complete, verified and independently reviewed. [Plan](../plans/2026-09-17-duel-preparation-milestone-4.md): shared preparation service, YDK import retaining Side, immutable prepared decks, actual solo/multiplayer policy checks, and removal of legacy bypass paths. |
| M5: Player entry points | Implemented; Oct 8 non-multiplayer acceptance with lobby/dropdown exceptions. QA.2 dropdown automated regression passes; human retest pending. [Plan](../plans/2026-09-17-player-entry-points-milestone-5.md), [report](../reports/2026-10-08-m5-playtest.md). |
| M6: Release validation | Task 4 audit/documentation complete and independently reviewed; multiplayer and final-candidate gates pending. [Plan](../plans/2026-09-17-interaction-release-milestone-6.md), [release evidence](../../testing/player-interaction-release.md). Full UI design pass stays separate. |

M3 completed all four tasks, including conservation/capacity/revision tests, real inventory UI flows, retained-world restart checks, and dedicated-player isolation. Transfers use the existing `CollectionService` and shared policy. Preserve the contracts for accessible slots, canonical components, amount bounds, and all-or-nothing transfers.

M3 bumped the request/reply protocol from `5` to `6`; clients and servers must match. Transfer controls are connected in the existing editor. The physical item reuses the existing card-back texture.

M3 checkboxes, exact verification evidence and manual checks are complete. Keep M4–M6 as separate milestone checkpoints. M4 replaces legacy duel-start/upload authority with collection-backed preparation and bumps protocol to 7. See the M4 report for current verification and the remaining M5/M6 checkpoints.

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

Use remappable **J** for Home; the binder, mat and command routes share server authority. `/duel collection` remains available. M3 connected Deposit/Withdraw/Deposit carried cards and the level-2 `/duel card give <player> <passcode> <count>` command. Current channel protocol is **8**. See the [player guide](../../player-collections.md).

LDLib2 source is `C:/Users/haxer/Documents/Programming/Modding/LDLib2`, not a sibling of this nested worktree. UI guidance: `docs/ldlib2-ui-guide.md` and `docs/ui-wiring-guide.md`. For engine-related changes, begin with `docs/engine-wiki/README.md`.

The SERVER config normally loads from `<gameDir>/config/duelcraft-server.toml`; an existing `<world>/serverconfig/duelcraft-server.toml` override takes precedence. Consult the policy report for verified lifecycle test setup rather than guessing the active config file.

LDLib UI runs overwrite `build/ldlib2-uitest`. Copy reports/screenshots into a distinct evidence directory before the next run. Use disposable fixtures for new transfer tests and preserve existing retained-world evidence. Report unrun or blocked checks explicitly. Keep changes scoped, commit milestone work, and do not merge or push without a request.

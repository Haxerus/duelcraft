# Gallery proof correction implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Independent visual reviewers must inspect the actual images and the approved reference, not infer appearance from code or passing checks.

**Goal:** Correct shared Gallery controls and faithfully prove the approved collection/lobby compositions without changing the production UI.

**Architecture:** Keep DEV_ONLY fixtures using real controllers. Scope presentation styles to named components, add small harness diagnostics using existing LDLib2 APIs, and permit a narrow fixture-only opponent-button renderer seam where Java owns dynamic presentation. Normal factories retain their resources and renderer.

**Tech stack:** Existing Java 21/NeoForge/LDLib2 2.2.39.a; XML/LSS; current UI harness and static evidence archive script.

**Spec:** [Gallery guide](../specs/2026-10-17-gallery-style-guide.md), [approved Gallery composition](../../mockups/ui-redesign/gallery.html), and the user's four rejected proof details (button centering, scrollbar arrows, collection toolbar, lobby avatar cards). This correction supersedes weaker composition criteria in the original tooling plan, not historical evidence.

## Global constraints

- Work in the existing deck-editor worktree, branch `codex/ui-redesign`, starting at `c5cf505`. Preserve later user changes. No push or merge.
- Production XML/LSS, controllers' behavior, navigation, networking, policy and input semantics stay unchanged. A package-private fixture renderer seam may delegate the original production rendering unchanged; do not gate shipped behavior on resource names.
- Retain management 1280×720 and duel 960×540 canvases and the existing Minecraft font. No library upgrade, new UI framework, CSS transpiler or general inspector.
- Retain rich collection filters, four-column/128-unit browser virtualization, Deck/Side routing, saved/active authority, long-name limits and unique temporary-file teardown.
- Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`. One worker owns client/Gradle launches at a time. Archive each run before another overwrites `build/ldlib2-uitest`.
- Targeted red/green checks and screenshots during iteration. Run the full matrix and full JUnit once after component and composition visual review; repeat only for relevant subsequent changes/failures.
- Human visual approval is still pending. Independent agent review must nevertheless reject visible alignment/component/composition departures; it must not defer basic correctness to the user.
- Preserve original reports. New raw evidence belongs in `build/gallery-proof/correction-*`, galleries in `build/ui-gallery/correction-*`; report actual effective scales and exact source identity.

## Acceptance contract and review focus

| ID | Required outcome | Evidence owner |
|---|---|---|
| C1 | Ordinary/dense button label line boxes centered vertically and horizontally inside button content; visible glyphs balanced at native resolution; no clipping with mixed case/descenders | Task 1 bounds/line-origin diagnostics and independent component crops |
| C2 | Ordinary action styles never resize/retexture scrollbar internals; thin track/thumb and compact distinguishable arrow controls, without elongated blank rectangles | Task 1 resolved style/texture assertions and scrollbar captures |
| C3 | Editable deck name, adjacent active state, New deck/My decks/Import–Export/Save/Use for duels occupy one aligned action row; no second auxiliary toolbar; Save secondary and Use for duels primary | Task 2 explicit header geometry and approved-reference comparison |
| C4 | Saved/dirty feedback belongs beside Main, not isolated in a wide spacer; long editable names remain bounded; Help/Close remain discoverable without adding a toolbar | Task 2 normal and long-name captures/geometry |
| C5 | Normal three-player lobby uses centered horizontal selectable avatar/name/availability cards and centered Invite; top-right Change deck/Settings; bottom active-deck artwork/name/counts plus rule summary | Task 3 geometry/component assertions and approved-reference comparison |
| C6 | Long roster scrolls while retaining card identity; zero/one-player states remain intentional; selection/pending input use real controller | Task 3 state captures and routed input |
| C7 | Every visual review identifies the exact reference and actual images opened, compares composition, alignment, typography, internal controls and states, and lists departures | Each independent reviewer, retained report |

Font-specific raster differences from HTML are expected; composition, hierarchy and component identity are not optional. Use deterministic fixture names/content and local artwork for normal composition; retain explicit missing-art stress cases separately. Initials inside avatar shapes match the approved mockup and avoid network/player-skin dependencies. Do not bless rejected screenshots as regression goldens. Capture a reference pair/contact sheet or focused native-resolution crops for review; retain original PNGs. Pixel diffs can support later regression detection only after human acceptance.

## Task 1 — Correct component styles and add focused diagnostics

Files: `lss/gallery.lss`, `ui/uitest/gallery_specimen.xml`, Gallery specimen scenario, new small `client/uitest/GalleryVisualAssertions.java` (or equivalent single-purpose helper), fixture XML class annotations, guide/proof documentation.

- [x] Add failing checks for label vertical center (not just fit), ordinary and dense controls, and scrollbar head/tail dimensions and arrow textures before style corrections. Retain red evidence.
- [x] Replace broad descendant-button presentation with explicit component selectors/classes. Center the child text box and its line box; do not hide glyph errors by shrinking font. Preserve internal selector/scroller geometry and expected textures. Keep shared colors scoped.
- [x] Expose reusable `GalleryVisualAssertions.control(TestContext, String)` and `scroller(TestContext, String)` plus compact attachments for actual outer/content bounds, line start Y, font/align settings and winning style/texture values. Reuse StyleBag/ElementRef APIs; no generic inspector or speculative file/line provenance system.
- [x] Exercise ordinary/dense/default/hover/pressed/disabled/focused controls and a real scrollbar. Capture native images and component crops on management and duel canvases. Explicitly test internal controls are not ordinary actions. Run focused `gallery_specimen`/preflight checks only.
- [x] Update guide with scoped-style ownership, line-box versus glyph review, scroller contract, reference/capture review checklist and separate functional/visual verdicts. Add new correction proof report. Commit scoped changes after verification.
- [x] Independent reviewer opens actual component images and compares alignment and scrollbar semantics against C1/C2; also reviews code/spec. Resolve findings before task 2.

## Task 2 — Match collection composition

Files: `ui/uitest/gallery_collection.xml`, `CollectionGalleryScenario.java`, fixture-only supporting resources/data where needed, correction proof report. Reuse Task 1 helpers.

- [x] Define failing header tests before editing layout: required controls share one row; field/action content centers align; Active stays adjacent to name; saved/dirty feedback is adjacent to Main; no separate navigation row; long field text cannot push actions.
- [x] Rebuild the fixture header from the approved reference, keeping required IDs/types. Existing title/navigation wrapper IDs can remain structural/hidden if needed; all meaningful actions must remain accessible. Use an editable bounded deck title. Preserve a concise My Collection context label only if it does not recreate the stacked name/toolbar problem.
- [x] Keep inspector left, deck center, browser right; maintain existing rich browser controls, full 60/15/15 stress state and both densities. Include a deterministic normal 40-card, ordinary-name composition with real local card artwork plus explicit missing-art/long-name stress captures. Assets may reuse/copy existing local mockup artwork into fixture-only resources; do not download new art or depend on asynchronous online data.
- [x] Run routed editing, filter/import, Save pending/error, scroll and resize checks on the real controller. Apply C1/C2 audits to relevant visible controls/scrollers. Preserve prior functional coverage without adding thousands of duplicate aesthetic assertions as a substitute for review.
- [x] Archive focused run and native header/scrollbar/full-screen captures; create approved-reference/runtime comparison material with viewport/state metadata. Record actual deviations. Commit scoped changes.
- [ ] Independent visual reviewer must open the approved collection reference and actual captures, explicitly verdict C1–C4 and card-space/browser preservation. A plain second-row toolbar, floating status or off-center labels is a failure even if tests pass.


## Task 3 — Match lobby composition and close verification

Files: `ui/uitest/gallery_lobby.xml`, `LobbyGalleryScenario.java`, optional narrow fixture-only rendering seam in `DuelLobbyScreen.java`, fixture-only resources, correction report/guide/handoff.

- [ ] Add failing tests for three opponent cards in one centered horizontal row; each has avatar, name and availability, selected marker, and centered Invite below. Test zero, one and long rosters. Test deck strip and top actions match C5.
- [ ] Preserve production player-row rendering exactly. If required, add a package-private fixture overload accepting `BiFunction<Target, Boolean, Button>` for opponent rendering; the existing constructor/default production factories use the unchanged original text-only row renderer. Controller retains selection/input binding and authoritative state. Fixture rendering must survive controller updates, not be patched only for screenshots.
- [ ] Build avatar/name/availability cards in the fixture renderer using deterministic local initials/avatar shapes. Match Gallery's centered stage, top Change deck/Settings grouping and active-deck artwork strip. Long roster uses a bounded scroll area preserving card structure; no fallback to text-only full-width rows. Settings follows the approved side-sheet composition while preserving modal/dropdown input semantics.
- [ ] Retain real selector/invalid/pending/Tab/Escape tests. Investigate Gallery-only popup offset/dirty-layout warnings as needed to deliver stable fixture presentation; any correction stays fixture-only and retains production behavior. Existing production focus-restoration gap remains a documented observation, not a disguised pass or unrequested production fix.
- [ ] Archive focused captures and comparison material. Commit, then independent reviewer opens reference plus actual normal/selected/empty/long-roster/Settings images and verdicts C1/C2/C5/C6/C7. Fix visual failures before full verification.
- [ ] Freeze source and run `group:duelcraft_gallery` at 1280×720 and 1920×1080 requested scales 2/3/4, plus 1024×768 scale 2. Archive requested/effective scale, dependency identity and source hashes; clamped combinations are not new effective coverage. Inspect matrix captures for new defects.
- [ ] Run unchanged `collection_layout`, `collection_overflow`, `duel_lobby`, and full `./gradlew.bat test` once. Run archive script tests if tool changes. Verify production defaults/resources unchanged and `git diff --check c5cf505..HEAD` clean.
- [ ] Update current handoff with functional results, independent visual verdicts, open human review and preserved historical limitations. Whole-change review after tasks; commit verification/docs. Keep branch/worktree, no merge/push.

## Completion

Proof implementation is complete only when requirements above have evidence and independent visual review has no unaddressed basic composition/control defects. Human taste/legibility approval is a separate remaining checkpoint, not a waiver for mismatching the approved mockup.

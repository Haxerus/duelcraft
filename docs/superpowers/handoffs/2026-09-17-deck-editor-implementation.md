# Deck editor implementation handoff

## Start here

Implement **milestone 1: the in-game deck editor using sample data**. Finish its five tasks and validation before moving to persistent collections or other roadmap milestones. The user has completed visual design iteration and requested a clean session for implementation. Continue from the existing plan rather than restarting product discovery.

Read in this order:

1. Repository `AGENTS.md` and any instructions applicable to the implementation workspace.
2. [Design specification](../specs/2026-09-15-player-interaction-design.md).
3. [Integration roadmap](../plans/2026-09-15-player-interaction-roadmap.md), for boundaries and future dependencies.
4. [Milestone 1 implementation plan](../plans/2026-09-15-deck-editor-milestone-1.md), the executable task list.
5. [Accepted interactive prototype](../references/2026-09-17-deck-editor-1280x720.html), especially Standard/Large density, expanded Side, filters, and independent scrolling. The copied file includes its card images.

The [kickoff prompt](2026-09-17-deck-editor-kickoff.md) is intended for a fresh session; this document supplies the context it references.

## Workspace state at handoff

Checked on 2026-09-17:

- Main checkout: `C:/Users/haxer/Documents/Programming/Modding/Duelcraft`.
- Branch: `main`; HEAD: `a957157` (`Update .gitignore & add misc. files`). Recheck before editing.
- No collection/editor implementation has started. The planned `DeckList`, editor model, controller, screen, and collection scenarios do not exist yet.
- `docs/engine-gap-analysis.md` has a pre-existing user modification. Preserve it; do not revert, stage, or bundle it into implementation commits.
- The September 15 spec and two plans are untracked. This handoff, kickoff, and repository prototype copy are also uncommitted delivery artifacts.
- A new git worktree will not inherit untracked files. Before leaving this checkout, preserve/copy this documentation package into the implementation worktree with the same relative paths, or deliberately commit only these documents. Do not assume a clean checkout contains them.
- No Java/native build or Minecraft harness was run while writing these planning/handoff documents. Any past browser validation is not evidence that the Minecraft port works.

Use an isolated implementation workspace following the applicable worktree workflow and a `codex/` branch. A fresh worktree needs the native submodule/build artifacts described in AGENTS.md. Worktree creation is not a reason to lose the plan or incorporate unrelated user edits.

The package consists of the linked spec, roadmap, milestone plan, prototype, this handoff, and kickoff prompt. The original prototype remains available at `C:/Users/haxer/.codex/visualizations/2026/09/16/01a0a7ac-e1fe-70d0-9df2-65139ed2a651/duelcraft-deck-720.html` if recovering the reference is necessary.

## Product context that must survive the new session

The user and friends spent 300+ hours on a custom server using YDM II. Physical-card storage pressure and poor binder searching were major pain points. Keep a Minecraft feel while adopting Master Duel's effective deck-editor layout. The user approved the three-column mockup and explicitly approved a full **1280x720 design canvas**; 960x540 is only a smaller viewport test, not the editor's design size.

Agreed behavior for the eventual integrated product:

- Say **collection**, never archive. It belongs to each player on that world/server, like an Ender Chest.
- Support collectible/tradable physical cards and deposits/withdrawals into personal storage without a slot/gameplay capacity limit.
- Save deck lists even when cards are missing. Multiple lists can reference the same owned copies; lists do not consume copies.
- Validate playability and ownership when activating a deck and preparing a duel. Ineligible lists cannot be active or used for duels.
- If withdrawal makes the active deck ineligible, clear its active selection, keep the list, and explain why.
- No deposits, withdrawals, or deck changes during duels. The eventual server must enforce this, not just hide the UI.
- Binder opens collection/deckbuilding; duel mat opens invitations/lobby preparation; a remappable hotkey opens a home screen linking to both. Players need not carry either item.
- Final readability, comfortable minimum screen size, and density depend on in-game playtesting. The user has not supplied numerical usability/performance targets.

The spec separately labels proposed technical defaults: deposited copies only for ownership, exact-passcode storage, Side included in ownership, preparation locking from accepted invite through RPS, and persistence/network implementation choices. Do not describe those as separately user-confirmed details. They do not block the sample-data milestone; revisit material changes when planning the relevant server milestone.

## Milestone 1 boundary

Build the local `DeckList`/editor model, pure rich search/filtering, XML/LSS screen, virtual collection rows, sample fixtures, and real-screen harness scenarios described in the plan. Keep draft state outside widgets. Make save an injected in-memory callback and identify sample data in the screen.

Preserve the existing duel UI, its 960x540 sizing, and duel-specific input/lifecycle behavior. Give the editor its own 1280x720 host. Keep `core.Deck` as Main/Extra simulation input; the new list retains Side without adding match mode.

Do not implement real persistence, transfer packets, active-deck selection, new physical items, production home/hotkey, lobbies, acquisition/economy, or banlists in this milestone. Controls representing those functions must be disabled or clearly identified as unavailable. Do not put sample cards or ownership into a player's real inventory/storage.

The plan's Java interface blocks describe contracts, not compilable source to paste verbatim. Check signatures against pinned APIs and implement the methods. Make small evidence-backed corrections to the plan when necessary; do not expand scope or restart the approved visual design.

## Source findings and pitfalls

- Minecraft 1.21.1; NeoForge 21.1.224; Java 21; LDLib2 2.2.39.a. Verify `build.gradle` if versions have changed.
- Use `client/DuelScreen.java` and `client/LDLibDuelScreen.java` as scaling/XML examples, not as targets for refactoring. Their lower scale clamp cannot be copied blindly into a larger editor canvas.
- `client/carddata/CardDatabase.java` currently supplies point lookups and specialized announce-card search. Keep that search intact; collection filtering has different rules.
- `CardInfo` already has the metadata needed for the sample filters. Avoid changing its widespread constructor just for this milestone.
- The pinned LDLib source JAR contains `VirtualScrollerView<T>`. Group four cards into each virtual row and retain selection/model state outside mounted widgets. Request images only for mounted rows and the inspector.
- `CardImageManager` has a single texture-loaded callback. Do not overwrite the duel UI's callback. An injected texture provider is sufficient for the first milestone and can return null in tests.
- The browser prototype once overlapped Large cards because grid rows shrank. Explicit fixed row heights and scrolling fixed it. Carry that behavior into LSS and verify bounds, rather than relying on browser CSS semantics.
- Current `DeckLoader` drops Side; current uploaded deck selections are session-only. Neither is the eventual collection model.
- `DuelManager.isBusy` includes first-turn rolls. `ServerPayloadHandler.handleDeck` currently lacks the new ownership/busy policy; addressing it belongs to later integration, not the sample screen.
- `docs/multiplayer-decks.md` reflects newer uploaded-deck behavior than the filename-based flow in AGENTS.md. Prefer current source when prose disagrees.
- LDLib source is at `../LDLib2` relative to the main checkout. From a different worktree, locate it using the main checkout path rather than assuming it is still a sibling. XML schema: `LDLib2/ldlib2-ui.xsd`.

Java paths in this section are under `src/main/java/com/haxerus/duelcraft/`.

## Verification and completion

Follow the plan's model/search tests and actual LDLib2 scenarios. Verify behavior, not just screenshots: add/remove, save failure retaining draft, dirty-close choices, ownership indicators, filter combinations, transformed clicks, scroll independence, and bounded mounted rows with 16,000 synthetic cards.

Exercise 1280x720, 960x540, and 1920x1080 windows and GUI scales 2/3/4; record effective sizes/scales because Minecraft may clamp the request. Include non-16:9 and resize-with-dirty-draft checks. Keep prior harness reports/screenshots before another run overwrites them.

Commands in the plan assume the new scenarios exist. On Windows, use `./gradlew.bat` if necessary. Full tests need native DLLs and configured EDOPro data. The documented paths are `C:/ProjectIgnis/expansions/cards.cdb` and `C:/ProjectIgnis/script;C:/ProjectIgnis/script/official`. Do not assume the proposed `-PskipNative` option is implemented. Diagnose environment failures separately from failing behavior tests.

Deliver code, test results, actual Minecraft screenshots, and instructions for opening the sample editor. Update task checkboxes and append observed results to the roadmap. Explain any failed/unrun checks accurately. Keep human playtesting distinct from automated bounds checks. Stop at the milestone 1 boundary with a usable editor for playtesting; the next milestone needs its storage/network implementation plan.

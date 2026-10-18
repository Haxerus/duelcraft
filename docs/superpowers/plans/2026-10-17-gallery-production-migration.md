# Gallery production UI migration implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Track the steps below and preserve the approved proof evidence.

**Goal:** Apply the user-approved Gallery design to the production UI while preserving existing player workflows.

**Architecture:** Promote the proven collection and lobby resources and components into their production hosts. Share Gallery styles and production assets; keep the existing controllers and server contracts. Apply the same visual vocabulary to Home and the live duel without changing field geometry or engine semantics.

**Tech Stack:** Java 21, NeoForge 21.1, pinned LDLib2 2.2.39.a, XML/LSS, real-client scenario harness.

**Spec:** `docs/superpowers/specs/2026-10-17-gallery-style-guide.md`; approved native proofs at commit `63168de`, including stacked deck sections, grouped lobby header and shared control insets.

## Global Constraints

- Work in the existing deck-editor worktree on `codex/ui-redesign`; baseline `63168de`. No merge or push.
- Management/editor canvas 1280×720; duel canvas 960×540. Preserve field topology and engine-specific colors/markers.
- Preserve rich filters, Deck/Side routing, card quantities, ownership modes, saved/active distinction, transfer authority, preparation locking, prompt response ordering and hidden information.
- Shared field-like horizontal insets remain 10 management / 7.5 duel units, defined once per canvas. Action styling must never resize internal scrollbar buttons.
- Use existing approved Gallery assets, controls and native styles. No new dependencies or server/protocol changes.
- Keep carried-forward multiplayer checks nonblocking and accurately recorded. Run clients serially; archive reports before overwriting. Use GRADLE_USER_HOME=C:/Users/haxer/.gradle.
- Keep existing behavior tests. Change outdated visual assertions only when the approved design requires it; do not weaken functional assertions.

## Review Focus

1. Long names and popup text remain inside bounds at fractional scale; native screenshots and input checks (Tasks 1–3).
2. Production routes use the same layouts/renderers as proofs, including dynamically created controls (Tasks 1–2).
3. Modal closing and unavailable/pending states retain input gating and draft data (Tasks 1–2).
4. Live duel hidden information, response actions and field coordinates survive theming (Task 3).
5. Panel expansion preserves independent scroll/visibility intent and never shifts the board (Task 3).

### Task 1: Promote collection and shared Gallery assets

**Files:** production `ui/collection_screen.xml`, proof `ui/uitest/gallery_collection.xml`, `lss/gallery.lss`, Gallery arrow assets; `client/collection/CollectionScreen.java`, `CollectionGalleryScenario.java`; relevant collection regression scenarios. Keep CollectionController behavior intact unless a proven production integration issue requires a small fix.

**Interfaces:** Consume approved Gallery XML/classes. Produce production collection layout with the same existing controller IDs, shared styles, and production-path scrollbar icons. Proof scenarios must load that production resource; remove redundant proof XML and unnecessary resource-injection seams. Do not change specimen fixture resources except asset references.

- [x] Promote approved collection composition, retaining all IDs and controls, including dialogs/filters and stacked equal-grid deck sections.
- [x] Move shared arrows out of `textures/uitest` into production `textures/gui/gallery`; preserve fixture artwork as fixture-only.
- [x] Give the production collection a quiet Canvas backdrop across letterboxing using its screen host, not a globally installed test listener.
- [x] Point Gallery collection checks at production; retain input, resize, density, popup, and stress checks. Compile and run focused real-client collection checks; inspect screenshots independently.
- [x] Review and commit scoped changes.

### Task 2: Migrate Home and lobby

**Files:** `ui/home_screen.xml`, `ui/duel_lobby.xml`, redundant `ui/uitest/gallery_lobby.xml`; `client/interaction/HomeScreen.java`, `ManagementScreen.java`, `DuelLobbyScreen.java`, `LobbyGalleryScenario.java`; relevant Home/lobby scenarios. Add a small production player-card renderer only if needed to keep fixture dependencies out of production.

**Interfaces:** Consume shared Gallery LSS/assets from Task 1. Keep all existing navigation, preparation and settings APIs. Production and fixtures use the same player-card renderer, with injected test data/art only where needed.

- [x] Promote approved lobby layout and avatar-card renderer into production, resolving real player skins with a deterministic fallback. Remove redundant fixture layout/renderer routing.
- [x] Preserve zero/one/long roster, settings, pending, incoming/outgoing invitation, RPS, tie, first choice, startup and close-versus-cancel behavior.
- [x] Restyle Home as the approved art-backed left menu; preserve active/missing deck, invitation, resume, retry and Help. Keep art ratio/cropping and Canvas fade.
- [x] Apply quiet production management backdrop and retain modal/keyboard ownership. Point proof scenarios at production and test Home/lobby regression plus native visual captures.
- [x] Review and commit scoped changes.

### Task 3: Live duel Gallery treatment and complete validation

**Files:** `ui/duel_screen.xml`, `client/LDLibDuelScreen.java`, `client/DuelScreen.java`, directly affected renderers/prompt controllers only where hard-coded visual styling bypasses shared classes; duel UI scenarios and focused panel state tests; migration verification report and handoff.

**Interfaces:** Consume Gallery duel styles without applying ordinary action geometry to field cards, slots, counters or scrollbar internals. Preserve all server/engine and response APIs. Expand/restore right reading panels through the existing layout method and visibility state, not a second screen controller.

- [x] Apply Gallery colors, flat edges, explicit action roles and readable label alignment to live duel controls, reading surfaces and prompt/leave/result overlays. Preserve field bounds and current prompt/layer ownership.
- [x] Add zone/log Expand/Restore controls with independent visibility intent and scroll preservation as required by the guide; test each single/split/expanded/closed transition and append-follow behavior.
- [x] Run focused duel checks, then source-freeze and run JUnit plus the full non-multiplayer UI regression group and Gallery proofs at 1280×720 and 1024×768. Archive evidence and inspect native screenshots with independent reviewers.
- [x] Fix material regressions, record exact results and limitations, update handoff and plan checkboxes. Perform final whole-change review and commit. Keep multiplayer manual checks carried forward; do not claim human visual acceptance of the newly migrated screens.

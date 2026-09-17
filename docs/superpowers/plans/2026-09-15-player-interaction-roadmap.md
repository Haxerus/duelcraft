# Player interaction integration roadmap

**Design:** [Player interaction and collection](../specs/2026-09-15-player-interaction-design.md).

This roadmap covers the complete integration. Each milestone produces a testable result and gets a focused implementation plan before coding. Milestone 1 has its detailed plan now; later plans use the decisions and file boundaries below, revised with actual playtest findings.

## 1. Playable editor with sample data

Deliver the accepted 1280x720 layout in LDLib2 with deck grids, inspector, rich filtering, Standard/Large density, and independent scrolling. Use injected sample cards/ownership and an in-memory save callback. Expose it through a development-only UI scenario; the player-facing hotkey arrives with authoritative state.

Create `collection/DeckList.java`, `client/collection/` editor/model/search files, `ui/collection_screen.xml`, and `client/uitest/Collection*` scenarios. Preserve `core/Deck` and the current duel UI. Follow the [detailed plan](2026-09-15-deck-editor-milestone-1.md).

**Exit:** actual Minecraft screenshots at 1280x720 and smaller/larger windows, transformed clicks at GUI scales 2/3/4, overflow without overlapping rows, and a large synthetic catalog with bounded mounted widgets. User playtests readability before measurements become fixed.

**Implementation record — 2026-09-17:** Milestone 1 is implemented on `codex/deck-editor-m1` in `.worktrees/deck-editor-m1`. The independent 1280x720 sample editor supports permissive Main/Extra/Side drafts, rich filters, virtual collection rows, Standard/Large density, independent scrolling, save failures and dirty-close choices. The original duel UI and engine `Deck` are unchanged. Review-driven regressions cover unapplied filters, edit/resize scroll retention and collapsed Side; an adaptive text-width layout loop was reproduced and fixed.

Verification: 694 JUnit tests passed with no skips; all nine requested window/GUI-scale combinations passed 2,250 checks; the full `group:duelcraft` run passed 32 scenarios and 7,691 checks. The 16,000-card fixture mounted five rows at both ends of its 4,000-row catalog. Actual viewport scales, non-16:9 dirty-resize captures, screenshot paths and the keep-open playtest command are in the [verification record](../reports/2026-09-17-deck-editor-m1.md). Text is noticeably small at 960x540; comfortable density and minimum window size remain user playtest decisions.

Milestone 2 is ready for its focused storage/network implementation plan. No persistence, transfers, activation, production entry point, items or lobbies were implemented in milestone 1.

## 2. Persistent personal collections and saved lists

Create focused common models and storage in `collection/`: `SavedDeck`, `PlayerCollectionData`, and attachment registration. Add `server/collection/CollectionService` for list CRUD, activation, eligibility checks, and ownership mutations. Add small typed payloads under `server/collection/` and `client/collection/ClientCollectionState` for the private view. Register through `Duelcraft` and `DuelNetworking`; clear client state on logout in `DuelcraftClient`.

Write the detailed storage/sync plan first, including schema version, request bounds, page sizes, expected revisions, missing-card result shape, and the server catalog choice described in the design. Reuse the editor's local `DeckList` rather than serializing UI objects. Add the production card-search loader on its own database connection/worker; preserve existing announce-card search behavior. Load a metadata snapshot once, apply filters away from the render thread, and ignore outdated query generations.

Keep collection state changes server-owned. Store copies by passcode; save any well-formed draft, validate before activation, and clear invalid active selection after edits. Persist through player save/clone. Full collection and deck contents go only to their owner. Saving and activation remain separate operations.

**Exit:** persistence/codec tests; two distinct players with same-named decks cannot access each other's state; reconnect/restart/death preserve collections; drafts with missing cards save but cannot activate; stale updates do not overwrite newer lists; large snapshots assemble without exceeding packet bounds. New tests use tiny temporary database fixtures rather than local EDOPro data wherever possible.

## 3. Physical cards and inventory transfers

Create `item/CardItem`, the passcode data component registration, and `server/collection/CardTransferService`. Register cards in `Duelcraft`, add item definition/model/lang resources, and connect the editor transfer controls to authoritative operations. Add explicit amount selection, Deposit carried cards, capacity errors, and status updates without rebuilding the entire editor.

Implement inventory checks and collection updates as one server-thread operation. First inspect stacks/capacity, then apply the matching removal/addition and count delta. Reject transfer requests during preparation or a duel. Clear active selection only if remaining ownership no longer covers the active list. Permit full-stack deposits and counts above vanilla stack limits in the collection. Keep normal withdrawal all-or-nothing for the requested amount.

A restricted development grant command provides test cards until acquisition content has its own plan. Client packet data never creates ownership. Keep physical cards on normal inventory/death/drop semantics; deposited cards follow personal storage semantics.

**Exit:** inventory + collection conservation tests, including repeated requests, changed slots, offhand/partial stacks, full inventory, and numeric bounds; real deposit/withdraw flows survive restart; invalidation preserves the saved list and sends a clear reason. No inventory transfer UI claims success before the server reply.

## 4. Authoritative duel preparation and list import

Add `server/DuelPreparationService` to share challenge, accept, cancel, and readiness validation across commands and UI. Adapt `DuelManager` to resolve persistent active-list IDs and capture immutable engine decks on acceptance. Keep `isBusy` true through first-turn selection/start; release it on timeout/cancel/disconnect/failure. Revalidate at preparation/start, including unknown/type/ownership checks defined in milestone 2.

Update `DuelCommand`, `ServerPayloadHandler`, `DuelClientCommand`, and existing deck-upload tests together. Replace normal raw upload activation with YDK list import plus server activation; retain Main/Extra/Side in the import parser while preserving `DeckLoader` callers that only need engine decks. The Side list stays out of single-duel engine input. The human's solo deck must be owned; the AI's deck does not require a player collection.

Replace session-only `playerCurrentDeck` as the authoritative selection. Pending invites contain participants/options and expiry, not access to the other player's list. Reject old/stale mutation paths while busy. If protocol shapes break compatibility, bump the current network version `2` and update setup instructions for matching clients/servers.

**Exit:** request-sequence tests cover withdrawal between invite and accept, editing an active list, activation during first-turn selection, stale uploads during a duel, cancellation/disconnect, and immutable prepared decks. Existing seed/seat/shuffle behavior stays unchanged. Dedicated-server two-player duels use collections without manual deck commands.

## 5. Home, binder, mat, and invitation screens

Create `client/interaction/DuelcraftScreens`, key registration/handling, `HomeScreen`, and `DuelLobbyScreen`, with their XML resources. Register a binder item and a placed duel-mat block/item that open the corresponding route. Give the mat no collection inventory or ownership data; access through a mat still uses the interacting player's collection. Add item/block textures/models/lang resources as part of their implementation task.

The remappable home key provides collection/deckbuilding and duel preparation without carrying an item. Bind the same routing policy to item/block interactions. During a live duel, return to that duel instead of opening management; during accepted preparation, return to preparation. Route from server state as well as local state so stale screens cannot bypass locks. Respect text input and other open screens.

Private two-player lobby supports invite target selection, received invites, accept/decline/cancel, chosen rule/options supported by existing commands, active-deck eligibility, expiry, RPS, and first-player choice. Display status without exposing the opponent's card list. Preserve chat feedback as a fallback where useful. Closing the screen does not silently accept or cancel a duel; provide an explicit cancel action.

**Exit:** complete hotkey-only journey with no items; binder reaches the same saved data; mat reaches the same lobby; request failures explain missing cards/busy/offline/expired states. Two-player testing proves invite races and end-of-duel navigation. Changing GUI scale/resizing preserves draft state.

## 6. Release playtest and documentation

Update `docs/multiplayer-decks.md`, `docs/engine-wiki/duelcraft-jni.md` for changed host contracts, and relevant project guidance. Remove obsolete normal-player upload paths rather than leaving ownership bypasses. Do not rewrite unrelated engine documentation or cleanup unrelated mod template code.

Run the full JUnit suite and the existing/new `group:duelcraft` UI scenarios, with the repository's native/data prerequisites. Do not assume `-PskipNative` exists; current guidance only proposes it. Test server restart, respawn, and a dedicated-server connection, not just integrated singleplayer. Capture actual large-catalog query/scroll behavior with warm/cold card images and missing downloads. Check long names/effect text, empty collection, missing/unknown cards, invalid drafts, and keyboard navigation.

**Release acceptance:** both friends can acquire test cards, deposit, build/save/select lists, duel through hotkey or mat, and withdraw afterward. No card duplication/loss from tested transfer sequences, no edit during preparation/duel, no cross-player collection access, and no regression in the current duel UI. Record observed performance/readability and remaining content work separately from correctness results.

## Dependencies and deferred scope

Order: 1 -> 2 -> 3 -> 4 -> 5 -> 6. Build production entry points only after their server operations exist. Binder/mat visual assets can be prepared during those stages, but this roadmap does not authorize a separate art/design expansion.

Defer pack economy, trading screens, crafting/progression balance, banlist management, public matchmaking, spectators, match mode/sideboarding, cosmetic card variants, and cloud/global collections. These are not necessary to integrate the approved interaction model.

# Player interaction integration roadmap

**Design:** [Player interaction and collection](../specs/2026-09-15-player-interaction-design.md).

This roadmap covers the complete integration. Milestone 1 is complete through `2030dae` in `codex/deck-editor-m1`, including the approved dark duel-UI styling; see the [verification report](../reports/2026-09-17-deck-editor-m1.md). Its implementation is reconciled onto main `bc2fcba` in `codex/player-collections`, with 720 unit tests passing. Milestones 2–6 have detailed plans. Extend the completed editor and incorporate playtest findings without silently changing product scope.

| Milestone | Detailed plan |
| --- | --- |
| 1. Sample-data editor — complete | [Editor](2026-09-15-deck-editor-milestone-1.md) |
| 2. Persistence and saved lists — complete and reviewed | [Personal collections](2026-09-17-player-collections-milestone-2.md) |
| 3. Physical cards and transfers | [Inventory transfers](2026-09-17-card-transfers-milestone-3.md) |
| 4. Duel preparation and import | [Authoritative preparation](2026-09-17-duel-preparation-milestone-4.md) |
| 5. Home, binder, mat and lobby | [Player entry points](2026-09-17-player-entry-points-milestone-5.md) |
| 6. Release validation | [Tests and documentation](2026-09-17-interaction-release-milestone-6.md) |

Read the [shared implementation contracts](../specs/2026-09-17-player-interaction-contracts.md) alongside each plan. They settle storage/packet bounds, operation signatures, acknowledged saving, inventory scope, and preparation rules. Milestone 2's [verification report](../reports/2026-09-17-player-collections-m2.md) records 850 final unit/JNI tests, real editor/respawn, dedicated two-player privacy and retained-world rejoin/distinct-JVM restart evidence. Independent whole-branch and final-fix reviews are complete. Milestones 3–6 remain; no physical transfers begin before M2 state/acknowledgement acceptance. Legacy upload/deck-command paths do not yet enforce shared deck-use policy; M4 still owns that cutover. This intermediate branch is not a release with complete policy enforcement.

## Policy amendment before further implementation

Decision 2026-09-19: core `requireCardOwnership=false` server setting plus an optional companion restriction hook. See the [ownership policy amendment](../handoffs/2026-09-18-optional-ownership-amendment.md) and [M2 policy follow-up](2026-09-19-deck-use-policy-follow-up.md). Original M2 and subsequent editor fixes exist in `codex/player-collections`; M3–M6 have not started. The follow-up now has 902 passing unit/JNI tests, four retained-world JVM phases (60 checks), and dedicated A/B privacy runs under both settings (61 checks each), plus separate editor captures; see the [follow-up report](../reports/2026-09-19-deck-use-policy-follow-up.md). Final independent review is complete; M3 may proceed. Preserve the M1/M2 reports as evidence for their original behavior.

Core owns collections, transfers, legality, and optional ownership enforcement. The companion supplies acquisition/progression and may deny additional deck use. Verify settings off/on without a companion and a test companion restriction under both settings. No companion development is required to complete integration.

## 1. Playable editor with sample data

Deliver the accepted 1280x720 layout in LDLib2 with deck grids, inspector, rich filtering, Standard/Large density, and independent scrolling. Use injected sample cards/ownership and an in-memory save callback. Expose it through a development-only UI scenario; the player-facing hotkey arrives with authoritative state.

Create `collection/DeckList.java`, `client/collection/` editor/model/search files, `ui/collection_screen.xml`, and `client/uitest/Collection*` scenarios. Preserve `core/Deck` and the current duel UI. Follow the [detailed plan](2026-09-15-deck-editor-milestone-1.md).

**Exit:** actual Minecraft screenshots at 1280x720 and smaller/larger windows, transformed clicks at GUI scales 2/3/4, overflow without overlapping rows, and a large synthetic catalog with bounded mounted widgets. User playtests readability before measurements become fixed.

## 2. Persistent personal collections and saved lists

Create focused common models and storage in `collection/`: `SavedDeck`, `PlayerCollectionData`, and attachment registration. Add `server/collection/CollectionService` for list CRUD, activation, eligibility checks, and ownership mutations. Add small typed payloads under `server/collection/` and `client/collection/ClientCollectionState` for the private view. Register through `Duelcraft` and `DuelNetworking`; clear client state on logout in `DuelcraftClient`.

Follow the milestone 2 plan and shared contracts for schema version, request bounds, page sizes, expected revisions, missing-card results, and the server catalog. Reuse the editor's local `DeckList` rather than serializing UI objects. Add the production card-search loader on its own database connection/worker; preserve existing announce-card search behavior. Load a metadata snapshot once, apply filters away from the render thread, and ignore outdated query generations.

Keep collection state changes server-owned. Store copies by passcode; save any well-formed draft, validate before activation, and clear invalid active selection after edits. Persist through player save/clone. Full collection and deck contents go only to their owner. Saving and activation remain separate operations.

**Exit:** persistence/codec tests; two distinct players with same-named decks cannot access each other's state; reconnect/restart/death preserve collections; drafts with missing cards save and can activate when legal unless configured ownership or a companion restriction denies use; stale updates do not overwrite newer lists; large snapshots assemble without exceeding packet bounds. New tests use tiny temporary database fixtures rather than local EDOPro data wherever possible.

## 3. Physical cards and inventory transfers

Create `item/CardItem`, the passcode data component registration, and `server/collection/CardTransferService`. Register cards in `Duelcraft`, add item definition/model/lang resources, and connect the editor transfer controls to authoritative operations. Add explicit amount selection, Deposit carried cards, capacity errors, and status updates without rebuilding the entire editor.

Implement inventory checks and collection updates as one server-thread operation. First inspect stacks/capacity, then apply the matching removal/addition and count delta. Reject transfer requests during preparation or a duel. Revalidate the active selection against planned post-transfer state. A shortage alone clears it only with core ownership enforcement enabled; companion denials also apply. Permit full-stack deposits and counts above vanilla stack limits in the collection. Keep normal withdrawal all-or-nothing for the requested amount.

A restricted development grant command provides test cards until acquisition content has its own plan. Client packet data never creates ownership. Keep physical cards on normal inventory/death/drop semantics; deposited cards follow personal storage semantics.

**Exit:** inventory + collection conservation tests, including repeated requests, changed slots, offhand/partial stacks, full inventory, and numeric bounds; real deposit/withdraw flows survive restart; invalidation preserves the saved list and sends a clear reason. No inventory transfer UI claims success before the server reply.

## 4. Authoritative duel preparation and list import

Add `server/DuelPreparationService` to share challenge, accept, cancel, and readiness validation across commands and UI. Adapt `DuelManager` to resolve persistent active-list IDs and capture immutable engine decks on acceptance. Keep `isBusy` true through first-turn selection/start; release it on timeout/cancel/disconnect/failure. Revalidate at preparation/start, including unknown/type checks, configured ownership enforcement, and companion restrictions defined in the M2 policy follow-up.

Update `DuelCommand`, `ServerPayloadHandler`, `DuelClientCommand`, and existing deck-upload tests together. Replace normal raw upload activation with YDK list import plus server activation; retain Main/Extra/Side in the import parser while preserving `DeckLoader` callers that only need engine decks. The Side list stays out of single-duel engine input. The human's solo deck follows the same deck-use policy; the AI's deck does not require a player collection.

Replace session-only `playerCurrentDeck` as the authoritative selection. Pending invites contain participants/options and expiry, not access to the other player's list. Reject old/stale mutation paths while busy. If protocol shapes break compatibility, bump the then-current network version (the M2 policy follow-up ends at `5`) and update setup instructions for matching clients/servers.

**Exit:** request-sequence tests cover withdrawal between invite and accept, editing an active list, activation during first-turn selection, stale uploads during a duel, cancellation/disconnect, and immutable prepared decks. Existing seed/seat/shuffle behavior stays unchanged. Dedicated-server two-player duels use collections without manual deck commands.

## 5. Home, binder, mat, and invitation screens

Create `client/interaction/DuelcraftScreens`, key registration/handling, `HomeScreen`, and `DuelLobbyScreen`, with their XML resources. Register a binder item and a placed duel-mat block/item that open the corresponding route. Give the mat no collection inventory or ownership data; access through a mat still uses the interacting player's collection. Add item/block textures/models/lang resources as part of their implementation task.

The remappable home key provides collection/deckbuilding and duel preparation without carrying an item. Bind the same routing policy to item/block interactions. During a live duel, return to that duel instead of opening management; during accepted preparation, return to preparation. Route from server state as well as local state so stale screens cannot bypass locks. Respect text input and other open screens.

Private two-player lobby supports invite target selection, received invites, accept/decline/cancel, chosen rule/options supported by existing commands, active-deck eligibility, expiry, RPS, and first-player choice. Display status without exposing the opponent's card list. Preserve chat feedback as a fallback where useful. Closing the screen does not silently accept or cancel a duel; provide an explicit cancel action.

**Exit:** complete hotkey-only journey with no items; binder reaches the same saved data; mat reaches the same lobby; request failures explain missing cards/busy/offline/expired states. Two-player testing proves invite races and end-of-duel navigation. Changing GUI scale/resizing preserves draft state.

## 6. Release playtest and documentation

Update `docs/multiplayer-decks.md`, `docs/engine-wiki/duelcraft-jni.md` for changed host contracts, and relevant project guidance. Remove obsolete normal-player upload paths rather than leaving paths that bypass configured ownership or companion restrictions. Do not rewrite unrelated engine documentation or cleanup unrelated mod template code.

Run the full JUnit suite and the existing/new `group:duelcraft` UI scenarios, with the repository's native/data prerequisites. Do not assume `-PskipNative` exists; current guidance only proposes it. Test server restart, respawn, and a dedicated-server connection, not just integrated singleplayer. Capture actual large-catalog query/scroll behavior with warm/cold card images and missing downloads. Check long names/effect text, empty collection, missing/unknown cards, invalid drafts, and keyboard navigation.

**Release acceptance:** first verify legal duels from empty collections with the default setting and no companion; then enable core ownership without the companion and verify both friends can acquire test cards, deposit, build/save/select lists, duel through hotkey or mat, and withdraw afterward. No card duplication/loss from tested transfer sequences, no edit during preparation/duel, no cross-player collection access, and no regression in the current duel UI. Verify an installed test companion denial under either setting and revalidation after config/addon changes across restart. Record observed performance/readability and remaining content work separately from correctness results.

## Dependencies and deferred scope

Order: 1 -> original 2 -> M2 policy follow-up -> 3 -> 4 -> 5 -> 6. Build production entry points only after their server operations exist. Binder/mat visual assets can be prepared during those stages, but this roadmap does not authorize a separate art/design expansion.

Defer pack economy, trading screens, crafting/progression balance, banlist management, public matchmaking, spectators, match mode/sideboarding, cosmetic card variants, and cloud/global collections. These are not necessary to integrate the approved interaction model.

The user confirmed the planned legality scope and will handle card acquisition later. Acquisition is intentionally outside these plans, not an unresolved prerequisite. Deposited-only ownership, Side ownership checks for single duels, and passcode as the primary key are also confirmed in the design and implementation contracts.

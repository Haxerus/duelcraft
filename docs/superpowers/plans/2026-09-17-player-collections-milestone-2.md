# Persistent Collections and Saved Lists Implementation Plan — Milestone 2

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use subagent-driven-development only when delegation is authorized. Steps use checkbox syntax for tracking.

**Goal:** Persist private player collections and saved deck lists, connect the editor to server-confirmed saving/activation, and search the real card catalog.

**Architecture:** A serialized player attachment owns immutable collection state. Pure service operations validate revisions and eligibility, while authenticated main-thread packet handlers persist results. Clients assemble private paged snapshots and keep unsaved editing state separate.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, existing SQLite JDBC, JUnit 5.

**Spec:** [Product design](../specs/2026-09-15-player-interaction-design.md) and [shared implementation contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: completed [milestone 1](2026-09-15-deck-editor-milestone-1.md), implemented through `2030dae` in `codex/deck-editor-m1` and reconciled onto current main. Its actual model/controller/scenario interfaces are the starting point; see the [M1 report](../reports/2026-09-17-deck-editor-m1.md).

**Implementation verification:** Tasks 1–5, the whole-branch review and the final scoped fix review are complete, with 850 final unit/JNI tests and lifecycle evidence recorded in the [M2 report](../reports/2026-09-17-player-collections-m2.md). This verifies the original M2 baseline only; the policy follow-up has separate [verification evidence](../reports/2026-09-19-deck-use-policy-follow-up.md), including completed final independent review. No merge/push is included.

## Policy revision after original M2 completion

The [2026-09-19 decision](../handoffs/2026-09-18-optional-ownership-amendment.md) replaces unconditional ownership with core `requireCardOwnership=false` plus an optional companion restriction hook. The implemented [policy follow-up](2026-09-19-deck-use-policy-follow-up.md) has passed final independent review and is ready for M3. It revises eligibility/report semantics, server context, wire data, UI feedback, and tests; it does not rebuild persistence or the editor.

Tasks 1–5 below describe the original M2 baseline and its original test examples. Checked boxes, where present, and the original M2 verification report record that behavior only. In particular, the unowned-activation rejection and shortage-driven invalidation examples must become explicit ownership-enabled cases, paired with default-setting success cases in the follow-up. They are not evidence for the amended policy and must not be executed as current unconditional requirements.

## Global constraints

- Use the term **collection**.
- Preserve the completed editor's dark styling matching the duel UI. The earlier HTML prototype supplies geometry/interaction references; its light palette is superseded by user feedback.
- Saved decks are card lists. Saving a draft does not require owning its cards or satisfying duel legality.
- Multiple lists can reference the same owned copies. Saving or activating a list does not consume or reserve cards.
- Keep `core.Deck` as the simulation input.
- User-confirmed: only deposited copies count, ownership includes Main+Extra+Side even for single duels, and passcode is the primary key. No alternate-art identity or ownership substitution.
- Follow the contracts for IDs, schema, bounded requests, revisions, private sync, and asynchronous save. No new item/transfer operations in this milestone.
- Preserve current engine/wiki/cache work. Recheck worktree state before executing; do not bundle unrelated edits.

Java paths in this plan start at `src/main/java/com/haxerus/duelcraft/`; test paths start at `src/test/java/com/haxerus/duelcraft/`. Resource paths start at `src/main/resources/`.

## Task 1: Serialized player attachment

**Create:** `collection/SavedDeck.java`, `PlayerCollectionData.java`, `CollectionAttachment.java`, `CollectionAttachments.java`, `CollectionLimits.java` in the same package. **Modify:** `Duelcraft.java` to register attachment types. **Tests:** `collection/PlayerCollectionDataTest.java`, `CollectionAttachmentTest.java`.

**Interfaces:** `SavedDeck` and `PlayerCollectionData` match the contracts and expose `CODEC`. `CollectionLimits` defines schema1, name128, total draft512, count-page256, summary-page32, packet24576 bytes, issue64, issue-key128, snapshot idle30000ms. `CollectionAttachment.valid(PlayerCollectionData)` and `.unreadable(Tag)` produce wrappers with `Optional<PlayerCollectionData> data()` and a preserving serializer. `CollectionAttachments.COLLECTION` is the registered attachment type.

- [x] Write round-trip and isolation tests using `NbtOps.INSTANCE`, including this invariant:

```java
var id = UUID.randomUUID();
var draft = new SavedDeck(id, "Missing cards", new DeckList(List.of(89631139), List.of(), List.of()));
var original = new PlayerCollectionData(4, Map.of(89631139, 1000L), Map.of(id, draft), null);
var tag = PlayerCollectionData.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
assertEquals(original, PlayerCollectionData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
assertTrue(PlayerCollectionData.empty().counts().isEmpty());
```

Test defensive copies, unknown positive IDs in drafts, count >64, zero/negative counts, overflow, wrong schema, duplicate IDs, invalid active ID, 512 vs513 draft entries, and name validation. An empty/underfilled draft must round-trip.
- [x] Run `./gradlew.bat test --tests '*PlayerCollectionDataTest' --tests '*CollectionAttachmentTest'`; confirm the new behavior fails, not just Gradle setup.
- [x] Implement codecs using lists of count/deck records and validate duplicate keys before converting to maps. Do not serialize UUID map keys as display names. Use immutable values; new successful mutations replace the attachment data.
- [x] Register `AttachmentType.builder(() -> CollectionAttachment.valid(PlayerCollectionData.empty())).serialize(serializer).copyOnDeath().build()`. The serializer reads raw NBT, attempts the codec, and retains a copy of undecodable NBT in an unreadable wrapper. Its write method returns that raw copy untouched. Management operations return DATA_UNAVAILABLE for unreadable data and log the decode error; no empty-state repair. Verify the actual pinned `IAttachmentSerializer` signatures before implementing this sketch.
- [x] Add a test that an unsupported-version tag survives a read/write cycle unchanged and denies mutation. Run targeted tests and commit `feat: persist personal collection data`.

## Task 2: Server card facts and state operations

**Create:** `core/data/CardCatalog.java`, `collection/DeckEligibility.java`, `collection/CollectionError.java`, `server/collection/CollectionService.java`. **Tests:** `core/data/CardCatalogTest.java`, `collection/DeckEligibilityTest.java`, `server/collection/CollectionServiceTest.java`, `server/collection/CollectionTestData.java`.

**Interfaces:** implement `Facts`, `Report`, `Issue`, `Change`, and all five `CollectionService` methods from the contracts. `CollectionTestData.facts()` returns 40 synthetic monster records keyed1–40; `.owned()` returns one of each; `.deck(UUID id)` returns a list named Fixture with Main1–40 and empty Extra/Side. This fixture lives only in test sources.

- [x] Write activation and invalidation tests:

```java
var service = new CollectionService(CollectionTestData.facts());
var id = UUID.randomUUID();
var saved = service.save(PlayerCollectionData.empty(), 0, false, CollectionTestData.deck(id));
assertTrue(saved.success());
assertEquals(CollectionError.INELIGIBLE, service.activate(saved.data(), 1, false, id).error());
var stocked = service.replaceCounts(saved.data(), 1, false, CollectionTestData.owned());
var active = service.activate(stocked.data(), 2, false, id);
assertEquals(id, active.data().activeDeckId());
var reduced = new HashMap<>(active.data().counts());
reduced.remove(1);
var withdrawn = service.replaceCounts(active.data(), 3, false, reduced);
assertTrue(withdrawn.success());
assertNull(withdrawn.data().activeDeckId());
assertTrue(withdrawn.data().decks().containsKey(id));
assertEquals(CollectionError.STALE, service.delete(withdrawn.data(), 3, false, id).error());
```

- [x] Run `./gradlew.bat test --tests '*CardCatalogTest' --tests '*DeckEligibilityTest' --tests '*CollectionServiceTest'` and confirm intended failures.
- [x] Load card facts from a dedicated read-only connection to `CardData.load().join().database()` during server initialization. Use `Class.forName("org.sqlite.JDBC")` as existing code does, set read-only in connection configuration before opening, query `SELECT id,type FROM datas`, and close the connection. Construct the immutable map once; no SQL per click and no native API changes. Unit-test with a temporary tiny SQLite database, including nonexistent file rejection rather than creating it.
- [x] Implement a common gate: busy -> BUSY, revision mismatch -> STALE, malformed input -> INVALID, missing ID -> NOT_FOUND; none change data. Service constructor receives immutable facts. Save is an upsert scoped to that player's map, delete removes only that ID, activate requires a complete eligible list, clearActive clears selection. No ownership checks on Save.
- [x] Implement eligibility exactly as the contracts specify. Count copies across all sections once so `DeckValidator`'s Main/Extra count messages are not duplicated. Missing-card quantities are independent of structural errors. Truncate issues only after determining eligibility; `moreProblems=true` also means ineligible. Format problems using translation keys, not localized server strings.
- [x] Revalidate an active list after Save/Delete/replaceCounts; keep valid selection, clear invalid selection, preserve list contents. Return updated revision once even when active selection also changes. Catch checked arithmetic overflow as INVALID without altering state. Test busy operations, deletion, rename, duplicate, over-limit draft save, Side-only shortage, unknown IDs, token rejection, placement, and no auto-reactivation. Run targeted tests; commit `feat: validate collection deck activation`.

## Task 3: Private, bounded network protocol

**Create:** `collection/CollectionCommand.java`, `CollectionReply.java`; `server/collection/CollectionRequestPayload.java`, `CollectionReplyPayload.java`, `CollectionPayloadHandler.java`, `CollectionSnapshotStore.java`. **Modify:** `server/DuelNetworking.java`, `DuelManager.java`, `ServerPayloadHandler.java`, `DuelCommand.java`. **Tests:** `server/collection/CollectionPayloadTest.java`, `CollectionSnapshotStoreTest.java`, `CollectionHandlerTest.java`.

**Interfaces:** records and limits match the contracts. `CollectionSnapshotStore.open(UUID owner, PlayerCollectionData data, long now)` returns `CollectionReply.Opened`; `.page(UUID owner, CollectionCommand.Page request, long now)` returns a page or Rejected; `.invalidate(UUID owner)` and `.clear()` remove snapshots. `CollectionPayloadHandler.handle(CollectionRequestPayload, IPayloadContext)` is the request boundary. Store the service/snapshot store for the current server lifecycle, not across server restarts in unchecked static state.

- [x] Start with payload round trips and this ownership test:

```java
var store = new CollectionSnapshotStore();
var alice = UUID.randomUUID();
var bob = UUID.randomUUID();
var data = new PlayerCollectionData(0, Map.of(1, 999L), Map.of(), null);
var opened = store.open(alice, data, 0);
var request = new CollectionCommand.Page(opened.snapshotId(), CollectionCommand.PageKind.COUNTS, 0);
assertInstanceOf(CollectionReply.Rejected.class, store.page(bob, request, 1));
assertInstanceOf(CollectionReply.Counts.class, store.page(alice, request, 1));
store.invalidate(alice);
assertInstanceOf(CollectionReply.Rejected.class, store.page(alice, request, 2));
```

- [x] Run `./gradlew.bat test --tests '*CollectionPayloadTest' --tests '*CollectionSnapshotStoreTest' --tests '*CollectionHandlerTest'`; establish failing behavior.
- [x] Implement explicit discriminant codecs with bounds checked before allocations, including negative sizes, duplicate IDs, unknown tags, excessive UTF, and >24KiB input. Round-trip optional fields and Report.moreProblems. Test maximum UTF encodings and total envelope size against the byte budget; bound issue keys as well as deck names. Decode only whitelisted record shapes; expose no replaceCounts packet.
- [x] Register request/reply handlers using registrar `3`; use default MAIN handlers. For each mutation: resolve authenticated sender -> valid attachment -> current busy state -> service operation -> setData on success -> invalidate snapshot -> send Changed. On failure send Rejected to that sender only. Keep attachments private and clear snapshot entries on logout/server stop.
- [x] Add busy/revision tests at the actual handler seam with injected sender/data access or a minimal fixture; do not mock only the pure service and call that packet verification. Guard current legacy upload and `/duel deck clear` while busy now. They remain development-era ownership paths until milestone 4, so this intermediate build is not a collection-enforced release.
- [x] Test >256 count records, >32 lists, both empty page kinds, index mismatch, expiry, other-owner requests, mutation during page assembly, and replay of an accepted Save with the original revision. Confirm aggregate collections can exceed a packet without an aggregate capacity cap. Run targeted tests and commit `feat: synchronize private collection views`.

## Task 4: Real catalog and acknowledged editor saves

**Create:** `client/collection/ClientCollectionState.java`, `CollectionClient.java`, `CollectionCatalog.java`, `CollectionSearchWorker.java`, `DeckSaveHandler.java`. **Modify:** milestone 1 `CollectionScreen.java`, `CollectionController.java`, `DeckEditorModel.java`; `DuelcraftClient.java`; `client/ClientPayloadHandler.java` only for needed delegation; `assets/duelcraft/ui/collection_screen.xml`, `assets/duelcraft/lang/en_us.json`. **Tests:** `client/collection/ClientCollectionStateTest.java`, `CollectionCatalogTest.java`, `CollectionSearchWorkerTest.java`.

**Interfaces:**

```java
@FunctionalInterface public interface DeckSaveHandler {
    CompletionStage<SavedDeck> save(String name, DeckList submitted);
}
// CollectionClient methods; all callbacks/state application on Minecraft's client thread.
public CompletionStage<CollectionReply> request(CollectionCommand command);
public void disconnect();
// CollectionCatalog
public static List<CardInfo> load(Path database) throws SQLException;
// CollectionSearchWorker
public void submit(List<CardInfo> cards, String text, CardSearch.Filters filters,
        Map<Integer,Long> counts, DeckList draft, Consumer<List<CardInfo>> apply);
public void close();
```

`ClientCollectionState` stages pages, exposes immutable complete counts/summaries/revision/activeId, and never replaces a complete snapshot with partial data. `CollectionClient` owns request IDs, pending futures, connection generation, sequential page pulls, and failure/timeouts (10 seconds per request). ReadDeck contents must match the view revision; otherwise refresh without losing unsaved local edits.

**Completed M1 integration seams:**

- Keep one small owner of the selected saved-list UUID, editable name, acknowledged name/cards baseline, and pending read/navigation. An optional `SavedDeckController` may isolate this lifecycle from widget wiring. Generate new/duplicate UUIDs once; name-only edits are dirty. Accept ReadDeck only for the still-requested UUID and current view revision. Snapshot refresh must preserve the selected dirty draft.
- Capture submitted UUID/name/cards before Save. Freeze draft/list mutations and duplicate saves while pending, retain inspect/search, and defer close/list switching until the matching success is applied on the client thread. Record the submitted/returned baseline, not whatever draft happens to exist later. Failure, timeout, disposal, or disconnect must not complete deferred navigation or clear edits.
- Add ownership replacement to `DeckEditorModel` without changing draft or acknowledged baseline. Refresh badges, inspector, filters, summary, and search generation only from complete snapshots. Test ownership changes during dirty drafts and pending Save.
- Inject query submission/application into the screen/controller. Production owns a worker; sample scenarios use deterministic injected queries. Inject scheduler/executor/client dispatch for debounce/generation tests without sleeps. Define loading/failed/empty catalog states and reject late results after disposal/disconnect. Resize retains model/worker. Supersede queries on text/filter/order, ownership, draft, catalog, and selected-list changes; preserve M1 virtual-grid and scroll behavior.
- Unknown saved IDs already have placeholder deck tiles. Also show a passcode inspector fallback and enable Remove by section membership even without metadata; keep Add disabled for unknown IDs. Verify select/remove/save in a UI scenario.
- Extend `DeckEditorModelTest` and focused controllable-future lifecycle tests for these seams. Update all three existing `CollectionLayoutScenario`, `CollectionSearchScenario`, and `CollectionOverflowScenario` fixtures when changing factory/save/query signatures.

- [x] Test empty snapshot completion, out-of-order/wrong-revision pages, stale-connection replies, save failure and timeout, and late completion after disconnect. Test search generations with a controllable executor rather than sleeps: apply newer result then complete older, assert only newer is visible.
- [x] Run `./gradlew.bat test --tests '*ClientCollectionStateTest' --tests '*CollectionCatalogTest' --tests '*CollectionSearchWorkerTest'`; verify failures.
- [x] Load catalog metadata once on a dedicated worker/read-only connection, close JDBC after creating the immutable list, and reuse milestone 1 filtering. Add loading/failed/empty results states. A failed artwork download retains metadata and placeholder. Avoid borrowing CardDatabase's existing connection across workers. Use injected catalog fixtures for tests.
- [x] Replace milestone 1's synchronous `Consumer<DeckList>` save with `DeckSaveHandler` in factory/controller/scenarios. Sample fixtures return `CompletableFuture.completedFuture(new SavedDeck(id,name,submitted))`. Production callback submits `CollectionCommand.Save(currentRevision,new SavedDeck(id,name,submitted))` and accepts only the matching Changed reply with that saved list.
- [x] Freeze editing during pending save; allow inspecting/searching. On client-thread success update baseline and revision, then perform requested close/navigation; on error retain draft/dirty state and explain STALE/BUSY/invalid name. Never call markSaved when the packet is merely sent. Refresh authoritative counts/summaries after mutation. Local active-state indicators wait for server reply.
- [x] Add a saved-list picker with New/Rename/Duplicate/Delete/Activate/Clear Active. Use Save upsert for rename/duplicate, new UUID for duplicate, explicit confirm for delete, and Save/Discard/Cancel when changing a dirty draft. Preserve selection by UUID. This picker can be a controller/panel in the current screen; add `SavedDeckController.java` only if it keeps lifecycle/list actions separate from card-grid rendering. Wire missing-card explanations and supported-validation copy. Run tests and commit `feat: connect deck editor to player collections`.

## Task 5: Live persistence and UI integration verification

**Create:** `client/uitest/CollectionPersistenceScenario.java`, `CollectionSavedDeckScenario.java` (`DEV_ONLY`, group `duelcraft`, names `collection_persistence`, `collection_saved_decks`). **Modify:** existing collection fixture/scenarios for asynchronous saving.

**Interfaces:** real UI/packet path; server fixture operations through `ScenarioBuilder.server` and `waitUntilServer`, as in `DuelDeckUploadScenario`. Capture the original attachment and restore it in teardown; use a disposable test world. Only these fixtures may set artificial counts directly.

- [x] Add a failing scenario that creates an unowned draft via the UI, receives save acknowledgement, closes/reopens the editor, selects the saved list, and fails activation with a visible shortage.
- [x] Add server-side attachment serialization/clone checks, then actual death/respawn and logout/rejoin/restart checks in a disposable world. Verify a count of1000 survives and saved IDs/name/Side/active selection survive. Do not treat codec round-trip alone as a restart test.
- [x] Wire a development-only `/duel collection` client command to open the real editor using CollectionClient, with a server busy check before management data opens. Register only in the development environment; production entry points arrive in milestone 5. Handle unreadable storage with DATA_UNAVAILABLE and show recovery guidance without modifying it.
- [x] Run `./gradlew.bat test`, `./gradlew.bat runClient -PldTest=collection_persistence`, `./gradlew.bat runClient -PldTest=collection_saved_decks`, and the milestone 1 scenarios. Record results/screenshots and any prerequisites that prevented a run. Run a two-player dedicated-server privacy check; integrated-server fixture success is not a substitute.
- [x] Commit `test: verify collection persistence and saved deck UI` and record milestone results in the roadmap. Do not start physical transfers until these state/acknowledgement checks pass.

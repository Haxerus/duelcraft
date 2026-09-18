# Player interaction implementation contracts

This document resolves shared implementation choices for milestones 2–6 of the [design](2026-09-15-player-interaction-design.md). Engineering choices remain implementation defaults unless identified as user-confirmed. The [roadmap](../plans/2026-09-15-player-interaction-roadmap.md) indexes the executable plans.

User clarification on 2026-09-17 confirms deposited-only ownership, Main+Extra+Side ownership checks even for single duels, passcode as the primary key with separate ownership per passcode, and the currently planned legality scope. The user will handle acquisition later; it is not an open question or prerequisite for this integration.

## Baseline and execution

Milestone 1 is complete in `codex/deck-editor-m1` through `2030dae`, including the user's approved dark styling matching the duel UI; see its [report](../reports/2026-09-17-deck-editor-m1.md). Its five implementation commits have been reconciled onto main `bc2fcba` in `codex/player-collections`; all 720 unit tests pass at that baseline. Later milestones must extend this actual editor, preserving its dark styling and existing engine/cache improvements. Minecraft 1.21.1, NeoForge 21.1.224, Java 21, and LDLib2 2.2.39.a remain the relevant versions. The browser prototype's light palette is superseded.

All Java paths below start at `src/main/java/com/haxerus/duelcraft/`. Matching unit tests live under `src/test/java/com/haxerus/duelcraft/`. Implement milestones in order. Retain their independent review/test boundaries; do not run all six as an unreviewed change.

## Models and storage (milestone 2)

Use these immutable records, with defensive copies and explicit constructor/codec validation:

```java
// collection/SavedDeck.java
public record SavedDeck(UUID id, String name, DeckList cards) {}
// collection/PlayerCollectionData.java
public record PlayerCollectionData(long revision, Map<Integer, Long> counts,
        Map<UUID, SavedDeck> decks, @Nullable UUID activeDeckId) {
    public static PlayerCollectionData empty();
}
// collection/DeckEligibility.java (nested Issue/Report types)
public record Issue(String key, int code, int actual, int limit) {}
public record Report(List<Issue> problems, Map<Integer, Integer> missing, boolean moreProblems) {
    public boolean eligible();
}
```

`DeckList` comes from milestone 1. Saved-deck IDs are random UUIDs; clients can generate one for a new/duplicate draft. A UUID is only meaningful within the authenticated owner's attachment. Names are 1–128 printable characters; duplicates are allowed, identity is the UUID. Blank or control-character names fail with an actionable message.

Serialize `PlayerCollectionData` through a NeoForge attachment `duelcraft:collection` with `copyOnDeath()`. Do not register automatic entity tracking sync. Schema version 1 stores revision, positive passcode/count pairs, saved lists, and optional active ID. Counts are positive signed 64-bit integers; absent means zero. Omit zero entries. Use checked arithmetic. The collection and number of saved decks have no gameplay capacity limit. Active ID must refer to a stored list or decode fails. Codec tests reject invalid versions/records; never silently rewrite failed loads as empty player data. At the load boundary, preserve malformed attachment NBT and deny management access with an error until repaired; document the serializer wrapper in milestone 2.

Draft storage/transport accepts 0–512 cards **in total across all three sections** and positive passcodes, including unknown IDs and over-limit decks. This is a request/storage bound, not a rule declaring those drafts playable. UI describes a rejected oversized import as exceeding the editor's draft limit. Existing game legality remains 40–60 Main, <=15 Extra, <=15 Side and <=3 copies per exact passcode across all sections. Save bypasses gameplay/ownership eligibility. Activation checks both. Renaming preserves activation; save/delete/withdraw clears selection if it becomes invalid. Depositing never auto-activates.

Ownership counts include Main+Extra+Side even for single duels and only deposited cards. Carried inventory cards must first be deposited. Passcode is the primary key; each passcode has separate ownership. Do not model alternate artwork as an identity or substitute/group passcodes for ownership. The user approved the planned ownership, size, copy-count, known-card and placement checks. Banlists, alias-group limits, full Rush/Speed-specific deck rules, and match sideboarding are not included; UI must describe the supported checks accurately.

## Card facts and query data (milestone 2)

Create `core/data/CardCatalog.java` with `record Facts(int code, int type)` and `static Map<Integer, Facts> load(Path database) throws SQLException`. Read `SELECT id,type FROM datas` once using a dedicated read-only JDBC connection and close it. Use the existing managed snapshot from `CardData.load()`. Do not import client classes or change native engine callbacks/scripts. Java already manages/merges the database; this adds a host-policy metadata read, deliberately avoiding a new JNI metadata API.

`DeckEligibility.check(DeckList list, Map<Integer,Long> counts, Map<Integer,CardCatalog.Facts> facts, DuelRule rule)` returns a `Report`. Retain `DeckValidator` structural behavior through the list's engine snapshot, add Side counts/copy aggregation, known-ID and type checks. Reject tokens and cards without a playable Monster/Spell/Trap category. Extra requires Fusion/Synchro/Xyz/Link monsters; Main forbids these Extra-only types. Side accepts playable cards of either placement. A rule parameter documents preparation context; do not claim checks it does not implement.

The client uses a separate `client/collection/CollectionCatalog` read of names/text/stats into `List<CardInfo>` on a single worker. Keep announce-card JDBC access untouched. `client/collection/CollectionSearchWorker` runs milestone 1's pure search on immutable snapshots, debounces text input by 150 ms, and ignores superseded generations. No SQL, sorting of the entire catalog, or network fetches in a render callback. Unknown saved IDs show a passcode placeholder and can be removed/saved.

## Server operations and revisions (milestones 2–4)

`server/collection/CollectionService` owns pure state transformations; its server adapter obtains the attachment and calls `ServerPlayer.setData`. The service receives the card facts and a busy flag; it does not query client state. Every successful mutation increments the player's revision exactly once with checked arithmetic. Rejection leaves all state unchanged. A repeated request with an old revision returns STALE, never applies again. Read and mutation packets derive identity from `context.player()`.

```java
// collection/CollectionError.java
public enum CollectionError { NONE, BUSY, STALE, INVALID, NOT_FOUND, INELIGIBLE,
    INVENTORY_FULL, INSUFFICIENT_CARDS, DATA_UNAVAILABLE }
// Nested in CollectionService; return values, not exceptions for user errors.
public record Change(PlayerCollectionData data, CollectionError error,
        DeckEligibility.Report eligibility) { public boolean success(); }
public Change save(PlayerCollectionData before, long expectedRevision, boolean busy, SavedDeck deck);
public Change delete(PlayerCollectionData before, long expectedRevision, boolean busy, UUID id);
public Change activate(PlayerCollectionData before, long expectedRevision, boolean busy, UUID id);
public Change clearActive(PlayerCollectionData before, long expectedRevision, boolean busy);
public Change replaceCounts(PlayerCollectionData before, long expectedRevision, boolean busy,
        Map<Integer,Long> counts); // SERVER-INTERNAL, never accept this map from the client
```

Construct `CollectionService(Map<Integer,CardCatalog.Facts> facts)`. Activation uses current supported checks with MR5 as the existing default; preparation rechecks using the selected `DuelRule`. `Change.eligibility` is nonnull, with empty lists/maps when not applicable. On an invalidating successful mutation it describes why activation was cleared. Busy comes from `DuelManager.isBusy` and later includes STARTING as well as RPS/live play. Read management data can be denied while busy; mutations must always be denied. Milestone 2 temporarily adds this guard to the legacy deck upload/clear paths; milestone 4 removes their ownership bypass entirely.

## Network contract (milestones 2–3)

Use `server/collection/CollectionRequestPayload(UUID requestId, CollectionCommand command)` and `CollectionReplyPayload(UUID requestId, CollectionReply reply)` with explicit bounded codecs. Put sealed command/reply types in `collection/CollectionCommand.java` and `CollectionReply.java`. They contain data only; no client UI classes. Enumerate tags rather than serializing class names.

Commands, as nested records of `CollectionCommand`:

| Record | Fields |
| --- | --- |
| Open | none |
| Page | `UUID snapshotId, PageKind kind, int index` |
| ReadDeck | `UUID id` |
| Save | `long expectedRevision, SavedDeck deck` |
| Delete / Activate | `long expectedRevision, UUID id` |
| ClearActive | `long expectedRevision` |
| Deposit / Withdraw (M3) | `long expectedRevision, int code, int amount` |
| DepositAll (M3) | `long expectedRevision` |

`PageKind` is `COUNTS` or `DECKS`. Replies, nested in `CollectionReply`:

| Record | Fields |
| --- | --- |
| Opened | `UUID snapshotId, long revision, int countPages, int deckPages, @Nullable UUID activeId` |
| Counts | `UUID snapshotId, long revision, int index, Map<Integer,Long> entries` |
| Decks | `UUID snapshotId, long revision, int index, List<Summary> entries` |
| Deck | `long revision, SavedDeck deck` |
| Changed | `long revision, @Nullable UUID activeId, @Nullable SavedDeck saved, int transferred, DeckEligibility.Report eligibility` |
| Rejected | `CollectionError error, long revision, DeckEligibility.Report eligibility` |

Use the common `CollectionError` enum in both `Change` and `Rejected`. `Summary(UUID id,String name,int main,int extra,int side)` is nested in `CollectionReply`. `Changed.saved` is present only for Save; `transferred` is zero outside transfer operations.

One snapshot per authenticated player, captured from immutable state, with counts sorted by passcode and summaries by name/UUID. Pages hold at most 256 count entries or 32 deck summaries, each encoded message <=24 KiB including its envelope. Saved-deck contents travel separately, bounded to 512 cards. Issue keys are at most128 printable ASCII characters; replies hold at most64 issues plus the `moreProblems` boolean in `Report`; missing counts have at most512 keys. Set `moreProblems` false when no issues were omitted. These caps bound messages rather than total collection size. Verify actual encoded lengths in codec tests, including maximum UTF encodings, instead of treating entry counts alone as a byte bound.

Capture a snapshot on Open; pull one page at a time; refresh its 30-second idle expiry on each page read. New Open replaces that player's snapshot. A mutation invalidates it; a late page gets STALE. Validate snapshot ownership, page kind/index, unique keys/IDs, declared sizes, exact completion, and matching revision. Grow assembly only as bounded pages arrive; do not allocate an array from an untrusted advertised total. Publish the new client snapshot only after all pages arrive. Zero-page snapshots still complete. Request IDs plus a client connection generation prevent replies from previous connections replacing state. Clear pending reads and complete pending saves exceptionally on disconnect. Never automatically replay a failed mutation; refresh and let the player retry.

Use NeoForge's default MAIN handler thread (verified in pinned `PayloadRegistrar`). Check busy/revision and apply state/inventory synchronously there. Bump network registrar from `2` to `3` in milestone 2; milestone 4 removes old payload semantics and bumps to `4`. Milestone 3/5 additions ship as the same in-development release; bump again if they are released separately.

## Inventory semantics (milestone 3)

Register `duelcraft:card` with stack size 64 and positive-int data component `duelcraft:card_code`. Counts can exceed 64 after deposit. Deposit scans main inventory slots 0–35 and offhand 40; exclude armor, cursor stacks, containers, and Ender Chest. Withdraw fills compatible stacks then empty main slots, never offhand or dropped entities. Requested amount is 1–4096; actual inventory capacity/ownership still governs success. DepositAll deposits supported stacks only and reports transferred/skipped counts in UI; add `int skipped` to `Changed` in M3 with zero on other actions.

Only stacks equal in item/components to `CardItem.stack(code,count)` are depositable. Keep unsupported customized stacks intact and identify that reason rather than silently stripping components. Unknown IDs remain withdrawable if owned (to avoid trapping cards after data changes), but cannot activate a deck or enter through deposit until server facts recognize them. This version does not store cosmetic variants.

Simulate on copies of accessible inventory slots and counts; on success apply the complete slot patch and revised attachment synchronously. Return all-or-nothing for a selected-amount transfer. DepositAll atomically applies the supported subset, leaving skipped stacks untouched; skipped counts measure card copies, not stacks. If no supported cards can move, return INSUFFICIENT_CARDS without incrementing the revision. Do not rely on `Inventory.add` partially mutating then dropping overflow. Inventory and attachment are saved by the player's normal lifecycle; do not claim transactional durability across a forced crash during Minecraft disk writes.

## Preparation and routes (milestones 4–5)

One outstanding invitation per participant; no silent replacement. Pending invitations do not lock editing. The sender must have an eligible active list; the target may choose theirs before acceptance. On accept, validate both current lists, capture immutable snapshots, invalidate invitations involving either participant, and lock both through RPS, first choice, STARTING, and live play. Cancel/expiry/logout/start failure releases both. Use invitation/roll IDs on GUI actions so an old button cannot answer a new invitation or RPS round. Do not reveal the opponent's chosen hand before both submit.

Retain the existing 60-second timeout per invitation and each first-turn step, with a fresh round ID on ties. Host rule/options are fixed when invited. Defaults remain MR5, 8000 LP, hand 5, draw 1; bounds match commands: LP1–99999, hand0–20, draw0–10. An omitted seed is generated server-side. Server AI decks need no player collection; the human's solo deck does.

One `DuelcraftScreens` route coordinator serves home key, binder, and mat. A server reply resolves HOME/COLLECTION/LOBBY requests to management, preparation, or the live duel. Binding default: J, remappable in Minecraft Controls. Consume it only with player/world present and no other screen open. A placed mat holds no inventories/player data and grants no permissions unavailable via the hotkey.

Management Save becomes `CompletionStage<SavedDeck>` in M2. Freeze draft edits while saving and clear dirty state only after success for that submitted draft. Failure preserves edits. Home/lobby navigation honors Save/Discard/Cancel. A server-enforced preparation transition suspends unsaved edits locally rather than discarding them; resume after cancel/end in the same connection. Disconnect clears that local state; do not invent durable offline drafts.

Normal `/duel deck set <localName>` becomes local YDK import/save, then an activation attempt; failure to activate retains the imported list. Retain `/duel deck list` as local files, `/duel deck get` as authoritative selection, `/duel deck clear` through service checks. Add UI import in the saved-list picker. No normal-player raw upload bypass remains. Do not add unrestricted debug duels; a permission-level-2 card-grant command is sufficient for development.

## Requirement coverage

| Requirement | Owning tasks and acceptance |
| --- | --- |
| Ender Chest-like private collection | M2 tasks1/3/5: attachment, owner-only pages, clone/rejoin/restart |
| Save unowned/incomplete drafts; reuse copies across lists | M2 task2 pure policy, task4 acknowledged list UI |
| Active selection requires cards and supported legality | M2 task2; M4 task3 rechecks at actual duel startup |
| Withdrawal invalidates selection without deleting list | M3 tasks2/3/4 conservation and UI checks |
| Physical storage without slot limits | M2 counts/schema/paging; M3 canonical card items and transfers |
| No mutation while preparing/dueling | M2 gates; M4 tasks2–5 full lock, failure cleanup, legacy removal |
| Existing seed/first-player behavior | M4 task2 challenger/accepter snapshots and task3 shuffle regression |
| Main/Extra/Side grids and rich search | M1; M2 task4 real asynchronous catalog and preserved announce search |
| Saved-list CRUD/import/selection | M2 task4 picker; M4 task1 complete YDK import |
| Item-free home plus binder/mat shortcuts | M5 tasks1/2/4 and task5 two-player journey |
| Private invites, RPS and first-player UI | M4 service/protocol; M5 task3 screens |
| No inventory loss/duplication from rejected or repeated requests | M3 planner/handler tests; M6 dedicated-server sequences |
| Scaling/readability/performance | M1 harness; M5 new-screen matrix; M6 task2 measurements/task3 human feedback |
| Deployment/upgrade instructions and truthful validation | M6 candidate evidence, compatibility audit, docs, final sign-off |

Every remaining milestone has a complete task plan; none of the acceptance checks in this table have been executed by writing these documents. The plans remain subject to evidence-backed adjustments when their prerequisite code and playtests exist.

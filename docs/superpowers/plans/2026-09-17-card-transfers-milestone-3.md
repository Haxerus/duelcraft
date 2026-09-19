# Physical Cards and Transfers Implementation Plan — Milestone 3

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Let players deposit physical cards into their collection and withdraw them without loss, duplication, or invalid active selections.

**Architecture:** One registered card item carries a passcode component. A pure inventory planner computes complete slot/count changes; the server checks busy state/revision, simulates, validates the new collection, and commits both on the main thread. The editor displays the acknowledged result.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, JUnit 5.

**Spec:** [Design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: [milestone 2](2026-09-17-player-collections-milestone-2.md) and its [policy follow-up](2026-09-19-deck-use-policy-follow-up.md).

## Global constraints

- Use the term **collection**.
- Withdrawal revalidates the active deck under configured ownership and companion restrictions. A shortage alone clears selection only when `requireCardOwnership=true`. Preserve the saved list.
- Prevent collection transfers and deck changes during a duel, in both the interface and server handlers.
- Depositing never auto-activates a deck. Server-only state owns quantities.
- Follow the contracts' accessible slots, canonical components, amount bounds, and all-or-nothing selected transfers. No pack economy, loot/recipes, or cosmetic variants.

Java paths start at `src/main/java/com/haxerus/duelcraft/`, tests at `src/test/java/com/haxerus/duelcraft/`, resources at `src/main/resources/`.

## Task 1: Physical item and passcode representation

**Create:** `item/CardItem.java`, `item/CardComponents.java`; `assets/duelcraft/models/item/card.json`. **Modify:** `Duelcraft.java` registrations/creative tab, `assets/duelcraft/lang/en_us.json`. **Tests:** `item/CardItemTest.java`.

**Interfaces:** `CardComponents.CARD_CODE` is `DeferredHolder<DataComponentType<?>,DataComponentType<Integer>>`; `CardItem.stack(int code,int count)` returns a canonical stack from registered `duelcraft:card`, and `CardItem.code(ItemStack)` returns `OptionalInt`. `CardItem.isCanonical(ItemStack)` compares item/components with a canonical stack; count does not affect component equivalence. Store max stack64.

- [x] Write registry-aware component serialization tests: distinct codes cannot merge; same codes can; absent/nonpositive component is not depositable; quantities1 and64 have equivalent components. Use the existing NeoForge unit-test environment and bootstrap registries only if its tested mod has not done so.

```java
var a = CardItem.stack(89631139, 1);
var b = CardItem.stack(89631139, 64);
var other = CardItem.stack(46986414, 1);
assertTrue(ItemStack.isSameItemSameComponents(a, b));
assertFalse(ItemStack.isSameItemSameComponents(a, other));
assertEquals(89631139, CardItem.code(a).orElseThrow());
```

- [x] Run `./gradlew.bat test --tests '*CardItemTest'` and establish failure for missing item/component behavior.
- [x] Register the integer component with persistent `Codec.INT` validated positive and network `ByteBufCodecs.VAR_INT` with equivalent validation. Centralize positivity checks in stack construction and request decoding; never trust a packet to describe an actual inventory item. Register the component deferred register on the mod bus.
- [x] Use the existing card-back asset for an initial recognizable item, not a new asset generator:

```json
{"parent":"minecraft:item/generated","textures":{"layer0":"duelcraft:card_back"}}
```

Client tooltip shows passcode and cached card name when available; server/common item code must not statically load `DuelcraftClient`. Put optional display lookup in a client tooltip event handler in `client/collection/CardItemTooltip.java`, registered by `DuelcraftClient.java`. Missing metadata retains passcode text. Do not show a positive-ID-less card as a normal collectible in the creative tab.
- [x] Verify component round trips and a dedicated-server launch without client-class loading, then commit `feat: add physical card items`.

## Task 2: Inventory transaction planner

**Create:** `server/collection/InventoryTransferPlan.java`, `CardTransferService.java`. **Tests:** `server/collection/InventoryTransferPlanTest.java`, `CardTransferServiceTest.java`.

**Interfaces:** nested pure planner types below. Input slots are copies of main0–35 plus offhand40 only. `code=0,count=0` means empty; `code=-1` means occupied/unusable and must remain unchanged. Positive code means a canonical card stack; server catalog validity is applied separately for deposit eligibility.

```java
public enum Kind { DEPOSIT, WITHDRAW, DEPOSIT_ALL }
public record Slot(int index, int code, int count) {}
public record Result(List<Slot> slots, Map<Integer,Long> counts,
        int moved, int skipped, CollectionError error) {}
public static Result plan(List<Slot> slots, Map<Integer,Long> counts,
        Set<Integer> depositableCodes, Kind kind, int code, int amount);
// CardTransferService
public CardTransferService(CollectionService collections, Set<Integer> depositableCodes);
public CollectionReply apply(ServerPlayer player, long expectedRevision,
        InventoryTransferPlan.Kind kind, int code, int amount);
```

No-op/error results retain input values, moved0; immutable copies protect against caller mutation. Withdrawal uses only slots0–35. Deposit counts skipped canonical unknown stacks, and the adapter counts unsupported card stacks separately for DepositAll. Noncard occupied slots are not reported as skipped cards.

- [x] Write pure conservation/capacity tests before implementation:

```java
var slots = IntStream.range(0, 36).mapToObj(i -> new InventoryTransferPlan.Slot(i, -1, 64)).toList();
var result = InventoryTransferPlan.plan(slots, Map.of(7, 10L), Set.of(7),
        InventoryTransferPlan.Kind.WITHDRAW, 7, 1);
assertEquals(CollectionError.INVENTORY_FULL, result.error());
assertEquals(Map.of(7, 10L), result.counts());
assertEquals(slots, result.slots());

var deposit = InventoryTransferPlan.plan(List.of(new InventoryTransferPlan.Slot(0, 7, 20)),
        Map.of(7, 1000L), Set.of(7), InventoryTransferPlan.Kind.DEPOSIT, 7, 5);
assertEquals(1020L, deposit.slots().getFirst().count() + deposit.counts().get(7));
assertEquals(15, deposit.slots().getFirst().count());
assertEquals(1005L, deposit.counts().get(7));
assertEquals(5, deposit.moved());
```

The conservation assertion and exact resulting quantities must both pass.
- [x] Run `./gradlew.bat test --tests '*InventoryTransferPlanTest' --tests '*CardTransferServiceTest'` for intended failures.
- [x] Simulate deposits in ascending main-slot order then offhand. Selected amount must all exist as eligible stacks or return INSUFFICIENT_CARDS; use checked count addition before any mutation. For withdraw, check stored amount, fill same-code stacks to64 then empty main slots; fail without changes if capacity is short. For DepositAll, sum supported stacks with checked arithmetic and remove only that supported subset.
- [x] Implement the real adapter: snapshot accessible ItemStacks; reject busy/unreadable/stale first; classify canonical and unsupported stacks; run planner; call `CollectionService.replaceCounts` with authenticated owner context and planned counts, so the shared policy evaluates proposed post-withdrawal state; if both succeed apply slot replacements and attachment in the same server-thread call, mark inventory changed and broadcast container changes. Do no asynchronous work between validation and commit. Preserve untouched ItemStack objects/components, including offhand and unsupported custom cards. Never use partial `Inventory.add` followed by dropping a remainder.
- [x] Add table tests for exact capacity, partial-stack capacity, amounts0/negative/4097, overflow at Long.MAX_VALUE, unknown owned withdrawals, mixed-code stacks, offhand deposit, offhand not used for withdraw, unsupported components, empty bulk deposit, and repeated original-revision request. Test ownership off/no hook preserves an otherwise legal selection after a shortage; ownership on/no hook clears it; an independent companion denial clears it under either setting. A throwing hook leaves both inventory and attachment unchanged. Failure does not increment revision; actual stored ownership is always required to withdraw. Run targeted tests; commit `feat: transact inventory cards with collections`.

## Task 3: Transfer packets and editor controls

**Modify:** milestone 2 `CollectionCommand.java`, `CollectionReply.java`, request/reply codecs and `CollectionPayloadHandler.java`; `client/collection/CollectionController.java`, `ClientCollectionState.java`; collection XML/lang. **Create:** `client/collection/CardTransferController.java` only to own transfer form/request state. **Tests:** extend `CollectionPayloadTest.java`, `CollectionHandlerTest.java`, `ClientCollectionStateTest.java`.

**Interfaces:** add Deposit/Withdraw/DepositAll from the contracts; extend Changed with `int skipped`, zero outside transfers. Handler delegates to `CardTransferService.apply` with sender identity and main-thread execution; add no packet that sets counts or inventory slots. Reuse `CollectionClient.request` and revision refresh.

- [x] Test decoding rejects invalid amount/code before allocating data, and round-trip all three operations. At the handler seam, replay accepted withdrawal with its original revision and assert one inventory change total.
- [x] Run the targeted protocol/handler tests to expose missing commands/semantics.
- [x] Add selected-code Deposit/Withdraw amounts with a positive numeric field, owned/carried counts clearly distinguished, and Deposit carried cards. Disable request controls until the pending response resolves. Refresh inventory-derived carried counts from Minecraft's authoritative inventory sync and stored counts via a new collection snapshot after Changed; never optimistic-credit cards.
- [x] Translate capacity/busy/stale/missing/unsupported errors. On shortage-driven invalidation with ownership required, show "Active deck cleared: required cards were withdrawn" plus shortages. For a companion denial show its actual reason; when ownership is optional, show shortages neutrally without an invalidation warning; retain its list in the picker. DepositAll shows moved and skipped counts. If no supported cards can move, return INSUFFICIENT_CARDS without a revision increment and explain that no eligible carried cards were found.
- [x] Test rejection preserves draft, selected card, scroll offsets, and collection counts; success refreshes owned/in-list indicators without changing the card list. Commit `feat: connect collection transfer controls` after targeted tests pass.

## Task 4: Real inventory scenarios and development card grant

**Create:** `server/CardGrantCommand.java`, `client/uitest/CollectionTransferScenario.java`; **Modify:** `Duelcraft.java` command registration. **Tests:** `server/CardGrantCommandTest.java` (permission and bounds), real scenario named `collection_transfers` in `group:duelcraft`, DEV_ONLY.

**Interfaces:** `/duel card give <player> <passcode> <count>` requires permission level2, known playable code and count1–4096. Simulate capacity through the same planner using temporary counts for the requested grant, then apply only inventory changes; it must never alter collection directly or silently drop overflow. Reject while target busy. It is a development/admin source of physical cards, not a player-accessible progression system.

- [ ] Write a command permission test where a nonoperator cannot execute give and an operator with insufficient target capacity gets failure and no items. Ensure direct transfer packets provide no equivalent grant.
- [ ] In the disposable-world scenario snapshot attachment/inventory, seed physical canonical stacks server-side, deposit20 through the UI, and withdraw5. Assert physical/stored counts: start20/0 -> deposit20 gives0/20 -> withdraw5 gives5/15. Save a draft requiring16 copies for an ownership indicator check; use a separate valid 40-card fixture to verify retained activation with ownership off and invalidation with ownership on, both without a companion. Add a companion-denial case under each setting. Restore snapshot in teardown.
- [ ] Fill main inventory, attempt withdraw and assert0 moved; free one slot, retry with the refreshed revision and assert exact quantity. Test main/offhand mixed deposit, unsupported custom-named card staying intact, and a BUSY transfer during an existing test duel/roll. Read each server assertion through harness `.server`/`.waitUntilServer` operations.
- [ ] Run `./gradlew.bat test`, `./gradlew.bat runClient -PldTest=collection_transfers`, and the collection persistence scenarios. Verify actual restart preserves the combined inventory/collection result, and two-player transfer isolation on a dedicated server. Record measured results; commit `test: verify physical collection transfers`.

## Completion

Players can transfer real physical cards through the editor, with conservation and active-selection behavior verified. Collection-backed duels and removal of legacy paths that bypass shared deck-use policy remain milestone 4. No public collection-enforced release before that cutover.

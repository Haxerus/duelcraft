# Core Ownership Setting and Companion Restrictions — M2 Follow-up

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Make ownership an optional core server rule and provide a small additional-restriction hook, preserving existing collections and the approved editor.

**Architecture:** A single server policy combines existing legality/missing-copy facts, a startup-captured SERVER setting, and a synchronous companion restriction. Collection operations evaluate immutable candidate state before commit. The client receives policy context and authoritative denial reports.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, existing collection protocol, JUnit 5 and LDLib2 harness.

**Spec:** [Amendment](../handoffs/2026-09-18-optional-ownership-amendment.md), [design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md).

**Status:** Implementation and execution checks complete on `codex/player-collections`; independent Task 3 and final follow-up review remain pending before M3. Tasks 1 and 2 were independently reviewed clean (commits `7706965`, `2783248`, `37a963d`). [Actual follow-up evidence](../reports/2026-09-19-deck-use-policy-follow-up.md) records 901 fresh full-suite tests, 60 retained-world checks, 61 dedicated checks per ownership mode, and final UI runs at both requested scales. Original M1/M2 history and evidence remain separate; M3–M6 remain unimplemented.

## Global constraints

- Core `requireCardOwnership` defaults to `false`; server restart is required to change the effective setting. No companion is needed to enforce ownership.
- Core legality is mandatory. Companion restrictions can deny, never override core gates or another denial. Hook failure never grants permission.
- Count deposited copies by exact passcode across Main + Extra + Side. No consumption, reservation, substitutions, collection credits, or storage migration.
- Save any well-formed draft regardless of playability. Preserve revision, privacy, and preparation/live-duel locks.
- Keep the approved dark editor, layout, filters, artwork, quiet unselected inspector, and recent playtest fixes. Only policy feedback changes.
- No progression implementation, generic addon framework, runtime configuration UI, or live addon/config switching.

Java paths below start at `src/main/java/com/haxerus/duelcraft/`; matching test paths start at `src/test/java/com/haxerus/duelcraft/`. Resources start at `src/main/resources/`. Proposed class names/signatures below are engineering defaults; adjust them together with callers if pinned APIs require it.

## Task 1: Core policy, configuration, and additional restriction hook

**Create:** `ServerConfig.java`, `server/collection/DeckUsePolicy.java`, `api/DeckUseCheckEvent.java`; tests `server/collection/DeckUsePolicyTest.java` and `ServerConfigTest.java`.

**Modify:** `Duelcraft.java`, `server/DuelManager.java`, `collection/DeckEligibility.java`, `server/collection/CollectionService.java`, `CollectionPayloadHandler.java`, and their existing unit tests. Update `server/collection/CollectionWire.java` and report constructors/fixtures atomically with the Report shape so this task builds.

**Interfaces:** add the Report fields from the contracts: `ownershipRequired` and nullable `restrictionReason`. Proposed policy seam:

```java
public record Context(UUID owner, DeckList list, Map<Integer, Long> counts, DuelRule rule) {}
@FunctionalInterface public interface Restriction {
    // null means no additional restriction; nonblank text explains a denial.
    String denial(Context context);
}
public DeckUsePolicy(boolean ownershipRequired, Restriction restriction);
public DeckEligibility.Report check(Context context, Map<Integer, CardCatalog.Facts> facts);
```

Context defensively copies counts; DeckList is already immutable. `CollectionService` receives facts and a policy dependency, and its mutation methods receive an authenticated owner UUID wherever active-list revalidation can occur. Pass proposed counts/list into policy, not a captured pre-mutation attachment. Keep MR5 for activation and accept the selected rule for M4 preparation.

The production Restriction adapter posts `DeckUseCheckEvent` to the server-side NeoForge event bus. The event exposes immutable Context and `deny(String reason)`; the first denial is retained and no allow/reset method exists. With no listener, the result is null. Unit tests inject a Restriction directly; an event test verifies listener denial cannot be reversed. Verify event/config lifecycle signatures in the pinned NeoForge source before wiring them.

- [x] Add failing policy tests with synthetic known legal Main1–40 and empty counts: setting false/null hook permits; true/null hook denies with missing1–40; false/denying hook denies; true/fully-owned/denying hook denies. An approving hook cannot rescue unknown IDs, tokens, placement/copy/size errors, or enabled ownership shortages.
- [x] Add report assertions that directly establish the distinction:

```java
var missing = Map.of(1, 1);
assertTrue(new DeckEligibility.Report(List.of(), missing, false, false, null).eligible());
assertFalse(new DeckEligibility.Report(List.of(), missing, false, true, null).eligible());
assertFalse(new DeckEligibility.Report(List.of(), Map.of(), false, false, "Era locked").eligible());
assertFalse(new DeckEligibility.Report(List.of(), Map.of(), true, false, null).eligible());
```

- [x] Run `./gradlew.bat test --tests '*DeckEligibilityTest' --tests '*DeckUsePolicyTest' --tests '*CollectionServiceTest' --tests '*CollectionHandlerTest' --tests '*ServerConfigTest'`; retain failing behavior evidence.
- [x] Register a dedicated `ModConfig.Type.SERVER` spec with boolean `requireCardOwnership=false`. Verify config is loaded before constructing the server policy. Capture it for the server lifecycle, reset on stop, and ignore live file reloads for effective policy. Test integrated-server stop/start does not retain another world's setting. Leave existing COMMON image/data settings alone.
- [x] Implement the policy and event adapter. Bound nonblank denial text to 256 characters with a generic fallback for an invalid reason; preserve bounded packet encoding. A listener exception is an evaluation failure, logged server-side and mapped to DATA_UNAVAILABLE at the collection boundary. It must not return an eligible report or partially mutate state.
- [x] Update activation and active-list revalidation. A normal denial after a valid save/count change clears activation and preserves the saved mutation; an evaluation failure rejects the entire operation. Keep malformed/busy/stale checks before policy callbacks. Do not consult the hook for inactive draft storage when no active list needs revalidation.
- [x] Convert original unconditional ownership tests to explicit setting=true cases; add false cases for activation and count reductions. Verify exact post-mutation counts reach the hook, Side-only shortages, no auto-activation after deposit, rename behavior for eligible lists, and unchanged counts/inventory on activation. Existing attachment codec/schema stays unchanged.
- [x] Run the targeted suites and report/payload round trips. Commit the focused server change after verification; record the actual hook/config API in the contracts if it differs from these defaults.

## Task 2: Authoritative policy context and editor feedback

**Modify:** `collection/CollectionReply.java`, `server/collection/CollectionWire.java`, `CollectionSnapshotStore.java`, `CollectionPayloadHandler.java`, `server/DuelNetworking.java`; `client/collection/ClientCollectionState.java`, `SavedDeckController.java`, `CollectionController.java`; `assets/duelcraft/lang/en_us.json`, `assets/duelcraft/ui/collection_screen.xml`. Extend their corresponding tests and collection UI scenarios.

**Interfaces:** Opened carries effective `boolean ownershipRequired`; evaluated Report carries that flag and the optional companion denial. Snapshot creation receives policy context from the current server, never a client-supplied value. Keep connection generation/revision safeguards. The changed wire format requires a bump from the currently registered protocol version; M4 must bump from that new value again.

- [x] Add failing round trips for optional/required ownership and absent/present companion denial; reject malformed/oversized reason encodings and retain the total 24 KiB envelope limit. Test policy state clears on disconnect and cannot be replaced by stale-connection replies.
- [x] Add client/UI checks: a legal unowned list can activate when the server permits; it shows collection shortages without a rejection dialog or blocking warning color. The same list with ownership required shows the authoritative shortage denial. A companion denial displays its reason under either setting. Existing invalid-deck details remain actionable.
- [x] Run `./gradlew.bat test --tests '*CollectionPayloadTest' --tests '*CollectionSnapshotStoreTest' --tests '*ClientCollectionStateTest' --tests '*SavedDeckControllerTest'`; confirm failures before implementing.
- [x] Wire the server-supplied setting through complete snapshots and evaluated replies. Display concise ownership-mode context in existing status/tooltip space. Do not imply that ownership optional means companion restrictions are absent. Activation requests remain server-authoritative; do not locally infer hook approval from counts.
- [x] Separate the editor's current missing-copy summary from legality warnings when ownership is optional. Preserve owned filters/counts and missing-card inspection. Update saved/active-cleared messaging to use the actual denial rather than assuming every clearance means missing copies.
- [x] Run targeted tests and real saved-deck/editor scenarios at 1280x720 GUI scale2 and 1920x1080 GUI scale3. Capture both modes and companion denial; retain evidence per run. Commit after verification.

## Task 3: Lifecycle, compatibility, and readiness for M3

**Modify:** existing `client/uitest/CollectionSavedDeckScenario.java`, `CollectionPersistenceScenario.java`, retained-world/privacy fixtures where needed; contracts/roadmap status. **Create:** `docs/superpowers/reports/2026-09-19-deck-use-policy-follow-up.md` only when recording actual execution evidence.

- [x] Run real UI/packet activation with empty counts and setting=false/no hook; assert active ID changes and counts do not. Repeat with setting=true/no hook, then enough deposited fixture counts. Exercise a test-only companion denial and throwing check under both settings; restore fixtures in teardown.
- [x] Restart with an active unowned list, changing false -> true or adding a restrictive test companion. Revalidate on login or before next use and reject/clear ineligible activation privately while preserving list/counts. Restart after removing a restriction and verify no auto-reactivation. Use a dev-only test adapter for companion behavior; do not ship test listeners enabled in production.
- [x] Verify prior-wire rejection through pinned NeoForge negotiation against the actual initialized channel registry, and real same-current-version dedicated server/client Open/Save/Activate under both settings. Controller ruling: this replaces a separate old-client coordinator; no old-binary disconnect screen was observed. Retain the two-player privacy check and lifecycle evidence rather than assuming pure tests prove routing.
- [x] Run `./gradlew.bat test`, relevant collection UI scenarios, and `git diff --check`. Preserve reports/screenshots and record exact code/config/hook state and commands; do not reuse original M2 test totals as evidence for this change.
- [x] Update the roadmap with actual results and commit the scoped follow-up. Continue to M3 only after independent Task 3 and final follow-up review pass. M4 still owns removal of legacy upload paths and verification of actual solo/multiplayer startup through shared policy; this follow-up alone does not make every existing duel route policy-enforced.

## Completion

Core setting and companion restriction semantics are implemented and verified for collection operations and editor feedback. Original collection storage/editor behavior remains intact. M3 tests transfers under both settings; M4 applies the same evaluator to every actual duel route; M5/M6 cover full player journeys. No companion progression content is required for any of these milestones.

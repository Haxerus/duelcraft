# Collection-backed Duel Preparation Implementation Plan — Milestone 4

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Make all normal duel entry paths use legal decks permitted by core ownership configuration and companion restrictions, with an unbroken edit/transfer lock from acceptance through duel completion.

**Architecture:** A testable preparation service owns invitations, RPS, and first-turn selection. DuelManager owns native live sessions and adapts the preparation service to online players and collection data. Commands and GUI payloads call the same service; saved lists become immutable prepared snapshots before engine startup.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, current ygopro-core bridge, existing typed networking/JUnit.

**Spec:** [Design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: completed [milestone 3](2026-09-17-card-transfers-milestone-3.md). Read `docs/engine-wiki/README.md`, `rules-and-coordinates.md`, and `duelcraft-jni.md` before changing host startup contracts; inspect current source because engine updates are in progress.

## Global constraints

- Activation and duel preparation require supported legality, enough deposited copies when `requireCardOwnership=true`, and no denial from an installed companion restriction hook.
- Prevent collection transfers and deck changes during a duel, in both the interface and server handlers.
- Keep `core.Deck` as the simulation input.
- Preserve challenger seed / accepter seed+1 shuffle identity regardless of who wins first turn.
- No new match mode, sideboarding, banlist subsystem, native protocol, or bypass of shared player-deck policy. Ownership-optional play is the normal standalone default.

Java paths start at `src/main/java/com/haxerus/duelcraft/`; tests at `src/test/java/com/haxerus/duelcraft/`.

## Task 1: YDK lists retain Side and import without ownership

**Create:** `collection/DeckListLoader.java`. **Modify:** `core/DeckLoader.java`, `client/DuelClientCommand.java`; collection saved-list picker and XML/lang. **Tests:** `collection/DeckListLoaderTest.java`; extend `core/DeckLoaderTest.java`.

**Interfaces:** `DeckListLoader.parseYdk(String)` and `.loadFromFile(Path)` return `DeckList`. Existing `DeckLoader.parseYdk` and `.loadFromFile` delegate and convert with `.toDuelDeck()` while retaining `DeckLoader.DeckParseException` and line-number behavior. Unknown positive IDs and invalid gameplay sizes may import within total512 bound.

- [ ] Add a failing preservation test:

```java
var list = DeckListLoader.parseYdk("#main\n1\n#extra\n2\n!side\n3\n");
assertEquals(List.of(3), list.side());
assertEquals(new Deck(List.of(1), List.of(2)), DeckLoader.parseYdk("#main\n1\n#extra\n2\n!side\n3\n"));
```

- [ ] Run `./gradlew.bat test --tests '*DeckListLoaderTest' --tests '*DeckLoaderTest'`.
- [ ] Move parsing into DeckListLoader without changing BOM/comments/error behavior; retain Side instead of dropping it. List import resolves only `.ydk` files listed within the client deck directory, rejects traversal/outside paths, and creates a new SavedDeck UUID without touching ownership. Add UI Import selection from that directory; no server file picker or upload of local paths.
- [ ] Wire UI Import -> Save draft; `/duel deck set <name>` -> Save imported draft -> Activate only after Save acknowledgement with the returned revision. Missing cards block activation only when core ownership is required. Any blocking legality/ownership/companion denial leaves a saved inactive list and a clear reason; default legal imports can activate with an empty collection. Keep local list completion/quoted filenames. Delay removal of the old packet until task 4 so this task can build, but route the updated client through new commands immediately.
- [ ] Test same-named imports preserve independent IDs, malformed input shows the line, 513-card input is rejected as an import limit, and source-file deletion after save cannot change the list. Commit `feat: import complete YDK deck lists` after tests pass.

## Task 2: Pure preparation state machine

**Create:** `duel/DuelSettings.java`, `duel/PreparationView.java`, `duel/PreparationResult.java`, `server/DuelPreparationService.java`. **Tests:** `server/DuelPreparationServiceTest.java`, `server/PreparationTestHost.java`.

**Interfaces:** `DuelSettings(DuelRule rule,long seed,PlayerOptions options)` validates the contract's ranges. Nested in service:

```java
public record Selection(@Nullable SavedDeck deck, DeckEligibility.Report eligibility) {}
public record PreparedPlayer(UUID id, SavedDeck deck, long shuffleSeed) {}
public record PreparedDuel(UUID id, PreparedPlayer challenger, PreparedPlayer accepter, DuelSettings settings) {}
// Common enum in duel/PreparationResult.java; all other types below are nested in the service.
public enum PreparationResult { OK, OFFLINE, BUSY, INELIGIBLE, STALE, INVALID, START_FAILED }
public record Result(PreparationResult code) {}
public interface Host {
    boolean online(UUID id);
    String name(UUID id);
    boolean isDueling(UUID id);
    Selection selection(UUID id, DuelRule rule);
    boolean start(PreparedDuel prepared, int firstSeat);
    void changed(UUID id, PreparationResult result);
}
public DuelPreparationService(Host host, LongSupplier nextSeed);
public Result invite(UUID sender, UUID target, DuelRule rule, @Nullable Long seed, PlayerOptions options, long now);
public Result accept(UUID actor, UUID invitationId, long now);
public Result decline(UUID actor, UUID invitationId, long now);
public Result cancel(UUID actor, UUID flowId, long now);
public Result hand(UUID actor, UUID flowId, UUID roundId, FirstTurnLobby.Hand hand, long now);
public Result first(UUID actor, UUID flowId, boolean goFirst, long now);
public void expire(long now);
public void logout(UUID actor);
public boolean isPreparing(UUID actor);
public PreparationView view(UUID viewer, long now);
```

`PreparationView` contains `long revision`, Mode (`IDLE`, `INVITED`, `RPS`, `FIRST_CHOICE`, `STARTING`, `DUEL`), nullable `UUID flowId/roundId/opponentId`, opponent name, `boolean outgoing/ownHandSubmitted/canChooseFirst`, remaining milliseconds, and nullable DuelSettings. Send no deck contents, opponent hand, or opponent eligibility details. Increment view revision on transitions; use recipient-specific booleans. `PreparedPlayer` snapshots deep-copy SavedDeck contents. The test Host stores online UUIDs/selections, records starts, has toggles for start failure, and checks `isPreparing` from within its start callback.

- [ ] Write tests demonstrating invite remains editable and acceptance locks both:

```java
var host = new PreparationTestHost(); // constructor installs eligible players alice and bob
var service = new DuelPreparationService(host, () -> 42L);
host.service = service;
assertEquals(PreparationResult.OK,
        service.invite(host.alice, host.bob, DuelRule.MR5, null, PlayerOptions.standard(), 0).code());
assertFalse(service.isPreparing(host.alice));
var id = service.view(host.bob, 1).flowId();
assertEquals(PreparationResult.OK, service.accept(host.bob, id, 1).code());
assertTrue(service.isPreparing(host.alice));
assertTrue(service.isPreparing(host.bob));
assertEquals(PreparationResult.STALE, service.accept(host.bob, id, 2).code());
```

`PreparationTestHost` uses the synthetic facts/deck fixture from milestone 2; new instances create UUIDs and independent selections. Provide a noneligible/empty target by replacing its Selection for the target-not-ready case.
- [ ] Run `./gradlew.bat test --tests '*DuelPreparationServiceTest'` for intended failures.
- [ ] Store one invitation per participant; reject existing/busy/self/offline instead of replacing an invite. Sender must be eligible, target need only be online/not busy until acceptance. Validate options and server-generate omitted seed. At accept, check correct target, invite ID/expiry, both current selections and online/busy state; capture snapshots; remove outstanding invitation; install shared preparation for both before notifying clients.
- [ ] Implement RPS using `FirstTurnLobby.resolve`. Store each submitted hand privately, reject duplicate/old-round actions, generate a fresh round ID and clear choices on ties. Winner alone chooses first. A successful action updates recipient views; timeout resets only on the documented new step, not on arbitrary reads/invalid packets.
- [ ] Set STARTING before Host.start; both players remain preparing throughout the callback. Seat0/1 refer to challenger/accepter for firstSeat selection; shuffle seeds stay42/43 even when accepter goes first. On success remove preparation only after the synchronous Host.start returns with session ownership installed or an already-completed duel result. Immediate engine completion is a success, not a startup failure. On failure release both and send a terminal error. Cancel is allowed by a participant before STARTING, decline only by target, and stale requests cannot cancel a newer flow.
- [ ] Cover tie replay, loser attempting first choice, actor not participant, expiry at60s, logout in each state, duplicate accept, withdrawal before accept invalidating sender only when ownership is required or a companion denies the resulting state, edited deck before accept, online loss before start, and injected start failure. Host.start test asserts both preparing during transition and snapshot immutability. Commit `feat: model authoritative duel preparation`.

## Task 3: Integrate preparation with live sessions and collections

**Modify:** `server/DuelManager.java`, `DuelCommand.java`, `ServerPayloadHandler.java`; collection mutation busy checks use the unified manager. **Tests:** `server/DuelStartPolicyTest.java`; retain `core/DeckShuffleTest.java`, `duel/FirstTurnLobbyTest.java`.

**Interfaces:** `DuelManager.preparation()` exposes the current server's service. `isBusy(ServerPlayer)` returns live-duel membership OR `preparation.isPreparing(uuid)` OR membership in a private `startingPlayers` set used during solo startup. `getPlayerCurrentDeck`/`resolveDeck` resolve the attached active SavedDeck and validate it; no session-only selection map. CollectionService remains the only mutation policy.

- [ ] Add an adapter test that every start is supplied with two current eligible collection selections, and a stale raw Deck argument alone cannot reach native setup. Use an injected engine-start boundary for tests so policy checks do not require JNI.
- [ ] Run `./gradlew.bat test --tests '*DuelStartPolicyTest' --tests '*DeckShuffleTest' --tests '*FirstTurnLobbyTest'`.
- [ ] Move invitations/roll state out of DuelManager into the service, removing old duplicate maps and timeout ownership after callers migrate. Host.selection reads that player's attachment and invokes the shared deck-use policy with authenticated identity, current counts/list, and the chosen rule. Return detailed errors only to the owner; the other participant sees "Opponent's deck is not ready".
- [ ] Restrict the raw start implementation to private/package-internal use by the verified preparation adapter. Immediately before engine creation recheck online state, active-list identity/contents, configured ownership and current companion eligibility against the prepared snapshot. Ordinary operations cannot change them while busy; this also catches administrative/server changes. Preserve both copy-specific seeds and seat mapping when calling existing session setup.
- [ ] Implement startup failure cleanup: close a newly allocated session exactly once, remove any newly installed duel/player/seat/AI mappings, and notify both. Never remove preparation before native/session startup succeeds. Preserve DuelStartPayload before setupDuel: the current setup method emits extra-deck refresh messages through its listener, so moving the start packet after it would deliver updates before the client has duel state. On failure, increment preparation view revision and call Host.changed for both participants with START_FAILED. Client preparation handling closes the newly opened duel screen, restores idle management routing, and shows a start error rather than a fake victory. The solo failure adapter sends the same terminal status. Add a test for packet order: Start -> setup refreshes -> either normal processing or explicit start failure.
- [ ] Solo startup uses the same eligibility/busy check for the human and adds their UUID to startingPlayers before native creation, removing it in finally after ownership transfers to a live session or failure cleanup completes. Server AI content needs no player ownership. `/duel test` must apply the same core setting and companion restrictions to the human; default legal solo play needs no owned cards. Tick expiry, logout, cancel, and server stop clean up preparation/start/live state; successful ending does not erase persistent active selection.
- [ ] Revalidate active selection on login against current catalog, core setting, and installed companion restrictions, clearing invalid selection with a private explanation. Repeat at use/start; persisted activation is not proof of current permission. Test restart with ownership toggled and with a companion added/removed; do not auto-reactivate previously cleared lists. Keep saved unknown IDs intact. Run targeted and existing core tests with native prerequisites, then commit `feat: start duels through shared deck-use policy`.

## Task 4: Typed lobby actions and command cutover

**Create:** `duel/PreparationCommand.java`, `server/PreparationRequestPayload.java`, `PreparationStatePayload.java`, `PreparationPayloadHandler.java`; `client/interaction/ClientPreparationState.java`. **Modify:** `DuelNetworking.java`, `DuelCommand.java`, `DuelClientCommand.java`, `ServerPayloadHandler.java`, `ClientPayloadHandler.java`. **Remove after callers migrate:** `server/DuelDeckPayload.java`, old setPlayerCurrentDeck upload setter/map. **Tests:** `server/PreparationPayloadTest.java`, `server/DuelCommandPolicyTest.java`; replace `server/DuelDeckUploadTest.java` with import/activation policy tests, adapt `client/uitest/DuelDeckUploadScenario.java` rather than leaving a failing old fixture.

**Interfaces:** request `(UUID requestId, PreparationCommand command)`; reply `(@Nullable UUID requestId, PreparationResult result, PreparationView view)`. Use the common PreparationResult enum from task 2; wire/client types never depend on service classes. Unsolicited state changes use null requestId encoded as optional. Commands are sealed records View, Invite(target,rule,nullable seed,options), Accept(invitationId), Decline(invitationId), Cancel(flowId), Hand(flowId,roundId,hand), First(flowId,goFirst). Encode explicit tags/ranges and <=24KiB; opponent name max128 characters and rule IDs from the existing enum. Route by authenticated sender.

- [ ] Test packet round trips, illegal rule/options/tags, outsiders' actions, and recipient privacy. Serialize a RPS view after only Alice chooses; Bob's decoded view must not expose Alice's choice. Packet tests must inspect fields, not just assert class type.
- [ ] Add command-path tests for set/import, get, clear, challenge, accept, hand, first, forfeit, and solo. Busy edits must reject identically through commands and packets. Run command paths with ownership off/on and a test companion denial under both settings. A local YDK file grants no copies and cannot satisfy enabled ownership or bypass the hook.
- [ ] Run `./gradlew.bat test --tests '*PreparationPayloadTest' --tests '*DuelCommandPolicyTest' --tests '*DuelDeckUploadTest'` before removing/renaming the old test, then use the new import-policy test name afterward.
- [ ] Register lobby request/state handlers on MAIN. Host.changed sends its result and per-owner view through online-player lookup; normal transitions use OK, startup cleanup uses START_FAILED. Client applies only nonolder view revisions and resets on disconnect, but resolves a matching request's future even if its view revision equals the already-applied revision. Send terminal IDLE/error state for cancellation/start failure so controls unblock. Update text commands to delegate using current flow/round IDs; the GUI includes explicit IDs. Keep chat RPS fallback but route the same state methods.
- [ ] Remove old upload registration/handler/type and playerCurrentDeck; migrate the existing upload scenario to real list save/activation, with fixture counts/catalog supplied for valid known cards. Bump the then-current registrar version after the M2 policy follow-up; do not hardcode version3 ->4. Older clients must fail compatibility negotiation, not bypass shared policy. Search callers before removing symbols and update their tests/docs.
- [ ] Implement `/duel invite decline` and `/duel invite cancel` server commands (alongside existing challenge/accept) so this milestone is usable before the lobby screen. Keep forfeit semantics for live duels and preparation cancellation as documented.
- [ ] Run policy suites and integrated-server import scenario, then a dedicated two-player command duel covering RPS/first-turn selection. Commit `feat: unify duel commands and lobby protocol`.

### Required policy matrix for task 3/4 verification

Run human solo and multiplayer with `requireCardOwnership=false/true`, each without a hook and with a test hook. Empty legal lists activate/start only when core permits and the hook does not deny. An approving hook cannot override illegal decks, enabled ownership, authentication, or locks. A throwing hook cannot start a duel; return an actionable generic failure and release startup/preparation state.

After invitation, change test companion eligibility without editing cards and assert acceptance rechecks it. After acceptance, change companion eligibility before engine creation and assert the final recheck rejects startup and releases locks. This uses test progression state, not runtime addon installation. Ownership checks remain deposited-only, exact-passcode, and include Side. Capture an actual empty-collection default solo and two-player start in addition to pure tests.

## Task 5: Race and failure regression scenarios

**Create:** `client/uitest/CollectionDuelPolicyScenario.java` (DEV_ONLY name `collection_duel_policy`); extend `DuelPreparationServiceTest` and `DuelStartPolicyTest`.

- [ ] Add deterministic request-order tests: invite -> withdraw -> accept fails when required copies become missing with ownership enabled, but succeeds for an otherwise legal list with enforcement disabled and no companion denial; accept -> withdraw fails; accept -> Save fails; accept -> stale upload cannot be dispatched; two accepts serialize to one start; cancel -> delayed old Hand fails; old timeout does not erase a new flow. Use explicit timestamps, not sleeping tests.
- [ ] In the live scenario, snapshot/restore inventory and collection, activate an eligible fixture, enter preparation/solo as appropriate, send otherwise-valid transfer/Save requests and assert BUSY with unchanged revision/counts. End/cancel and verify the same operations now work. Preserve the actual native start path in one test run; fake-host tests alone are insufficient.
- [ ] Run `./gradlew.bat test` and `./gradlew.bat runClient -PldTest=collection_duel_policy`. Run a dedicated-server two-player duel and injected startup failure test, and capture behavior before/after cancellation/disconnect. Record whether native/session cleanup was exercised rather than claiming it from pure tests.
- [ ] Update `docs/multiplayer-decks.md` and the host-policy sections of `docs/engine-wiki/duelcraft-jni.md` with the actual protocol version, core ownership setting/default/restart behavior, companion restrictions, import semantics, and selection persistence. Preserve unrelated wiki edits. Commit `test: close collection duel preparation races`.

## Completion

All normal player duel paths enforce supported legality, configured core ownership, companion restrictions, preparation/live locks, and shared command/packet policy. A dedicated two-player command flow works without the milestone 5 screens. Public UX remains the next milestone; the engine's in-duel rendering/protocol stays unchanged.

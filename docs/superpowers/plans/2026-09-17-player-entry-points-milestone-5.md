# Home, Binder, Mat and Lobby Implementation Plan — Milestone 5

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Delegate only when authorized. Steps use checkbox syntax for tracking.

**Goal:** Make the collection and duel-preparation flows usable through a hotkey home screen, portable binder, and placed duel mat.

**Architecture:** One route coordinator asks the server which screen is allowed by current player state. Home and physical interactions use the same collection/preparation services and payloads. The private two-player lobby renders the existing preparation view and issues typed actions, without duplicating rules.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, current collection/preparation protocols.

**Spec:** [Design](../specs/2026-09-15-player-interaction-design.md), [contracts](../specs/2026-09-17-player-interaction-contracts.md). Dependency: completed [milestone 4](2026-09-17-duel-preparation-milestone-4.md).

## Global constraints

- Use the term **collection**.
- Support the hotkey, binder, and mat through the same server policies and player data.
- Players need not carry either item. All routes use the core ownership setting (default false) and optional companion restrictions; clients cannot override them.
- Keep the existing duel UI/input behavior. The new home/lobby screens are management screens; closing them is not surrender.
- No public matchmaking, spectators, match mode, or collection inventory attached to a block. Crafting/acquisition recipes remain separate content work.

Java paths start at `src/main/java/com/haxerus/duelcraft/`, tests at `src/test/java/com/haxerus/duelcraft/`, resources at `src/main/resources/`.

## Task 1: State-aware routing and remappable hotkey

**Create:** `interaction/ScreenRoute.java`, `server/InteractionRequestPayload.java`, `InteractionStatePayload.java`, `InteractionPayloadHandler.java`, `client/interaction/DuelcraftScreens.java`, `DuelcraftKeys.java`. **Modify:** `DuelcraftClient.java`, `server/DuelNetworking.java`, `client/ClientPayloadHandler.java`. **Tests:** `interaction/ScreenRouteTest.java`, `server/InteractionPayloadTest.java`, `client/interaction/DuelcraftScreensTest.java`.

**Interfaces:** `ScreenRoute` enum HOME/COLLECTION/LOBBY/DUEL. Request `(UUID requestId, ScreenRoute requested)` accepts only the first three. Reply `(UUID requestId, ScreenRoute resolved, PreparationView preparation)` comes only from the server. `InteractionPayloadHandler.resolve(ScreenRoute requested, boolean live, boolean preparing)` is a pure function; `.open(ServerPlayer,ScreenRoute,UUID requestId)` sends the response. `DuelcraftScreens.request(ScreenRoute)` sends a packet; `.apply(InteractionStatePayload)` applies only the current request; `.disconnect()` clears state and pending drafts. The coordinator owns parent-screen navigation and one suspended editor session for this connection.

- [ ] Write routing tests:

```java
assertEquals(ScreenRoute.DUEL, InteractionPayloadHandler.resolve(ScreenRoute.COLLECTION, true, false));
assertEquals(ScreenRoute.LOBBY, InteractionPayloadHandler.resolve(ScreenRoute.COLLECTION, false, true));
assertEquals(ScreenRoute.COLLECTION, InteractionPayloadHandler.resolve(ScreenRoute.COLLECTION, false, false));
```

Add a delayed HOME reply after a newer COLLECTION request and assert no navigation; changing connection generation must also invalidate it. Server push of new preparation/duel state always takes precedence over older management replies.
- [ ] Run `./gradlew.bat test --tests '*ScreenRouteTest' --tests '*InteractionPayloadTest' --tests '*DuelcraftScreensTest'`.
- [ ] Resolve server state first: live -> DUEL; RPS/FIRST_CHOICE/STARTING -> LOBBY; otherwise requested management route. Do not treat a pending invitation as a lock. If management becomes busy before its data request is handled, the existing collection gate still rejects it. A route reply never grants authority to mutate.
- [ ] Register a `KeyMapping` through `RegisterKeyMappingsEvent`, category `key.categories.duelcraft`, default GLFW_KEY_J. Handle `ClientTickEvent.Post` through NeoForge's client event bus:

```java
while (HOME.consumeClick()) {
    if (mc.player != null && mc.level != null && mc.screen == null) {
        DuelcraftScreens.request(ScreenRoute.HOME);
    }
}
```

Consumption while another screen is open must not queue a later surprise navigation. Expose rebinding through standard Controls. No duplicate polling on both tick phases.
- [ ] On live-route response use `LDLibDuelScreen.reopen()`; if client duel state is absent, show a recoverable error and retain the server lock instead of opening collection. Do not add engine resync in this task. On preparing state suspend a dirty editor locally and open the lobby; after cancel/end offer Resume draft. Never auto-save or discard that suspended draft. On ClientPayloadHandler.handleStart and a forced preparation transition, invalidate all pending management route request IDs before opening the required screen. Disconnect clears the local draft and pending routes as documented.
- [ ] Test key while chat/textfield open, repeat presses, game menu, no world, busy-state races, and dirty close/nav prompts. Commit `feat: route Duelcraft screens through server state`.

## Task 2: Home screen and connected collection navigation

**Create:** `client/interaction/HomeScreen.java`, `assets/duelcraft/ui/home_screen.xml`. **Modify:** `DuelcraftScreens`, collection screen/controller, `assets/duelcraft/lang/en_us.json`. **Tests:** `client/uitest/DuelcraftHomeScenario.java` (DEV_ONLY name `duelcraft_home`, group `duelcraft`).

**Interfaces:** `HomeScreen.create(ClientCollectionState collection, ClientPreparationState preparation)` returns the LDLib host; callbacks call `DuelcraftScreens.request`. Home reads summaries/status rather than cloning editor data. It can render loading/error state while opening a collection summary snapshot.

- [ ] Add a failing real-screen scenario for two primary buttons `#home-collection`, `#home-duels`, active-deck status `#home-active-deck`, and `#home-close`; click both routes and verify destination screen without needing items. Use injected state fixtures for layout and real packets in the network scenario.
- [ ] Build the home using the editor's visual vocabulary: Minecraft-style framed panels, clear Collection & Decks and Duel buttons, current active deck or no-selection status, received invitation notification, and optional Resume draft when suspended. Do not add currencies, quests, shops, or empty placeholder destinations.
- [ ] Use a compact centered content panel within a uniformly scaled 1280x720 management canvas. Keep main actions visible in smaller viewports and navigation keyboard-accessible. Preserve editor's fixed layout rather than embedding it inside the home.
- [ ] Home -> Collection opens the authoritative saved-list picker/editor; Collection -> Home uses Save/Discard/Cancel if dirty. Bind New/Rename/Duplicate/Delete/Activate and UI Import from milestone 4; do not hide these behind commands. Initial empty collection/deck list has New deck and Import as usable actions.
- [ ] Run `./gradlew.bat runClient -PldTest=duelcraft_home -PldTestWindow=1280x720` and a smaller-window capture. Verify no-item navigation, pending-save close behavior, loading/error retries, and return navigation; commit `feat: add Duelcraft home screen`.

## Task 3: Private invitation and preparation UI

**Create:** `client/interaction/DuelLobbyScreen.java`, `DuelLobbyController.java`, `assets/duelcraft/ui/duel_lobby.xml`, `client/uitest/DuelLobbyScenario.java`. **Modify:** ClientPreparationState, DuelcraftScreens, lang. **Tests:** `client/interaction/DuelLobbyControllerTest.java`, `server/PreparationPayloadTest.java` if representation changes.

**Interfaces:** `DuelLobbyScreen.create(ClientPreparationState)` returns the management host. `ClientPreparationState.request(PreparationCommand)` returns a `CompletionStage<PreparationStatePayload>`; state notifications update subscribed controller during screen lifetime, then unsubscribe on removal. Keep revisions/flow IDs/round IDs from milestone 4. Do not dispatch command strings from buttons.

- [ ] Test this mapping through a recording packet sink:

```java
// On a view with flowId and roundId, clicking Rock must preserve both IDs.
var expected = new PreparationCommand.Hand(flowId, roundId, FirstTurnLobby.Hand.ROCK);
controller.onRock();
assertEquals(expected, sent.getFirst());
```

Create the controller with an explicit `Consumer<PreparationCommand>` sink and current-view supplier so the unit test has no network. Test Accept/Decline/Cancel and First buttons likewise, plus double-click disabled while pending.
- [ ] Run targeted tests before implementation. Build these state-specific surfaces with fixed header/footer, scrollable content, and no whole-screen height growth:

| State | Controls and visible information |
| --- | --- |
| Idle | Online target picker from vanilla connection player list; own active-deck status; rule preset; LP/hand/draw; optional seed; Invite |
| Outgoing invite | Opponent, fixed settings, remaining time, Cancel; allow navigation to collection until accepted |
| Incoming invite | Sender/settings, own deck readiness, Select deck route, Accept, Decline |
| RPS | Rock/Paper/Scissors; disable after own submission; say waiting without revealing opponent hand; Cancel |
| First choice | Winner: Go first/Go second; loser: waiting; Cancel |
| Starting | Progress/status only; no editing or duplicate start controls |
| Live | Transition to existing duel screen |

Defaults/bounds come from DuelSettings and contract. Invalid text input disables Invite and identifies the field. UI selection accepts only online nonself UUIDs; server validates again. Pending-invite settings remain immutable; to change them cancel and resend.
- [ ] Preserve the user's unsaved invitation form when opening collection for deck selection, but re-read current authoritative preparation view on return. Display server-provided ownership mode and actual own activation/companion denials; present missing-copy counts neutrally when ownership is optional; show only generic readiness for the opponent. Countdown derives from server-provided remaining time and local receipt time; reaching zero requests a view instead of locally manufacturing cancellation.
- [ ] Closing the lobby returns to world/home without cancelling; pending invites expire normally. Explicit Cancel cancels the displayed flow ID only. Notifications expose an Open invitation action; they do not forcibly replace a dirty editor for an unaccepted invite. Accepted preparation must suspend management and route both players.
- [ ] Add fixture scenarios for each state, long names, invalid options, expired invite, declined invite, offline target, stale response, RPS tie and winner/loser. Assert no opponent choice/list data appears. Run `./gradlew.bat test --tests '*DuelLobbyControllerTest'` and `./gradlew.bat runClient -PldTest=duel_lobby`; capture/screens inspect before committing `feat: add private duel lobby interface`.

## Task 4: Binder and placed mat entry points

**Create:** `item/CardBinderItem.java`, `block/DuelMatBlock.java`, resource models/blockstates below. **Modify:** `Duelcraft.java` registration/tab and lang. **Tests:** `client/uitest/DuelcraftEntryScenario.java` (DEV_ONLY `duelcraft_entry_points`), registry/route tests as needed.

**Interfaces:** binder server-side Item.use delegates to `InteractionPayloadHandler.open(player,COLLECTION,requestId)`. Mat server-side block interaction delegates to LOBBY. Item/block opens carry a generated request ID plus an explicit `boolean unsolicited` on InteractionStatePayload, added to the codec/tests: the client may accept unsolicited physical entry only while in world/no screen and still applies current authoritative duel/preparation state. This avoids requiring a matching client route request for server-triggered use.

- [ ] Add a failing scenario that uses the binder in either hand and a mat with empty/nonempty hand, then verifies the correct route for the interacting player. A second player must see their own collection/status when using the same mat.
- [ ] Register `duelcraft:card_binder` stack size1; `duelcraft:duel_mat` block and BlockItem. Use a thin full-block footprint (one-sixteenth-block height), carpet-like outline, no collision, no block entity, no ownership storage, and no range tether after opening. Verify the pinned block interaction override handles ordinary nonempty-hand use as intended; item-specific interactions must not produce two open replies.
- [ ] Provide initial resource definitions using existing vanilla/mod textures:

```text
assets/duelcraft/models/item/card_binder.json      item/generated; layer0 minecraft:item/book
assets/duelcraft/models/block/duel_mat.json        element [0,0,0]..[16,1,16]; wool border/tabletop palette
assets/duelcraft/models/item/duel_mat.json         parent duelcraft:block/duel_mat
assets/duelcraft/blockstates/duel_mat.json         default variant -> duelcraft:block/duel_mat
data/duelcraft/loot_table/blocks/duel_mat.json     self-drop with survives_explosion condition
```

Use vanilla wool textures (e.g. green/brown) for the initial mat model; no new image-generation dependency. Register both in the mod creative tab with translatable names. No recipe gate is required for the hotkey-first interaction release; physical acquisition/progression remains separately scoped.
- [ ] Test both physical routes while a live duel/preparation is authoritative but the client has no screen; route back to the appropriate existing flow rather than opening management. Verify breaking the mat does not destroy player collection/lobby data and item loss never blocks hotkey access.
- [ ] Run `./gradlew.bat runClient -PldTest=duelcraft_entry_points`, check placed/item rendering and missing-model logs, then a dedicated server launch to catch client-only references. Commit `feat: add binder and duel mat entry points`.

## Task 5: End-to-end player journey

**Create:** `client/uitest/DuelcraftJourneyScenario.java` (DEV_ONLY `duelcraft_journey`); extend existing network/UI scenarios for navigation state. **Modify:** only behavior surfaced by failing scenarios; maintain model/packet contracts.

- [ ] Add deterministic integrated-server coverage for hotkey -> empty collection -> create/save legal draft -> activate -> lobby form -> collection back navigation with default ownership off and no companion, without any grant or deposit. Repeat with ownership on/no companion: save succeeds, activation fails with shortages until deposit. Verify ordinary interaction requires no chat commands and selection/count indicators survive screen changes.
- [ ] Run a two-client dedicated-server journey with ownership off/no companion, starting from empty collections without granting cards. Repeat with core ownership on/no companion: grant physical fixture cards as operator, deposit and activate independently, invite via home, accept via mat UI, finish RPS/first choice, complete or concede a duel, return home, withdraw and observe active invalidation. Repeat collection entry with binder. Verify an independent test companion restriction denies use via home/binder/mat under either setting, with the actual private denial reason. Use the same installed JAR on server/clients; no integrated-server fixture access in this manual check.
- [ ] Test accepted preparation during dirty editing, save reply arriving during forced route, late management reply after duel start, closing/reopening lobby, target disconnect, and post-result home entry. No unsaved draft loss within the connection and no stale response replacing the duel screen.
- [ ] Run `./gradlew.bat test`, `./gradlew.bat runClient -PldTest=group:duelcraft`, and GUI scale/window checks from milestone 1 for all new screens. Record actual results and commit `test: verify Duelcraft player entry journeys`.

## Completion

Both players can use the integrated feature without carrying items or issuing commands. Binder and mat reach the same data/policies. The remaining milestone is release validation/documentation, including any fixes found by actual playtesting; do not claim that review complete from fixture-only runs.

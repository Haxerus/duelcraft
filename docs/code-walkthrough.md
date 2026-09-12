# Duelcraft Code Walkthrough

A bottom-up tour of the codebase, written 2026-09-07 for the project owner to read system by system. Stops 1 to 3 (native bridge, message layer, DuelSession) were covered live in a working session; a recap of what they established opens this document. Stops 4 to 8 are written out in full.

Line numbers refer to the tree at commit `2be7e70` on branch `message-layer-cleanup`, after the first cleanup commit landed. Later commits on that branch move a few lines in `DuelSession`, `MessageParser`, `FieldQuery`, `DuelCommand`, and the docs. Search for the method name when a line reference misses.

Each stop has the same shape: the files and what each one owns, the flows to trace with the file open, the design points worth understanding, the cleanup candidates found, and questions you can answer yourself from the code to check your understanding.

## The layers, top to bottom

```
Minecraft client                                   Minecraft server
LDLibDuelScreen + FieldRenderer/PromptController   DuelCommand (/duel ...)
ClientDuelState (dirty flags)                      DuelManager (singleton, sessions, decks)
ClientPayloadHandler                               ServerDuelHandler / SoloDuelHandler (DuelEventListener)
   ^  DuelStart/Message/EndPayload                 DuelSession (process loop, field queries)
   |  DuelResponsePayload  --------------------->  DuelEngine -> OcgCore natives (JNI)
                                                   duelcraft_jni.dll: CardDatabase, ScriptProvider (C++)
                                                   ocgcore.dll (ygopro-core, submodule)
```

Card display data (names, art, strings) never touches the server. The client downloads its own `cards.cdb`, `strings.conf`, and images into `<gameDir>/duelcraft/cache/` and looks everything up locally (Stop 7).

## Recap of Stops 1 to 3

**Stop 1, native bridge and core.** `OcgCore` declares the native methods; its static initializer runs `NativeLoader.load()`, which loads `ocgcore.dll` before `duelcraft_jni.dll` because the bridge links against the engine. The card reader, script reader, and log handler callbacks live in C++, so the Java server never opens SQLite or reads Lua. `enableUnsafeLibraries` is hardcoded to 0 in `duel_engine.cpp`, which keeps card scripts away from `os` and `io`. `OCG_StartDuel` does not shuffle: `Deck.shuffled(seed)` shuffles Java-side with `java.util.Random`, and `DuelSession.setupDuel` adds the main deck in reverse list order so list index 0 ends on top and is drawn first. `SeedExpander` turns the one duel seed into the four longs the engine wants for its xoshiro state, so the engine seed only drives in-duel randomness (coin flips, engine shuffles), never the opening hands. Decisions taken at this stop: shuffle the second deck with `seed + 1` (landed in `2be7e70`), delete `Deck.standard()` so a player with no deck set cannot start a duel (landed), `DuelEngine.close()` no longer throws (landed), the engine log handler is unfinished on both sides (Stop 7 confirms), and CI is broken (Stop 8 explains why).

**Stop 2, message layer.** The engine hands back a buffer of frames, each `[u32 length][u8 type][body]`. `MessageParser.parse` walks it and turns every frame into one of about 70 sealed `DuelMessage` records; any frame it cannot read becomes `Raw(type, body)`. A try/catch per frame plus a drift check against the declared length keep one bad layout from corrupting the rest of the buffer, and since this session the drift log is a warning, because drift means a misread layout. `DuelMessageCodec` is our own wire format for sending those records to the client; its encode switch is exhaustive over the sealed interface, so adding a record without a codec arm fails compilation, while decode has a `Raw` default. `QueryParser` parsed edopro's query-buffer framing for `MSG_UPDATE_DATA` and `MSG_UPDATE_CARD`, two messages the engine never emits; it was deleted on this branch. The live query path is `FieldQuery`, which reads the native per-slot `[u16 size][u32 flag][data]` blocks (Stop 3).

**Stop 3, DuelSession and responses.** `process()` calls `nDuelProcess`, parses the frames, and hands each record to the `DuelEventListener`, which returns 0 (keep going), 1 (stop, a player must respond), or 2 (duel over). It auto-passes an empty, non-forced `SelectChain` with `ResponseBuilder.selectChain(-1)` so the human is not asked "activate nothing?" at every timing. After each processing pass `sendFieldStats` runs one `nDuelQuery` per occupied slot, about sixty calls per pause, and injects synthetic `UpdateData` records into the same stream, so the client learns ATK, DEF, level, link data, and the `isPublic` flag the server's sanitizer relies on. `ResponseBuilder` encodes every response type as little-endian bytes. `ResponseValidator` mirrors it with 42 tests and zero production callers; it was meant to catch bad responses client-side before the engine answers `MSG_RETRY`, and whether to wire it into `PromptController` or delete it is decided at Stop 6. The public query wrappers `queryCount`, `query`, `queryLocation`, `queryField` have no production callers either. Two format bugs surfaced here and are being fixed on the branch: `QUERY_IS_PUBLIC` was read as four bytes when the engine writes one, which made every card look public to the sanitizer and misaligned the Link block after it, and `MSG_SELECT_CHAIN` entries were read as 19 bytes when the engine writes 23 (a full `loc_info` with position).

## Stop 4: the server

The server layer owns three things: the lifetime of duels (`DuelManager`), the decision of who sees which engine message (`ServerDuelHandler`, `SoloDuelHandler`), and the way players reach it (`DuelCommand`, the four payloads). Nothing here interprets game rules. The engine does that; the server relays and redacts.

### Files

| File | Lines | Owns |
|---|---|---|
| `server/DuelManager.java` | ~230 | Singleton created on `ServerStartingEvent`; the `DuelEngine`; maps from duel id to session, player to duel, duel to solo handler; per-player current deck; deck resolution |
| `server/ServerDuelHandler.java` | 168 | `DuelEventListener` for two humans: routes prompts to one player, sanitizes broadcasts, ends the duel on `Win` |
| `server/SoloDuelHandler.java` | 244 | `DuelEventListener` for `/duel test`: answers player 1's prompts from a fixed heuristic table |
| `server/DuelCommand.java` | 235 | Brigadier tree for `/duel challenge|accept|forfeit|test|deck` |
| `server/DuelNetworking.java` | 23 | Registers the four payloads on the mod bus |
| `server/DuelStartPayload.java` etc. | 24-50 each | The four records: start snapshot, one `DuelMessage`, end, response bytes |
| `server/ServerPayloadHandler.java` | 11 | Forwards a `DuelResponsePayload` to `DuelManager.handleResponse` |
| `duel/DuelEventListener.java` | 25 | The callback interface with the 0/1/2 return protocol |

### Lifecycle

`Duelcraft`'s constructor registers `DuelManager::onServerStarting` and `::onServerStopped` on `NeoForge.EVENT_BUS` (`Duelcraft.java:86-87`). `init()` builds one `DuelEngine` from the two config lists and opens the `DeckRegistry` at `<gameDir>/duelcraft/decks` (`DuelManager.java:52-69`). Every `DuelSession`, PvP or solo, shares that one engine by reference; each session holds its own native duel handle on top of it. `shutdown()` closes the sessions and the engine but does not clear `soloHandlers`, `duelInvites`, or `playerCurrentDeck`, which is harmless only because `onServerStopped` drops the whole instance right after.

The maps: `activeDuels` (duel id to session), `playerToDuel` (player UUID to duel id; solo duels register only the human), `soloHandlers` (duel id to handler, populated only by `startSoloDuel`), `playerCurrentDeck` (UUID to deck name), and `duelInvites` (target UUID to `PendingChallenge`). `duelInvites` is public, carries a `// FIXME: Temporary for testing`, and `DuelCommand` pokes it directly with `put`, `get`, and `remove` (`DuelManager.java:35-36`). Invites never expire.

### Follow a PvP duel

1. `/duel challenge <player> [seed] [rule]` (`DuelCommand.challenge`, 97-116) rejects self-challenges and players already dueling, stores `PendingChallenge(challengerUUID, seed, rule)` under the target's UUID, and messages both players. No deck is checked here.
2. `/duel accept` (`accept`, 118-150) finds the invite under the accepter's own UUID, drops it if the challenger went offline, resolves both decks through `DuelManager.resolveDeck` (which throws when a player has no deck set), reads both deck names for the log, and calls `startDuel`.
3. `startDuel` (`DuelManager.java`, from line 160) guards re-entry, builds `DuelOptions.of(seed, rule)`, creates a `ServerDuelHandler(p1, p2, duelId)` and a `DuelSession(engine, options, handler)`, registers both players, shuffles team 1 with `seed` and team 2 with `seed + 1`, sends each player a `DuelStartPayload` carrying their own index (0 or 1) and the opponent's name, then calls `setupDuel` and the first `process()`.
4. `process()` runs the engine until the first prompt. Every record goes through `ServerDuelHandler.onMessage`, which decides where it goes (next section).

One detail to catch on step 3: `deckSize` and `extraSize` are read once from the shuffled team 1 deck and reused in player 2's payload (`startDuel`, the two `PacketDistributor.sendToPlayer` calls). Player 2's client starts with player 1's counts. Whether that is visible depends on how the client uses those two numbers (Stop 5: the constructor sizes the extra-deck placeholder list from them, and `Start` resets both players' lists from the engine's own counts a moment later).

### Follow a response

`DuelResponsePayload` arrives, `ServerPayloadHandler.handleResponse` calls `DuelManager.handleResponse(player, bytes)` (196-203). It looks up the duel by player UUID, drops the response if the session is missing or ended, calls `session.setResponse(bytes)`, which hands the bytes to the engine and runs `process()` again until the next prompt, then calls `processSoloAutoResponseByDuelId(duelId)`, a no-op for PvP duels because `soloHandlers` has no entry for them. One code path serves both duel kinds.

### The solo AI

`/duel test [aiDeck] [seed] [rule]` resolves the human's deck, uses the same `Deck` object for the AI when no name is given (both still shuffle with different seeds), and calls `startSoloDuel`. That method mirrors `startDuel` but sends one `DuelStartPayload`, with opponent name `"AI Opponent"`, and finishes with `processSoloAutoResponse(duelId, handler)` so an AI decision queued during the opening pass runs at once.

`SoloDuelHandler.onMessage` (32-72) sends the 19 prompt record types through `routePrompt(player, msg)`. A prompt for player 0 goes to the client and returns 1. A prompt for player 1 goes to `buildAutoResponse(msg)` (116-243), a pure function over the prompt record: first summonable card, first candidate in any `SelectCard` or `SelectSum`, yes to `SelectEffectYn` and `SelectYesNo`, decline non-forced chains, always rock in rock-paper-scissors, and the whole counter total on the first card for `SelectCounter`. The result is wrapped in a `Runnable` and parked in the single field `pendingAutoResponse`; `onMessage` still returns 1, so `process()` stops. `DuelManager.processSoloAutoResponse` then calls `consumePendingAutoResponse()` and runs the Runnable inline. The Runnable calls `handleSoloAutoResponse`, which calls `session.setResponse` (the engine runs, possibly parking a new AI decision) and then `processSoloAutoResponseByDuelId` again. Each chained AI action adds a Java stack frame; the recursion unwinds when the engine reaches a human prompt. There is no queue and no tick scheduling. A prompt type with no case in `buildAutoResponse` returns null and is forwarded to the human with a warning, so the human answers on the AI's behalf. `AnnounceCard` is in the routing switch (line 63) but has no case in the table, so that is one prompt where this happens today.

### Who sees what: `ServerDuelHandler.onMessage`

Return codes follow `DuelEventListener` (11-19): 0 continue, 1 wait for a response, 2 duel over.

- `Retry` (31-35): broadcast to both players, return 1. Both clients receive it, though only one sent the bad response.
- `Win` (36-41): `DuelEndPayload(winner, reason)` to both, return 2. `process()` then sets `ended` and calls `onDuelEnd()`, which calls `DuelManager.endDuel`.
- The 19 prompt types (42-61): `sendToPlayer(sel.player(), msg)`, no sanitization, return 1.
- Everything else (62-65): `broadcastToBoth`, which runs `hideInfo(msg, recipient)` once per recipient, return 0.

`hideInfo` (84-147) is the whole hidden-information policy of the game:

| Message | Redacted when | How |
|---|---|---|
| `Draw` | the drawing player is not the recipient | all codes become 0 |
| `ShuffleHand` | same | all codes become 0 |
| `Set` | the setting player is not the recipient | code becomes 0 (a set is face-down by definition) |
| `Move` | destination controller is not the recipient and the destination is face-down, or the card left the other player's hand into a face-down destination | code becomes 0 |
| `PosChange` | controller is not the recipient and the new position is face-down | code becomes 0 |
| `UpdateData`, `UpdateCard` | the queried player is not the recipient | each card passes through `sanitizeCard` |

`sanitizeCard` (149-158) keeps a `QueriedCard` intact unless the card is face-down and `isPublic` is false; then it returns a fresh card carrying only `flags` and `position`. Until the `FieldQuery` fix on this branch lands, `isPublic` reads as true for every card (Stop 3), so this branch never fires and face-down cards reach the opponent with their codes. `SoloDuelHandler` has no `hideInfo` at all: its default branch sends every non-prompt message to the human unredacted, so in `/duel test` you can see the AI's set cards if you look at the packets.

### Ending a duel

Three paths exist and only one of them tells the clients.

- Engine `MSG_WIN`: `DuelEndPayload` to both, then `endDuel`.
- `/duel forfeit` (`DuelCommand.forfeit`, 152-161): calls `DuelManager.endDuel(duelId)` directly. `endDuel` (205-212) removes and closes the session, drops the solo handler, and scrubs `playerToDuel`. No packet leaves the server. The opponent's screen stays open on a dead session, which is the freeze you saw in the two-client test.
- Disconnect: nothing. A grep for `LoggedOut`, `PlayerEvent`, `Disconnect`, `logout` under `src/main/java` finds no listener. The maps keep the entries and the opponent waits forever. Disconnect recovery is the deferred item in the roadmap.

### Networking

`DuelNetworking.onRegisterPayloads` runs on the mod bus for `RegisterPayloadHandlersEvent` (`Duelcraft.java:73`). One `PayloadRegistrar` versioned `"1"`, three `playToClient` registrations pointing at `ClientPayloadHandler.handleStart/handleMessage/handleEnd`, one `playToServer` pointing at `ServerPayloadHandler.handleResponse`. `DuelStartPayload` has seven fields, one past what `StreamCodec.composite` supports, so it has a hand-written `StreamCodec.of` (27-44). `DuelMessagePayload` delegates to `DuelMessageCodec.encode/decode`. The other two use `composite` with `VAR_INT` and `BYTE_ARRAY`.

### Threading

Nothing in this package hops threads: no `enqueueWork`, no `executesOn`. You still get single-threaded behavior, from two NeoForge defaults. Brigadier commands run on the server thread. `PayloadRegistrar` defaults to `HandlerThread.MAIN` and wraps every handler in `MainThreadPayloadHandler`, which enqueues onto the main thread (the Stop 5 agent confirmed this in NeoForge's own `PayloadRegistrar.java:29, 224-231` in the Gradle cache). So every call into the engine, including the AI recursion, runs on the server thread. No synchronization is needed, and a slow engine step stalls the tick. That is the trade you accepted, and it is worth knowing before you ever move `process()` off-thread.

### Things to notice

- Prompts go to one player and everything else to both because a menu belongs to the acting player while board changes belong to everyone. The two code paths in `onMessage` encode that rule.
- The AI is a heuristic table, not a search, and `SoloDuelHandler.java:114-115` says so. It ignores `min`, `max`, and target sums beyond "pick one".
- Two unrelated methods react to `ServerStartingEvent`: the static `DuelManager::onServerStarting` that does the work, and an instance `Duelcraft.onServerStarting` (116-120) from the MDK template that logs "HELLO from server starting".
- `DuelManager.get()` is called without null checks everywhere. The server lifecycle events keep it valid; a unit test that constructs a handler without a server would find out.
- No `/duel` subcommand has a `.requires(...)` permission predicate. Any player can run any of them.

### Cleanup candidates

- `DuelManager.handleSoloAutoResponse` (127-142): the `for` loop over `activeDuels.entrySet()` re-finds an entry already fetched by key two lines earlier; `processSoloAutoResponseByDuelId(duelId)` alone does the job, as `handleResponse` shows. The comments around it (132-134, 152) read as in-progress reasoning.
- `DuelCommand.java:222`: "No current deck (using standard)." describes a fallback that no longer exists. Task 4 on the branch fixes it, and splits the `accept` deck errors so each player learns whose deck failed.
- `DuelStartPayload` sizes for player 2 come from player 1's deck (see the PvP flow).
- `SoloDuelHandler.buildAutoResponse`: no `AnnounceCard` case though it is routed to the AI.
- Two wordings for the same guard: "A player is already in a duel!" (108) and "You are already in a duel!" (170).
- `Config.java:19-34` and `Duelcraft.java` (fields 44-66, `addCreative`, `commonSetup` body, the instance `onServerStarting`) are untouched MDK template code: example block, item, creative tab, magic-number config, dirt-block logging.
- `endDuel` sends no `DuelEndPayload`; the win overlay and concede button on the known-issues list depend on fixing this first.

### Check yourself

- Why does `handleResponse` call `processSoloAutoResponseByDuelId` even for PvP duels? (Because the solo map is empty for them, one path serves both.)
- What happens if the AI's deck has no summonable monster on turn one? (Trace `buildAutoResponse` for `SelectIdleCmd`: which index does it fall through to, and does it end the turn?)
- Where would a disconnect listener register, and which two maps must it clean? (`Duelcraft`'s constructor for the event, `activeDuels` and `playerToDuel`, plus a `DuelEndPayload` to the survivor.)

## Stop 5: the client state model

`ClientDuelState` is the client's copy of the board. It is mutated in exactly one place, `applyMessage`, and read by the UI once per tick through a set of dirty flags. Understanding this file means understanding what the UI can and cannot know.

### Files

| File | Lines | Owns |
|---|---|---|
| `client/ClientDuelState.java` | 664 | Zones, piles, LP, phase, the pending prompt, the chain, the click-menu action map, dirty flags |
| `client/ClientPayloadHandler.java` | 30 | The three client payload entry points |
| `DuelcraftClient.java` | 80 | Client mod entry: config screen, async card-data init, static getters |
| `DuelcraftLDLibPlugin.java` | 16 | The `@LDLibPlugin` hook LDLib2 needs to see the mod; logs and nothing else |
| `client/LDLibDuelScreen.java` (part) | 646 | Not state, but its inner `UIRefresher` is the only consumer of the dirty flags, so it belongs in this stop |

### The model

Every per-player array is indexed by the engine's player index, 0 or 1, exactly as it arrives on the wire. `localPlayer` (line 68, final) records which index you are; `opponent()` returns `1 - localPlayer`. Nothing in the arrays knows "mine" from "theirs"; every consumer computes `plr` and `opp` first (`LDLibDuelScreen.java:165-166, 298-299`). Follow that pattern in any new per-player UI code.

- Fixed after construction: `localPlayer`, `opponentName`, `duelFlags` (the engine creation flags, read later by `FieldLayout.fromFlags` to choose the zone geometry).
- LP and time: `lp[2]`, `startingLP`, `currentTurn`, `currentPhase`, `turnCount`.
- Hands: `hand[2]`, lists of codes; 0 means unknown.
- Zones: `mzone[2][7]` and `mzonePos[2][7]` (0-4 main, 5-6 extra monster zones), `szone[2][8]` and `szonePos[2][8]` (0-4 spell/trap, 5 field, 6-7 pendulum). Code 0 means empty.
- Stats cache: `mzoneStats[2][7]`, `szoneStats[2][8]` of `QueriedCard`, replaced wholesale by reference on every `UpdateData`.
- Deck: `deckCount[2]` only. Nobody browses the deck, so it is a counter (comment at line 98).
- Browsable piles: `extra`, `extraPos`, `grave`, `banished`, all `List<Integer>[2]`, full lists because the zone inspector opens them (comment at line 101). `extraPos` runs parallel to `extra` so a face-up pendulum monster in the extra deck can be told apart from the face-down cards.
- Overlay: `overlay[2][7]`, one material list per monster zone.
- Chain: `chain`, a list of `ChainLink(code, location, chainIndex)`.
- Prompt: `pendingPrompt`, one `DuelMessage` or null.
- Click menu: `cardActions`, a map from `CardLocation(controller, location, sequence)` to the list of `CardAction(actionType, listIndex, label)` built from `SelectIdleCmd` and `SelectBattleCmd` (623-661). This is how the UI knows that clicking a specific card can Summon, Set, Activate, or Attack, and which list index to answer with.
- One-shot display values: `lastAction`, `lastHintType`, `lastHintData`, `confirmTitle`, `confirmCards`, `rpsHand0`, `rpsHand1`, `winner`, `winReason`.

### `applyMessage`

`lastAction = msg` runs first (177), then one `switch (msg)` over the sealed records (176-460). The mutations and flags, grouped:

| Records | State change | Flags |
|---|---|---|
| `Start` | LP, `startingLP`, deck counts, extra lists rebuilt as placeholder zeros | `PILE_COUNTS` |
| `Draw` | append to `hand[p]`, decrement `deckCount[p]` | `HAND_p`, `PILE_COUNTS` |
| `Move`, `Set`, `Swap` | `removeCard(from)` then `placeCard(to, code)`; `Swap` only handles monster zone to monster zone (578-587) | via `markLocationDirty` on each location |
| `PosChange`, `FlipSummoning` | zone code (if nonzero) and position | `MZONE_p` or `SZONE_p` |
| `NewTurn`, `NewPhase` | turn and phase | `TURN_PHASE` |
| `Damage`, `Recover`, `PayLpCost`, `LpUpdate` | `lp[p]` | none (LP is bound reactively, see below) |
| `Chaining`, `ChainEnd` | chain list | `CHAIN` |
| `UpdateData` | stats cache slot, or the extra list plus positions when the location is EXTRA | `FIELD_STATS` (+ `PILE_COUNTS` for EXTRA) |
| `ShuffleHand` | hand replaced | `HAND_p` |
| `SelectIdleCmd`, `SelectBattleCmd` | `pendingPrompt`, rebuild `cardActions` | `PROMPT` |
| The other 18 prompt records | `pendingPrompt` | `PROMPT` |
| `ConfirmDeckTop`, `ConfirmCards` | title and cards for the inspector | `CONFIRM` |
| `HandResult` | the two rock-paper-scissors hands | `CHAIN` (reused so `updateStatusLabel` runs) |
| `Win` | winner, reason | `WINNER` |
| `Retry` | nothing | `PROMPT` |
| `Hint` | last hint | none |
| Summoning/summoned family, `Chained`/`ChainSolving`/`ChainSolved`/`ChainNegated`/`ChainDisabled`, `Attack`/`Battle`/`AttackDisabled`/`DamageStep*`, `ShuffleDeck`, `ShuffleExtra`, `CardSelected` | nothing | none |

Twelve records reach the `default` branch and are logged at debug: `Raw`, `CardHint`, `FieldDisabled`, `BecomeTarget`, `Equip`, `Unequip`, `CardTarget`, `CancelTarget`, `AddCounter`, `RemoveCounter`, `TossCoin`, `TossDice`. The engine-implementation checklist tracks most of them as UI gaps.

`markLocationDirty` (464-474) maps a `LocInfo` to flags: HAND to the hand flag, MZONE and OVERLAY to the monster flag, SZONE to the spell flag, DECK/EXTRA/GRAVE/REMOVED to `PILE_COUNTS`.

### Dirty flags and the refresh cycle

`DirtyFlag` has 14 values (26-33). `consumeDirtyFlags()` copies and clears the set; it is a drain, not a peek. Only `applyMessage` raises flags. Only `UIRefresher.onTick` (`LDLibDuelScreen.java:348-396`) consumes them, and it runs as a `UIEvents.TICK` listener on the root element (line 260). Refresh is tick-driven: a message can sit for up to one UI tick before it is drawn, and a burst of twenty messages redraws once.

| Flag | Consumer |
|---|---|
| `HAND_0/1` | `rebuildHand` for whichever side matches |
| `MZONE_0/1` | `field.refreshMonsterZones(player)` and `field.refreshFieldStats()` |
| `SZONE_0/1` | `field.refreshSpellZones(player)` |
| `FIELD_STATS` | `field.refreshFieldStats()` |
| `PILE_COUNTS` | `field.refreshPiles()`, `zoneInspector.refresh()` |
| `PROMPT` | `prompt.rebuild()`, `updatePhaseButtons()` |
| `TURN_PHASE` | `updatePhaseButtons()` |
| `CHAIN` | `updateStatusLabel()` |
| `WINNER` | `showWinOverlay()` |
| `CONFIRM` | `zoneInspector.showConfirmCards()` |
| `LP` | nobody |

LP labels and pile counts bypass the flags. `bindReactiveData()` (297-330) binds them with `SupplierDataSource.of(() -> state.lp[...])`, LDLib2's reactive mechanism, which re-reads the supplier every tick regardless. That is why `DirtyFlag.LP` exists and is never raised.

A second trigger path exists for card art. `CardImageManager.setOnTextureLoaded` holds one callback (a single overwritten slot, `CardImageManager.java:53, 89-91`); `LDLibDuelScreen` registers one that calls `state.markAllVisualsDirty()` and retries the placeholder images (269-274) whenever a texture finishes decoding.

### Prompts

Every prompt record overwrites `pendingPrompt`; there is no queue, matching the engine's one-outstanding-prompt model. The only place that clears it is `LDLibDuelScreen.sendResponse()` (110-117), which nulls `pendingPrompt` and `cardActions` at send time, before any server acknowledgement.

That optimistic clear interacts badly with `Retry`, by inspection. `applyMessage`'s `Retry` case (181-184) only raises `PROMPT` and logs "re-prompting". `PromptController.rebuild()` (`PromptController.java:96-101`) hides the prompt overlay whenever `pendingPrompt` is null. So a rejected response followed by `MSG_RETRY` hides the prompt instead of re-showing it, and the player has no way to answer again. Nobody has reported hitting it, which fits the engine rarely sending `MSG_RETRY` for responses the UI builds. It is the strongest argument for either wiring `ResponseValidator` (reject bad responses before sending) or keeping the last prompt around until the engine moves on.

In-progress selection (which cards are picked so far, the running sum, field-selection mode) does not live here. `PromptController` holds it (`selectedIndices` at `PromptController.java:621` and the `instanceof` dispatch at 597-613). The split is: state keeps the immutable prompt the server sent plus `cardActions`; the UI keeps the mutable answer under construction.

### Hidden information

One convention, everywhere: code 0 means hidden or unknown. There is no "known" boolean. Hands, zones, and piles all use it, and the hand renderer draws a card back for code 0 (`LDLibDuelScreen.java:415-419`). The zeroing happens on the server in `hideInfo` (Stop 4); the client trusts what it receives. Extra-deck contents arrive through `UpdateData` for `LOCATION_EXTRA` (327-336), so a sanitized `QueriedCard` with code 0 lands in `extra[p]` through the same convention by a different route.

### Client lifecycle and threading

`DuelcraftClient` registers NeoForge's `ConfigurationScreen` as the config screen and, on `FMLClientSetupEvent`, fires `CompletableFuture.runAsync(DuelcraftClient::initCardData)` on the common pool (31-34). `initCardData` (36-67) downloads or loads `cards.cdb`, builds the `CardImageManager`, loads `strings.conf`, and adds a JVM shutdown hook to close the first two. One try/catch wraps all of it, so a failed database download also skips images and strings; the three statics stay null and every consumer null-checks.

`ClientPayloadHandler.handleStart` opens the screen through `LDLibDuelScreen.open(payload)`, `handleMessage` calls `LDLibDuelScreen.applyMessage`, `handleEnd` closes the screen and sets the Minecraft screen to null. None of them call `enqueueWork`, and none need to: NeoForge wraps them in `MainThreadPayloadHandler` by default (Stop 4, Threading). `applyMessage`, `onTick`, and the texture callback (documented as render-thread in `CardImageManager.java:88`) all run on the client main thread, which is why the plain `EnumSet` of dirty flags needs no locking.

`LDLibDuelScreen` keeps the active duel in three statics, `activeState`, `activeUI`, `refresher` (55-57). One duel screen exists at a time; `create()` builds a fresh `ClientDuelState` per duel (69) and `close()` nulls the statics (89-93). The TICK and CLICK listeners on the root element are discarded with the `ModularUI`, and the texture callback slot is overwritten by the next screen, so nothing leaks across duels.

### Things to notice

- The UI writes back into state in two places: `updateStatusLabel` zeroes `rpsHand0/1` after showing them (455-478), and `sendResponse` clears the prompt. Everything else flows one way.
- `HandResult` borrowing `DirtyFlag.CHAIN` works because `updateStatusLabel` happens to serve both; the flag name no longer describes what it drives.
- `refreshFieldStats` runs on `MZONE_0/1` as well as `FIELD_STATS` (372-374), so a monster that moves gets its stat overlay redrawn even though `Move` raises no `FIELD_STATS`. Spell zones get no such coupling.
- The stats cache and the zone arrays can disagree for a moment: a card placed by `Move` has no `QueriedCard` until the next synthetic `UpdateData` lands after that processing pass.
- Several prompt cases log every candidate card at `info` (369-377, 388-394, 408-418), the rest of the file at `debug`. Debugging leftovers.

### Cleanup candidates

- `DirtyFlag.LP` is declared and never raised or checked.
- `UpdateCard` is unreachable: nothing produces it since `QueryParser` left, yet `ClientDuelState` (339-346), `ServerDuelHandler` (130-136), and `DuelMessageCodec` (51, 263) still carry it. Deleting the record is a compile-checked change thanks to the exhaustive codec switch. Decision pending from Stop 2.
- `winReason` is set (211) and never read; `PromptController.showWinOverlay` takes only a boolean.
- `lastAction` is never read outside the class; the "UI can animate from lastAction" comment (357) describes a hook that does not exist.
- The `Retry` gap above.
- `extraPos` is not cleared where `extra` is (constructor 161-164 versus the `Start` handler 200-204); harmless with one `Start` per duel, but the comment says "reset" and the code only grows the list.
- `handFlag`, `mzoneFlag`, `szoneFlag` (55-65) are three copies of one ternary.

### Check yourself

- A `Move` from your hand to a face-down set position: which flags fire, and what code does the opponent's client store? (`HAND_p` and `SZONE_p`; 0, because `hideInfo` zeroes it before the payload leaves.)
- Why can `bindReactiveData` skip the dirty flags for LP but the hand cannot? (A label re-reads one int cheaply every tick; a hand rebuild creates elements and images and must be gated.)
- If the engine sends two prompts in one pass, which one wins? (It cannot; the listener returns 1 on the first prompt and `process()` stops.)

## Stop 6: the client UI

Seven files, 2,466 lines, one screen. `LDLibDuelScreen` loads the XML and owns the tick loop; four subcontrollers each own one concern; `FieldLayout` is the only pure-Java piece and the only one with unit tests. Read this stop with `duel_screen.xml` open next to the Java.

### Files

| File | Lines | Owns |
|---|---|---|
| `client/LDLibDuelScreen.java` | 646 | Static entry points (`open`, `applyMessage`, `close`, `sendResponse`); inner `UIRefresher` binds elements by id, wires HUD, hands, and phase buttons, dispatches dirty flags per tick, caches card images, constructs the subcontrollers |
| `client/DuelScreen.java` | 36 | `ModularUIScreen` subclass whose only job is the canvas scale transform |
| `client/FieldLayout.java` | 137 | Rule-set geometry: which zones exist and which XML slot id each engine zone maps to |
| `client/FieldRenderer.java` | 419 | Zone slots, piles, stat overlays, `SelectPlace` highlighting; the only class that turns an absolute player index into a viewer-relative `Side` |
| `client/PromptController.java` | 830 | The prompt overlay, one builder per prompt type, field-click handling for active selections, the in-progress selection state |
| `client/ZoneInspectorController.java` | 164 | The pile browser and the reveal list |
| `client/ClickDispatcher.java` | 184 | Routes a card click to the context menu, `SelectPlace`, or the prompt controller; owns the context menu |
| `assets/duelcraft/ui/duel_screen.xml` | ~830 | The superset layout for every rule set plus the LSS stylesheet |

### How the pieces talk

Each subcontroller declares a nested `Callbacks` interface naming only what it needs from the host: five methods for `FieldRenderer` (43-50), four for `ZoneInspectorController`, six for `PromptController` including `sendResponse`, `cardDisplayName`, and `resolveDesc`, one for `ClickDispatcher`. `UIRefresher`'s constructor builds each with an anonymous implementation that forwards to its own private methods (`LDLibDuelScreen.java:176-192, 215-228, 232-251`). Construction order is field, zone inspector, prompt, then clicks, because `ClickDispatcher`'s constructor takes `field` and `prompt`. That order is why `FieldRenderer`'s click callback goes through `UIRefresher.onCardClicked` (531-533) instead of calling `clicks` directly: `clicks` does not exist yet when `field` is built. The one-hop forwarder has no other logic.

### Screen lifecycle

`ClientPayloadHandler.handleStart` calls `LDLibDuelScreen.open(payload)`, which sets the screen to `create(startInfo)`. `create()` (68-75) builds the `ClientDuelState`, loads the XML, builds the `UIRefresher`, resolves `#duel-canvas`, and returns a `DuelScreen`. The split lets the UI harness call `create()` and open the result itself.

`loadFromXml()` (95-106) parses `duel_screen.xml` and calls `UI.of(root, stylesheets, screenSize -> Size.of(960, 540))`; the lambda ignores the real window, so the root is always laid out at the design size. `DuelScreen.init()` (26-35) runs on open and on every resize, computes `k = max(0.25, min(width/960, height/540))`, and applies `canvas.transform(t -> t.scale(k))` to the `#duel-canvas` child. Minecraft hands `init()` GUI-scaled width and height, so GUI scale 1 to 4 falls out of the same arithmetic with no special case. Scaling the child rather than the root works because LDLib2 centers the root and ignores the root's own transform; the pivot defaults to the center, which sits on the screen center.

Closing has a gap. `LDLibDuelScreen.close()` (89-93) nulls the three statics and is called from exactly one place, `ClientPayloadHandler.handleEnd`. `DuelScreen` overrides neither `onClose` nor `shouldCloseOnEsc`, so ESC closes the Minecraft screen through the default path and leaves `activeState`, `activeUI`, and `refresher` populated; later payloads still apply messages to the orphaned state. The win overlay's Close button (489-492, `PromptController.showWinOverlay` 762-777) has the same gap. This is the code behind the "ESC with no reopen" item on the known-issues list.

### Layout

`FieldLayout` is a record `(columns, emz, pendulum)` built once by `fromFlags(long)` (38-44): three columns when `DUEL_3_COLUMNS_FIELD` is set, else five; EMZ when `DUEL_EMZONE` is set and the field has five columns; pendulum `NONE` without `DUEL_PZONE`, `SEPARATE` with `DUEL_SEPARATE_PZONE`, else `SHARED`. So MR1 is five columns and nothing else; MR3 adds separate pendulum zones (engine S/T sequences 6 and 7); MR4 and MR5 add EMZ and shared pendulum zones on S/T 0 and 4; Speed and Rush are three columns with no pendulum.

`slotId(Zone)` (47-71) maps an engine `(Side, location, sequence)` to an XML id. Main zones 0 to 4 pass through `columnVisible()` (three-column fields show only 1 to 3). Monster sequences 5 and 6 are the EMZ and cross-map: the local player's 5 is `emz-left` and 6 is `emz-right`, the opponent's 5 is `emz-right` and 6 is `emz-left`, because the two physical slots are shared and must read left and right from the viewer's seat. S/T 5 is the field spell; 6 and 7 are `pz-left` and `pz-right` only under `SEPARATE`. Piles map one to one. Slot ids live in the owner's frame (`opp-st-0` is the opponent's zone 0, mirrored by CSS) except the two EMZ ids, which live in the viewer's frame.

The XML holds every slot for every rule set. `hiddenSlotIds()` (83-89) lists the ids no zone maps to under the current layout, and `FieldRenderer.bindSlots()` (69-89) gives them `.rule-hidden` (`display: none`, XML 587-590). Under `SHARED` pendulum it also tags S/T 0 and 4 with `.pendulum`, which reveals the lapis and redstone markers (XML 592-598, 650-666, 749-765).

`FieldRenderer.side()` and `player()` (93-99) are the only translation between engine index and `Side`. `shownZone()` (114-116) picks the first occupied candidate for a shared slot and falls back to the first candidate, which is always the local player's because `FieldLayout.allZones()` lists `PLR` before `OPP`. An empty EMZ therefore counts as yours for placement and clicks.

### Refresh

The flag dispatch (Stop 5) lands here:

- Hands rebuild wholesale (399-429): clear the scroll view, add one `.card` element per code. Only your own cards with a nonzero code get an image and click and hover listeners; opponent cards get the card back and nothing else.
- Zones update in place (`refreshZoneSlot`, `FieldRenderer.java:165-214`): strip the old `.card`, `.card-back`, and `.stat-*` children from the fixed slot element, add a fresh one if occupied. A nonzero position with code 0 still counts as present, which is how the opponent's face-down cards render. Opponent cards in an EMZ get `.emz-opp` to rotate 180 degrees by hand, because the EMZ slots sit outside `#opponent-side` and miss its blanket flip (XML 71-73).
- Stat overlays (`updateMonsterStats`, 232-290) rebuild only the ATK/DEF and level labels. Link monsters show ATK only. Buff colors compare `attack` to `baseAttack` and the live level to `CardInfo.levelOrRank()`.
- Piles (294-307) update their backgrounds in place; an open zone inspector refreshes with them.
- LP, title, turn and phase, and the eight pile counts bind through `SupplierDataSource` (297-343) and re-evaluate every tick. The XML seeds them with placeholders (`max-value="8000"`, `40`) that the bindings replace on the first tick.

Images that were not cached when a slot was built go into `pendingCardImages` (157); the texture-loaded callback marks all visuals dirty and retries them (598-620). The hover banner has a single-slot version of the same retry.

### Prompts

`PromptController.rebuild()` (96-163) switches on `state.pendingPrompt`:

| Prompt | UI | Response | Submission |
|---|---|---|---|
| `SelectIdleCmd`, `SelectBattleCmd` | no overlay; sets `isBattleCmd`; field highlights; phase buttons | `selectCmd(type, index)` from the context menu or a phase button | one click |
| `SelectYesNo`, `SelectEffectYn` | Yes and No buttons | `selectYesNo(bool)` | one click |
| `SelectOption`, `RockPaperScissors` | one button per option, text through `resolveDesc` | `selectOption(i)`, `rockPaperScissors(i+1)` | one click |
| `SelectChain` | auto-pass when empty and not forced; else a card scroller plus Pass | `selectChain(idx)` or `-1` | one click |
| `SelectCard` | field-selection mode when every candidate is on the field and none is overlay material, else a scroller with Confirm and optional Cancel | `selectCards(int[])`, `selectCardsCancel()` | batch; field mode auto-submits at `max` |
| `SelectTribute` | field-selection mode only | `selectCards` equivalent | auto-submits when the tribute sum reaches `min`; over-tribute allowed because the engine allows it |
| `SelectUnselectCard` | field mode or scroller, Finish and Cancel | `selectUnselectCard(index)`, `selectUnselectCardFinish()` | one index per click; the engine re-prompts |
| `SelectSum` | field mode or scroller with a live running sum | `selectCards(int[])` | auto-submits when sum equals target and the count is in range |
| `SelectPosition` | up to four text buttons from the bitmask | `selectPosition(bit)` | one click |
| `SelectPlace` | no overlay; `FieldRenderer.highlightValidPlaces` | handled entirely by `ClickDispatcher.handlePlaceSelection` | one click |
| `SelectCounter`, `SortCard`, `SortChain`, `AnnounceRace`, `AnnounceAttrib`, `AnnounceNumber`, `AnnounceCard` | a bare overlay with the record's class name and no buttons; a logged warning | none | the player cannot answer |

Those last seven have records, codec arms, `ResponseBuilder` methods, and `ResponseValidator` methods. The gap is entirely in `PromptController`, and the engine-implementation checklist tracks them as Tier 2.

Every response path ends in `LDLibDuelScreen.sendResponse()` (110-117): send the payload at once, null `pendingPrompt` and `cardActions`, then `onResponseSent()` hides the overlay, exits field-selection mode, strips every `.target`, `.selected`, and `.selectable` class, and hides the context menu.

`PromptController`'s class comment (22-31) explains the shared mutable selection state: one prompt is active at a time, and earlier bugs came from calling `rebuild()` in the middle of an interaction, so field clicks go through per-prompt handlers (`handleSelectCardClick` and friends, 597-726) that mutate state without rebuilding.

### Clicks

`ClickDispatcher.onCardClicked(player, location, sequence, event)` (70-98) is the single entry, reached from field slots, pile entries, and hand cards. In order: if `cardActions` has entries for that card and the pending prompt is an idle or battle command, stop propagation and show the context menu; else hide any menu, and if a `SelectPlace` is pending, validate the zone bit against `sel.field()` and send; else hand the click to `PromptController.handleFieldClick`.

The context menu's position (138-158) starts from screen coordinates, runs through `canvas.getWorldToLocalPose()` to undo the scale, then subtracts `canvas.getPositionX()` and `getPositionY()` because the inverse pose lands in root-layout space, which still carries the root's centering offset. The menu size is computed from literals (`actions.size() * 15f + 2f` by `16f`) that mirror the `.ctx-action` and `#context-menu` styles by hand, and the comment at 141-143 says so.

Popups race the root's outside-dismiss listener (`wireOutsideDismiss`, 58-66). Every handler that opens or uses a popup calls `stopPropagation()` first: `ClickDispatcher.java:78`, `PromptController.java:224, 258, 393, 410, 521, 738, 747, 752`, `ZoneInspectorController.java:88`. Forget one and the click that opened the popup closes it.

`ZoneInspectorController.wirePileClicks()` (49-63) binds both graveyards, both banished piles, and your extra deck. The opponent's extra deck has no handler. `showConfirmCards()` (98-124) is a separate one-shot path for reveals that consumes `state.confirmCards` without touching the inspected-pile tracking.

### `ResponseValidator`: the decision

Zero production callers, 42 tests, one static method per prompt type that throws `IllegalArgumentException` on a bad index, count, duplicate, or bit before delegating to `ResponseBuilder`. Where it would plug in: `selectCards` and `selectTribute` at the confirm and auto-submit sites (274-278, 721-722), `selectChain` at 224-225 and 234, `selectOption` at 122-124, `selectUnselectCard` at its six send sites, all drop-ins because `sel` is in scope. `selectYesNo` and `selectPosition` would need the record threaded into `buildYesNoPrompt` and `addPositionButton`, and the yes/no validators check nothing anyway. `selectPlace` is the interesting one: `ClickDispatcher.handlePlaceSelection` plus `FieldRenderer.getFieldBit` (412-418) already reimplement the zone-to-bit computation that `ResponseValidator.zoneToBit` (246-256) contains, with separate code. The six validators for the seven unbuilt prompts have nowhere to go until those prompts exist.

My recommendation: delete `ResponseValidator` and its test, move the one piece of logic that is duplicated (`zoneToBit`) into `FieldLayout` or `FieldRenderer` so the bit layout has one home, and fix the `MSG_RETRY` gap from Stop 5 by keeping `pendingPrompt` until the next message replaces it. The UI already gates every input it builds (buttons exist only for valid options, auto-submit fires only when the counts fit), so the validator re-checks what the UI enforced. The rejections it cannot predict are the same ones the engine decides, and for those the client needs a working retry path, not a second guess. If you would rather keep it, wire the five drop-in sites in one commit and delete the rest.

### Things to notice

- Every element lookup is `ui.selectId(id).findFirst()`; `getElementById` appears nowhere in the client (`LDLibDuelScreen.java:288`, `FieldRenderer.java:71`, `PromptController.java:828`).
- Float everywhere the LDLib2 API is involved: `float k`, `Vector3f`, `(float) state.lp[plr]` for the progress bar.
- LSS spellings: `aspect-rate` (XML 113, 122, 133, 219), `row_reverse`, `space_between`, `quad_in_out`, `HORIZONTAL`, `ALWAYS`. Unknown tokens default silently.
- `<label>` binds to `Label` and `<text>` to `TextElement`; `setTextElement()` (622-627) branches on both so either tag works.
- `DynamicSizeProvider`, named in an older project note, appears nowhere in the tree.
- `.phase-btn.hidden` fades a button with `opacity: 0` rather than collapsing it (XML 181-183), so the phase strip keeps its width.

### Cleanup candidates

- Battle-phase "Activate" sends the wrong action code. `ClientDuelState.buildBattleCmdActions` (651-661) tags activatable cards with type 2, `ClickDispatcher.showContextMenu` passes it straight to `ResponseBuilder.selectCmd` (132), and the engine reads battle responses as 0 activate, 1 attack, 2 go to Main Phase 2, 3 end battle (`playerop.cpp:56-66`; `ResponseBuilder`'s own javadoc at 45-49 agrees). Clicking Activate in the battle phase either jumps to Main Phase 2 or draws `MSG_RETRY`. The idle-command codes 0 to 5 are correct. Fix: type 0 in `buildBattleCmdActions`, and `getActionIconInfo`'s battle branch (164-168) maps 0 to Activate.
- Duplicated `SelectPlace` bit logic (`FieldRenderer.getFieldBit` versus `ResponseValidator.zoneToBit`).
- Context-menu size literals mirrored from the LSS by hand.
- Seven prompts with no UI.
- `PromptController.java:294` TODO: card images for the position picker.
- Dead LSS: `#card-image-area` still points at `duelcraft:textures/test.png` (XML 396) with commented alternatives at 395 and 149.
- `opp-extra-deck` has no click handler.
- `isBattleCmd` is written only by the two command prompts and never reset; safe today because the menu opens only while one of them is pending.
- `docs/ldlib2-ui-guide.md` and `docs/ui-wiring-guide.md` describe the skeleton-era UI: `getElementById`, card codes as text, a `DuelUIBuilder`/`DuelFieldRenderer`/`DuelPromptBuilder` split that was never built, `sel.cancelable() != 0` on a boolean. Rewrite or delete before anyone reads them as current.
- ESC and the win overlay bypass `LDLibDuelScreen.close()`.

### Check yourself

- A Speed Duel opponent sets a card in their S/T zone 1: which XML id does it land in, and what class flips it? (`opp-st-1` through `slotId`; `#opponent-side .card { transform: rotate(180) }`.)
- Why does `SelectTribute` never show a scroller? (Tributes are on the field by definition, so field-selection mode always applies.)
- The context menu opens at the wrong offset only at GUI scale 3: which two transforms would you check first? (The canvas scale and the root centering offset in `showContextMenu`; the click scenario in the harness pins both.)

## Stop 7: card data

Two copies of `cards.cdb` exist and they never meet. The engine, in C++, loads the `datas` table of whatever files `Config.CARD_DATABASE_PATHS` names, because the rules need types, levels, and setcodes. The client downloads BabelCDB's combined file into its cache and reads `datas` joined with `texts`, because the screen needs names and effect text. Neither side checks the other's version.

### Files

Client, `client/carddata/`:

| File | Lines | Owns |
|---|---|---|
| `CardDatabase.java` | 129 | sqlite-jdbc point queries with per-card and per-string caches |
| `CardDatabaseDownloader.java` | 70 | Downloads `cards.cdb` once; skips if a non-empty file exists |
| `CardImageManager.java` | 229 | Two image channels (art, full card), disk cache, async download, `DynamicTexture` registration |
| `CardInfo.java` | 47 | One `datas`+`texts` row as a record, with bit decoders for level, scales, link data |
| `CardStringHelper.java` | 133 | Attribute and race names, the type line, the ATK/DEF line |
| `OptionTextResolver.java` | 66 | Turns a prompt's 64-bit `desc` into text |
| `SystemStringTable.java` | 173 | Downloads, caches, and parses EDOPro's `strings.conf` |

Engine side, `native/jni-bridge/`: `card_database.{h,cpp}` (the `OCG_DataReader` callback over an in-memory map), `script_provider.{h,cpp}` (the `OCG_ScriptReader` callback over search paths), `duel_engine.{h,cpp}` (owns both, wires all callbacks), `jni_interface.cpp` (the `JNIEXPORT` functions matching `OcgCore`), `CMakeLists.txt`. Java side: `core/DuelEngine.java`, `core/NativeLoader.java`, `core/OcgCore.java`, `Config.java`.

### Client startup

`DuelcraftClient.initCardData` (36-67) runs on the common pool after `FMLClientSetupEvent`:

1. `cacheDir = <gameDir>/duelcraft/cache`.
2. `CardDatabaseDownloader.ensureDatabase(url, cacheDir)` writes `cards.cdb` unless a non-empty one exists (35-38). No checksum, no version check: a cached database is trusted forever.
3. `new CardDatabase(path)` opens one JDBC connection for the life of the client.
4. `new CardImageManager(baseUrl, cacheDir/images)` creates `images/cards/` and `images/art/`.
5. `new SystemStringTable(url, cacheDir)` writes `strings.conf`. This one re-downloads every startup and falls back to the cached copy on failure (61-84), the one self-updating cache in the pipeline.
6. A JVM shutdown hook closes the image manager and the connection.

Offline with no cache, step 2 throws, the outer catch logs, and all three statics stay null for the session. `LDLibDuelScreen` and `FieldRenderer` null-check them and fall back to "Card #<code>" style placeholders.

### Reading the database

`CardDatabase` force-loads `org.sqlite.JDBC` in a static initializer (22-28) because NeoForge's module classloader breaks `META-INF/services` discovery. `getCard` runs `SELECT d.id, t.name, t.desc, d.type, d.atk, d.def, d.level, d.race, d.attribute FROM datas d JOIN texts t ON d.id=t.id WHERE d.id=?` (30-34) and memoizes the `CardInfo` in a `ConcurrentHashMap`; `getCardString(code, offset)` selects column `str(offset+1)` and memoizes under an internal key `(code << 8) | offset` with a sentinel for known-missing strings (38-41, 73-78). Each uncached call prepares a fresh statement. The connection is opened without a read-only flag; the C++ side opens with `SQLITE_OPEN_READONLY`.

`CardInfo` decodes the packed `level` column: `levelOrRank()` is `level & 0xFF`, `leftScale()` bits 24-31, `rightScale()` bits 16-23. For Link monsters the `def` column holds the link-arrow bitmask, so `linkArrows()` returns `def` raw and `CardStringHelper.atkDefLine` prints "ATK x / Link n" without ever reading `def` as a stat (69-77). Get that wrong and a Link monster shows a DEF in the thousands. Nothing draws the arrows yet: `linkArrows()`, `leftScale()`, and `rightScale()` have no callers.

### Strings and prompt text

`SystemStringTable` parses `!system`, `!victory`, `!counter`, `!setname` lines into four maps (43-46, 133-161), splitting on the first two spaces so multi-word text survives; codes may be decimal or `0x` hex; unknown directives and unparsable codes are skipped on purpose.

`OptionTextResolver` splits a prompt `desc` at 2^20 (34-40). Below it, the value is a system string code. At or above it, the value is `(cardCode << 20) | offset`, Lua's `aux.Stringid(code, N)`, and offset N maps to the card's `str(N+1)` column; the full `desc` column is never used for options (10-14). Misses fall back to "<name> (effect N)", "Effect <code>#<offset>", or "System string #N"; the method never returns null. Do not confuse this 20-bit wire packing with `CardDatabase`'s internal 8-bit cache key; they are unrelated encodings that both say "offset".

### Images

Two channels share one machine: art (`cards_cropped/`, derived from the configured base URL by string replacement, line 76) and full card (`cards_small/`, the URL as configured). `getTexture` (103-133) returns a cached `ResourceLocation` at once, or adds the code to a loading set and submits to a two-thread daemon executor. On that thread: read the PNG from `<cache>/cards/<code>.png` or `art/<code>.png` if present, else download `<code>.jpg`, convert to PNG through `ImageIO` (204-214), write it, and hop to the render thread with `Minecraft.getInstance().execute` to build the `NativeImage`, `DynamicTexture`, and register it (139, 168). `NativeImage.read` accepts PNG only, which is why the disk cache is PNG and why `loadFromDisk` can feed raw bytes straight in.

While loading, `getTexture` returns null and the caller shows a card back (`LDLibDuelScreen.java:581-595`) and remembers the element in `pendingCardImages`; the texture-loaded callback marks all visuals dirty and retries. Failures go into a permanent per-code failed set (115): no retry, no backoff for the life of the process. A shared 50 ms throttle (180-187) spaces HTTP requests across both channels. There is no eviction; both caches grow until `close()`, which runs once from the shutdown hook and releases every texture from the `TextureManager`.

### The engine side

`DuelEngine.java` passes two `String[]` to `nCreateEngine`; `jni_interface.cpp:36-46` converts them and calls the C++ `DuelEngine::init`, which opens the databases and stores the script paths. A failure returns handle 0 and the Java constructor throws `IllegalStateException`; `DuelManager.init` does not catch it, so one bad `.cdb` path stops the server from starting.

`CardDatabase::open` requires every configured file to load and stops at the first failure (5-12). Each file is read whole, `SELECT id, alias, setcode, type, level, attribute, race, atk, def FROM datas` (25), into one `unordered_map`; later files overwrite earlier codes. It never touches `texts`. The 64-bit `setcode` becomes up to four `uint16_t` archetype codes plus a terminator (38-45). `level` is unpacked the same way as `CardInfo` except the engine masks level to `0xFFFF` where the client masks to `0xFF`. `TYPE_LINK` is the literal `0x4000000` (61), the same value as `OcgConstants.TYPE_LINK` but not derived from it. `cardReader` (79-99) copies the entry into the engine's `OCG_CardData` and hands out a raw pointer into the persistent `setcodes` vector, safe because the map never changes after load; `cardReaderDone` is a no-op for the same reason. An unknown code leaves the struct zeroed (81), so a card missing from the engine database plays as an all-zero card rather than an error.

`ScriptProvider::readFile` (28-46) tries each search path in order and returns the first full read. It caches nothing: every `OCG_ScriptReader` call re-reads the file, and a missing script rescans every directory on every request. `DuelEngine::createDuel` loads `constant.lua` and `utility.lua` right after `OCG_CreateDuel` (70-71); they must live in one of the configured paths. All four callback slots are set per duel (43-53) and `enableUnsafeLibraries = 0` is hardcoded there.

The log handler is unfinished on both sides. `DuelEngine::logHandler` prints `[ocgcore] <message>` to stderr with a comment "Java log forwarding will be added later" (156-160), and `OcgCore.java:12` has `nSetLogHandler` commented out. Engine log lines never reach the Minecraft log.

`nGetVersion` (`jni_interface.cpp:201-208`) constructs a whole stack `DuelEngine` to call a static `OCG_GetVersion`. `CMakeLists.txt` builds sqlite3 from the amalgamation, imports the MSBuild-built `ocgcore` as a shared import target, and links both into `duelcraft_jni`.

### Config

One `ModConfigSpec`, type COMMON (`Duelcraft.java:94`):

| Key | Default | Read by |
|---|---|---|
| `dbPaths` | `["C:/ProjectIgnis/expansions/cards.cdb"]` | server, engine databases |
| `scriptPaths` | `["C:/ProjectIgnis/script", "C:/ProjectIgnis/script/official"]` | server, engine scripts; always separate entries, never `;`-joined |
| `cardDatabaseUrl` | BabelCDB raw GitHub URL | client |
| `cardImageBaseUrl` | ygoprodeck `cards_small/` | client |
| `stringsConfUrl` | ProjectIgnis distribution `strings.conf` | client |
| `logDirtBlock`, `magicNumber`, `magicNumberIntroduction` | template values | `Duelcraft.java:101, 105`, MDK leftovers |

The three URLs are client-only in practice but live in the common spec. No `config/duelcraft-common.toml` is checked in; NeoForge generates one under `run/config/` from the spec, and CLAUDE.md's reference to the file means that generated copy.

### Things to notice

- The thick bridge shows up at the SQL level: the engine's query has no `texts` join, and the server's Java never sees a card name.
- Caching is inconsistent by layer: `CardDatabase` memoizes with a missing-sentinel, `CardImageManager` memoizes with a failed set, `ScriptProvider` memoizes nothing.
- Failure modes are asymmetric: the client degrades to placeholders, the engine refuses to start on a bad database path and silently zero-fills an unknown card.
- `CardDatabase` has one JDBC connection and no synchronization. Every current caller is on the render thread. Check before calling it from a background thread.

### Cleanup candidates

- Finish or remove the log handler on both sides (`OcgCore.java:12`, `duel_engine.cpp:156-160`).
- `CardInfo.linkArrows`, `leftScale`, `rightScale` have no callers.
- `initCardData` wraps three independent steps in one try/catch; a database failure needlessly skips images and strings.
- `DuelcraftClient.java:60` swallows the close exception with a comment.
- `ScriptProvider` re-reads scripts on every request.
- `nGetVersion` builds an engine to call a static.
- `TYPE_LINK` duplicated as a literal in C++.
- Template config fields.

### Check yourself

- A card exists in the client's BabelCDB but not in the server's `cards.cdb`: what does each side do when it is drawn? (Client shows name and art; the engine plays it as a zeroed card.)
- Why does the image pipeline convert to PNG on the download thread rather than the render thread? (`ImageIO` is slow and thread-safe; `NativeImage` and GL registration must be on the render thread.)
- Which `desc` values does `OptionTextResolver` treat as system strings, and what does Lua write for "Activate this card's effect?" style options? (Below 2^20; `aux.Stringid(code, n)` packs the card code above the offset.)

## Stop 8: build, tests, and CI

### Files

| File | Lines | Owns |
|---|---|---|
| `build.gradle` | 339 | Plugins, the native Exec tasks, `neoForge.runs`, dependencies, the `test` task, datagen |
| `gradle/ldlib2-uitest.gradle` | 89 | Wires `-PldTest` into the `client` run and verifies the harness report |
| `gradle.properties` | 37 | Versions, mod metadata, `org.gradle.configuration-cache=true` |
| `settings.gradle` | 9 | Plugin repositories and the foojay toolchain resolver |
| `.github/workflows/build.yml` | 29 | One Ubuntu job: checkout, JDK 21, `./gradlew build` |
| `native/jni-bridge/CMakeLists.txt` | 41 | sqlite3 static lib, imported `ocgcore`, the `duelcraft_jni` target |
| `src/test/java/**` | 2755 | 12 JUnit 5 classes, 183 tests |
| `client/uitest/**` | 452 | The LDLib2 in-client scenario harness |

### The build graph

```
buildOcgcore      Exec: MSBuild ocgcoreshared.vcxproj, Release x64        build.gradle:77-93
   |  out: native/ygopro-core/bin/x64/release/ocgcore.dll
configureJniBridge  Exec: cmake -G "Visual Studio 17 2022" -A x64         113-124
buildJniBridge      Exec: cmake --build . --config Release                129-143
   |  out: native/jni-bridge/build/Release/duelcraft_jni.dll
copyNative          Copy both DLLs -> src/main/resources/natives/windows-x86_64/   153-160
   |
processResources.dependsOn(copyNative)   163-165        test.dependsOn(copyNative)   281
   v                                                     v
jar -> assemble -> build <- check <- test
```

`findMSBuild()` and `findCMake()` (40-67) search four hardcoded Visual Studio 2022 roots at configuration time and fall back to the bare command names. The three native tasks declare inputs and outputs so Gradle skips them when nothing changed; `cleanOcgcore` and `cleanJniBridge` do not and always run. `jniBridgeBuildDir.mkdirs()` at line 127 runs on every configuration pass, not inside a task.

Run configurations (186-238): `client`, `client2` (adds `--username Player2`), `server` (`--nogui`), `gameTestServer`, `data`, all with `DEBUG` logging and the `REGISTRIES` marker. The harness script patches only `client` (`ldlib2-uitest.gradle:25`), so `-PldTest` does nothing for `client2` or `server`.

The `test` task (279-290) uses the JUnit platform, depends on `copyNative`, puts the natives directory on `java.library.path`, and sets two system properties to fixed Windows paths: `duelcraft.test.dbPath = C:/ProjectIgnis/expansions/cards.cdb` and `duelcraft.test.scriptPaths = C:/ProjectIgnis/script;C:/ProjectIgnis/script/official` (the test splits that string on `;`, `OcgCoreTest.java:128`). There is no `testLogging` block.

`sqlite-jdbc` appears three times (262-276): `implementation` for compile and dev runtime, `additionalRuntimeClasspath` so ModDevGradle puts it on the game classpath, and `jarJar` so the production jar bundles it. Drop any one and something breaks in one environment only.

The configuration cache is on for the whole project (`gradle.properties:6`). `ldlib2-uitest.gradle:17-20` captures `project.hasProperty('ldTest')` into a plain boolean at configuration time because a closure evaluated later has no live `Project`. Keep that pattern when you add Gradle logic.

### Test inventory

| Class | Tests | Covers | Native | EDOPro data |
|---|---|---|---|---|
| `OcgCoreTest` | 8 | JNI lifecycle end to end: version, create, add cards, start, process, respond, query, destroy and recreate; ordered 1-8 | yes | yes, both paths |
| `CardDatabaseTest` | 5 | Lookups against the real `cards.cdb` | no | yes, database only |
| `MessageParserTest` | 43 | Hand-built byte buffers for about 35 message types plus multi-message, unknown, and empty buffers | no | no |
| `ResponseValidatorTest` | 42 | Valid and invalid responses for every prompt type | no | no |
| `ResponseBuilderTest` | 25 | Encoded response layouts, decoded back with local readers | no | no |
| `FieldLayoutTest` | 14 | Preset to geometry, EMZ cross-mapping, MR3 pendulum, Speed columns, slot round trips | no | no |
| `CardStringHelperTest` | 16 | Type lines and ATK/DEF lines for every card kind | no | no |
| `DeckLoaderTest` | 8 | `.ydk` parsing, error line numbers, BOM | no | no |
| `DeckRegistryTest` | 7 | Directory listing and loading with `@TempDir` | no | no |
| `DuelRuleTest` | 6 | Ids, parsing, flags match engine presets | no | no |
| `DeckShuffleTest` | 5 | Determinism, divergence, element preservation | no | no |
| `SeedExpanderTest` | 4 | Four-long expansion properties | no | no |

Ten of twelve classes need nothing but the JVM. The two data-dependent classes guard their system properties with `assertNotNull`, which can never fire because `build.gradle` always sets the properties; on a machine without `C:/ProjectIgnis` they fail with a native-load or file-not-found exception rather than skipping. No test uses `assumeTrue` or `@EnabledIf...`.

Coverage gaps worth naming: `DuelMessageCodec` (701 lines, the server-to-client wire format) has no test and no round-trip check; `FieldQuery` gets its first tests in Task 2 of the cleanup branch.

### The UI harness

`./gradlew runClient -PldTest=group:duelcraft` runs all six scenarios; `-PldTest=<name>` runs one; `-PldTestKeepOpen` leaves the client up. Each scenario class carries `@LDLRegisterClient(name, group = "duelcraft", registry = UIScenario.REGISTRY, environment = DEV_ONLY)`, so production registration never sees them. The runner itself lives in LDLib2.

`DuelScreenScenario` (128 lines) takes a `DuelRule` and a GUI scale, opens `LDLibDuelScreen` with `DuelScreenFixture.startPayload`, populates the board with synthetic `Draw` and `Move` messages (no server, engine, database, or images), waits two ticks, runs the shared checks, then the subclass's `ruleChecks`, screenshots, and tears down. The shared checks: canvas and both hands inside the viewport; hands span their side row; banished piles continue the graveyard column and sit outside the zone grid; both grids share a left edge; the field area fits inside the canvas. The scenarios: MR3 at scale 3, MR5 at scales 2, 3, and 4, Speed at scale 3, and an MR5 scale 3 click scenario that sends a reposition prompt, clicks the card, and asserts the context menu opens within 2 px of the card center (the tolerance comes from `showContextMenu` truncating to whole design pixels). Reports land in `build/ldlib2-uitest/report.json` and `report.txt`, screenshots under `screenshots/<scenario>/`. `verifyUiTest` fails the build when the report is missing or not `PASS`, and the output directory is wiped before each run so a crashed client cannot pass on a stale report. None of this runs in CI.

### Why CI fails

`.github/workflows/build.yml` runs `./gradlew build` on `ubuntu-latest` with Temurin 21. Three independent causes, in the order the build hits them:

1. The checkout has no `submodules:` input, so `native/ygopro-core/` is empty on the runner.
2. `processResources` and `test` both depend on `copyNative`, and `buildOcgcore` execs `MSBuild.exe`, which does not exist on Linux. The task fails before anything compiles.
3. `configureJniBridge` hardcodes the `Visual Studio 17 2022` generator, unavailable on Linux even with cmake installed.

Behind those, the test properties point at `C:/ProjectIgnis`, so even a native-free run would fail `OcgCoreTest` and `CardDatabaseTest` with exceptions instead of skips. The roadmap (`docs/jni-integration-roadmap.md:73-79, 102-108`) planned a premake/gmake Linux build for ocgcore; nobody implemented it, and `CMakeLists.txt`'s `libocgcore.so` branch is unreachable from any wired task.

The minimal fix has two parts, decided at Stop 1 and not yet scheduled:

- A Gradle property, say `-PskipNative`, that removes `copyNative` from both dependency chains so Ubuntu can compile and run the ten pure tests; the workflow passes it.
- `assumeTrue(Files.exists(...))` in the two data-dependent `@BeforeAll` methods so they skip cleanly wherever the EDOPro data is absent.

A Windows job that runs the native build and the full suite is the optional third part.

### Things to notice

- Only `OcgCoreTest` exercises the real duel loop, because the engine-side data lives in C++. Everything else tests byte layouts and pure logic against hand-built inputs, which is why it needs no data.
- The harness runs inside a real client because `insideViewport` reads `Minecraft.getInstance().getWindow()` and the checks measure LDLib2's actual layout pass; no headless substitute exists.
- The natives directory is gitignored (`.gitignore:36`); the jar gets its DLLs only through `copyNative`.
- The click scenario exists to catch a coordinate-transform regression that the plain layout scenarios could not see.

### Cleanup candidates

- `docs/engine-implementation-checklist.md:83` still says "QueryParser, 123 tests pass"; the count is 183 across 12 classes and the class is gone. CLAUDE.md's package layout and test list are fixed in your local copy by Task 4 of the cleanup branch (the file is gitignored); the checklist line is not.
- No codec round-trip test.
- `assertNotNull` where an assumption belongs.
- Configuration-time `mkdirs()`.
- No `testLogging`; add `events "failed"` with `exceptionFormat "full"` if you want failures in the console.

### Check yourself

- Which Gradle task first fails on Ubuntu, and would fixing the submodule checkout alone change that? (`buildOcgcore`; no, MSBuild is still missing.)
- Why does `-PldTest` need `tasks.configureEach` matching by name instead of `tasks.named('runClient')`? (ModDevGradle creates the run tasks after this script evaluates.)
- What would a `DuelMessageCodec` round-trip test look like, given the parser tests already build byte buffers? (Build a record, encode to a `FriendlyByteBuf`, decode, assert equality; the records are Java records, so `equals` is free.)

## Consolidated cleanup list

Everything the walkthrough turned up, grouped by area. "Branch" means the `message-layer-cleanup` branch fixes it; "decide" means you owe a decision; "open" means known and unscheduled.

### Correctness

| Item | Where | Status |
|---|---|---|
| `QUERY_IS_PUBLIC` read as 4 bytes; sanitizer never strips face-down cards; Link stats misaligned | `FieldQuery` | branch, Task 2 |
| `MSG_SELECT_CHAIN` entries read as 19 bytes instead of 23 | `MessageParser.parseSelectChain` | branch, Task 3 |
| `MSG_SHUFFLE_EXTRA` under-read; `MSG_PLAYER_HINT` parsed with the `MSG_HINT` layout | `MessageParser` | branch, Task 3 |
| Identical decks gave identical hands | `DuelManager` | done, `2be7e70` |
| `/duel forfeit` sends no `DuelEndPayload`; opponent freezes | `DuelManager.endDuel` | open, known issue |
| No disconnect handling | server | open, roadmap deferral |
| `MSG_RETRY` hides the prompt because `sendResponse` cleared it first | `ClientDuelState`, `PromptController.rebuild` | open; ties to the `ResponseValidator` decision |
| Player 2's `DuelStartPayload` carries player 1's deck sizes | `DuelManager.startDuel` | open, verify visibility first |
| Solo AI has no `AnnounceCard` case though it is routed to the AI | `SoloDuelHandler.buildAutoResponse` | open |
| `SoloDuelHandler` never sanitizes; the human's client receives the AI's hidden codes | `SoloDuelHandler` | open, test-mode only |
| Engine log handler unfinished on both sides | `OcgCore`, `duel_engine.cpp` | decide |
| Battle-phase "Activate" sends action code 2 (Main Phase 2) instead of 0 | `ClientDuelState.buildBattleCmdActions`, `ClickDispatcher.getActionIconInfo` | open, verified against `playerop.cpp:56-66`; one-line fix plus the icon map |
| ESC and the win overlay close the screen without `LDLibDuelScreen.close()`; statics linger | `DuelScreen`, `PromptController.showWinOverlay` | open, known issue |
| Seven prompt types show a buttonless overlay: `SelectCounter`, `SortCard`, `SortChain`, `Announce*` | `PromptController` | open, Tier 2 in the checklist |

### Dead or stale code

| Item | Where | Status |
|---|---|---|
| `QueryParser`, `QueryParserTest`, the two parser cases | message layer | done, `d99885b` |
| `Deck.standard()` fallback | `Deck`, `DuelManager`, fixture | done, `2be7e70` |
| "No current deck (using standard)" and the `accept` error that names nobody | `DuelCommand` | branch, Task 4 |
| CLAUDE.md names `QueryParser`, `QueryParserTest`, the fallback | `CLAUDE.md` (gitignored, local file) | fixed locally by Task 4; the file is never committed |
| `DuelMessage.UpdateCard` and its codec, client, and sanitizer arms; nothing produces it | message layer, client, server | decide |
| `ResponseValidator`: 42 tests, zero callers | `duel/response` | decide at Stop 6 |
| `DuelSession.queryCount/query/queryLocation/queryField`: no production callers | `DuelSession` | decide (check `OcgCoreTest` first) |
| `DirtyFlag.LP`, `winReason`, `lastAction` | `ClientDuelState` | open |
| `CardInfo.linkArrows/leftScale/rightScale`: no callers | `client/carddata` | open |
| Redundant loop and in-progress comments in `handleSoloAutoResponse` | `DuelManager` | open |
| `MessageParser.readActivatableList` has no callers; the idle command inlines its own loop | `MessageParser` | open |
| MDK template leftovers: example block, item, creative tab, magic-number config, dirt logging, "HELLO from server starting" | `Duelcraft`, `Config` | open |
| `docs/engine-implementation-checklist.md:83` test count and class name | docs | open |
| `docs/jni-integration-roadmap.md` and the April plan and spec still describe `QueryParser` | docs | leave, historical |
| `docs/ldlib2-ui-guide.md`, `docs/ui-wiring-guide.md` describe the skeleton-era UI | docs | decide: rewrite or delete |
| Dead LSS: `test.png` background, two commented rules | `duel_screen.xml:149, 395-396` | open |
| `opp-extra-deck` has no click handler | `ZoneInspectorController` | open |

### Hardening and structure

| Item | Where | Status |
|---|---|---|
| CI: `-PskipNative` plus `assumeTrue` on data paths, optional Windows job | `build.gradle`, tests, workflow | decided at Stop 1, unscheduled |
| `DuelMessageCodec` has no round-trip test | tests | open |
| No test parses a live engine query buffer; one that sets up a `DuelSession`, queries a face-down deck card with `QUERY_IS_PUBLIC`, and asserts `FieldQuery.parse` reads it false would have caught the u8 bug | tests | recommended by the final review |
| `ScriptProvider` caches nothing | C++ | open |
| `initCardData` one try/catch over three independent steps | `DuelcraftClient` | open |
| `DuelEventListener.onMessage` returns 0/1/2 ints; an enum would read better | `duel` | open, low |
| `duelInvites` public with a FIXME and no expiry | `DuelManager` | open |
| No permission predicate on `/duel` | `DuelCommand` | decide |
| Test cross-checking `OcgConstants` against `common.h` | tests | idea |
| Deck passcode validation against the server database at `/duel deck set` | server | idea |
| `TYPE_LINK` literal in C++ duplicates `OcgConstants` | C++ | open, low |
| `SelectPlace` zone-to-bit logic exists twice (`FieldRenderer.getFieldBit`, `ResponseValidator.zoneToBit`) | client, `duel/response` | fold into the `ResponseValidator` decision |
| Context-menu size literals mirror the LSS by hand | `ClickDispatcher.showContextMenu` | open, low |

### Product gaps from the manual pass

Win and lose overlay that must be dismissed, a concede button, and reopening the screen after ESC (`/duel show` or blocking ESC). All three sit on top of the forfeit fix, because a client cannot show a result it never receives.

## Suggested order after the branch merges

0. The battle-phase Activate code: one constant in `buildBattleCmdActions` and one icon case in `ClickDispatcher`, then a solo duel to confirm an effect activates during battle.
1. Forfeit and disconnect: make every server-side end send `DuelEndPayload`, add a logout listener, then build the result overlay and concede button on top.
2. CI: `-PskipNative`, `assumeTrue`, green Ubuntu job.
3. `ResponseValidator` decision, together with the `MSG_RETRY` behavior; whichever way you go, the prompt must survive a rejected response.
4. The dead-code decisions (`UpdateCard`, the query wrappers, the client fields) in one small commit each.
5. A codec round-trip test before the next batch of new message types.

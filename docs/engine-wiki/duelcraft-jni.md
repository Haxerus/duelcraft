# Embedding ygopro-core through JNI: Duelcraft

This page is a source guide for agents working on a host around ygopro-core. It describes the repository snapshot, then extracts practices that apply to other hosts. Treat statements marked **Current** as Duelcraft behavior, **Upstream** as behavior proven by the vendored ygopro-core snapshot, and **Recommendation** as design advice rather than an API guarantee.

## Boundary at a glance

**Host selection update (2026-09-19, M4):** [`DuelClientCommand`](../../src/main/java/com/haxerus/duelcraft/client/DuelClientCommand.java) imports local Main/Extra/Side through acknowledged collection Save followed by Activate. No raw upload or session-only selection map remains. [`DuelManager`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java) resolves the authenticated player's active `SavedDeck` attachment through shared `DeckUsePolicy`. [`DuelNetworking`](../../src/main/java/com/haxerus/duelcraft/server/DuelNetworking.java) registers **protocol 7**, nine payload types including typed preparation request/state. Player files stay local; explicitly named AI decks still use the server registry. See [multiplayer deck setup](../multiplayer-decks.md).

The core SERVER setting [`ServerConfig.requireCardOwnership`](../../src/main/java/com/haxerus/duelcraft/ServerConfig.java) defaults false and is captured once per server lifecycle; changing it requires restart. When enabled, deposited exact-passcode counts must cover Main, Extra and Side. Local imports grant no copies. Supported legality and [`DeckUseCheckEvent`](../../src/main/java/com/haxerus/duelcraft/api/DeckUseCheckEvent.java) companion denials apply under both ownership modes. Hook approval cannot override core restrictions; hook exceptions deny use with a generic explanation and preserve recoverable saved data.

Saved counts/lists/activation persist across reconnect and restart. Login revalidates active selection against current policy: ineligibility clears only activation with one revision increment and a private explanation; removing a restriction never auto-reactivates. A failed evaluation keeps the attachment unchanged. [`DuelPreparationService`](../../src/main/java/com/haxerus/duelcraft/server/DuelPreparationService.java) rechecks both players at acceptance, holds locks through RPS/first choice/startup, and supplies immutable prepared selections. The manager rechecks identity, contents, online state, ownership and companion eligibility immediately before native creation. Solo uses the same policy with a startup guard; server AI lists require no player ownership. `core.Deck` remains the simulation input; Side is retained and validated by the host but is not inserted into the duel.

Collection commands and packets reject mutations while preparing, starting or live. Terminal cancellation/timeout/start failure/end/disconnect release ownership; failed eligibility at Accept retains an editable invitation. Challenger and accepter retain shuffle seeds `seed` and `seed + 1` independently of first-seat choice. Start packets precede `setupDuel` because setup emits Extra refresh messages. Startup rollback closes an allocated session once, removes newly installed session/player/seat/AI mappings and sends terminal `START_FAILED` to restore management routing without a fake win result. Runtime fixtures wrap an actual JNI session to exercise post-setup failure and delegated close; this is cleanup-path evidence, not an independent native leak measurement.

```text
Minecraft client UI
  -> typed response payload
Minecraft server / DuelSession
  -> little-endian byte[]
OcgCore JNI declarations
  -> JNI copies and pointer-shaped long handles
DuelEngine C++ bridge
  -> ygopro-core C API
ygopro-core duel + Lua state
```

**Current.** One server-owned [`DuelEngine`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L29) owns the native engine handle. Each [`DuelSession`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L16) owns one native duel handle. The C++ engine owns the card table, script search paths, and a map of live duel contexts in [`DuelEngine`](../../native/jni-bridge/include/duel_engine.h#L51). ygopro-core owns the duel state, Lua interpreter, cards, groups, effects, message buffer, and query buffer; its destructor releases those objects in [`duel::~duel`](../../native/ygopro-core/duel.cpp#L28).

The split keeps simulation card lookups and script reads in C++, but it does not mean Java performs no SQLite or network I/O. The client presentation path downloads a separate database in [`DuelcraftClient.initCardData`](../../src/main/java/com/haxerus/duelcraft/DuelcraftClient.java#L36) and queries it through JDBC in [`client.carddata.CardDatabase`](../../src/main/java/com/haxerus/duelcraft/client/carddata/CardDatabase.java#L15).

## Ownership and lifetime

| Object | Owner | Created | Released | Constraint |
|---|---|---|---|---|
| `DuelEngine*` | Java `DuelEngine` wrapper | [`nCreateEngine`](../../native/jni-bridge/src/jni_interface.cpp#L35) uses `new` | [`nDestroyEngine`](../../native/jni-bridge/src/jni_interface.cpp#L48) calls `shutdown` and `delete` | Must outlive every duel made from it. |
| Card table and script paths | Native `DuelEngine` members | [`DuelEngine::init`](../../native/jni-bridge/src/duel_engine.cpp#L10) | [`DuelEngine::shutdown`](../../native/jni-bridge/src/duel_engine.cpp#L19) | Callback payloads point at these members. |
| `OCG_Duel` | Native `DuelEngine::contexts_` | [`DuelEngine::createDuel`](../../native/jni-bridge/src/duel_engine.cpp#L28) | [`destroyDuel`](../../native/jni-bridge/src/duel_engine.cpp#L76), or engine shutdown | Java stores the pointer value as a `long`. |
| Java session | `DuelManager.activeDuels` | [`startDuel`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L161) | [`endDuel`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L208) or server shutdown | Calls `nDestroyDuel` from `close`. |
| Core message/query bytes | The `duel`'s vectors | core process/query call | A later call may reuse or clear the vector | JNI copies before returning to Java. |

**Upstream.** `OCG_DuelGetMessage` returns an internal buffer and subsequent calls invalidate earlier data; the vendored API guide says hosts must copy it ([`README`, message buffer](../../native/ygopro-core/README.md#L92)). Query calls have the same invalidation rule ([`README`, queries](../../native/ygopro-core/README.md#L109)). `OCG_DuelSetResponse` copies the supplied response internally ([`duel::set_response`](../../native/ygopro-core/duel.cpp#L128)).

**Current.** The JNI helper [`nativeBufToJbyteArray`](../../native/jni-bridge/src/jni_interface.cpp#L24) makes the required copy. It maps both a null pointer and a zero-length buffer to Java `null`, so callers cannot distinguish “empty result” from “no result.” Response JNI obtains the Java bytes, calls the core synchronously, then releases without copying changes back to Java in [`nDuelSetResponse`](../../native/jni-bridge/src/jni_interface.cpp#L132).

`DuelEngine.close()` and `DuelSession.close()` are manual `AutoCloseable` methods ([engine](../../src/main/java/com/haxerus/duelcraft/core/DuelEngine.java#L20), [session](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L222)). `DuelSession.close()` is idempotent: it marks the session closed before destroying the native duel, so startup rollback and later cleanup cannot destroy it twice. Neither wrapper clears its final handle. `DuelEngine.close()` still has no repeated-close guard, so a second engine close can delete the same pointer twice; operations after either close can pass stale pointers. There is no `Cleaner` fallback.

**Recommendation.** Make Java ownership explicit: close sessions before the engine, make engine close idempotent, reject operations after close, and keep native validation strong enough that a stale or cross-engine duel handle cannot reach ygopro-core. A handle registry with generated IDs is safer than exposing raw addresses.

## Initialization, card data, and scripts

1. Referencing [`OcgCore`](../../src/main/java/com/haxerus/duelcraft/core/OcgCore.java#L3) runs `NativeLoader.load()`.
2. [`NativeLoader`](../../src/main/java/com/haxerus/duelcraft/core/NativeLoader.java#L29) tries `java.library.path`, then extracts the core library followed by the JNI library into a temporary directory.
3. Server startup constructs one engine from NeoForge config paths in [`DuelManager.init`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L53).
4. [`CardDatabase::open`](../../native/jni-bridge/src/card_database.cpp#L5) opens each configured SQLite database read-only and copies every `datas` row into memory. Later database files overwrite earlier entries with the same card code.
5. [`ScriptProvider::setSearchPaths`](../../native/jni-bridge/src/script_provider.cpp#L4) stores directories in priority order. [`readFile`](../../native/jni-bridge/src/script_provider.cpp#L28) returns the first nonempty matching file.
6. Duel creation fills `OCG_DuelOptions`, including four seed words, flags, team settings, callbacks, and callback payloads in [`DuelEngine::createDuel`](../../native/jni-bridge/src/duel_engine.cpp#L28).
7. The bridge sets `enableUnsafeLibraries = 0` before `OCG_CreateDuel` ([source](../../native/jni-bridge/src/duel_engine.cpp#L53)). It then requests `constant.lua` and `utility.lua` ([source](../../native/jni-bridge/src/duel_engine.cpp#L69)).
8. Java adds both decks and starts the state machine in [`DuelSession.setupDuel`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L37). Main decks are inserted in reverse list order because the core treats them as stacks.

The native card loader decodes packed set codes and pendulum scales, and maps a Link monster's database `def` value to link markers in [`CardDatabase::loadFromFile`](../../native/jni-bridge/src/card_database.cpp#L18). The callback zeros missing records, then points `OCG_CardData.setcodes` into the persistent native table ([`cardReader`](../../native/jni-bridge/src/card_database.cpp#L79)). ygopro-core copies scalar data and set codes into its per-duel cache before invoking `cardReaderDone` ([`duel::read_card`](../../native/ygopro-core/duel.cpp#L148)). This makes the bridge's no-op done callback valid for the current immutable table.

**Current limitations.** Engine initialization only reports a Boolean failure. It does not identify the bad database or SQLite error. A database step error after statement preparation is not checked before success. An empty path list fails, while an empty script list does not. Bootstrap script return values are ignored, so `createDuel` can return a live handle without either global script. Native core logs go to `stderr` in [`DuelEngine::logHandler`](../../native/jni-bridge/src/duel_engine.cpp#L156); the commented Java handler declaration is not implemented ([`OcgCore`](../../src/main/java/com/haxerus/duelcraft/core/OcgCore.java#L9)).

[`ScriptProvider::readFile`](../../native/jni-bridge/src/script_provider.cpp#L28) concatenates the core-supplied name with each directory. It does not canonicalize the result or enforce directory containment. Hosts that admit untrusted scripts should reject absolute paths and `..` traversal. A false unsafe-library flag removes Lua `io`, `dofile`, and `loadfile` in this upstream snapshot ([`interpreter::interpreter`](../../native/ygopro-core/interpreter.cpp#L60)); it does not prove that arbitrary card scripts are safe, bounded, or terminating.

## Processing, querying, and responses

**Current pipeline.** [`DuelSession.process`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L72) repeatedly:

1. calls `OCG_DuelProcess` through JNI;
2. copies the core message buffer into a Java `byte[]`;
3. parses length-prefixed core frames into typed messages with [`MessageParser.parse`](../../src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java#L14);
4. gives each message to the server listener;
5. stops on a prompt, retry, or duel end, otherwise continues while the core returns `CONTINUE`.

The core itself clears the prior message buffer, processes until it emits data or stops continuing, and returns an `OCG_DuelStatus` in [`OCG_DuelProcess`](../../native/ygopro-core/ocgapi.cpp#L111). The status values are `END`, `AWAITING`, and `CONTINUE` in [`OCG_DuelStatus`](../../native/ygopro-core/ocgapi_types.h#L30).

When processing pauses, `DuelSession.sendFieldStats` queries monster zones, spell/trap zones, and extra decks for both players ([source](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L116)). Each per-slot result uses little-endian size/flag/data blocks parsed by [`FieldQuery`](../../src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java#L8). These query results become synthetic `UpdateData` messages; they do not come from the native message stream.

Server-to-client traffic uses Duelcraft's typed codec, not raw ygopro-core frames. [`DuelMessageCodec`](../../src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java#L10) documents the parse/re-encode boundary, and [`DuelMessagePayload`](../../src/main/java/com/haxerus/duelcraft/server/DuelMessagePayload.java#L10) installs that codec. The client applies decoded objects directly in [`ClientPayloadHandler.handleMessage`](../../src/main/java/com/haxerus/duelcraft/client/ClientPayloadHandler.java#L21).

On input, the UI builds little-endian core response bytes with [`ResponseBuilder`](../../src/main/java/com/haxerus/duelcraft/duel/response/ResponseBuilder.java#L6), sends a [`DuelResponsePayload`](../../src/main/java/com/haxerus/duelcraft/server/DuelResponsePayload.java#L9), and clears its local prompt in [`LDLibDuelScreen.sendResponse`](../../src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java#L108). The server resolves the sender's active duel and calls `DuelSession.setResponse`, which submits bytes and resumes processing ([`DuelManager.handleResponse`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L199), [`DuelSession.setResponse`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L160)).

`MessageParser` contains two useful compatibility measures: unknown types become `Raw`, and every known parser is forced to the frame's declared endpoint after success or failure ([source](../../src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java#L125)). This preserves framing but cannot make a guessed message layout correct. An agent updating protocol support should verify the vendored core writer for each field width and signedness.

## JNI conventions and failure surface

**Current.** JNI uses two opaque `jlong` values. The engine value is a cast `DuelEngine*`; the duel value is a cast `OCG_Duel` pointer ([engine creation](../../native/jni-bridge/src/jni_interface.cpp#L35), [duel creation](../../native/jni-bridge/src/jni_interface.cpp#L60)). The Java declarations mirror every entry point in [`OcgCore`](../../src/main/java/com/haxerus/duelcraft/core/OcgCore.java#L9).

The bridge narrows Java `int` arguments to native `uint8_t` or `uint32_t` without range checks in [`nDuelNewCard`](../../native/jni-bridge/src/jni_interface.cpp#L88). `nCreateDuel` reads seed elements 0 through 3 without a null or length check ([source](../../native/jni-bridge/src/jni_interface.cpp#L60)). String arrays use `GetStringUTFChars` in [`jstringArrayToVector`](../../native/jni-bridge/src/jni_interface.cpp#L7), which also means narrow native paths and JNI modified UTF-8 must work with the host filesystem.

Most native methods check only whether the engine pointer is zero. They do not check whether the duel is live, belongs to that engine, or is in `contexts_`; only destruction searches the map. JNI allocation failures and pending exceptions from `NewByteArray`, `Get*ArrayElements`, or string conversion are not checked, and C++ exceptions are not translated into Java exceptions.

**Recommendation.** Validate array sizes, numeric domains, handle ownership, lifecycle state, and JNI exceptions before calling the core. Catch C++ exceptions at every exported JNI function and translate them to one documented Java exception type. Preserve zero-length arrays if the Java API needs to distinguish them from failures.

## Threading boundary

**Current.** All core calls are synchronous on whichever Java thread enters JNI. The bridge has no mutex around `contexts_`, `CardDatabase`, `ScriptProvider`, a duel, or its internal buffers. Java's `activeDuels` and `playerToDuel` are plain `HashMap` instances ([`DuelManager`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L29)), and `DuelSession` has no lock or thread assertion. [`ServerPayloadHandler`](../../src/main/java/com/haxerus/duelcraft/server/ServerPayloadHandler.java#L6) forwards the network callback directly; this repository does not explicitly enqueue it onto a duel executor.

The source therefore establishes an assumption, not a guarantee: lifecycle events, commands, and payload handlers must reach a given engine/session serially. The vendored C API exposes no thread-safety contract in [`ocgapi.h`](../../native/ygopro-core/ocgapi.h#L27).

**Recommendation.** Assign one serialized execution context to each duel, or one to the shared engine if callbacks share mutable engine resources. Route create, add-card, start, process, query, response, and destroy through it. Never call process and query concurrently on one duel because both expose mutable per-duel buffers.

## Host validation and privacy

**Current identity check.** NeoForge supplies the sender through `IPayloadContext`; [`ServerPayloadHandler.handleResponse`](../../src/main/java/com/haxerus/duelcraft/server/ServerPayloadHandler.java#L7) does not accept a client-claimed player ID. [`DuelManager.handleResponse`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L199) confirms only that this player belongs to an active duel.

The server does not track which player currently owes a response, compare a response with the outstanding prompt, impose an application-level response length cap, or invoke [`ResponseValidator`](../../src/main/java/com/haxerus/duelcraft/duel/response/ResponseValidator.java#L7). The current UI also calls `ResponseBuilder` directly. `ResponseValidator` is client-side-capable convenience code, not an enforcement boundary. A participant can therefore submit arbitrary bytes, submit while the opponent is prompted, or send repeated responses. ygopro-core may answer with `MSG_RETRY`, but that is protocol behavior rather than host authorization.

**Current privacy filter.** [`ServerDuelHandler.onMessage`](../../src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java#L28) sends selection prompts only to the named player. Other messages go through recipient-specific [`hideInfo`](../../src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java#L74), which masks opponent draw and hand-shuffle codes, some moves, sets, face-down position changes, and nonpublic queried cards. Query privacy depends on requesting `QUERY_IS_PUBLIC` in [`sendFieldStats`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L120); [`sanitizeCard`](../../src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java#L149) retains only flags and position for a nonpublic face-down opponent card.

The filter is message-type-specific and defaults to returning the original message. Unknown `Raw` frames and any newly implemented message type are broadcast unchanged unless a developer adds a privacy case. Some transitions rely on destination position and controller rather than a complete visibility policy. Treat this layer as incomplete defense-in-depth, not proof that the client cannot learn hidden state.

**Recommendation.** Keep the server authoritative. Store the outstanding prompt and expected player in the session; reject other senders, stale responses, oversized buffers, and encodings that fail prompt-specific validation. Build outbound views from a visibility policy that defaults to hiding unknown data. Add privacy tests for every message and query type before exposing it to clients, including parser failure and `Raw` fallback paths.

## Build and deployment facts

The native bridge compiles as C++17, links the vendored shared core and bundled SQLite amalgamation, and exports `duelcraft_jni` in [`CMakeLists.txt`](../../native/jni-bridge/CMakeLists.txt#L1). Gradle builds the Windows x64 core with Visual Studio, builds the bridge with CMake, copies both DLLs into resources, and makes `processResources` depend on that copy ([`build.gradle`](../../build.gradle#L37), [`copyNative`](../../build.gradle#L151)).

`NativeLoader` recognizes Linux x86-64 and looks for `.so` resources ([source](../../src/main/java/com/haxerus/duelcraft/core/NativeLoader.java#L38)), and CMake has a non-Windows imported-library branch ([source](../../native/jni-bridge/CMakeLists.txt#L17)). The Gradle pipeline shown here still creates and packages only the Windows x64 artifacts. Linux loading is scaffolded but not produced by this build.

## Task-to-file map

| Task | Start with | Key symbols |
|---|---|---|
| Add or change a JNI entry point | [`OcgCore.java`](../../src/main/java/com/haxerus/duelcraft/core/OcgCore.java#L9), [`jni_interface.cpp`](../../native/jni-bridge/src/jni_interface.cpp#L33) | `n*`, `Java_com_haxerus_duelcraft_core_OcgCore_*` |
| Change duel creation options or callbacks | [`duel_engine.cpp`](../../native/jni-bridge/src/duel_engine.cpp#L28), [`ocgapi_types.h`](../../native/ygopro-core/ocgapi_types.h#L64) | `DuelEngine::createDuel`, `OCG_DuelOptions` |
| Change database precedence or card conversion | [`card_database.cpp`](../../native/jni-bridge/src/card_database.cpp#L5) | `open`, `loadFromFile`, `cardReader` |
| Change script resolution | [`script_provider.cpp`](../../native/jni-bridge/src/script_provider.cpp#L8) | `scriptReader`, `loadScript`, `readFile` |
| Change the process loop or field snapshots | [`DuelSession.java`](../../src/main/java/com/haxerus/duelcraft/duel/DuelSession.java#L67) | `process`, `sendFieldStats`, `setResponse` |
| Decode core messages | [`MessageParser.java`](../../src/main/java/com/haxerus/duelcraft/duel/message/MessageParser.java#L14) | `parse`, per-message parsers |
| Decode query blocks | [`FieldQuery.java`](../../src/main/java/com/haxerus/duelcraft/duel/message/FieldQuery.java#L20) | `parse`, `readField` |
| Encode core responses | [`ResponseBuilder.java`](../../src/main/java/com/haxerus/duelcraft/duel/response/ResponseBuilder.java#L39) | prompt-specific factories |
| Change network schema | [`DuelMessageCodec.java`](../../src/main/java/com/haxerus/duelcraft/duel/message/DuelMessageCodec.java#L20), [`DuelNetworking.java`](../../src/main/java/com/haxerus/duelcraft/server/DuelNetworking.java#L7) | `encode`, `decode`, `onRegisterPayloads` |
| Enforce sender and prompt rules | [`ServerPayloadHandler.java`](../../src/main/java/com/haxerus/duelcraft/server/ServerPayloadHandler.java#L6), [`DuelManager.java`](../../src/main/java/com/haxerus/duelcraft/server/DuelManager.java#L199) | `handleResponse` |
| Audit hidden information | [`ServerDuelHandler.java`](../../src/main/java/com/haxerus/duelcraft/server/ServerDuelHandler.java#L69) | `sendToPlayer`, `broadcastToBoth`, `hideInfo`, `sanitizeCard` |
| Change native packaging | [`build.gradle`](../../build.gradle#L37), [`NativeLoader.java`](../../src/main/java/com/haxerus/duelcraft/core/NativeLoader.java#L15) | native tasks, `load` |

## Snapshot cautions for future agents

- Do not describe network delivery as raw-frame forwarding. The current server parses native frames and sends typed messages through its own codec.
- Do not say Java never uses SQLite or performs card-data I/O. That remains true for the server simulation path; the client presentation path uses JDBC and downloads cache data.
- Do not infer thread safety from NeoForge registration. This code contains no explicit serialization or native locking.
- Do not treat `enableUnsafeLibraries = 0` as a complete script sandbox.
- Do not treat `ResponseValidator` as server validation until the receive path calls it against server-owned prompt state.
- Recheck line links and behavior after updating the ygopro-core submodule; message layouts and API behavior belong to the pinned snapshot.

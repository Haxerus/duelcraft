# ygopro-core integration wiki

An agent-oriented reference for embedding the Yu-Gi-Oh! simulator into a native application, server, game, or foreign-language runtime. EDOPro supplies a complete host example; Duelcraft supplies a C++/JNI/Java example.

**Engine baseline:** `native/ygopro-core` at `7471af3c`, API **11.0**. **EDOPro baseline:** `../edopro` at `48ec006c`, with a different core revision. Read [source versions](source-versions.md) before using these contracts with another checkout. Facts below come from the local sources, not from an assumed upstream protocol version.

## Task router

Read this index and the page for your task. Open cited source only to verify a proposed change or investigate a gap; a full repository scan is not the default.

| You need to… | Read | Main source symbols / search terms |
|---|---|---|
| Understand the system boundary and locate internals | [Architecture](architecture.md) | `duel`, `field`, `card`, `effect`, `Processors`, `interpreter` |
| Build/load a core library or upgrade versions | [Build and compatibility](build-and-compatibility.md) | Premake, Lua stack unwinding, `OCG_GetVersion` |
| Write a C/FFI/JNI adapter and pump a duel | [Core API](core-api.md) | `OCG_CreateDuel`, `OCG_DuelProcess`, callbacks, buffers |
| Provide card data and Lua scripts | [Data and scripts](data-and-scripts.md) | `OCG_CardData`, `.cdb`, `setcodes`, `ScriptReader`, base scripts |
| Configure rules, deck ordering, teams or a replay seed | [Rules and coordinates](rules-and-coordinates.md) | `DUEL_MODE_*`, `OCG_NewCardInfo`, sequence, `seed[4]` |
| Parse raw engine message batches | [Protocol](protocol.md) | `generate_buffer`, `MSG_*`, `loc_info`, frame length |
| Find an individual message's producer and consumer | [Message index](message-index.md) | Every public `MSG_*` constant |
| Encode a UI/bot answer, handle cancel or retry | [Responses](responses.md) | `Select*`, `Announce*`, `returns`, `MSG_RETRY` |
| Query stats or reconstruct a display model | [Queries](queries.md) | `QUERY_*`, `card::get_infos`, `OCG_DuelQuery*` |
| Reuse EDOPro's host policy and privacy handling | [EDOPro host](edopro-host.md) | `GenericDuel`, `Analyze`, refreshes, `STOC_GAME_MSG`, replay |
| Work on this Minecraft/JNI integration | [Duelcraft JNI](duelcraft-jni.md) | Native engine, `OcgCore`, `DuelSession`, server/client handlers |
| Implement/test an embedding or diagnose a symptom | [Integration recipes](integration-recipes.md) | Acceptance checks, fixtures, symptom router |
| Check freshness or maintain the wiki | [Source versions](source-versions.md), [manifest](sources.json) | Commits, file fingerprints, `verify_wiki.py` |

## Invariants to retain across tasks

| Contract | Detail |
|---|---|
| The host supplies data and policy | Core callbacks consume card records and script bytes. Deck files, identity, networking, rendering and visibility filtering belong to the host. |
| Lifecycle is create → setup → start → pump → respond/pump → destroy | Process is synchronous. Awaiting requires a host response before the next process call. |
| Drain every process result | Output may accompany continue, awaiting or end. Get-message does not consume old output; process clears it. |
| Native output is borrowed | Copy message/query bytes before reuse; queries share one query buffer per duel. |
| Native struct layout is not wire layout | ABI padding/alignment differs from field-by-field serialized messages. |
| Modern core batches have frame lengths | Each core frame has a `u32` payload length followed by `u8 MSG_*` and fields. EDOPro transport is a separate envelope. |
| Widths matter | A serialized `loc_info` is 10 bytes; options, race and many descriptions are 64-bit. Per-message exceptions remain in the protocol reference. |
| A response is prompt-specific | Preserve the exact ordered choice list and constraints; do not substitute card codes, UI indices or guessed legacy encodings. |
| Raw engine output is authoritative private state | Filter recipient views before network delivery. Query controller is a card address, not an authorization identity. |
| A seed alone is insufficient for replay | Retain core/Lua, data/scripts, options, insertion order and responses. Queries are not restorable snapshots. |
| Serialize access to a duel | This is the recommended host ownership model; the C API does not provide async operations or per-duel locking. |
| Evidence is revision-specific | Headers, producer code and response validators take precedence over old prose and unrelated client branches. |

Each row is expanded and source-linked in the owning page above. For a new embedding, read **Architecture → Build → Core API → Data and scripts → Protocol/Responses/Queries → Integration recipes**. Read the EDOPro and Duelcraft pages when choosing host policies or adapting their implementations.

## Agent retrieval contract

- Keep symbol names and widths exact when implementing from this wiki. Statements labeled recommendation/recipe describe host design choices.
- Check [source versions](source-versions.md) or run `python docs/engine-wiki/verify_wiki.py` when the source tree may have changed. The check reports stale cited sources; it does not prove protocol semantics.
- For a new or changed message, follow its index entry to the producer **and** consumer/response validator. Record the resolved contract here so the next task can reuse it.
- Treat EDOPro as a reference host, and Duelcraft as the current local implementation. Neither host's omissions limit the generic engine API.
- If required behavior remains ambiguous after the relevant source checks, state the ambiguity and ask the developer before choosing behavior.

[llms.txt](llms.txt) provides a compact ingestion list. The wiki is plain Markdown with relative source links; it needs no hosting, database or retrieval framework.

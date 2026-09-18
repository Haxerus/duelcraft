# EDOPro as a host integration reference

Use this page when designing a server, game client, bot runner, or other host around ygopro-core. EDOPro supplies a mature reference host, but its application protocol is not part of the core API. Scope: EDOPro `48ec006c` with vendored core `158aebe7`; core contracts elsewhere in this wiki use Duelcraft's newer core `122e0d09`. See [source versions](source-versions.md), [core API](core-api.md), and [data and scripts](data-and-scripts.md).

## Task lookup

| Host task | EDOPro entry point | Contract to copy |
|---|---|---|
| Load and version-check a core library | [`LoadOCGcore`, `Core::Core`](../../../edopro/gframe/dllinterface.cpp#L108) | Resolve every export, call `OCG_GetVersion`, then publish function pointers only for a valid library. |
| Install callbacks and base scripts | [`Game::SetupDuel`](../../../edopro/gframe/game.cpp#L3930) | Set card/script/log callbacks, create, then load required host scripts. |
| Validate uploaded decks | [`GenericDuel::UpdateDeck`](../../../edopro/gframe/generic_duel.cpp#L406), [`GenericDuel::PlayerReady`](../../../edopro/gframe/generic_duel.cpp#L366) | Parse bounds before card IDs; reject unknown cards, bad sizes, forbidden types, and list violations before starting. |
| Insert cards and start | [`GenericDuel::TPResult`](../../../edopro/gframe/generic_duel.cpp#L547) | Choose seed/rules, create, insert each deck in intended order, publish initial counts, then start and pump. |
| Pump core output | [`GenericDuel::Process`](../../../edopro/gframe/generic_duel.cpp#L730) | Process, split the returned batch, analyze each packet, and stop on a prompt or end. |
| Route prompts and hide choices | [`GenericDuel::Sending`](../../../edopro/gframe/generic_duel.cpp#L829) | Send choice packets only to the active player; redact references to the other player's private cards. |
| Refresh authoritative state | [`BeforeParsing`, `AfterParsing`](../../../edopro/gframe/generic_duel.cpp#L795) | Query after transitions that can change derived state and synthesize update packets. |
| Build player/public query views | [`RefreshLocation`, `RefreshSingle`](../../../edopro/gframe/generic_duel.cpp#L1370) | Send a fuller owner view and a public/redacted opponent-observer view. |
| Resume from a response | [`GenericDuel::GetResponse`](../../../edopro/gframe/generic_duel.cpp#L1284) | Authenticate sender/state in the host, record bytes, set response, then resume the pump. |
| Serve late observers | [`GenericDuel::Catchup`](../../../edopro/gframe/generic_duel.cpp#L137) | Cache a sanitized packet history; bracket replay with catch-up markers. |
| Record/replay | [`Replay::WriteStream`, `ParseStream`](../../../edopro/gframe/replay.cpp#L22) | Treat replay containers as host formats, separate from the live core batch format. |

## The host owns the session

The core knows two teams and duelists, but it has no socket, account, observer, room, clock, deck file, or permission model. EDOPro's [`DuelMode`](../../../edopro/gframe/network.h#L231) holds the core handle beside host state. `GenericDuel` maps connected players to team positions, checks readiness, controls who may answer, and destroys the duel.

Keep the same boundary in another host:

```text
transport event -> session authorization -> response validation -> OCG_DuelSetResponse
OCG_DuelProcess -> copy and parse core batch -> state refresh/redaction -> recipient transport
```

Never expose a core handle as a session credential. `OCG_DuelSetResponse` accepts bytes without a player identity; EDOPro gates it through connection state and `WaitforResponse` before calling the core. See [`WaitforResponse`](../../../edopro/gframe/generic_duel.cpp#L1326) and the network opcode/state definitions at [`CTOS_RESPONSE`](../../../edopro/gframe/network.h#L274).

## Dynamic loading and snapshot compatibility

EDOPro loads a platform-named library, resolves every symbol listed in [`ocgcore_functions.inl`](../../../edopro/gframe/ocgcore_functions.inl#L1), and rejects it unless the reported major/minor equals the host's compiled API version. [`Core::Enable`](../../../edopro/gframe/dllinterface.cpp#L138) assigns the validated addresses to global call pointers; [`ChangeOCGcore`](../../../edopro/gframe/dllinterface.cpp#L173) validates the replacement before unloading the old handle.

The network handshake is a separate compatibility gate. [`NetServer::HandleCTOSPacket`](../../../edopro/gframe/netserver.cpp#L352) compares EDOPro's handshake token and combined client/core version before creating a room. A matching wire protocol does not replace the dynamic library ABI check, and a matching core API does not establish client packet compatibility.

Use the chosen core's header as the ABI authority. This snapshot illustrates why: EDOPro's loader list declares `OCG_StartDuel` with an `int` result at [`ocgcore_functions.inl:5`](../../../edopro/gframe/ocgcore_functions.inl#L5), while its vendored core exports `void` in [`ocgapi.h`](../../../edopro/ocgcore/ocgapi.h#L35). EDOPro call sites discard the result. Duelcraft core also declares `void` at [`ocgapi.h`](../../native/ygopro-core/ocgapi.h#L34). Do not copy a loader typedef without comparing it to the binary's exact header and version.

EDOPro's vendored 11.0 `OCG_DuelCreationStatus` ends at `NULL_SCRIPT_READER` in [`gframe/ocgapi_types.h`](../../../edopro/gframe/ocgapi_types.h#L20). Duelcraft's newer 11.0 snapshot adds incompatible-Lua and all-zero-seed failures in [`ocgapi_types.h`](../../native/ygopro-core/ocgapi_types.h#L20). A matching major/minor does not prove two snapshots have identical diagnostics or behavior; pin a commit and test the ABI you ship.

## Authoritative duel startup

EDOPro's network host performs these steps in [`GenericDuel::TPResult`](../../../edopro/gframe/generic_duel.cpp#L547):

1. Resolve sides and active duelists, generate four seed words, and construct replay headers.
2. Combine the low/high rule words and host options, then call `Game::SetupDuel` with both teams' LP, opening draw, and per-turn draw count.
3. Shuffle host deck vectors unless pseudo-shuffle policy says otherwise.
4. Insert rule cards, then each duelist's main and extra deck. EDOPro iterates vectors from back to front before `OCG_DuelNewCard`; preserve whichever ordering your own deck representation requires.
5. Query initial deck/extra counts, send the host-created `MSG_START`, and create owner/public refreshes.
6. Call `OCG_StartDuel`, then enter `GenericDuel::Process`.

The core does not validate deck legality. EDOPro validates upload framing in [`GenericDuel::UpdateDeck`](../../../edopro/gframe/generic_duel.cpp#L406), divides cards into main/extra/side in [`DeckManager::LoadDeck`](../../../edopro/gframe/deck_manager.cpp#L330), and applies size/content policy when the player becomes ready. [`DeckManager::CheckDeckContent`](../../../edopro/gframe/deck_manager.cpp#L204) covers allowed card pools, forbidden types, extra-deck placement, copies, list limits, skills, and legend rules. [`CheckDeckSize`](../../../edopro/gframe/deck_manager.cpp#L238) handles configured zone bounds.

Your host must also decide malformed-upload limits, unknown IDs, aliases, token handling, side-deck invariants, list selection, match flow, surrender, disconnect, and timeouts. None belongs to `OCG_DuelNewCard`.

## Pump, framing, and stopping

The core batch returned by `OCG_DuelGetMessage` is a sequence of frames:

```text
u32 frame_size | u8 MSG_* | frame payload
```

[`duel::generate_buffer`](../../native/ygopro-core/duel.cpp#L103) writes that framing. EDOPro's [`PacketStream::PacketStream`](../../../edopro/gframe/core_utils.cpp#L360) splits it and stores the message byte apart from the payload.

`GenericDuel::Process` calls `OCG_DuelProcess`, drains every packet with [`CoreUtils::ParseMessages`](../../../edopro/gframe/core_utils.cpp#L309), and passes packets through `Analyze`. `Analyze` runs pre-refresh logic, recipient routing, post-refresh logic, then records the replay stream at [`GenericDuel::Analyze`](../../../edopro/gframe/generic_duel.cpp#L1269). A selection packet returns `1`, so the pump yields to network input; a win/retry returns `2`; otherwise it continues.

Use the core status as the integration contract rather than copying EDOPro's integer loop condition. A robust host drains messages for `CONTINUE`, `AWAITING`, and `END`, then stops according to the returned status. See the lifecycle recipe in [core API](core-api.md#normal-host-lifecycle).

## Core messages versus network packets

EDOPro uses three nested formats:

| Layer | Shape | Producer |
|---|---|---|
| Core batch | Repeated `u32 size` plus one `MSG_*` frame | `OCG_DuelGetMessage` |
| EDOPro game payload | One `u8 MSG_*` plus payload | `CoreUtils::Packet`, or host-generated refresh/start/wait packets |
| EDOPro transport | `u16 packet_length`, `u8 STOC_*`, payload | [`NetServer` send helpers](../../../edopro/gframe/netserver.h#L40) |

`STOC_GAME_MSG` is an EDOPro transport opcode, not a core message. `MSG_UPDATE_DATA`, `MSG_UPDATE_CARD`, the initial `MSG_START`, and `MSG_WAITING` can be synthesized by the host. A consumer must know which layer it is parsing; copying the entire core batch into one EDOPro game packet changes the protocol.

## Visibility and recipient filtering

The authoritative host receives facts that a given client must not see. EDOPro enforces visibility in two places.

First, [`GenericDuel::Sending`](../../../edopro/gframe/generic_duel.cpp#L829) routes and rewrites event packets:

| Packet class | EDOPro behavior |
|---|---|
| Selection and announce prompts | Send only to `cur_player[player]`; mark them non-recordable; move that connection into response state. |
| `MSG_SELECT_CARD`, tribute, unselect | Zero card codes for choices controlled by the other player before sending the prompt. |
| `MSG_CONFIRM_CARDS` | Restrict deck/extra confirmations to the addressed player; public locations go to everyone. |
| Hand/extra shuffle | Send real codes to owner-side duelists, then zero codes for the other side and observers. |
| Move, set, facedown special summon, draw, tag swap | Send owner/full form first; rewrite hidden codes to zero for opponents and observers. |
| Public events | Broadcast and add the sanitized packet to observer catch-up. |

The zeroed foreign selection codes do not mean a revealed hand should be drawn blank. EDOPro's `MSG_CONFIRM_CARDS` consumer (`duelclient.cpp:2582`) writes each nonzero revealed identity onto its addressed card before displaying it. Duelcraft now retains hand reveals the same way, so later sanitized candidates resolve through `ClientDuelState.candidateCode` even after the reveal panel consumes its list. Unrevealed cards stay unknown; `ShuffleHand` replaces remembered identities with the recipient's sanitized codes. Real Confiscation and Trap Dustshoot regressions cover this flow in `PlaytestInteractionTest`. This fix is scoped to hand reveals; other zone knowledge/invalidation is unchanged.

Extra Deck shuffles also invalidate remembered identities. Core `7471af3c` (`field.cpp:936`, `field::shuffle`) shuffles the facedown prefix and excludes the face-up Pendulum tail. Duelcraft's `ShuffleExtra` record carries only the player; `RefreshSchedule.after` already queries that player's Extra Deck. The client clears non-face-up codes and cached query stats before that refresh, preserving face-up cards and marking the pile dirty. Owner queries restore identities in the new order; sanitized opponent queries leave shuffled cards unknown. A zero-code query alone cannot invalidate old knowledge because ordinary partial refreshes deliberately retain known codes. `ClientDuelStateTest` covers both recipient flows.

Second, query refreshes produce different buffers. [`Query::IsPublicQuery`](../../../edopro/gframe/core_utils.cpp#L224) treats code, alias, type, level/rank, attribute/race, combat values, status, scales, and link data as private unless the card is public or face-up. [`Query::GenerateBuffer`](../../../edopro/gframe/core_utils.cpp#L144) omits private query fields when building a public view.

[`GenericDuel::RefreshLocation`](../../../edopro/gframe/generic_duel.cpp#L1370) sends the owner-side view first and the public view to the opposing side and observers. `RefreshSingle` follows the same split. The core query API has no recipient parameter. Calling `OCG_DuelQuery*` and broadcasting its bytes leaks state; the host must derive each recipient view.

EDOPro refreshes around selection windows, phase/chain boundaries, summons, moves, position changes, swaps, shuffles, tag swaps, and reloads in [`BeforeParsing`](../../../edopro/gframe/generic_duel.cpp#L795) and [`AfterParsing`](../../../edopro/gframe/generic_duel.cpp#L1143). Messages describe events, while queries reconcile current state and derived properties. A client that applies events alone will drift.

Observer catch-up uses `packets_cache`, populated with sanitized packets and public refreshes. [`GenericDuel::Catchup`](../../../edopro/gframe/generic_duel.cpp#L137) does not replay owner-only prompts or full private query data.

## Response authority

Before accepting response bytes, bind them to all of these host facts:

- live session and live core handle;
- authenticated connection and expected active duelist;
- one pending prompt of the expected `MSG_*` type;
- prompt generation or nonce, so a delayed answer cannot satisfy a later prompt;
- format, length, indices, counts, and values permitted by that exact prompt;
- one accepted answer only.

EDOPro's `WaitforResponse` sets every other duelist to a non-response state and only the current player to `CTOS_RESPONSE`. [`GenericDuel::GetResponse`](../../../edopro/gframe/generic_duel.cpp#L1284) records the bytes, calls `OCG_DuelSetResponse`, clears host response state, and resumes processing. The core copies bytes but does not authenticate or validate the network sender.

## Replays are host artifacts

EDOPro supports two replay strategies. Legacy `YRP1` records seed, duel parameters, decks, and response byte strings; playback reconstructs a core and feeds responses in [`ReplayMode::OldReplayThread`](../../../edopro/gframe/old_replay_mode.cpp#L9). Streamed `YRPX` stores analyzed `CoreUtils::Packet` objects; [`ReplayMode::ReplayThread`](../../../edopro/gframe/replay_mode.cpp#L77) plays that stream into the client without running the core.

The server records an authoritative replay stream after host-added refreshes, while selection prompts have `record=false`. `GenericDuel::Analyze` copies each core packet before `Sending` performs recipient redaction, and refresh functions add the full query form to `replay_stream`. It also embeds the legacy replay inside an `OLD_REPLAY_MODE` packet when ending at [`GenericDuel::EndDuel`](../../../edopro/gframe/generic_duel.cpp#L1298). These container markers and synthesized update packets are EDOPro conventions.

Treat replay access as a separate visibility decision. A replay can contain card identities hidden from opponents and observers during live play. EDOPro sends replay data after the duel ends; another product may need different release timing, authorization, retention, or export rules. Do not reuse the authoritative replay stream as an observer catch-up stream.

For deterministic legacy replay, retain the exact core/script/data versions, seed, flags, team parameters, insertion order, rule cards, and responses. For streamed replay, retain the packet schema expected by the client. Neither format is a serialized `OCG_Duel`.

## Single-player and replay references

[`SingleMode::SinglePlayThread`](../../../edopro/gframe/single_mode.cpp#L65) uses the same `SetupDuel`, callback, process, message, response, and query APIs without sockets. It runs the pump on its own thread, waits on a host signal for selection input, refreshes local client state, and records both replay forms. This is the clearer EDOPro reference for an offline host.

[`ReplayMode::ReplayAnalyze`](../../../edopro/gframe/replay_mode.cpp#L215) consumes streamed host packets, including `MSG_UPDATE_DATA` and `MSG_UPDATE_CARD`. [`OldReplayThread`](../../../edopro/gframe/old_replay_mode.cpp#L16) reconstructs and executes a duel. Do not feed YRPX packets back into the core or assume a YRP1 response list can update a client without re-execution.

## Porting checklist

- Pin the core binary, public headers, card database, and script pack as one tested bundle.
- Verify every function signature against that core snapshot; check version before creation.
- Own deck policy, player identity, response authorization, clocks, disconnects, and match state outside the core.
- Copy core-owned message/query bytes before the next mutating call.
- Parse every frame in order and preserve prompt choice order.
- Build owner, opponent, and observer views before transport.
- Refresh query state after message classes that can change derived or hidden state.
- Distinguish core frames, host-created `MSG_*` packets, network envelopes, and replay containers.
- Keep public catch-up history separate from authoritative replay data.
- Destroy the duel once and unload the library only after all handles from it are gone.

Raw license texts for the inspected snapshots are at [EDOPro `LICENSE`](../../../edopro/LICENSE) and [ygopro-core `LICENSE`](../../native/ygopro-core/LICENSE).

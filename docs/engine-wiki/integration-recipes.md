# Integration recipes and troubleshooting

Use this page to turn the reference into an adapter and its acceptance checks. These are **host recommendations and test recipes**, not results from a runtime test run. [Index](README.md) · [API](core-api.md) · [EDOPro example](edopro-host.md) · [JNI example](duelcraft-jni.md).

## Smallest useful host

Build these responsibilities in this order. They can be functions in one adapter; separate services or frameworks are not required.

| Step | Deliverable | Acceptance check |
|---|---|---|
| 1. Library and ownership | Version check; checked creation; one handle owner; destroy on each exit path | A valid-options create/destroy succeeds; zero seed and absent required readers return the expected failures. |
| 2. Data and scripts | Card reader, script reader, base-script initialization, logged load failures | Read a normal monster, effect monster, link monster, pendulum card and multi-setcode card; compare decoded values with the database. Load a known scripted card without missing-base errors. |
| 3. Initial state | Full flags, both teams, deterministic deck order and insertions | Query main/extra counts before start; reproduce the expected opening hand and team-0 first turn. |
| 4. Message pump | Copy output; bounded length frames; ordered message dispatch; wait/resume state | Decode a batch containing several messages; preserve the final prompt; avoid duplicate dispatch when get-message is called twice. |
| 5. Responses | Prompt-specific encoders and server-side session checks | Submit legal choices for the supported prompt families; reject wrong sender, stale prompt and malformed reply before entering core. |
| 6. State and privacy | Query parser, host card model, recipient filters | Owner, opponent and spectator views show only intended information after draw, set, reveal, shuffle, move and query refresh. |
| 7. Host lifecycle | Scheduling, session end/cancel, replay inputs and diagnostics | End, disconnect and setup failure each destroy once; callbacks cannot outlive their context; a known replay reproduces output. |

Use [data and scripts](data-and-scripts.md), [pump pseudocode](core-api.md#pump-pseudocode), [response layouts](responses.md), and [queries](queries.md) as the implementation contracts. The [EDOPro page](edopro-host.md) identifies host policies worth comparing.

## A prompt is a suspended operation

Keep the following host state while awaiting input:

```text
session identity + live duel owner
canonical core team whose input is required
prompt generation / host request ID
message type and original ordered candidates
constraints from that prompt: counts, masks, cancel/finish permissions
last response and retry status for diagnostics
```

The host request ID belongs in the host transport; it is not an extra field to append to `OCG_DuelSetResponse`. The core receives only the prompt's response bytes. Likewise, card artwork, display sorting and UI grouping must not change the indices used by encoders.

On `MSG_RETRY`, preserve the pending prompt context and inspect the rejected encoding/constraints. Do not treat retry as a fresh choice list or resume repeatedly with the same invalid bytes. See [retry behavior](responses.md) and [ProgressiveBuffer reads](../../native/ygopro-core/progressivebuffer.h#L18): short fields can read as zero, which is another reason the host must enforce exact response structure.

## Protocol test matrix

| Area | Cases that catch integration errors |
|---|---|
| Frame parsing | Multiple frames; unknown message ID with known frame length; truncated length; length beyond remaining bytes; zero-length frame. |
| Fixed values | Negative sentinel encoded with correct width; a description above 32 bits; race flag above bit 31; sequence above 255 where the protocol uses `u32`. |
| Selection | Empty/one/many candidates, exact min/max, cancel allowed/disallowed, reordered UI, duplicate card codes, mandatory vs optional sum choices, select/unselect finish. |
| Commands | Idle and battle command category/index packing; phase transitions; chain decline only where allowed. |
| Queries | Empty slot, no card, all requested flags, unknown chunk skip by length, location header, overlays, 64-bit race and 10-byte location records. |
| Message/query interaction | Move or draw followed by query refresh; several updates in a batch; upper rule bits retained outside structural snapshot. |
| Privacy | Hidden hand, face-down field, face-down banished cards, extra deck, reveal then conceal, owner/opponent/spectator query variants. |
| Lifecycle | Process continue/await/end; nonempty final output; repeated get-message; response copy; query-buffer reuse; destroy after setup failure. |

For a parser, compare consumed length against the current frame/chunk's boundary. Unknown informational frames can be skipped using framing while recording the unsupported type. Unknown **interactive** prompts must stop the host's progression with a clear unsupported-prompt error; inventing a default reply changes duel semantics. Core producer/consumer evidence is indexed in [message-index.md](message-index.md).

## Reproducible diagnostic record

Capture core/API/build identity, database and script hashes, seed words, complete options, insertion order, accepted response bytes and each process status. Attach copied raw message/query bytes to the relevant step, together with the host's decoded view. Limit access to diagnostics containing private card identities according to the host's normal access policy.

To reproduce a failure, create a fresh duel with the recorded inputs, replay accepted responses in order and compare copied output at the first divergence. Preserve the card/script versions used at the time. Field snapshots cannot restore the complete engine. See [deterministic setup](rules-and-coordinates.md#reproducibility-envelope) and EDOPro [old_replay_mode.cpp](../../../edopro/gframe/old_replay_mode.cpp) for a replay-driven core consumer.

For a narrow scripted scenario, inspect `Debug.ReloadFieldBegin`, `Debug.AddCard` and `Debug.ReloadFieldEnd` in [libdebug.cpp](../../native/ygopro-core/libdebug.cpp#L33), with EDOPro [single_mode.cpp](../../../edopro/gframe/single_mode.cpp) as the host reference. Follow that scenario lifecycle; do not mix field-reset scripts into an already-running ordinary match and assume state survives. `ReloadFieldBegin` calls `duel::clear`.

## Symptom router

| Symptom | First check | Evidence / next page |
|---|---|---|
| Creation fails only with a different Lua library | Creation status 5 and the Lua unwinding diagnostic | [build and compatibility](build-and-compatibility.md#lua-is-a-compatibility-constraint) |
| Creation fails with an empty seed | Four words are all zero; this snapshot rejects them | [creation statuses](core-api.md#creation-statuses) |
| Crash during create or card insertion | Struct layout, live callback payload, valid arguments; log callback may run during creation | [C API binding and lifetime](core-api.md) |
| Card exists but has no effect | Correct DB code/type, base scripts loaded, script resolution and actual `OCG_LoadScript` result | [data and scripts](data-and-scripts.md) |
| Parser shifts after one card address | Used old four-byte location record or byte-sized sequence/position | [protocol](protocol.md) |
| Parser shifts after a description or race | Truncated a 64-bit field | [responses](responses.md), [queries](queries.md) |
| Messages disappear or repeat | Process clears output; get-message does not consume old output | [pump contract](core-api.md#pump-pseudocode) |
| Duel hangs after UI choice | Status handling, awaiting prompt owner, missing set-response/process, unhandled prompt | [responses](responses.md), [message index](message-index.md) |
| Core retries a plausible selection | Wrong prompt encoding, index space, mask polarity, or sum/tribute constraints | [responses](responses.md) |
| Duel accepts an unexpected zero-like answer | Host accepted a short response; native typed reads return default zero for absent data | [ProgressiveBuffer](../../native/ygopro-core/progressivebuffer.h#L18) |
| UI stats are stale | Missing query refresh or cache update after a message | [queries](queries.md), [EDOPro refresh policy](edopro-host.md) |
| Opponent knows hidden cards | Raw output/query broadcast or insufficient field-level filtering | [EDOPro host](edopro-host.md), [JNI implementation caveats](duelcraft-jni.md) |
| Wrong initial hand / first player | Host deck order, reverse iteration, team mapping; startup does not choose them | [rules and coordinates](rules-and-coordinates.md#initial-decks-and-first-player) |
| New rules silently disappear | 32-bit options storage or deriving options from field snapshot | [64-bit rules](rules-and-coordinates.md#rule-flags) |
| Overlay query returns no card | Review the primary snapshot's query overlay branch | [query caveats](queries.md) |
| Java/native crash after duel end | Stale native handle, double close, callback lifetime, concurrent work | [Duelcraft JNI](duelcraft-jni.md) |

## Extending support for one message

1. Find its numeric ID and producer/consumer in [message-index.md](message-index.md).
2. Transcribe the producer's typed writes, including loops and conditional fields. Check the normal EDOPro consumer branch for the same protocol generation.
3. If interactive, trace the engine's next-step reads and validation, then EDOPro's response writer. Record index spaces and cancellation rules.
4. Add host parsing, state/visibility behavior, and response encoding as needed. Query-generated updates and transport-generated packets may have different producers.
5. Verify a minimal byte fixture plus an engine scenario that reaches the message; update the corresponding wiki page with exact source references.

This workflow limits new source inspection to the behavior being changed. It also gives later agents a checked contract to reuse.

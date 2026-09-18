# ygopro-core binary protocol

This reference describes the C API and binary output of ygopro-core commit `122e0d091a0f399221a4510cc98a406ae485905f` (API version 11.0). It is for hosts that embed the core in any language or runtime. It does not describe EDOPro's lobby protocol, replay container, deck format, JNI, or Duelcraft networking.

EDOPro commit `48ec006c49d7899b39f39420b9a71dea5ecdd22a` embeds a different core commit (`158aebe758be3c46249c75d602e3f16d63d2ef31`). Its client code is cited as a working decoder and compatibility guide. Treat this core checkout as authoritative when the two differ.

## Binary conventions

| Name | Width | Meaning |
|---|---:|---|
| `u8`, `i8` | 1 byte | unsigned/signed integer |
| `u16`, `i16` | 2 bytes | unsigned/signed integer |
| `u32`, `i32` | 4 bytes | unsigned/signed integer |
| `u64` | 8 bytes | unsigned integer, commonly an effect description |
| `location_info` | 10 bytes | `u8 controller, u8 location, u32 sequence, u32 position` |

The serializer copies native integer objects into byte vectors with `memcpy`; it performs no byte-order conversion ([`duel_message::write`](../../native/ygopro-core/duel.h#L49), [`insert_value_int`](../../native/ygopro-core/ocgapi.cpp#L167)). Current clients read the stream as little-endian values. A portable host should explicitly encode and decode little-endian and should reject a core built for a host whose representation differs.

`location_info` is serialized field by field, so C/C++ struct padding never enters the stream ([definition](../../native/ygopro-core/card.h#L26), [`duel_message::write(loc_info)`](../../native/ygopro-core/duel.cpp#L167)). Older protocol variants used one-byte sequence and position. EDOPro's [`ReadLocInfo`](../../../edopro/gframe/core_utils.cpp#L283) selects that four-byte legacy form only in compatibility mode. Do not infer widths from the logical range of a field.

## Processing loop

The minimal host loop is:

1. Call `OCG_DuelProcess`.
2. Call `OCG_DuelGetMessage`, copy its returned `length` bytes, and split the records described below.
3. Apply every record in order. If the status is `OCG_DUEL_STATUS_AWAITING`, present the last prompt to its selected player, pass exactly one response byte string to `OCG_DuelSetResponse`, then return to step 1.
4. Stop on `OCG_DUEL_STATUS_END`.

The status values are `END=0`, `AWAITING=1`, and `CONTINUE=2` ([definitions](../../native/ygopro-core/ocgapi_types.h#L30)). `OCG_DuelProcess` clears the previous message buffer, runs processors until a message exists or processing stops, and returns the processor status ([implementation](../../native/ygopro-core/ocgapi.cpp#L111)). It may return several records in one call.

`OCG_DuelSetResponse` copies an arbitrary `length` into the core's progressive response buffer ([C API](../../native/ygopro-core/ocgapi.cpp#L130), [`duel::set_response`](../../native/ygopro-core/duel.cpp#L127)). The core's readers return zero for fields beyond the supplied length instead of rejecting a short response ([`ProgressiveBuffer::at`](../../native/ygopro-core/progressivebuffer.h#L19)). Validate sizes in the host; do not rely on a malformed response to fail closed.

## Message stream framing

`OCG_DuelGetMessage` returns a concatenation of records:

```text
u32 record_length
u8  message_id
u8  payload[record_length - 1]
...next record...
```

`record_length` includes the message ID and excludes its own four bytes. The core constructs each record with the ID as its first byte and prepends the 32-bit length while draining the record queue ([constructor](../../native/ygopro-core/duel.cpp#L157), [`duel::generate_buffer`](../../native/ygopro-core/duel.cpp#L103)). EDOPro independently implements the same walk in [`PacketStream::PacketStream`](../../../edopro/gframe/core_utils.cpp#L360).

Parse with bounds checks: require at least four bytes for the length, require `record_length >= 1`, require the complete record to remain, then dispatch on the first record byte. The length makes unknown IDs skippable. Never scan for a plausible next ID.

The returned pointer belongs to the duel. Copy before the next `OCG_DuelProcess`, which clears the backing vector, or any operation that can resize it ([`OCG_DuelProcess`](../../native/ygopro-core/ocgapi.cpp#L111), [`OCG_DuelGetMessage`](../../native/ygopro-core/ocgapi.cpp#L122)). Query calls use a separate shared query buffer, but each new query overwrites the prior query result.

## Message payload grammar

The message ID selects the payload grammar; records contain no schema version. Read fields in producer order with the declared widths. Counts in this API version are commonly `u32`, even when a list can never approach that size. Card references commonly use `u32 code + location_info`; some older messages instead write controller/location/sequence fields individually. The producer is the only safe authority for a specific message.

Use [message-index.md](message-index.md) to find the producer and EDOPro decoder for each ID. Prompt layouts and their accepted replies are in [responses.md](responses.md). Query blobs carried by `MSG_UPDATE_DATA` and `MSG_UPDATE_CARD` are in [queries.md](queries.md).

## Version and transport rules

- Call `OCG_GetVersion` at load time and compare its result with the adapter's supported version; this checkout's `OCG_VERSION_MAJOR` and `OCG_VERSION_MINOR` macros declare 11.0 ([version macros](../../native/ygopro-core/ocgapi_types.h#L10)). EDOPro refuses a loaded core whose major/minor differs ([`CheckVersion`](../../../edopro/gframe/dllinterface.cpp#L116)).
- Preserve record boundaries and bytes when forwarding core messages, after applying recipient-specific filtering and query redaction. Adding a network packet wrapper is a host concern; do not confuse it with the C API's `u32 record_length`. See [query visibility](queries.md#visibility-belongs-to-the-host).
- Keep response bytes attached to the duel instance and outstanding prompt. A response has no outer message ID, authenticated sender ID, or request token; player fields within a choice describe coordinates.
- Apply records in order. Hints and state updates can precede a prompt in the same returned buffer.
- `MSG_RETRY` has no payload. It means the preceding response failed validation; keep the duel awaiting input and rebuild the response for the same logical prompt ([first validation site](../../native/ygopro-core/playerop.cpp#L56), [EDOPro handler](../../../edopro/gframe/duelclient.cpp#L1348)).

## Common state-event payloads

These fields follow the message ID inside a frame. They are a starter reference for a host state model; use [the complete index](message-index.md) for other messages and EDOPro consumers. Apply [recipient filtering](edopro-host.md#visibility-and-recipient-filtering) before forwarding identities.

| Message | Payload after ID | Interpretation and producer |
|---|---|---|
| `MSG_HINT` | `u8 hint_type, u8 player, u64 value` | Hint subtype selects the meaning of value; [Duel.Hint](../../native/ygopro-core/libduel.cpp#L3063). |
| `MSG_NEW_TURN` | `u8 player` | Canonical core team starting a turn; [Turn](../../native/ygopro-core/processor.cpp#L3334). |
| `MSG_NEW_PHASE` | `u16 phase` | An announced phase; ordinary turns emit Draw, Standby, Main 1, Battle Start, Main 2 and End. ForcedBattle can restore a saved phase; see boundaries below. [Turn](../../native/ygopro-core/processor.cpp#L3365). |
| `MSG_DRAW` | `u8 player, u32 count`, then `count * (u32 code, u32 position)` | Entries contain position as well as code; [Draw](../../native/ygopro-core/operations.cpp#L482). |
| `MSG_MOVE` | `u32 code, location_info from, location_info to, u32 reason` | Update zone membership and address; [SendTo](../../native/ygopro-core/operations.cpp#L4547). |
| `MSG_SUMMONING` | `u32 code, location_info card` | Summon in progress; [SummonRule](../../native/ygopro-core/operations.cpp#L2239). |
| `MSG_SUMMONED` | empty | Summon completion marker; [SummonRule](../../native/ygopro-core/operations.cpp#L2309). |
| `MSG_DAMAGE` | `u8 player, u32 amount` | Subtract amount from LP; [Damage](../../native/ygopro-core/operations.cpp#L603). |
| `MSG_RECOVER` | `u8 player, u32 amount` | Add amount to LP; [Recover](../../native/ygopro-core/operations.cpp#L674). |
| `MSG_LPUPDATE` | `u8 player, u32 lp` | Replace LP with the supplied total; [Duel.SetLP](../../native/ygopro-core/libduel.cpp#L48). |
| `MSG_CHAINING` | `u32 code, location_info handler, u8 trigger_controller, u8 trigger_location, u32 trigger_sequence, u64 description, u32 chain_size` | Handler address and triggering address are separate; [AddChain](../../native/ygopro-core/processor.cpp#L3700). |
| `MSG_CHAINED` | `u8 chain_count` | This count has a different width from `MSG_CHAINING`; [AddChain](../../native/ygopro-core/processor.cpp#L3893). |
| `MSG_WIN` | `u8 winner, u8 reason` | Preserve both values for host end policy; [engine win logic](../../native/ygopro-core/processor.cpp#L4420). |

### Log boundaries and chain numbers

Use received records as context. Normal turns enter Battle Step internally without `MSG_NEW_PHASE`
([producer](../../native/ygopro-core/processor.cpp#L3497)). `ForcedBattle` emits Battle Start and later
restores its saved `infos.phase`, so the protocol is not restricted to the six normal turn labels
([producer](../../native/ygopro-core/processor.cpp#L2780), [restore](../../native/ygopro-core/processor.cpp#L2858)).
An unfamiliar received phase can use a neutral numeric label.

`MSG_DAMAGE_STEP_START` and `MSG_DAMAGE_STEP_END` have no payload and mark real damage-step boundaries
([start](../../native/ygopro-core/processor.cpp#L2295), [end](../../native/ygopro-core/processor.cpp#L2719)).
Damage Calculation is internal; `MSG_BATTLE` supplies battle stats, not a phase transition. EDOPro
consumes the damage-step records without inventing additional phases
([consumer](../../../edopro/gframe/duelclient.cpp#L3832)). Duelcraft logs these two boundaries without
changing its last received phase or adding banners.

`MSG_CHAINING` supplies the one-based link number as `u32`; solving, solved, negated and disabled
records identify that same link with `u8`. Display the engine number, not the link's current list
position ([activation](../../native/ygopro-core/processor.cpp#L3693),
[solving](../../native/ygopro-core/processor.cpp#L4113),
[consumer](../../../edopro/gframe/duelclient.cpp#L3426)). Duelcraft logs activation and solving once,
plus negation/disable when received, using the stored link before it is removed by `ChainSolved`.
Card-name spans use only recipient-sanitized codes and existing client state; styling performs no
additional identity lookup. Each line retains its primary card code for the existing whole-line click.
Sanitized code zero never reaches the name resolver: target/selection lines use "Face-down card"
when the client position is face-down, otherwise "Unknown card". These labels use plain styling
and retain click code zero; no hidden name or card type is inferred.

### Effect-question text arguments

`MSG_SELECT_EFFECTYN` carries the card and location needed to format its description. EDOPro uses
system string 200 for description zero, and system string 221 followed by hint 223 for description
221. Both receive the card name and formatted location; other descriptions receive only the card
name ([consumer](../../../edopro/gframe/duelclient.cpp#L1927)). These templates use `%ls`, which is
not a Java formatter conversion. Duelcraft substitutes arguments in the original template so a
percent sequence inside an inserted name is not interpreted again. Plain descriptions retain
their existing card-name context. Location labels follow EDOPro's system strings, including Field
Spell Zone and Pendulum Zone for spell/trap sequences 5 and 6+
([location formatting](../../../edopro/gframe/data_manager.cpp#L425)).

## Set-card shuffle ordering

`MSG_SHUFFLE_SET_CARD` writes `u8 location, u8 count`, then `count` source locations followed by `count` follow locations. Both lists use the same card-ID order. A follow entry contains the new location only for a card with overlay materials; other entries are zero locations. Resolve the source list before applying follows, and clear the shuffled cards' known identities. The upstream update fixes ordering without changing field widths or list structure ([producer](../../native/ygopro-core/libduel.cpp#L1423)).

## Small byte fixtures

These are constructed examples of the documented layouts, not captures from a live duel. Spaces separate bytes; line breaks separate frames for readability.

```text
# MSG_SELECT_YESNO (13), player 1, description 0x0102030405060708
0a 00 00 00  0d 01 08 07 06 05 04 03 02 01

# MSG_WIN (5), winner 0, reason 1
03 00 00 00  05 00 01
```

The first frame has 10 payload bytes (ID + player + 8-byte description); the second has three. Concatenating them tests frame boundaries, although this artificial order is not a suggested duel scenario. A yes answer to the first prompt is the unframed four-byte response `01 00 00 00`.

## Source retrieval map

| Task | Start here |
|---|---|
| Enumerate IDs and flags | [`ocgapi_constants.h`](../../native/ygopro-core/ocgapi_constants.h#L168) |
| Confirm public C function signatures | [`ocgapi.h`](../../native/ygopro-core/ocgapi.h#L36) |
| Trace record framing | [`duel::generate_buffer`](../../native/ygopro-core/duel.cpp#L103) |
| Trace a prompt and validation | [`playerop.cpp`](../../native/ygopro-core/playerop.cpp#L18) |
| Trace a game event | [`operations.cpp`](../../native/ygopro-core/operations.cpp#L1), [`processor.cpp`](../../native/ygopro-core/processor.cpp#L1), [`field.cpp`](../../native/ygopro-core/field.cpp#L1) |
| Compare a production decoder | [`DuelClient::ClientAnalyze`](../../../edopro/gframe/duelclient.cpp#L1286) |
| Parse message records without UI | [`CoreUtils::PacketStream`](../../../edopro/gframe/core_utils.cpp#L360) |

# Card and field queries

Queries are synchronous snapshots from the C API. They are separate from the message stream, although a host commonly wraps their bytes in `MSG_UPDATE_DATA` or `MSG_UPDATE_CARD`. This page describes commit `122e0d091a0f399221a4510cc98a406ae485905f` only.

## API calls and lifetimes

| Call | Result |
|---|---|
| `OCG_DuelQueryCount(duel, team, loc)` | occupied/countable cards in one base location; invalid team or a non-single-bit location returns 0 |
| `OCG_DuelQuery(duel, &length, info)` | one card's query chunks, with no outer length prefix |
| `OCG_DuelQueryLocation(duel, &length, info)` | `u32 payload_length` followed by one query object per slot/card |
| `OCG_DuelQueryField(duel, &length)` | fixed field snapshot described below, not query chunks |

The public declarations and `OCG_QueryInfo` fields are in [`ocgapi.h`](../../native/ygopro-core/ocgapi.h#L42) and [`ocgapi_types.h`](../../native/ygopro-core/ocgapi_types.h#L90). All three blob-returning calls reuse `duel::query_buffer`; copy a result before the next query. A missing single card returns `nullptr` and length 0 ([`OCG_DuelQuery`](../../native/ygopro-core/ocgapi.cpp#L180)). Initialize the output length before each call: an invalid location mask returns null before writing it.

`flags` chooses fields. `con` is controller/team, `loc` is one base location, `seq` is the slot/card index, and `overlay_seq` selects a material. For a direct overlay query, callers are expected to OR `LOCATION_OVERLAY` into a base location. At this pinned commit the implementation masks to `LOCATION_OVERLAY` and passes `0x80` to `get_field_card`, whose switch accepts base locations instead ([overlay branch](../../native/ygopro-core/ocgapi.cpp#L186), [`get_field_card`](../../native/ygopro-core/field.cpp#L521)). The direct overlay branch therefore resolves no host card. Treat this as a core defect at this SHA; query the host card with `QUERY_OVERLAY_CARD` to obtain material codes unless you patch or upgrade the core.

## Query chunk grammar

One card is a sequence of self-describing chunks:

```text
u16 chunk_size
u32 query_flag
u8  value[chunk_size - 4]
...
u16 4
u32 QUERY_END
```

`chunk_size` counts `query_flag + value` and excludes its own two bytes. Unknown flags can be skipped by advancing `chunk_size - 4` after reading the flag. `QUERY_END` terminates one card and has no value. A zero `u16` is a vacant on-field slot, not `QUERY_END`. The producer macros and terminator are in [`card::get_infos`](../../native/ygopro-core/card.cpp#L109); EDOPro's independent parser follows the same rule in [`Query::Parse`](../../../edopro/gframe/core_utils.cpp#L9).

`OCG_DuelQueryLocation` prefixes the complete stream with `u32 payload_length`, excluding that four-byte prefix. Monster and spell/trap zones include a zero marker for every vacant slot; list locations contain only existing cards. EDOPro reads the prefix as the parse limit in [`QueryStream::Parse`](../../../edopro/gframe/core_utils.cpp#L318). `OCG_DuelQuery` has no prefix because its API length already bounds one object.

## Flags and values

| Flag | Value after `u32 flag` | Meaning |
|---|---|---|
| `QUERY_CODE 0x1` | `u32` | printed/original code from card data |
| `QUERY_POSITION 0x2` | `u32` | current position; for an overlay material, its zero-based material index |
| `QUERY_ALIAS 0x4` | `u32` | effective code returned by `get_code()` |
| `QUERY_TYPE 0x8` | `u32` | effective type mask |
| `QUERY_LEVEL 0x10` | `u32` | level |
| `QUERY_RANK 0x20` | `u32` | rank |
| `QUERY_ATTRIBUTE 0x40` | `u32` | attribute mask |
| `QUERY_RACE 0x80` | `u64` | race mask; old cores used 32 bits |
| `QUERY_ATTACK 0x100` | `i32` | current attack |
| `QUERY_DEFENSE 0x200` | `i32` | current defense |
| `QUERY_BASE_ATTACK 0x400` | `i32` | base attack |
| `QUERY_BASE_DEFENSE 0x800` | `i32` | base defense |
| `QUERY_REASON 0x1000` | `u32` | reason mask |
| `QUERY_REASON_CARD 0x2000` | `location_info` | reason card; all zero when absent |
| `QUERY_EQUIP_CARD 0x4000` | `location_info` | equip target; all zero when absent |
| `QUERY_TARGET_CARD 0x8000` | `u32 count`, then `count * location_info` | effect targets |
| `QUERY_OVERLAY_CARD 0x10000` | `u32 count`, then `count * u32 code` | attached material codes |
| `QUERY_COUNTERS 0x20000` | `u32 count`, then `count * u32 packed` | low 16 bits counter type; high 16 bits total count |
| `QUERY_OWNER 0x40000` | `u8` | original owner |
| `QUERY_STATUS 0x80000` | `u32` | card status mask |
| `QUERY_IS_PUBLIC 0x100000` | `u8` | core's visibility hint |
| `QUERY_LSCALE 0x200000` | `u32` | left pendulum scale |
| `QUERY_RSCALE 0x400000` | `u32` | right pendulum scale |
| `QUERY_LINK 0x800000` | `u32 link, u32 marker` | link rating and marker mask |
| `QUERY_IS_HIDDEN 0x1000000` | `u8` | affected by `EFFECT_DARKNESS_HIDE` |
| `QUERY_COVER 0x2000000` | `u32` | cover/card-back identifier |
| `QUERY_END 0x80000000` | none | terminator, emitted regardless of requested flags |

The constant values are declared together in [`ocgapi_constants.h`](../../native/ygopro-core/ocgapi_constants.h#L168). Widths and compound layouts come from [`card::get_infos`](../../native/ygopro-core/card.cpp#L119). Signed attack values use the same four bytes as `u32`; decode them as `i32`. `QUERY_POSITION` calls `get_info_location()`, which substitutes an overlay material's `current.sequence` for position ([implementation](../../native/ygopro-core/card.cpp#L222)).

## Visibility belongs to the host

The core returns every requested field and always includes `QUERY_IS_PUBLIC`, even if its bit was absent from the requested mask. It does not know which remote viewer will receive the result. `QUERY_IS_PUBLIC` becomes 1 for face-up cards, chain-related cards, or cards in the hand or on the field affected by `EFFECT_PUBLIC`; `QUERY_IS_HIDDEN` reports `EFFECT_DARKNESS_HIDE` ([producer](../../native/ygopro-core/card.cpp#L191)). These are inputs to a host's disclosure policy, not automatic redaction. Parsers must accept the extra visibility chunk; hosts must still filter recipient views.

EDOPro parses the raw query once, then generates different buffers for the owning side and public viewers in [`GenericDuel::RefreshLocation`](../../../edopro/gframe/generic_duel.cpp#L1367) and [`GenericDuel::RefreshSingle`](../../../edopro/gframe/generic_duel.cpp#L1394). Its [`Query::IsPublicQuery`](../../../edopro/gframe/core_utils.cpp#L224) treats code, alias, type, level/rank, attribute/race, combat stats, status, scales, and link data as private unless the card is public or face-up. Copy that policy only if it matches your product's spectator and team rules; the essential invariant is to filter before serialization to an unauthorized process or network peer.

## Field snapshot grammar

`OCG_DuelQueryField` writes:

```text
u32 duel_options_low
for player 0, then player 1:
  u32 lp
  for 7 monster-zone slots:
    u8 present; if present: u8 position, u32 overlay_count
  for 8 spell/trap-zone slots:
    u8 present; if present: u8 position, u32 overlay_count
  u32 deck_count, hand_count, grave_count, banished_count
  u32 extra_count, u32 faceup_extra_count
u32 chain_count
repeat chain_count:
  u32 code
  u8 handler_controller, u8 handler_location, u32 handler_sequence, u32 handler_position
  u8 trigger_controller, u8 trigger_location, u32 trigger_sequence
  u64 description
```

This snapshot has no leading message ID or internal length. The player state allocates exactly seven monster slots and eight spell/trap slots, and the query iterates both full vectors regardless of active rules ([`player_info`](../../native/ygopro-core/field.h#L104), [`OCG_DuelQueryField`](../../native/ygopro-core/ocgapi.cpp#L250)). `duel_options_low` is the low 32 bits of the core's 64-bit duel-options field because this function explicitly serializes it as `u32`; do not reconstruct high flags from this snapshot.

## Query decoder invariants

A constructed single-card query containing only code `0x12345678` is `08 00 01 00 00 00 78 56 34 12 04 00 00 00 00 80`: an eight-byte code chunk after its size field, followed by the four-byte `QUERY_END` flag after its size field. The total API length is 16. A location query adds a separate four-byte payload length and includes a `00 00` marker for each empty slot.

- Bound the outer blob with the API length even when a location prefix is present.
- Bound every chunk before reading its value; do not trust `chunk_size` or list counts from an untrusted transport.
- Preserve unknown chunks if a proxy must round-trip newer query data; otherwise skip by size.
- Reset all destination fields before parsing a new object. An absent flag means "not supplied", not zero.
- Treat a zero slot marker as one position in MZONE/SZONE so later cards keep their sequence.

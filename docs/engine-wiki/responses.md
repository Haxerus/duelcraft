# Prompts and responses

Only prompt messages consume `OCG_DuelSetResponse`. Responses are unframed byte arrays with no outer message ID, sender identity, or length header. Some layouts include card-coordinate player fields or list counts; those do not authenticate the sender. The C API length argument supplies the outer byte length ([API](../../native/ygopro-core/ocgapi.h#L37)). Integer responses use the binary conventions in [protocol.md](protocol.md).

## Scalar and command responses

| Prompt | Response | Validation and meaning | Source |
|---|---|---|---|
| `MSG_SELECT_BATTLECMD` | `i32`, low 16 bits type, high 16 bits index | type 0 activate[index], 1 attack[index], 2 Main Phase 2, 3 End Phase | [`SelectBattleCmd`](../../native/ygopro-core/playerop.cpp#L18) |
| `MSG_SELECT_IDLECMD` | same packing | type 0 summon, 1 special summon, 2 position, 3 monster set, 4 spell/trap set, 5 activate; 6 Battle Phase, 7 End Phase, 8 shuffle hand | [`SelectIdleCmd`](../../native/ygopro-core/playerop.cpp#L69) |
| `MSG_SELECT_EFFECTYN`, `MSG_SELECT_YESNO` | `i32` 0 or 1 | no/yes | [`SelectEffectYesNo`](../../native/ygopro-core/playerop.cpp#L166), [`SelectYesNo`](../../native/ygopro-core/playerop.cpp#L189) |
| `MSG_SELECT_OPTION` | `i32 index` | zero-based index into options | [`SelectOption`](../../native/ygopro-core/playerop.cpp#L205) |
| `MSG_SELECT_CHAIN` | `i32 index`, or `-1` | `-1` declines only when `forced == 0` | [`SelectChain`](../../native/ygopro-core/playerop.cpp#L454) |
| `MSG_SELECT_POSITION` | `i32 position` | exactly one of `1,2,4,8`, and present in offered mask | [`SelectPosition`](../../native/ygopro-core/playerop.cpp#L604) |
| `MSG_ROCK_PAPER_SCISSORS` | `i32` 1..3 | hand value; core prompts players in sequence | [`RockPaperScissors`](../../native/ygopro-core/playerop.cpp#L1120) |
| `MSG_ANNOUNCE_RACE` | `u64 mask` | subset of offered mask with the requested popcount | [`AnnounceRace`](../../native/ygopro-core/playerop.cpp#L916) |
| `MSG_ANNOUNCE_ATTRIB` | `u32 mask` | subset of offered mask with the requested popcount | [`AnnounceAttribute`](../../native/ygopro-core/playerop.cpp#L951) |
| `MSG_ANNOUNCE_CARD` | `i32 card_code` | database entry must satisfy the postfix opcode expression | [`AnnounceCard`](../../native/ygopro-core/playerop.cpp#L1075) |
| `MSG_ANNOUNCE_NUMBER` | `i32 index` | zero-based index into the offered `u64` values | [`AnnounceNumber`](../../native/ygopro-core/playerop.cpp#L1099) |

The battle and idle response is a packed command, not two fields. EDOPro constructs the same value as `(index << 16) + type` ([event handler](../../../edopro/gframe/event_handler.cpp#L373)).

The command prompt payloads preserve six separate index spaces:

```text
MSG_SELECT_BATTLECMD:
  u8 player
  u32 activate_count
    repeat: u32 code, u8 controller, u8 location, u32 sequence,
            u64 description, u8 client_mode
  u32 attack_count
    repeat: u32 code, u8 controller, u8 location, u8 sequence, u8 direct
  u8 can_main2, u8 can_end

MSG_SELECT_IDLECMD:
  u8 player
  u32 summon_count       + repeat (u32 code, u8 controller, u8 location, u32 sequence)
  u32 spsummon_count     + repeat (u32 code, u8 controller, u8 location, u32 sequence)
  u32 reposition_count   + repeat (u32 code, u8 controller, u8 location, u8 sequence)
  u32 monster_set_count  + repeat (u32 code, u8 controller, u8 location, u32 sequence)
  u32 spell_set_count    + repeat (u32 code, u8 controller, u8 location, u32 sequence)
  u32 activate_count     + repeat (u32 code, u8 controller, u8 location, u32 sequence,
                                   u64 description, u8 client_mode)
  u8 can_battle, u8 can_end, u8 can_shuffle_hand
```

The one-byte reposition and attack sequence fields are deliberate exceptions to v11's usual 32-bit sequence. Keep a separate candidate array for every list.

Other scalar prompt payloads are:

| Prompt | Payload after ID |
|---|---|
| `MSG_SELECT_EFFECTYN` | `u8 player, u32 code, location_info card, u64 description` |
| `MSG_SELECT_YESNO` | `u8 player, u64 description` |
| `MSG_SELECT_OPTION` | `u8 player, u8 count, count * u64 description` |
| `MSG_SELECT_CHAIN` | `u8 player, u8 special_count, u8 forced, u32 own_timing, u32 opponent_timing, u32 count`; each candidate is `u32 code, location_info card, u64 description, u8 client_mode` |
| `MSG_SELECT_POSITION` | `u8 player, u32 code, u8 allowed_position_mask` |
| `MSG_ROCK_PAPER_SCISSORS` | `u8 player` |
| `MSG_ANNOUNCE_RACE` | `u8 player, u8 required_count, u64 available_mask` |
| `MSG_ANNOUNCE_ATTRIB` | `u8 player, u8 required_count, u32 available_mask` |
| `MSG_ANNOUNCE_CARD` | `u8 player, u8 opcode_count, opcode_count * u64 opcode` |
| `MSG_ANNOUNCE_NUMBER` | `u8 player, u8 count, count * u64 value` |

## Modern card-list response

`MSG_SELECT_CARD`, `MSG_SELECT_TRIBUTE`, and `MSG_SELECT_SUM` share `parse_response_cards`. API v11 accepts four encodings plus cancellation ([parser](../../native/ygopro-core/playerop.cpp#L237)):

| `i32 type` at byte 0 | Remaining bytes | Use |
|---:|---|---|
| `-1` | none required | cancel; accepted only when the prompt permits cancellation |
| `0` | `u32 count`, then `count * u32 index` | general index list |
| `1` | `u32 count`, then `count * u16 index` | compact list |
| `2` | `u32 count`, then `count * u8 index` | compact list |
| `3` | bitset starting at byte 4; absolute response bit `32 + i` (bit `i` within the bitset) selects index `i` | sparse or dense selection without a count |

For types 0..2, the count starts at byte 4 and indices start at byte 8. Each index addresses the exact candidate order in the prompt. The core rejects out-of-range indices and duplicate card pointers. Type 3 derives its count from set bits and therefore has no count field. EDOPro chooses among these forms in [`SetResponseSelectedCards`](../../../edopro/gframe/event_handler.cpp#L2819).

Do not send the legacy `[u8 count][u8 indices...]` format to this core. EDOPro uses it only when its negotiated `compat_mode` is true.

For example, indices 0 and 3 use type 0 bytes `00 00 00 00 02 00 00 00 00 00 00 00 03 00 00 00`, or type 3 bytes `03 00 00 00 09`. These constructed fixtures assume that those indices exist and satisfy the prompt constraints.

### `MSG_SELECT_CARD` payload

```text
u8 player, u8 cancelable, u32 min, u32 max, u32 count
repeat count: u32 code, location_info card
```

The same ID can represent script-provided card codes. Those entries use `location_info { player, 0, 0, 0 }`, so location zero is intentional ([`SelectCard`](../../native/ygopro-core/playerop.cpp#L279), [`SelectCardCodes`](../../native/ygopro-core/playerop.cpp#L335)).

### `MSG_SELECT_TRIBUTE` payload

```text
u8 player, u8 cancelable, u32 min_value, u32 max_count, u32 count
repeat count: u32 code, u8 controller, u8 location, u32 sequence, u8 tribute_value
```

The response selects indices through the common card-list format. The core rejects more than `max_count` selected cards, but tests the minimum against the sum of their `tribute_value` fields. `max_count` is not a maximum tribute sum ([producer and validator](../../native/ygopro-core/playerop.cpp#L641)).

### `MSG_SELECT_SUM` payload

```text
u8 player, u8 mode, u32 target, u32 min, u32 max
u32 mandatory_count
repeat mandatory_count: u32 code, location_info card, u32 sum_param
u32 optional_count
repeat optional_count: u32 code, location_info card, u32 sum_param
```

Only optional cards appear in the response index space. Mandatory cards still participate in validation. `sum_param` packs one candidate contribution in its low 16 bits and an alternative in its high 16 bits. In mode 0 (`max != 0`), the number of optional cards must be within `min..max`, and one choice of contribution from every mandatory and selected card must total `target`. In mode 1 (`max == 0` before normalization), the core accepts when the maximum possible total is at least `target` and `minimum_total - smallest_minimum_contribution < target`; this models reaching or crossing the target with no removable selected card ([`SelectSum`](../../native/ygopro-core/playerop.cpp#L787)). EDOPro reads API v11 in this order and uses full `location_info` ([consumer](../../../edopro/gframe/duelclient.cpp#L2417)).

## Select/unselect

`MSG_SELECT_UNSELECT_CARD` is an iterative toggle prompt:

```text
u8 player, u8 finishable, u8 cancelable, u32 min, u32 max
u32 selectable_count
repeat selectable_count: u32 code, location_info card
u32 unselectable_count
repeat unselectable_count: u32 code, location_info card
```

Reply with two `i32` values: `{1, combined_index}` to toggle one card, or a single `i32 -1` to finish/cancel when either flag permits it. `combined_index` indexes selectable cards first, followed by unselectable cards ([producer and validator](../../native/ygopro-core/playerop.cpp#L391)). This response does not use the generic card-list encoding. EDOPro makes the two-word reply explicitly ([`SetResponseSelectedCards`](../../../edopro/gframe/event_handler.cpp#L2821)).

## Place, counter, and sort responses

| Prompt | Payload essentials | Response | Source |
|---|---|---|---|
| `MSG_SELECT_PLACE`, `MSG_SELECT_DISFIELD` | `u8 player, u8 count, u32 unavailable_mask` | exactly `count` triples of `u8 player, u8 location, u8 sequence` | [`SelectPlace`](../../native/ygopro-core/playerop.cpp#L506) |
| `MSG_SELECT_COUNTER` | `u8 player, u16 counter_type, u16 total, u32 count`; each candidate is `u32 code, u8 controller, u8 location, u8 sequence, u16 available` | `count * u16 amount`, one per candidate; amounts must sum to `total` | [`SelectCounter`](../../native/ygopro-core/playerop.cpp#L708) |
| `MSG_SORT_CARD`, `MSG_SORT_CHAIN` | `u8 player, u32 count`; each candidate is `u32 code, u8 controller, u32 location, u32 sequence` | `count * i8`, a permutation of `0..count-1`; `response[candidate_index]` is that candidate's destination order index; first byte `-1` declines | [`SortCard`](../../native/ygopro-core/playerop.cpp#L878), [order application](../../native/ygopro-core/processor.cpp#L5066) |

For place prompts, a set bit in `unavailable_mask` means forbidden. Bits 0–6 address the prompted player's monster slots, bits 8–15 its spell/trap slots, bits 16–22 the other player's monster slots, and bits 24–31 the other player's spell/trap slots. Response triples use canonical team 0/1, not relative own/opponent IDs. The validator accepts only monster zone (`0x04`, sequences 0–6) or spell/trap zone (`0x08`, sequences 0–7), checks the mask, and marks each accepted slot unavailable so duplicates fail ([validation](../../native/ygopro-core/playerop.cpp#L570)). Do not return a 32-bit zone mask; return the triples.

## Retry discipline

On invalid input the core emits `MSG_RETRY` and leaves the processor waiting. The message carries no reason or corrected schema. Keep the original prompt model until a valid response advances the duel. A robust host should validate candidate indices, cardinality, masks, and byte length before calling the core. Log the prompt ID plus response hex locally; never expose hidden candidate codes to unauthorized viewers.

## Response implementation checklist

- Associate one outstanding prompt with one duel and selected player.
- Keep candidate ordering exactly as received; display sorting needs a mapping back to protocol indices.
- Encode signed sentinels (`-1`) as two's-complement `i32` where required.
- Use the generic modern card-list envelope only for select-card, tribute, and sum.
- Handle `MSG_RETRY` without calling `OCG_DuelProcess` repeatedly or discarding the prompt.

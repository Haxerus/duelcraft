# Rule flags, coordinates, and deterministic setup

Use this page for deck insertion, team mapping, field layouts, rule selection and replay fixtures. Scope: core `7471af3c`, API 11.0. [Index](README.md) · [C API](core-api.md) · [protocol](protocol.md).

## Core identities

| Term | Meaning | Do not substitute |
|---|---|---|
| Team / owner | Core side 0 or 1; `OCG_NewCardInfo.team` initializes ownership | Minecraft player identity or UI screen orientation |
| Controller (`con`, source spelling `controler`) | Core side currently controlling the card | Original owner |
| Duelist | Index within a team; `duelist=0` uses the active card insertion path, higher values populate additional main/extra lists | Controller index |
| Card code | Unsigned 32-bit database identifier | Unique physical card instance |
| Sequence | Current index in a zone; insertion uses special deck semantics | A permanent card ID or prompt-choice index |
| Prompt index | Position in the exact offered choice list | Zone sequence, sorted UI row, or card code |

Sources: [OCG_NewCardInfo](../../native/ygopro-core/ocgapi_types.h#L80), [insertion branches](../../native/ygopro-core/ocgapi.cpp#L62), [player state](../../native/ygopro-core/field.h#L82). Keep canonical core team indices in network/state code; convert perspective only at presentation boundaries.

## Locations and positions

| Location | Value | Storage / coordinate behavior |
|---|---|---|
| `LOCATION_DECK` | `0x01` | Main deck vector; highest vector index is the top. |
| `LOCATION_HAND` | `0x02` | Variable-size vector. |
| `LOCATION_MZONE` | `0x04` | Seven slots: main zones 0–4, extra monster zones 5–6. |
| `LOCATION_SZONE` | `0x08` | Eight slots: main spell/trap zones 0–4, field slot 5, separate pendulum slots 6–7. Availability depends on rules. |
| `LOCATION_GRAVE` | `0x10` | Graveyard vector. |
| `LOCATION_REMOVED` | `0x20` | Banished vector; position matters for visibility. |
| `LOCATION_EXTRA` | `0x40` | Extra deck vector with face-up pendulum count tracked separately. |
| `LOCATION_OVERLAY` | `0x80` | Overlay-material addressing modifier; not an independent player zone vector. |

Position bits: face-up attack `0x1`, face-down attack `0x2`, face-up defense `0x4`, face-down defense `0x8`. Combined masks (`POS_FACEUP`, `POS_FACEDOWN`, etc.) are masks, not additional orientations.

Sources: [public constants](../../native/ygopro-core/ocgapi_constants.h#L11), [slot allocation](../../native/ygopro-core/field.h#L105), [location classification](../../native/ygopro-core/card.h#L59), [location availability](../../native/ygopro-core/field.cpp#L618). `LOCATION_FZONE=0x100`, `PZONE=0x200`, and other [internal location selectors](../../native/ygopro-core/common.h#L39) serve script/rule checks; serialized `loc_info.location` is one byte. Follow each API/message's contract rather than inserting a high-bit selector into a wire byte.

`loc_info` serializes controller `u8`, location `u8`, sequence `u32`, position `u32`: **10 bytes** despite native struct padding. An overlay record uses the parent card's address with the overlay flag and the material index in its position component. See [protocol](protocol.md) and [queries](queries.md) for the distinct query API overlay fields.

## Initial decks and first player

For ordinary two-player setup, insert with `team=con=0` or `1` and `duelist=0`. Each insertion with `loc=LOCATION_DECK, seq=0` appends to the top; `seq=1` inserts at the bottom. Other deck sequence values use the top-and-shuffle-mark path in `field::add_card`. These values are insertion instructions, not requested final indices.

`Startup` clears setup shuffle-check flags and draws starting hands, then schedules `Turn(0)`. Consequently, the host must prepare initial deck ordering/shuffling and map the chosen first player to core team 0. Do not assume `OCG_StartDuel` shuffles initial decks or chooses a random first player. Sources: [field::add_card](../../native/ygopro-core/field.cpp#L147), [startup](../../native/ygopro-core/processor.cpp#L5009), [draw from back](../../native/ygopro-core/operations.cpp#L438). EDOPro's preparation policy appears in [the host page](edopro-host.md).

The insertion path can redirect extra-deck card types into `LOCATION_EXTRA`. Validate the intended main/extra partition before insertion and verify counts afterward. Deck-format rules, forbidden/limited lists, side decks and player identity are host concerns; the insertion API's void result is not a deck-validity report. See [field::add_card](../../native/ygopro-core/field.cpp#L116) and [EDOPro deck_manager.cpp](../../../edopro/gframe/deck_manager.cpp).

## Rule flags

`OCG_DuelOptions.flags` and `field::core.duel_options` are **64-bit**. A host with a 32-bit mask loses flags such as `DUEL_TCG_SEGOC_NONPUBLIC` and `DUEL_EXTRA_DECK_RITUAL`. Store the full original options for replay; the field snapshot writes a 32-bit options value and cannot recover the upper bits.

| Goal | Source-defined selection |
|---|---|
| Master Rule 5 engine preset | `DUEL_MODE_MR5`: pendulum zones, extra monster zones, fusion/synchro/xyz main-zone rules, trap-monster zone behavior, trigger-only-in-location |
| Earlier master rules | `DUEL_MODE_MR1` through `DUEL_MODE_MR4`; use the named expression, not a guessed integer rule number |
| Speed or Rush behavior | `DUEL_MODE_SPEED`, `DUEL_MODE_RUSH`; host deck sizes/UI/data policy still needs a matching format |
| Additional duelists / relay behavior | Inspect `DUEL_RELAY` and the `duelist` insertion branch with EDOPro's team handling |
| Controlled deck/extra shuffling for fixtures | `DUEL_PSEUDO_SHUFFLE` changes deck/extra shuffle behavior; it does **not** disable hand shuffles or all randomness |
| Historical first-turn draw | `DUEL_1ST_TURN_DRAW`; distinct from each team's starting-hand count |

Sources: [preset expressions and flags](../../native/ygopro-core/ocgapi_constants.h#L378), [64-bit internal options](../../native/ygopro-core/field.h#L327), [field snapshot truncation](../../native/ygopro-core/ocgapi.cpp#L271), [shuffle branch](../../native/ygopro-core/field.cpp#L936). `DUEL_MODE_MR*_FORB` values are forbidden card-type masks, not values to OR into duel options.

The engine defines phase values as flags, e.g. draw `0x01`, standby `0x02`, main 1 `0x04`, main 2 `0x100`, end `0x200`, with multiple battle/damage subphases. A host should preserve the source values and distinguish `PHASE_BATTLE_START`, `PHASE_BATTLE_STEP`, `PHASE_DAMAGE`, `PHASE_DAMAGE_CAL` and `PHASE_BATTLE`. Source: [phase constants](../../native/ygopro-core/ocgapi_constants.h#L366).

## Reproducibility envelope

For a reproducible fixture, retain all four seed words, full flags, team LP/draw settings, core/Lua build identity, card database content, script-pack content and load order, ordered insertions, and exact accepted response bytes. Host-side deck shuffling happens outside the core RNG unless the host explicitly ties them together; record final inserted order as well.

The core constructs a per-duel `Xoshiro256StarStar` from `seed[4]` and uses rejection sampling for bounded integers. It rejects the all-zero seed in this snapshot. A seed alone does not establish reproducibility across different scripts, databases, revisions, or altered random-call sequences. Sources: [constructor and RNG](../../native/ygopro-core/duel.cpp#L15), [bounded RNG](../../native/ygopro-core/duel.cpp#L135), [seed rejection](../../native/ygopro-core/ocgapi.cpp#L43).

The public C API contains no clone, serialize, restore, or rewind function. Field queries provide display/state data, not Lua stacks, processor queues, RNG state, or a restorable simulation. Use host replay-by-reconstruction or design a separate strategy after reviewing its requirements. [Export list](../../native/ygopro-core/ocgapi.h#L29) · [replay recipe](integration-recipes.md).

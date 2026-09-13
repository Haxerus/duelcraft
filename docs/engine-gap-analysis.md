# Duelcraft Engine Gap Analysis

**Date:** 2026-09-08. **Mod:** `main` at `b910dd5`. **Engine reference:** `native/ygopro-core` at `7471af3` (v11.0-56, 2026-03-31): `ocgapi_constants.h`, `ocgapi.h`, `ocgapi_types.h`, and the message writers in `playerop.cpp`, `processor.cpp`, `operations.cpp`, `field.cpp`, `card.cpp`, `libduel.cpp`, `libcard.cpp`, `libgroup.cpp`, `libdebug.cpp`. **Design-authority reference:** `../edopro` at `48ec006c` (41.0.2-86, 2026-04-11): `gframe/generic_duel.cpp` (multiplayer host), `single_mode.cpp`, `duelclient.cpp`, `client_field.cpp`, `client_card.cpp`, `core_utils.cpp`, `event_handler.cpp`.

**Method.** Every one of the 95 `MSG_*` constants was traced to its `new_message(...)` writers (or shown to have none), its byte layout transcribed, and compared field by field against `MessageParser`, the `DuelMessage` record, `DuelMessageCodec`, server routing (`ServerDuelHandler`, `SoloDuelHandler`), client handling (`ClientDuelState`, `PromptController`, `FieldRenderer`, `ZoneInspectorController`), `ResponseBuilder`/`ResponseValidator` against the engine's response readers in `playerop.cpp`, and the test suites. The engine defines the wire format; edopro defines who sees what, when card data is refreshed, and how prompts behave. §12 records edopro's behaviour for every message and prompt and lists the divergences. Engine line numbers refer to the submodule commit above, edopro line numbers to the checkout above. `docs/engine-wiki/` pins the same two commits and holds the per-message producer/consumer index, protocol, response and query references this analysis was checked against; consult it before re-deriving any engine fact.

This document supersedes sections 1 to 3 of `docs/engine-implementation-checklist.md`. That file's section 4 (open bugs from the 2026-09-06 manual pass) is folded into §7 and §8 here.

**Legend:** ✅ done, verified against engine source · ⚠️ partial (works but drops data or covers only some cases) · 🐞 incorrect (verified defect) · ❌ missing · ➖ not applicable (never emitted by the core, or unreachable in Duelcraft's 1v1, non-debug configuration).

---

## 0. Scorecard

| What | Count | Detail |
|---|---|---|
| `MSG_*` constants in the engine header | 95 | all 95 present in `OcgConstants` with correct values |
| Never written by the core | 11 | `WAITING`, `START`, `UPDATE_DATA`, `UPDATE_CARD`, `REQUEST_DECK`, `REFRESH_DECK`, `UNEQUIP`, `BE_CHAIN_TARGET`, `CREATE_RELATION`, `RELEASE_RELATION`, `CUSTOM_MSG` (host-side or legacy constants) |
| Written but unreachable in Duelcraft | 4 | `TAG_SWAP` (needs `duelist > 0`), `RELOAD_FIELD`, `AI_NAME`, `SHOW_HINT` (Debug library only) |
| Messages a Duelcraft duel can actually receive | 80 | |
| … parsed into a typed record | 80 | 74 exact, 5 partial, 1 incorrect (`SELECT_SUM`) |
| … falling through to `Raw` | 0 | |
| Prompt messages | 21 | 14 have a client UI (3 of those with verified response defects), 7 have none and wedge a real duel |
| Non-prompt records delivered to the client | 50 | 22 handled (`Retry` and `Hint` defectively), 18 deliberate no-ops, 10 dropped by the `default` branch |
| `QUERY_*` flags | 27 | 14 requested by the server, 19 parsed by `FieldQuery`, 7 skipped; 3 of 8 locations refreshed |
| `OCG_*` API functions | 13 | 12 exposed to Java, 10 used in production; `QueryLocation`, `QueryField` unused |
| Parser cases with a byte-level test | 66 / 72 | `SELECT_IDLECMD` and `SELECT_BATTLECMD` untested; `DuelMessageCodec` has zero tests |
| edopro divergences (§12.6) | 44 | 17 host (routing, hiding, refresh, lobby), 12 client model, 15 prompt behaviours |

**Verified defects that break gameplay** (details in §3): `SELECT_SUM` parse and response, battle-phase Activate action code, `MSG_RETRY` hiding the prompt, overlay moves never applied, `SELECT_DISFIELD` with count > 1, face-up-destination `MSG_MOVE` leaking hidden codes, seven prompts without UI, `ANNOUNCE_CARD` with no response path at all.

---

## 1. Message coverage matrix

Columns: **Core** = does the engine write it; **Parse** = `MessageParser` verdict against the engine layout; **Client** = what `ClientDuelState`/UI does with the record; **Test** = byte-level parser test exists.

### 1.1 Duel flow and bookkeeping

| #   | Message            | Core                                   | Parse                                                               | Client | Test | Notes                                                                                                                                                                                                                                                                                                                                            |
| --- | ------------------ | -------------------------------------- | ------------------------------------------------------------------- | ------ | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | `MSG_RETRY`        | ✅ 32 sites (`playerop.cpp`)            | ✅ empty body                                                        | ✅     | ✅    | Client nulls `pendingPrompt` on send; `Retry` only re-renders, so the prompt disappears and the engine waits forever. The engine does **not** re-send the `SELECT_*` after a retry.                                                                                                                                                              |
| 2   | `MSG_HINT`         | ✅ 75 sites                             | ✅ `u8 type, u8 player, u64 data`                                    | ✅      | ✅    | `HINT_SELECTMSG` captions the next prompt; `OPSELECTED`/`RACE`/`ATTRIB`/`CODE`/`NUMBER` toast; `MESSAGE` opens a modal; `CARD` fills the info banner; `ZONE` flashes the zones. `HINT_EVENT` and `HINT_EFFECT` are still dropped. Core emits EVENT/SELECTMSG/OPSELECTED/RACE/ATTRIB/CODE/NUMBER/CARD; MESSAGE/ZONE/SKILL arrive only via `Duel.Hint` from scripts. `HINT_CARD` sites hardcode `player = 0`. |
| 3   | `MSG_WAITING`      | ➖ host-only                            | ➖                                                                   |        |      | edopro's server synthesises it.                                                                                                                                                                                                                                                                                                                  |
| 4   | `MSG_START`        | ➖ host-only                            | ➖                                                                   | ➖      | ✅    | `parseStart`, `DuelMessage.Start`, the codec arm and `ClientDuelState`'s `Start` branch are all dead. Duelcraft uses `DuelStartPayload`.                                                                                                                                                                                                         |
| 5   | `MSG_WIN`          | ✅ `processor.cpp:4420-4427, 4712-4719` | ✅ `u8 winner (2 = draw), u8 reason (1 LP, 2 deck-out, else script)` | ➖      | ✅    | Both server handlers convert it to `DuelEndPayload`, so the client's `Win` branch and `PromptController.showWinOverlay` are unreachable.                                                                                                                                                                                                         |
| 6   | `MSG_UPDATE_DATA`  | ➖ synthesised by `DuelSession`         | n/a                                                                 | ⚠️     | n/a  | Only ATK/DEF/level labels consume it; see §5.                                                                                                                                                                                                                                                                                                    |
| 7   | `MSG_UPDATE_CARD`  | ➖                                      | ➖                                                                   | ➖      |      | Record, codec, sanitiser and client arms exist; nothing constructs one.                                                                                                                                                                                                                                                                          |
| 8   | `MSG_REQUEST_DECK` | ➖ legacy                               |                                                                     |        |      |                                                                                                                                                                                                                                                                                                                                                  |
| 40  | `MSG_NEW_TURN`     | ✅ `processor.cpp:3334`                 | ✅ `u8`                                                              | ✅      | ✅    | `TURN_PHASE` does not refresh the status label, so "Waiting…" never appears on a turn change.                                                                                                                                                                                                                                                    |
| 41  | `MSG_NEW_PHASE`    | ✅ 10 sites, all `u16`                  | ✅                                                                   | ✅      | ✅    | All five battle sub-phases render as "Battle".                                                                                                                                                                                                                                                                                                   |
| 130 | `MSG_TOSS_COIN`    | ✅ `operations.cpp:6034, 6059`          | ✅ `u8 p, u8 n, u8[n]`                                               | ❌      | ✅    | Dropped by `default`.                                                                                                                                                                                                                                                                                                                            |
| 131 | `MSG_TOSS_DICE`    | ✅ 4 sites                              | ✅ same shape; two-player rolls arrive as two messages               | ❌      | ✅    | Dropped.                                                                                                                                                                                                                                                                                                                                         |
| 133 | `MSG_HAND_RES`     | ✅ `playerop.cpp:1151`                  | ✅ packed `u8 = h0 \| h1 << 2`                                       | ⚠️     | ✅    | Status text set via the `CHAIN` dirty flag and never cleared.                                                                                                                                                                                                                                                                                    |
| 170 | `MSG_MATCH_KILL`   | ✅ `operations.cpp:609`                 | ✅ `u32 code`                                                         | ⚠️ stored | ✅    | `u32 code`. Match play only; edopro reads it only when `best_of > 1`.                                                                                                                                                                                                                                                                            |

### 1.2 Deck, hand, reveals

| #   | Message                | Core                                                           | Parse                                                  | Client  | Test | Notes                                                                                                                                                                                                                  |
| --- | ---------------------- | -------------------------------------------------------------- | ------------------------------------------------------ | ------- | ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 30  | `MSG_CONFIRM_DECKTOP`  | ✅ `libduel.cpp:832`                                            | ✅ `u8 p, u32 n, n×{u32 code, u8 con, u8 loc, u32 seq}` | ✅      | ✅    | Opens the zone inspector, titled for the pile's owner, and writes the revealed codes onto the deck's card objects.                                                        |
| 31  | `MSG_CONFIRM_CARDS`    | ✅ 3 sites (`Duel.ConfirmCards`, reversed-deck draw, set-group) | ✅ same layout                                          | ✅      | ✅    | Same inspector, titled for the owner of the revealed cards.                                                                                                                                                                                                 |
| 32  | `MSG_SHUFFLE_DECK`     | ✅ `field.cpp:987`                                              | ✅ `u8`                                                 | ✅      | ✅    | Zeroes every deck card's code and orientation, as edopro does.                                                                                                                                                                                                    |
| 33  | `MSG_SHUFFLE_HAND`     | ✅ `field.cpp:967`                                              | ✅ `u8 p, u32 n, u32[n]`                                | ✅       | ✅    | Codes zeroed for the opponent server-side.                                                                                                                                                                             |
| 34  | `MSG_REFRESH_DECK`     | ➖ legacy                                                       |                                                        |         |      |                                                                                                                                                                                                                        |
| 35  | `MSG_SWAP_GRAVE_DECK`  | ✅ `field.cpp:1048`                                             | ✅                                                      | ✅       | ✅    | `u8 p, u32 extraCount, u32 maskBytes, mask[]`. The middle `u32` is an Extra Deck count that edopro discards; the bitmask flags which swapped-in cards route to the Extra Deck. |
| 36  | `MSG_SHUFFLE_SET_CARD` | ✅ `libduel.cpp:1404`, `operations.cpp:2958`                    | ✅                                                      | ✅       | ✅    | `u8 loc, u8 n, n×loc_info (old), n×loc_info`. The second block is a sparse XYZ-material-follow list (zeros in the set-group path), not new positions.                                                                  |
| 37  | `MSG_REVERSE_DECK`     | ✅ `processor.cpp:4926`                                         | ✅                                                      | ✅       | ✅    | Empty body. Toggles one global `deckReversed` flag, as edopro's.                                                                                                                                                                                     |
| 38  | `MSG_DECK_TOP`         | ✅ 14 sites                                                     | ✅                                                      | ✅       | ✅    | `u8 p, u32 offsetFromTop, u32 code, u32 pos` (13 bytes). Public information (reversed or revealed deck top); edopro broadcasts it to both players.                                                                     |
| 39  | `MSG_SHUFFLE_EXTRA`    | ✅ `field.cpp:967`                                              | ⚠️ codes read and discarded by design                  | ✅ no-op | ✅    | `UpdateData(EXTRA)` rebuilds the list at every pause.                                                                                                                                                                  |
| 42  | `MSG_CONFIRM_EXTRATOP` | ✅ `libduel.cpp:854`                                            | ✅                                                      | ✅       | ✅    | Byte-identical to `CONFIRM_DECKTOP`.                                                                                                                                          |
| 90  | `MSG_DRAW`             | ✅ `operations.cpp:482`                                         | ✅                                                     | ✅       | ✅    | Engine writes `u32 code, u32 position` per card (not a top-bit flag). Parser reads both and drops `position`, the only signal that a reversed-deck draw is public.                                                     |
| 190 | `MSG_REMOVE_CARDS`     | ✅ `libduel.cpp:536-554`                                        | ✅                                                      | ✅       | ✅    | `u32 n (≤255), n×loc_info`, batched across messages.                                                                                                                        |

### 1.3 Card movement and position

| # | Message | Core | Parse | Client | Test | Notes |
|---|---|---|---|---|---|---|
| 50 | `MSG_MOVE` | ✅ 17 sites | ✅ `u32 code, loc_info from, loc_info to, u32 reason` | ✅ | ✅ | Card objects move between containers; overlay attach/detach renumbers materials, counters and links follow edopro's rules. `reason` ignored. Hand removal falls back to the tail on an out-of-range sequence. Tokens leaving play arrive with a zeroed `to`. |
| 53 | `MSG_POS_CHANGE` | ✅ `operations.cpp:5278` | ✅ `u32 code, u8 con, u8 loc, u8 seq, u8 prev, u8 cur` | ✅ | ✅ | `prevPosition` unused (no flip animation). |
| 54 | `MSG_SET` | ✅ 3 sites | ✅ `u32 code + loc_info` | ✅ | ✅ | Code zeroed for the opponent. |
| 55 | `MSG_SWAP` | ✅ `field.cpp:461` | ✅ `u32, loc_info, u32, loc_info` | ✅ | ✅ | Swaps the two card objects wherever they sit, so stats, counters and materials travel with them. |
| 56 | `MSG_FIELD_DISABLED` | ✅ `processor.cpp:4476, 4641` | ✅ `u32` (low 16 = p0, high 16 = p1) | ✅ | ✅ | Mask stored per player; disabled zones get a grey overlay. They are still clickable. |
| 60 | `MSG_SUMMONING` | ✅ `operations.cpp:2239` | ✅ `u32 code + loc_info` | ⚠️ no-op | ✅ | No summon announcement or animation. |
| 61 | `MSG_SUMMONED` | ✅ | ✅ empty | ✅ no-op | ✅ | |
| 62 | `MSG_SPSUMMONING` | ✅ 3 sites | ✅ (`code` is 0 for face-down summons) | ⚠️ no-op | ✅ | |
| 63 | `MSG_SPSUMMONED` | ✅ 3 sites | ✅ empty | ✅ no-op | ✅ | |
| 64 | `MSG_FLIPSUMMONING` | ✅ `operations.cpp:2373` | ✅ | ✅ | ✅ | |
| 65 | `MSG_FLIPSUMMONED` | ✅ | ✅ empty | ✅ no-op | ✅ | |
| 80 | `MSG_CARD_SELECTED` | ✅ `processor.cpp:2031` | ✅ `u32 n, n×loc_info` (no player byte) | ✅ | ✅ | Attack-target flow only. Highlights the named cards until the next prompt. |
| 81 | `MSG_RANDOM_SELECTED` | ✅ `libgroup.cpp:326` | ✅ | ✅ | ✅ | `u8 p, u32 n, n×loc_info`. Highlights the picked cards until the next prompt. |
| 83 | `MSG_BECOME_TARGET` | ✅ 3 sites | ✅ `u32 n, n×loc_info` | ✅ | ✅ | Highlights the targeted cards until the next prompt. |

### 1.4 Chains

| # | Message | Core | Parse | Client | Test | Notes |
|---|---|---|---|---|---|---|
| 70 | `MSG_CHAINING` | ✅ `processor.cpp:3700` | ✅ `u32 code, loc_info, u8 tc, u8 tl, u32 ts, u64 desc, u32 count` | ✅ | ✅ | Numbered marker on the trigger slot, `desc` in the log, count on `#chain-count`. |
| 71 | `MSG_CHAINED` | ✅ | ✅ `u8` | ⚠️ no-op | ✅ | |
| 72 | `MSG_CHAIN_SOLVING` | ✅ | ✅ `u8` | ✅ | ✅ | The resolving link's marker is stamped `.chain-solving`. |
| 73 | `MSG_CHAIN_SOLVED` | ✅ | ✅ `u8` | ✅ | ✅ | Pops the link, so the count falls as the chain resolves. |
| 74 | `MSG_CHAIN_END` | ✅ | ✅ empty | ✅ | ✅ | |
| 75 | `MSG_CHAIN_NEGATED` | ✅ `operations.cpp:36` | ✅ `u8` | ✅ | ✅ | Negated stamp on the link's marker plus a log line. |
| 76 | `MSG_CHAIN_DISABLED` | ✅ 2 sites | ✅ `u8` | ✅ | ✅ | Same stamp and log line as `CHAIN_NEGATED`. |
| 120 | `MSG_MISSED_EFFECT` | ✅ `processor.cpp:4374` | ✅ | ✅ | ✅ | `loc_info, u32 code`. Highlights the card and writes a "missed the timing" log line. |
| 121 | `MSG_BE_CHAIN_TARGET` | ➖ | | | | |
| 122 | `MSG_CREATE_RELATION` | ➖ | | | | |
| 123 | `MSG_RELEASE_RELATION` | ➖ | | | | |

### 1.5 Life points and battle

| # | Message | Core | Parse | Client | Test | Notes |
|---|---|---|---|---|---|---|
| 91 | `MSG_DAMAGE` | ✅ `operations.cpp:603` | ✅ `u8 p, u32 delta` | ✅ | ✅ | LP is polled every frame; a red floating number and a log line carry the change. |
| 92 | `MSG_RECOVER` | ✅ | ✅ | ✅ | ✅ | Green floating number. |
| 94 | `MSG_LPUPDATE` | ✅ `libduel.cpp:48`, `field.cpp:1240` | ✅ `u8 p, u32 newTotal` | ✅ | ✅ | Confirmed absolute, not a delta. |
| 100 | `MSG_PAY_LPCOST` | ✅ `operations.cpp:749` | ✅ | ✅ | ✅ | Blue floating number, told apart from damage. |
| 110 | `MSG_ATTACK` | ✅ 3 sites | ✅ `loc_info, loc_info` (zeroed target = direct attack) | ✅ | ✅ | Attacker and target slots marked for ~1.5 s; a direct attack marks the LP bar. No drawn arrow. |
| 111 | `MSG_BATTLE` | ✅ `processor.cpp:2444` | ✅ layout; the two `u8` fields are **battle-destroyed flags**, misnamed `atkDamage`/`defDamage` in the record | ✅ | ✅ | Combat ATK/DEF override the queried stats until `DAMAGE_STEP_END`. |
| 112 | `MSG_ATTACK_DISABLED` | ✅ | ✅ empty | ✅ | ✅ | "An attack was negated" in the log. |
| 113 | `MSG_DAMAGE_STEP_START` | ✅ 2 sites | ✅ empty | ❌ no-op | ✅ | |
| 114 | `MSG_DAMAGE_STEP_END` | ✅ | ✅ empty | ✅ | ✅ | Clears the combat stat overrides. |

### 1.6 Relationships, counters, hints

| # | Message | Core | Parse | Client | Test | Notes |
|---|---|---|---|---|---|---|
| 93 | `MSG_EQUIP` | ✅ `card.cpp:1551` | ✅ `loc_info card, loc_info target` | ✅ | ✅ | Bidirectional link tracked on the card objects; not drawn yet. |
| 95 | `MSG_UNEQUIP` | ➖ constant only | ➖ | | | `card::unequip()` emits nothing; hosts learn from `MSG_MOVE`. Parser case, record and codec arm are dead. |
| 96 | `MSG_CARD_TARGET` | ✅ `card.cpp:2345` | ✅ | ✅ | ✅ | Bidirectional link; the target gets the highlight. |
| 97 | `MSG_CANCEL_TARGET` | ✅ `card.cpp:2358` | ✅ | ✅ | ✅ | |
| 101 | `MSG_ADD_COUNTER` | ✅ `card.cpp:2241` | ✅ `u16 type, u8 con, u8 loc, u8 seq, u16 n` | ✅ | ✅ | Per-card counter map; the total is badged on the card. |
| 102 | `MSG_REMOVE_COUNTER` | ✅ 4 sites | ✅ same | ✅ | ✅ | |
| 160 | `MSG_CARD_HINT` | ✅ 5 sites | ✅ `loc_info, u8 type, u64 value` | ✅ | ✅ | `CHINT_TURN` badges the card; `CHINT_DESC_ADD/REMOVE` refcount per card and list in the info banner. Scripts can emit types 0 to 5; 6 and 7 are engine-only. |
| 165 | `MSG_PLAYER_HINT` | ✅ `field.cpp:1332-1402` | ✅ | ✅ | ✅ | `u8 player, u8 type (PHINT_DESC_ADD 6 / REMOVE 7), u64 desc`. Emitted only as a side effect of `EFFECT_FLAG_PLAYER_TARGET \| EFFECT_FLAG_CLIENT_HINT` effects. |
| 161 | `MSG_TAG_SWAP` | ➖ unreachable | | | | `field::tag_swap` returns early unless `OCG_NewCardInfo.duelist > 0`; Duelcraft always passes 0. |
| 162 | `MSG_RELOAD_FIELD` | ➖ Debug only | | | | Only from `Debug.ReloadFieldEnd`. Same payload as `OCG_DuelQueryField` minus the id byte, so a resync could be synthesised from the query instead. |
| 163 | `MSG_AI_NAME` | ➖ Debug only | | | | `u16 len, bytes, u8 0`. |
| 164 | `MSG_SHOW_HINT` | ➖ Debug only | | | | |
| 180 | `MSG_CUSTOM_MSG` | ➖ | | | | No writer in this tree. |

### 1.7 Message parsing work items

- [x] Parse `MSG_CONFIRM_EXTRATOP` (42) by reusing the `CONFIRM_DECKTOP` body; route to the zone inspector.
- [x] Parse `MSG_REVERSE_DECK` (37) as an empty record; track a `deckReversed` flag so `DECK_TOP` can render.
- [x] Parse `MSG_DECK_TOP` (38): `u8 p, u32 offsetFromTop, u32 code, u32 pos`; sanitise for the opponent unless the position is face-up.
- [x] Parse `MSG_RANDOM_SELECTED` (81): `u8 p, u32 n, n×loc_info`; flash the selected cards.
- [x] Parse `MSG_MISSED_EFFECT` (120): `loc_info, u32 code`; show a "missed timing" marker.
- [x] Parse `MSG_REMOVE_CARDS` (190): `u32 n, n×loc_info`; remove from client state.
- [x] Parse `MSG_SHUFFLE_SET_CARD` (36) and re-place face-down cards; treat the second block as an XYZ-material-follow list.
- [x] Parse `MSG_SWAP_GRAVE_DECK` (35): swap the GY list into the deck count and route bitmask-flagged cards to the Extra Deck.
- [x] Decide on `MSG_PLAYER_HINT` (165): parse `u8 player, u8 type, u64 desc` once a UI consumes it.
- [x] Parse `MSG_MATCH_KILL` (170) when match play exists.
- [x] Keep `MSG_DRAW`'s per-card `position` in the record.
- [x] Keep `position` in `SELECT_CHAIN` entries (needed for overlay-material chain options); `ActivatableCard` has no field for it.
- [x] Rename `Battle.atkDamage/defDamage` to destroy flags (naming only; layout is correct).
- [x] Delete the dead `MSG_START` and `MSG_UNEQUIP` parser cases, records, codec arms and client branches. (`DuelMessage.UpdateCard` stays: the per-slot refresh uses it.)

---

## 2. Prompt matrix

Every prompt's engine layout and response reader were checked in `playerop.cpp`. **Response** = byte layout produced by `ResponseBuilder` and its callers; **AI** = `SoloDuelHandler` answer; **Validator** = `ResponseValidator` coverage (dead code in production, see §9); **Tests** = parser / response test.

| #   | Prompt                 | Parse                                                          | Response                                                                                                 | Client UI                                                                                                                                                                                  | AI                                                 | Validator                                                         | Tests                            |
| --- | ---------------------- | -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------- | ----------------------------------------------------------------- | -------------------------------- |
| 10  | `SELECT_BATTLECMD`     | ✅                                                              | 🐞 client sends Activate as type 2 ("to Main Phase 2"); engine wants 0 (`playerop.cpp:56-66`)            | ✅ context menu + M2/EP buttons; `directAttack` flag unused                                                                                                                                 | ✅                                                  | ❌ none                                                            | ✅ / ✅                            |
| 11  | `SELECT_IDLECMD`       | ✅                                                              | ✅ `(index << 16) \| type`                                                                                | ✅ shuffle-hand button when `canShuffle`; several effects on one card open the `desc` option dialog                                                                 | ✅                                                  | ❌ none                                                            | ✅ / ✅                            |
| 12  | `SELECT_EFFECTYN`      | ✅                                                              | ✅                                                                                                        | ✅ desc resolved, prompt card highlighted on the field; card image and zone not shown                                                                                                                                             | ✅ yes                                              | ✅                                                                 | ✅ / ✅                            |
| 13  | `SELECT_YESNO`         | ✅                                                              | ✅                                                                                                        | ✅                                                                                                                                                                                          | ✅ yes                                              | ✅                                                                 | ✅ / ✅                            |
| 14  | `SELECT_OPTION`        | ✅ `u8` count                                                   | ✅                                                                                                        | ✅                                                                                                                                                                                          | ✅ index 0                                          | ✅                                                                 | ✅ / ✅                            |
| 15  | `SELECT_CARD`          | ✅                                                              | ✅ type-0 list, `-1` cancel                                                                               | ⚠️ shared Cancel/Finish button, auto-submit at `max` or when every candidate is picked, field clicks blocked while the dialog is open; dialog mode has no names/stats | ⚠️ one card, ignores `min`                         | ✅                                                                 | ✅ / ✅                            |
| 16  | `SELECT_CHAIN`         | ⚠️ `position` dropped                                          | ✅ `-1` decline                                                                                           | ✅ one image per card, several effects open the `desc` option dialog, hint 550/556; `speCount`/hint timings unused                                                      | ✅                                                  | ✅                                                                 | ✅ / ✅                            |
| 18  | `SELECT_PLACE`         | ✅                                                              | ✅ (engine always sends count 1)                                                                          | ✅ zone highlight; opponent EMZ bits never highlighted                                                                                                                                      | ✅                                                  | ⚠️ `zoneToBit` misses SZONE 6/7                                   | ✅ / ✅                            |
| 19  | `SELECT_POSITION`      | ✅                                                              | ✅                                                                                                        | ✅ card-image buttons, turned sideways for defense, card back for the face-down variants                                                                                                        | ✅                                                  | ✅                                                                 | ✅ / ✅                            |
| 20  | `SELECT_TRIBUTE`       | ✅                                                              | ✅                                                                                                        | ✅ auto-submit only at `max` cards; Finish once the summed tribute meets `min`, so over-tributing stays reachable                                                                                               | ⚠️ one card, ignores `min`                         | 🐞 checks card count where the engine checks the tribute sum      | ✅ / shared                       |
| 21  | `SORT_CHAIN`           | ✅ `u32` location                                               | ✅ bytes; javadoc describes the permutation backwards (`response[i]` is the destination rank of card `i`) | ✅                                                                                                                                                                                          | ✅ `-1` default                                     | ✅                                                                 | ✅ / ✅                            |
| 22  | `SELECT_COUNTER`       | ✅                                                              | ✅ `u16[n]`                                                                                               | ✅                                                                                                                                                                                          | ⚠️ all from card 0 (hangs if card 0 holds too few) | ✅ (no per-card cap)                                               | ✅ / ✅                            |
| 23  | `SELECT_SUM`           | ✅                                                             | ✅                                                                                                       | ✅                                                                                                                                                                                          | ✅                                                  | ❌ none                                                            | ✅ / ✅                            |
| 24  | `SELECT_DISFIELD`      | ✅ own `SelectDisfield` record, keeps type 24 on the wire | ✅ `selectPlaces` sends `count` triples                                           | ✅ collects `count` zones, "become unusable" caption                                                                                                                             | ✅                                                  | ⚠️ single triple                                                  | ❌ / shared                       |
| 25  | `SORT_CARD`            | ✅                                                              | ✅ (javadoc inverted)                                                                                     | ✅                                                                                                                                                                                          | ✅ `-1` default                                     | ✅                                                                 | ✅ / ✅                            |
| 26  | `SELECT_UNSELECT_CARD` | ✅                                                              | ✅ `[1][index]`, `[-1]` finish                                                                            | ✅ per-click; caption shows the running count and `min`/`max`                                                                                                                                                   | ✅                                                  | 🐞 rejects legal unselect-list indices and `cancelable`-only `-1` | ✅ / ✅                            |
| 132 | `ROCK_PAPER_SCISSORS`  | ✅                                                              | ✅ `int32` 1..3                                                                                           | ✅                                                                                                                                                                                          | ✅ rock                                             | ✅                                                                 | ✅ / ✅                            |
| 140 | `ANNOUNCE_RACE`        | ✅ `u64` mask                                                   | ✅ `int64`                                                                                                | ✅ checkbox grid, no OK button                                                                                                                                                              | ✅                                                  | ✅                                                                 | ✅ / ✅                            |
| 141 | `ANNOUNCE_ATTRIB`      | ✅ `u32` mask                                                   | ✅ `int32`                                                                                                | ✅ checkbox grid, no OK button                                                                                                                                                              | ✅                                                  | ✅                                                                 | ✅ / ✅                            |
| 142 | `ANNOUNCE_CARD`        | ✅ `u8 count` + `u64` opcodes                                    | ✅ `int32` code                                                                                           | ✅                                                                                                                | ✅ human declares for the AI (no server-side card DB in test mode) | ❌                                                                 | ✅ / ✅                            |
| 143 | `ANNOUNCE_NUMBER`      | ✅                                                              | ✅ `int32` index                                                                                          | ✅ reuses `buildOptionPrompt`                                                                                                                                                               | ✅ index 0                                          | ✅                                                                 | ✅ / ✅                            |

### 2.1 Engine facts a future UI must respect

- `SELECT_CARD`, `SELECT_TRIBUTE`, `SELECT_SUM`, `SELECT_UNSELECT_CARD` share `parse_response_cards` (`playerop.cpp:238-272`): `int32` type at offset 0 (`-1` cancel, `0` u32 indices, `1` u16, `2` u8, `3` bitfield from byte 4), `u32` count at offset 4, indices from offset 8. Duplicates are rejected after sorting, so selection order is not preserved. Indices always address `core.select_cards`; must-select cards are never indexed.
- `SELECT_TRIBUTE`: `max` bounds the card count, `min` bounds the summed `release_param`. Over-tribute is legal.
- `SELECT_SUM`: mode byte is `0` when `max != 0` (exact sum over `[min, max]` picks) and `1` when `max == 0` (greater-or-equal mode). Per card `sum_param` packs two values (`o1 = low 16`, `o2 = high 16`). No cancel.
- `SELECT_UNSELECT_CARD`: one card per round trip; `-1` is accepted when `cancelable || finishable`; indices `≥ selectable.size()` address the unselect list.
- `SELECT_PLACE`/`SELECT_DISFIELD`: a **set** bit means blocked. Bit = `seq + (SZONE ? 8 : 0) + (opponent of the prompted player ? 16 : 0)`; SZONE 5 is the field zone, 6 and 7 the pendulum zones. Response is `count` triples of `u8 player, u8 location, u8 seq`; the same zone cannot appear twice. `DISFIELD` count comes from `dis_count` (up to 5) and Lua callers.
- `SORT_CARD`/`SORT_CHAIN`: `int8` per card, `response[i]` = destination rank of original card `i`; a leading `-1` skips sorting.
- `SELECT_COUNTER`: `int16` per card in prompt order; per card `≤` its current counters; total must equal `count` exactly.
- `ANNOUNCE_CARD`: `u8 player, u8 count, u64 opcodes[count]` (a postfix filter evaluated by `is_declarable`, `playerop.cpp:1004-1069`); response is one `int32` card code. A usable UI needs an opcode evaluator over the client card database or, at minimum, the common `OPCODE_ISSETCARD`/`ISTYPE`/`ISRACE` shapes.
- `ANNOUNCE_NUMBER` answers with the option **index**, not the number. `ROCK_PAPER_SCISSORS` needs a 4-byte `int32`; a 1-byte response reads as 0 and retries.
- `SELECT_OPTION`, `SELECT_EFFECTYN`, `SELECT_YESNO`, `SELECT_POSITION`, `SELECT_SUM` have no cancel encoding.

### 2.2 Prompt work items

- [x] `SELECT_SUM`: fix `SumCard.read` to `code u32, con u8, loc u8, seq u32, position u32, sumParam u32` (§3.1); send indices into the selectable list only; honour `selectMode` and `value2`; fix `MessageParserTest.parseSelectSum` to encode the engine layout.
- [x] `SELECT_BATTLECMD`: Activate must send type 0 (`ClientDuelState.buildBattleCmdActions`, `ClickDispatcher.getActionIconInfo`).
- [x] `SELECT_DISFIELD`: give it its own record or a `type` field so the wire keeps id 24; add `selectPlaces(count, triples…)` to `ResponseBuilder`; UI that collects `count` zones and shows "disable" semantics.
- [x] `SELECT_COUNTER` UI: per-card stepper, total must equal `count`.
- [x] `SORT_CARD` / `SORT_CHAIN` UI: reorderable list plus a "keep order" button that sends `sortCardsDefault()` (the button alone unblocks duels).
- [x] `ANNOUNCE_RACE` / `ANNOUNCE_ATTRIB` UI: checkbox grid limited to `available`, exactly `count` picks; names from `SystemStringTable`.
- [x] `ANNOUNCE_NUMBER` UI: reuse `buildOptionPrompt` over the `List<Long>`.
- [x] `ANNOUNCE_CARD`: parse opcodes, add `ResponseBuilder.announceCard(code)`, add the solo AI case, build a name-search UI with an opcode filter.
- [x] `SELECT_CARD`: add Confirm in field mode when `min < max`; guard `handleFieldClick` while the dialog is open; caption it with `HINT_SELECTMSG`.
- [x] `SELECT_TRIBUTE`: Confirm button instead of auto-submit at `min`.
- [x] `SELECT_CHAIN`: label options with `desc` text; handle forced + empty list. (`speCount` carries no text worth showing: it is either a count of special summons or the 0x7f trigger marker.)
- [x] `SELECT_IDLECMD`: offer shuffle-hand when `canShuffle`; label duplicate effects with `desc`.
- [x] `SELECT_UNSELECT_CARD`: show `min`/`max` progress.
- [x] `SELECT_POSITION`: card-image buttons (existing TODO).
- [x] Solo AI: respect `min` for `SELECT_CARD`/`SELECT_TRIBUTE`/`SELECT_SUM`, per-card caps for `SELECT_COUNTER`, and re-answer after `MSG_RETRY`.
- [x] Fix the `sortCards` javadoc direction.

---

## 3. Verified correctness defects

Ranked by gameplay impact. Each was confirmed against the engine source at the cited lines.

### 3.1 `SELECT_SUM` is mis-parsed and mis-answered
- [x] **Parse.** Engine writes per card `u32 code, loc_info (con u8, loc u8, seq u32, pos u32), u32 sum_param` (`playerop.cpp:808-819`). `SumCard.read` reads `code, u8, u8, i32, i64`, folding `position` and `sum_param` into one `long`. Both are 18 bytes, so the drift check stays silent and the existing test encodes the wrong layout. `value1()` returns position bits (1 for face-up attack), `value2()` is always 0. The "Sum: X / Y" display and the confirm gate are computed from position flags.
- [x] **Response.** `PromptController` sends `0..mustCount-1` plus `mustCount + idx`; the engine indexes `core.select_cards` alone (`playerop.cpp:820-838`, `parse_response_cards`). Every pick is shifted by the must-select count.
- [x] `min`/`max` are applied to selectable picks only, not to `mustSelect.size() + picks`.

### 3.2 Battle-phase Activate sends the wrong action type
- [x] `ClientDuelState.buildBattleCmdActions` and `ClickDispatcher.getActionIconInfo` use 2 for Activate; the engine reads 0 activate, 1 attack, 2 to M2, 3 end battle (`playerop.cpp:55-67`). Effects cannot be activated in the Battle Phase; when M2 is unavailable the engine answers `MSG_RETRY`.

### 3.3 `MSG_RETRY` hides the prompt
- [x] `LDLibDuelScreen.sendResponse` nulls `pendingPrompt` before the engine answers; `Retry` sets `PROMPT` dirty and `PromptController.rebuild` hides on null. The engine never re-sends the `SELECT_*` (`playerop.cpp:154, 178, 199, 590` return `FALSE` after `MSG_RETRY`). edopro does not recover: its host ends the duel on `MSG_RETRY` (`generic_duel.cpp:836-841`, recorded as a draw) and its client only shows an error popup (`duelclient.cpp:1348`); it relies on client-side validation so a retry never happens. Duelcraft needs one of the two: restore the last prompt on `Retry`, or validate before sending (the original purpose of `ResponseValidator`). Compounded by 3.2 and 3.5.
- [x] Server broadcasts `Retry` to both players; only the responder needs it.

### 3.4 XYZ overlay moves never apply on the client
- [x] `card::get_info_location()` reports a material as `{host con, host loc | LOCATION_OVERLAY, host seq, material seq}` (`card.cpp:216-222`), so `location` is `0x84`/`0x88`, never `0x80`. `ClientDuelState.markLocationDirty/removeCard/placeCard` switch on `case LOCATION_OVERLAY` (exact `0x80`) and fall to `default`.
- [x] `DuelMessageCodec.writeLocInfo/readLocInfo` (and every card-list writer except `SortableCard`) store `location` with `writeByte`/`readByte`; values `≥ 0x80` decode negative. Use `readUnsignedByte` throughout.
- [x] `FieldRenderer` never reads `state.overlay`, so materials would be invisible even once tracked. Detach removes the last material regardless of which one left.

### 3.5 `SELECT_DISFIELD` with `count > 1` deadlocks
- [x] `ResponseBuilder.selectPlace` emits one triple and `PromptController` never reads `sel.count()`. `processor.cpp:4579` passes `dis_count` (up to 5) and `libduel.cpp:3206, 3247` pass Lua-supplied counts. Short responses read as zeros, `location == 0` fails validation, `MSG_RETRY` forever.
- [x] The record is re-tagged as `MSG_SELECT_PLACE` on the wire (`SelectPlace.type()` is constant), so the client cannot tell the two apart.

### 3.6 Hidden information leaks
- [x] **`MSG_MOVE` to deck or hand.** `field::send_to` sets `pos = POS_FACEUP` for every destination except `LOCATION_REMOVED` (`operations.cpp:327-328`), so `ServerDuelHandler.hideInfo`'s face-down test passes the real code of a searched or returned card to the opponent. edopro's rule (`generic_duel.cpp:1032`): hide for the non-controller when `!(loc & (GRAVE|OVERLAY)) && ((loc & (DECK|HAND)) || (pos & FACEDOWN))`.
- [x] `MSG_CONFIRM_CARDS` is broadcast to both players; edopro sends it only to the target player when the revealed cards sit in the deck or extra deck (`generic_duel.cpp:985-1004`), so a private deck peek reaches the opponent here.
- [x] **Prompt payloads are unsanitised.** edopro zeroes the code of every `SELECT_CARD`, `SELECT_TRIBUTE` and `SELECT_UNSELECT_CARD` candidate the prompted player does not control (`generic_duel.cpp:933-977`); Duelcraft forwards the engine's codes, so a face-down opponent card offered as a target leaks.
- [x] `SoloDuelHandler` performs no sanitisation; the human sees the AI's face-down and hand codes (test mode only).
- [x] `sanitizeCard` keeps `flags` while zeroing values, so a recipient sees `QUERY_ATTACK` "present" with `attack == 0`.
- [x] `QUERY_IS_HIDDEN` (`EFFECT_DARKNESS_HIDE`) is neither requested nor honoured.

### 3.7 Seven prompts have no UI
- [x] `SelectCounter`, `SortCard`, `SortChain`, `AnnounceRace`, `AnnounceAttrib`, `AnnounceNumber`, `AnnounceCard` hit `PromptController.rebuild`'s `default`: a full-screen dimmed overlay titled with the class name, no buttons, no timeout. The only exit is `/duel forfeit`. In `/duel test` the solo AI masks all but `AnnounceCard` when the AI is the one prompted.

### 3.8 `ANNOUNCE_CARD` has no response path at all
- [x] Not parsed (raw body), no `ResponseBuilder` method, no UI, no solo AI case. Any card-declaring effect (e.g. "Prohibition", "Mind Crush") wedges the duel for both human and AI.

### 3.9 Smaller verified defects
- [x] `ResponseValidator.selectTribute` requires card count `≥ min`; the engine requires summed `release_param ≥ min` (`playerop.cpp:688-700`).
- [x] `ResponseValidator.selectUnselectCard` rejects indices in the unselect list and rejects `-1` when only `cancelable` is set.
- [x] `ResponseValidator.zoneToBit` returns `-1` for SZONE 6/7 (pendulum zones), skipping validation.
- [ ] `FieldRenderer.getFieldBit` bases the `SELECT_PLACE` bitmask on `localPlayer` rather than `sel.player()`; equal today because prompts only reach their target.
- [x] `FieldRenderer.highlightValidPlaces` reads EMZ bits only from the viewer block (5/6); a prompt whose only legal zones are the opponent-side EMZ bits (21/22) shows nothing.
- [x] `ConfirmDeckTop`/`ConfirmCards` reveal is overwritten by the next `PILE_COUNTS` refresh because `showConfirmCards` does not reset the inspected pile.
- [ ] `OcgCoreTest` reads `MSG_WIN` as `u8 + u32`; the body is `u8 + u8`. Latent `BufferUnderflowException` if a test duel ends.
- [ ] `card_database.cpp:49` comment describes the lscale/rscale bit ranges backwards; the code is right (lscale bits 24-31, rscale 16-23).

---

## 4. Wire codec (`DuelMessageCodec`)

- [x] Every one of the 74 records (73 typed + `Raw`) encodes and decodes; `encode` is an exhaustive switch over the sealed interface, so a new record without a codec arm fails compilation.
- [x] `u64` fields (`desc`, hint data, `opParam`, race masks) travel as `long`.
- [x] `location` bytes are read signed (§3.4); `SortableCard` is the only writer using a full `int`.
- [x] `MSG_SELECT_DISFIELD` ships as type 18 (§3.5).
- [ ] No version byte and no bound on `readByteArray` lengths.
- [x] `decode`'s `default -> Raw(type, readByteArray(buf))` is unreachable today and would mis-frame the buffer if it ever fired.
- [x] Zero tests. A round-trip test over every record, including a `LocInfo` with location `0x84`, would have caught §3.4.

---

## 5. Query pipeline (`DuelSession.sendFieldStats` → `FieldQuery` → `UpdateData`)

### 5.1 `QUERY_*` flag coverage

| Flag | Engine data | Requested | Parsed | Consumed by the client |
|---|---|---|---|---|
| `CODE` | u32 | ✅ | ✅ | ✅ extra deck rebuild |
| `POSITION` | u32 | ✅ | ✅ | ✅ face-down test, extra deck |
| `ALIAS` | u32 | ✅ | ✅ | ❌ |
| `TYPE` | u32 | ✅ | ✅ | ❌ (Link detection uses the local DB instead) |
| `LEVEL` / `RANK` | u32 | ✅ | ✅ | ✅ shown only when changed from the printed value |
| `ATTRIBUTE` / `RACE` | u32 / u64 | ✅ | ✅ | ❌ |
| `ATTACK` / `DEFENSE` | u32 | ✅ | ✅ | ✅ (`-2` renders as "?") |
| `BASE_ATTACK` | u32 | ✅ | ✅ | ✅ drives buffed/debuffed colour |
| `BASE_DEFENSE` | u32 | ✅ | ✅ | ❌ |
| `REASON` | u32 | ✅ | ✅ | ❌ |
| `REASON_CARD` / `EQUIP_CARD` | 10-byte loc_info (zeros when absent) | ❌ | ✅ (zeros → null) | ❌ |
| `TARGET_CARD` | u32 n + n×loc_info | ❌ | ✅ | ❌ |
| `OVERLAY_CARD` | u32 n + n×u32 code | ❌ | ✅ | ✅ sets the materials' codes |
| `COUNTERS` | u32 n + n×u32 `type \| count << 16` | ❌ | ✅ | ✅ assigns the counter map |
| `OWNER` | u8 | ❌ | ✅ | ❌ |
| `STATUS` | u32 | ✅ | ✅ | ❌ |
| `IS_PUBLIC` | u8 | ✅ hand, pile, single | ✅ | server sanitiser only |
| `LSCALE` / `RSCALE` | u32 | ✅ spell zone, hand, single | ✅ | ✅ scale badge on the slot |
| `LINK` | u32 link + u32 marker | ✅ | ✅ | ❌ |
| `IS_HIDDEN` | u8 | ✅ | ✅ | server sanitiser only |
| `COVER` | u32 | ✅ | ✅ | ❌ |
| `END` | terminator | n/a | ✅ | |

### 5.2 Behaviour and gaps

- [x] Per-slot `OCG_DuelQuery` with `u16 size, u32 flag` blocks; `FieldQuery` resyncs on each block's declared size, so an unknown flag cannot shift later fields.
- [x] Empty slot (`length == 0`) handled as `null`.
- [x] Only `MZONE` (7 slots, hardcoded), `SZONE` (8, hardcoded) and `EXTRA` are refreshed, for both players, after **every** engine pause (≥30 JNI calls and 2 to 3 payloads per player per pause). `HAND`, `GRAVE`, `REMOVED`, `DECK`, `OVERLAY` are never refreshed; those piles live on `MOVE`/`DRAW` deltas alone. edopro refreshes per event: hand after `DRAW`, a single slot after `MOVE`/`POS_CHANGE` face-up, extra after `SHUFFLE_EXTRA`.
- [x] `UpdateData` updates `mzoneStats`/`szoneStats` only; it never writes `code`/`position` back into `mzone[]`/`szone[]`, so movement-tracked state cannot self-heal. `szoneStats` is populated and never read.
- [x] `UpdateData` is sent **after** the prompt it should precede (early-return path in `process()`). Now `RefreshSchedule.before(msg)` runs ahead of the message.
- [ ] The empty-chain auto-pass `break`s out of the batch, discarding already-parsed trailing messages. Left as `break`: `OCG_DuelProcess` returns as soon as a processor unit needs an answer (`ocgapi.cpp:115-118`, `processor_visit.cpp:14-21`), so the prompt is always the last record of its batch and nothing trails it.
- [ ] `OCG_DuelQueryField` (full snapshot: `u32 flags`, per player `u32 lp`, 7 + 8 slot records, six pile counts, chain links) is exposed but unused. `QueryField` is the natural basis for reconnect/ESC-reopen resync. (`OCG_DuelQueryLocation` now backs every whole-location refresh.)
- [x] `FieldQuery` stops silently on a truncated trailing block (`remaining() >= 6` guard) with no error for a missing `QUERY_END`. Now throws; the caller drops that one refresh.
- [x] `FieldQuery.parse` is never run against real engine output; `OcgCoreTest` never calls `nDuelQuery`.
- [x] edopro's schedule and masks (§12.2) differ on four points: the hand is refreshed before every idle, battle and chain prompt (with `IS_PUBLIC`, `LSCALE`, `RSCALE`, `IS_HIDDEN`, `COVER`); field zones are refreshed after state-changing messages rather than after every pause; overlays, counters, equips and targets are tracked from messages and never queried; private fields are omitted from the buffer rather than zeroed.

---

## 6. Engine API and duel options

### 6.1 `OCG_*` surface

| Function | Bridge | `OcgCore` | Production use |
|---|---|---|---|
| `OCG_GetVersion` | ✅ | `nGetVersion` | ✅ logged at init |
| `OCG_CreateDuel` / `OCG_DestroyDuel` | ✅ | ✅ | ✅ |
| `OCG_DuelNewCard` | ✅ | ✅ | ✅ deck + extra, `duelist = 0`, `seq = 0`, main deck added in reverse so index 0 is drawn first |
| `OCG_StartDuel` | ✅ | ✅ | ✅ |
| `OCG_DuelProcess` / `OCG_DuelGetMessage` / `OCG_DuelSetResponse` | ✅ | ✅ | ✅ |
| `OCG_LoadScript` | ✅ internal to `ScriptProvider` | ➖ | `constant.lua`, `utility.lua` at creation; result ignored |
| `OCG_DuelQueryCount` | ✅ | ✅ | ⚠️ extra deck slot count only |
| `OCG_DuelQuery` | ✅ | ✅ | ✅ single-slot refreshes |
| `OCG_DuelQueryLocation` | ✅ | ✅ | ✅ whole-location refreshes |
| `OCG_DuelQueryField` | ✅ | ✅ | ❌ unused (smoke-tested only) |

### 6.2 Options, readers, logging

- [x] `OCG_DuelOptions` fully populated: `seed[4]` from `SeedExpander`, `flags` from `DuelRule`, both teams' LP/hand/draw, all four callbacks, `enableUnsafeLibraries = 0`.
- [x] All 37 `DUEL_*` flags, 8 `DUEL_MODE_*` presets and 5 `_FORB` sets mirrored with correct values (`DUEL_6_STEP_BATLLE_STEP` renamed to fix the engine typo).
- [x] Card reader fills every `OCG_CardData` field: setcodes unpacked to `u16[]` with terminator, `race` as u64, level/lscale/rscale unpacked, `def` used as `link_marker` for `TYPE_LINK`.
- [ ] Only the 8 presets are selectable (`/duel … [rule]`); individual flags (`DUEL_TEST_MODE`, `DUEL_ATTACK_FIRST_TURN`, `DUEL_PSEUDO_SHUFFLE`, …) cannot be composed. Client reads 4 flags for layout.
- [ ] Starting LP 8000, hand 5, draw 1 hardcoded in `PlayerOptions.standard()`; no asymmetric team options; LP bars hardcode `max-value="8000"`.
- [ ] No banlist, no deck legality (size, copies, rule-set forbidden types); `DUEL_MODE_MR*_FORB` declared and unused.
- [ ] No match / best-of-3.
- [ ] Card database `open()` failure and unknown card codes are silent (blank card, no log). Missing `constant.lua`/`utility.lua` or a missing `cXXXX.lua` is silent; the card becomes effect-less.
- [ ] `ScriptProvider` caches nothing; lookup is a flat string join over the search paths.
- [ ] `OCG_LogHandler` prints to `stderr` with the `type` ignored; `nSetLogHandler` is commented out. Script errors never reach the Java logger or carry a duel id.
- [ ] `TYPE_LINK` literal in `card_database.cpp` duplicates `OcgConstants`.

---

## 7. Server flow and lifecycle

### 7.1 Routing (`ServerDuelHandler`)

| Recipient | Messages |
|---|---|
| Prompted player only, unsanitised | all 21 prompt records (correct: prompts only reach their target) |
| Both, sanitised per recipient | `Draw`, `Move`, `ShuffleHand`, `Set`, `UpdateData`, `UpdateCard`, `PosChange` |
| Both, verbatim | everything else including `Retry`, `Hint`, `ConfirmCards`, `BecomeTarget`, `TossCoin/Dice`, and all `Raw` |
| Converted | `Win` → `DuelEndPayload` to both, return 2 |

### 7.2 Gaps

- [x] Both decks shuffled Java-side (team 1 with `seed`, team 2 and the solo AI with `seed + 1`); engine never shuffles the starting deck.
- [x] Player indices stay absolute on the wire; the client mirrors from `DuelStartPayload.localPlayer`.
- [x] **No response ownership check.** `DuelManager.handleResponse` applies any player's bytes to whatever prompt is pending; a player can answer the opponent's prompt. `ResponseValidator` has zero production callers.
- [x] **Forfeit sends no `DuelEndPayload`.** `DuelManager.endDuel` closes the session silently; both screens stay open on a dead duel and later responses are dropped. Same for an engine `DUEL_STATUS_END` without `MSG_WIN`.
- [x] **No disconnect handling.** No logout listener; a quitting duellist leaves the session alive and the opponent stuck. `duelInvites` never expires (`FIXME` in `DuelManager`).
- [ ] `ServerPlayer` references captured at construction go stale on respawn or dimension change.
- [x] `MSG_RETRY` is broadcast to both players.
- [x] `/duel challenge` does not check that either side has a deck; failure surfaces at `accept`.
- [ ] Solo AI: `handleSoloAutoResponse` → `process()` → `routePrompt` → … is directly recursive; a long AI chain grows the stack. The `activeDuels` loop and "need a way to get the handler" comments are scaffolding.
- [ ] A prompt with no `routePrompt`/`onMessage` case returns 0, so `process()` exits with `AWAITING` and waits forever (same class as §3.7).
- [ ] No threading guards anywhere in `duel/`, `server/`, `core/`; `ServerPayloadHandler` does not `enqueueWork`. Everything assumes the server thread.
- [ ] **First-turn choice.** edopro runs rock-paper-scissors between the players before creating the duel and lets the winner choose who goes first, swapping teams so the first player is engine player 0 (§12.3). Duelcraft: the challenger always goes first; the engine's own `ROCK_PAPER_SCISSORS` prompt is script-driven and unrelated.
- [ ] **`MSG_WAITING` and time limit.** edopro's host tells the non-prompted player it is waiting and runs a per-turn clock that ends the duel on expiry (§12.3). Duelcraft has neither; its "Waiting…" label is driven by a dirty flag that only fires on chain events.

---

## 8. Client state and rendering gaps

Non-prompt records: 22 handled, 18 deliberate no-ops, 10 dropped (§1). Structural gaps beyond the per-message rows:

- [x] **Card object model.** edopro moves one card object between containers, so counters, equip links, targets and materials travel with it (§12.4). Duelcraft's parallel code/position arrays are the root cause of the `Swap`, overlay and counter gaps below.
- [x] **Win/lose.** `DuelEndPayload` closes the screen at once; the win overlay code path is unreachable. Wanted: a result overlay with winner and reason, a concede button, and `LDLibDuelScreen.close()` on every exit so statics do not linger.
- [x] **ESC** closes the screen with no way back (`shouldCloseOnEsc` inherited); incoming messages accumulate into an unrendered state. Needs `shouldCloseOnEsc = false` or `/duel show` rebuilding from `ClientDuelState` (or a `QueryField` resync).
- [x] **No duel log.** A `DuelLog` panel, toggled from the HUD, lists attacks, coin and dice results, chain activations and negations, targets, equips, LP changes with their reason, summons, missed timing, turns, phases and the result; a line naming a card opens it in the info banner.
- [x] **Overlay materials** tracked (badly, §3.4) and never drawn. Now stored on the host card and badged with the material count.
- [x] **Counters** have no client state. Now a per-card map, badged with the total.
- [x] **Equip and target links** not tracked or drawn. Both are tracked bidirectionally now; only the target highlight is drawn, equip links still are not.
- [x] **Targeting highlight** (`BecomeTarget`, `CardSelected`) drawn until the next prompt; `RandomSelected` is still unparsed.
- [x] **Disabled zones**: `FieldDisabled` greys the zone out; `HINT_ZONE` flashes the zones it names.
- [x] **Chain visualisation**: numbered markers on the trigger slot, a stamp while a link resolves and another when it is negated or disabled; `ChainSolved` pops the link.
- [x] **Battle feedback**: the two ends of an attack are marked for ~1.5 s, `MSG_BATTLE`'s combat ATK/DEF replace the queried stats for the damage step, and each LP change floats a signed number in its reason's colour. A drawn arrow and real animation are still out of scope.
- [x] **Pendulum scales** never shown (`LSCALE`/`RSCALE` not requested). Now requested for the spell zones and badged on the slot.
- [x] **Card hints** (`CHINT_TURN` counters, `CHINT_DESC_ADD`) not shown. Now a badge and info-banner lines.
- [x] **Hint captions**: prompt titles are hard-coded ("Select 1-1 card(s)") instead of `HINT_SELECTMSG`. Now the hint wins wherever edopro uses `select_hint`.
- [ ] `DuelStartPayload` initialises both players' deck counts from the recipient's own deck; since `MSG_START` never arrives, asymmetric deck sizes stay wrong. `extraPos[]` is filled only in the dead `Start` branch.
- [x] `Swap` handles MZONE↔MZONE only and does not move stats or overlays.
- [ ] `Move.reason` ignored (no destroy/banish/return distinction).
- [x] Battle sub-phases collapse to "Battle"; no phase-track widget. There is nothing to expand: all ten `MSG_NEW_PHASE` sites (`processor.cpp:2787, 2793, 2832, 2858, 3365, 3414, 3450, 3469, 3562, 3580`) announce only DRAW, STANDBY, MAIN1, BATTLE_START, MAIN2 and END, so `BATTLE_STEP`/`DAMAGE`/`DAMAGE_CAL`/`BATTLE` never reach the client. A phase-track widget is still open.
- [x] Status label is written by both `updateStatusLabel()` (on `CHAIN` only) and the prompt controller; they overwrite each other. The chain count moved to `#chain-count` and `updateStatusLabel()` leaves the label alone while a prompt is up.
- [ ] Mouse only; no keyboard handling.
- [ ] Untextured cards render as the card back, indistinguishable from face-down cards.
- [ ] Longer-term (carried from the old checklist): animations, sound, deck editor, replay, spectator mode, match mode.

---

## 9. Dead code inventory

- [x] `MSG_START`: `parseStart`, `DuelMessage.Start`, codec arm, `ClientDuelState` branch (engine never emits it).
- [x] `MSG_UNEQUIP`: parser case, `DuelMessage.Unequip`, codec arm.
- [x] `MessageParser.readActivatableList` (no callers).
- [x] `ResponseValidator` (42 tests, zero production callers; several rules contradict the engine, §3.9). Decided: kept and wired into every client send path, so a response the engine would reject is reported on the status label instead of sent.
- [x] `DuelSession.queryLocation/queryField` wrappers (test-only). `queryField` deleted; the refresh emitters now go through `queryLocation`/`query`.
- [x] `ClientDuelState`: `DirtyFlag.LP`, `startingLP`, `lastAction`, `CardAction.label`, `szoneStats`, `lastHintType/Data` deleted. (`winReason` stays: the result overlay names the reason.)
- [x] `PromptController.showWinOverlay` (unreachable until `Win` or `DuelEndPayload` triggers it).
- [x] `CardInfo.linkArrows/leftScale/rightScale` (no callers).
- [ ] `FieldQuery` cases for `ALIAS`, `REASON`, `LSCALE`, `RSCALE`, `COVER` are exercised only by tests until requested.
- [ ] `DuelMessageCodec.encode`'s `Raw` throw arm is required for the sealed switch to be exhaustive; `decode`'s `default` now throws `DecoderException` deliberately. Neither is deletable.
- [x] Magic numbers instead of constants: `ResponseValidator.zoneToBit` (`0x04`/`0x08`), `SoloDuelHandler` (`0x04`, `0x08`, `POS_*` literals), unnamed idle/battle action ordinals in `LDLibDuelScreen` and `ClientDuelState`, `DuelEventListener.onMessage` return codes 0/1/2. (`IdleAction`/`BattleAction` now live in `OcgConstants` so the server can name them too.)

---

## 10. Test coverage

- [x] 66 of 72 parser cases have byte-level tests; all 366 shared constants match the engine header (values checked, including octal link markers).
- [x] **Parsed but untested (28):** `RETRY`, `SELECT_BATTLECMD`, `SELECT_IDLECMD`, `SORT_CHAIN`, `SELECT_COUNTER`, `SELECT_DISFIELD`, `SORT_CARD`, `SWAP`, `FIELD_DISABLED`, `SPSUMMONING`, `SPSUMMONED`, `FLIPSUMMONING`, `FLIPSUMMONED`, `CHAIN_SOLVED`, `CHAIN_DISABLED`, `CARD_SELECTED`, `BECOME_TARGET`, `UNEQUIP`, `CARD_TARGET`, `CANCEL_TARGET`, `PAY_LPCOST`, `ADD_COUNTER`, `REMOVE_COUNTER`, `TOSS_DICE`, `ANNOUNCE_ATTRIB`, `ANNOUNCE_CARD`, `ANNOUNCE_NUMBER`, `CARD_HINT`. The two idle/battle command parsers (six and two variable-length lists with three entry widths) are the highest-risk untested code.
- [x] `MessageParserTest.parseSelectSum` encodes the parser's wrong layout; rewrite from the engine layout (§3.1). Add an offset-level check, not just entry size, for every card-list parser.
- [x] `DuelMessageCodec`: no tests at all. Add a round trip over every record with overlay locations (`0x84`) and `u64` descs.
- [x] `ResponseBuilder`: `selectCardsCancel`, `sortCardsDefault` untested. `ResponseValidator`: `selectCmd`, `selectSum`, `announceCard` have no methods; `sortChain` untested.
- [x] `FieldQueryTest`: add `RACE` u64, `OVERLAY_CARD`, `COUNTERS`, `TARGET_CARD`, `REASON_CARD`/`EQUIP_CARD` present and absent, empty buffer, missing `QUERY_END`.
- [x] `OcgCoreTest`: never calls `nDuelQuery`, so `FieldQuery` is never run on real engine bytes; re-declares ~30 constants locally instead of importing `OcgConstants`; `MSG_WIN` read is wrong (§3.9). `testQueryField` still asserts only non-empty.
- [x] A live test that sets up a `DuelSession`, queries a face-down deck card with `QUERY_IS_PUBLIC`, and asserts the sanitiser strips it.
- [x] A test cross-checking `OcgConstants` against `ocgapi_constants.h` at build time (idea from the walkthrough).
- [ ] CI: tests need EDOPro data and the native build is MSBuild-only; `-PskipNative` plus `assumeTrue` on data paths (decided, unscheduled).

---

## 11. Recommended order

1. **Unblock real duels.** §3.2 Activate code (two constants), §3.3 keep the prompt across `Retry` or validate before sending, §3.1 `SELECT_SUM` parse and indices, §3.5 `DISFIELD` count, `ANNOUNCE_CARD` response path plus solo AI case. Add edopro's `answered` guard and send-after-close so a double click cannot answer twice (§12.5). Each is small and each currently ends a duel.
2. **Stop the leaks.** §3.6: the `MSG_MOVE` rule, `CONFIRM_CARDS` deck/extra routing, prompt candidate codes; route hints by type; keep face-up draws visible (§12.1).
3. **Codec hardening.** Unsigned `location` bytes, keep type 24 on the wire, round-trip test for all 74 records.
4. **Seven prompt UIs**, following edopro (§12.5): `SelectCounter` needs no dialog (one click per counter, live caption); `SortCard`/`SortChain` use the card list with click order as rank and right-click to skip, auto-declining `SortChain` behind a setting; `AnnounceNumber` reuses the option dialog and answers with the index; `AnnounceRace`/`AnnounceAttrib` are checkbox grids that submit at exactly `count`; `AnnounceCard` is a search box over a port of `is_declarable`.
5. **Lifecycle.** Forfeit and engine-end send `DuelEndPayload`; logout listener; response ownership check; result overlay and concede button (edopro has only a retitled leave button, so this is an original design); ESC handling; rock-paper-scissors with the winner choosing who goes first; `MSG_WAITING` for the idle player; `QueryField`-based resync.
6. **Card object model, then overlays, counters, links.** Replace the parallel code/position arrays with card objects (§12.4); track materials, counters, equips and targets from messages as edopro does; fix the `0x84` switch (§3.4); apply `BecomeTarget` and `FieldDisabled`; move the query pipeline to edopro's schedule and masks (§12.2), including the hand, and omit private fields instead of zeroing them.
7. **Remaining unparsed messages** (§1.7) and the hint surfaces: `HINT_SELECTMSG` captions, `HINT_OPSELECTED`/`RACE`/`ATTRIB`/`CODE`/`NUMBER` toasts, `HINT_MESSAGE` modal, `HINT_ZONE` flash.
8. **Dead-code sweep** (§9) and the `ResponseValidator` decision, informed by edopro's model of validating client-side so a retry never happens.
9. **Feedback layer.** Duel log with click-to-view codes, chain link markers at the trigger location, attack arrow and battle stats, LP numbers in red/green/blue, turn and phase banners, targeting highlights, coin and dice toasts.
10. **Options.** Composable `DUEL_*` flags, LP/hand/draw, deck legality and banlist per `CheckDeckContent`, match mode with side decking.

---

## 12. edopro reference behaviour

edopro's host (`generic_duel.cpp`) and client (`duelclient.cpp`, `client_field.cpp`) sit on the same engine and are the design authority for everything the engine leaves to the host: routing, hidden information, refresh timing, lobby flow and prompt UX. This section records what they do and where Duelcraft diverges. Line numbers are edopro's.

### 12.1 Host routing and hidden information

edopro serialises each message once, sends it to the owning team, mutates the buffer in place, and sends the downgraded copy to the other team and all observers (`generic_duel.cpp:1023-1041` is the canonical shape). A full-information copy is taken **before** any mutation (`:1274`) and is what the replay records. Anything without a dedicated case is broadcast verbatim (`:1133-1138`).

| Message | edopro recipients and hide rule | Duelcraft today | Gap |
|---|---|---|---|
| All 21 prompts | Prompted player only (`:881-984`). `SELECT_CARD`, `SELECT_TRIBUTE`, `SELECT_UNSELECT_CARD` zero the code of every candidate whose `controler != player` (`:933-934`, `:951-952`, `:968-977`), so a face-down opponent card in a selection list does not leak through the prompt. | Prompted player only, payload verbatim. | ❌ candidate codes leak |
| `MSG_HINT` | By type (`:843-880`): 1, 2, 3, 5 (`EVENT`, `MESSAGE`, `SELECTMSG`, `EFFECT`) to the target player only and not recorded; 4, 6, 7, 8, 9, 11 to everyone **except** the target; 10 (`CARD`) and 201 to 203 to everyone; 200 (`SKILL`) to the target's team. | Broadcast to both. | ⚠️ |
| `MSG_MOVE` | Split by `current.controler`. Code zeroed for the other side when `!(loc & (GRAVE\|OVERLAY)) && ((loc & (DECK\|HAND)) \|\| (pos & FACEDOWN))` (`:1032-1033`). | Face-down test only. | 🐞 §3.6 |
| `MSG_SET` | Code zeroed for **everyone**, setter included (`:1043`); the setter learns the card from the single-slot refresh that follows the preceding `MSG_MOVE`. | Zeroed for the opponent. | ✅ equivalent |
| `MSG_SPSUMMONING` | Face-down summon: code zeroed for the other team (`:1049-1069`); the engine already writes 0. | Verbatim. | ✅ |
| `MSG_DRAW` | Other team gets code 0 per card unless `pos & POS_FACEUP` (`:1080-1084`). | All opponent codes zeroed. | ⚠️ face-up draws hidden |
| `MSG_SHUFFLE_HAND`, `MSG_SHUFFLE_EXTRA` | Codes zeroed for the other team (`:1013-1014`). | Hand zeroed; extra codes dropped for both. | ✅ |
| `MSG_CONFIRM_CARDS` | If the first card's location is `DECK` or `EXTRA`: target player only; otherwise everyone (`:985-1005`). All-or-nothing, never rewritten. | Broadcast. | 🐞 private peek leaks |
| `MSG_CONFIRM_DECKTOP`, `MSG_CONFIRM_EXTRATOP`, `MSG_DECK_TOP` | Everyone, full codes (public reveals). | `DECKTOP` broadcast; the other two unparsed. | ✅ routing |
| `MSG_MISSED_EFFECT` | Controller only (`:1094-1098`). | Unparsed, `Raw` broadcast. | ⚠️ |
| `MSG_MATCH_KILL` | Only when `best_of > 1`, else nobody (`:1126-1132`). | Unparsed. | ➖ |
| `MSG_RETRY` | Everyone, then the duel **ends** as a draw (`:836-842`, return 2 → `DuelEndProc`). | Broadcast; duel continues with the prompt hidden. | see §3.3 |
| `MSG_WIN` | Everyone; match bookkeeping (`:890-901`). | Converted to `DuelEndPayload`. | ✅ |
| `MSG_WAITING` | Synthesised by `WaitforResponse` (`:1326-1343`) to every duellist except the prompted one, with a time-limit packet. Not recorded. | Not synthesised. | ❌ |
| Everything else | Everyone verbatim, cached for late-joining spectators. | Everyone verbatim. | ✅ |

Observers only ever receive the public projection: never prompts, `MSG_WAITING`, hint types 1/2/3/5, deck or extra `CONFIRM_CARDS`, or the timeout `MSG_WIN`.

### 12.2 Refresh policy

edopro never re-queries on every message. It refreshes specific locations at specific moments, with a mask per location, and sends the result as a synthesised `MSG_UPDATE_DATA` (`u8 player, u8 location, u32 total, blocks`; `RefreshLocation` `:1370-1396`) or `MSG_UPDATE_CARD` (`u8 player, u8 location, u8 sequence, one block`, no size prefix; `RefreshSingle` `:1397-1424`).

| Refresh | Mask | Flags beyond the 13 base stats (`CODE` … `BASE_DEFENSE`, `REASON`) | Not requested |
|---|---|---|---|
| `RefreshMzone` | `0x3881fff` | `STATUS`, `LINK`, `IS_HIDDEN`, `COVER` | `IS_PUBLIC`, scales, overlays, counters, equip/target |
| `RefreshSzone` | `0x3e81fff` | as MZONE plus `LSCALE`, `RSCALE` | `IS_PUBLIC`, overlays, counters |
| `RefreshHand` | `0x3781fff` | `STATUS`, `IS_PUBLIC`, `LSCALE`, `RSCALE`, `IS_HIDDEN`, `COVER` | `LINK`, overlays, counters |
| `RefreshGrave`, `RefreshExtra` | `0x381fff` | `STATUS`, `IS_PUBLIC`, `LSCALE` | `RSCALE`, `LINK`, `IS_HIDDEN`, `COVER` |
| `RefreshSingle` | `0x3f81fff` | everything except `REASON_CARD`, `EQUIP_CARD`, `TARGET_CARD`, `OVERLAY_CARD`, `COUNTERS`, `OWNER` | |
| `PseudoRefreshDeck` | `0x1181fff` | replay file only; **no network send** (`:1425-1438`) | |

Schedule (`BeforeParsing` `:795-828`, `AfterParsing` `:1143-1266`):

- Duel start: `RefreshExtra` both players; deck to the replay only. Opening hands arrive through `MSG_DRAW`.
- Before `SELECT_IDLECMD` / `SELECT_BATTLECMD`: MZONE, SZONE and HAND for both players. Before `SELECT_CHAIN` and `NEW_TURN`: MZONE and SZONE for both. Before `FLIPSUMMONING`: the flipped slot. No refresh precedes any other prompt.
- After `DRAW`, `SHUFFLE_HAND`: that player's hand. After `SHUFFLE_EXTRA`: extra. After `SWAP_GRAVE_DECK`: grave. After `SHUFFLE_SET_CARD`: that location for both players.
- After `NEW_PHASE`, `SUMMONED`, `SPSUMMONED`, `FLIPSUMMONED`, `CHAINED`, `CHAIN_SOLVED`, `CHAIN_END`: MZONE and SZONE for both (`NEW_PHASE`, `CHAINED`, `CHAIN_END` also HAND). After `DAMAGE_STEP_START/END`: MZONE only.
- After `MOVE`: the destination slot, when the location or controller changed and the destination is not an overlay. After `POS_CHANGE` face-down to face-up: that slot. After `SWAP`: both slots. After `RELOAD_FIELD`: extra only.
- Never: `DECK` over the network, `REMOVED`.

Hiding is one predicate, `IsPublicQuery` (`core_utils.cpp:224-232`): a field is public when the card `is_public` or `position & POS_FACEUP`; otherwise the private set (`CODE`, `ALIAS`, `TYPE`, `LEVEL`, `RANK`, `ATTRIBUTE`, `RACE`, `ATTACK`, `DEFENSE`, base stats, `STATUS`, `LSCALE`, `RSCALE`, `LINK`) is **omitted from the buffer**, not zeroed (`:153-160`). Position, reason, counters, overlay count and equip/target links always survive, so the opponent knows where a card is and how many materials it carries, but not what it is. Because omitted fields leave the client's last value in place, the client zeroes deck codes itself on `SHUFFLE_DECK` and re-sets the code on returns to the Extra Deck.

Duelcraft instead requests one 14-flag mask for MZONE, SZONE and EXTRA after every engine pause, never refreshes the hand, and `sanitizeCard` zeroes values while keeping the flag bits (§5).

### 12.3 Lobby, start, end

- **Deck check at ready time** (`:366-397`, `deck_manager.cpp:204-258`): main/extra/side sizes against host limits; forbidden types; at most one legend monster, spell and trap and one skill; at most 3 copies by alias-collapsed code; banlist limits; fusion/synchro/xyz/link (and optionally ritual) must sit in the extra deck. Duelcraft: none (§6.2).
- **Rock-paper-scissors between the team captains** (`:444-516`): `STOC_SELECT_HAND`, results mirrored per team, ties replayed, cycle 1 < 2 < 3 < 1. The winner receives `STOC_SELECT_TP`; if they choose second, the teams are swapped so that **the team going first is always engine player 0** (`:547-565`). Duelcraft: the challenger is always player 0 and goes first; no choice.
- **`OCG_DuelOptions`** (`:598-602`, `game.cpp:3930-3948`): four `u64` seeds from a global RNG; flags from the host settings plus `DUEL_PSEUDO_SHUFFLE` when host-side shuffling is off; **the same** `OCG_Player {start_lp, start_hand, draw_count}` for both teams; `enableUnsafeLibraries = 1` (Duelcraft sets 0, the safer choice for a server). `constant.lua`, `utility.lua` and every init script are preloaded.
- **Shuffle and card registration** (`:603-690`): the main deck is shuffled host-side with a separate RNG, never the extra deck; cards are registered extra-rule tokens first, then per duellist main deck and extra deck in **reverse list order** so index 0 is drawn first. Duelcraft matches this (seed-derived shuffle, reverse order).
- **Synthesised `MSG_START`** (18 bytes, `:696-721`): `u8 playerType` (0 or 1 for duellists, `0x10`/`0x11` for observers), `u32 lp0, u32 lp1`, `u16` deck and extra counts for both players from `OCG_DuelQueryCount`. Duelcraft's `DuelStartPayload` carries only the recipient's own deck sizes and initialises both players from them (§8).
- **Process loop** (`:730-744`): `Analyze` returns 0 continue, 1 wait for a response, 2 end.
- **Response gate** (`netserver.cpp:302-314`, `:1284-1297`): a per-connection state (`CTOS_RESPONSE` set by `WaitforResponse`, cleared to `0xff` on receipt). No payload validation, no check that the responder is the prompted player, and surrender bypasses the gate so any client can end the duel. Copy the state machine; add the two checks edopro lacks.
- **Time limit** (`:1439-1460`): a per-turn budget reset on every `NEW_TURN`, ticked by a one-second timer while a response is pending; on expiry the host fabricates `MSG_WIN {1 - player, reason 3}`. No auto-response is submitted.
- **End of duel** (`:745-776`): `MSG_WIN` → match bookkeeping (`best_of`, majority, `match_kill`) → rematch offer with decks restored, or side-decking (`LoadSide` enforces same card multiset and sizes) with the previous loser choosing turn order. No second RPS.
- **Surrender** (`:777-793`): host synthesises `MSG_WIN {1 - player, reason 0}` and ends normally; the client's only affordance is the leave button retitled "Surrender" (string 1351), there is no surrender window. **Duellist disconnect** (`:284-309`): `MSG_WIN {1 - player, reason 4}` to everyone, then the server shuts down. **No duellist reconnection**; `packets_cache` and `STOC_CATCHUP` exist only for spectators joining mid-duel, who receive the public packets and then `RefreshAllCards()`.
- **Single mode** (`single_mode.cpp`): no hiding at all, refreshes the deck as well (mask `0x2f81fff`, drops `IS_HIDDEN`), blocks the duel thread on prompts, aborts the session on `MSG_RETRY`. This is the closest analogue to `/duel test`.

### 12.4 Client: non-prompt message behaviour

edopro's client keeps one `ClientCard` object per card and moves the same object between containers, so counters, equip links, targets and overlay materials travel with a card for free. Its rendering surfaces are a scrollable log (`AddLog`), a hint line (`stHintMsg`), a blocking modal (`wMessage`), a 40-frame toast (`stACMessage`) and full-screen banners (`showcard`, system strings 1701 to 1711). Sounds and animations are suppressed while catching up; state changes are not.

| Message | edopro client | Duelcraft | Gap |
|---|---|---|---|
| `MSG_RETRY` | Error modal (string 1434), nothing else (`duelclient.cpp:1348`). | Prompt hidden. | both wrong; §3.3 |
| `MSG_HINT` | `SELECTMSG` → caption of the next prompt (`:1412`); `MESSAGE` → blocking modal; `OPSELECTED` → log + toast 1510/1512; `RACE`/`ATTRIB`/`CODE` → log + toast 1511; `NUMBER` → toast 1512; `CARD`/`EFFECT` → card reveal; `ZONE` → zone flash + log using the `SELECT_PLACE` bit layout; `EVENT` → nothing. | Same, minus the log lines and the `EFFECT` reveal. | ✅ |
| `MSG_WAITING` | "Waiting..." (1390) in the hint line (`:1631`). | n/a (not synthesised). | ❌ |
| `MSG_START` | Creates blank face-down card objects for both decks and extras (`client_field.cpp:114-131`); "Duel Start" banner. | Counts only. | structural |
| `MSG_CONFIRM_DECKTOP` / `EXTRATOP` | Writes the codes into the deck objects permanently (until a shuffle zeroes them); log 207 plus one line per card; slide-and-flip animation (`:2513`, `:2548`). | Zone inspector; `EXTRATOP` unparsed. | ⚠️ |
| `MSG_CONFIRM_CARDS` | Sets codes; on-field/hand cards flip and highlight for 90 frames, deck/extra cards open the card panel; log 208 (`:2582`). | Zone inspector. | ⚠️ |
| `MSG_SHUFFLE_DECK` | **Zeroes every deck card's code** and clears reversed flags (`:2691`). | No-op. | ❌ (needed once deck codes exist) |
| `MSG_SHUFFLE_HAND` | Re-codes hand cards and clears their `desc_hints` (`:2733`). | Re-codes hand. | ✅ |
| `MSG_SWAP_GRAVE_DECK` | Swaps the two containers, then routes each bitmask-flagged card of the new deck to the extra deck face-down (`:2811`). | Unparsed. | ❌ needs deck objects |
| `MSG_SHUFFLE_SET_CARD` | Zeroes the named cards' codes, swaps them within the zone array, moves their materials with them (`:2881`). | Unparsed. | ❌ |
| `MSG_REVERSE_DECK` / `MSG_DECK_TOP` | Global `deck_reversed` toggle; deck card face-down when `deck_reversed == is_reversed`; `DECK_TOP` sets the code at `size-1-seq` (`:2852`, `:2862`). | Unparsed. | ❌ |
| `MSG_NEW_TURN` / `MSG_NEW_PHASE` | Turn/phase banners (1710, 1704-1709), phase buttons, surrender button shown; only `PHASE_BATTLE_START` among the battle sub-phases has a case (`:2928`, `:2960`). | Header text. | ✅ (sub-phase collapse matches) |
| `MSG_MOVE` | Token appear/vanish with fades; field↔field: `SetCode` when `code != 0` or the destination is EXTRA, `counters.clear()` on leaving the field, `ClearTarget()` and equip detach on any location change; overlay branches store the material on the host card and renumber survivors (`:3044-3215`). `reason` picks only the destroy/banish sound. No log line. | Arrays; overlay never applied (§3.4). | 🐞 |
| `MSG_POS_CHANGE` | Face-up → face-down clears counters and targets (`:3217`). | Position only. | ⚠️ |
| `MSG_SET` | Sound only; the move is conveyed by `MSG_MOVE` (`:3240`). | Places the card. | ✅ |
| `MSG_SWAP` | Swaps the two objects; everything travels with them (`:3247`). | MZONE only, stats/overlays lost. | ⚠️ |
| `MSG_FIELD_DISABLED` | Stores the mask (halves swapped for the second player), draws a grey X per zone (`:3273`). | Dropped. | ❌ |
| `MSG_SUMMONING` / `SPSUMMONING` / `FLIPSUMMONING` | Chant or sound, zoom/fade reveal; `FLIPSUMMONING` also sets code and position (`:3280-3349`). `*SUMMONED` are no-ops. | No-ops except `FlipSummoning`. | ⚠️ visual only |
| `MSG_CHAINING` … `CHAIN_END` | Chain link stores card, `desc` and the **trigger** location where the numbered marker is drawn, offset per stacked link; `CHAIN_SOLVING` blinks the marker; `NEGATED`/`DISABLED` show the "negated" stamp; links are cleared only at `CHAIN_END` (`:3353-3468`). | "Chain: N" text. | ⚠️ |
| `MSG_CARD_SELECTED` / `BECOME_TARGET` / `RANDOM_SELECTED` | Three-blink highlight on field cards, slide-out for pile cards; log 1610 "targeted" / 1680 "selected"; `BECOME_TARGET` feeds the chain's target set (`:3470-3527`). | Dropped. | ❌ |
| `MSG_DRAW` | Codes applied to the deck top first, then cards moved to hand; per-card position discarded here too (`:3529`). | Hand append. | ✅ |
| `MSG_DAMAGE` / `RECOVER` / `PAY_LPCOST` / `LPUPDATE` | Floating signed number in red / green / blue, bar drains over ten frames; `LPUPDATE` is deliberately silent (`:3559-3708`). | Value snaps. | ⚠️ |
| `MSG_EQUIP` / `UNEQUIP` / `CARD_TARGET` / `CANCEL_TARGET` | Bidirectional links (`equipTarget` + `equipped`; `cardTarget` + `ownerTarget`), icons drawn on hover (`:3605-3687`). | Dropped. | ❌ |
| `MSG_ADD_COUNTER` / `REMOVE_COUNTER` | `counters` map per card, toast 1617/1618, tooltip listing (`:3711-3751`). | Dropped. | ❌ |
| `MSG_ATTACK` / `BATTLE` / `ATTACK_DISABLED` | Arrow between the cards for 40 frames, direct attacks aimed at the player; `BATTLE` writes combat ATK/DEF onto both cards (`:3753-3831`). | No-ops. | ⚠️ |
| `MSG_MISSED_EFFECT` | Log 1622 "missed the timing" (`:3838`). | Unparsed. | ❌ |
| `MSG_TOSS_COIN` / `TOSS_DICE` | Log + toast 1623/1624 with each result (`:3847`, `:3865`). | Dropped. | ❌ |
| `MSG_HAND_RES` | Animated hands, 60 frames (`:3891`). | Status text. | ✅ |
| `MSG_CARD_HINT` | `DESC_ADD/REMOVE` → refcounted `desc_hints` shown in the tooltip; other types → single `cHint/chValue` (tooltip strings 211-215); `CHINT_TURN` shows a numbered badge (`:3964`). | Same, in the card-info banner instead of a tooltip. | ✅ |
| `MSG_PLAYER_HINT` | Per-player refcounted hints in the name tooltip (`:3999`). | Refcounted, shown under the player's name. | ✅ |
| `MSG_REMOVE_CARDS` | Resolve all, fade, delete, renumber surviving materials (`:4017`). | Unparsed. | ❌ |
| `MSG_RELOAD_FIELD` | Wipes the field and rebuilds blank cards from the counts; codes arrive via the following `UPDATE_DATA`s (`:4136`). | Unparsed. | resync design |

**Card model rules worth copying** (`client_card.cpp`, `client_field.cpp`): a card object with identity; materials on the host card plus a flat set, renumbered on detach; `ClearTarget()` and equip detach on every location change and face-down flip; `counters.clear()` when leaving the field or turning face-down; decks and extras as lists of card objects (needed by `DECK_TOP`, `CONFIRM_DECKTOP`, `SHUFFLE_DECK`, `SWAP_GRAVE_DECK`, `REVERSE_DECK`); query application where `ATTACK < 0` renders "?", `EQUIP_CARD`/`TARGET_CARD` are additive only, `COUNTERS` assign rather than accumulate, and `IS_HIDDEN` is never a client field. Rendering: ATK/DEF colour against the base value; level and rank not colour-coded by change; link markers drawn only on hover as shaded zones; `STATUS_DISABLED | STATUS_FORBIDDEN` draws the negated stamp; hand cards are face-down when `code == 0`, graveyard and overlay cards always face-up.

### 12.5 Client: prompt behaviour

Plumbing edopro applies to every prompt (`duelclient.cpp`, `event_handler.cpp`, `drawing.cpp`): an `answered` flag is reset once per engine message and `SendResponse()` returns early if it already fired, so double clicks and racing dismiss handlers are harmless (`duelclient.cpp:432`, `:4269-4271`); dialogs set the response, animate closed, and send when the animation ends (`drawing.cpp:845-848`); a selection index is the card's position in the **message** (`select_seq`), assigned before the display list is sorted (`:2005-2011`); one shared Cancel/Finish button with three states (hidden, "Cancel" 1295, "Finish" 1296) is bound to right-click as well (`event_handler.cpp:2725-2745`, `:1498-1511`); a selection goes to the card-list panel when any candidate sits in deck, graveyard, banished, extra or overlay (`location & 0xF1`) or has no location, otherwise it is picked on the field (`:1999-2011`). Escape never cancels a prompt.

| Prompt | edopro | Duelcraft | Gap |
|---|---|---|---|
| `SELECT_IDLECMD` / `SELECT_BATTLECMD` | Highlighted cards; click opens a menu of per-card buttons (Activate, Normal Summon, Special Summon, Set, S/T Set, Repos relabelled Flip Summon / To Defense / To Attack, Attack, View); pile zones open a chooser; several effects on one card open the option dialog with each effect's `desc`, skipped when only one survives; BP/M2/EP and Shuffle buttons gated by the trailing flags. Codes idle 0-8, battle 0-3 (`event_handler.cpp:347-616`). | Icon strip, no `desc` disambiguation, no shuffle, battle Activate sends 2. | 🐞 §3.2, ⚠️ |
| `SELECT_EFFECTYN` | Query window; body is the `desc` text with the card name substituted (desc 0 → string 200, 221 → trigger text); the card is highlighted on the field; right-click = No (`duelclient.cpp:1927-1954`). | Desc text plus name; no highlight. | ✅ |
| `SELECT_YESNO` | Query window with the `desc` text. | Same. | ✅ |
| `SELECT_OPTION` | Up to five buttons, paged when texts are wide; caption `HINT_SELECTMSG` or 555 "Select an option"; no auto-answer even for one option. | Buttons list. | ✅ |
| `SELECT_CARD` | Field or panel by the location rule; caption `hint(min-max)`; per-click toggle; **auto-submits at `max`**, and in field mode when everything selectable is selected and `min` is met; the shared button reads Cancel when cancelable, Finish once `min` is reached (`event_handler.cpp:677-708`, `:1407-1446`). Response type chosen by payload size; indices in message order. | Field mode auto-submits at `max` with no Finish; right-click only cancels. | ⚠️ right-click should finish once `min` is met |
| `SELECT_UNSELECT_CARD` | One click per round trip, `{1, index}`; Finish and Cancel both send `-1`, the caption differs (`:2033-2113`). | Same. | ✅ |
| `SELECT_CHAIN` | Hint 550 "Select the effect you want to activate" (556 for resolve-mode effects); click a card, several effects open the option dialog; three toggles Chain OFF / Always pause / Chain ON, also held keys A/S/D; **auto-pass when not forced and (OFF, or no candidates, or `specount == 0` without Always)**, with an optional 20-frame delay so response latency reveals nothing; forced + auto-order answers 0; with no candidates and Always pause a query "No card or effect can be activated" (201/202) lets the player inspect the field (`duelclient.cpp:2115-2226`). | Passes only on an empty list (client and server); `desc` unused. | ⚠️ |
| `SELECT_PLACE` / `SELECT_DISFIELD` | Zone highlighting only; caption 569 "Select the zone to place X" (the hint is a card code here) or 570 "Select the zone(s) to become unusable"; each click toggles a zone and counts down `count`, the response is sent at zero; the mask halves are swapped for player 1; EMZ clicks fall back to the opponent-side bits; no cancel (`count` clamped to at least 1); optional auto-placement settings with a centre-out preference order (`:2228-2311`, `event_handler.cpp:1343-1404`). | Single click, `count` ignored. | 🐞 §3.5 |
| `SELECT_POSITION` | Window 561 "Select the battle position" with up to four **card-image** buttons; the single-bit case never arrives (the engine collapses it). | Text buttons. | ⚠️ |
| `SELECT_TRIBUTE` | Field only; caption 531 "Select monsters for Tribute Summon" with `(min-max)`; `min` is compared against the summed tribute value, `max` against the count; **auto-submit only when the count reaches `max`**, Finish available once `min` is met; no running total shown (`:2356-2389`, `event_handler.cpp:1419-1435`). | Auto-submits as soon as the sum reaches `min`. | ⚠️ |
| `SORT_CHAIN` / `SORT_CARD` | Card-list panel titled 206 / 205; clicking assigns the next ordinal (shown under the card), re-clicking removes it; sent when every card is numbered, one `u8` per card; right-click sends `-1` to skip; `SORT_CHAIN` auto-declines behind the "Automatic Chain Link order" setting (`:2479-2511`, `event_handler.cpp:742-770`). | None. | ❌ |
| `SELECT_COUNTER` | **No dialog.** Each click removes one counter from that card, the hint reads 204 `Remove %d "%ls"` with the remaining count, cards with nothing left stop being selectable, auto-submit at zero, one `int16` per card in message order (`:2391-2415`, `event_handler.cpp:1464-1481`). | None. | ❌ |
| `SELECT_SUM` | Panel or field; caption `hint(sumval)`; **every click recomputes which cards can still complete a valid total** and offers only those; must-select cards are locked and **excluded from the response**; auto-submit when the must-select set alone completes the sum; mode 0 exact with count bounds, mode 1 greater-or-equal (`client_field.cpp:1021-1151`). | Wrong layout and indices. | 🐞 §3.1 |
| `ROCK_PAPER_SCISSORS` | Borderless three-image window, `int32` 1-3; the auto-RPS setting applies only to the lobby packet. | Three buttons. | ✅ |
| `ANNOUNCE_RACE` / `ANNOUNCE_ATTRIB` | Checkbox grids (title 563 / 562) showing only the bits in `available`; **submit fires when exactly `count` boxes are checked**, no OK button; race as `u64` (`event_handler.cpp:822-851`). | None. | ❌ |
| `ANNOUNCE_CARD` | Edit box + list + OK (title 564); the list is filtered by a client-side port of the engine's `is_declarable` stack machine (`client_field.cpp:1252-1315`: arithmetic, logical and bitwise ops, `ISCODE`/`ISTYPE`/`ISRACE`/`ISATTRIBUTE`/`ISSETCARD`, getters, `ALLOW_ALIASES`/`ALLOW_TOKENS`; aliases and tokens excluded unless allowed; two hardcoded exceptions); search matches a passcode or an accent- and case-insensitive name substring with exact matches first; response is the passcode (`:1320-1370`). | None. | ❌ |
| `ANNOUNCE_NUMBER` | Combo box + OK (title 565); response is the **index** (`:3949-3962`). | None. | ❌ |

### 12.6 Divergence checklist

Items already tracked in §3 are referenced, not repeated.

**Host**
- [x] Zero the code of prompt candidates the prompted player does not control (`SELECT_CARD`, `SELECT_TRIBUTE`, `SELECT_UNSELECT_CARD`; `generic_duel.cpp:933-977`).
- [x] Route `MSG_HINT` by type: 1/2/3/5 to the target only, 4/6-9/11 to the others, 10 to everyone (`:843-880`).
- [x] Send `MSG_CONFIRM_CARDS` only to the target player when the cards are in the deck or extra deck (`:985-1005`); §3.6.
- [x] Adopt the `MSG_MOVE` hide predicate (`:1032-1033`); §3.6.
- [x] Keep face-up draws visible to the opponent (`:1080-1084`).
- [x] Send `MSG_MISSED_EFFECT` to the controller only once parsed (`:1094-1098`).
- [ ] Synthesise `MSG_WAITING` for the non-prompted player (`:1326-1343`).
- [x] Refresh on edopro's schedule and masks (§12.2): hand before idle, battle and chain prompts; field after state changes; never the deck; single slot after `MOVE`, `POS_CHANGE` flip-up and `SWAP`.
- [x] Omit private query fields instead of zeroing values while keeping flags (`core_utils.cpp:153-160`, `:224-232`).
- [ ] Take the full-information copy before sanitising and keep it for a future replay.
- [x] Response gate: per-player pending-response state plus the responder-equals-prompted check edopro lacks (`:1284-1297`).
- [ ] Rock-paper-scissors before the duel and let the winner choose who goes first; make the first player engine player 0 (`:444-565`).
- [ ] Synthesised start packet with both players' deck and extra counts from `OCG_DuelQueryCount` (`:696-721`).
- [ ] Deck legality at ready time per `CheckDeckSize`/`CheckDeckContent` (sizes, copies, banlist, extra-deck types).
- [x] Surrender and disconnect end with a synthesised `MSG_WIN` (reasons 0 and 4) so both clients learn the result (`:777-793`, `:284-309`).
- [ ] Optional: per-turn time limit ending in `MSG_WIN` reason 3 (`:1439-1460`).
- [x] Treat `MSG_RETRY` as a bug signal (edopro ends the duel) and prevent it by validating before sending; §3.3.

**Client model**
- [x] One card object per card, moved between containers (§12.4).
- [x] Materials stored on the host card, renumbered on detach; §3.4. (No flat set: nothing needs to iterate every material.)
- [x] `ClearTarget()` and equip detach on every location change and face-down flip; `counters.clear()` on leaving the field or turning face-down.
- [x] Decks and extra decks as card lists so `CONFIRM_DECKTOP`, `DECK_TOP`, `REVERSE_DECK`, `SWAP_GRAVE_DECK` and `SHUFFLE_DECK` code-zeroing can work.
- [x] Bidirectional equip and target links; counters map.
- [x] Refcounted card and player hints; `cHint/chValue`.
- [ ] Query application rules: negative ATK renders "?"; `COUNTERS` assign; `IS_HIDDEN` stays server-side.
- [ ] Rendering rules: ATK/DEF colour against base; link arrows only on hover as shaded zones; `STATUS_DISABLED | STATUS_FORBIDDEN` stamp; graveyard and overlay cards always face-up; hand cards face-down when `code == 0`.
- [x] Surfaces: toast, blocking modal for `HINT_MESSAGE`.
- [x] Surfaces: scrollable log with click-to-view codes. Turn and phase banners are up; a result banner is the result overlay instead.
- [x] Hint handling per type (§12.4 row `MSG_HINT`), `HINT_SELECTMSG` as the caption of the next prompt.
- [x] Highlights for `BECOME_TARGET` and `CARD_SELECTED`; grey overlay for `FIELD_DISABLED`.
- [x] Highlight for `RANDOM_SELECTED`; negated stamp for `CHAIN_NEGATED`/`DISABLED`; chain markers at the trigger location.
- [x] LP feedback: signed floating number in red (damage), green (recover), blue (cost); `LPUPDATE` silent.
- [x] Coin and dice results as log plus toast.

**Prompts**
- [x] `answered` guard and send-after-close (`duelclient.cpp:4269-4271`).
- [x] Shared Cancel/Finish button with three states bound to right-click; Finish once `min` is met in field mode.
- [ ] Selection indices from message order, display sorted separately.
- [ ] Field-versus-panel by the location mask `0xF1` or location 0.
- [ ] `SELECT_CHAIN` auto-pass on `specount == 0`, the three chain toggles, optional constant delay.
- [x] `SELECT_PLACE` count loop with un-pick, half-swap for player 1, EMZ fallback; §3.5.
- [x] `SELECT_TRIBUTE` auto-submit at `max` count only, Finish once the sum meets `min`.
- [x] `SELECT_SUM` viability recomputation, must-select exclusion; §3.1.
- [x] `SELECT_COUNTER` as click-per-counter with a live caption, no dialog.
- [x] `SORT_CARD`/`SORT_CHAIN` click-order list, right-click skip; auto-decline setting for chains skipped (no settings menu in this mod).
- [x] `ANNOUNCE_RACE`/`ATTRIB` checkbox grids submitting at exactly `count`.
- [x] `ANNOUNCE_CARD` search over a port of `is_declarable`.
- [x] `ANNOUNCE_NUMBER` via the option dialog, answering with the index.
- [x] Effect disambiguation through the option dialog with `desc` text, skipped when one effect survives. Separate Activate / Resolve / Reset entries by client mode is still open.
- [x] `SELECT_POSITION` with card-image buttons.
- [x] Concede: edopro offers only a retitled leave button and no result screen beyond the win banner, so the result overlay and concede button need an original design.

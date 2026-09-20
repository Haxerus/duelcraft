# QA visibility source and native trace

## Verified inputs (2026-09-19)

Worktree baseline `b2b2a2a`; core `122e0d091a0f399221a4510cc98a406ae485905f`; EDOPro `48ec006c49d7899b39f39420b9a71dea5ecdd22a`. Read-only SQLite lookup in `C:/ProjectIgnis/expansions/cards.cdb` verified the following names/passcodes before writing fixtures. Scripts are the installed `C:/ProjectIgnis/script/official` files, not an assumed upstream revision.

| Effect | Passcode | Script SHA256 |
|---|---:|---|
| Painful Choice | 74191942 | 5CA88F45183ACA65272ECBACA8128E4E8F5F404E29D62F4A24A2521D316930EE |
| Number 89: Diablosis the Mind Hacker | 95474755 | 6C44BE6A4ABC168A9D6EE2B575B4294CB90D117CDBBA0FD70449CA53E7362710 |
| Ceremonial Bell | 20228463 | 2C35B57A92E894CFA197C6A8C1893CC887A8B1DD9562680F304D926D3854553D |

Extra fixture: player 0 controls face-up Diablosis in monster slot 0 with one attached Blue-Eyes White Dragon material. Player 1 Extra Deck contains Gaia Saber (67598234), LANphorhynchus (77637979), and a second Gaia Saber. Activate Diablosis, detach, inspect/choose opponent Extra index 1, decline optional subsequent triggers. The real script `exop` confirms the entire Extra Deck to player 0, selects one removable card, banishes it facedown, and shuffles the remainder. Both players have 40 normal-monster deck cards; starting hand/draw zero; seed words 42; MR5.

## Producer/host/consumer contracts

- Core `libduel.cpp` `Duel.ConfirmCards` writes code/controller/location/current sequence (no position) for each actual card. `playerop.cpp` `SelectCard` writes data code and `get_info_location()` in authoritative candidate order (lines 306-317).
- EDOPro `generic_duel.cpp` lines 922-941 zero foreign selection codes; lines 985-1005 route deck/Extra confirms only to the addressed player. Duelcraft already follows both rules. No packet shape or redaction change is justified.
- EDOPro `duelclient.cpp` lines 2582-2608 resolve general reveals by `GetCard(controller, location, sequence)` and store nonzero codes. Selection lines 1976 onward resolve the same card coordinates, including overlays by their position index.
- Core `card.cpp` lines 191-207 always supplies QUERY_IS_PUBLIC; face-up, chain relation or EFFECT_PUBLIC in hand/on-field makes it true. QUERY_IS_HIDDEN is explicit darkness hiding. EDOPro query field presence controls incremental updates.
- First client loss for Painful Choice/Diablosis: ConfirmCards ignores non-HAND locations. Later correctly redacted candidates therefore resolve to code zero.
- First client loss for Bell: opponent hand rendering unconditionally uses backs and lacks hover; query-only visibility changes do not dirty the hand. Remembered identity is not continuous permission.

Native and focused test results will be appended after execution. Raw test-only traces belong under ignored `build/evidence/qa-m4-follow-up/a1`, never ordinary multiplayer logs.

## Fixture refinement before native scenario implementation

The provisional Diablosis setup cannot be inserted directly: pinned `ocgapi.cpp:62` rejects multi-bit new-card locations, so a material would need a full summon flow. Use the simpler real effect **Dragunity Knight - Trident (80159717)** instead; read-only SQLite confirms the name. Its installed `c80159717.lua` SHA256 is recorded with the native results below. Player 0 controls face-up Trident in monster slot 0 and sends itself as its legal ignition cost; player 1 holds the same three Extra cards above. `exop` confirms all opponent Extra cards, selects one for the Graveyard and shuffles the remainder. This avoids any fake effect or native API change. The provisional fixture above is retained as investigation history, not claimed as executed.

## Executed native trace

Trident script SHA256: `1C28AB867541564B953AD4941CCBF93C9C2E64FF775B26D6B3FF9981988D329F`. All three tests use the installed, unmodified scripts/database, seed `[42,42,42,42]`, MR5, LP 8000, start hand/draw 0, and 40 normal monsters per deck. Painful Choice is in player 0's hand. Bell is in player 1's monster slot 0, with Blue-Eyes in player 1 hand slot 0 and Raigeki in player 0 hand slot 0. Trident uses the refined setup above.

| Effect / packet | Recipient and actual coordinates | Raw / sanitized / lookup | Native response |
|---|---|---|---|
| Painful Choice MSG_CONFIRM_CARDS (31) | recipient 1 only; controller 0, DECK=1, seq 35/36/37/38/39 | codes 89631139/46986414/39552864/13039848/43096270 retained | player 0 previously selects indices 4,2,0,3,1 |
| Painful Choice MSG_SELECT_CARD (15) | recipient 1; seq **39/38/37/36/35**, inverse of reveal order | raw 43096270/13039848/39552864/46986414/89631139; all packet codes 0; location lookup restores each exact identity | index **2**, `000000000100000002000000`; Mystical Shine Ball (39552864) moves from Deck to hand |
| Trident MSG_CONFIRM_CARDS (31) | recipient 0 only; controller 1, EXTRA=64, seq 0/1/2 | 67598234/77637979/67598234; both duplicate copies tracked separately | cost index 0 sends Trident itself |
| Trident MSG_SELECT_CARD (15) | recipient 0; seq **2/1/0** | raw 67598234/77637979/67598234; all packet codes 0; exact location lookup restores codes | index **1**, `000000000100000001000000`; LANphorhynchus goes to Graveyard |
| Trident MSG_SHUFFLE_EXTRA (39) | both; controller 1 | recipient 0 remaining Extra codes 0; recipient 1 restored from full owner query | no reply |
| Bell hand query (host UPDATE_DATA/UPDATE_CARD, 6/7) | controller 1, HAND=2, seq 0; both recipients | raw code 89631139, flags `0x3781fff`, public=true, hidden=false; both packet/display codes 89631139 | player 0 activates Raigeki, command `05000000` |
| Bell removal + MSG_SHUFFLE_HAND (33) + query | same coordinates | raw code 89631139, flags `0x3781fff`, public=false, hidden=false; opponent packet/display 0; owner packet/display 89631139 | chain/place replies complete; no Retry |

At both foreign selection windows, test-only native single-card queries report flags `0x3f81fff`, public=false and hidden=false; recipient sanitization omits QUERY_CODE and returns code 0 even for a previously revealed candidate. The cached reveal identity supplies the prompt; query privacy remains intact. These Deck queries are test diagnostics, never production refreshes. Unrelated opponent Deck seq 0 remains unknown. The native tests assert successful continuation and the chosen card's actual move, not only prompt appearance.

## Verification and boundaries

- Focused initial RED: 108 tests, 2 failures, 0 errors/skips. Out-of-order Extra lookup returned 0 instead of 89631139; a public-hand transition with unchanged code/position failed to dirty HAND_1.
- Focused initial GREEN: 111 tests, no failures/errors/skips.
- Initial native fixture run: 3 tests, 2 fixture assertion failures (negative control accidentally picked a revealed Deck seq 39; Bell's actual hand shuffle legitimately clears cached code). Preserved evidence, corrected expectations without production changes. Subsequent 3 native tests passed.
- Movement RED: 1 test failed, proving a field's public flag could follow a known card into hidden opponent hand before the destination refresh. Visibility now resets across location/controller changes.
- Final focused + complete OcgCoreTest run: **128 tests, 0 failures/errors/skips** (ClientDuelState 69, MessageSanitizer 24, RefreshSchedule 20, OcgCore 15).
- Raw evidence: ignored `build/evidence/qa-m4-follow-up/a1/{red,native-initial,native-green,movement-red,final}`; final native trace is `final/native-trace.txt`. Test-only tracing uses stdout, with no production logging changes. One intermediate test trace formatter compile typo is preserved in `compile-test-error.log`; it is not counted as a behavioral reproduction.

A1 proves synthetic state/reset, paired sanitizer/query behavior and real native/script continuation. It does not claim pointer-driven rendering, dedicated A/B network transport, packaged runtime, actual logout/reconnect, or same-tick reveal presentation acceptance. Those remain A2/later checks. Source inspection confirms logout calls `LDLibDuelScreen.close`, which clears activeState/UI/refresher/screen; the state test verifies fresh card objects retain no earlier reveal identity. Already-hovered inspector behavior during a public-off hand rebuild should be exercised by A2's real-pointer scenario. No wire metadata, protocol, sanitizer, schedule, dependency or native source changed.

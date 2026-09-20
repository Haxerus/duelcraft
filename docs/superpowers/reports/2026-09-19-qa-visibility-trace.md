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


## A1 review correction: live inspector revocation

The already-hovered inspector concern above was reproduced with DEV_ONLY `duel_public_hand_hover`: 7/9 checks passed, with exactly two failures when permission ended under a stationary hand pointer and under an inspector-descendant pointer. The client now associates a hand inspector with its actual card and displayed code and revalidates that association each tick. Losing permission or the tracked card hides the inspector immediately; the ordinary pointer-crossing grace period remains available while permission holds. The regression keeps the cached code nonzero and waits 700ms over the inspector before revocation.

Final command `./gradlew.bat test --tests '*ClientDuelStateTest' --tests '*MessageSanitizerTest' --tests '*RefreshScheduleTest' runClient '-PldTest=duel_public_hand_hover,duel_revealed_hand'` passed: **113 JUnit tests, 2/2 real UI scenarios, 175/175 UI checks**. RED/GREEN reports and screenshots are preserved under ignored `build/evidence/qa-m4-follow-up/a1/review-fix`; final screenshots show the inspector gone for both revoked pointer positions. This is real-pointer synthetic-state UI evidence, separate from the native Bell test and still-unexecuted dedicated A/B runtime acceptance.

## A2: prompt-owned reveals and dedicated native effects

Source/test commit: `026e623b9e4ff807b6aea9dde4b5143f2b049ab6` (`fix: present revealed choices in selection prompts`).

`PromptController.refreshReveals` now consumes an ordered queue of immutable reveal events after the tick's message/prompt refresh. A mandatory choice consumes reveals whose coordinates all belong to its candidate list; unrelated events wait. Read-only events use the prompt dialog and a local Close acknowledgement. No confirmation response is fabricated. Manual pile inspection is independent. Card/unselect candidates already used recipient state; sum/sort now use that same resolver for artwork and hover. Mixed field/pile tributes use a dialog with the original release weights, count bounds and response indices; field-only tribute highlights remain unchanged. Producer/validator: `native/ygopro-core/playerop.cpp:641`; host/client confirmation references remain `generic_duel.cpp:Sending`, `duelclient.cpp:2582`, `libduel.cpp:891` at the SHAs above.

The dedicated `duel_visibility_qa_mp` uses three ordinary prepared duels, legal inverse-shuffled saved decks, MR5, user seed 42 (normal manager expansion), shuffle seeds 42/43, 8000 LP, opening hand 5 and per-turn draw 0. Required mode seeds actual deposited copies from each saved Main/Extra list. The fixture forwards the original options/listener result and calls the real manager/native session; it has no native insertion, raw-handle, custom script, synthetic message or alternate pump path. It restores collection attachments and client observation/art fixtures; inventory is never changed.

| Effect | Actual setup and response | Native continuation and recipient assertions |
|---|---|---|
| Painful Choice | A activates its real opening spell; pointer chooses five own Deck candidates `[4,2,0,3,1]`; B receives reversed reveal/choice order with zero-coded foreign candidates and clicks original index **2**, `000000000100000002000000` | Actual candidate sequences 34/33/32/31/30 oppose reveal order 30/31/32/33/34. Code **1784619**, sequence 32, enters A hand; the other four go to grave, followed by the spell itself. Both client receipt traces enforce private routing; B's unrelated Deck cards stay unknown. |
| Trident | A normally summons Delta Flyer on turn 1 and Faith Bird on turn 3; real SelectUnselect material prompts Synchro Summon Trident. Its ignition sends itself as cost. A clicks original Extra candidate **1**, `000000000100000001000000` | LANphorhynchus 77637979 enters B grave. Two separate Gaia Saber 67598234 copies remain, followed by a real Extra shuffle. A's remaining Extra identities are 0; B's owner refresh retains Gaia identities. Actual sanitized candidates stay zero, while coordinate lookup, tile texture and hover show authorized identities. |
| Ceremonial Bell | B normally summons Bell on turn 2. Both clients inspect real public hand artwork/hover. B's pointer remains on a stable opponent-hand filler while A activates Raigeki by pointer/context action. | Bell actually moves to grave; native ShuffleHand occurs for **both** players. Received public-off queries follow Bell's removal. Both opponent hands become backs, B's stationary inspector disappears, owner codes remain available, and new pointer hover over all four hidden opponent cards shows no inspector. |

Synthetic validation independently covers one-callback reveal+selection batching, reveal-before-selection, reversed order, duplicate identities, unknown artwork/hover, local read-only acknowledgement, multiple queued events, Retry, Waiting, preserved partially selected mandatory choices and preserved manually opened pile contents. `duel_revealed_hand` now uses deterministic local art and captures a non-first validated response. The legacy panel fixture manually opens a populated graveyard, preserving its large/sparse-row grid and scroll assertions.

### Effective dedicated data provenance

A1's JNI unit tests use `C:/ProjectIgnis`. A2's real manager uses the product's existing CardData snapshot. **These are different data sources.** The final default and required server logs independently report `runs/mpServer/duelcraft/cache/card-data/snapshot-025b75e5-7bd0-47f3-89e8-60e6464213ae`; its effective DB is `cards.cdb`, SHA256 `397BB08C8D2785B3626F56CC5E951D6F59DD0E21C7DCD59B439653B79B01443A`. Actual native search order is `scripts`, `scripts/official`, `scripts/goat`, `scripts/pre-errata`, `scripts/pre-release`, `scripts/rush`, `scripts/skill`, `scripts/unofficial` beneath that snapshot. The recorded revision file contains `28997fafdb1e5bd939b93c8ae9747a7be45c0d6b` and `fd21d93a1df5d0e3af00143fdea14df9e7f68594`.

Bell, Trident and Delta Flyer script hashes match the installed hashes recorded above. The actual runtime Painful Choice hash is `3F6458BE1B47880DE2342D46A9CF70CC1DE2503BBB415182AF0209DAA6673B2F`; runtime `proc_synchro.lua` is `CACD92D496AB653E6E5F304CB2B38B831E4F6D41AB1E842FFAB909A0EFB1543D`. Inspected differences add Painful Choice's AbleToGrave filter, description/operation metadata and conditional remaining-card grave send; the Synchro procedure adds nonpositive-level exclusions and an unused hand-material helper. The traced confirmation/selection and normal level-3 + level-4 material contracts were observed in the actual runtime. No scripts, native submodule or dependencies were modified to force identical sources.

Each final mode archive includes `runtime-provenance.json` with its own effective server log line, full absolute DB/script paths and hashes, plus `runtime-revisions`. `runtime-painful-vs-installed.diff` and `runtime-synchro-vs-installed.diff` retain the source comparisons.

### A2 verification evidence

- Initial pointer RED: **46/63 checks**, 17 failed assertions, then the absent read-only Close control ended the scenario with an error. The inspector opened incorrectly and standalone reveals had no prompt. Archived under `a2/red`.
- An intermediate **72/74** run exposed a fixture hand card that was already known. The fixture now explicitly shuffles/redacts that synthetic hand before testing unknown candidates. Archived under `a2/fixture-known-hand`; subsequent initial matrix **74/74** passed.
- Mixed/pile tribute RED: **80/85 checks**, five failed assertions and absent dialog action error. The focused fallback passed in the final UI matrix. Archived under `a2/tribute-red`.
- Required scoped JUnit command: `./gradlew.bat test --tests '*ClientDuelStateTest' --tests '*ResponseBuilderTest' --tests '*ResponseValidatorTest' --tests '*DuelMessageCodecTest'`: **251 tests**, no failures/errors/skips (70 state, 29 builder, 62 validator, 90 codec).
- Selected UI command: `./gradlew.bat runClient '-PldTest=duel_visibility_qa,duel_revealed_hand,duel_panel_layout,duel_public_hand_hover'`: **4/4 scenarios, 1104/1104 checks**. Evidence `a2/final-ui`, including all reports/screenshots and scoped XML. Final `./gradlew.bat runClient -PldTest=duel_visibility_qa` explicitly applies reveal and selection inside one callback, independent of the harness's per-frame step scheduling: **1/1 scenario, 86/86 checks**, preserved under `a2/final-batch`.
- Final dedicated commands: `./gradlew.bat runMpTest -PldMpTest=duel_visibility_qa_mp -PldMpOwnership=default` and the same command with `required`: **1/1 scenario, 1183/1183 checks per mode** (server 39, A 610, B 534). Coordinator, server, A and B all exit **0**. Evidence `a2/mp-default-final` and `a2/mp-required`.
- Earlier dedicated expectations are preserved: first default completed all effects but omitted the resolved spell from its grave expectation and expected a fallback name while the actual DB supplied Blue-Eyes's name; first required reached the final new-hover check but used a selector matching four cards. Those fixture failures are archived in `a2/mp-default-first` and `a2/mp-required-hover-selector`. Intermediate passing default evidence remains in `a2/mp-default`.

All `a2/...` paths are beneath ignored `build/evidence/qa-m4-follow-up/`. Mode archives contain merged/role reports, screenshots, coordinator/child logs, server-only encoded native records and accepted bytes, and **actual decoded sanitized** receipt snapshots separately for A/B. They are not reconstructed sanitizer outputs. Pointer actions use the normal response/packet/manager path in MP; only synthetic scenarios capture validated callbacks locally. Generic action/place/material responses used to prepare native effects are explicitly setup driving. Fixture textures replace bitmap downloads only, not card identity or native data. Full integration, packaged runtime and reconnect checks remain I1/later scope; A2 did not rerun the full suite.

### A2 review fix 1: right-click acknowledgement

Independent review found that idle/battle right-click hid a read-only reveal without advancing its queue, leaving Close and later events inaccessible. Close and right-click now share one local acknowledgement operation; ordinary effect-chooser cancellation and inspection guards retain their existing behavior. The real-pointer regression separately exercises idle and battle commands, two ordered reveals, first-event right-click, usable next-event Close, eventual empty queue, unchanged pending command and zero response bytes. It also opens and cancels the ordinary two-effect chooser in both command states.

Focused RED command `./gradlew.bat runClient -PldTest=duel_visibility_qa` produced **110/116 checks**, exactly six behavioral failures, no scenario errors, exit **1**. Final GREEN command `./gradlew.bat runClient '-PldTest=duel_visibility_qa,duel_prompt_controls'` passed **2/2 scenarios, 641/641 checks**, exit **0**. The existing prompt-controls scenario checks the shared handler's inspection/optional-chain behavior. Reports, logs and screenshots are preserved under `a2/review-fix1/red` and `a2/review-fix1/green`; an initial test-selector correction run (108/116) is separately retained. Green output is not pristine: existing environment/model/intentional Retry warnings remain, plus 16 LDLib layout-dirty warnings during the unchanged controls scenario's mixed selection, before the reveal scenario. No unrelated warning cleanup or broad unit/UI/MP rerun was performed.

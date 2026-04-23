# Duelcraft Engine Implementation Checklist

Comprehensive gap analysis against edopro (`../edopro/`) and ygopro-core (`native/ygopro-core/`).
Generated 2026-04-11.

**Current state:** 23 message types fully handled across all layers. 12 of 21 prompt types have proper UI. ResponseBuilder formats verified correct against `playerop.cpp`.

---

## 1. Message Handling Gaps

### Tier 1 — Will cause wrong game state or broken gameplay

- [x] **MSG_UPDATE_DATA (6)** — Server queries engine per-slot via nDuelQuery, forwards UpdateData to client. Live ATK/DEF/Level/Rank on field.
- [x] **MSG_UPDATE_CARD (7)** — Same pipeline, single card variant.
- [x] **MSG_SELECT_SUM (23)** — Full parsing with SumCard/opParam. Prompt UI with running sum display and local toggle. Confirm when target reached.
- [x] **MSG_SELECT_UNSELECT_CARD (26)** — Per-click prompt UI with immediate engine responses. Finish/cancel buttons. Field and overlay selection.
- [x] **MSG_CONFIRM_DECKTOP (30)** — Parsed. Auto-opens zone inspector showing revealed cards.
- [x] **MSG_CONFIRM_CARDS (31)** — Parsed. Auto-opens zone inspector showing confirmed cards.
- [ ] **MSG_SWAP_GRAVE_DECK (35)** — Not parsed. Requires deck card tracking (deferred).
- [x] **MSG_HAND_RES (133)** — Parsed. Display ready, awaiting lobby-level RPS flow.

### Tier 2 — Missing UI for uncommon but real prompts

- [ ] **MSG_SORT_CHAIN (21)** — No UI for chain resolution order. Needs drag-to-reorder or numbered buttons.
- [ ] **MSG_SELECT_COUNTER (22)** — No UI for counter distribution. Needs slider/button per card.
- [ ] **MSG_SORT_CARD (25)** — No UI for card reordering (e.g., multiple cards returning to deck).
- [ ] **MSG_ANNOUNCE_RACE (140)** — No UI for monster type declaration. Needs checkbox grid.
- [ ] **MSG_ANNOUNCE_ATTRIB (141)** — No UI for attribute declaration. Needs checkbox grid.
- [ ] **MSG_ANNOUNCE_NUMBER (143)** — No UI for number selection from a list.
- [ ] **MSG_ANNOUNCE_CARD (142)** — No UI for card name declaration. Most complex prompt (requires opcode filter evaluation + card name search).

### Tier 3 — Visual/info gaps (not blocking but notable)

- [ ] **MSG_WAITING (3)** — Not parsed. No "waiting for opponent" display.
- [ ] **MSG_RELOAD_FIELD (162)** — Not parsed. No state resync capability. High priority for robustness.
- [ ] **MSG_FIELD_DISABLED (56)** — Parsed but not tracked in state. Bitmask needs same relative-to-absolute treatment as SelectPlace when implemented.
- [ ] **MSG_SHUFFLE_SET_CARD (36)** — Not parsed. Face-down card tracking desyncs when set cards are shuffled.
- [ ] **MSG_TOSS_COIN (130)** — Parsed but no visual display of results.
- [ ] **MSG_TOSS_DICE (131)** — Parsed but no visual display of results.
- [ ] **MSG_EQUIP / MSG_UNEQUIP (93/95)** — Parsed but no equip relationship tracking.
- [ ] **MSG_CARD_TARGET / MSG_CANCEL_TARGET (96/97)** — Parsed but no targeting relationship tracking.
- [ ] **MSG_ADD_COUNTER / MSG_REMOVE_COUNTER (101/102)** — Parsed but no counter state on cards.
- [ ] **MSG_BECOME_TARGET (83)** — Parsed but not stored for UI highlighting.
- [ ] **MSG_CARD_HINT (160)** — Parsed but not processed.

### Tier 4 — Low priority / niche

- [ ] MSG_REFRESH_DECK (34) — Deck count refresh
- [ ] MSG_REVERSE_DECK (37) — Reverse deck order (Convulsion of Nature)
- [ ] MSG_DECK_TOP (38) — Reveal top card of deck
- [ ] MSG_CONFIRM_EXTRATOP (42) — Confirm top extra deck cards
- [ ] MSG_CARD_SELECTED (80) — Selection confirmation notification
- [ ] MSG_RANDOM_SELECTED (81) — Random selection notification
- [ ] MSG_MISSED_EFFECT (120) — "Missed timing" indicator
- [ ] MSG_BE_CHAIN_TARGET (121) — Chain target notification
- [ ] MSG_SHOW_HINT (164) — Script-defined hint text
- [ ] MSG_PLAYER_HINT (165) — Player-specific hint
- [ ] MSG_TAG_SWAP (161) — Tag duel swap (not relevant for 1v1)
- [ ] MSG_AI_NAME (163) — AI player name
- [ ] MSG_MATCH_KILL (170) — Match-ending effect
- [ ] MSG_CUSTOM_MSG (180) — Custom script message
- [ ] MSG_REMOVE_CARDS (190) — Card cleanup

### Already Fully Handled (23 messages)

MSG_RETRY, MSG_HINT, MSG_START, MSG_WIN, MSG_NEW_TURN, MSG_NEW_PHASE, MSG_DRAW, MSG_MOVE, MSG_POS_CHANGE, MSG_SET, MSG_SWAP, MSG_SUMMONING, MSG_SUMMONED, MSG_SPSUMMONING, MSG_SPSUMMONED, MSG_FLIPSUMMONING, MSG_FLIPSUMMONED, MSG_CHAINING, MSG_CHAINED, MSG_CHAIN_SOLVING, MSG_CHAIN_SOLVED, MSG_CHAIN_END, MSG_CHAIN_NEGATED, MSG_CHAIN_DISABLED, MSG_DAMAGE, MSG_RECOVER, MSG_LPUPDATE, MSG_PAY_LPCOST, MSG_SHUFFLE_DECK, MSG_SHUFFLE_HAND, MSG_SHUFFLE_EXTRA, MSG_ATTACK, MSG_BATTLE, MSG_ATTACK_DISABLED, MSG_DAMAGE_STEP_START, MSG_DAMAGE_STEP_END.

### Prompts with Full UI (12 of 21)

SelectIdleCmd, SelectBattleCmd, SelectEffectYn, SelectYesNo, SelectOption, SelectCard, SelectChain, SelectPlace, SelectDisfield (via SelectPlace), SelectPosition, SelectTribute, RockPaperScissors.

---

## 2. Client Feature Gaps

### Critical — Needed for basic playability

- [x] **Card data/name display** — Players see raw card codes (e.g., `46986421`) instead of names everywhere. The C++ bridge loads the card DB but exposes no query path to Java/client. **Single most impactful improvement** — unlocks card names in prompts, hover info, duel log, on-field stats. Options: (a) JNI method to query card data by code, (b) separate read-only SQLite on Java side.
- [x] **Card images** — No card artwork. Cards are colored rectangles with code numbers. Needs image loading system (local files by card code, potentially download from configurable URLs).
- [x] **Card tooltip with real data** — The card info banner UI shell exists (card-info-banner in XML) but `showCardInfo()` only shows `"Card #" + code`. Depends on card data system above.
- [x] **On-field ATK/DEF display** — Players can't see monster stats for combat decisions. edopro shows current ATK/DEF color-coded (green=buffed, red=debuffed). Depends on MSG_UPDATE_DATA parsing + card query pipeline.
- [x] **Card query pipeline to client** — JNI query infrastructure exists and works (DuelSession.query/queryLocation/queryField, QueryParser, 123 tests pass). But queries are never sent to the client. Server needs to forward query results after effects resolve.

### Important — Expected by players

- [ ] **Animation system** — Card movements are instant. edopro has per-card position/rotation interpolation. LDLib2 supports CSS transitions which could handle basic movement.
- [ ] **Sound/audio** — Completely silent duels. edopro has 24+ SFX types and BGM. Minecraft has a sound system that could be leveraged.
- [ ] **Duel log** — No action history. edopro has a scrollable log of all game events with card names. Depends on card data system.
- [ ] **Deck editor** — No in-game deck building. edopro has full search/filter/drag-drop editor. Could potentially integrate with existing deck file format.
- [ ] **Chain visualization** — Only "Chain: N" text. edopro shows numbered chain link icons on each card in the chain. Needs storing chain card locations and rendering indicators.
- [ ] **Targeting arrows / equip indicators** — No relationship visualization. edopro draws arrows for attacks, equips, and targeting. Depends on MSG_EQUIP/CARD_TARGET state tracking.

### Nice-to-have — Polish

- [x] **LP change animation** — LP values snap instantly. edopro animates over 10 frames with floating damage text.
- [ ] **Replay system** — No recording or playback. edopro saves replays with full packet stream.
- [ ] **Spectator mode** — No way for other players to watch a duel. edopro has full observer support.
- [ ] **Match mode (best-of-3)** — Only single duels. edopro supports matches with side decking between games.

---

## 3. Response Format Issues (Fixed)

All verified correct after the audit session on 2026-04-11:

- [x] ~~SelectPlace bitmask relative vs absolute~~ — Fixed
- [x] ~~SelectBattleCmd action indices (MP2=2, EndBattle=3)~~ — Fixed
- [x] ~~SelectCard cancel format (format code -1)~~ — Fixed
- [x] ~~selectUnselectCard format ([1][index] not just [index])~~ — Fixed
- [x] ~~sortCards AI caller (all-zeros not valid permutation)~~ — Fixed
- [x] ~~selectCmd javadoc with wrong BattleCmd indices~~ — Fixed

---

## Recommended Implementation Order

1. **Card data pipeline** (JNI query or Java-side SQLite) — unlocks items 2-4 below
2. **Card names in UI** — replace all `"Card #" + code` with real names
3. **MSG_UPDATE_DATA/CARD parsing** — enables live ATK/DEF display
4. **On-field ATK/DEF** — critical for gameplay decisions
5. **Card images** — visual identity of cards
6. **Missing Tier 1 messages** — CONFIRM_DECKTOP, CONFIRM_CARDS, SWAP_GRAVE_DECK, HAND_RES, SELECT_SUM, SELECT_UNSELECT_CARD
7. **Missing prompt UIs** — counter, sort, announce, sum selection
8. **Duel log** — game action history
9. **Chain/equip/target visualization** — relationship indicators
10. **Animation + sound** — polish

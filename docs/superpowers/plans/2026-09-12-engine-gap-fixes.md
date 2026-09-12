# Engine Gap Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Work through `docs/engine-gap-analysis.md` (2026-09-08) as a checklist, in the order its §11 recommends: unblock real duels, stop hidden-information leaks, harden the wire codec, give every prompt a UI, fix the duel lifecycle, move the client to a card object model, parse the remaining messages, surface hints, sweep dead code, add the feedback layer, then options. Every task ends with a tangible proof: a JUnit test that fails before and passes after, or a UI-harness screenshot showing the new surface.

**Spec:** `docs/engine-gap-analysis.md` is the binding authority for *what* is wrong and what edopro does. Section references below (§3.1, §12.5, …) point into it. Engine facts come from `docs/engine-wiki/` (pinned to the same ygopro-core and edopro commits); consult the wiki before re-deriving anything from `native/ygopro-core` or `../../../../edopro`.

**Architecture:** No new frameworks. Pure logic that the UI or server needs (sanitising, selection viability, opcode filtering, deck legality) goes into small Minecraft-free classes so it is unit-testable under plain JUnit. `ClientDuelState` and `DuelSession` are already Minecraft-free and stay that way. UI work lands in the existing subcontrollers (`PromptController`, `FieldRenderer`, `ClickDispatcher`, `ZoneInspectorController`) and `duel_screen.xml`.

**Tech Stack:** Java 21, NeoForge 21.1.224 (MC 1.21.1), LDLib2 2.2.39.a, JUnit 5 via Gradle 9, LDLib2 in-client UI test harness. Windows; commands run in Git Bash from the worktree root.

## Global Constraints

- Repo `CLAUDE.md` best practices bind every task: minimal, surgical, human-readable. Every changed line traces to the task. No speculative flexibility, no abstractions for single-use code, no reformatting or "improving" adjacent code. If you notice unrelated dead code, mention it in your report; do not delete it unless your task says so.
- **Engine is the authority on wire layout; edopro is the authority on host policy and prompt UX.** `docs/engine-wiki/protocol.md`, `message-index.md`, `responses.md`, `queries.md`, `edopro-host.md` first; then the cited lines in `native/ygopro-core/*.cpp` and `../../../../edopro/gframe/*.cpp` (paths relative to the worktree root). Never modify `native/ygopro-core/`.
- Shared context files in the plan workspace (`.superpowers/sdd/2026-09-12-engine-gap-fixes/`): `codebase-map.md` (where everything lives, with line numbers) and `ui-notes.md` (LDLib2 gotchas, harness usage). Read the map before editing; read the UI notes before any `client/` UI or XML/LSS change.
- **Tests:** `./gradlew test --console=plain` must end `BUILD SUCCESSFUL` before each commit (Bash timeout 600000 ms). The suite needs EDOPro data at `C:/ProjectIgnis` (present). Run the focused test class while iterating (`./gradlew test --tests '*MessageParserTest*' --console=plain`), the full suite once before committing.
- **UI harness:** `./gradlew runClient -PldTest=group:duelcraft --console=plain` must print `N/N scenarios passed` with N = every registered duelcraft scenario. Screenshots land in `build/ldlib2-uitest/screenshots/`. A task whose acceptance names a screenshot must add a scenario that produces it and quote the report line in its report. Scenarios register with `@LDLRegisterClient(name = "duel_<thing>", group = "duelcraft", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)` in `client/uitest/`. Scenarios must not depend on the card database or images being present.
- Stage only the paths your task touches (`git add <file>…`). Never `git add -A` or `git add .`. `CLAUDE.md`, `AGENTS.md`, `.superpowers/`, `native/ygopro-core/build/*.vcxproj`, `src/main/resources/natives/` are untracked or ignored and must never be staged.
- Commit messages: one imperative sentence, no `feat:`-style prefix, matching repo history (`Fixed player1 deck size being erroneously given to player2 duel start packet`). One or more commits per task.
- Message record shapes are shared contracts between `MessageParser`, `DuelMessageCodec`, server handlers and the client. When a task changes a record, it updates all four and the tests in the same commit. `DuelMessageCodec.encode` is an exhaustive switch, so a new record without a codec arm fails compilation on purpose.
- Player indices stay absolute (engine 0/1) on the wire; the client mirrors from `DuelStartPayload.localPlayer`.
- Response byte layouts are defined in `docs/engine-wiki/responses.md` and `playerop.cpp`; `ResponseBuilder` is the only place that encodes them.
- The `/duel test` solo duel (`SoloDuelHandler`) must keep working end to end after every task: it is how the user verifies UI changes by hand.
- Docs: when a task closes a checklist item, tick its `- [ ]` box in `docs/engine-gap-analysis.md` in the same commit (turn `- [ ]` into `- [x]`; do not rewrite prose). Also update `docs/engine-gap-analysis.md` matrix cells only where the task changes the verdict.

---

### Task 1: Battle-phase Activate sends type 0; Battle destroy flags named; double-send guard

Closes §3.2, the `Battle` rename in §1.7, and the `answered` guard in §12.5/§12.6.

**Requirements**
- `ClientDuelState.buildBattleCmdActions` and `ClickDispatcher.getActionIconInfo` send the engine's battle action codes: 0 activate, 1 attack, 2 to Main Phase 2, 3 end battle (`playerop.cpp:55-67`). Replace the unnamed ordinals with named constants (one small holder, e.g. `BattleAction`/`IdleAction` ints in `ClientDuelState` or `OcgConstants` if such constants exist there already; do not create a new file for this if an existing constants home fits).
- Rename `DuelMessage.Battle.atkDamage/defDamage` to `atkDestroyed/defDestroyed` (or equivalent) with the same layout; update parser, codec, tests, and any consumer.
- edopro's `answered` guard: a response may be sent at most once per prompt. `LDLibDuelScreen.sendResponse` (or `ClientDuelState`) must ignore a second send for the same pending prompt. The guard resets when the next prompt (or `Retry`) arrives. Put the flag in `ClientDuelState` so it is testable.

**Acceptance evidence**
- New JUnit test class `ClientDuelStateTest` (MC-free) with: a `SelectBattleCmd` containing an activatable card yields an action whose response bytes decode to type 0; the guard rejects a second response until a new prompt arrives.
- `MessageParserTest` battle test reads the renamed fields.
- Full suite `BUILD SUCCESSFUL`; report quotes the test count line.

---

### Task 2: `MSG_RETRY` restores the prompt; retry reaches only the responder; solo AI re-answers

Closes §3.3 and its second bullet, §7.2 "`MSG_RETRY` is broadcast", and the Solo AI retry item in §2.2.

**Requirements**
- Client: `ClientDuelState` keeps the last prompt record after a response is sent (e.g. `lastPrompt`). On `Retry`, `pendingPrompt` is restored from it, the answered guard from Task 1 resets, and `PROMPT` is marked dirty so `PromptController.rebuild` shows it again. A short status text ("Invalid response, try again") is set via the existing status label path.
- Server: `ServerDuelHandler` tracks the player of the last prompt it forwarded and sends `Retry` only to that player (edopro sends it to everyone but ends the duel; Duelcraft continues, so only the responder needs it). Return code stays 1.
- `SoloDuelHandler`: when the AI's own answer triggers `Retry`, it must not hang. Re-answer with a fallback (for card-list prompts pick the first legal count per §2.1; for the rest, the existing default) at most a bounded number of times, then log an error and forward the prompt to the human so the duel is not wedged.
- Do not add generic "restore any state" machinery; only the prompt is restored.

**Acceptance evidence**
- `ClientDuelStateTest`: apply a prompt → mark response sent → apply `Retry` → `pendingPrompt` equals the original prompt and the guard accepts a new send.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 3: `SELECT_SUM` parsed and answered per the engine layout

Closes §3.1 (all three bullets), the `SELECT_SUM` row in §2.2, and the `parseSelectSum` item in §10.

**Requirements**
- `SumCard.read`: `u32 code, u8 con, u8 loc, u32 seq, u32 position, u32 sumParam` (`playerop.cpp:808-819`). Expose `position`, `sumParam`, and helpers `value1()` = low 16 bits, `value2()` = high 16 bits of `sumParam`. Update `DuelMessageCodec`.
- Rewrite `MessageParserTest.parseSelectSum` to encode the engine layout, asserting field values at offsets (not just entry size). Include a card with `sumParam = (7 << 16) | 3` and a face-down position to prove the two are no longer folded.
- Response: indices address `selectable` only (must-select cards are never indexed); `min`/`max` bound the number of selectable picks. Move the viability logic out of `PromptController` into a small MC-free class (suggested `client/SumSelection.java`, package-private constructor taking the record): `canPick(index)`, `toggle(index)`, `isComplete()`, `responseIndices()`. Mode 0 (`max != 0`): exact sum with `[min, max]` picks; mode 1 (`max == 0`): sum ≥ target. A card counts as `value1` or `value2` when `value2 != 0` (either value may complete the sum, like edopro `client_field.cpp:1021-1151`). Auto-submit when must-select alone completes the sum.
- `PromptController.buildSelectSumPrompt` uses `SumSelection`; the caption shows the target and the running total.
- `SoloDuelHandler` answers `SelectSum` via `SumSelection` (first viable combination), not "one card".

**Acceptance evidence**
- `MessageParserTest.parseSelectSum` (engine layout, offsets asserted), new `SumSelectionTest` covering mode 0 exact, mode 1 ≥, `value2` alternative, must-select-only completion, and that `responseIndices()` never includes must-select cards.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 4: `SELECT_DISFIELD` keeps id 24 and collects `count` zones

Closes §3.5 (both bullets), the `SELECT_DISFIELD` row of §2.2, the `MSG_SELECT_DISFIELD` codec bullet of §4, and the `SELECT_PLACE` count loop of §12.6.

**Requirements**
- Give `SELECT_DISFIELD` its own record (`DuelMessage.SelectDisfield(int player, int count, int field)`) with `type() == MSG_SELECT_DISFIELD`; parser, codec, `ServerDuelHandler` and `SoloDuelHandler` routing, client `apply`. `SelectPlace` stays for id 18.
- `ResponseBuilder.selectPlaces(List<ZoneRef>)` (or `int[]` triples) emitting `count` × `u8 player, u8 location, u8 seq`; keep `selectPlace(...)` as the single-zone convenience calling it.
- `PromptController`: for both records, each click toggles a zone; the caption counts down (`"Select N more zone(s)"`); the response is sent when `count` zones are chosen. `SelectDisfield` caption says the zones become unusable (edopro string 570). Blocked bits and EMZ mapping follow `ui-notes.md`. Include the §3.9 EMZ item: highlight and accept opponent-side EMZ bits (21/22) when they are the only legal zones.
- `SoloDuelHandler` answers `SelectDisfield` with the first `count` free zones.

**Acceptance evidence**
- Parser test for id 24; codec round-trip for `SelectDisfield`; `ResponseBuilderTest` for `selectPlaces` with count 2 (6 bytes, exact values).
- New harness scenario `duel_disfield_prompt`: feed a `SelectDisfield(count=2)` with two zones free, click one, assert the caption still shows one remaining, screenshot `duel_disfield_prompt.png`. Report quotes the `scenarios passed` line.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 5: `ANNOUNCE_CARD` response path

Closes §3.8 and the parse/builder/solo-AI parts of the `ANNOUNCE_CARD` row in §2.2 (the search UI is Task 10).

**Requirements**
- Parse `MSG_ANNOUNCE_CARD`: `u8 player, u8 count, u64 opcodes[count]` → `DuelMessage.AnnounceCard(int player, List<Long> opcodes)`; codec arm; parser test with two opcodes.
- `ResponseBuilder.announceCard(int code)` → 4-byte `int32`; test.
- `SoloDuelHandler`: when the AI is prompted with `AnnounceCard`, forward the prompt to the human (test mode has no server-side card database, so the human declares for the AI); log that it did so. No engine-side heuristic.
- `PromptController`: a minimal interim UI so the duel is not wedged before Task 10: a text field accepting a passcode plus OK, sending `announceCard`. Keep it small; Task 10 replaces it.

**Acceptance evidence**
- `MessageParserTest.parseAnnounceCard`, `ResponseBuilderTest.announceCard`, codec round-trip.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 6: Stop hidden-information leaks (host policy from edopro)

Closes §3.6 (all bullets), §12.6 Host items 1-6 and 9 ("omit private fields"), and §7.1's 🐞.

**Requirements**
- Extract the recipient filter from `ServerDuelHandler.hideInfo/sanitizeCard` into an MC-free class `duel/MessageSanitizer.java` with `static DuelMessage forRecipient(DuelMessage msg, int recipient)` and a routing decision `static Recipients recipientsOf(DuelMessage msg)` (`BOTH`, `PLAYER_ONLY(p)`, `ALL_EXCEPT(p)`). `ServerDuelHandler` and `SoloDuelHandler` both use it (solo: the human is recipient `humanPlayer`).
- Rules (edopro `generic_duel.cpp` lines cited in §12.1):
  - `Move`: zero the code for the non-controller when `!(loc & (GRAVE|OVERLAY)) && ((loc & (DECK|HAND)) || (pos & FACEDOWN))`, using `to`.
  - `Draw`: opponent sees code 0 per card unless that card's position is face-up. This needs `Draw` to carry per-card position: change `Draw(int player, List<Integer> codes)` to carry `List<DrawnCard(code, position)>` or a parallel `positions` list (also closes the `MSG_DRAW` position item in §1.7). Update parser, codec, client, fixture, tests.
  - `SelectCard`, `SelectTribute`, `SelectUnselectCard`: zero the code of every candidate whose controller is not the prompted player (prompt still goes only to that player).
  - `ConfirmCards`: if the first card's location is `DECK` or `EXTRA`, target player only; otherwise both.
  - `Hint`: types 1,2,3,5 → target only; 4,6,7,8,9,11 → everyone except target; 10 and 200-203 → everyone (200 → target's team = target in 1v1).
  - `MissedEffect` (once parsed in Task 14) → controller only; leave a `default` that broadcasts.
  - `QueriedCard` sanitising: when a face-down, non-public card is stripped, **clear the flag bits** of the omitted fields too (private set = CODE, ALIAS, TYPE, LEVEL, RANK, ATTRIBUTE, RACE, ATTACK, DEFENSE, BASE_ATTACK, BASE_DEFENSE, STATUS, LSCALE, RSCALE, LINK); position, reason, counters, overlay count, equip/target survive. Add `QUERY_IS_HIDDEN` to the server's query flags and treat `isHidden` like face-down for the opponent.
- Keep `ServerDuelHandler` a thin router calling `MessageSanitizer`.

**Acceptance evidence**
- New `MessageSanitizerTest` covering each rule above with both recipients (at least: Move to hand hides for opponent and not for controller; Move to grave face-down keeps the code; Draw face-up stays visible; SelectCard opponent-controlled candidate zeroed; ConfirmCards deck → target only, field → both; Hint type 3 → target only, type 6 → other player, type 10 → both; sanitised QueriedCard has CODE flag cleared and position kept).
- Full suite `BUILD SUCCESSFUL`.

---

### Task 7: Codec hardening and a full round-trip test

Closes §4 open bullets except the version byte (out of scope), the codec bullets of §3.4, and the `DuelMessageCodec` item of §10.

**Requirements**
- `DuelMessageCodec`: every `location` byte read with `readUnsignedByte`; every card-list writer/reader consistent (`SortableCard` included). `readByteArray` bounds its length (reject > 1 MiB with an exception).
- `DuelMessageCodecTest`: one round trip per record in the sealed interface (use reflection over `DuelMessage.class.getPermittedSubclasses()` to assert nothing is missed), with `LocInfo` locations `0x84` and `0x88`, `u64` descs above 2^32, negative-looking `int` fields, and empty lists.
- Remove the unreachable `decode` `default -> Raw` mis-framing by making unknown types throw.

**Acceptance evidence**
- `DuelMessageCodecTest` green with the permitted-subclass coverage assertion; a temporary revert of one `readUnsignedByte` makes the `0x84` case fail (describe in report as RED evidence).
- Full suite `BUILD SUCCESSFUL`.

---

### Task 8: Prompt UIs: `SELECT_COUNTER`, `SORT_CARD`, `SORT_CHAIN`

Closes those three rows of §2 / §3.7, the `SORT_*`/`SELECT_COUNTER` items of §2.2 and §12.6, the `sortCards` javadoc fix, and the Solo AI counter cap.

**Requirements (edopro §12.5)**
- `SelectCounter`: no dialog. Candidate cards get the `.target` highlight; each click removes one counter from that card; the caption reads `Remove N "<counter name>"` with the remaining count (counter name via `SystemStringTable` string `1000 + type` if present, else the number); a card whose remaining count hits 0 stops being selectable; auto-submit at 0 with one `int16` per card in message order (`ResponseBuilder.selectCounters`). Put the tally in an MC-free helper (`client/CounterSelection.java`) so it is testable.
- `SortCard` / `SortChain`: card-list panel (reuse the prompt overlay card list); clicking assigns the next ordinal (drawn on the card), re-clicking removes it; when every card is numbered send one byte per card where `response[i]` is the destination rank of original card `i` (`ResponseBuilder.sortCards`); a "Keep order" button sends `sortCardsDefault()` (`-1`). Fix the inverted `sortCards` javadoc. Ordinal bookkeeping in an MC-free helper (`client/SortSelection.java`).
- `SoloDuelHandler`: counters spread across cards respecting each card's cap; sorts keep `-1`.

**Acceptance evidence**
- `CounterSelectionTest`, `SortSelectionTest`.
- Harness scenarios `duel_select_counter` (two cards with counters, click one, caption decremented, screenshot) and `duel_sort_card` (three cards, click two, ordinals visible, screenshot). Report quotes `scenarios passed`.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 9: Prompt UIs: `ANNOUNCE_NUMBER`, `ANNOUNCE_RACE`, `ANNOUNCE_ATTRIB`

Closes those rows of §2 / §3.7 and §12.6.

**Requirements (edopro §12.5)**
- `AnnounceNumber`: reuse `buildOptionPrompt`'s button list over the `List<Long>` values; the response is the **index** (`ResponseBuilder.announceNumber(index)`).
- `AnnounceRace` / `AnnounceAttrib`: checkbox grid showing only the bits set in `available`; names from `SystemStringTable` (races 1020-1044ish, attributes 1010-1016; check `strings.conf` for the exact ids and record them in a comment); submit automatically when exactly `count` boxes are checked (`announceRace(long mask)` / `announceAttrib(int mask)`). Selection tally in an MC-free helper (`client/BitSelection.java`).

**Acceptance evidence**
- `BitSelectionTest` (count gating, only-available bits).
- Harness scenarios `duel_announce_race` (available = 3 races, count 1, screenshot before the click) and `duel_announce_number` (four values, screenshot). Report quotes `scenarios passed`.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 10: `ANNOUNCE_CARD` search UI with an `is_declarable` port

Closes the `ANNOUNCE_CARD` UI items in §2.2 / §12.6 and replaces Task 5's interim field.

**Requirements**
- `client/carddata/DeclarableFilter.java` (MC-free): a stack-machine evaluator over the opcode list, ported from `playerop.cpp:1004-1069` / edopro `client_field.cpp:1252-1315`: arithmetic (`ADD SUB MUL DIV`), logical (`AND OR NEG NOT`), bitwise (`BAND BOR BNOT`), `ISCODE ISSETCARD ISTYPE ISRACE ISATTRIBUTE`, getters (`GETCODE GETSETCARD GETTYPE GETRACE GETATTRIBUTE`), `ALLOW_ALIASES`, `ALLOW_TOKENS`; aliases and tokens excluded unless allowed. Opcode constants come from `OcgConstants` (add any missing `OPCODE_*` with values from `ocgapi_constants.h`). Input is a small card-facts record (code, alias, setcodes, type, race, attribute) so it does not depend on `CardInfo`'s exact shape.
- `CardDatabase`: a query that streams the facts record for all cards (or for a name substring), so the filter can run over the DB. Search matches a passcode or a case-insensitive name substring, exact matches first, capped at ~50 rows.
- `PromptController.buildAnnounceCardPrompt`: text field + result list + OK; a click on a result sends `announceCard(code)`.

**Acceptance evidence**
- `DeclarableFilterTest`: `ISSETCARD` match/mismatch, `ISTYPE` with `AND`, `NOT`, alias excluded by default and allowed with `ALLOW_ALIASES`, token rules.
- Harness scenario `duel_announce_card` shows the dialog with the search field (DB may be absent; the scenario only asserts the field and OK are visible) and screenshot.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 11: Duel lifecycle: forfeit, disconnect, response ownership, result overlay, concede, ESC

Closes §7.2 items (ownership, forfeit payload, disconnect, stale invites, `/duel challenge` deck check), §8 Win/lose and ESC, §12.3 surrender/disconnect and response gate, §12.6 "Surrender and disconnect end with a synthesised MSG_WIN".

**Requirements**
- `DuelManager.handleResponse` accepts a response only from the player whose prompt is pending (`ServerDuelHandler` exposes the pending player); otherwise log and drop.
- Forfeit and disconnect: `DuelManager` sends `DuelEndPayload(winner = 1 - player, reason 0 for surrender, 4 for disconnect)` to both players (the leaver too when still connected), then ends the session. Engine `DUEL_STATUS_END` without `MSG_WIN` also sends an end payload (winner 2 = draw, reason 0). Add a `PlayerEvent.PlayerLoggedOutEvent` listener in `Duelcraft`/`DuelManager` that forfeits an active duel and clears invites for that player. Invites expire after 60 s (replace the `FIXME`).
- `/duel challenge` refuses when either player has no current deck, with the same message `accept` uses today.
- Client result overlay: on `DuelEndPayload`, show an overlay (winner name, reason text: LP, deck out, surrender, disconnect, timeout, effect) with a "Close" button that calls `LDLibDuelScreen.close()`; the screen no longer closes by itself.
- Concede button on the duel HUD sending a new `DuelConcedePayload` (C→S), registered in `DuelNetworking`; the server treats it as forfeit.
- ESC: `shouldCloseOnEsc` returns false while a duel is live; add `/duel show` (client-side command or existing client command path) that re-opens `LDLibDuelScreen` from the current `ClientDuelState`.

**Acceptance evidence**
- Harness scenario `duel_result_overlay`: apply an end payload's effect (a static `LDLibDuelScreen.showResult(...)` or equivalent), assert `#result-overlay` visible and the winner text present, screenshot.
- Unit test for the ownership check if `DuelManager`'s pending-player check can be isolated (MC-free helper); otherwise the report documents a `/duel test` manual check with the log line.
- Full suite `BUILD SUCCESSFUL`; harness `scenarios passed`.

---

### Task 12: Client card object model; overlays, counters, links, targets, disabled zones

Closes §3.4 (client bullets), §8 card object model / overlays / counters / links / targeting / disabled zones / Swap, §12.6 Client model items 1-4, 6-7 partially, and the `BecomeTarget`/`FieldDisabled` highlights.

**Requirements**
- Replace `ClientDuelState`'s parallel `mzone[]/szone[]` code+position arrays and stat maps with one `ClientCard` object per card (fields: code, position, controller, location, sequence, stats from queries, `List<ClientCard> materials`, `Map<Integer,Integer> counters`, `ClientCard equipTarget`, `Set<ClientCard> targets`, `desc hints` can wait). Containers: `mzone[2][7]`, `szone[2][8]`, hand/grave/removed/extra/deck as `List<ClientCard>` (deck as a list of blank cards sized from the start payload, so Task 14 can set deck-top codes). Public read API for the renderers stays as close as practical to today's to keep the diff surgical.
- `Move` handles `location & LOCATION_OVERLAY` (0x84/0x88): attach to the host at `(controller, loc & ~0x80, seq)` at material index `position`; detach removes the right material and renumbers. Clear counters and targets when a card leaves the field or turns face-down; detach equips on any location change (edopro `client_field.cpp:3044-3215`, `:3217`).
- `Swap` swaps the two objects wherever they are (MZONE or SZONE).
- `AddCounter/RemoveCounter`, `Equip`, `CardTarget/CancelTarget`, `BecomeTarget`/`CardSelected` (transient highlight set cleared on the next prompt), `FieldDisabled` (mask stored, halves swapped for player 1) all update state and set the right dirty flags.
- `FieldRenderer`: draws a material count badge, a counter badge (total count), a `.targeted` highlight, and a grey overlay on disabled zones. Keep the visuals minimal (badges as small text elements inside the slot; one LSS class each).
- Update `DuelScreenFixture` if constructor shapes change.

**Acceptance evidence**
- `ClientDuelStateTest`: overlay attach with location 0x84 places the material on the host; detach renumbers; counters clear when the card leaves the field; Swap across SZONE; FieldDisabled mask mapping for player 1.
- Harness scenario `duel_card_model`: field with one XYZ host carrying two materials, one card with 3 counters, one disabled zone, one targeted card; screenshot.
- Full suite and harness green.

---

### Task 13: Query pipeline on edopro's schedule and masks

Closes §5.2 open bullets, §12.6 Host items 8-9, the `QUERY_*` consumption gaps for `OVERLAY_CARD`, `COUNTERS`, `LSCALE`/`RSCALE`, and the `FieldQueryTest`/`OcgCoreTest.nDuelQuery` items of §10.

**Requirements**
- `DuelSession`: replace the "refresh MZONE/SZONE/EXTRA after every pause" with edopro's schedule (§12.2): before `SELECT_IDLECMD`/`SELECT_BATTLECMD` refresh MZONE, SZONE, HAND for both; before `SELECT_CHAIN` and `NEW_TURN` MZONE and SZONE; after `DRAW`/`SHUFFLE_HAND` that hand; after `SHUFFLE_EXTRA` extra; after `NEW_PHASE`, `*SUMMONED`, `CHAINED`, `CHAIN_SOLVED`, `CHAIN_END` MZONE+SZONE (and HAND for `NEW_PHASE`, `CHAINED`, `CHAIN_END`); after `MOVE` the destination slot when location or controller changed and destination is not overlay; after `POS_CHANGE` to face-up that slot; after `SWAP` both slots. Never `DECK` or `REMOVED`. Use edopro's masks per location (`RefreshMzone 0x3881fff`, `RefreshSzone 0x3e81fff`, `RefreshHand 0x3781fff`, `RefreshGrave/Extra 0x381fff`, `RefreshSingle 0x3f81fff`). Single-slot refreshes construct `UpdateCard` (it already exists as a record).
- Ordering: refreshes scheduled "before" a prompt are emitted before the prompt is forwarded; the empty-chain auto-pass no longer discards already-parsed trailing messages.
- `FieldQuery`: parse `OVERLAY_CARD` (u32 n + n×u32), `COUNTERS` (u32 n + n×u32 `type | count<<16`), `EQUIP_CARD`/`REASON_CARD` (10-byte loc_info), `TARGET_CARD` (u32 n + n×loc_info), `OWNER`, `IS_HIDDEN`; error on a missing `QUERY_END`.
- Client `UpdateData`/`UpdateCard` application writes code/position back into the `ClientCard` (self-heal), assigns counters (not accumulate), sets materials' codes, and shows pendulum scales on the card overlay when `LSCALE`/`RSCALE` present.
- `OcgCoreTest`: call `nDuelQuery` on a real duel and run `FieldQuery.parse` over the bytes; fix the `MSG_WIN` read (`u8 + u8`); import `OcgConstants` instead of local copies.

**Acceptance evidence**
- `FieldQueryTest` additions for each new flag present and absent, empty buffer, missing `QUERY_END`.
- `OcgCoreTest` live query test passes (quote its name and the suite line).
- A `DuelSessionTest` (MC-free; use a fake `OcgCore`-like seam only if one already exists, otherwise test the scheduling table as a pure function `RefreshSchedule.before(msg)`/`after(msg)`).
- Full suite `BUILD SUCCESSFUL`.

---

### Task 14: Parse the remaining messages and apply them on the client

Closes §1.7 (all items except the deletions, which are Task 18), the `Raw` rows of §1, and §12.4 rows for `DECK_TOP`, `REVERSE_DECK`, `SWAP_GRAVE_DECK`, `SHUFFLE_SET_CARD`, `REMOVE_CARDS`, `RANDOM_SELECTED`, `MISSED_EFFECT`, `CONFIRM_EXTRATOP`, `SHUFFLE_DECK` code-zeroing.

**Requirements** (layouts in §1.2-1.4 and `docs/engine-wiki/message-index.md`)
- Records + parser + codec + tests: `ConfirmExtraTop` (same body as `ConfirmDeckTop`), `ReverseDeck` (empty), `DeckTop(player, offsetFromTop, code, position)`, `RandomSelected(player, List<LocInfo>)`, `MissedEffect(LocInfo, code)`, `RemoveCards(List<LocInfo>)`, `ShuffleSetCard(location, List<LocInfo> from, List<LocInfo> follow)`, `SwapGraveDeck(player, extraCount, byte[] mask)`, `PlayerHint(player, type, desc)`, `MatchKill(code)`. Keep `position` in `SELECT_CHAIN` entries (`ActivatableCard` gains a field; update tests).
- Client application per edopro (§12.4): `ConfirmExtraTop` → zone inspector titled for the extra deck (and fix the §3.9 inspector item: `showConfirmCards` resets the inspected pile so `PILE_COUNTS` does not overwrite the reveal; title says whose deck); `ReverseDeck` toggles `deckReversed[player]`; `DeckTop` sets the code of deck card `size-1-offset`; `ShuffleDeck` zeroes deck codes; `SwapGraveDeck` swaps the two lists and routes bitmask-flagged cards to the extra deck face-down; `ShuffleSetCard` zeroes the named cards and moves them (with materials) to the new positions; `RemoveCards` deletes and renumbers; `RandomSelected`/`MissedEffect` feed the transient highlight set from Task 12 (a "missed timing" badge for the latter, cleared on next prompt).
- Sanitiser routing for `MissedEffect` (controller only) and `DeckTop` (both players; public).

**Acceptance evidence**
- Parser tests for every new record (byte-level), codec round trips (the reflection test from Task 7 enforces coverage), `ClientDuelStateTest` cases for `DeckTop` indexing, `SwapGraveDeck` routing, `RemoveCards` renumbering.
- Full suite `BUILD SUCCESSFUL`.

---

### Task 15: Prompt polish from §2.2

Closes the remaining §2.2 items: `SELECT_CARD` Confirm/caption/guard, `SELECT_TRIBUTE` Confirm, `SELECT_CHAIN` desc labels / forced+empty / `speCount`, `SELECT_IDLECMD` shuffle-hand and desc disambiguation, `SELECT_UNSELECT_CARD` progress, `SELECT_POSITION` card-image buttons, `SELECT_EFFECTYN` card highlight, plus §12.6 Prompt items 2-3, 7, 12.

**Requirements (edopro §12.5)**
- Shared Cancel/Finish button with three states (hidden / "Cancel" / "Finish"), also bound to right-click: `SELECT_CARD` field mode shows Finish once `min` is met and auto-submits only at `max` (or when everything selectable is selected and `min` is met); `SELECT_TRIBUTE` auto-submits only when the card count reaches `max`, Finish once the summed tribute meets `min`; `SELECT_UNSELECT_CARD` shows `selected/min-max` in the caption.
- `handleFieldClick` ignores field clicks while a dialog-mode selection is open.
- `SELECT_CHAIN`: when a card has several activatable effects, open the option dialog with each `desc` (skip when one); forced with empty list answers 0; the caption shows the hint string 550/556.
- `SELECT_IDLECMD`: a "Shuffle hand" button when `canShuffle`; multiple effects on one card disambiguated through the option dialog with `desc`.
- `SELECT_POSITION`: card-image buttons (rotated/face-down variants can be the card back for face-down; keep it simple).
- `SELECT_EFFECTYN`: highlight the card on the field while the dialog is open.

**Acceptance evidence**
- Harness scenarios: `duel_select_card_finish` (min 1 max 2, one click → Finish visible, screenshot), `duel_select_position` (card-image buttons visible, screenshot), `duel_idle_shuffle` (shuffle button visible when `canShuffle`).
- `ClientDuelStateTest`/helper tests for the tribute Finish gating.
- Full suite and harness green.

---

### Task 16: Hint surfaces

Closes §1.1 `MSG_HINT` ❌, `MSG_CARD_HINT`, `MSG_PLAYER_HINT` client rows, §8 Hint captions, §11.7, §12.6 client items on hints and toasts.

**Requirements (edopro §12.4 `MSG_HINT` row)**
- `HINT_SELECTMSG` (3): stored and used as the caption of the next prompt in `PromptController` (falls back to the current hard-coded text).
- `HINT_MESSAGE` (2): blocking modal with OK.
- `HINT_OPSELECTED` (4), `HINT_RACE` (6), `HINT_ATTRIB` (7), `HINT_CODE` (8), `HINT_NUMBER` (9): a toast line (2-3 s, top centre) with the resolved text (desc via `resolveDesc`, race/attribute names from `SystemStringTable`, card name via `CardDatabase` when available).
- `HINT_CARD` (10): show the card in the card-info banner briefly.
- `HINT_ZONE` (11): flash the zones in the `SELECT_PLACE` bit layout for ~1 s.
- `CARD_HINT` `CHINT_TURN` (type 3?) shows a numbered badge on the card; `DESC_ADD/REMOVE` refcounted per card and shown in the card-info banner; `PlayerHint` `DESC_ADD/REMOVE` shown next to the player name. Check the exact `CHINT_*`/`PHINT_*` values in `OcgConstants`.
- A single `Toast` helper element in `LDLibDuelScreen`, not one per hint type.

**Acceptance evidence**
- Harness scenario `duel_hints`: apply `Hint(SELECTMSG)` then a `SelectCard` → caption text equals the resolved hint; apply `Hint(OPSELECTED)` → `#toast` visible; screenshot.
- `ClientDuelStateTest` for the selectmsg capture and card-hint refcount.
- Full suite and harness green.

---

### Task 17: Test coverage backfill

Closes §10 remaining items.

**Requirements**
- Byte-level parser tests for every case listed as untested in §10 that earlier tasks did not cover: `RETRY`, `SELECT_BATTLECMD`, `SELECT_IDLECMD` (all six lists with non-empty entries and the three entry widths), `SORT_CHAIN`, `SELECT_COUNTER`, `SORT_CARD`, `SWAP`, `FIELD_DISABLED`, `SPSUMMONING`, `SPSUMMONED`, `FLIPSUMMONING`, `FLIPSUMMONED`, `CHAIN_SOLVED`, `CHAIN_DISABLED`, `CARD_SELECTED`, `BECOME_TARGET`, `CARD_TARGET`, `CANCEL_TARGET`, `PAY_LPCOST`, `ADD_COUNTER`, `REMOVE_COUNTER`, `TOSS_DICE`, `ANNOUNCE_ATTRIB`, `ANNOUNCE_NUMBER`, `CARD_HINT`. Each asserts field values, not just record type.
- `ResponseBuilderTest`: `selectCardsCancel`, `sortCardsDefault`, and every method added by Tasks 3-10.
- A build-time cross-check test: parse `native/ygopro-core/ocgapi_constants.h` with a regex and assert every `MSG_*`, `QUERY_*`, `LOCATION_*`, `POS_*`, `HINT_*`, `OPCODE_*` constant in `OcgConstants` has the header's value (skip when the header is absent with `assumeTrue`).
- A live `OcgCoreTest` that queries a face-down deck card with `QUERY_IS_PUBLIC` and asserts `MessageSanitizer` strips its code for the opponent.

**Acceptance evidence**
- Full suite `BUILD SUCCESSFUL`; report lists the new test count vs. the baseline count.

---

### Task 18: Dead-code sweep and `ResponseValidator` wiring

Closes §9, §3.9 validator bullets, §12.6 Host item 17 (validate before sending), and the §1.7 deletions.

**Requirements**
- Delete: `MSG_START` parser case / `DuelMessage.Start` / codec arm / client branch; `MSG_UNEQUIP` case / record / arm; `MessageParser.readActivatableList`; `DuelSession.queryLocation/queryField` if still test-only after Task 13 (keep if Task 13 used them); `ClientDuelState` fields listed in §9 that are still unused after Tasks 11-16 (`DirtyFlag.LP`, `startingLP`, `winReason`, `lastAction`, `CardAction.label`, `lastHintType/Data`); `PromptController.showWinOverlay` if Task 11 replaced it; `CardInfo.linkArrows/leftScale/rightScale` only if still unused. Keep `DuelMessage.UpdateCard` (Task 13 uses it).
- `ResponseValidator`: fix `selectTribute` (sum of tribute counts ≥ min), `selectUnselectCard` (indices ≥ selectable size address the unselect list; `-1` legal when `cancelable || finishable`), `zoneToBit` (SZONE 6/7), add `selectSum`, `selectDisfield`, `announceCard`, `selectCmd` (battle 0-3, idle 0-8 with index bounds). Wire it into the client send path: `LDLibDuelScreen.sendResponse` validates against `pendingPrompt` and, on failure, logs and shows the status text instead of sending (this is edopro's model: a retry should never happen).
- Replace the magic numbers listed in §9 with the constants from Task 1 / `OcgConstants`.

**Acceptance evidence**
- `ResponseValidatorTest` updated for the corrected rules (the old wrong expectations flipped, new methods covered).
- `git grep` shows no references to the deleted symbols; full suite `BUILD SUCCESSFUL`; harness green (screens still open).

---

### Task 19: Feedback layer

Closes §8 duel log / chain visualisation / battle feedback / LP feedback / phase display / coin-dice, §11.9, §12.6 client items on log, LP colours, coin/dice, chain markers.

**Requirements (keep each element minimal)**
- Duel log: a scrollable panel (toggle button on the HUD) listing one line per event: attack (attacker → target or direct), coin/dice results, chain link activated (card name + desc text), negated/disabled, targets, equips, LP change with reason (damage/recover/cost), summons, missed timing, turn and phase changes. Lines come from a `DuelLog` MC-free model (`List<Entry(code, text)>`) populated in `ClientDuelState.apply`; clicking a line with a code shows the card info.
- Chain: numbered link markers drawn on the trigger location's slot (`Chaining.triggerLocation`), blink on `ChainSolving`, "negated" stamp on `ChainNegated/Disabled`, all cleared at `ChainEnd`; `ChainSolved` pops the link.
- Battle: attack arrow (a simple line/element between the two slots, or a highlight pair) for ~1.5 s; `Battle` writes the combat ATK/DEF onto both cards while the damage step lasts.
- LP: signed floating number in red (damage), green (recover), blue (cost) near the LP bar, ~1 s; `LpUpdate` silent.
- Turn/phase banner for `NewTurn` and battle sub-phases shown as their own names (start/step/damage/damage-calc/end).
- Toasts for coin and dice reuse Task 16's toast.

**Acceptance evidence**
- `DuelLogTest` (MC-free) for line generation from a sample message sequence.
- Harness scenario `duel_feedback`: chain of two links with markers visible, one LP damage number visible, the log panel open with ≥ 3 lines; screenshot.
- Full suite and harness green.

---

### Task 20: Options, deck legality, first-turn choice, waiting indicator

Closes §6.2 options bullets (except match mode), §7.2 first-turn and `MSG_WAITING`, §12.3 deck check and RPS, §12.6 Host items 7, 12-14, 16.

**Requirements**
- `/duel challenge <player> [rule] [seed] [lp] [hand] [draw]` and `/duel test …` accept starting LP, hand size and draw count (defaults 8000/5/1); `PlayerOptions` carries them; LP bars use the real starting LP as max. Individual `DUEL_*` flags composable via `/duel rule flags <hex>` is out of scope; presets stay.
- Deck legality at challenge/accept time (`core/DeckValidator.java`, MC-free): main 40-60, extra ≤ 15, side ≤ 15, at most 3 copies by code (alias collapsing needs the DB, so by code only; note the limitation in a comment), and rule-set forbidden types via the `DUEL_MODE_MR*_FORB` masks already declared. Needs card types server-side: the server has no DB, so validate what `.ydk` gives (counts and copies) and skip type checks unless a type source exists; say so in the report.
- First turn: after `accept`, both players get a `RockPaperScissors` prompt from the host (reuse the existing record and UI with a distinguishing flag or a new `SelectHand` payload), ties replayed; the winner gets a `SelectYesNo`-style "Go first?" choice; the player going first becomes engine player 0 (swap the pair before `startDuel`).
- `MSG_WAITING`: when a prompt is forwarded to one player, the other receives `DuelMessage.Waiting` (new record; not from the engine) and the client shows "Waiting for opponent…" in the status label until its next prompt.
- Per-turn time limit: out of scope (ruling: optional in edopro; note in report).

**Acceptance evidence**
- `DeckValidatorTest`; a unit test for the RPS resolution (cycle 1<2<3<1, tie detection) in an MC-free helper.
- Harness scenario `duel_waiting` shows the waiting label; screenshot.
- Full suite and harness green.

---

### Task 21: Native logging and silent-failure surfacing

Closes §6.2 items on logging, silent DB/script failures, `ScriptProvider` cache, `TYPE_LINK` literal.

**Requirements** (C++ in `native/jni-bridge/`, rebuilt by `./gradlew copyNative`)
- Enable the log handler path: `OCG_LogHandler` forwards `type` and message to Java (`nSetLogHandler` un-commented and wired to a static Java method that logs at INFO/WARN/ERROR by type with the duel id).
- `CardDatabase::open` failure and unknown card codes log once per code; missing `constant.lua`/`utility.lua` or a missing `cXXXX.lua` logs a warning with the path list searched.
- `ScriptProvider` caches resolved script paths per name (a `std::unordered_map`), no other change.
- Replace the `TYPE_LINK` literal in `card_database.cpp` with a named constant; fix the lscale/rscale comment.

**Acceptance evidence**
- `./gradlew copyNative` builds both DLLs; `OcgCoreTest` still passes; a new `OcgCoreTest` case registers a card with an unknown code and asserts the Java log handler received a message (capture via the log handler callback).
- Full suite `BUILD SUCCESSFUL`.

# Player interaction and collection design

Date: 2026-09-15. Product behavior agreed in the design conversation; technical integration proposal for implementation planning.

Implementation update 2026-09-17: milestone 1 is complete in `codex/deck-editor-m1` through `2030dae`; see its [verification report](../reports/2026-09-17-deck-editor-m1.md). That implementation is the baseline for milestones 2–6. The [implementation contracts](2026-09-17-player-interaction-contracts.md) resolve their shared technical choices.

## Outcome

Players can manage their personal collection, build saved deck lists, and arrange duels through a hotkey home screen. A binder opens collection/deckbuilding directly; a placed duel mat opens invitations and duel preparation directly. Items provide physical entry points without becoming access requirements.

The accepted editor reference is the [fixed 1280x720 prototype](../references/2026-09-17-deck-editor-1280x720.html), copied unchanged into the repository for the implementation handoff on 2026-09-17.
The original remains at `C:/Users/haxer/.codex/visualizations/2026/09/16/01a0a7ac-e1fe-70d0-9df2-65139ed2a651/duelcraft-deck-720.html`.
The layout and behavior below are the implementation contract. The browser prototype remains a geometry and interaction reference; its light palette is superseded by the user's approved **dark styling matching the existing duel UI**, implemented in `2030dae`. Preserve that dark XML styling when adding collection features.

## Agreed product rules

- Use the term **collection**. Store it per player in the current world/server, with Ender Chest-like ownership and persistence.
- Support physical cards and deposits/withdrawals into storage without a slot or gameplay capacity limit.
- Saved decks are card lists. Saving a draft does not require owning its cards or satisfying duel legality.
- Multiple lists can reference the same owned copies. Saving or activating a list does not consume or reserve cards.
- Activation and duel preparation require enough copies and a valid deck under the supported rules.
- Withdrawing enough cards to invalidate the active deck clears that selection and explains why. Preserve the saved list.
- Prevent collection transfers and deck changes during a duel, in both the interface and server handlers.
- Support the hotkey, binder, and mat through the same server policies and player data.
- Determine final readability, density, and minimum comfortable window size through in-game playtesting.

## Technical baseline inspected

Source baseline: repository HEAD `a957157`; Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a. Inspection only; no new game/test run underlies this document.

| Existing source | Reuse or integration consequence |
| --- | --- |
| `client/DuelScreen.java`, `client/LDLibDuelScreen.java` | Existing 960x540 fixed canvas, centered scale transform, XML loading. New management screens need their own 1280x720 host; leave duel sizing and ESC behavior intact. |
| `assets/duelcraft/ui/duel_screen.xml` | LSS inside XML, grid rows and scrollers already used. Browser CSS cannot be pasted into LSS unchanged. |
| `client/carddata/CardDatabase.java` | Cached card lookup and a specialized announce-card name/passcode search. Collection search needs its own query semantics; preserve announce-card behavior. |
| `client/carddata/CardImageManager.java` | Full-card and cropped-art textures, asynchronous downloads. Request textures for mounted rows and the inspected card. It currently has a single texture-loaded callback; do not let a new screen overwrite another screen's callback. |
| `core/Deck.java`, `core/DeckLoader.java` | Engine input contains Main/Extra only. The YDK loader currently discards Side. Add a separate list model that retains all three sections. |
| `server/DuelManager.java` | Uploaded deck snapshots live in `playerCurrentDeck` and disappear on logout. `isBusy` includes first-turn rolls and live duels. Rolls already hold deck snapshots. |
| `server/DuelCommand.java` | Challenge/accept, expiry, RPS, first-player selection, and solo startup exist. Extract the preparation operations needed by UI instead of invoking command strings from buttons. |
| `server/ServerPayloadHandler.java` | Deck upload currently validates structure but lacks collection ownership and busy guards. This path must converge with the new policy. |
| `core/DeckValidator.java` | Checks sizes, positive passcodes, and copies across Main/Extra. It does not check known IDs, placement, aliases, or banlists. Do not present these checks as complete format legality. |
| `Duelcraft.java` | Example item/block registrations exist; actual cards, binders, mats, and persistent collection storage do not. |
| `client/uitest/`, `gradle/ldlib2-uitest.gradle` | Real-screen bounds/click scenarios, GUI scale selection, fixed window captures. Extend this harness. |

Paths in this table are relative to `src/main/java/com/haxerus/duelcraft/`, except assets under `src/main/resources/` and the explicit Gradle path. Also read `docs/multiplayer-decks.md`; its uploaded-deck description is newer than the filename-based description in AGENTS.md.

Confirmed against the pinned dependency source JARs: NeoForge attachments support serialization and `copyOnDeath`; LDLib2 has `VirtualScrollerView<T>` with `setItems`, `setItemUIProvider`, and mounted-item counts. No dependency upgrade is necessary for the proposed first milestone.

## Integration approach

Implement a client-only editor milestone first, then persist and synchronize the same deck-list model, add physical transfers, and connect invitations and entry points. This gives the Minecraft layout a playtest before the server work depends on it.

Alternatives considered:

1. **UI first, then complete the server-backed flow (recommended).** Validates scaling and LDLib behavior early. Sample data must stay explicitly separate from real player state.
2. **Persistence first.** Establishes ownership sooner, but delays the unresolved readability and interaction checks.
3. **All subsystems together.** Produces the whole journey at once, but makes UI, inventory, persistence, and duel-start failures harder to isolate.

## Data and authority

Keep `core.Deck` as the simulation input. Introduce an immutable `collection.DeckList` containing Main, Extra, and Side, plus a conversion to a defensive Main/Extra `Deck` snapshot at duel preparation. Store saved-list identity/name separately from card contents so rename does not change identity.

Use one server-persisted player attachment for collection counts, saved lists, and optional active-list ID. Opt into copying on death and verify respawn, End return, restart, and reconnect. Keep it private: send management data to the owning player only. Do not synchronize it to entity trackers or expose deck contents in lobby messages.

Confirmed ownership and card-identity rules (user clarification, 2026-09-17):

- Only deposited collection copies satisfy activation. Carried card items become usable through Deposit; silently counting inventory cards would make dropping/trading items invalidate decks outside collection operations.
- Key ownership by exact card passcode, using a nonnegative `long` count with checked arithmetic. This has no gameplay capacity limit, although machine representations remain finite.
- Include Main + Extra + Side when checking copies needed by a list, including for single duels. Single duels pass Main/Extra to the engine; saving Side does not introduce match mode or sideboarding.
- The card passcode is the primary key. Each passcode has separate ownership. Do not introduce alternate-art identities, ownership equivalence, or passcode substitution.

Additional implementation defaults:

- Saving edits to the active list rechecks eligibility; clear active status if the new contents are ineligible. Deleting the active list also clears it. Renaming preserves selection.
- Do not auto-reactivate after depositing cards. The player chooses the active list.

The ownership and identity rules above are user-confirmed. The edit/delete/reactivation behaviors are implementation defaults consistent with those rules.

## State transitions

| State | Collection/deck changes | How it ends |
| --- | --- | --- |
| Idle or outstanding invitation | Allowed; invalidate active selection as needed | Invitation acceptance validates both players again |
| Accepted preparation / first-turn selection | Locked for both players | Cancel/timeout/disconnect releases both; successful selection starts duel |
| Live duel | Locked for both players | Duel result, surrender, or disconnect cleanup |
| After duel | Allowed | Normal management flow |

Lock from acceptance through first-turn selection because the existing roll captures the decks at that point. Under server-thread serialization, validate both players, capture immutable lists, and enter the busy state as one operation. Retain the lock during the transition from roll to duel; avoid a separate asynchronous unlock/start interval. Before constructing the engine session, confirm the prepared state still belongs to those players and that required cards remain present.

Reject stale packets and legacy command mutations while busy. The client follows server state, closes management UI when preparation takes over, and offers return to the live duel instead of editing. A pending invitation alone does not lock either player.

## Persistence, sync, and transfers

The attachment is authoritative; the client holds a view and a local unsaved draft. Mutations identify a saved deck by stable ID, never a client-supplied player UUID. Derive the player from the network context.

Use bounded messages and revisioned responses. Page initial collection entries and saved-list summaries so an unconstrained collection is not forced into a single packet. Fetch deck contents on selection. Apply an initial snapshot only after its pages are complete; ignore late replies from a previous world or screen request. Return success only after the server commits a mutation. Keep unsaved edits on rejection and show the reason.

Register one physical card item with a passcode data component. Server transfer operations inspect actual inventory stacks; a client-reported quantity is only a request. Deposit removes exactly the quantity credited. Withdrawal verifies inventory capacity first and either completes the requested quantity or leaves both inventory and collection unchanged. Never drop overflow cards silently. Support selected amounts and Deposit carried cards with an explicit result count.

All transfer and list mutations run on the server thread. Test repeated/stale operations for conservation, full inventory, partial stacks, negative/overflow quantities, and active-deck invalidation. Save inventory and collection through the player's normal persistence lifecycle.

The user will handle card acquisition later; do not reopen it as a question or expand this integration to design it. Pack loot, trading screens, rarity/foil variants, and recipes are separate content work. Integration tests may grant physical cards through a restricted development command; ordinary deck selection never grants ownership.

## Deck validity and existing commands

Activation returns actionable missing-card counts and supported legality problems. Draft saving validates the storage/request shape, not playability. Keep decoder bounds separate from gameplay limits so an invalid imported draft can still be represented safely.

For the collection release, add authoritative known-card/type facts for activation and card-item validation. The milestone 2 plan chooses a small common/server catalog loaded once from the existing managed merged database, independent of client presentation classes. This adds a Java host-policy read of the managed database; it does not move engine callbacks or scripts out of C++. It avoids adding a JNI metadata API; the shared implementation contracts describe the chosen boundary.

The user confirmed the planned validation scope: preserve the current structural rule behavior, add Side size/copy checks, reject unknown cards/tokens and wrong Main/Extra placement before activation, and enforce collection ownership. Show the implemented validation scope honestly. Banlist selection and format-specific rules beyond existing support remain outside this integration; do not invent a default banlist.

Retain YDK import as a list operation, including Side; importing grants no cards. Route normal `/duel deck set`, challenge, accept, and solo-player preparation through the ownership policy. Server AI decks remain content, not a player collection. Remove the old upload bypass when the new path ships. If unrestricted engine testing remains necessary, give it an explicit operator/dev-only path rather than a normal player exception.

## Editor and navigation

The editor uses a 1280x720 design canvas, centered and uniformly scaled to the available GUI-space viewport. Preserve aspect ratio on non-16:9 windows. Do not inherit the duel screen's minimum scale blindly: a lower clamp can force a 1280-wide canvas outside a small GUI-space viewport.

Layout baseline:

- 224-unit inspector, flexible center deck pane, 348-unit collection pane; 12-unit column gaps.
- Fixed Save/header and bottom controls. Scroll card description, collection results, filters, and each deck grid within bounded panels.
- Main, Extra, and Side headers remain visible. Side expands by reducing Main's viewport, not by growing the screen.
- Standard deck grid: ten columns, 79-unit rows. Large: eight columns, 103-unit rows. These are prototype starting measurements, subject to LDLib/font playtesting.
- Collection: four columns. Virtualize rows; mount only visible rows plus overscan. Keep data/search state outside mounted widgets.
- Selected card stays selected through scrolling and filtering. Reflow does not discard the draft or silently reset independent scroll positions.
- Inspector has explicit Add/Remove and destination controls. Saving acknowledges success; leaving an unsaved draft offers Save/Discard/Cancel.
- Owned and in-list counts are distinct. A missing-copy indicator remains readable without relying only on color.

Search matches name, effect text, or exact passcode. Exact passcode/name matches rank first before the chosen stable sort. Filters cover ownership, Monster/Spell/Trap, summoning/subtypes, card properties, race, attribute, Level/Rank/Link rating, Pendulum scales, and ATK/DEF. OR within alternative subtype/attribute choices; AND across filter groups and explicitly required properties. Ignore inapplicable stats rather than treating Link arrows as DEF or unknown ATK as zero. Define "Missing" as a shortage in the current draft and "Extras" as owned copies beyond that draft's requirement; do not imply copies are reserved by other saved lists.

Use readable labels/tooltips, keyboard focus, and remappable input. Hotkey input must not fire while typing in another screen. The home screen needs two clear routes and active-deck status, without duplicating the editor or introducing crafting currencies. Both mat and home use the same invitations/preparation screen. Initially this is a private two-player lobby with invites, RPS, first-player choice, and existing timeout behavior; public matchmaking, spectators, and best-of-three are outside this integration.

## Milestones and verification

See [integration roadmap](../plans/2026-09-15-player-interaction-roadmap.md) and [first editor milestone plan](../plans/2026-09-15-deck-editor-milestone-1.md).

Acceptance at release: two players on a dedicated server can deposit real cards, save missing-card drafts, activate eligible lists, invalidate selection by withdrawal, invite and complete a duel through either entry path, and retain their private collections after reconnect/restart. Attempts through stale screens, legacy uploads, or commands cannot alter preparation/live-duel cards. Each stage also retains the existing duel UI and engine regression suite.

No implementation or new runtime validation is claimed by this design document.

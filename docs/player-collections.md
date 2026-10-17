# Player collections and saved deck lists

Your collection belongs to your player on the current world/server, like an Ender Chest. The server stores deposited card counts, saved lists and one active-list choice. Reconnecting, restarting and player death preserve that data. A different server has its own collection. Unsaved editor drafts live in the current client connection.

## Open and edit

Press **J** while in the world with no screen open to open Home. Rebind **Open Duelcraft** in Minecraft Controls under **Duelcraft**. Choose **Collection & Decks** to build decks or Duel to open the private lobby. A Card Binder opens Collection; a placed Duel Mat opens the lobby with either an empty hand or a held item. The server redirects these routes to accepted preparation or live play when needed. The binder and mat store no personal collection data. `/duel collection` and `/duel lobby` remain available.

In the collection editor:

- Left-click a browser card to inspect it. Right-click or double-left-click a browser card to add a copy. You can also use the inspector's add/remove buttons.
- Choose **Deck** or **Side** before adding. Deck routes Fusion, Synchro, Xyz and Link monsters to Extra; other cards go to Main. Right-click a card already in a deck section to remove that occurrence.
- Search and filters narrow the browser. Standard/Large changes card density. **Sort deck** orders each section by Normal Monsters, other monsters, Spells and Traps, keeping identical passcodes together. Save to keep that order.
- Use **My decks** to Open, Make a copy or Delete a saved list. **New deck** starts a draft. **Clear active** appears beside the active-list status in the editor header. Deleting the open saved list starts a blank draft; deleting another saved list keeps your current draft. Deposited cards stay unchanged in both cases.

Saving keeps a list; **Use for duels** activates the saved list. You can save a well-formed incomplete, unowned or unplayable draft. Save freezes edits while waiting for the server and clears the unsaved marker only after an acknowledgement matching that submitted list. A failure preserves the draft. Save changes before activating; the server checks the acknowledged saved version. Several lists can reuse the same deposited copies without reservation or consumption.

Open, New deck, Import YDK, Close and navigation away from a changed draft ask **Save / Discard / Keep editing**. Save waits for acknowledgement before continuing; Keep editing cancels navigation. Esc closes the topmost menu or dialog first. Accepted preparation suspends the editor in the same connection. Cancellation or startup failure allows it to resume, and closing an ended duel's result restores it. Disconnect clears suspended private drafts; save before leaving a server to retain a list.

The editor marks missing copies across Main, Extra and Side. **Ownership optional** reports them as neutral collection information. **Ownership required** makes those shortages block activation and use. Details distinguish missing deposited copies, supported legality problems and additional server restrictions. Saving or depositing cards does not auto-activate a list.

## Deposit and withdraw physical cards

`duelcraft:card` stacks carry a positive exact passcode in `duelcraft:card_code`, up to 64 copies per stack. Select a card and enter **1–4096** copies for Deposit or Withdraw. **Deposit carried cards** transfers the supported cards it finds and reports moved and skipped copies.

Deposits scan main inventory slots, including the hotbar, and the offhand. They exclude armor, the cursor, containers and the Ender Chest. The server accepts only canonical card stacks whose item and components match the built-in passcode stack, within its stack limit, and whose passcode identifies a known playable non-token card. Customized, malformed, unknown or otherwise unsupported stacks stay intact. Bulk deposit skips those stacks; its skipped number counts copies, not stacks. If no supported cards can move, the server rejects the request without changing the collection revision.

Withdrawals require actual deposited copies under either ownership setting. They fill compatible card stacks, then empty main inventory slots; they do not fill the offhand or drop overflow. A selected-amount transfer succeeds in full or leaves inventory and collection unchanged. Bulk deposit commits its supported subset together. A passcode already stored in the collection remains withdrawable if later card data no longer recognizes it, so a catalog change does not trap the physical copies.

A withdrawal that makes the active list ineligible clears activation and keeps the list. A shortage alone does this only when ownership is required; a companion restriction can do it in either mode. Deposits and withdrawals evaluate the proposed counts before commit. A failed policy evaluation rejects the transfer without changing inventory or collection. Minecraft saves the inventory and attachment through its normal lifecycle; these checks do not promise crash-atomic disk persistence.

Each collection mutation includes the expected revision. The server rejects stale requests; the client refreshes so you can retry against current state. Accepted RPS, first-turn choice, startup and live duels block card transfers and list changes. Pending invitations allow editing until acceptance. These rules also apply to commands and packets; reopening a screen grants no bypass.

## Local YDK import and export

Use `<client gameDir>/duelcraft/decks/` for local `.ydk` files. In Prism this is the instance's Minecraft folder. **Import / export → Import YDK** reads Main, Extra and Side into a new unsaved draft. Save that draft, then Activate when ready. Import grants no physical or deposited copies. Files from older versions stay available to import; the upgrade gives no ownership credit and selects no list automatically.

**Export YDK** writes the captured current draft, including unsaved edits, with Main/Extra/Side order preserved. Choose a single `.ydk` filename in that same local folder. Confirm Replace to overwrite an existing regular file; paths, directories and symbolic-link targets are rejected. Export keeps the server's save and activation state as it was. Later changes to the draft require another export.

`/duel deck list` lists local files. `/duel deck set <name>` omits `.ydk` and quotes spaces, for example `/duel deck set "Blue Eyes"`. That command saves a new list, waits for its acknowledgement, then attempts activation. A denied activation leaves the imported list saved and inactive. The editor import loads a draft and leaves Save and Activate to you. Editing a local file does not change a saved server list. See [the multiplayer guide](multiplayer-decks.md#local-ydk-files-and-retained-commands) for the retained command table and server-side AI files.

## Server ownership setting

Duelcraft's standalone default is:

```toml
[ownership]
requireCardOwnership = false
```

The server generates `<gameDir>/config/duelcraft-server.toml` with pinned NeoForge 21.1.224. An existing `<world>/serverconfig/duelcraft-server.toml` overrides it; for an integrated world this is under `<gameDir>/saves/<world>/serverconfig/`. Edit the effective file with the server stopped. Set `requireCardOwnership = true` to require deposited copies, then restart the dedicated server or reopen the integrated world. The running server captures the setting for its lifecycle; a config reload does not change it. Client preferences cannot override it. The [implementation contracts](superpowers/specs/2026-09-17-player-interaction-contracts.md) record the pinned config-loader behavior.

With ownership disabled, you can activate and use a supported legal list without deposits. With ownership enabled, the server counts exact passcodes across **Main + Extra + Side**, including single duels, and requires enough deposited copies. Carried cards count after deposit. Duel use consumes no copies, and allowing a list never creates physical cards or collection counts.

The server revalidates activation on login, collection Open and later use. An ordinary denial clears activation with an explanation and one revision increment while preserving counts and lists. Removing a restriction does not reactivate a cleared list. An unavailable or throwing policy check denies use and preserves the recoverable attachment.

Activation/use enforce Main 40–60, Extra/Side at most 15 each, three copies per exact passcode across all sections, known playable cards and Main/Extra type placement. Side remains saved and checked but does not enter the single-duel simulation. Built-in banlists, alias grouping, rule-specific forbidden types, match play and sideboarding are outside the current checks.

## Companion restrictions

Companion mods can add denials through the synchronous [`DeckUseCheckEvent`](../src/main/java/com/haxerus/duelcraft/api/DeckUseCheckEvent.java) on `NeoForge.EVENT_BUS`. Its immutable context contains the authenticated owner, candidate list, deposited counts and selected rule. Listeners call `deny(reason)`; the first denial remains. Without a listener, Duelcraft applies its core policy. A listener cannot override core legality, enabled ownership, authentication or busy locks. Restrictions apply under both ownership settings.

Checks must have no inventory, collection, reservation or network side effects. The shared policy evaluates activation, active-list edits/transfers, revalidation, invitations/acceptance, final startup and human solo use. An ordinary denial after a valid edit/transfer preserves that successful change and list while clearing activation. A throwing listener fails closed: the server logs the cause, gives the owner a generic permission-check failure and rejects the pending mutation without changing inventory or collection. Revalidation failures retain the attachment for recovery. Detailed denial information stays private to the affected owner.

Packs, recipes, rewards, economy and acquisition/progression content belong to companion work. For admin or transfer testing, `/duel card give <player> <passcode> <count>` requires permission level 2 and grants 1–4096 supported physical cards if the idle target has main-inventory capacity. Deposit those cards to satisfy ownership. The grant command does not credit the collection or bypass deck restrictions.

Clients and server must run matching builds using **protocol 8**. See [the current handoff](superpowers/handoffs/2026-09-17-deck-editor-implementation.md) for verification evidence. Installed-build multiplayer acceptance remains deferred until friend testing; the full visual UI pass is separate work.

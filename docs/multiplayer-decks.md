# Deck selection in multiplayer

Install the same Duelcraft build on the server and every client. The current network protocol is **7**; older clients cannot negotiate it. Player selection is a saved list attached to the authenticated server player.

1. Put `.ydk` files in your Minecraft instance's `duelcraft/decks/` directory. For Prism, use the instance's Minecraft folder.
2. Run `/duel deck list`, then `/duel deck set <name>` without `.ydk`. Quote names containing spaces, for example `/duel deck set "Blue Eyes"`.
3. Wait for `Imported and activated local deck '…'.` If activation is denied, the imported list remains saved and inactive, with the server's reason.
4. Run `/duel challenge <player> [rule] [seed] [lp] [hand] [draw]`. The target runs `/duel accept`; both answer `/duel hand rock|paper|scissors`. Ties replay. The winner chooses `/duel first yes|no`.

`set` imports Main, Extra and Side through the ordinary Save request, waits for its acknowledgement, then requests Activate at the acknowledged revision. It grants no deposited copies. Local filenames never become server paths; players can import different contents with the same name. Editing a local file has no effect until another import. Explicitly named AI decks for `/duel test <aiDeck>` are still read from the server's `duelcraft/decks/` directory. Plain `/duel test` uses the human's current eligible list for both sides.

`/duel deck get` reports the current active saved list. `/duel deck clear` clears only activation. Saved lists, deposited counts and active selection persist through reconnects and server restarts. Login revalidates activation against current card data, ownership and companion restrictions. A now-ineligible activation is cleared with a private explanation and one revision increment; its saved list and counts remain. Removing a restriction does not reactivate a cleared list. An unavailable or throwing permission check denies use while retaining the recoverable attachment.

The SERVER setting `[ownership] requireCardOwnership` in `duelcraft-server.toml` defaults to **false**, allowing supported legal lists with an empty collection. It is captured for the server lifecycle; restart the server/world after changing it. With `true`, every exact passcode across Main, Extra and Side needs enough deposited copies. Inventory cards and YDK files do not count as deposits. Copies are checked, not consumed, when a duel starts.

Companion mods can deny use through `DeckUseCheckEvent` under either ownership setting. Approval cannot override supported legality, required deposited copies, authenticated ownership or busy locks. A throwing listener fails closed with a generic permission-check error. Eligibility is checked at invitation/acceptance and immediately before native startup, so a progression change without a card edit can prevent starting.

Supported legality includes Main 40–60, Extra/Side at most 15 each, at most three copies per exact passcode across all sections, known playable cards and Main/Extra type placement. Banlists, alias grouping, rule-specific forbidden types, match play and sideboarding are not implemented. Side remains saved and participates in checks but is not inserted into a single duel's simulation deck. The server downloads its database and scripts into its [managed cache](card-data.md).

Invitations remain editable until acceptance and expire after 60 seconds. Accepted rock-paper-scissors, first-turn choice, startup and live duels lock collection transfers and deck changes in both commands and payload handlers. `/duel invite decline` declines an invitation; `/duel invite cancel` cancels the current preparation. `/duel forfeit` cancels preparation or concedes a live duel. Cancel, timeout, failure, end and disconnect release the relevant locks. Failed eligibility at acceptance leaves the editable invitation until cancelled or expired.

An unsaved collection editor is suspended during preparation/start in the same connection. Cancellation or startup failure restores it; closing an ended duel's result restores it. Disconnect clears suspended private state. Startup sends the Start payload before setup refreshes; a failure then sends terminal `START_FAILED`, clears the transient duel display and releases locks without a victory result.

The command flow works over the ordinary Minecraft connection on integrated, LAN and dedicated servers. Public preparation screens are a later milestone.

## Regression checks

`DuelPreparationServiceTest`, `DuelStartPolicyTest`, `PreparationPayloadTest`, `DuelCommandPolicyTest` and `DuelDeckImportPolicyTest` and `DeckImportServiceTest` cover policy, request ordering, private views and imports. The DEV scenario `duel_deck_import` exercises local registered commands and actual Save/Activate payloads. `collection_duel_policy` exercises native solo startup, live BUSY replies, post-setup failure cleanup and dirty-editor restoration. Dedicated `duel_preparation_commands`, `collection_transfer_isolation` and `collection_duel_policy_mp` cover real command/network/native paths with separate players. The latter disconnects a client and must run alone. `-PcollectionM4Lifecycle=default|ownership|throwing|restricted|removed` runs ordered, separate JVM phases in its own retained world for login-time policy changes; archive evidence and stop each JVM before proceeding.

For a new M4 retained run, start with the `default` phase and no saved M4 world or SERVER config. After it passes and its game JVM stops, change `requireCardOwnership` to `true` in `run-collection-m4-lifecycle/config/duelcraft-server.toml`, then run `ownership`, `throwing`, `restricted` and `removed` in that order. Keep its phase manifests and archives; the launcher rejects reseeding or skipping a phase.

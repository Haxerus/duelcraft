# Deck selection and multiplayer duels

Install the same Duelcraft build on the server and every client. The current network protocol is **8**; older clients cannot negotiate it. Collections, saved lists and activation belong to your player on that world/server. See [player collections](player-collections.md) for editing, physical cards and ownership settings.

## Start from the screens

1. Press **J** in the world with no other screen open. Rebind **Open Duelcraft** under **Duelcraft** in Minecraft Controls if needed. Home shows your active saved list and opens **Collection & Decks** or **Duel**.
2. Open Collection, build or import a list, then **Save**. Wait for the server acknowledgement before **Use for duels**. Saving an incomplete or unowned list is allowed; activation checks whether you can use it.
3. Open Duel from Home, use `/duel lobby`, or use a placed Duel Mat. Select an online player, review Settings and send an invitation. Defaults are MR5, 8000 LP, hand 5 and draw 1. An empty seed field asks the server to generate a seed.
4. The recipient opens the invitation, selects an eligible active list if needed, and accepts. Both players choose rock, paper or scissors in the lobby. Ties start another round. The winner chooses first or second, then the duel opens.

Using a Card Binder requests Collection. Using a Duel Mat, with an empty hand or an item, requests the lobby; the mat holds no cards or player data. Home, binder and mat routes return you to the lobby during accepted preparation, or to the live duel during play. They grant the same permissions. Actual client item/offhand dispatch and OS key rebinding still need installed-build acceptance; see [the M5 playtest guide](qa-m5.md).

Invitations are private and allow deck editing until acceptance. Each player can participate in one invitation at a time. The challenger needs an eligible active list to invite; the recipient can choose one before accepting. Accept rechecks both lists and captures their contents. Preparation and collection messages keep deck contents, counts and denial details private to their owner; the opponent receives readiness information.

Invitations, each RPS round and first-turn choice expire after 60 seconds. Accepted preparation, startup and live duels lock deck changes and card transfers on the server. Cancel, expiry, startup failure, duel end and disconnect release the relevant locks. Failed eligibility at acceptance leaves the invitation editable until cancellation or expiry. A preparation transition suspends an open editor in the same connection. After cancellation or failure you can resume it; closing the ended duel's result also restores it. Disconnect clears suspended drafts.

The server rechecks the selected list, owner, online state and policy before native startup. It sends Start before setup refresh messages. A startup failure releases locks and clears the transient duel screen through `START_FAILED`, without showing a victory result. Challenger and accepter keep shuffle seeds `seed` and `seed + 1` regardless of who goes first.

## Local YDK files and retained commands

Put `.ydk` files in `<client gameDir>/duelcraft/decks/`. For Prism, use the instance's Minecraft folder. Existing local files from older versions remain available to import; the upgrade grants no collection copies and does not import or activate them automatically.

The editor's **Import / export → Import YDK** loads Main, Extra and Side as an unsaved draft. Save and Activate are separate steps. **Export YDK** writes the captured current draft to that local folder, including unsaved edits and all three sections. It asks before replacing an existing file. Export does not save or activate the server list. See [the collection guide](player-collections.md#local-ydk-import-and-export) for details.

| Command | Current behavior |
| --- | --- |
| `/duel collection` | Opens the collection editor while management is available. |
| `/duel lobby` | Requests the lobby through the state-aware route. |
| `/duel show` | Reopens the current duel display. |
| `/duel deck list` | Lists client-local YDK filenames. |
| `/duel deck set <name>` | Imports a local YDK through acknowledged Save, then requests Activate at the new revision. Omit `.ydk`; quote spaces, for example `"Blue Eyes"`. |
| `/duel deck get` | Reports the authoritative active saved list after revalidation. |
| `/duel deck clear` | Clears activation through server checks; keeps saved lists and cards. |
| `/duel challenge <player> [rule] [seed] [lp] [hand] [draw]` | Sends an invitation using the same preparation service as the lobby. |
| `/duel accept` | Accepts the current invitation after checking both lists. |
| `/duel hand rock\|paper\|scissors` | Answers the current RPS round. |
| `/duel first yes\|no` | Chooses first or second after winning RPS. |
| `/duel invite decline` | Declines an incoming invitation. |
| `/duel invite cancel` | Cancels your invitation or accepted preparation. |
| `/duel forfeit` | Cancels preparation or concedes a live duel. |
| `/duel test [aiDeck] [rule] [seed] [lp] [hand] [draw]` | Starts a solo duel through the same player policy. A named AI deck comes from the server's YDK directory; plain `/duel test` uses the human's eligible list for both sides. |
| `/duel card give <player> <passcode> <count>` | Permission-level-2 test/admin grant of 1–4096 physical cards. Requires a known supported passcode, room in the target's main inventory and an idle target. Grants no deposited copies. |

Command import creates a new saved list and waits for matching Save and Activate acknowledgements. A denied activation leaves the imported list saved and inactive, with the server's reason. Local filenames never become server paths; players may import different contents under the same name. Editing a YDK has no effect on an existing saved list until another import. Explicitly named AI decks remain server content in `<server gameDir>/duelcraft/decks/` and require no player collection.

## Server policy and limits

The SERVER ownership setting defaults to **false**. With it off, an otherwise eligible list can activate and play with an empty collection. With it on, deposited exact-passcode counts must cover **Main + Extra + Side**, even for a single duel. Inventory cards must be deposited first. Duel use checks copies without consuming or reserving them. Follow [the configuration instructions](player-collections.md#server-ownership-setting) to change the setting and restart.

Activation and duel use require Main 40–60, Extra and Side at most 15 each, at most three copies of each exact passcode across the sections, known playable cards and correct Main/Extra placement. The server also enforces optional companion denials in either ownership mode. Side remains stored and checked; the host sends only Main and Extra to a single duel. Built-in banlists, alias grouping, rule-specific forbidden types, match play and sideboarding are outside the current checks. Packs, recipes, rewards and acquisition economy are separate companion work.

Saved lists, deposited counts and activation persist through reconnect and restart on the same world/server. Login and later use revalidate activation. An ordinary denial clears only activation and explains the reason; it preserves the list and counts. Removing a restriction does not reactivate the list. A failed policy evaluation denies use while preserving the attachment. See [the companion-hook contract](player-collections.md#companion-restrictions).

## Regression evidence

`DuelPreparationServiceTest`, `DuelStartPolicyTest`, `PreparationPayloadTest`, `DuelCommandPolicyTest`, `DuelDeckImportPolicyTest` and `DeckImportServiceTest` cover preparation, policy and imports. DEV scenario `duel_deck_import` exercises registered commands and Save/Activate payloads. `collection_duel_policy` covers native solo startup, live BUSY replies, post-setup failure cleanup and draft restoration. Dedicated scenarios `duel_preparation_commands`, `collection_transfer_isolation`, `duel_preparation_notifications` and `duelcraft_journey` cover separate-player paths. `collection_duel_policy_mp` disconnects a client and runs alone.

The retained `-PcollectionM4Lifecycle=default|ownership|throwing|restricted|removed` sequence uses separate JVMs and its own retained world. Begin `default` with no saved M4 world or SERVER config. After that JVM stops, set `requireCardOwnership = true` in `run-collection-m4-lifecycle/config/duelcraft-server.toml`, then run the remaining phases in order, stopping each JVM and preserving its manifests and archives. The launcher rejects reseeding or skipped phases.

Automated fixtures do not close installed-JAR multiplayer acceptance. Friend testing remains deferred, and the broader visual UI pass is separate from this documentation audit. The [implementation handoff](superpowers/handoffs/2026-09-17-deck-editor-implementation.md) records current evidence and remaining acceptance.

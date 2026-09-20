# Duelcraft M4 QA.2 friend playtest

Use **1.0.0-m4-qa.2**, protocol **7**, on each client and the host. This checkpoint includes the M4 playtest fixes for public card visibility, reveal prompts, duel panels, preparation messages and collection editing. `BUILD-INFO.txt` identifies the exact source commit and build state; `SHA256SUMS.txt` identifies the distributed files.

## Install

Use **Windows x64**, **Java 21**, **Minecraft 1.21.1**, **NeoForge 21.1.224** and the Microsoft Visual C++ 2015–2022 x64 runtime.

1. Create a separate launcher instance and test world.
2. Copy both ZIP entries into its `mods` folder: `duelcraft-1.0.0-m4-qa.2.jar` and `ldlib2-neoforge-1.21.1-2.2.39.a-all.jar`. Remove older copies of those two mods from the test instance.
3. Install the same NeoForge version and both JARs on a dedicated server. LAN players also need both mods.
4. Allow the card database and script downloads to finish on first launch. The mod includes the Windows DLLs and SQLite; leave the JAR intact. You do not need EDOPro.

Keep your existing worlds and QA.1 installation intact. All participants should use this exact QA.2 package.

## Start a duel

Open `/duel collection`, import or edit a list, click **Save**, then **Use for duels**. Save alone does not activate a list. You can also place a `.ydk` in `duelcraft/decks` under the client instance and run `/duel deck set "Deck name"` without its extension. That compatibility command imports, saves and attempts activation; it grants no cards.

One player runs `/duel challenge OtherPlayer`; the other runs `/duel accept`. Each chooses `/duel hand rock`, `/duel hand paper` or `/duel hand scissors`. Repeat after a tie. The winner chooses `/duel first yes` or `/duel first no`. Use `/duel show` to reopen the duel, `/duel forfeit` to concede, and `/duel invite cancel` to cancel preparation. The recipient can decline an unaccepted invitation with `/duel invite decline`. `/duel test` starts solo play.

The server defaults to `requireCardOwnership=false`. Supported legality and companion restrictions still apply. To test ownership, stop the server/world, set `[ownership] requireCardOwnership=true` in its `config/duelcraft-server.toml`, then restart. A world `serverconfig/duelcraft-server.toml` override takes precedence. Ownership counts deposited exact-passcode copies across Main, Extra and Side; carried inventory does not count. Depositing cards does not activate a deck. Operators can grant test items with `/duel card give <player> <passcode> <count>`.

## Playtest checklist

1. Open the editor with an active deck and check its name, cards and Active badge. Close a fresh empty draft. Empty a saved deck and check that closing still asks about unsaved changes.
2. Add three copies across Main/Extra/Side where applicable, then try a fourth through the button and double click. Import a four-copy list: the draft should remain editable and removable, while activation rejects it. Check natural Extra Deck routing and right-click the last duplicate in an interleaved list.
3. Open each dropdown, submenu and dialog; Escape should close only the top layer. Use visible Close, choose Keep editing, then Save and close. Repeat while a save is pending.
4. Exercise New, Save, Make a copy, Rename, Import and Delete in My decks. Export Main/Extra/Side, reimport, and compare ordered sections and counts. Test overwrite cancellation and confirmation. Export must leave server lists and deposited quantities unchanged.
5. Exercise Painful Choice, Dragunity Knight - Trident (80159717) and Ceremonial Bell from both seats. For the Extra Deck check, control face-up Trident and send it as its ignition cost; the opponent Extra Deck should contain Gaia Saber (67598234), LANphorhynchus (77637979), then another Gaia Saber. Choose LANphorhynchus from the revealed choice; it should reach the Graveyard and the remaining Extra Deck should return to backs after shuffling. Check names/art in the choice, no selection reveal in the zone inspector, and the correct selection when reveal order differs. After revocation or an identity shuffle, check both clients for unauthorized names/art.
6. Open zone inspector and log together, hover a card for left details, scroll both right panels, then hide and reopen each. Repeat at GUI scales 2 and 3.
7. Try repeated/crossed invites, accept, then submit one RPS hand before the other. Expect one prompt per player per round and one new pair after a tie. Cancel or disconnect and start another flow; check for stale prompts or locks.
8. Banish face-down, inspect from both seats, then change turn. Check the Face-down marker without unauthorized artwork. Open an Xyz host's Materials action, detach a material and move the host. The list should update or close without sending a gameplay choice.
9. Rejoin and restart under both ownership modes. Missing required copies should clear activation and preserve the list; sufficient deposited copies should preserve activation. Finish a duel, cancel preparation, and transfer cards afterward to check lock release. Check dirty-editor restoration after cancellation and disposal after disconnect.

## Limits and reporting

This remains the command-based M4 checkpoint. Home, binder, mat and lobby navigation belong to M5. Supported deck checks cover sizes, known/playable card types, placement and three copies per exact passcode; they do not implement banlists, alias-group limits, full format rules or match sideboarding. Acquisition/progression remains companion work. Native deployment supports Windows x64 only; the Ubuntu CI native path remains unresolved.

The filesystem test machine cannot create Windows symlinks without additional privileges, so that one export test is a documented skip. The exporter rejects links in source and refuses an unsafe fallback when atomic overwrite is unsupported; the latter filesystem condition was not exercised. Export work already submitted to the disk worker can finish after closing the editor, while stale feedback is discarded.

Report the build, ownership setting, host type, actions, expected/actual result and screenshots. Include both clients' and the server's `logs/latest.log`, any crash report, installed mod list and whether the issue repeats in a separate instance. See the [verification report](superpowers/reports/2026-09-19-m4-qa-follow-up-verification.md) for measured automated and packaged acceptance results.

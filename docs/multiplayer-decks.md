# Deck selection in multiplayer

Each player puts `.ydk` files in their own Minecraft instance's `duelcraft/decks/` directory. For Prism, use the instance's Minecraft folder, not the launcher's installation directory.

1. Install the same updated Duelcraft JAR on the host/server and every client. This build uses network protocol version 2 and is incompatible with the earlier filename-only build.
2. Join the world/server and run `/duel deck list` to list your local files.
3. Run `/duel deck set <name>` without the `.ydk` extension. Quote names containing spaces, for example `/duel deck set "Blue Eyes"`. Tab completion lists local deck names.
4. Wait for `Current deck set to '…'.` from the server, then challenge another player as usual.

`/duel deck set` reads the local file and sends its main/extra card IDs to the server. The server validates the upload and stores an immutable snapshot for the sending player. Players can use the same filename with different contents. A rejected selection leaves the previous selection intact. No deck file is copied to or opened on the server for player selection.

`/duel deck get` reports the server's accepted selection; `/duel deck clear` removes it. Run `set` again after changing the file or reconnecting. Selections are cleared on disconnect/server shutdown. Existing duels and first-turn rolls already hold their own deck snapshots.

This works through the normal Minecraft connection, whether singleplayer, LAN, a tunnel, or a dedicated server. The tunnel does not grant access to anyone's filesystem.

`/duel test` uses your uploaded deck for both you and the AI by default. An explicit `/duel test <aiDeck>` still loads the named AI deck from the **server's** `duelcraft/decks/` directory.

The server automatically downloads its card database and Lua scripts into its [managed cache](card-data.md). Deck selection uploads only card IDs and a display name. Existing legality checks cover positive passcodes, 40–60 main cards, at most 15 extra cards, and at most three copies per passcode across both sections. Unknown positive IDs, card types, aliases and banlists are not checked by the current validator. Side decks remain ignored.

## Regression checks

`DuelDeckUploadTest` loads different same-named files from two independent client directories, round-trips each upload, and verifies per-player server resolution without a server deck registry. It also covers invalid uploads, immutable snapshots, clearing, replacement, and bounded decoding.

`./gradlew runClient -PldTest=duel_deck_upload -x copyNative` exercises the registered local commands, completion of spaced filenames, the real upload packet, server-side snapshot resolution after deleting the fixture file, and the server clear command. This integrated-server check does not replace a multi-machine LAN/tunnel play-test.

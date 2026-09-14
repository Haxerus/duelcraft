# Automatic card data

Duelcraft downloads the complete [CardScripts](https://github.com/ProjectIgnis/CardScripts) and [BabelCDB](https://github.com/ProjectIgnis/BabelCDB) collections during mod startup. This runs on clients and dedicated servers; an integrated server shares its client's prepared snapshot. No EDOPro installation or database/script path configuration is required.

The first launch needs internet access and may take longer while the archives download and extract. Later launches check the upstream commits and reuse the cache if unchanged. If an update fails, Duelcraft logs the failure and uses the last valid snapshot. Without a valid cache, card data initialization fails with the cache location and download error in the log. Restore connectivity and restart Minecraft/the server to retry; the startup result is shared for the lifetime of that process.

## Contents and location

Everything lives under the Minecraft instance's `duelcraft/cache/card-data/` directory. In Prism, open the instance's Minecraft folder. A dedicated server uses its working directory.

Each `snapshot-…` directory contains:

- `scripts/`: root Lua libraries plus official, unofficial, pre-release, pre-errata, GOAT, skill, and Rush scripts.
- `databases/`: every root `.cdb` from BabelCDB, including supplemental databases added upstream in the future.
- `cards.cdb`: the combined database used by both the native engine and client display/search.
- `revisions`: the CardScripts and BabelCDB commit IDs, in that order.
- `script-count`: the expected number of extracted Lua files.

The `current` file names the active snapshot. Downloaded files are staged and validated before this pointer changes. Older snapshots stay intact to avoid changing files used by another running instance. You may delete old snapshots while all instances using that folder are closed; keep the one named in `current`. Deleting the entire `card-data` folder forces a fresh download next launch.

The old `dbPaths`, `scriptPaths`, and `cardDatabaseUrl` settings are no longer used. The old client-only `cache/cards.cdb` is also unused and can be removed. Card images and system strings retain their existing cache locations and URL settings.

## Card variants and updates

Official database entries take priority on duplicate passcodes. Supplemental databases are merged in filename order, with the first occurrence winning. Script lookup checks the root libraries, then `official`, then remaining directories in path order.

Use the upstream variant's passcode in the deck for an unofficial, GOAT, or pre-errata card. Including its data does not change an ordinary card's effects, add banlist enforcement, or implement a new duel mode. Rush and skill data being available does not imply full support for their game rules. Artwork availability still depends on the configured image provider.

Updates apply on the next process launch, never during a duel. The server's snapshot drives gameplay; clients use their own snapshot for names, descriptions, and search. Restart the server and clients with internet access when updating together. Commit IDs are recorded in the startup log for troubleshooting. Newly published scripts may depend on newer engine behavior, so test new card interactions with the bundled core.

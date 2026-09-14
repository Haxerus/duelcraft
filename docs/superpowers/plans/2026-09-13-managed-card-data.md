# Managed card data implementation plan

**Goal:** Download full Project Ignis scripts and databases at runtime, with no local EDOPro path configuration.

**Architecture:** One common startup future prepares an immutable cache snapshot shared by client display and server engine. Resolve upstream commits, download archives only when revisions change, merge all root BabelCDB databases, and publish a pointer only after validation. A failed update uses the last valid snapshot. Previously published snapshots remain intact because another instance may still be reading them.

**Tech stack:** Java 21 HttpClient, ZIP, existing sqlite-jdbc, JUnit 5, NeoForge lifecycle events.

**Constraints:** Work in `codex/ui-design-pass`; preserve existing UI/deck changes. No native ABI changes. Keep image and strings settings. Include official, unofficial, prerelease, GOAT, pre-errata, skill, and Rush data without claiming new duel-mode support.

## 1. Cache and merge

- [x] Add `core/data/CardDataCacheTest.java` with local HTTP fixtures and SQLite databases. Verify full collection coverage, base scripts, official precedence, unchanged revision reuse, successful updates, offline fallback, first-run failure, malformed archives/databases, and traversal rejection.
- [x] Run the tests before implementation and confirm the missing feature fails.
- [x] Add `CardDatabaseMerger`: validate `datas`/`texts`, preserve all columns, copy official `cards.cdb` first, then insert missing supplementary IDs in filename order. Validate SQLite integrity and matching card IDs.
- [x] Add `CardDataCache`: resolve GitHub master commits, download pinned archives into a temporary directory, extract only Lua/CDB and attribution files with path/size checks, discover script directories with root and official first, validate and publish `current` atomically. Return `Snapshot(database, scriptPaths)` from `prepare(Path cacheDir)`.
- [x] Run focused tests and resolve failures.

## 2. Runtime integration

- [x] Add `CardData`: one synchronized future using `<gameDir>/duelcraft/cache/card-data`; start during common setup, await before constructing the server engine, and share the result with client initialization.
- [x] Replace client-only `CardDatabaseDownloader`; remove `dbPaths`, `scriptPaths`, and `cardDatabaseUrl` settings.
- [x] Ensure failed server initialization does not publish a partially initialized DuelManager.
- [x] Update current setup documentation and the data/scripts wiki with cache layout, refresh/fallback, precedence, and version behavior.

## 3. Verification

- [x] Run all JUnit tests and assemble the JAR.
- [x] Exercise real upstream archives through the production cache and native engine; verify official/unofficial/GOAT lookup and Lua loading.
- [x] Run a real client startup scenario to check lifecycle wiring and caching. Inspect logs for data/native failures.
- [x] Review the diff, fix actionable findings, and report results with the JAR path.

## Verification results

- 587 JUnit tests passed, zero failures/errors/skips, including 13 local-HTTP cache regressions. Empty supplements and truncated non-base Lua scripts were reproduced as failing regressions before their fixes.
- Fresh real client startup downloaded 13 CDBs and 22,738 Lua files; the merged database contained 24,779 cards. The integrated-server deck-upload scenario passed 7/7 checks.
- A second client launch reused the same snapshot without archive downloads and passed the same 7 checks.
- A native smoke probe loaded base libraries and Raigeki, Chaos End Ruler (Anime), Skull Dice (GOAT), and Firewall Dragon (Pre-Errata), with no unexpected native diagnostics. The probe accounts for the core moving the Link monster to the Extra Deck.
- Verified upstream revisions: CardScripts `9b4c0ed72e2ed62508f133f5e11dd914e0e3e710`, BabelCDB `a45c2a2666d0727ef1081a0dedae46db9525a973`.
- Review addressed empty supplements, truncated cached scripts, bounded extraction, and cleanup failures masking fallback. Failed first-run initialization deliberately requires a process restart after restoring connectivity, keeping client/server startup results consistent.

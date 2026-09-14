# Client-owned deck selection

Goal: `/duel deck list` and `/duel deck set <name>` read each player's own Minecraft directory, including LAN/tunnel and dedicated-server connections.

Design: client commands parse the local YDK and upload a bounded name/main/extra payload. The server derives identity from the connection, validates the cards using existing legality rules, and stores an immutable deck snapshot per player. `get` and `clear` remain server commands. Selection lasts for the connection; editing a file requires `set` again. Named solo AI decks remain in the server registry. Keep this fix in the existing UI worktree so the next test JAR includes both changes.

- [x] Add regression tests for invalid uploaded IDs, independent players using the same local filename, immutable snapshots, clearing, and bounded payload decoding.
- [x] Implement the upload payload/handler, local list/set commands and suggestions, and server snapshot resolution/cleanup. Remove the server filename-based player selection path and bump the network version.
- [x] Run targeted and full JUnit checks, verify actual client command dispatch where practical, review the network boundary, build a test JAR, and update command/network documentation.

Validation limits: deck membership, unknown positive IDs, aliases and banlists remain governed by the existing validator's documented limitations. This change does not upload files, scripts or paths to be opened by the server.

Verification: the baseline failed the new nonpositive-ID regression. The final JUnit run passed 574/574 tests. The real-client `duel_deck_upload` scenario passed 7/7 checks, including raw/quoted filename completion, local command dispatch, network upload, server snapshot resolution after local-file deletion, and clear-command routing. Independent review found a completion bug caused by filtering already-quoted names; filtering raw names before quoting fixed it and both prefixes passed in Minecraft. Logs: `build/deck-upload-red.log`, `build/deck-upload-focused.log`, `build/deck-upload-integration.log`; saved harness report: `build/deck-upload-verification/`. Multi-machine e4mc play-testing remains for the players.

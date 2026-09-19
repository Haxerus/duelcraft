# Duelcraft M4 friend playtest

Build: **1.0.0-m4-qa.1**, protocol **7**, branch **codex/qa-m4**.

This checkpoint contains M1–M4 and current main (`bc2fcba`). Main was already an ancestor of the reviewed M4 commit (`45b7460`), so no merge was necessary. The QA branch enables the existing `/duel collection` command in packaged clients so testers can open the editor. Home, binder, mat and lobby navigation remain M5 work.

## Install

Use **Windows x64**, **Java 21**, **Minecraft 1.21.1** and **NeoForge 21.1.224** on every client and the host. The JNI bridge also needs the Microsoft Visual C++ 2015–2022 x64 runtime (its DLL imports include MSVCP140 and VCRUNTIME140).

1. Create a separate launcher instance and test world.
2. Copy both files from the ZIP's `mods` folder into that instance's `mods` folder: `duelcraft-1.0.0-m4-qa.1.jar` and `ldlib2-neoforge-1.21.1-2.2.39.a-all.jar`. Replace older copies of these mods rather than installing duplicates.
3. For a dedicated server, install the same NeoForge version and both JARs in its `mods` folder. For LAN, every player still needs both mods; the host opens the world to LAN normally.
4. Launch with internet access. Card databases and scripts download automatically on first launch; no EDOPro installation is needed. Allow startup to finish before testing.

Native DLLs and SQLite are included inside Duelcraft. Do not unpack the mod JAR. `BUILD-INFO.txt` records the source commit; `SHA256SUMS.txt` identifies the exact distributed files. All participants must use this same build.

## Start a duel

1. Use `/duel collection` to edit and save a list, then activate it. Alternatively, put a `.ydk` file in your instance's `duelcraft/decks` folder and run `/duel deck set "Deck name"` without the extension. This imports and saves Main/Extra/Side, then attempts activation. It grants no cards.
2. Each player needs an active eligible list. The default `requireCardOwnership=false` allows legal lists without deposited copies. Supported legality and companion restrictions still apply.
3. One player runs `/duel challenge OtherPlayer`; the other runs `/duel accept`.
4. Both run `/duel hand rock`, `/duel hand paper`, or `/duel hand scissors`. Repeat after a tie. The winner runs `/duel first yes` or `/duel first no`.
5. Use `/duel show` to reopen the duel, `/duel forfeit` to concede, or `/duel invite cancel` to cancel preparation. The recipient can use `/duel invite decline` before acceptance. `/duel test` starts a solo duel with the active list.

The command interface is temporary for this checkpoint. Collection editing is blocked after invitation acceptance and during a duel. An incoming acceptance can suspend an open editor; cancellation or closing an ended duel restores it. Disconnect clears unsaved private screen state.

## Multiplayer checklist

- Have both players save/activate different decks, duel, leave and rejoin. Confirm each keeps their own saved lists and selection.
- Play through a normal win and a surrender. Start another duel afterward. Try both players going first and an RPS tie.
- Decline and cancel an invitation; cancel after acceptance; disconnect during RPS and during a duel. Confirm the remaining player can start another flow.
- Open the collection before an incoming invitation is accepted, if practical. Confirm acceptance closes/suspends editing and cancellation restores the draft. This checks the current temporary command route; M5 will provide UI navigation.
- Test deposits/withdrawals, full inventory, and repeated clicks. Neither transfers nor deck changes should succeed while preparing or dueling.
- For ownership testing, stop the world/server, set `[ownership] requireCardOwnership=true` in `config/duelcraft-server.toml`, and restart. An existing world `serverconfig/duelcraft-server.toml` override takes precedence. Missing ownership clears activation but preserves the list. Only deposited exact-passcode copies across Main/Extra/Side count. Deposits do not auto-activate. An operator can obtain fixture cards through `/duel card give <player> <passcode> <count>`.

When reporting a problem, include the build version, ownership setting, which player hosted, actions immediately before the issue, expected/actual behavior and screenshots. Include both clients' and the server's `logs/latest.log`, plus a crash report if generated. Note whether other mods were installed and whether the issue repeats in the separate test instance.

## Build verification

- `GRADLE_USER_HOME=C:/Users/haxer/.gradle ./gradlew.bat build`: successful after the QA entry-point change; **1,007 tests, 64 suites, zero failures/errors/skips**.
- Final JAR inspection confirms the QA version, protocol implementation, both Windows DLLs, bundled SQLite and unconditional registration of the existing collection command.
- A separate Java 21 process with an empty native-library search directory loaded the DLLs from this JAR, reported core version 11.0, and created/destroyed a real engine successfully. This is a packaged-native smoke check, not a full production Minecraft launch.
- M4's earlier real Minecraft verification passed 286 final runtime checks, including dedicated players under both ownership settings and five restart JVMs. Those checks predate the QA-only command registration change and remain separately recorded.
- The user found no issues in their M4 playtest, confirmed saved/active persistence and missing-ownership clearance on rejoin, and ran the fast automated failure scenario without noticing a visual issue. Two-player manual acceptance remains for this friend playtest.

This preparation did not rerun the complete two-player matrix or launch the packaged client in a fresh production Minecraft installation. Existing Ubuntu CI still uses the Windows-only native build pipeline; this local Windows build is the verified artifact. M5/M6 implementation and a main-branch merge are outside this QA checkpoint.

Maintainer rebuild: use the pinned native submodule/build prerequisites in AGENTS.md and `GRADLE_USER_HOME=C:/Users/haxer/.gradle`, then run `./gradlew.bat build`. Local evidence is in `build/evidence/qa-m4/`; the distributable ZIP is in `build/distributions/`.

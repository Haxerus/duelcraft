# Duelcraft M4 QA.3 collection playtest

This checkpoint refines collection controls and groups identical cards when sorting. It preserves the existing card browser, filters, inspector and server policy. The QA.2 duel fixes remain included; M5 entry points and the separate card-interaction follow-ups are not part of this batch.

## Install

Use Windows x64, Java 21, Minecraft 1.21.1, NeoForge 21.1.224 and the Microsoft Visual C++ 2015–2022 x64 runtime. The package contains Duelcraft **1.0.0-m4-qa.3** and LDLib2 **2.2.39.a**; protocol remains **7**. Use the same package on all clients and the host. Keep prior QA installations and worlds intact; use a separate test instance/world.

Copy the two JARs into that instance's `mods` folder, with only one version of each mod installed. The packaged DLLs and SQLite remain inside the Duelcraft JAR. `BUILD-INFO.txt` records the source revision and `SHA256SUMS.txt` records the package contents. The [verification report](superpowers/reports/2026-10-01-collection-controls-qa3-verification.md) records which automated checks actually ran and any outstanding limitations.

## Collection checks

1. Open `/duel collection`. Check **My Collection**, the compact deck name and readable saved/active state. Confirm the inspector is left, deck centre and browser right. Exercise the existing search, rich filters, ownership selector, sorting selector, artwork, Deck/Side routing and card transfers.
2. Save at least six decks, including two with the same display name. Open **My decks** and scroll. At least five complete rows should be visible. Selecting a row should only select it; **Open** should load that exact deck. **Make a copy** and **Delete** should target the selected saved deck, even when another deck is currently open. Check the active marker and close with X.
3. Use top-level **New deck**. Closing an untouched empty draft should not ask about saving. Change a populated deck, then try New, Open, Import and Close. Check **Save**, **Discard** and **Keep editing**; a failed save must keep the draft and prevent the requested navigation. While saving, conflicting actions should be disabled.
4. Put several `.ydk` files in `<instance root>/duelcraft/decks`. Open **Import / export** and select a file from the dropdown. It should offer files from this folder, with no operating-system file picker. Import preserves Main/Extra/Side. Check an empty folder and a malformed file; there should be no silent loss of the current draft.
5. Export a deck with all three sections. Reimport and compare exact order and counts. Check overwrite cancellation and confirmation. The saved server deck, active selection and deposited quantities should not change from exporting alone.
6. Open **?** Help. Instructions and ownership-policy explanations should live here rather than in routine footer captions. Check that actionable validation, save/import/export failures and policy denials remain visible.
7. Open each dropdown and nested dialog, including export overwrite. Esc should close only the topmost layer before closing the editor. Check the visible Close button and that modal controls do not click through to cards underneath.
8. Interleave copies of two cards of the same type, then Sort. Identical copies should become consecutive, with first-seen order between different cards retained. Check Main, Extra and Side separately; quantities and section membership should not change. A second sort should leave the list unchanged.
9. Repeat layout checks at 1280×720 / GUI scale 2 and 1920×1080 / GUI scale 3, including a long deck name and long saved-list names. Confirm menus, dropdowns and active state fit without clipping.

User acceptance of in-game spacing remains the visual checkpoint after the mockup. Report the build, screen size/GUI scale, ownership setting, reproduction steps and a screenshot where useful.

## Policy and deferred work

The server still defaults to `requireCardOwnership=false`. Ownership enabled counts deposited exact-passcode copies across Main, Extra and Side. Drafts can be saved without those copies. Supported legality and companion restrictions apply in either mode. Transfers still require actual items and authoritative checks. This update grants no cards.

Final Countdown / Gold Sarcophagus indicators, pre-errata copy-limit identity, Question's Graveyard restriction and summon-method selection are separate passes. This is still the command-based M4 checkpoint; use the [QA.2 guide](qa-m4-qa2.md) for duel command entry points and earlier checks. Earlier automated packaged-client GLFW failures remain historical evidence; user-reported QA.2 successes are recorded separately and do not establish a new automated QA.3 pass.

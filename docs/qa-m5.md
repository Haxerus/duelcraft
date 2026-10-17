# M5 player-entry checkpoint

M5 adds the illustrated Home screen, private duel lobby, Card Binder and Duel Mat. M6 remains the consolidated QA and release-acceptance checkpoint. Use this alongside the [QA.3 carry-forward register](superpowers/reports/2026-10-06-qa3-carry-forward.md); earlier manual results do not automatically cover this build.

**2026-10-08 playtest:** the user reports that all non-multiplayer checks worked except the lobby presentation and broken rule-preset dropdown. Multiplayer checks carry forward. The lobby redesign and an LDLib2 style guide belong to a separate full UI pass. See [the feedback and correction report](superpowers/reports/2026-10-08-m5-playtest.md); keep the checklist below as the procedure for remaining checks and affected regressions.

## Installation

Windows x64 throughout, Java 21, Minecraft 1.21.1, NeoForge 21.1.224, LDLib2 2.2.39.a, and the Microsoft Visual C++ 2015–2022 x64 runtime. Replace the Duelcraft JAR in a separate test instance and use the same version on both clients and the server. M5 uses protocol **8**; M4/protocol-7 peers are incompatible. Saved collections and decks retain their existing format.

The build artifact and exact automated results are recorded in the [M5 verification report](superpowers/reports/2026-10-07-player-entry-m5.md). A development-harness pass is distinct from installing and accepting the same packaged JAR on all three processes.

## Playtest

1. With no screen open, press **J**. Rebind it in Minecraft Controls, then test the new binding. Chat, text fields, and the game menu should consume no queued Home action. Home should remain usable while artwork loads or is unavailable.
2. Use **Collection & Decks**. Save and activate a legal deck with ownership off and no deposited cards. Check browser right-click and double-left-click add; deck right-click removes. The fourth copy must be rejected across Main/Extra/Side, and Side routing must still work.
3. Make an unsaved change and use **Back**. Check Save, Discard and Cancel. Start a Back request and make another edit before it returns: the later edit must still require a decision. Keep the existing rich filters, saved-list/import/export controls and inspector arrangement in the review.
4. Open **Duel**, select a player and inspect **Settings**. Change the seed, visit the collection, then return: the unsent form should survive. Invalid LP/hand/draw/seed input must disable Invite. Escape closes the dropdown, then Settings, then the screen. Typing after closing Settings must not change a hidden field.
5. Try Tab/Shift+Tab and Enter/Space on Home and the lobby. Arrow keys change the focused rule preset. Focus must stay within an open Help/Settings dialog and reveal target rows while scrolling.
6. Invite the second player. They can open the invitation from Home or the chat link. Closing the lobby leaves the invitation pending; Decline or Cancel ends that flow. Try simultaneous invites, an offline target and expiration. A pending invitation should not replace an editor.
7. Accept while either player has an unsaved editor open. Both should reach RPS, with no opponent hand exposed. Test a tie, both first-player choices, Cancel, closing/reopening the lobby, and an opponent disconnect. Use Home's **Resume draft** after cancelled preparation. A late Save acknowledgement must not replace the preparation/duel screen. Disconnect clears unsaved local drafts.
8. Complete or concede a duel and return Home. Confirm the active deck and saved lists remain. Existing result Close can return to a suspended editor.
9. Obtain a **Card Binder** and **Duel Mat** from the creative tab (no acquisition recipe is part of M5). Use the binder in each hand, and the same mat with empty/occupied hands from both players. Each sees their own data. Test ordinary client clicks, offhand precedence and a binder held while clicking the mat; a single use must not open twice. Break the mat: collections remain and J still works. Repeat entry during preparation/live play to verify the server redirects it.
10. Restart the host with ownership enabled. Saving an unowned list still works; activation waits for deposited exact-passcode copies, including Side. Deposit, activate, duel, then withdraw enough to create a shortage: activation clears but the saved list remains. With a companion restriction installed, repeat under both ownership settings and verify the affected owner sees its reason, the other player does not, and failed policy evaluation changes neither inventory nor collection.

Check the new screens at 1280×720 / GUI 2, 1920×1080 / GUI 3, and a smaller window. Report the build, window/scale, ownership mode and reproduction steps. Help wording and final artwork remain editable; levels, rewards and acquisition belong to a companion mod.

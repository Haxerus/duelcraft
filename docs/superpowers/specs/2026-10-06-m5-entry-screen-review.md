# M5 Home and lobby visual review

Status: illustrated Home approved by the user and implemented in M5. See [verification and remaining acceptance](../reports/2026-10-07-player-entry-m5.md).

The user approved moving to M5 on 2026-10-06 and carrying outstanding QA.3 acceptance into M6. The existing [M5 plan](../plans/2026-09-17-player-entry-points-milestone-5.md), [interaction design](2026-09-15-player-interaction-design.md) and [contracts](2026-09-17-player-interaction-contracts.md) still define scope and authority. This note specifies the proposed presentation, not a new subsystem.

## Review artifact

Open [the interactive Home/lobby mockup](../../mockups/m5-player-entry.html) in a browser. Controls above the canvas switch example states and are not part of the game. Names, timers, server events and deck status are fixtures. No network requests, saves or Minecraft changes occur.

The preview uses a uniformly scaled 1280x720 canvas. Home places compact controls on the left and a featured monster illustration on the right, fading the art behind the controls. The lobby has a fixed header/footer and scrollable content. The dark panel colours and restrained purple selection follow the approved collection mockup. LDLib implementation will use the existing Minecraft font and framed GDP widgets; HTML typography is approximate.

After the initial review, the user approved static featured monster artwork until original Minecraft-styled Duelcraft renders are available. The revised preview offers Blue-Eyes White Dragon, Dark Magician and Stardust Dragon through a review-only selector. Those examples use existing cached artwork, not generated replacements. There is no automatic slideshow or new in-game artwork settings menu in this proposal. Keep the lobby/settings layout; Help wording is provisional and the user may edit it later. Expanded duel rules are a separate pass. Duelist levels/progression belong to the companion mod; do not add a core level system or placeholder indicator in M5.

## Proposed layout

- Home: active-deck summary, Collection & Decks and Duel. Show an invitation action and Resume draft only when relevant. A single Close button returns to the world.
- Lobby: own active-deck status and Select deck on the left; the current invitation/preparation step on the right. The online-player list shows six rows before scrolling. The footer holds the action for that step.
- Settings: a compact dialog for rule preset, life points, starting hand, draw per turn and optional seed. Show the current settings in the lobby. Freeze them once an invitation exists.
- Incoming invitation: sender, settings, own deck status, Select deck, Accept and Decline. Outgoing invitation: target, settings, expiry and Cancel invitation.
- RPS: three text buttons, disabled after submission. Ties ask for another choice; opponent hands remain private. First-choice view offers Go first / Go second only to the winner. Starting shows progress without duplicate start actions.
- Keep Help behind `?`. Include contextual denial/loading/error text where the player needs it; omit routine explanatory footers.

The alternative of putting every numeric setting beside the target picker would make a routine invitation busier. The proposed dialog keeps those options available without expanding the main form. Home has two direct actions rather than duplicating the editor or adding another deck picker.

## Navigation contract

Home, binder and mat use the same server-authorized route coordinator. Pending invitations permit collection editing. Accepted preparation and live duels take priority over management. Closing the lobby does not cancel the flow; explicit Cancel does. Help/settings close with Escape before the underlying screen.

Select deck opens the existing editor and preserves the invitation form for return. The mockup shows only a labelled stand-in for that existing screen; it does not redesign the browser, filters or inspector. Dirty voluntary navigation uses Save / Discard / Keep editing. Forced preparation suspends a draft locally; Resume draft appears after cancellation/end during that connection. No automatic save or discard. Disconnect clears the suspended session.

The preview simulates these transitions but does not prove server routing, asynchronous replies, failure recovery or privacy. M5 tests must cover those contracts in Java and the real UI.

## Execution checkpoints

Preview verification: headless Chrome exercised all 16 scene choices with five deck fixtures at 1320px and 900px browser widths (160 combinations; full-size and uniformly scaled canvas). Button/heading/panel bounds checks and settings, invitation close/reopen, RPS submission, busy binder routing, dirty Escape and suspended-draft interactions passed, with no JavaScript errors. Home, incoming-invitation and RPS screenshots were visually inspected. Local script/screenshots: `build/evidence/m5-mockup-2026-10-06/`. Browser automation through the app failed to initialize; the check used installed Chrome with Playwright instead. These are HTML preview checks only, not new Minecraft/LDLib or multiplayer results. No Gradle suite was needed for this documentation/prototype batch.

Featured-art revision verification: the 160-combination preview regression and its interaction checks passed again. All three local images decoded; 12 additional combinations covered the three artworks at both widths with normal content and long names plus invitation/Resume draft together. Screenshots were inspected for the artwork placement and text separation. Revised evidence is separate at `build/evidence/m5-art-preview-2026-10-06/`; the original preview evidence remains intact.

- [x] Inspect existing worktree, branch and HEAD; preserve QA.3 source/evidence.
- [x] Carry open acceptance checks into the [M6 register](../reports/2026-10-06-qa3-carry-forward.md) without marking them passed.
- [x] Prepare interactive Home/lobby mockup using the current preparation states and settings bounds.
- [x] User reviews initial Home/lobby structure and approves the lobby/settings layout and featured-art direction.
- [x] User reviews the revised illustrated Home before LDLib implementation.
- [ ] Implement M5's five existing tasks with focused tests and real UI checks; include the browser right-click add correction as a scoped change.
- [ ] Complete M5's integrated/dedicated journey checks, recording any blocked manual checks accurately.
- [ ] Run M6's consolidated manual QA, bounded fixes and release/documentation audit.

Continue in `.superpowers/worktrees/deck-editor`, currently `codex/qa-m4` at `22e5f70` before this documentation change. Inspect state again before implementation. No branch merge or push is authorized by this checkpoint.

# M5 player-entry implementation and verification

Worktree: `.superpowers/worktrees/deck-editor`; branch `codex/qa-m4`; implementation commit `a07b651883154d46ca36a0f2907c91b249063787` (base `25a807e`). No merge or push. The user approved the featured-art Home mockup and moving remaining M4 QA.3 acceptance into M6.

## Implemented

- Server-resolved Home/Collection/Lobby/Duel routes, latest-request and connection-generation guards, and the remappable J entry.
- Featured monster artwork fading behind the compact Home menu; current deck, invitation, Help, and Resume draft controls.
- Typed private invitations, settings, RPS and first-player controls using the existing preparation service. Owner-only policy explanations. Collection Back preserves its Home/lobby parent and existing Save/Discard/Cancel behavior.
- Stack-one binder and a thin, non-colliding mat without a block entity or stored collection. Both use the same route/policy authority. No recipes or progression were added.
- Browser right-click adds through the existing routing/copy-limit path; double-left-click add and deck right-click remove remain.
- Protocol 8 adds mandatory route and private-notice payloads. Persistence formats remain unchanged.

## Evidence

Evidence is under `build/evidence/collection-m5/`; prior reports, worlds and screenshots are preserved.

| Check | Result |
| --- | --- |
| Baseline `test` | Passed before M5 changes (`baseline.log`) |
| Focused route, payload, controller and physical-item tests | Passed |
| Full `test build` | Final post-review build passed: 1113 cases, 1112 passed, one existing platform skip, no failures/errors (`final-checkpoint.log`, `junit-final/`) |
| Home/lobby initial real UI | 2/2 scenarios, 18/18 checks |
| Browser right-click and preparation retention | Real regression failed before wiring, then passed (`ui-integration-first/`) |
| Real binder/mat routes and integrated Home/save/activate/lobby-return journey | Passed (`ui-journeys-default/`) |
| Independent review regressions | Home keyboard navigation, hidden Settings focus and edits during pending Back each reproduced; all three scenarios passed after fixes (`review-green/`) |
| Two-client dedicated journey, ownership off | Initial and expanded journeys passed: native duel, invitation/RPS/first choice, UI concede, Home, own binder collection, shared mat and private companion denial (`mp-default/`, `mp-default-expanded/`) |
| Two-client dedicated journey, ownership on | Expanded journey passed: 93/93 checks, including physical-card deposit before activation, shared mat and private companion denial (`mp-required/`) |
| Dedicated private collection regression | Warm-cache retry passed: 1/1 scenario, 62/62 checks (`mp-privacy-warm/`) |
| 1920x1080 / GUI 3 | 4/4 scenarios, 40/40 checks, including Home/lobby and import-to-binder regression (`entry-import-green-scale3/`) |
| 960x540 / GUI 2 | Home/lobby 2/2 scenarios, 26/26 checks (`ui-small/`) |
| 1920x1080 / GUI 4 | Home/lobby 2/2 scenarios, 26/26 checks (`ui-scale4/`) |
| Full UI group, 1280x720 / GUI 2 default | Passed: 57/57 scenarios, 12,227/12,227 checks (`final-checkpoint.log`, `ui-final-passed/`) |
| Installed same-JAR human acceptance | Not performed; remains M6 |

Screen captures were inspected at all three window sizes. Recorded framebuffer/GUI dimensions were 1280x720/640x360 at scale 2, 1920x1080/640x360 at scale 3, 960x540/480x270 at scale 2 and 1920x1080/480x270 at scale 4. Runs used synthetic input on Windows 11, Java 21.0.1, NeoForge 21.1.224 and LDLib2 2.2.39.a. The full 3x3 matrix, non-16:9/resize acceptance and human comfort assessment remain M6.

The initial integrated journey ran against the user's ownership-enabled `run/config/duelcraft-server.toml` and correctly rejected an unowned activation. That configuration was preserved. Default-ownership checks use the existing isolated `run-collection-m3-ui` environment via `-PcollectionTransferUi`.

LDLib's synthetic keys only dispatch to an open screen, so the world entry fixtures inject `KeyMapping.click` and exercise the registered tick handler. Physical fixture steps call actual server item/block methods and observe network replies. They do not establish real OS key rebinding or client-side item dispatch/offhand precedence; these remain explicit manual checks.

An initial dedicated fixture failed because LDLib's click helper re-resolved a button after its press had already changed screens. The corrected fixture retains release coordinates. Historical failed reports are retained rather than counted as product regressions or silently overwritten.

The first full UI run passed 55/57 scenarios and 12,222/12,223 checks. One assertion still expected the old collection Help copy and was updated. The next run passed all 12,223 assertions but still timed out in physical entry (56/57 scenarios). Isolating `duel_deck_import,duelcraft_entry_points` reproduced the failure: the old import fixture restored server revision 0 while leaving the client requiring its temporary revision 2. Its teardown now resets client collection state, matching the other runtime fixtures. This changes fixture cleanup, not production revision checks. Failed reports and the direct reproduction remain preserved.

The combined default-ownership dedicated run passed the expanded M5 journey but timed out in the older privacy scenario while client B extracted its cold card-data cache. The retry used the already downloaded matching snapshot and passed all 62 checks; both outcomes are retained.

The dedicated journey accepts through Home's incoming invitation and calls the actual server-side mat interaction for both players. It does not prove the manual accept-through-mat or post-duel withdrawal steps. Existing transfer/policy tests retain their separate evidence; the exact installed-JAR sequence remains in M6.

## Artifact

Local Windows x64 JAR: `build/libs/duelcraft-1.0.0-m5-qa.1.jar`.

SHA256: `431644AE4690CC64D85CC03CC55FC1A30676F2E1DAB15F6D3EE5A2C5914C7EDB`.

Produced from implementation commit `a07b651883154d46ca36a0f2907c91b249063787`, including the independent-review and fixture-cleanup fixes (`committed-jar.log`). The JAR contains both Windows x64 native DLLs and the new Home/lobby/item resources. Use matching protocol-8 clients/server and the pinned LDLib2 dependency. No packaged three-process acceptance is claimed.

## Independent review

One fresh-context reviewer inspected all M5 changes against the plan/contracts. It found three important issues: late voluntary navigation could discard edits made during the route round trip; hidden Settings fields retained focus; Home/lobby lacked keyboard traversal. Each received a real-screen failing regression and a fix. No new server-authority bypass or opponent-denial leak was found.

Rulings: preserve the existing worktree and user configuration; include unsolicited physical routes in the initial protocol-8 change; use explicit Resume after cancelled preparation while retaining the existing result-close return; use PowerShell bookkeeping because the skill's Bash helper could not resolve this Windows workspace. Acquisition/progression, engine resync, expanded legality/matchmaking, original artwork and the retained legacy command audit remain outside this M5 change. Packaged acceptance remains M6 under the user's approved sequence.

## Manual acceptance

Follow [the M5 playtest guide](../../qa-m5.md) and [QA.3 carry-forward register](2026-10-06-qa3-carry-forward.md). Pending manual checks must not be represented as passed from fixture results. M6 owns the final artifact matrix, human readability/polish, compatibility audit and release sign-off.

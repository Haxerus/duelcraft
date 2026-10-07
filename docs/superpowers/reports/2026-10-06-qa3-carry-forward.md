# QA.3 checks carried into M6

Decision: on 2026-10-06 the user approved starting M5 through the mockup-first workflow, followed by consolidated QA/polish in M6. Open M4/QA.3 acceptance no longer blocks starting M5. This changes scheduling, not the recorded results or release requirements. A demonstrated data-loss, duplication, privacy or startup defect still requires a scoped fix and regression checks.

## Evidence retained

- [QA.3 verification](2026-10-01-collection-controls-qa3-verification.md) records the source-specific build and 53-scenario real UI run. No checks were rerun for this scheduling decision.
- [Original QA.3 manual checklist](../../qa-m4-qa3.md) remains the procedure for its packaged build.
- The 2026-10-04 computer-control trial used the installed QA.3 package in an isolated world, mostly at 1920x1080 / GUI 3. Its local report and screenshots remain under `build/evidence/qa3-computer-use-2026-10-04/`.
- That trial exercised save/activation, duplicate-name selection/copy, grouping in all three deck sections, folder import/export, malformed input, layered Escape and world rejoin. It was partial coverage, not release acceptance.
- Earlier user-reported friend playtests remain manual evidence. Historical automated packaged-client failures are retained in their original reports and are not asserted to recur in QA.3.

## Input expectation correction

The trial observed no browser right-click action. Its interpretation that the browser should remove a card was incorrect. The user clarified the intended controls:

- Browser right-click adds one card using the selected Deck/Side route.
- Browser double-left-click also adds one card.
- Deck right-click removes one card from that section.

Preserve the original report as historical evidence; this correction supersedes its expected-behavior wording. A focused follow-up must add the missing browser action and clarify Help. Verify the fourth-copy limit, routing, pending/disabled guards and no modal click-through. The correction is not implemented by this documentation commit.

## Remaining acceptance register

Every row below is still open for the final candidate. Existing automated or historical passes remain separate evidence.

| Open checks | QA.3 checklist group | Owner |
| --- | --- | --- |
| Browser right-click add and matching Help, with existing double-click/removal retained | 1, 6, 7 | Scoped M5 follow-up; M6 regression |
| Exact 1280x720 / GUI 2 and 1920x1080 / GUI 3 readability; long deck/list names; clipping and active state | 1, 9 | M6 UI/readability pass |
| Text search, ownership/sort selectors, remaining rich-filter combinations, physical transfers in the installed candidate | 1 | M6 editor and transfer pass |
| Delete targets the selected saved ID; My decks X closes correctly | 2 | M6 saved-list pass |
| Remaining dirty New/Open/Import/Close combinations; failed-save retention; pending-action disablement | 3 | M5 navigation regression and M6 editor pass |
| Empty import folder; inspect exact order of all cards after UI reimport | 4, 5 | M6 YDK pass |
| Remaining dropdown/modal close and blocked-input combinations | 7 | M5 navigation regression and M6 input pass |
| Ownership-on, companion denial/failure, installed multiplayer/dedicated journeys and full process/server restart | Policy and earlier QA.2 checks | M5 journeys; M6 final acceptance |
| Earlier duel-specific QA.2 regressions on the final candidate | QA.2 guide | M6 duel pass |

The single inconclusive mouse-wheel observation in My decks is not a confirmed bug. Check ordinary wheel scrolling during the saved-list pass and record a reproduction if it fails.

## Scope and completion

M5 retains its required targeted/unit, real UI and two-player journey checks; this decision does not defer all testing until the end. M6 owns the final packaged matrix, human readability feedback, compatibility audit and sign-off. Use one identified final JAR on both clients and server; rerun affected journeys after fixes. An unavailable or skipped check remains open.

Final Countdown / Gold Sarcophagus indicators, pre-errata copy-limit identity, Question's Graveyard restriction and summon-method selection remain separately scoped follow-ups. Moving into M5 does not silently add those engine/UI changes to its plan.

No production changes, merge, push, rebuild or new game playtest occurred in this scheduling/mockup checkpoint.

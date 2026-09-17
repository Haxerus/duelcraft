# Multiplayer playtest issues — 2026-09-13

No regressions were reported from the UI rework. The two critical interactions were merged in PR #4. The next pass is on `codex/playtest-polish`; animations remain a separate pass.

For review outside the local checkout, see the [committed screenshot evidence and validation summary](superpowers/evidence/2026-09-playtest-polish/README.md). Tests and assembly were rerun successfully on 2026-09-17: 617 passed, zero failures/errors/skips.

## Critical interactions

- [x] Finish an iterative material selection without an invalid response. Reported with a Link-2 plus one other monster selected from a field of four for a legal Link Summon. Fixed the finish sentinel encoding; verified with a real Gaia Saber/LANphorhynchus summon, including rejection of the old encoding as a negative control.
- [x] Show revealed opponent hand identities in subsequent selection prompts, including Confiscation and Trap Dustshoot. Keep unrevealed information private and clear remembered hand identities on shuffle. Verified with both real card scripts and client state/packet filtering regressions.

Validation: 593 JUnit tests passed. All three real-card regressions also passed against the automatically downloaded database/scripts. `duel_revealed_hand` passed 156 UI checks, and both screenshots were visually inspected for card art and hover details. Run `./gradlew test` and `./gradlew runClient -PldTest=duel_revealed_hand -PldTestWindow=1280x720` to repeat the standard checks.

## Additional functional follow-up found during review

- [x] Invalidate remembered Extra Deck identities after `ShuffleExtra`. Clear non-face-up identities and cached stats, preserve face-up Pendulums, and use the existing recipient-filtered query to restore the owner's new order. Two regressions reproduced the old behavior before the fix. Validation: 595 JUnit tests passed, none skipped; `test assemble -x copyNative` succeeded in the new worktree.

## UI follow-ups

- [x] Improve duel-log readability: phase/step context where available, chain link numbers, and card-name highlighting. Received phase and damage-step boundaries are distinct; chain events use engine link numbers and names have semantic styling. Long-entry wrapping, scrolling, privacy and card-detail clicks are verified.
- [x] Highlight actionable cards in the zone inspector, so activation/summon options can be found without clicking every card. Uses existing idle/battle actions and clears on prompt changes and responses; sparse/full-grid screenshots verified.
- [x] Display Link markers in the card inspector sidebar. All eight directions, flagged live query overrides and clearing on non-Link/unknown cards are verified.
- [x] Render Xyz materials as cards beneath their host monster instead of the purple count pip. Bounded stacks preserve host size/click targets, including defense, opponent and shared Extra Monster Zone cards; detach/transfer refresh verified.
- [x] Correct card annotation backgrounds, including chain-number pips whose fills are narrower than their labels. Backgrounds size to rendered text and padding; single/multiple-digit labels verified in screenshots.
- [x] State the source zone of selection candidates: Hand, Deck, GY, and other zones; support mixed-zone choices. Mixed sources have readable per-card captions, including opponent ownership; verified by `duel_selection_captions` and screenshot review.
- [x] Clarify iterative material-selection captions: engine `min/max=1/1` can describe one toggle rather than the total number of materials already selected. Captions show one toggle and the current selected count while preserving engine-controlled Finish/Cancel behavior.

Static-polish acceptance (2026-09-14): 611 JUnit tests passed with no failures/errors/skips; assembly succeeded. The full UI group passed 99 scenario runs and 24,891 checks across 1280×720, 1920×1080 and 1024×768. All 408 final screenshots passed visual review. See [the screenshot gallery and reports](../build/ui-review/README.md).

## Design feedback — 2026-09-14

- [x] Put the selection source above the instructions as a larger gold title, including field prompts and mixed zones. Keep detailed instructions white and preserve Finish/Cancel behavior. Stack phase, chain and selection feedback without overlap.
- [x] Format effect questions with the card name and location instead of displaying `%ls`. Follow EDOPro's default/trigger question arguments and retain plain descriptions and trigger help text.
- [x] Arrange Link markers in a 3×3 grid with an empty center, red active arrows and gray inactive arrows. Preserve live query overrides and hide the grid for non-Link/unknown cards.
- [x] Replace sanitized zero-code log names with plain “Face-down card” or “Unknown card” labels. Preserve known card names and links without resolving hidden identities.

Fresh feedback validation: **617 JUnit tests passed**, zero failures/errors/skips; assembly succeeded. The full 1920×1080 UI group passed 34/34 scenarios and 9,427 checks. Targeted runs passed 2,121 checks at 1280×720 and 1,819 at 1024×768, including representative MR3/MR5/Speed layouts. Total: **48 scenario runs, 13,367 checks and 206 screenshots**. See [the updated screenshot gallery and reports](../build/ui-review/feedback-README.md); these captures supersede the earlier source-caption and linear Link-marker presentation.

## Toast and lingering-effect readability — 2026-09-14

- [x] Move missed bottom toasts near the middle of the board, as requested. Use larger wrapped text and a padded translucent panel; keep dialogs and side panels clear and allow pointer input through. Preserve queue order and the existing 2.5-second display time, including coin/dice results.
- [x] Give lingering player effects a translucent backdrop, padding and readable wrapping. Keep each effect on its own line in the owner's side column; position open side panels below the text and restore their space when effects clear. Preserve field and control geometry.

Fresh validation: **617 tests passed**, zero failures/errors/skips; assembly succeeded. Full 1920×1080 UI group: 36/36 scenarios and 10,494 checks. Targeted/supplemental 1280×720 and 1024×768 runs bring the total to **50 scenario runs, 14,193 checks and 199 screenshots**. See [current screenshots and reports](../build/ui-review/hint-readability-README.md). The previous bottom-toast/sidebar overlap is resolved.

## Animation follow-ups

- [ ] Animate movement between zones.
- [ ] Animate face-up/face-down flips, including temporary reveals from hands or other zones.
- [ ] Draw attack arrows to the target monster or opponent for direct attacks.
- [ ] Make chain resolution prominent: a thick hollow circle around the link number, a flash, and a brief readable pause before the effect plays out.

Animations need an explicit presentation queue so consecutive network messages remain readable while authoritative duel state and prompt ordering stay correct. Scope and timing should be designed together rather than adding unrelated delays in message handlers.

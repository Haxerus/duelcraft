# Multiplayer playtest issues — 2026-09-13

No regressions were reported from the UI rework. The two critical interactions are fixed in the worktree; the UI improvements below remain a separate follow-up backlog.

## Critical interactions

- [x] Finish an iterative material selection without an invalid response. Reported with a Link-2 plus one other monster selected from a field of four for a legal Link Summon. Fixed the finish sentinel encoding; verified with a real Gaia Saber/LANphorhynchus summon, including rejection of the old encoding as a negative control.
- [x] Show revealed opponent hand identities in subsequent selection prompts, including Confiscation and Trap Dustshoot. Keep unrevealed information private and clear remembered hand identities on shuffle. Verified with both real card scripts and client state/packet filtering regressions.

Validation: 593 JUnit tests passed. All three real-card regressions also passed against the automatically downloaded database/scripts. `duel_revealed_hand` passed 156 UI checks, and both screenshots were visually inspected for card art and hover details. Run `./gradlew test` and `./gradlew runClient -PldTest=duel_revealed_hand -PldTestWindow=1280x720` to repeat the standard checks.

## Additional functional follow-up found during review

- [ ] Audit remembered Extra Deck identities after `ShuffleExtra`, which the client currently ignores. Keep face-up Pendulum cards distinct from shuffled facedown cards. The hand-reveal fix intentionally does not extend identity retention to other zones.

## UI follow-ups

- [ ] Improve duel-log readability: phase/step context where available, chain link numbers, and card-name highlighting. Check which battle steps the engine actually exposes before promising detail.
- [ ] Highlight actionable cards in the zone inspector, so activation/summon options can be found without clicking every card.
- [ ] Display Link markers in the card inspector sidebar.
- [ ] Render Xyz materials as cards beneath their host monster instead of the purple count pip.
- [ ] Correct card annotation backgrounds, including chain-number pips whose fills are narrower than their labels.
- [ ] State the source zone of selection candidates: Hand, Deck, GY, and other zones; support mixed-zone choices.
- [ ] Clarify iterative material-selection captions: engine `min/max=1/1` can describe one toggle rather than the total number of materials already selected.

## Animation follow-ups

- [ ] Animate movement between zones.
- [ ] Animate face-up/face-down flips, including temporary reveals from hands or other zones.
- [ ] Draw attack arrows to the target monster or opponent for direct attacks.
- [ ] Make chain resolution prominent: a thick hollow circle around the link number, a flash, and a brief readable pause before the effect plays out.

Animations need an explicit presentation queue so consecutive network messages remain readable while authoritative duel state and prompt ordering stay correct. Scope and timing should be designed together rather than adding unrelated delays in message handlers.

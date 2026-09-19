# Task 2 report — authoritative policy context and editor feedback

Status: complete.

## Changes

- Extended `CollectionReply.Opened` and `ClientCollectionState.View` with the server's effective `ownershipRequired` mode and optional `clearedActivation` report. Snapshot assembly publishes both only after all pages complete; disconnect and connection-generation safeguards continue to clear or reject stale state.
- Passed snapshot policy context from `CollectionService.ownershipRequired()` rather than deriving client mode from a neutral report. Evaluated reports still carry their candidate's policy facts, but neutral/non-applicable reports do not replace the Opened mode.
- Encoded the new Opened fields with the existing bounded Report codec, covered absent/present clearance, optional/required mode, malformed and oversized restriction reasons, and the 24 KiB total reply envelope. Bumped the payload registrar from protocol `4` to `5`.
- Added first-Open active-clearance feedback with generic previous-active-list wording. The controlled client path retains it through the initial view and a later ordinary snapshot without claiming that the displayed draft was evaluated.
- Split core legality warnings from deposited-copy shortages. Optional shortages use neutral footer, tile, and inspector styling; required shortages use blocking warning styling. Activation remains a server request in both modes.
- Added mode-specific activation tooltips that preserve mandatory legality and explicitly say companion restrictions may still apply.
- Rendered companion restriction reasons in the activation dialog under either ownership mode. Optional shortage rows are not shown as denial reasons; counts remain visible in the existing footer, inspector, tiles, and filters.
- Changed controller failure detail to report the actual companion reason first, and to describe missing copies as a failure only when the evaluated report required ownership.
- Updated the real saved-list scenario to save through authenticated packets, then use a distinct legal 40-card real-catalog fixture with zero deposited copies for the activation phase. This verifies server-approved optional activation rather than accidentally exercising mandatory Main Deck size rejection.
- Updated the interaction contract so Opened is the authoritative snapshot mode, evaluated reports carry candidate-specific policy facts, and neutral reports neither change mode nor certify a draft. Documented `clearedActivation` and protocol `5`.

## Red/green evidence

Mandated red command:

```text
./gradlew.bat test --tests '*CollectionPayloadTest' --tests '*CollectionSnapshotStoreTest' --tests '*ClientCollectionStateTest' --tests '*SavedDeckControllerTest'
```

Result before implementation: failed in `compileTestJava` with 19 expected missing-API errors for the new Opened/View policy fields and snapshot signature; there were no unrelated failures.

Initial UI red command:

```text
./gradlew.bat runClient -PldTest=collection_eligibility -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain
```

Result: run `32920_20bf7206`, 0/1 scenarios, 21/28 checks, seven expected policy-presentation failures. Retained at `build/evidence/policy-task2/red-1280x720-scale2/`.

After the first green capture exposed an ambiguity in the optional companion dialog, an explicit assertion was added that optional shortages are absent from denial rows. Run `13636_65013d71` then failed exactly that assertion (28/29 checks), and the renderer was corrected to show missing-copy denial rows only for required ownership.

The first paired integration attempt, run `27072_65013d71`, reported 1/2 scenarios and 46/46 completed assertions before timing out on optional activation. Diagnosis: the stale fixture had saved a one-card Main Deck, so mandatory core legality correctly rejected before optional ownership was relevant. Its reconstruction and diagnosis are retained at `build/evidence/policy-task2/failed-27072_65013d71/`. A follow-up run `35392_30a99b85` reached the new legal fixture and failed only an obsolete `1`-tile assertion (actual `40`); its complete report and screenshot are retained at `build/evidence/policy-task2/failed-35392_30a99b85/`. Focused run `33360_386d41cb` then passed `collection_saved_decks`, 23/23 checks.

Final real UI commands:

```text
./gradlew.bat runClient '-PldTest=collection_eligibility,collection_saved_decks' -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain
./gradlew.bat runClient '-PldTest=collection_eligibility,collection_saved_decks' -PldTestWindow=1920x1080 -PldTestGuiScale=3 --console=plain
```

Results:

- Run `32320_386d41cb`: `BUILD SUCCESSFUL`; 2/2 scenarios, 52/52 checks, 222 steps, seven captures at 1280x720/scale2. Evidence: `build/evidence/policy-task2/final-1280x720-scale2-run-32320_386d41cb/`.
- Run `34252_65013d71`: `BUILD SUCCESSFUL`; 2/2 scenarios, 52/52 checks, 222 steps, seven captures at 1920x1080/scale3. Evidence: `build/evidence/policy-task2/final-1920x1080-scale3-run-34252_65013d71/`.

Both final sets capture optional activation, required shortage denial, optional companion denial, first-Open clearance, omitted-problem handling, authenticated save, and authenticated legal-unowned activation. Visual inspection found no clipping or style regression. The optional companion dialog contains only `Server restriction: Era locked`; neutral shortage information remains visible behind the dialog and on the active real list.

Mandated focused green command:

```text
./gradlew.bat test --tests '*CollectionPayloadTest' --tests '*CollectionSnapshotStoreTest' --tests '*ClientCollectionStateTest' --tests '*SavedDeckControllerTest' --console=plain
```

Result: `BUILD SUCCESSFUL` in 17 seconds; 46 tests across four suites, zero failures/errors/skips.

Final full verification after the last production edit:

```text
./gradlew.bat test --console=plain
```

Result: `BUILD SUCCESSFUL` in 31 seconds; 897 tests across 53 suites, zero failures/errors/skips. Language JSON parsing passed. `git diff --check` passed with only the repository's CRLF conversion notices.

## Files

Modified production and contract files:

- `docs/superpowers/specs/2026-09-17-player-interaction-contracts.md`
- `src/main/java/com/haxerus/duelcraft/collection/CollectionReply.java`
- `src/main/java/com/haxerus/duelcraft/server/DuelNetworking.java`
- `src/main/java/com/haxerus/duelcraft/server/collection/CollectionPayloadHandler.java`
- `src/main/java/com/haxerus/duelcraft/server/collection/CollectionService.java`
- `src/main/java/com/haxerus/duelcraft/server/collection/CollectionSnapshotStore.java`
- `src/main/java/com/haxerus/duelcraft/server/collection/CollectionWire.java`
- `src/main/java/com/haxerus/duelcraft/client/collection/ClientCollectionState.java`
- `src/main/java/com/haxerus/duelcraft/client/collection/SavedDeckController.java`
- `src/main/java/com/haxerus/duelcraft/client/collection/CollectionController.java`
- `src/main/resources/assets/duelcraft/lang/en_us.json`
- `src/main/resources/assets/duelcraft/ui/collection_screen.xml`

Extended tests/scenarios:

- `CollectionPayloadTest`, `CollectionSnapshotStoreTest`, `ClientCollectionStateTest`, `CollectionClientTest`, `SavedDeckControllerTest`
- `CollectionEligibilityScenario`, `CollectionSavedDeckScenario`

## Concerns / follow-up boundary

- Task 3 still owns producing `clearedActivation` from real first-Open persisted-active revalidation, persisting any cleared selection, and process/dedicated runtime evidence. Task 2 sends `null` from the current Open handler until that producer is wired.
- Evidence under `build/evidence/policy-task2/` is intentionally retained for local review and is build output rather than committed source.
- No M3 progression/transfer work or M4 duel-start cutover was added.

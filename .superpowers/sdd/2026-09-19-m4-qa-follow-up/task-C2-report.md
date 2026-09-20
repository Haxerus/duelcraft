# C2 implementation report

Status: implemented and focused verification passed.

## Scope

Source commit: `4de16a66b84891de6041a62da4169451f8f6b981`

The documentation/report commit is the commit containing this file. The parent QA index deliberately leaves C2 pending review; it includes the pre-existing C1 completion update from the coordinator.

Implemented only Task C2:

- One-shot initial open creates a blank draft, applies the initial collection view, and reads the active saved UUID once through the existing guarded selection path.
- Startup without a supplied view performs one refresh and feeds its result into the same initial-open path. Ordinary refresh and resume continue to update ownership/state without auto-selecting or replacing a dirty draft.
- Initial reads reject missing, failed, wrong-identity, stale-revision, locally edited, superseded-selection, newer-view, and disposed callbacks.
- `needsDiscardConfirmation()` is separate from save eligibility/`dirty()`: an empty unsaved draft remains savable and closes directly, while changed stored lists still require a decision.
- Navigation remains gated by a pending save before the discard predicate. Save failure keeps the draft/editor; acknowledged success completes deferred navigation.
- The header has a visible Close button. Escape closes exactly one top layer, calling `Selector.isOpen()/hide()` for collection ownership, card sort, filter measure, saved-list, and import selectors before owned dialogs/subviews. Keep editing cancels only queued navigation.
- The QA scenario uses real screen key-down/key-up events. It covers delayed save failure/success, nested selector → My decks → editor Escape, delete/Escape/cancel, empty unsaved close, changed saved-empty close, and the C1 selection-preservation tightening for right-click removal.
- Existing runtime scenario assertions that selected a saved list from a pristine empty draft now expect direct navigation, matching the new discard predicate. These are source assertion updates only; retained-world/restart acceptance remains owned by I1.

No server ownership/authentication/revision/transfer policy, payload/native/dependency/version, C3/C4, I1, M5, or M6 behavior changed.

## RED/GREEN provenance

All evidence is under `build/evidence/qa-m4-follow-up/c2/` and is intentionally build-local.

- `unit-interface-red/`: the new tests initially did not compile because the C2 interfaces were absent. Its copied 25-test XML/HTML is explicitly labelled stale in `README.txt`; it is not represented as output from that failed compilation.
- `unit-behavioral-red/`: after interface-only stubs, the focused suite ran 38 tests with 10 behavioral failures covering initial active open, stale/delayed guards, refresh retention, discard decisions, and pending navigation.
- `unit-lifecycle-green/`: the same 38-test focused suite passed after lifecycle implementation.
- `ui-behavioral-red/`: the real collection editor scenario passed its first 47 checks and then errored at the expected missing `#close-editor` control.
- `ui-green-attempt1/`: the scenario passed 62/62 checks after UI/Escape implementation.
- `final-unit/`, `final-editor/`, and `final-preparation/`: fresh final focused evidence after all source/test edits.
- `affected-saved-list/`: the directly affected initial-refresh-failure/empty-draft scenario passed.
- `affected-saved-decks/`: supplemental scenario evidence retained. The revised empty-draft navigation assertion passed; the run later recorded 16/17 checks and errored because ignored `run/config/duelcraft-server.toml` had `requireCardOwnership = true` while this scenario assumes optional ownership. Step 10 records expected “Ownership optional” versus actual “Ownership required”. This is a local policy-fixture mismatch, not a C2 product failure.

## Verification

Environment for Gradle commands:

```text
GRADLE_USER_HOME=C:/Users/haxer/.gradle
JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.1.12-hotspot/
```

Final required commands:

```text
./gradlew.bat test --tests '*SavedDeckControllerTest' --console=plain
BUILD SUCCESSFUL
39 tests, 0 failures, 0 errors, 0 skipped

./gradlew.bat runClient -PldTest=collection_editor_qa --console=plain
BUILD SUCCESSFUL
1/1 scenario, 62/62 checks, 216 steps

./gradlew.bat runClient -PldTest=collection_preparation_retention --console=plain
BUILD SUCCESSFUL
1/1 scenario, 7/7 checks, 38 steps
```

Additional directly affected command:

```text
./gradlew.bat runClient -PldTest=collection_saved_list --console=plain
BUILD SUCCESSFUL
1/1 scenario, 24/24 checks, 105 steps
```

`git diff --cached --check` passed before the source commit. The final editor and preparation screenshots were inspected at their original resolution: the visible Close control is inside the 1280×720 layout, the nested surfaces are unobstructed, and the retained dirty draft remains visible after suspension/resume without clipping or overlap.

## Warnings and limitations

- Expected framework/environment warnings: Gradle 10 deprecation notice; terminal capability; NeoForge/Mixin callback diagnostics; Minecraft command ambiguity; missing example block/item assets and `duelcraft:card_back`; missing vanilla goat-horn sounds; shader sampler/uniform notices; one missing card image HTTP 404; and card-data/string-table initialization logs.
- The supplemental saved-decks policy mismatch is classified above and archived without overwriting passing evidence.
- Per the task boundary, no full `group:duelcraft` run or reconnect/restart lifecycle was performed. I1 owns combined/restart acceptance.
- No C3 export dialogs exist yet. C2 includes the import selector in layered dismissal and does not add speculative export behavior.

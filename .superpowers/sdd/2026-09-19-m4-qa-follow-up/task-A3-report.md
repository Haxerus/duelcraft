# Task A3 report: stacked right panels

## Outcome

Task A3 is implemented in source commit `242928b` (`fix: stack duel log and zone inspector on the right`). The duel log and zone inspector now share `#duel-right-column`: the zone is above the log, each visible panel takes the full column when alone, and both receive an equal-height split after the existing four-unit gap. The card details panel remains independent on the left, including when a prompt is active or a card-bearing log entry is clicked.

No wire, payload, server, native, or Gradle behavior changed. The root A3 checkpoint remains unchecked for reviewer sign-off. The pre-existing root-index edit marking A2 complete is preserved for the documentation commit containing this report.

## Files

- `src/main/resources/assets/duelcraft/ui/duel_screen.xml`
  - Added `#duel-right-column` with the existing 210-unit width and top/right/bottom offsets.
  - Moved the existing zone and log nodes into the column without changing their selectors.
  - Kept the column above the prompt dimmer so both read-only panels remain usable beside an active prompt.
- `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java`
  - Added `refreshRightPanels()` and invoked it for log/zone visibility, field layout, and player-hint reflow.
  - Removed log/card-details mutual exclusion from the log toggle, log-entry click, and card-details display path.
  - Preserved independent pixel scroll offsets while either sibling changes the viewport height.
  - Followed append-only log updates only when the prior real offset was already at the bottom.
  - Added revision guards to delayed viewport/content layout callbacks. Superseded callbacks still unregister themselves but cannot overwrite offsets captured by a newer layout/rebuild.
  - Reflowed local hints against card details and opponent hints against the new right column.
- `src/main/java/com/haxerus/duelcraft/client/ZoneInspectorController.java`
  - Added a visibility callback after open/close without clearing the inspected pile when only the log changes.
- `src/main/java/com/haxerus/duelcraft/client/uitest/DuelPanelLayoutScenario.java`
  - Added all four panel states, long real log content, active prompt, long player hints, card-details coexistence, clickable log details, geometry invariants, independent real pixel offsets, non-bottom append preservation, and bottom-follow assertions.
- `src/main/java/com/haxerus/duelcraft/client/uitest/DuelFeedbackScenario.java`
  - Updated right-log placement and click expectations and explicitly brought the target chain row onscreen under bottom-follow behavior.
- `src/main/java/com/haxerus/duelcraft/client/uitest/DuelPlayerHintsScenario.java`
  - Updated ownership assertions for local details and the opponent-side stacked column.

## RED

Environment for all commands:

```powershell
$env:GRADLE_USER_HOME='C:\Users\haxer\.gradle'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.1.12-hotspot'
```

Command:

```powershell
.\gradlew.bat runClient -PldTest=duel_panel_layout -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain
```

Exit: `1`. Run `21912_3c070ade`: `0/1` scenarios, `1304/1321` checks, `17` failed checks. The failures proved the missing `#duel-right-column`, log/card-details exclusion, absent four-state geometry, initial/bottom append behavior, and card-bearing log click behavior. Full report and captures:

`build/evidence/qa-m4-follow-up/a3/red-scale2/`

An early compile attempt only exposed a removed scenario import and is not counted as behavioral RED.

## GREEN

Final scale 2 command:

```powershell
.\gradlew.bat runClient -PldTest=duel_panel_layout -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain
```

Exit: `0`. Run `38312_62818ab0`: `1/1` scenario, `1523/1523` checks, `195` steps. Evidence:

`build/evidence/qa-m4-follow-up/a3/green-scale2-final/`

Final scale 3 command:

```powershell
.\gradlew.bat runClient -PldTest=duel_panel_layout -PldTestWindow=1280x720 -PldTestGuiScale=3 --console=plain
```

Exit: `0`. Run `23840_39f5eba4`: `1/1` scenario, `1523/1523` checks, `195` steps. Evidence:

`build/evidence/qa-m4-follow-up/a3/green-scale3-final/`

Directly affected legacy scenarios:

```powershell
.\gradlew.bat runClient '-PldTest=duel_feedback,duel_player_hints' -PldTestWindow=1280x720 --console=plain
```

Exit: `0`. Run `35580_39f5eba4`: `2/2` scenarios, `485/485` checks, `119` steps. Evidence:

`build/evidence/qa-m4-follow-up/a3/green-affected-scenarios/`

The first legacy-pair iteration exited `1` at `483/485`: `duel_player_hints` passed, while two `duel_feedback` assertions still assumed the former top-start log and left-column dismissal sequence. The fixture was changed to assert the required bottom-follow state and scroll the card-bearing chain row into view; the rerun above is green. Likewise, an initial post-implementation scale-2 iteration reached `1472/1474`; both failures came from a stale card-details setup that relied on a much earlier hover. The scenario now sends a real `HINT_CARD` immediately before each coexistence checkpoint.

## Visual inspection

I inspected the neither, log-only, zone-only-scrolled, both-with-active-prompt, and log-click-with-details captures at both GUI scales. The right column remains inside the framebuffer and below the opponent hint; zone and log have an even split with no shared area; each fills the column alone; left card details, field, controls, and prompt remain usable. I also inspected the affected feedback and player-hint captures: the centered toast stays clear of the right log, local hints reserve the left details column, opponent hints reserve the right column, and clearing either side restores only that side.

## Compatibility and retained behavior

- A1's inspected-hand authorization revalidation remains in the existing tick path and was not altered.
- A2's prompt-owned ordered reveal queue, local read-only acknowledgement, and manually opened pile retention remain intact.
- Zone selection is retained while the log opens, closes, and reopens; the focused scenario asserts the same 60 inspected cards throughout those toggles.
- Field and prompt bounds are captured before panel changes and asserted unchanged with both right panels visible.

## Limitations and concerns

No unresolved A3 concern. Per the task split, the full duelcraft UI group and existing hand/rule-layout group are reserved for I1; this task ran only the A3 scenario at both required scales and the two directly affected legacy scenarios. Historical pre-self-review green evidence remains in `green-scale2/` and `green-scale3/`; the authoritative final evidence uses the `-final` directories above.

## Review fix 1: zone content rebuild offsets

Review found that the zone inspector restored its pixel offset after viewport resizing but not after `ZoneInspectorController.refresh()` cleared and repopulated its content. LDLib2 retained the normalized fraction while the container height changed, which moved the actual visible anchor.

Source commit `6404544` (`fix: preserve zone scroll across content rebuilds`) now captures the actual offset before clearing the open zone list. After the rebuilt content lays out, a zone-specific callback restores that offset through the existing clamp calculation. `zoneContentRevision` prevents an older delayed callback from overriding a newer rebuild; every callback still unregisters itself. The test explicitly queues two controller refreshes before layout, then allows the normal dirty-state refresh, so the final revision owns restoration.

### Review-fix RED

Command, with the same Java and Gradle environment recorded above:

```powershell
.\gradlew.bat runClient -PldTest=duel_panel_layout -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain 2>&1 | Tee-Object -FilePath build/evidence/qa-m4-follow-up/a3/review-fix1/red-scale2/console.log; exit $LASTEXITCODE
```

Exit: `1`. Run `11496_77e24d3c`: `0/1` scenarios, `1575/1577` checks, `216` steps. A saved offset of `7.0000005` drifted to `8.005748` after the first live content growth and to `9.011495` after the queued mutations. The shrink-to-fit clamp and every earlier A3 assertion passed. Full report, screenshots, and console stream:

`build/evidence/qa-m4-follow-up/a3/review-fix1/red-scale2/`

### Review-fix GREEN

Final command:

```powershell
.\gradlew.bat runClient -PldTest=duel_panel_layout -PldTestWindow=1280x720 -PldTestGuiScale=2 --console=plain 2>&1 | Tee-Object -FilePath build/evidence/qa-m4-follow-up/a3/review-fix1/green-scale2-final/console.log; exit $LASTEXITCODE
```

Exit: `0`. Run `30504_bb3487c`: `1/1` scenario, `1577/1577` checks, `216` steps. The first content growth preserved `7.0000005` exactly; rapid queued refreshes restored `7.000001`, within subpixel rounding; the short rebuilt pile clamped to zero. Full report, screenshots, and console stream:

`build/evidence/qa-m4-follow-up/a3/review-fix1/green-scale2-final/`

The final `right-panels-zone-content-refresh` capture was inspected and retains the existing field, controls, player hints, and right-column geometry. Scale 3 was not rerun for this review fix because no XML, sizing, or layout rule changed; the original final scale-3 run remains green at `1523/1523`.

The archived console streams expose the warning output omitted by `report.txt`. The final green stream contains the standard advanced-terminal warning, vanilla command ambiguities, union resource URL notices, placeholder example model/resource warnings, missing vanilla sounds, and known shader sampler/uniform warnings. None mentions layout dirtiness, the zone restore path, a scenario error, or a behavioral failure. The RED stream additionally prints Gradle's existing deprecation notice because that invocation failed. These are environment/resource noise; the final process ends with `BUILD SUCCESSFUL`.

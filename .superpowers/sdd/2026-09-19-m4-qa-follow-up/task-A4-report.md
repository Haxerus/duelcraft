# Task A4 report: face-down banish marker and Xyz material inspection

Status: **DONE**

## Scope and contracts

- Baseline: `3e2135fa864d6cc23be07ad43c32caa4aa134809`
- Implementation commit: `225423593f77e97cc61c22de414aa15ca2c79aa3`
- Documentation/evidence commit: the commit containing this report.
- Verified pinned sources: ygopro-core `122e0d091a0f399221a4510cc98a406ae485905f`; EDOPro `48ec006c49d7899b39f39420b9a71dea5ecdd22a`.
- No payload, protocol, native, dependency, Gradle, M5 or M6 change was made.

The engine order contract is now explicit in `docs/engine-wiki/queries.md`: `card::get_info_location()` places an overlay material's zero-based `current.sequence` in `location_info.position`, and `MSG_MOVE` writes that location structure for its endpoints. `ClientDuelState` therefore inserts an overlay destination at the clamped destination `position` and renumbers the list. This preserves the engine's order for attachment and overlay-to-overlay reorders instead of always appending.

## Changes

- Banished inspector tiles now render a localized `Face-down` marker from `ClientCard.isFaceDown()`. The marker is independent of identity/art: a known own card retains its art, an unknown opponent card remains a card back, and an unknown tile never exposes hover details.
- Field hosts with materials gain a read-only `Materials (N)` context action during normal browsing/waiting and explicit field inspection. Mandatory field selection receives the click first. Explicit inspection suppresses engine actions and leaves the active prompt intact.
- `ZoneInspectorController.openMaterials(ClientCard)` follows the exact host object, displays its live materials list in engine order, refreshes on monster/spell/pile/prompt mutations, and closes when that object no longer occupies a field slot. Material tiles do not dispatch field clicks or responses.
- Existing engine actions keep their list indices and response path. The material callback only opens the inspector; it never enters `ResponseBuilder`, `ResponseValidator`, or `sendResponse`.
- A3's `ZoneInspectorController.Callbacks.onVisibilityChanged()`, `restoreScrollAfterContentLayout(float)`, right-column split, and scroll restoration remain the only layout/visibility path used by material inspection.
- Focused real-pointer scenarios now cover zero/one/many/unknown materials, own/opponent hosts, no-action and combined summon/activate menus, mandatory selection priority, explicit inspection, detach/reorder/control change/host departure, both turn players, known/unknown banished identities, marker/art separation, hover privacy, menu containment, and zero response sends.

### Files

| File | Change |
|---|---|
| `src/main/java/com/haxerus/duelcraft/client/ClientDuelState.java` | Insert overlay moves at the engine destination index and renumber. |
| `src/main/java/com/haxerus/duelcraft/client/ClickDispatcher.java` | Selection-first routing plus read-only Materials action/callback. |
| `src/main/java/com/haxerus/duelcraft/client/ZoneInspectorController.java` | Live exact-host material inspector, read-only/privacy behavior, face-down marker. |
| `src/main/java/com/haxerus/duelcraft/client/LDLibDuelScreen.java` | Wire material inspection and refresh it through existing dirty/layout paths. |
| `src/main/resources/assets/duelcraft/ui/duel_screen.xml` | Marker and material-action styles. |
| `src/main/resources/assets/duelcraft/lang/en_us.json` | Localized `Face-down` and `Materials (%s)` captions. |
| `src/main/java/com/haxerus/duelcraft/client/uitest/DuelVisibilityFixture.java` | Capture both prompt and click-dispatch response paths. |
| `src/main/java/com/haxerus/duelcraft/client/uitest/DuelXyzMaterialsScenario.java` | Full material behavior, privacy, mutation, response, and bounds coverage. |
| `src/main/java/com/haxerus/duelcraft/client/uitest/DuelZoneInspectorActionsScenario.java` | Face-down banish coverage at both identities and turn players. |
| `src/test/java/com/haxerus/duelcraft/client/ClientDuelStateTest.java` | Destination-order regression and corrected sequential synthetic attachments. |
| `docs/engine-wiki/queries.md` | Source-backed overlay destination-index contract. |
| `.superpowers/sdd/2026-09-19-m4-qa-follow-up/task-A4-brief.md` | Detailed A4 checkboxes completed. |
| `docs/superpowers/plans/2026-09-19-m4-qa-follow-up.md` | Preserved the already-present A3 completion checkbox; A4 remains unchecked for parent review. |

## RED evidence

All evidence below is under `build/evidence/qa-m4-follow-up/a4/`; every directory contains the full console log and, for UI runs, the LDLib2 report and screenshots.

| Command | Exit/result | Evidence |
|---|---|---|
| `.\gradlew.bat test --tests '*ClientDuelStateTest' --console=plain` | Exit 1; 71 tests, 1 expected failure: `overlayMoveUsesTheEngineDestinationOrder` | `red-unit/` |
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials` | Exit 1; run `36432_4f1ea463`, ERROR after 26 steps, 101/103 checks passed before early abort. Expected checks found no visible menu and 0 rather than 1 material action; the next action lookup then failed because the absent feature supplied no element. | `red-xyz/` |
| `.\gradlew.bat runClient -PldTest=duel_zone_inspector_actions` | Exit 1; run `2848_39f5eba4`, ERROR after 13 steps, 3/5 checks passed before early abort. Expected checks found no face-down marker/text; the next marker lookup then failed because the absent feature supplied no element. | `red-zone/` |

The two UI RED reports intentionally stop at the first pointer action that depends on the missing element. Their check totals describe this early-abort boundary, not the final scenario size.

## GREEN evidence

| Command | Exit/result | Evidence |
|---|---|---|
| `.\gradlew.bat test --tests '*ClientDuelStateTest' --tests '*ResponseValidatorTest' --console=plain` | Exit 0; 133/133 tests, 0 failures/errors | `green-unit/` (final), `green-unit-pre-review/` (prior full pass) |
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials` | Exit 0; run `15772_39f5eba4`, PASS, 151/151 checks, 138 steps; default GUI scale 2, 1904x993 framebuffer | `green-xyz-exact/` |
| `.\gradlew.bat runClient -PldTest=duel_zone_inspector_actions` | Exit 0; run `13644_62818ab0`, PASS, 69/69 checks, 88 steps; default GUI scale 2, 1904x993 framebuffer | `green-zone-exact/` |
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials -PldTestGuiScale=2 -PldTestWindow=1280x720` | Exit 0; run `42636_62818ab0`, PASS, 151/151 checks, 138 steps | `green-xyz-scale2/` |
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials -PldTestGuiScale=3 -PldTestWindow=1280x720` | Exit 0; run `29092_62818ab0`, PASS, 151/151 checks, 138 steps | `green-xyz-scale3/` |
| `.\gradlew.bat runClient -PldTest=duel_zone_inspector_actions -PldTestGuiScale=2 -PldTestWindow=1280x720` | Exit 0; run `26548_3c070ade`, PASS, 69/69 checks, 88 steps | `green-zone-scale2/` |
| `.\gradlew.bat runClient -PldTest=duel_zone_inspector_actions -PldTestGuiScale=3 -PldTestWindow=1280x720` | Exit 0; run `42176_bb3487c`, PASS, 69/69 checks, 88 steps | `green-zone-scale3/` |

The first post-implementation unit attempt is preserved in `green-unit-attempt1/`: exit 1, 133 tests with one failure in the older `removeCardsRenumbersTheSurvivingMaterials` synthetic setup. That fixture attached all three materials to destination index 0, which now correctly reverses their order; it was corrected to model the engine's increasing destination indices. The new regression remained unchanged.

UI harness corrections are also preserved:

- `green-zone-scale2-attempt1/` (6/7 before error) compared logical bounds to a framebuffer-pixel threshold and used an ambiguous multi-card hover selector.
- `green-zone-scale2-attempt2/` (10/10 before error) used LDLib2's direct selector click, whose release re-resolved a now-hidden close button.
- `green-xyz-scale2-attempt1/` (111/112 before error) retained a delayed known-card hover banner and had the same hidden-button release problem.
- `green-xyz-scale2-attempt2/` (141/147) installed a replacement prompt and clicked in the same step before the prompt rebuild, so the intended inspector was not reopened for later mutation checks.
- `green-xyz-scale2-pre-menu-proof/` and `green-xyz-scale3-pre-menu-proof/` are passing 150/150 runs retained from before the final context-menu containment assertion/screenshot was added.

These were test timing, coordinate, or selector issues. The final runs use explicit pointer down/up with bounds captured before mutations, wait for prompt rebuilds, and measure readability in framebuffer pixels.

## Visual inspection

- At GUI scales 2 and 3, `20_known-face-down-banished.png` shows authorized art with a separate dark `Face-down` strip; `36_unknown-face-down-banished.png` shows the same marker over an anonymous back. The programmed marker assertion requires at least 20x5 framebuffer pixels and passes at both scales; the caption is visibly readable without covering the card identity area.
- `38_own-material-inspector.png` and `52_opponent-material-inspector.png` show ordered known/unknown material tiles in the A3 right column. Unknown material tiles remain backs and do not produce identity hover content.
- `73_materials-context-menu.png` at both scales shows the original summon/activate entries plus the turquoise material-count action. All three icons remain inside the duel canvas, and the material count is legible.
- `103_materials-during-explicit-inspection.png` shows the material inspector open while `Show Prompt` remains available, confirming the prompt is retained. `133_materials-after-detach-reorder-control-change.png` shows the live two-material order after mutation and host control change.
- No overlap or scroll regression was visible in the shared right-column inspector/log layout.

## Limitations and warnings

- The full grouped UI run is intentionally reserved for integration task I1; A4 ran only the required focused scenarios and focused unit suites.
- Minecraft startup continues to log pre-existing missing example block/item model, `card_back` atlas, sound, shader-uniform, and Gradle deprecation warnings. They do not fail `verifyUiTest` and are outside A4.
- Client startup loaded the existing card-data cache and refreshed `strings.conf`; scenario artwork and identity assertions use deterministic fixture textures and do not depend on card database/image contents.
- Evidence lives under ignored `build/evidence/qa-m4-follow-up/a4/` and is not part of the Git commits.

## Review fix 1: stale material hover identity

Review source: `.superpowers/sdd/2026-09-19-m4-qa-follow-up/task-A4-review.md`

Source commit: `eac5b15df97264e1044f87356568a33f66f380ce`

`ScrollerView.clearAllScrollViewChildren()` removes material tiles without emitting `MOUSE_LEAVE`. A known material hover had already canceled the delayed hide, so a live host query that replaced that material's code with zero could rebuild an anonymous tile while leaving the old identity visible. Closing the inspector or removing its host had the same stale-detail path.

`ZoneInspectorController` now keeps the exact `ClientCard` that supplied its current details through the normal 500 ms pointer-crossing grace. Before rebuilding or closing, it asks the screen to clear that source immediately. `LDLibDuelScreen` clears only when the supplied object still owns the displayed details. A later prompt/event detail has a different or null owner and remains visible. A1's stationary opponent-hand revocation now uses the same owner-aware immediate clear and retains its existing behavior.

### RED

| Command | Exit/result | Evidence |
|---|---|---|
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials` | Exit 1; run `32340_3c070ade`, FAIL, 160/163 checks, 166 steps. The only failures were the intended stale banner after owned close, stationary overlay-code redaction, and host departure. The unrelated `HINT_CARD` refresh/close control passed. | `build/evidence/qa-m4-follow-up/a4/review-fix1/red-xyz/` |

### GREEN

| Command | Exit/result | Evidence |
|---|---|---|
| `.\gradlew.bat runClient -PldTest=duel_xyz_materials` | Exit 0; run `37608_39f5eba4`, PASS, 163/163 checks, 166 steps. | `build/evidence/qa-m4-follow-up/a4/review-fix1/green-xyz/` |
| `.\gradlew.bat runClient -PldTest=duel_public_hand_hover` | Exit 0; run `31232_39f5eba4`, PASS, 9/9 checks, 31 steps. | `build/evidence/qa-m4-follow-up/a4/review-fix1/green-public-hand/` |
| `.\gradlew.bat runClient -PldTest=duel_zone_inspector_actions` | Exit 0; run `41868_4cb939d5`, PASS, 69/69 checks, 88 steps. | `build/evidence/qa-m4-follow-up/a4/review-fix1/green-zone/` |

No broader suite was run: this fix changes only card-detail ownership at the zone-inspector callback boundary, and the three directly affected scenarios are green. The grouped UI suite remains reserved for I1. The runs retain the previously documented model/texture, sound, shader, Gradle-deprecation, and unchecked fixture warnings.

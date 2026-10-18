# Gallery shared control inset — 2026-10-17

The user requested dropdown padding to match editable fields and a single source for this spacing. Starting point: clean `8b30c29`, branch `codex/ui-redesign`, existing deck-editor worktree.

The previous field-only rule was too narrow. LDLib Selector owns a display child with four-unit left padding, plus separate short-list and scrolling-popup containers with two-unit padding. Styling TextField alone could not reach those defaults.

`gallery.lss` now groups editable fields, Selector display children, short popup lists and scrolling popup viewports into one horizontal-inset declaration per design canvas (10 management, 7.5 duel). The selected text is inset once at its display, not at both display and preview. Its native explicit left edge is reset to auto so the shared horizontal inset wins. Generated dropdowns inherit the rule without per-screen overrides. Centered action captions and ordinary headings/prose keep their existing alignment and layout spacing; blanket-padding every nested text element would add spacing twice.

New native assertions compare actual selected-label and popup-label content origins to the intended shared inset. They cover component specimens on both canvases, collection browser controls, short/scrolled popup selections, import and lobby rules. The existing field checks remain in place.

## Evidence

The red specimen run `368928_6779959d` passed 329/345 checks: 16 expected selected-value and popup-row inset failures before the LSS change. Archive `build/gallery-proof/shared-inset-red/`, command log `shared-inset-red.log`.

Final verification and independent native image review follow. Production UI remains unchanged; Gallery LSS is loaded only by fixture XMLs. Full JUnit and unrelated production scenarios are not repeated for this small styling change.

The first integrated run (`shared-inset-mapping/`) exposed two LDLib mappings: the explicit display left edge remained 4 despite horizontal 10, and collection popups are reparented outside the inner canvas that previously held the Gallery class. The correction resets that left edge to auto and moves the collection theme class to its actual root (without duplicating it), so detached popups inherit the same rules. Lobby already scopes Gallery at its root; specimen popups already carry their canvas class. No controller or dependency patch is needed.

## Final verification

Both commands used `GRADLE_USER_HOME=C:/Users/haxer/.gradle`:

```powershell
./gradlew.bat runClient '-PldTest=gallery_specimen,gallery_collection,gallery_lobby' -PldTestWindow=1280x720 -PldTestGuiScale=2 -PskipNative
./gradlew.bat runClient '-PldTest=gallery_specimen,gallery_collection,gallery_lobby' -PldTestWindow=1024x768 -PldTestGuiScale=2 -PskipNative
```

| Window / GUI scale | Run | Result | Archive under `build/gallery-proof/` |
| --- | --- | --- | --- |
| 1280×720 / 2 | `343268_3d84f113` | 3/3 scenarios, 4361/4361 checks, exit 0 | `shared-inset-1280/` |
| 1024×768 / 2 | `357136_335a45a8` | 3/3 scenarios, 4361/4361 checks, exit 0 | `shared-inset-1024/` |

Logs are `shared-inset-green.log` and `shared-inset-small.log`. Final changed-source hashes in `shared-inset-source-hashes.json` were checked unchanged after both runs. Existing collection stress, keyboard/mouse input, popup selection, scrolling, live resize and component state checks remain passing. Generic shader/Gradle warnings remain unrelated exceptions.

Root inspected normal collection at both sizes, both import popup captures and the 1280 lobby rule popup. Independent review additionally opened collection filters, lobby Settings, and management/duel specimen dropdown captures. No actionable findings: text starts align, long import names fit, chevrons remain clear, and nested controls do not gain double padding. Production UI remains unchanged. Human aesthetic acceptance and production migration remain separate checkpoints.

Review: [collection](../../../build/gallery-proof/shared-inset-1280/screenshots/gallery_collection/29_gallery-collection-normal.png), [import popup](../../../build/gallery-proof/shared-inset-1280/screenshots/gallery_collection/508_gallery-collection-import-dropdown.png), [lobby rule popup](../../../build/gallery-proof/shared-inset-1280/screenshots/gallery_lobby/75_gallery-lobby-rule-popup.png).

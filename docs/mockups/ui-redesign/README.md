# UI direction review

Open `index.html` through a local HTTP server, for example from this worktree:

```powershell
python -m http.server 8770 --bind 127.0.0.1 --directory docs/mockups
```

Then visit <http://127.0.0.1:8770/ui-redesign/index.html>. The review page selects a direction, screen and canvas width. Each direction also opens independently with `?screen=home`, `lobby`, `collection` or `duel`.

These are **review prototypes**, not a proposed browser runtime for the mod. They make no network requests beyond local assets, do not save data, and do not contact the game. Some buttons demonstrate a local state change; others show a brief prototype notification. Such notifications are not a proposal to make ordinary in-game actions noisy.

## Review paths

- Compare all three Home screens, then compare their lobbies without changing the selected screen in the review toolbar.
- Select an opponent, preview an invitation and cancel it. Open Settings and its rule selector.
- Open My Collection. Inspect cards, search, open Filters, scroll My decks and open Import / export. Import uses a fictional list of files from the fixed `duelcraft/decks` folder, never an OS file picker.
- In Live duel, hide either right panel and restore it. Gallery also offers an explicit Expand/Restore control. Compare space for the field, inspected card, zone and log.
- Press Escape in an open management menu. Live-duel Escape currently opens the leave confirmation even over a prompt; prototype menu handling is not approval to change that production contract. Try the 960px canvas, noting that it scales the browser sketch rather than proving Minecraft text fit.

The browser, ownership and filter controls are representative; the final UI must retain category/subtype/properties, race, attributes, Level/Rank/Link, ATK/DEF/scale ranges, ownership, sorting and alternate formats. The current four-column browser uses fixed-row virtualization; a column-count or row-height change must update that model alongside its layout. These sketches do not validate rules, real inventory quantities, prompt legality or hidden-information routing.

Fonts are browser substitutes. The visual directions need Minecraft font, scale, popup and input tests in LDLib2 after selection. The [design brief](../../superpowers/specs/2026-10-17-ui-redesign-brief.md) and two linked audits record that next checkpoint.

## Preview assets

`assets/<passcode>.png` contains unmodified card images copied from the existing development client cache at `run-collection-m3-ui/duelcraft/cache/images/cards/`. `fixture.js` identifies the sample cards. `assets/card-back.png` is an unmodified copy of the existing mod resource. Featured Blue-Eyes artwork reuses `../featured-art/blue-eyes.png`; its provenance is in that directory's README. These assets serve documentation previews and are not added to the mod's resources. No assets were downloaded for this review.

No product source or dependency changes belong to this checkpoint. Carried-forward multiplayer playtests remain nonblocking and retain their own checklist.

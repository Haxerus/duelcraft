# M3 kickoff prompt

Updated 2026-09-19. Paste this into a fresh Duelcraft implementation session. This replaces the original M1 kickoff.

```text
Implement M3 of Duelcraft's collection/deck-editor integration: physical card items and authoritative inventory/collection transfers. Finish M3 first; keep M4–M6 as later milestone checkpoints.

Work directly in this existing worktree:
C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor
Branch: codex/player-collections. The latest implementation commit is b407f7b, followed by the handoff documentation commit. Check the current branch, HEAD and working tree before editing; preserve any later user changes. Do not start from main or the old deck-editor-m1 worktree.

Read AGENTS.md, then the current handoff and its linked contracts, ownership amendment, roadmap and M3 plan:
C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor/docs/superpowers/handoffs/2026-09-17-deck-editor-implementation.md

Executable plan:
C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor/docs/superpowers/plans/2026-09-17-card-transfers-milestone-3.md

M1, M2, the ownership-policy follow-up and editor polish are complete and user-approved. Preserve the existing dark duel-UI styling and the 1280x720 editor, including rich filters, Deck/Side routing, sorting, quiet empty inspector and centered feedback. Continue the approved design rather than repeating discovery.

Preserve the agreed policy: core SERVER requireCardOwnership defaults false; enabled ownership counts only deposited exact-passcode copies across Main/Extra/Side. Supported legality and optional companion denials still apply in either mode. Drafts can be saved without required cards. Transfers always require actual quantities, capacity, authoritative revisions and busy checks. Revalidate the active deck against proposed post-transfer state. A shortage alone clears activation only with ownership enabled; a companion denial can clear it under either setting. Keep the saved list. A failed policy evaluation leaves inventory and collection unchanged. Deposits never auto-activate or create copies. Acquisition/progression belongs to a companion mod.

Implement the four M3 tasks using the existing CollectionService and DeckUsePolicy. Follow the plan's tests for conservation, atomic transfer behavior, replay/stale requests, unsupported stacks, offhand rules, ownership modes and companion denial/failure. Run the real UI, restart and dedicated-player checks required by the plan; preserve earlier evidence and report any blocked checks accurately. Bump the current protocol if payload shapes become incompatible.

Use GRADLE_USER_HOME=C:/Users/haxer/.gradle. Keep task checkboxes current, commit scoped changes, and record verification results and manual playtest instructions. The handoff records the current 904-test and 226-UI-check baseline. Complete and verify M3 before starting M4. Do not merge or push without my request.
```

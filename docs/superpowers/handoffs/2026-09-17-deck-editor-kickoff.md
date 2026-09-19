> **Current policy, 2026-09-19:** Core `requireCardOwnership=false` plus an optional companion restriction hook supersedes unconditional ownership statements in this historical handoff. Original M1/M2 work is complete; the next prerequisite is the [M2 policy follow-up](../plans/2026-09-19-deck-use-policy-follow-up.md), then M3–M6. See the [amendment](2026-09-18-optional-ownership-amendment.md).

> **Historical M1 handoff — completed.** Use the implementation through `2030dae` in `codex/deck-editor-m1`, including the approved dark duel-UI styling. See [M1 results](../reports/2026-09-17-deck-editor-m1.md). Continue with [milestone 2](../plans/2026-09-17-player-collections-milestone-2.md); do not recreate the sample editor.

# Kickoff prompt

Paste the following into a new implementation session in the Duelcraft project:

```text
Implement milestone 1 of Duelcraft's collection/deck-editor integration: the approved 1280x720 LDLib2 editor with sample data.

Start by reading this handoff and the documents it links:
C:/Users/haxer/Documents/Programming/Modding/Duelcraft/docs/superpowers/handoffs/2026-09-17-deck-editor-implementation.md

The executable plan is docs/superpowers/plans/2026-09-15-deck-editor-milestone-1.md. Read AGENTS.md, the design spec, and the roadmap, and inspect the accepted HTML prototype before coding. The visual direction and product rules are settled; proceed with implementation rather than reopening general design questions. Resolve routine API details from the pinned source, and flag only material contradictions or genuinely blocking decisions.

Use an isolated implementation workspace and a codex/ branch. The handoff documents and prototype are currently uncommitted in the main checkout; preserve them in the new workspace before proceeding. Leave the existing user modification to docs/engine-gap-analysis.md untouched.

Complete all five milestone tasks, with meaningful model/search tests and the actual LDLib2 screen scenarios. Run the required Minecraft client checks and capture screenshots. Keep the existing duel UI unchanged. Use injected sample metadata, ownership, textures, and an in-memory save callback; clearly label the sample state. Do not implement persistence, real inventory transfers, activation, production hotkey/items, or lobbies yet.

Review the plan against current code, then carry the milestone through implementation and verification. Keep the plan's checkboxes current and report evidence for the completed work, any blocked checks, and how I can open the editor for playtesting. Do not proceed to milestone 2 in this session. Do not merge or push without my request.
```

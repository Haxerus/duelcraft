# M4 friend-playtest follow-up design

Status: user-approved direction, recorded for implementation planning on 2026-09-19. Implementation has not started.

## Scope and constraints

This follows the Windows friend playtest of `1.0.0-m4-qa.1`. Complete this QA follow-up before starting M5. M5 navigation and M6 remain separate milestone checkpoints. The temporary command interface remains available for this checkpoint.

- Work in `C:/Users/haxer/Documents/Programming/Modding/Duelcraft/.superpowers/worktrees/deck-editor`; planning baseline is branch `codex/qa-m4`, HEAD `1e95c277a1de7c8a014b77df1bd7df7744ee8365`. Recheck branch, HEAD and working tree before implementation; preserve subsequent user changes.
- Minecraft 1.21.1, NeoForge 21.1.224, Java 21, LDLib2 2.2.39.a, Windows x64. Use `GRADLE_USER_HOME=C:/Users/haxer/.gradle`.
- Preserve the dark duel UI, the 1280x720 collection editor, rich filters, Deck/Side routing, stable sorting, quiet empty inspector and centered feedback.
- Core SERVER `requireCardOwnership` defaults false. Enabled ownership counts deposited exact-passcode copies across Main/Extra/Side. Supported legality and companion restrictions apply in either mode. Saving drafts does not require eligibility. Keep authoritative revisions, authentication, busy checks and atomic transfers. Deposits never auto-activate or grant copies. Acquisition belongs to a companion mod.
- Protocol baseline is 7. Bump the current protocol only if payload shapes become incompatible. No native submodule or dependency upgrade is part of this work.
- Preserve historical M2/M3/M4 and QA.1 evidence. Report blocked runtime checks explicitly. Do not merge or push without a new user request.

## Duel visibility and selection

Treat the hidden-information report as a pipeline defect, not a Painful Choice exception. Trace native production, recipient sanitization, client state, and rendering. Painful Choice, an actual opponent Extra Deck viewing effect, and Ceremonial Bell are required real-engine regressions.

Selection candidates must display every identity the recipient is authorized to know, in the selection prompt's authoritative candidate order. Keep response indices tied to the original prompt, including duplicate passcodes, mixed zones and overlays. Never correlate reveal and selection lists by their displayed order or passcode alone. Unrevealed cards remain backs with no identifying hover text.

Selection reveals do not open the zone inspector. Put their identities directly into the choice prompt. Inspection-only reveals use a read-only reveal presentation owned by the prompt UI; they do not manufacture an engine response or replace an unanswered mandatory selection. A reveal followed by a selection in one received batch must not flash an unrelated inspector or leave two competing dialogs. If a reveal arrives before a later prompt, that later prompt supersedes the relevant read-only presentation without losing the revealed identities.

Keep temporary reveal knowledge, current public visibility and ordinary remembered identity distinct. Reveal tracking must follow the actual card while its identity remains trackable and be invalidated when shuffling or another engine event breaks that association. Public effects must update both artwork and hover behavior when they start and end. A cached code is not blanket permission to display an opponent's hidden hand or Extra Deck. Verify both recipients, redacted packets and rendered UI; client hiding cannot substitute for server redaction.

## Duel side panels

Keep card details in the existing left inspector. Move the duel log into the right column, below the zone inspector. When both right panels are open, use a fixed equal-height split initially; when one is hidden, the other fills the column. Both scroll independently. Preserve inspected selection and scroll when panels open, close or resize. Opening the log must not hide card details. Do not add a draggable divider in this checkpoint.

## Invitations and preparation

Distinguish duplicate invitations/flows from repeated notifications for one flow. Enforce one outstanding preparation flow per player and one initial RPS prompt per recipient per round. Publishing an updated state after the other player's choice must not repeat the prompt. A tie creates a new round and deliberately prompts both players again. Apply equivalent deduplication to incoming invitation and first-turn-choice announcements. Keep all authoritative state payloads and request acknowledgements.

## Collection editing

| Feedback | Required behavior |
|---|---|
| Three-copy limit | Manual additions stop at three exact-passcode copies total across Main/Extra/Side, through buttons and gestures. Existing/imported invalid drafts remain intact and editable; saving drafts stays permissive, activation stays authoritative. |
| Open active deck | A fresh editor loads the attached active saved list. No active list opens a new blank draft. Resuming a suspended editor preserves its draft. Delayed reads cannot replace newer edits or a newer selection. |
| Empty draft dismissal | A never-saved zero-card draft closes without a keep/discard prompt, including after adding then removing its cards. A saved deck emptied by editing still requires the ordinary dirty decision. Keep the ability to save an empty draft. |
| Mouse controls | Single left click inspects/selects. Double left click on a collection result adds exactly one card to the chosen Deck/Side route; Deck routes Main/Extra naturally. Right click on a deck tile removes that exact stored entry. Busy/pending guards and the copy limit apply to every route. |
| Save/list simplification | Use a primary current-deck name, Save action and one My decks management surface. Save updates the current UUID. New/Make a copy are explicit. Keep Rename, Import, Export and Delete accessible as secondary actions. Keep Use for duels and the Active badge distinct from Save; no implicit activation. |
| YDK export | Export .ydk explicitly writes a snapshot of the current Main/Extra/Side list to the client's `duelcraft/decks` directory. Preserve order, duplicates and supported draft content. Ask before replacing an existing file; show the resulting filename. Export neither saves server state nor activates a deck. |
| Close and Escape | Provide a visible Close button. Escape dismisses only the topmost dropdown, dialog or submenu; only an otherwise unobstructed editor closes. Consume the event. Top-level Escape and Close use the same dirty/pending-save policy. Child dialogs have visible dismissal controls. |

For the simplified save flow, New and Make a copy create editable drafts; Import loads a draft. Save is the explicit persistence action. Warn before replacing meaningful unsaved content, and preserve server validation and failure feedback. Existing compatibility import-and-activate commands retain their documented behavior.

## Banished cards and Xyz materials

Show a readable Face-down marker for face-down banished cards even when an authorized viewer can see their artwork. Unknown cards remain backs. Whose turn it is does not grant identity access.

Clicking a field card with materials offers a Materials (N) action alongside applicable summon/activate actions, using the existing context-menu visual language. It opens a read-only material list in the zone inspector, in engine order. Show only recipient-authorized identities. Refresh on detach or host changes and close or clear when the host leaves the field. A mandatory card-selection click must retain its selection meaning; inspection must never send an engine action or erase a pending response.

## Acceptance

Every feedback item requires a focused regression and real UI evidence. Hidden-information acceptance additionally requires real engine/script and dedicated two-recipient evidence. Preparation acceptance requires message counts and state assertions, not a quick visual impression. Finish with full automated checks, GUI scales 2/3, reconnect/restart and multiplayer policy regressions, then a separate QA.2 artifact and friend-playtest checklist. Record synthetic, native, dedicated and packaged evidence separately; an unavailable runtime check remains open.

Execution starts at [the QA follow-up plan](../plans/2026-09-19-m4-qa-follow-up.md).

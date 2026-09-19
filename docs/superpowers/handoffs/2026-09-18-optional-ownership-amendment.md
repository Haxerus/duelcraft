# Player interaction amendment: core ownership setting and companion restrictions

Date: 2026-09-18. Revised: 2026-09-19 after the user's final decision.

Status: M2 collection policy and editor implementation passed the [follow-up verification](../reports/2026-09-19-deck-use-policy-follow-up.md); final independent review remains before M3. M3–M6, including actual duel-route enforcement in M4, remain unfinished. This revision supersedes the earlier proposal in this document that made ownership enforcement exclusively a companion responsibility.

## Decision

Duelcraft provides physical cards, personal collections, transfers, saved lists, deckbuilding, legality checks, and authoritative duel preparation. It also provides a core server setting, `requireCardOwnership`, default `false`.

- Disabled: players can activate and use otherwise eligible deck lists without owning the cards.
- Enabled: players must own enough deposited copies to activate and use a list. This works without a companion mod.
- An optional companion restriction hook can deny additional deck use under either setting. It cannot override core legality, enabled ownership, authentication, or busy-state checks.

The companion supplies acquisition, packs, rewards, progression, and any additional restrictions it needs. It uses Duelcraft's collection rather than a second ownership ledger or a parallel duel-start flow. Owning a card and being permitted to use it in a progression stage are separate questions.

## Responsibilities

| Responsibility | Owner |
| --- | --- |
| Engine integration, supported legality, multiplayer authority | Duelcraft |
| Card identity, physical items, collection persistence and synchronization | Duelcraft |
| Deposits, withdrawals, conservation and capacity checks | Duelcraft |
| Saved lists, imports, editor, owned counts and missing-copy information | Duelcraft |
| Optional requirement to own deposited cards | Duelcraft server setting |
| Shared policy evaluation and enforcement of companion denials | Duelcraft |
| Acquisition, packs, rewards, progression, era/unlock restrictions | Companion |

## Player behavior

This table assumes no additional companion denial. Companion restrictions apply in both columns.

| Operation | Ownership disabled (default) | Ownership enabled |
| --- | --- | --- |
| Save/edit/duplicate/import a well-formed draft | Allowed without ownership or legality | Same |
| Activate a deck | Requires supported legality | Also requires deposited copies |
| Prepare/start a player duel, including human solo | Rechecks current legality and readiness | Also rechecks ownership |
| Withdraw while idle, creating a shortage | Preserves an otherwise eligible active list | Clears activation and explains shortage; saved list remains |
| Deposit cards | Updates actual counts; never auto-activates | Same |
| Withdraw cards absent from collection | Rejected | Rejected |

Unrestricted deck use never creates physical cards or credits collection counts. Transfers always preserve quantity, capacity, revision, privacy, and server-authority checks. Keep the preparation/live-duel locks on transfers and deck mutations. Do not add consumption or reservation.

When ownership is enabled, count deposited copies only, across Main + Extra + Side even for single duels, by exact passcode without substitutions. Lists may reference the same owned copies. Server AI decks remain server content without a player collection.

## Configuration and policy contract

Use a server-authoritative setting in dedicated SERVER configuration, default false. Client preferences cannot override it. Configuration changes require a server restart (integrated-world reopening for singleplayer); capture the effective setting for that server lifecycle. The companion must not silently enable it. A progression modpack can ship a server configuration with it enabled.

Final permission is supported legality AND configured ownership satisfied AND no companion denial. The hook is an additional restriction, not an allow override. No installed listener adds no restriction; an installed check that fails must not silently permit play. Return a generic evaluation failure privately and log the cause. A failed evaluation rejects a pending mutation without altering inventory or collection; an ordinary denial after a valid edit/transfer clears activation and preserves that successful mutation/list.

Pass the authenticated owner and immutable candidate list, deposited counts, and selected rule to the check. Evaluate proposed post-edit/post-withdrawal state before commit, not stale attachment counts. Checks run synchronously on the server thread and must have no mutation, consumption, reservation, or network side effects. Use the same evaluator for activation, active-list revalidation, invitation acceptance, final startup, commands, packets, and human solo play.

Revalidate persisted active IDs under the current setting/catalog/hooks when next used after restart. Recheck again before engine creation; neither an old activation nor a client claim grants permission. Removing a restriction does not auto-select a previously cleared list. Runtime addon hot-swapping, a policy-management UI, and a general addon framework remain outside scope.

## Editor and feedback

Keep the approved dark editor and recent playtest improvements. Preserve owned badges, filters, and missing-copy calculations in both modes. A shortage is neutral collection information when ownership is optional; it must not make a playable deck appear invalid or disable activation by itself.

Send the effective ownership setting and evaluated denial information from the server. Distinguish legality errors, enforced ownership shortages, and companion restrictions. Detailed deck/collection information remains private to the affected player. Other participants receive generic readiness status.

## Implementation order and affected documents

Original M1/M2 work and UI fixes remain reusable. Storage records do not need a migration for this policy change. Eligibility/report semantics, server context, network replies, UI feedback, and tests do need adjustment; old M2 evidence does not verify the amended policy.

1. Complete final review of the implemented [M2 policy follow-up](../plans/2026-09-19-deck-use-policy-follow-up.md) before physical transfers.
2. Continue the amended M3–M6 plans through shared policy enforcement on every duel route.
3. Validate settings off/on without the companion and a test restriction under both settings. Companion development is not a prerequisite.

Authoritative planning documents:

- [Player interaction design](../specs/2026-09-15-player-interaction-design.md)
- [Implementation contracts](../specs/2026-09-17-player-interaction-contracts.md)
- [Integration roadmap](../plans/2026-09-15-player-interaction-roadmap.md)
- [M2 baseline](../plans/2026-09-17-player-collections-milestone-2.md) and [implemented policy follow-up](../plans/2026-09-19-deck-use-policy-follow-up.md)
- [M3 transfers](../plans/2026-09-17-card-transfers-milestone-3.md)
- [M4 preparation](../plans/2026-09-17-duel-preparation-milestone-4.md)
- [M5 entry points](../plans/2026-09-17-player-entry-points-milestone-5.md)
- [M6 release validation](../plans/2026-09-17-interaction-release-milestone-6.md)

## Acceptance criteria

1. Default setting, no companion, empty collection: a known legal list saves, activates, and starts multiplayer and human solo duels. Inventory/counts remain unchanged.
2. Ownership enabled, no companion: the same list saves but activation/preparation fails with correct missing copies. Enough deposited copies permit use; carried copies alone do not.
3. An independent test companion restriction denies an otherwise eligible list under either setting. Companion approval cannot override core rejection. A throwing check never permits a duel or partially commits a mutation.
4. Withdrawal always requires actual copies and capacity. Shortages preserve an otherwise eligible active deck with ownership off and clear it with ownership on, preserving the saved list. Test the hook sees the planned reduced counts.
5. All modes preserve malformed-request, stale-revision, private-data, and busy-mutation protections. No draft save is rejected merely because the draft cannot be played.
6. Changes between invitation and acceptance trigger current checks. With enforcement off, a shortage alone cannot block acceptance. Companion eligibility is also rechecked immediately before engine creation.
7. Commands, packets, import/activation, home/binder/mat, solo, and multiplayer use the same policy. A local YDK file grants no copies and bypasses no enabled restriction.
8. UI communicates the server's effective ownership requirement and actual denial; neutral shortages never masquerade as blocking errors.
9. Restart with a changed setting or companion installation revalidates persisted selection; neither old activation nor removed restrictions create cards or auto-reactivate lists.
10. Existing persistence, privacy, conservation, locks, editor behavior, and engine regression coverage remain intact.

The follow-up implements `DeckUsePolicy` and the synchronous `DeckUseCheckEvent` adapter on `NeoForge.EVENT_BUS`; `deny(String)` retains the first denial. The contracts record the exact APIs and SERVER config paths. Acquisition balance, pack contents, recipes, quests, and progression design remain companion work. This amendment does not expand Duelcraft's built-in banlist or format support.

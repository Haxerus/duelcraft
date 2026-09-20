# M4 QA follow-up verification

## B1 preparation notifications

Implementation commit: `c950737 fix: announce preparation choices once per round`.

The reproduced defect was notification-only. Both existing and new cardinality tests kept one shared preparation flow and one completed start while the adapter repeated the waiting player's RPS prompt after the opponent submitted. The private `DuelManager` announcement key now contains only `flowId`, actionable `mode`, and `roundId`. Authoritative `PreparationStatePayload` delivery remains unconditional for online recipients. Terminal IDLE/DUEL publication, logout, and shutdown clear retained keys.

The focused RED was deliberately recreated after the initial observation by temporarily reverting only the production gate. It ran 63 tests and failed the two notification regressions: the waiting player received 2 prompts instead of 1, and the tie path produced 3 prompts instead of 2. Service cardinality stayed green. Restoring the gate made all 63 pass. The final five-suite selection passed 72 tests with no failures, errors, or skips. The final full suite passed 1,021 tests across 65 XML suites with no failures, errors, or skips.

Both real dedicated two-client runs passed 57/57 checks and 238 merged steps. Default and required ownership each proved one shared flow after repeated/crossed commands, recipient-specific state convergence after every hand, one RPS prompt per recipient per round, two total after the tie replay, one winner prompt only for A, cancellation/expiry/disconnect cleanup, and exactly one delegated native `setupDuel` call. Required mode seeded exact deposited passcodes for both players. The scenario clears chat at its start and attaches the current run's captured strings to each counted step.

Evidence:

- `build/evidence/qa-m4-follow-up/b1/unit-red/` — recreated RED log/XML.
- `build/evidence/qa-m4-follow-up/b1/unit-green/` — initial GREEN log/XML.
- `build/evidence/qa-m4-follow-up/b1/focused-final/` — 72-test final focused run.
- `build/evidence/qa-m4-follow-up/b1/mp-default-attempt1/` — harness-only dedicated-server classloading failure, zero behavior checks.
- `build/evidence/qa-m4-follow-up/b1/mp-default/` — default ownership, PASS 57/57.
- `build/evidence/qa-m4-follow-up/b1/mp-required/` — required ownership, PASS 57/57.
- `build/evidence/qa-m4-follow-up/b1/full-suite/` — 1,021-test final suite.

The first dedicated attempt loaded a client-only method reference while defining the scenario on the dedicated server. It is preserved but is not counted as behavior RED. Deferred client-segment lambdas fixed the harness wiring. In the required run, client A wrote its complete PASS report and then its child Gradle process exited 1 because `build/reports/problems/problems-report.html` disappeared during shutdown; the orchestrator merged all three complete reports, verified 57/57 checks, and the requested outer Gradle command exited successfully. Old text-chat buttons still resolve the current flow/round through command handlers; historical-ID binding is not claimed. Typed packet stale-ID rejection remains covered, and M5 UI replacement remains outside B1.

# Review triage - round 2

Run: player-device-tests-hardening   Base: 13432572   Reviewed head: c1de902f (incremental from 59da16aa)

Sources: review-edge-2.md (no findings), review-tests-2.md (no findings), review-codex-2.md (ran: review; clean). Antigravity: skipped, quota exhausted. Blind not re-run (incremental).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| - | all | - | - | no findings; the tests lens confirms the retry cannot hide a controller that reports A's failure for B (a counted attempt is judged without retry) and cannot pass silently (all attempts missed -> AssertionError) | itest-fix2-1..10.xml: 10/10, 4 tests each | - | triage-1 #1, #3 fixed (219d94f5); reopened #2 fixed (c1de902f) |

## Rejected codex claims

- awaitOnMain throws instead of retrying when the queue stays idle - a 5 s wait with no post is a real failure.
- Overlapping requests for one path share a latch - each attempt uses its own path.

## Next

- no fixes, go to judge (check.full before the judge: tests changed after qa); unproven lines: 5 (edge 2, tests 3)

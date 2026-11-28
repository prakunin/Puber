# Review triage - round 2

Run: watch-state-batch-in-progress   Base: 1beafa9c (incremental)   Reviewed head: 3318a62e

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-antigravity-2.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex | minor | `WatchStateDaoTest.kt:187-210` | The control case paces its commits by 200 ms but the atomicity test writes back to back, so the pair does not isolate the transaction as the only difference | Read both tests: the control has `delay(PAUSE_MILLIS)` between commits, the AC-4 test has none inside `run` | accept | Test strength only, round 2 minor; the production batching is proven by the JVM test `recordInProgress_runsTheWholeListInOneTransaction` (red on base). Listed for the owner at handover |

Round 1 fix #1 (control case, cf89461f) confirmed by edge and tests lenses; its box run is pending in qa.

## Rejected codex claims

- The control may miss both partial states on a busy device - unproven; the box run settles it.
- `cancel()` may drop an in-flight callback - does not hold: a 1 s settle precedes the cancel.
- Drive observers through a test-controlled query context - redesign beyond the ACs.

## Next

- No fixes; qa re-runs `WatchStateDaoTest` on the box (AC-4 and the control case), then judge; unproven lines: 1

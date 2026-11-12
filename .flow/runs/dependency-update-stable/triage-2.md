# Review triage - round 2

Run: dependency-update-stable   Base: 4db9c0d4   Reviewed head: 2d24287e (incremental c62b3ae1..2d24287e)

Sources: review-edge-2.md, review-codex-2.md (ran: review; adversarial not repeated for a docs and line-ending diff). Lens tests not re-run: no test changed.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 (major), codex (downgraded to minor) | major | evidence round 2 / AC-10; `DatabaseTransaction.kt:22` | Triage-1 #1 comes back: DAO suites pass on Room 3.0.3, but `withWriteTransaction` on a file-backed `PuberDatabase` never ran, so AC-10 stays NOT CHECKED | read `DatabaseTransaction.kt` (`transactions()` wraps `withWriteTransaction`); qa round 2 evidence confirms the suites call DAOs directly | accept (owner, standing instruction) | Same finding after its fix: escalated under the standing instruction; the recommended option (carry AC-10 to the gate as NOT CHECKED, partly covered by the DAO suites, missing path named) is recorded in state.md Decisions and the spec's Decisions; root cause of the base progress-save failure stays with `resume-progress-anomaly` |
| 2 | edge F2 | nit | plan :88 | Plan task C3 step text still says media3 1.11.0 -> 1.11.1 | read the plan; the blocker record below it states the outcome | accept | round-2 nit, too small for a run; the plan's Implementation record documents the gate-1 delta |

## Rejected codex claims

- none (codex confirmed no findings).

## Next

- No fixes left; go to judge. Unproven lines: 1 (.gitattributes pins only the wrapper scripts).

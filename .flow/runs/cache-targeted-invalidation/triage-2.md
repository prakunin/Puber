# Review triage - round 2

Run: cache-targeted-invalidation   Base: 40762767   Reviewed head: 525d68cd (incremental e4d5dba2..525d68cd); docs fix a5f79b4f reviewed by codex round 3

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-codex-3.md (ran: review, docs diff). Lens blind not re-run (incremental round). qa round 2 in qa-report.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | spec Traceability AC-6 unit row | `afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress` labelled P0 red but uses only base code | Test calls `repository.markStale(42)`, which exists at 40762767; holds | fix | fixed (a5f79b4f): test-design round 3 relabels P0 guard with its mutation; codex round 3 checked the row against the test |
| 2 | tests F2 | minor | same row | Row described interactor call and a progress field; test uses repository `markStale` and `title` | Read test lines 231-251; holds | fix | fixed (a5f79b4f) with #1 (docs only) |
| 3 | tests F3 | nit | row `clearedDuringBackgroundFlush_stillMarksAndRefreshes` | Row said scope cancel and `onCleared`; test only cancels the scope | Read test line 140; holds | fix | fixed (a5f79b4f) with #1 (docs only) |

## Rejected codex claims

- [P1] codex round 3: `PUBERBASE` logging in 5 main files breaks unit tests - those were qa's uncommitted measurement shim, absent from every commit in range; qa round 2 reverted them (`rg PUBERBASE app/src` empty, worktree clean).

## Next

- No blockers or majors left; go to judge. Unproven lines: 4 (edge 3, tests 1).

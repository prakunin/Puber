# Review triage - round 5

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: c4519fa4 (incremental 9910c26e..c4519fa4)

Sources: review-edge-5.md (no findings), review-tests-5.md (no findings), review-codex-5.md (ran: review, adversarial; clean), review-state-path-5.md (extra).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | state-path F1 (edge and tests unproven lines) | minor (regression introduced by c4519fa4) | `WatchStateRepository.kt:48, 188, 250` | The 1 s catch-up deadline is measured on the wall clock; an NTP step after boot can stretch or cut it | Read: the deadline uses the constructor `clock` = `System::currentTimeMillis` | fix: a monotonic window clock (`SystemClock.elapsedRealtime`), injectable for tests; `clock` stays for observedAt/updatedAt | fixed (aa19fb51) |

## Rejected codex claims

- Emission later than 1 s after a catch-up close during a walk - unproven latency; the change is delayed, not lost.

## Next

- Fix round 5: item 1 (a one-parameter change); then check.fast, lens edge + state-path + codex on the incremental diff. Unproven lines: 4 (edge 3, tests 1).

# Review triage - round 3

Run: session-revoked-pairing   Base: b53bf631   Reviewed head: 672d3618 (incremental from 3968eeaa); qa round 4 evidence at 8be284f2

Sources: review-edge-3.md (no findings), review-tests-3.md, review-codex-3.md (ran: review, adversarial), extras: review-antigravity-3.md (ran, clean), review-state-path-3.md, review-ux-3.md (no findings).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex C1 | minor | `docs/superpowers/evidence/...-device-r3.md` | Final root handler (831d6ccf) had no device run | qa round 4 ran M-1 on an APK rebuilt at 672d3618 (sha256 776218d8...), evidence 8be284f2 | fix | fixed (qa round 4, 8be284f2) |
| 2 | state-path F1 | minor | `core/session/SessionEndCleanup.kt:18,36`, `SessionEventBus.kt:38-44` | A replay that claims while a cancelled holder's clear has not yet returned skips the clears; the release comes too late | read run(): claim, clears, release in finally; overlap needs the old composition's clear to outlive the new handler's start; codex judged the same overlap unproven (cancellation resumes on Main before the new LaunchedEffect) | defer | new run session-end-cleanup-overlap (idea): a Mutex or a done flag on the pending end |
| 3 | tests F1 | minor | `core/session/SessionEndCleanup.kt:24-32` | No test that a throwing clear is logged and the next clear still runs | tests only pass non-throwing clears | accept | round 3 minor, test-only gap; listed for the owner |

## Rejected codex claims

- Replay overlapping a cancelled cleanup (P2) - codex's own reviewer rejected it as unproven; kept as triage #2 (deferred) because the state-path lens traces a storage-bound clear.
- clearAccountPrograms blocking routing behind a stalled refresh (high) - pre-existing order on base, bounded by the HTTP timeout; out of scope.

## Next

- No fixes; go to judge. Unproven lines: 3 (edge 2, tests 1).

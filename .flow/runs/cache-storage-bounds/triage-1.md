# Review triage - round 1

Run: cache-storage-bounds   Base: a22c03bc   Reviewed head: dfad8b7c

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial; verdict clean).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, edge F1 | minor | `data/di/modules.kt:79,116-120`, `PlaybackController.kt:417` | Lowering the cap does not shrink an existing 512 MB media cache; with the main player read-only only a warm-up write can trigger LRU eviction, so a films-only user keeps ~512 MB | Read: SimpleCache built with LeastRecentlyUsedCacheEvictor, no trim at init; main factory read-only. The device evidence shows a drop to 261300 KB at the first playback start, but the session played an episode with a next one, so the no-warm-up path is not shown | fix | fixed (9c5c5db9, a8fdd8d1) |
| 2 | edge F2 | minor | `PlaybackController.kt:202,418,594` | Field `dataSourceFactory` written, never read (the :488 name is a parameter) | grep: only assignments at 418 and 594 | fix | fixed (9c5c5db9, a8fdd8d1) |
| 3 | tests F1 | minor | `CachedPayloadSweeperTest.kt:57-66` | Cutoff boundary not pinned: a row at exactly now - HardCeiling is not asserted to survive | Read the test: rows at -1 and +1 only | fix | fixed (9c5c5db9, a8fdd8d1) |
| 4 | tests F2, codex (rejected observation) | minor | `PlaybackController.kt:627-666` | AC-1 read-only branch has no automated test; device run was 7 min, not 30 | No test constructs the factory choice; evidence says 7 min | fix (extract the write-sink choice and test both branches); the 7-min device run is listed for the owner at handover | fixed (9c5c5db9, a8fdd8d1) |
| 5 | tests F3 | nit | `CachedPayloadSweeper.kt:49` | `drop(1)` is redundant with `filter { !it }` on a flow starting at true; test design overstates the guard | Read: AppForegroundState starts true | fix (cheap: keep the code, correct the comment to say drop(1) is defensive and not observable) | fixed (9c5c5db9, a8fdd8d1) |
| 6 | tests F4 | nit | plan Implementation record | Instrumented P0 tests not stated compile-red on base; Coil commit hash stale (bbf13622 vs ae127475) | git log | fix | fixed (9c5c5db9, a8fdd8d1) |

## Rejected codex claims

- AC-1 evidence 7 min, not 30 - an evidence-coverage remark, not a defect; covered by row 4 (test added) and listed for the owner.
- PSS increase after the Home scroll - single samples on a loaded emulator; no code path ties it to this diff (the Coil cap is the only memory change, and 48 MiB is near the old 15% share on this AVD). Listed for the owner as a manual judgement.

## Next

- Fix round 1 (rows 1-6) by the implementer; then check.fast, lens edge and codex on the incremental diff, lens tests (tests change), qa on AC-1/AC-3 (runtime change: media cache trim). Unproven lines: 4 (blind 2, edge 2), counted, not triaged; the Coil 48 MB vs low largeMemoryClass one is listed for the owner.

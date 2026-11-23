# Review triage - round 3

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: c55c5d3c (incremental 6f9a07da..c55c5d3c)

Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran: review, adversarial), review-state-path-3.md (extra).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | state-path F1 | minor | `WatchStateRepository.kt:243`, `MainVM.kt:101-104` | A catch-up closing just before a walk opens (cold start with a stale index) leaves its in-flight emission to be held for the whole walk | Read: `catchUpEmissionPending = openWalkWindows > 0`; MainVM awaits `catchUp` then `syncIfStale` in one coroutine | fix (round 3 minor, taken because it is a one-line change on the AC-9 startup path the catch-up exists for) | fixed (9910c26e) |
| 2 | tests F1 | minor | `WatchStateSyncInteractor.kt:228, 244, 326, 330` | No test pins which window kind the interactor opens | Relaxed mock, no `verify` on the window calls | fix (cheap; guards the triage-2 fix) | fixed (9910c26e) |
| 3 | edge F1, tests F2 | minor/nit | `WatchStateRepository.kt:592-601` | `SyncWindowKind` inserted between `WatchStateSyncCursor` and its KDoc | Read | fix (move the enum) | fixed (9910c26e) |

## Rejected codex claims

- Intermediate snapshot consuming `catchUpEmissionPending` - unproven ordering (Room re-queries after each commit); accepted heuristic limit documented in the KDoc, worst case a delay bounded by one walk.

## Next

- Fix round 3: items 1-3; then check.fast, lens edge + state-path + codex on the incremental diff, lens tests. Unproven lines: 1 (tests). 

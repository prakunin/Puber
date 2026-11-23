# Review triage - round 2

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: 6f9a07da (incremental c7df62a8..6f9a07da)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-state-path-2.md (extra).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, state-path F1, codex minor 2 | minor (regression from fix round 1) | `WatchStateRepository.kt:174-185, 215-230` | Window ownership is decided by nesting depth: with the catch-up window outer and a walk inner, or with a late last emission after a nested close, the catch-up's change waits for the walk's end | Read `onIndexChanged`/`endSyncWindow`: `openSyncWindows > 1` stands for "the nested catch-up"; a catch-up opened first writes into `syncChangeHeld` | fix: regression introduced by fix round 1 on the AC-9 path; one consolidated change (per-kind window flag or token; the catch-up's close publishes its own held change plus a late emission whatever the open order); test opening the catch-up window first | fixed (c55c5d3c) |
| 2 | codex minor 1 | minor | `WatchStateSyncInteractor.kt:458-462` | A catch-up across logout + re-login wipes the new session's rows via `sessionSurvived -> repository.clear()` | Read: the catch-up mutex is separate from the walk's, so a new session's walk can write before the old catch-up checks its generation | defer | new run catchup-session-wipe-race |

## Rejected codex claims

- AC-9 NOT CHECKED - process gap, carried to the gate as NOT CHECKED.

## Next

- Fix round 2: item 1 by the implementer; then check.fast, lens edge + state-path + codex on the incremental diff, lens tests (tests change). Unproven lines: 8 (edge 3, tests 2, codex 0, state-path 0, plus 3 dropped by state-path itself) - not triaged.

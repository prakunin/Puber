# Review triage - round 1

Run: cache-targeted-invalidation   Base: 40762767   Reviewed head: e4d5dba2

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind | minor (conductor: real regression) | `PlayerVM.kt:1490-1504,1974-1982,2035` | Owed stale mark and Home refresh are dropped when the VM is cleared while the background flush waits on `awaitPendingMutations` (Home, then reopen from the launcher row -> `newRootScreens`); base had already marked on each save | Read `pauseForBackground`: flush runs in a `viewModelScope` `launch` after `awaitPendingMutations`; `onCleared` cancels jobs and does not flush; holds | fix | fixed (2eb4cab3, renamed a0fa2468): NonCancellable flush; `clearedDuringBackgroundFlush_stillMarksAndRefreshes`; device: qa round 2, 4 of 4 runs marked |
| 2 | tests F1, codex | minor | qa-report AC-6; spec Manual checks M1/M2 | AC-6 outcome (Details shows the saved progress) has no test and the device check is NOT CHECKED | qa-report AC-6 NOT CHECKED; no Details-level test after `markItemDetailsStale` | fix | fixed (2eb4cab3): `afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress` (guard); device check stays NOT CHECKED |
| 3 | tests F2 | minor | spec Traceability `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing`; test body | Row says P0 red but ran green on base; its never-started-VM half cannot fail | Implementation record says green on base; read the test | fix | fixed: test-design round 2 relabel; 2eb4cab3 drops the never-started half and asserts router.back |
| 4 | tests F3 | minor | `HistoryVMTest.kt:609` | check.full flaked once on an unrelated wall-clock test | Diff does not touch HistoryVM; matches the unit-suite-flaky pattern | reject | not this branch; recorded in `unit-suite-flaky` Log (2026-11-22) |

## Rejected codex claims

- none (codex's only item is #2, a verification gap, taken as fix)

## Next

- Fix round 1: implementer for #1, #2, #3 (test part); test-designer for #3 (row label). Then check.fast, lens edge + tests + codex on the incremental diff, qa on AC-2/AC-3/AC-4 for #1 (runtime code changes). Unproven lines: 5 (blind 2, edge 2, tests 1).

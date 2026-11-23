# Review triage - round 1

Run: watch-index-catch-up   Base: e4c3b5aa   Reviewed head: c7df62a8

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-state-path-1.md (extra).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, state-path F1, codex | major | `WatchStateRepository.kt:195-203`, `WatchStateSyncInteractor.kt` `runCatchUp` finally | A catch-up nested in a walk's window closes its window before Room emits its rows; the late emission is held until the walk ends, so AC-9 fails during a parked walk | Read `onIndexChanged`/`endSyncWindow`: `syncChangeHeld` is set only from the snapshot collector; `runCatchUp` calls `endSyncWindow` right after its writes with no wait; the unit test relies on an eager fake | fix | fixed (1bafea56) |
| 2 | state-path F2 | minor | `WatchStateRepository.kt:195-203` | Closing the nested catch-up window pays out the walk's held changes even when the catch-up changed nothing (against AC-7) | `syncChangeHeld` is one shared flag, cleared and published by any `endSyncWindow` | fix (same mechanism as 1) | fixed (1bafea56) |
| 3 | codex, edge F2, state-path F3, blind F1 | minor | `WatchStateSyncInteractor.kt` `refreshSeriesThatLeftTheList` | Id added to `detailsCheckedIds` before the refresh; a failed refresh, or a series that re-enters and leaves the list, is never checked again in the process | Read: `detailsCheckedIds += id` precedes `runCatchingCancellable { refresh }`; cleared only in `invalidate()` | fix: add only on success; drop ids present in `stillListed` at the start of each run | fixed (a02e3e38) |
| 4 | blind F2 | minor | `readHistoryNewerThan` `caughtUp` | Stops only when every entry on a page is old, so a catch-up with anything new always reads one extra page | Under the newest-first order the stop rule already assumes, a page with any entry <= mark holds the boundary | fix: `any { <= mark }`, keep the total exit | fixed (a02e3e38) |
| 5 | blind F3 (edge unproven) | minor | `readHistoryNewerThan` `while (true)` | No page cap and no check that `pagination.current` advances | Read: unbounded loop; `page = current + 1` with no progress check | fix: stop when `current < page`; cap pages per catch-up; at the cap write the rows read but do not raise the mark, leaving the rest to the next catch-up/walk | fixed (a02e3e38; cap 1000 pages, mark kept at cap) |
| 6 | blind F4 | minor | `MainVM.kt:92-107` | Cold start with a stale index fetches the watching lists and history page 1 twice (catch-up then walk) | Holds: catch-up then `syncIfStale` run back to back | reject | The catch-up runs first on purpose (Q1, design: the user must not wait for a minutes-long walk, and only the catch-up refreshes series that left the list, AC-4). The cost is about 3 requests once per cold start more than 1 h after the last complete sync, inside the startup slot that already serves the walk |
| 7 | tests F1 | major | `WatchStateSyncInteractorTest` `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks` | The AC-2 test passes even if the catch-up takes the walk's mutex, because runTest advances virtual time past the walk's pause | Read: asserts only the return value and page-1 count | fix: assert virtual time unchanged and page 16 not read when `catchUp()` returns | fixed (a02e3e38; mutation run recorded) |
| 8 | tests F2 | major | spec Traceability rows SI `catchUp_sendsNothingOverAnIndexWithNoMark`, MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` | P0 `red` rows that were green on base; no mutation evidence | Implementation record says green on base | fix: relabel both P0 `guard` in the spec (Decision line), record one mutation run each (remove the no-mark return; move the catch-up before `awaitSlot`) | fixed (559eae4f; rows relabelled guard, mutation runs recorded) |
| 9 | tests F3 | minor | plan Implementation record, Task 4 | Red runs failed on the stub's return value, not on the defect assertion | Record text | fix: mutation runs for the P0 SI rows of AC-3 and AC-7 against the real `catchUp` | fixed (559eae4f; mutation runs recorded) |

## Rejected codex claims

- "Complete AC-8 ordering evidence and AC-9 device verification" - a process note, not a code defect (already rejected by the codex-reviewer itself); AC-8 and AC-9 are with qa.

## Next

- Fix round: items 1-5 and 7-9 by the implementer (fix mode), then check.fast, lens edge + codex on the incremental diff, lens tests (tests change), state-path (window logic), and qa on AC-2, AC-4, AC-7, AC-9. Started after the running qa verify round finishes, since both use the same worktree. Unproven lines: 9 (blind 3, edge 4, tests 1, state-path 0, codex 0) - not triaged.

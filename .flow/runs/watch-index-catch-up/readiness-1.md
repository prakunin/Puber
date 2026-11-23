# Readiness 1: watch-index-catch-up

Verdict: **NOT READY**

Base e4c3b5aa; spec at 5f0e92c3, plan uncommitted in the worktree; test design `.flow/runs/watch-index-catch-up/test-design.md`.

## Fix list

1. **Task 2 leaves the unit-test build red.** `WatchStateSyncDao` is an interface (`WatchStateSyncDao.kt:9`) and its only fake, `FakeWatchStateSyncDao` (`WatchStateRepositoryTest.kt:975-990`), gains `raiseHistoryNewestSeen` only in Task 3. After Task 2 the test source set does not compile, so Task 2's own Done-when (`WatchStateSyncInteractorTest` green) cannot hold. Move the fake's `raiseHistoryNewestSeen` into Task 2's Files.
2. **The `catchUp` Lock step can lose an unfloored request.** Holder: `do { rerunRequested.set(false); runCatchUp() } while (rerunRequested.get())`, then unlock. A player-exit request that sets the flag after the holder's last `get()` and retries `tryLock()` before the unlock fails both times and returns false; nobody runs it. `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun` does not reach that window. Fix the contract: after `unlock()` the holder re-checks `rerunRequested` and, if set, tries the lock again (loop until the flag is clear or the lock is taken by another caller, which then owns the rerun).
3. **Spec text disagrees with the resolved plan.** (a) Spec AC-5 still names `ContentListInteractorTest`; Q4 moved tab entry to `MainVMWatchStateSyncTest` and no plan task touches `ContentListInteractorTest`. (b) Spec `#manual-checks` Manual 1 reads `/v1/history?page=N&perpage=50` with the box session; the plan (and the conductor's resolved question) runs it as Settings > Rebuild plus the `history order page=` log line. qa reads both; align the spec to the plan and log the change in spec Decisions.

## Checks

| Check | Result |
|---|---|
| AC -> task -> Test design row | AC-1..AC-7 each map to a Task (Coverage table) and named Test design rows; AC-8 Task 2 + Manual 1; AC-9 Manual 2; AC-10 Task 6 full gate. Pass |
| Citations (sampled at e4c3b5aa) | All resolve: `HistoryRequest.kt:16` `page`; `HistoryApiTransport.kt:12-15` `fetchHistoryPage`; `KinoPubApiClient.kt:403-406` `getHistoryData`; `WatchStateSyncDao.kt:17-18` `upsert`; `LegacyPayloadCleanup.kt:12-18`; `PuberDatabase.kt:13`, `:18-21`, `:34-43`; `WatchStateSyncInteractor.kt:43`, `:61` `mutex`, `:69` `generation`, `:175` `@Suppress("ReturnCount")`, `:259-264` `isRunDue`, `:271-278` `invalidate`, `:288` `sessionSurvived`, `:374` `getHistoryData(page)`, `:479` `fetch`, `:482` companion, `:509` `lastSeenOrZero`; `WatchStateRepository.kt:193` `endSyncWindow`, `:246` `lastWatchedAt`, `:292-304` `recordFromHistory`, `:364` `currentGeneration`, `:386-394` `recordHistoryPage`; `domain/di/modules.kt:65-71`; `data/di/modules.kt:99`; `ItemDetailsRepository.kt:80` `refresh`; `MainVM.kt:88-98`, `:122-127`, `:191`; `PlayerInteractor.kt:64-68`; `PlayerScreen.kt:50`; `PlayerVM.kt:2034-2035`; `PuberApp.kt:103-106`; `HistoryVM.kt:111-113` |
| Build order | Tasks 1-6 ordered by Consumes/Produces; constructor changes land with their tests (SI Task 4, `PlayerInteractorTest.kt:77` Task 6); only other constructions are DI. Fails at Task 2 (fix 1) |
| P0 rows | Every automated P0 row is `red` or `guard`, names the defect it catches and a backticked test name; AC-8/AC-9 P0 rows are manual by nature |
| Scope | Plan Out of scope matches spec; HistoryVM untouched (Q5) |
| Blocking questions | Q2 answered; Q1, Q3-Q5 answered |

## Notes

- AC-7 "no row is written" is looser than the plan: each catch-up re-runs `recordFromServer` and `recordInProgress`, which advance `updated_at` (`WatchStateDao.updateInProgress` has no equality guard). `settledChanges` stays silent because `WatchState` (`WatchStateRepository.kt:407-415`) carries no `updated_at` and `snapshot` is a `StateFlow`; the WR guard row covers that.
- Rebuild for Manual 1 starts at page 1 (`withNewPass` sets `historyResumePage = 1`, `WatchStateSyncInteractor.kt:326`) and pages 1-3 fall in the first 15-page chunk, so the log line covers AC-8.

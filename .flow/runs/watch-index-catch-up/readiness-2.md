# Readiness 2: watch-index-catch-up

Verdict: **READY**

Base e4c3b5aa; spec at 5f0e92c3 plus uncommitted alignment edits; plan uncommitted in the worktree; Test design is the spec's section.

## Round-1 fix list

| # | Fix | Status |
|---|---|---|
| 1 | Fake `raiseHistoryNewestSeen` lands with the DAO change | Done: plan Task 2 Files modify `FakeWatchStateSyncDao` (`WatchStateRepositoryTest.kt:975-990`); Task 3 consumes it |
| 2 | Lost unfloored request in the release window | Done: Task 4 contract adds the Release re-check after `unlock()`; `catchUpMutex` is an injectable `Mutex` ctor parameter; test phase (b) wraps `unlock` and launches `catchUp(false)` UNDISPATCHED before the real unlock, which fails without the re-check. Spec Decisions and Test design row updated |
| 3 | Spec vs plan (AC-5 test file, Manual 1 method) | Done: AC-5 names `MainVMWatchStateSyncTest`; Manual 1 uses Settings > Rebuild plus the `history order page=` line; both logged in spec Decisions |

## Checks

| Check | Result |
|---|---|
| AC -> task -> Test design row | AC-1..AC-10 in the plan Coverage table with named rows; AC-8/AC-9 manual by nature. Pass |
| Citations (sampled at e4c3b5aa) | Resolve: `WatchStateRepositoryTest.kt:975` `FakeWatchStateSyncDao`; `PuberDatabaseMigrationTest.kt:38` `autoMigrationSpecs`, `:58-62` `watch_state_sync` INSERT; `WatchStateDaoTest.kt:35-42` `setUp`; `WatchStateSyncInteractorTest.kt:53`, `:412`, `:552`, `:573`, `:646`, `:826` constructions, `:836-850` `stubHistory`; `MainVMWatchStateSyncTest.kt:84` slot advance, `:145` `vm(`; `PlayerVMInvalidationTest.kt:44-57`; `PlayerVMTestFixture.kt:86` relaxUnitFun; `KinoPubApiClientHistoryTest.kt:59`; `ItemDetailsRepositoryTest.kt:24-32`; `WatchStateRepository.kt:193-205` `endSyncWindow`, `:376` `syncCursor`, `:378` `saveSyncCursor`, `:386-394` `recordHistoryPage`, `:552` `toEntity`; `WatchStateSyncInteractor.kt:58` `awaitForeground`, `:121-129` `requestSync` launch, `:271-278` `invalidate`, `:374` `getHistoryData(page)`; `PlayerVM.kt:2031-2036` `closeJob` |
| Build order | Tasks 1-6 ordered by Consumes/Produces; every interface and ctor change lands with its fakes and constructions. Pass |
| P0 rows | Every automated P0 row is `red` or `guard`, names the defect, and names a backticked test; AC-8/AC-9 rows are manual by nature. Pass |
| Scope | Plan Out of scope matches spec; `HistoryVM` untouched (Q5). Pass |
| Blocking questions | Q2 answered; Q1, Q3-Q5 answered. Pass |

## Notes

- Phase (b) delegates `Mutex` (`object : Mutex by real`); the delegated deprecated `onLock` gives a warning at most (`app/build.gradle.kts:86` `warningsAsErrors = false`).
- Round-1 note on AC-7 wording (`updated_at` advances on re-recorded list rows) stands; the WR guard row covers the observable contract.

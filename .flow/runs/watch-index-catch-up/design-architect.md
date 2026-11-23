# Design (architect): watch-index-catch-up

Base e4c3b5aa. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the catch-up lives | New `catchUp(respectFloor)` and `requestCatchUp(respectFloor)` on `WatchStateSyncInteractor` (`domain/interactor/watchstate/WatchStateSyncInteractor.kt:43`), beside `syncIfStale` (`:81`) and `requestSync` (`:113`) | The interactor is an app-wide single (`domain/di/modules.kt:66`) with its own `requestScope` (`:51`), so a catch-up asked for by a closing player outlives the PlayerVM |
| Locking against a running walk (AC-2) | Own `catchUpMutex` taken with `tryLock`; the walk's `mutex` (`:61`) is not touched | A walk holds `mutex` for minutes, parked included (`:96`, `:429-433`); waiting on it is the defect |
| What a catch-up writes to the cursor | Only `history_newest_seen`, raised with `MAX` by a new `WatchStateSyncDao.raiseHistoryNewestSeen` in the same transaction as its rows | `historyResumePage`, `fullHistoryWalkDone`, `lastSyncAt`, `generation` stay the walk's (AC-2) |
| Walk overwriting a mark the catch-up raised | `WatchStateRepository.saveSyncCursor` (`data/repository/WatchStateRepository.kt:378`) keeps `historyNewestSeen = max(stored, given)` inside `transaction.run` | The walk saves its whole in-memory cursor (`WatchStateSyncInteractor.kt:184`, `:240`, `:365` via `WatchStateRepository.kt:386-394`) and would lower the mark; no path lowers it on purpose (`clear()` deletes the row) |
| Where the catch-up stops (AC-1, AC-3) | Page 1 onwards until the first non-empty page whose entries all have `last_seen <= mark`, or the last page; no page budget, no chunk pause | Same stop rule as `advancedOver` (`:443-477`), minus `MAX_HISTORY_PAGES_PER_RUN` (`:491`) and `holdBeforeNextChunk` (`:429`) |
| When the catch-up writes history | Once, after the stop page: entries with `last_seen > mark`, plus the mark raise, in one transaction. A failed page writes no history and leaves the mark | Writing per chunk would advance the mark past unread pages (AC-3); writing only newer entries keeps a no-news run silent (AC-7) |
| Catch-up on an index with no mark (`historyNewestSeen == 0`) | Returns without requests; `syncIfStale` owns the first walk | With no mark the catch-up would be an unbounded full walk |
| Watching lists in the catch-up | `watching/serials?subscribed=0` required (no list, no run); `watching/movies` optional. Both recorded after history, as `indexAccount` does (`:191-221`) | The serials list decides series verdicts (`WatchStateRepository.kt:450-475`); the catch-up never prunes, so it does not need both |
| Generation of catch-up rows (AC-6) | History rows go through `recordFromHistory` (`WatchStateRepository.kt:292`) inside the transaction, so `currentGeneration()` (`:364`) is read under the write lock; list and Details rows use the existing `recordFromServer`/`recordInProgress` (`:258`, `:270`) | A pass opened by a walk cannot slip between the generation read and the history write |
| Logout mid-run (AC-6) | `sessionSurvived(generation)` (`WatchStateSyncInteractor.kt:288`) after every write, as the walk does | Same guard, same wipe |
| Series that left the list (AC-4) | After the history write: series rows in `snapshot` (`WatchStateRepository.kt:53`) with `isFullyWatched == false`, absent from the fresh serials list, with play evidence (`watchedEpisodes > 0` or a `lastWatchedAt()` entry, `:246`), not checked yet in this process; newest `lastWatchedAt` first, at most 10 per run; each through `ItemDetailsRepository.refresh` (`data/repository/ItemDetailsRepository.kt:80`), failures skipped | `refresh` forces the network and records the fresh Details via `observeItemDetails` (`:17-28`); the play-evidence filter excludes series whose Details were only browsed (`watched == 0`), which would otherwise all qualify; the per-process set and the cap bound the request count |
| Settling the catch-up's writes (AC-7, AC-9) | The catch-up opens `beginSyncWindow`/`endSyncWindow` (`WatchStateRepository.kt:182`, `:193`); `endSyncWindow` pays out a held tick on every close, not only when the last window closes | Nested inside a walk's window, the catch-up's change would otherwise stay held until the walk ends, minutes later |
| Floor (AC-5) | `catchUpFloor = 60.seconds` constructor parameter, in-memory `lastCatchUpAt` stamped when a run starts, cleared by `invalidate()` (`:271`). `respectFloor = false` skips it | Owner decision Q1 |
| Request while a catch-up runs | Dropped when floored; a no-floor request sets `rerunRequested` and the holder runs once more before releasing the lock | A player exit after a catch-up started must still see its own history entry |
| Foreground | `awaitForeground()` once before the first request; no pause between pages | Matches `runSync` (`:144`); a catch-up is a few requests |
| Page size (state.md Q3) | `HistoryRequest.page(page, perPage: Int? = null)` (`data/api/history/HistoryRequest.kt:16`) adds `perpage`; `fetchHistoryPage` (`data/api/history/HistoryApiTransport.kt:12`) and `KinoPubApiClient.getHistoryData` (`data/api/KinoPubApiClient.kt:403`) pass it through; the walk and the catch-up send 50 | History screen, `RecentlyPlayedOrder`, `ContinueWatchingSource` keep the default |
| A full walk resumed across the page-size change | `PuberDatabase` version 4 -> 5 (`data/db/PuberDatabase.kt:13`) with `AutoMigration(from = 4, to = 5, spec = HistoryPageSizeMigration)`, whose `onPostMigrate` runs `UPDATE watch_state_sync SET history_resume_page = ((history_resume_page - 1) * 20) / 50 + 1 WHERE full_history_walk_done = 0` | A resume page counted in 20s, read in 50s, skips entries, and a reconcile that then completes prunes rows it never restamped (`WatchStateSyncInteractor.kt:228-229`). Same pattern as `LegacyPayloadCleanup` (`data/db/LegacyPayloadCleanup.kt:12`) |
| Start and resume (AC-5) | `MainVM.syncWatchState` (`ui/feature/main/vm/MainVM.kt:88`): the first run awaits `StartupSlot.WatchState` as now, then `catchUp(respectFloor = true)`, then `syncIfStale()`. Once the slot is served, every call first does `requestCatchUp(respectFloor = true)`, then the existing job logic | The start catch-up keeps the slot; a resume during a long walk job (dropped at `:89`) still gets its catch-up |
| Tab entry (AC-5) | `MainVM.onTabSelected` (`MainVM.kt:122`): `requestCatchUp(respectFloor = true)` when `item.type` is `TabType.Movies` or `TabType.Series` and the slot is served | MainVM sees every tab entry; `ContentListScreen` is reused for 9 tab types and its VM is not re-started on re-entry. See Open question 1 |
| Player exit (AC-5) | `PlayerInteractor` (`domain/interactor/player/PlayerInteractor.kt:64`) takes `WatchStateSyncInteractor` and exposes `requestWatchStateCatchUp()` = `requestCatchUp(respectFloor = false)`; `PlayerVM.exitPlayer` (`ui/feature/player/vm/PlayerVM.kt:2016`) calls it in `closeJob` after `flushOwedInvalidation()` (`:2034`), before `router.back` | After `awaitPendingMutations()` (`:2032`) the position save has reached the server, so its history entry is readable |

## Changes by layer

### API / repository

| File | Change |
|---|---|
| `data/api/history/HistoryRequest.kt:16` | `page(page, perPage: Int? = null)`; `perpage` in `query` when given, `require(perPage in 1..50)` |
| `data/api/history/HistoryApiTransport.kt:12` | `fetchHistoryPage(page, baseUrl, perPage: Int? = null)` |
| `data/api/KinoPubApiClient.kt:403` | `getHistoryData(page, perPage: Int? = null)` |
| `data/db/WatchStateSyncDao.kt:9` | `@Query("UPDATE watch_state_sync SET history_newest_seen = :seen WHERE id = :id AND history_newest_seen < :seen") raiseHistoryNewestSeen(seen, id = SINGLE_ROW_ID)` |
| `data/db/PuberDatabase.kt:13-21` | version 5, `AutoMigration(4, 5, spec = HistoryPageSizeMigration::class)`; exported `app/schemas/.../5.json` |
| `data/db/HistoryPageSizeMigration.kt` (new) | `AutoMigrationSpec` with the `UPDATE` above |
| `data/repository/WatchStateRepository.kt:378` | `saveSyncCursor`: `transaction.run`, `historyNewestSeen = max(stored, given)` |
| `data/repository/WatchStateRepository.kt` (after `:394`) | `recordCatchUpPage(entries, seriesStillInProgress, observedAt, newestSeen)` = `transaction.run { recordFromHistory(...); syncDao.raiseHistoryNewestSeen(newestSeen) }` |
| `data/repository/WatchStateRepository.kt:193` | `endSyncWindow`: publish a held tick on every close |

### Domain (interactor)

| File | Change |
|---|---|
| `domain/interactor/watchstate/WatchStateSyncInteractor.kt:46-58` | ctor: `itemDetails: ItemDetailsRepository`, `catchUpFloor: Duration = 60.seconds` |
| same, new members | `catchUpMutex`, `lastCatchUpAt`, `rerunRequested`, `detailsCheckedIds` (all cleared in `invalidate()` `:271`); `suspend fun catchUp(respectFloor: Boolean = true): Boolean`; `fun requestCatchUp(respectFloor: Boolean = true)` launching in `requestScope` with the error handling of `requestSync` (`:123-129`) |
| same, `syncHistory` `:374` | `api.getHistoryData(page, SYNC_HISTORY_PAGE_SIZE)`; `SYNC_HISTORY_PAGE_SIZE = 50` in the companion (`:482`) |
| `domain/di/modules.kt:66` | pass `itemDetails = get()` |
| `domain/interactor/player/PlayerInteractor.kt:64` | ctor `watchStateSync: WatchStateSyncInteractor` (resolved by `scopedOf`, `ui/feature/player/component/PlayerScreen.kt:50`); `fun requestWatchStateCatchUp()` |

### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/main/vm/MainVM.kt:88-98` | catch-up before `syncIfStale` on the first run; `requestCatchUp` on later calls |
| `ui/feature/main/vm/MainVM.kt:122` | catch-up request on Movies/Series entry |
| `ui/feature/player/vm/PlayerVM.kt:2031-2036` | `interactor.requestWatchStateCatchUp()` after `flushOwedInvalidation()` |

### Screen and DI module, Compose content, resources

N/A: no screen, composable or string changes; DI changes are the constructor parameters above.

### Tests touched by contract

`WatchStateSyncInteractorTest` mocks `getHistoryData(page)`; with the new parameter every stub and verify becomes `getHistoryData(page, 50)`. `PlayerVMTestFixture` builds `PlayerInteractor` as `mockk(relaxUnitFun = true)` (`app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:86`), so the new Unit call needs no stub. `PuberDatabaseMigrationTest` (`app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt`) gets a 4 -> 5 case.

## Alternatives rejected

- Loosen the existing gates (research.md option B): still dropped behind a running walk and blind to page 1 during a full pass.
- Run the catch-up between the walk's chunks: a parked walk has no chunk boundary for hours.
- Persist the set of series last seen in `watching/serials`: a schema column for what the per-process set and the play-evidence filter already bound.
- Re-read the old history entry instead of Details for series that left the list: the entry's verdict is the one that is stale.
- `perpage=50` only for the catch-up: contradicts owner decision Q3.
- Trigger tab entry from `ContentListVM.onStart`: not called again on re-entry to a kept tab, and the screen does not know whether its tab is Movies or Series without a new parameter.

## Risks

| # | Risk | Area | Where | Guard |
|---|---|---|---|---|
| 1 | `/v1/history` not ordered by `last_seen` descending: the stop rule misses entries | correctness | stop rule in `catchUp`, `advancedOver` `WatchStateSyncInteractor.kt:451-453` | AC-8 before implementation relies on it |
| 2 | Resume page counted in 20s read in 50s skips history, and a completing reconcile prunes live rows | data shape, rollout | `firstPageToRead` `:300-305`, prune `:228-229` | 4 -> 5 migration; overlap of 2 pages (`:505`) covers the floor rounding |
| 3 | Walk saves a lower mark over the catch-up's, so the next catch-up re-reads pages | concurrency | `saveSyncCursor` `WatchStateRepository.kt:378` | monotonic save; re-reading is idempotent if it slips (`WatchStateDao.kt:117-118`) |
| 4 | Catch-up and walk write the same item: an older read overwrites a newer one | concurrency | `WatchStateDao.kt:106-120`, `:132-156` | upserts already require `history_seen_at` and `updated_at` at least as new; each run stamps its own `observedAt` before its requests |
| 5 | Catch-up rows pruned by a reconcile, or kept after logout | lifecycle | `pruneOlderThan` `WatchStateDao.kt:233`, `sessionSurvived` `WatchStateSyncInteractor.kt:288` | generation read inside the history transaction; `sessionSurvived` after each write (AC-6) |
| 6 | Details refetch volume on accounts with many started-but-unlisted series, and a flap back to unfinished when a weekly reconcile re-reads an old sub-90% entry | performance, correctness | `catchUp` series step; `toHistoryRowOrNull` `WatchStateRepository.kt:450-475` | cap 10 per run, once per id per process; the flap is the out-of-scope 90% case and is corrected by the next process's catch-up |
| 7 | Start contention: catch-up and `syncIfStale` both fetch the watching lists | performance | `MainVM.kt:88-98` | sequential on the first run, inside `StartupSlot.WatchState`; `syncIfStale` is normally not due |
| 8 | `endSyncWindow` change publishes a walk's held chunk writes when a catch-up closes inside it | performance | `WatchStateRepository.kt:193-205` | one extra re-page per catch-up during a walk; a no-change catch-up publishes nothing (`onIndexChanged` only holds on a snapshot change, `:151-171`) |
| 9 | A Room auto-migration with no schema diff is refused by the Room 3 processor | rollout | `PuberDatabase.kt:18-21` | fall back to a manual `Migration(4, 5)` added in `create` (`:34-43`); `fallbackToDestructiveMigration` (`:42`) costs a full walk, not data |

## Open questions

1. AC-5 names `ContentListInteractorTest` for tab entry; this design triggers it in `MainVM.onTabSelected`, tested in `MainVMWatchStateSyncTest`. Accept the test location change? Recommended: yes; the AC's behaviour is unchanged. Blocks: no.
2. Should the History screen catch-up (`ui/feature/history/vm/HistoryVM.kt:111-113`, `requestSync(maxIndexAge = ZERO)`) switch to `requestCatchUp`? Recommended: no, not asked for; a follow-up idea if wanted. Blocks: no.

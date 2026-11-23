# Watch-state index lags the server across devices: catch up history to a last-updated mark on start/resume and catalogue entry, and on player exit

Status: approved
Date: 2026-11-23
Slug: watch-index-catch-up
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request
research.md: Request. Owner decision (`state.md` Decisions 2026-11-22): keep a last-updated mark, fetch history back to it on start, on every device, not only where Details was opened.

### Root cause
Status: **plausible** for the Znieczulenie report on the box (which candidate applied there is not measured); each gate is **confirmed** from code.

| Finding | Where | Status |
|---|---|---|
| The mark already exists (`historyNewestSeen`) and the incremental walk already stops at it | `WatchStateSyncEntity.kt:21-39`, `WatchStateSyncInteractor.kt:346-353`, `:443-477` | confirmed (code) |
| Start and resume skip the walk for 1 h after a complete sync and for 5 min after any run in the process | `WatchStateSyncInteractor.kt:259-264`, `MainVM.kt:48-54`, `:88-98` | confirmed (code) |
| A non-forced run is dropped while a walk holds the mutex, including while it is parked in the background | `WatchStateSyncInteractor.kt:96`, `:144`, `:429-433` | confirmed (code) |
| During a full or reconcile pass a resumed run starts at `historyResumePage - 2`, not page 1 | `WatchStateSyncInteractor.kt:300-305` | confirmed (code) |
| A series whose last history row was written while it was in `watching/serials` stays unfinished after it leaves the list until a newer entry, the 7-day reconcile or Details | `WatchStateRepository.kt:450-475`, `:444-445` | confirmed (code); whether `watching/toggle` writes history is unverified |
| Catalogue entry and a player exit with no watched mark trigger nothing | `ContentListInteractor.kt:70-77`, `PlayerVM.kt:2016-2036` | confirmed (code) |
| `/v1/history` order by `last_seen` descending is assumed, not documented | `WatchStateDao.kt:85-88`; research.md: External findings | unverified; AC-8 checks it |

## Acceptance criteria

- AC-1. Given an index whose `lastSyncAt` is less than 1 h old and history holding entries newer than `historyNewestSeen`, When the catch-up runs on Main start, Then both watching lists and history from page 1 are read until the first non-empty page whose entries all have `last_seen <= historyNewestSeen`, the newer entries are folded in, and the mark advances (unit, `WatchStateSyncInteractorTest`).
- AC-2. Given a full or reconcile walk in progress (running or parked, `fullHistoryWalkDone = false`), When a catch-up is requested, Then it reads from page 1 to the mark without waiting for the walk to finish, and `historyResumePage`, `fullHistoryWalkDone`, `lastSyncAt` and the prune are left as the walk set them (unit).
- AC-3. Given a mark 30+ pages behind page 1, When the catch-up runs, Then it reads every page down to the mark in one run, with no 300-page budget and no 20 s chunk pause, and an error on any page leaves the mark unchanged (unit).
- AC-4. Given a series unfinished in the index and absent from the fresh `watching/serials?subscribed=0`, When the catch-up runs, Then its Details are fetched once and the row takes the Details verdict (`Item.isFullyWatched`), so a series finished on another device is hidden under "Hide watched" without a new history entry (unit).
- AC-5. Given the app on Main, When it starts (after `StartupSlot.WatchState`), resumes, enters the Movies or Series tab, or exits the player, Then a catch-up is requested; start, resume and tab entry share a 60 s floor, player exit has none (unit, `MainVMWatchStateSyncTest` incl. tab entry per Q4, a PlayerVM test).
- AC-6. Given a catch-up writes rows, When a reconcile pass later prunes by generation, Then those rows carry `currentGeneration()` and survive; and a logout during a catch-up discards its writes (`sessionSurvived`) (unit).
- AC-7. Given history has nothing newer than the mark and no series left the list, When the catch-up runs, Then no row is written and `settledChanges` does not emit, so a filtered catalogue does not re-page (unit).
- AC-8. Given the box, When history pages 1-3 are read (read-only, `perpage` as the sync uses it), Then the evidence records whether `last_seen` is non-increasing across them; if it is not, the run stops for a design change (observation).
- AC-9. Given the box with "Hide watched" on, the Series tab showing a series, and that series' remaining episodes then watched or marked on another client, When the app on the box is resumed or restarted and the Series tab opened, without opening Details, Then the series is gone within one catch-up (observation).
- AC-10. Given the full check, When `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then it passes; existing `WatchStateSyncInteractorTest`, `WatchStateRepositoryTest` and `HistoryVMLifecycleTest` change only where AC-1 to AC-7 change their contract.

Out of scope: the 90% rule for a final episode left in the credits (research.md: Risks, own idea run if wanted); `collection-watched-mark-lag`.

## Out of scope

- the 90% rule for a final episode left in the credits (research.md: Risks, own idea run if wanted); `collection-watched-mark-lag`.

## Design

### Decisions
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
| Request while a catch-up runs | Dropped when floored; a no-floor request sets `rerunRequested` and the holder runs once more before releasing the lock, and after `unlock()` re-checks the flag and retries `tryLock()` | A player exit after a catch-up started must still see its own history entry |
| Foreground | `awaitForeground()` once before the first request; no pause between pages | Matches `runSync` (`:144`); a catch-up is a few requests |
| Page size (state.md Q3) | `HistoryRequest.page(page, perPage: Int? = null)` (`data/api/history/HistoryRequest.kt:16`) adds `perpage`; `fetchHistoryPage` (`data/api/history/HistoryApiTransport.kt:12`) and `KinoPubApiClient.getHistoryData` (`data/api/KinoPubApiClient.kt:403`) pass it through; the walk and the catch-up send 50 | History screen, `RecentlyPlayedOrder`, `ContinueWatchingSource` keep the default |
| A full walk resumed across the page-size change | `PuberDatabase` version 4 -> 5 (`data/db/PuberDatabase.kt:13`) with `AutoMigration(from = 4, to = 5, spec = HistoryPageSizeMigration)`, whose `onPostMigrate` runs `UPDATE watch_state_sync SET history_resume_page = ((history_resume_page - 1) * 20) / 50 + 1 WHERE full_history_walk_done = 0` | A resume page counted in 20s, read in 50s, skips entries, and a reconcile that then completes prunes rows it never restamped (`WatchStateSyncInteractor.kt:228-229`). Same pattern as `LegacyPayloadCleanup` (`data/db/LegacyPayloadCleanup.kt:12`) |
| Start and resume (AC-5) | `MainVM.syncWatchState` (`ui/feature/main/vm/MainVM.kt:88`): the first run awaits `StartupSlot.WatchState` as now, then `catchUp(respectFloor = true)`, then `syncIfStale()`. Once the slot is served, every call first does `requestCatchUp(respectFloor = true)`, then the existing job logic | The start catch-up keeps the slot; a resume during a long walk job (dropped at `:89`) still gets its catch-up |
| Tab entry (AC-5) | `MainVM.onTabSelected` (`MainVM.kt:122`): `requestCatchUp(respectFloor = true)` when `item.type` is `TabType.Movies` or `TabType.Series` and the slot is served | MainVM sees every tab entry; `ContentListScreen` is reused for 9 tab types and its VM is not re-started on re-entry. See Open question 1 |
| Player exit (AC-5) | `PlayerInteractor` (`domain/interactor/player/PlayerInteractor.kt:64`) takes `WatchStateSyncInteractor` and exposes `requestWatchStateCatchUp()` = `requestCatchUp(respectFloor = false)`; `PlayerVM.exitPlayer` (`ui/feature/player/vm/PlayerVM.kt:2016`) calls it in `closeJob` after `flushOwedInvalidation()` (`:2034`), before `router.back` | After `awaitPendingMutations()` (`:2032`) the position save has reached the server, so its history entry is readable |

### Changes by layer
#### API / repository

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

#### Domain (interactor)

| File | Change |
|---|---|
| `domain/interactor/watchstate/WatchStateSyncInteractor.kt:46-58` | ctor: `itemDetails: ItemDetailsRepository`, `catchUpFloor: Duration = 60.seconds` |
| same, new members | `catchUpMutex`, `lastCatchUpAt`, `rerunRequested`, `detailsCheckedIds` (`lastCatchUpAt` and `detailsCheckedIds` cleared in `invalidate()` `:271`; `catchUpMutex` is an injectable constructor parameter; `rerunRequested` belongs to the lock holder); `suspend fun catchUp(respectFloor: Boolean = true): Boolean`; `fun requestCatchUp(respectFloor: Boolean = true)` launching in `requestScope` with the error handling of `requestSync` (`:123-129`) |
| same, `syncHistory` `:374` | `api.getHistoryData(page, SYNC_HISTORY_PAGE_SIZE)`; `SYNC_HISTORY_PAGE_SIZE = 50` in the companion (`:482`) |
| `domain/di/modules.kt:66` | pass `itemDetails = get()` |
| `domain/interactor/player/PlayerInteractor.kt:64` | ctor `watchStateSync: WatchStateSyncInteractor` (resolved by `scopedOf`, `ui/feature/player/component/PlayerScreen.kt:50`); `fun requestWatchStateCatchUp()` |

#### ViewModel state and actions

| File | Change |
|---|---|
| `ui/feature/main/vm/MainVM.kt:88-98` | catch-up before `syncIfStale` on the first run; `requestCatchUp` on later calls |
| `ui/feature/main/vm/MainVM.kt:122` | catch-up request on Movies/Series entry |
| `ui/feature/player/vm/PlayerVM.kt:2031-2036` | `interactor.requestWatchStateCatchUp()` after `flushOwedInvalidation()` |

#### Screen and DI module, Compose content, resources

N/A: no screen, composable or string changes; DI changes are the constructor parameters above.

#### Tests touched by contract

`WatchStateSyncInteractorTest` mocks `getHistoryData(page)`; with the new parameter every stub and verify becomes `getHistoryData(page, 50)`. `PlayerVMTestFixture` builds `PlayerInteractor` as `mockk(relaxUnitFun = true)` (`app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:86`), so the new Unit call needs no stub. `PuberDatabaseMigrationTest` (`app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt`) gets a 4 -> 5 case.

### Alternatives rejected
- Loosen the existing gates (research.md option B): still dropped behind a running walk and blind to page 1 during a full pass.
- Run the catch-up between the walk's chunks: a parked walk has no chunk boundary for hours.
- Persist the set of series last seen in `watching/serials`: a schema column for what the per-process set and the play-evidence filter already bound.
- Re-read the old history entry instead of Details for series that left the list: the entry's verdict is the one that is stale.
- `perpage=50` only for the catch-up: contradicts owner decision Q3.
- Trigger tab entry from `ContentListVM.onStart`: not called again on re-entry to a kept tab, and the screen does not know whether its tab is Movies or Series without a new parameter.

### Risks
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

## Test design

### Risk profile
| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Catch-up dropped or delayed behind a running or parked walk (brief root cause; design Locking) | 3 | 3 | 9 | AC-2 unit |
| R2 | `/v1/history` not ordered by `last_seen` descending (brief, design #1) | 2 | 3 | 6 | AC-8 observation on the box; only the real server shows it |
| R3 | Resume page counted in 20s read in 50s skips entries, reconcile prunes live rows (design #2) | 2 | 3 | 6 | instrumented migration test |
| R4 | Catch-up rows pruned by a reconcile, or kept after logout (brief, design #5) | 2 | 3 | 6 | AC-6 unit |
| R5 | Mark raised past unread pages after a failed page (design: write once after the stop page) | 2 | 3 | 6 | AC-3 unit |
| R6 | Catch-up on an index with no mark becomes an unbounded walk (design: no-mark decision) | 2 | 3 | 6 | AC-1 unit |
| R7 | Catch-up change held in a walk's sync window until the walk ends, so AC-9 lags for minutes (design #8, `endSyncWindow`) | 3 | 2 | 6 | repository unit |
| R8 | Details refetch volume (brief, design #6) | 2 | 2 | 4 | AC-4 cap and once-per-process unit |
| R9 | Start contention: catch-up leaves `StartupSlot.WatchState` (brief, design #7) | 2 | 2 | 4 | AC-5 MainVM unit |
| R10 | Player-exit request swallowed by a catch-up already running (design: `rerunRequested`) | 2 | 2 | 4 | AC-5 unit |
| R11 | Catalogue re-pages on a no-news catch-up (brief, AC-7) | 2 | 2 | 4 | AC-7 unit |
| R12 | Walk saves a lower mark over the catch-up's (design #3) | 3 | 1 | 3 | repository unit, P1 |
| R13 | Catch-up and walk write the same item, older read wins (brief, design #4) | 1 | 3 | 3 | existing guards `recordFromHistory_doesNotLetAnOlderEntryOverwriteANewerOne`, `WatchStateDaoTest.aServerRowReadBeforeTheStoredOneDoesNotLand` |
| R14 | Room refuses an auto-migration with no schema diff (design #9) | 2 | 1 | 2 | accepted: `check.fast` fails at compile, design names the manual-`Migration` fallback |

### Traceability
Files: **SI** `domain/interactor/watchstate/WatchStateSyncInteractorTest.kt`, **WR** `data/repository/WatchStateRepositoryTest.kt`, **MV** `ui/feature/main/vm/MainVMWatchStateSyncTest.kt`, **PV** `ui/feature/player/vm/PlayerVMInvalidationTest.kt`, **PI** `domain/interactor/player/PlayerInteractorTest.kt`, **ID** `data/repository/ItemDetailsRepositoryTest.kt`, **HR** `data/api/history/HistoryRequestTest.kt`, **KH** `data/api/KinoPubApiClientHistoryTest.kt`, **MG** `data/db/PuberDatabaseMigrationTest.kt` (instrumented), **WD** `data/db/WatchStateDaoTest.kt` (instrumented).

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | SI `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour` | `lastSyncAt = now - 1 min`, mark 995, pages 1-3 newer, page 4 all <= mark, page 5 stubbed: pages 1-4 read with `perpage` 50, page 5 never, both watching lists fetched, `recordCatchUpPage` gets only entries > 995 and the newest `last_seen`; fails if `staleAfter` gates the catch-up or the stop rule is off by a page |
| AC-1 | unit | P0 guard | SI `catchUp_sendsNothingOverAnIndexWithNoMark` | `historyNewestSeen = 0`: no API call, returns false; fails if the catch-up walks the whole history (R6) |
| AC-1 | unit | P1 | SI `catchUp_doesNotTreatAnEmptyPageAsHavingCaughtUp` | empty page 2 then a page with newer entries: page 3 read; fails if an empty page stops the run |
| AC-1 | unit | P1 | SI `catchUp_runsNothingWithoutTheSerialsList` | serials list fails: no history page read, no write; fails if history is folded without the series list |
| AC-1 | unit | P1 | SI `catchUp_keepsGoingWhenOnlyTheMoviesListFails` | movies list fails: history still read and written; fails if the movies list is made required |
| AC-2 | unit | P0 red | SI `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks` | `pacedInteractor`, 20-page walk parked in its chunk pause, cursor `historyResumePage = 10`, `fullHistoryWalkDone = false`: `catchUp()` returns true without advancing time and reads page 1; fails if it takes the walk's `mutex` or starts at `historyResumePage - 2` (R1) |
| AC-2 | unit | P0 red | SI `catchUp_leavesTheWalkCursorAndThePruneAlone` | after a catch-up over a mid-walk cursor: `saveSyncCursor` and `pruneStaleRows` never called by it, `historyResumePage`, `fullHistoryWalkDone`, `lastSyncAt`, `generation` unchanged; fails if the catch-up saves its own cursor |
| AC-2 | unit | P1 | SI `aWalkResumedAfterACatchUpStillWalksFromItsBookmark` | after a catch-up, the parked walk continues from its own page, not page 1; fails if the catch-up resets the walk |
| AC-3 | unit | P0 red | SI `catchUp_readsThirtyFivePagesWithoutStandingDownBetweenChunks` | `pacedInteractor` (1 min pause), 35 pages newer than the mark: all 35 read under `runCurrent()` with no time advanced; fails if `holdBeforeNextChunk` applies |
| AC-3 | unit | P1 | SI `catchUp_readsPastTheWalksPageBudget` | 310 pages newer than the mark: page 310 read; fails if `MAX_HISTORY_PAGES_PER_RUN` applies |
| AC-3 | unit | P0 red | SI `catchUp_writesNoHistoryAndKeepsTheMarkWhenAPageFails` | page 3 of 5 fails: `recordCatchUpPage` never called, cursor mark unchanged; fails if pages 1-2 are written with a raised mark (R5) |
| AC-3 | unit | P0 red | WR `recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether` | real repository over the fakes: rows present and `syncCursor().historyNewestSeen` equals the given mark; fails if the mark is not raised or the rows are dropped |
| AC-4 | unit | P0 red | SI `catchUp_asksDetailsForASeriesThatLeftTheWatchingList` | index holds series 7 unfinished with `watchedEpisodes > 0`, fresh serials list lacks it: `itemDetails.refresh(7)` called once; fails if the series step is missing or reads the stale history entry instead |
| AC-4 | unit | P1 | SI `catchUp_leavesASeriesStillInTheWatchingListToTheList` | series present in the list: no Details request; fails if every unfinished series is refetched (R8) |
| AC-4 | unit | P1 | SI `catchUp_skipsASeriesThatWasOnlyBrowsed` | unfinished, no play evidence: no Details request; fails if the play-evidence filter is dropped (R8) |
| AC-4 | unit | P1 | SI `catchUp_asksDetailsForASeriesOncePerProcess` | two unfloored catch-ups: one `refresh(7)`; `invalidate()` then a third: asked again; fails if `detailsCheckedIds` is missing or survives a logout |
| AC-4 | unit | P1 | SI `catchUp_asksDetailsForTheTenMostRecentlyPlayedSeriesAtMost` | 12 candidates: the 10 newest by `lastWatchedAt` refreshed; fails if the cap or the order is lost (R8) |
| AC-4 | unit | P2 | SI `catchUp_carriesOnPastAFailedDetailsRequest` | first `refresh` throws: the rest still asked, catch-up returns true |
| AC-4 | unit | P1 guard | ID `refreshRecordsTheFreshVerdictInTheWatchStateIndex` | `refresh(id)` of a fully watched series records `isFullyWatched = true`; fails if `refresh` stops going through the index |
| AC-5 | unit | P0 red | MV `onStart_catchesUpBeforeTheStaleSyncOnceTheStartupBurstHasPassed` | after the slot offset: `catchUp(true)` then `syncIfStale()` in order; fails if the catch-up is missing or runs after the walk |
| AC-5 | unit | P0 guard | MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` | before the slot offset: no `catchUp`/`requestCatchUp`; fails if the catch-up leaves `StartupSlot.WatchState` (R9) |
| AC-5 | unit | P0 red | MV `resumed_requestsAFlooredCatchUpOnceTheStartupBurstHasPassed` | `Resumed` after the slot: `requestCatchUp(true)` once |
| AC-5 | unit | P1 | MV `resumed_requestsACatchUpEvenWhileAWalkJobIsInFlight` | `syncIfStale` suspended, `Resumed`: `requestCatchUp(true)` still called; fails if the catch-up sits behind `watchStateSyncJob?.isActive` |
| AC-5 | unit | P0 red | MV `tabSelected_requestsAFlooredCatchUpForMoviesAndSeries` | `ItemSelected` with `TabType.Movies`, then `TabType.Series`: `requestCatchUp(true)` twice |
| AC-5 | unit | P1 | MV `tabSelected_requestsNoCatchUpForOtherTabs` | Home, Collections: none; fails if the type check is dropped |
| AC-5 | unit | P1 | MV `tabSelected_requestsNoCatchUpBeforeTheStartupSlot` | Series tab before the slot: none (R9) |
| AC-5 | unit | P0 red | SI `catchUp_aFlooredRequestWithinAMinuteSendsNothing` | `catchUpFloor = 60 s`: second floored call at +59 s sends no request and returns false, at +60 s runs; fails if the floor is missing or not shared |
| AC-5 | unit | P0 red | SI `catchUp_withoutTheFloorRunsInsideTheMinute` | `catchUp(respectFloor = false)` at +1 s runs; fails if player exit is floored |
| AC-5 | unit | P1 | SI `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun` | page 1 suspended in run 1, `requestCatchUp(false)`, release: page 1 read twice; fails without `rerunRequested` (R10). Phase (b): a `catchUp(false)` that starts and finishes inside the holder's `unlock()` returns false and page 1 is still read twice; fails without the re-check after `unlock()` |
| AC-5 | unit | P1 | SI `catchUp_aFlooredRequestDuringARunIsDropped` | same with `respectFloor = true`: page 1 read once |
| AC-5 | unit | P0 red | PV `backPressed_requestsAWatchStateCatchUpAfterTheFlushBeforeBack` | `coVerifyOrder`: `markItemDetailsStale(42)`, `requestWatchStateCatchUp()`, `router.back`; fails if the call is missing or runs before the position save |
| AC-5 | unit | P1 | PV `backPressed_withoutSuccessfulSave_stillRequestsACatchUp` | every save fails: catch-up still requested once |
| AC-5 | unit | P1 | PI `requestWatchStateCatchUp_asksForAnUnflooredCatchUp` | delegates `requestCatchUp(respectFloor = false)` |
| AC-6 | unit | P0 red | WR `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune` | sync row generation 3, catch-up write, `pruneStaleRows(3)`: rows remain; fails if the generation is read outside the transaction or defaulted (R4) |
| AC-6 | unit | P0 red | SI `catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun` | `invalidate()` inside the serials fetch: returns false, `repository.clear()` after the write; fails if `sessionSurvived` is skipped (R4) |
| AC-6 | unit | P1 | SI `invalidate_letsTheNextCatchUpRunInsideTheFloor` | `invalidate()` clears `lastCatchUpAt` |
| AC-7 | unit | P0 red | SI `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark` | page 1 all <= mark: `recordCatchUpPage` never called, no Details request; fails if the catch-up rewrites known entries |
| AC-7 | unit | P0 guard | WR `syncWindow_announcesNothingForACatchUpThatChangedNothing` | rows already equal, catch-up window opened and closed: `settledChanges` does not emit; fails if `endSyncWindow` publishes on every close regardless of `syncChangeHeld` |
| AC-7 / AC-9 | unit | P0 red | WR `syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen` | outer window open, inner window writes a changed row and closes: `settledChanges` emits once before the outer closes; fails on the base `endSyncWindow` (`openSyncWindows > 0` holds it) (R7) |
| AC-8 | manual | P0 | Manual 1 | ordering assumption behind the stop rule (R2) |
| AC-9 | manual | P0 | Manual 2 | cross-device series disappears within one catch-up |
| AC-10 | check | P0 guard | check.full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | existing SI stubs move to `getHistoryData(page, 50)`; no other existing assertion changes |

Supporting rows (design decisions with no AC of their own):

| Change | Level | Priority | Test | Proves |
|---|---|---|---|---|
| `perpage` in `HistoryRequest.page` | unit | P1 | HR `page_addsPerPageWhenGiven` | query `page=3&perpage=50`; the existing `page_buildsVerifiedReadRequest` stays the no-`perpage` guard |
| `perpage` bound | unit | P2 | HR `page_rejectsAPerPageOutsideOneToFifty` | 0 and 51 throw |
| client pass-through | unit | P1 | KH `getHistoryData_sendsPerPageWhenGiven` | wire query `page=3&perpage=50`; fails if `KinoPubApiClient` drops it |
| walk at 50 | unit | P1 | SI `theWalkReadsFiftyEntriesAPage` | `syncIfStale()` calls `getHistoryData(1, 50)` (state.md Q3) |
| monotonic mark (R12) | unit | P1 | WR `saveSyncCursor_neverLowersTheHistoryMark` | stored 900, save 800: `historyNewestSeen` stays 900 |
| `raiseHistoryNewestSeen` SQL | instrumented | P1 | WD `raiseHistoryNewestSeenNeverLowersTheMark` | real Room query: 900 then 800 leaves 900, 950 lands |
| migration 4 -> 5 (R3) | instrumented | P0 red | MG `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk` | page 26 at 20/page becomes 11 at 50/page, other columns kept |
| migration 4 -> 5 (R3) | instrumented | P1 | MG `migrate4To5LeavesAFinishedWalkAlone` | `full_history_walk_done = 1`: `history_resume_page` unchanged |

Instrumented rows run on the box only: `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest` (and `...WatchStateDaoTest`).

### Failing-first test
`catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour`, written first. On e4c3b5aa `isRunDue` (`WatchStateSyncInteractor.kt:259-264`) returns false while `now - lastSyncAt < 1 h`, and there is no other entry point, so the newer pages are never read; the test fails there (no `catchUp`) and passes once the catch-up exists. Every other `red` row is run on the unchanged code first as well.

### Manual checks
Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease, worktree dev APK installed with `tools/android-apk-install-preserve`. Evidence through `tools/mobile-evidence-audit.sh`.

**Manual 1 (AC-8), run before the implementation relies on the stop rule.**

1. Install the worktree's devDebug APK with install-preserve; Settings > Rebuild watch index (reads history, server read-only).
2. `adb -s <serial> logcat -d | grep 'history order page='`; keep the lines for pages 1-3 (page number, entry count, ordered flag only).

Expected: `ordered=true` for pages 1-3. Must not happen: titles, tokens or raw response bodies in the evidence; any write request. Any `ordered=false` stops the run for a design change.

**Manual 2 (AC-9).**

1. Settings: "Hide watched" on. D-pad to the Series tab; note a series X with unwatched episodes left, visible in the grid.
2. `KEYCODE_HOME`; wait until the app is in the background.
3. On another client, the owner watches or marks X's remaining episodes watched (an account mutation: owner's own action or explicit authorization).
4. Resume the app from the launcher; D-pad to Movies, then Series.
5. Wait up to 60 s on the Series tab; screenshot.
6. Repeat 1-5 with a cold start (`am force-stop com.kino.puber.stage`, launch) instead of the resume, on a second series Y.

Expected: X (and Y) gone from the Series grid without opening Details; other series stay. Must not happen: Details of X or Y opened, a crash, focus lost from the grid or the side menu, the grid re-paging again on later tab switches with no new history, X or Y coming back after a second resume.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which events run the catch-up, and how often? | no | answered | Main start (WatchState slot), every resume, Movies/Series tab entry, player exit; at most once a minute except player exit |
| Q2 | Refetch Details for series that left watching/serials while unfinished in the index? | yes | answered | Yes |
| Q3 | 50 history entries per page in the sync? | no | answered | Yes; History screen keeps its own size |
| Q4 | AC-5 test location (architect) | no | answered | MainVMWatchStateSyncTest; trigger from MainVM.onTabSelected |
| Q5 | Switch HistoryVM to the new catch-up? (architect) | no | answered | No |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Keep a last-updated mark and fetch history back to it, so a rarely switched-on device is current on its next start | owner |
| 2026-11-22 | Q1-Q3 as answered above | owner |
| 2026-11-23 | Q4-Q5 as recommended by the architect | owner (standing instruction: recommended) |
| 2026-11-23 | codex: required; round caps lifted | owner |
| 2026-11-23 | Spec aligned to the plan after readiness-1: AC-5 tab-entry test in MainVMWatchStateSyncTest (Q4); Manual 1 observes order via the debug log line `history order page=` after Settings > Rebuild instead of reading /v1/history with the box session | conductor (readiness-1 must-fix 3) |
| 2026-11-23 | Spec aligned to plan round 2: catch-up re-checks `rerunRequested` after `unlock()`; test phase (b) for the release window; `invalidate()` clears only `lastCatchUpAt` and `detailsCheckedIds` | conductor (architect plan round 2 Q1-Q3, recommended) |
| 2026-11-23 | Review round 1 item 8: rows SI `catchUp_sendsNothingOverAnIndexWithNoMark` and MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` relabelled P0 guard, with mutation runs recorded | conductor (triage-1) |
| 2026-11-23 | Gate 1: approved (size M, tracks none, AC-1..AC-10). AC-9 needs an account mutation on another client: owner action or explicit authorization | owner (standing instruction: recommended) |

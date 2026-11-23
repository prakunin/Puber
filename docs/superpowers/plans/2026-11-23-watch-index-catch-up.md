# Watch-state index catch-up - plan

**Spec:** `docs/superpowers/specs/2026-11-23-watch-index-catch-up.md`
**Goal:** Every device folds history newer than its `historyNewestSeen` mark into the watch-state index on Main start, resume, Movies/Series tab entry and player exit, past a running walk, and refreshes series that left `watching/serials` unfinished.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/user1/orca/workspaces/Puber/watch-index-catch-up && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/user1/orca/workspaces/Puber/watch-index-catch-up && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'` from the worktree. Never `./gradlew`, never `connected*AndroidTest` (guard hook); instrumented tests are compiled here (`:app:compileDevDebugAndroidTestKotlin`) and run by qa on the box.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence with no prefix, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Path prefixes: `M/` = `app/src/main/java/com/kino/puber/`, `T/` = `app/src/test/kotlin/com/kino/puber/`, `A/` = `app/src/androidTest/kotlin/com/kino/puber/`. Line numbers are at e4c3b5aa.
- Out of scope: the 90% rule for a final episode left in the credits; `collection-watched-mark-lag`; switching `HistoryVM` (`M/ui/feature/history/vm/HistoryVM.kt:111-113`) to the catch-up (spec Q5).
- Test names below are the spec's Test design names; no other test is added.

## Coverage

| AC | Task | Tests |
|---|---|---|
| AC-1 | 4 | SI `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour`, `catchUp_sendsNothingOverAnIndexWithNoMark`, `catchUp_doesNotTreatAnEmptyPageAsHavingCaughtUp`, `catchUp_runsNothingWithoutTheSerialsList`, `catchUp_keepsGoingWhenOnlyTheMoviesListFails` |
| AC-2 | 4 | SI `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks`, `catchUp_leavesTheWalkCursorAndThePruneAlone`, `aWalkResumedAfterACatchUpStillWalksFromItsBookmark` |
| AC-3 | 3, 4 | WR `recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether`; SI `catchUp_readsThirtyFivePagesWithoutStandingDownBetweenChunks`, `catchUp_readsPastTheWalksPageBudget`, `catchUp_writesNoHistoryAndKeepsTheMarkWhenAPageFails` |
| AC-4 | 4 | SI `catchUp_asksDetailsForASeriesThatLeftTheWatchingList`, `catchUp_leavesASeriesStillInTheWatchingListToTheList`, `catchUp_skipsASeriesThatWasOnlyBrowsed`, `catchUp_asksDetailsForASeriesOncePerProcess`, `catchUp_asksDetailsForTheTenMostRecentlyPlayedSeriesAtMost`, `catchUp_carriesOnPastAFailedDetailsRequest`; ID `refreshRecordsTheFreshVerdictInTheWatchStateIndex` |
| AC-5 | 4, 5, 6 | SI `catchUp_aFlooredRequestWithinAMinuteSendsNothing`, `catchUp_withoutTheFloorRunsInsideTheMinute`, `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun`, `catchUp_aFlooredRequestDuringARunIsDropped`; MV (7, Task 5); PV (2) and PI (1), Task 6 |
| AC-6 | 3, 4 | WR `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune`; SI `catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun`, `invalidate_letsTheNextCatchUpRunInsideTheFloor` |
| AC-7 | 3, 4 | WR `syncWindow_announcesNothingForACatchUpThatChangedNothing`, `syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen`; SI `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark` |
| AC-8 | 2, qa | debug log line from Task 2; Device verification: Manual 1 |
| AC-9 | qa | Device verification: Manual 2 |
| AC-10 | 6 | full gate; SI stubs move to `getHistoryData(page, 50)` (Task 2) |

Supporting rows: HR `page_addsPerPageWhenGiven`, `page_rejectsAPerPageOutsideOneToFifty`, KH `getHistoryData_sendsPerPageWhenGiven` (Task 1); SI `theWalkReadsFiftyEntriesAPage`, MG `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk`, `migrate4To5LeavesAFinishedWalkAlone`, WD `raiseHistoryNewestSeenNeverLowersTheMark` (Task 2); WR `saveSyncCursor_neverLowersTheHistoryMark` (Task 3).

---

## Tasks

### Task 1: Let a history page request carry `perpage`

**Covers:** supporting rows (state.md Q3)
**Files:**
- Modify: `M/data/api/history/HistoryRequest.kt` - `page(page: Int, perPage: Int? = null)` (`:16`): `require(perPage == null || perPage in 1..50)`; query `mapOf("page" to page, "perpage" to perPage)` only when `perPage != null`, otherwise unchanged.
- Modify: `M/data/api/history/HistoryApiTransport.kt` - `fetchHistoryPage(page, baseUrl, perPage: Int? = null)` (`:12-15`) passes it to `HistoryRequest.page`.
- Modify: `M/data/api/KinoPubApiClient.kt` - `getHistoryData(page: Int, perPage: Int? = null)` (`:403-406`) passes it to `fetchHistoryPage`.
- Test: `T/data/api/history/HistoryRequestTest.kt` (existing `page_buildsVerifiedReadRequest` stays the no-`perpage` guard), `T/data/api/KinoPubApiClientHistoryTest.kt` (copy the `withServer`/`capture` pattern of `getHistoryData_usesExplicitGetWithPageAndNoStore`, `:59`).

**Consumes:** nothing new.
**Produces:** `KinoPubApiClient.getHistoryData(page: Int, perPage: Int? = null)`. Callers that stay on the default: `M/domain/interactor/watchstate/RecentlyPlayedOrder.kt:47`, `M/domain/interactor/history/HistoryInteractor.kt:48`, `M/core/tvhome/ContinueWatchingSource.kt:15`.

- [x] HR `page_addsPerPageWhenGiven`: `page(3, 50)` query is `{page=3, perpage=50}`.
- [x] HR `page_rejectsAPerPageOutsideOneToFifty`: 0 and 51 throw `IllegalArgumentException`.
- [x] KH `getHistoryData_sendsPerPageWhenGiven`: wire query `page=3&perpage=50`.
- [x] Implement; run the fast gate and both test classes; commit.

**Done when:** fast gate green; `HistoryRequestTest` and `KinoPubApiClientHistoryTest` green.

---

### Task 2: Walk history 50 entries a page, rescale a resumed walk, add the mark-raise query and the order log

**Covers:** state.md Q3, design Risks 2 and 9, AC-8 instrumentation, AC-10 (SI stub contract)
**Files:**
- Modify: `M/data/db/WatchStateSyncDao.kt` - add, after `upsert` (`:17-18`):
  `@Query("UPDATE watch_state_sync SET history_newest_seen = :seen WHERE id = :id AND history_newest_seen < :seen") suspend fun raiseHistoryNewestSeen(seen: Long, id: Int = WatchStateSyncEntity.SINGLE_ROW_ID)`.
- Create: `M/data/db/HistoryPageSizeMigration.kt` - `class HistoryPageSizeMigration : AutoMigrationSpec`, shaped like `LegacyPayloadCleanup` (`M/data/db/LegacyPayloadCleanup.kt:12-18`); `onPostMigrate` runs `UPDATE watch_state_sync SET history_resume_page = ((history_resume_page - 1) * 20) / 50 + 1 WHERE full_history_walk_done = 0`. The literals 20 and 50 stay literals (a later page-size edit must not change this migration).
- Modify: `M/data/db/PuberDatabase.kt` - `version = 5` (`:13`); add `AutoMigration(from = 4, to = 5, spec = HistoryPageSizeMigration::class)` to `autoMigrations` (`:18-21`); extend the comment at `:15-17` by one line for 4 -> 5.
  Fallback if KSP refuses an auto-migration with no schema diff (design Risk 9): make it `object HistoryPageSizeMigration : Migration(4, 5)` with the same `UPDATE` in `migrate(connection)`, register it with `.addMigrations(HistoryPageSizeMigration)` in `create` (`:34-43`), and record the deviation.
- Create: `app/schemas/com.kino.puber.data.db.PuberDatabase/5.json` - written by KSP during the fast gate; commit it.
- Modify: `M/domain/interactor/watchstate/WatchStateSyncInteractor.kt`:
  - companion (`:482`): `const val SYNC_HISTORY_PAGE_SIZE = 50`.
  - `syncHistory` (`:374`): `api.getHistoryData(page, SYNC_HISTORY_PAGE_SIZE)`.
  - new private `fun logHistoryOrder(page: Int, entries: List<History>, previousTail: Long?): Long?`: `ordered` = the entries' `lastSeenOrZero()` (`:509`) are non-increasing and the first is `<= previousTail` when that is not null; calls `log("history order page=$page entries=${entries.size} ordered=$ordered")` (`M/core/logger/Logger.kt`; Timber is planted only when `BuildConfig.DEBUG`, `M/PuberApp.kt:103-106`); returns the last entry's `lastSeenOrZero()`, or `previousTail` for an empty page. The line carries no timestamp, id or title.
  - `syncHistory` loop (`:371-412`): keep a local `var orderTail: Long? = null`; after each page `orderTail = logHistoryOrder(response.pagination.current, response.items, orderTail)`.
- Test: `T/domain/interactor/watchstate/WatchStateSyncInteractorTest.kt` - every `api.getHistoryData(x)` stub and verify becomes `api.getHistoryData(x, 50)`, every `getHistoryData(any())` becomes `getHistoryData(any(), any())` (including `stubHistory`, `:836-850`); no assertion changes otherwise.
- Test: `T/data/repository/WatchStateRepositoryTest.kt` - `FakeWatchStateSyncDao` (`:975-990`) implements `raiseHistoryNewestSeen`: updates `row.value` only when a row is present and its `historyNewestSeen` is lower than `seen`. Lands here because `WatchStateSyncDao` is an interface and the test source set must compile at this commit.
- Test: `A/data/db/PuberDatabaseMigrationTest.kt` - add `HistoryPageSizeMigration()` to `autoMigrationSpecs` (`:38`); insert rows with the existing `watch_state_sync` INSERT shape (`:58-62`).
- Test: `A/data/db/WatchStateDaoTest.kt` - uses `database.watchStateSyncDao()` from the in-memory database of `setUp` (`:35-42`).

**Consumes:** Task 1 `getHistoryData(page, perPage)`.
**Produces:** `WatchStateSyncDao.raiseHistoryNewestSeen(seen: Long, id: Int = SINGLE_ROW_ID)`; `SYNC_HISTORY_PAGE_SIZE`; `logHistoryOrder(page, entries, previousTail): Long?`; database version 5.

- [x] SI `theWalkReadsFiftyEntriesAPage`: `syncIfStale()` calls `getHistoryData(1, 50)`.
- [x] MG `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk`: v4 row with `history_resume_page = 26`, `full_history_walk_done = 0` migrates to 11, other columns unchanged.
- [x] MG `migrate4To5LeavesAFinishedWalkAlone`: `full_history_walk_done = 1` keeps its page.
- [x] WD `raiseHistoryNewestSeenNeverLowersTheMark`: upsert a row at 900; raise to 800 leaves 900; raise to 950 lands.
- [x] Implement; fast gate; `:app:compileDevDebugAndroidTestKotlin`; SI green; commit with `5.json`.

**Done when:** fast gate and `:app:compileDevDebugAndroidTestKotlin` green; `WatchStateSyncInteractorTest` and `WatchStateRepositoryTest` green; `5.json` committed. MG and WD run on the box (Device verification).

**Checkpoint (conductor):** after this commit, qa runs Manual 1 (AC-8). If `ordered=false` appears on pages 1-3, stop: the stop rule in Task 4 needs a design change.

---

### Task 3: Repository: monotonic mark, catch-up write, per-window settle

**Covers:** AC-3, AC-6, AC-7 (repository half), design Risk 3
**Files:**
- Modify: `M/data/repository/WatchStateRepository.kt`:
  - private `suspend fun writeCursor(cursor)`: reads `syncDao.get()?.historyNewestSeen ?: 0L` and upserts `cursor.copy(historyNewestSeen = maxOf(stored, cursor.historyNewestSeen)).toEntity()` (`toEntity` `:552`).
  - `saveSyncCursor` (`:378`) = `transaction.run { writeCursor(cursor) }`; `recordHistoryPage` (`:386-394`) calls `writeCursor` directly, so no transaction nests.
  - new `suspend fun recordCatchUpPage(entries: List<History>, seriesStillInProgress: Set<Int>, observedAt: Long, newestSeen: Long)` = `transaction.run { recordFromHistory(entries, seriesStillInProgress, observedAt); syncDao.raiseHistoryNewestSeen(newestSeen) }`. `recordFromHistory` (`:292-304`) reads `currentGeneration()` (`:364`) inside that transaction.
  - `endSyncWindow` (`:193-205`): decrement, then publish iff `syncChangeHeld` and clear it, on every close (drop the `openSyncWindows > 0` branch); update its KDoc to say a nested window pays out what is held when it closes.
- Test: `T/data/repository/WatchStateRepositoryTest.kt` - `FakeWatchStateSyncDao` already raises the mark (Task 2). Use `repository(eagerScope())` (`:53-62`, `:68-69`) and the `historyEntry` helper (`:770`).

**Consumes:** Task 2 `raiseHistoryNewestSeen` and its fake.
**Produces:** `WatchStateRepository.recordCatchUpPage(entries, seriesStillInProgress, observedAt, newestSeen)`; `saveSyncCursor` never lowers `historyNewestSeen`; `endSyncWindow` publishes a held change on every close.

- [x] WR `recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether`
- [x] WR `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune` (sync row generation 3, `pruneStaleRows(3)`)
- [x] WR `syncWindow_announcesNothingForACatchUpThatChangedNothing`
- [x] WR `syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen` (red on base: fails at `:196`)
- [x] WR `saveSyncCursor_neverLowersTheHistoryMark`
- [x] Implement; fast gate; `WatchStateRepositoryTest` green (existing `syncWindow_*` cases unchanged); commit.

**Done when:** fast gate green; `WatchStateRepositoryTest` green.

---

### Task 4: The catch-up in `WatchStateSyncInteractor`

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5 (interactor half), AC-6, AC-7
**Files:**
- Modify: `M/domain/interactor/watchstate/WatchStateSyncInteractor.kt` (class `:43`):
  - ctor (`:44-58`): `itemDetails: ItemDetailsRepository` after `repository`; `catchUpFloor: Duration = 60.seconds` after `pauseBetweenChunks`; `catchUpMutex: Mutex = Mutex()` after `catchUpFloor` (`kotlinx.coroutines.sync.Mutex` is an interface, so a test can wrap `unlock`; never the walk's `mutex`, `:61`).
  - fields: `@Volatile lastCatchUpAt: Long?`; `rerunRequested = AtomicBoolean()`; `detailsCheckedIds = ConcurrentHashMap.newKeySet<Int>()`.
  - `invalidate()` (`:271-278`): also `lastCatchUpAt = null`, `detailsCheckedIds.clear()`.
  - `fun requestCatchUp(respectFloor: Boolean = true)`: `requestScope.launch { catchUp(respectFloor) }` with the catch/log shape of `requestSync` (`:121-129`); no job dedupe.
  - `suspend fun catchUp(respectFloor: Boolean = true): Boolean`, contract below.
  - private `MAX_DETAILS_PER_CATCH_UP = 10` in the companion (`:482`).
- Modify: `M/domain/di/modules.kt` - `itemDetails = get()` in the `WatchStateSyncInteractor` single (`:65-71`); `ItemDetailsRepository` is a single at `M/data/di/modules.kt:99` and does not depend on the interactor.
- Test: `T/domain/interactor/watchstate/WatchStateSyncInteractorTest.kt` - field `itemDetails = mockk<ItemDetailsRepository>(relaxed = true)` passed to every construction (`:53`, `:412`, `:552`, `:573`, `:646`, `:826`); in `setUp` (`:66-75`) stub `repository.snapshot` from a `MutableStateFlow<Map<Int, WatchState>>` field, `repository.lastWatchedAt()` from a map field, and `recordCatchUpPage(any(), any(), any(), any())` to raise `cursor.historyNewestSeen` to `arg(3)`. Cases using `requestCatchUp` build the interactor with `requestScope = backgroundScope`. Pacing cases use `pacedInteractor` (`:826`).
- Test: `T/data/repository/ItemDetailsRepositoryTest.kt` - fixture at `:24-32`.

**Consumes:** Task 2 `SYNC_HISTORY_PAGE_SIZE`, `logHistoryOrder`; Task 3 `recordCatchUpPage`, `beginSyncWindow`/`endSyncWindow` (`WatchStateRepository.kt:182`, `:193`); existing `recordFromServer` (`:258`), `recordInProgress` (`:270`), `snapshot` (`:53`), `lastWatchedAt()` (`:246`), `ItemDetailsRepository.refresh` (`M/data/repository/ItemDetailsRepository.kt:80`, records via `:17-28`), `sessionSurvived` (`:288`), `fetch` (`:479`), `runCatchingCancellable` (`M/core/coroutine`).
**Produces:** `suspend fun catchUp(respectFloor: Boolean = true): Boolean`, `fun requestCatchUp(respectFloor: Boolean = true)`.

`catchUp` contract (returns true only when a run reached its end in the session it started in):

| Step | Behaviour |
|---|---|
| Floor | `respectFloor` and `clock() - lastCatchUpAt < catchUpFloor`: return false, no request |
| Request | unfloored: `rerunRequested.set(true)` before any `tryLock()`; floored: no flag |
| Lock | `catchUpMutex.tryLock()` fails: return false. The current holder owns the request: an unfloored flag set while it holds the lock is seen by its Holder loop or its Release re-check. No suspension point between the floor check and `tryLock()` |
| Holder loop | `do { rerunRequested.set(false); result = runCatchUp() } while (rerunRequested.get())`, then `unlock()` in `finally` |
| Release re-check | after `unlock()`: `rerunRequested.get()` true and `tryLock()` succeeds: run the Holder loop again; `tryLock()` fails: return `result` (the caller that took the lock owns the flag); flag false: return `result`. Closes the window where a request sets the flag after the loop's last `get()` and fails `tryLock()` before the `unlock()` |
| No mark | `repository.syncCursor().historyNewestSeen == 0L`: return false, no request |
| Start | `lastCatchUpAt = clock()`; `awaitForeground()` once (`:58`); then capture `generation` (`:69`), `observedAt = clock()`, `mark` re-read from `syncCursor()` |
| Lists | `getWatchingList(onlySubscribed = false)` required (null: return false, nothing read or written); `getWatchingMovies()` optional |
| History | page 1 up, `getHistoryData(page, SYNC_HISTORY_PAGE_SIZE)`; failed page: return false with no write; `logHistoryOrder` per page; keep entries with `lastSeenOrZero() > mark` and their max; stop after the first non-empty page whose entries are all `<= mark` or when `pagination.current >= pagination.total`; next page `pagination.current + 1`. No page budget, no `holdBeforeNextChunk`, no `mutableProgress` writes |
| Writes | inside `beginSyncWindow()` / `finally endSyncWindow()`: newer entries non-empty -> `recordCatchUpPage(newer, seriesIds, observedAt, newest)`; `recordFromServer(series, observedAt)`; `recordInProgress(movies.orEmpty(), observedAt)`; series step; `sessionSurvived(generation)` after each, false -> return false. Never `saveSyncCursor`, never `pruneStaleRows` |
| Series step | from `repository.snapshot.value`: `isSeriesLike && !isFullyWatched`, id not in the fresh serials ids, id not in `detailsCheckedIds`, and `(watchedEpisodes ?: 0) > 0` or id in `lastWatchedAt()`; newest `lastWatchedAt` first (absent = 0), `take(MAX_DETAILS_PER_CATCH_UP)`; each id added to `detailsCheckedIds` then `runCatchingCancellable { itemDetails.refresh(id) }`, a failure logged and skipped |

Annotate `@Suppress("ReturnCount")` where the guard returns exceed detekt's 3, as `indexAccount` does (`:175`). Function count must stay within detekt's 25 per class.

- [x] Write the failing test SI `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour` first; run it red on the base (no `catchUp`: compile failure counts as red, record it).
- [x] SI AC-1: `catchUp_sendsNothingOverAnIndexWithNoMark`, `catchUp_doesNotTreatAnEmptyPageAsHavingCaughtUp`, `catchUp_runsNothingWithoutTheSerialsList`, `catchUp_keepsGoingWhenOnlyTheMoviesListFails`
- [x] SI AC-2: `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks`, `catchUp_leavesTheWalkCursorAndThePruneAlone`, `aWalkResumedAfterACatchUpStillWalksFromItsBookmark`
- [x] SI AC-3: `catchUp_readsThirtyFivePagesWithoutStandingDownBetweenChunks`, `catchUp_readsPastTheWalksPageBudget`, `catchUp_writesNoHistoryAndKeepsTheMarkWhenAPageFails`
- [x] SI AC-4: `catchUp_asksDetailsForASeriesThatLeftTheWatchingList`, `catchUp_leavesASeriesStillInTheWatchingListToTheList`, `catchUp_skipsASeriesThatWasOnlyBrowsed`, `catchUp_asksDetailsForASeriesOncePerProcess`, `catchUp_asksDetailsForTheTenMostRecentlyPlayedSeriesAtMost`, `catchUp_carriesOnPastAFailedDetailsRequest`
- [x] SI AC-5: `catchUp_aFlooredRequestWithinAMinuteSendsNothing`, `catchUp_withoutTheFloorRunsInsideTheMinute`, `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun`, `catchUp_aFlooredRequestDuringARunIsDropped`
  - `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun` has two phases. (a) As the Test design row: page 1 suspended in run 1, `requestCatchUp(false)`, release: page 1 read twice. (b) Release window: a fresh interactor built with `catchUpMutex = object : Mutex by real { override fun unlock(owner: Any?) { ... ; real.unlock(owner) } }` whose first `unlock` first runs `backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { second = interactor.catchUp(respectFloor = false) }`; that call completes inline (no suspension before `tryLock()`), returns false, and page 1 is read twice. Phase (b) fails without the Release re-check.
- [x] SI AC-6: `catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun`, `invalidate_letsTheNextCatchUpRunInsideTheFloor`
- [x] SI AC-7: `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark`
- [x] ID `refreshRecordsTheFreshVerdictInTheWatchStateIndex` (guard, green on the base)
- [x] Implement; fast gate; SI and ID green; `:app:detektAll` green; commit.

**Done when:** fast gate green; `WatchStateSyncInteractorTest`, `ItemDetailsRepositoryTest` green; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:detektAll` green.

---

### Task 5: MainVM asks for the catch-up on start, resume and Movies/Series entry

**Covers:** AC-5 (start, resume, tab entry)
**Files:**
- Modify: `M/ui/feature/main/vm/MainVM.kt`:
  - `syncWatchState` (`:88-98`): before the `isActive` return (`:89`), `if (startupSlotServed) watchStateSyncInteractor.requestCatchUp(respectFloor = true)`. In the job, the first run after `awaitSlot(StartupSlot.WatchState)` (`:92-93`) does `runCatchingCancellable { catchUp(respectFloor = true) }` (failure logged) and then the existing `syncIfStale()` (`:95-96`). Update the KDoc (`:72-87`) for the catch-up.
  - `onTabSelected` (`:122-127`): `if (startupSlotServed && item.type in CATCH_UP_TABS) watchStateSyncInteractor.requestCatchUp(respectFloor = true)`; `CATCH_UP_TABS = setOf(TabType.Movies, TabType.Series)` in the companion (`:191`).
- Test: `T/ui/feature/main/vm/MainVMWatchStateSyncTest.kt` - reuse `vm(sync)` (`:145-191`); tab cases add `every { mapper.updateSelectedTab(any(), any()) } returns state` (the mapper is strict) and send `CommonAction.ItemSelected(MainTab(type = TabType.Series, icon = PhosphorIcons.Duotone.House))`; slot timing via `advanceTimeBy(StartupSlot.WatchState.offset.inWholeMilliseconds + 1)` as at `:84`. Existing 6 cases stay unchanged.

**Consumes:** Task 4 `catchUp`, `requestCatchUp`.
**Produces:** nothing new.

- [x] MV `onStart_catchesUpBeforeTheStaleSyncOnceTheStartupBurstHasPassed` (`coVerifyOrder`: `catchUp(true)`, `syncIfStale()`)
- [x] MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut`
- [x] MV `resumed_requestsAFlooredCatchUpOnceTheStartupBurstHasPassed`
- [x] MV `resumed_requestsACatchUpEvenWhileAWalkJobIsInFlight`
- [x] MV `tabSelected_requestsAFlooredCatchUpForMoviesAndSeries`
- [x] MV `tabSelected_requestsNoCatchUpForOtherTabs`
- [x] MV `tabSelected_requestsNoCatchUpBeforeTheStartupSlot`
- [x] Implement; fast gate; `MainVMWatchStateSyncTest` green; commit.

**Done when:** fast gate green; `MainVMWatchStateSyncTest` green.

---

### Task 6: Player exit asks for an unfloored catch-up

**Covers:** AC-5 (player exit), AC-10
**Files:**
- Modify: `M/domain/interactor/player/PlayerInteractor.kt` - ctor (`:64-68`) gains a last parameter `watchStateSync: WatchStateSyncInteractor` (resolved by `scopedOf(::PlayerInteractor)`, `M/ui/feature/player/component/PlayerScreen.kt:50`, from the global single); `fun requestWatchStateCatchUp() = watchStateSync.requestCatchUp(respectFloor = false)`.
- Modify: `M/ui/feature/player/vm/PlayerVM.kt` - in `exitPlayer`'s `closeJob` (`:2031-2036`), call `interactor.requestWatchStateCatchUp()` after `flushOwedInvalidation()` (`:2034`) and before `router.back` (`:2035`), unconditionally.
- Test: `T/domain/interactor/player/PlayerInteractorTest.kt` - construction at `:77` gains a `mockk<WatchStateSyncInteractor>(relaxed = true)` field.
- Test: `T/ui/feature/player/vm/PlayerVMInvalidationTest.kt` - `interactor` is `mockk(relaxUnitFun = true)` (`T/ui/feature/player/vm/PlayerVMTestFixture.kt:86`), so no stub; reuse `exit(vm)` (`:44-48`), `trackerSave()` (`:50-53`), `failEverySave()` (`:55-57`).

**Consumes:** Task 4 `requestCatchUp`.
**Produces:** `PlayerInteractor.requestWatchStateCatchUp()`.

- [x] PV `backPressed_requestsAWatchStateCatchUpAfterTheFlushBeforeBack` (`coVerifyOrder`: `markItemDetailsStale(42)`, `requestWatchStateCatchUp()`, `router.back(RESULT_CONTENT_CHANGED, any())`)
- [x] PV `backPressed_withoutSuccessfulSave_stillRequestsACatchUp`
- [x] PI `requestWatchStateCatchUp_asksForAnUnflooredCatchUp`
- [x] Implement; fast gate; then the full gate; commit.

**Done when:** fast gate and full gate green.

---

## Device verification (qa)

Box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease, `adb -s 192.168.1.110:5555`. APK: the worktree's `devDebug` (`./tools/agentw :app:assembleDevDebug`), installed with `tools/android-apk-install-preserve`; it is the debuggable dev build, so the Task 2 log line reaches logcat. Evidence goes through `tools/mobile-evidence-audit.sh`.

| Check | When | How | Pass |
|---|---|---|---|
| Manual 1 (AC-8) | after Task 2 | Clear logcat; Settings > Rebuild watch index (forced full pass, server read-only); after the first chunk, `adb -s <serial> logcat -d` piped through `grep "history order page="`; keep only the lines for pages 1-3 in the evidence | all three lines `ordered=true`; any `false` stops the run. No token, no `run-as`, no raw body, no title or timestamp in the evidence |
| MG, WD | after Task 2 | `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest`, then `TESTS=com.kino.puber.data.db.WatchStateDaoTest` | green |
| Manual 2 (AC-9) | after Task 6 | spec `#manual-checks` Manual 2; the account mutation on another client is the owner's action or needs explicit authorization | spec Manual 2 Expected |

---

## Implementation record

<!-- owner: implementer -->

**Commits:** Tasks 1-6 done; Task 6 is the commit that carries this record.

| Task | Commit |
|---|---|
| 1 | b5a11d80 Let a history page request carry perpage |
| 2 | d0995e44 Walk history fifty entries a page and log whether its order holds |
| 3 | ab2ef5ea Keep the history mark monotonic and add the catch-up write and per-window settle |
| 4 | 686eee8b Add the watch-state catch-up that folds history past the mark into the index |
| 5 | Ask for the watch-state catch-up on start, resume and Movies or Series entry (`git log`) |
| 6 | this commit (`git log`) |

**Red run** (P0 `red` tests on the unchanged code, signatures stubbed where needed):

- red: `HistoryRequestTest.page_addsPerPageWhenGiven` - query lacks `perpage` (assertion at :22)
- red: `HistoryRequestTest.page_rejectsAPerPageOutsideOneToFifty` - no `IllegalArgumentException` for 0 or 51
- red: `KinoPubApiClientHistoryTest.getHistoryData_sendsPerPageWhenGiven` - wire query is `page=3`
- red: `WatchStateSyncInteractorTest.theWalkReadsFiftyEntriesAPage` - `getHistoryData(1, 50)` never called (the walk called `(1, null)`)
- red: `WatchStateRepositoryTest.recordCatchUpPage_writesTheRowsAndRaisesTheMarkTogether`, `recordCatchUpPage_rowsCarryTheCurrentGenerationAndSurviveAPrune`, `syncWindow_publishesACatchUpsChangeWhileAWalksWindowIsStillOpen`, `saveSyncCursor_neverLowersTheHistoryMark` - each fails on its assertion against the stubbed `recordCatchUpPage` / the base `endSyncWindow` / the base `saveSyncCursor`
- green on base: `WatchStateRepositoryTest.syncWindow_announcesNothingForACatchUpThatChangedNothing` - the plan lists it as `guard`; it holds before and after
- Task 4 (`catchUp`/`requestCatchUp` stubbed to `false`/no-op so the class compiles): 21 of the 25 new `WatchStateSyncInteractorTest` cases failed on their assertion, among them `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour` (page 1 never read, line 909), `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks`, `catchUp_leavesTheWalkCursorAndThePruneAlone`, `catchUp_readsThirtyFivePagesWithoutStandingDownBetweenChunks`, `catchUp_asksDetailsForASeriesThatLeftTheWatchingList`, `catchUp_aFlooredRequestWithinAMinuteSendsNothing`, `catchUp_withoutTheFloorRunsInsideTheMinute`, `catchUp_throwsAwayWhatItWroteWhenTheSessionEndedMidRun`, `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark`
- green on base (Task 4): the 4 of 25 that passed on the stub are negative cases (nothing read, nothing written; e.g. `catchUp_sendsNothingOverAnIndexWithNoMark`, `catchUp_runsNothingWithoutTheSerialsList`) and guard the real run; `ItemDetailsRepositoryTest.refreshRecordsTheFreshVerdictInTheWatchStateIndex` is the plan's guard
- Task 5 (MainVM unchanged): red `onStart_catchesUpBeforeTheStaleSyncOnceTheStartupBurstHasPassed` (:150), `resumed_requestsAFlooredCatchUpOnceTheStartupBurstHasPassed` (:179), `resumed_requestsACatchUpEvenWhileAWalkJobIsInFlight` (:194), `tabSelected_requestsAFlooredCatchUpForMoviesAndSeries` (:210), each on the missing `catchUp`/`requestCatchUp` call; green on base: `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut`, `tabSelected_requestsNoCatchUpForOtherTabs`, `tabSelected_requestsNoCatchUpBeforeTheStartupSlot` (negative guards, nothing to call yet)
- Task 6 (PlayerVM unchanged, `PlayerInteractor.requestWatchStateCatchUp` already present): red `backPressed_requestsAWatchStateCatchUpAfterTheFlushBeforeBack` (:115), `backPressed_withoutSuccessfulSave_stillRequestsACatchUp` (:138); `PlayerInteractorTest.requestWatchStateCatchUp_asksForAnUnflooredCatchUp` was written after the interactor method and never ran red
- not run here (instrumented, box only): `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk`, `migrate4To5LeavesAFinishedWalkAlone`, `raiseHistoryNewestSeenNeverLowersTheMark`; they compile (`:app:compileDevDebugAndroidTestKotlin`)

**Deviations from the plan:** none in design. KSP accepted the schema-less `AutoMigration(4, 5, spec)`, so the `Migration` fallback was not needed; `5.json` is committed. Detekt forced three small additions: `private const val MAX_PER_PAGE` in `HistoryRequest` (MagicNumber from Task 1), `@Suppress("LargeClass")` on `WatchStateRepositoryTest` and `WatchStateSyncInteractorTest` (precedent: `CachedFeedTest`), `@Suppress("TooManyFunctions")` on `PlayerInteractor` (26 functions; precedent: `Paginator`).

**Files touched outside the plan:** none beyond the detekt additions above.

**For the reviewers:** `logHistoryOrder` logs through Timber (debug builds only) as the plan asks for AC-8; the line carries no timestamp, id or title. `recordCatchUpPage` is not called by anything yet (Task 4). `endSyncWindow` now pays out on every close, so the walk's own window closing is unchanged and a nested one pays out early. `catchUp` keeps its lock handshake (note before `tryLock`, re-check after `unlock`) in `catchUp`/`holdCatchUpLock`; `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun` phase (b) is the only test of the re-check and was not mutation-checked. The series step runs inside the sync window, so a refreshed series settles with the page write. `HomeVMIncrementalPublishTest` failed once in the first full run (`UncaughtExceptionsBeforeTest`), passed alone and in the second full run (the known flaky suite).

**Blocked:**

## Round 1 fixes

| Items | Commit |
|---|---|
| 1, 2 | Pay out a nested catch-up window by the database emission it earned, not by the walk's held change |
| 3, 4, 5, 7 | Bound the catch-up read, stop it on the page holding the mark, and retry series refreshes that failed |
| 8, 9 | this commit (spec rows relabelled, mutation runs below) |

Red runs (on the unchanged code, each failing on its assertion): `syncWindow_publishesACatchUpsChangeThatRoomEmitsAfterItsWindowClosed` (version not raised after the late emission), `syncWindow_aCatchUpThatChangedNothingDoesNotPayOutTheWalksHeldChange` (version raised by the empty nested close), `catchUp_stopsOnThePageThatHoldsTheMark`, `catchUp_stopsAtThePageCapAndKeepsTheMarkWhereItWas`, `catchUp_stopsWhenThePaginationDoesNotAdvance`, `catchUp_asksAgainAboutASeriesWhoseRefreshFailed`, `catchUp_asksAgainAboutASeriesThatReturnedToTheListAndLeftIt`. Green on base: the strengthened `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks` (item 7; the defect is absent, the test is a guard checked by mutation below).

Page cap: `MAX_CATCH_UP_PAGES = 1_000` (50,000 entries), injectable as `maxCatchUpPages`. At the cap, or on a response whose `pagination.current` is below the page asked, the rows read are written and the mark stays; the rest is left to the next catch-up or the walk. Nested-window release: an emission landing while a nested window is open, or the next one after it closes, is that window's; a nested window that changed nothing leaves its flag until the next emission or the outer close (at worst one walk chunk is announced early).

Mutation runs (change applied, test class run, change reverted):

| Row | Mutation | Result |
|---|---|---|
| item 7 `catchUp_readsFromPageOneWhileAWalkIsParkedBetweenChunks` | `runCatchUp` takes the walk's `mutex` | fails |
| item 8 SI `catchUp_sendsNothingOverAnIndexWithNoMark` | no-mark early return removed | fails |
| item 8 MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` | `catchUp` called before `awaitSlot` | fails (MainVMWatchStateSyncTest.kt:166) |
| item 9 AC-3 `catchUp_readsThirtyFivePagesWithoutStandingDownBetweenChunks` | pause every 15 pages in the read loop | fails |
| item 9 AC-3 `catchUp_readsPastTheWalksPageBudget` | cap at 300 pages | fails |
| item 9 AC-7 `catchUp_writesNoHistoryWhenNothingIsNewerThanTheMark` | write issued when nothing is newer | fails |

## Readiness

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

## Round 2 fixes

| Item | Commit |
|---|---|
| 1 (edge F1, state-path F1, codex minor 2) | Own the sync windows by kind instead of by nesting depth (`git log`) |

Window ownership is per `SyncWindowKind`: a catch-up's close pays its own held change plus, while a walk is open, the next emission; a walk's close pays only what landed with no catch-up open. An empty catch-up close pays nothing (AC-7).

- red: `WatchStateRepositoryTest.syncWindow_publishesACatchUpsChangeWhenItsWindowOpenedBeforeTheWalks` - version still at the start value after the catch-up closed while the walk was open (:210)
- red: `WatchStateRepositoryTest.syncWindow_publishesALateLastEmissionAfterTheCatchUpAlreadyPaidATick` - the late emission was held with the walk, version one short (:237)
- Round-1 window tests kept, switched to the kind API (first window WALK, nested one CATCH_UP); all green.
- Reviewers: after a catch-up closes inside a walk the next emission goes out even when the catch-up wrote nothing, so at worst one walk chunk is announced early (flag cleared at the walk's end).

## Round 3 fixes

| Item | Commit |
|---|---|
| 1 (state-path F1), 2 (tests F1), 3 (edge F1, tests F2) | Own a catch-up's late emission when nothing was held and pin the window kinds (`git log`) |

A catch-up's close now owns the next emission also when it held nothing (`catchUpEmissionPending = !catchUpChangeHeld || openWalkWindows > 0`), so a walk opening right after it does not hold that emission. `SyncWindowKind` moved above the cursor's KDoc.

- red: `WatchStateRepositoryTest.syncWindow_publishesTheCatchUpsLateEmissionWhenAWalkOpensRightAfterItClosed` - with the old line, version one short while the walk is open (:264)
- mutation: WALK and CATCH_UP swapped in `WatchStateSyncInteractor` -> `sync_holdsAWalkWindowAroundTheWalk` (:118) and `catchUp_holdsACatchUpWindowAroundItsWrites` (:947) fail; restored, both green. The two tests are new guards (green on base).
- Reviewers: a catch-up that wrote nothing and closed outside a walk now leaves the flag set until the next emission or a walk's end, so one later emission (any source) is published immediately, as it would be with no walk open.

## Round 4 fixes

| Item | Commit |
|---|---|
| 1 (edge F1, state-path F1, tests F1) | Replace the catch-up emission flag with a one-second deadline armed on every catch-up close (`git log`) |

`catchUpEmissionPending` became `catchUpEmissionDeadline` (clock-based). Every CATCH_UP close arms it at `clock() + 1 s`; `onIndexChanged` honours it only before the deadline and clears it when consumed, in the mark branch and at the walk's close. One second is far above Room's invalidation latency (tens of ms) and below a walk's first chunk, which needs its watching lists and a history page over the network first.

- red: `WatchStateRepositoryTest.syncWindow_aNoChangeCatchUpDoesNotLetAWalksFirstChunkThrough` - the walk's first chunk was published mid-walk (version one above the start while the walk was open)
- red: `WatchStateRepositoryTest.syncWindow_publishesTheLastEmissionOfACatchUpThatHeldAChangeWhenAWalkOpensRightAfter` - the late emission was held with the walk, version one short
- green on base, kept: the round 2 and 3 late-emission tests.
- Reviewers: a window of 1 s on the injected clock; a walk chunk landing within it after a no-change catch-up is still published early (bounded by the window).

## Round 5 fixes

| Item | Commit |
|---|---|
| 1 (state-path F1) | Measure the catch-up emission window on a monotonic clock (`git log`) |

`WatchStateRepository` takes `windowClock` (default `SystemClock::elapsedRealtime`), used only for `catchUpEmissionDeadline`; `clock` stays for observedAt/updatedAt. DI uses the default. The window tests drive `windowNow`.

- red: `WatchStateRepositoryTest.syncWindow_aWallClockStepBackDoesNotKeepTheCatchUpWindowOpen` - with the deadline on the wall clock (temporary revert) the walk's first chunk was published mid-walk (version one above the start); the round 4 window test failed there too, as it now drives `windowNow`.
- Reviewers: `SystemClock` is an Android class and a stub on the JVM. Round 6: the index collector read `windowClock()` on every emission, so a repository built from the default (the DI module test) died on its first emission; it now reads the clock only while a deadline is armed, and `WatchStateRepositoryModuleTest.repositoryFromTheModuleAdvancesVersionWhenTheSnapshotChanges` guards it.
- red: `WatchStateRepositoryModuleTest.repositoryFromTheModuleAdvancesVersionWhenTheSnapshotChanges` - on the unchanged code the collector died and the assertion at :106 failed (version never advanced).
- Outside the findings, so check.full passes: `readHistoryNewerThan` restructured to two returns (detekt ReturnCount) and one over-long test line wrapped; no behaviour change.

# Test design: watch-index-catch-up

Base e4c3b5aa. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Test paths under `app/src/test/kotlin/com/kino/puber/`; instrumented under `app/src/androidTest/kotlin/com/kino/puber/`.

Fakes and style reused: `WatchStateSyncInteractorTest` (mocked `KinoPubApiClient`, relaxed `WatchStateRepository` with the `cursor` stand-in, `stubHistory`, `pacedInteractor`, `AppForegroundState`); `WatchStateRepositoryTest` (`FakeWatchStateDao`, `FakeWatchStateSyncDao`, which gains `raiseHistoryNewestSeen`); `MainVMWatchStateSyncTest` (`vm(sync)`, `StartupSlot.WatchState.offset`); `PlayerVMInvalidationTest` (`exit(vm)`, `PlayerVMTestFixture`). `ItemDetailsRepository` is mocked in interactor tests; the class under test never is.

## Risk profile

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

## Traceability

Files: **SI** `domain/interactor/watchstate/WatchStateSyncInteractorTest.kt`, **WR** `data/repository/WatchStateRepositoryTest.kt`, **MV** `ui/feature/main/vm/MainVMWatchStateSyncTest.kt`, **PV** `ui/feature/player/vm/PlayerVMInvalidationTest.kt`, **PI** `domain/interactor/player/PlayerInteractorTest.kt`, **ID** `data/repository/ItemDetailsRepositoryTest.kt`, **HR** `data/api/history/HistoryRequestTest.kt`, **KH** `data/api/KinoPubApiClientHistoryTest.kt`, **MG** `data/db/PuberDatabaseMigrationTest.kt` (instrumented), **WD** `data/db/WatchStateDaoTest.kt` (instrumented).

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | SI `catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour` | `lastSyncAt = now - 1 min`, mark 995, pages 1-3 newer, page 4 all <= mark, page 5 stubbed: pages 1-4 read with `perpage` 50, page 5 never, both watching lists fetched, `recordCatchUpPage` gets only entries > 995 and the newest `last_seen`; fails if `staleAfter` gates the catch-up or the stop rule is off by a page |
| AC-1 | unit | P0 red | SI `catchUp_sendsNothingOverAnIndexWithNoMark` | `historyNewestSeen = 0`: no API call, returns false; fails if the catch-up walks the whole history (R6) |
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
| AC-5 | unit | P0 red | MV `onStart_doesNotCatchUpWhileTheStartupRequestsAreStillOut` | before the slot offset: no `catchUp`/`requestCatchUp`; fails if the catch-up leaves `StartupSlot.WatchState` (R9) |
| AC-5 | unit | P0 red | MV `resumed_requestsAFlooredCatchUpOnceTheStartupBurstHasPassed` | `Resumed` after the slot: `requestCatchUp(true)` once |
| AC-5 | unit | P1 | MV `resumed_requestsACatchUpEvenWhileAWalkJobIsInFlight` | `syncIfStale` suspended, `Resumed`: `requestCatchUp(true)` still called; fails if the catch-up sits behind `watchStateSyncJob?.isActive` |
| AC-5 | unit | P0 red | MV `tabSelected_requestsAFlooredCatchUpForMoviesAndSeries` | `ItemSelected` with `TabType.Movies`, then `TabType.Series`: `requestCatchUp(true)` twice |
| AC-5 | unit | P1 | MV `tabSelected_requestsNoCatchUpForOtherTabs` | Home, Collections: none; fails if the type check is dropped |
| AC-5 | unit | P1 | MV `tabSelected_requestsNoCatchUpBeforeTheStartupSlot` | Series tab before the slot: none (R9) |
| AC-5 | unit | P0 red | SI `catchUp_aFlooredRequestWithinAMinuteSendsNothing` | `catchUpFloor = 60 s`: second floored call at +59 s sends no request and returns false, at +60 s runs; fails if the floor is missing or not shared |
| AC-5 | unit | P0 red | SI `catchUp_withoutTheFloorRunsInsideTheMinute` | `catchUp(respectFloor = false)` at +1 s runs; fails if player exit is floored |
| AC-5 | unit | P1 | SI `catchUp_anUnflooredRequestDuringARunGetsOneMoreRun` | page 1 suspended in run 1, `requestCatchUp(false)`, release: page 1 read twice; fails without `rerunRequested` (R10) |
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

## Failing-first test

`catchUp_readsPastTheMarkOverAnIndexSyncedWithinTheHour`, written first. On e4c3b5aa `isRunDue` (`WatchStateSyncInteractor.kt:259-264`) returns false while `now - lastSyncAt < 1 h`, and there is no other entry point, so the newer pages are never read; the test fails there (no `catchUp`) and passes once the catch-up exists. Every other `red` row is run on the unchanged code first as well.

## Manual checks

Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease, worktree dev APK installed with `tools/android-apk-install-preserve`. Evidence through `tools/mobile-evidence-audit.sh`.

**Manual 1 (AC-8), run before the implementation relies on the stop rule.**

1. Read `/v1/history?page=N&perpage=50` for N = 1, 2, 3 with the box's session, read-only.
2. Record per page only the page number and the sequence of `last_seen` values.

Expected: `last_seen` non-increasing within and across pages 1-3. Must not happen: titles, tokens or raw response bodies in the evidence; any write request. If the order fails, the run stops for a design change.

**Manual 2 (AC-9).**

1. Settings: "Hide watched" on. D-pad to the Series tab; note a series X with unwatched episodes left, visible in the grid.
2. `KEYCODE_HOME`; wait until the app is in the background.
3. On another client, the owner watches or marks X's remaining episodes watched (an account mutation: owner's own action or explicit authorization).
4. Resume the app from the launcher; D-pad to Movies, then Series.
5. Wait up to 60 s on the Series tab; screenshot.
6. Repeat 1-5 with a cold start (`am force-stop com.kino.puber.stage`, launch) instead of the resume, on a second series Y.

Expected: X (and Y) gone from the Series grid without opening Details; other series stay. Must not happen: Details of X or Y opened, a crash, focus lost from the grid or the side menu, the grid re-paging again on later tab switches with no new history, X or Y coming back after a second resume.

## perf track

N/A: track off.

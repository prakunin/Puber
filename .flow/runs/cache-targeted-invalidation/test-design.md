# Test design: cache-targeted-invalidation

Base 40762767. Inputs: `brief.md` (ACs), `design-architect.md` (decisions D-*, risks), `research.md#Risks`. Tests under `app/src/test/kotlin/com/kino/puber/`; levels and style per `.flow/roles/test-designer.md` and `docs/architecture/testing.md`.

Helpers reused: `PlayerVMTestFixture` (`createVM` gains `tvHomeSyncCoordinator: TvHomeSyncCoordinator? = null`; the coordinator is `mockk<TvHomeSyncCoordinator>(relaxed = true)`, asserted with `verify(exactly = n) { requestRefresh(any()) }`), `CompletableDeferred` to hold a save as in `PlayerVMPlaybackFlowTest.backPressed_waitsForFinalProgressSave`, `runTest(mainDispatcher.dispatcher.scheduler)` + `advanceTimeBy` for the 30 s tracker (never `advanceUntilIdle`: the position loop never ends). `CachedFeedTest`: `FakePayloadStore` hooks (`onRead`, `onWrite`, `onWriteAll`), `runSupersededLoad`, the new `keyRecordCount`. New PlayerVM cases go in a new `PlayerVMInvalidationTest.kt` (`ui/feature/player/vm/`).

"Before `router.back`" is asserted with `verifyOrder { interactor.markItemDetailsStale(42); coordinator.requestRefresh(any()); router.back(RESULT_CONTENT_CHANGED, any()) }`.

## Risk profile

| # | Risk (source) | P | I | Score | Guard |
|---|---|---|---|---|---|
| R1 | Holder leak or early release reopens superseded-write races of 6be75d28 (design R1, research R4) | 2 | 3 | 6 | AC-7, AC-8 unit; existing `CachedFeedTest` unchanged |
| R2 | A hold site missed: a capture outside `holding` makes the record vanish mid-operation (design E1 who holds) | 2 | 3 | 6 | `whileAnOperationIsInFlight_itsKeyHasARecord` per site; `aShortOperationOnTheSameKeyDoesNotRemoveTheRecordOfAnInFlightLoad` |
| R3 | Release skipped on loader failure or cancellation: records leak, map unbounded again | 2 | 2 | 4 | `aFailedOrCancelledLoadReleasesItsKeyRecord` |
| R4 | Namespace drop without `clear()` lets a pre-drop ticket match (design D namespace) | 1 | 3 | 3 | Existing `invalidatingTheNamespaceDuringALoadKeepsThatLoadsResultOutOfTheStore`; `aNamespaceDropDuringALoadKeepsTheRecordUntilTheLoadEnds` |
| R5 | Exit or background mark skipped: next Details open shows old progress (research R1) | 2 | 3 | 6 | AC-3, AC-4, AC-6 unit; AC-6 device |
| R6 | Background mark runs before the background save lands (brief risk 2) | 2 | 2 | 4 | `onBackground_marksOnlyAfterTheHeldSaveDrains` |
| R7 | Exit returns while background flush is mid-write; scope cancel cuts `markStale` (design R2) | 2 | 2 | 4 | `backPressed_whileBackgroundFlushPending_marksOnceBeforeBack` |
| R8 | A failed mark blocks or crashes exit | 1 | 3 | 3 | `backPressed_markFailure_stillReturnsResult` |
| R9 | Exit latency from the mark before `router.back` (design R3) | 2 | 2 | 4 | AC-10 Back-to-screen time |
| R10 | Process kill in front skips the mark (brief risk 1) | 1 | 2 | 2 | Accepted (owner Q1); 10 min TTL bounds it |
| R11 | Pre-save details load writes pre-save progress fresh (design R5) | 1 | 2 | 2 | Accepted; superseded by the exit or background mark (AC-6) |
| R12 | `PUBERBASE` measure logs committed (design R7) | 2 | 1 | 2 | Reviewer checks `git diff` of the three files; no test |
| R14 | VM cleared while the background flush waits on `awaitPendingMutations` (Home, then reopen from the launcher row): owed mark and refresh dropped (triage-1 #1) | 2 | 3 | 6 | `clearedDuringBackgroundFlush_stillMarksAndRefreshes` |
| R13 | Merge overlap with `player-vm-split`, `resume-progress-anomaly` | 1 | 2 | 2 | Accepted; merge order |

## Traceability

Red = fails on 40762767 (behaviour or a missing symbol), passes after. Guard = green on both. Red-run results: plan, Implementation record.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `PlayerInteractorTest.saveWatchingTime_success_doesNotMarkItemDetailsStale` (replaces `saveWatchingTime_success_marksItemDetailsCacheStale` and `saveWatchingTimeMarksTheDetailsStaleInsteadOfDroppingThem`) | Fails if `markStale` stays in `saveWatchingTime` |
| AC-1 | unit | P0 red | `PlayerInteractorTest.markItemDetailsStale_marksTheRepositoryEntryStale` | Fails if the new call does not reach `ItemDetailsRepository.markStale(42)` or calls `invalidate` |
| AC-1 | unit | P1 guard | `PlayerInteractorTest.saveWatchingTime_failure_doesNotMarkStaleOrInvalidateItemDetailsCache` (kept) | Fails if a failed save starts marking or invalidating |
| AC-2 | unit | P0 red | `PlayerVMInvalidationTest.playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing` | Three tracker saves (`advanceTimeBy` 3 x 30 s), `onPlaybackEnded` prefetch, `SelectEpisode(1, 2)`: `markItemDetailsStale` 0, `requestRefresh` 0. Fails on base (one refresh per save); fails if the drain marks or refreshes |
| AC-3 | unit | P0 red | `PlayerVMInvalidationTest.backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack` | Two tracker saves then exit: mark 1 (id 42), refresh 1, both ordered before `router.back`, result still `PlaybackProgress`. Fails on base (3 refreshes); fails if the flush runs after `router.back` or per save |
| AC-3 | unit | P0 guard | `PlayerVMInvalidationTest.backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` | Started VM, `saveWatchingTime` throws for every save, exit: mark 0, refresh 0. Fails if `detailsStaleOwed` is set before `saveWatchingTime` returns or in its catch. No never-started-VM half: it cannot fail |
| AC-3 | unit | P1 red | `PlayerVMInvalidationTest.backPressed_markFailure_stillReturnsResult` | `markItemDetailsStale` throws: `router.back` once with `PlaybackProgress`, refresh still 1. Fails if the mark is not wrapped in `runCatchingCancellable` |
| AC-3 | unit | P1 guard | `PlayerVMPlaybackFlowTest.backPressed_waitsForFinalProgressSave`, `repeatedBackWhileFinalProgressSavePending_staysInterceptedAndReturnsOnce` (unchanged) | Fails if the flush breaks the single `router.back` after the drain |
| AC-4 | unit | P0 red | `PlayerVMInvalidationTest.onBackground_marksOnlyAfterTheHeldSaveDrains` | Background save held on `CompletableDeferred`: mark 0 and refresh 0 while held; after release mark 1, refresh 1. Fails if the flush does not await the drain |
| AC-4 | unit | P0 red | `PlayerVMInvalidationTest.backPressed_afterBackgroundFlush_withNoNewSuccessfulSave_marksAndRefreshesNothingMore` | After background flush, exit save throws: totals stay mark 1, refresh 1. Fails if flags are not cleared by the flush |
| AC-4 | unit | P1 red | `PlayerVMInvalidationTest.backPressed_whileBackgroundFlushPending_marksOnceBeforeBack` | `markItemDetailsStale` held on `CompletableDeferred` from the background flush, then exit: `router.back` not called until released; totals mark 1, refresh 1. Fails without `backgroundFlushJob?.join()` at exit |
| AC-4 | unit | P0 red | `PlayerVMInvalidationTest.clearedDuringBackgroundFlush_stillMarksAndRefreshes` | One successful tracker save; background save held on `CompletableDeferred`; `OnBackground`; scope cancel (`testCancelScope`); release the save, advance: mark 1 (id 42), refresh 1. Fails on e4d5dba2 (flush cancelled with `viewModelScope`); fails if the flush runs on a scope the clear cancels, or runs twice (on clear and after the drain) |
| AC-4 | unit | P2 red | `PlayerVMInvalidationTest.repeatedOnBackground_marksOnce` | Two `OnBackground` with no successful save between them: mark 1. Fails if chained jobs each flush |
| AC-5 | unit | P0 red | `PlayerVMInvalidationTest.watchedInFront_requestsNoRefreshUntilExit` | Manual `MarkCurrentWatched` and auto mark (`checkAutoMarkWatched` via position near end), all saves failing: refresh 0 in front, then exit refresh 1, mark 0. Fails on base (refresh per Watched); fails if `Watched` sets `detailsStaleOwed` |
| AC-5 | unit | P1 guard | `PlayerVMInvalidationTest.watchedInFront_backgroundRequestsOneRefresh` | Same with `OnBackground`: refresh 1, mark 0. Fails if the background flush ignores `homeRefreshOwed` or `Watched` sets `detailsStaleOwed` |
| AC-5 | unit | P1 guard | `PlayerVMMutationAndResultTest.playbackEnded_thenBack_returnsWatchedResult`, `episodeWatchedChanged_success_returnsWatchedResult` (unchanged) | Fails if `markContentChanged` stops merging the change set |
| AC-6 | unit | P0 guard | `DetailsVMCachedLoadTest.afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress` | Real `ItemDetailsRepository` over `ContentCacheRepository` and `FakePayloadStore`; a first load caches `before`, then `repository.markStale(42)`; API now returns `after` (items told apart by `title`); Details open maps `before` then `after`. Green on base (uses only base symbols). Fails if `markStale` is dropped or the memory tier serves a stale-marked entry as fresh (only `before` mapped). Interactor delegation: `PlayerInteractorTest.markItemDetailsStale_marksTheRepositoryEntryStale` (AC-1) |
| AC-6 | device | P0 | Manual check M1 | Fails if progress from a Home-launched session does not reach Details |
| AC-7 | unit | P0 red | `CachedFeedTest.afterEveryOperationSettles_theFeedHoldsNoKeyRecord` | 100 keys through `load` (success and stale), `put`, `putAll`, `invalidate`, `markStale`, `peek`, `peekAll`, `invalidateNamespace`: `keyRecordCount == 0`. Fails if any site never releases or removal is skipped at zero |
| AC-7 | unit | P0 red | `CachedFeedTest.whileAnOperationIsInFlight_itsKeyHasARecord` | Parameterised over `load` (loader suspended), `putAll` (`onWriteAll`), `put` (`onWrite`), `markStale`/`invalidate` (store hook), `peek`/`peekAll` (`onRead`): `keyRecordCount == 1` inside the hook, 0 after. Fails if a site from the design's hold table is not wrapped |
| AC-7 | unit | P1 red | `CachedFeedTest.aFailedOrCancelledLoadReleasesItsKeyRecord` | Loader throws; separately the collecting job is cancelled mid-loader: count 0. Fails if release is not in `finally` |
| AC-8 | unit | P0 red | `CachedFeedTest.aShortOperationOnTheSameKeyDoesNotRemoveTheRecordOfAnInFlightLoad` | Load of `k` suspended; `peek("k")` and `putAll` complete; count still 1; then `invalidate("k")`, release loader: `store.read("k")` null. Fails if release removes at any count or the second acquire does not increment |
| AC-8 | unit | P0 guard | `CachedFeedTest.aKeyWhoseRecordWasRemovedGuardsTheNextRaceAsBefore` | Key settled (count 0), then `runSupersededLoad` with `invalidate`, then with `markStale`: same outcomes as the existing cases. Fails if a recreated record does not start a fresh comparison base |
| AC-8 | unit | P1 red | `CachedFeedTest.aNamespaceDropDuringALoadKeepsTheRecordUntilTheLoadEnds` | `invalidateNamespace` mid-load: count 1 during, 0 after, store row absent. Fails if `supersedeNamespace` still clears the map (holder counts zeroed) |
| AC-8 | unit | P0 guard | Existing 42 `CachedFeedTest` cases, assertions unchanged | Fails on any changed superseded-write outcome; `invalidatingDuringALoadKeepsThatLoadsResultOutOfTheStore` also fails if `load` holds nothing |
| AC-9 | unit | P0 guard | `check.full`: `ContentCacheRepositoryTest`, `ItemDetailsRepositoryTest`, `DetailsVMCachedLoadTest`, `TvHomeSyncCoordinatorTest`, `PlayerVMPlaybackFlowTest`, `PlayerVMMutationAndResultTest` | Only AC-1 tests change assertions; `git diff` of other test files adds no changed assertion |
| AC-10 | device | P1 | Perf track below | Fails if marks or refreshes still happen in front, or exit gets slower than the threshold |

## Failing-first

`PlayerInteractorTest.saveWatchingTime_success_doesNotMarkItemDetailsStale` and `PlayerVMInvalidationTest.playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing` fail on base by behaviour (`git show 40762767:app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt` lines 273-279 call `markStale`; `PlayerVM.kt:1952-1956` refreshes per save). Write and run them red first. Tests using `markItemDetailsStale` or `keyRecordCount` fail on base by compilation; for those the Proves column names the mutation that must turn them red.

## Manual checks

Emulator, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every key via `adb -s <serial> shell input keyevent`.

| # | Step | Expected | Must not happen |
|---|---|---|---|
| M1 | Home: DPAD to a Continue Watching or history title with a known position, CENTER to play; wait past one 30 s save, DPAD_RIGHT seeks to move the position 2+ min; BACK until Home | Home shows; launcher row reflects the new position within ~5 s of Home | Crash, focus lost on Home, Back taking visibly longer than before |
| M2 | Within 10 min, open the same title's Details from Home (DPAD, CENTER) | Details progress shows the position from M1 | Pre-session position |
| M3 | Start the title again, wait past a save, press HOME, then reopen the app and its Details | Details shows the position at HOME | Pre-session position; a second launcher refresh on return |
| M4 | Series: play, let the next-episode countdown switch episodes (or Episodes panel, CENTER on next) | Next episode starts without a details spinner | Crash; stall waiting for details |

## Perf track (AC-10)

| Metric | Method | Baseline | Pass |
|---|---|---|---|
| `markItemDetailsStale` / `markStale` count while in front | Temporary uncommitted `PUBERBASE` log, `adb -s <serial> logcat -s PUBERBASE`, 30 min episode playback with one next-episode switch, then exit | `baseline.md` (qa-device, in progress): expected ~1 per 30 s save | 0 in front; 1 at exit |
| `TvHomeSyncCoordinator.refreshNow` count | Same log (`TvHomeSyncCoordinator.kt:30` in the measure patch) | `baseline.md` | 0 in front; 1 within 3 s after exit |
| Details network loads during playback | Same log in `ItemDetailsRepository` | `baseline.md` | Only those the episode switch and prefetch need with a warm record: at most the baseline, expected fewer |
| Back to screen-below time | Log at exit start and at the screen below's first frame, median of 5 exits | `baseline.md` | After median <= baseline median + 150 ms |

Same emulator, same dev build type, same title before and after.

## UNTESTABLE

N/A: every AC has a test or a device check.

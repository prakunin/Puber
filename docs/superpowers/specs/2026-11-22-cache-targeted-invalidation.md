# Targeted cache invalidation: no markStale per progress save, TV home refresh at exit and background, bounded CachedFeed key records

Status: approved
Date: 2026-11-22
Slug: cache-targeted-invalidation
Kind: refactor
Size: M
Tracks: perf

## Problem / Why

### Request
research.md: Request. Parent seed: `.flow/runs/cache-layer-rework/brief.md` Split row 4, AC-7, AC-8, AC-9. Absorbs `cached-feed-key-maps` (PERF-4, `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md:111`).

### Current state
research.md: Current state in code (Progress save and markStale, TV home refresh, CachedFeed epoch maps, Tests).

### Scope
Run option A (research.md: Options). Paths under `app/src/main/java/com/kino/puber/`.

| Item | Change | Where |
|---|---|---|
| M1 save | `saveWatchingTime` writes the API only; a separate interactor call marks the item's details stale | `domain/interactor/player/PlayerInteractor.kt:273-279` |
| M1 mark points | `PlayerVM` keeps a "saved since last mark" flag set by a successful drain; at `exitPlayer` (inside `closeJob`, after `awaitPendingMutations`, before `router.back`) and at `pauseForBackground` (after the queued save drains) it marks `params.itemId` stale once and clears the flag | `ui/feature/player/vm/PlayerVM.kt:1484-1491,1878-1918,1985-2003` |
| T1 | `markContentChanged` records the change and a "home refresh owed" flag instead of calling `requestRefresh`; the same two points issue one `requestRefresh()` when the flag is set. Coordinator, `DetailsVM`, `HistoryVM`, `MainVM` unchanged | `PlayerVM.kt:1952-1956` |
| E1 | The four per-key counters become one per-key record with a holder count; every operation that captures an `Epoch` acquires the record and releases it when done; the record is removed atomically (`compute`) at count zero. `namespaceEpoch` and `store.generation` unchanged | `data/cache/CachedFeed.kt:95-131,143-166,409-442` and the capture sites listed in research.md (CachedFeed epoch maps) |

### Risks (analyst)
research.md: Risks holds the table; rows 1-5 are guarded by AC-4/AC-6, AC-10, AC-3, AC-8 and AC-7/AC-8 here. Additions:

| Risk | Guard |
|---|---|
| `PlayerVM` cleared without `exitPlayer` (process kill in front, a navigation that replaces the stack) skips the mark; a Details open from another screen shows pre-session progress up to the 10 min TTL | Background mark covers Home/app switch; accepted residue, Q1 |
| The background mark runs before the background save lands and marks nothing | AC-4 requires the mark after the drain |
| `player-vm-split` and `resume-progress-anomaly` edit the same save path | This run merges first; never in parallel with another `CachedFeed.kt` run |

## Acceptance criteria

Unit tests under `app/src/test/kotlin/com/kino/puber/` unless named otherwise.

- AC-1. Given a successful `saveWatchingTime`, When it returns, Then `ItemDetailsRepository.markStale` was not called (`PlayerInteractorTest.kt:650-690` rewritten to the new contract).
- AC-2. Given playback in front with several 30 s progress saves, a `prefetchNextEpisode` and a next-episode switch, When the player stays in front, Then details are marked stale zero times and `TvHomeSyncCoordinator.requestRefresh` is called zero times (PlayerVM test with a fake interactor and a mock coordinator).
- AC-3. Given at least one successful save since the last mark, When the player exits, Then the item is marked stale exactly once and `requestRefresh` is called exactly once, both before `router.back`, and the returned change set still contains `PlaybackProgress`; Given no successful save (all failed or none queued), Then neither happens (PlayerVM test).
- AC-4. Given at least one successful save, When `PlayerAction.OnBackground` arrives, Then after the background save drains the item is marked stale once and `requestRefresh` is called once; a following exit with no new successful save marks and refreshes nothing (PlayerVM test).
- AC-5. Given a `Watched` change during playback (auto or manual), When the player is in front, Then no `requestRefresh` is issued; the change makes exit or background issue one (PlayerVM test).
- AC-6. Given a title played from Home (no Details below) and the player exited after a progress save, When that title's Details opens within 10 min, Then it shows the saved progress (emulator observation, D-pad driven).
- AC-7. Given a feed that ran loads, puts, `putAll`, invalidations, `markStale` and peeks on many distinct keys to completion, When all have settled, Then the feed holds no per-key record; While an operation on a key is in flight, Then that key's record exists (`CachedFeedTest` through an `internal` size accessor).
- AC-8. Given concurrent load, put, invalidate and namespace drop on one key, When a record's holder count reaches zero and a new operation starts, Then the superseded-write outcomes are the same as today (new `CachedFeedTest` cases for record removal and recreation under contention); existing `CachedFeedTest` assertions unchanged.
- AC-9. `ContentCacheRepositoryTest`, `ItemDetailsRepositoryTest`, `DetailsVMCachedLoadTest`, `TvHomeSyncCoordinatorTest`, `PlayerVMPlaybackFlowTest`, `PlayerVMMutationAndResultTest` stay green with assertions changed only where AC-1 changes the contract; `check.full` passes.
- AC-10 (perf). On the emulator, before and after, 30 min of episode playback with one next-episode switch, then exit: count of `markItemDetailsStale`, `TvHomeSyncCoordinator.refreshNow` and details network loads, and the time from Back to the screen below (temporary uncommitted log, as in `startup-settled-signal`). After: zero of each while in front, one mark and one refresh at exit. Recorded in evidence.

## Out of scope

The `watching/marktime` response that fails to decode on this account (`ApiError.Serialization`, seen in the baseline and since 2026-11-12) belongs to `resume-progress-anomaly`. The TV home coordinator, DI wiring, the `PlayerVM` constructor and the UI stay as they are (design-architect.md).

## Design

Base 40762767. Paths under `app/src/main/java/com/kino/puber/` unless named otherwise. Citations are at base; the worktree's uncommitted measure patch shifts `data/cache/ContentCacheRepository.kt` by one line after `:293`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Who marks details stale after progress saves | `PlayerVM`, through a new `PlayerInteractor.markItemDetailsStale(id)` that delegates to `ItemDetailsRepository.markStale` (`data/repository/ItemDetailsRepository.kt:84`); `saveWatchingTime` (`domain/interactor/player/PlayerInteractor.kt:273`) keeps only the API write | Only the VM knows exit and background; the VM never reaches a repository directly |
| What the VM tracks | Two `Main`-confined flags: `detailsStaleOwed` (set after a successful `saveWatchingTime` in the drain, `ui/feature/player/vm/PlayerVM.kt:1901-1907`) and `homeRefreshOwed` (set by `markContentChanged` for `Watched` or `PlaybackProgress`, `:1952`) | `Watched` alone owes a refresh but no mark: `markAsWatched` already invalidates (`PlayerInteractor.kt:283`) |
| One flush for both moments | `private suspend fun flushOwedInvalidation()`: read-and-clear each flag before suspending, then `interactor.markItemDetailsStale(params.itemId)` inside `runCatchingCancellable`, then `tvHomeSyncCoordinator?.requestRefresh()` | Clearing before the suspension makes exit and background unable to issue the pair twice; a failed mark must not block `router.back` |
| Exit ordering | `closeJob` (`PlayerVM.kt:2000-2003`) becomes: `awaitPendingMutations()`, `backgroundFlushJob?.join()`, `flushOwedInvalidation()`, `router.back(...)` | AC-3: both effects before `router.back`; the join stops exit returning while a background mark is mid-write and then being cancelled with the scope |
| Background ordering | `pauseForBackground` (`PlayerVM.kt:1484-1491`) after `saveCurrentPosition()` launches `backgroundFlushJob` = `launch { previous?.join(); awaitPendingMutations(); flushOwedInvalidation() }`, where `previous` is the prior `backgroundFlushJob` | AC-4: mark after the drain. Not a `launchMutation`: it awaits `pendingMutations` itself, so registering it there would wait on itself, and exit already joins it |
| TV home request mode | Plain `requestRefresh()` (2 s debounce, `core/tvhome/TvHomeSyncCoordinator.kt:21-27`); coordinator unchanged | Brief T1; the debounce costs nothing once per exit |
| E1 record shape | One immutable `private data class KeyRecord(holders: Int, epoch, invalidations, writes, supersedeWrite: Long)` in one `ConcurrentHashMap<String, KeyRecord>`, replacing `keyEpochs`, `keyInvalidations`, `keyWrites`, `keySupersedeWrites` (`data/cache/CachedFeed.kt:95,106,120,131`); every change is one `compute` | Acquire, release-and-remove and each counter bump are atomic per key; `putAll`'s three separate merges (`:353-355`) become one update |
| E1 hold scope | `private inline fun <T> holding(keys: Collection<String>, block: () -> T): T` (acquire all distinct keys, `try` block, `finally` release); `release` returns `null` from `compute` at holder count 1 | `inline` keeps the suspend block legal; one helper serves single and batch keys |
| E1 who holds | `load` (whole flow body, `CachedFeed.kt:173-255`), `markStale` (`:264`), `invalidate` (`:278`), `peek` from the first `epochOf` (`:302`), `peekAll` for `missing` (`:321`), `putAll` for `values.keys` (`:352`) | Every `epochOf` call and every counter bump is then inside a hold. The loader runs in the leader's own coroutine (`core/collections/TypedTtlCache.kt:84-97,214-221`), so the `load` hold covers the loader's captures at `:202,207,218` |
| Bumps outside a hold | Bumps use `compute` with `(r ?: KeyRecord())`, not `computeIfPresent` | A missed hold then shows as a leaked record in AC-7 instead of a silently lost barrier |
| Namespace drop | `supersedeNamespace` (`:435-442`) keeps only `namespaceEpoch.incrementAndGet()`; the two `clear()` calls go | Clearing would zero holder counts. Equivalent outcome: every epoch captured before the drop differs by `namespace`, and counters captured after it are compared only against later increments of themselves |
| Test seam | `@VisibleForTesting internal val keyRecordCount: Int` on `CachedFeed` | AC-7 |

### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `data/cache/CachedFeed.kt:95-131` | Four maps -> `keyRecords`; KDoc of `keyWrites` "never cleared" (`:117-119`) replaced by: a record lives exactly as long as some operation holds an `Epoch` of it |
| API / repository | `data/cache/CachedFeed.kt:409-433` | `epochOf` reads one record (zeros when absent); `supersedeKey` and `advanceKey` each one `compute` |
| API / repository | `data/cache/CachedFeed.kt:173,264,278,297,309,349` | Bodies wrapped in `holding(...)` per the table above; comparisons and control flow unchanged |
| API / repository | `data/cache/CachedFeed.kt:435-442` | Clears removed |
| API / repository | `data/repository/ItemDetailsRepository.kt:84`, `data/cache/ContentCacheRepository.kt:293-300` | Unchanged |
| Domain | `domain/interactor/player/PlayerInteractor.kt:273-279` | Drop `markStale` and its comment; add `suspend fun markItemDetailsStale(id: Int)` |
| ViewModel state and actions | `ui/feature/player/vm/PlayerVM.kt:186-189` | Fields `detailsStaleOwed`, `homeRefreshOwed`, `backgroundFlushJob` |
| ViewModel state and actions | `ui/feature/player/vm/PlayerVM.kt:1907`, `:1952-1956` | Drain sets `detailsStaleOwed`; `markContentChanged` merges the change set as today and sets `homeRefreshOwed` instead of `requestRefresh` |
| ViewModel state and actions | `ui/feature/player/vm/PlayerVM.kt:1484-1491`, `:1985-2003` | Background flush job; exit flush before `router.back` |
| Tests (fixture) | `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt` `createVM` | Optional `tvHomeSyncCoordinator` parameter passed to `PlayerVM`; `interactor` is `mockk(relaxUnitFun = true)`, so `markItemDetailsStale` needs no stub |
| Screen and DI module, Compose content, resources | N/A: constructor of `PlayerVM` unchanged (`PlayerVM.kt:64-75`) |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| Mark stale inside `onCleared` (`PlayerVM.kt:2008`) | `viewModelScope` is cancelled there; the write would not run |
| Background flush via `launchMutation` | It would await itself in `awaitPendingMutations` (`:1977-1983`) |
| `requestRefresh(immediate = true)` at exit | Coordinator debounce already coalesces; no AC asks for sooner |
| E1 with mutable record under `synchronized` | `compute` on an immutable record gives the same atomicity without a second lock |
| E1 holding only around the loader | `readCached`, `peek` and `putAll` capture epochs outside it (`:302,321,358,517`) |

### Risks

| Risk | Where | Guard |
|---|---|---|
| Holder-count leak or early release reopens the superseded-write races of 6be75d28 (a record removed while an `Epoch` of it is held, then recreated at zeros, lets an old ticket match a new one) | `CachedFeed.kt` `holding`, every site in E1 hold table | Every `epochOf` inside a hold; `finally` release; AC-7 count back to zero and record present mid-flight; AC-8 removal/recreation under contention; existing `CachedFeedTest` unchanged |
| Exit returns while the background mark is still writing, then scope cancellation interrupts `markStale` between its barriers and `store.touch` (`CachedFeed.kt:264-275`) | `PlayerVM.kt` `closeJob` | `backgroundFlushJob?.join()` before the exit flush |
| Exit latency: one decode, encode and Room write on `decodeDispatcher` before `router.back` | `ContentCacheRepository.kt:293-300` | AC-10 Back-to-screen time before/after |
| Process killed with the player in front skips the mark; Details shows pre-session progress up to 10 min (`data/cache/CacheKeys.kt:51`) | `PlayerVM.kt:2008` | Accepted (owner Q1); background mark covers Home and app switch |
| A details load started before a save writes pre-save progress fresh | `CachedFeed.kt:198-235` | Superseded by the exit or background mark; prefetch reads are of the same session |
| Repeated `OnBackground` chains flush jobs | `PlayerVM.kt` `pauseForBackground` | Each job joins the previous; flags make later ones no-ops |
| The worktree's uncommitted `PUBERBASE` measure logs (`core/tvhome/TvHomeSyncCoordinator.kt:30`, `data/cache/ContentCacheRepository.kt`, `data/repository/ItemDetailsRepository.kt`) land in a commit | worktree `git status` | Revert or stash before the first commit; reapply only for the AC-10 after-run |
| Merge overlap with `player-vm-split`, `resume-progress-anomaly` | `PlayerVM.kt:1878-2003` | This run merges first |

### Open questions

None.

## Test design

Base 40762767. Inputs: `brief.md` (ACs), `design-architect.md` (decisions D-*, risks), `research.md#Risks`. Tests under `app/src/test/kotlin/com/kino/puber/`; levels and style per `.flow/roles/test-designer.md` and `docs/architecture/testing.md`.

Helpers reused: `PlayerVMTestFixture` (`createVM` gains `tvHomeSyncCoordinator: TvHomeSyncCoordinator? = null`; the coordinator is `mockk<TvHomeSyncCoordinator>(relaxed = true)`, asserted with `verify(exactly = n) { requestRefresh(any()) }`), `CompletableDeferred` to hold a save as in `PlayerVMPlaybackFlowTest.backPressed_waitsForFinalProgressSave`, `runTest(mainDispatcher.dispatcher.scheduler)` + `advanceTimeBy` for the 30 s tracker (never `advanceUntilIdle`: the position loop never ends). `CachedFeedTest`: `FakePayloadStore` hooks (`onRead`, `onWrite`, `onWriteAll`), `runSupersededLoad`, the new `keyRecordCount`. New PlayerVM cases go in a new `PlayerVMInvalidationTest.kt` (`ui/feature/player/vm/`).

"Before `router.back`" is asserted with `verifyOrder { interactor.markItemDetailsStale(42); coordinator.requestRefresh(any()); router.back(RESULT_CONTENT_CHANGED, any()) }`.

### Risk profile

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

### Traceability

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

### Failing-first

`PlayerInteractorTest.saveWatchingTime_success_doesNotMarkItemDetailsStale` and `PlayerVMInvalidationTest.playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing` fail on base by behaviour (`git show 40762767:app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt` lines 273-279 call `markStale`; `PlayerVM.kt:1952-1956` refreshes per save). Write and run them red first. Tests using `markItemDetailsStale` or `keyRecordCount` fail on base by compilation; for those the Proves column names the mutation that must turn them red.

### Manual checks

Emulator, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held, every key via `adb -s <serial> shell input keyevent`.

| # | Step | Expected | Must not happen |
|---|---|---|---|
| M1 | Home: DPAD to a Continue Watching or history title with a known position, CENTER to play; wait past one 30 s save, DPAD_RIGHT seeks to move the position 2+ min; BACK until Home | Home shows; launcher row reflects the new position within ~5 s of Home | Crash, focus lost on Home, Back taking visibly longer than before |
| M2 | Within 10 min, open the same title's Details from Home (DPAD, CENTER) | Details progress shows the position from M1 | Pre-session position |
| M3 | Start the title again, wait past a save, press HOME, then reopen the app and its Details | Details shows the position at HOME | Pre-session position; a second launcher refresh on return |
| M4 | Series: play, let the next-episode countdown switch episodes (or Episodes panel, CENTER on next) | Next episode starts without a details spinner | Crash; stall waiting for details |

### Perf track (AC-10)

| Metric | Method | Baseline | Pass |
|---|---|---|---|
| `markItemDetailsStale` / `markStale` count while in front | Temporary uncommitted `PUBERBASE` log, `adb -s <serial> logcat -s PUBERBASE`, 30 min episode playback with one next-episode switch, then exit | `baseline.md` (qa-device, in progress): expected ~1 per 30 s save | 0 in front; 1 at exit |
| `TvHomeSyncCoordinator.refreshNow` count | Same log (`TvHomeSyncCoordinator.kt:30` in the measure patch) | `baseline.md` | 0 in front; 1 within 3 s after exit |
| Details network loads during playback | Same log in `ItemDetailsRepository` | `baseline.md` | Only those the episode switch and prefetch need with a warm record: at most the baseline, expected fewer |
| Back to screen-below time | Log at exit start and at the screen below's first frame, median of 5 exits | `baseline.md` | After median <= baseline median + 150 ms |

Same emulator, same dev build type, same title before and after.

### UNTESTABLE

N/A: every AC has a test or a device check.

### Baseline (qa-device, unchanged build)

Date 2026-11-22. Mode baseline, track perf. Base 40762767, worktree clean apart from a temporary measure patch (reverted after the run; kept as `.flow/runs/cache-targeted-invalidation/baseline-measure.patch`).

### Build and device

- Build: worktree own `app-dev-debug.apk` (dev debug, 1.12.4-dev, versionCode 2090000000), base 40762767 + measure patch. Installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, no destructive action). On-device sha256 of base.apk equals the local APK: `a5e0bfdc60f18027bf29d7243fd7269d992d95c0f18168ea6a6f9d468df6fe2f`.
- Device: emulator-5554 (AVD puber_tv_36, API 36 TV). It was found dead at the start of the run and was booted headless without wipe (`-no-window -no-snapshot-save`); app data and login were intact. Room database `puber.db` user_version = 4 (master v4).
- Lease: acquired and released through `tools/emulator-resource-lock.sh`.
- Title: "Odd Taxi" (id 76651), series, 13 episodes, resumed from a saved position; one next-episode switch (episode 1 -> 2) happened by itself during the window.

### Method

Temporary uncommitted `Log.i("PUBERBASE", ...)` lines (patch file above):

| Marker | Place |
|---|---|
| `markItemDetailsStale` | `ContentCacheRepository.markItemDetailsStale` |
| `refreshNow` | `TvHomeSyncCoordinator.refreshNow` |
| `detailsNetworkLoad` | `ItemDetailsRepository` just before `api.getItemDetails` |
| `saveOk` / `saveFail` | after the API write in `PlayerInteractor.saveWatchingTime` / in the `PlayerVM` drain catch |

Streamed with `adb logcat -v time -s PUBERBASE` (16 MiB buffer; the default 256 KiB buffer wrapped within ~9 min because of the request logging and lost the first attempt). Markers only; no raw logcat is kept.

#### Measurement shim (needed, read this)

On this build and account the `watching/marktime` call returns HTTP 200 but the response body fails to decode (`ApiError.Serialization: Malformed response`). `PlayerVM` swallows that as "save failed", so `saveWatchingTime` throws before `markStale` and `markContentChanged` run. The server did store the position (the resume dialog showed the new position after reopening). So with the unmodified base build the per-save effects never fire on this emulator and the baseline would read 0, which is not the behaviour on a working account. For the measurement only, `saveWatchingTime` ignores `ApiError.Serialization` from `setWatchingTime` (patch, line 1 of the interactor hunk). The same shim must be applied to the "after" build, otherwise "after" also reads 0 and shows nothing. This looks like part of `resume-progress-anomaly` ("progress written but no resume"); not investigated here.

### Result (base, emulator, in-front playback)

The 30 min target was not reachable: after the episode switch, episode 2 failed with ExoPlayer "Source error" at 09:30:44 device time (and again after two Retry presses, stuck buffering at 1:30). Effective in-front window: **09:23:10 to 09:30:44 = 7.6 min** (resume at 18:xx of episode 1, auto switch to episode 2 at ~09:29:07).

| Counter (window 09:23-09:30:50) | Count | Rate |
|---|---|---|
| successful progress saves (30 s tracker) | 15 | 2.0 / min |
| `markItemDetailsStale` | 15 | 2.0 / min (one per save) |
| `TvHomeSyncCoordinator.refreshNow` | 16 | 2.1 / min (15 per save, +1 after the episode end Watched change, 09:28:03) |
| details network loads | 3 | all for the playing title: 09:28:00 prefetch of the next episode (right after a mark), 09:29:06 the episode switch (`getItemDetails` waited for the refetch), 09:29:49 prefetch of the following episode |

Extrapolated to the brief's 30 min of playback: about 60 marks, about 60 refreshes, and a details refetch at every next-episode switch and prefetch, since each save re-marks the record stale.

Exit (Back pressed at 09:49:21 from a stalled player, one more save pending): 1 save, 1 `markItemDetailsStale`, 1 `refreshNow` (2 s debounce, 09:49:24), 1 details network load (the Details screen below reloads forced, `DetailsVM` returns-change path, 09:49:22.7).

Back to the screen below: Details content visible in the first screenshot frame at about 1.5 s; the frame at 0.7 s was still the player (frames ~0.8 s apart, adb screencap resolution, one run). Coarse; do not compare below ~1 s.

Not measured: start wait of the next episode in seconds (no start marker in the temporary log; the `getItemDetails` network refetch at the switch is what AC-10 expects to disappear). Spread: single 7.6 min run, saves landed at 30.0 s intervals (± 0.3 s), so the per-minute counts are deterministic; a repeat adds nothing except the switch timing.

### Account mutations

Only normal playback: watching/marktime saves for Odd Taxi episode 1 and 2 (progress moved forward; the title now shows 1 episode watched) and the Retry/Back presses. No watched toggles, bookmarks or other writes by hand.

### Evidence files

- `docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-baseline-markers.log` (PUBERBASE markers only).
- `docs/superpowers/evidence/2026-11-22-cache-targeted-invalidation-baseline-device.md`.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | When does progress reach later Details opens? | yes | answered | mark stale once at exit and once at background, only after a successful save; process kill falls back to the 10 min TTL |
| Q2 | Defer the TV home refresh for Watched during playback to exit and background? | yes | answered | yes |
| Q3 | Keep the epoch-map bound (E1) in this run? | yes | answered | yes |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-22 | Q1, Q2, Q3 as recommended | owner (standing instruction: recommended) |
| 2026-11-22 | codex: required; round caps lifted | owner |
| 2026-11-22 | Gate 1: approve (size M, track perf, 10 ACs) | owner (standing instruction: recommended) |

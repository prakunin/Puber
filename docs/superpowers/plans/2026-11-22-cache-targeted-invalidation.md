# Targeted cache invalidation - plan

**Spec:** `docs/superpowers/specs/2026-11-22-cache-targeted-invalidation.md`
**Goal:** Mark details stale and refresh the TV home row once at player exit and background instead of on every progress save, and bound `CachedFeed`'s per-key counters to keys with an operation in flight.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Work in `/home/user1/orca/workspaces/Puber/cache-targeted-invalidation` (branch `prakunin/cache-targeted-invalidation`, base 40762767). Never `./gradlew` there; every Gradle call is prefixed `AGENT_GRADLE_SERIAL=1`.
- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- Before every commit `rg PUBERBASE app/src` prints nothing (spec Design, Risks: measure logs).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: `M/` = `app/src/main/java/com/kino/puber/`, `T/` = `app/src/test/kotlin/com/kino/puber/`. Line numbers are at base 40762767.
- Out of scope: `TvHomeSyncCoordinator`, DI, the `PlayerVM` constructor, UI, `ContentCacheRepository`, `ItemDetailsRepository`, the `watching/marktime` decode failure.

## AC coverage

| AC | Task | Tests (spec Test design) |
|---|---|---|
| AC-1 | 1 | `PlayerInteractorTest`: 2 new, 1 kept |
| AC-2, AC-3, AC-4, AC-5 | 2 | `PlayerVMInvalidationTest` (10 cases); guards in `PlayerVMPlaybackFlowTest`, `PlayerVMMutationAndResultTest` unchanged |
| AC-7, AC-8 | 3 | `CachedFeedTest`: 6 new, 42 existing unchanged |
| AC-9 | 3 (full gate) | named suites green, no assertion changed outside `PlayerInteractorTest` |
| AC-6, AC-10 | Hand-off (qa-device) | Manual checks M1-M4, perf track |

---

## Tasks

### Task 1: `saveWatchingTime` writes the API only; `markItemDetailsStale` added

**Covers:** AC-1
**Files:**
- Modify: `M/domain/interactor/player/PlayerInteractor.kt` - in `saveWatchingTime` (`:273-279`) delete the comment (`:275-277`) and `itemDetailsRepository.markStale(id)` (`:278`); add below it `suspend fun markItemDetailsStale(id: Int) { itemDetailsRepository.markStale(id) }` (`ItemDetailsRepository.markStale`, `M/data/repository/ItemDetailsRepository.kt:84`).
- Test: `T/domain/interactor/player/PlayerInteractorTest.kt` (strict `mockk<ItemDetailsRepository>()`, `:32`).

**Consumes:** `ItemDetailsRepository.markStale(itemId: Int)` (`ItemDetailsRepository.kt:84`).
**Produces:** `PlayerInteractor.markItemDetailsStale(id: Int)` (suspend, Unit), used by Task 2.

- [x] Write `saveWatchingTime_success_doesNotMarkItemDetailsStale` replacing `saveWatchingTime_success_marksItemDetailsCacheStale` (`:650`) and `saveWatchingTimeMarksTheDetailsStaleInsteadOfDroppingThem` (`:682`): stub `api.setWatchingTime(42, 1, 120, 2)` success and `markStale(any())` returns Unit; after the call `coVerify(exactly = 1) { api.setWatchingTime(42, 1, 120, 2) }`, `coVerify(exactly = 0) { itemDetailsRepository.markStale(any()) }`, `coVerify(exactly = 0) { itemDetailsRepository.invalidate(any()) }`. Run it red on the unchanged interactor; record in the Implementation record.
- [x] Add `markItemDetailsStale_marksTheRepositoryEntryStale`: `markStale(any())` returns Unit; `interactor.markItemDetailsStale(42)`; `coVerify(exactly = 1) { markStale(42) }`, `coVerify(exactly = 0) { invalidate(any()) }`.
- [x] Keep `saveWatchingTime_failure_doesNotMarkStaleOrInvalidateItemDetailsCache` (`:664`) unchanged.
- [x] Change `PlayerInteractor`; run the fast gate and `PlayerInteractorTest`; commit.

**Done when:** `PlayerInteractorTest` green; `rg -n markStale M/domain/interactor/player/PlayerInteractor.kt` shows only the line inside `markItemDetailsStale`.

---

### Task 2: `PlayerVM` marks and refreshes once at exit and background

**Covers:** AC-2, AC-3, AC-4, AC-5
**Files:**
- Modify: `M/ui/feature/player/vm/PlayerVM.kt`
  - Fields next to `progressDrainJob` / `closeJob` (`:186-189`): `private var detailsStaleOwed = false`, `private var homeRefreshOwed = false`, `private var backgroundFlushJob: Job? = null` (`Job` imported `:53`). All three are touched only on `Main`.
  - Drain in `saveCurrentPosition` (`:1878-1918`): after `interactor.saveWatchingTime(...)` returns (`:1901-1906`) set `detailsStaleOwed = true`, then `markContentChanged(PlaybackProgress)` (`:1907`) as today. The catch (`:1908-1912`) sets nothing.
  - `markContentChanged` (`:1952-1957`): keep the merge (`:1953`); replace `tvHomeSyncCoordinator?.requestRefresh()` (`:1955`) by `homeRefreshOwed = true`. Watched callers (`:1106,1151,1202,1221`) unchanged.
  - New `private suspend fun flushOwedInvalidation()` (sketch below); `runCatchingCancellable` is `M/core/coroutine/CoroutinesErrorHandler.kt:37`, already imported (`PlayerVM.kt:10`).
  - `pauseForBackground` (`:1484-1491`): after `saveCurrentPosition()` (`:1490`) chain a background flush job (sketch below). Not via `launchMutation` (`:1959-1975`): `awaitPendingMutations` (`:1977-1983`) would wait on itself.
  - `exitPlayer` `closeJob` (`:2000-2003`): `awaitPendingMutations()`, `backgroundFlushJob?.join()`, `flushOwedInvalidation()`, then `router.back(RESULT_CONTENT_CHANGED, contentChanges)`.
  - `onCleared` (`:2008-2018`) unchanged.
- Modify: `T/ui/feature/player/vm/PlayerVMTestFixture.kt` - `createVM` (`:141-154`) gains `tvHomeSyncCoordinator: TvHomeSyncCoordinator? = null` passed to `PlayerVM(tvHomeSyncCoordinator = ...)` (`PlayerVM.kt:74`). `interactor` is `mockk(relaxUnitFun = true)` (`:85`), so `saveWatchingTime` and `markItemDetailsStale` succeed unless a test stubs them.
- Create: `T/ui/feature/player/vm/PlayerVMInvalidationTest.kt` extending `PlayerVMTestFixture`, `MainDispatcherExtension` registered as in `PlayerVMPlaybackFlowTest.kt:42-47`.

```kotlin
private suspend fun flushOwedInvalidation() {
    val markDetails = detailsStaleOwed
    val refreshHome = homeRefreshOwed
    detailsStaleOwed = false   // cleared before any suspension: exit and background cannot both issue the pair
    homeRefreshOwed = false
    if (markDetails) runCatchingCancellable { interactor.markItemDetailsStale(params.itemId) }
    if (refreshHome) tvHomeSyncCoordinator?.requestRefresh()
}

// pauseForBackground, after saveCurrentPosition()
val previous = backgroundFlushJob
backgroundFlushJob = launch {
    previous?.join()
    awaitPendingMutations()
    flushOwedInvalidation()
}
```

**Consumes:** `PlayerInteractor.markItemDetailsStale(id: Int)` (Task 1); `TvHomeSyncCoordinator.requestRefresh(immediate: Boolean = false)` (`M/core/tvhome/TvHomeSyncCoordinator.kt:21`).
**Produces:** nothing consumed later.

Test conventions for `PlayerVMInvalidationTest`:
- Coordinator: `mockk<TvHomeSyncCoordinator>(relaxed = true)`, passed to `createVM`, then `testOnStart()` (as `startedVM()`, fixture `:190`). Counts: `coVerify(exactly = n) { interactor.markItemDetailsStale(42) }`, `verify(exactly = n) { coordinator.requestRefresh(any()) }`.
- "Before `router.back`": `coVerifyOrder { interactor.markItemDetailsStale(42); coordinator.requestRefresh(any()); router.back(RESULT_CONTENT_CHANGED, any()) }` (`coVerifyOrder`, since the mark is suspend).
- Exit: two `OnBackPressed` (the first hides the initial controls, `PlayerVMPlaybackFlowTest.kt:117-119`). Result: `verifyContentChangeResult(...)` (fixture `:194`).
- Tracker saves: `runTest(mainDispatcher.dispatcher.scheduler)` + `advanceTimeBy(30_000)` per save; end the block with `vm.testCancelScope()`; never `advanceUntilIdle` (pattern `PlayerVMNextEpisodePrefetchTest.kt:46-63`). Tests without the tracker use no `runTest`.
- Held calls: `CompletableDeferred` in `coAnswers` (`PlayerVMPlaybackFlowTest.kt:110-124`). Startup does no save; the first `saveWatchingTime` is the first tracker, background or exit save (`PlayerVMPlaybackFlowTest.kt:229-235`). A success-then-failure sequence is `coEvery { interactor.saveWatchingTime(any(), any(), any(), any()) } just Runs andThenThrows IllegalStateException()` (or `returnsMany`/`answers` equivalent).
- Auto watched mark: `callbackSlot.captured.onPlaybackEnded()` reaches `autoMarkCurrentAsWatched` (`PlayerVM.kt:1380-1381,1930-1931`), the same entry the position check uses (`:1921-1927`). After a watched mark the content is watched and a second mark on the same VM is blocked (`:1244-1247`), so manual and auto use one VM each.

| Test | Setup and assertion |
|---|---|
| `playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing` | `findNextEpisode` returns `1 to 2`; three `advanceTimeBy(30_000)`; `onPlaybackEnded()` (prefetch `:1656`); `onAction(SelectEpisode(1, 2))`: `saveWatchingTime` called at least 3 times, mark 0, refresh 0. Write first; run red after Task 1 (base refreshes per save) |
| `backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack` | two tracker saves, exit: mark 1, refresh 1, `coVerifyOrder` as above, result `PlaybackProgress` |
| `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` | `saveWatchingTime` throws for every call, exit: mark 0, refresh 0; second VM exited before content (no `testOnStart`, or `getItemDetails` held): mark 0, refresh 0 |
| `backPressed_markFailure_stillReturnsResult` | `markItemDetailsStale` throws; one tracker save, exit: `router.back` exactly once with `PlaybackProgress`, refresh 1 |
| `onBackground_marksOnlyAfterTheHeldSaveDrains` | background save held on a `CompletableDeferred`; `OnBackground`: mark 0, refresh 0; release: mark 1, refresh 1 |
| `backPressed_afterBackgroundFlush_withNoNewSuccessfulSave_marksAndRefreshesNothingMore` | save succeeds then throws; `OnBackground`, then exit: totals mark 1, refresh 1, `router.back` once |
| `backPressed_whileBackgroundFlushPending_marksOnceBeforeBack` | save succeeds then throws (so exit owes nothing new); `markItemDetailsStale` held on a `CompletableDeferred`; `OnBackground`, exit: `router.back` 0 while held; release: `router.back` 1, totals mark 1, refresh 1 |
| `repeatedOnBackground_marksOnce` | save succeeds then throws; `OnBackground` twice: mark 1 |
| `watchedInFront_requestsNoRefreshUntilExit` | every save throws; VM A: `MarkCurrentWatched`, refresh 0, exit: refresh 1, mark 0; VM B: `onPlaybackEnded()`, refresh 0, exit: refresh 1 more, mark 0 |
| `watchedInFront_backgroundRequestsOneRefresh` | every save throws; `MarkCurrentWatched`, `OnBackground`: refresh 1, mark 0 |

- [x] Fixture parameter; write `playbackInFront_...` and `backPressed_afterSuccessfulSaves_...`, run red on Task 1's commit; record.
- [x] Remaining eight tests.
- [x] Change `PlayerVM`.
- [x] Run the fast gate and `PlayerVMInvalidationTest`, `PlayerVMPlaybackFlowTest`, `PlayerVMMutationAndResultTest`, `PlayerVMTest` (`--tests 'com.kino.puber.ui.feature.player.vm.*'`); commit.

**Done when:** the player vm test package is green; `git diff HEAD~1 -- T/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt T/ui/feature/player/vm/PlayerVMMutationAndResultTest.kt` is empty; `rg -n "requestRefresh" M/ui/feature/player/vm/PlayerVM.kt` shows only the line in `flushOwedInvalidation`.

---

### Task 3: `CachedFeed` keeps one per-key record while an operation holds it

**Covers:** AC-7, AC-8, AC-9
**Files:**
- Modify: `M/data/cache/CachedFeed.kt`
  - Replace `keyEpochs`, `keyInvalidations`, `keyWrites`, `keySupersedeWrites` (`:95,106,120,131`) by `private val keyRecords = ConcurrentHashMap<String, KeyRecord>()`. Keep the four KDoc rationales on `KeyRecord`'s fields; replace "Never cleared" (`:117-119`) with: a record lives exactly as long as some operation holds an `Epoch` of it. `namespaceEpoch` (`:132`), `Epoch` (`:143-166`) and `store.generation` unchanged.
  - `epochOf` (`:409-416`): one `keyRecords[key]` read, zeros when absent (`key = epoch`, `invalidation = invalidations`, `write = writes`, `supersedeWrite = supersedeWrite`).
  - `supersedeKey` (`:419-422`): one `compute` bumping `invalidations` and `epoch`. `advanceKey` (`:430-433`) becomes `advanceKey(key, supersede: Boolean): Long`: one `compute` that, when `supersede`, bumps `invalidations`; always bumps `epoch` and `writes`; when `supersede`, sets `supersedeWrite = writes` (new value); returns the new `writes`. `putAll`'s loop (`:352-357`) calls it in place of `:353-355`. Every bump is `compute { _, r -> (r ?: KeyRecord()).copy(...) }`, never `computeIfPresent`.
  - `supersedeNamespace` (`:435-442`): keep `namespaceEpoch.incrementAndGet()` and its comment; delete the two `clear()` calls.
  - Holds (sketch below), comparisons and control flow inside unchanged:

| Site | Hold |
|---|---|
| `load` (`:173-255`) | whole `flow { }` body in `holding(listOf(key))`; `return@flow` (`:179`) stays a non-local return through the inline lambda. Covers `readCached` (`:517-525`), `promoteToMemory` (`:529-550`) and the loader captures (`:202,207,218`): the loader runs in the leader's own coroutine (`M/core/collections/TypedTtlCache.kt:84-97,214-221`) |
| `markStale` (`:264-275`) | whole body |
| `invalidate` (`:278-287`) | whole body |
| `peek` (`:297-306`) | from `val epoch = epochOf(key)` (`:302`) to the end |
| `peekAll` (`:309-334`) | `holding(missing)` from `:321` to the loop end |
| `putAll` (`:349-395`) | `holding(values.keys)` from the barrier loop (`:352`) to the end; `put` (`:337-339`) delegates |

```kotlin
private data class KeyRecord(
    val holders: Int = 0,
    val epoch: Long = 0L,
    val invalidations: Long = 0L,
    val writes: Long = 0L,
    val supersedeWrite: Long = 0L,
)

@VisibleForTesting // androidx.annotation, as in M/core/ui/PuberVM.kt:21,64
internal val keyRecordCount: Int get() = keyRecords.size

private inline fun <T> holding(keys: Collection<String>, block: () -> T): T {
    val held = keys.distinct()
    held.forEach { key -> keyRecords.compute(key) { _, r -> (r ?: KeyRecord()).let { it.copy(holders = it.holders + 1) } } }
    try {
        return block()
    } finally {
        held.forEach { key ->
            keyRecords.compute(key) { _, r -> r?.takeIf { it.holders > 1 }?.let { it.copy(holders = it.holders - 1) } }
        }
    }
}
```

- Modify: `T/util/FakePayloadStore.kt` - add one-shot hooks in the style of `onWrite` (`:30,35-43`): `var onTouch: (suspend (String) -> Unit)?` fired in `touch` (`:45`) after the row changes, `var onRemove: (suspend (String) -> Unit)?` fired in `remove` (`:56`) after the row goes. Existing behaviour unchanged when null.
- Test: `T/data/cache/CachedFeedTest.kt` - six new cases; the 42 existing ones unchanged. Helpers: `feed()` (`:27-33`), `runSupersededLoad` (`:615-640`), `FakePayloadStore` `onRead`/`onWriteAll`/`onWrite`/`onTouch`/`onRemove`. `put` and `putAll` reach the store through `writeAll` (`onWriteAll`); `peekAll` through the default `readAll` -> `read` (`onRead`); a `load` is held by suspending its loader on a `CompletableDeferred`.

| Test | Setup and assertion |
|---|---|
| `afterEveryOperationSettles_theFeedHoldsNoKeyRecord` | 100 keys through `load` (fresh miss and stale hit), `put`, `putAll`, `invalidate`, `markStale`, `peek`, `peekAll`, then `invalidateNamespace`; after `runCurrent()`: `keyRecordCount == 0` |
| `whileAnOperationIsInFlight_itsKeyHasARecord` | `@ParameterizedTest` + `@EnumSource` over a private enum of sites: `load` (loader held), `put`/`putAll` (`onWriteAll`), `markStale` (`onTouch`), `invalidate` (`onRemove`), `peek`/`peekAll` (`onRead`, row present, memory empty): `keyRecordCount == 1` inside the hook, `0` after |
| `aFailedOrCancelledLoadReleasesItsKeyRecord` | loader throws: count 0; second load with loader suspended, collecting job cancelled, `runCurrent()`: count 0 |
| `aShortOperationOnTheSameKeyDoesNotRemoveTheRecordOfAnInFlightLoad` | load of `k` held; `peek("k")` and `putAll(mapOf("k" to ...))` complete: count 1; `invalidate("k")`, release loader, `runCurrent()`: `store.read("k")` null, count 0 after the job ends |
| `aKeyWhoseRecordWasRemovedGuardsTheNextRaceAsBefore` | `load("k")` settles, count 0; `runSupersededLoad` with `invalidate("k")`: `store.read("k")` null; again with `markStale("k")`: same outcome as `markingStaleDuringALoadKeepsThatLoadFromRefreshingTheTimestamp` (`:667`) |
| `aNamespaceDropDuringALoadKeepsTheRecordUntilTheLoadEnds` | prefixed feed as in `:652-662`; load held; `invalidateNamespace()`: count 1; release, `runCurrent()`: count 0, `store.read("k")` null |

**Consumes:** nothing from Tasks 1-2.
**Produces:** `CachedFeed.keyRecordCount` (test seam only).

- [x] `FakePayloadStore` hooks; the six tests (they fail on base by compilation; check each against the mutation named in the spec's Traceability by reverting one hold or the `finally` locally, not committed).
- [x] Change `CachedFeed`.
- [x] Run the fast gate, `CachedFeedTest`, `CachedFeedMemoryWeightTest`, `ContentCacheRepositoryTest`, `ItemDetailsRepositoryTest`.
- [x] Run the full gate (AC-9); commit.

**Done when:** the full gate passes; `rg -n "keyEpochs|keyInvalidations|keyWrites|keySupersedeWrites" M` prints nothing; `git diff 40762767 --stat -- T` lists only `PlayerInteractorTest.kt`, `PlayerVMTestFixture.kt`, `PlayerVMInvalidationTest.kt`, `FakePayloadStore.kt`, `CachedFeedTest.kt`, and the `CachedFeedTest` diff only adds tests.

---

## Hand-off to qa-device (AC-6, AC-10)

Device check required (spec brief: playback, VM state, caches, launcher). Steps M1-M4 and the perf table are in the spec, Test design: Manual checks and Perf track (AC-10); baseline in `.flow/runs/cache-targeted-invalidation/baseline.md`.

| Item | Detail |
|---|---|
| Build | Worktree's own dev debug APK after Task 3, plus the measure patch below, `tools/android-apk-install-preserve` |
| Measure patch | `/mnt/data/projects/PetProjets/Puber/.flow/runs/cache-targeted-invalidation/baseline-measure.patch` adapted, uncommitted, reverted after the run: the `PlayerInteractor` hunk keeps the `ApiError.Serialization` shim and `saveOk` but no longer has `markStale` context; the `TvHomeSyncCoordinator`, `ContentCacheRepository`, `ItemDetailsRepository` hunks apply as they are; the `PlayerVM` drain hunk needs its context re-cut (the drain now sets `detailsStaleOwed`) |
| After the run | `git status --short` clean of the patch; `rg PUBERBASE app/src` empty |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: `5215bc27` Move the details stale mark out of saveWatchingTime into its own interactor call
- Task 2: `69e9cdac` Mark details stale and refresh the Home row once at player exit and background
- Task 3: the commit that carries this record (CachedFeed key records; subject "Bound the CachedFeed per-key counters to keys with an operation in flight")

**Red run** (P0 `red` tests on the unchanged code):

- red: `PlayerInteractorTest.saveWatchingTime_success_doesNotMarkItemDetailsStale` - AssertionError at the `markStale` `exactly = 0` verify (base marks per save); `markItemDetailsStale_marksTheRepositoryEntryStale` run against an empty stub, failed on its `markStale(42)` verify
- red: `PlayerVMInvalidationTest.playbackInFront_savesPrefetchAndEpisodeSwitch_markAndRefreshNothing` - `requestRefresh` should not be called, 3+ calls found
- red: `backPressed_afterSuccessfulSaves_marksAndRefreshesOnceBeforeBack` - `markItemDetailsStale(42)` not called; `onBackground_marksOnlyAfterTheHeldSaveDrains` - same; `backPressed_afterBackgroundFlush_withNoNewSuccessfulSave_marksAndRefreshesNothingMore` - same; `backPressed_whileBackgroundFlushPending_marksOnceBeforeBack` - `router.back` called while the mark is held; `repeatedOnBackground_marksOnce` - mark not called; `backPressed_markFailure_stillReturnsResult` - 2 refreshes instead of 1; `watchedInFront_requestsNoRefreshUntilExit` - refresh requested in front
- green on base: `PlayerVMInvalidationTest.backPressed_withoutSuccessfulSave_marksAndRefreshesNothing` - on base a failed save requests no refresh either, so it is a guard; `watchedInFront_backgroundRequestsOneRefresh` - base requests exactly one refresh for the single Watched mark, so it is a guard
- `CachedFeedTest` P0 rows fail on base by compilation (`keyRecordCount`). Mutation checks on the final code, not committed: no release at zero (`Unit` in the `finally`) fails 12 new cases; release removing at any count fails `aShortOperation...`, `aKeyWhoseRecordWasRemoved...` and 3 existing race cases; `peek` holding nothing fails `whileAnOperationIsInFlight[Peek]`

**Deviations from the plan:**

- `CachedFeedTest` gets `@Suppress("LargeClass")` (detekt failed on the 6 new cases; the PlayerVM precedent suppresses it too). Splitting the class would have duplicated `runSupersededLoad`.
- `aKeyWhoseRecordWasRemovedGuardsTheNextRaceAsBefore`: the key is settled by `put` then `invalidate` (a cached load would never start the loader the race needs), and the `markStale` half runs on a second feed over the same store because the first race leaves its parked retry in the first feed's memory tier.
- `watchedInFront_requestsNoRefreshUntilExit`: the auto-marked VM needs three backs (countdown cancel, hide controls, leave); `testContentState` has `hasNextEpisode = true`.
- Tracker saves advance 30 000 ms then `runCurrent()`, so each advance runs the save due at the boundary.
- Both runTest-based `PlayerVMInvalidationTest` cases cancel the VM scope in `finally`: a failing assertion otherwise leaves the position loop spinning (541 s hang seen on the red run).

**Files touched outside the plan:** none.

**For the reviewers:**

- `holding` is `inline`: `return@flow` in `load` stays a non-local return and the release runs in `finally` on cancellation too.
- `CachedFeed.supersedeNamespace` no longer clears per-key state; the namespace counter alone supersedes, and a record is removed only by its last holder.
- `flushOwedInvalidation` clears both flags before its first suspension; the mark failure is swallowed by `runCatchingCancellable`, the refresh still follows.
- `PlayerVMPlaybackFlowTest` and `PlayerVMMutationAndResultTest` are unchanged.
- `detektDevDebugUnitTest` prints "7 compiler errors found during analysis" (type resolution notice), present before this run too; the gate passes.

**Blocked:** none.

### Fix round 1

| Finding | Fix | Proof |
|---------|-----|-------|
| #1 (blind F1) | `pauseForBackground` runs the flush body in `withContext(NonCancellable)`: clearing the VM while the final save waits no longer drops the owed mark and Home refresh. No new dependency, constructor unchanged. | `PlayerVMInvalidationTest.clearedDuringBackgroundFlush_stillMarksAndRefreshes`; without the wrap it fails on the `markItemDetailsStale` verify |
| #2 (tests F1) | `DetailsVMCachedLoadTest.afterMarkItemDetailsStale_detailsOpenRevalidatesAndShowsNewProgress`: real `ItemDetailsRepository` over `ContentCacheRepository` and `FakePayloadStore`; after `markStale` the Details open emits the stored item, then the new one | Without the `markStale` line it fails on the emitted list; device check M1/M2 stays NOT CHECKED |
| #3 (tests F2, test part) | `backPressed_withoutSuccessfulSave_marksAndRefreshesNothing`: the never-started-VM half is dropped; the exit now asserts `router.back` ran once beside zero marks and refreshes | With `val markDetails = true` in `flushOwedInvalidation` it fails |

Notes: the first draft of #1's test passed without the fix because `viewModelScope.cancel()` cancelled the held save first and so released the flush before its own cancellation. The held save now ignores cancellation (`NonCancellable`), as a request already on the wire does. The Details test passes the test dispatcher as `decodeDispatcher`; with the default, the revalidation runs on a real thread and `advanceUntilIdle` does not wait for it.

## Readiness

Final judge, mode readiness, 2026-11-22. Base 40762767; worktree HEAD 8acfa031, only the plan untracked, `rg PUBERBASE app/src` empty (measure patch reverted).

**Verdict: READY**

### Checks

| Check | Result |
|---|---|
| AC -> task -> Test design row | AC-1 T1; AC-2..AC-5 T2; AC-7, AC-8 T3; AC-9 T3 full gate; AC-6, AC-10 qa hand-off (M1-M4, perf track). Every AC has a Traceability row |
| Citations (sampled at base with `git show 40762767:`) | All resolve; table below |
| Build stays green per task | T1 adds `markItemDetailsStale` before T2 uses it; T2 fixture parameter defaults to `null`; T3 is independent. Each task ends on the fast gate plus touched tests |
| Order | T1 -> T2 (consumes `markItemDetailsStale`); T3 last runs `check.full` |
| P0 rows | Each automated P0 row is `red` or `guard`, names its test in backticks and the mutation it catches. AC-6 is a P0 device row (Manual check M1), manual by nature |
| Out of scope | Plan touches `PlayerInteractor`, `PlayerVM` (no constructor change), `CachedFeed` and five test files; coordinator, DI, UI, `ContentCacheRepository`, `ItemDetailsRepository`, marktime decode untouched |
| Blocking questions | Q1-Q3 answered (state.md Decisions) |
| Hold coverage (E1) | Every `epochOf`/`supersedeKey`/`advanceKey` call at base (`CachedFeed.kt:202,207,218,265,272,279,284,302,304,321-327,353-358,365,378,517-546`) falls inside a site of the plan's hold table; a bump outside a hold would leak a record, which `afterEveryOperationSettles_theFeedHoldsNoKeyRecord` catches |

### Citations sampled

| Citation | Resolves to |
|---|---|
| `PlayerInteractor.kt:278` | `itemDetailsRepository.markStale(id)` in `saveWatchingTime` |
| `ItemDetailsRepository.kt:84` | `suspend fun markStale(itemId: Int)` |
| `PlayerVM.kt:186,188` | `progressDrainJob`, `closeJob` |
| `PlayerVM.kt:1484-1491` | `pauseForBackground`, `saveCurrentPosition()` at :1490 |
| `PlayerVM.kt:1901-1912` | drain: `saveWatchingTime`, `markContentChanged(PlaybackProgress)`, catch |
| `PlayerVM.kt:1952-1957` | `markContentChanged`, `requestRefresh()` at :1955 |
| `PlayerVM.kt:1959-1983` | `launchMutation`, `awaitPendingMutations` |
| `PlayerVM.kt:2000-2003` | `closeJob = launch { awaitPendingMutations(); router.back(...) }` |
| `PlayerVM.kt:1106,1151,1202,1221` | `markContentChanged(Watched)` callers |
| `PlayerVM.kt:1380-1381,1244-1247,1656` | `onPlaybackEnded` -> auto mark; watched block; `prefetchNextEpisode` |
| `CoroutinesErrorHandler.kt:37`, `TvHomeSyncCoordinator.kt:21`, `PuberVM.kt:21,64` | `runCatchingCancellable`; `requestRefresh(immediate)`; `@VisibleForTesting` |
| `CachedFeed.kt:95,106,120,131,409-442` | the four maps; `epochOf`, `supersedeKey`, `advanceKey`, `supersedeNamespace` |
| `TypedTtlCache.kt:84-97,214-221` | `getOrPut` leader/wait; leader runs `defaultValue()` in its own coroutine |
| `PlayerVMTestFixture.kt:85,141-154,190,194` | `relaxUnitFun` interactor; `createVM`; `startedVM`; `verifyContentChangeResult` |
| `PlayerInteractorTest.kt:32,650,664,682` | strict repo mock; the three named tests |
| `CachedFeedTest.kt:27,615,652,667` | `feed()`, `runSupersededLoad`, namespace and markStale race tests; 42 `@Test` |
| `FakePayloadStore.kt` | `onRead`, `onWrite`, `onWriteAll` exist; `touch`/`remove` have no hook (plan adds `onTouch`, `onRemove`); `readAll` default delegates to `read` (`PersistentPayloadStore.kt:24-26`) |

### Notes (non-blocking)

1. Where test-design.md and the plan differ, the plan is right and governs: `coVerifyOrder` (the mark is suspend), `put` reaches the store through `writeAll` (`CachedFeed.kt:337-339`), auto mark through `onPlaybackEnded`.
2. AC-10 Back-to-screen time: the baseline is one screencap run, "do not compare below ~1 s" (baseline.md), while the pass line is median + 150 ms from logs. Before the qa hand-off, qa-device needs to re-measure that metric on base with the same log markers, or the row stays `NOT VERIFIED` at the gate.
3. AC-10 needs the `ApiError.Serialization` shim on the after build too (baseline.md, Measurement shim); the plan's hand-off row keeps it.


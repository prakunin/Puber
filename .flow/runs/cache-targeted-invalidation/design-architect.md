# Design (architect): cache-targeted-invalidation

Base 40762767. Paths under `app/src/main/java/com/kino/puber/` unless named otherwise. Citations are at base; the worktree's uncommitted measure patch shifts `data/cache/ContentCacheRepository.kt` by one line after `:293`.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Mark stale inside `onCleared` (`PlayerVM.kt:2008`) | `viewModelScope` is cancelled there; the write would not run |
| Background flush via `launchMutation` | It would await itself in `awaitPendingMutations` (`:1977-1983`) |
| `requestRefresh(immediate = true)` at exit | Coordinator debounce already coalesces; no AC asks for sooner |
| E1 with mutable record under `synchronized` | `compute` on an immutable record gives the same atomicity without a second lock |
| E1 holding only around the loader | `readCached`, `peek` and `putAll` capture epochs outside it (`:302,321,358,517`) |

## Risks

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

## Open questions

None.

# Readiness 1: cache-targeted-invalidation

Final judge, mode readiness, 2026-11-22. Base 40762767; worktree HEAD 8acfa031, only the plan untracked, `rg PUBERBASE app/src` empty (measure patch reverted).

**Verdict: READY**

## Checks

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

## Citations sampled

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

## Notes (non-blocking)

1. Where test-design.md and the plan differ, the plan is right and governs: `coVerifyOrder` (the mark is suspend), `put` reaches the store through `writeAll` (`CachedFeed.kt:337-339`), auto mark through `onPlaybackEnded`.
2. AC-10 Back-to-screen time: the baseline is one screencap run, "do not compare below ~1 s" (baseline.md), while the pass line is median + 150 ms from logs. Before the qa hand-off, qa-device needs to re-measure that metric on base with the same log markers, or the row stays `NOT VERIFIED` at the gate.
3. AC-10 needs the `ApiError.Serialization` shim on the after build too (baseline.md, Measurement shim); the plan's hand-off row keeps it.

# Readiness 1: cache-storage-bounds

Verdict: **READY**

Base a22c03bc. Spec, plan and test design read in the worktree. Paths are under `app/src/main/java/com/kino/puber/` unless stated.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | M1 (device, P0) |
| AC-2 | Task 1 | M2 (device, P0) |
| AC-3 | Task 1 | M3 (device, P1) |
| AC-4 | Task 3, Task 5 | `deleteOlderThanRemovesOnlyRowsBeforeTheCutoff`, `sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical` |
| AC-5 | Task 3, Task 5 | `deleteBeyondNewestKeepsTheNewestRows`, `deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep`, `sweepCapsRowsToTheNewest` |
| AC-6 | Task 5 | `sweepRunsOnceAfterTheStartupDelay`, `eachOnStopRunsOneMoreSweep`, `sweepRunsOffTheMainThread` |
| AC-7 | Task 2 | `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`, M4 |
| AC-8 | Task 4 | `firstUseLeavesLegacyRowsToTheMigration` |
| AC-9 | Task 6 | M5 |
| AC-10 | Tasks 3-6 (unchanged-assertion Done-when, full gate in Task 6) | guard row |
| AC-11 | Task 6 Done-when (qa-device perf track) | Perf track |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `ui/feature/player/vm/PlaybackController.kt:626-646` | `createDataSourceFactory()`, `CacheDataSource.Factory` with no write-sink override |
| `PlaybackController.kt:416`, `:593`, `:659` | main `createDataSourceFactory()`; `dataSourceFactory = null`; `dataSourceFactory ?: createDataSourceFactory()` |
| `PlaybackController.kt:39` | `import com.kino.puber.BuildConfig` |
| `data/di/modules.kt:78`, `:103-107` | `MEDIA_CACHE_SIZE_BYTES = 512L * MIB`; Room/DAO/store singles |
| `PuberApp.kt:58`, `:91`, `:111-113` | `IMAGE_MEMORY_CACHE_PERCENT`; `ApiDomainInteractor.initialize()`; `maxSizePercent` builder |
| `data/db/PuberDatabase.kt:13,17,30,38` | `version = 3`; `AutoMigration(2, 3)`; `create(context)`; destructive fallback |
| `data/cache/ContentCacheRepository.kt:61,98,163,219,277,324,341,351,361` | the nine `ensureLegacyRowsRemoved()` calls; `:368` flag write, `:621-628` `LEGACY_PREFIXES`, `:12-13` Mutex imports |
| `data/cache/CacheKeys.kt:15-20` | the six legacy prefixes the migration hard-codes |
| `data/cache/CachedFeed.kt:399`, `:560`, `:591` | `removeByPrefix(keyPrefix)`; read-path age rule; `HardCeiling = 7.days` |
| `core/lifecycle/AppForegroundState.kt:24`, `AppForegroundReporter.kt:30` | `isForeground` StateFlow (initial `true`); ON_STOP -> `onLeftForeground()` |
| `core/coroutine/CoroutinesErrorHandler.kt:37` | `runCatchingCancellable` |
| `Makefile:25` | `PACKAGE_dev := com.kino.puber.stage` |
| Test fakes: `HomeInteractorCacheTest.kt:217`, `ItemDetailsRepositoryTest.kt:253`, `PersistentPayloadStoreTest.kt:111`, `util/FakePayloadStore.kt:7` | every `PersistentPayloadStore` / `CachedPayloadDao` implementer in `app/src` (grep) |
| API claims | javap: coil-core-android 3.6.3 `MemoryCache$Builder.maxSizeBytes(long)`, `MemoryCache.getMaxSize()`; room3-runtime 3.0.3 `AutoMigrationSpec.onPostMigrate(SQLiteConnection, Continuation)` |

## Build order

Tasks 1-6 each leave main, unit and androidTest compiling: Task 3 updates all four fakes in the commit that adds the interface and DAO members; Task 4 runs after the Task 2 migration that replaces it; Task 5 consumes `sweep`, `sweepCount`, `onSweep` from Task 3.

## Test design

Every P0 row names the defect it catches, is marked `red` or `guard`, and names its automated test in backticks; device P0 rows (M1, M2, M4) are manual by nature. Blocking open questions: Q1 answered (migration); spec Open questions has none open.

## Notes for the implementer (non-blocking)

| # | Note |
|---|---|
| 1 | Task 4 Done-when `grep -rn "ensureLegacyRowsRemoved|legacyCleanup|LEGACY_PREFIXES" app/src` uses `|` in a basic regex, where it is a literal, so the check is always empty; run it as `grep -rnE` |
| 2 | Room 3 `MigrationTestHelper` reads schemas from androidTest assets; whether the `room3` plugin wires `app/schemas` there is unverified, so the `assets.srcDir` fallback (plan Task 2) may be needed at the first itest run |
| 3 | The spec section headed "Out of scope" holds the in-scope table; the exclusions live in the plan's Global constraints, which the tasks respect |

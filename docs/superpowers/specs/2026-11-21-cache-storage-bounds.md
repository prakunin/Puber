# Bound the caches: read-only main player cache, swept cached_payload, one-time legacy cleanup, byte-capped Coil

Status: approved
Date: 2026-11-21
Slug: cache-storage-bounds
Kind: refactor
Size: M
Tracks: perf

## Problem / Why

### Request
research.md: Request. Scope also carries `content-cache-legacy-cleanup` (folded in) and the media-cache half of `player-cache-save-churn` (`state.md` Decisions).

### Current state
research.md: Current state in code. Facts added here:

| Fact | Where |
|---|---|
| `markStale` moves a row's `updated_at` to `now - ttl - 1`, not to 0, so an age sweep at `HardCeiling` keeps stale-marked rows readable | `data/cache/CachedFeed.kt:263-266` |
| The age rule the sweep repeats already exists, but only on read | `data/cache/CachedFeed.kt:559-564` |
| `CachedPayloadDao` already has an instrumented test; no `room3-testing` dependency, so a migration test needs one (version via `libs.versions.toml`) | `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt`, `app/build.gradle.kts:426-430` |

### Risks (analyst)
research.md: Risks (read-only resume loss, warm-up URL identity, evictor on cap drop, sweep vs write race, destructive fallback on a failed migration, Coil cap too low, `PuberApp.kt` overlap with `startup-splash-preload`). Added:

| Risk | Guard |
|---|---|
| The migration test needs `room3-testing` and runs only as an instrumented test, i.e. through `make DEVICE=<serial> itest` | AC-7; plan adds the dependency through the version catalog |
| `cache-decoded-memory-tier` (B1) bumps the namespace later; orphaned `content:v1:` rows are then removed only by the age step, 7 days after their last write | AC-4; B1 may add its own prefix delete to a later migration |

### Scope (analyst, brief.md: Scope)
Option A (research.md: Options), with the idle trigger of research.md: Open questions 4 assumed: once per process after a fixed delay on a background dispatcher, plus on `AppForegroundState` ON_STOP (`core/lifecycle/AppForegroundState.kt:20`).

| Part | Change |
|---|---|
| Media | Separate `CacheDataSource.Factory` for main (read-only, `setCacheWriteDataSinkFactory(null)`) and warm-up (writes) in `PlaybackController.kt:626-646,659`; `MEDIA_CACHE_SIZE_BYTES` 512 -> 256 MB (`data/di/modules.kt:78`) |
| Sweep | DAO deletes rows older than `HardCeiling`, then oldest beyond a row cap; exposed through `PersistentPayloadStore`; index on `updated_at` |
| Legacy | Room v4 with `AutoMigration(3, 4)` whose spec deletes the six legacy prefixes once; `ensureLegacyRowsRemoved`, its mutex and flag removed (Q1) |
| Coil | `maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)` instead of `maxSizePercent` (`PuberApp.kt:58,116-118`) |

## Acceptance criteria

- AC-1. Given main playback of a stream with no next-episode warm-up, When 30 min are played, Then `media_cache` grows by 0 bytes (device: `du` of `media_cache` before/after, `adb -s`).
- AC-2. Given an episode with a next episode, When the warm-up runs and the next episode then starts, Then `media_cache` grew during the warm-up and the next episode's first segments are read from the cache (device: `du`, and a debug-build `CacheDataSource.EventListener.onCachedBytesRead` log > 0 at the next-episode start).
- AC-3. Given an installed build whose `media_cache` holds more than 256 MB, When the new build is installed with install-preserve and a warm-up writes, Then `media_cache` is at most 256 MB (device: `du`).
- AC-4. Given `cached_payload` rows with `updated_at` older than `HardCeiling` and newer rows, When the sweep runs, Then the old rows are gone and the newer ones remain byte-identical (instrumented DAO test; unit test with `FakePayloadStore` and a fake clock).
- AC-5. Given more rows than the cap after the age step, When the sweep runs, Then the rows with the oldest `updated_at` are deleted down to the cap and a row written during the sweep survives (instrumented DAO test; unit test).
- AC-6. Given a process start, When the startup delay passes and later the app goes to background, Then the sweep runs once after the delay and once per ON_STOP, never on the main thread (unit test with a test dispatcher and a main-thread assertion).
- AC-7. Given a v3 database with legacy-prefixed rows, `content:v1:` rows and `watch_state`/`watch_state_sync` rows, When the new build opens it, Then the legacy rows are gone and every other row is intact (Room migration test against `3.json`; device upgrade with install-preserve keeps watch history).
- AC-8. Given any process start after the migration, When the content cache is first used, Then no `removeByPrefix` call is made (unit test replacing `ContentCacheRepositoryTest.kt:232-246`).
- AC-9. Given the app is running, When the Coil memory cache is read, Then its `maxSize` equals the byte constant (device: debug log or `MemoryProbe` output).
- AC-10. `CachedFeedTest`, `ContentCacheRepositoryTest`, `PlayerVMNextEpisodePrefetchTest`, `CachedPayloadDaoTest` stay green with assertions unchanged except AC-8's rewrite, and the full check passes.
- AC-11 (perf). Before/after numbers on the emulator are recorded in evidence: `media_cache` bytes after the AC-1 session, `cached_payload` row count and `puber.db` size after a fixed browsing session, Coil memory cap and app PSS from `core/telemetry/MemoryProbe.kt` after scrolling Home rows.

## Out of scope

The other split items of cache-layer-rework (brief.md of the parent): the shared HTTP stack, DNS cache and KinoPub HTTP cache (cache-shared-http-stack); decoded memory tier, off-Main decode and watch_state re-reads (cache-decoded-memory-tier); markStale per save, TV home refresh, epoch maps (cache-targeted-invalidation); player ON_STOP and subtitle side-loading (player-cache-save-churn).

## Design

Base a22c03bc. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `gradle/`. ACs: `brief.md#acceptance-criteria`.

#### Decisions

| Question | Decision | Why |
|---|---|---|
| How does the main player stop writing? | `createDataSourceFactory(writeToCache: Boolean)`; main passes `false` and gets `setCacheWriteDataSinkFactory(null)`, warm-up passes `true` and builds its own factory per `warmUpNext` call instead of reusing `dataSourceFactory` | Read-only is fixed per factory (Media3); a per-call warm-up factory needs no new field or release step. Both OkHttp clients come from `okHttpClient.newBuilder()`, so they share one connection pool |
| How is AC-2 observed? | Main factory gets `setEventListener` only when `BuildConfig.DEBUG`, logging `onCachedBytesRead` through Timber | Evidence hook without a release-build cost; `CacheDataSource.EventListener.onCachedBytesRead(long, long)` exists in media3-datasource 1.11.0 (javap) |
| Media cap | `MEDIA_CACHE_SIZE_BYTES = 256L * MIB` | Owner decision; `LeastRecentlyUsedCacheEvictor` shrinks in `onStartFile`/`onSpanAdded`, i.e. on the next write, which is what AC-3 measures |
| Sweep queries | Two DAO statements: `DELETE ... WHERE updated_at < :cutoff`, then `DELETE ... WHERE key IN (SELECT key FROM cached_payload ORDER BY updated_at DESC LIMIT -1 OFFSET :keepNewest)` | Each is one atomic statement, so a row written mid-sweep is either already newest or lands after (AC-5); `OFFSET` on the newest needs no `COUNT(*)` |
| Sweep cutoff | `clock() - CachedFeed.HardCeiling.inWholeMilliseconds` | Same rule as the read path (`data/cache/CachedFeed.kt:560`), so the sweep deletes only rows a read would delete |
| Index | `@Entity(indices = [Index("updated_at")])` on `CachedPayloadEntity`; Room adds it in `AutoMigration(3, 4)` | Both sweep statements order or filter by `updated_at` |
| Sweep API | `PersistentPayloadStore.sweep(olderThan: Long, keepNewest: Int)`, no default body | One entry point that every fake must model; a no-op default would let a fake hide the sweep |
| Sweep owner | New `data/cache/CachedPayloadSweeper.kt`: `start()` (idempotent) launches one coroutine in an injected `CoroutineScope(SupervisorJob() + Dispatchers.IO)` that collects `merge(startup delay tick, AppForegroundState.isForeground.drop(1).filter { !it })` and calls `store.sweep` | One collector serialises sweeps without a mutex; `isForeground` false is ON_STOP (`core/lifecycle/AppForegroundReporter.kt:29-30`); same scope pattern as `PuberApp.kt:51` |
| Startup delay, cap | `60.seconds`, `MAX_ROWS = 2_000` as constants in the sweeper | Q2 answer; 60 s is past Home's first load. Both are constructor defaults so the unit test passes its own |
| Where `start()` is called | `PuberApp.initDi`, the line after `PuberApp.kt:91` | The app-level startup hook that exists today; `createdAtStart` would hide the side effect in a module |
| Legacy cleanup | `@AutoMigration(from = 3, to = 4, spec = LegacyPayloadCleanup::class)`; the spec's `suspend fun onPostMigrate(connection: SQLiteConnection)` runs six `DELETE FROM cached_payload WHERE key LIKE '<prefix>%'` with the prefixes written as literals | Q1 answer; `AutoMigrationSpec.onPostMigrate(SQLiteConnection)` is suspend in room3-runtime 3.0.3 (javap). Literals freeze the migration against later edits of `CacheKeys` |
| Runtime cleanup | Delete `ensureLegacyRowsRemoved`, its nine call sites, `legacyCleanupMutex`, `legacyCleanupComplete`, `LEGACY_PREFIXES` and the flag write in `clear()` | AC-8 |
| `removeByPrefix` / `deleteByPrefix` | Kept | Still called by `CachedFeed.invalidateNamespace` (`data/cache/CachedFeed.kt:399`); `CachedPayloadDaoTest` asserts `deleteByPrefix` (AC-10) |
| Coil | `maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)`, `48L * 1024 * 1024`; debug-only Timber line with the built cache's `maxSize` | Q3 answer; `MemoryCache.Builder.maxSizeBytes(long)` exists in coil-core-android 3.6.3 (javap). The log line is the AC-9 observation |
| Migration test dependency | `androidx-room-testing = { group = "androidx.room3", name = "room3-testing", version.ref = "room" }`, `androidTestImplementation` | AC-7; room3-testing 3.0.3 is on Google Maven (HTTP 200 on the POM) |

#### Changes by layer

##### API / repository

| File | Change |
|---|---|
| `data/db/CachedPayloadEntity.kt:20-26` | `indices = [Index(value = ["updated_at"])]` on `@Entity` |
| `data/db/CachedPayloadDao.kt:7-33` | Add `deleteOlderThan(cutoff: Long)` and `deleteBeyondNewest(keepNewest: Int)` (queries in Decisions) |
| `data/db/PuberDatabase.kt:11-17` | `version = 4`; add `AutoMigration(from = 3, to = 4, spec = LegacyPayloadCleanup::class)`; new `LegacyPayloadCleanup : AutoMigrationSpec` in `data/db/` |
| `app/schemas/com.kino.puber.data.db.PuberDatabase/4.json` | Generated by KSP on compile; committed |
| `data/repository/PersistentPayloadStore.kt:12-35` | Interface gains `suspend fun sweep(olderThan: Long, keepNewest: Int)` |
| `data/repository/PersistentPayloadStore.kt:37-107` | `RoomPersistentPayloadStore.sweep`: `dao.deleteOlderThan`, then `dao.deleteBeyondNewest`; no generation check (it only deletes) |
| `data/cache/ContentCacheRepository.kt:47-50,61,98,163,219,277,324,341,351,361,366-384,621-628` | Runtime legacy cleanup removed (Decisions) |
| `data/cache/CachedPayloadSweeper.kt` (new) | `class CachedPayloadSweeper(store: PersistentPayloadStore, foreground: AppForegroundState, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis, startupDelay: Duration = 60.seconds, maxRows: Int = 2_000)`; `fun start()` |

##### ViewModel state and actions (player controller)

| File | Change |
|---|---|
| `ui/feature/player/vm/PlaybackController.kt:626-646` | `createDataSourceFactory(writeToCache: Boolean)`; `false`: `.setCacheWriteDataSinkFactory(null)` and, in debug, `.setEventListener` |
| `ui/feature/player/vm/PlaybackController.kt:416` | Main calls `createDataSourceFactory(writeToCache = false)` |
| `ui/feature/player/vm/PlaybackController.kt:659` | Warm-up calls `createDataSourceFactory(writeToCache = true)`; stops reading `dataSourceFactory` |
| `ui/feature/player/vm/PlaybackController.kt:648-653` | KDoc: the warm-up writes and the main player only reads |

##### Screen and DI module

| File | Change |
|---|---|
| `data/di/modules.kt:78` | `512L` -> `256L` |
| `data/di/modules.kt:107` | Add `single { CachedPayloadSweeper(store = get(), foreground = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) }`; `AppForegroundState` is registered at `domain/di/modules.kt:57` |
| `PuberApp.kt:91` | Next line: `koinApplication.koin.get<CachedPayloadSweeper>().start()` |
| `PuberApp.kt:58,111-115` | `IMAGE_MEMORY_CACHE_PERCENT` -> `IMAGE_MEMORY_CACHE_BYTES`; `maxSizeBytes`; debug log of `maxSize` |

##### Compose content
N/A: no composable changes.

##### Resources
N/A: no strings or packaged resources.

##### Build and tests

| File | Change |
|---|---|
| `gradle/libs.versions.toml:55-57` | Add `androidx-room-testing` |
| `app/build.gradle.kts:426-430` | `androidTestImplementation(libs.androidx.room.testing)`; schema JSON reachable from androidTest assets (the room3 Gradle plugin at `app/build.gradle.kts:240-242` is expected to wire it; if `MigrationTestHelper` cannot find `3.json`, add `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`) |
| `app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt:7` | `sweep` with real semantics (age, then newest `keepNewest`) |
| `app/src/test/kotlin/com/kino/puber/domain/interactor/home/HomeInteractorCacheTest.kt:217`, `app/src/test/kotlin/com/kino/puber/data/repository/ItemDetailsRepositoryTest.kt:253` | `InMemoryPayloadStore` overrides `sweep` (unused there) |
| `app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt:111` | `FakeCachedPayloadDao` overrides the two new DAO methods |
| `app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt:231-248` | Rewritten for AC-8 |
| `app/src/androidTest/kotlin/com/kino/puber/data/db/` | New sweep cases in `CachedPayloadDaoTest`; new `PuberDatabaseMigrationTest` (AC-7) |

#### Alternatives rejected

| Alternative | Reason |
|---|---|
| Prefs flag around the runtime cleanup (research Option B) | Q1 answered: migration |
| `LIKE` replaced by a key range, no schema change (Option C) | Keeps a per-process step; the sweep would scan without an index |
| Byte cap on `cached_payload` | Q2 answered: rows; `SUM(length(payload))` scans the table |
| WorkManager or `IdleHandler` for the sweep | New dependency or main-looper coupling for a sub-second delete |
| Sweep inside `CachedFeed`/`ContentCacheRepository` | Would tie storage maintenance to a cache instance's first use |
| Shared warm-up factory field cleared in `release()` | Extra lifecycle state for a call made once per episode |

#### Risks

| # | Risk | Kind | Lives in | Guard |
|---|---|---|---|---|
| 1 | A failing 3->4 migration falls into `fallbackToDestructiveMigration(dropAllTables = true)` and drops `watch_state`/`watch_state_sync` with the cache | data shape, lifecycle | `data/db/PuberDatabase.kt:38`, `LegacyPayloadCleanup` | Migration only adds an index and deletes by prefix; `PuberDatabaseMigrationTest` against `3.json`; device upgrade with install-preserve (AC-7) |
| 2 | Read-only main player refetches on seek-back beyond the back buffer and on replay | performance, rollout | `PlaybackController.kt:626-646` | Owner accepted; AC-1, AC-11 |
| 3 | Warm-up hits depend on the warm-up and the main player requesting identical URIs (default cache key) | correctness | `PlaybackController.kt:654-706`, `:416` | Unchanged from today; AC-2 debug log at next-episode start |
| 4 | Cap step deletes item rows a live list record still names | correctness | `CachedPayloadDao` new query; `ContentCacheRepository.kt:439` | `queryNeedsRefetch` already forces a refetch when an item record is missing; the memory tier keeps serving what it holds |
| 5 | Sweep runs while a load writes or `clear()` bumps the generation | concurrency | `data/repository/PersistentPayloadStore.kt:64-84,97-106` | Single-statement deletes; writes carry `clock()` and sort newest; deleting after a wipe is a no-op (AC-5) |
| 6 | Sweep on the main thread or overlapping itself | concurrency, performance | `CachedPayloadSweeper` | Injected IO scope, one collector (AC-6); Room's query context is IO (`data/db/PuberDatabase.kt:35`) |
| 7 | 48 MB Coil cap reloads posters on Home scroll | performance | `PuberApp.kt:111-115` | AC-9, AC-11; raise the constant if the owner sees reloads |
| 8 | Unit sources stop compiling: three `PersistentPayloadStore` fakes and `FakeCachedPayloadDao` lack the new members; `check.fast` compiles main only | rollout | test files in Build and tests | The plan task that adds the members updates the fakes in the same commit |
| 9 | Text conflict with `startup-splash-preload`, which edits `PuberApp.kt:91` | rollout | `PuberApp.kt:91-92` | Rebase on it before review; the `start()` call moves next to its `AppStartup.start()` |
| 10 | `MigrationTestHelper` runs only on a device, through `make DEVICE=<serial> itest` | rollout | `app/src/androidTest/` | qa-device runs it under a lease (AC-7) |

#### Open questions
None.

## Test design

Base a22c03bc. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>` only), `device`. Test names follow the neighbouring files (sentence-style camelCase), not the `method_expected_when` pattern of `docs/architecture/testing.md`.

#### Risk profile

P = probability, I = impact, S = P x I. Design risks are `design-architect.md#risks` rows 1-10; D-risks are added here.

| Risk | P | I | S | Test that would fail |
|---|---|---|---|---|
| R1 (design 1): 3->4 migration fails, `fallbackToDestructiveMigration` drops `watch_state`/`watch_state_sync` | 2 | 3 | 6 | `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`, `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`, manual M4 |
| D1: sweep deletes live rows (cutoff sign or unit error, `<=` vs `<`, `ORDER BY ... ASC` keeping the oldest, off-by-one on `OFFSET`) | 2 | 3 | 6 | `deleteOlderThanRemovesOnlyRowsBeforeTheCutoff`, `deleteBeyondNewestKeepsTheNewestRows`, `sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical` |
| R2 (design 2): read-only main player refetches on seek-back and replay | 3 | 1 | 3 | Accepted by the owner; M1 records it |
| R3 (design 3): warm-up and main request different URIs, next episode misses the cache | 2 | 2 | 4 | M2 (`onCachedBytesRead` > 0) |
| D2: main factory still writes (`setCacheWriteDataSinkFactory(null)` on the warm-up instead, or warm-up reusing the main factory) | 2 | 2 | 4 | M1 (0 bytes growth), M2 (growth during warm-up) |
| D3: evictor does not shrink an existing 512 MB cache | 1 | 2 | 2 | M3 |
| R4 (design 4): cap step deletes item rows a live list still names | 2 | 2 | 4 | `listWhoseItemRowsWereSweptRefetchesThem` |
| R5 (design 5): sweep races a write or `clear()` | 2 | 2 | 4 | `deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep` |
| R6 (design 6): sweep on the main thread or overlapping itself | 2 | 2 | 4 | `sweepRunsOffTheMainThread`, `startTwiceRunsOneCollector` |
| D4: a throwing `store.sweep` (SQLite error) escapes the `SupervisorJob` scope as an uncaught exception and crashes the app, or ends the collector so no later sweep runs | 1 | 3 | 3 | `aFailingSweepNeitherCrashesNorStopsLaterSweeps` |
| D5: `start()` never called or scheduling wrong, so the table stays unbounded | 2 | 2 | 4 | `sweepRunsOnceAfterTheStartupDelay`, `eachOnStopRunsOneMoreSweep`, perf row count |
| D6: wrong prefix literal in `LegacyPayloadCleanup` deletes `content:v1:` rows or leaves legacy ones | 2 | 2 | 4 | `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow` |
| R7 (design 7): 48 MB Coil cap reloads posters on Home scroll | 2 | 1 | 2 | Accepted with M6 (owner judges) |
| R8 (design 8): unit fakes miss the new members | 3 | 1 | 3 | `check.full` |
| R9 (design 9): `PuberApp.kt` conflict with `startup-splash-preload` | 3 | 1 | 3 | `check.full` after rebase |
| R10 (design 10) + D7: `MigrationTestHelper` cannot find `3.json` in androidTest assets | 2 | 1 | 2 | The migration test itself errors; fixed by the assets `srcDir` in the design |

#### Traceability

Unit files under `app/src/test/kotlin/com/kino/puber/`; instrumented under `app/src/androidTest/kotlin/com/kino/puber/`. Red = fails (or does not compile) on a22c03bc, passes after.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | device | P0 red | M1 | Fails if the main factory keeps the default `CacheDataSink.Factory` |
| AC-2 | device | P0 red | M2 | Fails if the warm-up factory is read-only or the warm-up keeps reusing the main factory; fails if cached segments are not hit at next-episode start |
| AC-3 | device | P1 red | M3 | Fails if `MEDIA_CACHE_SIZE_BYTES` stays 512 MB or the evictor does not shrink on write |
| AC-4 | instrumented | P0 red | `data/db/CachedPayloadDaoTest`: `deleteOlderThanRemovesOnlyRowsBeforeTheCutoff` | Rows at `cutoff - 1` gone, rows at `cutoff` and newer keep payload and `updated_at`; fails on `<=`, a reversed comparison or a non-ms cutoff |
| AC-4 | unit | P0 red | `data/cache/CachedPayloadSweeperTest`: `sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical` | With `clock = { now }` and `FakePayloadStore`: row at `now - HardCeiling - 1` gone, row at `now - HardCeiling + 1` byte-identical; fails if the cutoff is not `clock() - HardCeiling` |
| AC-4 | unit | P2 red | `data/repository/PersistentPayloadStoreTest`: `sweepDeletesByAgeThenTrimsToTheNewest` | `RoomPersistentPayloadStore.sweep` applies both DAO steps in order; fails if a step is dropped |
| AC-5 | instrumented | P0 red | `CachedPayloadDaoTest`: `deleteBeyondNewestKeepsTheNewestRows` | N+k rows with distinct `updated_at`: exactly the k oldest gone; fails on `ASC` or `OFFSET` off by one |
| AC-5 | instrumented | P1 red | `CachedPayloadDaoTest`: `deleteBeyondNewestWithFewerRowsThanTheCapDeletesNothing` | Fails if `LIMIT -1 OFFSET` is replaced by something that deletes under the cap |
| AC-5 | instrumented | P0 red | `CachedPayloadDaoTest`: `deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep` | `deleteOlderThan`, then `upsert` of a row with the newest `updated_at`, then `deleteBeyondNewest`: the new row survives and one older row goes |
| AC-5 | unit | P0 red | `CachedPayloadSweeperTest`: `sweepCapsRowsToTheNewest` | Sweeper passes its `maxRows` to the store; with `maxRows = 3` and 5 fresh rows the 2 oldest are gone |
| AC-5 | unit | P2 red | `data/cache/ContentCacheRepositoryTest`: `listWhoseItemRowsWereSweptRefetchesThem` | A list read after its item rows were swept loads again instead of serving holes (design risk 4) |
| AC-6 | unit | P0 red | `CachedPayloadSweeperTest`: `sweepRunsOnceAfterTheStartupDelay` | `StandardTestDispatcher`, sweeper in `backgroundScope`: no sweep at `delay - 1 ms`, one at `delay`, still one after a further hour |
| AC-6 | unit | P0 red | `CachedPayloadSweeperTest`: `eachOnStopRunsOneMoreSweep` | Two `onLeftForeground` (with `onEnteredForeground` between) add two sweeps; fails if `drop(1)` or the filter is missing |
| AC-6 | unit | P1 red | `CachedPayloadSweeperTest`: `returningToForegroundDoesNotSweep` | `onEnteredForeground` adds no sweep; the initial `true` adds none |
| AC-6 | unit | P1 red | `CachedPayloadSweeperTest`: `startTwiceRunsOneCollector` | Second `start()` does not double the sweep count |
| AC-6 | unit | P0 red | `CachedPayloadSweeperTest`: `sweepRunsOffTheMainThread` | Built with the scope `data/di/modules.kt` uses (implementer exposes it as a named factory the test calls), `startupDelay = Duration.ZERO`, `MainDispatcherExtension` active: the thread recorded in the fake's `sweep` is not the test (Main) thread; awaited through a `CompletableDeferred` with a timeout, no sleep |
| AC-6 | unit | P1 red | `CachedPayloadSweeperTest`: `aFailingSweepNeitherCrashesNorStopsLaterSweeps` | First `sweep` throws `IllegalStateException`: no exception reaches the scope's handler and the next ON_STOP sweeps (D4) |
| AC-7 | instrumented | P0 red | `data/db/PuberDatabaseMigrationTest`: `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow` | `MigrationTestHelper` creates v3 with one row per legacy prefix (`home:`, `item:`, `similar:`, `section:`, `watchlist:`, `history:`), `content:v1:` rows, `watch_state` and `watch_state_sync` rows; `runMigrationsAndValidate(4)` passes (index present) and only legacy rows are gone |
| AC-7 | instrumented | P1 red | `PuberDatabaseMigrationTest`: `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` | v3 file opened with the builder configuration of `data/di/modules.kt` (destructive fallback on): `watch_state` row still readable; fails if the migration throws at runtime and the fallback wipes the DB |
| AC-7 | device | P0 red | M4 | Watch history survives an install-preserve upgrade from a v3 build |
| AC-8 | unit | P0 red | `ContentCacheRepositoryTest`: `firstUseLeavesLegacyRowsToTheMigration` (replaces `firstUseRemovesLegacyCacheRowsWithoutTouchingUnrelatedStorage`) | Legacy rows still present after the first `getPayload` and `FakePayloadStore` recorded no `removeByPrefix` call (new `removedPrefixes` list); fails if any `ensureLegacyRowsRemoved` call site remains |
| AC-9 | device | P1 red | M5 | Debug log prints `maxSize` = 50331648; fails if `maxSizePercent` stays |
| AC-10 | unit + instrumented | P0 guard | `CachedFeedTest`, `ContentCacheRepositoryTest`, `PlayerVMNextEpisodePrefetchTest`, `PersistentPayloadStoreTest`, `CachedPayloadDaoTest` (existing cases) | Green with unchanged assertions; `git diff a22c03bc -- <file>` shows only added cases, fake members and the AC-8 rewrite |
| AC-11 | device | P1 | Perf track below | Numbers before and after in evidence |

`FakePayloadStore.sweep` must model real semantics (age, then newest `keepNewest`, design Build and tests); the unit sweep tests depend on it.

#### Manual checks

Emulator under a lease, every adb call `adb -s <serial>`, dev debug APK of the worktree installed with `tools/android-apk-install-preserve`. `du` = `adb -s <serial> shell du -sb /sdcard/Android/data/com.kino.puber.dev/cache/media_cache` (adjust to the real `externalCacheDir`).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 (AC-1) | 1. `du`. 2. D-pad to a film (no next episode), `KEYCODE_DPAD_CENTER` to play. 3. Play 30 min, including one `KEYCODE_MEDIA_FAST_FORWARD` and one seek back. 4. `KEYCODE_BACK`, `du` | Size unchanged (0 bytes growth) | Playback stall or error dialog; growth of any size |
| M2 (AC-2) | 1. `du`. 2. Play a series episode with a next episode to the warm-up point (`PlayerVM.kt:1679`). 3. `du`. 4. Let the next episode start; `logcat -d -s <Timber tag>` filtered to `onCachedBytesRead` | Step 3 larger than step 1; at least one `onCachedBytesRead` with bytes > 0 at the next-episode start | Next episode starting with a network-only read; crash on episode switch |
| M3 (AC-3) | 1. On the base build, fill `media_cache` above 256 MB (several episodes' warm-ups). 2. `du` > 256 MB. 3. Install the worktree APK with install-preserve. 4. Trigger one warm-up (M2 step 2). 5. `du` | At most 268435456 bytes | Install failure handled by uninstall or data clear |
| M4 (AC-7) | 1. On the base build (DB v3), watch part of an episode so it appears in history; note it. 2. Install the worktree APK with install-preserve. 3. Launch, open history / Continue watching row with the D-pad | Same entry and progress; `adb -s <serial> shell run-as com.kino.puber.dev sqlite3 databases/puber.db 'PRAGMA user_version'` = 4 (or pull the DB) | Empty history, re-login prompt, legacy-prefixed rows left in `cached_payload` |
| M5 (AC-9) | 1. Cold start. 2. `logcat -d` for the Coil `maxSize` line | `maxSize` = 50331648 | Percent-based value |
| M6 (R7) | Scroll Home rows with `KEYCODE_DPAD_DOWN` x10, `KEYCODE_DPAD_RIGHT` x20 per row, then back up | Owner sees no visible poster reloads | Placeholders flashing on posters already seen |
| M7 (AC-6) | 1. Cold start, wait 70 s. 2. `KEYCODE_HOME`. 3. Row count via `sqlite3 ... 'SELECT COUNT(*) FROM cached_payload'` | Count at most 2,000; no ANR, no StrictMode main-thread disk violation from the sweep | Crash while backgrounded |

#### Bugs

N/A: refactor, no defect to reproduce.

#### Perf track

Same emulator, same dev debug build type, baseline in `baseline.md` captured from a22c03bc before implementation.

| Metric | Method | Pass threshold |
|---|---|---|
| `media_cache` growth over the M1 session | `du -sb` before/after | 0 bytes (baseline: today's growth) |
| `media_cache` size after M3 | `du -sb` | <= 268435456 bytes |
| `cached_payload` row count after the fixed browsing session and one sweep (M7) | `SELECT COUNT(*)` | <= 2,000 and <= baseline |
| `puber.db` size after the same session | `du -b` of `puber.db` + `-wal` | <= baseline |
| Coil memory cap | M5 log | 50331648 bytes (baseline: `maxSizePercent` result) |
| App PSS after the M6 scroll | `core/telemetry/MemoryProbe.kt` output, 3 runs, median | <= baseline median |

#### UNTESTABLE

None.

### Baseline (qa-device, unchanged build a22c03bc)

Unchanged build at base a22c03bc (worktree clean). Dev debug APK sha256 ea6b517688c4...d109d86 (version 1.12.3-dev), installed with install-preserve (outcome installed, compatible_replace, no destructive action); on-device base.apk sha256 matches. Device: emulator-5554 (AVD puber_tv_36, API 36, 1920x1080, heapgrowthlimit 192m, heapsize 512m), lease held and released. Date 2026-11-21. Note: the emulator process had died mid-session and was restarted (headless, `-read-only`); app, APK and data were intact afterwards (same sha256, same databases).

#### Media cache (AC-1, AC-2, AC-3, AC-11)

- Starting state: `media_cache` = 526360 KB by du (about 514 MB, 440 files), i.e. at the 512 MB cap. AC-3 starting condition (more than 256 MB) already holds.
- Because the cache is full, du stays flat during playback (eviction offsets writes). Bytes written are therefore measured as the total size of files that did not exist before playback (file list before vs after, `find -printf` over adb).
- Playback: series (Season 1, episode 1), started from the 4K tab with OK, main player only, about 6.5 min, no next-episode warm-up reached. Single run.

| Minute | new files | new MB | evicted files | evicted MB | total MB |
|---|---|---|---|---|---|
| 0 | 11 | 8.8 | 12 | 14.7 | 530.3 |
| 1 | 35 | 45.2 | 40 | 45.6 | 535.9 |
| 2 | 51 | 65.9 | 56 | 67.2 | 535.0 |
| 3 | 51 | 65.9 | 56 | 67.2 | 535.0 |
| 4 | 67 | 81.9 | 76 | 81.5 | 536.6 |
| 5 | 83 | 103.6 | 96 | 103.2 | 536.6 |
| 6 | 99 | 127.5 | 106 | 128.3 | 535.5 |

Main playback wrote about 127 MB in 6.5 min (about 20 MB/min, bursty, plateau at minute 2-3); du after: 525564 KB. For AC-1 the after-value must be 0 new bytes in the same measure; extrapolated 30 min is about 590 MB at this rate (single sample, rate depends on the stream).

#### cached_payload and puber.db (AC-4, AC-5, AC-7, AC-11)

- Schema version 3 (user_version 3); tables android_metadata, watch_state, watch_state_sync, cached_payload, room_master_table.
- Before: 2870 rows, payload total 24,528,435 bytes, puber.db 29,618,176 bytes (7230 pages of 4096, no free pages). updated_at range 2026-10-10 .. 2026-11-21.
- Rows older than 7 days (HardCeiling): 2444 rows, 15,319,229 bytes of payload (85 percent of rows; never swept today).
- Key prefixes: content:v1:item 2711 rows (24.49 MB), content:v1:query 156 (0.03 MB), content:v1:payload 3 (0.01 MB). Rows not starting with content:v1: (legacy): 0.
- After a fixed session (cold start, 5 x 18 D-pad steps on Home, Series tab, one series details open, 6.5 min playback): 2882 rows, 24,714,378 bytes, puber.db 29,835,264 bytes. A 2000-row cap would delete about 880 rows at the first sweep.
- Legacy cleanup cost: this database has 0 legacy rows, so the six removeByPrefix calls (home:, item:, similar:, section:, watchlist:, history:) delete nothing here; they run once per process on the first cache access. Per-call duration was not measured on device (no instrumentation at base); not captured.

#### Coil memory cache and memory (AC-9, AC-11)

- Cap at base: maxSizePercent 0.15 of the app memory class (largeHeap="true"). With heapgrowthlimit 192m / heapsize 512m on this emulator that is about 77 MB if the large class (512m) is used, or 29 MB for the normal class; the exact runtime value was not read (no debug log or MemoryProbe output: telemetry is off, last telemetry file from 2026-10-15). Not captured on device.
- dumpsys meminfo (KB, PSS / Java heap alloc / native heap alloc):
  - after cold start, Home loaded (about 15 s): PSS 254142, Java 49932, native 45836
  - after the Home D-pad scroll session: PSS 276232, Java 45600, native 68504
  - during playback (after about 6.5 min): PSS 306063, Java 85232, native 47664
- Home scroll screenshot showed posters loaded; no placeholder churn observed.

#### Startup

- am start -W TotalTime, cold, same build, emulator under load from another worktree: 2059, 2361, 4721 ms (first session), 6385 ms (after emulator restart, cold boot). High spread, not a stable baseline; startup marks (telemetry) not captured.

#### Not measured

AC-2 warm-up bytes and onCachedBytesRead (needs a debug listener, not in base; also needs a longer episode run), Coil maxSize runtime value, legacy cleanup duration, startup marks.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Legacy rows: delete once in a Room 3->4 migration, or runtime cleanup behind a saved prefs flag? | yes | answered | Room 3->4 migration |
| Q2 | cached_payload cap by rows or bytes, and value? | no | answered | Rows, 2,000, confirmed from the emulator row count (AC-11) |
| Q3 | Coil memory limit? | no | answered | 48 MB fixed |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Main player reads the media cache only, warm-up writes; cap 256 MB (from cache-layer-rework Q2) | owner |
| 2026-11-21 | Q1-Q3 as answered above | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approved as written | owner (standing instruction: recommended) |

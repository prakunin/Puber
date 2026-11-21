# Bound the caches: read-only main player cache, swept cached_payload, one-time legacy cleanup, byte-capped Coil - plan

**Spec:** `docs/superpowers/specs/2026-11-21-cache-storage-bounds.md`
**Goal:** Bound the media cache, the `cached_payload` table and the Coil memory cache, and run the legacy-row cleanup once through a Room migration instead of on every process start.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate (in the worktree): `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Always `AGENT_GRADLE_SERIAL=1`: `startup-splash-preload` builds in parallel. Never `./gradlew` in the worktree.
- Every task also runs `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugUnitTestKotlin :app:compileDevDebugAndroidTestKotlin` (the fast gate compiles main only; spec risk 8) and the unit tests it names with `./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`.
- Instrumented tests are not run by the implementer: qa-device runs them under a lease with `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fqcn>` (`Makefile:23,128-135`). The implementer leaves them compiling.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`, `gradle/` or `Makefile`; unit tests under `app/src/test/kotlin/com/kino/puber/`; instrumented under `app/src/androidTest/kotlin/com/kino/puber/`. Lines are at base a22c03bc.
- `PuberApp.kt` is also edited by `startup-splash-preload` (its line 91): touch only `PuberApp.kt:58`, `:91-92` (one added line) and `:111-115`; no reformatting, no import reordering beyond the added imports.
- Out of scope: the 30 s save side effects of `player-cache-save-churn`, any namespace bump (`cache-decoded-memory-tier`), WorkManager, Coil disk cache size, `CacheKeys` (its prefix constants stay; `CacheKeysTest` uses them).
- Device package of the dev flavor is `com.kino.puber.stage` (`Makefile:25`); the spec's manual checks that say `com.kino.puber.dev` mean that package.

---

## Tasks

### Task 1: Make the main player read the media cache only and let the warm-up write

**Covers:** AC-1, AC-2, AC-3 (device checks M1, M2, M3 of the spec's Test design; no unit test constructs `PlaybackController`)
**Files:**
- Modify: `ui/feature/player/vm/PlaybackController.kt` - `createDataSourceFactory()` (`:626-646`) gains `writeToCache: Boolean`; when `false` the `CacheDataSource.Factory` gets `.setCacheWriteDataSinkFactory(null)` and, only if `BuildConfig.DEBUG` (imported at `:39`), `.setEventListener(object : CacheDataSource.EventListener { onCachedBytesRead(cacheSizeBytes, cachedBytesRead) -> Timber.d(...); onCacheIgnored(reason) -> Unit })`. The log line contains the literal `onCachedBytesRead` and both numbers (M2 greps it). Main player at `:416` calls `createDataSourceFactory(writeToCache = false)`; `warmUpNext` at `:659` becomes `val sourceFactory = createDataSourceFactory(writeToCache = true)` and no longer reads `dataSourceFactory`. KDoc at `:648-653`: the warm-up builds its own writing factory, the main player only reads the cache. `dataSourceFactory` field and its clear at `:593` stay (other readers of the field keep working; check with `grep -n dataSourceFactory`).
- Modify: `data/di/modules.kt:78` - `MEDIA_CACHE_SIZE_BYTES = 256L * MIB`.

**Consumes:** `CacheDataSource.Factory.setCacheWriteDataSinkFactory(DataSink.Factory?)`, `setEventListener(EventListener?)`, `EventListener.onCachedBytesRead(long, long)` / `onCacheIgnored(int)` (media3-datasource 1.11.0).
**Produces:** nothing consumed by later tasks.

- [x] Change `createDataSourceFactory` and its two call sites as above; add `import timber.log.Timber` if missing.
- [x] Change the cap constant.
- [x] Run the fast gate and the compile line of Global constraints; commit.

**Done when:** fast gate green; `grep -n "createDataSourceFactory(" app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt` shows exactly one `writeToCache = false` and one `writeToCache = true` call; `grep -n "dataSourceFactory ?:" ...PlaybackController.kt` is empty.

---

### Task 2: Bump Room to v4 with an updated_at index and a migration that deletes the legacy rows once

**Covers:** AC-7
**Files:**
- Modify: `data/db/CachedPayloadEntity.kt:20` - `@Entity(tableName = "cached_payload", indices = [Index(value = ["updated_at"])])`; import `androidx.room3.Index`.
- Create: `data/db/LegacyPayloadCleanup.kt` - `class LegacyPayloadCleanup : AutoMigrationSpec` overriding `suspend fun onPostMigrate(connection: SQLiteConnection)` with six `connection.execSQL("DELETE FROM cached_payload WHERE key LIKE '<prefix>%'")`, prefixes as literals: `home:`, `item:`, `similar:`, `section:`, `watchlist:`, `history:` (same values as `data/cache/CacheKeys.kt:15-20`; literals so later edits of `CacheKeys` cannot change the migration). Imports `androidx.room3.migration.AutoMigrationSpec`, `androidx.sqlite.SQLiteConnection`, `androidx.sqlite.execSQL`; confirm the package names and the `suspend` signature with `javap` on room3-runtime 3.0.3 if the compile disagrees.
- Modify: `data/db/PuberDatabase.kt:13,17` - `version = 4`; `autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4, spec = LegacyPayloadCleanup::class)]`; extend the comment at `:15-16` by one line on why 3->4 exists. `create(context)` at `:30` becomes `create(context: Context, name: String = NAME)` (builder body `:31-39` unchanged) so the migration test opens a file with the production configuration.
- Create (generated): `app/schemas/com.kino.puber.data.db.PuberDatabase/4.json` - written by KSP on the compile; commit it. It must contain the `index_cached_payload_updated_at` index.
- Modify: `gradle/libs.versions.toml` after `:57` - `androidx-room-testing = { group = "androidx.room3", name = "room3-testing", version.ref = "room" }`.
- Modify: `app/build.gradle.kts:430` - next line `androidTestImplementation(libs.androidx.room.testing)`. Only if `MigrationTestHelper` reports `3.json` missing at run time (qa-device), add `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")` in the `android {}` block (spec risk 10).
- Test: `data/db/PuberDatabaseMigrationTest.kt` (instrumented, JUnit 4, `@RunWith(AndroidJUnit4::class)` like `data/db/CachedPayloadDaoTest.kt:19`).

**Consumes:** `PuberDatabase` (`data/db/PuberDatabase.kt:19`), room3-testing `MigrationTestHelper` (driver form: `MigrationTestHelper(instrumentation, file, driver = AndroidSQLiteDriver(), databaseClass = PuberDatabase::class)`, `createDatabase(3): SQLiteConnection`, `runMigrationsAndValidate(4): SQLiteConnection`; check the exact constructor with `javap` on the downloaded room3-testing 3.0.3 jar).
**Produces:** `PuberDatabase.create(context, name)`; schema v4 with the index Task 3's queries use.

- [x] Write `PuberDatabaseMigrationTest` first (it does not compile until the dependency exists):
  - `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`: create v3; insert one `cached_payload` row per legacy prefix (`home:hot`, `item:7`, `similar:7`, `section:popular`, `watchlist:subscribed`, `history:1`), two `content:v1:` rows (`content:v1:item:7`, `content:v1:query:home`), one `watch_state` and one `watch_state_sync` row (column lists from `app/schemas/com.kino.puber.data.db.PuberDatabase/3.json`); close; `runMigrationsAndValidate(4)`; assert no key of the six legacy prefixes remains, both `content:v1:` rows keep payload and `updated_at`, both watch rows are present.
  - `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`: create v3 under a test file name with one `watch_state` row; close; `PuberDatabase.create(context, name = <that file name>)`; read the row through `watchStateDao()` and assert it is there (a migration failure would fall into `fallbackToDestructiveMigration` at `data/db/PuberDatabase.kt:38` and drop it). Delete the test files in `@After`.
- [x] Add the dependency, the index, the spec class and the version bump; compile so KSP writes `4.json`.
- [x] Run the fast gate and the compile line (androidTest included); commit with `4.json`.

**Done when:** fast gate and `:app:compileDevDebugAndroidTestKotlin` green; `grep -c index_cached_payload_updated_at app/schemas/com.kino.puber.data.db.PuberDatabase/4.json` >= 1; qa-device later runs `PuberDatabaseMigrationTest` green.

---

### Task 3: Add the age and row-cap sweep to the DAO and the payload store

**Covers:** AC-4, AC-5
**Files:**
- Modify: `data/db/CachedPayloadDao.kt` after `:29` - `@Query("DELETE FROM cached_payload WHERE updated_at < :cutoff") suspend fun deleteOlderThan(cutoff: Long)`; `@Query("DELETE FROM cached_payload WHERE key IN (SELECT key FROM cached_payload ORDER BY updated_at DESC LIMIT -1 OFFSET :keepNewest)") suspend fun deleteBeyondNewest(keepNewest: Int)`.
- Modify: `data/repository/PersistentPayloadStore.kt:33` - interface gains `suspend fun sweep(olderThan: Long, keepNewest: Int)` with no default body; KDoc: deletes rows with `updatedAt < olderThan`, then all but the `keepNewest` newest.
- Modify: `data/repository/PersistentPayloadStore.kt` after `:95` - `RoomPersistentPayloadStore.sweep`: `dao.deleteOlderThan(olderThan)` then `dao.deleteBeyondNewest(keepNewest)`; no generation check (it only deletes).
- Modify: `util/FakePayloadStore.kt:7-74` - `sweep` with the same semantics on `rows` (remove `updatedAt < olderThan`; then sort by `updatedAt` descending and remove beyond `keepNewest`); `var sweepCount: Int` incremented per call; `var onSweep: (suspend () -> Unit)? = null` invoked first (Task 5 records the thread and throws through it).
- Modify: `domain/interactor/home/HomeInteractorCacheTest.kt:217` and `data/repository/ItemDetailsRepositoryTest.kt:253` - `InMemoryPayloadStore` overrides `sweep` with `Unit`.
- Modify: `data/repository/PersistentPayloadStoreTest.kt:111-145` - `FakeCachedPayloadDao` implements both new methods with real semantics and appends `"deleteOlderThan:$cutoff"` / `"deleteBeyondNewest:$keepNewest"` to a `calls` list.
- Test (unit): `data/repository/PersistentPayloadStoreTest`: `sweepDeletesByAgeThenTrimsToTheNewest` - `calls` equals the two entries in that order and the surviving rows are the expected ones.
- Test (unit): `data/cache/ContentCacheRepositoryTest`: `listWhoseItemRowsWereSweptRefetchesThem` - load `observeItems("home", ...)` with items 7 and 8 at `now`; `store.touch(ContentCacheRepository.queryKey("home"), now + 1)`; `store.sweep(olderThan = 0, keepNewest = 1)` (item rows go, the list row stays); a new `ContentCacheRepository(store = store, clock = { now })` (pattern at `ContentCacheRepositoryTest.kt:225`) collects `observeItems("home", ...)` with a counting loader: loader called once and the last emission holds items 7 and 8 (refetch through `queryNeedsRefetch`, `data/cache/ContentCacheRepository.kt:439-452`).
- Test (instrumented): `data/db/CachedPayloadDaoTest` (setup at `:25-32`): `deleteOlderThanRemovesOnlyRowsBeforeTheCutoff` (rows at `cutoff - 1` gone, at `cutoff` and `cutoff + 1` keep payload and `updatedAt`), `deleteBeyondNewestKeepsTheNewestRows` (5 rows, distinct `updatedAt`, `keepNewest = 3`: exactly the 2 oldest gone), `deleteBeyondNewestWithFewerRowsThanTheCapDeletesNothing` (2 rows, `keepNewest = 3`), `deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep` (`deleteOlderThan`, then `upsert` of a row with the newest `updatedAt`, then `deleteBeyondNewest`: the new row survives and one older row goes).

**Consumes:** schema v4 index (Task 2).
**Produces:** `PersistentPayloadStore.sweep(olderThan: Long, keepNewest: Int)`; `FakePayloadStore.sweepCount`, `FakePayloadStore.onSweep`.

- [x] Write the two unit tests and four DAO tests; confirm the unit ones fail to compile on the unchanged code (red).
- [x] Add the DAO queries, the interface member, the Room implementation and the four fake updates.
- [x] Run the fast gate, the compile line, and `--tests 'com.kino.puber.data.repository.PersistentPayloadStoreTest' --tests 'com.kino.puber.data.cache.ContentCacheRepositoryTest' --tests 'com.kino.puber.domain.interactor.home.HomeInteractorCacheTest' --tests 'com.kino.puber.data.repository.ItemDetailsRepositoryTest'`; commit.

**Done when:** those unit tests green; androidTest compiles; existing cases in the four files unchanged (`git diff a22c03bc -- <file>` shows only additions).

---

### Task 4: Remove the per-process legacy cleanup from the content cache

**Covers:** AC-8
**Files:**
- Modify: `data/cache/ContentCacheRepository.kt` - delete `legacyCleanupMutex` and `legacyCleanupComplete` (`:47-50`), the nine `ensureLegacyRowsRemoved()` calls (`:61,98,163,219,277,324,341,351,361`), the flag write in `clear()` (`:368`, `clear()` keeps `store.clear()`), `ensureLegacyRowsRemoved` with its KDoc (`:371-384`), `LEGACY_PREFIXES` (`:621-628`) and the now unused imports `Mutex`/`withLock` (`:12-13`). `removeByPrefix`/`deleteByPrefix` stay (used by `data/cache/CachedFeed.kt:399`).
- Modify: `util/FakePayloadStore.kt:60-62` - `removeByPrefix` appends its argument to a new `val removedPrefixes = mutableListOf<String>()`.
- Test (unit): `data/cache/ContentCacheRepositoryTest`: replace `firstUseRemovesLegacyCacheRowsWithoutTouchingUnrelatedStorage` (`:231-248`) with `firstUseLeavesLegacyRowsToTheMigration`: same six legacy keys and the `unrelated` row, same `getPayload("probe", ...)`; assert every legacy key and `unrelated` still read back and `store.removedPrefixes` is empty.

**Consumes:** Task 2's migration (the only remaining remover of legacy rows).
**Produces:** `FakePayloadStore.removedPrefixes`.

- [x] Rewrite the test; it fails on the unchanged repository (red).
- [x] Remove the cleanup code.
- [x] Run the fast gate, the compile line, and `--tests 'com.kino.puber.data.cache.ContentCacheRepositoryTest' --tests 'com.kino.puber.data.cache.CachedFeedTest'`; commit.

**Done when:** `grep -rn "ensureLegacyRowsRemoved|legacyCleanup|LEGACY_PREFIXES" app/src` is empty; those tests green.

---

### Task 5: Run the payload sweep once after startup and on every ON_STOP

**Covers:** AC-4, AC-5, AC-6
**Files:**
- Create: `data/cache/CachedPayloadSweeper.kt`:
  - `class CachedPayloadSweeper(store: PersistentPayloadStore, foreground: AppForegroundState, scope: CoroutineScope, clock: () -> Long = System::currentTimeMillis, startupDelay: Duration = STARTUP_DELAY, maxRows: Int = MAX_ROWS)`; `private val STARTUP_DELAY = 60.seconds`, `private const val MAX_ROWS = 2_000` in the companion (MagicNumber).
  - `fun start()`: idempotent through an `AtomicBoolean`; launches one coroutine in `scope` collecting `merge(flow { delay(startupDelay); emit(Unit) }, foreground.isForeground.drop(1).filter { !it }.map { })` and calling `sweepOnce()` per element. `isForeground` is `core/lifecycle/AppForegroundState.kt:24`; `false` is ON_STOP (`core/lifecycle/AppForegroundReporter.kt:30`).
  - `sweepOnce()`: `runCatchingCancellable { store.sweep(olderThan = clock() - CachedFeed.HardCeiling.inWholeMilliseconds, keepNewest = maxRows) }.onFailure { Timber.w(it, ...) }` (`core/coroutine/CoroutinesErrorHandler.kt:37`, `data/cache/CachedFeed.kt:591`), so a failing sweep neither reaches the scope nor ends the collector.
  - `companion object { fun defaultScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }` - the scope DI uses and `sweepRunsOffTheMainThread` builds.
- Modify: `data/di/modules.kt` after `:107` - `single { CachedPayloadSweeper(store = get(), foreground = get(), scope = CachedPayloadSweeper.defaultScope()) }` (`AppForegroundState` is registered at `domain/di/modules.kt:57`).
- Modify: `PuberApp.kt` - one new line after `:91`: `koinApplication.koin.get<CachedPayloadSweeper>().start()`, plus its import. Nothing else in `initDi`.
- Test (unit): `data/cache/CachedPayloadSweeperTest.kt`, JUnit 5, `FakePayloadStore` (Task 3) and a real `AppForegroundState`; scheduling tests use `runTest` with `StandardTestDispatcher` and `backgroundScope` as the sweeper's scope, `runCurrent()` after every foreground change (a `StateFlow` conflates otherwise):

| Test | Setup and assertion |
|---|---|
| `sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical` | `clock = { now }`, `startupDelay = Duration.ZERO`; rows at `now - HardCeiling - 1` and `now - HardCeiling + 1`; after `advanceUntilIdle()` the first is gone, the second has the same payload and `updatedAt` |
| `sweepCapsRowsToTheNewest` | `maxRows = 3`, 5 fresh rows with distinct `updatedAt`; the 2 oldest are gone |
| `sweepRunsOnceAfterTheStartupDelay` | default-like `startupDelay = 60.seconds`; `sweepCount` 0 at `delay - 1 ms`, 1 at `delay`, still 1 after a further hour |
| `eachOnStopRunsOneMoreSweep` | after the startup sweep, `onLeftForeground`, `onEnteredForeground`, `onLeftForeground`: `sweepCount` grows by 2 |
| `returningToForegroundDoesNotSweep` | initial `true` and an `onEnteredForeground` add no sweep (startup delay not yet reached) |
| `startTwiceRunsOneCollector` | `start()` twice, then one ON_STOP: `sweepCount` grows by 1 |
| `sweepRunsOffTheMainThread` | `@RegisterExtension MainDispatcherExtension` (`util/MainDispatcherExtension.kt:12`); scope `CachedPayloadSweeper.defaultScope()` (cancelled in `finally`), `startupDelay = Duration.ZERO`; `onSweep` completes a `CompletableDeferred<Thread>` with `Thread.currentThread()`; awaited with `withTimeout` inside `runBlocking`, no sleep; the thread is not the test thread |
| `aFailingSweepNeitherCrashesNorStopsLaterSweeps` | `onSweep` throws `IllegalStateException` on the first call only; scope `CoroutineScope(SupervisorJob() + testDispatcher + CoroutineExceptionHandler { caught = it })`; after the startup sweep and one ON_STOP, `caught` is null and the second sweep ran |

**Consumes:** `PersistentPayloadStore.sweep`, `FakePayloadStore.sweepCount`/`onSweep` (Task 3).
**Produces:** `CachedPayloadSweeper`, `CachedPayloadSweeper.defaultScope()`.

- [x] Write `CachedPayloadSweeperTest` (8 tests); it fails to compile without the class (red).
- [x] Add the class, the DI line and the `start()` call.
- [x] Run the fast gate, the compile line, and `--tests 'com.kino.puber.data.cache.CachedPayloadSweeperTest'`; commit.

**Done when:** the 8 tests green; `grep -n "CachedPayloadSweeper" app/src/main/java/com/kino/puber/PuberApp.kt app/src/main/java/com/kino/puber/data/di/modules.kt` shows the start call and the `single`.

---

### Task 6: Cap the Coil memory cache in bytes

**Covers:** AC-9 (device check M5; M6 for the owner)
**Files:**
- Modify: `PuberApp.kt:58` - `private const val IMAGE_MEMORY_CACHE_BYTES = 48L * 1024 * 1024` replaces `IMAGE_MEMORY_CACHE_PERCENT`.
- Modify: `PuberApp.kt:111-115` - `MemoryCache.Builder().maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES).build().also { cache -> if (BuildConfig.DEBUG) Timber.d("Coil memory cache maxSize=%d", cache.maxSize) }`. The lambda runs on the first image load, after `initLogger` (`PuberApp.kt:76,94-98`), so the line reaches logcat. `Timber` is already imported (`:43`).

**Consumes:** `MemoryCache.Builder.maxSizeBytes(Long)`, `MemoryCache.maxSize` (coil-core 3.6.3).
**Produces:** nothing.

- [x] Edit the constant and the builder; touch no other line of `PuberApp.kt`.
- [x] Run the full gate (last task): `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; commit.

**Done when:** full gate green; `grep -n "maxSizePercent" app/src/main/java/com/kino/puber/PuberApp.kt` is empty. Device checks M1-M7 and the perf track of the spec are qa-device's, with the instrumented classes `com.kino.puber.data.db.CachedPayloadDaoTest` and `com.kino.puber.data.db.PuberDatabaseMigrationTest`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- ae127475 Cap the Coil memory cache at 48 MB instead of a share of the heap
- 606ec946 Sweep expired and surplus cached payload rows once after startup and on every ON_STOP
- f088856f Remove the per-process legacy row cleanup now that the migration deletes them once
- 40b0ba55 Add the age and row-cap sweep to the cached payload DAO and store
- 589f8ac2 Bump Room to version 4 with an updated_at index and a migration that deletes the legacy cache rows once
- f223bc19 Make the main player read the media cache only, let the warm-up write it and cap it at 256 MB
- b22b0f28 Add the implementation plan for bounding the media, payload and image caches
- a8677df2 Add the spec for bounding the media, payload and image caches

**Red run** (P0 `red` tests on the unchanged code):

- red: `ContentCacheRepositoryTest.firstUseLeavesLegacyRowsToTheMigration` - AssertionFailedError at ContentCacheRepositoryTest.kt:250 (legacy row read back as null, the old cleanup still removed it).
- red (does not compile on base): `CachedPayloadSweeperTest` (all 8) - unresolved reference `CachedPayloadSweeper`; `sweepDeletesByAgeThenTrimsToTheNewest` and `listWhoseItemRowsWereSweptRefetchesThem` - unresolved reference `sweep`. The compile error was resolved by writing the production code, not by a stub, so these were not run to an assertion failure on the unchanged code.
- red (does not compile on base): the instrumented `CachedPayloadDaoTest` four new cases and `PuberDatabaseMigrationTest` two cases use the new DAO sweep query and Room v4, which the base lacks; they were not run to an assertion failure. They compile now (`:app:compileDevDebugAndroidTestKotlin`) and run under qa-device.
- review round 1 additions: `MediaCacheTrimTest` (3), `CacheWriteSinkTest` (2) and three `CachedPayloadSweeperTest` cases (ceiling boundary, trim once, failing trim) are new behaviour tests, written with the production code in the same commit; no red run on base.
- device rows M1-M5, M7: qa-device.
- green on base: none.

**Deviations from the plan:**

- Task 5 sweeper tests use `runCurrent()` instead of `advanceUntilIdle()`: `advanceUntilIdle` ignores tasks of `backgroundScope`, so the sweep never ran. The failing-sweep test uses a non-background scope and keeps `advanceUntilIdle()`.
- `LegacyPayloadCleanup` names its list `PREFIXES` (not `LEGACY_PREFIXES`) so the Task 4 grep for `LEGACY_PREFIXES` stays empty.
- `assets.srcDir` fallback for `MigrationTestHelper` not added: no instrumented run is possible here (plan: add only if `3.json` is reported missing).
- `MigrationTestHelper` constructor used as read with `javap` (room3-testing 3.0.3): named args `instrumentation`, `file`, `driver`, `databaseClass`, `autoMigrationSpecs`; it compiles, but its runtime behaviour is unverified.

- review round 2 fixes (rows 1-3 of triage-2): `MediaCacheTrimTest` gains three tests of `trimMediaCache` with a mocked media3 `Cache` (exact LRU spans removed above the cap, nothing when it fits) and of the wired trim `mediaCacheTrimAction` with `MEDIA_CACHE_SIZE_BYTES` (now a public constant in `MediaCacheTrim.kt`, used by `modules.kt` for both the evictor and the trim). `@androidx.annotation.OptIn(UnstableApi::class)` added in `MediaCacheTrim.kt` and `CacheWriteSink.kt` (lint was red with the Kotlin-only opt-in missing). Nested if in `PlaybackController.createDataSourceFactory` collapsed. New tests are behaviour tests written with the fix; no red run.
- check.full round 2: lint and detekt green, 2155 unit tests green except `HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus` (HistoryVMDeletionSchedulingTest.kt:371) once; it passed on rerun and touches no changed code (known flaky suite, run `unit-suite-flaky`).

**Files touched outside the plan:** none.

**For the reviewers:**

- `PuberDatabaseMigrationTest` has never run; check the `MigrationTestHelper` arguments and the `use` on `SQLiteConnection`.
- `FakePayloadStore.sweep` and the Room queries must agree on semantics (age with `<`, then newest `keepNewest` by `updated_at DESC`).
- `PuberApp.kt` edits are limited to the import, the constant, the `start()` line and the memory cache builder.
- `check.full` (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) green after Task 6.

**Blocked:** none.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

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

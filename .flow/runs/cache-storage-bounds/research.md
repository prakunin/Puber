# Research: cache-storage-bounds

## Request

split item B3 of cache-layer-rework. "Bound the caches: main player cache read-only, cached_payload swept at idle with a cap, legacy-cleanup flag persisted, Coil memory cache capped in bytes"

Owner decision inherited (`state.md` Decisions): main player reads the media cache only, warm-up player writes; cap 512 MB -> 256 MB after the device check.

## Understanding

Four storage bounds, each independent of the others: (1) the main ExoPlayer stops writing played bytes into the shared `SimpleCache` while the next-episode warm-up player keeps writing, and the cache cap drops to 256 MB; (2) `cached_payload` gets an idle-time sweep that deletes expired rows, then the oldest, down to a cap; (3) the one-time deletion of pre-`content:v1:` rows stops running on every process start; (4) the Coil memory cache gets a fixed byte cap instead of a percentage of the largeHeap memory class. Seed ACs: `../cache-layer-rework/brief.md` AC-3, AC-4, AC-8, AC-9.

## Current state in code

Paths under `app/src/main/java/com/kino/puber/`, master a22c03bc.

| Area | Location | Now |
|---|---|---|
| Media cache | `data/di/modules.kt:77-78` | `MEDIA_CACHE_SIZE_BYTES = 512L * MIB` |
| Media cache | `data/di/modules.kt:110-117` | `single<Cache>`: `SimpleCache` in `externalCacheDir/media_cache`, `LeastRecentlyUsedCacheEvictor`, `StandaloneDatabaseProvider`. Only consumer is `PlaybackController` (no other `mediaCache`/`SimpleCache` use in `app/src/main`). |
| Player | `ui/feature/player/vm/PlaybackController.kt:626-646` | `createDataSourceFactory()`: per-player OkHttp client, `CacheDataSource.Factory` with `setCache`, upstream, `FLAG_IGNORE_CACHE_ON_ERROR`; no `setCacheWriteDataSinkFactory`, so it writes. |
| Player | `PlaybackController.kt:416-417`, `:593` | Main player stores its factory in `dataSourceFactory`; cleared on release. |
| Warm-up | `PlaybackController.kt:654-706` | `warmUpNext` reuses `dataSourceFactory ?: createDataSourceFactory()` (`:659`), so main and warm-up share one factory today; splitting them is the core code change. Called from `PlayerVM.kt:1679`. |
| DI | `ui/feature/player/component/PlayerScreen.kt:54` | `PlaybackController(get(), get(), get(), get())`, screen-scoped. |
| Room entity | `data/db/CachedPayloadEntity.kt:20-26` | `key` PK, `payload` TEXT, `updated_at` INTEGER; no index on `updated_at` (also `app/schemas/.../PuberDatabase/3.json`). |
| Room DB | `data/db/PuberDatabase.kt:11-17,38` | `version = 3`, `exportSchema = true`, `AutoMigration(2, 3)`, `fallbackToDestructiveMigration(dropAllTables = true)`; schemas in `app/schemas` (`app/build.gradle.kts:240-242`). |
| DAO | `data/db/CachedPayloadDao.kt:27-28` | `deleteByPrefix`: `LIKE :prefix || '%'`; no sweep, count or size query. |
| Store | `data/repository/PersistentPayloadStore.kt:12-35,37-107` | Interface plus Room impl; `generation` bumped around `clear()`; `write` deletes its row if generation moved. A sweep must not race that. |
| Expiry | `data/cache/CachedFeed.kt:558-566,591` | Rows older than `HardCeiling` (7 days) are deleted only when read; never-read keys stay forever. |
| Legacy cleanup | `data/cache/ContentCacheRepository.kt:47-50,365-384,621-628` | `@Volatile legacyCleanupComplete` in memory; first cache access per process runs six `removeByPrefix` under `legacyCleanupMutex`; `clear()` sets the flag. |
| Coil | `PuberApp.kt:58-59,105-124` | `MemoryCache.Builder().maxSizePercent(context, 0.15)`; disk cache 100 MB. `AndroidManifest.xml:30` `largeHeap="true"`; the audit estimates 58-77 MB for the memory tier (unverified on device). |
| Idle hooks | `core/lifecycle/AppForegroundReporter.kt:23-37` | ON_START/ON_STOP reported to `AppForegroundState`; no WorkManager, no `IdleHandler`, no existing sweep scheduler. |
| Prefs | `data/repository/PlayerPreferencesRepository.kt:12` and others | Plain `SharedPreferences` per repository; a flag would follow this pattern. |

Tests: `app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt:232-246` (`firstUseRemovesLegacyCacheRowsWithoutTouchingUnrelatedStorage`), `CachedFeedTest.kt`, `test/util/FakePayloadStore.kt`. No unit test constructs `PlaybackController` (none in `test/.../ui/feature/player/vm/`); the warm-up is covered at VM level by `PlayerVMNextEpisodePrefetchTest.kt`.

Commits: 6be75d28 (2026-08-20, introduced `content:v1:` and `LEGACY_PREFIXES`), 9690b9e8 (2026-08-18, schema v3), 264748e7 (memory tier by bytes). Schema v3 predates `content:v1:`, so legacy rows can exist only in v3 databases.

## Related work

| Run | Stage | Overlap |
|---|---|---|
| cache-layer-rework | parent | B3 scope, Q2 answer, AC-3/AC-4 seeds. |
| content-cache-legacy-cleanup | idea | Title "Stop the per-start legacy-row LIKE deletes and bound cached_payload": strict subset of this run. Probable duplicate; fold in and close. |
| player-cache-save-churn | idea | "Reduce player cache writes and the 30 s save side effects": the media-cache half is this run, the save side effects are `cache-targeted-invalidation` (B4). Split or close across the two. |
| startup-splash-preload | impl (worktree) | Branch diff and dirty files touch `core/startup/*`, `PuberVM`, `HomeVM`, telemetry; its plan edits `PuberApp.kt:33,91` (`AppStartup.start()`) and `domain/di/modules.kt` (`AppCoroutineScope`). No edit to `ContentCacheRepository`, `CachedPayloadDao`, `PuberDatabase`, `PlaybackController` or `data/di/modules.kt`. Conflict risk: `PuberApp.kt` (Coil block `:105-124` vs `:91`, text-adjacent only). Its warm-up hits the first cache access, so it is the first caller to pay `ensureLegacyRowsRemoved`; its `AppCoroutineScope` is a candidate owner for the idle sweep once merged. |
| startup-settled-signal | idea | "Startup settled" signal; a natural trigger for the sweep. |
| cache-decoded-memory-tier (B1) | idea | Plans a namespace bump that orphans every `content:v1:` row; the sweep and the migration-time cleanup here are what remove them. |
| media3-hagc-removal | idea | Media3 pinned at 1.11.0; no API change needed here. |

## External findings

| Finding | Source |
|---|---|
| `CacheDataSource.Factory.setCacheWriteDataSinkFactory(null)` makes the cache read-only; the default is a `CacheDataSink.Factory`. The method exists in media3-datasource 1.11.0 (checked with `javap` on the local Gradle cache). | https://developer.android.com/reference/kotlin/androidx/media3/datasource/cache/CacheDataSource.Factory (current) |
| Media3 guide: a player that also plays non-downloaded content should use a read-only `CacheDataSource.Factory`; read-only cannot be toggled at runtime, so main and warm-up need separate factories. | https://developer.android.com/media/media3/exoplayer/downloading-media; https://github.com/google/ExoPlayer/issues/8053 |
| Coil 3 `MemoryCache.Builder` has `maxSizeBytes(Long)` besides `maxSizePercent(context, Double)`; seen in a third-party listing of coil-core 3.3.0, not in the 3.6.3 jar (not in the local cache). Unverified for 3.6.3; the compile settles it. | https://coil-kt.github.io/coil/image_loaders/; https://github.com/addict85/BrickInventory/pull/29 |

## Options

| Option | Content | Trade-offs | Size |
|---|---|---|---|
| A. Recommended | Media: two factories in `PlaybackController` (main read-only via `setCacheWriteDataSinkFactory(null)`, warm-up writes), cap 256 MB. Room v4: index on `updated_at`; `AutoMigration(3, 4)` with a spec whose `onPostMigrate` runs the six legacy deletes once, and `ensureLegacyRowsRemoved` plus its mutex and flag removed. Sweep: DAO deletes `updated_at < now - HardCeiling`, then oldest rows beyond a row cap, run from a scope after startup with the `generation` check. Coil `maxSizeBytes` constant. | Legacy cleanup tied to the only upgrade path that can carry legacy rows; no new prefs file. Schema bump and migration test needed; existing legacy unit test is rewritten. | M |
| B. Prefs flag | As A, but keep the runtime cleanup gated by a persisted `SharedPreferences` flag; schema v4 only for the index (or no index and no schema change). | Smallest Room change; keeps a startup-path step and an extra prefs read on the first cache access; flag must be reset by `clear()`/fresh DB semantics. | M |
| C. Range delete | Replace `LIKE` with `key >= :p AND key < :pEnd` (PK index), keep the in-memory flag; sweep without an index. | No schema change; still six queries per process (cheap); sweep scans the table each run. | S |

Recommended: A.

## Risks

| Risk | Guard |
|---|---|
| Read-only main player: seeking back past the in-memory back buffer and replaying a recent episode refetch from the network | Owner accepted (Q2); AC-3 device check |
| Warm-up hits depend on segment URLs being identical between warm-up and main (default cache key is the URI); signed/expiring URLs would miss (unverified) | Device observation in AC-3: next-episode start reads cached spans |
| Lowering the evictor cap with an existing 512 MB cache: `LeastRecentlyUsedCacheEvictor` evicts down on the next write (unverified for startup) | Device `du` before/after |
| Sweep deleting a row while `RoomPersistentPayloadStore.write` or a read is in flight | Sweep goes through `PersistentPayloadStore` and only deletes rows not in the memory tier's hot set or by age; unit test with `FakePayloadStore` |
| Migration 3->4 failure falls into `fallbackToDestructiveMigration`, also dropping `watch_state`/`watch_state_sync` | Room migration test against `3.json`; device upgrade with install-preserve |
| Coil cap too low causes poster reloads while scrolling rows | Device memory probe (`core/telemetry/MemoryProbe.kt`) and scroll check |
| Merge with startup-splash-preload in `PuberApp.kt` | Land after it, or rebase; edit only the Coil block |

## Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Fold `content-cache-legacy-cleanup` in and close it; move the media-cache half of `player-cache-save-churn` here and the rest to `cache-targeted-invalidation`? | Yes to both. |
| 2 | Legacy cleanup: Room migration (A) or persisted prefs flag (B, the request's wording)? | Migration 3->4 with `onPostMigrate`; it is a persisted once-only step without a new prefs key. |
| 3 | `cached_payload` cap: rows or bytes, and the value? | Row cap (e.g. 2,000 rows, unverified fit) plus the 7-day age rule; a byte cap needs `SUM(length(payload))`, a full scan per sweep. Measure row count and DB size on the emulator first (AC-9). |
| 4 | When does "idle" run? | Once per process, a fixed delay after startup on a background dispatcher, plus on ON_STOP via `AppForegroundState`; switch to the startup-settled signal when that run lands. |
| 5 | Coil memory cap value? | 48 MB fixed (`maxSizeBytes`), below the audit's 58-77 MB estimate; confirm with a memory probe. |
| 6 | Order against startup-splash-preload? | Start now; rebase on it before review because both edit `PuberApp.kt`. |

## Sources

- Files: `AGENTS.md`; `gradle/libs.versions.toml:25,27,33`; `app/build.gradle.kts:240-242`; `app/schemas/com.kino.puber.data.db.PuberDatabase/3.json`; the code paths above.
- Runs: cache-layer-rework (research.md, brief.md), startup-splash-preload (state.md, perf-audit-2026-11-21.md, worktree spec and plan, branch diff), content-cache-legacy-cleanup, player-cache-save-churn, cache-decoded-memory-tier, cache-shared-http-stack, cache-targeted-invalidation, startup-settled-signal, perf-deferred, media3-hagc-removal, coil-shared-http (`state.md` each).
- Commits: 6be75d28, 9690b9e8, 264748e7, d8682b3c.
- URLs: https://developer.android.com/reference/kotlin/androidx/media3/datasource/cache/CacheDataSource.Factory, https://developer.android.com/media/media3/exoplayer/downloading-media, https://github.com/google/ExoPlayer/issues/8053, https://coil-kt.github.io/coil/image_loaders/, https://github.com/addict85/BrickInventory/pull/29

# Brief: cache-storage-bounds

Base a22c03bc. Analyst, 2026-11-21. Builds on `research.md`; the lines I rely on were re-read at base and hold: `data/di/modules.kt:77-78,110-117`, `PlaybackController.kt:416-417,593,626-646,659`, `CachedPayloadDao.kt:27-28`, `PuberDatabase.kt:11-17,38`, `PersistentPayloadStore.kt:62-68,98-107`, `CachedFeed.kt:263-266,559-564,591`, `ContentCacheRepository.kt:47-50,366-384,621-628`, `PuberApp.kt:58,116-118` (paths under `app/src/main/java/com/kino/puber/`).

kind: refactor. size: M. tracks: perf. Device verification: **yes** (playback, persistence and caches, Room migration, startup scheduling).

Size reason: one goal (bounded storage) across player, Room, cache repository and app wiring; touches the risk areas playback, persistence and caches, and startup.

## Request
research.md: Request. Scope also carries `content-cache-legacy-cleanup` (folded in) and the media-cache half of `player-cache-save-churn` (`state.md` Decisions).

## Current state
research.md: Current state in code. Facts added here:

| Fact | Where |
|---|---|
| `markStale` moves a row's `updated_at` to `now - ttl - 1`, not to 0, so an age sweep at `HardCeiling` keeps stale-marked rows readable | `data/cache/CachedFeed.kt:263-266` |
| The age rule the sweep repeats already exists, but only on read | `data/cache/CachedFeed.kt:559-564` |
| `CachedPayloadDao` already has an instrumented test; no `room3-testing` dependency, so a migration test needs one (version via `libs.versions.toml`) | `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt`, `app/build.gradle.kts:426-430` |

## Scope
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

## Risks
research.md: Risks (read-only resume loss, warm-up URL identity, evictor on cap drop, sweep vs write race, destructive fallback on a failed migration, Coil cap too low, `PuberApp.kt` overlap with `startup-splash-preload`). Added:

| Risk | Guard |
|---|---|
| The migration test needs `room3-testing` and runs only as an instrumented test, i.e. through `make DEVICE=<serial> itest` | AC-7; plan adds the dependency through the version catalog |
| `cache-decoded-memory-tier` (B1) bumps the namespace later; orphaned `content:v1:` rows are then removed only by the age step, 7 days after their last write | AC-4; B1 may add its own prefix delete to a later migration |

## Open questions
1. Legacy cleanup: Room migration 3->4 deleting the prefixes once, or a persisted prefs flag around the runtime cleanup? Recommended: migration (research.md: Options A); it is the only upgrade path that can carry legacy rows and needs no new prefs key. Blocks: yes (AC-7, AC-8).
2. `cached_payload` cap: rows or bytes, and the value? Recommended: row cap, 2,000 as the starting value, confirmed or changed from the AC-11 row count before review; a byte cap needs a full-table `SUM(length(payload))` per sweep. Blocks: no.
3. Coil memory cap value? Recommended: 48 MB, kept if the AC-11 Home scroll shows no visible poster reloads (owner judges), else raised. Blocks: no.

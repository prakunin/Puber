# Test design: cache-storage-bounds

Base a22c03bc. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>` only), `device`. Test names follow the neighbouring files (sentence-style camelCase), not the `method_expected_when` pattern of `docs/architecture/testing.md`.

## Risk profile

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

## Traceability

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

## Manual checks

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

## Bugs

N/A: refactor, no defect to reproduce.

## Perf track

Same emulator, same dev debug build type, baseline in `baseline.md` captured from a22c03bc before implementation.

| Metric | Method | Pass threshold |
|---|---|---|
| `media_cache` growth over the M1 session | `du -sb` before/after | 0 bytes (baseline: today's growth) |
| `media_cache` size after M3 | `du -sb` | <= 268435456 bytes |
| `cached_payload` row count after the fixed browsing session and one sweep (M7) | `SELECT COUNT(*)` | <= 2,000 and <= baseline |
| `puber.db` size after the same session | `du -b` of `puber.db` + `-wal` | <= baseline |
| Coil memory cap | M5 log | 50331648 bytes (baseline: `maxSizePercent` result) |
| App PSS after the M6 scroll | `core/telemetry/MemoryProbe.kt` output, 3 runs, median | <= baseline median |

## UNTESTABLE

None.

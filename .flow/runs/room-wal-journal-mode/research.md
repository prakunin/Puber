# Research: room-wal-journal-mode

## Request

PuberDatabase.kt (around 36-46) builds Room without an explicit journal mode. With AUTOMATIC, Room picks TRUNCATE (synchronous=FULL) on devices that report low RAM (ActivityManager.isLowRamDevice), giving an fsync per write and readers blocked by writers. Set JournalMode.WRITE_AHEAD_LOGGING explicitly. Whether the box reports low RAM is unknown (getprop ro.config.low_ram, not yet checked).

## Understanding

The app's only database, `puber.db` (watch-state index, sync marks, cached API payloads), is built without `setJournalMode`, so Room decides at build time: WAL normally, TRUNCATE on a device whose `ActivityManager.isLowRamDevice` is true. On TRUNCATE Room also sets `synchronous = FULL` and uses a single connection. The request is a one-line builder change pinning WAL on every device. On the owner's box it is probably already WAL (indirect evidence below), so the gain is for low-RAM TVs in the wild; on the box the change should be a no-op.

## Current state in code

- `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36-45`: `Room.databaseBuilder(...)` with `setDriver(AndroidSQLiteDriver())` (:40), `setQueryCoroutineContext(Dispatchers.IO)` (:41), `fallbackToDestructiveMigration(dropAllTables = true)` (:44); no `setJournalMode`, so `JournalMode.AUTOMATIC`.
- `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:11-24`: schema version 5, three entities, auto-migrations 2-3, 3-4, 4-5.
- `app/src/main/java/com/kino/puber/data/di/modules.kt:119-133`: one Koin `single` of the database, DAOs and `transactions()` built from it.
- `app/src/main/AndroidManifest.xml`: no `android:process` entries, so one process opens the file (no multi-instance invalidation concern).
- Versions: `gradle/libs.versions.toml:33-34` Room `3.0.3` (`androidx.room3`), sqlite `2.7.1` (`sqlite-framework`, `app/build.gradle.kts:421-423`).
- Tests: `app/src/androidTest/kotlin/com/kino/puber/data/db/CachedPayloadDaoTest.kt:28-31`, `PersistentPayloadStoreDbTest.kt:31-34`, `WatchStateDaoTest.kt:36-39` all use `Room.inMemoryDatabaseBuilder` (Room forces a single connection for in-memory, and the journal mode there is not WAL), so none exercises `PuberDatabase.create` or the journal mode. No test opens a named file database.
- Box evidence (indirect): `docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md:9,21` copies `puber.db`, `-wal`, `-shm` from the box. A persistent `-wal` is consistent with WAL being active there (Room's TRUNCATE path runs `PRAGMA journal_mode = TRUNCATE`, which removes the WAL file). The evidence does not say the `-wal` copy succeeded, so whether the box is low RAM stays `unverified`.

## Related work

| Item | Relation |
|---|---|
| `watch-state-batch-in-progress` (triage) | Same 2026-11-28 performance audit; batches watch_state writes into one transaction. Complementary: fewer commits vs cheaper commits. Not a duplicate. |
| `cache-layer-rework` (idea, L) | Umbrella for the data/cache layer; its research lists no journal-mode item. |
| `cache-storage-bounds` (done) | Measured `puber.db` + `-wal` size on the emulator (`docs/superpowers/specs/2026-11-21-cache-storage-bounds.md:251`). |
| `perf-deferred` (idea) | Deferred perf items P3, M4, P8-P12; no journal-mode item. |
| `telemetry-core` (done) | Its `session` event records `low_ram` (`docs/superpowers/specs/2026-10-15-telemetry-core.md:212`); a telemetry session from the box would answer the low-RAM question without adb. |
| Commits on `PuberDatabase.kt` | `183c10b1` (v5 migration), `d52b5800` (v4 migration, sweeper), `03c2ca31` (Room 3 driver). `git log -S JournalMode` finds no prior journal-mode change in app code. |

Possible duplicate: none.

## External findings

| Finding | Source |
|---|---|
| `JournalMode.AUTOMATIC.resolve` returns TRUNCATE when `ActivityManager.isLowRamDevice` is true, otherwise WAL; an explicit mode is returned as is. | room3 `RoomDatabase.android.kt` lines 471-497, androidx-main (fetched 2026-11-28; identical in 3.0.3 `unverified`) |
| With no explicit pool config, TRUNCATE gets `SingleConnection`, WAL gets `MultipleConnection(WAL_DEFAULT_NUMBER_OF_READERS, ...)`. | same file, `build()` lines 1303-1317 |
| On every opened connection Room runs `PRAGMA journal_mode = WAL` + `synchronous = NORMAL`, or `journal_mode = TRUNCATE` + `synchronous = FULL`; busy timeout 3000 ms. | room3 `RoomConnectionManager.kt` lines 185-251, androidx-main |
| `AndroidSQLiteDriver.hasConnectionPool` is `true`, so Room uses a `PassthroughConnectionPool` and the Room pool config is ignored; the driver opens with `SQLiteDatabase.openOrCreateDatabase(fileName, null)`, without `ENABLE_WRITE_AHEAD_LOGGING`. | sqlite-framework `AndroidSQLiteDriver.android.kt`; room3 `RoomConnectionManager.android.kt` lines 479-488, androidx-main |
| Framework: without WAL enabled through the open flag or `enableWriteAheadLogging()`, reads and writes cannot run at the same time; WAL also cuts fsync calls. | https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase (`enableWriteAheadLogging`) |
| Room 3.0.3 released 2026-09-09; notes mention a `@Transaction` regression fix, nothing on journal mode. | https://developer.android.com/jetpack/androidx/releases/room3 |

Consequence for this app (`unverified`, inferred from the sources above): with `AndroidSQLiteDriver` the framework pool is opened without the WAL flag, so a `PRAGMA journal_mode = WAL` set by Room probably does not give parallel reader connections inside the framework pool. The certain gain of explicit WAL on a low-RAM device is `synchronous = NORMAL` and no rollback-journal fsync per commit; "readers no longer blocked by writers" is not guaranteed by this change alone.

## Options

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Add `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` in `PuberDatabase.create` (`PuberDatabase.kt:37-45`) with a comment; add an instrumented test that builds `PuberDatabase.create(context, name = <temp>)` and reads `PRAGMA journal_mode` = `wal`. | One line plus a test. Same mode on every device; no schema or data change; WAL file can grow until checkpoint (bounded by SQLite auto-checkpoint). No effect on a non-low-RAM box, so device verification only shows no regression. | S |
| B | A, plus open the framework database with WAL so the framework pool gets reader connections (a custom `SQLiteDriver` wrapping `openDatabase(..., ENABLE_WRITE_AHEAD_LOGGING)`), or switch to `BundledSQLiteDriver` and Room's own pool. | Delivers real reader/writer concurrency; changes the SQLite engine or driver the code comment at `PuberDatabase.kt:38-39` deliberately kept, adds APK size (bundled), needs perf measurement on the box. | M |
| C | Measure first: read `getprop ro.config.low_ram` on the box and a telemetry `low_ram` value; change nothing if the box is not low RAM. | No code risk; leaves low-RAM devices on TRUNCATE + FULL. | S |

## Risks

- Device-only behaviour: the journal mode is resolved at runtime from `isLowRamDevice`; on the box (likely not low RAM) the change cannot show a difference, and no low-RAM test device is listed in AGENTS.md. A unit test cannot cover it (needs Android SQLite).
- An existing TRUNCATE database switches to WAL on next open; Room issues the PRAGMA on each connection, no migration needed. A failed switch (for example a read-only file system) would make SQLite keep the old mode silently `unverified`.
- Persistence change: AGENTS.md requires on-device verification (install-preserve on the box, app opens with data intact, logged in).

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Option A only, or also B (real reader concurrency through the driver)? | A only; B is a separate run if a box measurement shows read stalls behind writes. |
| Q2 | Check `ro.config.low_ram` on the box as part of this run? | Yes, one `adb -s 192.168.1.110:5555 shell getprop ro.config.low_ram` under the lease during verification, recorded in evidence; it informs the expected effect, it does not gate the change. |
| Q3 | Test level for the journal mode? | One instrumented test on a named temp database asserting `PRAGMA journal_mode` returns `wal`, run via `make DEVICE=<serial> itest TESTS=...`; plus install-preserve on the box with `PRAGMA journal_mode` read from a run-as copy of `puber.db`. |

## Sources

- Files: `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt`, `app/src/main/java/com/kino/puber/data/di/modules.kt`, `app/src/main/AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/androidTest/kotlin/com/kino/puber/data/db/*Test.kt`, `docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md`, `docs/superpowers/specs/2026-11-21-cache-storage-bounds.md`, `docs/superpowers/specs/2026-10-15-telemetry-core.md`, `AGENTS.md`, `.flow/roles/test-designer.md`.
- Commits: `183c10b1`, `d52b5800`, `03c2ca31`.
- Runs: `watch-state-batch-in-progress`, `cache-layer-rework`, `cache-storage-bounds`, `perf-deferred`, `telemetry-core`.
- URLs (fetched 2026-11-28):
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/room3/room3-runtime/src/androidMain/kotlin/androidx/room3/RoomDatabase.android.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/room3/room3-runtime/src/androidMain/kotlin/androidx/room3/RoomConnectionManager.android.kt
  - https://raw.githubusercontent.com/androidx/androidx/androidx-main/sqlite/sqlite-framework/src/androidMain/kotlin/androidx/sqlite/driver/AndroidSQLiteDriver.android.kt
  - https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase
  - https://developer.android.com/jetpack/androidx/releases/room3

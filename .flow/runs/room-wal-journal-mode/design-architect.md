# Design (architect): room-wal-journal-mode

Inputs: [brief.md](brief.md) (ACs, Q1-Q2 taken on the recommended answers), [research.md](research.md#external-findings). Room 3.0.3 API checked against `~/.gradle/caches/modules-2/files-2.1/androidx.room3/room3-runtime-android/3.0.3/.../room3-runtime.aar` with `javap`: `RoomDatabase$Builder.setJournalMode(JournalMode)`, `JournalMode.WRITE_AHEAD_LOGGING`, `RoomDatabaseKt.useWriterConnection`, `PooledConnection.usePrepared`, and the `PRAGMA journal_mode = WAL` / `PRAGMA synchronous = NORMAL` strings in `BaseRoomConnectionManager.configureJournalMode` / `configureSynchronousFlag`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| How is the mode pinned | `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` in `PuberDatabase.create`, after `.setQueryCoroutineContext(Dispatchers.IO)` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:41`), with a one-line comment that it overrides the low-RAM TRUNCATE + FULL fallback of `AUTOMATIC` | An explicit mode skips `JournalMode.resolve`; Room then issues WAL + NORMAL on each connection it configures. `RoomDatabase` is already imported (`PuberDatabase.kt:7`) |
| Driver and pool | Unchanged: `AndroidSQLiteDriver()` (`PuberDatabase.kt:40`) | Q1 = A; reader concurrency (option B) is a separate run |
| Schema, version, migrations | Unchanged: version 5 (`PuberDatabase.kt:13`), no exported schema change | Journal mode is a connection setting, not schema |
| Where the AC-1 test lives | New `@Test` in `PuberDatabaseMigrationTest` (`app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:27`), reusing `file`/`TEST_DB` (`:30`, `:169`) and `deleteFiles()` (`:164-166`, already removes `-wal`/`-shm`/`-journal`) | Only class that opens `PuberDatabase.create` on a named file (`:128`); the DAO tests use in-memory builders |
| What the test does | Fresh file (setUp already wipes it, `:42-44`); `PuberDatabase.create(context, name = TEST_DB)`; `database.useWriterConnection { it.usePrepared("PRAGMA journal_mode") { st -> st.step(); st.getText(0) } }` equals `"wal"` (case-insensitive), same for `PRAGMA synchronous` with `getLong(0)` == `1L`; `database.close()` in `finally` as at `:129-133`. Proposed name `productionBuilderOpensTheFileInWalWithNormalSync` (test-designer owns the final name) | The writer connection is the one Room configured; `synchronous` is per connection, so reading it elsewhere proves nothing |
| `ro.config.low_ram` | Read once on the box under the lease during verification (AC-4); no code | Q2 = yes, informs only |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36-46` (`create`): one builder call plus comment. Callers unchanged: Koin `single { PuberDatabase.create(androidContext()) }` (`app/src/main/java/com/kino/puber/data/di/modules.kt:119`) |
| Tests | `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt`: one new test (above); imports `androidx.room3.useWriterConnection`. Existing tests at `:52`, `:90`, `:111`, `:125` untouched (AC-2) |

ViewModel state and actions, screen and DI module, Compose content, resources: N/A: the change is confined to the database builder.

## Alternatives rejected

- Option B (WAL open flag through a custom driver, or `BundledSQLiteDriver`): changes the engine the comment at `PuberDatabase.kt:38-39` keeps; out of the brief.
- Option C (measure only): leaves low-RAM devices on TRUNCATE + FULL.
- Raw `PRAGMA journal_mode = WAL` in a `RoomDatabase.Callback.onOpen`: duplicates what `setJournalMode` already makes Room do, and Room's own `synchronous = FULL` for TRUNCATE would still be resolved.
- A separate test class: would duplicate the `MigrationTestHelper` file setup of `PuberDatabaseMigrationTest.kt:29-49`.

## Risks

| Risk | Where | Mitigation |
|---|---|---|
| Correctness: the framework `SQLiteDatabase` behind `AndroidSQLiteDriver` sets its own journal mode when it opens a connection (Android 9+ compatibility WAL, or the platform default), and a framework connection reopened after Room configured the first one may not carry Room's PRAGMAs (`unverified`) | `AndroidSQLiteDriver` open path; `PuberDatabase.kt:40` | AC-1 reads the live values on Room's writer connection; AC-4 reads the file header on the box. A mismatch is a finding, not a reason to add a driver here |
| Data shape / persistence: an existing TRUNCATE `puber.db` (low-RAM device) switches to WAL on first open; a failed switch keeps the old mode silently | `BaseRoomConnectionManager.configureJournalMode` (room3-runtime 3.0.3) | No migration needed; AC-4 confirms on the box; AC-2 confirms the v3 file path at `PuberDatabaseMigrationTest.kt:125` |
| Lifecycle: low-RAM devices now keep `puber.db-wal` and `-shm` beside the file; WAL grows until SQLite's auto-checkpoint | app data dir; no code reads the db file size (`rg getDatabasePath app/src/main` finds none) | Bounded by auto-checkpoint (1000 pages); no action |
| Test validity: AC-1 passes before the change on a non-low-RAM device | brief.md#risks, row 1 | Accepted: the test pins the builder, it does not reproduce the low-RAM path |
| Rollout: an older build without the call, installed later on a low-RAM device, switches the file back to TRUNCATE | `JournalMode.resolve` in older builds | Harmless; both modes open the same file |
| Baseline profile | `app/src/main/generated/baselineProfiles/startup-prof.txt` | One extra builder call on startup; no profile regeneration |
| Concurrency, security, performance | N/A: pool, threads (`PuberDatabase.kt:41`) and data are unchanged; the expected gain is the brief's row 2 | none |

## Open questions

None.

# Set the Room database journal mode to WAL explicitly

Status: approved
Date: 2026-11-28
Slug: room-wal-journal-mode
Kind: improvement
Size: M
Tracks: none

## Problem / Why

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (persistence: how `puber.db` is opened on every launch).
Research: `research.md` (code state, Room 3 / driver sources, options A-C; not repeated here).

### Sizing
One goal and one production line (option A), but it changes how the only database is opened, a persistence risk area in `.flow/roles/analyst.md`, so M. Track `perf` is not set: no measured number is the goal, and the box most likely already runs WAL (research.md: Current state in code, box evidence).


### Current behaviour
`PuberDatabase.create` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36-46`) calls no `setJournalMode`, so Room resolves `AUTOMATIC`: WAL + `synchronous = NORMAL`, or TRUNCATE + `synchronous = FULL` when `ActivityManager.isLowRamDevice` is true (research.md: External findings). Status: `confirmed` from the Room source; whether any device the app runs on is low RAM is `unverified`.


### Recommendation
Option A (research.md: Options): add `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` to the builder in `PuberDatabase.kt:37-45` with a one-line comment naming the low-RAM fallback it overrides. Driver, pool and schema stay unchanged.

Correction to research.md: `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:125-134` already opens `PuberDatabase.create(context, name = TEST_DB)` on a named file and deletes `-wal`/`-shm`/`-journal` (`:183-185`); the new test belongs there or follows its setup.


### Risks
| Risk | Note |
|---|---|
| AC-1 cannot fail before the change on a non-low-RAM device | AUTOMATIC already gives WAL there; the test pins the mode against a future builder change, it does not reproduce the low-RAM path. No low-RAM device is available (AGENTS.md: test devices) |
| Expected gain smaller than the request states | With `AndroidSQLiteDriver` the framework pool is opened without the WAL flag, so reader/writer concurrency is not guaranteed; the certain gain on low-RAM devices is `synchronous = NORMAL` and no per-commit journal fsync (research.md: External findings, `unverified`) |
| Existing TRUNCATE file switches on first open | Room issues the PRAGMA per connection; no migration. A failed switch keeps the old mode silently; AC-4 checks the box |
| Box session loss after a worktree install | Refresh the session with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

## Acceptance criteria

- **AC-1** Given a named file database built by `PuberDatabase.create(context, name = <test db>)`, When a connection runs `PRAGMA journal_mode` and `PRAGMA synchronous`, Then they return `wal` and `1` (NORMAL). (instrumented, `PuberDatabaseMigrationTest` or a sibling in `app/src/androidTest/kotlin/com/kino/puber/data/db/`, run through `make DEVICE=192.168.1.110:5555 itest TESTS=<class>` under a lease)
- **AC-2** Given the change, When `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` (`PuberDatabaseMigrationTest.kt:125`) and the other `data.db` instrumented tests run, Then they pass unchanged.
- **AC-3** Given the worktree dev APK installed with install-preserve on the box over existing data, When the app is launched and Home, a card and the watch badges are opened at normal pace, Then there is no crash, the session stays logged in and the badges match the state before the install. (device observation; no scripted API calls)
- **AC-4** Given the box after AC-3, When `puber.db` of `com.kino.puber.stage` is read through `run-as` (header bytes 18-19, or `PRAGMA journal_mode` on a copy) and `getprop ro.config.low_ram` is read, Then the header reports WAL (`2`,`2`) / `wal`, and both values are recorded in the evidence file. (device observation)
- **AC-5** Given the change, When `check.full` runs in the worktree, Then it passes.

## Out of scope

The brief has no separate section; the non-goals are listed under Problem / Why and in the plan's Global constraints.

## Design

Inputs: [brief.md](brief.md) (ACs, Q1-Q2 taken on the recommended answers), [research.md](research.md#external-findings). Room 3.0.3 API checked against `~/.gradle/caches/modules-2/files-2.1/androidx.room3/room3-runtime-android/3.0.3/.../room3-runtime.aar` with `javap`: `RoomDatabase$Builder.setJournalMode(JournalMode)`, `JournalMode.WRITE_AHEAD_LOGGING`, `RoomDatabaseKt.useWriterConnection`, `PooledConnection.usePrepared`, and the `PRAGMA journal_mode = WAL` / `PRAGMA synchronous = NORMAL` strings in `BaseRoomConnectionManager.configureJournalMode` / `configureSynchronousFlag`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| How is the mode pinned | `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` in `PuberDatabase.create`, after `.setQueryCoroutineContext(Dispatchers.IO)` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:41`), with a one-line comment that it overrides the low-RAM TRUNCATE + FULL fallback of `AUTOMATIC` | An explicit mode skips `JournalMode.resolve`; Room then issues WAL + NORMAL on each connection it configures. `RoomDatabase` is already imported (`PuberDatabase.kt:7`) |
| Driver and pool | Unchanged: `AndroidSQLiteDriver()` (`PuberDatabase.kt:40`) | Q1 = A; reader concurrency (option B) is a separate run |
| Schema, version, migrations | Unchanged: version 5 (`PuberDatabase.kt:13`), no exported schema change | Journal mode is a connection setting, not schema |
| Where the AC-1 test lives | New `@Test` in `PuberDatabaseMigrationTest` (`app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:27`), reusing `file`/`TEST_DB` (`:30`, `:169`) and `deleteFiles()` (`:164-166`, already removes `-wal`/`-shm`/`-journal`) | Only class that opens `PuberDatabase.create` on a named file (`:128`); the DAO tests use in-memory builders |
| What the test does | Fresh file (setUp already wipes it, `:42-44`); `PuberDatabase.create(context, name = TEST_DB)`; `database.useWriterConnection { it.usePrepared("PRAGMA journal_mode") { st -> st.step(); st.getText(0) } }` equals `"wal"` (case-insensitive), same for `PRAGMA synchronous` with `getLong(0)` == `1L`; `database.close()` in `finally` as at `:129-133`. Proposed name `productionBuilderOpensTheFileInWalWithNormalSync` (test-designer owns the final name) | The writer connection is the one Room configured; `synchronous` is per connection, so reading it elsewhere proves nothing |
| `ro.config.low_ram` | Read once on the box under the lease during verification (AC-4); no code | Q2 = yes, informs only |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36-46` (`create`): one builder call plus comment. Callers unchanged: Koin `single { PuberDatabase.create(androidContext()) }` (`app/src/main/java/com/kino/puber/data/di/modules.kt:119`) |
| Tests | `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt`: one new test (above); imports `androidx.room3.useWriterConnection`. Existing tests at `:52`, `:90`, `:111`, `:125` untouched (AC-2) |

ViewModel state and actions, screen and DI module, Compose content, resources: N/A: the change is confined to the database builder.

### Alternatives rejected

- Option B (WAL open flag through a custom driver, or `BundledSQLiteDriver`): changes the engine the comment at `PuberDatabase.kt:38-39` keeps; out of the brief.
- Option C (measure only): leaves low-RAM devices on TRUNCATE + FULL.
- Raw `PRAGMA journal_mode = WAL` in a `RoomDatabase.Callback.onOpen`: duplicates what `setJournalMode` already makes Room do, and Room's own `synchronous = FULL` for TRUNCATE would still be resolved.
- A separate test class: would duplicate the `MigrationTestHelper` file setup of `PuberDatabaseMigrationTest.kt:29-49`.

### Risks

| Risk | Where | Mitigation |
|---|---|---|
| Correctness: the framework `SQLiteDatabase` behind `AndroidSQLiteDriver` sets its own journal mode when it opens a connection (Android 9+ compatibility WAL, or the platform default), and a framework connection reopened after Room configured the first one may not carry Room's PRAGMAs (`unverified`) | `AndroidSQLiteDriver` open path; `PuberDatabase.kt:40` | AC-1 reads the live values on Room's writer connection; AC-4 reads the file header on the box. A mismatch is a finding, not a reason to add a driver here |
| Data shape / persistence: an existing TRUNCATE `puber.db` (low-RAM device) switches to WAL on first open; a failed switch keeps the old mode silently | `BaseRoomConnectionManager.configureJournalMode` (room3-runtime 3.0.3) | No migration needed; AC-4 confirms on the box; AC-2 confirms the v3 file path at `PuberDatabaseMigrationTest.kt:125` |
| Lifecycle: low-RAM devices now keep `puber.db-wal` and `-shm` beside the file; WAL grows until SQLite's auto-checkpoint | app data dir; no code reads the db file size (`rg getDatabasePath app/src/main` finds none) | Bounded by auto-checkpoint (1000 pages); no action |
| Test validity: AC-1 passes before the change on a non-low-RAM device | brief.md#risks, row 1 | Accepted: the test pins the builder, it does not reproduce the low-RAM path |
| Rollout: an older build without the call, installed later on a low-RAM device, switches the file back to TRUNCATE | `JournalMode.resolve` in older builds | Harmless; both modes open the same file |
| Baseline profile | `app/src/main/generated/baselineProfiles/startup-prof.txt` | One extra builder call on startup; no profile regeneration |
| Concurrency, security, performance | N/A: pool, threads (`PuberDatabase.kt:41`) and data are unchanged; the expected gain is the brief's row 2 | none |

### Open questions

None.

## Test design

Inputs: [brief.md](brief.md#acceptance-criteria), [design-architect.md](design-architect.md#decisions). Base `8c3117ea`. Tests extend `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt` and reuse its `file`, `TEST_DB`, `helper` and `deleteFiles()` (setUp/tearDown already remove `-wal`/`-shm`/`-journal`).

Instrumented command (under a box lease): `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest,com.kino.puber.data.db.CachedPayloadDaoTest,com.kino.puber.data.db.PersistentPayloadStoreDbTest,com.kino.puber.data.db.WatchStateDaoTest` (the runner's `class` argument takes a comma list).

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Box session lost after the worktree install (memory: worktree-build-loses-box-session) | 2 | 3 | 6 | Manual step 1 (master build + `auth-save` first), AC-3 |
| Framework connection behind `AndroidSQLiteDriver` does not carry Room's PRAGMAs (design risk row 1) | 2 | 2 | 4 | `productionBuilderOpensTheFileInWalWithNormalSync` on the writer connection; AC-4 header on the box |
| AC-1 cannot fail on the base commit on a non-low-RAM device | 3 | 1 | 3 | Accepted: the low-RAM resolve path needs a low-RAM device and `isLowRamDevice` is not injectable; the tests are `guard`, the builder line is checked in review |
| Production builder drops data on open (destructive fallback) | 1 | 3 | 3 | `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` (AC-2) |
| Existing rollback-journal file keeps its mode silently | 1 | 2 | 2 | `productionBuilderSwitchesARollbackJournalFileToWal` (P1) |
| Gain smaller than requested (no reader concurrency with `AndroidSQLiteDriver`) | 2 | 1 | 2 | Accepted: not a correctness defect; option B is a separate run |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 guard | `productionBuilderOpensTheFileInWalWithNormalSync` | On a fresh `TEST_DB` built by `PuberDatabase.create`, `useWriterConnection` reads `PRAGMA journal_mode` = `wal` (case-insensitive) and `PRAGMA synchronous` = `1L` on the same connection; fails if the builder resolves TRUNCATE/FULL or sets another mode, or if the call is moved off the production builder |
| AC-1 | instrumented | P1 guard | `productionBuilderSwitchesARollbackJournalFileToWal` | `helper.createDatabase(5)` file switched with `PRAGMA journal_mode = TRUNCATE` and closed; after `PuberDatabase.create` the writer connection reads `wal`, and after `close()` header bytes 18-19 of `file` are `2`,`2`; fails if an existing rollback file is not converted |
| AC-2 | instrumented | P0 guard | `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`, `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`, `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk`, `migrate4To5LeavesAFinishedWalkAlone`, `CachedPayloadDaoTest`, `PersistentPayloadStoreDbTest`, `WatchStateDaoTest` | Unchanged and green; fails if WAL on the production builder trips the destructive fallback or breaks the v3 file path |
| AC-3 | device | P0 | Manual check 1 | No crash, session kept, badges unchanged after install-preserve over existing data |
| AC-4 | device | P0 | Manual check 2 | `puber.db` header is WAL on the box; `ro.config.low_ram` recorded |
| AC-5 | command | P0 | `check.full` in the worktree | Detekt, lint and unit suites stay green with the new test imports (`androidx.room3.useWriterConnection`) |

### Failing-first test

N/A: improvement, not a bug; on the box (not low RAM, per research.md#current-state-in-code) `AUTOMATIC` already resolves WAL, so no instrumented test can fail on `8c3117ea`.

### Manual checks

Device `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease from `tools/emulator-resource-lock.sh`; every adb call uses `-s 192.168.1.110:5555`.

1. **AC-3.**
   1. If the stage app shows pairing or the token is stale, refresh with a master build and run `make DEVICE=192.168.1.110:5555 auth-save` first.
   2. Open Home and note the watch badges of two cards (one partly watched, one fully watched).
   3. Install the worktree dev APK with `tools/android-apk-install-preserve`; launch.
   4. D-pad to Home, `KEYCODE_DPAD_CENTER` on one of the noted cards, `KEYCODE_BACK`; at human pace, no scripted API calls.
   - Expected: Home loads logged in; both badges match step 2.
   - Must not happen: crash or ANR, pairing screen, badges reset (destructive fallback), focus lost on return.
2. **AC-4.**
   1. `adb -s 192.168.1.110:5555 shell run-as com.kino.puber.stage od -An -tu1 -j18 -N2 databases/puber.db`.
   2. `adb -s 192.168.1.110:5555 shell getprop ro.config.low_ram`.
   - Expected: `2 2`; the low_ram value (empty, `false` or `true`) and the header bytes are written to the evidence file.
   - Must not happen: `1 1` (rollback journal); copying the database or raw rows into the evidence file.

### Perf track

N/A: track `perf` is off (brief.md#sizing).

### UNTESTABLE

None.

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Option A only, or also real reader concurrency through the driver (option B)? | A only; B is a separate run if a box measurement shows reads stalled behind writes | no |
| Q2 | Read `ro.config.low_ram` on the box in this run? | Yes, once under the lease during verification (AC-4); it informs, it does not gate | no |

All questions answered with the recommended answer (owner standing instruction: recommended).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Gate 1: approved on the recommended answers | owner (standing instruction: recommended) |

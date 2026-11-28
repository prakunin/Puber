# Brief: room-wal-journal-mode

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (persistence: how `puber.db` is opened on every launch).
Research: `research.md` (code state, Room 3 / driver sources, options A-C; not repeated here).

## Sizing
One goal and one production line (option A), but it changes how the only database is opened, a persistence risk area in `.flow/roles/analyst.md`, so M. Track `perf` is not set: no measured number is the goal, and the box most likely already runs WAL (research.md: Current state in code, box evidence).

## Current behaviour
`PuberDatabase.create` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36-46`) calls no `setJournalMode`, so Room resolves `AUTOMATIC`: WAL + `synchronous = NORMAL`, or TRUNCATE + `synchronous = FULL` when `ActivityManager.isLowRamDevice` is true (research.md: External findings). Status: `confirmed` from the Room source; whether any device the app runs on is low RAM is `unverified`.

## Recommendation
Option A (research.md: Options): add `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` to the builder in `PuberDatabase.kt:37-45` with a one-line comment naming the low-RAM fallback it overrides. Driver, pool and schema stay unchanged.

Correction to research.md: `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:125-134` already opens `PuberDatabase.create(context, name = TEST_DB)` on a named file and deletes `-wal`/`-shm`/`-journal` (`:183-185`); the new test belongs there or follows its setup.

## Acceptance criteria
- **AC-1** Given a named file database built by `PuberDatabase.create(context, name = <test db>)`, When a connection runs `PRAGMA journal_mode` and `PRAGMA synchronous`, Then they return `wal` and `1` (NORMAL). (instrumented, `PuberDatabaseMigrationTest` or a sibling in `app/src/androidTest/kotlin/com/kino/puber/data/db/`, run through `make DEVICE=192.168.1.110:5555 itest TESTS=<class>` under a lease)
- **AC-2** Given the change, When `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` (`PuberDatabaseMigrationTest.kt:125`) and the other `data.db` instrumented tests run, Then they pass unchanged.
- **AC-3** Given the worktree dev APK installed with install-preserve on the box over existing data, When the app is launched and Home, a card and the watch badges are opened at normal pace, Then there is no crash, the session stays logged in and the badges match the state before the install. (device observation; no scripted API calls)
- **AC-4** Given the box after AC-3, When `puber.db` of `com.kino.puber.stage` is read through `run-as` (header bytes 18-19, or `PRAGMA journal_mode` on a copy) and `getprop ro.config.low_ram` is read, Then the header reports WAL (`2`,`2`) / `wal`, and both values are recorded in the evidence file. (device observation)
- **AC-5** Given the change, When `check.full` runs in the worktree, Then it passes.

## Risks
| Risk | Note |
|---|---|
| AC-1 cannot fail before the change on a non-low-RAM device | AUTOMATIC already gives WAL there; the test pins the mode against a future builder change, it does not reproduce the low-RAM path. No low-RAM device is available (AGENTS.md: test devices) |
| Expected gain smaller than the request states | With `AndroidSQLiteDriver` the framework pool is opened without the WAL flag, so reader/writer concurrency is not guaranteed; the certain gain on low-RAM devices is `synchronous = NORMAL` and no per-commit journal fsync (research.md: External findings, `unverified`) |
| Existing TRUNCATE file switches on first open | Room issues the PRAGMA per connection; no migration. A failed switch keeps the old mode silently; AC-4 checks the box |
| Box session loss after a worktree install | Refresh the session with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Option A only, or also real reader concurrency through the driver (option B)? | A only; B is a separate run if a box measurement shows reads stalled behind writes | no |
| Q2 | Read `ro.config.low_ram` on the box in this run? | Yes, once under the lease during verification (AC-4); it informs, it does not gate | no |

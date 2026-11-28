# Test design: room-wal-journal-mode

Inputs: [brief.md](brief.md#acceptance-criteria), [design-architect.md](design-architect.md#decisions). Base `8c3117ea`. Tests extend `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt` and reuse its `file`, `TEST_DB`, `helper` and `deleteFiles()` (setUp/tearDown already remove `-wal`/`-shm`/`-journal`).

Instrumented command (under a box lease): `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest,com.kino.puber.data.db.CachedPayloadDaoTest,com.kino.puber.data.db.PersistentPayloadStoreDbTest,com.kino.puber.data.db.WatchStateDaoTest` (the runner's `class` argument takes a comma list).

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Box session lost after the worktree install (memory: worktree-build-loses-box-session) | 2 | 3 | 6 | Manual step 1 (master build + `auth-save` first), AC-3 |
| Framework connection behind `AndroidSQLiteDriver` does not carry Room's PRAGMAs (design risk row 1) | 2 | 2 | 4 | `productionBuilderOpensTheFileInWalWithNormalSync` on the writer connection; AC-4 header on the box |
| AC-1 cannot fail on the base commit on a non-low-RAM device | 3 | 1 | 3 | Accepted: the low-RAM resolve path needs a low-RAM device and `isLowRamDevice` is not injectable; the tests are `guard`, the builder line is checked in review |
| Production builder drops data on open (destructive fallback) | 1 | 3 | 3 | `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` (AC-2) |
| Existing rollback-journal file keeps its mode silently | 1 | 2 | 2 | `productionBuilderSwitchesARollbackJournalFileToWal` (P1) |
| Gain smaller than requested (no reader concurrency with `AndroidSQLiteDriver`) | 2 | 1 | 2 | Accepted: not a correctness defect; option B is a separate run |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 guard | `productionBuilderOpensTheFileInWalWithNormalSync` | On a fresh `TEST_DB` built by `PuberDatabase.create`, `useWriterConnection` reads `PRAGMA journal_mode` = `wal` (case-insensitive) and `PRAGMA synchronous` = `1L` on the same connection; fails if the builder resolves TRUNCATE/FULL or sets another mode, or if the call is moved off the production builder |
| AC-1 | instrumented | P1 guard | `productionBuilderSwitchesARollbackJournalFileToWal` | `helper.createDatabase(5)` file switched with `PRAGMA journal_mode = TRUNCATE` and closed; after `PuberDatabase.create` the writer connection reads `wal`, and after `close()` header bytes 18-19 of `file` are `2`,`2`; fails if an existing rollback file is not converted |
| AC-2 | instrumented | P0 guard | `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`, `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`, `migrate4To5RescalesTheResumePageOfAnUnfinishedWalk`, `migrate4To5LeavesAFinishedWalkAlone`, `CachedPayloadDaoTest`, `PersistentPayloadStoreDbTest`, `WatchStateDaoTest` | Unchanged and green; fails if WAL on the production builder trips the destructive fallback or breaks the v3 file path |
| AC-3 | device | P0 | Manual check 1 | No crash, session kept, badges unchanged after install-preserve over existing data |
| AC-4 | device | P0 | Manual check 2 | `puber.db` header is WAL on the box; `ro.config.low_ram` recorded |
| AC-5 | command | P0 | `check.full` in the worktree | Detekt, lint and unit suites stay green with the new test imports (`androidx.room3.useWriterConnection`) |

## Failing-first test

N/A: improvement, not a bug; on the box (not low RAM, per research.md#current-state-in-code) `AUTOMATIC` already resolves WAL, so no instrumented test can fail on `8c3117ea`.

## Manual checks

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

## Perf track

N/A: track `perf` is off (brief.md#sizing).

## UNTESTABLE

None.

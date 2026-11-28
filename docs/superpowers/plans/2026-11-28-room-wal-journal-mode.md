# Set the Room database journal mode to WAL explicitly - plan

**Spec:** `docs/superpowers/specs/2026-11-28-room-wal-journal-mode.md`
**Goal:** `PuberDatabase.create` opens `puber.db` in WAL with `synchronous = NORMAL` on every device, including low-RAM ones, pinned by two instrumented guards.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh && bash tools/tests/test-itest-tooling.sh`.
- androidTest compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`. Instrumented tests run only through qa-device under a box lease: `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.PuberDatabaseMigrationTest,com.kino.puber.data.db.CachedPayloadDaoTest,com.kino.puber.data.db.PersistentPayloadStoreDbTest,com.kino.puber.data.db.WatchStateDaoTest`. Never `connected*AndroidTest`, never `./gradlew` in the worktree.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- Out of scope: the driver (`AndroidSQLiteDriver`), pool, schema, version, migrations (spec Q1 = option A only). Existing tests in `PuberDatabaseMigrationTest.kt` stay unchanged (AC-2).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

---

## Tasks

### Task 1: Add the WAL guards to PuberDatabaseMigrationTest

**Covers:** AC-1 (guards), AC-2 (existing tests untouched)
**Files:**
- Test: `app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt` - two new `@Test`s after `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` (`:125-134`); reuse `context` (`:29`), `file` (`:30`), `helper` (`:33-39`), `TEST_DB` (`:169`); setUp/tearDown `deleteFiles()` (`:41-49`, `:164-166`) already remove the file and `-wal`/`-shm`/`-journal`. Add import `androidx.room3.useWriterConnection`.

**Consumes:** `PuberDatabase.create(context, name)` (`app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:36`); Room 3.0.3 (`gradle/libs.versions.toml:33`) `suspend RoomDatabase.useWriterConnection { transactor -> }` and the member `PooledConnection.usePrepared(sql) { st -> }`; `androidx.sqlite.execSQL` (already imported, `:7`).
**Produces:** none.

| Test | Body |
|---|---|
| `productionBuilderOpensTheFileInWalWithNormalSync` (P0) | `runTest`; `val database = PuberDatabase.create(context, name = TEST_DB)`; in `try`: `database.useWriterConnection { c -> c.usePrepared("PRAGMA journal_mode") { st -> st.step(); st.getText(0) } to c.usePrepared("PRAGMA synchronous") { st -> st.step(); st.getLong(0) } }`; assert mode `.lowercase()` == `"wal"` and sync == `1L`; `finally { database.close() }` as at `:129-133` |
| `productionBuilderSwitchesARollbackJournalFileToWal` (P1) | `runTest`; `helper.createDatabase(5).use { it.execSQL("PRAGMA journal_mode = TRUNCATE") }`; open `PuberDatabase.create(context, name = TEST_DB)`, read `PRAGMA journal_mode` on the writer connection as above, assert `"wal"`, `close()` in `finally`; then `file.inputStream().use { it.skip(18); it.read() to it.read() }` (or `RandomAccessFile`) == `2 to 2` |

- [x] Write both tests; leave `:51-134` byte-identical
- [x] Run the fast gate and `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit (e.g. "Pin the WAL journal mode of the production database builder with instrumented guards")

**Done when:** both commands pass; `git diff HEAD~1 -- app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt` shows only additions. The tests pass on the base on the box (not low RAM; test-design.md Failing-first N/A); record that, not a red run.

---

### Task 2: Set WRITE_AHEAD_LOGGING on the production builder

**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5
**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt` - in `create` (`:36-46`), after `.setQueryCoroutineContext(Dispatchers.IO)` (`:41`), add a one-line comment (explicit WAL overrides the TRUNCATE + `synchronous = FULL` that `AUTOMATIC` picks on low-RAM devices) and `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)`. `RoomDatabase` is already imported (`:7`); no new import. Caller unchanged: `single { PuberDatabase.create(androidContext()) }` (`app/src/main/java/com/kino/puber/data/di/modules.kt:119`).

**Consumes:** `RoomDatabase.Builder.setJournalMode(JournalMode)`, `RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING` (room3-runtime 3.0.3).
**Produces:** none.

- [x] Add the call and comment; nothing else in the file changes
- [x] Run the fast gate, the androidTest compile and the full gate; commit (e.g. "Open the Room database in WAL explicitly so low-RAM devices skip the rollback-journal fallback")
- [ ] Hand to qa-device: the itest command in Global constraints (AC-1, AC-2); test-design.md Manual checks 1 (AC-3) and 2 (AC-4) on `192.168.1.110:5555`, package `com.kino.puber.stage` only, session refreshed with a master build and `make DEVICE=192.168.1.110:5555 auth-save` first if stale

**Done when:** full gate passes (AC-5); `git diff HEAD~1 --stat` lists only `PuberDatabase.kt` with two added lines; qa-device reports the itest command green, AC-3 observed, and the evidence file records header bytes `2 2` and the `ro.config.low_ram` value.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- 4dccca49 Open the Room database in WAL explicitly so low-RAM devices skip the rollback-journal fallback
- b4d504c1 Pin the WAL journal mode of the production database builder with instrumented guards

**Red run** (P0 `red` tests on the unchanged code):

n/a: both new tests are `guard` rows. They were not run on base: no available device is low-RAM (`ro.config.low_ram` empty, header already `2 2` before install), so they cannot tell base from head. The `setJournalMode` line in `PuberDatabase.kt` is guarded by review; a config-reading test is not added (spec Test design accepts the risk; `isLowRamDevice` cannot be injected).

**Deviations from the plan:**

None. Magic numbers in the new test are named constants in the companion (detekt).

**Files touched outside the plan:**

**For the reviewers:**

The comment line above `setJournalMode` is the only prose added to production; `check.full` green.

**Blocked:**

## Readiness

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-28-room-wal-journal-mode.md`, plan `docs/superpowers/plans/2026-11-28-room-wal-journal-mode.md` (untracked in the worktree), test design `.flow/runs/room-wal-journal-mode/test-design.md`. Base `8c3117ea`; worktree HEAD `cb853ce2`.

### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 (guards), Task 2 (builder call) | `productionBuilderOpensTheFileInWalWithNormalSync` P0 guard; `productionBuilderSwitchesARollbackJournalFileToWal` P1 guard |
| AC-2 | Task 1 (existing tests byte-identical), Task 2 (itest hand-off) | P0 guard: existing `PuberDatabaseMigrationTest` methods + three DAO classes |
| AC-3 | Task 2 (qa-device, Manual check 1) | Manual check 1 |
| AC-4 | Task 2 (qa-device, Manual check 2) | Manual check 2 |
| AC-5 | Task 2 (full gate) | `check.full` |

### Citations sampled

| Citation | Resolves to |
|---|---|
| `PuberDatabase.kt:36-46` | `fun create(context, name = NAME)` builder through `.build()` |
| `PuberDatabase.kt:41` | `.setQueryCoroutineContext(Dispatchers.IO)` |
| `PuberDatabase.kt:7` | `import androidx.room3.RoomDatabase` |
| `PuberDatabase.kt:13` | `version = 5` |
| `modules.kt:119` | `single { PuberDatabase.create(androidContext()) }` |
| `PuberDatabaseMigrationTest.kt:125-134` | `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState` |
| `PuberDatabaseMigrationTest.kt:29`, `:30`, `:33-39`, `:164-166`, `:169` | `context`, `file`, `helper`, `deleteFiles()` (deletes `-wal`/`-shm`/`-journal`), `TEST_DB` |
| `gradle/libs.versions.toml:33` | `room = "3.0.3"` |
| Room 3.0.3 API (javap on `room3-runtime.aar`) | `RoomDatabaseKt.useWriterConnection(RoomDatabase, (Transactor) -> R)`; `Transactor extends PooledConnection`; `PooledConnection.usePrepared(String, ...)`; `RoomDatabase$JournalMode.WRITE_AHEAD_LOGGING` |

### Checks

| Check | Result |
|---|---|
| Build green after each task | Task 1 adds tests against APIs that exist on base; Task 2 adds one builder call with an existing import |
| Dependency order | Task 1 (guards) before Task 2 (production line); no task consumes a later one |
| P0 rows name defect, mark, test name | AC-1 and AC-2 rows: `guard`, backticked names, defect named (builder resolves TRUNCATE/FULL or another mode; destructive fallback). AC-3/4/5 rows are device/command checks |
| Out of scope | Plan Global constraints exclude driver, pool, schema, version, migrations; tasks touch only `PuberDatabase.kt` and `PuberDatabaseMigrationTest.kt` |
| Blocking open questions | None; Q1, Q2 answered (non-blocking) |

### Notes (non-blocking)

1. The AC-1 P0 guard does not catch removal of the `setJournalMode` line on the box (not low RAM: `AUTOMATIC` already gives WAL); accepted in the spec Risks and test-design Risk profile, the line is checked in review.
2. Spec `## Out of scope` points to "Problem / Why (Scope)", which has no Scope subsection; the scope lives in Recommendation and Design Decisions.
3. Spec Problem / Why cites `PuberDatabaseMigrationTest.kt:183-185` for the file deletion; the code has it at `:164-166` (Design cites it correctly).

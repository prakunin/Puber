# Readiness 1: room-wal-journal-mode

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-28-room-wal-journal-mode.md`, plan `docs/superpowers/plans/2026-11-28-room-wal-journal-mode.md` (untracked in the worktree), test design `.flow/runs/room-wal-journal-mode/test-design.md`. Base `8c3117ea`; worktree HEAD `cb853ce2`.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 (guards), Task 2 (builder call) | `productionBuilderOpensTheFileInWalWithNormalSync` P0 guard; `productionBuilderSwitchesARollbackJournalFileToWal` P1 guard |
| AC-2 | Task 1 (existing tests byte-identical), Task 2 (itest hand-off) | P0 guard: existing `PuberDatabaseMigrationTest` methods + three DAO classes |
| AC-3 | Task 2 (qa-device, Manual check 1) | Manual check 1 |
| AC-4 | Task 2 (qa-device, Manual check 2) | Manual check 2 |
| AC-5 | Task 2 (full gate) | `check.full` |

## Citations sampled

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

## Checks

| Check | Result |
|---|---|
| Build green after each task | Task 1 adds tests against APIs that exist on base; Task 2 adds one builder call with an existing import |
| Dependency order | Task 1 (guards) before Task 2 (production line); no task consumes a later one |
| P0 rows name defect, mark, test name | AC-1 and AC-2 rows: `guard`, backticked names, defect named (builder resolves TRUNCATE/FULL or another mode; destructive fallback). AC-3/4/5 rows are device/command checks |
| Out of scope | Plan Global constraints exclude driver, pool, schema, version, migrations; tasks touch only `PuberDatabase.kt` and `PuberDatabaseMigrationTest.kt` |
| Blocking open questions | None; Q1, Q2 answered (non-blocking) |

## Notes (non-blocking)

1. The AC-1 P0 guard does not catch removal of the `setJournalMode` line on the box (not low RAM: `AUTOMATIC` already gives WAL); accepted in the spec Risks and test-design Risk profile, the line is checked in review.
2. Spec `## Out of scope` points to "Problem / Why (Scope)", which has no Scope subsection; the scope lives in Recommendation and Design Decisions.
3. Spec Problem / Why cites `PuberDatabaseMigrationTest.kt:183-185` for the file deletion; the code has it at `:164-166` (Design cites it correctly).

# Readiness 1: watch-state-batch-in-progress

Verdict: **READY**

Read: spec and plan in the worktree (`69bd3763`, plan untracked), `.flow/runs/watch-state-batch-in-progress/test-design.md`, base `8c3117ea` (merge-base of the worktree HEAD).

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | P0 red `recordInProgress_runsTheWholeListInOneTransaction` |
| AC-2 | Task 1 | P0 red `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` |
| AC-3 | Task 1 (test left unchanged, checked in Done when) | P0 guard `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` |
| AC-4 | Task 2 (written); run by qa-device | P1 `markInProgressInOneTransactionEmitsNoPartialList` |
| AC-5 | Task 1 change, observed by qa-device | P1 manual check, steps 1-5 |
| AC-6 | Task 3 | P0 guard, check.full |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `WatchStateRepository.kt:325-335` | `recordInProgress`: `currentGeneration()` at :326, loop of `dao.markInProgress` outside any transaction |
| `WatchStateRepository.kt:450-458` | `recordHistoryPage = transaction.run { ... }`; its `recordFromHistory` calls `@Transaction upsertAllFromHistory` (:358), so the nesting claim holds |
| `WatchStateRepository.kt:48`, `:419` | `private val transaction: DatabaseTransaction`; `currentGeneration()` = `syncDao.get()?.generation` |
| `WatchStateDao.kt:175-194`, `:196-211`, `:36-37` | `@Transaction open suspend fun markInProgress`, `updateInProgress` SQL, `observeAll` |
| `DatabaseTransaction.kt:11-18`, `:21-23` | interface with `Direct`; `PuberDatabase.transactions()` over `withWriteTransaction` |
| `WatchStateRepositoryTest.kt:41-42`, `:56-66` (`:62`), `:1099`, `:1225`, `:1274`, `:1280` | fake fields, `repository(...)` factory with `DatabaseTransaction.Direct` literal, `FakeWatchStateDao`, its `markInProgress`, `FakeWatchStateSyncDao`, its `get` |
| `WatchStateRepositoryTest.kt:675-696` | `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`@Test` at :674) |
| `WatchStateDaoTest.kt:83-112`, `SETTLE_MILLIS` | `aServerRowEqualToTheStoredOneIsNotRewritten` collector pattern; `SETTLE_MILLIS = 1_000L` at :214; `markInProgressKeepsTheProgressAlreadyStored` at :175 |
| `WatchStateSyncInteractor.kt:239`, `:394` | the two `recordInProgress(movies.orEmpty(), observedAt)` callers |
| `modules.kt:133` (spec) | off by two: `WatchStateRepository(` binding is at `modules.kt:135`; no task depends on it |

## Checks

| Check | Result |
|---|---|
| Build stays green per task | Task 1 adds the seam with all tests green, writes the red tests, then the fix, commits on green; Tasks 2-3 add a test and the gate |
| Order by dependency | Task 2 consumes nothing from Task 1; Task 3 last |
| P0 rows name defect or mutation, `red`/`guard`, backticked test | AC-1, AC-2, AC-3 yes; AC-6 is the gate command, not a test name, acceptable for a gate row |
| Inside Out of scope | Plan Global constraints list DAO, no-op guard, batch DAO method, signature and callers as excluded; Tasks touch only `WatchStateRepository.kt`, `WatchStateRepositoryTest.kt`, `WatchStateDaoTest.kt` |
| Blocking open questions | None; Q1, Q2 non-blocking and answered |

## Notes for the conductor (non-blocking)

1. Spec `## Out of scope` reads "See Problem / Why (Scope)" but Problem / Why has no Scope subsection; the plan's Global constraints carry the real list. Replace the pointer with that list or a link to Design: Alternatives rejected.
2. Spec AC-4 runs `make DEVICE=192.168.1.110:5555 itest ...` without `GRADLE=./tools/agentw`; the Makefile defaults to `./gradlew`, which the guard hook refuses in a worktree. qa-device uses the plan's command (Task 2, Done when).
3. Spec cites `modules.kt:133`; the binding is at `:135`.

# Record in-progress watch states in one transaction - plan

**Spec:** `docs/superpowers/specs/2026-11-28-watch-state-batch-in-progress.md`
**Goal:** `recordInProgress` writes its whole list in one database transaction and opens none for an empty list.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :player-test-fixtures:testDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh && bash tools/tests/test-itest-tooling.sh`.
- All commands run in `/home/dev/orca/workspaces/Puber/watch-state-batch-in-progress`; never `./gradlew` there. Set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit,
  message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the
  files cited below, nothing else.
- Out of scope (spec: Design): DAO changes (`WatchStateDao.markInProgress`/`updateInProgress`, `app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:175-211`), a no-op rewrite guard, a new DAO batch method, any change to the `recordInProgress` signature or its callers (`WatchStateSyncInteractor.kt:239`, `:394`).
- Device work (AC-4 run, AC-5) belongs to `qa-device` under a lease on `192.168.1.110:5555`; the implementer does not touch a device.

---

## Tasks

### Task 1: Batch `recordInProgress` in one transaction

**Covers:** AC-1, AC-2, AC-3 (AC-5 is observed on this change by qa-device)
**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt` - `recordInProgress` (`:325-335`): add `if (items.isEmpty()) return` first, then wrap `val generation = currentGeneration()` and the `items.forEach { dao.markInProgress(...) }` loop in `transaction.run { }`, the way `recordHistoryPage` (`:450-458`) uses `transaction.run`. Relies on `private val transaction: DatabaseTransaction` (`:48`) and `currentGeneration()` (`:419`). Signature and KDoc stay.
- Test: `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt`

**Consumes:** `DatabaseTransaction` with `suspend fun <T> run(block: suspend () -> T): T` and `DatabaseTransaction.Direct` (`app/src/main/java/com/kino/puber/data/db/DatabaseTransaction.kt:11-18`).
**Produces:** test-local `RecordingTransaction` in `WatchStateRepositoryTest.kt`; no production interface.

Test seam (all in `WatchStateRepositoryTest.kt`):

| Piece | Change |
|---|---|
| `RecordingTransaction` (new, private, file level) | Implements `DatabaseTransaction`. `var runs` (count of `run` calls), `var active` (true inside `block()`, reset in `finally`), `val calls: MutableList<Pair<String, Boolean>>`, `fun record(call: String) { calls += call to active }` |
| Test-class fields (`:41-42`) | Add `private val recording = RecordingTransaction()` before them; construct `FakeWatchStateDao(recording)` and `FakeWatchStateSyncDao(recording)` |
| `repository(...)` factory (`:56-66`) | New parameter `transaction: DatabaseTransaction = DatabaseTransaction.Direct`, passed instead of the literal at `:62`; existing callers unchanged |
| `FakeWatchStateDao` (`:1099`) | Constructor `(private val recorder: RecordingTransaction)`; first line of `markInProgress` (`:1225`): `recorder.record("markInProgress")`. Rest of the fake unchanged |
| `FakeWatchStateSyncDao` (`:1274`) | Constructor `(private val recorder: RecordingTransaction)`; `get` (`:1280`) calls `recorder.record("syncDao.get")` before returning `row.value` |

- [x] Add the seam above; run `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.repository.WatchStateRepositoryTest'`: all existing tests green.
- [x] Write the failing test `recordInProgress_runsTheWholeListInOneTransaction`: `repository(eagerScope(), transaction = recording)`, `recording.calls.clear()`, `recordInProgress` with three `Item(id, title, type = ItemType.MOVIE)` (ids 1, 2, 3). Assert `recording.runs == 1`, `recording.calls` equals `listOf("syncDao.get" to true) + List(3) { "markInProgress" to true }`.
- [x] Write the failing test `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao`: same repository, `recording.calls.clear()`, `recordInProgress(emptyList())`. Assert `recording.runs == 0` and `recording.calls` is empty.
- [x] Run the class on the unchanged `recordInProgress`; record in the Implementation record that the first fails on `runs` (0) and the second on the `syncDao.get` log entry.
- [x] Apply the `recordInProgress` change.
- [x] Run the class again: both new tests and `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`:674-696`, left unchanged) green; run the fast gate; commit.

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.data.repository.WatchStateRepositoryTest'` passes with the two new tests, `git diff HEAD~1 --stat` lists only the two files above, and `git diff HEAD~1 -- app/src/test` shows no change inside `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress`.

---

### Task 2: Instrumented check that one transaction emits no partial list

**Covers:** AC-4
**Files:**
- Test: `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt` - add `markInProgressInOneTransactionEmitsNoPartialList` after `markInProgressKeepsTheProgressAlreadyStored`. Relies on `database`/`dao` fields and `setUp` (in-memory `PuberDatabase`), the collector pattern of `aServerRowEqualToTheStoredOneIsNotRewritten` (`:83-112`), `SETTLE_MILLIS` in the companion, `PuberDatabase.transactions()` (`app/src/main/java/com/kino/puber/data/db/DatabaseTransaction.kt:21-23`, same package, no import), `WatchStateDao.observeAll` (`WatchStateDao.kt:36-37`) and `markInProgress` (`WatchStateDao.kt:175-194`).

**Consumes:** nothing from Task 1 (the test drives Room directly).
**Produces:** none.

- [x] Write the test: `val ids = setOf(21, 22, 23)`; inside `withContext(Dispatchers.Default)`, a collector `async` on `dao.observeAll()` appends `rows.map { it.itemId }.toSet()` to a `java.util.concurrent.CopyOnWriteArrayList<Set<Int>>` and completes a `CompletableDeferred` on the first emission; after it, `database.transactions().run { ids.forEach { dao.markInProgress(generation = 1, itemId = it, isSeriesLike = false, updatedAt = 100) } }`; `delay(SETTLE_MILLIS)`; cancel the collector. Assert no emission is a non-empty proper subset of `ids` (`it.isNotEmpty() && ids.containsAll(it) && it != ids`), and the last emission equals `ids`.
- [x] Compile it: `./tools/agentw :app:compileDevDebugAndroidTestKotlin`; run the fast gate; commit.

**Done when:** `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and the fast gate pass. The test's run, `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` under a lease, is qa-device's; a red result is a finding for the owner (spec: Design, Risks), not a reason to change production code.

---

### Task 3: Full gate

**Covers:** AC-6
**Files:** none expected; a detekt or lint finding is fixed in the file Tasks 1-2 touched.

- [x] Run the full gate.
- [x] If a fix was needed, run the fast gate and commit it; otherwise no commit.

**Done when:** the full gate exits 0 on the branch head.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** `7ef420ef` Task 1 (seam, tests, batch change); Task 2 commit (instrumented test, compile-checked only); Task 3 needed no commit.

**Red run** (P0 `red` tests on the unchanged code):

- red: `recordInProgress_runsTheWholeListInOneTransaction` - assertion on `runs` (expected 1, was 0) at `WatchStateRepositoryTest.kt:701`
- red: `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` - assertion on the empty call log (`syncDao.get` logged) at `:717`

**Deviations from the plan:** none. The plan quoted `rows.value = emptyList()` in the fake `markInProgress`; the real body is the upsert logic, so only the `record` line was added.

**Files touched outside the plan:** none.

**For the reviewers:** AC-4 test is only compiled here; its run on the box is qa-device's. check.full exit 0 (BUILD SUCCESSFUL, both tooling scripts PASS).

**Fix round 1 (triage-1 #1):** added control case `markInProgressInSeparateCommitsEmitsAPartialList` to `WatchStateDaoTest` (same three `markInProgress` writes as separate commits, 200 ms pause between, no outer `run`); the collector and write helper are shared with the AC-4 test (`collectWhile`). Compiled only (`:app:compileDevDebugKotlin`, `:app:compileDevDebugAndroidTestKotlin` green); qa-device runs it on the box. Expected on the box: the control case is green, because it sees a non-empty proper subset of `{31, 32, 33}` among its emissions; the AC-4 test stays green with none. If the control case is red, no partial list can be produced there, so the AC-4 test is only a nesting smoke test and its KDoc must say so.

**Blocked:** none.

## Readiness

Verdict: **READY**

Read: spec and plan in the worktree (`69bd3763`, plan untracked), `.flow/runs/watch-state-batch-in-progress/test-design.md`, base `8c3117ea` (merge-base of the worktree HEAD).

### AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 1 | P0 red `recordInProgress_runsTheWholeListInOneTransaction` |
| AC-2 | Task 1 | P0 red `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` |
| AC-3 | Task 1 (test left unchanged, checked in Done when) | P0 guard `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` |
| AC-4 | Task 2 (written); run by qa-device | P1 `markInProgressInOneTransactionEmitsNoPartialList` |
| AC-5 | Task 1 change, observed by qa-device | P1 manual check, steps 1-5 |
| AC-6 | Task 3 | P0 guard, check.full |

### Citations sampled

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

### Checks

| Check | Result |
|---|---|
| Build stays green per task | Task 1 adds the seam with all tests green, writes the red tests, then the fix, commits on green; Tasks 2-3 add a test and the gate |
| Order by dependency | Task 2 consumes nothing from Task 1; Task 3 last |
| P0 rows name defect or mutation, `red`/`guard`, backticked test | AC-1, AC-2, AC-3 yes; AC-6 is the gate command, not a test name, acceptable for a gate row |
| Inside Out of scope | Plan Global constraints list DAO, no-op guard, batch DAO method, signature and callers as excluded; Tasks touch only `WatchStateRepository.kt`, `WatchStateRepositoryTest.kt`, `WatchStateDaoTest.kt` |
| Blocking open questions | None; Q1, Q2 non-blocking and answered |

### Notes for the conductor (non-blocking)

1. Spec `## Out of scope` reads "See Problem / Why (Scope)" but Problem / Why has no Scope subsection; the plan's Global constraints carry the real list. Replace the pointer with that list or a link to Design: Alternatives rejected.
2. Spec AC-4 runs `make DEVICE=192.168.1.110:5555 itest ...` without `GRADLE=./tools/agentw`; the Makefile defaults to `./gradlew`, which the guard hook refuses in a worktree. qa-device uses the plan's command (Task 2, Done when).
3. Spec cites `modules.kt:133`; the binding is at `:135`.

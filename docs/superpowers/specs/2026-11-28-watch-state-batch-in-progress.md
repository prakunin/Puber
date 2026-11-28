# Record in-progress watch states in one transaction

Status: approved
Date: 2026-11-28
Slug: watch-state-batch-in-progress
Kind: improvement
Size: M
Tracks: none

## Problem / Why

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (persistence: Room write path behind the watch index).
Research: `research.md` (code state, options A-C, related runs; not repeated here).

### Sizing
One goal and one production file (option A), but it changes how the watch index is persisted, a risk area in `.flow/roles/analyst.md`, so M. Track `perf` is not set: the target is a transaction count a test checks, not a device measurement.


### Current cost
`recordInProgress` (`app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:325-335`) reads `currentGeneration()` outside any transaction, then calls the `@Transaction` `markInProgress` (`app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:175-194`) once per item: N commits, N invalidations of `watch_state`, N re-runs of `observeAll` (`WatchStateDao.kt:36-37`). Every player exit reaches it through the catch-up (research.md: Current state in code).


### Recommendation
Option A (research.md: Options): `recordInProgress` returns at once on an empty list, otherwise runs `currentGeneration()` and the loop inside one `transaction.run { }`, as `recordHistoryPage` does (`WatchStateRepository.kt:450-460`). Signature, DAO and `updateInProgress` SQL stay unchanged.


### Risks
| Risk | Note |
|---|---|
| Nested transaction | `markInProgress`'s `@Transaction` nests in `withWriteTransaction`; `recordHistoryPage` already ships the same nesting. AC-4 confirms invalidation is delivered after the outer commit (research.md: External findings, `unverified`) |
| Rollback of the whole list on a mid-list failure | Earlier rows are no longer kept; the next catch-up rewrites them and no cursor depends on them (research.md: Risks) |
| Write lock held over the list | Tens of rows; a concurrent `markLocally` waits for one commit |
| Box session loss after a worktree install | Refresh the session with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

## Acceptance criteria

- **AC-1** Given a repository built with a recording `DatabaseTransaction` (counts `run` calls and whether each DAO call happened inside one), When `recordInProgress` gets three items, Then `run` is called exactly once and all three `markInProgress` calls and the `currentGeneration` read happen inside it. (unit, `WatchStateRepositoryTest`)
- **AC-2** Given the same repository, When `recordInProgress` gets an empty list, Then `run` is not called and the DAO is not touched. (unit)
- **AC-3** Given existing rows with stored progress, When `recordInProgress` marks them, Then `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt:675-697`) still passes unchanged. (unit)
- **AC-4** Given an in-memory `PuberDatabase` with a collector on `observeAll`, When three new items are marked in progress inside one `transactions().run { }`, Then no collected list holds only one or two of them, and the final list holds all three. (instrumented, `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`, run through `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` under a lease)
- **AC-5** Given the worktree dev APK installed with install-preserve on the box, When the owner's test account plays a movie and exits the player once at normal pace, Then the app returns to the previous screen with no crash and its watched/in-progress badges match the state before the change. (device observation; no scripted API calls)
- **AC-6** Given the change, When `check.full` runs in the worktree, Then it passes.

## Out of scope

The brief has no separate section; the non-goals are listed under Problem / Why and in the plan's Global constraints.

## Design

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the batch lives (brief Q1) | Repository: `recordInProgress` wraps its body in `transaction.run { }` | Same pattern as `recordHistoryPage` (`WatchStateRepository.kt:450-458`); one production file; a recording `DatabaseTransaction` makes AC-1 a plain unit test |
| Generation read | `currentGeneration()` (`WatchStateRepository.kt:419`) moves inside the transaction | AC-1; the read and the writes see one snapshot of `watch_state_sync` |
| Empty list | `if (items.isEmpty()) return` before the transaction and the generation read | AC-2: no `run`, no `syncDao.get`, no `dao` call |
| DAO | `markInProgress` / `updateInProgress` (`WatchStateDao.kt:175-211`) unchanged; the inner `@Transaction` nests in `withWriteTransaction` (`DatabaseTransaction.kt:21-23`) | Nesting already ships in `recordHistoryPage`/`recordCatchUpPage` |
| No-op rewrite guard (brief Q2) | Not added | Changes the `updated_at` observed-at rule (`WatchStateDao.kt:25-28`) |
| Unit-test seam | `repository(...)` factory (`WatchStateRepositoryTest.kt:56-66`) gains `transaction: DatabaseTransaction = DatabaseTransaction.Direct`; a test-local `RecordingTransaction` counts `run` calls and exposes `active`; `FakeWatchStateDao.markInProgress` (`:1225`) and `FakeWatchStateSyncDao.get` (`:1280`) append to a call log whether `active` was set | Other tests keep `Direct`; no production seam added |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:325-335` `recordInProgress`: early return on empty `items`, then `transaction.run { val generation = currentGeneration(); items.forEach { dao.markInProgress(...) } }`. Signature unchanged, so callers `WatchStateSyncInteractor.kt` catch-up and walk and their mocks stay as they are |
| Unit tests | `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt`: seam above; AC-1 and AC-2 tests; `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`:675-697`) untouched (AC-3) |
| Instrumented tests | `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`: AC-4 case using `database.transactions().run { }` around three `dao.markInProgress` calls, with an `observeAll` collector on `Dispatchers.Default` as in `aServerRowEqualToTheStoredOneIsNotRewritten` (`:83-112`) and its `SETTLE_MILLIS` wait; records every collected list's item-id set |
| ViewModel state and actions | N/A: no ViewModel reads or writes change |
| Screen and DI module | N/A: binding `modules.kt:133` unchanged |
| Compose content | N/A |
| Resources | N/A |

### Alternatives rejected

| Alternative | Reason |
|---|---|
| DAO `@Transaction markAllInProgress` (research option B) | Needs a fake override and a DAO test, and leaves the generation read outside the transaction |
| No-op guard in `updateInProgress` (option C) | Semantic change to a conflict rule; separate run if wanted |
| Collect rows and call `upsertAllFromServer` | That path overwrites progress columns; `markInProgress` must keep them (AC-3) |

### Risks

| Risk | Where | Note |
|---|---|---|
| Invalidation not deferred to the outer commit, so observers still see partial batches | `DatabaseTransaction.kt:22` (`withWriteTransaction`), Room 3 InvalidationTracker | Unverified in docs (research.md §5); AC-4 settles it on the box. If it fails, the unit-level gain (one commit) still holds but the AC is a finding for the owner |
| Mid-list failure now rolls back the whole list | `WatchStateRepository.kt:325-335` | Next catch-up rewrites the rows; no cursor depends on them |
| Write lock held across the list | same | Tens of rows; a concurrent `markLocally` waits one commit |
| Fake call log diverges from real DAO behaviour | `WatchStateRepositoryTest.kt:1225`, `:1280` | Log only appends; existing fake semantics kept, so other tests are unaffected |
| Box session loss after a worktree install (AC-5) | device run | Refresh with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

### Open questions

None.

## Test design

Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md). Base `8c3117ea`.

### Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| The loop stays outside one transaction (N commits), the goal of the run | 2 | 3 | 6 | AC-1 `recordInProgress_runsTheWholeListInOneTransaction` (unit) |
| `currentGeneration()` read before `transaction.run` opens | 2 | 2 | 4 | AC-1, per-call `active` flag on `syncDao.get` |
| Empty list still opens a transaction or reads `watch_state_sync` | 2 | 1 | 2 | AC-2 |
| Nested `withWriteTransaction` + `@Transaction markInProgress` throws or deadlocks on real Room | 1 | 3 | 3 | AC-4 (instrumented, nests the same way), AC-5 |
| Room delivers invalidation per inner commit, so observers still see partial batches | 2 | 2 | 4 | AC-4; a failure is a finding for the owner (design: Risks) |
| Stored progress lost by the in-progress mark | 1 | 3 | 3 | AC-3 guard |
| Mid-list failure rolls back the whole list | 1 | 2 | 2 | Accepted: next catch-up rewrites the rows, no cursor depends on them |
| Write lock held across the list | 1 | 1 | 1 | Accepted: tens of rows |
| Fake call log changes behaviour of other fake-based tests | 1 | 2 | 2 | Accepted: log only appends; AC-6 runs the whole suite |
| Box session lost after the worktree install | 2 | 3 | 6 | Manual check step 1-2 (refresh with master build, `auth-save`) |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `recordInProgress_runsTheWholeListInOneTransaction` | Three items: `RecordingTransaction.runs == 1`, call log holds one `syncDao.get` and three `markInProgress`, each with `active == true`. Fails on base (`runs == 0`); fails if `currentGeneration()` moves back outside `run` or the loop is split per item |
| AC-2 | unit | P0 red | `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` | `runs == 0` and the call log is empty. Red only through the empty log: base calls `syncDao.get` before the loop. Fails if the early return is removed |
| AC-3 | unit | P0 guard | `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` | Unchanged existing test (`WatchStateRepositoryTest.kt:675-697`); fails if the batch path writes through `upsertAllFromServer` or otherwise drops `progressTime`/`progressDuration` |
| AC-4 | instrumented | P1 | `markInProgressInOneTransactionEmitsNoPartialList` | Collector on `observeAll` (pattern of `aServerRowEqualToTheStoredOneIsNotRewritten`, `WatchStateDaoTest.kt:83-112`, `SETTLE_MILLIS`) records each emission's id set; three new ids marked inside `database.transactions().run { }`: no set is a non-empty proper subset of the three, the last equals all three. Fails if Room invalidates per nested `@Transaction` commit or rejects the nesting. Exercises Room, not `recordInProgress`, so it is green on base too |
| AC-5 | device | P1 | Manual check below | Real Room + catch-up path on the box: no crash, badges unchanged |
| AC-6 | gate | P0 guard | check.full command from `.flow/config.yaml` | Whole suite, detekt and lint stay green with the new fake seam |

`RecordingTransaction` and the call log are test-local in `WatchStateRepositoryTest.kt`; the `repository(...)` factory gains `transaction: DatabaseTransaction = DatabaseTransaction.Direct` (design: Decisions). `RecordingTransaction.run` sets `active = true` around `block()` and resets it in `finally`.

### Failing-first

`recordInProgress_runsTheWholeListInOneTransaction` is written and run first on the unchanged `recordInProgress` (base `WatchStateRepository.kt:325-335` has no `transaction.run`); it must fail on `runs == 1`. Then `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` must fail on the `syncDao.get` log entry.

### Manual checks

Device `192.168.1.110:5555` under a lease, package `com.kino.puber.stage` only, ordinary use at human pace, no scripted API calls.

1. Install a master dev build with install-preserve, launch, confirm the account is logged in, then `make DEVICE=192.168.1.110:5555 auth-save`.
2. On Home, D-pad to a movie row that holds an in-progress movie and a watched one; take a screenshot of their badges (before).
3. Build the worktree dev APK (`./tools/agentw :app:assembleDevDebug`), install it with `tools/android-apk-install-preserve`, launch. Expected: Home opens logged in, not the pairing screen.
4. D-pad to the same movie, `KEYCODE_DPAD_CENTER` to open it, start playback, wait about 30 s, `KEYCODE_BACK` until the player exits.
5. Wait about 5 s for the catch-up, `KEYCODE_BACK` to Home, screenshot the same row (after).

Expected: the player exits to the previous screen; badges after match badges before (the played movie may only gain progress).
Must not happen: crash or ANR, the pairing screen, a watched movie shown as in progress or the reverse, an in-progress badge disappearing, focus lost after the exit.

### Perf

N/A: track `perf` is off; the transaction count is checked by AC-1.

### UNTESTABLE

None.

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Batch in the repository (option A) or a new DAO `markAllInProgress` (option B)? | Option A: one file, same pattern as `recordHistoryPage`, testable with a fake transaction | no |
| Q2 | Also skip no-op in-progress rewrites (option C)? | No: it changes the `updated_at` observed-at rule (`WatchStateDao.kt:19-31`); a separate run if wanted | no |

All questions answered with the recommended answer (owner standing instruction: recommended).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-28 | Gate 1: approved on the recommended answers | owner (standing instruction: recommended) |
| 2026-11-28 | AC-4's itest command runs from a worktree as `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=...` (plan Global constraints); the AC text stays as approved | conductor (triage-1 #2) |

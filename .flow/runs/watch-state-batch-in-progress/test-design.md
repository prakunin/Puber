# Test design: watch-state-batch-in-progress

Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md). Base `8c3117ea`.

## Risk profile

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

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `recordInProgress_runsTheWholeListInOneTransaction` | Three items: `RecordingTransaction.runs == 1`, call log holds one `syncDao.get` and three `markInProgress`, each with `active == true`. Fails on base (`runs == 0`); fails if `currentGeneration()` moves back outside `run` or the loop is split per item |
| AC-2 | unit | P0 red | `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` | `runs == 0` and the call log is empty. Red only through the empty log: base calls `syncDao.get` before the loop. Fails if the early return is removed |
| AC-3 | unit | P0 guard | `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` | Unchanged existing test (`WatchStateRepositoryTest.kt:675-697`); fails if the batch path writes through `upsertAllFromServer` or otherwise drops `progressTime`/`progressDuration` |
| AC-4 | instrumented | P1 | `markInProgressInOneTransactionEmitsNoPartialList` | Collector on `observeAll` (pattern of `aServerRowEqualToTheStoredOneIsNotRewritten`, `WatchStateDaoTest.kt:83-112`, `SETTLE_MILLIS`) records each emission's id set; three new ids marked inside `database.transactions().run { }`: no set is a non-empty proper subset of the three, the last equals all three. Fails if Room invalidates per nested `@Transaction` commit or rejects the nesting. Exercises Room, not `recordInProgress`, so it is green on base too |
| AC-5 | device | P1 | Manual check below | Real Room + catch-up path on the box: no crash, badges unchanged |
| AC-6 | gate | P0 guard | check.full command from `.flow/config.yaml` | Whole suite, detekt and lint stay green with the new fake seam |

`RecordingTransaction` and the call log are test-local in `WatchStateRepositoryTest.kt`; the `repository(...)` factory gains `transaction: DatabaseTransaction = DatabaseTransaction.Direct` (design: Decisions). `RecordingTransaction.run` sets `active = true` around `block()` and resets it in `finally`.

## Failing-first

`recordInProgress_runsTheWholeListInOneTransaction` is written and run first on the unchanged `recordInProgress` (base `WatchStateRepository.kt:325-335` has no `transaction.run`); it must fail on `runs == 1`. Then `recordInProgress_withAnEmptyListOpensNoTransactionAndTouchesNoDao` must fail on the `syncDao.get` log entry.

## Manual checks

Device `192.168.1.110:5555` under a lease, package `com.kino.puber.stage` only, ordinary use at human pace, no scripted API calls.

1. Install a master dev build with install-preserve, launch, confirm the account is logged in, then `make DEVICE=192.168.1.110:5555 auth-save`.
2. On Home, D-pad to a movie row that holds an in-progress movie and a watched one; take a screenshot of their badges (before).
3. Build the worktree dev APK (`./tools/agentw :app:assembleDevDebug`), install it with `tools/android-apk-install-preserve`, launch. Expected: Home opens logged in, not the pairing screen.
4. D-pad to the same movie, `KEYCODE_DPAD_CENTER` to open it, start playback, wait about 30 s, `KEYCODE_BACK` until the player exits.
5. Wait about 5 s for the catch-up, `KEYCODE_BACK` to Home, screenshot the same row (after).

Expected: the player exits to the previous screen; badges after match badges before (the played movie may only gain progress).
Must not happen: crash or ANR, the pairing screen, a watched movie shown as in progress or the reverse, an in-progress badge disappearing, focus lost after the exit.

## Perf

N/A: track `perf` is off; the transaction count is checked by AC-1.

## UNTESTABLE

None.

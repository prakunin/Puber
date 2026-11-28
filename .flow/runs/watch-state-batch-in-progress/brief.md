# Brief: watch-state-batch-in-progress

Kind: improvement   Size: M   Tracks: none   Base: 6534e4e6
Device verification: yes (persistence: Room write path behind the watch index).
Research: `research.md` (code state, options A-C, related runs; not repeated here).

## Sizing
One goal and one production file (option A), but it changes how the watch index is persisted, a risk area in `.flow/roles/analyst.md`, so M. Track `perf` is not set: the target is a transaction count a test checks, not a device measurement.

## Current cost
`recordInProgress` (`app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:325-335`) reads `currentGeneration()` outside any transaction, then calls the `@Transaction` `markInProgress` (`app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:175-194`) once per item: N commits, N invalidations of `watch_state`, N re-runs of `observeAll` (`WatchStateDao.kt:36-37`). Every player exit reaches it through the catch-up (research.md: Current state in code).

## Recommendation
Option A (research.md: Options): `recordInProgress` returns at once on an empty list, otherwise runs `currentGeneration()` and the loop inside one `transaction.run { }`, as `recordHistoryPage` does (`WatchStateRepository.kt:450-460`). Signature, DAO and `updateInProgress` SQL stay unchanged.

## Acceptance criteria
- **AC-1** Given a repository built with a recording `DatabaseTransaction` (counts `run` calls and whether each DAO call happened inside one), When `recordInProgress` gets three items, Then `run` is called exactly once and all three `markInProgress` calls and the `currentGeneration` read happen inside it. (unit, `WatchStateRepositoryTest`)
- **AC-2** Given the same repository, When `recordInProgress` gets an empty list, Then `run` is not called and the DAO is not touched. (unit)
- **AC-3** Given existing rows with stored progress, When `recordInProgress` marks them, Then `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt:675-697`) still passes unchanged. (unit)
- **AC-4** Given an in-memory `PuberDatabase` with a collector on `observeAll`, When three new items are marked in progress inside one `transactions().run { }`, Then no collected list holds only one or two of them, and the final list holds all three. (instrumented, `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`, run through `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` under a lease)
- **AC-5** Given the worktree dev APK installed with install-preserve on the box, When the owner's test account plays a movie and exits the player once at normal pace, Then the app returns to the previous screen with no crash and its watched/in-progress badges match the state before the change. (device observation; no scripted API calls)
- **AC-6** Given the change, When `check.full` runs in the worktree, Then it passes.

## Risks
| Risk | Note |
|---|---|
| Nested transaction | `markInProgress`'s `@Transaction` nests in `withWriteTransaction`; `recordHistoryPage` already ships the same nesting. AC-4 confirms invalidation is delivered after the outer commit (research.md: External findings, `unverified`) |
| Rollback of the whole list on a mid-list failure | Earlier rows are no longer kept; the next catch-up rewrites them and no cursor depends on them (research.md: Risks) |
| Write lock held over the list | Tens of rows; a concurrent `markLocally` waits for one commit |
| Box session loss after a worktree install | Refresh the session with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Batch in the repository (option A) or a new DAO `markAllInProgress` (option B)? | Option A: one file, same pattern as `recordHistoryPage`, testable with a fake transaction | no |
| Q2 | Also skip no-op in-progress rewrites (option C)? | No: it changes the `updated_at` observed-at rule (`WatchStateDao.kt:19-31`); a separate run if wanted | no |

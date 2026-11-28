# Design: watch-state-batch-in-progress

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where the batch lives (brief Q1) | Repository: `recordInProgress` wraps its body in `transaction.run { }` | Same pattern as `recordHistoryPage` (`WatchStateRepository.kt:450-458`); one production file; a recording `DatabaseTransaction` makes AC-1 a plain unit test |
| Generation read | `currentGeneration()` (`WatchStateRepository.kt:419`) moves inside the transaction | AC-1; the read and the writes see one snapshot of `watch_state_sync` |
| Empty list | `if (items.isEmpty()) return` before the transaction and the generation read | AC-2: no `run`, no `syncDao.get`, no `dao` call |
| DAO | `markInProgress` / `updateInProgress` (`WatchStateDao.kt:175-211`) unchanged; the inner `@Transaction` nests in `withWriteTransaction` (`DatabaseTransaction.kt:21-23`) | Nesting already ships in `recordHistoryPage`/`recordCatchUpPage` |
| No-op rewrite guard (brief Q2) | Not added | Changes the `updated_at` observed-at rule (`WatchStateDao.kt:25-28`) |
| Unit-test seam | `repository(...)` factory (`WatchStateRepositoryTest.kt:56-66`) gains `transaction: DatabaseTransaction = DatabaseTransaction.Direct`; a test-local `RecordingTransaction` counts `run` calls and exposes `active`; `FakeWatchStateDao.markInProgress` (`:1225`) and `FakeWatchStateSyncDao.get` (`:1280`) append to a call log whether `active` was set | Other tests keep `Direct`; no production seam added |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:325-335` `recordInProgress`: early return on empty `items`, then `transaction.run { val generation = currentGeneration(); items.forEach { dao.markInProgress(...) } }`. Signature unchanged, so callers `WatchStateSyncInteractor.kt` catch-up and walk and their mocks stay as they are |
| Unit tests | `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt`: seam above; AC-1 and AC-2 tests; `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress` (`:675-697`) untouched (AC-3) |
| Instrumented tests | `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt`: AC-4 case using `database.transactions().run { }` around three `dao.markInProgress` calls, with an `observeAll` collector on `Dispatchers.Default` as in `aServerRowEqualToTheStoredOneIsNotRewritten` (`:83-112`) and its `SETTLE_MILLIS` wait; records every collected list's item-id set |
| ViewModel state and actions | N/A: no ViewModel reads or writes change |
| Screen and DI module | N/A: binding `modules.kt:133` unchanged |
| Compose content | N/A |
| Resources | N/A |

## Alternatives rejected

| Alternative | Reason |
|---|---|
| DAO `@Transaction markAllInProgress` (research option B) | Needs a fake override and a DAO test, and leaves the generation read outside the transaction |
| No-op guard in `updateInProgress` (option C) | Semantic change to a conflict rule; separate run if wanted |
| Collect rows and call `upsertAllFromServer` | That path overwrites progress columns; `markInProgress` must keep them (AC-3) |

## Risks

| Risk | Where | Note |
|---|---|---|
| Invalidation not deferred to the outer commit, so observers still see partial batches | `DatabaseTransaction.kt:22` (`withWriteTransaction`), Room 3 InvalidationTracker | Unverified in docs (research.md §5); AC-4 settles it on the box. If it fails, the unit-level gain (one commit) still holds but the AC is a finding for the owner |
| Mid-list failure now rolls back the whole list | `WatchStateRepository.kt:325-335` | Next catch-up rewrites the rows; no cursor depends on them |
| Write lock held across the list | same | Tens of rows; a concurrent `markLocally` waits one commit |
| Fake call log diverges from real DAO behaviour | `WatchStateRepositoryTest.kt:1225`, `:1280` | Log only appends; existing fake semantics kept, so other tests are unaffected |
| Box session loss after a worktree install (AC-5) | device run | Refresh with a master build and `auth-save` before installing (memory: worktree-build-loses-box-session) |

## Open questions

None.

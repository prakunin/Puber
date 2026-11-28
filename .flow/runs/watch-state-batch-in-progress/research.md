# Research: watch-state-batch-in-progress

## 1. Request

WatchStateRepository.recordInProgress (WatchStateRepository.kt around 325-335) runs a separate transaction per item: N commits and N rereads of the whole watch_state right after leaving the player. Do it in a single transaction for the whole list, with one change emission.

## 2. Understanding

`recordInProgress` writes the `/watching/movies` list into the watch index by calling the DAO's `@Transaction markInProgress` once per movie, so a list of N movies costs N write transactions, and each commit invalidates `watch_state` and makes Room re-run `SELECT * FROM watch_state` behind `snapshot`. The change wraps the whole list in one write transaction, so the batch commits once and invalidates once. The catch-up that every player exit requests runs this path, and so does the full sync walk.

## 3. Current state in code

| Location | What it does now |
|---|---|
| `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:325-335` | Reads `currentGeneration()` once, then `items.forEach { dao.markInProgress(...) }`; no outer transaction, no empty-list return |
| `app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:175-194` | `@Transaction open suspend fun markInProgress`: `insertIfAbsent`, then `updateInProgress` on conflict, one transaction per call |
| `app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:196-211` | `updateInProgress` sets `is_fully_watched = 0, updated_at, generation` whenever `:updatedAt >= updated_at` and the row is not pending; it has no no-op guard, so an unchanged movie is still rewritten and still invalidates |
| `app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:66-104` | Precedent: `upsertAllFromServer` / `upsertAllFromHistory` loop a whole list inside one `@Transaction` ("a single change rather than one per row", `:63-64`) |
| `app/src/main/java/com/kino/puber/data/db/WatchStateDao.kt:36-37` | `observeAll(): Flow<List<WatchStateEntity>>`, the full-table query that each invalidation re-runs |
| `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:55-57` | `snapshot` = `observeAll().map { associate }.stateIn(...)`; StateFlow drops maps equal to the last one, so an unchanged batch costs rereads but no emission |
| `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:174-203` | `onIndexChanged` decides each `version` tick; inside an open CATCH_UP or WALK window it only sets a held flag, paid out once at `endSyncWindow` (`:236-260`) |
| `app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:450-473` | Precedent: `recordHistoryPage` / `recordCatchUpPage` wrap a DAO `@Transaction` call in `transaction.run { }`, so nested transactions already ship |
| `app/src/main/java/com/kino/puber/data/db/DatabaseTransaction.kt:11-23` | `DatabaseTransaction.run` = `withWriteTransaction`; `Direct` runs the block as it is, for tests |
| `app/src/main/java/com/kino/puber/data/di/modules.kt:133` | Binds `PuberDatabase.transactions()` |
| `app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:37-40` | `Room.databaseBuilder(...).setDriver(AndroidSQLiteDriver())` |
| `app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:228-246` | Catch-up: `recordCatchUpPage`, `recordFromServer(series)`, `recordInProgress(movies)`, all inside `beginSyncWindow(CATCH_UP)` |
| `app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:392-395` | Walk: `recordInProgress(movies)` after the history, inside the WALK window (`:323-330`) |
| `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:2040-2047` | `exitPlayer` calls `interactor.requestWatchStateCatchUp()` (`PlayerInteractor.kt:289`, `respectFloor = false`), so every player exit runs the catch-up |
| `app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt:96-104` | Start, resume and tab entry also request the catch-up, with a 60 s floor |

Tests around it:

| Test | Coverage |
|---|---|
| `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt:675-697` | `recordInProgress_marksBareItemsUnfinishedWithoutLosingProgress`; the repository is built with `DatabaseTransaction.Direct` (`:56-62`) |
| `app/src/test/kotlin/com/kino/puber/data/repository/WatchStateRepositoryTest.kt:1099`, `:1225-1245` | `FakeWatchStateDao` overrides `markInProgress`; a new DAO method needs a fake override |
| `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:164-184` | Instrumented `markInProgress` insert/update and progress-kept cases |
| `app/src/test/kotlin/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractorTest.kt:109`, `:819`, `:851`, `:989` | Mock `recordInProgress`; the signature is unchanged, so these are unaffected |

## 4. Related work

| Item | Relation |
|---|---|
| Run `watch-index-catch-up` (done), `docs/superpowers/specs/2026-11-23-watch-index-catch-up.md` | Added the player-exit catch-up (AC-5) that now calls `recordInProgress`; its AC-6 wants generation read under the write lock for history rows only (spec `:60`) |
| `.flow/runs/watch-index-catch-up/review-tests-4.md:6` | Notes that the `recordInProgress` emission after the catch-up window closes can land in a walk window and be held. Ordering only, unchanged by this run |
| Run `room-wal-journal-mode` (triage) | Sibling from the same 2026-11-28 audit; journal mode changes the cost of each commit. Independent of this run |
| Run `card-progress-watching-duration` (done) | Relies on `recordInProgress` keeping the stored progress (research `:28`); the behaviour must stay |
| Commit `269e9e9b` | Added the no-op guard to `upsertFromServer` and none to `updateInProgress` |
| Commit `183c10b1` | Catch-up and its triggers |

DUPLICATE_OF: none. No other run under `.flow/runs` or `.flow/handoff` mentions `recordInProgress` or `markInProgress` as work to do.

## 5. External findings

| Finding | Source |
|---|---|
| Project uses Room 3.0.3 (`gradle/libs.versions.toml:33`), the current stable release (latest update 2026-11-18) | https://developer.android.com/jetpack/androidx/releases/room3 |
| A non-abstract `@Transaction` DAO function runs its body in one database transaction | https://developer.android.com/reference/kotlin/androidx/room3/Transaction (3.0.0) |
| The InvalidationTracker marks a table invalidated when an operation changes it, and Flows created on it emit a new value | https://developer.android.com/reference/kotlin/androidx/room3/InvalidationTracker (3.0.0) |
| Observable reads re-run every time the underlying tables change | https://developer.android.com/training/data-storage/room/async-queries |

`unverified`: the pages read do not say that Room 3 delivers invalidation once per outer transaction, after it ends. The nested pattern in `recordHistoryPage` (`WatchStateRepository.kt:450-460`) relies on the same thing, and an instrumented test can confirm it (Q3).

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Wrap the loop in `transaction.run { }` in `recordInProgress`, read `currentGeneration()` inside it, return early on an empty list | One file plus tests. Same pattern as `recordHistoryPage`. `markInProgress`'s inner `@Transaction` nests inside the outer write transaction. A recording `DatabaseTransaction` fake makes "one transaction" a plain unit test | S |
| B | New DAO `@Transaction open suspend fun markAllInProgress(generation, rows)` that loops `insertIfAbsent` and `updateInProgress`, like `upsertAllFromServer` | Matches the DAO's `upsertAll*` convention. Needs a `FakeWatchStateDao` override and an instrumented DAO test for the single transaction. Generation is still read outside the transaction, as in `recordFromServer` | S |
| C | A or B, plus a no-op guard in `updateInProgress` (skip when `is_fully_watched = 0` and the generation is equal) | Also drops the commit and reread when nothing changed. But `updated_at` would stop moving forward, which weakens the observed-at guard (`WatchStateDao.kt:19-31`). That is a semantic change to a conflict rule | M |

## 7. Risks

| Risk | Note |
|---|---|
| Write lock held for the whole list | One transaction over N small statements. The list is `/watching/movies`, typically tens of rows. A concurrent `markLocally` waits for one commit instead of interleaving. This is acceptable, and `recordHistoryPage` already does the same with larger batches |
| A failure mid-list rolls back the batch | Today the rows before the failure stay written. After the change none of them do, and the next catch-up rewrites them. No cursor depends on these rows |
| The emission count is already one inside the windows | `version` ticks are held by the CATCH_UP/WALK windows, so screens see no change. The gain is in commits and `observeAll` rereads, not in visible ticks |
| Persistence change | AGENTS.md (Runtime) counts persistence as needing on-device verification |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Should the batch go in the repository (`transaction.run`, option A) or in a new DAO method (option B)? | Repository, option A |
| Q2 | Should this run also skip no-op in-progress rewrites (option C)? | No. If wanted, it goes in a separate run, because it changes the `updated_at` rule |
| Q3 | What proves the change works? | A unit test with a recording `DatabaseTransaction` (one `run` per call, none for an empty list), the existing behaviour test, and an instrumented `WatchStateRepository`/DAO case through `make DEVICE=192.168.1.110:5555 itest` showing one `observeAll` emission for a multi-item batch. Then one player exit on the box at normal pace, with no API probing |

## 9. Sources

- Files: `WatchStateRepository.kt`, `WatchStateDao.kt`, `DatabaseTransaction.kt`, `PuberDatabase.kt`, `data/di/modules.kt`, `WatchStateSyncInteractor.kt`, `PlayerVM.kt`, `PlayerInteractor.kt`, `MainVM.kt`, `WatchStateRepositoryTest.kt`, `WatchStateDaoTest.kt`, `WatchStateSyncInteractorTest.kt`, `gradle/libs.versions.toml`
- Commits: `269e9e9b`, `183c10b1`, `eb6c9b16`
- Runs: `watch-index-catch-up`, `card-progress-watching-duration`, `room-wal-journal-mode`, `cache-layer-rework`
- Docs: `docs/superpowers/specs/2026-11-23-watch-index-catch-up.md`, `docs/superpowers/plans/2026-11-23-watch-index-catch-up.md`
- URLs: https://developer.android.com/jetpack/androidx/releases/room3, https://developer.android.com/reference/kotlin/androidx/room3/Transaction, https://developer.android.com/reference/kotlin/androidx/room3/InvalidationTracker, https://developer.android.com/training/data-storage/room/async-queries

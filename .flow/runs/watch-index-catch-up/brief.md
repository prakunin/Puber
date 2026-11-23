# Brief: watch-index-catch-up

Base 3ef5beec. Analyst, 2026-11-22.

Builds on `research.md`; the gates relied on below (`WatchStateSyncInteractor.kt:96`, `:259-264`, `:300-305`, `MainVM.kt:48-54`) were re-read at 3ef5beec and hold.

kind: bug. size: M. tracks: none. Device verification: **yes** (sync, persistence, networking, startup slot, ViewModel triggers; AC-8 and AC-9 only show on the box 192.168.1.110:5555).

Size reason: one goal (every device catches the index up to its mark on start and on the listed triggers), across MainVM, ContentList, PlayerVM, the sync interactor and the repository; persistence, networking and startup are risk areas in `.flow/roles/analyst.md`. Research option A (research.md: Options).

## Request
research.md: Request. Owner decision (`state.md` Decisions 2026-11-22): keep a last-updated mark, fetch history back to it on start, on every device, not only where Details was opened.

## Root cause
Status: **plausible** for the Znieczulenie report on the box (which candidate applied there is not measured); each gate is **confirmed** from code.

| Finding | Where | Status |
|---|---|---|
| The mark already exists (`historyNewestSeen`) and the incremental walk already stops at it | `WatchStateSyncEntity.kt:21-39`, `WatchStateSyncInteractor.kt:346-353`, `:443-477` | confirmed (code) |
| Start and resume skip the walk for 1 h after a complete sync and for 5 min after any run in the process | `WatchStateSyncInteractor.kt:259-264`, `MainVM.kt:48-54`, `:88-98` | confirmed (code) |
| A non-forced run is dropped while a walk holds the mutex, including while it is parked in the background | `WatchStateSyncInteractor.kt:96`, `:144`, `:429-433` | confirmed (code) |
| During a full or reconcile pass a resumed run starts at `historyResumePage - 2`, not page 1 | `WatchStateSyncInteractor.kt:300-305` | confirmed (code) |
| A series whose last history row was written while it was in `watching/serials` stays unfinished after it leaves the list until a newer entry, the 7-day reconcile or Details | `WatchStateRepository.kt:450-475`, `:444-445` | confirmed (code); whether `watching/toggle` writes history is unverified |
| Catalogue entry and a player exit with no watched mark trigger nothing | `ContentListInteractor.kt:70-77`, `PlayerVM.kt:2016-2036` | confirmed (code) |
| `/v1/history` order by `last_seen` descending is assumed, not documented | `WatchStateDao.kt:85-88`; research.md: External findings | unverified; AC-8 checks it |

## Acceptance criteria
- AC-1. Given an index whose `lastSyncAt` is less than 1 h old and history holding entries newer than `historyNewestSeen`, When the catch-up runs on Main start, Then both watching lists and history from page 1 are read until the first non-empty page whose entries all have `last_seen <= historyNewestSeen`, the newer entries are folded in, and the mark advances (unit, `WatchStateSyncInteractorTest`).
- AC-2. Given a full or reconcile walk in progress (running or parked, `fullHistoryWalkDone = false`), When a catch-up is requested, Then it reads from page 1 to the mark without waiting for the walk to finish, and `historyResumePage`, `fullHistoryWalkDone`, `lastSyncAt` and the prune are left as the walk set them (unit).
- AC-3. Given a mark 30+ pages behind page 1, When the catch-up runs, Then it reads every page down to the mark in one run, with no 300-page budget and no 20 s chunk pause, and an error on any page leaves the mark unchanged (unit).
- AC-4. Given a series unfinished in the index and absent from the fresh `watching/serials?subscribed=0`, When the catch-up runs, Then its Details are fetched once and the row takes the Details verdict (`Item.isFullyWatched`), so a series finished on another device is hidden under "Hide watched" without a new history entry (unit).
- AC-5. Given the app on Main, When it starts (after `StartupSlot.WatchState`), resumes, enters the Movies or Series tab, or exits the player, Then a catch-up is requested; start, resume and tab entry share a 60 s floor, player exit has none (unit, `MainVMWatchStateSyncTest`, `ContentListInteractorTest`, a PlayerVM test).
- AC-6. Given a catch-up writes rows, When a reconcile pass later prunes by generation, Then those rows carry `currentGeneration()` and survive; and a logout during a catch-up discards its writes (`sessionSurvived`) (unit).
- AC-7. Given history has nothing newer than the mark and no series left the list, When the catch-up runs, Then no row is written and `settledChanges` does not emit, so a filtered catalogue does not re-page (unit).
- AC-8. Given the box, When history pages 1-3 are read (read-only, `perpage` as the sync uses it), Then the evidence records whether `last_seen` is non-increasing across them; if it is not, the run stops for a design change (observation).
- AC-9. Given the box with "Hide watched" on, the Series tab showing a series, and that series' remaining episodes then watched or marked on another client, When the app on the box is resumed or restarted and the Series tab opened, without opening Details, Then the series is gone within one catch-up (observation).
- AC-10. Given the full check, When `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` runs, Then it passes; existing `WatchStateSyncInteractorTest`, `WatchStateRepositoryTest` and `HistoryVMLifecycleTest` change only where AC-1 to AC-7 change their contract.

Out of scope: the 90% rule for a final episode left in the credits (research.md: Risks, own idea run if wanted); `collection-watched-mark-lag`.

## Risks
| Risk | Guard |
|---|---|
| History not ordered by `last_seen` descending, so any stop-at-mark rule misses entries | AC-8 before relying on it |
| Network contention at start | Start catch-up keeps `StartupSlot.WatchState` and `awaitForeground` (`startup-settled-signal`) |
| Catch-up and a running walk race on rows and the cursor | Own lock or between chunks; row upsert keeps the newer `history_seen_at`/`updated_at` (`WatchStateDao.kt:106-118`); AC-2 |
| Prune deletes catch-up rows; logout mid-run leaks rows | AC-6 |
| Index churn re-pages the catalogue on every trigger | No-op writes skipped (`269e9e9b`); AC-7 |
| Details refetch volume | Only series that left the list since the last run (AC-4) |

## Open questions
1. Triggers and floor: Main start, every resume, Movies/Series tab entry, player exit; 60 s shared floor, none on player exit? Recommended: yes (as in AC-5). Blocks: no.
2. Refetch Details for series that left `watching/serials` but are unfinished in the index, in this run? Recommended: yes; it is the cross-device case behind the report and costs requests only when a series leaves the list (AC-4). Blocks: yes.
3. Use `perpage=50` for the catch-up and the full walk? Recommended: yes, 2.5x fewer requests; the History screen keeps its own page size. Blocks: no.

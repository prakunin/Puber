# Research: watch-index-catch-up

## 1. Request

Raw request (owner, 2026-11-22, Russian chat, summarised by the conductor):

> Watch-state index lags the server: a fully watched series (Znieczulenie, Series tab, "Hide watched" on) still shows in the catalogue, and on every other device it stays until that device's own sync catches up.
> Owner direction: keep a mark of when the index was last updated and fetch history back to that mark on each start, so a device that is rarely switched on also gets current state.

## 2. Understanding

Each install keeps its own watch-state index, and "Hide watched" filters the catalogue through it. The index already stores the mark the owner asks for (`historyNewestSeen`, the `last_seen` of the newest history entry folded in) and already has an incremental walk that stops at the first page with nothing newer. The walk is cheap, but the gates in front of it are not: a run happens at most once an hour after a complete sync, never from catalogue entry, is dropped while a long walk holds the lock, and skips page 1 while a full or reconcile walk is being resumed. The fix is a cheap catch-up path to the existing mark that is called often (start, resume, catalogue entry, player exit), with the full walk left on its own schedule.

## 3. Current state in code

All paths under `app/src/main/java/com/kino/puber/`.

**The mark and the walk**

- `data/db/WatchStateSyncEntity.kt:21-39`: one row holding `last_sync_at`, `history_newest_seen`, `full_history_walk_done`, `history_resume_page`, `generation`, `last_reconciled_at`, `history_total_items`. Written in the same transaction as the rows (`data/repository/WatchStateRepository.kt:386-394`).
- `domain/interactor/watchstate/WatchStateSyncInteractor.kt:46-50`: `staleAfter = 1h`, `minTimeBetweenRuns = 5 min` (in memory, `:75`), `reconcileAfter = 7 days`, `pauseBetweenChunks = 20 s`.
- `:96`: a non-forced call returns at once if the mutex is held (`tryLock`). A walk holds it for its whole length, including the time it is parked by `awaitForeground` (`:144`, `:429-433`).
- `:259-264` `isRunDue`: skipped while `now - lastSyncAt < maxIndexAge` (1 h by default), or less than 5 min after the last run in this process.
- `:183`, `:315-328` `withNewPass`: every 7 days (or on Rebuild) it resets `fullHistoryWalkDone = false` and starts a new full pass.
- `:191-193`: every run first fetches `watching/serials?subscribed=0` and `watching/movies`. The history walk runs only if both answered (`:205-214`).
- `:300-305` `firstPageToRead`: during a full walk a later run resumes at `historyResumePage - 2`, **not at page 1**. Entries played since the walk started are not read until that walk finishes.
- `:346-353`, `:443-477`: the incremental walk starts at page 1 and stops at the first non-empty page whose entries all have `last_seen <= historyNewestSeen`. This works only if `/v1/history` is ordered by `last_seen` descending. The code assumes that order (`data/db/WatchStateDao.kt:85-88`). The API docs do not state it (section 5).
- `:371`, `:491`, `:502`: at most 300 pages per run, in chunks of 15 with a 20 s pause between chunks. A full budget therefore takes at least about 6.5 min in the foreground.
- `:233-239`: `lastSyncAt` is stamped only when the walk reaches the end. A walk that runs out of budget or hits a failed page leaves it unstamped, and the next run waits out the 5 min floor.
- `data/api/history/HistoryRequest.kt:16-23`: the client sends only `page`, so it gets the default of 20 entries per page. The docs allow up to 50.
- `data/api/history/HistoryPageResponse.kt:24`, `:41-63`: entries flagged `deleted` are dropped, and `time`/`last_seen` become `watching.time` and `updated`.

**Verdicts**

- `data/repository/WatchStateRepository.kt:450-475` `toHistoryRowOrNull`: an entry counts as finished when `time >= 0.9 * duration` (`data/repository/WatchCompletionPolicy.kt:18-24`). A series also has to be absent from `watching/serials`.
- `:444-445` and `data/db/WatchStateDao.kt:106-118`: a history row replaces a stored row only if its `history_seen_at` and `updated_at` are at least as new.
- `data/api/models/Models.kt:164-176` `Item.isFullyWatched`: a series item with counters is finished when `new == 0`, or when `watched >= total`. These counters come from Details and from `watching/serials` (`WatchStateRepository.kt:258-263`, `:504-521`).
- **Gap:** a series whose history row was written while it was still in `watching/serials` stays unfinished after it leaves the list, unless a newer history entry for it appears. The incremental walk never re-reads the older entry, so the row waits for the next 7-day reconcile or for Details to be opened. One way this happens: the remaining episodes are marked watched with `watching/toggle`. Whether a toggle writes a history entry is `unverified`.

**Triggers**

| Trigger | Where | Gate |
|---|---|---|
| Main start and every resume | `ui/feature/main/vm/MainVM.kt:48-54`, `:88-98`, `:117` | `syncIfStale()`: 1 h, 5 min floor, dropped while a walk runs; the first run waits for `StartupSlot.WatchState` (`core/startup/StartupSettled.kt:12-19`, 2 s after the app settles) |
| History screen | `ui/feature/history/vm/HistoryVM.kt:111-113` | `requestSync(force = false, maxIndexAge = ZERO)`: 5 min floor, dropped while a walk runs |
| Settings > Rebuild | `ui/feature/device/settings/vm/DeviceSettingsVM.kt:237` | forced, full pass |
| Details read | `data/repository/ItemDetailsRepository.kt:17-29` | records the one item (fresh values only) |
| Watched mark in the player (auto, with less than 10% left, or manual) | `ui/feature/player/vm/PlayerVM.kt:1942-1953`, `:1194-1213` → `domain/interactor/player/PlayerInteractor.kt:286-295` → `ItemDetailsRepository.getItemDetails` (`:69-71`) | **records the item.** This corrects triage's "player never writes the index", which holds only for an exit with no watched mark. |
| Player exit with no watched mark | `PlayerVM.kt:2016-2036`, `:1980-1988` | saves the position, marks Details stale and refreshes TV home; **no index write** |
| Catalogue tab entry | `domain/interactor/contentlist/ContentListInteractor.kt:70-77`, `:170-177` | none. The list only re-pages when the index version changes. |

- The player opens directly, without Details, from Home, Collections, Bookmarks, Show all, Favourites, History, the catalogue, Search and deep links (`HomeVM.kt:252`, `ContentListVM.kt:113`, `SearchVM.kt:122`, `core/contentlink/ContentTargetScreenMapper.kt:17`, and others). After an exit with no watched mark, only a return to Details (`DetailsVM.kt:552-558`, forced reload) updates the item.

**Tests in place**

- `app/src/test/kotlin/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractorTest.kt` (46 tests)
- `.../ui/feature/main/vm/MainVMWatchStateSyncTest.kt` (6 tests)
- `.../data/repository/WatchStateRepositoryTest.kt` (38 tests)
- `WatchCompletionPolicyTest.kt`, `HistoryVMLifecycleTest.kt`, `ContentListInteractorTest.kt`, `ItemDetailsRepositoryTest.kt`

## 4. Related work

- **Probable duplicate:** none.
- `collection-watched-mark-lag` (idea): a card shows a stale mark for a few seconds after a Details mark, because the server lags its own write. Different cause, same index. It will matter if the catch-up writes right after a player mark.
- `card-progress-watching-duration` (done, `eb6c9b16`): a movie's verdict comes from a shared first-video rule. It touched `WatchStateRepository.kt:499-521`.
- `resume-progress-anomaly` (done): `watching/marktime` saves used to fail to decode, which since 1.5.0 skipped the follow-ups after a save. Now `Result<Unit>`, so position saves land.
- `startup-settled-signal` (done, `08271680`): the first watch-state run takes the last startup slot (`docs/superpowers/specs/2026-11-21-startup-settled-signal.md`, lines 25-26, 79). A catch-up on start has to keep that slot.
- Commits on the sync: `03c2ca31` (1.7.1: incremental walk, `historyNewestSeen`, reconcile), `b64e2ea5` (Rebuild and the account total), `5cd86940` (History screen catch-up), `269e9e9b` (no-op updates skipped, pending flags cleared after startup).

## 5. External findings

KinoPub API v1.3 docs, read 2026-11-22:

| Finding | Source |
|---|---|
| `GET /v1/history` takes `page` and `perpage` (default 20, max 50). There is no sort, `since` or `from` parameter. Each entry has `time`, `counter`, `first_seen`, `last_seen` (unixtime), `item` and `media`. **Ordering is not documented.** `deleted` is not documented. | https://kinoapi.com/api_history.html |
| `/v1/watching/serials` returns serials with new or unfinished episodes. `subscribed=0` returns all unfinished serials, `subscribed=1` the "Буду смотреть" list. Fields: `total`, `watched`, `new`. So a fully watched serial leaves the `subscribed=0` list. | https://kinoapi.com/api_watching.html |
| `/v1/watching/movies` returns unfinished films. | https://kinoapi.com/api_watching.html |
| `watching/marktime` (`id`, `video`, `time`, `season`) answers `status`. `watching/toggle` answers `watched`. There is no endpoint for "changes since a timestamp". | https://kinoapi.com/api_watching.html |

No cheaper signal than `watching/serials` plus page 1 of history exists in the docs. The cheapest catch-up is 2 list requests plus the history pages newer than the mark.

## 6. Options

| | A. Catch-up path to the mark (recommended) | B. Loosen the existing gates | C. Server truth per item |
|---|---|---|---|
| What | A `catchUp()` beside `syncIfStale`. It fetches both watching lists and reads history from page 1 until a page holds nothing newer than `historyNewestSeen`, with no 300-page budget and no chunk pause on short runs. It runs while a full walk is in progress, through its own lock or between chunks, and does not touch `lastSyncAt`, the resume page or the prune. It is called on Main start (same slot), every resume, catalogue tab entry and player exit. It also re-checks series that left `watching/serials` while the index still has them unfinished, by fetching Details for those few ids. The full walk and the 7-day reconcile stay as they are. | Pass `maxIndexAge = ZERO` from MainVM resume, add calls from catalogue entry and player exit, and lower the floor. | For each indexed series still unfinished, fetch Details on a schedule and trust `new`/`watched`. Optionally a periodic background job. |
| Cost per trigger | Usually 3 requests (2 lists and 1 history page). A device off for a month: about 5-10 history pages. | Same while idle, but still dropped behind a running walk, still blind to page 1 during a full walk, and the in-progress gap stays. | N Details requests per pass. |
| Fixes "left the list, no new entry" | yes | no | yes |
| Trade-offs | Locking and generation stamping need care, so that a catch-up row survives the reconcile prune and does not race a user's mark. | Smallest change, but it does not reach the owner's goal on a device in the middle of a walk. | Request volume grows with the library, and it duplicates what history already gives. |
| Size | M (crosses VM, interactor and repository; sync and persistence is a risk area) | S | L |

## 7. Risks

- **History ordering is undocumented.** If `/v1/history` is not sorted by `last_seen` descending, any "stop at the first old page" rule, existing or new, misses entries. Check it on the box with read-only history pages before relying on it.
- **Network contention at start.** The catch-up must keep the `WatchState` startup slot and `awaitForeground` (`startup-settled-signal`, `startup-first-input-on-tv`).
- **Index churn.** Catch-up writes reach `settledChanges` and re-page a filtered catalogue (`ContentListInteractor.kt:77`). Writing only rows that changed keeps a run with nothing new silent; `269e9e9b` already skips no-op updates.
- **Prune.** Catch-up rows have to carry the current pass `generation` (`WatchStateRepository.kt:297`, via `currentGeneration()`), or a reconcile that finishes later deletes them (`WatchStateDao.kt:233`).
- **Logout mid-run.** The `generation`/`sessionSurvived` guard (`WatchStateSyncInteractor.kt:288-293`) must cover the new path too.
- **Series shorter than 90%.** A final episode left in the credits below 90% is never "finished" by history (`WatchCompletionPolicy.kt:18`). The player auto-mark fires at the same 90% line (`PlayerVM.kt:1946`, `:2059`), so such an exit also leaves the server unmarked, and `watching/serials` keeps the series. That is consistent rather than a lag bug. Whether KinoPub marks an episode watched from `marktime` on its own is `unverified`.
- **Root cause on the box.** Why Znieczulenie stayed visible on 192.168.1.110:5555 is `unverified`. Candidates: the 1 h gate, a reconcile walk being resumed past page 1, or the "left the list, no new entry" gap. The Settings counter and `last_sync_at` would tell them apart.

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Which triggers run the catch-up, and with what floor? | Main start (startup slot kept), every resume, Movies/Series tab entry, player exit. A shared 60 s floor; none for player exit. |
| Q2 | Does the catch-up run while a full or reconcile walk is in progress? | Yes. It reads from page 1 to the mark, between the walk's chunks or under its own lock, and leaves the walk's resume page and prune alone. |
| Q3 | Should series that left `watching/serials` but are unfinished in the index get a Details refetch in this run? | Yes. It is the cross-device case behind the report, and it costs a few requests only when a series leaves the list. |
| Q4 | Change the 90% rule for a final episode left in the credits? | No. It would be a separate idea run, since the server is not marked watched either in that case. |
| Q5 | Use `perpage=50` for the sync walks? | Yes for the catch-up and the full walk; it is 2.5x fewer requests. The History screen keeps its own page size. |

## 9. Sources

- Code: `WatchStateSyncInteractor.kt`, `WatchStateRepository.kt`, `WatchStateSyncEntity.kt`, `WatchStateDao.kt`, `WatchCompletionPolicy.kt`, `HistoryRequest.kt`, `HistoryPageResponse.kt`, `KinoPubApiClient.kt:376-463`, `Models.kt:164-183`, `:264-287`, `MainVM.kt`, `HistoryVM.kt`, `DeviceSettingsVM.kt`, `ItemDetailsRepository.kt`, `PlayerInteractor.kt`, `PlayerVM.kt`, `DetailsVM.kt`, `ContentListInteractor.kt`, `StartupSettled.kt`
- Commits: `03c2ca31`, `d051c873`, `b64e2ea5`, `5cd86940`, `269e9e9b`, `eb6c9b16`, `08271680`
- Runs: `collection-watched-mark-lag`, `card-progress-watching-duration`, `resume-progress-anomaly`, `startup-settled-signal`
- Specs: `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`
- URLs: https://kinoapi.com/api_history.html, https://kinoapi.com/api_watching.html

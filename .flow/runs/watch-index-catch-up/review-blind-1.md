LENS: blind  ROUND: 1  BASE: e4c3b5aa

### F1 [minor] A series refreshed once this session is never checked again, even after it re-enters and leaves the watching list
- Where: app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt (`refreshSeriesThatLeftTheList`, `detailsCheckedIds`)
- Problem: an id goes into `detailsCheckedIds` before its refresh runs. It stays there until `invalidate()`, which in practice means for the life of the process. A failed refresh also leaves the id marked, so it is never retried. Nothing removes an id when the series shows up in the watching list again.
- Failure scenario: the TV app stays alive for days. Series X is unfinished in the index and not in the watching list, so the first catch-up refreshes it and marks it checked. Later the user resumes X on a phone, and X reappears in `stillListed`. The user then finishes it there, and X drops out of the list again. Every later catch-up skips X because of `itemId !in detailsCheckedIds`, so the index shows it unfinished until the app is restarted. A refresh that failed once on a network error is also never retried.
- Fix: at the start of each run, remove the ids in `stillListed` from `detailsCheckedIds`. Add an id only after `itemDetails.refresh` succeeds, or remove it again in `onFailure`.

### F2 [minor] The catch-up's history stop rule always fetches one extra page when there is anything new
- Where: WatchStateSyncInteractor.kt `readHistoryNewerThan`, `val caughtUp = response.items.isNotEmpty() && response.items.all { it.lastSeenOrZero() <= mark }`
- Problem: under newest-first order, a page that holds any entry at or below the mark already contains the boundary, but the loop stops only when every entry on the page is old.
- Failure scenario: a player exit with one new entry on page 1 requests page 2 and filters it to nothing; every catch-up that finds something new pays a wasted request.
- Fix: `val caughtUp = response.items.any { it.lastSeenOrZero() <= mark }`, keeping the `pagination.current >= total` exit.

### F3 [minor] The catch-up's history loop has no page cap and does not check that the page advances
- Where: WatchStateSyncInteractor.kt `readHistoryNewerThan` (`while (true)` … `page = response.pagination.current + 1`)
- Problem: no page cap, no pause, no foreground check; all newer entries held in memory and written in one transaction. A server returning a non-advancing `pagination.current` with a larger `total` loops forever.
- Failure scenario: a TV not opened for months reads every newer page in one burst at the startup slot.
- Fix: cap the pages per catch-up (on hitting it, return without raising the mark and leave the rest to the walk), and stop when `pagination.current < page`.

### F4 [minor] On a cold start with a stale index, the watching lists and the first history page are fetched twice in a row
- Where: app/src/main/java/com/kino/puber/ui/feature/main/vm/MainVM.kt `syncWatchState` (`catchUp(respectFloor = true)` then `syncIfStale()`)
- Problem: when the index is older than `staleAfter`, the walk refetches both watching lists and history page 1 right after the catch-up.
- Failure scenario: every cold start more than an hour after the last sync sends about three duplicate requests in the startup slot.
- Fix: skip the catch-up when `syncIfStale` is about to run a walk, or run the catch-up after the walk.

### Unproven
- `readHistoryNewerThan` strict `> mark` filter: an entry with `last_seen` equal to the stored mark that arrived after the mark was taken is skipped for good, if the server can store two entries in the same second.
- The stop rule assumes newest-first order; real if `ordered=false` shows on the device.
- `MAX_PER_PAGE = 50` as "the server's largest page": real if the server caps `perpage` below 50 while reporting `total` in its own page size.

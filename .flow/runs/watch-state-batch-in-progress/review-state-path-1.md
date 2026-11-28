LENS: state-path  ROUND: 1  BASE: 8c3117ea

No findings.

The diff changes no ViewModel, `UIAction` or composable, so there is no action path or focus path to trace. I traced the one write path it does change:

- **Sync paths:** `recordInProgress` is called from the catch-up (`/home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:239`) and from the full sync (`:394`). Neither caller wraps it in another transaction.
- **Order of writes:** it runs after `recordFromServer` and before `refreshSeriesThatLeftTheList` and `pruneStaleRows`, with a `sessionSurvived` check after each step. That order is unchanged. Neither later write can roll back the in-progress rows now committed in one transaction.
- **Generation read:** `currentGeneration()` now happens inside the same transaction as the writes. A reconciliation that starts a new pass can no longer slip in between the read and the writes, so the change narrows a sequencing gap rather than opening one.
- **Failure halfway through the list:** the whole list now rolls back. The exception still reaches the caller as before, so `pruneStaleRows` (`:403`) and the cursor stamp still do not run. Nothing reads state that assumed earlier rows were kept.
- **Empty list:** the early return skips the generation read, which only fed the writes the loop would have made. Nothing else relied on that read.
- **Partial lists on screen:** `observeAll` collectors now get one invalidation instead of N. AC-4 checks on real Room that no collected list holds only part of the batch. No ViewModel state built from that flow depended on seeing the rows arrive one by one.

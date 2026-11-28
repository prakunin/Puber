I found nothing in the 8c3117ea..1beafa9c diff that qualifies for this lens.

LENS: edge  ROUND: 1  BASE: 8c3117ea

No findings.

### Unproven
- /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:327 mid-list failure or cancellation now rolls back the whole list instead of keeping the earlier rows. The spec accepts this because the next catch-up rewrites the rows. It would only matter if a cursor or `pruneStaleRows` came to depend on these rows being written in the same pass before the prune.
- /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:329 the same item id twice in one `/watching/movies` list. The second `markInProgress` call takes the update path, and `:updatedAt >= updated_at` holds for the equal timestamp, so it is harmless. It would only matter if duplicates were seen in real responses together with an update rule that is not idempotent.
- /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:206 `emissions.last()` depends on the invalidation arriving within `SETTLE_MILLIS`. This is the same pattern `aServerRowEqualToTheStoredOneIsNotRewritten` already uses. It would only matter if that existing test were seen to flake on the box.

The boundaries I checked all have handling:
- **Empty list:** `recordInProgress` returns before opening a transaction or reading the generation.
- **Nested transactions:** the DAO's inner `@Transaction` runs inside the repository's outer `withWriteTransaction`, as `recordHistoryPage` and `recordCatchUpPage` already do.
- **Session end:** both callers in `WatchStateSyncInteractor.kt` (lines 239 and 394) still check `sessionSurvived` after the call. `clear()` and `markLocally` running at the same time now wait for one commit rather than interleaving with the loop.
- **Local marks:** a pending local mark is still protected by `is_local_pending = 0` in `updateInProgress`.
- **Stale data:** older data is still kept out by `:updatedAt >= updated_at`.
- **Generation read:** it now happens inside the transaction, so it is consistent with the writes.

The signature is unchanged, there is no change to the stored data shape, and none of the TV, Compose, DI or string items in `.flow/roles/reviewer.md` apply to this diff.

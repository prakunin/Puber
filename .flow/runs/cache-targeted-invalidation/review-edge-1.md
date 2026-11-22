LENS: edge  ROUND: 1  BASE: 40762767

No findings.

I checked these boundaries against `40762767..e4d5dba2` and found each one handled:

- **Every place an epoch is read now holds a record** (`/home/user1/orca/workspaces/Puber/cache-targeted-invalidation/app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt`). `epochOf`, `supersedeKey` and `advanceKey` are only called inside `holding`: in `load` (which covers `readCached` and `promoteToMemory`), `markStale`, `invalidate`, `peek`, `peekAll` and `putAll`. The loader runs in the caller's own coroutine (`TypedTtlCache.getOrPut`/`load`, lines 84-101 and 214-240). A waiting caller whose leader is cancelled runs its own loader under its own hold.
- **A record removed and recreated at zero.** Epochs are only compared within one hold, and `Stamped` carries only `generation`. `supersedesWrite` only looks at changes in `write`/`supersedeWrite` since the capture, so starting from zero gives the same results as the old counters that were never cleared. Removing the `clear()` calls from `supersedeNamespace` keeps the result the same, because `namespace` is part of `destroyedSince`.
- **Release.** It runs in `finally` and does not suspend. It covers an early return from `holding` inside the `flow` builder, a collector that stops early, and cancellation. Duplicate keys go through `distinct()`. `peekAll` with an empty `missing` list does nothing.
- **Item id.** `MediaKey.itemId` is always `params.itemId` (`PlayerVM.kt:347`), so marking `params.itemId` marks the same key the old per-save call did.
- **`OnBackground` while closing.** `handleAction` drops every action except Back once `closing` is set (`PlayerVM.kt:647-652`). So no background flush job can start after `closeJob` has joined the previous one.
- **Exits that skip the flush.** The end of a film or of the last episode, Back, Back with no content and `SaveAndExit` all go through `exitPlayer`, which flushes before `router.back`. `ON_STOP` is the only thing that sends `OnBackground` (`PlayerScreen.kt:66-70`).
- **Failed saves and failed Watched changes** leave both flags unset. The `WatchedDetailsRefreshException` path still sets the home-refresh flag. A failed mark is swallowed by `runCatchingCancellable`, so it cannot block exit.

### Unproven
- `/home/user1/orca/workspaces/Puber/cache-targeted-invalidation/app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1500` The user presses Home and returns before a slow save or Watched write finishes. The background job then flushes while the player is in front again: one mark and one refresh, against AC-2's zero in front. It would only be real with evidence that a save regularly takes longer than a Home-and-back trip on this account or network.
- `/home/user1/orca/workspaces/Puber/cache-targeted-invalidation/app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:203` The `load` hold also stays in place while `emit(cached)` runs the downstream collector. A collector that suspends for a long time would keep the record, which is extra memory, not wrong behaviour. It would only be real if some collector of a `CachedFeed.load` flow suspends for a long time inside its first emission.

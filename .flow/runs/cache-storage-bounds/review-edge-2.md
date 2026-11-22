LENS: edge  ROUND: 2  BASE: a22c03bc

No findings.

### Unproven
- /home/user1/orca/workspaces/Puber/cache-storage-bounds/app/src/main/java/com/kino/puber/data/di/modules.kt:111 The trim is now the first thing to call `get<Cache>()`, so the `SimpleCache` gets opened about 60 s after every cold start, even in a session that never plays anything. Before this change it was opened only once a player was built. This would matter only if a device check showed real startup or IO cost from it, or a lock clash on the cache folder. Koin's `single` makes sure only one instance exists.
- /home/user1/orca/workspaces/Puber/cache-storage-bounds/app/src/main/java/com/kino/puber/data/cache/MediaCacheTrim.kt:27 No unit test covers `trimMediaCache(cache, cap)` itself, the part that lists the cached spans and removes them. Only the pure `spansToEvict` is tested. This becomes a real gap only if the device check (M-row) does not show the on-disk cache shrinking from more than 256 MB down to the cap. Spans that vanish between listing and removing are safe, because `SimpleCache.removeSpan` does nothing for a span that is already gone.

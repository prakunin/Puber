LENS: edge  ROUND: 1  BASE: 4db1b061

No findings.

### Unproven
- app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:446-447 (and 468-473): the ownership check before the fallback write and the write itself are not atomic. `holding` only counts holders; it is not a lock. Say a newer `putAll` moves the epoch and lands its upsert while the fallback `store.write` is suspended, and the fallback upsert runs after it. The older card-only fallback then replaces the newer row. The post-write `supersedesWrite` check does not catch this, because it is a plain write and not a destroy. Base has the same window for the main write, and the result is still a card-only row with no `detailsUpdatedAt`. It becomes real if a test or a device trace shows a details load and a list merge for the same oversized title running at once, with the persisted card older than the one in memory.
- app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:508-513: `cardOnly()` sets `videos = null`, so after a restart `toWatchStateOrNull` and `moviePosition` lose a movie's own watch fields from `videos[0]`. Only a movie record over 1 MiB reaches this. It becomes real if a non-series title over 1 MiB is ever seen in `cached_payload`.

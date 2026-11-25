LENS: blind  ROUND: 1  BASE: 8e005395

### F1 [minor] Every cache miss now runs a DELETE on the read path
- Where: app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt:62-79
- Problem: `read` calls `dao.deleteOversized(listOf(key), …)` whenever `dao.read` returns null. `readAll` does the same for every missing key. A miss is usually just a key that was never stored, so lookups that used to only read now open a write transaction each time. A write can fail in ways a read cannot (database locked, disk full, read-only database), and that exception now comes out of `read`/`readAll` into `CachedFeed.readUsable`/`peekAll`, which have no `runCatching`. Before this change those calls could only fail on a read error.
- Failure scenario: on a cold start with an empty or partial cache, `peekAll` over a home row's item keys and one `peek` per details key each issue a DELETE that matches no rows. The cost is one serialized write per miss. If one of those writes throws, a lookup that should return "not cached" fails instead.
- Fix: delete only when a row exists but was filtered out. For example, have `readAll` also select `key` for rows whose payload is over the limit (`SELECT key FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) > :maxBytes`) and call `deleteOversized` only when that list is non-empty. Or wrap the cleanup in `runCatching` so it can never fail the read.

### F2 [minor] `refuse` encodes the whole oversized payload just to log its size
- Where: app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt:115-119
- Problem: `payload.encodeToByteArray().size` allocates a byte array of at least the payload's size, which is more than 1 MiB by definition and several MiB in the case that caused this run. It does this only to fill a log line. That goes against the stated purpose of `utf8Size` ("Counts without encoding, so it does not allocate"), and it does it on exactly the payloads that are too big, on a low-memory TV box.
- Failure scenario: an item-details payload of about 3 MiB is refused. `refuse` allocates about 3 MiB more (more for Cyrillic text) on top of the String already in memory, which adds GC pressure or risks OOM on the box.
- Fix: log `payload.length` (chars) or just the limit. Or log the capped count that `utf8Size` already computed in `isOversized` by passing it in.

### Unproven
- PersistentPayloadStore.kt:117: `Timber.w` logs the raw cache key. That would matter if `queryKey(key)` can contain user-entered text such as a search query, and a Timber tree is planted in release builds.
- PersistentPayloadStore.kt:91-104: a refused write for a key deletes that key's row with no ordering against a concurrent small write to the same key, so the small value can be lost. It would matter if two producers can write the same key at the same time, one of them over 1 MiB.

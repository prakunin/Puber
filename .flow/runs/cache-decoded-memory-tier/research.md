# Research: cache-decoded-memory-tier

## Request

"Typed values decoded once per load in the memory tier, decode off Main, plain JSON in Room behind a namespace bump, watch_state written without a full-table re-read". Split item B1 of cache-layer-rework (`.flow/runs/cache-layer-rework/brief.md`: Split row 3, AC-5, AC-6, AC-8, AC-9, Risks).

## Understanding

The content cache keeps every record as a JSON string, in memory and in Room (where the string is JSON-encoded again), so one card is decoded two to four times per Home or Details load. All of that decoding, plus the network response decode, runs in the collector's context, which is Main for HomeVM and DetailsVM. The run should hold decoded records in the memory tier, store each record once as plain JSON under a new key namespace (old rows are dropped, not migrated), move the work off Main, and stop a Details open from rebuilding the whole watch-state map. Base: master e9c383d0. Every line below was re-read there.

## Current state in code

Paths are under `app/src/main/java/com/kino/puber/`.

### Representation and decode count

| Location | Now |
|---|---|
| `data/cache/ContentCacheRepository.kt:32-43` | The only `CachedFeed` in the app (`rg "CachedFeed("`): `String.serializer()`, 16 MB tier (`:589`), weigher `length * 2` (`:42`). Namespace `content:v1:` (`:586`, keys `:591-593`). |
| `data/cache/CachedFeed.kt:210,360,567` | Encodes on `load` and `putAll`, decodes in `decodeUsable`. With `V = String`, Room holds a JSON string literal of the record's JSON. |
| `ContentCacheRepository.kt:408-421` | `queryNeedsRefetch` decodes the list record (`:414`), then every card through `readItemRecords` (`:420`), only to count them. |
| `ContentCacheRepository.kt:443-454` | `readItemRecords` decodes each card (`:449`) on every call. `peekAll` promotes cards to memory (`CachedFeed.kt:307-332`), but memory still holds strings. |
| `ContentCacheRepository.kt:63-73,100-110,165-185` | List load path: `mergeItems` re-reads (decodes) the existing cards and re-encodes them (`:363,367`); the emit decodes the list again (`:69,105,180`) and the cards again (`readItems`, `:394-397`). |
| `ContentCacheRepository.kt:210,221-222,230` | Details: decoded before the load, again inside the loader, and again on each emit. |
| `ContentCacheRepository.kt:272,277,281` | Payloads: `discardUndecodablePayload` decodes once to validate (`:423-427`), then each emit decodes again. Exists only because the feed stores strings (comment `:268-271`). |
| `ContentCacheRepository.kt:328-344` | `markItemDetailsStale` / `invalidateItemDetails` decode, copy and re-encode the record. |

Per card in a cached list load: two decodes when fresh, up to four plus the API decode and two encodes when stale (count derived from the paths above, not measured).

### Thread

| Location | Now |
|---|---|
| `data/api/KinoPubApiClient.kt:1017-1035` | `apiCall` calls `response.body<T>()` in the caller's context. The Ktor 3.6.1 converter buffers the body and decodes without a dispatcher switch (External findings). |
| `data/cache/`, `domain/interactor/home`, `domain/interactor/details` | No `flowOn` or `withContext` (`rg`). Only Room hops to IO (`data/db/PuberDatabase.kt:39`). |
| `core/ui/PuberVM.kt:108-116` | `launch` is `viewModelScope.launch`, which runs on Main. |
| `ui/feature/home/vm/HomeVM.kt:331-346,349-391` | Seven section collectors plus collections. Card mapping already hops to `presentationDispatcher` (Default, `:62,502`); collection and decode stay on Main. |
| `ui/feature/details/vm/DetailsVM.kt:98-106,148-160` | Details and similar collectors run on Main. |
| `domain/interactor/prefetch/DetailsPrefetcher.kt:41`, `core/startup/AppStartup.kt:38` | Prefetch and the startup warm-up already run off Main (Default, IO). |
| Precedents for an injectable dispatcher | `HomeVM.kt:62`, `data/repository/EpisodeScheduleRepository.kt:29,46`, `core/paginator/PagingVM.kt:25`. |

### watch_state

| Location | Now |
|---|---|
| `data/repository/WatchStateRepository.kt:49-51` | `snapshot` = `dao.observeAll()` (`SELECT * FROM watch_state`, `data/db/WatchStateDao.kt:33-34`) mapped to a full map, `stateIn(Eagerly)` on an IO scope (`:46`). Each committed write re-runs the query and rebuilds the map. `stateIn` drops equal maps, so `onIndexChanged` (`:144-164`) does not tick. |
| `data/repository/ItemDetailsRepository.kt:17-28` | Every non-stale Details value calls `recordFromServer` with `observedAt = emission.updatedAt`. |
| `WatchStateDao.kt:63-80,129-156` | `insertIfAbsent` (IGNORE), then an UPDATE guarded by `is_local_pending = 0 AND :updatedAt >= updated_at`. Re-reading a cached Details value passes the same `updatedAt`, so `>=` holds and an identical row is rewritten, which invalidates the table. |
| `WatchStateRepository.kt:121-135` | The constructor starts `clearAllPending()` on the IO scope. `markLocally` waits for it (`:119,304`). |
| `domain/interactor/watchstate/WatchStateSyncInteractor.kt:494-501` | The sync walk writes in chunks of 15 pages because each transaction re-runs the snapshot query. |
| `domain/interactor/home/HomeInteractor.kt:165` | Home filters with `isFullyWatched`, which reads `snapshot.value`, so the snapshot has to be warm before Home publishes. |

### Tests

Paths are under `app/src/test/kotlin/com/kino/puber/`.

| Test | Relevance |
|---|---|
| `data/cache/CachedFeedTest.kt` (799 lines) | Race invariants; uses `CachedFeed<String>` (`:27,615`). |
| `data/cache/ContentCacheRepositoryTest.kt` | `:237-244` writes a stored payload in the string-in-string form, so it changes with the storage form. `:249-266` covers legacy prefixes. |
| `data/repository/WatchStateRepositoryTest.kt` | `FakeWatchStateDao` (`:653-667`) models the query re-running after a write. The pending and timestamp SQL guards are not exercised (`:650`). |
| `domain/interactor/home/HomeInteractorCacheTest.kt`, `ui/feature/details/vm/DetailsVMCachedLoadTest.kt`, `data/repository/ItemDetailsRepositoryTest.kt`, `util/FakePayloadStore.kt` | Cached-load contracts (AC-8). |

## Related work

| Run / commit | Relation |
|---|---|
| cache-layer-rework (split) | Parent: scope, AC-5/6/8/9, risks. |
| home-decode-off-main (dropped) | Folded into this run (its `state.md` log, 2026-11-21); content is the Thread table above. |
| startup-main-thread-io (idea) | Its WatchStateRepository item: see below. |
| cache-targeted-invalidation, B4 (idea) | Touches `CachedFeed.kt:95-131,406-438` epoch maps; never run in parallel with this run. |
| cache-storage-bounds, B3 (done, d52b5800) | Room v4, `LegacyPayloadCleanup` auto-migration (`data/db/LegacyPayloadCleanup.kt`), sweeper with a 7-day age and a 2,000-row cap (`data/cache/CachedPayloadSweeper.kt:65-75`). |
| cache-shared-http-stack, B2 (done, 4f72f028, a90c6a8e) | No overlap with decode. |
| startup-splash-preload, startup-settled-signal (done) | `AppStartup` warm-up drains Hot and Watching through the same cache (`AppStartup.kt:59-67`); `StartupSettled` (`core/startup/StartupSettled.kt:26-55`) is available for deferral. |
| ktor-readremaining-deprecation (idea) | Ktor logging body path only; no overlap. |
| 264748e7, 6be75d28, b99a8fcf, d8682b3c | Byte-bounded tier, lifecycle races, refresh after wipe, bulk ops. Race invariants: `CachedFeed.kt:85-166,340-392`. |
| Specs `docs/superpowers/specs/2026-08-13-persistent-content-cache-design.md`, `2026-08-17-content-list-swr-cache-design.md` | Original design of the normalised card plus list store. |

Probable duplicate: none.

### startup-main-thread-io, WatchStateRepository item

The audit item (`startup-splash-preload/perf-audit-2026-11-21.md` item 1) is partly overtaken:

- **Resolution on Main is gone.** `PuberApp.kt:99` now resolves only `AppStartup`. It takes interactor providers and builds them on IO (`AppStartup.kt:20-48`), so `WatchStateRepository` is built on the warm-up path through `HomeInteractor` (`domain/di/modules.kt:99`), not on Main at `Application.onCreate`.
- **I/O contention remains.** Construction still starts the full read and `clearAllPending` at once (`WatchStateRepository.kt:49-56,121-135`), on IO, during the Home warm-up.
- **The full read cannot be deferred.** Home needs a warm snapshot before its first publish (`HomeInteractor.kt:165`). Only `clearAllPending` could move, for example to a `StartupSettled` slot; `markLocally` already waits for it.

Evidence for taking the item here: both items edit the same lines (`WatchStateRepository.kt:49-56,121-135`) and the same test file. The remaining work is small (moving one write). Prefs reads, StrictMode and largeHeap stay in startup-main-thread-io.

## External findings

| Finding | Source |
|---|---|
| Ktor 3.6.1 `KotlinxSerializationConverter.deserialize` reads the whole body (`readBuffer()`) and then calls `decodeFromString`. It does not switch dispatcher, so the decode runs in the caller's context. | https://github.com/ktorio/ktor/blob/3.6.1/ktor-shared/ktor-serialization/ktor-serialization-kotlinx/common/src/io/ktor/serialization/kotlinx/KotlinxSerializationConverter.kt |
| SQLite supports only FOR EACH ROW triggers, which fire per row inserted, updated or deleted. An UPDATE whose WHERE matches no row, or an INSERT OR IGNORE that is ignored, fires nothing. A matched UPDATE fires even when the values are unchanged. Room's invalidation is trigger-based; that Room 3.0.3 still works this way is `unverified`. | https://www.sqlite.org/lang_createtrigger.html |

Versions: Ktor 3.6.1, kotlinx.serialization 1.13.0, coroutines 1.11.0, Room 3.0.3, Kotlin 2.4.21 (`gradle/libs.versions.toml:3,15,16,18,33`).

## Options

Common to A and B: the namespace becomes `content:v2:`. v1 rows are never read again, sort oldest and fall to the sweeper's age and row cap, so Room needs no schema change. Decode moves off Main through an injected dispatcher (Default) applied with `flowOn` to the public `ContentCacheRepository` flows, and with `withContext` to `getPayload` and the one-shot reads, which covers the loader's `body<T>()`. The `flowOn` buffer is a timing change for the Home and Details VM tests.

| Option | Content | Trade-offs | Size |
|---|---|---|---|
| **A. One typed feed per record type (recommended)** | `CachedFeed<V>` stays generic. `ContentCacheRepository` builds feeds for `ItemRecord`, list, page and history records, plus one per payload key (genres, bookmark folders, collections), all on one store. Each read decodes once from Room and serves decoded values from memory after that. The weigher uses the encoded JSON length, which needs `Stamped` to carry a weight known at encode or read time (small `CachedFeed` change). watch_state: `upsertFromServer` skips rows whose values equal the stored ones, so re-opening Details invalidates nothing. | Race logic and `CachedFeedTest` untouched. Per-feed epoch maps are fine because keys never cross feeds. The memory budget is split between feeds. `invalidateQuery` (`:346-349`) routes to two feeds. | M |
| B. One feed, serializer per call | `CachedFeed<Any>`; `load`, `put` and `peek` take a `KSerializer`. Same watch_state change as A. | One memory budget, but `CachedFeed`'s API and every `CachedFeedTest` call change; the type safety is cast-based. | M |
| C. A plus an incremental watch_state snapshot | The snapshot is seeded once, then each write re-reads only the ids it touched (`SELECT … WHERE item_id IN`) and patches the map. `clearAllPending` moves to a `StartupSettled` slot (startup-main-thread-io item). | Also removes the full re-read per sync chunk (`WatchStateSyncInteractor.kt:494-501`). But `onIndexChanged` relies on Room publishing after commit (`WatchStateRepository.kt:138-143`), and prune, clear and revert need their own paths. Larger `WatchStateRepositoryTest` rework. | M-L |

Recommended: A. Also move `clearAllPending` to a `StartupSettled` slot (a one-line move, AC-tested); the incremental snapshot stays out.

## Risks

| Risk | Guard |
|---|---|
| Typed feeds reopen superseded-write races (6be75d28) | `CachedFeed` logic unchanged under A; `CachedFeedTest` green unchanged (AC-8); B4 not in parallel. |
| Decoded values in memory are shared objects; a caller that mutates one corrupts the cache | `Item` and the records are data classes with `val`s (`ContentCacheRepository.kt:531-581`); confirm no `var` or mutable collection in `data/api/models` (`unverified`). |
| A wipe misses a new feed | Every feed checks `store.generation` (`CachedFeed.kt:83,467-473`); one test per feed type after `clear()`. |
| Skipping an equal watch_state UPDATE leaves `updated_at` older; a slower sync observed between the two reads, with different values, could then overwrite the row | Narrow window (server change between two fetches plus out-of-order landing); pending rows stay protected by `is_local_pending`. Open question 2. |
| Weigher drift: a decoded `Item` costs more heap than its JSON length | Keep the 16 MB cap and measure heap on device (AC-9). |
| Upgrade over an installed v1 build | AC-6: device install-preserve over the current APK; v1 rows ignored and refetched once. |
| `flowOn` reorders or buffers emissions seen by Home publish coalescing (e6b42269) | `HomeVMIncrementalPublishTest` and the HomeVM tests in the full check. The `unit-suite-flaky` run already lists one of them as flaky. |

## Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Shape of the typed tier: a feed per record type (A) or one feed with per-call serializers (B)? | A. |
| 2 | Is it acceptable that a watch_state write with unchanged values does not advance `updated_at`? | Yes, for `upsertFromServer` only. Leave the history and in-progress paths as they are. |
| 3 | Take startup-main-thread-io's WatchStateRepository item here? | Partly: move `clearAllPending` to a `StartupSettled` slot here and keep the eager snapshot read, which Home needs. Record in startup-main-thread-io that the item is done and keep its prefs and StrictMode items. |
| 4 | Remove v1 rows actively (Room v5 auto-migration like `LegacyPayloadCleanup`) or leave them to the sweeper? | Sweeper: v1 rows are older than every v2 row, so the 2,000-row cap and the 7-day age remove them first, with no schema bump. |

## Sources

- Files: `AGENTS.md`; `docs/architecture/api.md`; `gradle/libs.versions.toml`; every code and test path cited above.
- Runs: cache-layer-rework (`brief.md`, `research.md`), startup-main-thread-io, home-decode-off-main, cache-targeted-invalidation, cached-feed-key-maps, cache-storage-bounds, cache-shared-http-stack, startup-splash-preload (`state.md`, `perf-audit-2026-11-21.md`), startup-settled-signal (`brief.md`), ktor-readremaining-deprecation, unit-suite-flaky, perf-deferred (`state.md` each).
- Commits: e9c383d0 (base), d52b5800, b161b061, 4f72f028, a90c6a8e, e6b42269, 264748e7, 6be75d28, b99a8fcf, d8682b3c.
- URLs: https://github.com/ktorio/ktor/blob/3.6.1/ktor-shared/ktor-serialization/ktor-serialization-kotlinx/common/src/io/ktor/serialization/kotlinx/KotlinxSerializationConverter.kt, https://www.sqlite.org/lang_createtrigger.html

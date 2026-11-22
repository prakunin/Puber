# Design (architect): cache-decoded-memory-tier

Base e9c383d0. Paths under `app/src/main/java/com/kino/puber/` unless named otherwise. Inputs: `brief.md`, `research.md`, `state.md` Decisions (Q1 A, Q2 yes, Q3 sweeper).

## Decisions

| Question | Decision | Why |
|---|---|---|
| Feed set (Q1 option A) | `ContentCacheRepository` holds: one `CachedFeed<ItemRecord>`; three query feeds `CachedFeed<ItemListRecord>`, `<ItemPageRecord>`, `<HistoryPageRecord>`; payload feeds created lazily, one per payload store key, in a `ConcurrentHashMap<String, CachedFeed<*>>` | Each key has exactly one type; a per-key payload feed needs no serializer-equality reasoning, and payload keys are few (`genres:*`, `bookmark-folders`, `collections:1`, `home:collections`) |
| Prefix | `CONTENT_PREFIX = "content:v2:"` (`ContentCacheRepository.kt:586`); key builders `:591-593` unchanged otherwise | AC-3, AC-4; v1 rows never read, left to the sweeper (Q3) |
| Weight carried with the entry | `CachedFeed` encodes once per write and once per Room read already; `Stamped` and `Usable` gain `encodedLength: Int` (the JSON string's `length`). Constructor param `memoryWeigher: (V) -> Int` becomes `memoryWeigher: (encodedLength: Int) -> Int = { 1 }` | No re-encode to weigh; `CachedFeedTest` never passes a weigher (`rg memoryWeigher app/src/test`: no hit), so its assertions stay unchanged (AC-9) |
| Weigher and cap | Every content feed: `memoryWeigher = { it }`, `maximumMemoryWeight = MEMORY_TIER_BYTES` (16 MB, `:589`), default count cap 1024 | AC-5. Only the item feed holds large values (details trees) |
| Encode in `load` | Move `json.encodeToString(serializer, value)` (`CachedFeed.kt:210`) above the epoch check (`:206-207`), so a discarded flight also knows its weight | One encode per flight, as today in the common path |
| Query-row read before a load | `queryNeedsRefetch` (`ContentCacheRepository.kt:408-421`) reads the query row with `peekAll(listOf(key))` instead of `peek` (`:413`) | `peek` does not promote (`CachedFeed.kt:287-293`), so the following `load` would decode the row a second time; `peekAll` promotes pinned to `HardCeiling`, which `readCached` (`:505-512`) and `loadFresh` (`:486-499`) already judge by the stamp, the same path card rows take today. Gives AC-1's exactly-once without touching `CachedFeed` logic |
| Undecodable rows | Delete `discardUndecodablePayload` (`:423-427`), `decodeOrDiscard` (`:430-437`), `decode` (`:529`), `encode` (`:527`) and the `runCatching` around record decodes (`:414-417`, `:449-451`) | With typed feeds `CachedFeed.decodeUsable` (`CachedFeed.kt:559-574`) is the only decode site and already drops an undecodable row |
| Decode dispatcher | Constructor param `decodeDispatcher: CoroutineDispatcher = Dispatchers.Default`; `.flowOn(decodeDispatcher)` on `observeItems`, `observeItemPage`, `historyFirstPage`, `observeItemDetails`, `observePayload`; `withContext(decodeDispatcher)` around public `mergeItems` (`:309-320`), `markItemDetailsStale` (`:328-335`), `invalidateItemDetails` (`:337-344`) | `sectionPage`, `watchlist`, `observeSimilarItems` delegate to a flowOn'd flow; `getPayload` (`:291-307`) collects `observePayload`, so the loader and its `body<T>()` (`KinoPubApiClient.kt:1017-1035`) run on the dispatcher (AC-2). Precedent: `HomeVM.kt:62` |
| DI for the dispatcher | `data/di/modules.kt:97` unchanged (default is Default) | Smallest change; tests inject |
| `invalidateQuery` (`:346-349`) | `invalidate(queryKey(key))` on each of the three query feeds; payload: `payloadFeeds[payloadKey(key)]?.invalidate(...) ?: store.remove(payloadKey(key))` | A key's owner feed is unknown at this call; a non-owner's `invalidate` only bumps its own counters and repeats a no-op delete |
| `clear()` (`:351-353`) | Unchanged (`store.clear()`) | Every feed drops its tier on a generation move (`CachedFeed.kt:467-473`, `:545-550`); AC-6 tests each feed |
| watch_state no-op predicate | Add to `upsertFromServer`'s WHERE (`WatchStateDao.kt:140-142`): `AND (is_series_like IS NOT :isSeriesLike OR is_fully_watched IS NOT :isFullyWatched OR watched_episodes IS NOT :watchedEpisodes OR total_episodes IS NOT :totalEpisodes OR progress_time IS NOT :progressTime OR progress_duration IS NOT :progressDuration OR updated_at IS NOT :updatedAt OR generation IS NOT :generation)` | `IS NOT` on every column, nullable or not, so no comparison can yield NULL; supported by the older SQLite on Fire OS. `upsertFromHistory` and `updateInProgress` unchanged (brief) |
| `clearAllPending` deferral (Q2) | `WatchStateRepository` gains `awaitStartupSettled: suspend () -> Unit = {}`; the coroutine at `WatchStateRepository.kt:125-134` calls it before `dao.clearAllPending()`, inside the same `try`/`finally`. DI `data/di/modules.kt:125` passes `get<StartupSettled>()::await` | Same wiring as `DetailsPrefetcher` (`domain/di/modules.kt:82`); `markLocally` keeps awaiting `inheritedFlagsCleared` (`:304`); the default keeps `WatchStateRepositoryTest.kt:51` construction valid |

## Changes by layer

### API / repository

| File | Change |
|---|---|
| `data/cache/CachedFeed.kt` | `:54` weigher signature; `:72` `weigher = { stamped -> memoryWeigher(stamped.encodedLength) }`; `:203-232` encode before the epoch check, write that payload, `Stamped(encodedLength = payload.length)`; `:358-372` `putAll` encodes each value once into a map, writes it, and stamps the memory entry with its length; `:573` `Usable(encodedLength = stored.payload.length)`; `:532` and `:540` carry `encodedLength` through `promoteToMemory`; `:576`, `:579-584` add the field. No change to epochs, barriers or `load` control flow |
| `data/cache/ContentCacheRepository.kt` | `:32-43` replaced by the feed set and `payloadFeed(key, serializer)` (`computeIfAbsent`, unchecked cast); every feed gets `json = json` (today `:32` does not pass `:29`); `:56-65`, `:92-101`, `:156-176`, `:216-226`, `:273-277` load typed records and loaders return records, not strings; emits read the typed `cached.value` (`:69`, `:105`, `:180`, `:230`, `:281`); `:272` call removed; `:330-342` `put` the copied record; `:365-377` `putAll` of `ItemRecord`s; `:443-454` `readItemRecords` maps `peekAll` values directly; `:586` v2; flowOn/withContext per Decisions |
| `data/db/WatchStateDao.kt` | `:140-142` predicate per Decisions; KDoc on the class (`:18-29`) gains the no-op rule |
| `data/repository/WatchStateRepository.kt` | `:41-47` constructor param; `:125-134` await before clearing; KDoc `:114-118` names the settle point |
| `data/di/modules.kt` | `:125` passes `awaitStartupSettled` |

### ViewModel state and actions

N/A: VMs keep collecting the same flows; only the upstream dispatcher changes.

### Screen and DI module, Compose content, resources

N/A: no screen, composable or resource change.

### Tests (owned by test-design; seams this design provides)

| Seam | Use |
|---|---|
| `ContentCacheRepository(decodeDispatcher = ...)` | AC-2 with a named `StandardTestDispatcher(testScheduler)` distinct from the collector's; fixtures whose loaders use virtual time or that assert after `advanceUntilIdle` pass a dispatcher on the test scheduler |
| `CachedFeed.decodeUsable` is the only decode site | Record decodes equal rows `FakePayloadStore` returns from `read`/`readAll` (`app/src/test/kotlin/com/kino/puber/util/FakePayloadStore.kt`); payload decodes via a counting `KSerializer` passed to `getPayload` |
| `WatchStateRepository(awaitStartupSettled = ...)` | AC-8 fake latch (`CompletableDeferred`) |
| `ContentCacheRepositoryTest.kt:236-247` | Still passes: `"\"not a genre list\""` fails the typed decode and is dropped; its comment changes |

## Alternatives rejected

- Option B, one `CachedFeed<Any>` with per-call serializers: changes every `CachedFeedTest` call (owner chose A).
- Payload feeds keyed by `serializer.descriptor`: relies on descriptor equality across `ListSerializer` instances; per-key is simpler.
- `load(refetchIf = ...)` inside `CachedFeed` for the incomplete-list check: edits `load` control flow that B4 and `CachedFeedTest` guard; `peekAll` achieves the same.
- Making `peek` promote: changes a documented `CachedFeed` contract (`:287-293`) under test at `CachedFeedTest.kt:46-135`.
- Weighing by re-encoding the decoded value: an extra encode per memory put.
- Moving decode off Main in HomeVM/DetailsVM collectors: seven call sites instead of one boundary.

## Risks

| Risk | Kind | Lives in | Guard |
|---|---|---|---|
| Decoded objects cost more heap than their JSON length; a 16 MB (chars) item tier can hold several times that, where the string tier held 16 MB of heap (`length * 2`, `:42`) | memory growth | `ContentCacheRepository.kt:41-42` | AC-10 heap after the sequence; open question 2 |
| `flowOn` buffers emissions and runs the loader on Default: tests that relied on Main-confined virtual time see work finish on real threads, and Home publish coalescing sees values it used to conflate (e6b42269) | concurrency | public flows of `ContentCacheRepository`; `HomeVM.kt:331-391` | AC-9; fixtures inject a test-scheduler dispatcher; `unit-suite-flaky` HomeVM flakes are re-run once before being read as a regression |
| Query row promoted by `peekAll` answers a load that should revalidate | cache invalidation | `ContentCacheRepository.kt:413`; `CachedFeed.kt:486-499`, `:505-512` | Same stamp checks already serve card rows; AC-1 stale-list case and existing `ContentCacheRepositoryTest` refetch tests |
| A no-op predicate that misses a column drops real server updates | correctness | `WatchStateDao.kt:129-156` | `IS NOT` per column; AC-7 differing-column case per column |
| A typed feed misses a wipe and serves the previous account | security / lifecycle | `CachedFeed.kt:83`, `:467-473` | AC-6, one test per record type including a payload feed |
| `invalidateQuery` before any payload feed exists must still delete the row | cache invalidation | `ContentCacheRepository.kt:346-349` | `store.remove` fallback; test on a fresh repository instance |
| Pending flags inherited from a dead process stay until settle (fallback 5 s, `StartupSettled.kt:28`); a process with no activity never settles, and `markLocally` would wait forever there | lifecycle | `WatchStateRepository.kt:125-134`, `:304`; `MainActivity.kt:79` | No worker or receiver builds the repository (`rg` for Worker/Receiver: none); `markLocally` is reached only from UI; AC-8 |
| Upgrade over an installed v1 build | rollout | `ContentCacheRepository.kt:586`; `CachedPayloadSweeper.kt:65-75` | AC-4 unit and device install-preserve |
| Baseline profile names `ContentCacheRepository` methods that change shape | performance | `app/src/main/generated/baselineProfiles/` | Profile rules on removed methods are ignored; regeneration not required for correctness |

## Open questions

1. AC-1 and AC-2 name a "counting serializer" for records, but the record serializers are private to `ContentCacheRepository` and `Json` cannot be wrapped. Accept counting rows returned by `FakePayloadStore` (each decoded once by `CachedFeed.decodeUsable`) for records, the counting serializer for payloads, and the dispatcher assertion in the fake store's `onRead` and the fake loader? Recommended: yes. Blocks: no.
2. If AC-10 shows heap growth over the current build, lower the item tier's cap (for example 8 MB) in a follow-up rather than in this run? Recommended: yes, follow-up. Blocks: no.

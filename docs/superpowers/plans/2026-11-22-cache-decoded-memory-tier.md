# Typed values decoded once in the memory tier, decode off Main, plain JSON under content:v2, no-op watch_state writes skipped - plan

**Spec:** `docs/superpowers/specs/2026-11-22-cache-decoded-memory-tier.md`
**Goal:** Each cached content record is decoded at most once per Room read and never on Main, API response bodies are decoded off Main, rows are plain JSON under `content:v2:`, a server sync that changes nothing does not rewrite `watch_state`, and inherited pending flags clear after startup settles.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in the worktree `/home/user1/orca/workspaces/Puber/cache-decoded-memory-tier`; never `./gradlew` there.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- `M/` = `app/src/main/java/com/kino/puber/`; `T/` = `app/src/test/kotlin/com/kino/puber/`; `A/` = `app/src/androidTest/kotlin/com/kino/puber/`.
- Test names, levels and what each proves: spec, Test design, Traceability. A task lists the rows it adds; it does not restate them.
- Red first (spec, Test design, Failing-first tests): each P0 `red` row runs red on the code before the task's production change. Where the row needs a new constructor parameter, add the parameter with base behaviour (the stub named in the task), run red, then implement. Record each red run in the Implementation record.
- Not touched: `CachedFeed` epochs, barriers and `load` control flow (`M/data/cache/CachedFeed.kt:95-166`, `:406-439`); `upsertFromHistory`, `updateInProgress`; the Room schema version; `CachedFeedTest` assertions. `cache-targeted-invalidation` must not run in parallel.
- `HomeVMIncrementalPublishTest` red once: re-run before reading it as a regression (`unit-suite-flaky`).

---

## Tasks

### Task 1: Weigh the CachedFeed memory tier by the encoded JSON length carried with each entry

**Covers:** AC-5, AC-9 (`CachedFeedTest` unchanged)
**Files:**
- Modify: `M/data/cache/CachedFeed.kt`
  - ctor `memoryWeigher: (V) -> Int = { 1 }` (`:54`) becomes `memoryWeigher: (encodedLength: Int) -> Int = { 1 }`; memory tier weigher (`:72`) becomes `{ stamped -> memoryWeigher(stamped.encodedLength) }`.
  - `Stamped` (`:579-584`) and `Usable` (`:576`) gain `encodedLength: Int`.
  - `load` loader body (`:203-232`): compute `val payload = json.encodeToString(serializer, value)` before the epoch check at `:206-207`, write that `payload` at `:208-212`, build `Stamped(..., encodedLength = payload.length)`.
  - `putAll` (`:347-392`): encode each value once into a map, write those strings (`:357-364`), stamp the memory entry (`:370-374`) with that string's length.
  - `decodeUsable` (`:559-574`): `Usable(value, updatedAt, encodedLength = stored.payload.length)`.
  - `promoteToMemory` (`:526-542`): carry `encodedLength` from `Usable` into `Stamped` (`:532`) and back (`:540`).
- Modify: `M/data/cache/ContentCacheRepository.kt:42` - `memoryWeigher = { length -> length * Char.SIZE_BYTES }` (same cap as today; Task 4 changes it).
- Test: `T/data/cache/CachedFeedMemoryWeightTest.kt` (new) - `aValuePastTheWeightCapEvictsTheOldestFromMemory` (P0 red). Fixture as `T/data/cache/CachedFeedTest.kt:27-33` plus `maximumMemoryWeight` and `memoryWeigher = { it }`; "reads the store again" via `FakePayloadStore.readCount` / `onRead` (`T/util/FakePayloadStore.kt:10-11`, `:22-27`). Eviction by weight lives in `M/core/collections/TypedTtlCache.kt:313`.

**Consumes:** `TypedTtlCacheImpl(maximumWeight, weigher)` (`M/core/collections/TypedTtlCache.kt:32-38`).
**Produces:** `CachedFeed(..., maximumMemoryWeight: Long, memoryWeigher: (encodedLength: Int) -> Int)`.

- [x] Stub: change the signature and fields with `encodedLength = 0` everywhere; write the test; run it red
- [x] Carry the real lengths as listed
- [x] Run the fast gate, `CachedFeedMemoryWeightTest`, `CachedFeedTest`, `ContentCacheRepositoryTest`; commit

**Done when:** `CachedFeedMemoryWeightTest` and `CachedFeedTest` pass; `git diff HEAD~1 -- T/data/cache/CachedFeedTest.kt` is empty.

---

### Task 2: Skip server watch_state updates that change no written column

**Covers:** AC-7
**Files:**
- Modify: `M/data/db/WatchStateDao.kt` - append to the WHERE of `upsertFromServer` (`:140-142`): `AND (is_series_like IS NOT :isSeriesLike OR is_fully_watched IS NOT :isFullyWatched OR watched_episodes IS NOT :watchedEpisodes OR total_episodes IS NOT :totalEpisodes OR progress_time IS NOT :progressTime OR progress_duration IS NOT :progressDuration OR updated_at IS NOT :updatedAt OR generation IS NOT :generation)`. Class KDoc (`:18-29`) gains a third rule: an update that would leave every written column equal is skipped, so Room's `observeAll` (`:33-34`) does not re-emit. Columns: `M/data/db/WatchStateEntity.kt`.
- Test: `A/data/db/WatchStateDaoTest.kt` - `aServerRowEqualToTheStoredOneIsNotRewritten` (P0 red), `aServerRowDifferingInAnyWrittenColumnLands` (P0 guard); existing `aServerRowReadBeforeTheStoredOneDoesNotLand` (`:52`) and `aPendingRowIsLeftAloneByASync` (`:60`) stay as they are (P1 guard). Row builder: `serverRow` (`:145-152`), widened with the nullable columns.

**Consumes:** `upsertAllFromServer` (`M/data/db/WatchStateDao.kt:63-80`).
**Produces:** nothing new.

- [x] Write both tests; change the SQL
- [x] Run the fast gate and `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`; commit. Running instrumented tests is qa-device's job (verify); record under Red run: `AC-7 not run on device; the base WHERE (WatchStateDao.kt:140-142) admits an equal row`

**Done when:** both compiles pass; `WatchStateDaoTest` green at verify.

---

### Task 3: Clear inherited pending watch_state flags after startup settles

**Covers:** AC-8
**Files:**
- Modify: `M/data/repository/WatchStateRepository.kt` - ctor (`:41-47`) gains `private val awaitStartupSettled: suspend () -> Unit = {}` after `scope`; the cleanup coroutine (`:125-134`) calls it inside the `try` before `dao.clearAllPending()`, so the `finally` still completes `inheritedFlagsCleared` (`:119`). `markLocally` keeps `inheritedFlagsCleared.await()` (`:304`). KDoc `:114-118` names the settle point.
- Modify: `M/data/di/modules.kt:125` - `WatchStateRepository(dao = get(), syncDao = get(), transaction = get(), awaitStartupSettled = get<StartupSettled>()::await)`, as `M/domain/di/modules.kt:82`. `StartupSettled` is a single at `M/domain/di/modules.kt:86`; `await` at `M/core/startup/StartupSettled.kt:50-52`.
- Test: `T/data/repository/WatchStateRepositoryTest.kt` - `clearAllPending_waitsForStartupToSettle`, `markLocally_completesOnlyAfterTheDeferredCleanup` (both P0 red); `repository(scope)` (`:51-57`) gains an `awaitStartupSettled` argument defaulting to `{}`; `FakeWatchStateDao.clearAllPending` (`:806-808`) counts its calls. Existing `markLocally_isNotStrippedByTheCleanupQueuedAtStartup` (`:271-293`) unchanged (P0 guard).
- Test: `T/data/di/WatchStateRepositoryModuleTest.kt` (new) - `repositoryFromTheModuleDefersCleanupUntilStartupSettles` (P1 red). Pattern: `T/data/di/EpisodeScheduleRepositoryModuleTest.kt:15-28`, with `repositoryModule` first and an override module after it: `WatchStateDao` and `WatchStateSyncDao` mockk with `observeAll()` / `observe()` returning `flowOf(...)`, `DatabaseTransaction.Direct` (`M/data/db/DatabaseTransaction.kt:16`), `StartupSettled` from `unsettledStartup(scope)` (`T/core/startup/StartupSettledTestSupport.kt:6`). The repository's default scope is `Dispatchers.IO`, so assert the clear with `coVerify(timeout = ...)` after `onActivityCreated(restored = true)` and `coVerify(exactly = 0)` before it.

**Consumes:** `StartupSettled.await()`.
**Produces:** `WatchStateRepository(..., awaitStartupSettled: suspend () -> Unit = {})`.

- [x] Stub: add the parameter, unused; write the three tests; run red
- [x] Await it in the cleanup; wire DI
- [x] Run the fast gate, `WatchStateRepositoryTest`, `WatchStateRepositoryModuleTest`; commit

**Done when:** both test classes pass.

---

### Task 4: Hold decoded records in one CachedFeed per record type, stored as plain JSON under content:v2

**Covers:** AC-1, AC-3, AC-4 (unit), AC-5 (repository cap), AC-6, AC-9 (`ContentCacheRepositoryTest.kt:235-267`)
**Files:**
- Modify: `M/data/cache/ContentCacheRepository.kt`
  - Replace `cache` (`:32-43`) with `itemFeed: CachedFeed<ItemRecord>`, `listFeed: CachedFeed<ItemListRecord>`, `pageFeed: CachedFeed<ItemPageRecord>`, `historyFeed: CachedFeed<HistoryPageRecord>` and `payloadFeeds = ConcurrentHashMap<String, CachedFeed<*>>()` with `private fun <T : Any> payloadFeed(storeKey: String, serializer: KSerializer<T>): CachedFeed<T>` (`computeIfAbsent`, `@Suppress("UNCHECKED_CAST")`). Every feed: `store`, `ttl = CachedFeed.HardCeiling`, `keyPrefix = CONTENT_PREFIX`, `json = json` (ctor `:29`), `clock`, `maximumMemoryWeight = MEMORY_TIER_BYTES` (`:589`), `memoryWeigher = { it }`.
  - `observeItems` (`:49-83`) on `listFeed`, `observeItemPage` (`:85-122`) on `pageFeed`, `historyFirstPage` (`:151-203`) on `historyFeed`, `observeItemDetails` (`:205-247`) on `itemFeed`, `observePayload` (`:260-289`) on `payloadFeed(storeKey, serializer)`: loaders return the record (no `encode`); `Cached.Value` branches read `cached.value` directly instead of `decodeOrDiscard` (`:69`, `:105`, `:180`, `:230`, `:281`); delete the `discardUndecodablePayload` call and comment (`:268-272`).
  - `queryNeedsRefetch` (`:407-421`): take the typed feed instead of a serializer and read with `feed.peekAll(listOf(key))[key]` instead of `peek` (`:413`); drop the `runCatching`/`invalidate` (`:414-417`). Reason: `peek` does not promote (`M/data/cache/CachedFeed.kt:287-304`), `peekAll` does (`:307-332`).
  - `markItemDetailsStale` / `invalidateItemDetails` (`:328-344`): `itemFeed.put(..., value = current.copy(...), supersede = true)`.
  - `invalidateQuery` (`:346-349`): `invalidate(queryKey(key))` on `listFeed`, `pageFeed`, `historyFeed`; then `payloadFeeds[payloadKey(key)]?.invalidate(payloadKey(key)) ?: store.remove(payloadKey(key))`.
  - private `mergeItems` (`:355-379`): `itemFeed.putAll` of `ItemRecord`s; `readItemRecords` (`:443-454`): `itemFeed.peekAll` values mapped directly.
  - Delete `discardUndecodablePayload` (`:423-427`), `decodeOrDiscard` (`:429-437`), `encode`/`decode` (`:527-529`) and the now-unused `String.serializer` import (`:14`).
  - `CONTENT_PREFIX = "content:v2:"` (`:586`).
  - `clear()` (`:351-353`) unchanged: each feed drops its tier on a generation move (`M/data/cache/CachedFeed.kt:467-473`, `:545-550`).
- Test: `T/data/cache/ContentCacheRepositoryTest.kt`
  - New: `aListServedFromTheMemoryTierReturnsTheSameDecodedCards`, `detailsServedFromTheMemoryTierAreNotDecodedAgain`, `aListOnlyInRoomReadsEachRowExactlyOnce`, `aPayloadInTheMemoryTierIsNotDecodedAgain` (P0 red); `aStaleListRefetchedAfterARoomReadStillRevalidates` (P1 guard); `storedRecordsArePlainJsonUnderTheV2Namespace` (P0 red); `v1RowsAreIgnoredAndRefetchedIntoV2` (P0 red); `itemTierHoldsSixteenMegabytesOfEncodedJson` (P1 red); `clearDropsEveryItemFromMemory`, `clearDropsEveryListFromMemory`, `clearDropsEveryPageFromMemory`, `clearDropsEveryHistoryPageFromMemory`, `clearDropsEveryPayloadFromMemory` (P0 guard); `invalidateQueryOnAFreshRepositoryRemovesThePayloadRow` (P1 guard).
  - Record decodes are counted as per-key `FakePayloadStore` reads (`onRead`, `T/util/FakePayloadStore.kt:11`, `:22-27`; `readAll` defaults to `read` per key); payload decodes with a counting `KSerializer` wrapping `ListSerializer(String.serializer())` passed to `getPayload`. Record classes are private, so AC-3 parses raw rows with `Json.parseToJsonElement` and decodes the `item` field with `Item.serializer()` and payload rows with the payload serializer. v1 rows for AC-4 are written with literal `content:v1:` keys and JSON-string-literal values.
  - Changed: `aPayloadWrittenByAnIncompatibleBuildIsDroppedAndFetchedAgain` (`:235-247`) comment only; `firstUseLeavesLegacyRowsToTheMigration` (`:249-267`) probe only if it fails; assertions unchanged.

**Consumes:** Task 1 `memoryWeigher: (encodedLength: Int) -> Int`.
**Produces:** typed feeds; key builders `itemKey`/`queryKey`/`payloadKey` (`:591-593`) yield `content:v2:` keys.

- [x] Write the new tests; run the P0/P1 red rows red
- [x] Implement as listed
- [x] Run the fast gate, `com.kino.puber.data.cache.*`, `ItemDetailsRepositoryTest`, `HomeInteractorCacheTest`, `HomeInteractorTest`, `ItemMergeCoverageTest`, `ContentListInteractorTest`, `HistoryInteractorTest`, `FavoritesInteractorTest`, `BookmarkFoldersInteractorTest`, `WatchLaterBookmarkInteractorTest`, `AppStartupTest`; commit

**Done when:** those classes pass; `rg 'decodeOrDiscard|discardUndecodablePayload|String.serializer' M/data/cache/ContentCacheRepository.kt` finds nothing.

---

### Task 5: Run ContentCacheRepository decodes and loaders on an injected dispatcher

**Covers:** AC-2, AC-9
**Files:**
- Modify: `M/data/cache/ContentCacheRepository.kt` - ctor (`:26-30`) gains `private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default`; `.flowOn(decodeDispatcher)` on `observeItems`, `observeItemPage`, `historyFirstPage`, `observeItemDetails`, `observePayload`; `withContext(decodeDispatcher)` around the bodies of public `mergeItems` (`:309-320`), `markItemDetailsStale` (`:328-335`), `invalidateItemDetails` (`:337-344`). `sectionPage`, `watchlist`, `observeSimilarItems` and `getPayload` (`:291-307`) inherit it through the flows they call. `M/data/di/modules.kt:97` unchanged.
- Test: `T/data/cache/ContentCacheRepositoryTest.kt` - `flowsDecodeAndLoadOnTheInjectedDispatcher`, `getPayloadAndOneShotWritesRunOnTheInjectedDispatcher` (P0 red). `decodeDispatcher` = `Executors.newSingleThreadExecutor { Thread(it, "decode") }.asCoroutineDispatcher()`, closed in `finally`; Main = `MainDispatcherExtension(StandardTestDispatcher())` via `@RegisterExtension` (`T/util/MainDispatcherExtension.kt:12-14`); thread name asserted in the counting serializer, the fake loader and `store.onRead`.
- Test fixtures: a test that fails only because work left the test scheduler gets `decodeDispatcher = StandardTestDispatcher(testScheduler)` when built inside `runTest`, `Dispatchers.Unconfined` when built as a property (likely `T/core/startup/AppStartupTest.kt:53`). Assertions unchanged; list each fixture change in the Implementation record.

**Consumes:** Task 4 typed feeds.
**Produces:** `ContentCacheRepository(store, clock, json, decodeDispatcher: CoroutineDispatcher = Dispatchers.Default)`.

- [x] Stub: add the parameter, unused; write both tests; run red
- [x] Add `flowOn`/`withContext`
- [x] Run the full gate; fix fixtures as above; commit

**Done when:** the full gate passes, `HomeVMIncrementalPublishTest` and `DetailsVMCachedLoadTest` included.

---

### Task 6: Decode KinoPubApiClient response bodies on an injected dispatcher

**Covers:** AC-10 (unit part; the device part stays with qa)
**Files:**
- Modify: `M/data/api/KinoPubApiClient.kt`
  - ctor (`:105-124`) gains, after `reachability` (`:123`), `@PublishedApi internal val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default` with a KDoc line: response bodies are decoded here, not in the caller's context, which is Main for VM callers. `@PublishedApi internal` because the public inline `apiCall` reads it (as `reportIfDomainUnreachable`, `:1059-1062`). Import `kotlinx.coroutines.CoroutineDispatcher`.
  - `apiCall` (`:1017-1033`): `:1026` becomes `Result.success(withContext(decodeDispatcher) { response.body<T>() })`. Only the decode moves: `block` is an inline parameter and stays outside the `withContext` lambda; the domain read (`:1023`) and the catch blocks are unchanged.
  - `thirdPartyApiCall` (`:1045-1053`): `:1048` the same way (`val response = block().requireSuccessful()`, then the `withContext` decode).
  - `handleApiResponse` (`:1159-1177`): `:1173` becomes `withContext(decodeDispatcher) { json.decodeFromString<T>(responseText) }` inside the existing `try`; `runCatchingCancellable` (`M/core/coroutine/CoroutinesErrorHandler.kt:37`) takes a suspend block.
- `M/data/di/modules.kt:73-80` unchanged (default `Dispatchers.Default`).
- Test: `T/data/api/KinoPubApiClientDecodeDispatcherTest.kt` (new) - `responseBodyIsDecodedOnTheInjectedDispatcher` (P0 red), row KDD in the spec's Test design.
  - Static setup: copy the companion of `T/data/api/KinoPubApiClientFailureTest.kt:37-54` (`Log`, `Base64`).
  - Client: as `client(cacheDir, baseUrl)` (`KinoPubApiClientFailureTest.kt:112-139`) plus `decodeDispatcher = recording`. Server: `withServer` and `HttpExchange.respond` (`:141`, `:159`) on path `/v1/items/7`, status 200, body `{"status":"200"}` (`ApiResponse` fields are all nullable, `M/data/api/models/Models.kt:18-22`).
  - `recording`: a `CoroutineDispatcher` subclass that counts `dispatch` calls in an `AtomicInteger` and forwards to `Executors.newSingleThreadExecutor { Thread(it, "decode") }.asCoroutineDispatcher()`, closed in `finally`.
  - Assert, from `runTest`: `getItemDetails(7)` (`M/data/api/KinoPubApiClient.kt:269`) succeeds with `status == "200"` and the count is at least 1. Base: count 0.

**Consumes:** nothing from Tasks 1-5.
**Produces:** `KinoPubApiClient(..., decodeDispatcher: CoroutineDispatcher = Dispatchers.Default)`.

- [x] Stub: add the parameter, unused; write the test; run it red (count 0)
- [x] Wrap the three decodes as listed
- [x] Run the fast gate and `com.kino.puber.data.api.*`; then the full gate; commit

**Done when:** the full gate passes; `rg -n 'body<T>\(\)|decodeFromString<T>' M/data/api/KinoPubApiClient.kt` shows each hit inside `withContext(decodeDispatcher)`.

---

### Verification handed to qa-device

Not implementer tasks; run at verify on this branch's dev APK, per `AGENTS.md` (Runtime) and spec, Test design.

| AC | Check |
|---|---|
| AC-4 | Manual check 1 (install-preserve upgrade over the e9c383d0 APK) |
| AC-7 | `make DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` under a lease |
| AC-8 | Manual check 2 |
| AC-10 | Perf track rows 1-3 against `.flow/runs/cache-decoded-memory-tier/baseline.md`, `handleApiResponse` (`:1173`) included; Manual check 3 |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Commit |
|---|---|
| 1 | a07da9c8 Weigh the CachedFeed memory tier by the encoded JSON length carried with each entry |
| 2 | 0df28e06 Skip server watch_state updates that change no written column |
| 3 | 41d17db8 Clear inherited pending watch_state flags after startup settles |
| 4 | 5f7f7ce9 Hold decoded records in one CachedFeed per record type and store them as plain JSON under content:v2 |
| 5 | d320a736 Run ContentCacheRepository decodes and loaders on an injected dispatcher |
| 6 | a27f1036 Decode KinoPubApiClient response bodies on an injected dispatcher |

**Red run** (P0 `red` tests on the unchanged code; Task 1 and 5 ran on the stub, Task 3 on the unused parameter):

- red: CachedFeedMemoryWeightTest.aValuePastTheWeightCapEvictsTheOldestFromMemory - expected 1 store read for the oldest key, was 0 (stub weighs every entry 0)
- red: ContentCacheRepositoryTest.aListServedFromTheMemoryTierReturnsTheSameDecodedCards - assertSame failed, the card was decoded again on the second read
- red: ContentCacheRepositoryTest.detailsServedFromTheMemoryTierAreNotDecodedAgain - assertSame failed, a new Item per read
- red: ContentCacheRepositoryTest.aListOnlyInRoomReadsEachRowExactlyOnce - the list row was read 2 times
- red: ContentCacheRepositoryTest.aPayloadInTheMemoryTierIsNotDecodedAgain - expected 0 deserializations after a load, was 1
- red: ContentCacheRepositoryTest.storedRecordsArePlainJsonUnderTheV2Namespace - `content:v1:item:7` does not start with `content:v2:`
- red: ContentCacheRepositoryTest.v1RowsAreIgnoredAndRefetchedIntoV2 - expected the loader's title "Loaded", was the v1 row's "Item 7"
- red: ContentCacheRepositoryTest.itemTierHoldsSixteenMegabytesOfEncodedJson (P1) - expected no store reads, was the v1 list row and items 9-16 re-read (cap reached at 8 M chars)
- red: ContentCacheRepositoryTest.flowsDecodeAndLoadOnTheInjectedDispatcher - expected [decode], was the test worker thread (no `flowOn`)
- red: ContentCacheRepositoryTest.getPayloadAndOneShotWritesRunOnTheInjectedDispatcher - expected [decode], was the test worker thread (no `withContext`)
- red: WatchStateRepositoryTest.clearAllPending_waitsForStartupToSettle - pending row was already cleared before the settle
- red: WatchStateRepositoryTest.markLocally_completesOnlyAfterTheDeferredCleanup - `markLocally` had already completed before the settle
- red: WatchStateRepositoryModuleTest.repositoryFromTheModuleDefersCleanupUntilStartupSettles (P1) - `clearAllPending` ran within 300 ms with `modules.kt` unwired (default `{}`)
- red: KinoPubApiClientDecodeDispatcherTest.responseBodyIsDecodedOnTheInjectedDispatcher - dispatches: 0
- AC-7 not run on device; the base WHERE (WatchStateDao.kt:140-142) admits an equal row. `WatchStateDaoTest` rows `aServerRowEqualToTheStoredOneIsNotRewritten` and `aServerRowDifferingInAnyWrittenColumnLands` are written and compile (`:app:compileDevDebugAndroidTestKotlin`); qa-device runs them.
- green on base (P0 guard, as designed): the five `clearDropsEvery...` rows, `aStaleListRefetchedAfterARoomReadStillRevalidates`, `invalidateQueryOnAFreshRepositoryRemovesThePayloadRow`, `markLocally_isNotStrippedByTheCleanupQueuedAtStartup`.

**Deviations from the plan:**

- `itemTierHoldsSixteenMegabytesOfEncodedJson` uses 16 items of 1 M chars that stay in memory (no store read, `assertSame`), then a 17th put evicts the first; the test design says 12 stay. 16 is what a 16 Mi-char cap admits, and 12 would not discriminate the base (8 M chars cap, 8 items).
- Task 4: `payloadFeed` returns `CachedFeed<T>` by an unchecked cast, so one store key must always be read with one serializer; the base had no such coupling. The only callers pass one serializer per key.
- Task 4: `observePayload` is `emitAll(feed.load(...))` (the feed's `Cached` already carries the typed value); the plan said to read `cached.value` directly, which is the same.
- Task 5: `handleApiResponse` (Task 6) needed an explicit `catch (CancellationException)` before the `Exception` catch, now that its try block suspends (detekt `SuspendFunSwallowedCancellation`).
- Task 5 fixture changes: `AppStartupTest.kt` builds its `ContentCacheRepository` with `decodeDispatcher = Dispatchers.Unconfined` (virtual time otherwise leaves the test scheduler). No other fixture needed a change; `HomeVMIncrementalPublishTest` and `DetailsVMCachedLoadTest` passed in `check.full`.

**Files touched outside the plan:**

- `app/src/test/kotlin/com/kino/puber/core/startup/AppStartupTest.kt` (fixture, above).

**For the reviewers:**

- `CachedFeed.load` now encodes before the epoch check (plan Task 1), so a load that is discarded still pays an encode.
- `ContentCacheRepository.queryNeedsRefetch` uses `peekAll`, which promotes the query row into memory pinned to the hard ceiling; freshness is judged from the stamp in `readCached`/`loadFresh`. `aStaleListRefetchedAfterARoomReadStillRevalidates` covers it.
- `invalidateQuery` asks the list, page and history feeds plus the payload feed for that key; on a fresh process with no payload feed it removes the row from the store directly.
- Every feed has its own 16 Mi-char cap, and each payload key has its own feed, so the worst case is above 16 Mi chars in total; the spec's AC-10 heap metric is the check.
- Task 2 SQL is untested on a device by me: the `IS NOT` comparison treats NULL as a value, which the per-column test exercises.
- `WatchStateRepositoryModuleTest` observes "no clear yet" over a 300 ms real-time window on the repository's IO scope.
- Baseline profile rules naming removed `ContentCacheRepository` methods are left alone (design Risks 9).

**Blocked:** none.

**Check results:**

- check.fast: `BUILD SUCCESSFUL` (`:app:compileDevDebugKotlin`).
- check.full (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) after the last task: `BUILD SUCCESSFUL in 6m 3s`, 46 actionable tasks.

## Readiness

Base e9c383d0, plan and spec at a11614d1. Previous: `readiness-1.md` (NOT READY, two fixes). Paths under `app/src/main/java/com/kino/puber/` unless named.

Verdict: **READY**

### Readiness-1 fixes

| Fix | Resolution | Status |
|---|---|---|
| 1. API body decode stays on Main | Plan Task 6 moves the decodes at `data/api/KinoPubApiClient.kt:1026`, `:1048`, `:1173` onto an injected `decodeDispatcher`; Test design row KDD `responseBodyIsDecodedOnTheInjectedDispatcher` (P0 red); AC-10 text unchanged, so no gate 1 rerun | closed |
| 2. AC-4 device and AC-10 rows without marks | Both rows now `manual`, carried by Manual check 1 and Perf track rows 1-2 | closed |

### AC coverage

| AC | Plan task | Test design row | Status |
|---|---|---|---|
| AC-1 | 4 | four CCR rows P0 red, one P1 guard | covered |
| AC-2 | 5 | two CCR rows P0 red | covered |
| AC-3 | 4 | `storedRecordsArePlainJsonUnderTheV2Namespace` | covered |
| AC-4 | 4, qa | `v1RowsAreIgnoredAndRefetchedIntoV2`, Manual check 1 | covered |
| AC-5 | 1, 4 | `aValuePastTheWeightCapEvictsTheOldestFromMemory`, `itemTierHoldsSixteenMegabytesOfEncodedJson` | covered |
| AC-6 | 4 | five `clearDrops...` rows | covered |
| AC-7 | 2, qa | DAO rows | covered |
| AC-8 | 3, qa | WSR rows, module test, Manual check 2 | covered |
| AC-9 | 1, 4, 5 | existing suites, `invalidateQueryOnAFreshRepositoryRemovesThePayloadRow` | covered |
| AC-10 | 6, qa | KDD row, Perf track | covered |

### Citations sampled (new in round 2)

| Citation | Claim | Resolves |
|---|---|---|
| `data/api/KinoPubApiClient.kt:105`, `:123` | ctor start, `reachability` param | yes |
| `data/api/KinoPubApiClient.kt:1017`, `:1026` | `suspend inline fun apiCall`, `response.body<T>()` | yes |
| `data/api/KinoPubApiClient.kt:1045`, `:1048` | `thirdPartyApiCall`, its `body<T>()` | yes |
| `data/api/KinoPubApiClient.kt:1059-1062` | `@PublishedApi internal fun reportIfDomainUnreachable` | yes |
| `data/api/KinoPubApiClient.kt:1159`, `:1173` | `handleApiResponse`, `json.decodeFromString<T>` | yes |
| `data/api/KinoPubApiClient.kt:269` | `getItemDetails` through `apiCall` | yes |
| `core/coroutine/CoroutinesErrorHandler.kt:37` | `runCatchingCancellable(crossinline call: suspend () -> T)` | yes |
| `data/api/models/Models.kt:18-22` | `ApiResponse` with all-nullable fields | yes |
| test `data/api/KinoPubApiClientFailureTest.kt:37-54`, `:112-139`, `:141`, `:159` | companion mocks, `client(...)`, `withServer`, `HttpExchange.respond` | yes |
| `data/di/modules.kt:73`, `:125` | `KinoPubApiClient(` single, `WatchStateRepository(` single | yes |

Round-1 citations for Tasks 1-5 are unchanged at this base (`git diff e9c383d0 -- app/src/main` empty).

### Other checks

| Check | Result |
|---|---|
| Build green per task, dependency order | Task 6 adds a defaulted ctor param, no caller change; independent of 1-5. Rest as readiness-1 |
| P0 rows name defect, mark, test | yes for every row; device rows are `manual` |
| Out of scope | plan stays inside |
| Blocking questions | none open |

### Notes for verify (non-blocking)

- Decodes left in the caller's context outside Task 6: token refresh `data/api/KinoPubApiClient.kt:207` (`body<TokenResponse>()`) and error bodies `data/api/ApiFailures.kt:66`, `:73`. They run only on a 401 refresh or an error response; if the AC-10 trace shows either on Main, it is a finding at review.
- TMDB decodes are already off Main (`data/repository/EpisodeScheduleRepository.kt:29`, `withContext(workerDispatcher)`).

### Fix round 1 (review triage #3, #4)

Tests only, no production change. #2 (base-DAO red run) is qa's.

| Finding | Test | Failure shown (guarded line removed, then restored) |
|---------|------|------------------------------------------------------|
| #3 | `ContentCacheRepositoryTest.detailsOnlyInRoomReadTheItemRowExactlyOnce` | `readItemRecords` using `peek` instead of `peekAll` (no promotion): failed, as did the list test |
| #4 | `KinoPubApiClientDecodeDispatcherTest.aThirdPartyResponseBodyIsDecodedOnTheInjectedDispatcher` | `withContext(decodeDispatcher)` removed from `thirdPartyApiCall`: failed |
| #4 | `KinoPubApiClientDecodeDispatcherTest.theApiCallBodyIsDecodedOnTheDecodeThread` | thread-name assertion on `apiCall`; not mutated separately (same shape as the third-party case) |
| #4 | `KinoPubApiClientDecodeDispatcherTest.theOAuthResponseTextIsDecodedOnTheInjectedDispatcher` | `withContext(decodeDispatcher)` removed from `handleApiResponse`: failed |

`handleApiResponse` is reached through `getDeviceCode` with an injected OkHttp interceptor that answers the OAuth request; the dispatcher records the thread each dispatched block runs on. The inline calls assert the thread name through a probe serializer.

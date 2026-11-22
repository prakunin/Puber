LENS: tests  ROUND: 1  BASE: e9c383d0

Suite state: I re-ran the touched classes in the worktree with `--rerun`: `ContentCacheRepositoryTest`, `CachedFeedMemoryWeightTest`, `CachedFeedTest`, `KinoPubApiClientDecodeDispatcherTest`, `WatchStateRepositoryModuleTest`, `WatchStateRepositoryTest`, `ItemDetailsRepositoryTest` and `AppStartupTest`. All passed (BUILD SUCCESSFUL). I did not run check.full again; qa-report.md records it green, 2279 tests with 0 failures. The diff removes no assertions; `ContentCacheRepositoryTest` loses only one comment. `CachedFeedTest` is unchanged.

### F1 [minor] The AC-7 P0 red test has no red run on the base code
- Where: app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:84 (`aServerRowEqualToTheStoredOneIsNotRewritten`)
- Problem: The test design marks this row P0 red. The Implementation record and qa-report.md both say it was never run against the base DAO. The only evidence that it can fail is the argument that "the base WHERE admits an equal row". The test checks that nothing re-emits during a 1 s real-time window, and a test that checks for absence can pass on both base and head without anyone noticing.
- Failure scenario: Suppose the base re-emission never reaches the collector inside the window, for example because Room batches invalidations or the second write lands before the observer is registered. The test then passes on base too and does not guard the no-op predicate.
- Fix: Under a lease, qa-device runs `make DEVICE=<serial> itest TESTS=com.kino.puber.data.db.WatchStateDaoTest` once on a build of the base `WatchStateDao` with this test file, and the red result goes into the Implementation record.

### F2 [minor] AC-1 "Details open with the record only in Room is decoded exactly once" has no test
- Where: app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt:343; production path app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:196 and :208
- Problem: AC-1 names two cases: a Home list load and a Details open. The Room-only exactly-once case is tested only for lists (`aListOnlyInRoomReadsEachRowExactlyOnce`). For Details there is only a memory-tier test. Yet `observeItemDetails` reads the record with `readItemRecord` before `itemFeed.load`, and reads it again in the loader. Exactly-once holds only because `peekAll` promotes the record into memory.
- Failure scenario: Change the pre-read in `observeItemDetails` to something that does not promote, such as `peek`. A fresh repository then decodes the Details record twice per open, and every test still passes.
- Fix: Add `detailsOnlyInRoomReadTheItemRowExactlyOnce`. Populate the store, build a fresh repository, call `observeItemDetails(7) { error("fresh") }`, and assert `store.onRead` saw `itemKey(7)` exactly once.

### F3 [minor] Two changed decode paths in `KinoPubApiClient` have no test and no manual check that reached them
- Where: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1055 (`thirdPartyApiCall`) and :1180-1182 (`handleApiResponse`: `withContext` plus the new `catch (CancellationException)` branch)
- Problem: The only unit test, `KinoPubApiClientDecodeDispatcherTest`, covers `apiCall`, and it asserts only that something was dispatched (`dispatches >= 1`), not which thread the decode ran on. The only caller of `thirdPartyApiCall` is `getLatestGitHubRelease`. `handleApiResponse` runs during the OAuth flow. qa-report.md shows the AC-10 device sequence exercised neither path, so the "including `handleApiResponse`" part of the Perf row is unverified.
- Failure scenario: Remove `withContext(decodeDispatcher)` at :1055 or :1180, or drop the new cancellation catch so a cancelled decode surfaces as `ApiError.Serialization`. No test fails, and the device evidence would not show it either.
- Fix: Add a `thirdPartyApiCall` case to `KinoPubApiClientDecodeDispatcherTest`, either by pointing a local server at it if the GitHub base URL can be injected, or by calling the inline `thirdPartyApiCall { client.get(localUrl) }` directly, and record the thread name inside the decode. If neither is practical, mark `handleApiResponse` as an explicit manual or unverified item in the evidence.

### Unproven
- app/src/test/kotlin/com/kino/puber/data/di/WatchStateRepositoryModuleTest.kt:52 The "not cleared before settle" check is a 300 ms real-time window on the IO scope. It cannot fail spuriously while green, but it guards the wiring only if the base clear happens within 300 ms. The recorded red run shows it did, once.
- app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt:47 The default `subject` and the fixtures in other suites (`ItemDetailsRepositoryTest`, `ContentListInteractorTest`, `HomeInteractorCacheTest`) now run cache work on the real `Dispatchers.Default` against the unsynchronized `FakePayloadStore`. This would matter only if a flaky run appeared in `unit-suite-flaky` after this merge.

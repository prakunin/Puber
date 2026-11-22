LENS: edge  ROUND: 2  BASE: e9c383d0

No findings.

The incremental diff `803329d9..c93c20c6` changes only tests and the plan, with no production code. I checked each new test against the production path it claims to guard:

- **`detailsOnlyInRoomReadTheItemRowExactlyOnce`** (`/home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/app/src/test/kotlin/com/kino/puber/data/cache/ContentCacheRepositoryTest.kt:371`): a new repository over the same store is fresh at the same `now`. In `observeItemDetails` (`ContentCacheRepository.kt:191-232`), the first `readItemRecord` promotes the row through `peekAll` (`:416`). If the loader ran, `error("fresh")` would fail the test. So the single read of `itemKey(7)` really guards the promotion. Removing `peekAll` would add a second store read, and the plan records that this mutation failed the test.
- **`thirdPartyApiCall` and `apiCall` cases**: the probe serializer reports the thread its own deserialization runs on. Without the `withContext(decodeDispatcher)` at `KinoPubApiClient.kt:1032` and `:1055`, `body<T>()` would run on the test thread, not on `decode`.
- **`getDeviceCode` / `handleApiResponse` case**: the client wraps the injected `OkHttpClient` (`preconfigured`, `KinoPubApiClient.kt:141`), so the application interceptor answers before any socket opens and port 1 is never contacted. `ranOn == ["decode"]` requires exactly one dispatch onto the decode dispatcher, and only the `withContext` at `:1180` produces one.
- **Boundaries left over from round 1**: base-DAO red run (#2) belongs to qa, and the memory budget (#1) was moved to `typed-feeds-memory-budget`. This diff does not touch either.

### Unproven
- `/home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientDecodeDispatcherTest.kt:175` `probeHttpClient()` is never closed, so each test leaves one Ktor/OkHttp engine open. This only matters if the unit suite shows thread or file-handle exhaustion or cross-test interference in `unit-suite-flaky`.

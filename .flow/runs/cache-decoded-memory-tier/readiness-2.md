# Readiness 2: cache-decoded-memory-tier

Base e9c383d0, plan and spec at a11614d1. Previous: `readiness-1.md` (NOT READY, two fixes). Paths under `app/src/main/java/com/kino/puber/` unless named.

Verdict: **READY**

## Readiness-1 fixes

| Fix | Resolution | Status |
|---|---|---|
| 1. API body decode stays on Main | Plan Task 6 moves the decodes at `data/api/KinoPubApiClient.kt:1026`, `:1048`, `:1173` onto an injected `decodeDispatcher`; Test design row KDD `responseBodyIsDecodedOnTheInjectedDispatcher` (P0 red); AC-10 text unchanged, so no gate 1 rerun | closed |
| 2. AC-4 device and AC-10 rows without marks | Both rows now `manual`, carried by Manual check 1 and Perf track rows 1-2 | closed |

## AC coverage

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

## Citations sampled (new in round 2)

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

## Other checks

| Check | Result |
|---|---|
| Build green per task, dependency order | Task 6 adds a defaulted ctor param, no caller change; independent of 1-5. Rest as readiness-1 |
| P0 rows name defect, mark, test | yes for every row; device rows are `manual` |
| Out of scope | plan stays inside |
| Blocking questions | none open |

## Notes for verify (non-blocking)

- Decodes left in the caller's context outside Task 6: token refresh `data/api/KinoPubApiClient.kt:207` (`body<TokenResponse>()`) and error bodies `data/api/ApiFailures.kt:66`, `:73`. They run only on a 401 refresh or an error response; if the AC-10 trace shows either on Main, it is a finding at review.
- TMDB decodes are already off Main (`data/repository/EpisodeScheduleRepository.kt:29`, `withContext(workerDispatcher)`).

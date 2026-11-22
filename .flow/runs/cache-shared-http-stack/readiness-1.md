# Readiness 1: cache-shared-http-stack

Verdict: **READY**

Spec: `docs/superpowers/specs/2026-11-21-cache-shared-http-stack.md` (Status: approved). Plan: `docs/superpowers/plans/2026-11-21-cache-shared-http-stack.md`. Citations checked against the worktree at 88fa7303.

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | 3 | `apiClients_shareOneConnectionPoolAndOneDns`, `dohClient_sharesThePoolButResolvesWithoutDoh` |
| AC-2 | 3 | `lookup_repeatWithinTtl_issuesNoDohQuery`, `lookup_afterTtl_issuesOneDohQuery`, `lookup_afterEvictAll_issuesOneDohQuery` |
| AC-3 | 4 | four `ApiDomainInteractorTest` rows, `apiDomainInteractor_evictsTheSharedDnsCache` |
| AC-4 | 1, 3 | `httpPosterUrl_isRewrittenToHttps`, `httpsAndNonStringData_passThroughUnchanged`, `imageRequest_carriesNoKinoPubAuthHeaderOrQuery`, M2 |
| AC-5 | 1 (baseline), 5 | M3; drop branch: `theLegacyHttpCacheIsDeletedOnceAcrossSweeps`, `deleteAction_removesOkhttpcacheAndKeepsSiblingCaches` and two P1/P2 rows |
| AC-6 | 2 | four `KinoPubConfigTest` rows |
| AC-7 | 1 (counters, before), verify (after) | M5, Perf section; metric per owner Q4 |
| AC-8 | every task, 5 (Done when) | existing suites, `check.full` |

## Citations sampled

| Citation | Resolves to |
|---|---|
| `data/api/network/DnsOverHttpsFactory.kt:11` | `val bootstrapClient = OkHttpClient.Builder().build()` |
| `data/di/modules.kt:60-64`, `:68`, `:108-116` | app `OkHttpClient` single; `cacheDir = androidContext().cacheDir`; `CachedPayloadSweeper` single |
| `data/api/KinoPubApiClient.kt:97,107,130,136-138,1187-1190,1196` | `import okhttp3.Cache`; `cacheDir` param; `httpClient`; `config { cache(...) }`; `shouldSendKinoPubToken`; `CACHE_SIZE` |
| `data/api/config/KinoPubConfig.kt:20-21,23-33,51-54,61-62,72-74,76-91` | `domainOverride`, `defaultEndpoint` getter, `activeEndpoint`, `BUILT_IN_ENDPOINTS`, `setDomainOverride`, `resolveEndpoint`/`buildCustomMirrorEndpoint` |
| `domain/interactor/api/ApiDomainInteractor.kt:45-51,77,138,150,161,171-179,185` | constructor; three `clearDomainSensitiveCaches()` callers; the function; `clearWithoutFailing`; `applyEndpoint` call |
| `domain/di/modules.kt:41-49`, `:45` | `ApiDomainInteractor` single; `HttpEndpointProbe(get())` |
| `PuberApp.kt:127-129`, `:134-145` | `components { add(HttpsEnforcingInterceptor()) }`; the private interceptor |
| `data/cache/CachedPayloadSweeper.kt:38,42,60-64`; `MediaCacheTrim.kt:43` | `trimMediaCache` param, `mediaTrimmed`, trim block in `sweepOnce`; `mediaCacheTrimAction` |
| `ui/feature/player/vm/PlaybackController.kt:623,632-633` | `okHttpClient.newBuilder()`; `builder.dns(okhttp3.Dns.SYSTEM)` |
| Test fixtures `KinoPubApiClientFailureTest.kt:133`, `AuthProbeTest.kt:150`, `DownloadTest.kt:108`, `HistoryTest.kt:46-47,206`, `ApiDomainInteractorTest.kt:36-45,54-64,176-188`, `CachedPayloadSweeperTest.kt:44-56,80-93,95-103` | all resolve as claimed |
| Library APIs | okhttp-android 5.5.1 has `okhttp3.DnsCache` with `evictAll()`; okhttp-dnsoverhttps 5.5.1 has `Builder.cache(DnsCache)` and `client()`; coil-network-ktor3 3.6.3 has `KtorNetworkFetcher.factory(Function0<HttpClient>)` (javap on the Gradle cache jars) |

## Order and build

Task 1 adds a defaulted factory parameter and new files; Task 2 is self-contained; Task 3 replaces the only `DnsOverHttpsFactory.create` caller in the same task; Task 4 updates both `ApiDomainInteractor(` call sites (`domain/di/modules.kt:42`, `ApiDomainInteractorTest.kt:36`) with the new parameter; Task 5 (drop) removes `cacheDir` from the one production and four test call sites it lists, which is every caller found by `rg "KinoPubApiClient\("`. No task leaves the build red.

## Scope and questions

Plan stays inside Out of scope (player fast-DNS path, `shouldSendKinoPubToken`, Ktor client merge, timeouts). Q1-Q6 answered in `state.md`; none blocks.

## Notes for the implementer (no fix required)

| # | Note |
|---|---|
| 1 | `DnsOverHttps.client()` is a JVM getter; from Kotlin it may read as `.client`. Let the compiler decide. |
| 2 | AC-1 test reads `OkHttpConfig.preconfigured`; KinoPub's engine derives its effective client via `config { }` (`newBuilder`), which keeps pool and `Dns`, so the assertion holds for the effective client too. |
| 3 | Device P0 rows (M2, M3, M5) carry no red/guard mark; they are manual by nature and are verified by qa-device. |
| 4 | The baseline in `baseline.md` is still "NOT MEASURED" (lease held by startup-splash-preload); Task 2 does not start until Task 1's device baseline records DROP or KEEP, so a lease outage stalls the run there and goes to the owner. |

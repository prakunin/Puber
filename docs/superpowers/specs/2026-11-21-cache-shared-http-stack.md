# Share one HTTP stack, cache DoH answers, decide the KinoPub HTTP cache, memoise KinoPubConfig

Status: approved
Date: 2026-11-21
Slug: cache-shared-http-stack
Kind: refactor
Size: M
Tracks: perf

## Problem / Why

### Request
research.md: Request. Owner rule (state.md: Decisions): drop the KinoPub OkHttp cache only if a device session shows no 304 or cache hit on KinoPub responses.

### Current state
research.md: Current state in code. Correction to the parent seed: IntroDb, TheIntroDb and TMDB already share the app `OkHttpClient`; the separate pools are Coil's default Ktor client and the DoH bootstrap client (`DnsOverHttpsFactory.kt:11`).

### Approach (analyst)
research.md: Options, A (recommended): one base `OkHttpClient`; DoH built on it with OkHttp 5.5.1 `DnsCache`; Coil gets `KtorNetworkFetcherFactory` over a plugin-free Ktor client on the shared client; `DnsCache.evictAll()` in `clearDomainSensitiveCaches`; `KinoPubConfig` resolves the endpoint once per `setDomainOverride` behind unchanged public getters. Fallback for the DNS cache: option C.

### Risks (analyst)
research.md: Risks. The one that shapes the plan: `startup-splash-preload` (stage review) edits `PuberApp.kt` `newImageLoader` and `ApiDomainInteractor.kt`; implement on a base that contains it or rebase onto it.

## Acceptance criteria

- AC-1. Given the Koin `apiModule` graph and the Coil image loader, When the KinoPub, IntroDb, TheIntroDb, TMDB, Coil and DoH-bootstrap clients are resolved, Then they share one `ConnectionPool` and the five app clients share one `Dns` instance (unit test).
- AC-2. Given a host resolved through DoH with record TTL T, When it is looked up again before T and after T, Then the first repeat issues no DoH query and the second issues one (unit test against a fake DoH server; if `DnsCache` fails it, option C with a fixed TTL and an owner note).
- AC-3. Given a resolved KinoPub host, When the API domain is switched (`ApiDomainInteractor` override path), Then the DNS cache is empty before the next request, and a failing evict does not stop the content-cache and prefetcher clears (unit test in `ApiDomainInteractorTest`).
- AC-4. Given the shared Coil client, When a poster URL is `http://`, Then `HttpsEnforcingInterceptor` still upgrades or rejects it as today, and no KinoPub header or plugin reaches image requests (unit test); posters load on Home and Details (device observation).
- AC-5. Given a debug build with the current cache, When a device session covers cold start, Home, Details, a series season and playback start, Then the KinoPub cache `hitCount`, `networkCount` and 304 count are recorded in evidence; if hits and 304s are both zero the cache is removed and `cacheDir/okhttpcache` deleted once, otherwise the cache stays (device evidence; unit test for the one-time delete).
- AC-6. Given default, alador, legacy-CDN and a custom mirror override, When the `KinoPubConfig` getters are read, Then values equal today's, and repeated reads return the same `ApiEndpointPreset` instance until the next `setDomainOverride` (unit test).
- AC-7 (perf). Given an emulator cold start through Home, Details and one poster grid, When DoH queries and new connections are counted before and after, Then both numbers are recorded and neither rises (device, `EventListener` or debug log).
- AC-8. Existing `KinoPubApiClient*Test`, `TmdbApiClientTest`, `data/api/network/*Test`, `ApiDomainInteractorTest` pass with assertions unchanged, and the full check passes.

Owner answer Q4 (2026-11-21): AC-7 counts resolver queries (DoH queries plus Coil's system lookups before, DoH queries after), not DoH queries only.

## Out of scope

Other split items of cache-layer-rework (cache-decoded-memory-tier, cache-targeted-invalidation; cache-storage-bounds is merged). Merging IntroDb, TheIntroDb and TMDB into one Ktor client (Q2). Per-request Url parsing in shouldSendKinoPubToken (Q5, left for network-timeouts-failover). Timeouts and retries (network-timeouts-failover). The player's fast-DNS path (stays on Dns.SYSTEM).

## Design

Base 88fa7303. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Where is the stack built? | New `data/api/network/HttpStack.kt`: `HttpStack.create(debugListener)` returns `HttpStack(client, dnsCache)`. One base `OkHttpClient`; DoH client = base (bootstrap DNS set by `DnsOverHttps` itself); app client = `base.newBuilder().dns(doh).build()`. | One pool by construction; one place a test can build without Koin. |
| DNS cache | `DnsCache()` with library defaults, passed to `DnsOverHttps.Builder.cache(...)`; `DnsOverHttpsFactory.create(client, cache)` takes both as parameters. | Bytecode (okhttp-android 5.5.1): defaults min 10 s, max 300 s, failure 10 s, revalidate 2 s, 1000 entries; record TTL clamped between min and max (`RealDnsCache`); blocking `DnsOverHttps.lookup` goes through `newCall` with the same `cache` field. Option C stays the fallback only if AC-2 fails. |
| Koin shape | `single { HttpStack.create(...) }`, `single<OkHttpClient> { get<HttpStack>().client }`, `single<DnsCache> { get<HttpStack>().dnsCache }`. | Every existing `get<OkHttpClient>()` (API clients, player, probes) keeps its type and now gets the DoH+cache client. |
| Coil | `components { add(KtorNetworkFetcherFactory(httpClient = { createImageHttpClient(get<OkHttpClient>()) })) }` in `newImageLoader`; `createImageHttpClient` in `data/api/network/ImageHttpClient.kt` = `HttpClient(OkHttp) { engine { preconfigured = it } }`, no plugins. | Lambda overload (coil-network-ktor3 3.6.3 `KtorNetworkFetcher.factory(Function0)`) defers Koin and Ktor setup to the first fetch. User components precede service-loaded ones (coil-core 3.6.3 `RealImageLoader.<init>`: registry, then `addServiceLoaderComponents`), so ours wins. |
| DNS evict on domain switch | `ApiDomainInteractor` gets `evictDnsCache: () -> Unit`; Koin passes `{ get<DnsCache>().evictAll() }`; called first in `clearDomainSensitiveCaches` inside `clearWithoutFailing`. | Lambda keeps the interactor free of OkHttp and lets the test inject a throwing evict (AC-3). |
| Measuring AC-5 and AC-7 | Task 1 adds debug-only `NetworkStats` (`EventListener.Factory` per label: `app`, `doh`, `image`) on the **current** stack, and gives Coil an explicit fetcher over a standalone default `OkHttpClient` (system DNS, own pool, label `image`) equal to today's service-loaded one. Baseline device session runs on that commit; the stack change follows. | Same counters before and after; at base, Coil's and the DoH bootstrap's clients cannot be observed. |
| Counters | Per label: `callStart`, `dnsStart`, `connectStart`, `cacheHit`, `cacheConditionalHit` (304), `cacheMiss`; one Timber line per event under tag `NetStats` with cumulative counts. DoH queries = `doh.callStart`; new connections = sum of `connectStart`; KinoPub cache = `app` cache events (only KinoPub's client has a cache). | OkHttp's `EventListener` reports cache hits and conditional hits directly; no handle on the `Cache` object needed. Release builds install no listener. |
| KinoPub HTTP cache (AC-5) | Branch after the baseline session. Hits and 304s both zero: drop `cache(...)` and the `cacheDir` parameter; `CachedPayloadSweeper` gets `deleteLegacyHttpCache: () -> Unit` run once per process in `sweepOnce`, Koin passes `legacyHttpCacheDeleteAction(cacheDir)` (`File(cacheDir, "okhttpcache").deleteRecursively()`). Otherwise no change. | Owner rule in `state.md` Decisions; deletion reuses the once-per-process pattern of `trimMediaCache`. |
| KinoPubConfig memo | `defaultEndpoint` and `BUILT_IN_ENDPOINTS` become `by lazy`; one `@Volatile` snapshot `(override, endpoint)` is replaced in `setDomainOverride`; all public getters keep their names and types and read the snapshot (default endpoint when never set). | One volatile read keeps override and endpoint consistent; getters unchanged keep `mockkObject` stubs in `ApiDomainInteractorTest` valid. |
| AC-1 test reach | `data/di/ApiModuleHttpStackTest` starts `koinApplication { androidContext(mockk(relaxed)); modules(apiModule, module { single<ICryptoPreferenceRepository> { mockk(relaxed = true) } }) }`, reads each client's private `httpClient` field by reflection, casts `engine.config` to `OkHttpConfig`, compares `preconfigured.connectionPool` and `.dns`; Coil via `createImageHttpClient`; DoH via `(dns as DnsOverHttps).client().connectionPool`. | No production surface added for the test. Pattern: `app/src/test/kotlin/com/kino/puber/data/di/EpisodeScheduleRepositoryModuleTest.kt:15`. |

#### Changes by layer

##### API / repository

| File | Change | AC |
|---|---|---|
| `data/api/network/HttpStack.kt` (new) | `class HttpStack(val client: OkHttpClient, val dnsCache: DnsCache)`; `companion fun create(listeners: ((String) -> EventListener.Factory)? = null)` builds base, `DnsCache()`, DoH via the factory, app client. | AC-1, AC-2 |
| `data/api/network/DnsOverHttpsFactory.kt:10-22` | `create(client: OkHttpClient, cache: DnsCache)`; drop `OkHttpClient.Builder().build()` at `:11`; add `.cache(cache)`. URL, bootstrap hosts, `post(true)`, `includeIPv6(false)` unchanged. | AC-1, AC-2 |
| `data/api/network/NetworkStats.kt` (new, debug-only use) | `EventListener` counting the events in Decisions; atomics; `Timber.d`. | AC-5, AC-7 |
| `data/api/network/ImageHttpClient.kt` (new) | `createImageHttpClient(okHttpClient: OkHttpClient): HttpClient`, no plugins. | AC-1, AC-4 |
| `data/api/KinoPubApiClient.kt:107,132-138,1196` | Only on the drop branch of AC-5: remove `cacheDir`, the `config { cache(...) }` block, `CACHE_SIZE` and `import okhttp3.Cache` (`:97`). Test fixtures drop the `cacheDir =` argument (`app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientFailureTest.kt:133` and the three sibling tests). | AC-5, AC-8 |
| `data/api/config/KinoPubConfig.kt:23-33,51-66,72-74` | Memo per Decisions; `resolveEndpoint` (`:76-80`) and `buildCustomMirrorEndpoint` (`:82-91`) run only inside `setDomainOverride`. | AC-6 |
| `data/cache/CachedPayloadSweeper.kt:31-39,60-71` | Drop branch only: `deleteLegacyHttpCache` parameter (default `{}`), own `AtomicBoolean`, `runCatchingCancellable` + `Timber.w` as for `trimMediaCache`. New `data/cache/LegacyHttpCache.kt` with `legacyHttpCacheDeleteAction(cacheDir: File)`, next to `mediaCacheTrimAction` (`data/cache/MediaCacheTrim.kt:43`). | AC-5 |
| `domain/interactor/api/ApiDomainInteractor.kt:45-51,161-169` | Constructor param `evictDnsCache: () -> Unit`; `clearWithoutFailing { evictDnsCache() }` (`:171`) as the first clear. Covers both callers: `resetToDefault` (`:147-151`) and `applyEndpoint` (`:181-185`). | AC-3 |

##### ViewModel state and actions
N/A: no ViewModel changes.

##### Screen and DI module

| File | Change | AC |
|---|---|---|
| `data/di/modules.kt:60-64` | Replace with the three `HttpStack` singles; listener factory `NetworkStats::factory` only when `BuildConfig.DEBUG`. | AC-1 |
| `data/di/modules.kt:66-74` | Drop branch: remove `cacheDir = androidContext().cacheDir`. | AC-5 |
| `data/di/modules.kt:108-116` | Drop branch: pass `deleteLegacyHttpCache = legacyHttpCacheDeleteAction(androidContext().cacheDir)`. | AC-5 |
| `domain/di/modules.kt:41-49` | Pass `evictDnsCache = { get<DnsCache>().evictAll() }` (resolve `DnsCache` at construction, call at evict). | AC-3 |
| `PuberApp.kt:127-129` | Task 1: `add(KtorNetworkFetcherFactory(httpClient = { createImageHttpClient(standalone image client) }))`; stack task: `createImageHttpClient(get<OkHttpClient>())` via `org.koin.android.ext.android.get`. `HttpsEnforcingInterceptor` (`:134-145`) moves to `core/image/HttpsEnforcingInterceptor.kt` as `internal` so AC-4 can test it; body unchanged. | AC-1, AC-4, AC-7 |

##### Compose content
N/A: no composable changes.

##### Resources
N/A: no strings or resources.

#### Alternatives rejected

| Alternative | Reason |
|---|---|
| Project TTL wrapper over DoH (option C) as the first choice | Cannot read record TTLs through `Dns.lookup`; kept as AC-2 fallback only. |
| One Ktor client for IntroDb, TheIntroDb and TMDB (option B) | Owner Q2: no. |
| `serviceLoaderEnabled(false)` on the image loader to suppress Coil's default fetcher | Also drops other service-loaded components (coil-video); ordering already makes ours win. |
| Exposing `internal val okHttpClient` on each API client for AC-1 | Adds production surface for one test; reflection in the test suffices. |
| Measuring the baseline on 88fa7303 itself | Coil's and the DoH bootstrap's clients there accept no listener; counts would not compare. |

#### Risks

| Risk | Where | Guard |
|---|---|---|
| AC-7 fails by construction: Coil moves from system DNS to DoH, so image hosts add DoH queries that did not exist before | `PuberApp.kt:127-129`, `NetworkStats` | Question 1. Report `image.dnsStart` before alongside `doh.callStart` after. |
| `DnsCache` revalidation (2 s before expiry) issues a background DoH query, so AC-2's "first repeat issues no query" flakes | `HttpStack.kt`, AC-2 test | Test builds `DnsCache(minimumTimeToLive = ZERO, revalidateBeforeExpire = ZERO)` with a 1 s record TTL and a real ~1.2 s wait (`RealDnsCache` uses `TimeSource.Monotonic`, no injectable clock). Fake DoH = an application interceptor on the DoH client answering DNS wire messages; no new test dependency. |
| Stale DNS answer delays mirror failover up to 300 s | `DnsCache` defaults | Evict on every domain switch (AC-3); failures cached 10 s only. |
| Coil's service-loaded factory still builds its own `HttpClient` | coil-network-ktor3 default factory | Device AC-7: `image` connections only on the shared pool; no connection outside a labelled client. |
| Image requests inherit KinoPub auth, headers or `HttpRequestRetry` | `ImageHttpClient.kt` | Plugins live on KinoPub's Ktor client (`KinoPubApiClient.kt:141-189`), not on the `OkHttpClient`; AC-4 unit test asserts the outgoing image request carries no `Authorization`, no KinoPub `User-Agent`, no KinoPub query parameters. |
| Startup: first image fetch now resolves Koin and builds a Ktor client | `PuberApp.kt:102` | Lambda overload runs on Coil's fetch dispatcher; the `OkHttpClient` single is already built during `initDi` (`domain/di/modules.kt:45` `HttpEndpointProbe(get())`). |
| `KinoPubConfig` snapshot read before `initialize` | `KinoPubConfig.kt` | Null snapshot falls back to the lazy default endpoint, as today. |
| Lazy `defaultEndpoint` caches the mocked `Base64` domain across tests in one JVM | `KinoPubConfig.kt:15-18` (already lazy today) | AC-6 test derives expectations from `KinoPubConfig.DEFAULT_API_DOMAIN`, not a literal. |
| Merge conflict with startup-splash-preload | `PuberApp.kt` `newImageLoader`, `ApiDomainInteractor.kt` | Owner decision: resolve at merge or rebase. |
| Player fast-DNS path keeps `Dns.SYSTEM` | `ui/feature/player/vm/PlaybackController.kt:632-633` | Unchanged by design; shares pool and listener through `newBuilder()` (`:623`). |

#### Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | AC-7 counts DoH queries; Coil leaving system DNS adds DoH queries for image hosts. Count resolver queries as DoH plus system lookups? | Yes: compare `doh.callStart + image.dnsStart` before with `doh.callStart` after; the AC wording changes to "resolver queries". | no |
| 2 | Per-request `Url` parsing in `shouldSendKinoPubToken` (`KinoPubApiClient.kt:1187-1190`) is not covered by any AC. | Leave for network-timeouts-failover or a later run. | no |
| 3 | `TmdbApiClient` KDoc (`data/api/TmdbApiClient.kt:26-30`) claims a shared cache that does not exist. | Fix the sentence in the stack task. | no |

## Test design

Base 88fa7303. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`; owner answers Q1-Q6: `state.md`. Levels per `.flow/roles/test-designer.md`. Test paths under `app/src/test/kotlin/com/kino/puber/`.

#### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | `DnsCache` ignores record TTLs or the blocking `DnsOverHttps.lookup` bypasses it | 2 | 3 | 6 | AC-2 unit; fallback option C (Q1) |
| R2 | Koin graph broken by the `HttpStack` singles: crash at launch or a client left on its own pool | 2 | 3 | 6 | AC-1 unit, M1 |
| R3 | Stale DNS answer survives a domain switch and delays failover (max TTL 300 s) | 2 | 3 | 6 | AC-3 unit, M4 |
| R4 | `KinoPubConfig` snapshot not replaced in `setDomainOverride`: getters disagree or stay on the old endpoint | 2 | 3 | 6 | AC-6 unit |
| R5 | AC-7 fails by construction: Coil's image hosts move from system DNS to DoH | 3 | 2 | 6 | Q4 metric (resolver queries), M5 |
| R6 | DoH built on the DoH-resolving client: recursive lookup | 1 | 3 | 3 | `dohClient_sharesThePoolButResolvesWithoutDoh` |
| R7 | Image requests inherit KinoPub auth, headers or query parameters | 1 | 3 | 3 | AC-4 unit |
| R8 | Coil's service-loaded factory still wins over ours (own pool, system DNS) | 2 | 2 | 4 | M5 per-label counts |
| R9 | Dropping the KinoPub cache raises traffic because it served hits | 2 | 2 | 4 | M3 gates the drop (owner rule) |
| R10 | Legacy `okhttpcache` delete hits the wrong dir or runs on every sweep | 1 | 3 | 3 | AC-5 unit |
| R11 | Evict lambda in `domain/di/modules.kt` bound to another `DnsCache` or a no-op | 2 | 2 | 4 | `apiDomainInteractor_evictsTheSharedDnsCache`, M4 |
| R12 | `mockkObject(KinoPubConfig)` stubs break when getters turn into fields | 2 | 2 | 4 | AC-8 |
| R13 | First image fetch now resolves Koin and builds a Ktor client on startup | 1 | 2 | 2 | Accepted; P2 cold-start timing in Perf |
| R14 | AC-2 flakes on `DnsCache` revalidation (2 s before expiry) | 2 | 2 | 4 | AC-2 test uses `minimumTimeToLive = ZERO`, `revalidateBeforeExpire = ZERO`, 1 s record TTL |
| R15 | Merge with startup-splash-preload (`PuberApp.kt`, `ApiDomainInteractor.kt`) loses the fetcher or the evict | 3 | 1 | 3 | Re-run AC-1, AC-3 tests and M1-M2 after the merge or rebase |
| R16 | Player fast-DNS path keeps `Dns.SYSTEM` | 1 | 2 | 2 | Accepted: unchanged by design |

#### Traceability

Test column: method name as written, then its class. `red` = fails (or does not compile) on 88fa7303; `guard` = green on the commit named in Proves and must stay green.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | `apiClients_shareOneConnectionPoolAndOneDns` (data/di/ApiModuleHttpStackTest) | Resolves KinoPub, IntroDb, TheIntroDb, TMDB from `apiModule` (reflection on `httpClient`, `OkHttpConfig.preconfigured`) plus `createImageHttpClient(get<OkHttpClient>())`; asserts one `connectionPool` and one `dns` instance. Fails if any client or Coil keeps its own `OkHttpClient.Builder()`. Needs `mockkStatic(Base64)` as in `KinoPubApiClientHistoryTest.kt:46`. |
| AC-1 | unit | P0 red | `dohClient_sharesThePoolButResolvesWithoutDoh` (data/di/ApiModuleHttpStackTest) | `(dns as DnsOverHttps).client()` has the app pool and a `dns` that is not the `DnsOverHttps` itself. Fails if the bootstrap `OkHttpClient.Builder().build()` at `DnsOverHttpsFactory.kt:11` stays, or if DoH is built on the app client (R6). |
| AC-2 | unit | P0 red | `lookup_repeatWithinTtl_issuesNoDohQuery` (data/api/network/DnsOverHttpsFactoryTest) | Fake DoH = application interceptor answering DNS wire messages with TTL 1 s, counting calls; two lookups: count 1. Fails if `.cache(cache)` is dropped from the factory or the blocking path bypasses the cache. |
| AC-2 | unit | P0 red | `lookup_afterTtl_issuesOneDohQuery` (data/api/network/DnsOverHttpsFactoryTest) | Lookup, wait ~1.2 s real time (no injectable clock in `RealDnsCache`), lookup: count 2. Fails if the cache keeps an entry past its record TTL. |
| AC-2 | unit | P1 red | `lookup_afterEvictAll_issuesOneDohQuery` (data/api/network/DnsOverHttpsFactoryTest) | Lookup, `cache.evictAll()`, lookup within TTL: count 2. Fails if the factory copies or wraps the cache so `evictAll` misses DoH's entries. |
| AC-3 | unit | P0 red | `resetToDefault_evictsTheDnsCacheBeforeReturning` (domain/interactor/api/ApiDomainInteractorTest) | Counting `evictDnsCache` lambda is 1 when `resetToDefault()` returns. Fails if the evict is missing from `clearDomainSensitiveCaches`. |
| AC-3 | unit | P0 red | `saveCustomDomain_evictsTheDnsCacheBeforeReturning` (domain/interactor/api/ApiDomainInteractorTest) | Same through `applyEndpoint`. Fails if only `resetToDefault` evicts. |
| AC-3 | unit | P0 red | `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails` (domain/interactor/api/ApiDomainInteractorTest) | Throwing evict; `contentCache.clear()` and `detailsPrefetcher.invalidate()` each called once, state returned. Fails if the evict is not inside `clearWithoutFailing`. |
| AC-3 | unit | P1 red | `autoResolve_failoverToAMirror_evictsTheDnsCache` (domain/interactor/api/ApiDomainInteractorTest) | Setup of `autoResolve_failsOverToAMirrorAfterTheCurrentDomainIsReportedUnreachable`; evict count 1 after the switch, 0 for a cache-window answer with `changed = false`. Fails if failover skips the evict. |
| AC-3 | unit | P2 red | `apiDomainInteractor_evictsTheSharedDnsCache` (domain/di/ApiDomainInteractorWiringTest) | Koin: domain module with `single<DnsCache> { mockk(relaxed = true) }` and relaxed boundaries (pattern `domain/di/DetailsPrefetcherWiringTest.kt`); `resetToDefault()` verifies `evictAll()` on that instance. Fails if the lambda is a no-op or resolves another cache (R11). |
| AC-4 | unit | P0 guard | `httpPosterUrl_isRewrittenToHttps` (core/image/HttpsEnforcingInterceptorTest) | Fake `Interceptor.Chain` records the proceeded request: `http://x/p.jpg` becomes `https://x/p.jpg`. Written against the moved `internal` class, body unchanged from `PuberApp.kt:134-145`; fails if the move drops the interceptor's rewrite. |
| AC-4 | unit | P1 guard | `httpsAndNonStringData_passThroughUnchanged` (core/image/HttpsEnforcingInterceptorTest) | `https://` string and a non-String data proceed with the same request. Fails if the rewrite widens. |
| AC-4 | unit | P0 guard | `imageRequest_carriesNoKinoPubAuthHeaderOrQuery` (data/api/network/ImageHttpClientTest) | After `KinoPubApiClient` is built on the same `OkHttpClient`, a GET through `createImageHttpClient(client.newBuilder().addInterceptor(capture-and-answer))` has no `Authorization`, no KinoPub `User-Agent`, the URL query unchanged. Green from plan task 1; fails if KinoPub plugins or interceptors move onto the shared `OkHttpClient` or into the image client. |
| AC-4 | device | P0 | M2 | Posters on Home and Details load over the shared stack. |
| AC-5 | device | P0 | M3 | `app` `cacheHit`, `cacheConditionalHit`, `cacheMiss` and `okhttpcache` entry count recorded; decides the branch. |
| AC-5 | unit | P0 red | `theLegacyHttpCacheIsDeletedOnceAcrossSweeps` (data/cache/CachedPayloadSweeperTest) | Drop branch only. Mirrors `theMediaCacheIsTrimmedOnceAcrossSweeps`: three sweeps, one delete. Fails if the `AtomicBoolean` guard is removed or shared with the media trim. |
| AC-5 | unit | P1 red | `aFailingLegacyHttpCacheDeleteDoesNotStopThePayloadSweep` (data/cache/CachedPayloadSweeperTest) | Drop branch only. Throwing delete; old row still swept. Fails if the delete is not in `runCatchingCancellable`. |
| AC-5 | unit | P0 red | `deleteAction_removesOkhttpcacheAndKeepsSiblingCaches` (data/cache/LegacyHttpCacheTest) | Drop branch only. `@TempDir` with `okhttpcache/` (files), `image_cache/` and a file at root; after `legacyHttpCacheDeleteAction(dir)()` only `okhttpcache` is gone. Fails on a wrong path or a delete of `cacheDir` itself. |
| AC-5 | unit | P2 red | `deleteAction_isANoOpWhenTheDirIsMissing` (data/cache/LegacyHttpCacheTest) | Drop branch only. No throw on a missing dir. |
| AC-6 | unit | P0 guard | `getters_matchTodaysValues_forDefaultAladorLegacyCdnAndCustomMirror` (data/api/config/KinoPubConfigTest) | Parameterized over `null`, `api.alador.space`, `cdn-service.online`, `mirror.example`; all eight public getters equal values written from the base code (custom mirror: main `https://mirror.example/v1/`, oauth and extra from default); default expectations derived from `DEFAULT_API_DOMAIN`. `mockkStatic(Base64)`; `@AfterEach` resets the override. Green on 88fa7303; fails if the memo changes any value. |
| AC-6 | unit | P0 red | `currentEndpoint_returnsTheSameInstanceUntilTheNextOverride` (data/api/config/KinoPubConfigTest) | `assertSame` on two `CURRENT_ENDPOINT` reads for the default and the custom mirror (both rebuilt per read on base); after `setDomainOverride(other)` a different instance with the new domain. Fails if the snapshot is removed. |
| AC-6 | unit | P1 guard | `setDomainOverride_switchesEveryGetterTogether` (data/api/config/KinoPubConfigTest) | Sequence mirror, alador, `null`: after each set every getter reflects the latest override. Fails if a getter reads a stale snapshot (R4). |
| AC-6 | unit | P2 red | `builtInEndpoints_returnsTheSameListOnRepeatedReads` (data/api/config/KinoPubConfigTest) | `assertSame` on two reads. Fails if the list is rebuilt per read. |
| AC-7 | device | P0 | M5 | Resolver queries and new connections, before and after; see Perf. |
| AC-8 | unit | P0 guard | `KinoPubApiClient*Test`, `TmdbApiClientTest`, `data/api/network/*Test`, `ApiDomainInteractorTest` (existing) | Pass on the final commit; `git diff 88fa7303 -- <those files>` changes no existing assertion (allowed: `cacheDir =` argument removal on the drop branch, new tests, the `evictDnsCache` constructor argument). |
| AC-8 | unit | P0 guard | check.full | `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` passes in the worktree. |

#### Bugs

N/A: refactor, no defect to reproduce.

#### Manual checks (device)

Emulator `puber_tv_36` under a lease, worktree dev debug APK via `tools/android-apk-install-preserve`, `adb -s <serial>` throughout, package `com.kino.puber.stage`. Counters: logcat tag `NetStats` (debug build). Navigation by `input keyevent` only.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. `am force-stop`. 2. Launch. 3. Wait for Home rows. | Home renders with rows and posters; no Koin error in logcat. | Crash, ANR, `NoDefinitionFoundException`, logged-out state. |
| M2 | 1. On Home, `DPAD_DOWN`/`DPAD_RIGHT` across two rows. 2. `DPAD_CENTER` on a card. 3. Wait for Details. | Every visited card and the Details poster/backdrop show an image; `NetStats` `image` lines appear on the shared pool. | Blank placeholders, image errors, focus loss after loading. |
| M3 | On the plan task-1 commit: 1. Cold start. 2. Home. 3. `DPAD_CENTER` into a movie Details, `BACK`. 4. A series Details, open a season. 5. Start playback, `BACK` after first frame. 6. Dump the last `NetStats` line; `run-as com.kino.puber.stage ls -la cache/okhttpcache`. | Evidence records `app` `cacheHit`, `cacheConditionalHit`, `cacheMiss` and the dir's entry files. Both hits and 304s zero: drop branch; otherwise keep. | Raw authenticated responses or tokens in evidence. |
| M4 | 1. Cold start, Home loads. 2. Settings: switch API domain to `api.alador.space`. 3. Back to Home. 4. Switch back to default. 5. Home. | After each switch the first request to a host resolved earlier logs one `doh` `callStart`; Home loads on each domain. | Home stuck on an error, requests on the old answer with no DoH query, crash. |
| M5 | Perf session (below), before and after. | Thresholds met. | Any `connectStart` from a client without a label. |
| M6 | Drop branch only: 1. Install the drop build over a build with a non-empty `cache/okhttpcache`. 2. Launch, wait 60 s (or `KEYCODE_HOME`). 3. `run-as ... ls cache`. | `okhttpcache` gone; `image_cache` and the media cache present. | Other cache dirs removed; app data or login lost. |

#### Perf

| Item | Value |
|---|---|
| Metrics | Resolver queries: before `doh.callStart + image.dnsStart`, after `doh.callStart` (Q4). New connections: sum of `connectStart` over `app`, `doh`, `image`. Both from the cumulative `NetStats` line at session end. |
| Session | `am force-stop`, launch, Home settled, `DPAD_CENTER` into Details, `BACK`, open one poster grid and `DPAD_DOWN` x5. Same emulator, dev debug build, same account and domain. |
| Runs | 3 counted runs per build, after one uncounted warm-up run following install (equal Coil disk cache state); median reported. Clearing `cache/image_cache` is a partial data clear and needs owner authorisation. |
| Baseline | On the plan task-1 commit (`NetStats` and the explicit standalone image fetcher), before the stack task; recorded in `baseline.md`. The 88fa7303 build only records `okhttpcache` content (M3). |
| Threshold | After median <= before median for both metrics. |
| Secondary (P2, R13) | `am start -W` TotalTime, median of 5 cold starts: after <= before + 10%. |

### Baseline (qa-device)

Status: NOT MEASURED. Device lease held by another agent.

- Worktree at base 88fa7303. `./tools/agentw :app:assembleDevDebug` succeeded; APK at app/build/outputs/apk/dev/debug/app-dev-debug.apk (36979141 bytes).
- emulator-5554 is listed by `adb-emulators tv`, but its lease is held by another agent (cwd .../startup-splash-preload, pid 3321018, age ~557 s at last check). `acquire emulator-5554 150` waited 150 s and returned resource_busy.
- Nothing was installed, launched or read from the device: no DB version check, no okhttpcache file count/size, no DoH or connection counts. No lease of mine to release.

Needed: retry once the startup-splash-preload lease is released; the base APK is already built.

The comparable before-numbers are measured on the plan's task-1 commit (debug counters), per the design and test design.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | DNS cache: OkHttp DnsCache or a project TTL wrapper? | no | answered | DnsCache, gated by AC-2; wrapper only if it fails |
| Q2 | Merge IntroDb, TheIntroDb and TMDB into one Ktor client? | no | answered | No, share the OkHttpClient only |
| Q3 | If the KinoPub cache is dropped, delete cacheDir/okhttpcache once? | no | answered | Yes, from CachedPayloadSweeper's idle pass |
| Q4 | AC-7 counts resolver queries instead of DoH queries only? | no | answered | Yes |
| Q5 | Per-request Url parsing in shouldSendKinoPubToken? | no | answered | Out of scope |
| Q6 | Fix the TmdbApiClient KDoc claiming a shared HTTP cache? | no | answered | Yes |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-21 | Drop the KinoPub OkHttp cache only if a device session shows no cache hits and no 304s (cache-layer-rework Q3) | owner |
| 2026-11-21 | Q1-Q6 as answered above | owner (standing instruction: recommended) |
| 2026-11-21 | Gate 1: approved as written | owner (standing instruction: recommended) |

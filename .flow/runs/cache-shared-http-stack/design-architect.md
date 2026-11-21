# Design (architect): cache-shared-http-stack

Base 88fa7303. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

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

## Changes by layer

### API / repository

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

### ViewModel state and actions
N/A: no ViewModel changes.

### Screen and DI module

| File | Change | AC |
|---|---|---|
| `data/di/modules.kt:60-64` | Replace with the three `HttpStack` singles; listener factory `NetworkStats::factory` only when `BuildConfig.DEBUG`. | AC-1 |
| `data/di/modules.kt:66-74` | Drop branch: remove `cacheDir = androidContext().cacheDir`. | AC-5 |
| `data/di/modules.kt:108-116` | Drop branch: pass `deleteLegacyHttpCache = legacyHttpCacheDeleteAction(androidContext().cacheDir)`. | AC-5 |
| `domain/di/modules.kt:41-49` | Pass `evictDnsCache = { get<DnsCache>().evictAll() }` (resolve `DnsCache` at construction, call at evict). | AC-3 |
| `PuberApp.kt:127-129` | Task 1: `add(KtorNetworkFetcherFactory(httpClient = { createImageHttpClient(standalone image client) }))`; stack task: `createImageHttpClient(get<OkHttpClient>())` via `org.koin.android.ext.android.get`. `HttpsEnforcingInterceptor` (`:134-145`) moves to `core/image/HttpsEnforcingInterceptor.kt` as `internal` so AC-4 can test it; body unchanged. | AC-1, AC-4, AC-7 |

### Compose content
N/A: no composable changes.

### Resources
N/A: no strings or resources.

## Alternatives rejected

| Alternative | Reason |
|---|---|
| Project TTL wrapper over DoH (option C) as the first choice | Cannot read record TTLs through `Dns.lookup`; kept as AC-2 fallback only. |
| One Ktor client for IntroDb, TheIntroDb and TMDB (option B) | Owner Q2: no. |
| `serviceLoaderEnabled(false)` on the image loader to suppress Coil's default fetcher | Also drops other service-loaded components (coil-video); ordering already makes ours win. |
| Exposing `internal val okHttpClient` on each API client for AC-1 | Adds production surface for one test; reflection in the test suffices. |
| Measuring the baseline on 88fa7303 itself | Coil's and the DoH bootstrap's clients there accept no listener; counts would not compare. |

## Risks

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

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | AC-7 counts DoH queries; Coil leaving system DNS adds DoH queries for image hosts. Count resolver queries as DoH plus system lookups? | Yes: compare `doh.callStart + image.dnsStart` before with `doh.callStart` after; the AC wording changes to "resolver queries". | no |
| 2 | Per-request `Url` parsing in `shouldSendKinoPubToken` (`KinoPubApiClient.kt:1187-1190`) is not covered by any AC. | Leave for network-timeouts-failover or a later run. | no |
| 3 | `TmdbApiClient` KDoc (`data/api/TmdbApiClient.kt:26-30`) claims a shared cache that does not exist. | Fix the sentence in the stack task. | no |

# Research: cache-shared-http-stack

## Request

Split item B2 of cache-layer-rework: "One shared OkHttp/Ktor stack for KinoPub, IntroDb, TheIntroDb and Coil, a TTL-respecting DNS cache in front of DoH, KinoPub HTTP cache removed after a device check, KinoPubConfig values memoised". Owner decision: drop the KinoPub OkHttp HTTP cache only if a device session shows no 304 or cache hits on KinoPub responses.

## Understanding

Make every HTTP caller resolve through one DoH resolver that remembers answers for their TTL and share one OkHttp connection pool; give Coil that stack (today it alone bypasses DoH); remove the 50 MB OkHttp disk cache under KinoPub's Room cache once a device session proves it never serves a hit; and stop rebuilding KinoPub endpoint values on every request. The premise of the parent research needs one correction: IntroDb, TheIntroDb and TMDB already sit on the shared `OkHttpClient`; only Coil and the DoH bootstrap client are separate pools.

## Current state in code

Paths under `app/src/main/java/com/kino/puber/`, base 88fa7303.

| Caller | Location | Stack today |
|---|---|---|
| Shared client | `data/di/modules.kt:60-64` | `OkHttpClient.Builder().dns(DnsOverHttpsFactory.create())`; no cache, no interceptors. |
| DoH | `data/api/network/DnsOverHttpsFactory.kt:10-22` | Own bootstrap `OkHttpClient.Builder().build()` (second pool, second dispatcher); dns.google, `post(true)`, IPv4 only, no `cache(...)`. Every new connection to an unresolved host costs a DoH round trip. |
| KinoPub | `data/api/KinoPubApiClient.kt:132-138,1196` | Ktor `HttpClient(OkHttp)` with `preconfigured = okHttpClient` plus `config { cache(Cache(cacheDir/okhttpcache, 50 MB)) }`; the cache lives only on KinoPub's derived client. 8 plugins (`:140-190`). |
| IntroDb | `data/api/IntroDbAppApiClient.kt:20-37` | Own Ktor `HttpClient(OkHttp)` on the shared client; ContentNegotiation + DefaultRequest base URL. |
| TheIntroDb | `data/api/TheIntroDbApiClient.kt:20-37` | Same shape as IntroDb. |
| TMDB | `data/api/TmdbApiClient.kt:31,88-105` | Same shape, plus bearer header. Its KDoc (`:26-30`) claims it shares the HTTP cache; it does not (cache is added only in KinoPub's `config {}`). |
| Coil | `PuberApp.kt:102-131` | No network fetcher registered, so the service-loaded `KtorNetworkFetcherFactory()` builds `HttpClient()` with the default engine (coil-network-ktor3 3.6.3 bytecode, `KtorNetworkFetcherFactory$lambda$5`): fresh OkHttpClient, system DNS, own pool. Only OkHttp is on the Ktor engine classpath (`app/build.gradle.kts:383`). |
| Player | `ui/feature/player/vm/PlaybackController.kt:622-636` | `okHttpClient.newBuilder()` (shares pool and DoH); `Dns.SYSTEM` when `useFastDns` (`:170,389,632`, default true). |
| Diagnostics | `data/api/network/EndpointProbe.kt:28`, `diagnostics/LatencyProbe.kt:29`, `diagnostics/BoundedDownload.kt:61` | `newBuilder()` of the shared client. |

Ktor engine detail (ktor-client-okhttp-jvm 3.6.1 bytecode, `OkHttpEngine.createOkHttpClient`): each engine derives its client by `preconfigured.newBuilder().dispatcher(Dispatcher())`, cached per `HttpTimeoutConfig`. So every Ktor `HttpClient` gets its own OkHttp `Dispatcher` (thread pool) while the connection pool and DNS stay shared. Four Ktor clients today = four dispatchers on one pool, plus Coil and DoH pools.

KinoPubConfig (`data/api/config/KinoPubConfig.kt`):

| Line | Per-call cost |
|---|---|
| `:23-34` | `defaultEndpoint` getter builds a new `ApiEndpointPreset` and four strings each read. |
| `:51-54`, `:76-80` | `activeEndpoint` resolves the override against `BUILT_IN_ENDPOINTS` each read. |
| `:61-62` | `BUILT_IN_ENDPOINTS` allocates a new list each read. |
| `KinoPubApiClient.kt:1023`, `:1188-1190` | Every request reads `CURRENT_API_DOMAIN`; `shouldSendKinoPubToken` parses two `Url`s per request. |

Domain switch: `domain/interactor/api/ApiDomainInteractor.kt:155-170` (`clearDomainSensitiveCaches`: content cache + prefetcher). No DNS or HTTP cache is cleared. Logout is not domain-bound for DNS.

Tests: `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt:54-58` mocks `KinoPubConfig` with `mockkObject` getters; `data/api/KinoPubApiClient*Test.kt` (4 files), `data/api/TmdbApiClientTest.kt`, `data/api/network/*Test.kt`. No test covers `DnsOverHttpsFactory`, the Koin `apiModule` graph or Coil's fetcher.

History: `okhttpcache` and `preconfigured = okHttpClient` date from 88cdf071 (Release 1.0.0); the IntroDb clients moved to the shared engine in a1a51cca. d52b5800 (cache-storage-bounds) last touched `PuberApp.kt` and `modules.kt`.

## Related work

| Run | Stage | Relation |
|---|---|---|
| cache-layer-rework | split | Parent; B2 scope and seed AC-1, AC-2, AC-9 in its `brief.md`. |
| coil-shared-http, http-stack-consolidation | dropped | Folded in here; no further content. |
| startup-splash-preload | review (worktree) | Conflicts: `PuberApp.kt` (`initDi` now calls `AppStartup.start()`; `newImageLoader` gains `.eventListener(StartupReveal.imageActivity)` next to `components`) and `ApiDomainInteractor.kt` (`autoResolveWorkingDomain` wrapped in a `Mutex`). `modules.kt` untouched. Land B2 after it or rebase on it. |
| cache-storage-bounds | done (d52b5800) | Owns `CachedPayloadSweeper` (`data/cache/CachedPayloadSweeper.kt`), the natural place to delete an orphaned `okhttpcache` dir. |
| telemetry-network | idea | OkHttp `EventListener`; rides on the shared client, so B2 first. |
| network-timeouts-failover | idea | Same files (`KinoPubApiClient.kt` timeouts `:1193-1195`); no goal overlap. |
| telemetry-frames-keys-memory spec | done | `docs/superpowers/specs/2026-10-15-telemetry-frames-keys-memory.md:253` waits on coil-shared-http for a non-creating handle to Coil's memory cache; unrelated to B2's network goal. |

Probable duplicate: none.

## External findings

| Finding | Source |
|---|---|
| OkHttp 5.5.0 (2026-08-16) reworked the DNS API: multiple record types, async results, in-memory caching. 5.5.1 (2026-10-22) is a certificate fix. | https://raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md |
| The project's OkHttp 5.5.1 jars contain public `okhttp3.DnsCache` (`minimumTimeToLive`, `maximumTimeToLive`, `failureTimeToLive`, `revalidateBeforeExpire`, `maxSize`, `evictAll()`, `hitCount`/`networkCount`/`requestCount`) and `DnsOverHttps.Builder.cache(DnsCache)`. Constructor defaults, whether record TTLs are honoured between the min/max bounds, and whether the blocking `lookup()` path uses the cache: unverified. | `okhttp-android-5.5.1`, `okhttp-dnsoverhttps-5.5.1` bytecode (javap) |
| Coil 3 `KtorNetworkFetcherFactory` overloads accept `HttpClient` or `() -> HttpClient` (plus cache strategy, connectivity checker, concurrency strategy). | https://coil-kt.github.io/coil/api/coil-network-ktor3/coil3.network.ktor3/index.html; coil-network-ktor3 3.6.3 bytecode |
| Earlier OkHttp DoH caching relied on the HTTP `Cache` and could not cache POST queries. Superseded by `DnsCache` if it works as named. | https://github.com/square/okhttp/issues/8113 (via cache-layer-rework research) |

## Options

| Option | Content | Trade-offs | Size |
|---|---|---|---|
| A. Share at the OkHttp level, built-in DNS cache (recommended) | One base client; DoH built on it with `DnsCache`; app client = `base.newBuilder().dns(doh)`; Coil gets `KtorNetworkFetcherFactory` with a plain Ktor client on the shared client (keeps `HttpsEnforcingInterceptor`); IntroDb, TheIntroDb and TMDB keep their small Ktor clients; `DnsCache.evictAll()` in `clearDomainSensitiveCaches`; KinoPub `Cache` removed after the device check and its dir deleted once; `KinoPubConfig` resolves the endpoint once per `setDomainOverride`. | Smallest diff, no new caching code; Coil gains DoH (posters on DoH-only networks). Three JSON clients still hold one OkHttp dispatcher each. Depends on `DnsCache` behaviour that needs a unit test to confirm. | M |
| B. A plus one Ktor client for the external JSON APIs | Same as A, and IntroDb, TheIntroDb and TMDB share one Ktor client with per-call base URL and TMDB header. | One dispatcher instead of three; touches three clients and `TmdbApiClientTest` for a small saving. | M |
| C. A with a project DNS cache | Replace `DnsCache` by a `Dns` wrapper over DoH using `core/collections/TypedTtlCache`. | Fully testable with a fake clock; cannot read record TTLs through `Dns.lookup`, so it applies a fixed TTL, which the request does not want. Fallback if `DnsCache` fails its test. | M |

Recommended: A.

## Risks

| Risk | Guard |
|---|---|
| `DnsCache` ignores record TTLs or bypasses the blocking `lookup()` path | Unit test against a fake DoH server before wiring; fallback C. |
| A DNS answer cached past a mirror's IP change delays failover | Evict on domain switch; bound `maximumTimeToLive`. |
| Coil on the shared client inherits KinoPub-specific headers or plugins | Coil's Ktor client gets no plugins; KinoPub plugins stay on KinoPub's client only. |
| Removing the OkHttp cache raises KinoPub traffic if it served hits | Owner rule: device session first (cache `hitCount`/`networkCount` logged in a debug build, or the `okhttpcache` dir holding no entry files). |
| Resolving the shared client from Koin in `newImageLoader` pulls DoH setup into the first image load on startup | Measure on device with AC-9; keep resolution lazy. |
| `mockkObject(KinoPubConfig)` tests break if memoisation changes public getters to fields | Keep the public getter API. |
| Merge conflicts with startup-splash-preload in `PuberApp.kt` and `ApiDomainInteractor.kt` | Land after it or rebase on its branch. |

## Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Use OkHttp 5.5.1's `DnsCache` or a project-owned TTL wrapper? | `DnsCache`, gated by a unit test that it honours record TTLs; fall back to option C only if it fails. |
| 2 | Merge IntroDb, TheIntroDb and TMDB into one Ktor client (option B)? | No; share the `OkHttpClient` only (option A). |
| 3 | When the KinoPub cache is dropped, delete the existing `cacheDir/okhttpcache` (up to 50 MB)? | Yes, once, from `CachedPayloadSweeper`'s idle pass. |

## Sources

- Files: `AGENTS.md`; `docs/architecture/api.md`; `gradle/libs.versions.toml:14,15,25,84-97`; `app/build.gradle.kts:372-404`; code paths in the tables above.
- Runs: cache-layer-rework (`research.md`, `brief.md`), cache-shared-http-stack, coil-shared-http, http-stack-consolidation, startup-splash-preload (`state.md`, `perf-audit-2026-11-21.md` items 1, 6, 10; worktree diff vs master), telemetry-network, network-timeouts-failover, startup-main-thread-io, cache-storage-bounds, telemetry-core.
- Commits: 88cdf071, a1a51cca, d52b5800.
- Bytecode (javap, Gradle cache): ktor-client-okhttp-jvm 3.6.1, okhttp-android 5.5.1, okhttp-dnsoverhttps 5.5.1, coil-network-ktor3-android 3.6.3.
- URLs: https://raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md, https://coil-kt.github.io/coil/api/coil-network-ktor3/coil3.network.ktor3/index.html, https://github.com/square/okhttp/issues/8113.

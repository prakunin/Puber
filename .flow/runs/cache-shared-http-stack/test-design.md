# Test design: cache-shared-http-stack

Base 88fa7303. ACs: `brief.md#acceptance-criteria`; design: `design-architect.md`; owner answers Q1-Q6: `state.md`. Levels per `.flow/roles/test-designer.md`. Test paths under `app/src/test/kotlin/com/kino/puber/`.

## Risk profile

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

## Traceability

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

## Bugs

N/A: refactor, no defect to reproduce.

## Manual checks (device)

Emulator `puber_tv_36` under a lease, worktree dev debug APK via `tools/android-apk-install-preserve`, `adb -s <serial>` throughout, package `com.kino.puber.stage`. Counters: logcat tag `NetStats` (debug build). Navigation by `input keyevent` only.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. `am force-stop`. 2. Launch. 3. Wait for Home rows. | Home renders with rows and posters; no Koin error in logcat. | Crash, ANR, `NoDefinitionFoundException`, logged-out state. |
| M2 | 1. On Home, `DPAD_DOWN`/`DPAD_RIGHT` across two rows. 2. `DPAD_CENTER` on a card. 3. Wait for Details. | Every visited card and the Details poster/backdrop show an image; `NetStats` `image` lines appear on the shared pool. | Blank placeholders, image errors, focus loss after loading. |
| M3 | On the plan task-1 commit: 1. Cold start. 2. Home. 3. `DPAD_CENTER` into a movie Details, `BACK`. 4. A series Details, open a season. 5. Start playback, `BACK` after first frame. 6. Dump the last `NetStats` line; `run-as com.kino.puber.stage ls -la cache/okhttpcache`. | Evidence records `app` `cacheHit`, `cacheConditionalHit`, `cacheMiss` and the dir's entry files. Both hits and 304s zero: drop branch; otherwise keep. | Raw authenticated responses or tokens in evidence. |
| M4 | 1. Cold start, Home loads. 2. Settings: switch API domain to `api.alador.space`. 3. Back to Home. 4. Switch back to default. 5. Home. | After each switch the first request to a host resolved earlier logs one `doh` `callStart`; Home loads on each domain. | Home stuck on an error, requests on the old answer with no DoH query, crash. |
| M5 | Perf session (below), before and after. | Thresholds met. | Any `connectStart` from a client without a label. |
| M6 | Drop branch only: 1. Install the drop build over a build with a non-empty `cache/okhttpcache`. 2. Launch, wait 60 s (or `KEYCODE_HOME`). 3. `run-as ... ls cache`. | `okhttpcache` gone; `image_cache` and the media cache present. | Other cache dirs removed; app data or login lost. |

## Perf

| Item | Value |
|---|---|
| Metrics | Resolver queries: before `doh.callStart + image.dnsStart`, after `doh.callStart` (Q4). New connections: sum of `connectStart` over `app`, `doh`, `image`. Both from the cumulative `NetStats` line at session end. |
| Session | `am force-stop`, launch, Home settled, `DPAD_CENTER` into Details, `BACK`, open one poster grid and `DPAD_DOWN` x5. Same emulator, dev debug build, same account and domain. |
| Runs | 3 counted runs per build, after one uncounted warm-up run following install (equal Coil disk cache state); median reported. Clearing `cache/image_cache` is a partial data clear and needs owner authorisation. |
| Baseline | On the plan task-1 commit (`NetStats` and the explicit standalone image fetcher), before the stack task; recorded in `baseline.md`. The 88fa7303 build only records `okhttpcache` content (M3). |
| Threshold | After median <= before median for both metrics. |
| Secondary (P2, R13) | `am start -W` TotalTime, median of 5 cold starts: after <= before + 10%. |

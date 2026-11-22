# Share one HTTP stack, cache DoH answers, decide the KinoPub HTTP cache, memoise KinoPubConfig - plan

**Spec:** `docs/superpowers/specs/2026-11-21-cache-shared-http-stack.md`
**Goal:** Every HTTP caller (KinoPub, IntroDb, TheIntroDb, TMDB, Coil, DoH) shares one OkHttp connection pool and one TTL-caching DoH resolver that is evicted on a domain switch; the KinoPub HTTP cache stays or goes on device evidence; `KinoPubConfig` resolves its endpoint once per override.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/user1/orca/workspaces/Puber/cache-shared-http-stack && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/user1/orca/workspaces/Puber/cache-shared-http-stack && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Never `./gradlew` in the worktree. `AGENT_GRADLE_SERIAL=1` always (startup-splash-preload builds in parallel).
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: production under `app/src/main/java/com/kino/puber/`, tests under `app/src/test/kotlin/com/kino/puber/`; citations are at base 88fa7303.
- Test names, classes and what each proves: spec section "Test design", Traceability table. Write each test as named there; P0/P1 `red` tests run red before the production change and the result goes in the Red run record.
- `PuberApp.kt` and `ApiDomainInteractor.kt` are also edited by startup-splash-preload: touch only the lines cited here, no reformatting, no import reordering beyond the added/removed lines.
- Out of scope (spec): merging the IntroDb/TheIntroDb/TMDB Ktor clients, `shouldSendKinoPubToken` Url parsing (`data/api/KinoPubApiClient.kt:1187-1190`), timeouts and retries, the player's fast-DNS path (`ui/feature/player/vm/PlaybackController.kt:632-634` stays `Dns.SYSTEM`).
- Order: Task 1, then the device baseline step at its end, which must record its verdict before Task 2 starts; Tasks 2-5 in order.

---

## Tasks

### Task 1: Count network events per client and give Coil an explicit fetcher on today's topology

**Covers:** AC-4 (unit guards), measurement hooks for AC-5 and AC-7
**Files:**
- Create: `data/api/network/NetworkStats.kt` - `object NetworkStats`: `fun factory(label: String): EventListener.Factory` and `fun factoryOrNull(label: String): EventListener.Factory? = if (BuildConfig.DEBUG) factory(label) else null`. One counter set per label (`AtomicInteger`s for `callStart`, `dnsStart`, `connectStart`, `cacheHit`, `cacheConditionalHit`, `cacheMiss`; the `okhttp3.EventListener` methods of the same names). Each event logs one line `Timber.tag("NetStats").d("<label>.<event> " + snapshot)` where the snapshot lists every label's cumulative counts, so the last line holds the session totals.
- Modify: `data/api/network/DnsOverHttpsFactory.kt:10-11` - `fun create(bootstrapListener: EventListener.Factory? = null)`; the bootstrap builder at `:11` adds `eventListenerFactory(bootstrapListener)` when non-null. Everything else at `:12-21` unchanged.
- Modify: `data/di/modules.kt:60-64` - `.dns(DnsOverHttpsFactory.create(NetworkStats.factoryOrNull("doh")))` and the app client adds `NetworkStats.factoryOrNull("app")` as its `eventListenerFactory` when non-null.
- Create: `data/api/network/ImageHttpClient.kt` - `fun createImageHttpClient(okHttpClient: OkHttpClient, listener: EventListener.Factory? = null): HttpClient` = `HttpClient(OkHttp) { engine { preconfigured = listener?.let { okHttpClient.newBuilder().eventListenerFactory(it).build() } ?: okHttpClient } }`; no plugins.
- Create: `core/image/HttpsEnforcingInterceptor.kt` - `internal class HttpsEnforcingInterceptor : coil3.intercept.Interceptor`, body moved verbatim from `PuberApp.kt:134-145`.
- Modify: `PuberApp.kt:127-129` - `components { add(HttpsEnforcingInterceptor()); add(KtorNetworkFetcherFactory(httpClient = { createImageHttpClient(OkHttpClient(), NetworkStats.factoryOrNull("image")) })) }` (a standalone default client: system DNS and its own pool, as Coil's service-loaded fetcher has today); delete `:134-145`; add imports `coil3.network.ktor3.KtorNetworkFetcherFactory`, `okhttp3.OkHttpClient`, the two new functions and the moved class; remove `coil3.intercept.Interceptor` and `coil3.request.ImageResult` (`:10`, `:12`) if unused.
- Test: `core/image/HttpsEnforcingInterceptorTest.kt`, `data/api/network/ImageHttpClientTest.kt`

**Consumes:** `KtorNetworkFetcherFactory(httpClient: () -> HttpClient)` (coil-network-ktor3 3.6.3, `KtorNetworkFetcher.factory(Function0)`); `KinoPubApiClient` constructor `data/api/KinoPubApiClient.kt:105-124`; fixture pattern `data/api/KinoPubApiClientFailureTest.kt:113-139` (test path).
**Produces:** `NetworkStats.factory`, `NetworkStats.factoryOrNull`, `createImageHttpClient(okHttpClient, listener)`, `internal class HttpsEnforcingInterceptor` in `com.kino.puber.core.image`; logcat tag `NetStats` with labels `app`, `doh`, `image`.

- [x] Write `httpPosterUrl_isRewrittenToHttps` and `httpsAndNonStringData_passThroughUnchanged` (HttpsEnforcingInterceptorTest): a fake `Interceptor.Chain` records the request passed to `withRequest`/`proceed` and returns a `mockk<ImageResult>()`; requests via `ImageRequest.Builder(mockk<Context>(relaxed = true)).data(...)`; non-String data = an `Int`.
- [x] Write `imageRequest_carriesNoKinoPubAuthHeaderOrQuery` (ImageHttpClientTest): build a `KinoPubApiClient` on `shared = OkHttpClient()` with the FailureTest fixture mocks (access token stubbed non-null so a leak would show); `createImageHttpClient(shared.newBuilder().addInterceptor(capture).build())`, where `capture` records `chain.request()` and returns a 200 `Response` with an empty body; GET `https://img.example/p.jpg?w=300`; assert no `Authorization`, `User-Agent` not the KinoPub `UserAgentBuilder` value, URL query exactly `w=300`.
- [x] Create the files above; move the interceptor; wire the fetcher and the labels.
- [x] Run the fast gate and the two test classes; commit ("Count network events per client in debug builds and give Coil an explicit image fetcher").
- [ ] **Device baseline (qa-device, conductor dispatches; implementer stops here).** On this commit's dev debug APK, under a lease, install-preserve, `adb -s <serial>`, package `com.kino.puber.stage`: run M3 (spec, Manual checks) and the Perf "before" session (3 counted runs after one warm-up, median of `doh.callStart + image.dnsStart` and of the summed `connectStart` over `app`, `doh`, `image`), plus 5 `am start -W` cold starts (median TotalTime). Record in `/mnt/data/projects/PetProjets/Puber/.flow/runs/cache-shared-http-stack/baseline.md`: the commit sha, `app` `cacheHit`, `cacheConditionalHit`, `cacheMiss`, the `cache/okhttpcache` entry-file count, the perf medians, and the verdict line `KinoPub HTTP cache: DROP` (hits and 304s both zero) or `KinoPub HTTP cache: KEEP`. Evidence passes `tools/mobile-evidence-audit.sh`. If no lease is obtainable, the conductor escalates; Task 2 does not start without the verdict.

**Done when:** fast gate green; HttpsEnforcingInterceptorTest (2) and ImageHttpClientTest (1) green; one commit; `baseline.md` names this commit's sha and carries a DROP or KEEP verdict.

---

### Task 2: Memoise KinoPubConfig per override

**Covers:** AC-6
**Files:**
- Modify: `data/api/config/KinoPubConfig.kt` - `defaultEndpoint` (`:23-33`) becomes `by lazy` (same values); `BUILT_IN_ENDPOINTS` (`:61-62`) returns a `by lazy` list; replace `domainOverride` (`:20-21`) by `@Volatile private var snapshot: Snapshot? = null` with `private class Snapshot(val override: String?, val endpoint: ApiEndpointPreset)`; `activeEndpoint` (`:51-54`) = `snapshot?.endpoint ?: defaultEndpoint`; `CUSTOM_API_DOMAIN` (`:60`) = `snapshot?.override`; `setDomainOverride` (`:72-74`) = `snapshot = Snapshot(domain, domain?.let(::resolveEndpoint) ?: defaultEndpoint)`. `resolveEndpoint` (`:76-80`) and `buildCustomMirrorEndpoint` (`:82-91`) unchanged. Every public getter (`:56-66`) keeps its name, type and `get()` form.
- Test: `data/api/config/KinoPubConfigTest.kt` (new directory)

**Consumes:** nothing new.
**Produces:** same public API; `CURRENT_ENDPOINT` and `BUILT_IN_ENDPOINTS` return stable instances between overrides.

- [x] Write `getters_matchTodaysValues_forDefaultAladorLegacyCdnAndCustomMirror` (`@ParameterizedTest` over `null`, `api.alador.space`, `cdn-service.online`, `mirror.example`; expected values from `KinoPubConfig.kt:23-49,82-91`, default ones built from `KinoPubConfig.DEFAULT_API_DOMAIN`), `currentEndpoint_returnsTheSameInstanceUntilTheNextOverride`, `setDomainOverride_switchesEveryGetterTogether`, `builtInEndpoints_returnsTheSameListOnRepeatedReads`. Getters checked: `CURRENT_API_DOMAIN`, `CURRENT_API_HOST`, `CURRENT_ENDPOINT`, `CUSTOM_API_DOMAIN`, `BUILT_IN_ENDPOINTS`, `MAIN_API_BASE_URL`, `OAUTH_BASE_URL`, `EXTRA_API_BASE_URL`. `@BeforeAll` `mockkStatic(Base64::class)` as in `data/api/KinoPubApiClientHistoryTest.kt:46-47` (test path); `@AfterEach` `KinoPubConfig.setDomainOverride(null)`.
- [x] Run them on the unchanged code: the two guards green, the two `red` ones red; record.
- [x] Implement the memo.
- [x] Run the fast gate, KinoPubConfigTest and `com.kino.puber.domain.interactor.api.ApiDomainInteractorTest` (mockkObject stubs, `ApiDomainInteractorTest.kt:54-64`); commit.

**Done when:** fast gate green; KinoPubConfigTest (4) and ApiDomainInteractorTest green with no edit to ApiDomainInteractorTest.

---

### Task 3: Build one HTTP stack with a cached DoH resolver and put Coil on it

**Covers:** AC-1, AC-2, AC-4 (shared Coil client)
**Files:**
- Create: `data/api/network/HttpStack.kt` - `class HttpStack(val client: OkHttpClient, val dnsCache: DnsCache)`; `companion object { fun create(listeners: ((String) -> EventListener.Factory)? = null): HttpStack }`: `base = OkHttpClient.Builder().build()`; `dnsCache = DnsCache()` (library defaults); `dohClient = base.newBuilder()` + `listeners?.invoke("doh")`; `doh = DnsOverHttpsFactory.create(dohClient, dnsCache)`; `client = base.newBuilder().dns(doh)` + `listeners?.invoke("app")`.
- Modify: `data/api/network/DnsOverHttpsFactory.kt:10-13` - `fun create(client: OkHttpClient, cache: DnsCache): DnsOverHttps`; drop the bootstrap builder at `:11` and Task 1's listener parameter; `.client(client)`, add `.cache(cache)`; `:14-21` unchanged.
- Modify: `data/di/modules.kt:45,60-64` - replace the import and the single by `single { HttpStack.create(if (BuildConfig.DEBUG) NetworkStats::factory else null) }`, `single<OkHttpClient> { get<HttpStack>().client }`, `single<DnsCache> { get<HttpStack>().dnsCache }`.
- Modify: `PuberApp.kt` (Task 1's fetcher line) - `createImageHttpClient(get<OkHttpClient>(), NetworkStats.factoryOrNull("image"))` with `import org.koin.android.ext.android.get`; the `okhttp3.OkHttpClient` import stays (used by `get<OkHttpClient>()`).
- Modify: `data/api/TmdbApiClient.kt:26-30` - KDoc: shares DNS-over-HTTPS and the connection pool; remove "and cache" (owner Q6).
- Test: `data/api/network/DnsOverHttpsFactoryTest.kt`, `data/di/ApiModuleHttpStackTest.kt`

**Consumes:** `okhttp3.DnsCache(minimumTimeToLive, maximumTimeToLive, failureTimeToLive, revalidateBeforeExpire, maxSize)` with `evictAll()` (okhttp-android 5.5.1); `DnsOverHttps.Builder.cache(DnsCache)`, `DnsOverHttps.client()` (okhttp-dnsoverhttps 5.5.1); Task 1's `NetworkStats`, `createImageHttpClient`; private `httpClient` fields at `data/api/KinoPubApiClient.kt:130`, `data/api/TmdbApiClient.kt:23`, `data/api/IntroDbAppApiClient.kt:31`, `data/api/TheIntroDbApiClient.kt:31`.
**Produces:** Koin singles `HttpStack`, `OkHttpClient` (DoH + cache), `DnsCache`; `DnsOverHttpsFactory.create(client, cache)`.

- [x] Write DnsOverHttpsFactoryTest: `lookup_repeatWithinTtl_issuesNoDohQuery`, `lookup_afterTtl_issuesOneDohQuery` (sleep 1.2 s), `lookup_afterEvictAll_issuesOneDohQuery`. Cache: `DnsCache(minimumTimeToLive = Duration.ZERO, revalidateBeforeExpire = Duration.ZERO)`. Fake DoH: an application interceptor on `OkHttpClient.Builder().addInterceptor(fake).build()` that counts calls and answers with the request body bytes (12-byte header + question) with flags set to `0x8180` and ANCOUNT 1, followed by one A answer: name pointer `0xC00C`, type 1, class 1, TTL 1, RDLENGTH 4, address `1.2.3.4` (a public address: DoH filters private ones by default). Content type `application/dns-message`. Host: `media.example`.
- [x] Write ApiModuleHttpStackTest: `apiClients_shareOneConnectionPoolAndOneDns` and `dohClient_sharesThePoolButResolvesWithoutDoh`. `koinApplication { androidContext(context); modules(apiModule, module { single<ICryptoPreferenceRepository> { mockk(relaxed = true) } }) }` with `context = mockk<Context>(relaxed = true)`, `every { context.cacheDir } returns tempDir`, `every { context.getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)`; `mockkStatic(Base64::class)` as in Task 2. For each of the four clients read the private `httpClient` field by reflection, take `(it.engine.config as OkHttpConfig).preconfigured!!`; Coil: same read on `createImageHttpClient(koin.get<OkHttpClient>())`; DoH: `(koin.get<OkHttpClient>().dns as DnsOverHttps).client()`. Assert one `connectionPool` instance across all six and one `dns` across the five app clients; DoH client's `dns` is not the `DnsOverHttps`. Close the application in `finally` (pattern `data/di/EpisodeScheduleRepositoryModuleTest.kt:15-28`, test path).
- [x] Run both classes red (they do not compile against the Task 1 factory signature: record that).
- [x] Implement `HttpStack`, the factory change, the Koin singles, the Coil lambda and the KDoc.
- [x] If an AC-2 test fails against `DnsCache`, stop: record the failing assertion under Blocked (option C, a fixed-TTL wrapper on `core/collections/TypedTtlCache.kt`, needs an owner note per AC-2); do not commit a workaround.
- [x] Run the fast gate, both new classes and `com.kino.puber.data.api.*`; commit.

**Done when:** fast gate green; DnsOverHttpsFactoryTest (3), ApiModuleHttpStackTest (2), ImageHttpClientTest, `KinoPubApiClient*Test`, `TmdbApiClientTest` and `data/api/network/*Test` green; `rg -n "OkHttpClient.Builder\(\)" app/src/main` lists only `HttpStack.kt`; `rg -n "OkHttpClient\(\)" app/src/main` lists nothing.

---

### Task 4: Evict the DNS cache on every domain switch

**Covers:** AC-3
**Files:**
- Modify: `domain/interactor/api/ApiDomainInteractor.kt:50-51` - add the last constructor parameter `private val evictDnsCache: () -> Unit,` (no default).
- Modify: `domain/interactor/api/ApiDomainInteractor.kt:161-164` - first statement of `clearDomainSensitiveCaches`: `clearWithoutFailing { evictDnsCache() }` (`clearWithoutFailing` at `:171-179`). Callers `saveCustomDomain` (`:77`), `autoResolveWorkingDomain` (`:138`), `resetToDefault` (`:150`), `applyEndpoint` (`:185`) need no change.
- Modify: `domain/di/modules.kt:41-49` - `single { val dnsCache = get<DnsCache>(); ApiDomainInteractor(..., evictDnsCache = { dnsCache.evictAll() }) }`.
- Modify (test): `domain/interactor/api/ApiDomainInteractorTest.kt:36-45` - pass `evictDnsCache = { evictCalls++; evictFailure?.let { throw it } }` with `private var evictCalls = 0` and `private var evictFailure: Throwable? = null` reset in `setUp` (`:49-67`); no existing assertion changes.
- Test: `domain/interactor/api/ApiDomainInteractorTest.kt` (4 new), `domain/di/ApiDomainInteractorWiringTest.kt` (new)

**Consumes:** Koin single `DnsCache` (Task 3).
**Produces:** `ApiDomainInteractor(preferences, contentCache, probe, reachability, detailsPrefetcher, evictDnsCache)`.

- [x] Add the constructor parameter (not yet called), the Koin argument and the test fixture argument, so the new tests compile.
- [x] New tests: `resetToDefault_evictsTheDnsCacheBeforeReturning`, `saveCustomDomain_evictsTheDnsCacheBeforeReturning` (the Traceability row says "through `applyEndpoint`"; `saveCustomDomain` reaches `clearDomainSensitiveCaches` directly at `:77`, as `applyEndpoint` does at `:185`), `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails`, `autoResolve_failoverToAMirror_evictsTheDnsCache` (setup of `autoResolve_failsOverToAMirrorAfterTheCurrentDomainIsReportedUnreachable`, `ApiDomainInteractorTest.kt:176-188`: evict count 0 after the first, unchanged resolve and 1 after the failover).
- [x] `apiDomainInteractor_evictsTheSharedDnsCache`: `startKoin`/`stopKoin` as in `domain/di/DetailsPrefetcherWiringTest.kt:21-33` with `interactorModule` plus `single<ICryptoPreferenceRepository> { mockk(relaxed = true) }`, `single<ContentCacheRepository> { mockk(relaxed = true) }`, `single<ItemDetailsRepository> { mockk(relaxed = true) }`, `single { EndpointReachability() }`, `single { OkHttpClient() }`, `single<DnsCache> { mockk(relaxed = true) }`; `mockkStatic(Base64::class)` as in Task 2; `runTest { koin.get<ApiDomainInteractor>().resetToDefault() }`; `verify(exactly = 1) { dnsCache.evictAll() }`.
- [x] Run the five new tests red (the parameter is never called); record.
- [x] Add the `clearWithoutFailing { evictDnsCache() }` call; run the fast gate and both classes; commit.

**Done when:** fast gate green; ApiDomainInteractorTest (existing + 4) and ApiDomainInteractorWiringTest (1) green; `git diff 88fa7303 -- app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt` shows only the fixture argument, the two fields and new tests.

---

### Task 5: Apply the KinoPub HTTP cache verdict

**Covers:** AC-5, AC-8
**Branch:** read the verdict line in `/mnt/data/projects/PetProjets/Puber/.flow/runs/cache-shared-http-stack/baseline.md` (Task 1).

**KEEP:** no code change and no commit; write "KinoPub HTTP cache kept: baseline.md <sha>, cacheHit <n>, cacheConditionalHit <n>" in the Implementation record. The four drop-branch tests below are not written.

**DROP (not taken, verdict KEEP; boxes stay open):**
**Files:**
- Modify: `data/api/KinoPubApiClient.kt` - remove `import okhttp3.Cache` (`:97`), the `cacheDir` parameter (`:107`), the `config { cache(...) }` block (`:136-138`), `CACHE_SIZE` (`:1196`); keep `import java.io.File` (`:99`, used at `:908-911`).
- Modify: `data/di/modules.kt:68` - remove `cacheDir = androidContext().cacheDir,`.
- Create: `data/cache/LegacyHttpCache.kt` - `fun legacyHttpCacheDeleteAction(cacheDir: File): () -> Unit = { File(cacheDir, "okhttpcache").deleteRecursively() }`, KDoc naming the removed 50 MB OkHttp cache; sibling of `mediaCacheTrimAction` (`data/cache/MediaCacheTrim.kt:41-44`).
- Modify: `data/cache/CachedPayloadSweeper.kt` - KDoc `:28-29` names the one-time delete; parameter `private val deleteLegacyHttpCache: () -> Unit = {}` after `trimMediaCache` (`:38`); `private val legacyHttpCacheDeleted = AtomicBoolean(false)` next to `mediaTrimmed` (`:42`); in `sweepOnce` (`:60-64`) a second block of the same shape: `runCatchingCancellable { deleteLegacyHttpCache() }.onFailure { Timber.w(it, "Legacy HTTP cache delete failed") }`.
- Modify: `data/di/modules.kt:110-115` - `deleteLegacyHttpCache = legacyHttpCacheDeleteAction(androidContext().cacheDir),`.
- Modify (tests): remove the `cacheDir =` argument at `data/api/KinoPubApiClientFailureTest.kt:133`, `KinoPubApiClientAuthProbeTest.kt:150`, `KinoPubApiClientDownloadTest.kt:108`, `KinoPubApiClientHistoryTest.kt:206` and in Task 1's `ImageHttpClientTest`; a now-unused `cacheDir` helper parameter may go with it; no assertion changes. `ApiModuleHttpStackTest` keeps its `cacheDir` stub only if still read.
- Test: `data/cache/CachedPayloadSweeperTest.kt` (2 new; extend the `sweeper(...)` helper at `:44-56` with `deleteLegacyHttpCache: () -> Unit = {}`), `data/cache/LegacyHttpCacheTest.kt` (new)

- [ ] Write `theLegacyHttpCacheIsDeletedOnceAcrossSweeps` (mirror `theMediaCacheIsTrimmedOnceAcrossSweeps`, `CachedPayloadSweeperTest.kt:80-93`), `aFailingLegacyHttpCacheDeleteDoesNotStopThePayloadSweep` (mirror `:95-103`), `deleteAction_removesOkhttpcacheAndKeepsSiblingCaches` (`@TempDir`: `okhttpcache/` with two files, `image_cache/` with one, a root file), `deleteAction_isANoOpWhenTheDirIsMissing`; run red; record.
- [ ] Implement; run the fast gate, both classes and `com.kino.puber.data.api.*`; commit.

**Done when (both branches):** the full gate passes on the last commit; `git diff 88fa7303 -- 'app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClient*Test.kt' app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt 'app/src/test/kotlin/com/kino/puber/data/api/network/*Test.kt'` shows no changed assertion (AC-8); DROP: the four new tests green and `rg -n "okhttpcache" app/src/main` lists only `LegacyHttpCache.kt`.

Device verification after Task 5 (M1, M2, M4, M5 "after", M6 on DROP) belongs to the verify stage (qa-device), against `baseline.md`.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- Task 1: dd19f70d "Count network events per client in debug builds and give Coil an explicit image fetcher" (device baseline: `baseline.md`, verdict KEEP).
- Task 2: df2199da "Memoise the KinoPubConfig endpoint per domain override".
- Task 3: 9210b11d "Build one HTTP stack with a cached DNS-over-HTTPS resolver and put Coil on it".
- Task 4: afb4e6ea "Evict the DNS cache on every domain switch".
- Task 5: no code change (KEEP); this record is its only trace.

**Red run** (P0 `red` tests on the unchanged code):
- Task 1: no `red` rows; three guards, green on base by design.
- Task 2: green on base (guards): `getters_matchTodaysValues_forDefaultAladorLegacyCdnAndCustomMirror` (4 cases), `setDomainOverride_switchesEveryGetterTogether`. red: `currentEndpoint_returnsTheSameInstanceUntilTheNextOverride` - AssertionFailedError at the `assertSame` on two reads; red: `builtInEndpoints_returnsTheSameListOnRepeatedReads` - AssertionFailedError at the `assertSame`.
- Task 3: the factory signature `create(client, cache)` does not exist on Task 1's code, so the new classes did not compile there. Run against the new signature with only `.cache(cache)` left out: red: `lookup_afterTtl_issuesOneDohQuery` - expected 2 but was 1; red: `lookup_afterEvictAll_issuesOneDohQuery` - expected 2 but was 1; green on base: `lookup_repeatWithinTtl_issuesNoDohQuery` - DoH's own default cache already answers the repeat, so it only guards `.cache(cache)`'s TTL settings from regressing the repeat. `ApiModuleHttpStackTest` (2) was written after the wiring; no red run (it needs `DnsCache` and the shared stack to exist).
- Task 4: red: `resetToDefault_evictsTheDnsCacheBeforeReturning`, `saveCustomDomain_evictsTheDnsCacheBeforeReturning`, `autoResolve_failoverToAMirror_evictsTheDnsCache` - expected 1 but was 0; red: `apiDomainInteractor_evictsTheSharedDnsCache` - `DnsCache.evictAll()` was not called. green on base: `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails` - the parameter is never called before the production change, so nothing can throw; it pins the `clearWithoutFailing` wrapper.
- Task 5: not applicable (KEEP).

**KinoPub HTTP cache kept:** baseline.md dd19f70d, cacheHit 1, cacheConditionalHit 1. The four drop-branch tests were not written.

**Deviations from the plan:**
- Task 1: `ImageHttpClientTest` checks the User-Agent does not start with `kinopub/` (`UserAgentBuilder` reads `Build.MODEL`); `NetworkStats` shares one listener per label.
- Task 3: `DnsOverHttpsFactoryTest` mocks `PublicSuffixDatabase.Companion.get()` (every host public): DoH consults it, and on the JVM its list is an Android asset that `OkHttp.initialize` cannot supply outside Android. `dohClient_sharesThePoolButResolvesWithoutDoh` asserts the DoH client's `dns` is not a `DnsOverHttps` and does not assert `Dns.SYSTEM`: DoH wraps the client in its own `BootstrapDns`. The two TTL tests assert the immediate repeat (count 1) first, so they fail without the cache.
- Task 3: `PuberApp.kt` needed `import org.koin.android.ext.android.get` and `modules.kt` `import com.kino.puber.BuildConfig`.

**Files touched outside the plan:** none.

**For the reviewers:** `HttpStack` derives the DoH client and the app client from one `base`, so the pool is shared by construction; the Coil client wraps `get<OkHttpClient>()` and adds only the `image` listener. In `ApiModuleHttpStackTest` the engine's `preconfigured` client is read by reflection from the four API clients' private `httpClient` fields.

**Review round 1** (triage-1 rows 1-5, 7; row 6 is qa's): f84af6cd.
- Row 1: `HttpStack.createDnsCache()` = `DnsCache(failureTimeToLive = Duration.ZERO)`; red: `lookup_afterAFailedLookup_asksTheResolverAgain` - the second lookup rethrew the cached `IOException` (DnsOverHttpsFactoryTest.kt:62), green with the fix. The test builds its DoH from the production cache factory.
- Row 2: the DoH client has its own `Dispatcher`, pool still shared; red: `dohClient_sharesThePoolButResolvesWithoutDohOnItsOwnDispatcher` - `assertNotSame` on the dispatchers failed (ApiModuleHttpStackTest.kt:105).
- Row 3: the Coil client is the Koin single `ImageHttpClientQualifier` (`data/di/modules.kt`), and `PuberApp` fetches `get<HttpClient>(ImageHttpClientQualifier)`; `apiClients_shareOneConnectionPoolAndOneDns` now reads that single, so a change to the production wiring breaks the test. No red run: the qualifier does not exist on the old code, the assertion is a guard.
- Row 4: `imageRequest_carriesNoKinoPubAuthHeaderOrQuery` builds its client from `HttpStack.create().client`; a guard, green on base by design.
- Row 5, corrected red-run lines: Task 4 `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails` is now a real red - with `evictDnsCache()` moved out of `clearWithoutFailing` it fails with the `IllegalStateException` of the stub (ApiDomainInteractorTest.kt:124), green when restored. `apiClients_shareOneConnectionPoolAndOneDns` and `lookup_repeatWithinTtl_issuesNoDohQuery` stay guards (the latter: DoH's own cache answers the repeat); the AC-1 pool/dns assertions were written after the wiring and have no red run, because the Task 1 module builds a different client topology that does not compile against them.
- Row 7: the player's client carries a debug-only `player` label (`PlaybackController.createDataSourceFactory`). The `app` counters still include the probes and diagnostics clients (derived from the shared base): qa's evidence must say so on both sides of the comparison.
- Gate: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleDevDebug` BUILD SUCCESSFUL in 5m 2s.
- Reviewers: `HttpStack.createDnsCache` (zero failure TTL, library defaults otherwise); `ImageHttpClientQualifier` single replaces the inline client in `PuberApp`.

**Blocked:** none.


### Rebase onto master 7bcbfd83 (startup-splash-preload merged)

- Conflict, `PuberApp.kt` imports (commit "Count network events per client..."): kept master's imports and this branch's `ApiDomainInteractor`/`OkHttpClient` imports. Master's `.reportingToStartupReveal()` stays after `.components { }`, which carries `HttpsEnforcingInterceptor` and the Coil fetcher on the Koin `ImageHttpClientQualifier` client.
- Master's shared domain walk (`switchToFound`) switches the domain outside the call sites this branch edited, but it ends in `clearDomainSensitiveCaches()`, where the DNS eviction lives; every `setDomainOverride` site (save, found, reset, apply) reaches it. No further change.
- `ApiDomainInteractorTest.interactorWith` (new on master) lacked `evictDnsCache`; it now passes `{ evictCalls++ }`.
- `testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:assembleDevDebug`: BUILD SUCCESSFUL.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

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

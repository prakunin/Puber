# Brief: cache-shared-http-stack

Base 88fa7303. Analyst, 2026-11-21. Builds on `research.md`; re-read at base and holding: `data/di/modules.kt:60-64`, `data/api/network/DnsOverHttpsFactory.kt:10-22`, `data/api/KinoPubApiClient.kt:132-138`, `PuberApp.kt:102-131`, `domain/interactor/api/ApiDomainInteractor.kt:162-170`, `data/api/config/KinoPubConfig.kt:23-80` (paths under `app/src/main/java/com/kino/puber/`).

kind: refactor. size: M. tracks: perf. Device verification: **yes** (networking, DI, startup, image loading, persistence of the HTTP cache dir).

Size reason: one goal (one network stack) crossing DI, network, Coil setup and domain switching; risk areas networking, caches, startup and Koin wiring (`.flow/roles/analyst.md`).

## Request
research.md: Request. Owner rule (state.md: Decisions): drop the KinoPub OkHttp cache only if a device session shows no 304 or cache hit on KinoPub responses.

## Current state
research.md: Current state in code. Correction to the parent seed: IntroDb, TheIntroDb and TMDB already share the app `OkHttpClient`; the separate pools are Coil's default Ktor client and the DoH bootstrap client (`DnsOverHttpsFactory.kt:11`).

## Approach
research.md: Options, A (recommended): one base `OkHttpClient`; DoH built on it with OkHttp 5.5.1 `DnsCache`; Coil gets `KtorNetworkFetcherFactory` over a plugin-free Ktor client on the shared client; `DnsCache.evictAll()` in `clearDomainSensitiveCaches`; `KinoPubConfig` resolves the endpoint once per `setDomainOverride` behind unchanged public getters. Fallback for the DNS cache: option C.

## Acceptance criteria
- AC-1. Given the Koin `apiModule` graph and the Coil image loader, When the KinoPub, IntroDb, TheIntroDb, TMDB, Coil and DoH-bootstrap clients are resolved, Then they share one `ConnectionPool` and the five app clients share one `Dns` instance (unit test).
- AC-2. Given a host resolved through DoH with record TTL T, When it is looked up again before T and after T, Then the first repeat issues no DoH query and the second issues one (unit test against a fake DoH server; if `DnsCache` fails it, option C with a fixed TTL and an owner note).
- AC-3. Given a resolved KinoPub host, When the API domain is switched (`ApiDomainInteractor` override path), Then the DNS cache is empty before the next request, and a failing evict does not stop the content-cache and prefetcher clears (unit test in `ApiDomainInteractorTest`).
- AC-4. Given the shared Coil client, When a poster URL is `http://`, Then `HttpsEnforcingInterceptor` still upgrades or rejects it as today, and no KinoPub header or plugin reaches image requests (unit test); posters load on Home and Details (device observation).
- AC-5. Given a debug build with the current cache, When a device session covers cold start, Home, Details, a series season and playback start, Then the KinoPub cache `hitCount`, `networkCount` and 304 count are recorded in evidence; if hits and 304s are both zero the cache is removed and `cacheDir/okhttpcache` deleted once, otherwise the cache stays (device evidence; unit test for the one-time delete).
- AC-6. Given default, alador, legacy-CDN and a custom mirror override, When the `KinoPubConfig` getters are read, Then values equal today's, and repeated reads return the same `ApiEndpointPreset` instance until the next `setDomainOverride` (unit test).
- AC-7 (perf). Given an emulator cold start through Home, Details and one poster grid, When DoH queries and new connections are counted before and after, Then both numbers are recorded and neither rises (device, `EventListener` or debug log).
- AC-8. Existing `KinoPubApiClient*Test`, `TmdbApiClientTest`, `data/api/network/*Test`, `ApiDomainInteractorTest` pass with assertions unchanged, and the full check passes.

## Risks
research.md: Risks. The one that shapes the plan: `startup-splash-preload` (stage review) edits `PuberApp.kt` `newImageLoader` and `ApiDomainInteractor.kt`; implement on a base that contains it or rebase onto it.

## Open questions
1. DNS cache: OkHttp `DnsCache` or a project TTL wrapper? Recommended: `DnsCache`, gated by AC-2; option C only if it fails. Blocks: no.
2. Merge IntroDb, TheIntroDb and TMDB into one Ktor client (option B)? Recommended: no, share the `OkHttpClient` only. Blocks: no.
3. If the KinoPub cache is dropped, delete the existing `cacheDir/okhttpcache`? Recommended: yes, once, from `CachedPayloadSweeper`'s idle pass (`data/cache/CachedPayloadSweeper.kt`). Blocks: no.

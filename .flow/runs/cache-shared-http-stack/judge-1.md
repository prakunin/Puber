# Gate - cache-shared-http-stack - round 1

**Decision:** CONCERNS
**Head:** 63dc038e   **Base:** 88fa7303

## Done as asked

The diff (`git -C /home/user1/orca/workspaces/Puber/cache-shared-http-stack diff 88fa7303...HEAD`) does what the spec Design asks:

- `HttpStack.kt` builds one base client. DoH runs on a copy of it with its own `Dispatcher` and a `DnsCache(failureTimeToLive = ZERO)`, and the app client is `base.newBuilder().dns(doh)`.
- `DnsOverHttpsFactory.create(client, cache)` no longer builds its own bootstrap client.
- Coil uses `KtorNetworkFetcherFactory` with the Koin single `ImageHttpClientQualifier` (`createImageHttpClient`, no plugins).
- `evictDnsCache` is called first in `clearDomainSensitiveCaches` inside `clearWithoutFailing`.
- `KinoPubConfig` reads a volatile `Snapshot`, and `defaultEndpoint` and `BUILT_IN_ENDPOINTS` are lazy.
- `HttpsEnforcingInterceptor` moved to `core/image` with its body unchanged, and the `TmdbApiClient` KDoc is fixed (Q6).
- AC-5 took the KEEP branch (state.md Decisions): `KinoPubApiClient.kt` and `CachedPayloadSweeper` are untouched.

Nothing in Out of scope is touched. The player keeps `Dns.SYSTEM` and gets only a debug `player` label (`PlaybackController.kt:636`).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `apiClients_shareOneConnectionPoolAndOneDns`, `dohClient_sharesThePoolButResolvesWithoutDohOnItsOwnDispatcher` (ApiModuleHttpStackTest); device labels smoke | Both tests are in the code and green in my check.full run at 63dc038e; evidence "Round 2 / AC-1 smoke" | PASS |
| AC-2 | `lookup_repeatWithinTtl_issuesNoDohQuery` (guard), `lookup_afterTtl_issuesOneDohQuery`, `lookup_afterEvictAll_issuesOneDohQuery`, `lookup_afterAFailedLookup_asksTheResolverAgain` | DnsOverHttpsFactoryTest has 4 tests, all green | PASS |
| AC-3 | `resetToDefault_evictsTheDnsCacheBeforeReturning`, `saveCustomDomain_evictsTheDnsCacheBeforeReturning`, `resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails`, `autoResolve_failoverToAMirror_evictsTheDnsCache`, `apiDomainInteractor_evictsTheSharedDnsCache` | Green. Device M4 shows Home loads on each domain but cannot tell an evict from TTL expiry (evidence M4) | PASS (unit, as the AC states) |
| AC-4 | `httpPosterUrl_isRewrittenToHttps`, `httpsAndNonStringData_passThroughUnchanged`, `imageRequest_carriesNoKinoPubAuthHeaderOrQuery` (built on `HttpStack.create().client`); device M2 | Green; evidence "M2" and "Round 2 / AC-4" (image.call 7-27) | PASS |
| AC-5 | Device M3 counters | Evidence "M3": app hit=1 in every run, cond=1 in 4 of 9, so KEEP. Delete tests and M6 apply only to the drop branch | PASS (keep branch) |
| AC-6 | `getters_matchTodaysValues_forDefaultAladorLegacyCdnAndCustomMirror`, `currentEndpoint_returnsTheSameInstanceUntilTheNextOverride`, `setDomainOverride_switchesEveryGetterTogether`, `builtInEndpoints_returnsTheSameListOnRepeatedReads` | KinoPubConfigTest green | PASS |
| AC-7 | Device M5, metric per state.md Decisions (host lookups) plus Q4 | Evidence "Round 2 / Counted runs": medians before to after are Q4 8 to 6, host lookups 7 to 6, connections 9 to 7. Each side used different hubs, so image counts differ | PASS |
| AC-8 | Existing suites; check.full | `git diff --numstat 88fa7303...HEAD -- app/src/test` deletes 0 lines. The only edit to an existing test is the `evictDnsCache` fixture. My check.full run at 63dc038e: BUILD SUCCESSFUL, 2176 tests, 0 failures, detekt and lint green | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| DnsCache cached failed lookups for 10 s, so a resolver blip could switch the mirror | edge-1 F1 (major), blind-1 F1 | fixed | f84af6cd: `HttpStack.kt:21` `failureTimeToLive = Duration.ZERO`; `lookup_afterAFailedLookup_asksTheResolverAgain` |
| DoH shares the parent Dispatcher | codex-1, triage-1 #2 | fixed | f84af6cd: `.dispatcher(Dispatcher())`, covered by `assertNotSame` |
| Coil production wiring was untested | tests-1 F1 (major) | fixed in part | f84af6cd: Koin single `ImageHttpClientQualifier` asserted in the AC-1 test. The rest is tests-2 F1 below |
| No-auth image test built its own client | blind-1 F3, tests-1 F5 | fixed | ImageHttpClientTest now builds from `HttpStack.create().client` |
| P0 red tests had no red run | tests-1 F2 (major), F4 | fixed | e3a8cdb3: plan record has the red runs and guard re-marks; tests-2 confirms |
| AC-7 measured without image fetches on the network | tests-1 F3 (major) | fixed | 63dc038e: round 2 evidence has image.dns 2-4 in every counted run |
| Debug app counters include derived clients | edge-1 F2 | fixed | `PlaybackController.kt:636` player label; evidence "Which clients count under app" |
| Images resolve only through DoH | blind-1 F2 | rejected, reason accepted | The API client was already DoH-only at base (base `data/di/modules.kt` `.dns(DnsOverHttpsFactory.create())`), so a network that blocks dns.google already broke the app |
| Real-time sleep and an internal okhttp mock in the test | blind-1 F4 (nit) | rejected, reason accepted | `javap okhttp3.DnsCache` (okhttp-android 5.5.1): the constructors take only durations and a size, no clock |
| PuberApp fetcher lambda untested | tests-2 F1 (minor) | accepted minor | One line `get<HttpClient>(ImageHttpClientQualifier)`; the device round 2 image lookups go through DoH |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One pool and a DNS answer cache. M5 shows fewer lookups and connections. Startup TotalTime (P2, R13) is NOT CHECKED reliably: +6.8% on 3 runs under host load 23 (evidence "Secondary") |
| Reliability | Evict sits inside `clearWithoutFailing`. Failures are not cached. DoH has its own dispatcher. The null snapshot falls back to the lazy default |
| Maintainability | Follows the Koin DSL; no dead code is left (the old private interceptor and the bootstrap builder are removed). `NetworkStats` ships in release but has no listener there (`BuildConfig.DEBUG`, `factoryOrNull`) |
| Security / privacy | The image client has no plugins, and the test builds it on the production stack. `mobile-evidence-audit.sh docs/superpowers/evidence`: passed, 55 files. The evidence holds counts only |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

1. The branch conflicts with master in `app/src/main/java/com/kino/puber/PuberApp.kt` (`git merge-tree master 63dc038e`). startup-splash-preload added `.reportingToStartupReveal()` and the `AppStartup` imports next to the new Coil fetcher. `ApiDomainInteractor.kt` and `domain/di/modules.kt` auto-merge. Owner decision: resolve the conflict at merge. Guard R15 then needs check.full and M1-M2 (posters load, no Koin error) on the merged head before ship.
2. During round 1, qa started playback of one series episode for a few seconds (evidence "Mistake note"). The account's watch progress for that title may have changed.
3. Startup cost (R13, P2) was not measured under normal host load.

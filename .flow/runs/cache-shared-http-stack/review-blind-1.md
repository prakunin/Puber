LENS: blind  ROUND: 1  BASE: 88fa7303

### F1 [minor] The shared DNS cache stores failed lookups, so a brief network outage can trigger a switch to a mirror
- Where: app/src/main/java/com/kino/puber/data/api/network/HttpStack.kt:18 (`val dnsCache = DnsCache()`)
- Problem: `DnsCache()` keeps the okhttp 5.5.1 defaults, which I read from the bytecode: min TTL 10 s, max TTL 300 s, failure TTL 10 s, revalidate 2 s, 1000 entries. So a failed DoH lookup is remembered for 10 s. Every client goes through this one resolver, including the endpoint probe (`HttpEndpointProbe(get())`). Before this change, every lookup asked DoH again. The diff does not mention this change.
- Failure scenario: the TV wakes up or Wi-Fi drops, and a request to the current domain fails DNS, so the failure is cached for that hostname. Network comes back. Within 10 s `autoResolveWorkingDomain` runs (home ON_RESUME). The current domain's probe fails on the cached failure. `api.alador.space` is not in the cache, so it resolves and probes fine. The app switches mirrors and saves the switch (`saveApiDomain`), so a working default domain is replaced by a mirror.
- Fix: build the cache with `failureTimeToLive = Duration.ZERO`. Alternatively, evict the cache (or skip the cache) before the domain probe walk in `autoResolveWorkingDomain` and `detectAndSave*`.

### F2 [minor] Image fetches now fail whenever DoH fails, where they used to use the system resolver
- Where: app/src/main/java/com/kino/puber/PuberApp.kt:133-139, app/src/main/java/com/kino/puber/data/api/network/ImageHttpClient.kt:9
- Problem: Coil used to use the default Ktor client from the service loader, which resolves image and CDN hosts through system DNS. Now it is built on the shared `OkHttpClient`, which resolves only through `dns.google` with 8.8.8.8 / 8.8.4.4 as bootstrap and has no fallback. The change is intended, but nothing records or handles the new way it can fail.
- Failure scenario: on a network that blocks or intercepts dns.google or 8.8.x.x but allows the image CDN, posters used to load and now none of them do.
- Fix: confirm this is intended and write it down. If not, give the image client a `Dns` that falls back to `Dns.SYSTEM` when DoH throws.

### F3 [minor] `ImageHttpClientTest` cannot fail for the reason it is named after
- Where: app/src/test/kotlin/com/kino/puber/data/api/network/ImageHttpClientTest.kt (`imageRequest_carriesNoKinoPubAuthHeaderOrQuery`)
- Problem: the test builds a `KinoPubApiClient` on `shared` and then never uses it. `KinoPubApiClient` adds its auth and headers to its own `newBuilder()` copy, so building it cannot change `shared`. The assertion therefore only shows that a plain Ktor client sends no `Authorization` header, which is true whatever the test sets up. The production wiring is never tested: the `PuberApp` lambda passing `get<OkHttpClient>()` plus the "image" listener.
- Failure scenario: someone later attaches the auth interceptor to the shared client inside `HttpStack` or the Koin `single<OkHttpClient>`. Tokens would then go to image CDNs, and this test stays green because it builds its own `OkHttpClient()`.
- Fix: build the client the way production does, from `HttpStack.create().client` resolved through `apiModule`. Assert on what the image client actually sends.

### F4 [nit] `DnsOverHttpsFactoryTest` depends on real time and mocks okhttp internals
- Where: app/src/test/kotlin/com/kino/puber/data/api/network/DnsOverHttpsFactoryTest.kt (`lookup_afterTtl_issuesOneDohQuery`, `declareEveryHostPublic`)
- Problem: the test does `Thread.sleep(1_200)` against a 1 s TTL on the real clock, which makes it slow and timing-sensitive on a loaded CI. It also mocks `okhttp3.internal.publicsuffix.PublicSuffixDatabase.Companion`, which is internal okhttp API and can break on any okhttp upgrade.
- Fix: use the TTL boundary only if a `TimeSource` can be injected; otherwise drop the TTL test and keep the `evictAll` test. Use a private-address test host instead of mocking internals, if DoH allows it.

### Unproven
- app/src/main/java/com/kino/puber/data/api/network/NetworkStats.kt:41 Debug builds log one Timber line, holding every label's counters, for every call/DNS/connect event, so a poster-heavy screen means hundreds of lines. It only matters if debug-build performance or log volume is measured.
- app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:165 Evicting on a domain switch only matters when the new hostname has cached entries (for example switching back to the default after a bad address). The comment's reason ("would keep dialling the old addresses") does not hold, because the old and new domains are different cache keys. It would be a real issue only if switching is supposed to fix a stale address for the same host.

LENS: tests  ROUND: 1  BASE: 88fa7303

Suite state: I ran `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` in the worktree at 780aa9ca and got BUILD SUCCESSFUL. Gradle marked `testDevDebugUnitTest`, detekt and lint analysis UP-TO-DATE, so the result comes from an earlier green run on the same inputs. The last commit after faa99d8e changes only evidence, and the worktree is clean. AC-8 holds: `git diff 88fa7303..780aa9ca -- app/src/test` removes no lines, and the only change to an existing test is the new `evictDnsCache` fixture argument with its two fields.

### F1 [major] AC-1 never tests Coil's production wiring; the test builds the Coil client itself
- Where: app/src/test/kotlin/com/kino/puber/data/di/ApiModuleHttpStackTest.kt:85 (`createImageHttpClient(shared)`); production wiring at app/src/main/java/com/kino/puber/PuberApp.kt:133-137
- Problem: The test passes the shared client into `createImageHttpClient` itself. Nothing reads the `ImageLoader` that `PuberApp.newImageLoader` builds, so the Coil part of AC-1 holds whatever `PuberApp` passes in.
- Failure scenario: The lambda is reverted to Task 1's `createImageHttpClient(OkHttpClient(), ...)`, or the startup-splash-preload merge (risk R15 in the test design) rewrites `newImageLoader`. Coil then has its own pool and system DNS again, and `apiClients_shareOneConnectionPoolAndOneDns` stays green. The device evidence shows image lookups going through DoH, but it states that pool sharing "is proven by the unit test", which does not check it.
- Fix: Move the fetcher factory into a function the test can call, for example `imageFetcherFactory(get: () -> OkHttpClient)` in `core/image`, and use it from `PuberApp`. Then assert in the AC-1 test that the client built from that factory with the Koin client has the shared `connectionPool` and `dns`. Or add a device check that compares connection reuse across the `image` and `app` labels.

### F2 [major] Two P0 `red` tests were recorded green on base and are not marked `guard`
- Where: app/src/test/kotlin/com/kino/puber/data/api/network/DnsOverHttpsFactoryTest.kt:83 (`lookup_repeatWithinTtl_issuesNoDohQuery`); app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorTest.kt:123 (`resetToDefault_stillClearsContentCacheAndPrefetcherWhenDnsEvictFails`); plan Implementation record, "Red run", Tasks 3 and 4
- Problem: The test design lists both as `P0 red`, and the record says both are "green on base".
  - The DoH test cannot fail for the regression its row names ("Fails if `.cache(cache)` is dropped"): DoH's built-in cache already answers the repeat. Its assertion is repeated as the first step of the two sibling TTL tests, which do go red.
  - The evict-failure test was never run against the failure it guards, a call to `evictDnsCache()` outside `clearWithoutFailing`.
- Failure scenario:
  - Remove `.cache(cache)` from `DnsOverHttpsFactory.kt:18`: `lookup_repeatWithinTtl_issuesNoDohQuery` stays green.
  - The evict test has never been seen to fail, so nothing shows it would catch an unwrapped call.
- Fix: Either re-mark `lookup_repeatWithinTtl_issuesNoDohQuery` as `guard` in the test design or delete it as a duplicate of the siblings. Record a red run of the evict-failure test with `evictDnsCache()` called outside `clearWithoutFailing`; it should fail with `IllegalStateException("resolver gone")`. Or re-mark it `guard` with that reasoning.

### F3 [major] AC-7 was measured without any image fetch reaching the network, so its main risk (R5) is untested
- Where: docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md, "M5 perf" and "M5 addendum"; /mnt/data/projects/PetProjets/Puber/.flow/runs/cache-shared-http-stack/qa-report.md, AC-7 row
- Problem: AC-7 covers "one poster grid". The design names a risk that AC-7 fails because Coil moves from system DNS to DoH. In every counted run, before and after, `image.dns` is 0 and the `image` label is missing because Coil's disk cache was warm. The addendum's unit, `app.dns + image.dns` (sum of `dnsStart`), is counted where OkHttp emits `dnsStart`, which is before the `Dns` lookup. That count does not change whether the DoH cache answers or not, so it cannot show more or fewer resolver queries. Owner answer Q4 asked for DoH queries plus Coil's system lookups before, and DoH queries after.
- Failure scenario: Posters on a cold Coil cache add two DoH calls per image host (A and AAAA) where there used to be one system lookup. The M2 deeper-scroll session shows `image dns=4` with doh calls going from 4 to 8, but there is no before run to compare it with. AC-7 is reported PASS without measuring that path.
- Fix: Re-run M5 for both builds with image fetches reaching the network, either on posters never loaded before (an unvisited hub or grid) or after an owner-authorised clear of `cache/image_cache`. Report the Q4 metric (`doh.call + image.dns` before, `doh.call` after) as the primary number.

### F4 [minor] The P0 `red` AC-1 tests have no red run on record
- Where: plan Implementation record, Task 3: "`ApiModuleHttpStackTest` (2) was written after the wiring; no red run"
- Problem: The project rule requires a red run on the unchanged code for each new P0 `red` test. Reasoning against base: the separate bootstrap `OkHttpClient.Builder().build()` at the base `DnsOverHttpsFactory.kt:11` has its own pool, so `dohClient_sharesThePoolButResolvesWithoutDoh` would fail there. The gap is in the record, not in the test, for that half. The Coil half could not go red at all (F1).
- Fix: Record a red run against Task 1's commit, with the shared client taken from that commit's module, or write the reasoning above into the Red run record.

### F5 [minor] ImageHttpClientTest cannot catch the leak its test-design row names
- Where: app/src/test/kotlin/com/kino/puber/data/api/network/ImageHttpClientTest.kt:38-56
- Problem: The row says it "fails if KinoPub plugins or interceptors move onto the shared `OkHttpClient`". The test builds its own `OkHttpClient()`, so an interceptor added in `HttpStack.create` or the Koin single never reaches it. Building `KinoPubApiClient` on that client is inert, because `OkHttpClient` is immutable. The test only fails if `createImageHttpClient` itself adds headers or plugins.
- Failure scenario: Someone adds an auth or `User-Agent` interceptor to `HttpStack`'s `base` builder. Posters then carry the KinoPub token, and this test stays green.
- Fix: Build the client under test from `HttpStack.create().client.newBuilder().addInterceptor(capture)`, or from the `apiModule` Koin `OkHttpClient`, instead of `OkHttpClient()`.

### Unproven
- app/src/test/kotlin/com/kino/puber/data/api/network/DnsOverHttpsFactoryTest.kt:97: a real 1.2 s sleep against a 1 s TTL, which could flake on a loaded host (load average about 23 was recorded). It would only be real if the suite were seen to fail here.
- app/src/main/java/com/kino/puber/data/api/config/KinoPubConfig.kt:53: no test reads the getters with a null snapshot (before any `setDomainOverride`), because `@AfterEach` always sets one. It would matter only if a caller reads the config before `initialize`.

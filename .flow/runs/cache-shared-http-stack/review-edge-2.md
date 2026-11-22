LENS: edge  ROUND: 2  BASE: 88fa7303

No findings.

I reviewed only the round-2 diff `780aa9ca..e3a8cdb3`. I read the code around each hunk in `HttpStack.kt`, `DnsOverHttpsFactory.kt`, `ImageHttpClient.kt`, `NetworkStats.kt`, `modules.kt`, `PuberApp.kt`, `PlaybackController.createDataSourceFactory` and the three changed tests, and checked the round-2 changes against each `also_consider` item. Checks that held up:

- **Failed lookups no longer cached** (`DnsCache(failureTimeToLive = Duration.ZERO)`): the base commit 88fa7303 used DoH with no answer cache at all. A failed lookup was never cached there either, so this adds no new behaviour when the resolver is down.
- **Separate DoH dispatcher**: the connection pool is still shared, because the DoH client is built from the same `base`. The new `assertNotSame` checks the dispatcher split, and the existing `assertSame` on the pool still holds.
- **Coil client as a Koin single** (`ImageHttpClientQualifier`): Coil's `httpClient = { ... }` lambda is read lazily and Koin is already started when `PuberApp` calls it, as the old `get<OkHttpClient>()` there already relied on. A single instance makes it no more likely that several image clients get built. No unqualified `HttpClient` binding conflicts with it.
- **`player` debug label**: `NetworkStats.factory` keeps one set of counters per label (`getOrPut`), so repeated `createDataSourceFactory` calls add to the same `player` counters. Release builds get `null` and attach no listener.
- **Tests**:
  - `lookup_afterAFailedLookup_asksTheResolverAgain` uses the production cache factory. Its `failures` counter is an instance field, and JUnit 5 creates a new instance per test, so it is not shared between tests.
  - `ImageHttpClientTest` now builds a real DoH stack, but its interceptor answers before any DNS lookup, so the test makes no network call.

### Unproven
- `/home/user1/orca/workspaces/Puber/cache-shared-http-stack/app/src/main/java/com/kino/puber/data/api/network/HttpStack.kt:21` If dns.google is unreachable, every lookup waits the full DoH timeout again, because failures are not cached. The base behaves the same, so this is not a regression. It would only matter with device evidence that lookups are slower than on the base during a resolver outage.

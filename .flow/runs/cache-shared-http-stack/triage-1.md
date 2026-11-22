# Review triage - round 1

Run: cache-shared-http-stack   Base: 88fa7303   Reviewed head: 780aa9ca

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial; clean).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, blind F1 | major | `data/api/network/HttpStack.kt:18` | Default DnsCache() caches failed lookups for 10 s; retries, the domain-unreachable report and the endpoint probe then all fail from the cache and a resolver blip can switch the saved mirror | Read: DnsCache() built with defaults; HttpEndpointProbe uses the same client; base had no cache | fix: failureTimeToLive = ZERO, plus a DnsOverHttpsFactoryTest case (first DoH call throws, two lookups, two queries) | fixed (f84af6cd, e3a8cdb3) |
| 2 | codex (rejected as unproven), conductor | minor | `HttpStack.kt:19-24` | DoH client shares the parent Dispatcher; parent calls blocked in DNS could starve the DoH queries | Read: dohClient derived from the base client; fan-out needed is unlikely (codex), but the failure mode would be a full network stall | fix (cheap hardening: own Dispatcher on dohClient, pool kept shared) | fixed (f84af6cd, e3a8cdb3) |
| 3 | tests F1 | major | `ApiModuleHttpStackTest.kt:85`, `PuberApp.kt:133-137` | Coil's production wiring is never tested; the AC-1 test builds the image client itself | Read the test and PuberApp | fix: extract the Coil fetcher factory into a testable function used by PuberApp, assert it shares the Koin client's pool and dns | fixed (f84af6cd, e3a8cdb3) |
| 4 | blind F3, tests F5 | minor | `ImageHttpClientTest.kt:38-56` | The no-auth test builds its own OkHttpClient(), so an interceptor added to the shared stack would not be caught | Read the test | fix: build the client under test from the production stack (HttpStack / apiModule client) | fixed (f84af6cd, e3a8cdb3) |
| 5 | tests F2, tests F4 | major | plan Implementation record; `DnsOverHttpsFactoryTest.kt:83`, `ApiDomainInteractorTest.kt:123`, `ApiModuleHttpStackTest` | P0 red tests recorded green on base or with no red run | Read the record | fix: record red runs (evict-failure test with evictDnsCache outside clearWithoutFailing; AC-1 tests against the task-1 module) or re-mark guard with the reason in the record; lookup_repeatWithinTtl_issuesNoDohQuery recorded as a guard (DoH built-in cache answers the repeat) | fixed (f84af6cd, e3a8cdb3) |
| 6 | tests F3 | major | evidence M5, qa-report AC-7 | AC-7 measured with no image fetch reaching the network; the Coil-to-DoH path (risk R5) is unmeasured | Read the evidence: image.dns 0 in every counted run | fix (qa): re-run M5 for both builds on posters never loaded before (unvisited hub or grid; no cache clear), report the Q4 metric and host lookups | done by qa round 2 (63dc038e): AC-7 PASS with images over the network |
| 7 | edge F2 | minor | `HttpStack.kt:23-26`, `PlaybackController.kt:623` | Debug "app" counters include derived clients (player, probe, diagnostics) | Read: listener on the shared base, inherited by newBuilder() copies | fix: debug-only "player" label on the player's builder; evidence notes which derived clients count under app on both sides | fixed (f84af6cd, e3a8cdb3) |
| 8 | blind F2 | minor | `PuberApp.kt:133-139` | Images now resolve only through DoH, no system fallback | grep: DoH is always on for the API client too (HttpStack.kt:24); no setting turns it off | reject | the whole app already depends on dns.google: on a network that blocks it the API fails before any poster is requested, so the image path adds no new failure |
| 9 | blind F4 | nit | `DnsOverHttpsFactoryTest.kt` | Real-time sleep and a mock of okhttp internal PublicSuffixDatabase | Read | reject | RealDnsCache takes no clock, so the TTL test needs real time (1.2 s); DoH consults the public suffix list for every host and the JVM has no asset for it; both are documented deviations of the implementer |

## Rejected codex claims

- Dispatcher starvation: codex rejected it as unproven; the conductor takes the cheap hardening anyway (row 2).

## Next

- Fix round 1 (rows 1-5, 7) by the implementer; then check.fast, lens edge + tests + codex on the incremental diff; qa re-runs AC-1/AC-2/AC-7 (row 6) on the new head. Unproven lines: 5 (blind 2, edge 1, tests 2).

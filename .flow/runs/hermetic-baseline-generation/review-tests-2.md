# Review tests, round 2

LENS: tests  ROUND: 2  BASE: 5cd3e37e (incremental 7d639a9b..3e79f660)

Extended check.full exit 0 (UP-TO-DATE, results from a run at 3e79f660): testDevDebugUnitTest 2726 tests, testInstrumentationDebugUnitTest (com.kino.puber.profile.*) 19 tests, 0 failures/errors/skipped; detekt and lint green for both variants.
Unpinned prod behaviour pinned by tests: DohResolver.GOOGLE values; DNS_RESOLVER GOOGLE when unpinned and after clearPinnedEndpoint; HttpStack.create() DoH by default, system resolver for null; unpinned API module builds DoH; default GitHub URL asserted through a real KinoPubApiClient with an interceptor; AppUpdateRepository passes its default origin; pinned walk probes once and touches nothing. Every new P0 test has a red run or a guard mark; profilesCoverProdColdStartPaths red on 7d639a9b profiles, green now; no profile/ or baselineprofile rules; no assertion removed or weakened.

### F1 [minor] pinnedEndpointWithResolver_buildsTheAppClientWithDoh cannot detect the failure its traceability row says it catches
- Where: ApiModulePinnedEndpointTest.kt (pinnedEndpointWithResolver_...), HttpStack.kt:46
- Problem: the row says "fails if the pinned resolver is ignored", but the test only asserts dns is DnsOverHttps; it was green against the always-DoH stub; DnsOverHttpsFactoryTest tests the factory alone.
- Failure scenario: HttpStack.kt:46 drops the resolver; all JVM tests green; prod unaffected (default Google), but generation resolves through dns.google, refused by the blocker; only a device run shows it.
- Fix: assert (client.dns as DnsOverHttps).url.toString() - the pinned URL in this test and https://dns.google/dns-query in unpinnedEndpoint_buildsTheAppClientWithDoh.

### Unproven
- BaselineMockBackend.kt:190-193 HTTPS-record and non-A query branch has no self-test.
- modules.kt:97 explicit AppUpdateRepository(get()) binding has no Koin resolution test.

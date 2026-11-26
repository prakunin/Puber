# Review codex, round 2

CODEX: ran: review, adversarial (gpt-6.2-sol)

CONFIRMED:
- [minor] app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineInstrumentationEnvironmentTest.kt:53-56 - hardcodes 18765 in the expected API and DoH URLs, but the app (BASELINE_MOCK_PORT, app/build.gradle.kts:246) and the generator (baselineProfile/build.gradle.kts:23) read puber.baselineMockPort; another port fails the test with a correct pin. Fix: build expected URLs from BuildConfig.BASELINE_MOCK_PORT.

REJECTED:
- Truncated DoH question in BaselineMockBackend.dnsResponse counts as a route hit and throws unrecorded - only OkHttp DnsOverHttps queries reach it; throws in the dispatcher, not a silent pass. Unproven.
- Journey boundary can erase a late violation - window of milliseconds in the same @Before with the target idle; no input shown. Unproven.
- pinnedEndpoint and pinnedResolver are two volatiles with mixed reads - installed once before DI, cleared only in tests. Not hit.
- Unpinned prod behaviour changed - DNS_RESOLVER still DohResolver.GOOGLE (dns.google, 8.8.4.4/8.8.8.8); autoResolveWorkingDomain candidates unchanged unpinned; GitHub base URL defaults to https://api.github.com; no instrumentation-only code in main; defaults pinned by unit tests.

VERDICT: clean

# Review blind, round 1

LENS: blind  ROUND: 1  BASE: 5cd3e37e

### F1 [major] The new profiles drop startup code that every production cold start runs
- Where: app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt:25-29, BaselineInstrumentationEnvironment.kt:20-28; result in app/src/main/generated/baselineProfiles/startup-prof.txt
- Problem: the generating app takes a different startup path: SyntheticAuthPreferences replaces CryptoPreferenceRepository (no token decryption); the pinned endpoint makes modules.kt:74 build no DoH resolver; endpoint probing in ApiDomainInteractor returns early when pinned. mergeIntoMain = true writes this into what prod ships, and prod runs all three on every launch. startup-prof.txt rule counts base -> head: CryptoPreferenceRepository 30 -> 12 (decrypt, secretKey, getAccessToken, getString gone); DnsOverHttps 41 -> 0, okhttp3/internal/dns -33; DnsCache 58 -> 16; ApiDomainInteractor 38 -> 18; EndpointProbe 16 -> 12; AppUpdate 52 -> 13; androidx/tvprovider 97 -> 26 (watching fixture empty, Home-channel publishing never runs). No test guards it; baselineProfileCoversImageTlsAndHlsPaths checks only coil, hls and tls.
- Failure scenario: a paired user cold-starts the prod APK; decrypt/secretKey, the DoH chain and the endpoint probe run interpreted/JIT instead of AOT; startup gets worse with each refresh.
- Fix (any): generate with the real CryptoPreferenceRepository holding seeded synthetic tokens; keep useDoh true in the instrumentation flavor and serve DoH from the mock (dns-query route); give watching/serials non-empty fixtures. Add BaselineProfileContentTest rules requiring CryptoPreferenceRepository;->decrypt, okhttp3/dnsoverhttps/ and ApiDomainInteractor in startup-prof.txt.

### F2 [minor] The new JVM tests run in no configured gate
- Where: app/src/testInstrumentation/.../BaselineNetworkBlockerTest.kt, BaselineFixtureContentTest.kt; .github/workflows/pr-checks.yml:95, release.yml:44
- Problem: testInstrumentation compiles only into test<Instrumentation*>UnitTest; CI runs testProdDebugUnitTest and lintProdDebug only.
- Fix: add :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' to pr-checks.yml (optionally lintInstrumentationDebug).

### F3 [minor] StartupBenchmarks now targets an app that has no backend
- Where: baselineprofile/build.gradle.kts:20-25; StartupBenchmarks.kt:17-21
- Problem: targetAppId is always com.kino.puber.instrumentation; StartupBenchmarks starts no mock, so requests to 127.0.0.1:18765 are refused; KDoc names connectedDevBenchmarkReleaseAndroidTest, which no longer exists.
- Fix: start a BaselineMockBackend in StartupBenchmarks as the generator does; at minimum update the KDoc and note it runs without a backend.

### Unproven
- Makefile:156-161 and generate-baseline-profile.sh preflight still give "keeps app data" as the reason for API 34+/emulator-only.
- BaselineInstrumentationEnvironment.kt:37-50 clearCaches leaves files under filesDir (prefs, DataStore).
- BaselineNetworkBlocker.kt only sees ProxySelector traffic (raw sockets, WebView, system services bypass it).

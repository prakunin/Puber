# Test design: hermetic-baseline-generation

Base 5cd3e37e, round-1 head 7d639a9b. ACs: `brief.md#acceptance-criteria` (11, amended round 2); decisions D1-D19 `design-architect.md#decisions`, D20-D27 `design-architect.md#round-2-delta`; fixes `triage-1.md` #1-#3. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, JVM), `instrumented` (device JUnit in `:baselineprofile`, run inside `make DEVICE=host.docker.internal:5555 baseline-profile`), `device` (observation recorded in evidence), `manual` (static inspection of build outputs and files, no device).

`red`/`guard` on rows without `R3` are against 5cd3e37e (all green at 7d639a9b). Rows marked `R3` (round 3) are against 7d639a9b, the code the round-3 implementer changes.

## Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Egress to a host outside the mock's origins during generation (2026-11-25 ban pattern) | 2 | 3 | 6 | `BaselineNetworkBlockerTest` (unit), journal check per iteration (AC-2), M1 |
| R2 | Fixture carries a real URL or an `imdb`/`trailer`/`subtitles`/`seasons` key that wakes TMDB, IntroDB or trailers | 2 | 3 | 6 | `BaselineFixtureContentTest` (unit), mock `validate()` |
| R3 | Instrumentation classes, control receiver or test CA in a shipped APK or profile (20 `com/kino/puber/profile/` rules per file at base) | 3 | 3 | 9 | `BaselineProfileContentTest` ban, M3 |
| R4 | Journey check never fails (unknown route, non-empty journal or missing route accepted), so R1 goes unseen | 2 | 3 | 6 | `BaselineMockSelfTest` (instrumented), M2 |
| R5 | Thin fixtures leave screens or Coil/TLS/HLS paths cold; release startup regresses | 2 | 2 | 4 | `BaselineProfileContentTest` guards, M4 |
| R6 | Caches serve later iterations, so playlist and `watching/marktime` look missed (D11) | 2 | 2 | 4 | Required-route check per iteration, M1 |
| R7 | `instrumentationRelease` variant does not wire, or dev/prod still generate | 2 | 2 | 4 | M1 step 1 |
| R8 | NSC trust anchor not applied to the IP literal or the `.test` names, or `:baselineprofile` cannot bind the port | 2 | 2 | 4 | M1 |
| R9 | Plain JUnit `BaselineMockSelfTest` skipped by the baseline-profile runner | 2 | 2 | 4 | M2 |
| R10 | Box loses its session on the worktree build (AC-9) | 2 | 2 | 4 | M6 step 1 |
| R11 | `check.fast` compiles neither the flavor nor `:baselineprofile` | 3 | 1 | 3 | M5 |
| R12 | Bypass of `ProxySelector` (raw socket, native client) | 1 | 3 | 3 | `BaselineNetworkSecurityConfigTest` (NSC trusts only the mock root); review grep |
| R13 | Request between verify and next kill lost because `CLEAR` wipes the journal (D12) | 1 | 3 | 3 | `BaselineNetworkBlockerTest.journal_keepsLines_whenNewBlockerOpensSameDir` |
| R14 | Stream ends inside the dwell and triggers `watching/toggle` | 1 | 2 | 2 | Accepted: 24 s stream (D10); an unexpected `watching/toggle` is 404-journaled and visible in M1 |
| R15 | Leaf certificate expires / committed key trips scanners | 1 | 1 | 1 | Accepted: both certificates valid to 2051-01-01, `-test-only` name |
| R16 | Blocker never installed (refactor drops `install`, later `ProxySelector.setDefault`): empty journal proves nothing (triage #2) | 2 | 3 | 6 | `BaselineInstrumentationEnvironmentTest` (unit) |
| R17 | Request after a journey's last iteration never checked: next `@Before` erases it (triage #3) | 2 | 3 | 6 | `BaselineMockSelfTest.lateCheck_*` (instrumented), M1 step 3 |
| R18 | Regenerated profiles drop prod cold-start paths: token read, DoH, endpoint probe, update check, TV home (triage #1; happened at 7d639a9b) | 3 | 2 | 6 | `BaselineProfileContentTest.profilesCoverProdColdStartPaths` (unit) |
| R19 | D22 edit changes what unpinned prod resolves through (URL, bootstrap, DoH off) | 2 | 3 | 6 | AC-10 unit guards; M6 launch through real DoH |
| R20 | D21 pinned walk moves the pin or probes a real built-in endpoint | 2 | 3 | 6 | AC-11 unit rows; blocker as second guard |
| R21 | D24 default GitHub URL changes, so prod update check breaks or hits another host | 2 | 2 | 4 | AC-10 `AppUpdateRepositoryTest`, `KinoPubApiClientGitHubReleaseTest` |
| R22 | Seeded tokens not re-sealed after an empty write; later iterations land on pairing (D20) | 2 | 2 | 4 | `BaselineAuthSeedTest` (unit); required routes fail the iteration |
| R23 | Mock `/dns-query` answer rejected by OkHttp's DoH codec; every API lookup fails | 2 | 2 | 4 | `BaselineMockSelfTest.dnsQuery_*` |
| R24 | GitHub fixture newer than the build: update prompt overlays journeys | 1 | 2 | 2 | Accepted: `tag_name` `v0.0.1` (D24); `BaselineFixtureContentTest.githubRelease_isOlderThanAnyBuild` (P2) |
| R25 | Launcher fetches published artwork outside the blocker | 1 | 2 | 2 | Accepted: artwork is `https://127.0.0.1:<port>`, untrusted root, no DNS (D23) |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | device | P0 guard | M1 | Fails if any journey fails, a profile file is not rewritten, a method rule in `baseline-prof.txt` lacks `H`, or `--dry-run` lists a `Dev`/`Prod` generation task |
| AC-2 | instrumented | P0 red | `BaselineProfileGenerator.startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile` (each iteration runs `JourneyCheck`) | Fails if the journal is non-empty, an unknown route was hit, or a D14/D26 required route is missing; fails if the per-iteration reset (D11) is removed |
| AC-2 | instrumented | P0 red R3 | `BaselineMockSelfTest.lateCheck_fails_whenUnknownRouteServedAfterTheLastIteration`, `lateCheck_fails_whenEgressJournalNotEmpty` | Fails if the check run before each test's reset and in `@AfterClass` ignores late unknown requests or a late journal line |
| AC-2 | instrumented | P1 R3 | `BaselineMockSelfTest.lateCheck_passes_withNoRequiredRoutesHit` | Fails if the late check demands required routes between tests (would fail every run) |
| AC-2 | instrumented | P1 R3 | `BaselineMockSelfTest.startupScenario_requiresFreshHistoryGithubAndDnsQuery` | Fails if a D26 required flag is dropped from Startup, BrowseAndDetails or Rail |
| AC-2 | instrumented | P1 R3 | `BaselineMockSelfTest.dnsQuery_resolvesBothTestHostsToLoopback_throughOkHttpDoh` | Fails if OkHttp's `DnsOverHttps` (bootstrap `127.0.0.1`) cannot decode the mock's answer for `api.baseline.test` and `dns.baseline.test` |
| AC-2 | unit | P1 | `BaselineFixtureContentTest.fixtures_haveNoUrlOutsideMockOrigin`, `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys` | Fails if a fixture (incl. `history.json`, `github_release.json`) holds an `http(s)://` URL not starting with `{{MOCK}}` or a banned key |
| AC-2 | unit | P2 | `BaselineFixtureContentTest.movieFixtures_carryPostersAndHlsStream` | Fails if posters or `videos[0].files[0].url.hls4` are dropped |
| AC-2 | unit | P2 R3 | `BaselineFixtureContentTest.historyFixture_carriesOneUnfinishedMovieWithHttpsWidePoster`, `githubRelease_isOlderThanAnyBuild` | Fails if `history.json` stops matching the `HistoryEntryResponse` shape (`media` with `id`, `number`, `snumber`, `duration`) or passing `ContinueWatchingSource` filters (D25) or the release tag can prompt an update (R24) |
| AC-3 | unit | P0 red | `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenHostIsNotLoopback` | Fails if the selector returns `NO_PROXY` or skips the journal for a real host |
| AC-3 | unit | P0 red | `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers` | Fails if the allow rule matches host only |
| AC-3 | unit | P0 red | `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsMock` | Fails if the blocker refuses `https://127.0.0.1:<port>` |
| AC-3 | unit | P0 red R3 | `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsApiOrDnsTestHost` | Fails if `install(journalDir, allowedOrigins)` (D23) drops either `.test` origin (every run would fail) |
| AC-3 | unit | P0 red R3 | `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenTestHostIsNotAllowed`, `select_returnsDeadProxyAndJournals_whenAllowedTestHostPortDiffers` | Fails if the rule allows any `*.baseline.test` host or any port on an allowed name |
| AC-3 | unit | P0 red R3 | `BaselineInstrumentationEnvironmentTest.configure_installsTheBlockerAsDefaultProxySelector` | After `configure(journalDir)`, `ProxySelector.getDefault().select(URI("https://example.org/"))` gives the dead proxy and a journal line; fails if `configure` stops calling `install` (R16) |
| AC-3 | unit | P1 R3 | `BaselineInstrumentationEnvironmentTest.configure_pinsApiToTheMockHostAndResolver` | Fails if the pin is not `https://api.baseline.test:<port>/` or `KinoPubConfig.DNS_RESOLVER` is not `https://dns.baseline.test:<port>/dns-query` with bootstrap `127.0.0.1` |
| AC-3 | unit | P1 R3 | `BaselineNetworkSecurityConfigTest.baseConfig_trustsOnlyTheMockRootAndRefusesCleartext` | Parses `app/src/instrumentation/res/xml/network_security_config.xml`; fails on `src="system"`/`"user"`, a second anchor, or `cleartextTrafficPermitted` not `false` |
| AC-3 | unit | P1 | `BaselineNetworkBlockerTest.connectFailed_journalsUri` | Fails if `connectFailed` is not journaled |
| AC-3 | unit | P1 | `BaselineNetworkBlockerTest.journal_keepsLines_whenNewBlockerOpensSameDir`, `clear_emptiesJournal` | Fails if the journal lives only in memory or `clear` leaves lines |
| AC-3 | instrumented | P0 red | `BaselineMockSelfTest.unknownRoute_returns404AndIsListedInVerify` | Fails if the mock answers an unknown path with 200 or forgets it in `verify()` |
| AC-3 | instrumented | P0 red | `BaselineMockSelfTest.journeyCheck_fails_whenUnknownRouteServed`, `journeyCheck_fails_whenEgressJournalNotEmpty`, `journeyCheck_fails_whenRequiredRouteMissing` | Fails if `JourneyCheck` ignores any of its three inputs |
| AC-3 | instrumented | P1 | `BaselineMockSelfTest.journeyCheck_passes_whenRequiredRoutesHitAndNothingElse` | Fails if `JourneyCheck` always throws |
| AC-3 | instrumented | P1 R3 | `BaselineMockSelfTest.dnsQuery_answersNxdomainAndListsUnknown_forOtherName` | Fails if `/dns-query` resolves a name other than the two `.test` hosts or leaves it out of `verify().unknownRequests` |
| AC-4 | unit | P0 red | `BaselineProfileContentTest.profilesHaveNoInstrumentationOrGeneratorClasses` | Fails on base (20 `profile/` rules per file); fails if `filterPredicate` (D15) is removed |
| AC-4 | unit | P0 red R3 | `BaselineProfileContentTest.profilesCoverProdColdStartPaths` | Fails if any of the 11 D27 substrings is absent from `baseline-prof.txt` or `startup-prof.txt` (all 0 at 7d639a9b; 1-13 each at base, rechecked) |
| AC-4 | unit | P0 guard | `BaselineProfileContentTest.baselineProfileCoversDetailsPlayerRailTabsAndStartupClasses`, `baselineProfileMethodRulesAreHot`, `startupProfileHasNoDetailsOrPlayerRules`, `baselineProfileHasNoRemovedTopTabClasses` | Fails if mock journeys stop reaching details, player, favorites, history, content list, or the `H` re-mark is lost |
| AC-4 | unit | P1 R3 | `BaselineAuthSeedTest.ensure_sealsSyntheticTokens_whenAccessTokenIsNullOrEmpty`, `ensure_keepsTheStoredTokens_whenAccessTokenIsPresent` | Through `ensure(store: ICryptoPreferenceRepository)` (delegate of `ensure(context)`) over an in-memory fake store; fails if a `contains`-style check skips re-sealing after `clear*` wrote `""` (D20) |
| AC-5 | unit | P1 guard | `BaselineProfileContentTest.baselineProfileCoversImageTlsAndHlsPaths` | Fails if `coil3/network/`, `coil3/decode/`, `androidx/media3/exoplayer/hls/` or `okhttp3/internal/tls/` has no rule |
| AC-5 | manual | P1 | M4 | Records counts and lists drops over 20 % |
| AC-6 | manual | P0 guard | M3 | Fails if either release APK lacks `assets/dexopt/baseline.prof` or holds an instrumentation class or the CA |
| AC-7 | manual | P0 guard | M5 | Fails if unit tests, detekt or lint fail on any variant incl. instrumentation |
| AC-8 | manual | P1 | M7 | Fails if a generation path still names pairing, client secret, `PuberAuth` or `profile.auth`, or the removed source sets exist |
| AC-9 | device | P0 guard | M6 | Fails if the flavor or main-source changes break the dev debug launch to Home on the box |
| AC-10 | unit | P0 guard R3 | `DnsOverHttpsFactoryTest.googleResolver_isDnsGoogleWithBootstrap8844And8888` | Fails if `DohResolver.GOOGLE` differs from `https://dns.google/dns-query`, `8.8.4.4`, `8.8.8.8` |
| AC-10 | unit | P0 guard R3 | `HttpStackTest.create_resolvesThroughDoh_byDefault`, `create_usesSystemDns_whenResolverIsNull`, `create_keepsTheAppEventListenerAndRequestsNoDohOne_whenResolverIsNull` | Fails if the default stops building `DnsOverHttps` or `null` stops giving `Dns.SYSTEM` |
| AC-10 | unit | P0 guard R3 | `ApiModulePinnedEndpointTest.unpinnedEndpoint_buildsTheAppClientWithDoh`, `pinnedEndpoint_buildsTheAppClientWithoutDoh` | Fails if `modules.kt:74` drops DoH unpinned or keeps it for a pin with no resolver |
| AC-10 | unit | P1 red R3 | `ApiModulePinnedEndpointTest.pinnedEndpointWithResolver_buildsTheAppClientWithDoh`, `DnsOverHttpsFactoryTest.create_queriesTheGivenResolverUrl` | Fails if the pinned resolver is ignored (DoH paths cold, R18) |
| AC-10 | unit | P1 R3 | `KinoPubConfigTest.dnsResolver_isGoogle_whenUnpinned`, `clearPinnedEndpoint_restoresTheGoogleResolver` | Fails if `DNS_RESOLVER` keeps a stale pinned resolver after `clearPinnedEndpoint` |
| AC-10 | unit | P0 guard R3 | `KinoPubApiClientGitHubReleaseTest.getLatestGitHubRelease_requestsGitHubApi_byDefault`, `getLatestGitHubRelease_replacesOnlyTheOrigin_whenBaseUrlGiven` | Requested URL recorded by an OkHttp `Interceptor` on the `OkHttpClient` passed to `KinoPubApiClient` (the `KinoPubApiClientSessionEndTest` `Env` pattern); fails if the default is not `https://api.github.com/repos/<owner>/<repo>/releases/latest` or a base URL changes path or `Accept` |
| AC-10 | unit | P0 guard R3 | `AppUpdateRepositoryTest.getAvailableUpdate_asksTheGitHubApiOrigin_byDefault`, `getAvailableUpdate_asksTheGivenOrigin_whenBaseUrlGiven` | Fails if the repository does not pass its base URL (default `GITHUB_API_BASE_URL`) to `getLatestGitHubRelease` |
| AC-11 | unit | P0 red R3 | `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_probesOnlyThePin_whenPinned` (replaces `_probesNothing_`) | Fails on 7d639a9b (zero probes); fails if a second candidate is probed, `changed` is true, or a domain is written or cleared |
| AC-11 | unit | P0 red R3 | `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_returnsNotFound_whenThePinDoesNotAnswer` | Fails on 7d639a9b (`Success`); fails if a built-in endpoint is probed after the pin fails |
| AC-11 | unit | P0 guard | `ApiDomainInteractorPinnedEndpointTest.initialize_keepsThePin_whenPinned`, `saveCustomDomain_writesNothing_whenPinned`, `detectAndSaveWorkingDomain_probesNothing_whenPinned`, `detectAndSaveAlternativeBuiltInDomain_probesNothing_whenPinned`, `switchToBuiltInDomain_keepsThePin_whenPinned`, `resetToDefault_writesNothing_whenPinned` | Fails if a D21 edit removes another mutator's pinned guard |
| AC-11 | unit | P1 guard | `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` | Fails if the pinned walk leaves `inFlightWalk` set, blocking the unpinned walk |

## Failing-first tests

| Test | Fails on | Evidence |
|---|---|---|
| `BaselineProfileContentTest.profilesHaveNoInstrumentationOrGeneratorClasses` | 5cd3e37e | 20 `Lcom/kino/puber/profile/` lines per file (`git -C <wt> show 5cd3e37e:app/src/main/generated/baselineProfiles/baseline-prof.txt`) |
| `BaselineProfileContentTest.profilesCoverProdColdStartPaths` | 7d639a9b | Written first in round 3; green only after D20-D25 and regeneration. Same test passes at 5cd3e37e (counts in the AC-4 row) |
| `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_probesOnlyThePin_whenPinned`, `_returnsNotFound_whenThePinDoesNotAnswer` | 7d639a9b | `ApiDomainInteractor.kt:136` returns `Success(changed = false)` with no probe when pinned |

`BaselineNetworkBlockerTest`, `BaselineInstrumentationEnvironmentTest`, `BaselineAuthSeedTest`, `BaselineNetworkSecurityConfigTest` and the new `BaselineMockSelfTest` methods are red by absence (compile failure) at their reference commit.

## Placement

| Test | Path | Notes |
|---|---|---|
| `BaselineNetworkBlockerTest`, `BaselineFixtureContentTest`, `BaselineInstrumentationEnvironmentTest`, `BaselineAuthSeedTest`, `BaselineNetworkSecurityConfigTest` | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/` | JUnit 5; `@TempDir` for the journal dir; `BaselineInstrumentationEnvironmentTest` restores `ProxySelector.getDefault()` and calls `KinoPubConfig.clearPinnedEndpoint()` in `@AfterEach` (needs `configure(context)` to delegate to `configure(journalDir: File)`); NSC read by relative path like `BaselineProfileContentTest.profileLines` |
| `BaselineMockSelfTest` | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/` | Plain JUnit 4; real `BaselineMockBackend` and `JourneyCheck`; DoH tests use OkHttp `DnsOverHttps` with bootstrap `127.0.0.1` trusting the leaf from `tls/leaf-test-only.pem` |
| `BaselineProfileContentTest` | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt` | Extend; reuse `profileLines` |
| `HttpStackTest`, `DnsOverHttpsFactoryTest` | `app/src/test/kotlin/com/kino/puber/data/api/network/` | Rewrite `useDoh` call sites |
| `ApiModulePinnedEndpointTest` | `app/src/test/kotlin/com/kino/puber/data/di/` | Reuse `startApiModule` |
| `KinoPubConfigTest` | `app/src/test/kotlin/com/kino/puber/data/api/config/` | Extend |
| `KinoPubApiClientGitHubReleaseTest` | `app/src/test/kotlin/com/kino/puber/data/api/` | New; an OkHttp `Interceptor` records the request URL (`KinoPubApiClientSessionEndTest` `Env` pattern) |
| `AppUpdateRepositoryTest` | `app/src/test/kotlin/com/kino/puber/data/repository/` | Extend; `coVerify` on the mocked client's base-URL argument |
| `ApiDomainInteractorPinnedEndpointTest` | `app/src/test/kotlin/com/kino/puber/domain/interactor/api/` | Reuse its probe fake and `assertNothingTouched` |

## Manual checks

Device rules: generation (M1, M2) on `host.docker.internal:5555` with a lease (`ssh mac-emu start`, stop after if no other lease); M6 on the box `192.168.1.110:5555`, `com.kino.puber.stage` only. No step sends a request to the live KinoPub API except M6's single ordinary launch.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. In the worktree with no `.env` and `PUBER_CLIENT_SECRET` unset: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:generateBaselineProfile --dry-run`. 2. `make DEVICE=host.docker.internal:5555 baseline-profile` (emulator not paired). 3. Read `PuberGen` lines per iteration and the late-check lines. 4. `git diff --stat app/src/main/generated/baselineProfiles/`; `grep -c '^[^H].*->'` on `baseline-prof.txt` | 1: only `InstrumentationNonMinifiedRelease` generation tasks. 2: four journeys pass. 3: every iteration: empty journal, no unknown routes, all D14 routes plus D26 (`items/fresh`, `history`, `github/.../releases/latest`, `POST /dns-query`) on home journeys and `.m3u8`, a `.ts`, `watching/marktime` on every playback iteration; a clean late check before each test after the first and in `@AfterClass`. 4: both files rewritten, count 0 | A `Dev`/`Prod` generation task; any journal line; a route missing after the first iteration (cache not reset); a pairing screen or `PUBER_CLIENT_SECRET` refusal; a missing late-check line |
| M2 | In the M1 run's test results (`baselineprofile/build/outputs/androidTest-results/` or the run log), find `BaselineMockSelfTest` | All its methods, incl. `lateCheck_*` and `dnsQuery_*`, passed | The class or a method absent (skipped by the runner: AC-2/AC-3 instrumented rows unproven, blocks) |
| M3 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease :app:assembleDevRelease`; for each APK: `unzip -l` for `assets/dexopt/baseline.prof`; `apkanalyzer dex packages` (or `dexdump`) grepped for `BaselineProfileApp`, `BaselineNetworkBlocker`, `BaselineAuthSeed`, `BaselineProfileControlReceiver`, `com.kino.puber.profile`; `unzip -l` for `baseline_mock_ca` | `baseline.prof` present in both; all greps empty | Any match; a missing `baseline.prof` |
| M4 | Count rule lines per prefix in the regenerated `baseline-prof.txt` and `startup-prof.txt` for every package in `research.md` "Image http->https gap" plus `okhttp3/internal/tls/` and the 11 D27 substrings | Counts recorded next to base; each drop over 20 % listed | A package silently left out |
| M5 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin` | Exit 0 | A task name that does not exist (record the nearest real one) |
| M6 | 1. Refresh the box session with a master dev build and `make DEVICE=192.168.1.110:5555 auth-save`. 2. Lease, `adb connect 192.168.1.110:5555`, `tools/android-apk-install-preserve` the worktree dev debug APK. 3. Launch `com.kino.puber.stage`; screenshot after Home settles | Home with content (API reached through real DoH and the unpinned walk); no crash in `adb -s 192.168.1.110:5555 logcat -b crash` | Pairing screen caused by the build; a domain error screen; crash; touching `com.kino.puber`; uninstall or data clear on install failure |
| M7 | `grep -nE 'CLIENT_SECRET|pair|PuberAuth|profile\.auth'` on `docs/release.md` (profile section), `tools/generate-baseline-profile.sh`, `BaselineProfileGenerator.kt`, the `baseline-profile` target in `Makefile`; `ls app/src/nonMinifiedRelease app/src/*NonMinifiedRelease/AndroidManifest.xml` | No match in the generation scope; paths absent | Matches in auth-save/itest targets counted as failures |

## Perf

N/A: track `perf` is off; profile coverage is checked by M4 and `profilesCoverProdColdStartPaths`.

## UNTESTABLE

None.

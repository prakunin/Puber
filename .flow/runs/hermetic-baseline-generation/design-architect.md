# Design (architect): hermetic-baseline-generation

Base 5cd3e37e. Paths are in the worktree; `app/.../` means `app/src/main/java/com/kino/puber/`. ACs: `brief.md#acceptance-criteria`. Code map and request table: `research.md: 3`.

## Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Variant for the hermetic app | Third flavor `instrumentation` in dimension `buildType` (`app/build.gradle.kts:223-236`): `applicationIdSuffix = ".instrumentation"`, `resValue app_name`, `CLIENT_SECRET`/`TMDB_READ_ACCESS_TOKEN`/`API_DOMAIN_OVERRIDE` overridden to `""` (defaults at `:58-61`), `int BASELINE_MOCK_PORT` from `gradle.properties` `puber.baselineMockPort=18765` | Own package next to `.stage`; no secret can reach the build even when `.env` exists; one port shared by app and mock |
| D2 | Application | `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp : PuberApp` (`PuberApp.kt:69`), set by flavor manifest `android:name` + `tools:replace` over `AndroidManifest.xml:22` | Uses the three merged seams; no main-source change |
| D3 | Hook use | `shouldInitializeRuntime()` (`PuberApp.kt:93`) false in process `:baseline_profile_control`; `configureRuntime()` (`:96`, runs before Koin at `:85`) pins the endpoint (`KinoPubConfig.kt:84`) to `https://127.0.0.1:<port>/{v1,oauth2,}` and installs the blocker; `runtimeModules()` (`:99`, appended last at `:112`) binds `ICryptoPreferenceRepository` to a synthetic store and `IAppUpdateInteractor` to a no-op | Blocker exists before any `OkHttpClient` is built, so every client captures it; pin makes `useDoh` false (`data/di/modules.kt:74`) |
| D4 | Synthetic auth store | Object implementing all 15 methods of `ICryptoPreferenceRepository.kt:3-20` (incl. `saveSessionEndReason`/`getSessionEndReason`/`clearSessionEndReason`); fixed non-empty access/refresh tokens, writes ignored, `getApiDomain() = null`, `getSessionEndReason() = null` | Overrides the only binding (`modules.kt:102`); branch store lacks the three new methods |
| D5 | Egress blocker | Branch `BaselineNetworkBlocker` re-ported: `ProxySelector.setDefault` with a selector that returns `NO_PROXY` only for origin `https://127.0.0.1:<port>` and a dead proxy (`127.0.0.1:9`) plus a journal line for anything else, incl. `connectFailed`. Journal takes a `File` directory (`noBackupFilesDir/baseline_network_journal`), not a `Context` | Fails closed, no DNS for denied hosts (OkHttp resolves only the proxy); `File` makes it a JVM unit test (AC-3) |
| D6 | Second net | Flavor `res/xml/network_security_config.xml` replaces the main one: `base-config cleartextTrafficPermitted="false"`, trust anchors `@raw/baseline_mock_ca` only (no `system`) | A request that bypasses `ProxySelector` cannot complete TLS to a real host, and cleartext is refused before connect |
| D7 | TLS for the mock (Q1 = B) | Two-level: a root CA (cert committed as `app/src/instrumentation/res/raw/baseline_mock_ca.pem`, key discarded after signing) and a leaf (SAN `IP:127.0.0.1`, 25 years) whose cert+PKCS#8 key are committed as `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem`; recipe `tools/make-baseline-mock-cert.sh` (openssl, present in the box) | Discarded root key means nothing else can be minted that the app trusts; leaf key is loopback-only |
| D8 | Mock backend | `BaselineMockBackend` (branch c17847cc) on `mockwebserver3` + `okhttp-tls` (`HeldCertificate.decode`, `HandshakeCertificates`, `useHttps`), both `version.ref = "okhttp"` (`gradle/libs.versions.toml:14`); fixed port; route table per scenario, path match query-insensitive, unknown route = 404 + journaled; serves JSON fixtures, two poster JPEGs and the HLS stream from assets with their content types | Same TLS, Coil and Media3 paths as production (AC-5); unknown routes become visible (AC-2/AC-3) |
| D9 | Fixtures | JSON in `baselineprofile/src/main/assets/baseline/` with a `{{MOCK}}` placeholder replaced by the origin at load; `items/{id}` echoes the requested id; movie items carry `posters` and `videos[0]` with `number`, `duration`, `files[0].url.hls4` (`PlayerInteractor.kt:233-240`); no `imdb`, `trailer`, `subtitles`, `seasons`. `validate()` fails on any URL not starting with the mock origin and on those keys | Keeps TMDB, IntroDB and trailers off by construction (`research.md: 3`, "Off by construction") |
| D10 | Stream (Q2) | HLS, H.264 + AAC, 320x180, 24 s, 4 s TS segments, under 300 KB, plus two poster JPEGs; built once with ffmpeg on the Mac host by `tools/make-baseline-mock-media.sh` (exact commands inside), outputs committed under `baselineprofile/src/main/assets/baseline/media/` | Ends well after the 5 s dwell, so `checkAutoMarkWatched` (`PlayerVM.kt:1942`, threshold `:2062`) and `onPlaybackEnded` (`:1389`) stay idle |
| D11 | Per-iteration reset | At the start of every `collect` iteration: `killProcess()`, then broadcast `CLEAR` to `BaselineProfileControlReceiver` (own process, `android:permission="android.permission.DUMP"`, exported); it deletes every database in `databaseList()` and the contents of `cacheDir` and `externalCacheDir` | Per iteration, not per journey, because `CacheDataSource` (`PlaybackController.kt:623`) and `okhttpcache` (`KinoPubApiClient.kt:145`) would serve later iterations and hide the playlist (AC-2). `databaseList()` avoids touching private `PuberDatabase.NAME` (`PuberDatabase.kt:34`) and also drops the Media3 index DB (`modules.kt:147`) |
| D12 | Journal lifetime | Egress journal cleared once per test (`@Before`), never by `CLEAR`; the mock clears matched-route counts per iteration but keeps unknown requests for the whole test | A late request between one iteration's verify and the next kill is still caught |
| D13 | Per-iteration check | At the end of every iteration, before the library dumps the profile and without killing the app: wait up to 15 s for the scenario's required routes, then 500 ms quiescence, then `VERIFY` broadcast (journal must be empty) and `mock.verify()` (no unknown, no missing required); log both to tag `PuberGen`; `check` fails the test. Replaces `requirePairedApp` (`BaselineProfileGenerator.kt:183-192`) | Branch force-stopped before verifying, which would drop the exit `watching/marktime` (`PlayerVM.kt:2016` → `KinoPubApiClient.kt:515`) and cut the iteration's profile |
| D14 | Required routes | Startup: `items/hot`, `watching/serials`. Browse: + `items/{id}`, `items/similar`, one poster. Rail: + `history`, `genres`, `items`. Playback: `items/{id}`, playlist `.m3u8`, one `.ts` segment, `watching/marktime` | AC-2 names playlist and marktime per playback iteration; the rest prove each journey reached its screens |
| D15 | Profile filtering | `collect(..., filterPredicate = { "com/kino/puber/profile/" !in it })` on every journey | Branch 5709c601: the consumer `filter {}` did not drop these classes; the control process is profiled too (benchmark 1.4.0-alpha02+) |
| D16 | Gradle wiring | Replace `app/build.gradle.kts:438-445` with `baselineProfile { variants { create("instrumentationRelease") { from(project(":baselineprofile")) } } }` inside the block at `:446-453`; `:baselineprofile` flavors become only `instrumentation` (`baselineprofile/build.gradle.kts:18-28`) with runner args `targetAppId=com.kino.puber.instrumentation` and `baselineMockPort` | Only `InstrumentationNonMinifiedRelease` generates (AC-1) |
| D17 | API level | Generator `@Before requireInPlaceReset` (`BaselineProfileGenerator.kt:28-34`) deleted; Makefile (`Makefile:156`) and script (`tools/generate-baseline-profile.sh:47-50`) keep their API 34 refusal, wording unchanged | Pairing reason is gone; 34 is the level generation is verified on, and changing it is not asked for |
| D18 | Static gate (Q3) | `check.full` gains `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug`; `detektAll` (`app/build.gradle.kts:342-346`) and PR workflows unchanged | Instrumentation sources do not vary by build type; `--tests` avoids running the whole unit suite twice. The `.flow/config.yaml:13` edit is the conductor's |
| D19 | Removal | Delete `app/src/nonMinifiedRelease/`, `app/src/devNonMinifiedRelease/AndroidManifest.xml`, `app/src/prodNonMinifiedRelease/AndroidManifest.xml`; script steps `:12-17`, `:20-22`, `:53-86`, `:102-103`, `:117-121`, `:130`; `PuberAuth` and `keystore2` out of the logcat filter (`:109`) | AC-8; absorbs hardening F1 and the false warning |

## Changes by layer

N/A for ViewModel state, screen/DI module and Compose content: no main-source change.

| Layer | File | Change |
|---|---|---|
| Build | `app/build.gradle.kts:223-236` | D1 flavor |
| Build | `app/build.gradle.kts:438-453` | D16 |
| Build | `gradle.properties` | `puber.baselineMockPort=18765` |
| Build | `gradle/libs.versions.toml:88` (next to `okhttp-doh`) | `mockwebserver3`, `okhttp-tls` |
| Build | `baselineprofile/build.gradle.kts:18-28`, `:41-46` | D16 flavor; deps `mockwebserver3`, `okhttp-tls` |
| App runtime (instrumentation flavor) | `app/src/instrumentation/AndroidManifest.xml` (new) | D2 application name; D11 receiver in `:baseline_profile_control` |
| App runtime | `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt` (new) | D3, D4, no-op `IAppUpdateInteractor` (`domain/interactor/update/IAppUpdateInteractor.kt:8`, `internal`, same module) |
| App runtime | `.../profile/BaselineInstrumentationEnvironment.kt` (new) | Pinned `ApiEndpointPreset` (`KinoPubConfig.kt:5-11`), origin string, D11 reset |
| App runtime | `.../profile/BaselineNetworkBlocker.kt` (new) | D5 selector + `BaselineNetworkJournal(dir: File)` |
| App runtime | `.../profile/BaselineProfileControlReceiver.kt` (new) | `CLEAR` (D11, journal untouched), `VERIFY` (result `-1` empty, `2` with lines) |
| Resources | `app/src/instrumentation/res/xml/network_security_config.xml`, `res/raw/baseline_mock_ca.pem` (new) | D6, D7; main config `res/xml/network_security_config.xml:28-32` untouched |
| Generator | `baselineprofile/src/main/AndroidManifest.xml` (new, only if the merged test manifest lacks it) | `android.permission.INTERNET` for the server socket |
| Generator | `.../baselineprofile/BaselineMockBackend.kt`, `BaselineProfileTestControl.kt` (new) | D8, D9, D12-D14; `JourneyCheck` as a pure function over (receiver output, mock verification) |
| Generator | `.../baselineprofile/BaselineProfileGenerator.kt` | Mock started in `@BeforeClass`, closed in `@AfterClass`; each journey wrapped with D11 + D13 + D15; `:28-34`, `:144-148` KDoc, `:179-192` removed |
| Generator | `baselineprofile/src/main/assets/baseline/**` (new) | D9 fixtures, D10 media, D7 leaf |
| Tooling | `tools/generate-baseline-profile.sh` | D19; default target package irrelevant once `MAIN_ACTIVITY`/install go |
| Tooling | `tools/make-baseline-mock-cert.sh`, `tools/make-baseline-mock-media.sh` (new) | D7, D10 recipes |
| Tooling | `Makefile:141-179` | Comment only if it names pairing; logic unchanged (F2-F5 stay in `baseline-generation-hardening`) |
| Docs | `docs/release.md:99-133` | Rewritten: one command, no secrets or account, emulator API 34+, mock + journal checks, `H` handling (kept), CA expiry date and both recipes |
| Profiles | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | Regenerated on `host.docker.internal:5555` |

### Tests

| Test | Where | Covers |
|---|---|---|
| `BaselineNetworkBlockerTest` (JVM) | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/` | AC-3 blocker: non-loopback URI → dead proxy + journal line; mock origin → `NO_PROXY`; other loopback port → denied; `connectFailed` journaled; clear empties |
| `BaselineMockSelfTest` (device, plain JUnit) | `baselineprofile/src/main/java/.../` | AC-3 mock: unknown route → 404 and listed in `verify()`; `JourneyCheck` throws on it and on a planted non-empty `VERIFY` output; runs on an ephemeral port inside every `make baseline-profile` |
| `BaselineProfileContentTest` | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:36-44` | AC-4: add `com/kino/puber/profile/` and `com/kino/puber/baselineprofile/` to the banned list, both files |

## Alternatives rejected

- Plain-HTTP mock (option A): Coil network/decode and OkHttp TLS rules go cold (AC-5).
- Loopback exception in `HttpsEnforcingInterceptor` (option C): reopens main code; TLS rules still cold.
- Committing the root CA key: anyone with the repo could mint certificates the instrumentation app trusts.
- `pm clear` per iteration: also wipes the journal and preferences, and its interaction with the library's profile dump is untested.
- Proxying denied requests to the mock instead of a dead proxy: removes the receiver but sends request bytes somewhere that may be down between journeys.
- Making `PuberDatabase.NAME` internal (branch): a main-source change `databaseList()` makes unnecessary.
- Keeping `dev`/`prod` flavors in `:baselineprofile`: their generator would run against a real backend.

## Risks

| Risk | Where it lives | Mitigation |
|---|---|---|
| Egress that `ProxySelector` does not see (raw socket, native client, a client built before `configureRuntime`) | `BaselineProfileApp.configureRuntime`; clients in `TmdbApiClient.kt:88`, `IntroDbAppApiClient.kt:31`, `TheIntroDbApiClient.kt:31`, `ImageHttpClient.kt:12`, `PlaybackController.kt:623` | Install before Koin (`PuberApp.kt:85`); D6 refuses cleartext and untrusted TLS; review greps for `Socket(`, `openConnection`, `HttpURLConnection`, `WebView` in the diff and main |
| A required route never arrives on the emulator (slow cold start, marktime still draining) and the run fails or flakes | `BaselineProfileTestControl` D13 | 15 s wait per iteration; failure message lists matched, missing and unknown routes |
| Instrumentation code or test CA in a shipped APK or profile | `app/src/instrumentation/**`, profile files | Source set only; D15; AC-4 test; AC-6 APK inspection of `assembleDevRelease`/`assembleProdRelease` |
| `variants { create("instrumentationRelease") }` does not resolve the dependency in baselineprofile 1.5.0 (`devBaselineProfile(...)` did not, per `docs/superpowers/plans/2026-11-23-release-startup-compilation.md:247`) | `app/build.gradle.kts:446-453` | Fallback: the existing `configurations.configureEach` pattern with `instrumentationReleaseBaselineProfile`; `--dry-run` decides (AC-1) |
| NSC trust anchor not applied for an IP literal host, so the app rejects the mock | flavor `network_security_config.xml` | Anchor lives in `base-config`, not a `domain-config`; first device run shows it |
| `:baselineprofile` process cannot bind the port (no `INTERNET` in the merged test manifest, or 18765 taken) | `baselineprofile/src/main/AndroidManifest.xml` | Check merged manifest; port is a Gradle property |
| `StartupBenchmarks` now targets `com.kino.puber.instrumentation` with no mock, and its KDoc names `connectedDevBenchmarkReleaseAndroidTest` (`StartupBenchmarks.kt:19-20`) | `StartupBenchmarks.kt` | Left to `startup-benchmark-entry` (Open questions) |
| Thin fixtures (one movie type, no series/seasons) leave paths cold; release startup regresses against today's real-backend profile | fixtures | AC-5 per-package counts against `research.md: Image http->https gap`; drops over 20 % listed |
| Committed private key trips secret scanners (`tools/mobile-evidence-audit.sh:94`) | `baselineprofile/src/main/assets/baseline/tls/` | `-test-only` name, comment in the recipe, never placed in an evidence dir |
| Build blind spot: `check.fast` compiles neither the flavor nor `:baselineprofile` | plan constraints | Every task touching them also runs `./tools/agentw :app:compileInstrumentationNonMinifiedReleaseKotlin :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin` |
| Leaf certificate expires | `tools/make-baseline-mock-cert.sh` | 25-year validity; expiry date in `docs/release.md` |
| AC-8 grep for `pair` hits unrelated Makefile targets (`Makefile:57-58`, `:117`, `:181-200` auth-save/itest) | `Makefile` | AC-8 reads "for generation": the grep is scoped to the `baseline-profile` target and its comment |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q-D1 | `StartupBenchmarks` retargets to the instrumentation package without a mock; fix it here? | No: `startup-benchmark-entry` owns it; this run only records it in `docs/release.md` | no |


## Round 2 delta

Inputs: `triage-1.md` #1 and #6, `review-blind-1.md` F1, `review-edge-1.md` F2, `review-tests-1.md` F4. Reviewed head 7d639a9b. Paths as in round 1; `profile/` is `app/src/instrumentation/java/com/kino/puber/profile/`, `mock` is `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt`.

### Lost cold-start paths (#1)

Rule lines in the committed profiles, base 5cd3e37e -> head 7d639a9b (same counts in `baseline-prof.txt` and `startup-prof.txt`):

| Prod path | Guard rule (substring) | base | head | Why it is cold at head |
|---|---|---|---|---|
| Token read | `CryptoPreferenceRepository;->decrypt`, `;->secretKey` | 1, 1 | 0, 0 | `SyntheticAuthPreferences` replaces the store (`profile/BaselineProfileApp.kt:25-30`, `:50-80`) |
| DoH | `DnsOverHttpsFactory;->create`, `okhttp3/dnsoverhttps/DnsOverHttps;->`, `okhttp3/dnsoverhttps/BootstrapDns;->lookup` | 1, 4, 1 | 0, 0, 0 | Pin sets `useDoh = false` (`data/di/modules.kt:74`, `HttpStack.kt:34-39`); an IP-literal host skips `Dns.lookup` anyway (OkHttp 5.5.1 `RouteSelector` calls `canParseAsIpAddress` first, checked with `javap`) |
| Endpoint probe | `ApiDomainInteractor;->walkForWorkingDomain`, `HttpEndpointProbe;->isReachable` | 1, 1 | 0, 0 | `autoResolveWorkingDomain` returns at `ApiDomainInteractor.kt:136` when pinned; prod walks on every cold start (`AppStartup.kt:62`) |
| Update check | `AppUpdateInteractor;->checkForUpdate`, `AppUpdateRepository;->getAvailableUpdate` | 2, 1 | 0, 0 | `NoOpAppUpdateInteractor` (`profile/BaselineProfileApp.kt:82-104`); prod runs it at `UpdatePromptVM.kt:60` (default on, `AppUpdatePreferencesRepository.kt:11`) against `https://api.github.com` (`KinoPubApiClient.kt:959-965`) |
| TV home channel | `androidx/tvprovider/media/tv/PreviewProgram`, `AndroidTvHomePublisher;->publish` | 13, 1 | 0, 0 | `ContinueWatchingSource.load` reads `history`, not `watching` (`ContinueWatchingSource.kt:15`); `history.json` is empty, so nothing is published (blind F1 named the wrong fixture) |

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D20 | Token store | Real `CryptoPreferenceRepository`: drop `SyntheticAuthPreferences` and its binding. New `profile/BaselineAuthSeed.ensure(context)`, called first in `configureRuntime()`: when `CryptoPreferenceRepository(context).getAccessToken()` is null or empty, it seals `baseline-access-token`, `baseline-refresh-token`, username `baseline-profile` with the public save methods (`CryptoPreferenceRepository.kt:37-53`) | Prod read path (`getString`/`decrypt`/`secretKey`, `:80-149`) runs as on a paired device. No main change. "Null or empty", not `contains`: `clear*` writes an encrypted empty string (`:41`, `:69-72`), so a key check would never re-seed after a session end. `CLEAR` (D11) leaves shared prefs, so sealing runs once per install |
| D21 | Endpoint probe under a pin | Main change in `ApiDomainInteractor`: remove the pinned early return at `:136`; `buildAutoResolveCandidates()` (`:257-261`) returns only `KinoPubConfig.CURRENT_ENDPOINT` when pinned. The other six mutators keep their guard; KDoc `:47` says so | With one candidate equal to the current domain, `switchToFound` (`:191-208`) reports `changed = false` and writes nothing, so the pin still cannot move. The probe hits `/v1/items/fresh`, already served by the mock (`mock:145`, `items.json` carries `items` and `pagination`, `EndpointProbe.kt:59-61`). A failing mock now gives `NotFound`, so Home loads nothing and the required routes go missing: the run fails loud |
| D22 | DoH | Main change: a `DohResolver(url: String, bootstrapHosts: List<String>)` value next to `DnsOverHttpsFactory` with `DohResolver.GOOGLE` = today's values (`DnsOverHttpsFactory.kt:19-23`); `create(client, cache, resolver = DohResolver.GOOGLE)`; `HttpStack.create(listeners, resolver: DohResolver?)` replaces `useDoh` (null = system resolver, the `:34-39` branch); `KinoPubConfig.setPinnedEndpoint(endpoint, resolver: DohResolver? = null)` and `val DNS_RESOLVER: DohResolver?` (pinned: the pinned resolver; else `GOOGLE`); `modules.kt:74` passes `resolver = KinoPubConfig.DNS_RESOLVER`. Instrumentation pins API base URLs to `https://api.baseline.test:<port>/` with resolver `https://dns.baseline.test:<port>/dns-query`, bootstrap `127.0.0.1`. Mock gains `POST /dns-query` (RFC 8484 wire format): an A record `127.0.0.1` for those two names, NXDOMAIN plus an unknown-request entry for any other | API calls resolve through DnsOverHttps -> mock, the DoH call itself through BootstrapDns, as prod does with `dns.google` and `8.8.x.x`. `.test` is reserved (RFC 6761). Neither name ever reaches the system resolver: allowed hosts resolve through DoH or bootstrap, denied hosts get the unresolved dead proxy (D5). Unpinned prod code builds exactly what it builds today |
| D23 | Hosts the blocker and TLS accept | `BaselineNetworkBlocker.install(journalDir, allowedOrigins: Set<String>)` (`profile/BaselineNetworkBlocker.kt:20-40`) with `https://127.0.0.1:<port>`, `https://api.baseline.test:<port>`, `https://dns.baseline.test:<port>`. Leaf gains SANs `DNS:api.baseline.test`, `DNS:dns.baseline.test` next to `IP:127.0.0.1`; root and leaf re-minted with `tools/make-baseline-mock-cert.sh` (new root key discarded as before). Fixture URLs (`{{MOCK}}`: posters, HLS) stay on `https://127.0.0.1:<port>` | Images and media skip DNS (IP literal), and the launcher, which fetches published artwork from its own process outside the blocker (D25), never makes a system DNS query for a `.test` name |
| D24 | Update check | Main change: `KinoPubApiClient.getLatestGitHubRelease(owner, repo, apiBaseUrl: String = GITHUB_API_BASE_URL)` (`:959-965`); `AppUpdateRepository(apiClient, githubApiBaseUrl: String = GITHUB_API_BASE_URL)` (`AppUpdateRepository.kt:8-21`); `modules.kt:97` becomes `single<IAppUpdateRepository> { AppUpdateRepository(get()) }`. Instrumentation drops `NoOpAppUpdateInteractor` and overrides only `IAppUpdateRepository` with `AppUpdateRepository(get(), "<mock origin>/github")`. Mock route `GET /github/repos/[^/]+/[^/]+/releases/latest` serves `github_release.json`: `tag_name` `v0.0.1`, not draft, not prerelease, no assets | Real interactor, preferences, Ktor third-party call and JSON decode run. `singleOf(::AppUpdateRepository)` would ask Koin for a `String` once the constructor has two parameters, hence the explicit lambda. An older tag makes `upgradeVersionOrNull` (`AppUpdateRepository.kt:24-36`) return null, so no prompt overlays the journeys |
| D25 | TV home channel | `history.json` gets one movie entry: `item` (id 1, title, `type` movie, `posters.wide` `{{MOCK}}/media/wide.jpg`), `video` (`id` 1, `number` 1, `duration` 24), `time` 5 | Passes every filter in `ContinueWatchingSource.kt:26-52` (5 s < 90 % of 24 s, `WatchCompletionPolicy.kt:18-24`; https artwork); `MainVM.kt:64-70` publishes it through `AndroidTvHomePublisher` on the leanback emulator (`TvHomePublisherFactory.kt:22-30`). `watching/*` stays empty: tvprovider does not read it |
| D26 | Required routes (adds to D14) | Startup, BrowseAndDetails and Rail also require `GET /v1/items/fresh`, `GET /v1/history`, `GET /github/repos/.../releases/latest`, `POST /dns-query` | Each proves one restored path ran in every home iteration; the update and TV-home slots fire 1-1.5 s after settle (`StartupSettled.kt:12-18`), inside D13's 15 s wait. `items/fresh` cannot tell the probe from a fresh row, so the content guards (D27) settle the probe |
| D27 | Content guards | `BaselineProfileContentTest.profilesCoverProdColdStartPaths`: every guard rule in the table above (11 substrings) is present in both `baseline-prof.txt` and `startup-prof.txt` | All 11 hold at base, so the test passes at base, fails at 7d639a9b, and fails again if a later change to the instrumentation app drops one of these paths |

#### Options considered per path

| Path | Option | Main-code change | Risk | Verdict |
|---|---|---|---|---|
| Token | A: real store, seeded (D20) | none | Keystore failure on the emulator makes `getString` return null and the app lands on pairing (loud: required routes missing) | **recommended** |
| Token | B: keep synthetic store | none | Decrypt chain stays cold | rejected |
| DoH | A: pinned resolver, hostname API (D22, D23) | `DnsOverHttpsFactory`, `HttpStack`, `KinoPubConfig`, `modules.kt:74` | Prod network stack edited; defaults keep prod byte-identical, guarded by tests | **recommended** |
| DoH | B: instrumentation code builds its own `DnsOverHttps` to the mock and runs one lookup at start | none | OkHttp DoH library rules come back, `DnsOverHttpsFactory` and `HttpStack`'s DoH branch stay cold; warm-up does not follow prod if the resolver config changes | fallback if the owner refuses main changes |
| DoH | C: DoH on, endpoint still `127.0.0.1` | `modules.kt:74` only | Builder rules only: an IP literal never calls `Dns.lookup`, so `BootstrapDns;->lookup` stays cold | rejected |
| Probe | A: walk only the pin (D21) | `ApiDomainInteractor` | Touches the seam contract the seams run tested | **recommended** |
| Probe | B: no pin, domain override to the mock | none | Custom mirrors keep the real oauth and extra base URLs (`KinoPubConfig.kt:98-106`): real-host requests | rejected |
| Update | A: base-URL parameter + mock route (D24) | `KinoPubApiClient`, `AppUpdateRepository`, `modules.kt:97` | Koin binding edit in prod | **recommended** |
| Update | B: fixture `IAppUpdateRepository` in the flavor | none | `AppUpdateRepository` and the Ktor GitHub call stay cold (`getAvailableUpdate` guard dropped) | fallback |
| TV home | A: non-empty `history.json` (D25) | none | Rail journey's History screen now shows a card | **recommended** |
| TV home | B: non-empty `watching/serials` (blind F1) | none | Does not feed tvprovider; changes Home's first row and the D-pad paths of every journey | rejected |

#### Changes by layer

| Layer | File | Change |
|---|---|---|
| API / repository | `app/src/main/java/com/kino/puber/data/api/network/DnsOverHttpsFactory.kt:15-27` | D22 `DohResolver`, `GOOGLE`, `resolver` parameter |
| API / repository | `.../data/api/network/HttpStack.kt:28-51` | `resolver: DohResolver?` replaces `useDoh`; KDoc `:9-13`, `:25-26` |
| API / repository | `.../data/api/config/KinoPubConfig.kt:63`, `:84-90` | `DNS_RESOLVER`; `setPinnedEndpoint(endpoint, resolver = null)`; `clearPinnedEndpoint` clears both |
| API / repository | `.../data/di/modules.kt:74`, `:97` | D22, D24 |
| API / repository | `.../data/api/KinoPubApiClient.kt:959-965`, `.../data/repository/AppUpdateRepository.kt:8-21` | D24 |
| API / repository | `.../domain/interactor/api/ApiDomainInteractor.kt:47`, `:135-136`, `:257-261` | D21 |
| App runtime (flavor) | `profile/BaselineProfileApp.kt` | D20 seed call, D24 override; remove `SyntheticAuthPreferences`, `NoOpAppUpdateInteractor` |
| App runtime (flavor) | `profile/BaselineInstrumentationEnvironment.kt:11-30` | Hostname API preset, D22 resolver, D23 origins |
| App runtime (flavor) | `profile/BaselineAuthSeed.kt` (new), `profile/BaselineNetworkBlocker.kt:20-40` | D20, D23 |
| Generator | `mock:135-163`, `BaselineFixtures` names (`mock:259-262`) | D22 `/dns-query`, D24 route, D26 required flags, `github_release` fixture |
| Generator | `baselineprofile/src/main/assets/baseline/history.json`, `github_release.json` (new), `tls/leaf-test-only.pem`; `app/src/instrumentation/res/raw/baseline_mock_ca.pem` | D25, D24, D23 |
| Tooling | `tools/make-baseline-mock-cert.sh` | D23 SANs |
| Profiles | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | Regenerated on `host.docker.internal:5555` |

ViewModel state, screen/DI module, Compose content, resources: N/A.

#### Tests

| Test | Where | Covers |
|---|---|---|
| `BaselineProfileContentTest.profilesCoverProdColdStartPaths` | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt` | D27 |
| `HttpStackTest`: default builds `DnsOverHttps`; `resolver = null` gives `Dns.SYSTEM` (rewrite of `:21`) | `app/src/test/kotlin/com/kino/puber/data/api/network/` | D22, prod unchanged |
| `DnsOverHttpsFactoryTest`: `DohResolver.GOOGLE` is `https://dns.google/dns-query` with `8.8.4.4`, `8.8.8.8` | same | D22, prod unchanged |
| `ApiModulePinnedEndpointTest`: pinned with no resolver -> system DNS (existing); pinned with a resolver -> `DnsOverHttps`; unpinned -> `DnsOverHttps` | `app/src/test/kotlin/com/kino/puber/data/di/` | D22 |
| `ApiDomainInteractorPinnedEndpointTest`: `autoResolveWorkingDomain_probesNothing_whenPinned` (`:144`) becomes `_probesOnlyThePin_` (one probe, the pinned preset, `changed = false`, nothing written or cleared); new `_returnsNotFound_whenThePinDoesNotAnswer` (no built-in endpoint probed) | `app/src/test/kotlin/com/kino/puber/domain/interactor/api/` | D21 |
| `AppUpdateRepositoryTest` (existing or new): default requests `https://api.github.com/repos/<owner>/<repo>/releases/latest`; a given base URL replaces only the origin | `app/src/test/kotlin/com/kino/puber/data/repository/` | D24, prod unchanged |
| `BaselineNetworkBlockerTest`: both `.test` origins -> `NO_PROXY`; `https://other.baseline.test:<port>` and the right host on another port -> denied and journaled | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/` | D23 |
| `BaselineMockSelfTest`: `/dns-query` answers `127.0.0.1` for `api.baseline.test`; another name gets NXDOMAIN and appears in `verify().unknownRequests` | `baselineprofile/.../` | D22 |

#### Risks (round 2, highest first)

| Risk | Where it lives | Mitigation |
|---|---|---|
| Prod network stack edit changes what every request resolves through | `HttpStack.kt`, `DnsOverHttpsFactory.kt`, `modules.kt:74` | Defaults equal today's values; `HttpStackTest`/`DnsOverHttpsFactoryTest` pin them; AC-9 launch on the box goes through real DoH |
| Pinned walk lets the pin move, or probes a real built-in endpoint | `ApiDomainInteractor.kt:257-261` | One candidate when pinned; `_returnsNotFound_` test asserts no built-in preset is probed; a real-host probe would also hit the blocker and fail the iteration |
| Seeded tokens lost (keystore error, session end) and later iterations land on pairing | `profile/BaselineAuthSeed.kt`, `CryptoPreferenceRepository.kt:80-91` | Re-seed on null or empty; required routes fail the iteration otherwise |
| Launcher fetches published artwork from its own process, outside the blocker | `AndroidTvHomePublisher`, `history.json` | Artwork is `https://127.0.0.1:<port>`: loopback only, no DNS; the launcher does not trust the test root, so the handshake fails before any request |
| DoH wire answer the OkHttp codec rejects, so every API lookup fails | `mock` `/dns-query` | `BaselineMockSelfTest` decodes the answer with OkHttp's own `DnsOverHttps` against the mock |
| Non-empty history changes the rail journey's focus path | `BaselineProfileGenerator.kt` rail journey | First device run (M1); a broken path fails on missing required routes |

### Spec rows to replace (#6)

| ID | Replacement |
|---|---|
| D7 | Two-level: a root CA (cert committed as `app/src/instrumentation/res/raw/baseline_mock_ca.pem`, key discarded after signing) and a leaf (SAN `IP:127.0.0.1`; D23 adds two DNS names) whose cert and PKCS#8 key are committed as `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem`; both valid 2020-01-01 to 2051-01-01, set with `openssl ca` by `tools/make-baseline-mock-cert.sh:17-20`. Why: discarded root key means nothing else can be minted that the app trusts; a fixed notBefore in the past lets an emulator with a lagging clock (restored snapshot) accept the chain |
| D14 | Startup: `items/hot`, `watching/serials`. Browse: + `items/{id}`, `items/similar`, one poster. Rail: + `history`, `items` (`genres` is served, not required: only `ShowAllVM.kt:223` requests it, and the rail journey opens no show-all screen). Playback: `items/{id}`, playlist `.m3u8`, one `.ts` segment, `watching/marktime` (`mock:135-163`). D26 adds four routes to every home journey |
| R15 | Leaf certificate expires / committed key trips scanners \| 1 \| 1 \| 1 \| Accepted: both certificates valid to 2051-01-01, date in `docs/release.md:137`; `-test-only` name |

### AC wording

No AC must change for D20-D27. Two edits make traceability exact; both are the owner's (questions below).

### Open questions (round 2)

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q-D2 | D21, D22 and D24 change main source that ships (round 1 had none). Accept, or take the no-main-change fallbacks (DoH B, Update B) and leave `DnsOverHttpsFactory`, `HttpStack`, `AppUpdateRepository` and the GitHub call cold? | Accept: every change has a default equal to today's behaviour and a JVM test pinning it | yes (D21, D22, D24) |
| Q-D3 | AC-4 names only the ban; add "and with the prod cold-start path rules (D27)" so the new guard traces to an AC? | Yes | no |
| Q-D4 | AC-3 says "a non-loopback host"; with D22 the allowed hosts include two `.test` names. Reword to "a host outside the mock's three origins"? | Yes | no |

# Brief: hermetic-baseline-generation

Kind: improvement   Size: M   Tracks: none   Device verification: yes (build variants, networking, startup DI of a new flavor, the shipped baseline profile). Generation and its evidence on the emulator `host.docker.internal:5555` (API 36); the dev launch check on the box `192.168.1.110:5555` (`state.md: Decisions`, owner 2026-11-26)

## Request

Split item 2 of `hermetic-baseline-profiles` (`../hermetic-baseline-profiles/brief.md: Split`, umbrella AC-4..AC-9). Replace the paired-account generation route with a hermetic one: an `instrumentation` flavor whose `Application` uses the merged seams (pinned loopback endpoint, synthetic auth, no-op update check, egress blocker that fails on any non-loopback request); a mock backend in `:baselineprofile` serving all four journeys, playback with a synthetic stream (umbrella Q2); per-journey cache reset; delete the `nonMinifiedRelease` auth provider, `AuthEventLog` and the pairing steps; rewrite the profile section of `docs/release.md`; regenerate the committed profiles. Absorbs `baseline-auth-provider-presence` and `baseline-generation-hardening` F1 plus the false "not paired" warning. Re-port of `upstream-sync-triage` 0b6253a9, c17847cc, 5709c601, not a cherry-pick.

## Findings

Code map, per-journey request table, branch commits, options and risks: `research.md: 3. Current state in code`, `4. Related work`, `6. Options` (option B recommended), `7. Risks`. Rechecked at 5cd3e37e, the facts this brief relies on:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/PuberApp.kt:69`, `:78-99` | Class `open`; `shouldInitializeRuntime`, `configureRuntime` (before `startKoin`), `runtimeModules` (appended last) exist with no caller |
| `app/src/main/java/com/kino/puber/data/api/config/KinoPubConfig.kt:84` | `setPinnedEndpoint` has no caller; all seven `ApiDomainInteractor` mutators return early when pinned; `data/di/modules.kt:74` drops DoH when pinned |
| `app/src/main/java/com/kino/puber/core/image/HttpsEnforcingInterceptor.kt:9-13` | Every `http://` image URL becomes `https://`, so a plain-HTTP loopback mock cannot serve posters |
| `app/src/main/java/com/kino/puber/data/repository/ICryptoPreferenceRepository.kt:17-19` | Three session-end methods newer than the branch; the branch synthetic store does not compile without them |
| `app/build.gradle.kts:440-445` | `:baselineprofile` wired only into `devReleaseBaselineProfile` |
| `app/src/nonMinifiedRelease/java/com/kino/puber/profile/`, `app/src/{dev,prod}NonMinifiedRelease/AndroidManifest.xml` | Exported token provider and `AuthEventLog` (hardening F1) |
| `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | 20 `com/kino/puber/profile/` rules in each, so today's profile already fails umbrella AC-6 |
| `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:28-34`, `:183-192` | API 34 refusal justified by pairing; `requirePairedApp()` after every journey |
| `tools/generate-baseline-profile.sh:13-17`, `:54-86`, `:117-121` | `PUBER_CLIENT_SECRET` refusal, pairing check and install, false "not paired" warning |
| `Makefile:141-179` | `baseline-profile` target: private adb 5038, emulator and API checks, `H` strip/re-mark around `:app:generateBaselineProfile` |
| `docs/release.md:99-133` | Profile section requires `.env` secrets, an account write and pairing |
| `.flow/config.yaml:13` | `check.full` covers only `DevDebug` variants, so `app/src/instrumentation` gets no test, detekt or lint |

## Size

M: one shippable goal (a generation route that needs no account and regenerated profiles from it). It touches risk areas from `.flow/roles/analyst.md`: build variants, networking, Koin wiring, the baseline profile. Deleting the old route and regenerating cannot ship apart from the new route; Makefile/script hardening F2-F5 stays in `baseline-generation-hardening`, after this run.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | the emulator `host.docker.internal:5555` (API 36) with no KinoPub pairing, a worktree with no `.env` and no `PUBER_CLIENT_SECRET` | `make DEVICE=host.docker.internal:5555 baseline-profile` runs | all four journeys (startup, browse+details, rail, playback) pass; both files under `app/src/main/generated/baselineProfiles/` are rewritten; every method rule in `baseline-prof.txt` is `H` (`startup-prof.txt` keeps its `S`/`P` flags, as at base); `:app:generateBaselineProfile --dry-run` lists only `InstrumentationNonMinifiedRelease` generation tasks |
| AC-2 | that run | the egress journal and the mock's request log are read after each journey | the journal is empty; every request reached the loopback mock; each journey's required routes were hit, including the stream playlist and `watching/marktime` in every playback iteration (caches reset between journeys) |
| AC-3 | the blocker and the mock | a unit or instrumented test plants a request to a host outside the mock's three origins (`https://127.0.0.1:<port>`, `https://api.baseline.test:<port>`, `https://dns.baseline.test:<port>`, including an allowed host on another port), and one to an unknown mock route | the first is refused and journaled; the second returns 404 and is journaled; in both cases the journey check fails |
| AC-4 | the regenerated profiles | `BaselineProfileContentTest` runs, extended with a `com/kino/puber/profile/` and `com/kino/puber/baselineprofile/` ban and with the prod cold-start path guard (`design-architect.md: Round 2 delta`, D27: the 11 token-read, DoH, endpoint-probe, update-check and TV-home rule substrings) | it passes; the guard holds in both `baseline-prof.txt` and `startup-prof.txt`, as at base |
| AC-5 | the regenerated `baseline-prof.txt` against the 5cd3e37e counts in `research.md: Image http->https gap` | per-package rule lines are counted | `coil3/network/`, `coil3/decode/`, `androidx/media3/exoplayer/hls/` and `okhttp3/internal/tls/` each have rules; every count is recorded in the evidence, with drops over 20 % against today listed |
| AC-6 | `assembleProdRelease` and `assembleDevRelease` | the APKs are inspected (`apkanalyzer` or unzip + dex listing) | each carries `assets/dexopt/baseline.prof`; neither contains `BaselineProfileApp`, the blocker, the synthetic auth store, the control receiver or the loopback CA |
| AC-7 | `check.full` plus the instrumentation static targets agreed in Q3 | they run with the third flavor present | unit tests, detekt and lint pass |
| AC-8 | `docs/release.md`, `tools/generate-baseline-profile.sh`, `BaselineProfileGenerator.kt`, `Makefile` | read and grepped for `CLIENT_SECRET`, `pair`, `PuberAuth`, `profile.auth` | no pairing, client secret, account write or auth provider is required or mentioned for generation; `app/src/nonMinifiedRelease/` and both `*NonMinifiedRelease` manifests are gone |
| AC-9 | the dev debug APK built in the worktree with the `instrumentation` flavor present | installed with `tools/android-apk-install-preserve` on the box `192.168.1.110:5555` and launched | it reaches Home with no crash; the launch of an APK with the new profile embedded is a step of the profile section of `docs/release.md`, not of this run |
| AC-10 | main source after the change, no pin set | JVM tests run (`HttpStackTest`, `DnsOverHttpsFactoryTest`, `ApiModulePinnedEndpointTest`, `AppUpdateRepositoryTest`) | the defaults equal today's: `DohResolver.GOOGLE` is `https://dns.google/dns-query` with bootstrap `8.8.4.4`, `8.8.8.8`; `HttpStack` with the default resolver builds `DnsOverHttps` and with `null` the system resolver; the unpinned API module builds `DnsOverHttps`; `AppUpdateRepository` with the default base URL requests `https://api.github.com/repos/<owner>/<repo>/releases/latest`, and a given base URL replaces only the origin |
| AC-11 | a pinned endpoint (D21) | `ApiDomainInteractorPinnedEndpointTest` runs `autoResolveWorkingDomain` with the pin answering, then not answering | answering: exactly one probe, of the pin, `changed = false`, no domain written or cleared; not answering: `NotFound` and no built-in endpoint probed; the other six mutators still return early when pinned |

Mapping to the umbrella: AC-1 = umbrella AC-4; AC-2, AC-3 = AC-5; AC-4, AC-5 = AC-6; AC-6 = AC-7; AC-7 = AC-8; AC-8 = AC-9; AC-9 is new (the shipped profile changes); AC-10, AC-11 are new (round 2 main-source changes D21, D22, D24, `state.md: Q5`).

## Risks

| Risk | Mitigation |
|---|---|
| Unseen egress repeats the 2026-11-25 ban pattern | Blocker installed in `configureRuntime()` before Koin, fails closed; blank TMDB token; no `imdb`/`trailer` in fixtures; no-op update interactor; AC-2, AC-3; review greps for HTTP clients outside OkHttp/Ktor/Media3 |
| Thin mock data leaves series, season and poster paths cold, so release startup regresses | Option B posters and HTTPS (Q1); AC-5 counts against today |
| Instrumentation classes or the test CA reach a shipped APK or profile | Code only in `app/src/instrumentation/`; `filterPredicate` on `com/kino/puber/profile/` (branch 5709c601: the consumer `filter {}` did not work); AC-4, AC-6 |
| Short stream ends inside the 5 s dwell and triggers `watching/toggle` and the ended overlay (`PlayerVM.kt:1940-1948`) | Stream 20-30 s (Q2) |
| `:baselineprofile` dropping `dev`/`prod` retargets `StartupBenchmarks` to the instrumentation package | Recorded for `startup-benchmark-entry`; benchmarks not part of this run's ACs |
| Box launch check on a worktree build loses the box session (expired token, no client secret) | Refresh with a master build and `auth-save` before AC-9 (owner memory) |

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Posters and TLS against the mock: option A (no posters, plain HTTP), B (HTTPS loopback mock with a committed loopback-only test CA trusted by an instrumentation-flavor `network_security_config.xml`) or C (main-source change to `HttpsEnforcingInterceptor`)? (`research.md: 6. Options`) | B: CA valid 20+ years, SAN `IP:127.0.0.1`, cert in `app/src/instrumentation/res/raw`, key in `:baselineprofile` assets, named test-only | yes (AC-5, AC-6) |
| Q2 | The synthetic stream: format and how it is produced, given no `ffmpeg` in the `puber-mac` box | HLS, H.264 + AAC, about 320x180, 20-30 s, under 300 KB, built once with `ffmpeg` on the Mac host and committed with the exact command in a short recipe script | no |
| Q3 | Add the `instrumentation` variant to the static gate? | Yes: `:app:testInstrumentationReleaseUnitTest :app:detektInstrumentationRelease :app:lintInstrumentationRelease` (or the nearest existing task names) in `check.full`; PR checks unchanged | yes (AC-7) |

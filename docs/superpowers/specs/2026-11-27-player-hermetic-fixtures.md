# Hermetic player test fixtures and the stale-callback probe

Status: approved
Date: 2026-11-27
Slug: player-hermetic-fixtures
Kind: improvement
Size: M
Tracks: none

## Problem / Why

Port upstream P6 Part A (triage T10; upstream fe6224fb, 1d3ec39e): a `:player-test-fixtures` module with synthetic MP4/HLS/WebVTT, a loopback media server and request journal, one network blocker, controller-level instrumented tests against the real `PlaybackController` and ExoPlayer, `docs/testing/player-playback.md` and `tools/generate-player-test-fixtures.sh`. Inside it, run the stale-callback probe (spec D25) and record whether the defect reproduces. Design choices are the triage spec's D20-D25 and D28 (`git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`); research.md: 4 checked them against master.

### Why M, not L or S

One goal (a hermetic player test kit plus the probe it hosts). Production player code stays unchanged (AC-7). It still crosses the Gradle build (new module, plugin alias, `settings.gradle.kts`, `app/build.gradle.kts:439-444` dependencies), `app/src/instrumentation` (blocker), `Makefile` and CI, and it exercises playback and build variants, which are risk areas. Moved out of this run:

| Item | Where it goes | Condition |
|---|---|---|
| Part B: Compose E2E/focus tests, `PuberVM` router rebind, test tags (D27) | new idea row `player-compose-e2e-tests`, `blocked_by: player-overlays-port` | that port is stalled, 504 commits behind (research.md: 4) |
| Gate fix: `PlaybackCallbackGate` + one listener per media session (D26) | new run `player-callback-gate` | only if AC-5 reproduces the stale callback |
| upstream 7c30221e | nothing | `MainVM.kt` merge fix, not player (research.md: 4) |

### Code facts this run relies on

| Fact | Where |
|---|---|
| Constructor `(context, okHttpClient, mediaCache, playerPreferencesRepository)`; DI builds it in the screen scope | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:160`, `ui/feature/player/component/PlayerScreen.kt:58` |
| One `playerListener` for the controller's life; `onPlayerError` attributes failures to `currentStreamUrl` at dispatch time | `PlaybackController.kt:214-271`, `:260-264` |
| `switchStream` reuses player and listener, sets `currentStreamUrl` before `prepare()` | `PlaybackController.kt:499-519` |
| `release` removes both listeners before `player.release()` | `PlaybackController.kt:581-598` |
| `SeekStallRecovery` posts `onStreamFailure` through a main-thread `Handler` | `PlaybackController.kt:184-196` |
| VM drops an `onStreamFailure` for a non-current URL; `onError` is not generation-checked | `ui/feature/player/vm/PlayerVM.kt:273`, `:282` |
| Existing blocker: a default `ProxySelector` with an origin allow-list and file journal | `app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:19-48` |
| `mockwebserver3` is already in the catalog | `gradle/libs.versions.toml:89` |
| `instrumentation` flavor, `.instrumentation` suffix | `app/build.gradle.kts:239-248` |
| `make itest` runs `connected$(VARIANT)AndroidTest`; `PACKAGE_instrumentation` missing; `build` and `itest` call `$(GRADLE)` (default `./gradlew`) | `Makefile:25-27`, `:73-74`, `:131-141` |
| CI unit job runs only `testProdDebugUnitTest` | `.github/workflows/pr-checks.yml:95` |
| `docs/testing/` does not exist; `docs/architecture/testing.md` covers JVM tests only | research.md: 3 |

Stale callback: implausible on code reading (ListenerSet drops events queued for a removed listener; ExoPlayer masks old-media updates across `setMediaItem`/`prepare`), residual windows at `:260-264` and the stall handler post. Not proven absent; AC-5 is the check (research.md: 5).

## Acceptance criteria

- AC-1. Given the repo after the change, when `./tools/agentw :player-test-fixtures:testDebugUnitTest` runs, then JUnit 5 tests pass that show `HermeticTestServer` serves byte-range requests, the request journal records each request, and every asset matches `SHA256SUMS`.
- AC-2. Given `app/build.gradle.kts`, when the dependency graph of `devDebug` and `prodRelease` is printed (`:app:dependencies --configuration devDebugRuntimeClasspath` and `prodReleaseRuntimeClasspath`), then neither contains `:player-test-fixtures` or `mockwebserver3`; the module is consumed only by the instrumentation flavor's test configurations.
- AC-3. Given the instrumentation flavor, when `BaselineNetworkBlockerTest` and a new blocker test run (`./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`), then a loopback origin of the media server is allowed and a non-loopback host gets the deny proxy and is journaled; there is one `ProxySelector` implementation (D22).
- AC-4. Given a device (Q1), when `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<PlaybackControllerDeviceTest>` runs, then the synthetic MP4 and HLS fixtures reach `STATE_READY`, position advances, and playback ends, served only by the loopback server; the network journal is empty after the run.
- AC-5. Given the same device, when `PlaybackControllerStaleCallbackTest` runs (stream A answers with a delayed failure, `switchStream` to B before it lands), then the result is recorded in evidence whether it passes or fails: pass = "not reproduced" and the test stays as a regression test; fail = run `player-callback-gate` is opened and the failing test is committed `@Ignore`d with that slug.
- AC-6. Given upstream's NetworkTracks and Resources device tests, when the port ends, then each upstream test name appears in `docs/testing/player-playback.md` as ported or "not ported: needs a production change" (D24).
- AC-7. Given the branch, when `git diff master --stat -- app/src/main` runs, then it is empty.
- AC-8. Given `docs/testing/player-playback.md`, when read, then it names the exact `make ... itest TESTS=` command, the device rule from Q1, how fixtures are checked (`sha256sum -c SHA256SUMS`), and that `tools/generate-player-test-fixtures.sh` is a maintainer tool not run in the gate.
- AC-9. Given `.flow/config.yaml` `check.full` and `.github/workflows/pr-checks.yml`, when read, then both run `:player-test-fixtures:testDebugUnitTest`, and `check.full` passes in the worktree.

Evidence for AC-4 and AC-5 is headed "loopback fixtures" (D28). Until the owner answers Q1, AC-4 and AC-5 are reported `NOT CHECKED: device not authorized`; the run does not reach gate 2 with them unchecked unless the owner accepts that.

## Out of scope

Moved out of this run (brief, "Why M, not L or S"): Part B Compose E2E/focus tests and test tags (new idea run `player-compose-e2e-tests`, blocked by `player-overlays-port`); the `PlaybackCallbackGate` fix (run `player-callback-gate`, only if AC-5 reproduces); upstream 7c30221e (not player work). No production player code change (AC-7).

## Design

Source read in `/home/dev/orca/workspaces/Puber/player-hermetic-fixtures` at f53f2144; upstream sources from commit 1d3ec39e (present in the repo). ACs: `brief.md#acceptance-criteria`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Where the blocker lives (D22) | Keep `BaselineNetworkBlocker` (`app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:19-48`) as the only `ProxySelector`; add the fixture server origin to `BaselineInstrumentationEnvironment.allowedOrigins()` (`BaselineInstrumentationEnvironment.kt:28-33`). Upstream's `LoopbackNetworkBlocker`/`LoopbackNetworkJournal` are not ported | The fork's blocker already does what upstream's does, for several origins rather than one, and `BaselineProfileApp.configureRuntime` (`BaselineProfileApp.kt:17-20`) installs it in every instrumentation process before a test runs. Turning it into a delegate would move code and change `BaselineNetworkBlockerTest` for no new behaviour. AC-3 still holds: there is one implementation |
| Which origin the media server uses | Fixed port from a new `puber.playerFixturePort=18768` in `gradle.properties`, exposed as `BuildConfig.PLAYER_FIXTURE_PORT` in the `instrumentation` flavor next to `app/build.gradle.kts:246-247`. Origin `https://127.0.0.1:18768` | The allow-list is fixed when the app starts, so the port has to be known at build time (the pattern `BASELINE_MOCK_PORT` follows). 18768 is not `maxOf(h1, h2) + 1` = 18767, which `BaselineNetworkBlockerTest.kt:21` uses as its "other port" |
| HTTP or HTTPS on loopback | HTTPS. `HermeticTestServer` gets an optional `HandshakeCertificates` (`okhttp-tls`, `libs.versions.toml:90`) and calls `MockWebServer.useHttps` when one is given. Device tests generate a `HeldCertificate` for `127.0.0.1` per run and build the controller's `OkHttpClient` with `sslSocketFactory` and a trust manager that trust only that certificate. JVM tests in the module keep plain HTTP | The instrumentation network security config refuses cleartext and trusts only the mock CA (`app/src/instrumentation/res/xml/network_security_config.xml:4-6`), and `BaselineNetworkSecurityConfigTest.kt:27` asserts that it has no `domain-config`. A client-side trust manager leaves that P5 contract and its test untouched |
| Where the module is consumed (D21, AC-2) | `"androidTestInstrumentationImplementation"(project(":player-test-fixtures"))` only. Upstream's `instrumentationImplementation` is not used | AC-2 requires the module and `mockwebserver3` to stay out of the instrumentation app APK as well, not only out of dev/prod |
| Where `PlayerTestControl` lives (D23) | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, with the tests, not `app/src/instrumentation` | It depends on the module, which only the test APK sees (row above) |
| Module shape (D21) | `player-test-fixtures/` from 1d3ec39e: `PlayerTestFixtures.kt`, `server/HermeticTestServer.kt`, `server/HermeticRequestJournal.kt`, `src/main/assets/player-fixtures/**` (byte for byte, Q3), `fixtures-provenance.md`. Own `build.gradle.kts` on `libs.plugins.android.library` with `Versions.CompileSdk`, `Versions.MinSdk`, `Versions.JavaVersionCompat` and the `kotlin { jvmToolchain(Versions.ToolchainJavaVersion); jvmTarget }` block of `app/build.gradle.kts:258-261`. `api(libs.mockwebserver3)`, `api(libs.okhttp.tls)`; tests on `libs.junit5` + `libs.junit5.launcher`, `useJUnitPlatform()`; assets on the JVM test classpath through `tasks.withType<Test> { classpath += files("src/main/assets") }` | `api` because `PlayerTestControl` exposes `RecordedRequest` and `HeldCertificate`. One classpath line instead of upstream's extra `sourceSets["test"].resources`, which also duplicated the assets |
| Module unit tests (AC-1) | Port `PlayerTestFixturesTest` and `HermeticTestServerTest` from 1d3ec39e to JUnit 5 (annotations and assertions only); add one HTTPS case for the new `HandshakeCertificates` parameter | Fork convention (`docs/architecture/testing.md`); the TLS parameter is new code and needs a test |
| How tests drive the controller | A harness in the test source set builds `PlaybackController(targetContext, client, cache, PlayerPreferencesRepository(targetContext))` (`PlaybackController.kt:160`, `data/repository/PlayerPreferencesRepository.kt:10`) and makes every call on the main thread (`Instrumentation.runOnMainSync`). Cache: `SimpleCache` in a per-test directory under `targetContext.cacheDir`, released in teardown. Callback: a recording `PlaybackControl.Callback` (`PlaybackController.kt:61-95`) with a thread-safe event list | ExoPlayer built on a thread with no looper binds to the main looper and refuses calls from other threads; the controller's own `mainHandler` (`:182`) is on main too. A per-test cache directory avoids the `SimpleCache` folder lock held by the app's own singleton (`data/di/modules.kt:142-148`) |
| Reading fixture bytes on the device | `PlayerTestFixtures.open(id, InstrumentationRegistry.getInstrumentation().context)`: the test APK's context, not `targetContext` | The assets merge into the test APK because the module is an androidTest dependency |
| Network journal check (AC-4) | `BaselineNetworkJournal(BaselineInstrumentationEnvironment.journalDirectory(targetContext))`: `clear()` in setup, `snapshot()` empty in teardown | The journal the installed blocker writes to (`BaselineInstrumentationEnvironment.kt:22`, `:54`) |
| Stale-callback probe (D25, AC-5) | `PlaybackControllerStaleCallbackTest`: stream A's playlist or segment route is `ResponsePlan.delayed(..., gate)` ending in a failure that `streamRecovery()` classifies (`PlaybackController.kt:334-372`); `switchStream(B)` (`:499-519`) while the gate is closed; open the gate; assert that no `onError`/`onStreamFailure` arrives after the switch and that the next `onStreamReady` carries B's URL. Open the gate before the 5 s read timeout (`PlaybackNetworkTuning.READ_TIMEOUT_SECONDS`) | Exercises the one shared `playerListener` (`:214-271`) and the `currentStreamUrl` attribution at `:260-264` against a real ExoPlayer. After the timeout the failure would be a retry on A instead of the delayed failure under test |
| Ported upstream cases (D24, AC-6) | Rule: a case is ported when it needs only `PlaybackControl`, `PlaybackController.player` (`:204`) and the fixture server; otherwise it is listed in `docs/testing/player-playback.md` as not ported. Device: `progressiveMp4` and `hlsVod` reach READY, advance and end (AC-4). The test-designer maps the 12 NetworkTracks/Resources names of 1d3ec39e | AC-7 forbids `app/src/main` edits; `player` is already public |
| CI and gate (D23, AC-9) | `pr-checks.yml:95` becomes `./gradlew testProdDebugUnitTest :player-test-fixtures:testDebugUnitTest`; `:102` also uploads `player-test-fixtures/build/reports/tests/`. `check.full` gains `:player-test-fixtures:testDebugUnitTest` (see Q2 for which copy of `.flow/config.yaml`) | `testProdDebugUnitTest` matches no task in a library module |
| Device | Every device step takes the serial as `DEVICE=<serial>`; nothing in code, Makefile or docs names a device. `docs/testing/player-playback.md` states the rule the owner gives for Q1 | Q1 is open; AC-4/AC-5 run unchanged on whichever device it names |
| Makefile | No change: `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fqcn>` already resolves to `assembleInstrumentationDebug` and `connectedInstrumentationDebugAndroidTest` (`Makefile:73-74`, `:133-141`); `itest` does not read `PACKAGE` | Smallest change that meets AC-4 |

### Changes by layer

API / repository, ViewModel state and actions, screen and DI module, Compose content, resources: N/A: AC-7 keeps `app/src/main` unchanged; every change is build, test or docs.

| Area | File | Change |
|---|---|---|
| Build | `gradle/libs.versions.toml:41` | add `android-library = { id = "com.android.library", version.ref = "agp" }` |
| Build | `build.gradle.kts:2-11` | `alias(libs.plugins.android.library) apply false` |
| Build | `settings.gradle.kts:24` | `include(":player-test-fixtures")` |
| Build | `gradle.properties:6` | add `puber.playerFixturePort=18768` |
| Build | `app/build.gradle.kts:247` | `buildConfigField("int", "PLAYER_FIXTURE_PORT", ...)` in `instrumentation` |
| Build | `app/build.gradle.kts:439-444` | `"androidTestInstrumentationImplementation"(project(":player-test-fixtures"))` |
| Module (new) | `player-test-fixtures/build.gradle.kts`, `src/main/kotlin/.../{PlayerTestFixtures,server/HermeticTestServer,server/HermeticRequestJournal}.kt`, `src/main/assets/player-fixtures/**`, `fixtures-provenance.md` | from 1d3ec39e; `HermeticTestServer` gains the optional `HandshakeCertificates` |
| Module tests (new) | `player-test-fixtures/src/test/kotlin/.../{PlayerTestFixturesTest,server/HermeticTestServerTest}.kt` | JUnit 5 port plus the HTTPS case (AC-1) |
| Instrumentation source | `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:28-33` | `playerFixtureOrigin()` = `https://127.0.0.1:${BuildConfig.PLAYER_FIXTURE_PORT}`, added to `allowedOrigins()`; KDoc "four origins" updated |
| Instrumentation JVM tests | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineInstrumentationEnvironmentTest.kt:66-81` | expected set gains the fixture origin |
| Instrumentation JVM tests | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkBlockerTest.kt` | new case: the fixture origin gets `Proxy.NO_PROXY` and nothing is journaled (AC-3); non-loopback denial is `:35-43` |
| Device tests (new) | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`: `PlayerTestControl.kt`, harness, `PlaybackControllerDeviceTest.kt`, `PlaybackControllerStaleCallbackTest.kt`, ported NetworkTracks/Resources cases | AC-4, AC-5, AC-6 |
| CI | `.github/workflows/pr-checks.yml:95`, `:102` | module tests and their report |
| Gate | `.flow/config.yaml` `check.full` | `:player-test-fixtures:testDebugUnitTest` (Q2) |
| Docs (new) | `docs/testing/player-playback.md` | AC-6, AC-8; evidence heading "loopback fixtures" (D28) |
| Tools (new) | `tools/generate-player-test-fixtures.sh` | from 1d3ec39e, maintainer tool, not run in the gate |

New androidTest sources are not compiled by `check.fast` (`:app:compileDevDebugKotlin`); each task that adds them also runs `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`.

### Alternatives rejected

- `BaselineNetworkBlocker` as a delegate to a ported `LoopbackNetworkBlocker` (D22 as written): moves the working multi-origin selector and its tests for no behaviour change.
- Cleartext for `127.0.0.1` in the instrumentation network security config (upstream): breaks `BaselineNetworkSecurityConfigTest.kt:27` and loosens the P5 contract.
- Ephemeral server port with a runtime allow-list: adds mutable state to the blocker, and `BaselineNetworkBlockerTest.kt:45-53` requires a different loopback port to be denied.
- `instrumentationImplementation` for the module: puts `mockwebserver3` and 1.7 MB of assets into the instrumentation app APK.
- Porting upstream's module `androidTest` (`PlayerTestFixturesAndroidTest`): no AC needs it, and it would add a second device run with another package.
- Robolectric callback-generation tests: D25.

### Risks

| # | Risk | Kind | Lives in | Mitigation |
|---|---|---|---|---|
| 1 | A test's `OkHttpClient` bypasses the blocker: `newBuilder()` at `PlaybackController.kt:624` keeps the parent client's `ProxySelector`, which OkHttp reads from `ProxySelector.getDefault()` when the client is built | security / correctness | harness | build the client in the test after app start (the blocker is installed in `Application`); AC-4 asserts the journal; NetworkTracks redirect-isolation case, if ported, proves the deny path on the device |
| 2 | Calls off the main thread: ExoPlayer throws on wrong-thread access, and callbacks would interleave with the test thread | concurrency | harness | every controller call through `runOnMainSync`; the recording callback is thread-safe; waits poll with generous timeouts |
| 3 | Probe passes vacuously: A's failure never reaches the controller because ExoPlayer cancelled the load, or the gate opens after the read timeout | correctness (of AC-5) | `PlaybackControllerStaleCallbackTest` | a control case without `switchStream` shows the same delayed route does produce `onStreamFailure` for A; the gate opens within 5 s |
| 4 | Device flakiness from timing and decoders (API 29 box vs API 36 emulator) | rollout | device tests | timeouts bounded but generous; a per-test `SimpleCache`; `release()` and `server.close()` in teardown; record the device's API level in evidence |
| 5 | Fixed port 18768 already bound (an earlier test that crashed, or another process) | lifecycle | `PlayerTestControl`, `gradle.properties` | close in `@After` whatever the outcome; one test process at a time under `make itest` |
| 6 | Server threads left behind by a closed gate | lifecycle / memory | `PlayerTestControl` | open every gate in teardown before `awaitQuiescence` and `close` |
| 7 | Assets read from the wrong context, so `open` fails on the device | correctness | harness | the test APK's context (decision above) |
| 8 | Fixture bytes copied wrong | data shape | `player-test-fixtures/src/main/assets/player-fixtures/SHA256SUMS` | AC-1 test plus `sha256sum -c SHA256SUMS` in evidence |
| 9 | Library module under AGP 9.5.1 built-in Kotlin: `kotlin {}` and `Test` task configuration not taken as in `:app` | rollout | `player-test-fixtures/build.gradle.kts` | the module task's first run is `./tools/agentw :player-test-fixtures:testDebugUnitTest` |
| 10 | `.flow/config.yaml` edited on the branch conflicts with the main checkout's uncommitted changes to the same file at ship | rollout | `.flow/config.yaml` | Q2 |

Focus/D-pad, Koin scope lifetime, recomposition, baseline profile: not touched (no `app/src/main` change; the profile is generated from `:baselineprofile`, which this run does not change).

### Open questions

| # | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | (brief) device for AC-4/AC-5 | as the brief says | yes |
| Q2 | `check.full` lives in `.flow/config.yaml`, which the main checkout has uncommitted edits to (`check.full` among them), and the worktree copy is older. Who changes it? | The conductor adds `:player-test-fixtures:testDebugUnitTest` to the main checkout's `check.full` when the module task lands; the branch leaves `.flow/config.yaml` alone | no |
| Q3 | AC-6 allows only "ported" or "not ported: needs a production change". Some cases need something else: `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` needs an Activity and view host (Part B) | Change AC-6 to "ported" or "not ported: <reason>", where the reason is a production change or Part B | no |
| Q4 | detekt and lint do not cover `:player-test-fixtures` or the androidTest sources (`detektAll` is dev-only, `app/build.gradle.kts:355-358`) | Leave as is in this run | no |
| Q5 | `Makefile:25-27` has no `PACKAGE_instrumentation`, so `make FLAVOR=instrumentation info/logs` do not work | Out of this run; `itest` does not need it | no |

## Test design

Inputs: `brief.md#acceptance-criteria`, `design-architect.md`. Base f53f2144. Upstream names from `git show 1d3ec39e:<path>`.

### Levels

| Level | Meaning here |
|---|---|
| `unit` | JUnit 5 on the JVM: `:player-test-fixtures:testDebugUnitTest`, `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` |
| `instrumented` | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run only as `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fqcn>`; `<serial>` is the Q1 device |
| `gate` | a shell command whose output goes into evidence |
| `manual` | read or observe, recorded in evidence |

### Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Device not authorized (Q1): AC-4/AC-5 cannot run | 3 | 3 | 9 | process: rows T12-T16 take `<serial>`; reported `NOT CHECKED: device not authorized` until Q1 is answered |
| R2 | The controller's `OkHttpClient` bypasses `BaselineNetworkBlocker` (client built before the blocker, `PlaybackController.kt:624` `newBuilder()`), so "no real network" is false | 2 | 3 | 6 | T14, journal assertion in T12/T13 |
| R3 | Fixture origin missing from `allowedOrigins()`, or allow-list widened beyond it | 2 | 3 | 6 | T5, T6, T7 |
| R4 | `:player-test-fixtures` or `mockwebserver3` reaches an app APK (`devDebug`, `prodRelease`, `instrumentationDebug`) | 2 | 3 | 6 | T9 |
| R5 | Stale-callback probe passes vacuously: A's failure never surfaces (load cancelled, retried instead of failing, or gate opened after the 5 s read timeout `PlaybackNetworkTuning.kt:18`) | 2 | 3 | 6 | T16 control next to T15 |
| R6 | Wrong-thread ExoPlayer calls or tight timeouts make device tests flaky (API 29 box vs API 36 emulator) | 3 | 2 | 6 | M2 (two consecutive full runs) |
| R7 | Fixed port 18768 still bound, server threads or `SimpleCache` lock left by an earlier test | 2 | 2 | 4 | M2 (all classes in one process), T19 |
| R8 | `HermeticTestServer` HTTPS path (new `HandshakeCertificates` parameter) broken | 2 | 2 | 4 | T4 |
| R9 | Library module misconfigured under AGP built-in Kotlin (`kotlin {}`, `Test` classpath for assets) | 2 | 2 | 4 | T1-T3 run in `check.full` (T11) |
| R10 | CI keeps running only `testProdDebugUnitTest` | 2 | 2 | 4 | T11 |
| R11 | Fixture bytes copied wrong | 1 | 3 | 3 | T1, M1 |
| R12 | Production player code edited (AC-7) | 1 | 3 | 3 | T10 |
| R13 | A test reaches the live KinoPub API | 1 | 3 | 3 | every media URL is `server.url(...)` or `*.invalid`; journal assertion in T12-T16 |
| R14 | Cleartext added to the instrumentation network security config | 1 | 2 | 2 | accepted: existing `BaselineNetworkSecurityConfigTest` in `check.full` |

### Traceability

| # | AC | Level | Priority | Test | Proves |
|---|---|---|---|---|---|
| T1 | AC-1 | unit | P0 red | `PlayerTestFixturesTest.everyCommittedPackFile_matchesItsSha256AndExpectedFileSet`, `everyCatalogEntry_isPresentAndMatchesCommittedSha256` | fails if one asset byte differs from `SHA256SUMS` or a file is missing or extra |
| T2 | AC-1 | unit | P0 red | `HermeticTestServerTest.sequenceRedirectAndRangeResponsesAreDeterministic` | fails if a `Range: bytes=a-b` request does not get 206 with exactly bytes a..b and `Content-Range` |
| T3 | AC-1 | unit | P0 red | `HermeticTestServerTest.routesMatchMethodPathAndQueryWithoutQueueOrderAndRedactValues`, `unknownRoutesAreRecordedAndDelayedResponsesTrackActiveRequests` | fails if the journal drops a matched or unknown request, or keeps query values |
| T4 | AC-1 | unit | P1 red | `HermeticTestServerTest.httpsServer_servesRangeRequest_toClientTrustingItsCertificate` | fails if `useHttps` is not applied (plain HTTP answer) or the range body differs over TLS |
| T5 | AC-3 | unit | P0 red | `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsPlayerFixture` | on base the fixture origin `https://127.0.0.1:18768` gets the dead proxy and is journaled; passes once the origin is in `allowedOrigins()` |
| T6 | AC-3 | unit | P0 red | `BaselineInstrumentationEnvironmentTest.allowedOrigins_listsEachServedOriginOnItsServerPort` (expected set gains the fixture origin) | fails if the origin is missing or a second loopback origin is added |
| T7 | AC-3 | unit | P0 guard | `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenHostIsNotLoopback`, `select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers`, `BaselineInstrumentationEnvironmentTest.configure_installsTheBlockerAsDefaultProxySelector` | fails if adding the fixture origin opens other hosts or ports, or the blocker is no longer the default selector |
| T8 | AC-3 | gate | P2 | `rg -l 'ProxySelector\(\)' app/src player-test-fixtures/src` lists only `BaselineNetworkBlocker.kt` | a second `ProxySelector` (upstream `LoopbackNetworkBlocker`) ported by mistake |
| T9 | AC-2 | gate | P0 guard | `./tools/agentw :app:dependencies --configuration <c>` for `devDebugRuntimeClasspath`, `prodReleaseRuntimeClasspath`, `instrumentationDebugRuntimeClasspath`; `grep -cE 'player-test-fixtures\|mockwebserver3'` is 0 for each, and `instrumentationDebugAndroidTestRuntimeClasspath` lists the module | fails if the module is wired as `implementation` or `instrumentationImplementation` |
| T10 | AC-7 | gate | P0 guard | `git -C <wt> diff master --stat -- app/src/main` prints nothing | fails on any production source edit |
| T11 | AC-9 | gate | P1 | `grep -n ':player-test-fixtures:testDebugUnitTest' .github/workflows/pr-checks.yml` and the main checkout's `.flow/config.yaml` (Q2); `check.full` exits 0 in the worktree | fails if CI or the gate skips the module tests, or the module does not build |
| T12 | AC-4 | instrumented | P0 guard | `PlaybackControllerDeviceTest.progressiveMp4_reachesReadyAdvancesAndEnds` | fails if the MP4 fixture over loopback HTTPS does not reach `STATE_READY`, `currentPosition` does not grow, `onPlaybackEnded` is not called, or the network journal is non-empty at the end |
| T13 | AC-4 | instrumented | P0 guard | `PlaybackControllerDeviceTest.hlsVod_reachesReadyAdvancesAndEnds` | same for `hls/master.m3u8`; also fails if any request misses a registered route (`server.verifyRequiredRoutes()`) |
| T14 | AC-4 | instrumented | P0 guard | `PlaybackControllerNetworkTracksTest.playerMediaNetworkIsolation_rejectsExternalRedirect` | a loopback route redirects to `https://media.invalid/x`: fails if the controller's client is not routed through the blocker (the journal must hold that origin and a failure callback must arrive), R2 |
| T15 | AC-5 | instrumented | P0 guard (probe) | `PlaybackControllerStaleCallbackTest.switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream` | A's master playlist is `ResponsePlan.delayed(gate)` ending in 404 or a malformed body (not retried, `PlaybackErrorPolicy`); `switchStream(B)`, gate opened within 5 s; fails if any `onError` or `onStreamFailure` arrives after the switch, or the next `onStreamReady` is not B's URL. A failure here is the AC-5 "reproduced" branch, not a test bug |
| T16 | AC-5 | instrumented | P0 guard | `PlaybackControllerStaleCallbackTest.delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched` | same route without the switch: fails if no `onStreamFailure` with A's URL arrives, which would make T15 vacuous (R5) |
| T17 | AC-6 | gate | P1 | each upstream test name of the table below found by `grep -F` in `docs/testing/player-playback.md`, each with "ported" or "not ported: <reason>" | fails if an upstream case is silently dropped |
| T18 | AC-4 | instrumented | P1 guard | `PlaybackControllerDeviceTest.progressivePlayback_controlsAndStartPositionUseRealPlayerEvents` | fails if `pause`/`play`/`seekTo` or `prepare(startPosition=)` do not move the real player |
| T19 | AC-4 | instrumented | P1 guard | `PlaybackControllerResourcesTest.releaseDuringBlockedLoad_closesRequests` | fails if `release()` during a gated segment leaves an active server request (`awaitQuiescence` times out), R7 |

New-code tests (T1-T4) cannot run on the base commit because the module does not exist there; their red step is one local mutation each before commit: flip a byte in `progressive.mp4` (T1), answer 200 to a range request (T2), skip the journal write (T3), drop `useHttps` (T4).

### Upstream cases (AC-6)

Rule from `design-architect.md`: ported when it needs only `PlaybackControl`, `PlaybackController.player` and the fixture server. A case marked "port" may still end as "not ported: <reason>" if it cannot run without a view host; the doc says which.

| Upstream (1d3ec39e) | Fork test | Priority |
|---|---|---|
| `progressiveMp4_reachesReadyRendersFrameAdvancesAndEnds` | T12; rendered-frame check not ported: needs a `PlayerView` (Part B) | P0 |
| `hlsVod_reachesReadyRendersFrameAdvancesAndEnds` | T13; same note | P0 |
| `progressivePlayback_controlsAndStartPositionUseRealPlayerEvents` | T18 | P1 |
| `awaitCondition_failsClosedWhenConditionRemainsFalse` | `PlaybackControllerDeviceTest.awaitCondition_failsClosedWhenConditionRemainsFalse` | P2 |
| `unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics` | `PlaybackControllerDeviceTest.unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics` | P2 |
| `hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` | `PlaybackControllerNetworkTracksTest.hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` (cue via `player.addListener`) | P1 |
| `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | `PlaybackControllerNetworkTracksTest.hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | P1 |
| `hls404AndMalformedPlaylist_failWithoutUnboundedRetry` | `PlaybackControllerNetworkTracksTest.hls404AndMalformedPlaylist_failWithoutUnboundedRetry` (`onStreamFailure` with `NEXT_URL`, request count bounded) | P1 |
| `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | port | P2 |
| `progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` | port | P2 |
| `hlsVariant400_fallsBackToTheHealthyVariant` | port (`PlaybackErrorPolicy.getFallbackSelectionFor`) | P2 |
| `hlsVariant502_fallsBackToTheHealthyVariant` | port | P2 |
| `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | port | P2 |
| `playerMediaNetworkIsolation_rejectsExternalRedirectAndSanitizesViolation` | T14; sanitizing not ported: `BaselineNetworkJournal` already stores no query (`BaselineNetworkBlockerTest`) | P0 |
| `progressiveReplay_readsCommittedCacheAfterUpstreamRouteIsRemoved` | port (per-test `SimpleCache`) | P2 |
| `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | port | P2 |
| `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` | not ported: needs an Activity and `PlayerView` (Part B) | n/a |
| `progressiveDecode_reportsMandatoryCapabilitiesDecodersCountersAndAnalytics` | port if `player.videoDecoderCounters` move without a surface, else not ported: needs a view host | P2 |
| `releaseDuringBlockedLoad_closesRequestsAndDetachesReleasedView` | T19; view detach not ported (Part B) | P1 |
| `boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequestsOrOpenSpans` | `PlaybackControllerResourcesTest.boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequests` | P2 |

### Bugs

N/A: improvement run; the suspected stale callback is the AC-5 probe (T15), whose fail branch opens `player-callback-gate`.

### Manual checks

| # | AC | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 | AC-1, AC-8 | `cd <wt>/player-test-fixtures/src/main/assets/player-fixtures && sha256sum -c SHA256SUMS` | every line `OK` | a `FAILED` line or a file in the folder that `SHA256SUMS` does not list |
| M2 | AC-4, AC-5 | 1. lease `<serial>` (`tools/emulator-resource-lock.sh`); 2. run `make ... itest` with `TESTS=` the four classes comma-separated (`PlaybackControllerDeviceTest`, `PlaybackControllerNetworkTracksTest`, `PlaybackControllerResourcesTest`, `PlaybackControllerStaleCallbackTest`, package `com.kino.puber.ui.feature.player.vm`); 3. run it again; 4. record device API level, both results, T15's outcome; 5. release the lease | both runs green, or T15 alone red (then AC-5 fail branch); evidence headed "loopback fixtures" | a test that passes in one run and fails in the other (flake, R6); `BindException` on 18768 (R7); any `.stage` or `com.kino.puber` package touched; uninstall or data clear |
| M3 | AC-8 | read `docs/testing/player-playback.md` | names the exact `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=...` command, the Q1 device rule, `sha256sum -c SHA256SUMS`, and `tools/generate-player-test-fixtures.sh` as a maintainer tool outside the gate | a device serial hard-coded as the only option, or the generator listed as a gate step |
| M4 | AC-5 | if T15 fails: check the run `player-callback-gate` exists in `.flow/runs/` and T15 carries `@Ignore("player-callback-gate")` | both present | the failing probe deleted or left un-ignored |

### Perf

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Which device may install and run `com.kino.puber.instrumentation` and its test APK through `make FLAVOR=instrumentation itest`? | yes (AC-4, AC-5) | answered | emulator `host.docker.internal:5555`: lease, install of the instrumentation app and its test APK through `make itest`, no uninstall or data clear |
| Q2 | Drop Part B, open `player-compose-e2e-tests` blocked by `player-overlays-port`? | no | answered | yes |
| Q3 | Take upstream fixture bytes without regenerating? | no | answered | yes, verified against SHA256SUMS |
| A2 | `check.full` edit: on the branch or in the main checkout? | no | answered | conductor edits the main checkout's `.flow/config.yaml`; the branch leaves it alone |
| A3 | AC-6 allows only "not ported: needs a production change" | no | answered | widened to "not ported: <reason>" |
| A4 | detekt/lint do not cover the new module or androidTest sources | no | answered | leave as is |
| A5 | Makefile has no `PACKAGE_instrumentation` | no | answered | out of this run; itest does not need it |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-27 | Q2, Q3, A2-A5 as recommended (table above). AC-6 reads "not ported: <reason>" instead of "not ported: needs a production change"; AC-9's `.flow/config.yaml` part is done by the conductor in the main checkout | owner (standing instruction: recommended) |
| 2026-11-27 | Q1: AC-4/AC-5 instrumented tests run on the emulator `host.docker.internal:5555` (lease, `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 itest`, no uninstall or data clear) | owner (explicit answer, 2026-11-27) |
| 2026-11-27 | Gate 1: approved (size M, tracks none, 9 ACs, design and test design as written) | owner (standing instruction: recommended) |
| 2026-11-27 | codex required, antigravity on every round, round caps lifted | owner |

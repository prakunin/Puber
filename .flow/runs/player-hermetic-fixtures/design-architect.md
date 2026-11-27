# Design (architect): player-hermetic-fixtures

Source read in `/home/dev/orca/workspaces/Puber/player-hermetic-fixtures` at f53f2144; upstream sources from commit 1d3ec39e (present in the repo). ACs: `brief.md#acceptance-criteria`.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

- `BaselineNetworkBlocker` as a delegate to a ported `LoopbackNetworkBlocker` (D22 as written): moves the working multi-origin selector and its tests for no behaviour change.
- Cleartext for `127.0.0.1` in the instrumentation network security config (upstream): breaks `BaselineNetworkSecurityConfigTest.kt:27` and loosens the P5 contract.
- Ephemeral server port with a runtime allow-list: adds mutable state to the blocker, and `BaselineNetworkBlockerTest.kt:45-53` requires a different loopback port to be denied.
- `instrumentationImplementation` for the module: puts `mockwebserver3` and 1.7 MB of assets into the instrumentation app APK.
- Porting upstream's module `androidTest` (`PlayerTestFixturesAndroidTest`): no AC needs it, and it would add a second device run with another package.
- Robolectric callback-generation tests: D25.

## Risks

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

## Open questions

| # | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | (brief) device for AC-4/AC-5 | as the brief says | yes |
| Q2 | `check.full` lives in `.flow/config.yaml`, which the main checkout has uncommitted edits to (`check.full` among them), and the worktree copy is older. Who changes it? | The conductor adds `:player-test-fixtures:testDebugUnitTest` to the main checkout's `check.full` when the module task lands; the branch leaves `.flow/config.yaml` alone | no |
| Q3 | AC-6 allows only "ported" or "not ported: needs a production change". Some cases need something else: `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` needs an Activity and view host (Part B) | Change AC-6 to "ported" or "not ported: <reason>", where the reason is a production change or Part B | no |
| Q4 | detekt and lint do not cover `:player-test-fixtures` or the androidTest sources (`detektAll` is dev-only, `app/build.gradle.kts:355-358`) | Leave as is in this run | no |
| Q5 | `Makefile:25-27` has no `PACKAGE_instrumentation`, so `make FLAVOR=instrumentation info/logs` do not work | Out of this run; `itest` does not need it | no |

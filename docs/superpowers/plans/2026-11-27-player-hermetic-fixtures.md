# Hermetic player test fixtures and the stale-callback probe - plan

**Spec:** `docs/superpowers/specs/2026-11-27-player-hermetic-fixtures.md`
**Goal:** Port upstream P6 Part A as a `:player-test-fixtures` module plus controller-level device tests against the real `PlaybackController`, and host the stale-callback probe (AC-5) in it, with no `app/src/main` change.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; tasks that add or change `app/src/androidTest*` sources also run `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`.
- Full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug :player-test-fixtures:testDebugUnitTest && bash tools/tests/test-baseline-profile-tooling.sh` (the main checkout's `check.full` plus the module task).
- Run every Gradle command from `/home/dev/orca/workspaces/Puber/player-hermetic-fixtures` with `AGENT_GRADLE_SERIAL=1`; never `./gradlew` (guard hook).
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else. Upstream sources are read with `git show 1d3ec39e:<path>` (the commit is in the repo).
- `app/src/main` is not edited (AC-7). `.flow/config.yaml` is not edited on the branch: the conductor adds `:player-test-fixtures:testDebugUnitTest` to the main checkout's `check.full` (spec Open questions A2).
- Implementer tasks never touch a device. Device runs are the qa/conductor step after Task 6.
- No test or script reaches the live KinoPub API: every media URL is `PlayerTestControl.url(...)` or a `*.invalid` host.
- Short file names in citations resolve as follows; every other path is from the worktree root.

  | File | Directory |
  |---|---|
  | `PlaybackController.kt`, `PlaybackErrorPolicy.kt`, `PlaybackNetworkTuning.kt` | `app/src/main/java/com/kino/puber/ui/feature/player/vm/` |
  | `BaselineNetworkBlocker.kt`, `BaselineInstrumentationEnvironment.kt` | `app/src/instrumentation/java/com/kino/puber/profile/` |
  | `BaselineNetworkBlockerTest.kt`, `BaselineInstrumentationEnvironmentTest.kt`, `BaselineNetworkSecurityConfigTest.kt` | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/` |
  | upstream `HermeticTestServer.kt` | `player-test-fixtures/src/main/kotlin/com/kino/puber/playertestfixtures/server/` at 1d3ec39e |
- Device tests are JUnit 4 on `AndroidJUnitRunner` (`@RunWith(AndroidJUnit4::class)`, `org.junit.*`, as `app/src/androidTest/kotlin/com/kino/puber/data/db/WatchStateDaoTest.kt:13-28`); JVM tests are JUnit 5. Upstream's Kaspresso `run { step(...) { } }` blocks become plain test bodies; Kaspresso is not added.

---

## Tasks

### Task 1: Add the `:player-test-fixtures` module with its fixture pack and JVM tests

**Covers:** AC-1
**Files:**
- Modify: `gradle/libs.versions.toml` - after `android-application` (`:41`) add `android-library = { id = "com.android.library", version.ref = "agp" }`
- Modify: `build.gradle.kts` - after `:3` add `alias(libs.plugins.android.library) apply false`
- Modify: `settings.gradle.kts` - after `include(":baselineprofile")` (`:24`) add `include(":player-test-fixtures")`
- Create (byte for byte, `git checkout 1d3ec39e -- <path>`): `player-test-fixtures/src/main/assets/player-fixtures/` (the whole directory, incl. `SHA256SUMS` and `fixture-manifest.properties`), `player-test-fixtures/fixtures-provenance.md`, `tools/generate-player-test-fixtures.sh` (keep mode 755)
- Create (from 1d3ec39e, unchanged): `player-test-fixtures/src/main/kotlin/com/kino/puber/playertestfixtures/PlayerTestFixtures.kt`, `.../server/HermeticRequestJournal.kt`
- Create (from 1d3ec39e, changed): `.../server/HermeticTestServer.kt` - constructor `HermeticTestServer(port: Int = 0, serverCertificates: HandshakeCertificates? = null)`; in `start()` (upstream `:48-55`), before `server.start(...)`, when `serverCertificates != null`: `server.protocols = listOf(Protocol.HTTP_1_1)` and `server.useHttps(serverCertificates.sslSocketFactory())` (the pattern of `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:37`, `:63`; HTTP/1.1 keeps `Truncated`/`Disconnected` socket effects identical to plain HTTP)
- Create: `player-test-fixtures/build.gradle.kts` - text below
- Test (from 1d3ec39e, JUnit 5): `player-test-fixtures/src/test/kotlin/com/kino/puber/playertestfixtures/PlayerTestFixturesTest.kt`, `.../server/HermeticTestServerTest.kt`

Not ported: upstream `network/LoopbackNetworkBlocker.kt`, `network/LoopbackNetworkJournal.kt`, `src/androidTest/**` (spec Design, Alternatives rejected).

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kino.puber.playertestfixtures"
    compileSdk = Versions.CompileSdk
    defaultConfig { minSdk = Versions.MinSdk }
    compileOptions {
        sourceCompatibility = Versions.JavaVersionCompat
        targetCompatibility = Versions.JavaVersionCompat
    }
}

kotlin {
    jvmToolchain(Versions.ToolchainJavaVersion)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.JvmTargetVersion))
    }
}

dependencies {
    api(libs.mockwebserver3)
    api(libs.okhttp.tls)
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit5.launcher)
}

// The assets are the JVM tests' classpath resources too (PlayerTestFixtures.openPath falls back to the class loader).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    classpath += files("src/main/assets")
}
```

Symbols relied on: `Versions.MinSdk`, `CompileSdk`, `JavaVersionCompat`, `JvmTargetVersion`, `ToolchainJavaVersion` (`buildSrc/src/main/kotlin/Versions.kt:6`, `:12`, `:17`, `:18`, `:21`); `libs.mockwebserver3`, `libs.okhttp.tls` (`gradle/libs.versions.toml:89-90`), `libs.junit5`, `libs.junit5.launcher` (`:126-127`); the `kotlin {}` block mirrors `app/build.gradle.kts:258-261`.

**Consumes:** nothing earlier.
**Produces:** `HermeticTestServer(port, serverCertificates)` with `start/reset/route/url/takeRequest/awaitQuiescence/verifyRequiredRoutes/close`, `requestJournal`, `activeRequestCount` and the companion builders `bytes/text/redirect/sequence/delayed(gate, body, status, contentType)/truncated/disconnected` (upstream `HermeticTestServer.kt:21-193`); `ResponsePlan.Range` (`:419-444`); `PlayerTestFixtures.open/readBytes(FixtureId, Context?)` and `FixtureId`.

- [x] Baseline: `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin` on the unchanged tree; record the result (Tasks 3-5 depend on it being green before they add sources)
- [x] Add the catalog alias, the root `apply false`, the `include`, the module build file and the copied files
- [x] Port every test of both upstream test files to JUnit 5: `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*`; a JUnit 4 `assertX(message, ...)` becomes `assertX(..., message)`; `Assert.fail` becomes `Assertions.fail`. The AC-1 rows are T1 `PlayerTestFixturesTest.everyCommittedPackFile_matchesItsSha256AndExpectedFileSet`, `everyCatalogEntry_isPresentAndMatchesCommittedSha256`; T2 `HermeticTestServerTest.sequenceRedirectAndRangeResponsesAreDeterministic`; T3 `routesMatchMethodPathAndQueryWithoutQueueOrderAndRedactValues`, `unknownRoutesAreRecordedAndDelayedResponsesTrackActiveRequests`
- [x] Add T4 `HermeticTestServerTest.httpsServer_servesRangeRequest_toClientTrustingItsCertificate`: `HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()`; server with `HandshakeCertificates.Builder().heldCertificate(held).build()`, a `ResponsePlan.Range` route; an `OkHttpClient` with `sslSocketFactory`/`trustManager` from `HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()` sends `Range: bytes=2-5`; assert `server.url("/")` starts with `https://`, status 206, the 4 body bytes and `Content-Range`
- [x] Red step, one local mutation each, reverted before commit, results in the Implementation record: flip a byte in `progressive.mp4` (T1 red), make `ResponsePlan.Range.responseFor` return the full 200 (T2 red), skip `journal.begin` in the dispatcher (T3 red), drop the `useHttps` call (T4 red)
- [x] `cd player-test-fixtures/src/main/assets/player-fixtures && sha256sum -c SHA256SUMS` (M1): every line `OK`
- [x] `bash -n tools/generate-player-test-fixtures.sh`; the script is not run (no ffmpeg in the box)
- [x] Run the fast gate and `./tools/agentw :player-test-fixtures:testDebugUnitTest`; commit

**Done when:** `./tools/agentw :player-test-fixtures:testDebugUnitTest` passes with T1-T4 in its report, `sha256sum -c SHA256SUMS` prints only `OK`, and the fast gate is green.

---

### Task 2: Allow the player fixture origin in the instrumentation network blocker

**Covers:** AC-3
**Files:**
- Modify: `gradle.properties` - after `puber.baselineMockH2Port=18766` (`:6`) add `puber.playerFixturePort=18768`
- Modify: `app/build.gradle.kts` - in `create("instrumentation")` after `:247` add `buildConfigField("int", "PLAYER_FIXTURE_PORT", providers.gradleProperty("puber.playerFixturePort").get())`
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt` - add `fun playerFixtureOrigin(): String = "https://$MOCK_HOST:${BuildConfig.PLAYER_FIXTURE_PORT}"` next to `mockOrigin()` (`:17`); add it to `allowedOrigins()` (`:28-33`); KDoc `:24-27` names five origins and the player fixture server
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkBlockerTest.kt` - new T5 after `select_returnsNoProxy_whenOriginIsApiOrDnsTestHost` (`:67-77`)
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineInstrumentationEnvironmentTest.kt` - T6: expected set at `:72-77` gains `"https://127.0.0.1:${BuildConfig.PLAYER_FIXTURE_PORT}"`

**Consumes:** `BaselineNetworkBlocker.selector` (`BaselineNetworkBlocker.kt:27-42`), `BaselineNetworkJournal` (`:67-131`), `MOCK_HOST` (`BaselineInstrumentationEnvironment.kt:12`), `journalDirectory` (`:22`).
**Produces:** `BuildConfig.PLAYER_FIXTURE_PORT` (instrumentation flavor), `BaselineInstrumentationEnvironment.playerFixtureOrigin()`.

- [x] Add the property and the `buildConfigField` first, so the tests compile on the base behaviour
- [x] Write T5 `select_returnsNoProxy_whenOriginIsPlayerFixture`: `selector.select(URI("https://127.0.0.1:${BuildConfig.PLAYER_FIXTURE_PORT}/hls/master.m3u8"))` equals `listOf(Proxy.NO_PROXY)` and the journal is empty; update T6
- [x] Red run: `./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` - T5 and T6 fail; record the failing lines
- [x] Add `playerFixtureOrigin()` to `allowedOrigins()`; re-run: all green, including the T7 guards `select_returnsDeadProxyAndJournals_whenHostIsNotLoopback` (`:35-43`), `select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers` (`:45-53`, other port 18767), `configure_installsTheBlockerAsDefaultProxySelector` (`BaselineInstrumentationEnvironmentTest.kt:37-48`)
- [x] T8: `rg -l 'ProxySelector\(\)' app/src player-test-fixtures/src` lists only `BaselineNetworkBlocker.kt`
- [x] Run the fast gate; commit

**Done when:** `./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` passes with T5-T7, the T8 `rg` prints one file, and the fast gate is green.

---

### Task 3: Wire the module into the instrumentation test APK and add the controller harness and `PlaybackControllerDeviceTest`

**Covers:** AC-2, AC-4 (device run after Task 6), AC-7
**Files:**
- Modify: `app/build.gradle.kts` - after `androidTestImplementation(libs.coroutines.test)` (`:444`) add `"androidTestInstrumentationImplementation"(project(":player-test-fixtures"))`; no `implementation` or `instrumentationImplementation`
- Create: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTestControl.kt` - from `git show 1d3ec39e:app/src/instrumentation/java/com/kino/puber/profile/PlayerTestControl.kt`, package `com.kino.puber.ui.feature.player.vm`, changes below
- Create: `.../vm/PlaybackControllerHarness.kt` - shared setup/teardown, below
- Create: `.../vm/PlaybackControllerDeviceTest.kt` - from `git show 1d3ec39e:app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerDeviceTest.kt`

`PlayerTestControl` changes against upstream: default `port = BuildConfig.PLAYER_FIXTURE_PORT`; generates `HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()` once per instance, passes `HandshakeCertificates.Builder().heldCertificate(held).build()` to `HermeticTestServer`, and adds `fun newClient(): OkHttpClient` = `OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()` with `trust = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()`. The instrumentation network security config is not touched (`app/src/instrumentation/res/xml/network_security_config.xml:4-6`, guarded by `BaselineNetworkSecurityConfigTest.kt:27`).

`PlaybackControllerHarness` (composition, one instance per test, created in `@Before`, `close()` in `@After`):

| Member | Contract |
|---|---|
| `targetContext`, `testContext` | `InstrumentationRegistry.getInstrumentation().targetContext` / `.context`; fixture bytes are read with `PlayerTestFixtures.readBytes(id, testContext)` (assets live in the test APK) |
| `networkJournal` | `BaselineNetworkJournal(BaselineInstrumentationEnvironment.journalDirectory(targetContext))`, `clear()` in setup |
| `server: PlayerTestControl` | started with the test's routes; `mediaRoutes()` from upstream builds MP4 (`ResponsePlan.Range`), HLS playlists/segments and WebVTT routes from fixture bytes |
| `cache` | `SimpleCache(targetContext.cacheDir.resolve("player-device-${UUID.randomUUID()}"), LeastRecentlyUsedCacheEvictor(...), StandaloneDatabaseProvider(targetContext))`; not the app singleton (`app/src/main/java/com/kino/puber/data/di/modules.kt:142-148`) |
| `controller` | `PlaybackController(targetContext, server.newClient(), cache, PlayerPreferencesRepository(targetContext))` (`PlaybackController.kt:160-165`, `app/src/main/java/com/kino/puber/data/repository/PlayerPreferencesRepository.kt:10`), built after app start so the client takes the blocker as `ProxySelector` |
| `events: RecordingCallback` | implements `PlaybackControl.Callback` (`PlaybackController.kt:61-95`); appends one sealed `PlaybackEvent` per call (`StreamReady(url)`, `StreamFailure(message, recovery, url)`, `Error(message)`, `Ended`, `State(...)`, `Tracks(...)`, `Seek`) to a `CopyOnWriteArrayList`; `mark(): Int` and `since(mark)` |
| `onMain { }` | `runOnMainSync` returning the block's value; every `controller`/`controller.player` (`:204`) call goes through it |
| `awaitCondition(description, timeoutMs = 15_000) { }` | polls `onMain` every 50 ms; throws `AssertionError` naming `description` and the last events when it stays false |
| `newGate(): CountDownLatch` | tracked so `close()` can open it |
| `close()` | in order, each step run even if an earlier one threw, first failure rethrown with the rest suppressed: open all gates; `onMain { controller.release() }` (`:581-598`); `server.awaitQuiescence(10, SECONDS)`; `assertRequiredRoutesRequested()`; `assertNoUnknownRequests()`; `server.close()`; `cache.release()`; delete the cache directory; `networkJournal.snapshot()` is empty |

Streams are `StreamCandidate(url, StreamType.PROGRESSIVE | HLS)` (`app/src/main/java/com/kino/puber/domain/interactor/player/PlayerInteractor.kt:21-29`); subtitles `SubtitleLink(lang, url)` (`app/src/main/java/com/kino/puber/data/api/models/Models.kt:437`); `prepare(stream, subtitles, startPosition = 0L)` starts playback (`PlaybackController.kt:378-448`; `startPosition = null` leaves `playWhenReady` false). Upstream `ActivityScenario`/`PlayerView` and the rendered-frame checks are dropped (Part B).

**Consumes:** Task 1 module API; `BuildConfig.PLAYER_FIXTURE_PORT` and the allowed origin (Task 2).
**Produces:** `PlayerTestControl` (+ `newClient()`), `PlaybackControllerHarness`, `RecordingCallback`/`PlaybackEvent` for Tasks 4-5.

- [x] Add the dependency line, `PlayerTestControl`, the harness
- [x] Port into `PlaybackControllerDeviceTest`: T12 `progressiveMp4_reachesReadyAdvancesAndEnds` and T13 `hlsVod_reachesReadyAdvancesAndEnds` (READY, `currentPosition > 0`, `Ended` event; T13 also `server.assertRequiredRoutesRequested()`), T18 `progressivePlayback_controlsAndStartPositionUseRealPlayerEvents`, P2 `awaitCondition_failsClosedWhenConditionRemainsFalse`, P2 `unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics`
- [x] T9: for `devDebugRuntimeClasspath`, `prodReleaseRuntimeClasspath`, `instrumentationDebugRuntimeClasspath`: `./tools/agentw :app:dependencies --configuration <c> | grep -cE 'player-test-fixtures|mockwebserver3'` prints 0; for `instrumentationDebugAndroidTestRuntimeClasspath` `grep -c 'project :player-test-fixtures'` is at least 1; outputs in the Implementation record
- [x] T10: `git diff master --stat -- app/src/main` prints nothing
- [x] Run the fast gate and `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`; commit

**Done when:** both compile tasks are green, the four T9 counts are as stated, and T10 prints nothing.

---

### Task 4: Add the stale-callback probe `PlaybackControllerStaleCallbackTest`

**Covers:** AC-5 (device run after Task 6)
**Files:**
- Create: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt`

Routes: stream A = `/a/master.m3u8` as HLS, response `HermeticTestServer.delayed(gate, body = "not a playlist".toByteArray(), status = 200, contentType = "application/vnd.apple.mpegurl")`. A malformed body, not a 404: `Delayed` holds only the body (upstream `HermeticTestServer.kt:333-357`, `:477-486`), so a delayed 404 would fail at once on the status line, and a 404 is retried (`PlaybackErrorPolicy.kt:30-36`) while a `ParserException` is not and maps to `NEXT_URL` (`PlaybackController.kt:342-345`). Stream B = the progressive MP4 route from `mediaRoutes()`. The gate opens within 2 s of the switch, inside the 5 s read timeout (`PlaybackNetworkTuning.kt:18`) and the gate's own 10 s limit (upstream `HermeticTestServer.kt:480`).

**Consumes:** `PlaybackControllerHarness`, `RecordingCallback.mark/since` (Task 3); `switchStream` (`PlaybackController.kt:499-519`).
**Produces:** the AC-5 probe.

- [x] T16 `delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched`: prepare A; await A's request in `server.requestJournal`; open the gate; `awaitCondition` for a `StreamFailure` with `url == A` and `recovery == NEXT_URL`
- [x] T15 `switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream`: prepare A; await A's request admitted; `m = events.mark()`; `onMain { controller.switchStream(B, null) }`; open the gate; await `StreamReady`; then wait 3 s more; assert `events.since(m)` has no `StreamFailure`/`Error` and its first `StreamReady` has `url == B`. KDoc: a failure here is the AC-5 "reproduced" branch, handled in the device step below
- [x] Run the fast gate and `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`; commit
- [x] Fix round 1: `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` covers the queued-error window (see Implementation record)

**Done when:** both compile tasks are green and the class holds T15, T16 and the queued-failure case.

---

### Task 5: Port the NetworkTracks and Resources controller cases

**Covers:** AC-4, AC-6 (device run after Task 6)
**Files:**
- Create: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt` - from `git show 1d3ec39e:app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerNetworkTracksTest.kt`
- Create: `.../vm/PlaybackControllerResourcesTest.kt` - from `git show 1d3ec39e:app/src/androidTest/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt`

Port rule (spec Design, D24): a case is ported when it needs only `PlaybackControl`, `PlaybackController.player` and the fixture server. Replace upstream `LoopbackNetworkJournal` with `harness.networkJournal`, `ActivityScenario`/`PlayerView` with nothing, `okhttp3.OkHttpClient()` with `server.newClient()`, and adapt callback shapes to the fork's `PlaybackControl.Callback`. A case marked "port" that cannot run without a view host or a production change is left out and its reason goes to Task 6's table.

| Test (fork name) | Priority | Note |
|---|---|---|
| T14 `NetworkTracks.playerMediaNetworkIsolation_rejectsExternalRedirect` | P0 | a loopback route redirects to `https://media.invalid/x`; assert a `StreamFailure` arrives (timeout 30 s: 6 attempts, `PlaybackNetworkTuning.kt:24-31`) and `networkJournal.snapshot()` holds `https://media.invalid:443/x`; then `networkJournal.clear()` so teardown passes |
| `NetworkTracks.hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` | P1 | cue through `onMain { controller.player!!.addListener(...) }` (`onCues`) |
| `NetworkTracks.hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | P1 | |
| `NetworkTracks.hls404AndMalformedPlaylist_failWithoutUnboundedRetry` | P1 | `StreamFailure` with `NEXT_URL`; 404 route requested at most `MINIMUM_RETRY_COUNT + 1` = 6 times, malformed route once |
| `NetworkTracks.hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | P2 | |
| `NetworkTracks.progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` | P2 | |
| `NetworkTracks.hlsVariant400_fallsBackToTheHealthyVariant` | P2 | `PlaybackErrorPolicy.getFallbackSelectionFor` (`PlaybackErrorPolicy.kt:41-74`) |
| `NetworkTracks.hlsVariant502_fallsBackToTheHealthyVariant` | P2 | |
| `NetworkTracks.hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | P2 | gates from `harness.newGate()` |
| T19 `Resources.releaseDuringBlockedLoad_closesRequests` | P1 | gated segment; `onMain { controller.release() }`; open the gate; `server.awaitQuiescence` passes |
| `Resources.progressiveReplay_readsCommittedCacheAfterUpstreamRouteIsRemoved` | P2 | harness cache |
| `Resources.emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | P2 | |
| `Resources.progressiveDecode_reportsMandatoryCapabilitiesDecodersCountersAndAnalytics` | P2 | only if `player.videoDecoderCounters` advance without a surface; else not ported |
| `Resources.boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequests` | P2 | |

Not ported: `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` (needs an Activity and `PlayerView`, Part B).

**Consumes:** Task 3 harness and `PlayerTestControl`.
**Produces:** the final ported/not-ported list for Task 6.

- [x] Port the rows above; list each case left out and its reason in the Implementation record
- [x] Run the fast gate and `./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin`; commit

**Done when:** both compile tasks are green and every row above is either a test in the two classes or a line with its reason in the Implementation record.

---

### Task 6: Run the module tests in CI and write `docs/testing/player-playback.md`

**Covers:** AC-6, AC-8, AC-9 (CI part)
**Files:**
- Modify: `.github/workflows/pr-checks.yml` - `:95` becomes `run: ./gradlew testProdDebugUnitTest :player-test-fixtures:testDebugUnitTest`; `:102` `path:` becomes a two-line block with `app/build/reports/tests/` and `player-test-fixtures/build/reports/tests/`
- Create: `docs/testing/player-playback.md` - adapted from `git show 1d3ec39e:docs/testing/player-playback.md`, without Kaspresso, Compose UI, `PlayerVideoSurfaceTest` or `LoopbackNetworkBlocker` content

The doc holds:

| Section | Content |
|---|---|
| Layers | `:player-test-fixtures:testDebugUnitTest` (JVM); the four device classes in `com.kino.puber.ui.feature.player.vm` (instrumentation flavor test APK) |
| Run | `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerDeviceTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest` (`Makefile:133-141`); in the main checkout `GRADLE` may stay default |
| Device rule (Q1) | `<serial>` is the emulator `host.docker.internal:5555` under a lease (`tools/emulator-resource-lock.sh`); the run installs `com.kino.puber.instrumentation` and its test APK; no uninstall or data clear; the TV box `192.168.1.110:5555` is not used for these tests; another device needs the owner |
| Network | one blocker, `BaselineNetworkBlocker`; fixture origin `https://127.0.0.1:${PLAYER_FIXTURE_PORT}` from `puber.playerFixturePort`; per-run certificate trusted by the test's client only; journal must be empty after each test |
| Fixtures | path of the pack; `cd player-test-fixtures/src/main/assets/player-fixtures && sha256sum -c SHA256SUMS`; `tools/generate-player-test-fixtures.sh` is a maintainer tool (ffmpeg 9.0.1), not run by Gradle, tests or any gate |
| Evidence | device evidence for these tests is headed "loopback fixtures" (D28) |
| Upstream cases | one row per upstream test name of `PlaybackControllerDeviceTest`, `PlaybackControllerNetworkTracksTest`, `PlaybackControllerResourcesTest` at 1d3ec39e (the 20 names of the spec's "Upstream cases (AC-6)" table): fork test name and "ported", or "not ported: <reason>", taken from Task 5's record |

**Consumes:** Task 5's ported/not-ported list.
**Produces:** nothing later tasks use.

- [x] Edit the workflow; `python3 tools/tests/test_ci_workflows.py` exits 0
- [x] Write the doc
- [x] T17: for each of the 20 upstream names, `grep -cF '<name>' docs/testing/player-playback.md` is at least 1, and each row says "ported" or "not ported: "
- [x] T11 (branch part): `grep -n ':player-test-fixtures:testDebugUnitTest' .github/workflows/pr-checks.yml` prints line 95
- [x] Run the full gate (Global constraints); commit

**Done when:** the full gate exits 0 in the worktree, `test_ci_workflows.py` exits 0, and T17 finds all 20 names.

---

### Device step (qa-device and conductor, after Task 6; not an implementer task)

| Step | Owner | Action | Covers |
|---|---|---|---|
| 1 | conductor | add `:player-test-fixtures:testDebugUnitTest` to the main checkout's `.flow/config.yaml` `check.full`; T11 greps it | AC-9 |
| 2 | qa-device | M2: lease `host.docker.internal:5555` (`ssh mac-emu start`, `adb connect host.docker.internal:5555`), run the doc's `make ... itest` command with that serial twice, record API level, both results and T15's outcome under "loopback fixtures", release the lease, `ssh mac-emu stop` if no other lease | AC-4, AC-5 |
| 3 | qa-device | M1 output and the T9/T10 outputs into evidence | AC-1, AC-2, AC-7 |
| 4 | conductor | if T15 failed and T16 passed: open run `player-callback-gate`; a fix round adds `@Ignore("player-callback-gate")` to T15 only and records it in the doc (M4). If T16 failed, T15 is vacuous: a fix round on the test, not the AC-5 branch | AC-5 |
| 5 | qa-device | a P2 case red on the device for want of a view host moves to "not ported: <reason>" in a fix round; any other red test is a defect | AC-4, AC-6 |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Commit |
|---|---|
| 1 | d7858228 Add the player-test-fixtures module with its fixture pack, hermetic server and JVM tests |
| 2 | 48dd1db1 Allow the player fixture origin in the instrumentation network blocker |
| 3 | 48e5c0b8 Run the real PlaybackController against the loopback fixture server in instrumentation device tests |
| 4 | 7587253f Add the stale-callback probe for a stream failure that outlives a stream switch |
| 5 | b0833994 Port the controller NetworkTracks and Resources device cases onto the loopback fixture harness |
| 6 | the commit that holds this record |

**Red run** (P0 `red` tests on the unchanged code; no device was touched, so T12-T16, T18, T19 are compile-checked only and wait for the device step):

- red: T5 `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsPlayerFixture` - `AssertionFailedError at BaselineNetworkBlockerTest.kt:85` (dead proxy, origin journaled)
- red: T6 `BaselineInstrumentationEnvironmentTest.allowedOrigins_listsEachServedOriginOnItsServerPort` - `AssertionFailedError at BaselineInstrumentationEnvironmentTest.kt:71` (the set lacks the fixture origin)
- red: T1 `PlayerTestFixturesTest` (`everyCatalogEntry_isPresentAndMatchesCommittedSha256` at :17, `everyCommittedPackFile_matchesItsSha256AndExpectedFileSet` at :52) - mutation: one byte of `progressive.mp4` overwritten; reverted, `sha256sum -c SHA256SUMS` all OK
- red: T2 `HermeticTestServerTest.sequenceRedirectAndRangeResponsesAreDeterministic` - mutation: range response status 206 -> 200; `AssertionFailedError at HermeticTestServerTest.kt:85`
- red: T3 `HermeticTestServerTest.unknownRoutesAreRecordedAndDelayedResponsesTrackActiveRequests` (and the other journal-dependent tests) - mutation: journal entry not added; `IllegalStateException at HermeticTestServerTest.kt:117`
- red: T4 `HermeticTestServerTest.httpsServer_servesRangeRequest_toClientTrustingItsCertificate` - mutation: `useHttps` line dropped; `AssertionFailedError at HermeticTestServerTest.kt:296`
- guard (T7): the three listed tests stayed green after the origin was added
- Baseline of `:app:compileInstrumentationDebugAndroidTestKotlin` on the tree before any androidTest source: BUILD SUCCESSFUL

**Checks:**

- T8: `rg -l 'object : ProxySelector\(\)'` over app and module sources lists only `BaselineNetworkBlocker.kt`. The plan's `rg 'ProxySelector\(\)'` also matches the existing test name `configure_installsTheBlockerAsDefaultProxySelector` and the generated baseline profile text, as on the base commit.
- T9: `devDebugRuntimeClasspath`, `prodReleaseRuntimeClasspath`, `instrumentationDebugRuntimeClasspath` each `grep -cE 'player-test-fixtures|mockwebserver3'` = 0; `instrumentationDebugAndroidTestRuntimeClasspath` lists `project ':player-test-fixtures'` once (the plan's pattern `project :player-test-fixtures` lacks the quotes Gradle prints).
- T10: `git diff master --stat -- app/src/main` prints nothing.
- T11 branch part: `pr-checks.yml:95` runs `:player-test-fixtures:testDebugUnitTest`; `tools/tests/test_ci_workflows.py` passes.
- T17: all 20 upstream names found in `docs/testing/player-playback.md`.
- M1: `sha256sum -c SHA256SUMS` all OK.
- Full gate (Global constraints) exit 0.

**Deviations from the plan:**

- Task 1: the module's JVM tests get the assets through `sourceSets.getByName("test").resources.directories.add("src/main/assets")` in `android {}`; the plan's `classpath += files(...)` in `tasks.withType<Test>()` left the resources off the test classpath (AGP sets it later).
- Task 1: `HermeticTestServer.baseUrl` and `url()` now always use host `127.0.0.1` (the bound address). `MockWebServer.hostName` resolved to `localhost`, which breaks the certificate's `127.0.0.1` subject name and the blocker's allowed origin.
- Task 3: `PlaybackControllerHarness.mediaRoutes(required)` takes route ids, so T13 can require the master playlist without T12 failing teardown on routes it never requests.
- Task 5: `commonHlsRoutes` (the NetworkTracks route set) and the player read helpers live in two files, `PlaybackControllerRoutes.kt` and `PlaybackControllerReads.kt`, beside the harness. The read helpers are extensions on `PlaybackController` because `awaitCondition` already runs on the main thread, where `runOnMainSync` throws.
- Task 5: `SubtitleTrackUIState` has no `index` in the fork; the playback intent of upstream is `player.playWhenReady`.
- Task 5, not ported: `progressiveReplay_readsCommittedCacheAfterUpstreamRouteIsRemoved` (production change: `prepare` never writes the cache, `warmUpNext` does and stops at READY), `progressiveDecode_reportsMandatoryCapabilitiesDecodersCountersAndAnalytics` (needs an output surface, Part B), `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` (Activity and `PlayerView`, Part B). The doc carries the reasons.

**Files touched outside the plan:** none beyond the plan, plus the two helper files named above.

**For the reviewers:**

- Nothing in this run executed on a device. T12-T16, T18, T19 and the P2 ports are compiled only; whether the fork's `selectSubtitle` matches a side-loaded WebVTT row by URL (`hlsTracks`, `hlsSwitch`, `progressiveSwitch`), the loose `corrupt-cache` outcome and the "no callbacks after release" assertions hold is for the device step.
- `PlaybackControllerHarness.close()` checks the network journal last; `playerMediaNetworkIsolation_rejectsExternalRedirect` clears it itself after asserting the violation.
- The harness builds its client from `PlayerTestControl.newClient()` after the app started, so it picks the blocker as `ProxySelector`; T14 is what proves it.

**Blocked:** none.

### Fix round 1 (device run 1)

- Cause: `HermeticTestServer.url()` and `baseUrl` called `MockWebServer.url()`, which reverse-resolves the host name (`InetAddress.getHostName`); StrictMode throws `NetworkOnMainThreadException` when a test builds a URL inside `onMain` (13 failures). Fix: `start()` builds the root `HttpUrl` once from scheme, `127.0.0.1` and `server.port` (no lookup); `url()` resolves the path against it.
- `progressiveMp4` and `hlsVod` "Required routes were not requested": same root cause, not a separate bug. `prepare()` builds the stream URL inside `onMain`, threw before any request, and the teardown check on the never-requested required route then reported its own failure. No other change.
- Check: `:app:compileDevDebugKotlin :app:compileInstrumentationDebugAndroidTestKotlin :player-test-fixtures:testDebugUnitTest` green. The device re-run is the conductor's.

### Fix round 2 (review round 1, items #1-#11)

| Item | Fix | Commit |
|---|---|---|
| #3 occurrence outside the lock | `HermeticRequestJournal.begin` returns an `Admission(requestId, occurrence)`; the dispatcher no longer reads `matchedCount` | server commit |
| #4 nested Range | `Sequence.responseFor` delegates to its element | server commit |
| #5 truncation outcome | `ResponseOutcome.Truncated` and `Disconnected`; the recovery test asserts `Truncated(200)` then `Completed(200)` | server and device-test commits |
| #10, #11 | close-stall test releases the dispatcher once `close()` is parked; `awaitQuiescence` sleeps 5 ms per poll | server commit |
| #1, #2 | new case `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream`: in one `onMain` turn the gate opens, the turn waits for A's journal outcome and 500 ms for the parse, then `switchStream(B)`; it asserts the player probe saw A's error (non-vacuous) and no `StreamFailure`/`Error`. The old case keeps its name, waits on A's journal outcome and asserts the current media item is B | device-test commit |
| #7 | harness init failure closes what was built and rethrows; every test's `@After` skips an unbuilt harness | device-test commit |
| #8 | `hlsVod` accepts any `video_*.m3u8` | device-test commit |
| #9 | corrupt-cache step asserts READY without error and the path re-requested; the entry is no longer removed before the clean run | device-test commit |
| #6 | `pr-checks.yml` unit-tests job also compiles `:app:compileInstrumentationDebugAndroidTestKotlin` | CI/docs commit |

**Red run (JVM):** red: `HermeticTestServerTest.truncationAndDisconnectAreObservableWithoutLeakingActiveRequests` - `AssertionFailedError at :276` (outcome was `Completed(200)`, the new `Truncated` stub never produced); red: `sequenceOccurrenceIsFixedWhenTheRequestIsAdmitted` - `AssertionFailedError at :315` (first request got "second"); red: `sequenceDelegatesToTheRangeResponseOfItsElement` - `AssertionFailedError at :335` (200 instead of 206). #1 and #9 have no JVM red: device only.

**Deviations:** the server commit adds the test-only hook `afterAdmissionForTest` (after admission, outside the lock) so the occurrence race is deterministic.

**For the reviewers / device step:** #9 asserts a stronger outcome than before; READY on a corrupt entry is unproven (the cache's `FLAG_IGNORE_CACHE_ON_ERROR` covers IO errors, not a malformed container). If the device shows an error, the doc row becomes "ported, outcome not asserted". #1 asserts the player did deliver A's error; if it never does, the window is not reachable by this construction.

### Fix round 3 (device run 5)

- AC-5: reproduced. `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` is `@Ignore`d with slug `player-callback-gate` (assertions unchanged); the doc ordering table and the device evidence state the outcome.
- #9 fallback: the corrupt step asserts only a terminal outcome (READY or a reported error); the clean scenario removes the resource first and asserts READY and the path re-requested. Doc row: "ported, outcome not asserted", run `player-corrupt-cache-recovery`.
- `docs/testing/player-playback.md` warns that `make itest` can exit 0 on an incomplete run (run `itest-incomplete-run-green`).
- Red run: n/a (device-only; the red result is device run 5). `app/src/main` untouched.

## Readiness

Verdict: **READY**

Inputs: spec and plan in the worktree at df3a8725 (base f53f2144, plan uncommitted), `test-design.md`, `design-architect.md`, upstream 1d3ec39e.

### AC coverage

| AC | Plan task | Test design |
|---|---|---|
| AC-1 | Task 1 | T1-T4, M1 |
| AC-2 | Task 3 | T9 |
| AC-3 | Task 2 | T5-T8 |
| AC-4 | Tasks 3, 5; device step 2 | T12-T14, T18, T19, M2 |
| AC-5 | Task 4; device steps 2, 4 | T15, T16, M2, M4 |
| AC-6 | Tasks 5, 6 | T17 |
| AC-7 | Task 3 | T10 |
| AC-8 | Task 6 | M3 |
| AC-9 | Task 6; device step 1 (conductor, A2) | T11 |

### Citations sampled

All resolve to the claimed symbol in the code:

| Citation | Symbol |
|---|---|
| `PlaybackController.kt:160`, `:204`, `:260-264`, `:342-345`, `:499-519`, `:581-598`, `:624` | constructor, `player`, `onPlayerError` attribution to `currentStreamUrl`, `ParserException` -> `NEXT_URL`, `switchStream`, `release`, `okHttpClient.newBuilder()` |
| `PlaybackErrorPolicy.kt:30-36`, `PlaybackNetworkTuning.kt:18`, `:24` | no retry for `ParserException` only; `READ_TIMEOUT_SECONDS = 5L`; `MINIMUM_RETRY_COUNT = 5` |
| `BaselineNetworkBlocker.kt:27-42`, `BaselineInstrumentationEnvironment.kt:28-33`, `:54` | `selector`, `allowedOrigins()`, `install` call |
| `BaselineNetworkBlockerTest.kt:21`, `:45-53`; `BaselineNetworkSecurityConfigTest.kt:27` | `other = maxOf(h1, h2) + 1`; port-differs denial; no `domain-config` |
| `app/build.gradle.kts:246-247`, `:439-444`; `Makefile:133-141`; `pr-checks.yml:95` | `BASELINE_MOCK_*` fields; `androidTestImplementation` block; `itest`; `testProdDebugUnitTest` |
| upstream `HermeticTestServer.kt:48-55`, `:333-357`, `:480` | `start()`, `Delayed` (status sent, body gated), 10 s gate await |
| upstream test names | 5 + 9 + 6 in the three upstream device classes, matching the 20 rows of test-design "Upstream cases" |

### Order and build

Tasks 1 -> 2 -> 3 -> 4/5 -> 6 follow Consumes/Produces. Each task ends on the fast gate; tasks adding androidTest sources also run `:app:compileInstrumentationDebugAndroidTestKotlin`, whose base state Task 1 records first.

### Test design

Every P0 row (T1-T3, T5-T7, T9, T10, T12-T16) names its defect, is `red` or `guard`, and names the test or gate command in backticks. T16 guards T15 against a vacuous pass. Task 4 picks the malformed-body branch of T15 with a reason (`Delayed` sends the status before the gate).

### Scope and questions

The plan stays inside Out of scope: no `app/src/main` edit, no `.flow/config.yaml` edit on the branch, no Part B, no gate fix. Q1 (blocking) is answered: emulator `host.docker.internal:5555`, owner, 2026-11-27.

### Notes for the gate

1. Spec AC-6 still reads "not ported: needs a production change", and AC-9 says `check.full` passes in the worktree. Decisions widen AC-6 to "not ported: <reason>" and move the `check.full` edit to the conductor. The gate judges both ACs by the Decisions.
2. The Q1 answer puts device evidence on the emulator, not the TV box. The owner gave it explicitly for this run.

**Verify round 2 fixes:**

- `playerMediaNetworkIsolation_rejectsExternalRedirect`: the blocker journals the URI as `ProxySelector.select` receives it (origin, path `/`), so the test now expects the distinct journal to be `[https://media.invalid:443/]` after the `StreamFailure` callback. Blocker unchanged.
- Teardown flake: `PlayerTestControl.newClient()` created a client nobody could reach, so its idle keep-alive connection stayed in the pool when `MockWebServer.close` waited for its queue. `PlayerTestControl.close()` now cancels the dispatcher and evicts the connection pool of every client it handed out before closing the server. The controller is already released earlier in `PlaybackControllerHarness.close`. Not reproduced here (no device); the device re-run decides.

**Verify round 3 fix (teardown flake, root cause):**

The round 2 explanation (idle pooled client connection) was wrong. Evidence from the `mockwebserver3` 5.5.1 bytecode (the jar ships no sources; line numbers are from its `LineNumberTable`, `MockWebServer.kt`):

- `close()` (`MockWebServer.kt:402-418`) closes the server socket, then for every active `TaskQueue` waits `idleLatch().await(5 s)` and throws `AssertionError("Gave up waiting for queue to shut down")` (`:414`) when a queue is still busy. It closes no client socket itself.
- The accept task (`MockWebServer.kt:349-371`) closes `openClientSockets` and `openConnections` once `accept()` fails, so an idle keep-alive connection or a client that stopped reading does not hold the wait. Measured on the host JVM: with a keep-alive socket held open and with a 64 MB body the client never reads, `close()` returns in 1 ms.
- So only a serving task that cannot finish keeps a queue busy. `HermeticTestServer.close()` called `server.close()` inside `synchronized(lock)`, and `dispatch()` takes the same lock. A request still being dispatched while the controller is released (a loader retry or a pooled-connection reuse) blocked on the lock held by `close()`, which waited for that very task for 5 s. That is why the failing test changes between runs (about 1 in 19): it depends on which request is in flight at release.

Fix: `HermeticTestServer.close()` flips its state under the lock and calls `server.close()` outside it. The `cancelAll`/`evictAll` of 542b65c5 stays (it ends client calls early) but was not the cause.

Red run: `closeDoesNotStallARequestThatIsStillBeingDispatched` (JVM, `:player-test-fixtures`) held a request in `dispatch()` through the new `beforeDispatchForTest` hook, started `close()`, then released the request: red on the old code with `java.lang.AssertionError: Gave up waiting for queue to shut down` (`close() failed`), green after the change. Not re-run on a device by the implementer (no device in fix mode); the conductor's device runs decide.

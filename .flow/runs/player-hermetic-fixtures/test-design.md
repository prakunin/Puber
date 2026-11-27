# Test design: player-hermetic-fixtures

Inputs: `brief.md#acceptance-criteria`, `design-architect.md`. Base f53f2144. Upstream names from `git show 1d3ec39e:<path>`.

## Levels

| Level | Meaning here |
|---|---|
| `unit` | JUnit 5 on the JVM: `:player-test-fixtures:testDebugUnitTest`, `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` |
| `instrumented` | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run only as `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<fqcn>`; `<serial>` is the Q1 device |
| `gate` | a shell command whose output goes into evidence |
| `manual` | read or observe, recorded in evidence |

## Risk profile

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

## Traceability

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

## Bugs

N/A: improvement run; the suspected stale callback is the AC-5 probe (T15), whose fail branch opens `player-callback-gate`.

## Manual checks

| # | AC | Steps | Expected | Must not happen |
|---|---|---|---|---|
| M1 | AC-1, AC-8 | `cd <wt>/player-test-fixtures/src/main/assets/player-fixtures && sha256sum -c SHA256SUMS` | every line `OK` | a `FAILED` line or a file in the folder that `SHA256SUMS` does not list |
| M2 | AC-4, AC-5 | 1. lease `<serial>` (`tools/emulator-resource-lock.sh`); 2. run `make ... itest` with `TESTS=` the four classes comma-separated (`PlaybackControllerDeviceTest`, `PlaybackControllerNetworkTracksTest`, `PlaybackControllerResourcesTest`, `PlaybackControllerStaleCallbackTest`, package `com.kino.puber.ui.feature.player.vm`); 3. run it again; 4. record device API level, both results, T15's outcome; 5. release the lease | both runs green, or T15 alone red (then AC-5 fail branch); evidence headed "loopback fixtures" | a test that passes in one run and fails in the other (flake, R6); `BindException` on 18768 (R7); any `.stage` or `com.kino.puber` package touched; uninstall or data clear |
| M3 | AC-8 | read `docs/testing/player-playback.md` | names the exact `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=...` command, the Q1 device rule, `sha256sum -c SHA256SUMS`, and `tools/generate-player-test-fixtures.sh` as a maintainer tool outside the gate | a device serial hard-coded as the only option, or the generator listed as a gate step |
| M4 | AC-5 | if T15 fails: check the run `player-callback-gate` exists in `.flow/runs/` and T15 carries `@Ignore("player-callback-gate")` | both present | the failing probe deleted or left un-ignored |

## Perf

N/A: perf track off.

## UNTESTABLE

None.

# Player playback test guide

The player controller tests run the production `PlaybackController` and Media3 player against committed
synthetic media served from loopback. They need no KinoPub credentials, backend or public stream.

## Layers

| Layer | Entry point | What it proves |
|---|---|---|
| Fixture integrity (JVM) | `./tools/agentw :player-test-fixtures:testDebugUnitTest` | The committed bytes match `SHA256SUMS`; the hermetic server answers ranges, redirects, sequences, delays, truncation and HTTPS deterministically. Also runs in `.github/workflows/pr-checks.yml`. |
| Controller on a device | `PlaybackControllerDeviceTest`, `PlaybackControllerNetworkTracksTest`, `PlaybackControllerResourcesTest`, `PlaybackControllerStaleCallbackTest`, package `com.kino.puber.ui.feature.player.vm` (source set `androidTestInstrumentation`) | The real controller loads MP4 and HLS, selects tracks and cues, falls back on faulty variants, releases cleanly, and reports no failure for a load cancelled by a stream switch. A failure queued before `switchStream` is neither reported against the new stream nor stops it loading. |

## Run

```sh
AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest \
  TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerDeviceTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerNetworkTracksTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest,com.kino.puber.ui.feature.player.vm.PlaybackControllerStaleCallbackTest
```

In the main checkout `GRADLE` may stay at its default. The target is `make itest` (`Makefile`), which drives a private adb
server that knows only `<serial>`.

## Device rule

- `<serial>` is the emulator `host.docker.internal:5555` under a lease (`tools/emulator-resource-lock.sh`); the TV box
  `192.168.1.110:5555` is not used for these tests, any other device needs the owner.
- The run installs `com.kino.puber.instrumentation` and its test APK; it never uninstalls or clears data.
- Device evidence for these tests is headed "loopback fixtures".

## Network

- One blocker: `BaselineNetworkBlocker`. The fixture origin `https://127.0.0.1:${PLAYER_FIXTURE_PORT}` (gradle property
  `puber.playerFixturePort`, `BuildConfig.PLAYER_FIXTURE_PORT`) is in `BaselineInstrumentationEnvironment.allowedOrigins()`;
  every other origin gets the dead proxy and is journaled.
- The server speaks HTTP/1.1 over TLS with a certificate generated per test. Only the client from `PlayerTestControl.newClient()`
  trusts it; the network security config is unchanged.
- The network journal must be empty after each test, and so must the server's unknown-request list.

## Exit code

`make itest` reads the JUnit XML after a Gradle exit 0 and fails when no XML was written, when it holds the synthetic `testRunFailed` case (an incomplete run), or when it holds no test case (for example a `TESTS=` filter that matches nothing).

## Fixtures

The pack is `player-test-fixtures/src/main/assets/player-fixtures` (provenance: `player-test-fixtures/fixtures-provenance.md`).
Verify it with:

```sh
cd player-test-fixtures/src/main/assets/player-fixtures && sha256sum -c SHA256SUMS
```

`tools/generate-player-test-fixtures.sh` regenerates it. It is a maintainer tool (ffmpeg 9.0.1), not run by Gradle, tests or any gate.

## Stale-callback probe

`PlaybackControllerStaleCallbackTest` holds a stream's malformed playlist behind a gate. A failure or error event after
`switchStream(B)` is a regression, fixed by run `player-callback-gate` (`PlaybackCallbackGate`). Each case fixes one ordering:

| Case | Ordering it covers |
|---|---|
| `delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched` | Control: without the switch the failure must be reported, otherwise the probes prove nothing |
| `switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream` | The switch runs first; A's loader is cancelled before its body is parsed, so A's error normally never exists |
| `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` | A's error is parsed and queued on the main looper, then `switchStream(B)` runs in the same main-thread turn; the case also asserts the player delivered that error, so it cannot pass without reaching the window. Passes; guards the stale-error re-prepare in `PlaybackController.switchStream` |

## Upstream cases

Upstream is commit `1d3ec39e`. Kaspresso, `ActivityScenario` and `PlayerView` are not part of this port; those cases
wait for Part B.

| Upstream test | Fork test | Status |
|---|---|---|
| `progressiveMp4_reachesReadyRendersFrameAdvancesAndEnds` | `PlaybackControllerDeviceTest.progressiveMp4_reachesReadyAdvancesAndEnds` | ported; rendered-frame check not ported: needs a `PlayerView` (Part B) |
| `hlsVod_reachesReadyRendersFrameAdvancesAndEnds` | `PlaybackControllerDeviceTest.hlsVod_reachesReadyAdvancesAndEnds` | ported; rendered-frame check not ported: needs a `PlayerView` (Part B) |
| `progressivePlayback_controlsAndStartPositionUseRealPlayerEvents` | `PlaybackControllerDeviceTest.progressivePlayback_controlsAndStartPositionUseRealPlayerEvents` | ported |
| `awaitCondition_failsClosedWhenConditionRemainsFalse` | `PlaybackControllerDeviceTest.awaitCondition_failsClosedWhenConditionRemainsFalse` | ported |
| `unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics` | `PlaybackControllerDeviceTest.unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics` | ported |
| `hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` | `PlaybackControllerNetworkTracksTest.hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText` | ported; the cue is read through a player listener |
| `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | `PlaybackControllerNetworkTracksTest.hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | ported |
| `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | `PlaybackControllerNetworkTracksTest.hlsSwitch_preservesPositionIntentTracks_andLastStreamWins` | ported; the playback intent is read from `playWhenReady` |
| `progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` | `PlaybackControllerNetworkTracksTest.progressiveSwitch_restoresSelectedSubtitleAndPausedIntent` | ported |
| `hls404AndMalformedPlaylist_failWithoutUnboundedRetry` | `PlaybackControllerNetworkTracksTest.hls404AndMalformedPlaylist_failWithoutUnboundedRetry` | ported; asserts `onStreamFailure` with `NEXT_URL`, at most 6 requests for the 404 and one for the malformed playlist |
| `hlsVariant400_fallsBackToTheHealthyVariant` | `PlaybackControllerNetworkTracksTest.hlsVariant400_fallsBackToTheHealthyVariant` | ported |
| `hlsVariant502_fallsBackToTheHealthyVariant` | `PlaybackControllerNetworkTracksTest.hlsVariant502_fallsBackToTheHealthyVariant` | ported |
| `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | `PlaybackControllerNetworkTracksTest.hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | ported |
| `playerMediaNetworkIsolation_rejectsExternalRedirectAndSanitizesViolation` | `PlaybackControllerNetworkTracksTest.playerMediaNetworkIsolation_rejectsExternalRedirect` | ported; sanitizing not ported: `BaselineNetworkJournal` stores origin and path only, covered by `BaselineNetworkBlockerTest` |
| `progressiveReplay_readsCommittedCacheAfterUpstreamRouteIsRemoved` | - | not ported: needs a production change; `prepare` never writes the media cache (`cacheWriteSink(cache, writeToCache = false)`), only `warmUpNext` does, and it stops at READY |
| `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | `PlaybackControllerResourcesTest.emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | ported, outcome not asserted: corrupt container fails with Source error, see run player-corrupt-cache-recovery; the test asserts a terminal outcome, then a clean load after the resource is removed |
| `lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable` | - | not ported: needs an Activity and a `PlayerView` (Part B) |
| `progressiveDecode_reportsMandatoryCapabilitiesDecodersCountersAndAnalytics` | - | not ported: the video decoder is only initialised with an output surface, which needs a view host (Part B) |
| `releaseDuringBlockedLoad_closesRequestsAndDetachesReleasedView` | `PlaybackControllerResourcesTest.releaseDuringBlockedLoad_closesRequests` | ported; view detach not ported (Part B) |
| `boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequestsOrOpenSpans` | `PlaybackControllerResourcesTest.boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequests` | ported |

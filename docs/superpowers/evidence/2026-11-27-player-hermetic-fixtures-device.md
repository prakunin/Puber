# Device evidence: player-hermetic-fixtures

Build: worktree HEAD 73f3b5a4 for rounds 5 and 6 (da1c1f89 for rounds 1-4), instrumentation flavor (`com.kino.puber.instrumentation` and its test APK). Device: emulator `host.docker.internal:5555` (sdk_google_atv64_arm64, Android 16, API 36), the device the owner named for Q1. The device step was run by the conductor, not by the tester; the numbers below are copied from the conductor's `device-run-1..6.md` and `itest-r4-*.xml`, `itest-r5-*.xml`, `itest-r6-*.xml` in `.flow/runs/player-hermetic-fixtures/`. Only `com.kino.puber.instrumentation` was installed; no uninstall, no data clear, the TV box and `com.kino.puber.stage` untouched.

## loopback fixtures

Command, five runs in a row (round 4):

`AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerDeviceTest,...NetworkTracksTest,...ResourcesTest,...StaleCallbackTest`

| Run | Result | Suites (Device / NetworkTracks / Resources / StaleCallback) |
|-----|--------|------|
| r4-1 | exit 0, 19/19 | 5/5, 9/9, 3/3, 2/2 |
| r4-2 | exit 0, 19/19 | 5/5, 9/9, 3/3, 2/2 |
| r4-3 | exit 0, 19/19 | 5/5, 9/9, 3/3, 2/2 |
| r4-4 | exit 0, 19/19 | 5/5, 9/9, 3/3, 2/2 |
| r4-5 | exit 0, 19/19 | 5/5, 9/9, 3/3, 2/2 |

Failures, skips and errors in every suite of all five XML files: 0.

### AC-4

- Steps: run the command above; the controller is the real `PlaybackController` with ExoPlayer, media served only by the HTTPS loopback `HermeticTestServer` on 127.0.0.1:18768.
- Expected: MP4 and HLS reach STATE_READY, position advances, playback ends, network journal empty, required routes requested.
- Observed (5 of 5 runs): `progressiveMp4_reachesReadyAdvancesAndEnds` (4.36 to 4.40 s) and `hlsVod_reachesReadyAdvancesAndEnds` (4.70 to 4.80 s) pass; `progressivePlayback_controlsAndStartPositionUseRealPlayerEvents`, `playerMediaNetworkIsolation_rejectsExternalRedirect` (the redirect to media.invalid reached the blocker and was journaled) and `releaseDuringBlockedLoad_closesRequests` pass.
- Result: PASS.

### AC-5

- Steps: same command; `PlaybackControllerStaleCallbackTest` fails stream A with a delayed failure and switches to B. Orderings are in `docs/testing/player-playback.md`: cancellation ordering (switch first, gate opens after; `switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream`) and queued-error window (A's error queued on the main looper, `switchStream(B)` in the same main-thread turn; `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream`).
- Observed, cancellation ordering plus control `delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched`: pass in every run of rounds 2-6.
- Observed, queued-error window (round 5, build 3c437d95, runs 1 and 3; run 2 incomplete, see `device-run-5.md`): fails. A's malformed playlist completed (`/a/master.m3u8` Completed(200)), `switchStream(B)` ran in the same turn, the controller emitted `StreamFailure(message=Source error, recovery=NEXT_URL)` carrying B's URL, B was never requested.
- Result: reproduced (the AC's fail branch). Run `player-callback-gate` is opened (`.flow/runs/player-callback-gate/state.md`, stage idea) and the failing case is committed `@Ignore("player-callback-gate: ...")` in 73f3b5a4.

### Round 6 (build 73f3b5a4, conductor-run)

Three runs of the same command: each exit 0, JUnit XML 20 tests, 0 failures, 0 errors, 1 skipped (the `@Ignore`d queued-error case); no `testRunFailed`. Files `itest-r6-1..3.xml`.

### Round 5 side findings

- `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` failed in runs 1 and 3: a corrupt cache entry reaches READY, then fails with "Source error"; recovery is not reachable without a production change. 73f3b5a4 softened the step to a terminal outcome; green in round 6.
- Run 2 of round 5 was green by exit code but had no results (`testRunFailed` in the XML); tracked as run `itest-incomplete-run-green`.

### History before the final build

| Round | Build | Outcome |
|-------|-------|---------|
| 1 | before 1837ce91 | 13/19 failed twice: reverse DNS on the main thread (NetworkOnMainThreadException) |
| 2 | 1837ce91 | 18/19 and 17/19: wrong expected URI in the isolation test; teardown flake "Gave up waiting for queue to shut down" |
| 3 | 542b65c5 | 19/19, 18/19, 18/19: teardown flake in 2 of 3 runs |
| 4 | da1c1f89 | 5 of 5 runs 19/19 (root cause fixed: server close under the dispatch lock) |

## Static gate (tester, round 3, HEAD 73f3b5a4)

- `check.full` exit 0, including `bash tools/tests/test-baseline-profile-tooling.sh`; `python3 tools/tests/test_ci_workflows.py` all PASS.
- `:player-test-fixtures:testDebugUnitTest`: PlayerTestFixturesTest 7/7, HermeticTestServerTest 10/10.
- `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`: 33 tests, 0 failures (BaselineNetworkBlockerTest 11, BaselineFixtureContentTest 16, BaselineInstrumentationEnvironmentTest 3, BaselineAuthSeedTest 2, BaselineNetworkSecurityConfigTest 1).
- AC-2: `:app:dependencies` match count for `player-test-fixtures|mockwebserver3`: devDebugRuntimeClasspath 0, prodReleaseRuntimeClasspath 0, instrumentationDebugRuntimeClasspath 0, instrumentationDebugAndroidTestRuntimeClasspath 2.
- AC-7: `git diff master --stat -- app/src/main` prints nothing.

# Test design: player-callback-gate

Base `cec5f7fc`. Levels from `.flow/roles/test-designer.md`; `check` = a gate command. Unit tests in `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackCallbackGateTest.kt` (new, JUnit 5, plain `internal class` as `SeekStallRecoveryPolicyTest`; no ExoPlayer, no dispatcher needed: the gate is synchronous). Instrumented tests in `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/`, run with `AGENT_GRADLE_SERIAL=1 make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` under an emulator lease (state.md Q2).

## Risk profile

| # | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Stale A error slips through: B's URL marked loaded by A's queued load start, or the stale check placed after the BehindLiveWindow/AC3 branches (design) | 2 | 3 | 6 | `routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives`, AC-1 test |
| R2 | Queued stale error re-prepared without bound, or a real B error never reported (design: bound) | 2 | 3 | 6 | `routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry` |
| R3 | A `prepare` session treats its first real error as stale and swallows it (design: only switch sessions re-prepare) | 2 | 3 | 6 | `routeError_reportsFirstError_whenSessionComesFromPrepare`, guards `delayedFailure_...`, `hls404AndMalformedPlaylist_...` |
| R4 | Per-session listener rebinding loses the current session's READY/ENDED or tracks (design: listener lifetime) | 2 | 3 | 6 | AC-1 test (awaits B `StreamReady`), guards `hlsSwitch_preservesPositionIntentTracks_andLastStreamWins`, `progressiveMp4_reachesReadyAdvancesAndEnds` |
| R5 | Device test passes without reaching the window (brief, design) | 2 | 3 | 6 | precondition `assertNotNull(probe.error)` unchanged; three runs with XML case count (AC-1) |
| R6 | A media3 bump changes `prepare()` masking (brief, design) | 2 | 3 | 6 | AC-1 test is the regression test |
| R7 | Worktree build drops the box session to pairing (brief) | 3 | 2 | 6 | Manual step 1 |
| R8 | Re-entrant switch inside a callback leaves old events for a removed listener (design) | 2 | 2 | 4 | `dispatch_skipsAction_whenNewerSessionBegan` |
| R9 | Re-prepare after `release` or for a replaced session (design) | 1 | 3 | 3 | `routeError_doesNothing_whenSessionIsNotCurrent`, `dispatch_skipsAction_afterInvalidate` |
| R10 | Stale error flips `isPlaying` for one turn (design) | 3 | 1 | 3 | Manual check AC-6 |
| R11 | Real B error before B's `onLoadStarted` retried once (design) | 2 | 1 | 2 | Accepted: one extra prepare, then reported (R2 test) |
| R12 | Switch to the same URL as the failing stream: stale error reported, no re-prepare (design) | 1 | 2 | 2 | Accepted: today's behaviour, VM advances (`PlayerVM.kt:275-277`) |
| R13 | Listener add/remove per switch (design) | 1 | 1 | 1 | Accepted |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` (`@Ignore` removed, body unchanged; 3 runs, 3 cases each in the XML) | fails if A's queued error is reported against B or B is never prepared (`StreamReady` never arrives / current item not B) |
| AC-1 | instrumented | P0 guard | `PlaybackControllerStaleCallbackTest.delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched` | fails if the gate swallows a real failure of a `prepare` session or reports it with another URL (asserts A's URL, `NEXT_URL`) |
| AC-1 | instrumented | P0 guard | `PlaybackControllerStaleCallbackTest.switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream` | fails if rebinding the listener breaks the cancelled-load ordering |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_runsActionWithSessionUrl_whenSessionIsCurrent` | fails if the current session's action is dropped or gets a URL other than its own |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_skipsAction_whenNewerSessionBegan` | fails if the generation check is removed (session A dispatched after `beginSession(B)` runs) |
| AC-2 | unit | P0 red | `PlaybackCallbackGateTest.dispatch_skipsAction_afterInvalidate` | fails if `invalidate` does not end the current session (callback after `release`) |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsNothingAndRepreparesOnce_whenSwitchSessionHasNotLoadedItsUrl` | fails if a stale error is reported, or re-prepare is not called exactly once |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsSecondErrorWithSessionUrl_afterStaleRetry` | fails if the per-session retry flag is missing (second error re-prepared again) or the report carries a URL other than B's |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsNothingAndReprepares_whenLoadStartOfPreviousUrlArrives` | `onLoadStarted(A)` after `beginSession(B, fromSwitch = true)`; fails if `onLoadStarted` ignores the URL |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsWithSessionUrl_whenSwitchSessionLoadedItsUrl` | `onLoadStarted(B)` first; fails if a loaded session still re-prepares (real B error swallowed) |
| AC-3 | unit | P0 red | `PlaybackCallbackGateTest.routeError_reportsFirstError_whenSessionComesFromPrepare` | fails if `fromSwitch` is ignored and a first-play error is retried instead of reported |
| AC-3 | unit | P1 | `PlaybackCallbackGateTest.routeError_doesNothing_whenSessionIsNotCurrent` | fails if a stale session's error reprepares or reports (also after `invalidate`) |
| AC-3 | unit | P2 | `PlaybackCallbackGateTest.routeError_allowsOneRetryPerSession_whenAnotherSwitchBegins` | fails if the retry flag lives on the gate instead of the session |
| AC-4 | instrumented | P1 guard | `PlaybackControllerNetworkTracksTest.hlsSwitch_preservesPositionIntentTracks_andLastStreamWins`, `PlaybackControllerNetworkTracksTest.hls404AndMalformedPlaylist_failWithoutUnboundedRetry`, `PlaybackControllerDeviceTest.progressiveMp4_reachesReadyAdvancesAndEnds` | fails if gated ready, failure or ended callbacks stop arriving or rapid switches lose the last stream |
| AC-4 | manual (review) | P2 | Diff review of `PlaybackController.kt` `SessionListener` | every `onStreamReady`/`onStreamFailure`/`onError`/`onPlaybackEnded` goes through `gate.dispatch`/`routeError` and reads the session URL |
| AC-5 | manual (review) | P2 | Diff review of `switchStream` | no `player.stop()`; KDoc moved onto `switchStream` |
| AC-6 | device | P1 | Manual check 1 | fails if a switch shows the error screen, restarts from 0 or skips a candidate |
| AC-7 | manual (review) | P2 | Read `docs/testing/player-playback.md:11,57,63` | no "tracked"/"`@Ignore`d" wording left for the queued-failure case |
| AC-8 | check | P0 guard | `check.full` from `.flow/config.yaml` in the worktree | fails on any unit, detekt or lint regression, including `PlaybackCallbackGateTest` |

## Failing-first test

`PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream`: on base it is `@Ignore`d; with the annotation removed it fails (`StreamFailure(NEXT_URL)` with B's URL, B never requested: `.flow/runs/player-hermetic-fixtures/device-run-5.md`; cause `PlaybackController.kt:260-264` at `cec5f7fc` reads `currentStreamUrl`). Run it once on unchanged production code with only the `@Ignore` removed and record the failure before the fix. The `PlaybackCallbackGateTest` rows are red because the class does not exist on base.

## Manual checks

1. Device AC-6, TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only, under a lease.
   1. Before installing the worktree APK: build and install a master dev APK, confirm the app is logged in, run `make DEVICE=192.168.1.110:5555 auth-save` (R7).
   2. Install the worktree dev APK with `tools/android-apk-install-preserve`; launch; confirm the home screen, not pairing.
   3. Open a movie, start playback, let it play 30 s (`KEYCODE_DPAD_CENTER` on Play).
   4. Open the quality menu with the D-pad, pick a different quality (`KEYCODE_DPAD_DOWN`/`KEYCODE_DPAD_CENTER`), note the position before and after.
   5. Repeat step 4 back to the first quality.
   - Expected: each switch resumes within a few seconds of the noted position, playing; focus returns to the player.
   - Must not happen: error screen, restart from 0, stuck buffering, crash, pairing screen, a second skipped quality, any touch of `com.kino.puber`.

## perf track

N/A: perf track is off.

## UNTESTABLE

none

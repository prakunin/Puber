# Device evidence: player-device-tests-hardening (QA rounds 1 and 2)

Build: worktree HEAD c1de902f (round 1 was taken at 9ccbb15c; the commits after it are 219d94f5 and c1de902f), instrumentation flavor via `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest`.
Device: emulator AVD puber_tv_36 (sdk_google_atv64_arm64, Android 16), serial host.docker.internal:5555, under lease; released, emulator stopped afterwards (no other lease).
Only instrumented test classes were run; no live API traffic (hermetic local server).
Result XMLs are kept in .flow/runs/player-device-tests-hardening/ as itest-<n>.xml; every count below was re-read from the XMLs.

## Runs at 9ccbb15c (round 1)

| XMLs | Run | Result |
|---|---|---|
| 1-3 | NetworkTracks x3 | 13/13 each (AC-1, AC-2) |
| 4 | Resources x1 | 3/3 (AC-4) |
| 5-8 | StaleCallback, earlier signal attempts | 1 failure each (superseded, see implementation.md deviations) |
| 9-14 | StaleCallback, 9ccbb15c code | 4/4 each, 6 of 6. SUPERSEDED: the test was changed in c1de902f, so these no longer describe the head |
| 15 | mutant M2 (master, gates on 720p) | 13/13 pass: mutant survived (AC-3 as written not met) |
| 16 | mutant M1 | 1 failure: delayed segment is pending while BUFFERING condition stayed false |
| 18 | mutant M2b (master, gates on 360p) | 1 failure: first HLS media chunks are blocked condition stayed false |

NetworkTracks and Resources are unchanged by the later commits, which touch only PlayerTestControl.kt and PlaybackControllerStaleCallbackTest.kt, so these runs still apply at c1de902f. The round 1 QA re-run (StaleCallback 4/4, NetworkTracks 13/13 at 9ccbb15c) likewise applies to NetworkTracks only; the StaleCallback figure is superseded.

## Fix round 1 (219d94f5): per-path latch reset, KDoc softened

ClosedResponseSignals.intercept replaces the path's latch when a request for it starts. StaleCallback, 4 tests per run:

| XML | Result |
|---|---|
| itest-fix1-1 | 3/4: 1 failure (switchStreamAfterQueuedFailure, A's error never reached the player: the isIdle wait was satisfied by an unrelated main-looper message) |
| itest-fix1-2..6 | 4/4, 0 failures each (5 of 6 runs green) |
| itest-fix1-m4 | mutant M4 (SignalingBody counts down every path's latch): 4/4 pass. The mutant SURVIVES: the test does not discriminate per-path scoping; that scoping rests on code reading only |

The 1-in-6 failure showed the isIdle remainder was not reliable enough, which led to fix round 2.

## Fix round 2 (c1de902f): bounded retry replaces assertNotNull(probe.error)

Design (read from PlaybackControllerStaleCallbackTest.kt lines 88-130 and implementation.md): up to 5 attempts (QUEUED_ATTEMPT_PATHS = /a1..a5/master.m3u8), each with its own stream A path and gate, so the journal and latch of an earlier attempt cannot satisfy a later one. Per attempt: bounded wait (PARSE_WAIT_MS 500 ms) for A's response body close, then a bounded wait for a queued main-looper message. The attempt is judged (assertNoFailureAfter) only when probe.error is non-null, meaning A's error was delivered. Otherwise the next attempt starts; if none delivers, the test fails with per-attempt diagnostics. The old unconditional assertNotNull(probe.error) is gone. The only Thread.sleep left is SETTLE_MS after B is ready (a settle sleep after the precondition, not a gate).

| XML | Result |
|---|---|
| itest-fix2-1..10 | 4/4, 0 failures, 0 errors, 0 skipped in each: 10 of 10 green |

Runs 1 and 8 took about 2.7 s for the queued-failure case against about 1.3 s otherwise, consistent with one retry (implementation.md; QA did not re-time them). A first batch of 10 whose XMLs were not kept also ended green; only its last XML was inspected, so it is not counted here.

## Per AC (at c1de902f)

- AC-1: NetworkTracks 13/13 x3 (itest-1..3), mutant M1 fails (itest-16). Gate opens only after a pending delayed-segment and BUFFERING. PASS.
- AC-2: single-variant quality-low.m3u8 gates video_360_000.ts and audio_english_000.ts, both pending required with tracks; passes in itest-1..3. PASS.
- AC-3: as written (master path, gates on 720p) the mutant PASSED (itest-15) because ABR requested 720p first. Substitute M2b (master, gates on 360p) fails as intended (itest-18). PASS per the recorded substitute only; AC as written FAIL.
- AC-4: Resources 3/3 (itest-4); assertTrue(getCachedSpans(url).isEmpty()) follows the second removeResource; KDoc names the follow-up run; test name unchanged. PASS.
- AC-5: no Thread.sleep between the A outcome and switchStream(B). Waits are the bounded response-body close (application interceptor, per-path latch, reset at request start) and the bounded queued-message wait, inside a bounded retry. Remaining sleeps are poll and settle sleeps. PASS with delta (interceptor, not EventListener; retry added).
- AC-6: itest-fix2-1..10, 4/4 each, 10 of 10 (requirement 5); a counted attempt requires probe.error non-null. Earlier runs of 9ccbb15c code (itest-9..14) and fix round 1 (5 of 6) are superseded. PASS.
- Not proven: per-path scoping of the latch (M4 survives); isIdle can be satisfied by an unrelated message, which the retry absorbs but does not detect.

# Implementation record: player-device-tests-hardening

Emulator `host.docker.internal:5555` under lease, `make FLAVOR=instrumentation ... itest`. Result XMLs: `itest-<n>.xml` in this folder.

## Commits

9ccbb15c Wait for stream A's response to close instead of sleeping before the queued-failure switch
32fe9d46 Assert the cache entry is gone before the clean scenario and document the unasserted corrupt outcome
1f11d5a4 Gate the single-variant HLS test media so the tracks check proves both chunks are pending
716e2b2e Require a pending delayed segment before the BUFFERING test opens its gate
## Device runs

| XML | Run | Result |
|---|---|---|
| 1-3 | `PlaybackControllerNetworkTracksTest` x3 | 13/13 pass each (AC-1, AC-2) |
| 4 | `PlaybackControllerResourcesTest` | 3/3 pass (AC-4) |
| 5-7 | `PlaybackControllerStaleCallbackTest`, first attempt with an `EventListener` | 1 fail each: the signal never fired (see Deviations) |
| 8 | same, response-body interceptor, no main-looper wait | fail: A's error not yet queued (`probe.error` null) |
| 9-14 | same, final code | 4/4 pass, 6 of 6 runs (AC-6 asked 5) |
| 15 | mutant M2 (AC-3 as written) | **passes**, did not fail (see Deviations) |
| 16 | mutant M1 (delayed-segment route moved to an unfetched segment) | fails: "delayed segment is pending while the player is BUFFERING: condition stayed false" |
| 17 | M2 diagnostic (journal dump) | shows 720p requested first on the emulator |
| 18 | mutant M2b (master, gates on 360p) | fails: "first HLS media chunks are blocked: condition stayed false" |

Lease released, `ssh mac-emu stop` run (no other lease). No other device touched.

## Red run

n/a: items 1-2 pass on base vacuously by design; strength is shown by the mutants above.

## Deviations

1. **OkHttp signal (item 4).** An `EventListener` never fires: debug builds of `PlaybackController.createDataSourceFactory` replace the client's listener factory (`NetworkStats.factoryOrNull`, `PlaybackController.kt:668`). The signal is an application interceptor (`ClosedResponseSignals` in `PlayerTestControl.kt`) that raises a per-path latch when the response body source is closed; this fires on the loader thread after the parse failed. Event used: close of the response body. Test-only, no production change.
2. **Remainder (item 4).** Close fires before A's error is queued on the main looper (run 8). The test therefore also waits, bounded by `PARSE_WAIT_MS`, until `Looper.myQueue().isIdle` is false inside the blocked main turn. No fixed sleep remains between `aOutcome()` and `switchStream(B)`. `isIdle` could be true-trivially if other messages are queued; `assertNotNull(probe.error)` keeps a miss loud and held 6/6.
3. **AC-3 not met as written.** Mutant M2 (master path, gates on 720p) passes: on this emulator ABR/chunkless preparation requests `video_720_000.ts` first (run 17), so the 720p gate is genuinely pending and the new condition holds. The brief's premise that ABR starts on the ungated 360p is device-dependent. The single-variant master removes that dependence. M2b (master, gates on 360p, the variant not requested first here) fails as intended, showing the pending-gate condition discriminates. Conductor decides whether AC-3 is accepted via M2b.
4. Item 3: the :53 line comment was shortened to "A malformed container fails with "Source error"" because its rest moved to the KDoc.

## Files outside the plan

`PlaybackControllerHarness.kt` and `PlayerTestControl.kt` (planned: step 4). No `app/src/main` change.

## Look at

- `ClosedResponseSignals` and the `isIdle` wait in `PlaybackControllerStaleCallbackTest.kt`.
- `pending()` helper and the tracks condition in `PlaybackControllerNetworkTracksTest.kt`.

## check.full

BUILD SUCCESSFUL (117 tasks), then `test-baseline-profile-tooling.sh` and `test-itest-tooling.sh` pass, exit 0.

## Fix round 1

Triage items #1 and #3 (test code only, `PlayerTestControl.kt`).

| # | Change |
|---|---|
| 1 | `ClosedResponseSignals.intercept` replaces the path's latch when a request for it starts, so an earlier close of the same path no longer satisfies a later wait. |
| 3 | KDoc: "normally does once it is done with the playlist. Any close counts, so it is a bound on parsing, not proof of it." |

Mutant M4 (SignalingBody counts down every path's latch), run once, not committed: 4/4 pass (`itest-fix1-m4.xml`). The stale-callback test does not discriminate per-path scoping: in its window no other path's body closes before A's. The reset therefore rests on reading, not on a failing mutant.

Device runs, `PlaybackControllerStaleCallbackTest` on the emulator (`itest-fix1-<n>.xml`):

| n | Result |
|---|---|
| 1 | 1 fail: `switchStreamAfterQueuedFailure`, "A's error never reached the player" (the `isIdle` remainder, triage #2; the assertion keeps it loud) |
| 2, 3 | 4/4 pass |
| 4-6 | extra runs, 4/4 pass |

So 5 of 6 pass. Run 1 was the first run after the mutant build, and the failure is the accepted bounded remainder; the conductor decides whether 1 in 6 is acceptable. Lease released, `ssh mac-emu stop` run.

check.fast, `:app:compileInstrumentationDebugAndroidTestKotlin`, `:app:detektInstrumentationDebug`: BUILD SUCCESSFUL.

## Fix round 2

Triage item #2. Test code only (`PlaybackControllerStaleCallbackTest.kt`).

Cause: the `isIdle` wait, not the latch reset. In `itest-fix1-1.xml` A's request is `Completed(status=200)` and the close wait had passed (the reset happens at request start, before the wait), yet `probe.error` is null and B became ready. So `!Looper.myQueue().isIdle` was satisfied by a message other than A's error (the queue is not specific), and `switchStream(B)` ran first.

Choice: (b), bounded retry. (a) has no observable signal: A's error reaches the main thread only as a looper message, invisible from a blocked main turn, and `probe.error` is set only after it runs. Implementation: up to 5 attempts, each with its own stream A path (`/a1..a5/master.m3u8`) and gate, so the journal and latches of an earlier attempt cannot satisfy a later one. An attempt is judged (`assertNoFailureAfter`) only when `probe.error` is non-null, i.e. A's error was delivered, as before. Otherwise the next attempt starts; if none delivers, the test fails with the per-attempt diagnostics. No fixed sleep gates the precondition (`SETTLE_MS` after B is ready is unchanged).

| n | Result (`itest-fix2-<n>.xml`) |
|---|---|
| 1-10 | 4/4 pass, each |

10 of 10 pass. Runs 1 and 8 took 2.7 s for the case against 1.3 s otherwise, consistent with one retry. A first batch of 10 (XMLs not kept, the result file is overwritten per run) also ended green; only its last XML was inspected. Lease released, `ssh mac-emu stop` run.

check.fast (`:app:compileDevDebugKotlin`), `:app:compileInstrumentationDebugAndroidTestKotlin`, `:app:detektInstrumentationDebug`: BUILD SUCCESSFUL.

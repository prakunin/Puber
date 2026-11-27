LENS: tests  ROUND: 1  BASE: 13432572

I did not re-run the device tests. I judged them from the diff, the brief, the test design and the recorded evidence.

### F1 [minor] The "A's error is queued" precondition in the stale-callback test is only loosely tied to A's error
- Where: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:105`
- Problem: After A's response closes, the test waits on `!Looper.myQueue().isIdle`. That is true for any non-delayed message on the main queue, not only the message carrying A's error. The test's own comment says "queue turns non-idle". Any other main-looper work, such as player or harness posts, satisfies it.
- Failure scenario: Unrelated main-queue traffic makes the wait return before A's parse error is posted. `switchStream(B)` then runs first and A's error is dropped by the stale guard. The test still fails loudly at the existing `assertNotNull(probe.error)` precondition, so this is a flake risk, not a vacuous pass. It is a smaller form of the "slow device misses the precondition" problem item 4 set out to remove.
- Fix: Accept it as the documented bounded remainder, which the brief's Risks already allow. Or tighten it by waiting for a main-looper message that is known to come from A's error path. Note in the evidence that `probe.error` non-null held in all 6 final runs.

### F2 [minor] Per-path scoping of the close signal has no check, and AC-3 is met only by a substitute mutant
- Where: `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTestControl.kt:142-180` and `/home/dev/Puber/docs/superpowers/evidence/2026-11-27-player-device-tests-hardening-device.md`
- Problem: Mutant M4 (signal raised for any path) was optional and is not in the evidence list. Nothing shows that the latch is raised only by A's path. The latches are never reset, so an earlier close of the same path would pass silently. Separately, AC-3 as written (master path with gates on 720p) passed on the emulator, because ABR requested 720p first. It is recorded as met via the substitute mutant M2b, which the gate 1 delta accepted. M2b does show that the single-variant fix matters.
- Failure scenario: A future change makes another request of `STREAM_A_PATH` close earlier, for example a retry or a second fetch. The latch is already down, the wait returns immediately, and the test is back to timing luck. F1's isIdle wait is the only remaining guard.
- Fix: Optional. Run M4 once and record it, or note in the evidence that scoping is verified by reading only.

### Unproven
- `PlayerTestControl.kt` `SignalingBody`: the application interceptor wraps the body only when the loader closes the source, and `ResponseBody.close()` is assumed to route through `signalingSource`. The 6 final-code device runs support this, since the wait never timed out. A different Media3 version could change which close path is used.

Traceability against the test design:
- **AC-1, AC-2, AC-4:** each has a changed assertion in the named test. The guards are backed by M1 (AC-1) and M2b (AC-2) failing as expected.
- **AC-4:** the `getCachedSpans(url).isEmpty()` assertion is restored and the KDoc is present.
- **AC-5:** no fixed `Thread.sleep` remains between `aOutcome()` and `switchStream(B)`. Both new waits are bounded by `PARSE_WAIT_MS`.
- **AC-6:** 6 of 6 final-code runs plus 1 QA re-run, against a requirement of 5.
- **AC-7:** `check.full` is recorded as passing in the run state. I did not re-run it.
- **P0 red-run rule:** the P0 tests are marked guard plus mutant, as the test design requires.

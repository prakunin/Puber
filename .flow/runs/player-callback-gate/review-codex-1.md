CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt:83-84 - `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` blocks the main thread in `Thread.sleep(PARSE_WAIT_MS)` and assumes A's error is queued by then. - On a slow or contended emulator or CI device, `switchStream(B)` can run before Media3 has parsed A's response. A's load is then cancelled and the `probe.error` assertion fails, although the controller is correct. The test only checks `probe.error.get()` after the switch, so the failure message blames the harness and not the code. Only the test is affected. Replace the fixed sleep with a bounded wait on a playback-thread signal that the error is queued.

REJECTED:
- An old load-start for URL A can mark a replacement session for the same URL A as already loading, so the old error is reported and recovery is skipped (PlaybackCallbackGate.kt:40-55) - this needs a switch to the identical URL, either a refreshed link that returns the same URL or a fast A, B, A sequence. The spec already acknowledges that case. In that case the error is a real error for that URL and reporting it is correct. No ordinary caller or data produces a mismatch, and `PlayerVM` already ignores a failure URL that is not the current one. Unproven, and the cost is a worse state in a case that is not a regression.
- Track updates replace "01. Dubbing (RUS)" with "Track 1" (PlaybackController.kt:864-878). Lead from qa: the diff did not cause it.
  - The diff does not touch the mapper behind `notifyTracksUpdated`. It only wraps the call in `gate.dispatch(session)`.
  - `bindSession` registers the new listener before `setMediaItem`, so the current session's `onTracksChanged` and `STATE_READY` always get through. Only a stale session is dropped.
  - The label comes from the new rendition's `Format`, which has no label or language. The positional fallback was already in the code on base `cec5f7fc`, and the evidence notes the comparison with master was never run.
  - Nothing in the diff can produce "Track 1". I did not run the comparison on a master build, so a pre-existing quality-switch label regression is not ruled out. It belongs in a separate run if the owner cares, not in this diff.
- Simplify around a single source-session identity that can distinguish repeated URLs - a design preference. A smaller change would not give the stale-callback and re-prepare guarantees the ACs require, and no concrete failure was shown.

VERDICT: changes needed (one minor test-robustness item; the production diff is clean)

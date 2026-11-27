LENS: blind  ROUND: 1  BASE: 13432572

I read the diff and the route fixtures. I did not run anything.

### F1 [minor] ClosedResponseSignals latch is per path and one-shot, so the signal can fire for the wrong response
- Where: PlayerTestControl.kt, `ClosedResponseSignals.latch` / `awaitClosed`, and its use in PlaybackControllerStaleCallbackTest.kt.
- Problem: The latch is keyed by `encodedPath` only and is never reset. Any earlier close of the same path, such as a retry, a second request to the same playlist, or a previous scenario on the same harness, has already counted it down.
- Failure scenario: The stream A playlist path is requested twice, or an earlier close of it already happened. `awaitClosed` returns true at once, before the parse-failure close. The old fixed sleep is then back as a race, only less visible.
- Fix: Count closes per request, or reset the latch when a new request for the path starts, for example in `intercept` before `proceed`. Alternatively assert that the path was requested exactly once.

### F2 [minor] "Main queue is non-idle" check can pass for the wrong reason
- Where: PlaybackControllerStaleCallbackTest.kt, the new `awaitOnMain("stream A error is queued on the main looper") { !Looper.myQueue().isIdle }`.
- Problem: `isIdle` is false when any message is ready on the main looper, including progress ticks, frame callbacks and other player posts. It does not show that the stream A error is the queued message.
- Failure scenario: An unrelated message is posted while the main thread is blocked. The check passes before the error hop reaches the main queue, and `switchStream(B)` runs too early. The test then cannot reproduce the stale-callback ordering it is meant to cover, so it can pass without exercising it.
- Fix: Wait on a signal specific to the error, such as a hook in the playback-thread error path. Otherwise document the heuristic and keep a short extra grace sleep.

### F3 [nit] SignalingBody reads `delegate.source()` eagerly and wraps every response
- Where: PlayerTestControl.kt, `SignalingBody`.
- Problem: The interceptor wraps the body of every response, including ones that never reach the consumer's `close()`. For example, a response abandoned on a failed call only releases the connection when the body is closed. This is harmless here, since the source is a ForwardingSource whose close still closes the delegate. It does mean the wrapper reports a close, not "parsed". The KDoc says "which the loader does after it parsed", but any close counts, including a cancel.
- Failure scenario: A cancelled or aborted request closes the body and trips the latch, so the test proceeds as if the parse had completed.
- Fix: Soften the KDoc, or combine the latch with the `aOutcome()` check, which is already done.

### F4 [nit] `assertTrue(getCachedSpans(url).isEmpty())` added after `removeResource` is a precondition, not a behaviour check
- Where: PlaybackControllerResourcesTest.kt.
- Problem: The assertion is fine as a guard. The corrupt-entry test still asserts only "ready or error". Its new KDoc says this openly and defers to another run, so the test cannot fail if recovery regresses.
- Failure scenario: A cache-corruption regression that turns a recoverable entry into a permanent failure is not caught by this test.
- Fix: None needed if `player-corrupt-cache-recovery` covers it. Confirm that it does.

### Unproven
- PlaybackControllerNetworkTracksTest.kt:88 The gate on `video_360_000.ts` relies on the single-variant master serving only the 360p variant. The fixture (`singleVariantMaster("video_360.m3u8")`) matches this, and the audio rendition is present, so the audio gate is reachable. I found no evidence of a break.
- PlayerTestControl.kt:`newClient` The interceptor is an application interceptor. If the player's data source uses a different client or a network interceptor path, the signal might not fire. Nothing in the diff shows this happening.

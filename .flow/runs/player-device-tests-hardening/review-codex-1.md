CODEX: ran: review
CONFIRMED:
(none)
REJECTED:
- PlaybackControllerStaleCallbackTest.kt:105-106: `!Looper.myQueue().isIdle` can be true because of an unrelated main-looper message, so A's error might be posted after `switchStream`. Unproven. No source of such a message was shown. The test also asserts `probe.error` is non-null afterwards, so a miss fails loudly and doesn't pass silently. It can only make the test weaker in a rare race, and it cannot change production behaviour.
- PlaybackControllerNetworkTracksTest.kt:335-337: the historical `stateEvents` branch lets the wait pass after the player has left BUFFERING. Not reachable. The player cannot reach READY while the first segment is still held at the gate. A pending segment therefore implies the player is still buffering, and the wait cannot pass in a wrong state.
VERDICT: clean

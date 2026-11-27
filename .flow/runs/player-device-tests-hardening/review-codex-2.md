CODEX: ran: review (no adversarial pass, since size is S)
CONFIRMED:
- none
REJECTED:
- `PlaybackControllerStaleCallbackTest.kt:112`: `awaitOnMain` throws when the main queue stays idle, which aborts the test instead of retrying. The throw is not a retry case. It only fires if no message reaches the main looper within `PARSE_WAIT_MS` after the response has closed. The main thread is blocked for that whole time, so the queue turns non-idle as soon as the error hop is posted. A 5 s wait with no post means the hop never happened, and that is a real failure the test should report. Codex gave no scenario where it happens in ordinary use, so I rejected it as `unproven`.
- `PlayerTestControl.kt:158-161`: a second request for the same path replaces the latch, so an older body's `close()` can signal the newer request's latch. This needs two requests for one path to overlap. Each attempt uses its own unique path (`QUEUED_ATTEMPT_PATHS`), and a stream with a failed parse is not re-requested within an attempt. The class doc already says the latch is per path and that a close only bounds the parse. I found no real caller that starts overlapping requests for one path, so I rejected it as `unproven`.
VERDICT: clean

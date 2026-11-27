LENS: edge  ROUND: 2  BASE: cec5f7fc

No findings.

This round's diff (2d748aaf..6ec6e015) changes only tests and the plan. It adds `switchStreamToFailingStream_reportsOneFailureWithoutRePrepare` to `/home/dev/orca/workspaces/Puber/player-callback-gate/app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerStaleCallbackTest.kt` and an Implementation-record entry to `/home/dev/orca/workspaces/Puber/player-callback-gate/docs/superpowers/plans/2026-11-27-player-callback-gate.md`. Here is what I checked against the production code it exercises:

- **What the test depends on:** the new case expects exactly one request to B. That is safe because `PlaybackErrorPolicy.getRetryDelayMsFor` returns `C.TIME_UNSET` for a `ParserException`, so Media3 never retries the malformed playlist by itself. Any second request to B can only come from the gate's `reprepare`.
- **Does it fail when the wiring breaks?** If `gate.onLoadStarted` is removed, the session keeps `streamLoadStarted=false`. B's first error then triggers one silent `exoPlayer?.prepare()` and a second request to B before any `StreamFailure`, so the request-count assertion fails. The plan records this mutation run as red (`itest-fix1-mut.xml`).
- **URL match:** the expected URL is `harness.url(FAILING_B_PATH)`, which is the same string given to `bindSession` through `harness.stream(...)`. The test asserts it against `StreamFailure.url`. `ParserException` maps to `NEXT_URL`, so the controller takes the `onStreamFailure` branch, not `onError`.
- **Ordering and timing:** the switch runs after `awaitReady(probe)` on a healthy progressive A, so no error from A is queued and the stale-error branch is not involved. Any re-prepare would happen before the reported failure, so the wait on `StreamFailure` already covers it. The 1 s settle afterwards catches a late duplicate failure.
- **Teardown:** the new `/b/malformed.m3u8` route goes in through `useMediaRoutes(extra=...)` and is not `required`. The B request is a known route, so `assertNoUnknownRequests` stays clean.

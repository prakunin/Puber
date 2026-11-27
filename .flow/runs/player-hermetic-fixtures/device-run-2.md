# Device run 2 (conductor), 2026-11-27, after 1837ce91

Emulator host.docker.internal:5555 (API 36), lease acquired and released, emulator stopped. Same itest command, twice.

Run 1: 18/19 pass. Run 2: 17/19 pass.

- playerMediaNetworkIsolation_rejectsExternalRedirect fails in both runs: AssertionError expected [https://media.invalid:443/x] but was [https://media.invalid:443/] (PlaybackControllerNetworkTracksTest.kt:336). The blocker's journal records the URI as ProxySelector sees it (origin, path "/"), so the expected value is wrong; the isolation itself works (the redirect reached the blocker and was journaled).
- progressivePlayback_controlsAndStartPositionUseRealPlayerEvents fails in run 2 only, in teardown: "Gave up waiting for queue to shut down" at MockWebServer.close (HermeticTestServer.kt:142, via PlaybackControllerHarness.close:296). Flaky teardown: a connection still open when the server closes.
- T15 switchStreamBeforeDelayedFailure_reportsNoFailureForPreviousStream and T16 delayedFailure_reportsStreamFailure_whenStreamIsNotSwitched pass in both runs: stale callback not reproduced.

XML: itest-r2-1.xml, itest-r2-2.xml in this folder.

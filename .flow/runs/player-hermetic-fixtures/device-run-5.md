# Device run 5 (conductor), 2026-11-27, after 3c437d95 (review fix round 1)

Emulator host.docker.internal:5555 (API 36), lease acquired and released, emulator stopped. Same itest command (four classes, now 20 tests), three times.

- Run 1: exit 2, 18/20. Run 3: exit 2, 18/20. The same two tests fail in both:
  - PlaybackControllerStaleCallbackTest.switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream: after A's malformed playlist completed (journal: /a/master.m3u8 Completed(200)) and switchStream(B) ran in the same main-thread turn, the controller emitted StreamFailure(message=Source error, recovery=NEXT_URL, url=https://127.0.0.1:18768/media/progressive.mp4), i.e. A's failure attributed to stream B's URL, and B was never requested; "stream B is ready" stayed false for 15 s. This is the AC-5 "reproduced" outcome for the queued-error window.
  - PlaybackControllerResourcesTest.emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario: the corrupt cache entry reaches READY, then fails with ExoPlaybackException "Source error" (StreamFailure NEXT_URL). Recovery from a corrupt container is not reachable without a production change (FLAG_IGNORE_CACHE_ON_ERROR covers IO errors only).
- Run 2: Gradle exit 0 but the JUnit XML holds only "android-test.testRunFailed: Test run failed to complete. No test results" (itest5-2.log: connected task finished in 3 s). An incomplete instrumentation run is reported green by make itest.
- switchStreamBeforeDelayedFailure (cancelled-load ordering) and delayedFailure control pass in runs 1 and 3.

XML: itest-r5-1..3.xml, log itest5-2.log in this folder.

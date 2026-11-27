# Device run 3 (conductor), 2026-11-27, after 542b65c5

Emulator host.docker.internal:5555 (API 36), lease acquired and released, emulator stopped. Same itest command, three times.

- Run 1: 19/19 pass.
- Run 2: 18/19; progressivePlayback_controlsAndStartPositionUseRealPlayerEvents teardown "Gave up waiting for queue to shut down" (MockWebServer.close via HermeticTestServer.close:142, PlayerTestControl.close:123).
- Run 3: 18/19; hlsDelayedAndInterruptedSegment_enterBufferingThenRecover, same teardown failure.
- The isolation test now passes in all three runs. T15/T16 pass in all three runs.

The client connection-pool eviction did not remove the teardown flake. XML: itest-r3-1..3.xml in this folder.
